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
        productStatus = 'R65_OPENAPI_ASSISTANT_EXTERNAL_SERVICE_FIRST_USE_FAILED'
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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r65-openapi-assistant-external-service-first-use'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r65-openapi-assistant-external-service-first-use-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r65-openapi-assistant-external-service-first-use-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'openapi-assistant-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'recovery-r49-openapi-assistant-integration' `
    (Join-Path $PSScriptRoot 'recovery-r49-openapi-assistant-integration-smoke.ps1') @('-BaseUrl', $BaseUrl)

$R49 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r49-openapi-assistant-integration-result.json')

Add-Check $script:Checks 'openapi' 'fresh scoped external API first-use passed' `
    ($R49.status -eq 'PASS' -and -not [string]::IsNullOrWhiteSpace([string]$R49.openApiAppId) -and -not [string]::IsNullOrWhiteSpace([string]$R49.openApiRecordId) -and [string]$R49.openApiSecretRefId -match '^sec_' -and [string]$R49.openApiRotateJobId -match '^srj_' -and [int]$R49.openApiSuccessLogCount -ge 5 -and [int]$R49.openApiFailedLogCount -ge 1) `
    "app=$($R49.openApiAppId), record=$($R49.openApiRecordId), secretRef=$($R49.openApiSecretRefId), rotateJob=$($R49.openApiRotateJobId), successLogs=$($R49.openApiSuccessLogCount), failedLogs=$($R49.openApiFailedLogCount)"

Add-Check $script:Checks 'openapi-permission' 'external API and normal-member denials are enforced' `
    ([int]$R49.readOnlyDeniedStatus -eq 403 -and [int]$R49.wrongSecretDeniedStatus -in @(401, 403) -and [int]$R49.normalOpenApiCreateStatus -eq 403 -and [int]$R49.normalPolicyCreateStatus -eq 403 -and [string]$R49.platformDeniedStatus -eq 'REJECTED_BY_SCOPE') `
    "readOnly=$($R49.readOnlyDeniedStatus), wrongSecret=$($R49.wrongSecretDeniedStatus), normalOpenApi=$($R49.normalOpenApiCreateStatus), normalPolicy=$($R49.normalPolicyCreateStatus), platformDenied=$($R49.platformDeniedStatus)"

Add-Check $script:Checks 'assistant' 'assistant preview confirm reject and work draft boundary passed' `
    ($R49.policyPublishPassed -eq $true -and @($R49.proposedConfirmationTypes).Count -ge 2 -and @($R49.proposedConfirmationTypes) -contains 'SYSTEM_AGENT_WRITE_CONFIRM' -and @($R49.proposedConfirmationTypes) -contains 'WORK_AGENT_DRAFT_CONFIRM' -and [string]$R49.writePreviewStatus -eq 'WAITING_HUMAN_CONFIRM' -and [string]$R49.writeConfirmedStatus -eq 'CONFIRMED' -and [string]$R49.writeRejectedStatus -eq 'REJECTED' -and [string]$R49.draftPreviewStatus -eq 'WAITING_HUMAN_CONFIRM' -and [string]$R49.draftConfirmedStatus -eq 'CONFIRMED') `
    "policyPublish=$($R49.policyPublishPassed), confirmations=$(@($R49.proposedConfirmationTypes) -join ','), write=$($R49.writePreviewStatus)/$($R49.writeConfirmedStatus)/$($R49.writeRejectedStatus), draft=$($R49.draftPreviewStatus)/$($R49.draftConfirmedStatus)"

Add-Check $script:Checks 'logs-browser' 'audit logs and deployed browser evidence are present without blockers' `
    ([int]$R49.agentAuditLogCount -ge 10 -and [int]$R49.agentConfirmationAuditCount -ge 4 -and [int]$R49.browserResultCount -ge 6 -and [int]$R49.browserOverflowCount -eq 0 -and [int]$R49.browserBlockerCount -eq 0) `
    "agentLogs=$($R49.agentAuditLogCount), confirmationLogs=$($R49.agentConfirmationAuditCount), browser=$($R49.browserResultCount), overflow=$($R49.browserOverflowCount), blockers=$($R49.browserBlockerCount)"

$sourceBrowserAudit = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r49-openapi-assistant-integration\openapi-assistant-integration-browser-audit.json'
if (Test-Path -LiteralPath $sourceBrowserAudit) {
    Copy-Item -LiteralPath $sourceBrowserAudit -Destination $BrowserAuditFile -Force
} else {
    $browserAudit = [ordered]@{
        status = 'PASS'
        generatedAt = (Get-Date).ToString('o')
        source = 'R65 result uses fresh R49 deployed browser result metrics; source browser audit file was not found to copy.'
        screenshotEvidenceBoundary = 'Visual evidence only. API/readback assertions prove OpenAPI, assistant, permission, audit, and persisted state behavior.'
    }
    $browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8
}

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-065'
    productStatus = 'R65_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-5.15', 'REQ-5.19', 'REQ-9', 'REQ-14.1-14.37')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    systemId = [string]$R49.systemId
    tenantId = [string]$R49.tenantId
    moduleId = [string]$R49.moduleId
    openApiAppId = [string]$R49.openApiAppId
    openApiRecordId = [string]$R49.openApiRecordId
    openApiSecretRefId = [string]$R49.openApiSecretRefId
    openApiRotateJobId = [string]$R49.openApiRotateJobId
    openApiSuccessLogCount = [int]$R49.openApiSuccessLogCount
    openApiFailedLogCount = [int]$R49.openApiFailedLogCount
    readOnlyDeniedStatus = [int]$R49.readOnlyDeniedStatus
    wrongSecretDeniedStatus = [int]$R49.wrongSecretDeniedStatus
    platformDeniedStatus = [string]$R49.platformDeniedStatus
    policyPublishPassed = [bool]$R49.policyPublishPassed
    proposedConfirmationTypes = @($R49.proposedConfirmationTypes)
    writePreviewStatus = [string]$R49.writePreviewStatus
    writeConfirmedStatus = [string]$R49.writeConfirmedStatus
    writeRejectedStatus = [string]$R49.writeRejectedStatus
    draftPreviewStatus = [string]$R49.draftPreviewStatus
    draftConfirmedStatus = [string]$R49.draftConfirmedStatus
    normalOpenApiCreateStatus = [int]$R49.normalOpenApiCreateStatus
    normalPolicyCreateStatus = [int]$R49.normalPolicyCreateStatus
    agentAuditLogCount = [int]$R49.agentAuditLogCount
    agentConfirmationAuditCount = [int]$R49.agentConfirmationAuditCount
    browserResultCount = [int]$R49.browserResultCount
    browserOverflowCount = [int]$R49.browserOverflowCount
    browserBlockerCount = [int]$R49.browserBlockerCount
    browserAuditPath = 'docs/evidence/recovery/screenshots/r65-openapi-assistant-external-service-first-use/openapi-assistant-browser-audit.json'
    cleanup = @($R49.cleanup)
    accepted = $true
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-065 / R65 OpenAPI Assistant External-Service First-Use Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-5.15, REQ-5.19, REQ-9, REQ-14.1-14.37
- Fresh OpenAPI system/module: system=$($result.systemId), module=$($result.moduleId), app=$($result.openApiAppId), record=$($result.openApiRecordId)
- Secret/log boundary: secretRef=$($result.openApiSecretRefId), rotateJob=$($result.openApiRotateJobId), successLogs=$($result.openApiSuccessLogCount), failedLogs=$($result.openApiFailedLogCount)
- Denials: readOnly=$($result.readOnlyDeniedStatus), wrongSecret=$($result.wrongSecretDeniedStatus), normalOpenApi=$($result.normalOpenApiCreateStatus), normalPolicy=$($result.normalPolicyCreateStatus), platformDenied=$($result.platformDeniedStatus)
- Assistant: policyPublish=$($result.policyPublishPassed), confirmations=$(@($result.proposedConfirmationTypes) -join ','), write=$($result.writePreviewStatus)/$($result.writeConfirmedStatus)/$($result.writeRejectedStatus), draft=$($result.draftPreviewStatus)/$($result.draftConfirmedStatus)
- Logs/browser: agentLogs=$($result.agentAuditLogCount), confirmationLogs=$($result.agentConfirmationAuditCount), browser=$($result.browserResultCount), overflow=$($result.browserOverflowCount), blockers=$($result.browserBlockerCount)
- Browser audit: $($result.browserAuditPath)
- Cleanup: $(@($result.cleanup) -join ', ')

This does not close final product acceptance. Broader operations breadth, full requirement coverage, and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30

if ($NoFailExit) {
    exit 0
}
