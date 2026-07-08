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
    $logFile = Join-Path $script:WorkDir "$Name.log"
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
        throw "Missing required JSON file: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R96_DECISION_EVIDENCE_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = if ($script:Checks) { @($script:Checks.ToArray()) } else { @() }
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:WorkDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r96-final-remaining-partial-coverage-selection'
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery'
$script:ResultFile = Join-Path $EvidenceDir 'r96-final-remaining-partial-coverage-selection-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r96-final-remaining-partial-coverage-selection-2026-07-08.md'
$script:Checks = New-Object System.Collections.Generic.List[object]
New-Item -ItemType Directory -Force -Path $script:WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$childResults = @()
$childResults += Invoke-Child 'final-goal-framework-audit' (Join-Path $PSScriptRoot 'final-goal-framework-audit.ps1')
$childResults += Invoke-Child 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1')
$childResults += Invoke-Child 'final-requirement-coverage-audit' (Join-Path $PSScriptRoot 'final-requirement-coverage-audit.ps1') @('-NoFailExit')
$childResults += Invoke-Child 'final-requirement-gap-report' (Join-Path $PSScriptRoot 'final-requirement-gap-report.ps1') @('-NoFailExit')

$frameworkAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$staticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$coverageAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-requirement-coverage-audit-result.json')
$r95 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r95-platform-workbench-system-entry-density-result.json')
$session = Read-JsonFile (Join-Path $RepoRoot '.cursor\session\state.json')
$gapText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'docs\evidence\final-requirement-gap-report.md')
$flowText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'docs\framework\final-system-flow-blueprint.md')

Add-Check $script:Checks 'R95 blocker is accepted before selection' ($r95.status -eq 'PASS') "R95 status=$($r95.status)"
Add-Check $script:Checks 'framework audit passes' ($frameworkAudit.status -eq 'PASS') "status=$($frameworkAudit.status)"
Add-Check $script:Checks 'static usability audit has no blockers' ($staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
Add-Check $script:Checks 'coverage remains open and honest' ([int]$coverageAudit.notClosedCount -gt 0 -and [bool]$session.gates.user_script_passed -eq $false) "notClosed=$($coverageAudit.notClosedCount), userSignoff=$($session.gates.user_script_passed)"
Add-Check $script:Checks 'gap report keeps first-use IA gaps visible' ($gapText -match 'REQ-4\.1' -and $gapText -match 'REQ-6\.2' -and $gapText -match 'first-use') 'REQ-4.1/REQ-6.2 first-use gaps are still partial'
Add-Check $script:Checks 'flow blueprint requires C1 before deeper no-code/runtime work' ($flowText -match 'Flow C1: First-Use Configuration Guide' -and $flowText -match 'New systems must have a real setup path') 'C1 is the next flow-order candidate after entry/shell/system switch'

$selected = [ordered]@{
    taskId = 'REC-P0-097'
    title = 'C1 Fresh System Initialization Path And Empty Dashboard Hierarchy Closure'
    reason = 'R95 closed platform system-entry density. The next flow-order gap is C1: a freshly created or empty system must show a real setup path, and the empty system business dashboard must not mix runtime panels with initialization.'
    requirementRows = @('REQ-4.1', 'REQ-4.3', 'REQ-5.2', 'REQ-5.4', 'REQ-5.7', 'REQ-5.10', 'REQ-6.2', 'REQ-6.3', 'REQ-2.1')
    flowIds = @('A2', 'P2', 'S1', 'B1', 'C1', 'C2', 'C3')
    journeyRows = @('J1', 'J2', 'J3', 'J5', 'J8', 'J9', 'J11')
    implementationBoundary = 'Frontend C1/empty-dashboard hierarchy first; reuse existing admin data/readback APIs and system switch context. Add backend only if evidence proves an API readback gap.'
}

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'R96_ACCEPTED_AS_DECISION_EVIDENCE_ONLY'
    task = 'REC-P0-096'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    coverage = [ordered]@{
        totalRows = [int]$coverageAudit.ledgerRowCount
        missingCount = [int]$coverageAudit.missingCount
        notClosedCount = [int]$coverageAudit.notClosedCount
    }
    frameworkAuditStatus = [string]$frameworkAudit.status
    staticAuditStatus = [string]$staticAudit.status
    staticBlockerCount = [int]$staticAudit.blockerCount
    staticWarningCount = [int]$staticAudit.warningCount
    r95Status = [string]$r95.status
    selectedNextTask = $selected
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    accepted = $true
}
$result | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-096 / R96 Final Remaining Partial Coverage Selection

Status: PASS as decision evidence only.

- Base URL: $BaseUrl
- Framework audit: $($result.frameworkAuditStatus)
- Static audit: $($result.staticAuditStatus), blockers=$($result.staticBlockerCount), warnings=$($result.staticWarningCount)
- Coverage: totalRows=$($result.coverage.totalRows), missing=$($result.coverage.missingCount), notClosed=$($result.coverage.notClosedCount)
- R95 reference: $($result.r95Status)
- Selected next task: $($selected.taskId) $($selected.title)
- Requirement rows: $($selected.requirementRows -join ', ')
- Flow ids: $($selected.flowIds -join ', ')
- User signoff: false

R96 does not claim final product completion. It selects the next executable C1 first-use/empty-dashboard hierarchy closure and keeps every remaining partial coverage row visible.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 50)
