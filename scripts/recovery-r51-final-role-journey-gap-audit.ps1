param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery'
$ResultFile = Join-Path $EvidenceDir 'r51-final-role-journey-gap-audit-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r51-final-role-journey-gap-audit-2026-07-01.md'

function Read-Text {
    param([string]$RelativePath)
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        return $null
    }
    Get-Content -Raw -Encoding UTF8 -LiteralPath $path
}

function Read-Json {
    param([string]$RelativePath)
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        return [ordered]@{ ok = $false; path = $RelativePath; reason = 'missing' }
    }
    try {
        $value = Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json
        return [ordered]@{ ok = $true; path = $RelativePath; value = $value }
    } catch {
        return [ordered]@{ ok = $false; path = $RelativePath; reason = $_.Exception.Message }
    }
}

function Invoke-RepoScript {
    param(
        [string]$RelativePath,
        [string[]]$Arguments = @()
    )
    $scriptPath = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        return [ordered]@{
            ok = $false
            path = $RelativePath
            exitCode = -1
            output = "missing script: $RelativePath"
        }
    }

    $previousExitCode = $global:LASTEXITCODE
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath @Arguments 2>&1
    $exitCode = if ($null -eq $global:LASTEXITCODE) { 0 } else { [int]$global:LASTEXITCODE }
    $global:LASTEXITCODE = $previousExitCode

    return [ordered]@{
        ok = ($exitCode -eq 0)
        path = $RelativePath
        exitCode = $exitCode
        output = (($output | Out-String).Trim())
    }
}

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Category,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail,
        [string]$Severity = 'ERROR'
    )
    $Checks.Add([ordered]@{
        category = $Category
        name = $Name
        passed = $Passed
        severity = $Severity
        detail = $Detail
    }) | Out-Null
}

function Get-FirstRecommendedBatch {
    param([string]$GapReportText)
    if ([string]::IsNullOrWhiteSpace($GapReportText)) {
        return [ordered]@{ name = ''; rows = @() }
    }

    $headingMatch = [regex]::Match($GapReportText, '(?m)^###\s+(.+)$')
    if (-not $headingMatch.Success) {
        return [ordered]@{ name = ''; rows = @() }
    }

    $batchName = $headingMatch.Groups[1].Value.Trim()
    $start = $headingMatch.Index + $headingMatch.Length
    $nextHeading = [regex]::Match($GapReportText.Substring($start), '(?m)^###\s+')
    $section = if ($nextHeading.Success) {
        $GapReportText.Substring($start, $nextHeading.Index)
    } else {
        $GapReportText.Substring($start)
    }

    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($section -split "`r?`n")) {
        if ($line -match '^\|\s*(REQ-[^|]+)\s*\|') {
            $rows.Add($matches[1].Trim()) | Out-Null
        }
    }

    [ordered]@{ name = $batchName; rows = @($rows.ToArray()) }
}

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$checks = [System.Collections.Generic.List[object]]::new()

$frameworkAuditRun = Invoke-RepoScript -RelativePath 'scripts\final-goal-framework-audit.ps1'
$frameworkAudit = Read-Json -RelativePath 'docs/evidence/final-goal-framework-audit-result.json'
Add-Check $checks 'framework' 'framework audit executes' $frameworkAuditRun.ok "exitCode=$($frameworkAuditRun.exitCode)"
Add-Check $checks 'framework' 'framework audit passes V6 locks' ($frameworkAudit.ok -and $frameworkAudit.value.status -eq 'PASS') "status=$($frameworkAudit.value.status)"

$coverageRun = Invoke-RepoScript -RelativePath 'scripts\final-requirement-coverage-audit.ps1' -Arguments @('-NoFailExit')
$coverage = Read-Json -RelativePath 'docs/evidence/final-requirement-coverage-audit-result.json'
$coverageNotClosed = if ($coverage.ok) { [int]$coverage.value.notClosedCount } else { -1 }
Add-Check $checks 'requirements' 'requirement coverage audit executes' $coverageRun.ok "exitCode=$($coverageRun.exitCode)"
Add-Check $checks 'requirements' 'requirement coverage remains honestly open' ($coverage.ok -and $coverage.value.status -eq 'FAIL' -and $coverageNotClosed -gt 0) "status=$($coverage.value.status), notClosed=$coverageNotClosed" 'INFO'

$gapRun = Invoke-RepoScript -RelativePath 'scripts\final-requirement-gap-report.ps1'
$gapReportText = Read-Text -RelativePath 'docs/evidence/final-requirement-gap-report.md'
$firstBatch = Get-FirstRecommendedBatch -GapReportText $gapReportText
Add-Check $checks 'requirements' 'requirement gap report generates recommended batches' ($gapRun.ok -and -not [string]::IsNullOrWhiteSpace($firstBatch.name) -and $firstBatch.rows.Count -gt 0) "firstBatch=$($firstBatch.name), rows=$($firstBatch.rows -join ', ')"

$staticRun = Invoke-RepoScript -RelativePath 'scripts\final-usability-static-audit.ps1' -Arguments @('-NoFailExit')
$staticAudit = Read-Json -RelativePath 'docs/evidence/final-usability-static-audit-result.json'
Add-Check $checks 'usability' 'static usability audit executes' $staticRun.ok "exitCode=$($staticRun.exitCode)"
Add-Check $checks 'usability' 'static usability audit has no blockers' ($staticAudit.ok -and [int]$staticAudit.value.blockerCount -eq 0) "status=$($staticAudit.value.status), blockers=$($staticAudit.value.blockerCount), warnings=$($staticAudit.value.warningCount)"

$releaseRun = Invoke-RepoScript -RelativePath 'scripts\verify-release.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
Add-Check $checks 'release' 'release verification passes for deployed package' $releaseRun.ok "exitCode=$($releaseRun.exitCode)"

$stateText = Read-Text -RelativePath '.cursor/session/state.json'
$state = if ($null -ne $stateText) { $stateText | ConvertFrom-Json } else { $null }
$nextTasks = if ($null -ne $state) { @($state.build_plan.nextTasks) } else { @() }
Add-Check $checks 'state' 'session keeps user signoff open' ($null -ne $state -and $state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"
$nextTaskText = $nextTasks -join ' '
Add-Check $checks 'state' 'session points to V6 remediation or the R52 requirement-driven next task' (($nextTaskText -match 'Framework V6') -or ($nextTaskText -match 'REC-P0-052')) "nextTasks=$($nextTasks -join ', ')"

$finalAcceptanceText = Read-Text -RelativePath 'docs/recovery/final-usable-system-acceptance.md'
$journeyPartialCount = if ($null -ne $finalAcceptanceText) {
    ([regex]::Matches($finalAcceptanceText, 'PARTIAL_PASS_ENGINEERING')).Count
} else {
    0
}
$badFinalPassRows = @()
if ($null -ne $finalAcceptanceText) {
    foreach ($line in ($finalAcceptanceText -split "`r?`n")) {
        if ($line -match '^\|\s*J\d+\s*\|.*\|\s*`?PASS`?\s*\|') {
            $badFinalPassRows += $line.Trim()
        }
    }
}
Add-Check $checks 'final-acceptance' 'final usable-system acceptance is reopened' ($null -ne $finalAcceptanceText -and $finalAcceptanceText -match 'REOPENED_BY_USER_FEEDBACK') 'status marker expected'
Add-Check $checks 'final-acceptance' 'journey rows are engineering partials after user feedback' ($journeyPartialCount -ge 7 -and $badFinalPassRows.Count -eq 0) "partialRows=$journeyPartialCount, finalPassRows=$($badFinalPassRows.Count)"

$priorEvidence = @(
    @{ id = 'R45'; path = 'docs/evidence/recovery/r45-field-dict-menu-result.json' },
    @{ id = 'R46'; path = 'docs/evidence/recovery/r46-page-surface-result.json' },
    @{ id = 'R47'; path = 'docs/evidence/recovery/r47-runtime-daily-use-result.json' },
    @{ id = 'R48'; path = 'docs/evidence/recovery/r48-workflow-message-flow-result.json' },
    @{ id = 'R49'; path = 'docs/evidence/recovery/r49-openapi-assistant-integration-result.json' },
    @{ id = 'R50'; path = 'docs/evidence/recovery/r50-operations-release-log-result.json' }
)
$priorEvidenceResults = New-Object System.Collections.Generic.List[object]
foreach ($evidence in $priorEvidence) {
    $json = Read-Json -RelativePath $evidence.path
    $passed = $json.ok -and $json.value.status -eq 'PASS'
    $priorEvidenceResults.Add([ordered]@{
        id = $evidence.id
        path = $evidence.path
        ok = $json.ok
        status = if ($json.ok) { [string]$json.value.status } else { 'MISSING' }
    }) | Out-Null
    Add-Check $checks 'prior-evidence' "$($evidence.id) slice evidence is readable" $passed "$($evidence.path) status=$(if ($json.ok) { $json.value.status } else { $json.reason })" 'INFO'
}

$errorChecks = @($checks.ToArray() | Where-Object { $_.severity -eq 'ERROR' -and -not $_.passed })
$productStatus = if ($coverage.ok -and $coverage.value.status -eq 'PASS' -and $journeyPartialCount -eq 0 -and $state.gates.user_script_passed -eq $true) {
    'PASS'
} else {
    'FAIL_EXPECTED'
}
$auditStatus = if ($errorChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

$recommendedNextTask = "REC-P0-052 fresh deployed role-journey closure from $($firstBatch.name)"
if ($firstBatch.rows.Count -gt 0) {
    $recommendedNextTask = "$recommendedNextTask ($($firstBatch.rows -join ', '))"
}

$result = [ordered]@{
    status = $auditStatus
    productStatus = $productStatus
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    coverageNotClosedCount = $coverageNotClosed
    finalJourneyPartialCount = $journeyPartialCount
    userSignoff = if ($null -ne $state) { [bool]$state.gates.user_script_passed } else { $false }
    firstRecommendedBatch = $firstBatch
    recommendedNextTask = $recommendedNextTask
    priorEvidence = @($priorEvidenceResults.ToArray())
    checks = @($checks.ToArray())
    errorCount = $errorChecks.Count
}

$result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add('# R51 Final Role-Journey Gap Audit')
$summary.Add('')
$summary.Add("Status: $auditStatus")
$summary.Add('')
$summary.Add("Product status: $productStatus")
$summary.Add('')
$summary.Add("Base URL: $BaseUrl")
$summary.Add('')
$summary.Add('## Current Truth')
$summary.Add('')
$summary.Add("- Requirement rows not closed: $coverageNotClosed")
$summary.Add("- Final journey partial rows: $journeyPartialCount")
$summary.Add("- User signoff: $($result.userSignoff)")
$summary.Add("- First recommended batch: $($firstBatch.name)")
$summary.Add("- Recommended next task: $recommendedNextTask")
$summary.Add('')
$summary.Add('## Checks')
$summary.Add('')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary.Add("- [$mark] $($check.category) / $($check.name): $($check.detail)")
}
$summary.Add('')
$summary.Add('## Evidence')
$summary.Add('')
$summary.Add('- `docs/evidence/recovery/r51-final-role-journey-gap-audit-result.json`')
$summary.Add('- `docs/evidence/final-goal-framework-audit-result.json`')
$summary.Add('- `docs/evidence/final-requirement-coverage-audit-result.json`')
$summary.Add('- `docs/evidence/final-requirement-gap-report.md`')
$summary.Add('- `docs/evidence/final-usability-static-audit-result.json`')
$summary -join [Environment]::NewLine | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 80

if ($auditStatus -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
