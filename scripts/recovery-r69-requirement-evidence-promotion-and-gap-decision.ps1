param(
    [string]$BaseUrl = 'http://127.0.0.1:18131'
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r69-requirement-evidence-promotion-and-gap-decision'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r69-requirement-evidence-promotion-and-gap-decision-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r69-requirement-evidence-promotion-and-gap-decision-2026-07-02.md'
$BrowserAuditFile = Join-Path $EvidenceDir 'requirement-evidence-browser-audit.json'

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail,
        [string]$Severity = 'ERROR'
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        severity = $Severity
        detail = $Detail
    }) | Out-Null
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required JSON missing: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

function Read-TextFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file missing: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
}

function Run-Step {
    param(
        [string]$Name,
        [string]$Script,
        [string[]]$Arguments = @(),
        [int[]]$AllowedExitCodes = @(0)
    )
    $logFile = Join-Path $EvidenceDir "$Name.log"
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Script) + $Arguments
    $process = Start-Process -FilePath 'powershell' -ArgumentList $argList -NoNewWindow -Wait -PassThru -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"
    $stderr = "$logFile.err"
    if (Test-Path -LiteralPath $stderr) {
        $errText = Get-Content -Raw -Encoding UTF8 -LiteralPath $stderr
        if (-not [string]::IsNullOrWhiteSpace($errText)) {
            Add-Content -Encoding UTF8 -LiteralPath $logFile -Value $errText
        }
        Remove-Item -LiteralPath $stderr -Force
    }
    return [ordered]@{
        name = $Name
        exitCode = $process.ExitCode
        allowed = $AllowedExitCodes -contains $process.ExitCode
        logFile = $logFile
    }
}

$steps = @()
$steps += Run-Step -Name 'verify-release' -Script (Join-Path $RepoRoot 'scripts\verify-release.ps1') -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$steps += Run-Step -Name 'final-usability-static-audit' -Script (Join-Path $RepoRoot 'scripts\final-usability-static-audit.ps1')
$steps += Run-Step -Name 'final-goal-framework-audit' -Script (Join-Path $RepoRoot 'scripts\final-goal-framework-audit.ps1')
$steps += Run-Step -Name 'final-requirement-coverage-audit' -Script (Join-Path $RepoRoot 'scripts\final-requirement-coverage-audit.ps1') -AllowedExitCodes @(0, 1)

foreach ($step in $steps) {
    Add-Check 'child-step' "$($step.name) exit code is allowed" ([bool]$step.allowed) "exitCode=$($step.exitCode), log=$($step.logFile)"
}

$static = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$framework = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$coverage = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-requirement-coverage-audit-result.json')
$r67 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r67-final-role-journey-requirement-acceptance-candidate-result.json')
$r68 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r68-page-visual-designer-fresh-evidence-result.json')
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor\session\state.json')
$gapReportText = Read-TextFile (Join-Path $RepoRoot 'docs\evidence\final-requirement-gap-report.md')

Add-Check 'release' 'deployed release verification executed successfully' (($steps | Where-Object { $_.name -eq 'verify-release' }).allowed) "baseUrl=$BaseUrl"
Add-Check 'static-usability' 'static usability audit has no blockers or warnings' ($static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0 -and [int]$static.warningCount -eq 0) "status=$($static.status), blockers=$($static.blockerCount), warnings=$($static.warningCount)"
Add-Check 'framework' 'framework audit recognizes R69 as active executable task' ($framework.status -eq 'PASS' -and (@($framework.nextTasks) -join ' ') -match 'REC-P0-069') "status=$($framework.status), nextTasks=$(@($framework.nextTasks) -join ', ')"
Add-Check 'coverage' 'coverage audit remains honestly open while promotion decision is pending' ($coverage.status -eq 'FAIL' -and [int]$coverage.missingCount -eq 0 -and [int]$coverage.notClosedCount -eq 45) "status=$($coverage.status), missing=$($coverage.missingCount), notClosed=$($coverage.notClosedCount)"
Add-Check 'accepted-evidence' 'R67 candidate matrix is readable and complete' ($r67.status -eq 'PASS' -and [int]$r67.requirementTotal -eq 45 -and @($r67.requirementCandidateMatrix).Count -eq 45) "status=$($r67.status), rows=$(@($r67.requirementCandidateMatrix).Count)"
Add-Check 'accepted-evidence' 'R68 page visual designer fresh evidence is readable and accepted' ($r68.status -eq 'PASS' -and $r68.accepted -eq $true -and @($r68.requirementRows).Count -eq 3) "status=$($r68.status), rows=$(@($r68.requirementRows) -join ', ')"
Add-Check 'signoff' 'user script remains false before explicit user signoff' ($state.gates.user_script_passed -eq $false -and $r67.userSignoff -eq $false -and $r68.userSignoff -eq $false) "state=$($state.gates.user_script_passed), r67=$($r67.userSignoff), r68=$($r68.userSignoff)"

$r68EvidenceRows = @($r68.requirementRows)
$promotionMatrix = @()
foreach ($row in @($r67.requirementCandidateMatrix)) {
    $freshEvidence = @($row.freshEvidence)
    if ($r68EvidenceRows -contains $row.reqId) {
        $freshEvidence += 'R68 page-visual-designer'
    }
    $freshEvidence = @($freshEvidence | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Sort-Object -Unique)
    $hasFreshEvidence = $freshEvidence.Count -gt 0
    $canPromote = $false
    $decision = if ($hasFreshEvidence) { 'FRESH_ENGINEERING_EVIDENCE_STILL_PARTIAL' } else { 'NO_FRESH_EVIDENCE_STILL_PARTIAL' }
    $reason = if (-not $hasFreshEvidence) {
        'No fresh deployed evidence is attached to this row.'
    } elseif ($row.userSignoffRequired -eq $true -or $row.remainingGap -match 'Need ') {
        'Fresh engineering evidence exists, but the row still has a residual gap or explicit user acceptance boundary.'
    } else {
        'Promotion is blocked by the global user-signoff boundary.'
    }
    $promotionMatrix += [ordered]@{
        reqId = $row.reqId
        area = $row.area
        previousLedgerStatus = $row.ledgerStatus
        decision = $decision
        freshEvidence = $freshEvidence
        hasFreshEvidence = $hasFreshEvidence
        canPromoteToProven = $canPromote
        userSignoffRequired = $true
        residualGap = $row.remainingGap
        decisionReason = $reason
    }
}

$freshRows = @($promotionMatrix | Where-Object { $_.hasFreshEvidence })
$promotedRows = @($promotionMatrix | Where-Object { $_.canPromoteToProven })
$partialRows = @($promotionMatrix | Where-Object { -not $_.canPromoteToProven })
$noFreshRows = @($promotionMatrix | Where-Object { -not $_.hasFreshEvidence })

Add-Check 'promotion-matrix' 'all 45 requirement rows have promotion decisions' ($promotionMatrix.Count -eq 45) "rows=$($promotionMatrix.Count)"
Add-Check 'promotion-matrix' 'R68 closes the R67 no-fresh-evidence gap' ($freshRows.Count -eq 45 -and $noFreshRows.Count -eq 0) "freshRows=$($freshRows.Count), noFreshRows=$($noFreshRows.Count)"
Add-Check 'promotion-matrix' 'R69 does not promote rows without user signoff and residual gap closure' ($promotedRows.Count -eq 0 -and $partialRows.Count -eq 45) "promoted=$($promotedRows.Count), stillPartial=$($partialRows.Count)"

$nextResidualBatch = [ordered]@{
    taskId = 'REC-P0-070'
    name = 'No-Code Configuration Residual Depth Closure'
    source = 'R69 residual decision'
    requirementRows = @(
        'REQ-4.3',
        'REQ-5.3',
        'REQ-5.3.1',
        'REQ-5.4',
        'REQ-5.5',
        'REQ-5.6',
        'REQ-5.7',
        'REQ-5.9',
        'REQ-5.10',
        'REQ-6.7',
        'REQ-6.10'
    )
    reason = 'FRC-2 has the largest coherent residual block after R69: no-code configuration depth, app/module lifecycle, menu/group permutations, permission matrix UX, org/member lifecycle, field/dictionary designer depth, and user-facing admin clarity.'
    mustNotAskUser = $true
}

Add-Check 'next-batch' 'R69 emits a concrete residual coding batch instead of asking the user to inspect manually' (-not [string]::IsNullOrWhiteSpace($nextResidualBatch.taskId) -and @($nextResidualBatch.requirementRows).Count -gt 0) "task=$($nextResidualBatch.taskId), rows=$(@($nextResidualBatch.requirementRows).Count)"
Add-Check 'gap-report' 'gap report still exposes residual no-code configuration rows' ($gapReportText -match 'FRC-2 No-Code Configuration Depth' -and $gapReportText -match 'REQ-4.3' -and $gapReportText -match 'REQ-6.10') 'FRC-2 residual rows present'

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R69 aggregates R67 final role-journey browser evidence and R68 page visual designer browser evidence.'
    screenshotEvidenceBoundary = 'Screenshots prove visible hierarchy, overflow, clipping, and visible state only. API/readback assertions, permission positives/negatives, requirement matrix, residual gap decisions, and explicit user signoff prove behavior and acceptance.'
    r67BrowserAuditPath = $r67.browserAuditPath
    r68BrowserAuditPath = $r68.browserAuditPath
    r67RoleShellResults = $r67.primaryJourneyEvidence.roleShellRefresh.resultCount
    r68PageDesignerBrowserResults = $r68.pageDesignerEvidence.browserResultCount
    r68PageRuntimeBrowserResults = $r68.pageRuntimeEvidence.browserResultCount
    noFreshEvidenceRowsAfterR68 = @($noFreshRows | ForEach-Object { $_.reqId })
}
$browserAudit | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -LiteralPath $BrowserAuditFile

$errors = @($Checks | Where-Object { -not $_.passed -and $_.severity -eq 'ERROR' })
$status = if ($errors.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    task = 'REC-P0-069'
    productStatus = 'R69_REQUIREMENT_EVIDENCE_PROMOTION_DECISION_PASS_ENGINEERING_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    requirementTotal = $promotionMatrix.Count
    freshEvidenceRows = $freshRows.Count
    noFreshEvidenceRows = $noFreshRows.Count
    promotedToProvenRows = $promotedRows.Count
    stillPartialRows = $partialRows.Count
    nextResidualBatch = $nextResidualBatch
    checks = $Checks
    childResults = $steps
    promotionMatrix = $promotionMatrix
    browserAuditPath = 'docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json'
    accepted = ($status -eq 'PASS')
}
$result | ConvertTo-Json -Depth 25 | Set-Content -Encoding UTF8 -LiteralPath $ResultFile

$summaryLines = @(
    '# R69 Requirement Evidence Promotion And Residual Gap Decision',
    '',
    "Status: $status as engineering evidence only.",
    '',
    "Base URL: $BaseUrl",
    "Generated at: $($result.generatedAt)",
    '',
    'Key results:',
    '',
    "- Requirement rows: $($promotionMatrix.Count)",
    "- Rows with fresh evidence after R68: $($freshRows.Count)",
    "- Rows with no fresh evidence after R68: $($noFreshRows.Count)",
    "- Promoted to PROVEN: $($promotedRows.Count)",
    "- Still partial: $($partialRows.Count)",
    "- Next residual batch: $($nextResidualBatch.taskId) $($nextResidualBatch.name)",
    "- User signoff: false",
    '',
    'Evidence:',
    '',
    '- Result: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-result.json`',
    '- Browser aggregate: `docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json`',
    '',
    'Boundary:',
    '',
    'R69 decides evidence promotion and residual gaps. It does not set final user signoff and does not claim the product is complete.'
)
$summaryLines | Set-Content -Encoding UTF8 -LiteralPath $SummaryFile

$result | ConvertTo-Json -Depth 25
if ($status -ne 'PASS') {
    exit 1
}
