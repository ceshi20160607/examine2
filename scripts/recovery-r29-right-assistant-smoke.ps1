param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 60 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 24 -Compress)"
    }
    return $response.data
}

function Invoke-ExpectedFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$ExpectedStatus = 403,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 45
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
        }
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        Assert-True -Condition ($status -eq $ExpectedStatus) -Message "Expected HTTP $ExpectedStatus for $Method $Path but got HTTP $status."
        return @{ status = $status; failedAsExpected = $true }
    }
    throw "Expected API failure for $Method $Path but request succeeded."
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r29 right assistant cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r29-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R29 Assistant $script:Suffix"
    systemCode = "r29_assistant_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId

$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r29 assistant admin setup'
}

$ModelAuth = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/model-authorizations' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-model-auth-$script:Suffix" }) -Body @{
    authorizationCode = "r29_model_$script:Suffix"
    modelProvider = 'LOCAL'
    modelName = "R29 Local Model $script:Suffix"
    modelCredentialRef = @{
        secretRefId = "sec_model_r29_$script:Suffix"
        refType = 'MODEL'
        version = "v1-$script:Suffix"
        displayName = 'R29 model credential'
    }
    quota = @{ tokenLimit = 100000; requestLimit = 10000; costLimit = 0; resetPolicy = 'MONTHLY' }
    dataOutboundPolicy = @{ allowExternalModel = $false; allowedRegions = @(); outboundFields = @(); retentionDays = 0 }
    status = 1
    idempotencyKey = "r29-model-auth-$script:Suffix"
}

$PolicyCode = "r29_policy_$script:Suffix"
$AgentPolicy = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-policy-$script:Suffix" }) -Body @{
    policyCode = $PolicyCode
    scope = @{
        moduleScope = @('*')
        fieldScope = @{ '*' = @('*') }
        actionScope = @('read', 'statistics', 'requestWriteConfirmation', 'createWorkDraft')
        dataScopeExpression = 'CURRENT_MEMBER_PERMISSION'
        outboundLimit = @{ maxRows = 100; allowFileExport = $false; allowThirdPartyWebhook = $false }
        desensitizePolicy = @{ mode = 'MASK_SENSITIVE'; maskedFields = @('mobile', 'email'); preview = @{ mobile = '138****0000' } }
        policyVersion = "r29_policy_v_$script:Suffix"
    }
    status = 1
    idempotencyKey = "r29-policy-$script:Suffix"
}
$PolicyCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies/$($AgentPolicy.policyId)/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PolicyCheck.passed -eq $true) -Message 'R29 Agent policy publish-check did not pass.'

$AdminSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions" -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r29_assistant_prompt_$script:Suffix"
    openingQuestion = 'Right-side assistant should prepare analysis, write preview and daily report draft.'
    metadata = @{ source = 'right-side-assistant-smoke' }
}
Assert-True -Condition ($AdminSession.scope -eq 'system' -and -not [string]::IsNullOrWhiteSpace([string]$AdminSession.permissionSnapshotId)) -Message 'Admin assistant session did not bind system permission snapshot.'

$AdminMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions/$($AdminSession.sessionId)/messages" -Headers $script:AdminHeaders -Body @{
    message = 'Generate an assistant write preview and daily report draft from current system context.'
    toolHints = @('business_write_preview', 'work_daily_report_draft')
    idempotencyKey = "r29-admin-message-$script:Suffix"
}
$ProposedTypes = @($AdminMessage.proposedConfirmations | ForEach-Object { [string]$_.confirmType })
Assert-True -Condition ($ProposedTypes -contains 'SYSTEM_AGENT_WRITE_CONFIRM' -and $ProposedTypes -contains 'WORK_AGENT_DRAFT_CONFIRM') -Message 'Right-side assistant message did not propose write and work confirmations.'
Assert-True -Condition ($AdminMessage.audit.scope -eq 'system' -and -not [string]::IsNullOrWhiteSpace([string]$AdminMessage.audit.permissionSnapshotId)) -Message 'Assistant message audit did not include system permission snapshot.'

$WritePreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-write-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'R29 assistant write preview'
    moduleId = 'assistant-module'
    recordId = 'assistant-record'
    fieldDiffs = @(
        @{ fieldCode = 'status'; fieldName = 'Status'; beforeValue = 'DRAFT'; afterValue = 'IN_PROGRESS'; writable = $true; disabledReason = $null },
        @{ fieldCode = 'secretNote'; fieldName = 'Sensitive Note'; beforeValue = 'old'; afterValue = 'new'; writable = $false; disabledReason = 'Field is masked or not writable by current permission snapshot' }
    )
    permissionSnapshotId = [string]$AdminSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'Rollback field changes if downstream validation fails.'
    humanConfirmed = $false
    idempotencyKey = "r29-write-preview-$script:Suffix"
}
Assert-True -Condition ($WritePreview.confirmation.status -eq 'WAITING_HUMAN_CONFIRM') -Message 'Assistant write preview did not wait for human confirmation.'
Assert-True -Condition (@($WritePreview.permissionClips).Count -ge 1) -Message 'Assistant write preview did not include permission clipping evidence.'

$ConfirmedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations/$($WritePreview.confirmation.confirmationId)/confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-write-confirm-$script:Suffix" }) -Body @{
    reason = 'R29 right-side assistant confirms safe write preview'
    idempotencyKey = "r29-write-confirm-$script:Suffix"
}
Assert-True -Condition ($ConfirmedWrite.confirmation.status -eq 'CONFIRMED') -Message 'Assistant write confirmation did not become CONFIRMED.'

$RejectSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions" -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r29_reject_prompt_$script:Suffix"
    openingQuestion = 'Prepare rejected assistant write preview.'
    metadata = @{ source = 'right-side-assistant-smoke-reject' }
}
$RejectPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-write-reject-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$RejectSession.sessionId
    sourceConversation = 'R29 rejected write preview'
    moduleId = 'assistant-module'
    recordId = 'assistant-record'
    fieldDiffs = @(@{ fieldCode = 'status'; fieldName = 'Status'; beforeValue = 'IN_PROGRESS'; afterValue = 'DONE'; writable = $true; disabledReason = $null })
    permissionSnapshotId = [string]$RejectSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'No-op because rejected.'
    humanConfirmed = $false
    idempotencyKey = "r29-write-reject-preview-$script:Suffix"
}
$RejectedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations/$($RejectPreview.confirmation.confirmationId)/reject" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-write-reject-$script:Suffix" }) -Body @{
    reason = 'R29 right-side assistant rejects unsafe write preview'
    idempotencyKey = "r29-write-reject-$script:Suffix"
}
Assert-True -Condition ($RejectedWrite.confirmation.status -eq 'REJECTED') -Message 'Assistant write rejection did not become REJECTED.'

$DraftPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/agent/work-agent-draft-confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-work-draft-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'Draft today work report from right-side assistant'
    draftType = 'DAILY_REPORT_DRAFT'
    draftPayload = @{ title = "R29 Daily Report $script:Suffix"; summary = 'Generated from authorized assistant context.' }
    sourceSnapshot = @(@{ sourceType = 'ASSISTANT_CONTEXT'; sourceId = "session-$script:Suffix"; title = 'Right-side assistant context'; permissionPolicy = 'CURRENT_MEMBER'; included = $true })
    humanConfirmed = $false
    idempotencyKey = "r29-work-draft-preview-$script:Suffix"
}
Assert-True -Condition ($DraftPreview.confirmation.status -eq 'WAITING_HUMAN_CONFIRM' -and $DraftPreview.manualConfirmRequired -eq $true) -Message 'Assistant work draft preview did not wait for human confirmation.'
$DraftConfirmed = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/agent/work-agent-draft-confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r29-work-draft-confirm-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'Confirm today work report from right-side assistant'
    draftType = 'DAILY_REPORT_DRAFT'
    draftPayload = $DraftPreview.draftPayload
    sourceSnapshot = $DraftPreview.sourceSnapshot
    humanConfirmed = $true
    idempotencyKey = "r29-work-draft-confirm-$script:Suffix"
}
Assert-True -Condition ($DraftConfirmed.confirmation.status -eq 'CONFIRMED') -Message 'Assistant work draft did not become CONFIRMED.'

$NormalLoginName = "r29_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r29_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R29 Owned $script:Suffix"
    systemCode = "r29_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId
$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R29 Normal Member $script:Suffix"
    employeeNo = "R29NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r29_member_runtime_$script:Suffix@example.com"
    status = 1
    roleIds = @()
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}
$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r29 normal assistant setup'
}
Assert-True -Condition (-not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_ADMIN') -and -not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) `
    -Message "Normal member unexpectedly has system admin role: $($NormalSwitch.effectiveRoleIds -join ',')"

$NormalSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions" -Headers $NormalHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r29_normal_prompt_$script:Suffix"
    openingQuestion = 'Normal member uses right-side assistant in permitted context.'
    metadata = @{ source = 'right-side-assistant-normal-smoke' }
}
Assert-True -Condition ($NormalSession.scope -eq 'system' -and -not [string]::IsNullOrWhiteSpace([string]$NormalSession.permissionSnapshotId)) -Message 'Normal member assistant session did not bind permission snapshot.'
$NormalMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions/$($NormalSession.sessionId)/messages" -Headers $NormalHeaders -Body @{
    message = 'Explain my current work context without admin configuration changes.'
    toolHints = @('statistics_query')
    idempotencyKey = "r29-normal-message-$script:Suffix"
}
Assert-True -Condition ($NormalMessage.audit.scope -eq 'system' -and -not [string]::IsNullOrWhiteSpace([string]$NormalMessage.audit.permissionSnapshotId)) -Message 'Normal assistant message did not return scoped audit.'

$NormalForbiddenPolicy = Invoke-ExpectedFailure -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies" -Headers $NormalHeaders -ExpectedStatus 403 -Body @{
    policyCode = "r29_forbidden_$script:Suffix"
    scope = @{
        moduleScope = @('*')
        fieldScope = @{ '*' = @('*') }
        actionScope = @('admin_policy_generation')
        dataScopeExpression = 'ALL'
        outboundLimit = @{ maxRows = 1; allowFileExport = $false; allowThirdPartyWebhook = $false }
        desensitizePolicy = @{ mode = 'MASK_SENSITIVE'; maskedFields = @(); preview = @{} }
        policyVersion = "forbidden_$script:Suffix"
    }
    status = 1
    idempotencyKey = "r29-forbidden-$script:Suffix"
}

$AuditLogs = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/agent/audit-logs?pageNo=1&pageSize=80&scope=system" -Headers $script:AdminHeaders
$AuditRecords = @($AuditLogs.records)
Assert-True -Condition ($AuditRecords.Count -ge 8) -Message 'Assistant audit logs did not include session/message/confirm/reject/draft records.'
$ConfirmationAudits = @($AuditRecords | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.confirmationId) })
Assert-True -Condition ($ConfirmationAudits.Count -ge 4) -Message 'Assistant audit logs did not include enough confirmation records.'

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-029'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    agentPolicyId = [string]$AgentPolicy.policyId
    policyPublishPassed = [bool]$PolicyCheck.passed
    adminSessionId = [string]$AdminSession.sessionId
    proposedConfirmationTypes = $ProposedTypes
    writePreviewStatus = [string]$WritePreview.confirmation.status
    writeConfirmedStatus = [string]$ConfirmedWrite.confirmation.status
    writeRejectedStatus = [string]$RejectedWrite.confirmation.status
    draftPreviewStatus = [string]$DraftPreview.confirmation.status
    draftConfirmedStatus = [string]$DraftConfirmed.confirmation.status
    normalSessionId = [string]$NormalSession.sessionId
    normalPolicyCreateStatus = [int]$NormalForbiddenPolicy.status
    auditLogCount = $AuditRecords.Count
    confirmationAuditCount = $ConfirmationAudits.Count
    traceId = [string]$AdminMessage.operation.traceId
    cleanup = $script:CleanupResult
}
$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r29-right-assistant-result.json'
$Result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
$Result | ConvertTo-Json -Depth 60
