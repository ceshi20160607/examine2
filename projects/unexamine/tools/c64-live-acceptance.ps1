$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString().Substring(7)
$username = "c64_live_$suffix"
$systemCode = "c64_live_system_$suffix"
$moduleCode = "customer_guard_$suffix"
$applicationCode = "customer_bridge_$suffix"
$password = 'Correct-c64-live-password!'

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$apiBase$Path"
        Headers = $headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress }
    (Invoke-RestMethod @parameters).data
}

function Invoke-SignedCall {
    param(
        [object]$Body,
        [string]$ClientId,
        [string]$ClientSecret,
        [string]$Nonce,
        [string]$IdempotencyKey,
        [string]$SignatureOverride
    )
    $rawBody = $Body | ConvertTo-Json -Depth 30 -Compress
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $requestHash = [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rawBody))).ToLowerInvariant()
    $canonical = "$ClientId`n$timestamp`n$Nonce`n$IdempotencyKey`n$requestHash"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($ClientSecret))
    $signature = if ($SignatureOverride) { $SignatureOverride } else {
        [Convert]::ToHexString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    }
    $response = Invoke-WebRequest -Method Post -Uri "$apiBase/api/application-access/v1/calls" `
        -ContentType 'application/json; charset=utf-8' -Body $rawBody -SkipHttpErrorCheck -Headers @{
            'X-App-Client-Id' = $ClientId
            'X-App-Timestamp' = $timestamp
            'X-App-Nonce' = $Nonce
            'X-App-Idempotency-Key' = $IdempotencyKey
            'X-App-Signature' = $signature
        }
    [pscustomobject]@{
        status = [int]$response.StatusCode
        body = $response.Content | ConvertFrom-Json
    }
}

function Invoke-InternalCall {
    param(
        [long]$ApplicationId,
        [object]$Body,
        [string]$Token,
        [string]$IdempotencyKey
    )
    $response = Invoke-WebRequest -Method Post `
        -Uri "$apiBase/api/application-internal-access/v1/applications/$ApplicationId/calls" `
        -ContentType 'application/json; charset=utf-8' -Body ($Body | ConvertTo-Json -Depth 30 -Compress) `
        -SkipHttpErrorCheck -Headers @{
            Authorization = "Bearer $Token"
            'X-Idempotency-Key' = $IdempotencyKey
        }
    [pscustomobject]@{
        status = [int]$response.StatusCode
        body = $response.Content | ConvertFrom-Json
    }
}

$registration = Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = $username
    password = $password
    displayName = 'C64 真实应用验收管理员'
    email = "$username@example.com"
    systemName = 'C64 应用受控桥梁验收系统'
    systemCode = $systemCode
}
$token = $registration.tokens.accessToken
$systemId = [long]$registration.systemId
$tenantId = [long]$registration.tenantId
$accountId = [long]$registration.accountId

$flowCode = "internal_review_$suffix"
$flow = Invoke-Api -Method Post -Path '/api/flows' -Token $token -Body @{
    code = $flowCode
    name = '系统内受控复核流程'
    description = 'C64 系统内应用访问真实验收'
}
$flowDraft = Invoke-Api -Method Put -Path "/api/flows/$($flow.id)/draft" -Token $token -Body @{
    expectedVersion = 0
    nodes = @(
        @{ nodeKey = 'start'; nodeType = 'START'; name = '开始'; positionX = 0; positionY = 0; config = @{} },
        @{ nodeKey = 'end'; nodeType = 'END'; name = '结束'; positionX = 400; positionY = 0; config = @{} }
    )
    edges = @(@{ edgeKey = 'complete'; sourceNodeKey = 'start'; targetNodeKey = 'end'; priorityOrder = 10; config = @{} })
}
$flowCheck = Invoke-Api -Method Get -Path "/api/flows/$($flow.id)/publication-check" -Token $token
if (-not $flowCheck.valid) { throw 'Internal access Flow publication check failed.' }
$null = Invoke-Api -Method Post -Path "/api/flows/$($flow.id)/publish" -Token $token -Body @{
    expectedDraftRevision = $flowDraft.draftRevision
    changeSummary = 'C64 系统内访问验收版本'
}

$memberId = docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e `
    "select id from sys_member where system_id=$systemId and account_id=$accountId"
$groupCode = "${moduleCode}_group"
$snapshot = @{
    actions = @(
        @{ code = 'LIST'; name = '查询列表'; status = 'ACTIVE' },
        @{ code = 'DETAIL'; name = '查看详情'; status = 'ACTIVE' }
    )
    fields = @(
        @{ code = 'customer_name'; name = '客户名称'; fieldType = 'TEXT'; required = $true; status = 'ACTIVE' }
    )
} | ConvertTo-Json -Depth 10 -Compress
$escapedSnapshot = $snapshot.Replace("'", "''")
$sql = @"
insert into cfg_module_group(system_id,owner_tenant_id,code,name,sort_order,status,created_by_member_id,version)
values($systemId,$tenantId,'$groupCode','客户资料',10,'ACTIVE',$memberId,0);
set @group_id=last_insert_id();
insert into cfg_module(system_id,owner_tenant_id,group_id,code,name,description,status,draft_revision,created_by_member_id,version)
values($systemId,$tenantId,@group_id,'$moduleCode','客户资料','C64 失败保护真实验收资源','PUBLISHED',1,$memberId,0);
set @module_id=last_insert_id();
insert into cfg_module_version(system_id,owner_tenant_id,module_id,version_number,draft_revision,schema_hash,snapshot_json,change_summary,published_by_member_id)
values($systemId,$tenantId,@module_id,1,1,'0000000000000000000000000000000000000000000000000000000000000000','$escapedSnapshot','C64 真实验收发布',$memberId);
set @version_id=last_insert_id();
insert into cfg_module_publication(system_id,owner_tenant_id,module_id,current_version_id,updated_by_member_id,version)
values($systemId,$tenantId,@module_id,@version_id,$memberId,0);
"@
$sql | docker exec -i unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine | Out-Null

$created = Invoke-Api -Method Post -Path '/api/applications' -Token $token -Body @{
    code = $applicationCode
    name = '客户资料受控桥梁'
    description = 'C64 真实调用、导入导出与失败停用验收'
    applicationType = 'SERVICE'
}
$applicationId = [long]$created.application.id
$clientId = [string]$created.issuedCredential.clientId
$clientSecret = [string]$created.issuedCredential.clientSecret
$grant = @{
    targetType = 'SYSTEM'
    targetSystemId = $systemId
    targetTenantId = $tenantId
    resourceType = 'MODULE'
    resourceId = $moduleCode
    actionCode = 'DETAIL'
    dataScope = @{ type = 'SELF' }
    rateLimit = @{
        accessChannels = @('EXTERNAL')
        maxRequests = 100
        windowSeconds = 600
        allowedIps = @('*')
        failureDisableThreshold = 2
        failureWindowSeconds = 300
    }
    fields = @(@{ fieldCode = 'customer_name'; readable = $true; writable = $false; maskStrategy = 'NONE' })
}
$internalGrant = @{
    targetType = 'SYSTEM'
    targetSystemId = $systemId
    targetTenantId = $tenantId
    resourceType = 'FLOW'
    resourceId = $flowCode
    actionCode = 'START'
    dataScope = @{ type = 'SELF' }
    rateLimit = @{
        accessChannels = @('INTERNAL')
        maxRequests = 100
        windowSeconds = 600
        allowedIps = @()
        failureDisableThreshold = 5
        failureWindowSeconds = 300
    }
    fields = @()
}
$saved = Invoke-Api -Method Put -Path "/api/applications/$applicationId/draft" -Token $token -Body @{
    expectedVersion = 0
    name = '客户资料受控桥梁'
    description = 'C64 真实调用、导入导出与失败停用验收'
    applicationType = 'SERVICE'
    callbacks = @()
    grants = @($grant, $internalGrant)
}
$check = Invoke-Api -Method Get -Path "/api/applications/$applicationId/publication-check" -Token $token
if (-not $check.valid) { throw "Application publication check failed: $($check.issues | ConvertTo-Json -Compress)" }
$published = Invoke-Api -Method Post -Path "/api/applications/$applicationId/publish" -Token $token -Body @{
    expectedDraftRevision = $saved.draftRevision
    changeSummary = 'C64 失败策略和配置迁移真实验收版本'
}

$exported = Invoke-Api -Method Get -Path "/api/applications/$applicationId/export" -Token $token
$imported = Invoke-Api -Method Post -Path '/api/applications/import' -Token $token -Body @{
    code = "${applicationCode}_import"
    name = '客户资料受控桥梁（导入）'
    description = $exported.description
    applicationType = $exported.applicationType
    callbacks = $exported.callbacks
    grants = $exported.grants
}

$internalBody = [ordered]@{
    resourceType = 'FLOW'
    resourceId = $flowCode
    actionCode = 'START'
    targetSystemId = $systemId
    targetTenantId = $tenantId
    requestedDataScope = @{ type = 'SELF' }
    payload = @{ title = 'C64 系统内应用调用'; variables = @{ source = 'internal-application' } }
}
$internalSuccess = Invoke-InternalCall -ApplicationId $applicationId -Body $internalBody -Token $token `
    -IdempotencyKey "internal-success-$suffix"
$internalReplay = Invoke-InternalCall -ApplicationId $applicationId -Body $internalBody -Token $token `
    -IdempotencyKey "internal-success-$suffix"
$externalChannelDenied = Invoke-SignedCall -Body $internalBody -ClientId $clientId -ClientSecret $clientSecret `
    -Nonce "external-channel-$suffix" -IdempotencyKey "external-channel-$suffix"

$callBody = [ordered]@{
    resourceType = 'MODULE'
    resourceId = $moduleCode
    actionCode = 'DETAIL'
    targetSystemId = $systemId
    targetTenantId = $tenantId
    requestedDataScope = @{ type = 'SELF' }
    payload = @{ recordId = 900001 }
}
$internalChannelDenied = Invoke-InternalCall -ApplicationId $applicationId -Body $callBody -Token $token `
    -IdempotencyKey "internal-channel-$suffix"
$badSignature = Invoke-SignedCall -Body $callBody -ClientId $clientId -ClientSecret $clientSecret `
    -Nonce "bad-$suffix" -IdempotencyKey "bad-$suffix" -SignatureOverride ('0' * 64)
$afterBadSignature = Invoke-Api -Method Get -Path "/api/applications/$applicationId" -Token $token
$firstFailure = Invoke-SignedCall -Body $callBody -ClientId $clientId -ClientSecret $clientSecret `
    -Nonce "failure-1-$suffix" -IdempotencyKey "failure-1-$suffix"
$afterFirstFailure = Invoke-Api -Method Get -Path "/api/applications/$applicationId" -Token $token
$callBody.payload = @{ recordId = 900002 }
$secondFailure = Invoke-SignedCall -Body $callBody -ClientId $clientId -ClientSecret $clientSecret `
    -Nonce "failure-2-$suffix" -IdempotencyKey "failure-2-$suffix"
$afterSecondFailure = Invoke-Api -Method Get -Path "/api/applications/$applicationId" -Token $token
$thirdCall = Invoke-SignedCall -Body $callBody -ClientId $clientId -ClientSecret $clientSecret `
    -Nonce "failure-3-$suffix" -IdempotencyKey "failure-3-$suffix"

$callCount = docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e `
    "select count(*) from app_call where application_id=$applicationId"
$immutableCallCount = docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e `
    "select count(*) from app_call where application_id=$applicationId and application_version_id=$($published.versionId) and grant_id is null"
$autoDisableAuditCount = docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e `
    "select count(*) from audit_event where object_type='APPLICATION' and object_id='$applicationId' and event_code='APPLICATION_FAILURE_POLICY_DISABLED'"

if ($badSignature.body.code -ne 'APPLICATION_SIGNATURE_INVALID' -or $afterBadSignature.status -ne 'ACTIVE') {
    throw 'Signature rejection must not disable the application.'
}
if ($internalSuccess.status -ne 200 -or -not $internalReplay.body.data.replayed) {
    throw 'System-internal application call or its idempotent replay failed.'
}
if ($externalChannelDenied.body.code -ne 'APPLICATION_ACCESS_CHANNEL_DENIED' -or `
        $internalChannelDenied.body.code -ne 'APPLICATION_ACCESS_CHANNEL_DENIED') {
    throw 'Separately authorized internal and external channels were not enforced.'
}
if ($afterFirstFailure.status -ne 'ACTIVE' -or $afterSecondFailure.status -ne 'DISABLED') {
    throw 'Target execution failure policy did not disable at the configured threshold.'
}
if ($thirdCall.body.code -ne 'APPLICATION_DISABLED') {
    throw 'Disabled application still accepted a new call.'
}
if ([int]$immutableCallCount -ne [int]$callCount -or [int]$autoDisableAuditCount -ne 1) {
    throw 'Application calls or automatic shutdown audit are not durably linked.'
}

[ordered]@{
    systemId = $systemId
    tenantId = $tenantId
    applicationId = $applicationId
    publishedVersionId = $published.versionId
    exportSchemaVersion = $exported.schemaVersion
    exportContainsCredentials = $null -ne $exported.credentials -or $null -ne $exported.clientSecret
    importedApplicationId = $imported.application.id
    importedStatus = $imported.application.status
    importedCredentialShownOnce = $imported.issuedCredential.shownOnce
    internalCallSucceeded = $internalSuccess.status -eq 200
    internalCallReplayed = $internalReplay.body.data.replayed
    internalCallSource = $internalSuccess.body.data.source.accessChannel
    externalUseOfInternalGrant = $externalChannelDenied.body.code
    internalUseOfExternalGrant = $internalChannelDenied.body.code
    badSignatureCode = $badSignature.body.code
    statusAfterBadSignature = $afterBadSignature.status
    firstTargetFailureCode = $firstFailure.body.code
    statusAfterFirstTargetFailure = $afterFirstFailure.status
    secondTargetFailureCode = $secondFailure.body.code
    statusAfterSecondTargetFailure = $afterSecondFailure.status
    thirdCallCode = $thirdCall.body.code
    activeCredentialCountAfterDisable = @($afterSecondFailure.credentials | Where-Object status -eq 'ACTIVE').Count
    callLogCount = [int]$callCount
    immutableVersionCallCount = [int]$immutableCallCount
    autoDisableAuditCount = [int]$autoDisableAuditCount
} | ConvertTo-Json -Depth 10
