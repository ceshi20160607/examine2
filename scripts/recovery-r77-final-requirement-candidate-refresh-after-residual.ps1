param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        throw "$Name failed: $Detail"
    }
}

function Invoke-Child {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments = @(),
        [int[]]$AllowedExitCodes = @(0)
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($AllowedExitCodes -notcontains $exitCode) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logFile
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing required evidence file: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R77_FINAL_REQUIREMENT_CANDIDATE_REFRESH_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 40)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r77-final-requirement-candidate-refresh-after-residual'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r77-final-requirement-candidate-refresh-after-residual-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r77-final-requirement-candidate-refresh-after-residual-2026-07-02.md'
$script:Checks = New-Object System.Collections.Generic.List[object]
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-Child 'final-goal-framework-audit' (Join-Path $PSScriptRoot 'final-goal-framework-audit.ps1')
$childResults += Invoke-Child 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1')
$childResults += Invoke-Child 'final-requirement-coverage-audit' (Join-Path $PSScriptRoot 'final-requirement-coverage-audit.ps1') -AllowedExitCodes @(0, 1)
$childResults += Invoke-Child 'final-requirement-gap-report' (Join-Path $PSScriptRoot 'final-requirement-gap-report.ps1') -AllowedExitCodes @(0, 1)
$childResults += Invoke-Child 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')

$frameworkAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$staticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$coverageAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-requirement-coverage-audit-result.json')
$gapText = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'docs\evidence\final-requirement-gap-report.md')
$session = Read-JsonFile (Join-Path $RepoRoot '.cursor\session\state.json')

$residualIds = @('r70', 'r71', 'r72', 'r73', 'r74', 'r75', 'r76')
$residualEvidence = @()
foreach ($id in $residualIds) {
    $file = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'docs\evidence\recovery') -Filter "$id-*-result.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Add-Check $script:Checks "fresh residual evidence exists for $id" ($null -ne $file) ($file.FullName)
    $json = Read-JsonFile $file.FullName
    $residualEvidence += [ordered]@{
        id = $id.ToUpperInvariant()
        path = $file.FullName.Substring($RepoRoot.Length + 1).Replace('\', '/')
        status = [string]$json.status
        productStatus = [string]$json.productStatus
        userSignoff = [bool]$json.userSignoff
        browserResultCount = if ($null -ne $json.browserResultCount) { [int]$json.browserResultCount } else { $null }
        browserOverflowCount = if ($null -ne $json.browserOverflowCount) { [int]$json.browserOverflowCount } else { $null }
        browserBlockerCount = if ($null -ne $json.browserBlockerCount) { [int]$json.browserBlockerCount } else { $null }
    }
}

$failedResidual = @($residualEvidence | Where-Object { $_.status -ne 'PASS' })
$signoffLeak = @($residualEvidence | Where-Object { $_.userSignoff -eq $true })
Add-Check $script:Checks 'framework audit passed' ($frameworkAudit.status -eq 'PASS') "status=$($frameworkAudit.status)"
Add-Check $script:Checks 'static usability audit passed without blockers' ($staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
Add-Check $script:Checks 'coverage audit remains honest and not final closed' ([int]$coverageAudit.notClosedCount -gt 0 -and [bool]$session.gates.user_script_passed -eq $false) "notClosed=$($coverageAudit.notClosedCount), userSignoff=$($session.gates.user_script_passed)"
Add-Check $script:Checks 'gap report still exposes open final requirement rows' ($gapText -match 'PARTIAL' -or $gapText -match 'notClosed') "gapReportLength=$($gapText.Length)"
Add-Check $script:Checks 'all residual evidence R70-R76 passed' ($failedResidual.Count -eq 0) (($failedResidual | ConvertTo-Json -Compress -Depth 8))
Add-Check $script:Checks 'residual evidence does not claim user signoff' ($signoffLeak.Count -eq 0) (($signoffLeak | ConvertTo-Json -Compress -Depth 8))

$nextGap = 'Final candidate refresh found all requirement rows still partial because user signoff is false and the coverage ledger has not promoted rows to PROVEN. Next execution should choose the largest remaining PARTIAL row group from final-requirement-gap-report after reviewing the refreshed matrix.'

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'R77_ACCEPTED_AS_DIAGNOSTIC_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-077'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    coverage = [ordered]@{
        totalRows = [int]$coverageAudit.ledgerRowCount
        requiredCount = [int]$coverageAudit.requiredCount
        missingCount = [int]$coverageAudit.missingCount
        notClosedCount = [int]$coverageAudit.notClosedCount
        promotedToProven = 0
    }
    frameworkAuditStatus = [string]$frameworkAudit.status
    staticAuditStatus = [string]$staticAudit.status
    staticBlockerCount = [int]$staticAudit.blockerCount
    staticWarningCount = [int]$staticAudit.warningCount
    residualEvidence = $residualEvidence
    nextGap = $nextGap
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    accepted = $true
}
$result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-077 / R77 Final Requirement Candidate Refresh After Residual Closure

Status: PASS as diagnostic engineering evidence only.

- Base URL: $BaseUrl
- Framework audit: $($result.frameworkAuditStatus)
- Static audit: $($result.staticAuditStatus), blockers=$($result.staticBlockerCount), warnings=$($result.staticWarningCount)
- Coverage: totalRows=$($result.coverage.totalRows), missing=$($result.coverage.missingCount), notClosed=$($result.coverage.notClosedCount), promotedToProven=$($result.coverage.promotedToProven)
- Residual evidence: $($residualEvidence.Count) files checked for R70-R76, all PASS, userSignoff=false
- Next gap: $nextGap

This does not close final product acceptance. `gates.user_script_passed` remains false.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 60

if ($NoFailExit) {
    exit 0
}
