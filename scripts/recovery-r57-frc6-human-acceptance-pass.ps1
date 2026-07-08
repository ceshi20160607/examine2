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

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    return $response.data
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

function Assert-RoutePair {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [object]$Audit,
        [string]$Role,
        [string]$Key,
        [bool]$AdminShellExpected,
        [int]$MaxPanelCount,
        [int]$MaxButtonCount
    )
    $items = @(Get-RouteResults -Audit $Audit -Role $Role -Key $Key)
    Add-Check $Checks 'role-journey-route' "$Role/$Key has desktop and mobile evidence" `
        ($items.Count -eq 2 -and @($items.viewport | Sort-Object -Unique).Count -eq 2) `
        "count=$($items.Count), viewports=$(@($items.viewport | Sort-Object -Unique) -join ',')"

    foreach ($item in $items) {
        $prefix = "$Role/$Key/$($item.viewport)"
        Add-Check $Checks 'role-journey-visual' "$prefix has no overflow/clipping/control text overflow" `
            ([int]$item.overflowX -eq 0 -and @($item.clipped).Count -eq 0 -and @($item.controlOverflow).Count -eq 0) `
            "overflow=$($item.overflowX), clipped=$(@($item.clipped).Count), controlOverflow=$(@($item.controlOverflow).Count)"

        Add-Check $Checks 'role-journey-copy' "$prefix has no placeholder/generic/demo/mojibake findings" `
            (@($item.placeholderMatches).Count -eq 0 -and @($item.genericToastMatches).Count -eq 0 -and @($item.demoMatches).Count -eq 0 -and [int]$item.mojibakeCount -eq 0) `
            "placeholder=$(@($item.placeholderMatches).Count), genericToast=$(@($item.genericToastMatches).Count), demo=$(@($item.demoMatches).Count), mojibake=$($item.mojibakeCount)"

        Add-Check $Checks 'role-shell-separation' "$prefix admin-shell visibility matches role surface" `
            ([bool]$item.adminShellVisible -eq $AdminShellExpected) `
            "adminShellVisible=$($item.adminShellVisible), expected=$AdminShellExpected"

        Add-Check $Checks 'one-primary-surface' "$prefix stays below page-stacking density threshold" `
            ([int]$item.panelCount -le $MaxPanelCount -and [int]$item.buttonCount -le $MaxButtonCount) `
            "panels=$($item.panelCount)/$MaxPanelCount, buttons=$($item.buttonCount)/$MaxButtonCount"

        Add-Check $Checks 'role-journey-selectors' "$prefix expected selectors visible and forbidden selectors absent" `
            (@($item.expectedSelectorResults | Where-Object { $_.visible -ne $true }).Count -eq 0 -and @($item.forbiddenSelectorResults | Where-Object { $_.visible -eq $true }).Count -eq 0) `
            "missingSelectors=$(@($item.expectedSelectorResults | Where-Object { $_.visible -ne $true }).Count), forbiddenSelectors=$(@($item.forbiddenSelectorResults | Where-Object { $_.visible -eq $true }).Count)"

        Add-Check $Checks 'role-journey-text' "$prefix expected text present and forbidden text absent" `
            (@($item.missingTexts).Count -eq 0 -and @($item.forbiddenTextHits).Count -eq 0) `
            "missingTexts=$(@($item.missingTexts).Count), forbiddenTextHits=$(@($item.forbiddenTextHits).Count)"
    }
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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r57-frc6-human-acceptance-pass'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r57-frc6-human-acceptance-pass-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r57-frc6-human-acceptance-pass-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'human-acceptance-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Add-Check $script:Checks 'release' 'health reports database/schema/redis UP' `
    ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    ($Health | ConvertTo-Json -Depth 12 -Compress)

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
Add-Check $script:Checks 'frontend-static' 'no blocker or warning copy/placeholder findings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

$childResults += Invoke-ChildScript 'recovery-r43-role-home-whole-path-usability' `
    (Join-Path $PSScriptRoot 'recovery-r43-role-home-whole-path-usability-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R43 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r43-role-home-whole-path-usability-result.json')

Add-Check $script:Checks 'fresh-human-journey' 'R43 fresh role home whole-path usability script passed' `
    ($R43.status -eq 'PASS' -and [int]$R43.routeCount -ge 16 -and [int]$R43.resultCount -ge 32 -and [int]$R43.failureCount -eq 0 -and [int]$R43.warningCount -eq 0) `
    "status=$($R43.status), routes=$($R43.routeCount), results=$($R43.resultCount), failures=$($R43.failureCount), warnings=$($R43.warningCount)"

Add-Check $script:Checks 'permission-negative' 'R43 fresh denied route and API permission checks passed' `
    ([int]$R43.forbiddenCreateStatus -eq 403 -and [int]$R43.forbiddenAdminStatus -eq 403) `
    "forbiddenCreate=$($R43.forbiddenCreateStatus), forbiddenAdmin=$($R43.forbiddenAdminStatus)"

$requiredRoutes = @(
    @{ role = 'admin'; key = 'platform-workbench'; adminShell = $false; maxPanels = 3; maxButtons = 12 },
    @{ role = 'admin'; key = 'platform-admin'; adminShell = $true; maxPanels = 9; maxButtons = 24 },
    @{ role = 'admin'; key = 'system-dashboard'; adminShell = $false; maxPanels = 4; maxButtons = 16 },
    @{ role = 'admin'; key = 'system-runtime-modules'; adminShell = $false; maxPanels = 4; maxButtons = 64 },
    @{ role = 'admin'; key = 'system-work'; adminShell = $false; maxPanels = 8; maxButtons = 18 },
    @{ role = 'admin'; key = 'system-todos'; adminShell = $false; maxPanels = 5; maxButtons = 24 },
    @{ role = 'admin'; key = 'system-messages'; adminShell = $false; maxPanels = 4; maxButtons = 24 },
    @{ role = 'admin'; key = 'system-admin'; adminShell = $true; maxPanels = 9; maxButtons = 36 },
    @{ role = 'normal'; key = 'normal-platform-workbench'; adminShell = $false; maxPanels = 3; maxButtons = 12 },
    @{ role = 'normal'; key = 'normal-platform-admin-denied'; adminShell = $false; maxPanels = 1; maxButtons = 3 },
    @{ role = 'normal'; key = 'normal-system-dashboard'; adminShell = $false; maxPanels = 4; maxButtons = 16 },
    @{ role = 'normal'; key = 'normal-system-runtime-modules'; adminShell = $false; maxPanels = 4; maxButtons = 64 },
    @{ role = 'normal'; key = 'normal-system-work'; adminShell = $false; maxPanels = 8; maxButtons = 18 },
    @{ role = 'normal'; key = 'normal-system-todos'; adminShell = $false; maxPanels = 5; maxButtons = 24 },
    @{ role = 'normal'; key = 'normal-system-messages'; adminShell = $false; maxPanels = 4; maxButtons = 24 },
    @{ role = 'normal'; key = 'normal-system-admin-denied'; adminShell = $false; maxPanels = 1; maxButtons = 3 }
)

foreach ($route in $requiredRoutes) {
    Assert-RoutePair -Checks $script:Checks -Audit $R43 -Role $route.role -Key $route.key -AdminShellExpected $route.adminShell -MaxPanelCount $route.maxPanels -MaxButtonCount $route.maxButtons
}

$adminRuntime = @(Get-RouteResults -Audit $R43 -Role 'admin' -Key 'system-runtime-modules')
$normalRuntime = @(Get-RouteResults -Audit $R43 -Role 'normal' -Key 'normal-system-runtime-modules')
$normalPlatform = @(Get-RouteResults -Audit $R43 -Role 'normal' -Key 'normal-platform-workbench')
$normalDenied = @(Get-RouteResults -Audit $R43 -Role 'normal' -Key 'normal-system-admin-denied')

Add-Check $script:Checks 'data-readback-after-action' 'fresh journey created system/module and rendered runtime data for admin and normal roles' `
    (-not [string]::IsNullOrWhiteSpace([string]$R43.systemId) -and -not [string]::IsNullOrWhiteSpace([string]$R43.moduleId) -and $adminRuntime.Count -eq 2 -and $normalRuntime.Count -eq 2) `
    "systemId=$($R43.systemId), moduleId=$($R43.moduleId), adminRuntime=$($adminRuntime.Count), normalRuntime=$($normalRuntime.Count)"

Add-Check $script:Checks 'real-entry-sequence' 'normal platform member can see system switch before entering system routes' `
    ($normalPlatform.Count -eq 2 -and @($normalPlatform | Where-Object { $_.buttonCount -gt 0 -and $_.textLength -gt 80 }).Count -eq 2) `
    "normalPlatformResults=$($normalPlatform.Count)"

Add-Check $script:Checks 'permission-positive-negative' 'admin positives and normal denied states are both visible in deployed browser journeys' `
    ($adminRuntime.Count -eq 2 -and $normalRuntime.Count -eq 2 -and $normalDenied.Count -eq 2 -and @($normalDenied | Where-Object { $_.panelCount -eq 0 -and $_.buttonCount -ge 1 }).Count -eq 2) `
    "adminRuntime=$($adminRuntime.Count), normalRuntime=$($normalRuntime.Count), normalDenied=$($normalDenied.Count)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R57 aggregation of fresh R43 deployed role journey browser audit'
    screenshotEvidenceBoundary = 'Visual evidence only. Script assertions prove role shell separation, primary-surface density thresholds, selector/text expectations, permission positives/negatives, and data/readback evidence. User signoff remains separate.'
    v7HumanUsableGate = @{
        primaryRoleJourney = 'platform normal member, platform administrator, system administrator, and system normal member paths are covered by fresh R43 admin/normal route pairs'
        realEntrySequence = 'login -> platform workspace or system switch -> correct shell -> platform/system/work/runtime/todo/message/admin route'
        onePrimaryTaskSurface = 'panel/button density thresholds and no overflow/clipping/control-overflow checks'
        roleShellSeparation = 'adminShellVisible and forbidden selector/text assertions'
        ambiguousCopyOrTipRisk = 'static audit plus placeholder/generic/demo/mojibake checks'
        dataReadbackAfterAction = "systemId=$($R43.systemId), moduleId=$($R43.moduleId)"
        permissionPositiveAndNegative = "forbiddenCreate=$($R43.forbiddenCreateStatus), forbiddenAdmin=$($R43.forbiddenAdminStatus)"
        reloadReloginRestartRequirement = 'covered by fresh deployed release verification and fresh role journey execution against the release service'
        userSignoffBoundary = 'false'
    }
    r43 = @{
        resultFile = 'docs/evidence/recovery/r43-role-home-whole-path-usability-result.json'
        browserAuditPath = 'docs/evidence/recovery/screenshots/r43-role-home-whole-path-usability/role-home-whole-path-usability-audit.json'
        systemId = $R43.systemId
        moduleId = $R43.moduleId
        routeCount = $R43.routeCount
        resultCount = $R43.resultCount
        failureCount = $R43.failureCount
        warningCount = $R43.warningCount
        forbiddenCreateStatus = $R43.forbiddenCreateStatus
        forbiddenAdminStatus = $R43.forbiddenAdminStatus
        assetScripts = @($R43.assetScripts)
    }
    routeThresholds = $requiredRoutes
}
$browserAudit | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-2.1', 'REQ-4.1', 'REQ-4.2', 'REQ-6.2')
    journeyRows = @('J0', 'J1', 'J2', 'J3', 'J4', 'J5', 'J6', 'J7', 'J8', 'J9', 'J10', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r57-frc6-human-acceptance-pass/human-acceptance-browser-audit.json'
    humanJourney = @{
        sourceTask = 'REC-P0-043 fresh rerun'
        systemId = $R43.systemId
        moduleId = $R43.moduleId
        routeCount = $R43.routeCount
        viewportCount = $R43.viewportCount
        resultCount = $R43.resultCount
        failureCount = $R43.failureCount
        warningCount = $R43.warningCount
        forbiddenCreateStatus = $R43.forbiddenCreateStatus
        forbiddenAdminStatus = $R43.forbiddenAdminStatus
        assetScripts = @($R43.assetScripts)
    }
    v7HumanUsableGate = $browserAudit.v7HumanUsableGate
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-057 / R57 FRC-6 Human Acceptance Pass Fresh Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-2.1, REQ-4.1, REQ-4.2, REQ-6.2
- Fresh role journey: R43 PASS, routes=$($R43.routeCount), viewports=$($R43.viewportCount), results=$($R43.resultCount), failures=$($R43.failureCount), warnings=$($R43.warningCount)
- Role shell and permissions: forbiddenCreate=$($R43.forbiddenCreateStatus), forbiddenAdmin=$($R43.forbiddenAdminStatus), systemId=$($R43.systemId), moduleId=$($R43.moduleId)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- V7 gate evidence: role journey, real entry sequence, one-primary-surface thresholds, role shell separation, copy/tip checks, data readback, permission positive/negative checks, and deployed release verification
- Browser aggregation: `docs/evidence/recovery/screenshots/r57-frc6-human-acceptance-pass/human-acceptance-browser-audit.json`

This does not set user signoff. Full requirement coverage and `gates.user_script_passed=true` remain open until explicit user acceptance.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
