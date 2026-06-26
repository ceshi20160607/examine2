param(
    [string]$BaseUrl = 'http://127.0.0.1:9999',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $allHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $allHeaders[$key] = $Headers[$key]
    }
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 12 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
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
    $Cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:AdminHeaders -Body @{
        reason = 'real-g8-api-e2e cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-$script:Suffix"
    }
    return [string]$Cleanup.result
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
$Password = 'Aa123456!'
$CleanupResult = 'SKIPPED'

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminAccessToken = [string]$AdminLogin.accessToken
$AdminPlatformRoles = @($AdminLogin.profile.platformRoles)
$AdminHeaders = @{
    'Authorization' = "Bearer $AdminAccessToken"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AdminAccessToken)) -Message 'Default admin login did not return an access token.'
Assert-True -Condition ($AdminPlatformRoles -contains 'PLATFORM_ROOT') -Message 'Default admin is not bound to PLATFORM_ROOT.'

$Register = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = "g8_e2e_account_$Suffix"
    mobile = "19$Suffix"
    email = "g8_$Suffix@example.com"
    password = $Password
    systemName = "G8 Real System $Suffix"
    systemCode = "g8_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}

$AccountId = [string]$Register.accountId
$AccessToken = [string]$Register.accessToken
$SystemId = [string]$Register.systemId
$SystemMemberId = [string]$Register.systemMemberId
$Headers = @{
    'Authorization' = "Bearer $AccessToken"
}

$Switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $Headers -Body @{
    systemId = $SystemId
    tenantId = $null
    reason = 'real-g8-e2e'
}
$TenantId = [string]$Switch.tenantId

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $Headers -Body @{
    name = "Business Group $Suffix"
    sort = 10
    visibleRoleIds = @()
    publishStatus = 'DRAFT'
}
$GroupId = [string]$Group.groupId

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $Headers -Body @{
    groupId = $GroupId
    moduleCode = "contract_$Suffix"
    name = "Contract Ledger $Suffix"
    status = 1
    description = 'REAL-G8 runtime module'
}
$ModuleId = [string]$Module.moduleId

$CustomerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body @{
    fieldCode = 'customerName'
    name = 'Customer Name'
    fieldType = 'TEXT'
    storageType = 'VARCHAR'
    required = $true
    sortable = $true
    dictTypeId = $null
    permissionMetadata = $null
    maskRule = 'NONE'
    importExportRule = @{
        importable = $true
        exportable = $true
        requiredOnImport = $true
        duplicateKey = 'none'
        desensitizeMode = 'none'
    }
}

$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body @{
    fieldCode = 'contractAmount'
    name = 'Contract Amount'
    fieldType = 'NUMBER'
    storageType = 'DECIMAL'
    required = $false
    sortable = $true
    dictTypeId = $null
    permissionMetadata = $null
    maskRule = 'NONE'
    importExportRule = @{
        importable = $true
        exportable = $true
        requiredOnImport = $false
        duplicateKey = 'none'
        desensitizeMode = 'none'
    }
}

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $Headers -Body @{
    sceneCode = 'default'
    sceneName = 'All Contracts'
    defaultScene = $true
    visibleRoleIds = @()
    columnFieldIds = @([string]$CustomerField.fieldId, [string]$AmountField.fieldId)
    filterFieldIds = @([string]$CustomerField.fieldId, [string]$AmountField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $Headers -Body @{
    reason = 'real-g8 module publish'
    idempotencyKey = "module-publish-$Suffix"
}

$Flow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows" -Headers $Headers -Body @{
    flowCode = "flow_contract_$Suffix"
    flowName = "Contract Approval $Suffix"
    boundModuleId = $ModuleId
    triggerRule = @{
        triggerType = 'MODULE_ACTION'
        actionCodes = @('record.submitApproval')
        conditionExpression = $null
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @(
            @{
                nodeKey = 'node_submit_review'
                nodeType = 'approval'
                nodeName = 'Submit review'
                position = @{
                    x = 120
                    y = 120
                    width = 180
                    height = 72
                }
                propertyPayload = @{}
                status = 1
            },
            @{
                nodeKey = 'node_end_passed'
                nodeType = 'end'
                nodeName = 'Approved end'
                position = @{
                    x = 380
                    y = 120
                    width = 180
                    height = 72
                }
                propertyPayload = @{}
                status = 1
            }
        )
        edges = @(
            @{
                edgeKey = 'edge_submit_end'
                sourceNodeKey = 'node_submit_review'
                targetNodeKey = 'node_end_passed'
                branchLabel = 'approved'
                conditionPayload = $null
            }
        )
    }
    description = 'REAL-G8 approval flow'
}
$FlowId = [string]$Flow.flowId

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/publish" -Headers $Headers -Body @{
    reason = 'real-g8 flow publish'
    idempotencyKey = "flow-publish-$Suffix"
}

$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $Headers -Body @{
    fieldValues = @{
        customerName = "LLC VIMPEL STROY $Suffix"
        contractAmount = 3000
    }
    childRows = @{}
    attachmentIds = @()
    draftId = $null
    sourceType = 'manual'
}
$RecordId = [string]$Record.recordId

$Search = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $Headers -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $Suffix
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @(@{ fieldCode = 'contractAmount'; direction = 'DESC' })
}
Assert-True -Condition ([int]$Search.page.total -ge 1) -Message 'Runtime record search did not return the created record.'

$Approval = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/actions/record.submitApproval" -Headers $Headers -Body @{
    parameters = @{}
    selectedRecordIds = @($RecordId)
    reason = 'real-g8 approval submit'
    sourceType = 'manual'
}
Assert-True -Condition ([bool]$Approval.accepted) -Message 'Approval action was not accepted.'

$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $Headers
Assert-True -Condition ([bool]$Detail.approvalSidebar.visible) -Message 'Approval sidebar is not visible after submit approval.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Detail.approvalSidebar.pendingTaskId)) -Message 'Approval sidebar has no pending task id.'

$Todos = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/todos/search?pageNo=1&pageSize=20" -Headers $Headers -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $null
    status = 'PENDING'
    priority = $null
    assigneeId = $AccountId
    dueRange = $null
    traceId = $null
}

$Messages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/messages/search?pageNo=1&pageSize=20" -Headers $Headers -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'active'
    timeRange = $null
    keyword = $null
}

$TodoTotal = [int]$Todos.page.total
$MessageTotal = [int]$Messages.total
Assert-True -Condition ($TodoTotal -gt 0) -Message 'Approval submit did not create a visible system todo.'
Assert-True -Condition ($MessageTotal -gt 0) -Message 'Approval submit did not create a visible system message.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    defaultAdminAccount = 'admin'
    defaultAdminRole = ($AdminPlatformRoles -join ',')
    suffix = $Suffix
    accountId = $AccountId
    systemId = $SystemId
    tenantId = $TenantId
    systemMemberId = $SystemMemberId
    moduleGroupId = $GroupId
    moduleId = $ModuleId
    moduleCode = $Module.moduleCode
    sceneId = $Scene.sceneId
    flowId = $FlowId
    recordId = $RecordId
    searchTotal = $Search.page.total
    approvalResult = $Approval.result
    pendingTaskId = $Detail.approvalSidebar.pendingTaskId
    todoTotal = $TodoTotal
    firstTodoId = if ($Todos.page.records.Count -gt 0) { $Todos.page.records[0].todoId } else { $null }
    messageTotal = $MessageTotal
    firstMessageId = if ($Messages.records.Count -gt 0) { $Messages.records[0].messageId } else { $null }
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20
