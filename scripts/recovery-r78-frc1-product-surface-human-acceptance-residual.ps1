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
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        throw "$Area/$Name failed: $Detail"
    }
}

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments = @(),
        [int[]]$AllowedExitCodes = @(0)
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
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
        throw "Expected JSON file was not found: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R78_PRODUCT_SURFACE_HUMAN_ACCEPTANCE_RESIDUAL_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
        userSignoff = $false
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 50)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r78-frc1-product-surface-human-acceptance-residual'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r78-frc1-product-surface-human-acceptance-residual-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r78-frc1-product-surface-human-acceptance-residual-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'product-surface-human-acceptance-browser-audit.json'
$script:Checks = New-Object System.Collections.Generic.List[object]
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1')
$childResults += Invoke-ChildScript 'final-goal-framework-audit' (Join-Path $PSScriptRoot 'final-goal-framework-audit.ps1')
$childResults += Invoke-ChildScript 'recovery-r68-page-visual-designer-fresh-evidence' (Join-Path $PSScriptRoot 'recovery-r68-page-visual-designer-fresh-evidence.ps1') @('-BaseUrl', $BaseUrl)
$childResults += Invoke-ChildScript 'recovery-r75-operations-logs-release-error-state-residual' (Join-Path $PSScriptRoot 'recovery-r75-operations-logs-release-error-state-residual.ps1') @('-BaseUrl', $BaseUrl)
$childResults += Invoke-ChildScript 'recovery-r77-final-requirement-candidate-refresh-after-residual' (Join-Path $PSScriptRoot 'recovery-r77-final-requirement-candidate-refresh-after-residual.ps1') @('-BaseUrl', $BaseUrl)

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$FrameworkAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$R68 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r68-page-visual-designer-fresh-evidence-result.json')
$R75 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r75-operations-logs-release-error-state-residual-result.json')
$R77 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r77-final-requirement-candidate-refresh-after-residual-result.json')
$Session = Read-JsonFile (Join-Path $RepoRoot '.cursor\session\state.json')

$PlatformAdminSource = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend\src\features\platform-admin\platformAdmin.ts')
$RuntimeSource = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend\src\features\runtime\records\runtimeRecords.ts')

Add-Check $script:Checks 'release' 'deployed release verification executed before product-surface audit' `
    (@($childResults | Where-Object { $_.name -eq 'verify-release' -and $_.exitCode -eq 0 }).Count -eq 1) `
    "baseUrl=$BaseUrl"

Add-Check $script:Checks 'static-usability' 'production source has no static usability blockers or warnings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

Add-Check $script:Checks 'framework' 'framework audit recognizes R78 as the active next task' `
    ($FrameworkAudit.status -eq 'PASS' -and @($FrameworkAudit.nextTasks | Where-Object { [string]$_ -match 'REC-P0-078' }).Count -gt 0) `
    "status=$($FrameworkAudit.status), nextTasks=$(@($FrameworkAudit.nextTasks) -join ', ')"

Add-Check $script:Checks 'session' 'user signoff remains separate from engineering evidence' `
    ([bool]$Session.gates.user_script_passed -eq $false -and [bool]$R68.userSignoff -eq $false -and [bool]$R75.userSignoff -eq $false -and [bool]$R77.userSignoff -eq $false) `
    "state=$($Session.gates.user_script_passed), r68=$($R68.userSignoff), r75=$($R75.userSignoff), r77=$($R77.userSignoff)"

Add-Check $script:Checks 'page-home-runtime' 'R68 proves page designer and runtime surface readback without overflow' `
    ($R68.status -eq 'PASS' -and [int]$R68.pageDesignerEvidence.browserOverflowCount -eq 0 -and [int]$R68.pageRuntimeEvidence.browserOverflowCount -eq 0 -and [int]$R68.pageRuntimeEvidence.browserBlockerCount -eq 0) `
    "status=$($R68.status), designerOverflow=$($R68.pageDesignerEvidence.browserOverflowCount), runtimeOverflow=$($R68.pageRuntimeEvidence.browserOverflowCount), runtimeBlockers=$($R68.pageRuntimeEvidence.browserBlockerCount)"

Add-Check $script:Checks 'page-home-runtime-permission' 'R68 keeps hidden component/field pruning and admin write denials' `
    ($R68.pageDesignerEvidence.hiddenFieldLeaked -eq $false -and $R68.pageDesignerEvidence.hiddenComponentLeaked -eq $false -and $R68.pageRuntimeEvidence.hiddenFieldLeaked -eq $false -and $R68.pageRuntimeEvidence.hiddenComponentLeaked -eq $false -and [int]$R68.pageRuntimeEvidence.forbiddenHomeWriteStatus -eq 403 -and [int]$R68.pageRuntimeEvidence.forbiddenPageWriteStatus -eq 403) `
    "designerHidden=$($R68.pageDesignerEvidence.hiddenFieldLeaked)/$($R68.pageDesignerEvidence.hiddenComponentLeaked), runtimeHidden=$($R68.pageRuntimeEvidence.hiddenFieldLeaked)/$($R68.pageRuntimeEvidence.hiddenComponentLeaked), forbiddenHome=$($R68.pageRuntimeEvidence.forbiddenHomeWriteStatus), forbiddenPage=$($R68.pageRuntimeEvidence.forbiddenPageWriteStatus)"

Add-Check $script:Checks 'advanced-boundary' 'R75 proves advanced operations and logs surfaces have browser containment' `
    ($R75.status -eq 'PASS' -and [int]$R75.browserResultCount -ge 6 -and [int]$R75.browserOverflowCount -eq 0 -and [int]$R75.browserBlockerCount -eq 0) `
    "status=$($R75.status), browser=$($R75.browserResultCount), overflow=$($R75.browserOverflowCount), blockers=$($R75.browserBlockerCount)"

Add-Check $script:Checks 'product-surface-source' 'platform config surface exposes human summary markers and compact records' `
    ($PlatformAdminSource -match 'platformConfigSummary' -and $PlatformAdminSource -match 'compact-product-panel' -and $PlatformAdminSource -match 'auditActionLabel' -and $PlatformAdminSource -match 'trace-chip') `
    'platform config/log source markers present'

Add-Check $script:Checks 'runtime-source' 'runtime published version marker uses real module publishVersion' `
    ($RuntimeSource -match 'runtimePublishedVersion: liveData\?\.activeModule\.publishVersion' -and $RuntimeSource -notmatch 'activeModule\.publishedVersion') `
    'runtime module version marker uses publishVersion'

Add-Check $script:Checks 'coverage-boundary' 'R77 keeps final coverage honest instead of promoting rows without user signoff' `
    ($R77.status -eq 'PASS' -and [int]$R77.coverage.notClosedCount -gt 0 -and [int]$R77.coverage.promotedToProven -eq 0) `
    "notClosed=$($R77.coverage.notClosedCount), promoted=$($R77.coverage.promotedToProven)"

$browserAggregate = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R78 aggregates fresh R68 page/home/runtime evidence, fresh R75 operations/log surface evidence, and source-level human-surface markers.'
    screenshotEvidenceBoundary = 'Screenshots prove visible hierarchy, overflow, clipping, and visible copy only. API/readback assertions, permission positives/negatives, residual matrix, and explicit user signoff prove behavior and acceptance.'
    pageVisualDesigner = @{
        source = 'R68 fresh rerun'
        browserAuditPath = $R68.browserAuditPath
        pageDesignerBrowserResults = $R68.pageDesignerEvidence.browserResultCount
        pageRuntimeBrowserResults = $R68.pageRuntimeEvidence.browserResultCount
        overflow = [int]$R68.pageDesignerEvidence.browserOverflowCount + [int]$R68.pageRuntimeEvidence.browserOverflowCount
        blockers = [int]$R68.pageRuntimeEvidence.browserBlockerCount
    }
    advancedOperationsLogs = @{
        source = 'R75 fresh rerun'
        browserAuditPath = $R75.browserAuditPath
        browserResultCount = $R75.browserResultCount
        browserOverflowCount = $R75.browserOverflowCount
        browserBlockerCount = $R75.browserBlockerCount
    }
    productSurfaceSource = @{
        platformConfigSummary = $true
        platformLogHumanActionLabels = $true
        runtimePublishVersionMarker = $true
    }
}
$browserAggregate | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'R78_ACCEPTED_AS_PRODUCT_SURFACE_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-078'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    requirementRows = @('REQ-5.8', 'REQ-6.1', 'REQ-6.3', 'REQ-6.8', 'REQ-9')
    flowIds = @('A1-A4', 'P1-P2', 'S1-S2', 'C1-C3', 'B1-B2', 'AI1-AI3', 'O1-O4')
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    pageVisualDesignerEvidence = @{
        status = $R68.status
        pageDesignerBrowserResults = $R68.pageDesignerEvidence.browserResultCount
        pageRuntimeBrowserResults = $R68.pageRuntimeEvidence.browserResultCount
        pagePublishedVersion = $R68.pageRuntimeEvidence.pagePublishedVersion
        hiddenFieldLeaked = $R68.pageRuntimeEvidence.hiddenFieldLeaked
        hiddenComponentLeaked = $R68.pageRuntimeEvidence.hiddenComponentLeaked
        forbiddenHomeWriteStatus = $R68.pageRuntimeEvidence.forbiddenHomeWriteStatus
        forbiddenPageWriteStatus = $R68.pageRuntimeEvidence.forbiddenPageWriteStatus
    }
    advancedBoundaryEvidence = @{
        status = $R75.status
        browserResultCount = $R75.browserResultCount
        browserOverflowCount = $R75.browserOverflowCount
        browserBlockerCount = $R75.browserBlockerCount
        platformAuditReadbackCount = $R75.platformAuditReadbackCount
        systemAuditReadbackCount = $R75.systemAuditReadbackCount
    }
    coverageBoundary = @{
        source = 'R77'
        notClosedCount = $R77.coverage.notClosedCount
        promotedToProven = $R77.coverage.promotedToProven
    }
    browserAuditPath = 'docs/evidence/recovery/screenshots/r78-frc1-product-surface-human-acceptance-residual/product-surface-human-acceptance-browser-audit.json'
    accepted = $true
}
$result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-078 / R78 FRC-1 Product Surface Human Acceptance Residual Closure

Status: PASS as product-surface engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9
- Static audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Page/home/runtime: R68 status=$($R68.status), pagePublishedVersion=$($R68.pageRuntimeEvidence.pagePublishedVersion), designerBrowser=$($R68.pageDesignerEvidence.browserResultCount), runtimeBrowser=$($R68.pageRuntimeEvidence.browserResultCount)
- Advanced operations/logs boundary: R75 status=$($R75.status), browser=$($R75.browserResultCount), overflow=$($R75.browserOverflowCount), blockers=$($R75.browserBlockerCount)
- Source-level product surface improvements: platform config summary/compact records, platform log human action labels, trace short chips, runtime publishVersion marker
- Browser aggregate: $($result.browserAuditPath)

This does not close final product acceptance. It tightens the product surface evidence and keeps `gates.user_script_passed=false`; final acceptance still requires broader coverage promotion and explicit user verification.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 60

if ($NoFailExit) {
    exit 0
}
