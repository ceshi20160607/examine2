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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r55-frc4-workflow-integration-ai-depth'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r55-frc4-workflow-integration-ai-depth-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r55-frc4-workflow-integration-ai-depth-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'workflow-integration-ai-browser-audit.json'
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

$childResults += Invoke-ChildScript 'recovery-r48-workflow-message-flow' `
    (Join-Path $PSScriptRoot 'recovery-r48-workflow-message-flow-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R48 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r48-workflow-message-flow-result.json')
Add-Check $script:Checks 'workflow-message' 'R48 fresh workflow todo message approval closure passed' `
    ($R48.status -eq 'PASS' -and $R48.publishCheckPassed -eq $true -and $R48.simulationPassed -eq $true -and $R48.simulationRuntimeInstanceCreated -eq $false -and [int]$R48.publishCheckImpactCount -ge 1 -and [int]$R48.simulationStepCount -ge 2 -and [int]$R48.requesterPendingTodoTotal -eq 0 -and [int]$R48.approverPendingTodoTotal -eq 1 -and [int]$R48.approverMessageTotal -eq 1 -and [int]$R48.requesterApproveDeniedStatus -eq 403 -and [int]$R48.normalAdminDeniedStatus -eq 403 -and [string]$R48.todoActionStatus -eq 'HANDLED' -and [string]$R48.duplicateTodoActionStatus -eq 'HANDLED' -and [string]$R48.todoActionTraceId -eq [string]$R48.duplicateTodoActionTraceId -and [string]$R48.detailTerminalStatus -eq 'APPROVED' -and [string]$R48.approvalSidebarStatus -eq 'APPROVED' -and [int]$R48.pendingAfterApproveTotal -eq 0 -and [int]$R48.handledAfterApproveTotal -eq 1 -and [int]$R48.browserOverflowCount -eq 0 -and [int]$R48.browserBlockerCount -eq 0) `
    "flow=$($R48.flowId), todo=$($R48.pendingTodoId), message=$($R48.pendingMessageId), action=$($R48.todoActionStatus), terminal=$($R48.detailTerminalStatus), browserBefore=$($R48.browserBeforeResultCount), browserAfter=$($R48.browserAfterResultCount)"

$childResults += Invoke-ChildScript 'recovery-r49-openapi-assistant-integration' `
    (Join-Path $PSScriptRoot 'recovery-r49-openapi-assistant-integration-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R49 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r49-openapi-assistant-integration-result.json')
Add-Check $script:Checks 'openapi-assistant' 'R49 fresh OpenAPI assistant integration boundary passed' `
    ($R49.status -eq 'PASS' -and -not [string]::IsNullOrWhiteSpace([string]$R49.openApiAppId) -and -not [string]::IsNullOrWhiteSpace([string]$R49.openApiRecordId) -and [string]$R49.openApiSecretRefId -match '^sec_' -and [string]$R49.openApiRotateJobId -match '^srj_' -and [int]$R49.openApiSuccessLogCount -ge 5 -and [int]$R49.openApiFailedLogCount -ge 1 -and [int]$R49.readOnlyDeniedStatus -eq 403 -and [int]$R49.wrongSecretDeniedStatus -eq 401 -and [string]$R49.platformDeniedStatus -eq 'REJECTED_BY_SCOPE' -and $R49.policyPublishPassed -eq $true -and @($R49.proposedConfirmationTypes).Count -ge 2 -and @($R49.proposedConfirmationTypes) -contains 'SYSTEM_AGENT_WRITE_CONFIRM' -and @($R49.proposedConfirmationTypes) -contains 'WORK_AGENT_DRAFT_CONFIRM' -and [string]$R49.writePreviewStatus -eq 'WAITING_HUMAN_CONFIRM' -and [string]$R49.writeConfirmedStatus -eq 'CONFIRMED' -and [string]$R49.writeRejectedStatus -eq 'REJECTED' -and [string]$R49.draftPreviewStatus -eq 'WAITING_HUMAN_CONFIRM' -and [string]$R49.draftConfirmedStatus -eq 'CONFIRMED' -and [int]$R49.normalOpenApiCreateStatus -eq 403 -and [int]$R49.normalPolicyCreateStatus -eq 403 -and [int]$R49.agentAuditLogCount -ge 10 -and [int]$R49.agentConfirmationAuditCount -ge 4 -and [int]$R49.browserOverflowCount -eq 0 -and [int]$R49.browserBlockerCount -eq 0) `
    "openApiApp=$($R49.openApiAppId), record=$($R49.openApiRecordId), successLogs=$($R49.openApiSuccessLogCount), failedLogs=$($R49.openApiFailedLogCount), confirmations=$(@($R49.proposedConfirmationTypes) -join ','), browser=$($R49.browserResultCount)"

$workflowSystemId = [string]$R48.systemId
$integrationSystemId = [string]$R49.systemId
Add-Check $script:Checks 'frc4-integration' 'fresh workflow and integration slices both create real systems and cleanup evidence' `
    (-not [string]::IsNullOrWhiteSpace($workflowSystemId) -and -not [string]::IsNullOrWhiteSpace($integrationSystemId) -and @($R48.cleanup).Count -ge 1 -and @($R49.cleanup).Count -ge 1) `
    "workflowSystem=$workflowSystemId, integrationSystem=$integrationSystemId, r48Cleanup=$(@($R48.cleanup) -join ','), r49Cleanup=$(@($R49.cleanup) -join ',')"

Add-Check $script:Checks 'frc4-integration' 'browser evidence covers workflow before/after plus OpenAPI assistant surfaces without blockers' `
    ([int]$R48.browserBeforeResultCount -ge 6 -and [int]$R48.browserAfterResultCount -ge 4 -and [int]$R49.browserResultCount -ge 6 -and [int]$R48.browserOverflowCount -eq 0 -and [int]$R49.browserOverflowCount -eq 0 -and [int]$R48.browserBlockerCount -eq 0 -and [int]$R49.browserBlockerCount -eq 0) `
    "r48Before=$($R48.browserBeforeResultCount), r48After=$($R48.browserAfterResultCount), r49=$($R49.browserResultCount), overflow=$($R48.browserOverflowCount)/$($R49.browserOverflowCount), blockers=$($R48.browserBlockerCount)/$($R49.browserBlockerCount)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R55 aggregation of fresh R48/R49 deployed browser audits'
    screenshotEvidenceBoundary = 'Visual evidence only. API/readback assertions prove workflow, OpenAPI, assistant, permission, audit, and persisted state behavior.'
    r48 = @{
        resultFile = 'docs/evidence/recovery/r48-workflow-message-flow-result.json'
        browserAuditPath = $R48.browserAuditPath
        browserBeforeResultCount = $R48.browserBeforeResultCount
        browserAfterResultCount = $R48.browserAfterResultCount
        browserOverflowCount = $R48.browserOverflowCount
        browserBlockerCount = $R48.browserBlockerCount
    }
    r49 = @{
        resultFile = 'docs/evidence/recovery/r49-openapi-assistant-integration-result.json'
        browserAuditPath = $R49.browserAuditPath
        browserResultCount = $R49.browserResultCount
        browserOverflowCount = $R49.browserOverflowCount
        browserBlockerCount = $R49.browserBlockerCount
    }
}
$browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.5', 'REQ-5.12', 'REQ-5.15', 'REQ-5.19')
    journeyRows = @('J4', 'J6', 'J8', 'J9', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json'
    workflowMessage = @{
        systemId = $R48.systemId
        tenantId = $R48.tenantId
        moduleId = $R48.moduleId
        recordId = $R48.recordId
        flowId = $R48.flowId
        flowCurrentVersion = $R48.flowCurrentVersion
        publishCheckPassed = $R48.publishCheckPassed
        publishCheckImpactCount = $R48.publishCheckImpactCount
        simulationPassed = $R48.simulationPassed
        simulationStepCount = $R48.simulationStepCount
        requesterPendingTodoTotal = $R48.requesterPendingTodoTotal
        approverPendingTodoTotal = $R48.approverPendingTodoTotal
        approverMessageTotal = $R48.approverMessageTotal
        requesterApproveDeniedStatus = $R48.requesterApproveDeniedStatus
        normalAdminDeniedStatus = $R48.normalAdminDeniedStatus
        todoActionStatus = $R48.todoActionStatus
        duplicateTodoActionStatus = $R48.duplicateTodoActionStatus
        duplicateTraceSame = ([string]$R48.todoActionTraceId -eq [string]$R48.duplicateTodoActionTraceId)
        detailTerminalStatus = $R48.detailTerminalStatus
        approvalSidebarStatus = $R48.approvalSidebarStatus
        pendingAfterApproveTotal = $R48.pendingAfterApproveTotal
        handledAfterApproveTotal = $R48.handledAfterApproveTotal
    }
    openApiAssistant = @{
        systemId = $R49.systemId
        tenantId = $R49.tenantId
        moduleId = $R49.moduleId
        openApiAppId = $R49.openApiAppId
        openApiRecordId = $R49.openApiRecordId
        openApiSecretRefId = $R49.openApiSecretRefId
        openApiRotateJobId = $R49.openApiRotateJobId
        openApiSuccessLogCount = $R49.openApiSuccessLogCount
        openApiFailedLogCount = $R49.openApiFailedLogCount
        readOnlyDeniedStatus = $R49.readOnlyDeniedStatus
        wrongSecretDeniedStatus = $R49.wrongSecretDeniedStatus
        platformDeniedStatus = $R49.platformDeniedStatus
        policyPublishPassed = $R49.policyPublishPassed
        proposedConfirmationTypes = @($R49.proposedConfirmationTypes)
        writePreviewStatus = $R49.writePreviewStatus
        writeConfirmedStatus = $R49.writeConfirmedStatus
        writeRejectedStatus = $R49.writeRejectedStatus
        draftPreviewStatus = $R49.draftPreviewStatus
        draftConfirmedStatus = $R49.draftConfirmedStatus
        normalOpenApiCreateStatus = $R49.normalOpenApiCreateStatus
        normalPolicyCreateStatus = $R49.normalPolicyCreateStatus
        agentAuditLogCount = $R49.agentAuditLogCount
        agentConfirmationAuditCount = $R49.agentConfirmationAuditCount
    }
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-055 / R55 FRC-4 Workflow Integration AI Depth Fresh Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.5, REQ-5.12, REQ-5.15, REQ-5.19
- Fresh workflow/message/approval: R48 PASS, flow=$($R48.flowId), publishCheck=$($R48.publishCheckPassed), simulation=$($R48.simulationPassed), todo=$($R48.pendingTodoId), message=$($R48.pendingMessageId), action=$($R48.todoActionStatus), terminal=$($R48.detailTerminalStatus), duplicateTraceSame=$([string]$R48.todoActionTraceId -eq [string]$R48.duplicateTodoActionTraceId), browserBefore=$($R48.browserBeforeResultCount), browserAfter=$($R48.browserAfterResultCount), overflow=$($R48.browserOverflowCount), blockers=$($R48.browserBlockerCount)
- Fresh OpenAPI/assistant/integration: R49 PASS, openApiApp=$($R49.openApiAppId), record=$($R49.openApiRecordId), secretRef=$($R49.openApiSecretRefId), rotateJob=$($R49.openApiRotateJobId), successLogs=$($R49.openApiSuccessLogCount), failedLogs=$($R49.openApiFailedLogCount), readOnlyDenied=$($R49.readOnlyDeniedStatus), wrongSecretDenied=$($R49.wrongSecretDeniedStatus), platformDenied=$($R49.platformDeniedStatus), confirmations=$(@($R49.proposedConfirmationTypes) -join ','), browser=$($R49.browserResultCount), overflow=$($R49.browserOverflowCount), blockers=$($R49.browserBlockerCount)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Browser aggregation: `docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json`

This does not close final product acceptance. Broader workflow variants, richer OpenAPI/assistant failure and UX states, full requirement coverage, and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
