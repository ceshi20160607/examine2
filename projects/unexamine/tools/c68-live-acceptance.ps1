$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString().Substring(7)
$password = 'Correct-c68-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [switch]$AllowError)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method; Uri = "$apiBase$Path"; Headers = $headers
        ContentType = 'application/json; charset=utf-8'; SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 50 -Compress }
    $response = Invoke-WebRequest @parameters
    $payload = $response.Content | ConvertFrom-Json
    if (-not $AllowError -and [int]$response.StatusCode -ge 400) {
        throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    [pscustomobject]@{ status = [int]$response.StatusCode; payload = $payload; data = $payload.data }
}

function Database-Scalar([string]$Sql) {
    [long](docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e $Sql)
}

function New-Module([string]$Code, [string]$Name, [long]$GroupId) {
    (Invoke-Api -Method Post -Path '/api/admin/module-config/modules' -Token $script:ownerToken -Body @{
        groupId = $GroupId; code = $Code; name = $Name; description = "$Name 的业务资料与协作记录"
    }).data.module
}

function Add-Field {
    param([long]$ModuleId, [string]$Code, [string]$Name, [string]$Type, [bool]$Required,
          [int]$SortOrder, [Nullable[long]]$ReferenceModuleId)
    $body = @{
        code = $Code; name = $Name; fieldType = $Type; required = $Required
        searchable = $true; sortOrder = $SortOrder; config = @{}
    }
    if ($null -ne $ReferenceModuleId) { $body.referenceModuleId = [long]$ReferenceModuleId }
    $null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$ModuleId/fields" -Token $script:ownerToken -Body $body
}

function Publish-Module([long]$ModuleId, [string]$Summary) {
    $revision = Database-Scalar "select draft_revision from cfg_module where id=$ModuleId"
    $null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$ModuleId/publish" -Token $script:ownerToken -Body @{
        expectedDraftRevision = $revision; changeSummary = $Summary
    }
}

function Enter-System([string]$Username, [long]$SystemId) {
    $platformToken = (Invoke-Api -Method Post -Path '/api/auth/login' -Body @{
        username = $Username; password = $script:password
    }).data.accessToken
    (Invoke-Api -Method Post -Path "/api/systems/$SystemId/enter" -Token $platformToken -Body @{
        previousSystemId = $null; previousTenantId = $null
    }).data.tokens.accessToken
}

function Query-Record {
    param([string]$Token, [long]$AgentId, [long]$RecordId, [array]$Fields, [long]$TenantId, [string]$Question)
    (Invoke-Api -Method Post -Path '/api/ai/queries' -Token $Token -Body @{
        agentId = $AgentId; conversationId = $null; question = $Question
        requestedFieldCodes = $Fields; requestedTenantId = $TenantId
        entryContext = @{
            entryType = 'RECORD_DETAIL'; moduleCode = $script:customerCode; recordId = $RecordId
            sourcePath = "/systems/$script:systemId`?workspace=runtime&module=$script:customerCode&recordId=$RecordId"
            lifecycleState = 'ACTIVE'; tenantScope = 'ALL'; search = ''; filters = @()
            sortField = 'updatedAt'; sortDirection = 'DESC'; pageSize = 5
        }
    }).data
}

function Recognize-FollowUp {
    param([string]$Token, [long]$AgentId, [long]$CustomerId, [string]$Text)
    (Invoke-Api -Method Post -Path '/api/ai/writes/recognize' -Token $Token -Body @{
        agentId = $AgentId; moduleCode = $script:followupCode; inputText = $Text
        sourceType = 'TEXT'; requestedTenantId = $script:tenantId; entryType = 'RECORD_DETAIL'
        sourceReference = "/systems/$script:systemId`?workspace=runtime&module=$script:customerCode&recordId=$CustomerId"
    }).data
}

$ownerRegistration = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = "c68_owner_$suffix"; password = $password; displayName = '周明'
    email = "c68_owner_$suffix@example.com"; systemName = '北辰客户协作中心'; systemCode = "c68_system_$suffix"
}).data
$script:ownerToken = $ownerRegistration.tokens.accessToken
$script:systemId = [long]$ownerRegistration.systemId
$script:tenantId = [long]$ownerRegistration.tenantId
$ownerAccountId = [long]$ownerRegistration.accountId
$ownerTenantMemberId = Database-Scalar "select stm.id from sys_tenant_member stm join sys_member sm on sm.id=stm.system_member_id where stm.system_id=$systemId and stm.tenant_id=$tenantId and sm.account_id=$ownerAccountId"

$sellerUsername = "c68_seller_$suffix"
$supervisorUsername = "c68_supervisor_$suffix"
$viewerUsername = "c68_viewer_$suffix"
foreach ($account in @(
    @{ username = $sellerUsername; name = '陈晨' },
    @{ username = $supervisorUsername; name = '李毅' },
    @{ username = $viewerUsername; name = '王宁' }
)) {
    $null = Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
        username = $account.username; password = $password; displayName = $account.name
        email = "$($account.username)@example.com"; systemName = "$($account.name)个人系统"; systemCode = "$($account.username)_system"
    }
}

$script:customerCode = "customer_c68_$suffix"
$script:followupCode = "followup_c68_$suffix"
$group = (Invoke-Api -Method Post -Path '/api/admin/module-config/groups' -Token $ownerToken -Body @{
    code = "sales_c68_$suffix"; name = '客户经营'; sortOrder = 10
}).data
$customerModule = New-Module -Code $customerCode -Name '客户' -GroupId $group.id
$followupModule = New-Module -Code $followupCode -Name '跟进记录' -GroupId $group.id
Add-Field -ModuleId $customerModule.id -Code 'customer_name' -Name '客户名称' -Type 'TEXT' -Required $true -SortOrder 10
Add-Field -ModuleId $customerModule.id -Code 'contact_phone' -Name '联系电话' -Type 'TEXT' -Required $false -SortOrder 20
Add-Field -ModuleId $followupModule.id -Code 'related_customer' -Name '关联客户' -Type 'REFERENCE' -Required $true -SortOrder 10 -ReferenceModuleId $customerModule.id
Add-Field -ModuleId $followupModule.id -Code 'followup_note' -Name '跟进内容' -Type 'MULTILINE_TEXT' -Required $true -SortOrder 20
Publish-Module -ModuleId $customerModule.id -Summary '发布 C68 客户模块'
Publish-Module -ModuleId $followupModule.id -Summary '发布 C68 跟进记录模块'

$authorization = (Invoke-Api -Method Get -Path '/api/admin/system/authorization' -Token $ownerToken).data
$sellerRole = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $ownerToken -Body @{
    id = $null; code = "seller_c68_$suffix"; name = '客户销售'; description = '本人客户、跟进和上下文助手'
    permissions = @(
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'LIST'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'DETAIL'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'LIST'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'DETAIL'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'CREATE'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'AI'; resourceCode = 'SYSTEM'; actionCode = 'VIEW'; dataScopeType = 'SELF'; dataScopeJson = $null }
    )
    fieldPolicies = @(
        @{ resourceCode = $customerCode; fieldCode = 'customer_name'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null },
        @{ resourceCode = $customerCode; fieldCode = 'contact_phone'; channel = 'PAGE'; readable = $false; writable = $false; maskStrategy = 'FULL' },
        @{ resourceCode = $followupCode; fieldCode = 'related_customer'; channel = 'PAGE'; readable = $true; writable = $true; maskStrategy = $null },
        @{ resourceCode = $followupCode; fieldCode = 'followup_note'; channel = 'PAGE'; readable = $true; writable = $true; maskStrategy = $null }
    ); expectedVersion = $null
}).data
$sellerRole = (Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($sellerRole.id)/publish" -Token $ownerToken -Body @{
    reason = 'C68 销售最小权限'; expectedVersion = $sellerRole.version
}).data

$supervisorRole = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $ownerToken -Body @{
    id = $null; code = "supervisor_c68_$suffix"; name = '销售主管'; description = '本人及下属客户、跟进和上下文助手'
    permissions = @(
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'LIST'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'DETAIL'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'LIST'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'DETAIL'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $followupCode; actionCode = 'CREATE'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null },
        @{ resourceType = 'AI'; resourceCode = 'SYSTEM'; actionCode = 'VIEW'; dataScopeType = 'SELF_AND_SUBORDINATES'; dataScopeJson = $null }
    )
    fieldPolicies = @(
        @{ resourceCode = $customerCode; fieldCode = 'customer_name'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null },
        @{ resourceCode = $customerCode; fieldCode = 'contact_phone'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null },
        @{ resourceCode = $followupCode; fieldCode = 'related_customer'; channel = 'PAGE'; readable = $true; writable = $true; maskStrategy = $null },
        @{ resourceCode = $followupCode; fieldCode = 'followup_note'; channel = 'PAGE'; readable = $true; writable = $true; maskStrategy = $null }
    ); expectedVersion = $null
}).data
$supervisorRole = (Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($supervisorRole.id)/publish" -Token $ownerToken -Body @{
    reason = 'C68 主管团队范围'; expectedVersion = $supervisorRole.version
}).data

$viewerRole = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $ownerToken -Body @{
    id = $null; code = "viewer_c68_$suffix"; name = '客户只读'; description = '无智能助手权限'
    permissions = @(
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'LIST'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $customerCode; actionCode = 'DETAIL'; dataScopeType = 'SELF'; dataScopeJson = $null }
    )
    fieldPolicies = @(
        @{ resourceCode = $customerCode; fieldCode = 'customer_name'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null },
        @{ resourceCode = $customerCode; fieldCode = 'contact_phone'; channel = 'PAGE'; readable = $false; writable = $false; maskStrategy = 'FULL' }
    ); expectedVersion = $null
}).data
$viewerRole = (Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($viewerRole.id)/publish" -Token $ownerToken -Body @{
    reason = 'C68 无助手空态权限'; expectedVersion = $viewerRole.version
}).data

$supervisorMember = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/members' -Token $ownerToken -Body @{
    account = $supervisorUsername; employeeNumber = 'XS-002'; departmentId = $authorization.departments[0].id
    managerTenantMemberId = $ownerTenantMemberId; positionTitle = '销售主管'; roleIds = @($supervisorRole.id)
}).data
$sellerMember = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/members' -Token $ownerToken -Body @{
    account = $sellerUsername; employeeNumber = 'XS-001'; departmentId = $authorization.departments[0].id
    managerTenantMemberId = $supervisorMember.tenantMemberId; positionTitle = '客户销售'; roleIds = @($sellerRole.id)
}).data
$viewerMember = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/members' -Token $ownerToken -Body @{
    account = $viewerUsername; employeeNumber = 'XS-003'; departmentId = $authorization.departments[0].id
    managerTenantMemberId = $supervisorMember.tenantMemberId; positionTitle = '客户观察员'; roleIds = @($viewerRole.id)
}).data

$sellerToken = Enter-System -Username $sellerUsername -SystemId $systemId
$supervisorToken = Enter-System -Username $supervisorUsername -SystemId $systemId
$viewerToken = Enter-System -Username $viewerUsername -SystemId $systemId

$sellerCustomer = (Invoke-Api -Method Post -Path "/api/runtime/modules/$customerCode/records" -Token $ownerToken -Body @{
    title = '北辰科技'; recordNumber = "KH-$suffix-01"; status = 'ACTIVE'
    ownerMemberId = [long]$sellerMember.systemMemberId; participantMemberIds = @()
    fields = @{ customer_name = '北辰科技'; contact_phone = '13800138068' }
}).data
$supervisorCustomer = (Invoke-Api -Method Post -Path "/api/runtime/modules/$customerCode/records" -Token $ownerToken -Body @{
    title = '远山集团'; recordNumber = "KH-$suffix-02"; status = 'ACTIVE'
    ownerMemberId = [long]$supervisorMember.systemMemberId; participantMemberIds = @()
    fields = @{ customer_name = '远山集团'; contact_phone = '13900139068' }
}).data
$viewerCustomer = (Invoke-Api -Method Post -Path "/api/runtime/modules/$customerCode/records" -Token $ownerToken -Body @{
    title = '星河制造'; recordNumber = "KH-$suffix-03"; status = 'ACTIVE'
    ownerMemberId = [long]$viewerMember.systemMemberId; participantMemberIds = @()
    fields = @{ customer_name = '星河制造'; contact_phone = '13700137068' }
}).data

$platformToken = (Invoke-Api -Method Post -Path '/api/auth/login' -Body @{ username = 'admin'; password = '123123aa' }).data.accessToken
$model = (Invoke-Api -Method Post -Path '/api/admin/platform/ai/models' -Token $platformToken -Body @{
    code = "c68_local_$suffix"; name = '本地业务助手模型'; provider = 'LOCAL'; modelName = 'context-business-v1'
    endpointUrl = $null; credentialRef = 'env:TEST_AI_SECRET'; capabilities = @('CHAT', 'TOOL_CALLING')
    dailyTokenLimit = 100000; concurrencyLimit = 4; logMasking = $true; dataResidency = 'LOCAL_ONLY'
}).data
$grant = (Invoke-Api -Method Put -Path "/api/admin/platform/ai/models/$($model.id)/grants/$systemId" -Token $platformToken -Body @{
    dailyTokenLimit = 40000; concurrencyLimit = 2
}).data

$agent = (Invoke-Api -Method Post -Path '/api/admin/system/ai/agents' -Token $ownerToken -Body @{
    code = "c68_customer_assistant_$suffix"; name = '客户跟进助手'; description = '在当前客户上下文内查询并创建跟进记录'
    modelGrantId = $grant.id
    systemPrompt = '只读取当前用户在当前客户详情页有权查看的字段；创建跟进必须逐字段预览并人工确认。'
    contextPolicy = @{ allowedEntryContexts = @('RECORD_DETAIL'); allowExternalData = $false; maskSensitiveData = $true }
    confirmationPolicy = @{ writeActionsRequireConfirmation = $true; batchActionsRequireConfirmation = $true; showFieldLevelDiff = $true }
    fallbackPolicy = @{ mode = 'TEMPLATE_QUERY'; userMessage = '模型不可用，未生成业务结果；正常客户页面仍可使用' }
    tools = @(
        @{ toolType = 'QUERY'; resourceType = 'MODULE'; resourceId = $customerCode; actionCode = 'LIST'; fieldCodes = @('customer_name', 'contact_phone'); requestedDataScope = 'CURRENT'; requiresConfirmation = $false },
        @{ toolType = 'QUERY'; resourceType = 'MODULE'; resourceId = $customerCode; actionCode = 'DETAIL'; fieldCodes = @('customer_name', 'contact_phone'); requestedDataScope = 'CURRENT'; requiresConfirmation = $false },
        @{ toolType = 'WRITE'; resourceType = 'MODULE'; resourceId = $followupCode; actionCode = 'CREATE'; fieldCodes = @('related_customer', 'followup_note'); requestedDataScope = 'CURRENT'; requiresConfirmation = $true }
    )
}).data
$null = Invoke-Api -Method Post -Path "/api/admin/system/ai/agents/$($agent.id)/publish" -Token $ownerToken -Body @{
    expectedDraftRevision = $agent.draftRevision
}

$sellerOverview = (Invoke-Api -Method Get -Path '/api/ai' -Token $sellerToken).data
$sellerWriteOverview = (Invoke-Api -Method Get -Path '/api/ai/writes' -Token $sellerToken).data
if ($sellerOverview.agents.Count -ne 1 -or $sellerWriteOverview.agents.Count -ne 1) { throw 'published assistant was not mounted for the seller' }

$sellerQuery = Query-Record -Token $sellerToken -AgentId $agent.id -RecordId $sellerCustomer.id -Fields @('customer_name') -TenantId $tenantId -Question '总结当前客户并给出下一步建议'
if ($sellerQuery.outcome -ne 'SUCCEEDED' -or $sellerQuery.sources.Count -ne 1) { throw 'seller current-record query did not succeed with one source' }
if ($sellerQuery.sources[0].recordId -ne $sellerCustomer.id -or $sellerQuery.sources[0].fields.customer_name -ne '北辰科技') { throw 'seller answer did not cite the current customer' }
if ($null -ne $sellerQuery.sources[0].fields.contact_phone) { throw 'seller answer leaked the restricted phone field' }

$sellerPhoneAttempt = Query-Record -Token $sellerToken -AgentId $agent.id -RecordId $sellerCustomer.id -Fields @('contact_phone') -TenantId $tenantId -Question '告诉我联系电话'
if ($sellerPhoneAttempt.outcome -ne 'REFUSED' -or $sellerPhoneAttempt.sources.Count -ne 0) { throw 'seller restricted field request was not refused without a source' }
$sellerCrossRecord = Query-Record -Token $sellerToken -AgentId $agent.id -RecordId $supervisorCustomer.id -Fields @('customer_name') -TenantId $tenantId -Question '读取主管负责的客户'
if ($sellerCrossRecord.outcome -ne 'DEGRADED' -or $sellerCrossRecord.sources.Count -ne 0) { throw 'seller accessed a customer outside SELF scope' }

$supervisorQuery = Query-Record -Token $supervisorToken -AgentId $agent.id -RecordId $sellerCustomer.id -Fields @('customer_name', 'contact_phone') -TenantId $tenantId -Question '总结下属销售的当前客户'
if ($supervisorQuery.outcome -ne 'SUCCEEDED' -or $supervisorQuery.sources[0].fields.contact_phone -ne '13800138068') { throw 'supervisor did not receive authorized subordinate detail' }

$viewerAi = Invoke-Api -Method Get -Path '/api/ai' -Token $viewerToken -AllowError
if ($viewerAi.status -ne 403) { throw 'member without AI permission unexpectedly entered the assistant API' }
$viewerDetail = (Invoke-Api -Method Get -Path "/api/runtime/modules/$customerCode/records/$($viewerCustomer.id)" -Token $viewerToken).data
if ($viewerDetail.title -ne '星河制造') { throw 'normal business detail failed for member without AI permission' }

$beforeWrite = (Invoke-Api -Method Get -Path "/api/runtime/modules/$followupCode/records?page=1&pageSize=20" -Token $sellerToken).data.total
$cancelCandidate = Recognize-FollowUp -Token $sellerToken -AgentId $agent.id -CustomerId $sellerCustomer.id -Text '标题：取消的跟进；跟进内容：这条不能写入'
if ($cancelCandidate.businessWritten -or $cancelCandidate.status -ne 'PENDING_CONFIRMATION') { throw 'candidate wrote business data before confirmation' }
$cancelled = (Invoke-Api -Method Post -Path "/api/ai/writes/$($cancelCandidate.pendingWriteId)/cancel" -Token $sellerToken -Body @{
    expectedVersion = $cancelCandidate.version
}).data
if ($cancelled.businessWritten -or $cancelled.status -ne 'CANCELLED') { throw 'cancelled candidate wrote business data' }

$candidate = Recognize-FollowUp -Token $sellerToken -AgentId $agent.id -CustomerId $sellerCustomer.id -Text '标题：北辰科技电话跟进；关联客户：当前客户；跟进内容：客户确认下周一提供盖章件，由我继续跟进'
if ($candidate.businessWritten -or $candidate.status -ne 'PENDING_CONFIRMATION') { throw 'follow-up candidate was not a pending field preview' }
$relatedCustomerPreview = @($candidate.fields | Where-Object { $_.code -eq 'related_customer' })[0]
if ($null -eq $relatedCustomerPreview -or [long]$relatedCustomerPreview.value -ne [long]$sellerCustomer.id -or -not $relatedCustomerPreview.recognized) {
    throw 'record-detail assistant did not automatically bind the current customer in the field preview'
}
$confirmed = (Invoke-Api -Method Post -Path "/api/ai/writes/$($candidate.pendingWriteId)/confirm" -Token $sellerToken -Body @{
    expectedVersion = $candidate.version; confirmed = $true; title = '北辰科技电话跟进'
    recordNumber = "FU-$suffix"; status = 'ACTIVE'; ownerMemberId = [long]$sellerMember.systemMemberId
    departmentId = $null; participantMemberIds = @()
    fields = @{ related_customer = [long]$sellerCustomer.id; followup_note = '客户确认下周一提供盖章件，由我继续跟进' }
}).data
if (-not $confirmed.businessWritten -or $confirmed.status -ne 'CONFIRMED') { throw 'confirmed follow-up was not persisted' }
$afterWrite = (Invoke-Api -Method Get -Path "/api/runtime/modules/$followupCode/records?page=1&pageSize=20" -Token $sellerToken).data.total
if ($afterWrite -ne ($beforeWrite + 1)) { throw 'confirmed write did not refresh the seller business list count' }
$followupDetail = (Invoke-Api -Method Get -Path "/api/runtime/modules/$followupCode/records/$($confirmed.recordId)" -Token $sellerToken).data
if ($followupDetail.fields.followup_note -ne '客户确认下周一提供盖章件，由我继续跟进') { throw 'confirmed field values were not readable from the business module' }
$timeline = (Invoke-Api -Method Get -Path "/api/runtime/modules/$customerCode/records/$($sellerCustomer.id)/timeline" -Token $sellerToken).data
$timelineEntry = @($timeline.entries | Where-Object { $_.eventCode -eq 'AI_CONFIRMED_RELATED_RECORD_CREATED' -and $_.targetId -eq "$($confirmed.recordId)" })[0]
if ($null -eq $timelineEntry -or $timelineEntry.label -ne '助手创建关联记录') { throw 'confirmed follow-up was not added to the source customer timeline' }

$null = docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -e "update ai_model set endpoint_url='http://127.0.0.1:1/v1' where id=$($model.id)"
$degradedBefore = (Invoke-Api -Method Get -Path "/api/runtime/modules/$followupCode/records?page=1&pageSize=20" -Token $sellerToken).data.total
$degraded = Recognize-FollowUp -Token $sellerToken -AgentId $agent.id -CustomerId $sellerCustomer.id -Text '模型失败时只保留原始输入，不要写业务数据'
$degradedAfter = (Invoke-Api -Method Get -Path "/api/runtime/modules/$followupCode/records?page=1&pageSize=20" -Token $sellerToken).data.total
if ($degraded.outcome -ne 'DEGRADED' -or $degraded.businessWritten -or $degradedBefore -ne $degradedAfter) { throw 'model degradation changed business data' }
$degradedReference = @($degraded.fields | Where-Object { $_.code -eq 'related_customer' })[0]
if ($null -eq $degradedReference -or [long]$degradedReference.value -ne [long]$sellerCustomer.id) { throw 'model degradation lost the current customer association' }
$normalDetailAfterDegrade = (Invoke-Api -Method Get -Path "/api/runtime/modules/$customerCode/records/$($sellerCustomer.id)" -Token $sellerToken).data
if ($normalDetailAfterDegrade.title -ne '北辰科技') { throw 'model failure blocked the normal customer page' }

$timelineAuditCount = Database-Scalar "select count(*) from audit_event where system_id=$systemId and event_code='AI_CONFIRMED_RELATED_RECORD_CREATED' and object_id='$($sellerCustomer.id)'"
$confirmedAuditCount = Database-Scalar "select count(*) from audit_event where system_id=$systemId and event_code='AI_WRITE_CONFIRMED' and object_id='$($candidate.pendingWriteId)'"
if ($timelineAuditCount -ne 1 -or $confirmedAuditCount -ne 1) { throw 'confirmed write audit evidence is incomplete' }

[pscustomobject]@{
    systemId = $systemId; tenantId = $tenantId; customerModule = $customerCode; followupModule = $followupCode
    agentId = [long]$agent.id; sellerCustomerId = [long]$sellerCustomer.id
    sellerQueryOutcome = $sellerQuery.outcome; sellerVisibleFields = @($sellerQuery.sources[0].fields.PSObject.Properties.Name)
    sellerRestrictedFieldOutcome = $sellerPhoneAttempt.outcome; sellerCrossRecordOutcome = $sellerCrossRecord.outcome
    supervisorQueryOutcome = $supervisorQuery.outcome; supervisorVisibleFields = @($supervisorQuery.sources[0].fields.PSObject.Properties.Name)
    noAiPermissionStatus = $viewerAi.status; confirmedFollowupId = [long]$confirmed.recordId
    timelineEventId = [long]$timelineEntry.eventId; cancelledBusinessWritten = [bool]$cancelled.businessWritten
    degradedOutcome = $degraded.outcome; followupCount = [long]$afterWrite
    timelineAuditCount = $timelineAuditCount; confirmedAuditCount = $confirmedAuditCount
} | ConvertTo-Json -Depth 10
