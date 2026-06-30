param(
    [string]$BaseUrl = 'http://127.0.0.1:9999',
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
    $allHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $allHeaders[$key] = $Headers[$key]
    }
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    try {
        $response = if ($null -eq $jsonBody) {
            Invoke-RestMethod -Method $Method -Uri $uri -Headers $allHeaders
        } else {
            Invoke-RestMethod -Method $Method -Uri $uri -Headers $allHeaders `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 16 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData) {
        return 'SKIPPED'
    }
    if ([string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:AdminHeaders) {
        return 'SKIPPED'
    }
    $cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:AdminHeaders -Body @{
        reason = 'recovery-r4-admin-breadth cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-$script:Suffix"
    }
    return [string]$cleanup.result
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystem
        } catch {
            Write-Error "Cleanup created system failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$Suffix = Get-Date -Format 'MMddHHmmss'
$CleanupResult = 'SKIPPED'

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminAccessToken = [string]$AdminLogin.accessToken
$AdminHeaders = @{ Authorization = "Bearer $AdminAccessToken" }
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AdminAccessToken)) -Message 'Default admin login did not return an access token.'

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health' -Headers $AdminHeaders
Assert-True -Condition ($Health.status -eq 'UP') -Message 'Health status is not UP.'
Assert-True -Condition ($Health.database -eq 'UP') -Message 'Database health is not UP.'
Assert-True -Condition ($Health.schema -eq 'UP') -Message 'Schema health is not UP.'
Assert-True -Condition ($Health.redis -eq 'UP') -Message 'Redis health is not UP.'

$PlatformSystemsBefore = Invoke-Api -Method 'Get' -Path '/api/v1/platform/systems?pageNo=1&pageSize=20' -Headers $AdminHeaders
$PlatformRoles = Invoke-Api -Method 'Get' -Path '/api/v1/platform/roles?pageNo=1&pageSize=20' -Headers $AdminHeaders
$IdentityProviders = Invoke-Api -Method 'Get' -Path '/api/v1/platform/identity-providers' -Headers $AdminHeaders
$ModelAuthorizations = Invoke-Api -Method 'Get' -Path '/api/v1/platform/agent/model-authorizations?pageNo=1&pageSize=20' -Headers $AdminHeaders
$PlatformLogs = Invoke-Api -Method 'Post' -Path '/api/v1/platform/logs/search?pageNo=1&pageSize=20' -Headers $AdminHeaders -Body @{
    logType = $null
    keyword = $null
    result = $null
}
$PlatformHealth = Invoke-Api -Method 'Get' -Path '/api/v1/platform/health' -Headers $AdminHeaders
Assert-True -Condition ($null -ne $PlatformSystemsBefore.records) -Message 'Platform systems list did not return records.'
Assert-True -Condition ($null -ne $PlatformRoles.records) -Message 'Platform roles list did not return records.'
Assert-True -Condition ($null -ne $IdentityProviders) -Message 'Identity provider list did not return data.'
Assert-True -Condition ($null -ne $ModelAuthorizations.records) -Message 'Model authorization list did not return records.'
Assert-True -Condition ($null -ne $PlatformLogs.records) -Message 'Platform log search did not return records.'
Assert-True -Condition ($PlatformHealth.status -eq 'UP') -Message 'Platform health is not UP.'

$CreatedSystem = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $AdminHeaders -Body @{
    systemName = "R4 Admin System $Suffix"
    systemCode = "r4adm_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$SystemId = [string]$CreatedSystem.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($SystemId)) -Message 'Platform system create did not return systemId.'

$UpdatedSystem = Invoke-Api -Method 'Patch' -Path "/api/v1/platform/systems/$SystemId" -Headers $AdminHeaders -Body @{
    systemName = "R4 Admin System $Suffix Updated"
    tenantMode = 1
    disabledReason = $null
}
Assert-True -Condition ($UpdatedSystem.systemName -like '*Updated') -Message 'Platform system update did not read back the updated name.'

$Switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $null
    reason = 'recovery-r4-admin-breadth'
}
$TenantId = [string]$Switch.tenantId
$SystemHeaders = $AdminHeaders
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'System switch did not return tenantId.'

$DepartmentsBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/org/departments" -Headers $SystemHeaders
$MembersBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/members?pageNo=1&pageSize=20" -Headers $SystemHeaders
$SystemRolesBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/roles?pageNo=1&pageSize=20" -Headers $SystemHeaders
$Tenants = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/tenants" -Headers $SystemHeaders
$DictTypesBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/dict-types?pageNo=1&pageSize=20" -Headers $SystemHeaders
$FlowsBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/flows?pageNo=1&pageSize=20" -Headers $SystemHeaders
$WorkConfigBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/work/config" -Headers $SystemHeaders
$AgentPoliciesBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/agent/policies?pageNo=1&pageSize=20" -Headers $SystemHeaders
$OpenApiAppsBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/openapi/apps?pageNo=1&pageSize=20" -Headers $SystemHeaders
$SsoPolicyBefore = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/sso/policies" -Headers $SystemHeaders
$SystemLogsBefore = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/logs/search?pageNo=1&pageSize=20" -Headers $SystemHeaders -Body @{
    logType = $null
    keyword = $null
    result = $null
}

# PowerShell folds an empty JSON array from Invoke-RestMethod into $null. Empty department trees are valid first-use state.
Assert-True -Condition ($null -ne $MembersBefore.records) -Message 'System members did not load.'
Assert-True -Condition ($null -ne $SystemRolesBefore.records) -Message 'System roles did not load.'
Assert-True -Condition (@($Tenants).Count -ge 1) -Message 'Tenant list did not load.'
Assert-True -Condition ($null -ne $DictTypesBefore.records) -Message 'Dictionary types did not load.'
Assert-True -Condition ($null -ne $FlowsBefore.records) -Message 'Flow list did not load.'
Assert-True -Condition ($null -ne $WorkConfigBefore) -Message 'Work config did not load.'
Assert-True -Condition ($null -ne $AgentPoliciesBefore.records) -Message 'Agent policies did not load.'
Assert-True -Condition ($null -ne $OpenApiAppsBefore.records) -Message 'OpenAPI apps did not load.'
Assert-True -Condition ($null -ne $SsoPolicyBefore) -Message 'System SSO policy did not load.'
Assert-True -Condition ($null -ne $SystemLogsBefore.records) -Message 'System logs did not load.'

$Department = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/org/departments" -Headers $SystemHeaders -Body @{
    parentId = $null
    deptCode = "r4_dept_$Suffix"
    deptName = "R4 Department $Suffix"
    sortOrder = 10
    status = 1
}
$DepartmentId = [string]$Department.deptId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($DepartmentId)) -Message 'Department create did not return deptId.'

$SystemRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $SystemHeaders -Body @{
    roleName = "R4 Role $Suffix"
    roleCode = "r4_role_$Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'R4 admin breadth smoke role'
}
$SystemRoleId = [string]$SystemRole.roleId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($SystemRoleId)) -Message 'System role create did not return roleId.'

$DictType = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types" -Headers $SystemHeaders -Body @{
    dictCode = "r4_status_$Suffix"
    dictName = "R4 Status $Suffix"
    dictKind = 'STATUS'
    status = 1
}
$DictTypeId = [string]$DictType.dictTypeId
$DictItem = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$DictTypeId/items" -Headers $SystemHeaders -Body @{
    parentId = $null
    itemCode = 'active'
    itemName = 'Active'
    color = '#16a34a'
    icon = 'check'
    semantic = 'success'
    sort = 10
    defaultFlag = $true
    kanbanEnabled = $true
    status = 1
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$DictItem.itemId)) -Message 'Dictionary item create did not return itemId.'

$Flow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows" -Headers $SystemHeaders -Body @{
    flowCode = "r4_flow_$Suffix"
    flowName = "R4 Flow $Suffix"
    boundModuleId = $null
    triggerRule = @{
        triggerType = 'MANUAL'
        actionCodes = @()
        conditionExpression = $null
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @()
        edges = @()
    }
    description = 'R4 admin breadth draft flow'
}
$FlowId = [string]$Flow.flowId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($FlowId)) -Message 'Flow draft create did not return flowId.'
$FlowCanvas = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/flows/$FlowId/canvas" -Headers $SystemHeaders
Assert-True -Condition ($FlowCanvas.flowId -eq $FlowId) -Message 'Flow canvas did not read back the created flow.'

$WorkField = @{
    fieldId = "r4_work_priority_$Suffix"
    fieldCode = "r4Priority$Suffix"
    fieldName = 'R4 Priority'
    fieldType = 'SELECT'
    required = $false
    listVisible = $true
    filterable = $true
    sortable = $true
    dictTypeCode = [string]$DictType.dictCode
    dictItems = @(
        @{
            itemCode = 'active'
            itemName = 'Active'
            color = '#16a34a'
            icon = 'check'
            semantic = 'success'
            sort = 10
            enabled = $true
            defaultItem = $true
        }
    )
    cardVisible = $true
    kanbanEligible = $true
    permissionCode = "work.r4.$Suffix.priority"
}
$WorkConfig = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$SystemId/work/config" -Headers $SystemHeaders -Body @{
    projectTaskFields = @($WorkField)
    plainTaskFields = @($WorkField)
    dailyReportFields = @(
        @{
            fieldId = "r4_report_note_$Suffix"
            fieldCode = "r4ReportNote$Suffix"
            fieldName = 'R4 Report Note'
            fieldType = 'TEXT'
            required = $false
            listVisible = $true
            filterable = $false
            sortable = $false
            dictTypeCode = $null
            dictItems = @()
            cardVisible = $true
            kanbanEligible = $false
            permissionCode = "work.r4.$Suffix.report"
        }
    )
    projectTaskKanban = $null
    plainTaskKanban = $null
    dailyReportAutoSourceRule = @{
        sourceTypes = @('TASK', 'TODO', 'MESSAGE', 'BUSINESS_LOG', 'APPROVAL')
        taskScope = 'CURRENT_MEMBER_PROJECT_AND_PLAIN_TASKS'
        todoScope = 'CURRENT_MEMBER_TODOS'
        messageScope = 'CURRENT_MEMBER_MESSAGES'
        logScope = 'CURRENT_MEMBER_BUSINESS_LOGS'
        approvalScope = 'CURRENT_MEMBER_APPROVALS'
        permissionPolicy = 'CURRENT_MEMBER_PERMISSION'
        manualConfirmRequired = $true
    }
    changeReason = 'recovery-r4 work config smoke'
}
Assert-True -Condition (@($WorkConfig.projectTaskFields).Count -ge 1) -Message 'Work config update did not read back project task fields.'
$WorkConfigReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/work/config" -Headers $SystemHeaders
$WorkProjectFieldCodes = @($WorkConfigReadback.projectTaskFields | ForEach-Object { [string]$_.fieldCode })
$WorkProjectFieldNames = @($WorkConfigReadback.projectTaskFields | ForEach-Object { [string]$_.fieldName })
Assert-True -Condition ($WorkProjectFieldCodes -contains [string]$WorkField['fieldCode']) -Message 'Work config GET readback did not include the saved project task field code.'
Assert-True -Condition ($WorkProjectFieldNames -contains [string]$WorkField['fieldName']) -Message 'Work config GET readback did not include the saved project task field name.'
$WorkCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/work/config/publish-check" -Headers $SystemHeaders
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$WorkCheck.traceId)) -Message 'Work config publish-check did not return traceId.'

$SsoPolicy = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$SystemId/sso/policies" -Headers $SystemHeaders -Body @{
    enabledProviderIds = @()
    tenantDomains = @("r4-$Suffix.example.test")
    orgMapping = @{
        externalDeptRoot = $DepartmentId
    }
    employeeBinding = @{
        requiredFields = @('email', 'employeeNo')
    }
    jitMemberPolicy = @{
        enabled = $true
        requireApproval = $true
    }
    noMemberFeedback = @{
        status = 'SUBMITTED'
        disabledReason = 'NO_SYSTEM_MEMBER_MAPPING'
    }
    status = 'DRAFT'
}
Assert-True -Condition (@($SsoPolicy.tenantDomains) -contains "r4-$Suffix.example.test") -Message 'System SSO policy did not read back tenant domain.'

$OpenApiApp = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/openapi/apps" -Headers $SystemHeaders -Body @{
    externalAppCode = "r4_openapi_$Suffix"
    appName = "R4 OpenAPI $Suffix"
    status = 1
    tenantId = $TenantId
    scopes = @('record:read', 'record:write')
    callbackUrl = "https://callback.example.test/r4/$Suffix"
    rateLimit = @{
        dimension = 'APP'
        windowSeconds = 60
        limit = 100
        burstLimit = 20
        overflowPolicy = 'REJECT'
        status = 1
        version = "rl_$Suffix"
    }
    secretMaterialRef = "secret-material-r4-$Suffix"
    requestedSecretVersion = 'v1'
    idempotencyKey = "openapi-create-$Suffix"
}
$OpenApiAppId = [string]$OpenApiApp.externalAppId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($OpenApiAppId)) -Message 'OpenAPI app create did not return externalAppId.'
$OpenApiRotation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/openapi/apps/$OpenApiAppId/rotate-secret" -Headers $SystemHeaders -Body @{
    newMaterialRef = "secret-material-r4-$Suffix-rotated"
    requestedVersion = 'v2'
    dualWriteValidation = $true
    switchAfterValidation = $true
    rollbackPlan = 'rollback-to-v1'
    idempotencyKey = "openapi-rotate-$Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$OpenApiRotation.jobId)) -Message 'OpenAPI rotate-secret did not return jobId.'

$AgentPolicy = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/agent/policies" -Headers $SystemHeaders -Body @{
    policyCode = "r4_agent_$Suffix"
    scope = @{
        moduleScope = @('*')
        fieldScope = @{}
        actionScope = @('read', 'statistics', 'draft_write')
        dataScopeExpression = 'CURRENT_MEMBER_PERMISSION'
        outboundLimit = @{
            maxRows = 500
            allowFileExport = $false
            allowThirdPartyWebhook = $false
        }
        desensitizePolicy = @{
            mode = 'MASK_SENSITIVE'
            maskedFields = @()
            preview = @{}
        }
        policyVersion = "policy_$Suffix"
    }
    status = 1
    idempotencyKey = "agent-policy-$Suffix"
}
$AgentPolicyId = [string]$AgentPolicy.policyId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AgentPolicyId)) -Message 'Agent policy create did not return policyId.'
$AgentCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/agent/policies/$AgentPolicyId/publish-check" -Headers $SystemHeaders
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$AgentCheck.traceId)) -Message 'Agent publish-check did not return traceId.'

$DepartmentsAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/org/departments" -Headers $SystemHeaders
$RolesAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/roles?pageNo=1&pageSize=50" -Headers $SystemHeaders
$DictTypesAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/dict-types?pageNo=1&pageSize=50" -Headers $SystemHeaders
$FlowsAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/flows?pageNo=1&pageSize=50" -Headers $SystemHeaders
$OpenApiAppsAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/openapi/apps?pageNo=1&pageSize=50" -Headers $SystemHeaders
$AgentPoliciesAfter = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/agent/policies?pageNo=1&pageSize=50" -Headers $SystemHeaders
$SystemLogsAfter = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/logs/search?pageNo=1&pageSize=50" -Headers $SystemHeaders -Body @{
    logType = $null
    keyword = "R4"
    result = $null
}

$DepartmentIds = @($DepartmentsAfter | ForEach-Object { [string]$_.deptId })
$RoleIds = @($RolesAfter.records | ForEach-Object { [string]$_.roleId })
$DictTypeIds = @($DictTypesAfter.records | ForEach-Object { [string]$_.dictTypeId })
$FlowIds = @($FlowsAfter.records | ForEach-Object { [string]$_.flowId })
$OpenApiIds = @($OpenApiAppsAfter.records | ForEach-Object { [string]$_.externalAppId })
$AgentPolicyIds = @($AgentPoliciesAfter.records | ForEach-Object { [string]$_.policyId })

Assert-True -Condition ($DepartmentIds -contains $DepartmentId) -Message 'Created department not visible in department tree readback.'
Assert-True -Condition ($RoleIds -contains $SystemRoleId) -Message 'Created role not visible in role list readback.'
Assert-True -Condition ($DictTypeIds -contains $DictTypeId) -Message 'Created dictionary type not visible in dict list readback.'
Assert-True -Condition ($FlowIds -contains $FlowId) -Message 'Created flow not visible in flow list readback.'
Assert-True -Condition ($OpenApiIds -contains $OpenApiAppId) -Message 'Created OpenAPI app not visible in OpenAPI list readback.'
Assert-True -Condition ($AgentPolicyIds -contains $AgentPolicyId) -Message 'Created Agent policy not visible in Agent policy list readback.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-007'
    suffix = $Suffix
    platform = [ordered]@{
        healthStatus = $Health.status
        platformHealthStatus = $PlatformHealth.status
        systemsBefore = $PlatformSystemsBefore.total
        rolesTotal = $PlatformRoles.total
        identityProviders = @($IdentityProviders).Count
        modelAuthorizations = $ModelAuthorizations.total
        platformLogs = $PlatformLogs.total
        createdSystemId = $SystemId
        updatedSystemName = $UpdatedSystem.systemName
    }
    system = [ordered]@{
        systemId = $SystemId
        tenantId = $TenantId
        tenants = @($Tenants).Count
        departmentId = $DepartmentId
        roleId = $SystemRoleId
        dictTypeId = $DictTypeId
        dictItemId = $DictItem.itemId
        flowId = $FlowId
        workConfigProjectFields = @($WorkConfig.projectTaskFields).Count
        workConfigReadbackProjectFields = @($WorkConfigReadback.projectTaskFields).Count
        workConfigReadbackFieldCode = [string]$WorkField['fieldCode']
        workPublishCheckTraceId = $WorkCheck.traceId
        ssoPolicyStatus = $SsoPolicy.status
        ssoTenantDomains = $SsoPolicy.tenantDomains
        openApiAppId = $OpenApiAppId
        openApiRotationJobId = $OpenApiRotation.jobId
        agentPolicyId = $AgentPolicyId
        agentPublishCheckTraceId = $AgentCheck.traceId
        systemLogsBefore = $SystemLogsBefore.total
        systemLogsAfter = $SystemLogsAfter.total
    }
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
