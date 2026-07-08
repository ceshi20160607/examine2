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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r56-frc5-operations-robustness-delivery'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r56-frc5-operations-robustness-delivery-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r56-frc5-operations-robustness-delivery-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'operations-robustness-delivery-browser-audit.json'
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

$childResults += Invoke-ChildScript 'recovery-r50-operations-release-log' `
    (Join-Path $PSScriptRoot 'recovery-r50-operations-release-log-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R50 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r50-operations-release-log-result.json')

$serverCommands = @($R50.serverScriptCommands)
$hasAllServerCommands = @('start', 'stop', 'restart', 'status', 'health') | ForEach-Object { $serverCommands -contains $_ }
Add-Check $script:Checks 'release-delivery' 'R50 fresh release and server script command evidence passed' `
    ($R50.status -eq 'PASS' -and [string]$R50.releaseVerifyStatus -eq 'PASS' -and -not ($hasAllServerCommands -contains $false)) `
    "release=$($R50.releaseVerifyStatus), commands=$($serverCommands -join ',')"

Add-Check $script:Checks 'operations-readback' 'R50 fresh operations tasks and settings read back persisted state' `
    (-not [string]::IsNullOrWhiteSpace([string]$R50.backupTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$R50.restoreTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$R50.archiveTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$R50.rollbackTaskId) -and $R50.rollbackSupported.restore -eq $true -and $R50.rollbackSupported.archive -eq $true -and $R50.rollbackSupported.deployment -eq $true -and [int]$R50.deploymentCount -ge 1 -and [int]$R50.cachePolicyCount -ge 1 -and [int]$R50.cacheUpdatedCount -ge 1) `
    "backup=$($R50.backupTaskId), restore=$($R50.restoreTaskId), archive=$($R50.archiveTaskId), rollback=$($R50.rollbackTaskId), deploymentCount=$($R50.deploymentCount), cache=$($R50.cachePolicyCount)/$($R50.cacheUpdatedCount)"

Add-Check $script:Checks 'logs-audit' 'R50 fresh platform/system audit search and detail readback passed' `
    ([int]$R50.platformAuditReadbackCount -ge 9 -and [int]$R50.systemAuditReadbackCount -ge 1 -and -not [string]::IsNullOrWhiteSpace([string]$R50.platformHealthTraceId) -and -not [string]::IsNullOrWhiteSpace([string]$R50.systemHealthTraceId)) `
    "platformAudit=$($R50.platformAuditReadbackCount), systemAudit=$($R50.systemAuditReadbackCount), platformTrace=$($R50.platformHealthTraceId), systemTrace=$($R50.systemHealthTraceId)"

Add-Check $script:Checks 'permission-negative' 'R50 fresh normal-member operation denials and failure audit rows passed' `
    ([int]$R50.deniedPlatformStatus -eq 403 -and [int]$R50.deniedSystemStatus -eq 403 -and [string]$R50.deniedPlatformAuditResult -eq 'FAILURE' -and [string]$R50.deniedSystemAuditResult -eq 'FAILURE') `
    "platformDenied=$($R50.deniedPlatformStatus)/$($R50.deniedPlatformAuditResult), systemDenied=$($R50.deniedSystemStatus)/$($R50.deniedSystemAuditResult)"

Add-Check $script:Checks 'browser-ops' 'R50 fresh operations and log browser containment passed' `
    ([int]$R50.browserResultCount -ge 6 -and [int]$R50.browserOverflowCount -eq 0 -and [int]$R50.browserBlockerCount -eq 0) `
    "browser=$($R50.browserResultCount), overflow=$($R50.browserOverflowCount), blockers=$($R50.browserBlockerCount)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R56 aggregation of fresh R50 deployed operations browser audit'
    screenshotEvidenceBoundary = 'Visual evidence only. API/readback assertions prove operations safety, release integrity, audit persistence, permission denials, and persisted state behavior.'
    r50 = @{
        resultFile = 'docs/evidence/recovery/r50-operations-release-log-result.json'
        browserAuditPath = $R50.browserAuditPath
        browserResultCount = $R50.browserResultCount
        browserOverflowCount = $R50.browserOverflowCount
        browserBlockerCount = $R50.browserBlockerCount
    }
}
$browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.6', 'REQ-5.17', 'REQ-5.18', 'REQ-7', 'REQ-8', 'REQ-10', 'REQ-14.1-14.37', 'REQ-A')
    journeyRows = @('J7', 'J8', 'J9', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r56-frc5-operations-robustness-delivery/operations-robustness-delivery-browser-audit.json'
    operationsRelease = @{
        systemId = $R50.systemId
        tenantId = $R50.tenantId
        releaseVerifyStatus = $R50.releaseVerifyStatus
        serverScriptCommands = @($R50.serverScriptCommands)
        platformHealthTraceId = $R50.platformHealthTraceId
        systemHealthTraceId = $R50.systemHealthTraceId
        featureFlagTraceId = $R50.featureFlagTraceId
        quotaTraceId = $R50.quotaTraceId
        rateLimitTraceId = $R50.rateLimitTraceId
        backupTaskId = $R50.backupTaskId
        restoreTaskId = $R50.restoreTaskId
        archiveTaskId = $R50.archiveTaskId
        rollbackTaskId = $R50.rollbackTaskId
        rollbackSupported = $R50.rollbackSupported
        deploymentCount = $R50.deploymentCount
        cachePolicyCount = $R50.cachePolicyCount
        cacheUpdatedCount = $R50.cacheUpdatedCount
        platformAuditReadbackCount = $R50.platformAuditReadbackCount
        systemAuditReadbackCount = $R50.systemAuditReadbackCount
        deniedPlatformStatus = $R50.deniedPlatformStatus
        deniedSystemStatus = $R50.deniedSystemStatus
        deniedPlatformAuditResult = $R50.deniedPlatformAuditResult
        deniedSystemAuditResult = $R50.deniedSystemAuditResult
        browserResultCount = $R50.browserResultCount
        browserOverflowCount = $R50.browserOverflowCount
        browserBlockerCount = $R50.browserBlockerCount
        cleanup = @($R50.cleanup)
    }
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-056 / R56 FRC-5 Operations Robustness Delivery Fresh Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.6, REQ-5.17, REQ-5.18, REQ-7, REQ-8, REQ-10, REQ-14.1-14.37, REQ-A
- Fresh operations/release/log evidence: R50 PASS, releaseVerify=$($R50.releaseVerifyStatus), commands=$($serverCommands -join ','), platformAudit=$($R50.platformAuditReadbackCount), systemAudit=$($R50.systemAuditReadbackCount), deniedPlatform=$($R50.deniedPlatformStatus)/$($R50.deniedPlatformAuditResult), deniedSystem=$($R50.deniedSystemStatus)/$($R50.deniedSystemAuditResult)
- Operations tasks/readback: backup=$($R50.backupTaskId), restore=$($R50.restoreTaskId), archive=$($R50.archiveTaskId), rollback=$($R50.rollbackTaskId), deploymentCount=$($R50.deploymentCount), cachePolicy=$($R50.cachePolicyCount), cacheUpdated=$($R50.cacheUpdatedCount)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Browser aggregation: `docs/evidence/recovery/screenshots/r56-frc5-operations-robustness-delivery/operations-robustness-delivery-browser-audit.json`

This does not close final product acceptance. Full operations breadth, every remaining launch-rule item, architecture/robustness/delivery conformance, full requirement coverage, and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
