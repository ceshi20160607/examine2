param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
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
    if (-not $Passed -and $Severity -eq 'ERROR') {
        throw "$Area/$Name failed: $Detail"
    }
}

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $allArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell @allArgs 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
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
        throw "Expected result file was not found: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        task = 'REC-P0-068'
        productStatus = 'R68_PAGE_VISUAL_DESIGNER_FRESH_EVIDENCE_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
        userSignoff = $false
    }
    if ($script:FailureResultFile) {
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:FailureResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r68-page-visual-designer-fresh-evidence'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r68-page-visual-designer-fresh-evidence-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r68-page-visual-designer-fresh-evidence-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'page-visual-designer-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')
$childResults += Invoke-ChildScript 'final-goal-framework-audit' (Join-Path $PSScriptRoot 'final-goal-framework-audit.ps1') @()
$childResults += Invoke-ChildScript 'recovery-r42-page-designer-component-accessibility-fresh' (Join-Path $PSScriptRoot 'recovery-r42-page-designer-component-accessibility-smoke.ps1') @('-BaseUrl', $BaseUrl)
$childResults += Invoke-ChildScript 'recovery-r46-page-surface-fresh' (Join-Path $PSScriptRoot 'recovery-r46-page-surface-smoke.ps1') @('-BaseUrl', $BaseUrl)

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$FrameworkAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$R42 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r42-page-designer-component-accessibility-result.json')
$R46 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r46-page-surface-result.json')

Add-Check $script:Checks 'release' 'deployed release verification executed successfully' `
    (@($childResults | Where-Object { $_.name -eq 'verify-release' -and $_.exitCode -eq 0 }).Count -eq 1) `
    "baseUrl=$BaseUrl"

Add-Check $script:Checks 'static-usability' 'static usability audit has no blockers or warnings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

Add-Check $script:Checks 'framework' 'framework audit is healthy and keeps an executable next-task ledger' `
    ($FrameworkAudit.status -eq 'PASS' -and @($FrameworkAudit.nextTasks).Count -gt 0) `
    "status=$($FrameworkAudit.status), nextTasks=$(@($FrameworkAudit.nextTasks) -join ', ')"

Add-Check $script:Checks 'page-designer' 'fresh R42 page designer component evidence passed' `
    ($R42.status -eq 'PASS' -and [int]$R42.componentReadbackCount -ge 5 -and [int]$R42.browserResultCount -ge 8 -and [int]$R42.browserOverflowCount -eq 0) `
    "status=$($R42.status), components=$($R42.componentReadbackCount), browser=$($R42.browserResultCount), overflow=$($R42.browserOverflowCount)"

Add-Check $script:Checks 'page-designer-permission' 'fresh R42 keeps hidden field/component and readonly permission boundaries' `
    ($R42.hiddenFieldLeaked -eq $false -and $R42.hiddenComponentLeaked -eq $false -and [int]$R42.forbiddenCreateStatus -eq 403) `
    "hiddenFieldLeaked=$($R42.hiddenFieldLeaked), hiddenComponentLeaked=$($R42.hiddenComponentLeaked), forbiddenCreate=$($R42.forbiddenCreateStatus)"

Add-Check $script:Checks 'page-runtime' 'fresh R46 page definition publish and runtime readback passed' `
    ($R46.status -eq 'PASS' -and -not [string]::IsNullOrWhiteSpace([string]$R46.pagePublishedVersion) -and [int]$R46.adminSchemaComponentCount -ge 5 -and [int]$R46.browserResultCount -ge 10 -and [int]$R46.browserOverflowCount -eq 0 -and [int]$R46.browserBlockerCount -eq 0) `
    "status=$($R46.status), pagePublishedVersion=$($R46.pagePublishedVersion), components=$($R46.adminSchemaComponentCount), browser=$($R46.browserResultCount), overflow=$($R46.browserOverflowCount), blockers=$($R46.browserBlockerCount)"

Add-Check $script:Checks 'page-runtime-permission' 'fresh R46 keeps hidden component/field and normal-member admin write denials' `
    ($R46.hiddenFieldLeaked -eq $false -and $R46.hiddenComponentLeaked -eq $false -and [int]$R46.forbiddenHomeWriteStatus -eq 403 -and [int]$R46.forbiddenPageWriteStatus -eq 403) `
    "hiddenFieldLeaked=$($R46.hiddenFieldLeaked), hiddenComponentLeaked=$($R46.hiddenComponentLeaked), forbiddenHome=$($R46.forbiddenHomeWriteStatus), forbiddenPage=$($R46.forbiddenPageWriteStatus)"

$browserAggregate = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R68 aggregates fresh R42 page-designer component evidence and fresh R46 page publish/runtime evidence.'
    screenshotEvidenceBoundary = 'Screenshots prove visible hierarchy, overflow, clipping, and visible state only. API/readback assertions, permission positives/negatives, requirement matrix, and explicit user signoff prove behavior and acceptance.'
    pageDesigner = @{
        source = 'R42 fresh rerun'
        browserAuditPath = $R42.browserAuditPath
        componentReadbackCount = $R42.componentReadbackCount
        browserResultCount = $R42.browserResultCount
        browserOverflowCount = $R42.browserOverflowCount
        hiddenFieldLeaked = $R42.hiddenFieldLeaked
        hiddenComponentLeaked = $R42.hiddenComponentLeaked
        forbiddenCreateStatus = $R42.forbiddenCreateStatus
    }
    pageRuntime = @{
        source = 'R46 fresh rerun'
        browserAuditPath = $R46.browserAuditPath
        pagePublishedVersion = $R46.pagePublishedVersion
        adminSchemaComponentCount = $R46.adminSchemaComponentCount
        browserResultCount = $R46.browserResultCount
        browserOverflowCount = $R46.browserOverflowCount
        browserBlockerCount = $R46.browserBlockerCount
        hiddenFieldLeaked = $R46.hiddenFieldLeaked
        hiddenComponentLeaked = $R46.hiddenComponentLeaked
        forbiddenHomeWriteStatus = $R46.forbiddenHomeWriteStatus
        forbiddenPageWriteStatus = $R46.forbiddenPageWriteStatus
    }
}
$browserAggregate | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-068'
    productStatus = 'R68_PAGE_VISUAL_DESIGNER_FRESH_EVIDENCE_PASS_ENGINEERING_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    requirementRows = @('REQ-5.8', 'REQ-6.1', 'REQ-6.8')
    flowIds = @('C1', 'C2', 'C3', 'B1', 'B2')
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    pageDesignerEvidence = @{
        task = 'R42'
        status = $R42.status
        systemId = $R42.systemId
        moduleId = $R42.moduleId
        componentReadbackCount = $R42.componentReadbackCount
        browserResultCount = $R42.browserResultCount
        browserOverflowCount = $R42.browserOverflowCount
        hiddenFieldLeaked = $R42.hiddenFieldLeaked
        hiddenComponentLeaked = $R42.hiddenComponentLeaked
        forbiddenCreateStatus = $R42.forbiddenCreateStatus
    }
    pageRuntimeEvidence = @{
        task = 'R46'
        status = $R46.status
        systemId = $R46.systemId
        moduleId = $R46.moduleId
        pagePublishedVersion = $R46.pagePublishedVersion
        adminSchemaComponentCount = $R46.adminSchemaComponentCount
        browserResultCount = $R46.browserResultCount
        browserOverflowCount = $R46.browserOverflowCount
        browserBlockerCount = $R46.browserBlockerCount
        hiddenFieldLeaked = $R46.hiddenFieldLeaked
        hiddenComponentLeaked = $R46.hiddenComponentLeaked
        forbiddenHomeWriteStatus = $R46.forbiddenHomeWriteStatus
        forbiddenPageWriteStatus = $R46.forbiddenPageWriteStatus
    }
    browserAuditPath = 'docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json'
    accepted = $true
}
$result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# R68 Page Visual Designer Fresh Evidence

Status: PASS as engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-5.8, REQ-6.1, REQ-6.8
- Static audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- R42 page designer: status=$($R42.status), components=$($R42.componentReadbackCount), browser=$($R42.browserResultCount), overflow=$($R42.browserOverflowCount), hiddenFieldLeaked=$($R42.hiddenFieldLeaked), hiddenComponentLeaked=$($R42.hiddenComponentLeaked)
- R46 page runtime: status=$($R46.status), pagePublishedVersion=$($R46.pagePublishedVersion), components=$($R46.adminSchemaComponentCount), browser=$($R46.browserResultCount), overflow=$($R46.browserOverflowCount), blockers=$($R46.browserBlockerCount)
- Browser aggregate: `docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json`

This does not close final product acceptance. The requirement rows remain ledger-PARTIAL until the coverage ledger is deliberately promoted by a later requirement-closure task or the user explicitly signs/excludes scope. `gates.user_script_passed` remains false.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 40)
