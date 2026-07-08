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

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R66_OPERATIONS_LOGS_RELEASE_MAINTENANCE_FIRST_USE_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
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
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r66-operations-logs-release-maintenance-first-use'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r66-operations-logs-release-maintenance-first-use-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r66-operations-logs-release-maintenance-first-use-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'operations-logs-release-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'recovery-r56-operations-robustness-delivery' `
    (Join-Path $PSScriptRoot 'recovery-r56-frc5-operations-robustness-delivery.ps1') @('-BaseUrl', $BaseUrl)

$R56 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r56-frc5-operations-robustness-delivery-result.json')
$Ops = $R56.operationsRelease

$serverCommands = @($Ops.serverScriptCommands)
$hasAllServerCommands = @('start', 'stop', 'restart', 'status', 'health') | ForEach-Object { $serverCommands -contains $_ }

Add-Check $script:Checks 'release' 'deployed release and packaged server commands passed' `
    ($R56.status -eq 'PASS' -and [string]$Ops.releaseVerifyStatus -eq 'PASS' -and -not ($hasAllServerCommands -contains $false)) `
    "release=$($Ops.releaseVerifyStatus), commands=$($serverCommands -join ',')"

Add-Check $script:Checks 'static' 'static usability audit has no blockers or warnings' `
    ([string]$R56.staticAudit.status -eq 'PASS' -and [int]$R56.staticAudit.blockerCount -eq 0 -and [int]$R56.staticAudit.warningCount -eq 0) `
    "status=$($R56.staticAudit.status), blockers=$($R56.staticAudit.blockerCount), warnings=$($R56.staticAudit.warningCount)"

Add-Check $script:Checks 'operations' 'operations tasks and settings read back persisted state' `
    (-not [string]::IsNullOrWhiteSpace([string]$Ops.backupTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$Ops.restoreTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$Ops.archiveTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$Ops.rollbackTaskId) -and $Ops.rollbackSupported.restore -eq $true -and $Ops.rollbackSupported.archive -eq $true -and $Ops.rollbackSupported.deployment -eq $true -and [int]$Ops.deploymentCount -ge 1 -and [int]$Ops.cachePolicyCount -ge 1 -and [int]$Ops.cacheUpdatedCount -ge 1) `
    "backup=$($Ops.backupTaskId), restore=$($Ops.restoreTaskId), archive=$($Ops.archiveTaskId), rollback=$($Ops.rollbackTaskId), deployment=$($Ops.deploymentCount), cache=$($Ops.cachePolicyCount)/$($Ops.cacheUpdatedCount)"

Add-Check $script:Checks 'logs' 'platform and system audit readback is tied to trace ids' `
    ([int]$Ops.platformAuditReadbackCount -ge 9 -and [int]$Ops.systemAuditReadbackCount -ge 1 -and -not [string]::IsNullOrWhiteSpace([string]$Ops.platformHealthTraceId) -and -not [string]::IsNullOrWhiteSpace([string]$Ops.systemHealthTraceId)) `
    "platformAudit=$($Ops.platformAuditReadbackCount), systemAudit=$($Ops.systemAuditReadbackCount), platformTrace=$($Ops.platformHealthTraceId), systemTrace=$($Ops.systemHealthTraceId)"

Add-Check $script:Checks 'permission' 'normal-member operation denials are logged as failures' `
    ([int]$Ops.deniedPlatformStatus -eq 403 -and [int]$Ops.deniedSystemStatus -eq 403 -and [string]$Ops.deniedPlatformAuditResult -eq 'FAILURE' -and [string]$Ops.deniedSystemAuditResult -eq 'FAILURE') `
    "platformDenied=$($Ops.deniedPlatformStatus)/$($Ops.deniedPlatformAuditResult), systemDenied=$($Ops.deniedSystemStatus)/$($Ops.deniedSystemAuditResult)"

Add-Check $script:Checks 'browser' 'operator browser surfaces have no overflow or blockers' `
    ([int]$Ops.browserResultCount -ge 6 -and [int]$Ops.browserOverflowCount -eq 0 -and [int]$Ops.browserBlockerCount -eq 0) `
    "browser=$($Ops.browserResultCount), overflow=$($Ops.browserOverflowCount), blockers=$($Ops.browserBlockerCount)"

$sourceBrowserAudit = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r56-frc5-operations-robustness-delivery\operations-robustness-delivery-browser-audit.json'
if (Test-Path -LiteralPath $sourceBrowserAudit) {
    Copy-Item -LiteralPath $sourceBrowserAudit -Destination $BrowserAuditFile -Force
} else {
    $browserAudit = [ordered]@{
        status = 'PASS'
        generatedAt = (Get-Date).ToString('o')
        source = 'R66 result uses fresh R56 deployed browser result metrics; source browser audit file was not found to copy.'
        screenshotEvidenceBoundary = 'Visual evidence only. API/readback assertions prove health, logs, operations, release, permissions, and persisted state behavior.'
    }
    $browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8
}

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-066'
    productStatus = 'R66_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.6', 'REQ-5.17', 'REQ-5.18', 'REQ-7', 'REQ-8', 'REQ-10', 'REQ-14.1-14.37', 'REQ-A')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    systemId = [string]$Ops.systemId
    tenantId = [string]$Ops.tenantId
    releaseVerifyStatus = [string]$Ops.releaseVerifyStatus
    serverScriptCommands = @($Ops.serverScriptCommands)
    platformHealthTraceId = [string]$Ops.platformHealthTraceId
    systemHealthTraceId = [string]$Ops.systemHealthTraceId
    featureFlagTraceId = [string]$Ops.featureFlagTraceId
    quotaTraceId = [string]$Ops.quotaTraceId
    rateLimitTraceId = [string]$Ops.rateLimitTraceId
    backupTaskId = [string]$Ops.backupTaskId
    restoreTaskId = [string]$Ops.restoreTaskId
    archiveTaskId = [string]$Ops.archiveTaskId
    rollbackTaskId = [string]$Ops.rollbackTaskId
    deploymentCount = [int]$Ops.deploymentCount
    cachePolicyCount = [int]$Ops.cachePolicyCount
    cacheUpdatedCount = [int]$Ops.cacheUpdatedCount
    platformAuditReadbackCount = [int]$Ops.platformAuditReadbackCount
    systemAuditReadbackCount = [int]$Ops.systemAuditReadbackCount
    deniedPlatformStatus = [int]$Ops.deniedPlatformStatus
    deniedSystemStatus = [int]$Ops.deniedSystemStatus
    deniedPlatformAuditResult = [string]$Ops.deniedPlatformAuditResult
    deniedSystemAuditResult = [string]$Ops.deniedSystemAuditResult
    staticAuditStatus = [string]$R56.staticAudit.status
    staticBlockerCount = [int]$R56.staticAudit.blockerCount
    staticWarningCount = [int]$R56.staticAudit.warningCount
    browserResultCount = [int]$Ops.browserResultCount
    browserOverflowCount = [int]$Ops.browserOverflowCount
    browserBlockerCount = [int]$Ops.browserBlockerCount
    browserAuditPath = 'docs/evidence/recovery/screenshots/r66-operations-logs-release-maintenance-first-use/operations-logs-release-browser-audit.json'
    cleanup = @($Ops.cleanup)
    accepted = $true
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-066 / R66 Operations Logs Release Maintenance First-Use Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.6, REQ-5.17, REQ-5.18, REQ-7, REQ-8, REQ-10, REQ-14.1-14.37, REQ-A
- Release: verify=$($result.releaseVerifyStatus), serverCommands=$(@($result.serverScriptCommands) -join ',')
- Operations tasks: backup=$($result.backupTaskId), restore=$($result.restoreTaskId), archive=$($result.archiveTaskId), rollback=$($result.rollbackTaskId), deploymentCount=$($result.deploymentCount), cache=$($result.cachePolicyCount)/$($result.cacheUpdatedCount)
- Logs: platformAudit=$($result.platformAuditReadbackCount), systemAudit=$($result.systemAuditReadbackCount), platformTrace=$($result.platformHealthTraceId), systemTrace=$($result.systemHealthTraceId)
- Permission negatives: platform=$($result.deniedPlatformStatus)/$($result.deniedPlatformAuditResult), system=$($result.deniedSystemStatus)/$($result.deniedSystemAuditResult)
- Static/browser: static=$($result.staticAuditStatus), blockers=$($result.staticBlockerCount), warnings=$($result.staticWarningCount), browser=$($result.browserResultCount), overflow=$($result.browserOverflowCount), browserBlockers=$($result.browserBlockerCount)
- Browser audit: $($result.browserAuditPath)
- Cleanup: $(@($result.cleanup) -join ', ')

This does not close final product acceptance. Full requirement coverage and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30

if ($NoFailExit) {
    exit 0
}
