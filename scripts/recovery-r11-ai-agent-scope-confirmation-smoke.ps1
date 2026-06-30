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
    foreach ($systemId in @($script:TargetSystemId, $script:RequesterOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r11-ai-agent cleanup created system'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r11-$script:Suffix-$systemId"
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
        $script:CleanupResult = Remove-CreatedSystems
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:TargetSystemId = $null
$script:RequesterOwnedSystemId = $null
$script:AdminHeaders = $null
$CleanupResult = @()

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$Target = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R11 Agent Target $script:Suffix"
    systemCode = "r11_agent_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:TargetSystemId = [string]$Target.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:TargetSystemId }) | Select-Object -First 1
$TargetTenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TargetTenantId)) -Message 'Target tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r11 agent setup'
}
$AdminContext = Invoke-Api -Method 'Get' -Path '/api/v1/context/current-system' -Headers $script:AdminHeaders
$AdminMemberId = [string]$AdminContext.systemMemberId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AdminMemberId)) -Message 'Admin system member id missing after switch.'

$CredentialRefId = "sec_model_r11_$script:Suffix"
$ModelAuth = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/model-authorizations' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-model-auth-$script:Suffix" }) -Body @{
    authorizationCode = "r11_model_$script:Suffix"
    modelProvider = 'LOCAL'
    modelName = "R11 Local Model $script:Suffix"
    modelCredentialRef = @{
        secretRefId = $CredentialRefId
        refType = 'MODEL'
        version = "v1-$script:Suffix"
        displayName = 'R11 model credential'
    }
    quota = @{
        tokenLimit = 100000
        requestLimit = 10000
        costLimit = 0
        resetPolicy = 'MONTHLY'
    }
    dataOutboundPolicy = @{
        allowExternalModel = $false
        allowedRegions = @()
        outboundFields = @()
        retentionDays = 0
    }
    status = 1
    idempotencyKey = "r11-model-auth-$script:Suffix"
}
$ModelAuthJson = $ModelAuth | ConvertTo-Json -Depth 40 -Compress
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$ModelAuth.authorizationId)) -Message 'Model authorization did not return id.'
Assert-True -Condition ([string]$ModelAuth.modelCredentialRef.secretRefId -eq $CredentialRefId) -Message 'Model authorization did not preserve SecretRef id.'
Assert-True -Condition ($ModelAuthJson -notlike '*storageRef*' -and $ModelAuthJson -notlike '*plain*' -and $ModelAuthJson -notlike '*secretMaterial*') -Message 'Model authorization response exposed secret material fields.'

$PlatformSession = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/sessions' -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    promptVersion = "r11_prompt_$script:Suffix"
    openingQuestion = 'Summarize platform health without touching system business data.'
    metadata = @{ source = 'r11' }
}
Assert-True -Condition ($PlatformSession.scope -eq 'platform' -and $PlatformSession.boundary.deniedTargetTypes -contains 'SYSTEM_BUSINESS_RECORD') -Message 'Platform Agent session boundary did not deny system business data.'

$PlatformMessage = Invoke-Api -Method 'Post' -Path "/api/v1/platform/agent/sessions/$($PlatformSession.sessionId)/messages" -Headers $script:AdminHeaders -Body @{
    message = 'Check model quota and prepare a platform task draft.'
    toolHints = @('model_quota_check')
    idempotencyKey = "r11-platform-message-$script:Suffix"
}
Assert-True -Condition (@($PlatformMessage.proposedConfirmations).Count -ge 1) -Message 'Platform Agent message did not propose a confirmation.'
Assert-True -Condition ($PlatformMessage.audit.scope -eq 'platform' -and -not [string]::IsNullOrWhiteSpace([string]$PlatformMessage.audit.auditLogId)) -Message 'Platform Agent message did not return platform audit log.'

$PlatformDenied = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/platform-agent-confirm' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-platform-deny-$script:Suffix" }) -Body @{
    sessionId = [string]$PlatformSession.sessionId
    sourceConversation = 'Attempt to write system business data'
    platformActions = @(@{ actionType = 'SYSTEM_WRITE'; title = 'Forbidden system write'; payload = @{ systemId = $script:TargetSystemId } })
    targetScope = 'system'
    humanConfirmed = $true
    idempotencyKey = "r11-platform-deny-$script:Suffix"
}
Assert-True -Condition ($PlatformDenied.allowed -eq $false -and $PlatformDenied.confirmation.status -eq 'REJECTED_BY_SCOPE') -Message 'Platform Agent system-scope confirmation was not rejected.'
Assert-True -Condition (@($PlatformDenied.rejectedItems).Count -ge 1) -Message 'Platform Agent rejection did not include rejected item evidence.'

$PlatformAllowed = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/platform-agent-confirm' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-platform-allow-$script:Suffix" }) -Body @{
    sessionId = [string]$PlatformSession.sessionId
    sourceConversation = 'Create platform-only task'
    platformActions = @(@{ actionType = 'PLATFORM_TASK'; title = "R11 Platform Task $script:Suffix"; payload = @{ source = 'r11' } })
    targetScope = 'platform'
    humanConfirmed = $true
    idempotencyKey = "r11-platform-allow-$script:Suffix"
}
Assert-True -Condition ($PlatformAllowed.allowed -eq $true -and $PlatformAllowed.confirmation.status -eq 'CONFIRMED') -Message 'Platform Agent platform-scope confirmation did not confirm.'
Assert-True -Condition (@($PlatformAllowed.generatedObjects).Count -ge 1) -Message 'Platform Agent allowed confirmation did not produce generated object evidence.'

$PolicyCode = "r11_policy_$script:Suffix"
$AgentPolicy = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/policies" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-policy-$script:Suffix" }) -Body @{
    policyCode = $PolicyCode
    scope = @{
        moduleScope = @('*')
        fieldScope = @{ '*' = @('*') }
        actionScope = @('read', 'statistics', 'draft_write')
        dataScopeExpression = 'CURRENT_MEMBER_PERMISSION'
        outboundLimit = @{
            maxRows = 100
            allowFileExport = $false
            allowThirdPartyWebhook = $false
        }
        desensitizePolicy = @{
            mode = 'MASK_SENSITIVE'
            maskedFields = @('mobile', 'email')
            preview = @{ mobile = '138****0000' }
        }
        policyVersion = "r11_policy_v_$script:Suffix"
    }
    status = 1
    idempotencyKey = "r11-policy-$script:Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$AgentPolicy.policyId)) -Message 'Agent policy create did not return policyId.'
Assert-True -Condition ($AgentPolicy.scope.fieldScope.PSObject.Properties.Name -contains '*') -Message 'Agent policy readback did not include field scope.'

$PolicyCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/policies/$($AgentPolicy.policyId)/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PolicyCheck.passed -eq $true) -Message "Agent policy publish-check did not pass: $($PolicyCheck | ConvertTo-Json -Depth 20 -Compress)"

$SystemSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/sessions" -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r11_system_prompt_$script:Suffix"
    openingQuestion = 'Prepare a business write preview and daily report draft.'
    metadata = @{ source = 'r11' }
}
Assert-True -Condition ($SystemSession.scope -eq 'system' -and [string]$SystemSession.systemId -eq $script:TargetSystemId) -Message 'System Agent session did not bind to target system.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$SystemSession.permissionSnapshotId)) -Message 'System Agent session did not include permission snapshot.'

$SystemMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/sessions/$($SystemSession.sessionId)/messages" -Headers $script:AdminHeaders -Body @{
    message = 'Change contract amount to 1200 and draft today report.'
    toolHints = @('business_write_preview', 'work_daily_report_draft')
    idempotencyKey = "r11-system-message-$script:Suffix"
}
$ProposedTypes = @($SystemMessage.proposedConfirmations | ForEach-Object { [string]$_.confirmType })
Assert-True -Condition ($ProposedTypes -contains 'SYSTEM_AGENT_WRITE_CONFIRM' -and $ProposedTypes -contains 'WORK_AGENT_DRAFT_CONFIRM') -Message 'System Agent message did not propose write and work confirmations.'
Assert-True -Condition ($SystemMessage.audit.scope -eq 'system' -and [string]$SystemMessage.audit.policyVersion -eq [string]$AgentPolicy.scope.policyVersion) -Message 'System Agent audit did not include system scope and policy version.'

$WritePreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-write-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$SystemSession.sessionId
    sourceConversation = 'Preview write from R11'
    moduleId = 'module-r11'
    recordId = 'record-r11'
    fieldDiffs = @(
        @{ fieldCode = 'contractAmount'; fieldName = 'Contract Amount'; beforeValue = 1000; afterValue = 1200; writable = $true; disabledReason = $null },
        @{ fieldCode = 'secretNote'; fieldName = 'Secret Note'; beforeValue = 'old'; afterValue = 'new'; writable = $false; disabledReason = 'Field is not writable by current permission snapshot' }
    )
    permissionSnapshotId = [string]$SystemSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'Rollback field changes if downstream validation fails.'
    humanConfirmed = $false
    idempotencyKey = "r11-write-preview-$script:Suffix"
}
Assert-True -Condition ($WritePreview.confirmation.status -eq 'WAITING_HUMAN_CONFIRM') -Message 'System write preview did not wait for human confirmation.'
Assert-True -Condition (@($WritePreview.permissionClips).Count -ge 1) -Message 'System write preview did not include permission clips.'

$ConfirmedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/system-agent-write-confirmations/$($WritePreview.confirmation.confirmationId)/confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-write-confirm-$script:Suffix" }) -Body @{
    reason = 'R11 confirms approved write preview'
    idempotencyKey = "r11-write-confirm-$script:Suffix"
}
Assert-True -Condition ($ConfirmedWrite.confirmation.status -eq 'CONFIRMED') -Message 'System write confirmation did not become CONFIRMED.'

$RejectPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-write-reject-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$SystemSession.sessionId
    sourceConversation = 'Preview rejected write from R11'
    moduleId = 'module-r11'
    recordId = 'record-r11'
    fieldDiffs = @(@{ fieldCode = 'contractAmount'; fieldName = 'Contract Amount'; beforeValue = 1200; afterValue = 1300; writable = $true; disabledReason = $null })
    permissionSnapshotId = [string]$SystemSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'No-op because rejected.'
    humanConfirmed = $false
    idempotencyKey = "r11-write-reject-preview-$script:Suffix"
}
$RejectedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/system-agent-write-confirmations/$($RejectPreview.confirmation.confirmationId)/reject" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-write-reject-$script:Suffix" }) -Body @{
    reason = 'R11 rejects unsafe write preview'
    idempotencyKey = "r11-write-reject-$script:Suffix"
}
Assert-True -Condition ($RejectedWrite.confirmation.status -eq 'REJECTED') -Message 'System write rejection did not become REJECTED.'

$WorkDraft = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/work/agent/work-agent-draft-confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r11-work-draft-$script:Suffix" }) -Body @{
    sessionId = [string]$SystemSession.sessionId
    sourceConversation = 'Draft today work report'
    draftType = 'DAILY_REPORT_DRAFT'
    draftPayload = @{
        title = "R11 Daily Report $script:Suffix"
        summary = 'Generated from authorized work/task/message snapshots.'
    }
    sourceSnapshot = @(
        @{ sourceType = 'TASK'; sourceId = "task-r11-$script:Suffix"; title = 'R11 task source'; permissionPolicy = 'CURRENT_MEMBER'; included = $true },
        @{ sourceType = 'MESSAGE'; sourceId = "message-r11-$script:Suffix"; title = 'R11 message source'; permissionPolicy = 'CURRENT_MEMBER'; included = $true }
    )
    humanConfirmed = $true
    idempotencyKey = "r11-work-draft-$script:Suffix"
}
Assert-True -Condition ($WorkDraft.manualConfirmRequired -eq $true -and $WorkDraft.confirmation.status -eq 'CONFIRMED') -Message 'Work draft confirm did not preserve manual confirmation boundary.'
Assert-True -Condition (@($WorkDraft.sourceSnapshot).Count -ge 2) -Message 'Work draft confirm did not preserve source snapshot.'

$AuditLogs = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/agent/audit-logs?pageNo=1&pageSize=50&scope=system" -Headers $script:AdminHeaders
$SystemAuditRecords = @($AuditLogs.records)
Assert-True -Condition ($SystemAuditRecords.Count -ge 6) -Message 'Agent audit logs did not include system session/message/confirm/work records.'
$ConfirmationAudit = @($SystemAuditRecords | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.confirmationId) }) | Select-Object -First 1
Assert-True -Condition ($null -ne $ConfirmationAudit -and -not [string]::IsNullOrWhiteSpace([string]$ConfirmationAudit.permissionSnapshotId)) -Message 'Agent audit log did not include confirmation and permission snapshot evidence.'

$Provider = Invoke-Api -Method 'Post' -Path '/api/v1/platform/identity-providers' -Headers $script:AdminHeaders -Body @{
    name = "R11 OIDC $script:Suffix"
    protocol = 'OIDC'
    issuer = "https://idp.r11.$script:Suffix.example"
    clientId = "r11-client-$script:Suffix"
    secretRefId = "sec_r11_idp_$script:Suffix"
    certRefId = "cert_r11_$script:Suffix"
    domainWhitelist = @("r11-$script:Suffix.example")
    jitPolicy = @{ enabled = $true; createAccount = $true }
    mfaPolicy = @{ inherited = $true }
    status = 'DRAFT'
}
$ProviderId = [string]$Provider.providerId

$RequesterLoginName = "r11_member_$script:Suffix"
$RequesterRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $RequesterLoginName
    mobile = "17$script:Suffix"
    email = "r11_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R11 Member Owned $script:Suffix"
    systemCode = "r11_member_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:RequesterOwnedSystemId = [string]$RequesterRegister.systemId
$RequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $RequesterLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$RequesterHeaders = @{ Authorization = "Bearer $($RequesterLogin.accessToken)" }
$RequesterAccountId = [string]$RequesterLogin.profile.accountId

$AccessRequest = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests" -Headers $RequesterHeaders -Body @{
    identityProvider = $ProviderId
    externalUserId = $RequesterAccountId
    tenantId = $TargetTenantId
    requestRole = 'SYSTEM_MEMBER'
    requestReason = 'R11 normal member permission negative'
    idempotencyKey = "r11-no-member-$script:Suffix"
}
$NormalRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R11 Normal Member $script:Suffix"
    roleCode = "R11_NORMAL_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'R11 normal member role'
}
$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R11 Normal Member $script:Suffix"
    employeeNo = "R11$script:Suffix"
    mobile = "16$script:Suffix"
    email = "r11_normal_$script:Suffix@example.com"
    status = 1
    roleIds = @()
}
$FullApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/no-member-access-requests/$($AccessRequest.requestId)/approve" -Headers $script:AdminHeaders -Body @{
    approverId = $AdminMemberId
    accountId = $RequesterAccountId
    systemMemberId = [string]$NormalMember.systemMemberId
    roleIds = @([string]$NormalRole.roleId)
    dataScope = @{ type = 'ALL'; source = 'recovery-r11' }
    approveComment = 'Grant normal member for Agent negative permission test'
    idempotencyKey = "r11-full-approve-$script:Suffix"
}
Assert-True -Condition ($FullApprove.status -eq 'APPROVED' -and [bool]$FullApprove.businessAccessAllowed) -Message 'Normal member approval did not grant business access.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'r11 normal member switch'
}
$NegativePolicyCreate = Invoke-ExpectedFailure -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/agent/policies" -Headers $RequesterHeaders -ExpectedStatus 403 -Body @{
    policyCode = "r11_forbidden_$script:Suffix"
    scope = @{
        moduleScope = @('*')
        fieldScope = @{ '*' = @('*') }
        actionScope = @('draft_write')
        dataScopeExpression = 'ALL'
        outboundLimit = @{ maxRows = 1; allowFileExport = $false; allowThirdPartyWebhook = $false }
        desensitizePolicy = @{ mode = 'MASK_SENSITIVE'; maskedFields = @(); preview = @{} }
        policyVersion = "forbidden_$script:Suffix"
    }
    status = 1
    idempotencyKey = "r11-forbidden-$script:Suffix"
}

$CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    result = 'PASS'
    suffix = $script:Suffix
    targetSystemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    modelAuthorizationId = [string]$ModelAuth.authorizationId
    modelSecretRefId = [string]$ModelAuth.modelCredentialRef.secretRefId
    platformSessionId = [string]$PlatformSession.sessionId
    platformDeniedStatus = [string]$PlatformDenied.confirmation.status
    platformAllowedStatus = [string]$PlatformAllowed.confirmation.status
    agentPolicyId = [string]$AgentPolicy.policyId
    policyPublishPassed = [bool]$PolicyCheck.passed
    systemSessionId = [string]$SystemSession.sessionId
    proposedConfirmationTypes = $ProposedTypes
    writeConfirmedStatus = [string]$ConfirmedWrite.confirmation.status
    writeRejectedStatus = [string]$RejectedWrite.confirmation.status
    workDraftStatus = [string]$WorkDraft.confirmation.status
    agentAuditLogCount = $SystemAuditRecords.Count
    normalMemberPolicyCreateStatus = [int]$NegativePolicyCreate.status
    requesterOwnedSystemId = $script:RequesterOwnedSystemId
    cleanup = $CleanupResult
}

$Result | ConvertTo-Json -Depth 20
