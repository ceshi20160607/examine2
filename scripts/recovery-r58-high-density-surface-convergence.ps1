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

function Get-RouteResults {
    param(
        [object]$Audit,
        [string]$Role,
        [string]$Key
    )
    return @($Audit.results | Where-Object { $_.role -eq $Role -and $_.key -eq $Key })
}

trap {
    if ($script:FailureResultFile) {
        $failure = [ordered]@{
            status = 'FAIL'
            productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
            generatedAt = (Get-Date).ToString('o')
            baseUrl = $BaseUrl
            error = $_.Exception.Message
            checks = @($script:Checks.ToArray())
        }
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:FailureResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r58-high-density-surface-convergence'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r58-high-density-surface-convergence-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r58-high-density-surface-convergence-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'high-density-surface-convergence-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'recovery-r57-human-acceptance' (Join-Path $PSScriptRoot 'recovery-r57-frc6-human-acceptance-pass.ps1') @('-BaseUrl', $BaseUrl)

$R57 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r57-frc6-human-acceptance-pass-result.json')
$R43 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r43-role-home-whole-path-usability-result.json')
$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')

Add-Check $script:Checks 'release' 'latest deployed frontend uses the high-density convergence asset' `
    ($R57.status -eq 'PASS' -and @($R57.humanJourney.assetScripts | Where-Object { $_ -match 'index-BnZhqLS7\.js$' }).Count -ge 1) `
    "status=$($R57.status), assets=$(@($R57.humanJourney.assetScripts) -join ',')"

Add-Check $script:Checks 'static-usability' 'static usability audit still has no blockers or warnings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

$adminRuntime = @(Get-RouteResults -Audit $R43 -Role 'admin' -Key 'system-runtime-modules')
$normalRuntime = @(Get-RouteResults -Audit $R43 -Role 'normal' -Key 'normal-system-runtime-modules')
$workRoutes = @(
    @(Get-RouteResults -Audit $R43 -Role 'admin' -Key 'system-work'),
    @(Get-RouteResults -Audit $R43 -Role 'normal' -Key 'normal-system-work')
) | ForEach-Object { $_ }

$maxRuntimeButtons = (@($adminRuntime + $normalRuntime) | Measure-Object -Property buttonCount -Maximum).Maximum
$maxWorkPanels = (@($workRoutes) | Measure-Object -Property panelCount -Maximum).Maximum
$maxWorkButtons = (@($workRoutes) | Measure-Object -Property buttonCount -Maximum).Maximum

Add-Check $script:Checks 'runtime-density' 'runtime module list actions are converged into menus' `
    ([int]$maxRuntimeButtons -le 30 -and @($adminRuntime + $normalRuntime).Count -eq 4) `
    "maxRuntimeButtons=$maxRuntimeButtons, routeCount=$(@($adminRuntime + $normalRuntime).Count)"

Add-Check $script:Checks 'work-density' 'work management panels and actions are reduced without losing route coverage' `
    ([int]$maxWorkPanels -le 7 -and [int]$maxWorkButtons -le 14 -and @($workRoutes).Count -eq 4) `
    "maxWorkPanels=$maxWorkPanels, maxWorkButtons=$maxWorkButtons, routeCount=$(@($workRoutes).Count)"

Add-Check $script:Checks 'role-safety' 'R57 role journey still passes after density convergence' `
    ($R57.status -eq 'PASS' -and [int]$R57.humanJourney.failureCount -eq 0 -and [int]$R57.humanJourney.warningCount -eq 0 -and [int]$R57.humanJourney.forbiddenCreateStatus -eq 403 -and [int]$R57.humanJourney.forbiddenAdminStatus -eq 403) `
    "failures=$($R57.humanJourney.failureCount), warnings=$($R57.humanJourney.warningCount), forbiddenCreate=$($R57.humanJourney.forbiddenCreateStatus), forbiddenAdmin=$($R57.humanJourney.forbiddenAdminStatus)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R58 high-density convergence aggregation from fresh R57/R43 deployed browser audit'
    screenshotEvidenceBoundary = 'Visual evidence only. Density assertions use deployed browser route metrics; final user acceptance remains separate.'
    assets = @($R57.humanJourney.assetScripts)
    density = @{
        runtimeModuleMaxButtonCount = $maxRuntimeButtons
        workMaxPanelCount = $maxWorkPanels
        workMaxButtonCount = $maxWorkButtons
    }
    r57 = @{
        resultFile = 'docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json'
        browserAuditFile = $R57.browserAuditFile
        systemId = $R57.humanJourney.systemId
        moduleId = $R57.humanJourney.moduleId
    }
}
$browserAudit | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-2.1', 'REQ-4.1', 'REQ-4.2', 'REQ-4.4', 'REQ-6.2', 'REQ-6.4')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    browserAuditFile = 'docs/evidence/recovery/screenshots/r58-high-density-surface-convergence/high-density-surface-convergence-browser-audit.json'
    density = $browserAudit.density
    assetScripts = @($R57.humanJourney.assetScripts)
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-058 / R58 High-Density Surface Convergence

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Deployed asset: $(@($R57.humanJourney.assetScripts) -join ', ')
- Runtime module visible action density: max button count $maxRuntimeButtons after row actions and import/export/columns were converged into menus
- Work management density: max panel count $maxWorkPanels, max button count $maxWorkButtons after dashboard summary and task create/view controls were converged
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- R57 role journey remains PASS with forbidden create/admin HTTP 403.

This is product-usability engineering evidence only. It does not set user signoff or close all partial requirement rows.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
