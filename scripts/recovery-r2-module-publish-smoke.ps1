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
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function New-FieldBody {
    param(
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [bool]$Required,
        [string]$DictTypeId = $null
    )
    $storageType = switch ($Type) {
        'NUMBER' { 'DECIMAL' }
        'DATE' { 'DATETIME' }
        'DATETIME' { 'DATETIME' }
        'ATTACHMENT' { 'JSON' }
        'MULTI_SELECT' { 'JSON' }
        default { 'VARCHAR' }
    }
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = $storageType
        required = $Required
        sortable = $true
        dictTypeId = $DictTypeId
        permissionMetadata = $null
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = 'none'
            desensitizeMode = 'none'
        }
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
        reason = 'recovery-r2-module-publish cleanup created system'
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
$Password = 'Aa123456!'
$CleanupResult = 'SKIPPED'

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminAccessToken = [string]$AdminLogin.accessToken
$AdminHeaders = @{ Authorization = "Bearer $AdminAccessToken" }
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AdminAccessToken)) -Message 'Default admin login did not return an access token.'
Assert-True -Condition (@($AdminLogin.profile.platformRoles) -contains 'PLATFORM_ROOT') -Message 'Default admin is not PLATFORM_ROOT.'

$Register = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = "r2_module_admin_$Suffix"
    mobile = "18$Suffix"
    email = "r2_module_$Suffix@example.com"
    password = $Password
    systemName = "R2 Module System $Suffix"
    systemCode = "r2mod_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$AccessToken = [string]$Register.accessToken
$SystemId = [string]$Register.systemId
$Headers = @{ Authorization = "Bearer $AccessToken" }

$Switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $Headers -Body @{
    systemId = $SystemId
    tenantId = $null
    reason = 'recovery-r2-module-publish'
}
$TenantId = [string]$Switch.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'System switch did not return a tenant id.'

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $Headers -Body @{
    name = "R2 Business Group $Suffix"
    sort = 10
    visibleRoleIds = @()
    publishStatus = 'DRAFT'
}
$GroupId = [string]$Group.groupId

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $Headers -Body @{
    groupId = $GroupId
    moduleCode = "asset_$Suffix"
    name = "Asset Ledger $Suffix"
    status = 1
    description = 'Recovery R2 module publish smoke'
}
$ModuleId = [string]$Module.moduleId

$DictType = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types" -Headers $Headers -Body @{
    dictCode = "asset_status_$Suffix"
    dictName = "Asset Status $Suffix"
    dictKind = 'STATUS'
    status = 1
}
$DictTypeId = [string]$DictType.dictTypeId

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$DictTypeId/items" -Headers $Headers -Body @{
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
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$DictTypeId/items" -Headers $Headers -Body @{
    parentId = $null
    itemCode = 'paused'
    itemName = 'Paused'
    color = '#f59e0b'
    icon = 'pause'
    semantic = 'warning'
    sort = 20
    defaultFlag = $false
    kanbanEnabled = $true
    status = 1
}

$Fields = @()
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body (New-FieldBody -Code 'assetName' -Name 'Asset Name' -Type 'TEXT' -Required $true)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body (New-FieldBody -Code 'purchaseDate' -Name 'Purchase Date' -Type 'DATE' -Required $false)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body (New-FieldBody -Code 'assetStatus' -Name 'Asset Status' -Type 'SELECT' -Required $true -DictTypeId $DictTypeId)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body (New-FieldBody -Code 'assetFiles' -Name 'Asset Files' -Type 'ATTACHMENT' -Required $false)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $Headers -Body (New-FieldBody -Code 'assetNo' -Name 'Asset Number' -Type 'AUTO_NUMBER' -Required $false)

$FieldIds = @($Fields | ForEach-Object { [string]$_.fieldId })
$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $Headers -Body @{
    sceneCode = 'default'
    sceneName = 'All Assets'
    defaultScene = $true
    visibleRoleIds = @()
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$Fields[0].fieldId, [string]$Fields[2].fieldId, [string]$Fields[1].fieldId)
    sortFieldIds = @([string]$Fields[1].fieldId)
}

$Action = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/actions" -Headers $Headers -Body @{
    actionCode = 'record.submitApproval'
    actionName = 'Submit Approval'
    actionType = 'ROW'
    position = 'ROW'
    selectionRule = @{
        selectionMode = 'SINGLE'
        minSelected = 1
        maxSelected = 1
        requiredStatuses = @()
        sameTenantRequired = $true
        forbiddenReason = 'Select one record first'
    }
    permissionCode = "module.asset_$Suffix.submit"
    resultContract = @{
        resultType = 'WORKFLOW_TASK'
        returnsAuditLog = $true
        returnsAsyncTask = $false
        resultDrawer = 'approvalResultDrawer'
        traceField = 'traceId'
        userVisibleStates = @('PENDING', 'APPROVED', 'REJECTED')
    }
    enabled = $true
}

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish-check" -Headers $Headers -Body @{
    reason = 'recovery-r2 publish check'
    idempotencyKey = "module-publish-check-$Suffix"
}
Assert-True -Condition ([bool]$PublishCheck.passed) -Message "Module publish check failed: $($PublishCheck | ConvertTo-Json -Depth 12 -Compress)"

$Publish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $Headers -Body @{
    reason = 'recovery-r2 module publish'
    idempotencyKey = "module-publish-$Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Publish.version)) -Message 'Module publish did not return a version.'

$AdminListSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/list-schema" -Headers $Headers
$RuntimeListSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/list-schema?sceneCode=default" -Headers $Headers
$RuntimeSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $Headers -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $null
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @()
}

$AdminColumnCodes = @($AdminListSchema.columns | ForEach-Object { [string]$_.fieldCode })
$RuntimeColumnCodes = @($RuntimeListSchema.columns | ForEach-Object { [string]$_.fieldCode })
$ExpectedCodes = @('assetName', 'purchaseDate', 'assetStatus', 'assetFiles', 'assetNo')
foreach ($code in $ExpectedCodes) {
    Assert-True -Condition ($AdminColumnCodes -contains $code) -Message "Admin list schema missing field: $code"
    Assert-True -Condition ($RuntimeColumnCodes -contains $code) -Message "Runtime list schema missing field: $code"
}
Assert-True -Condition (@($RuntimeListSchema.toolbarActions).Count -gt 0) -Message 'Runtime schema did not expose toolbar actions.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-004'
    suffix = $Suffix
    systemId = $SystemId
    tenantId = $TenantId
    moduleGroupId = $GroupId
    moduleId = $ModuleId
    moduleCode = $Module.moduleCode
    dictTypeId = $DictTypeId
    fieldTypes = @($Fields | ForEach-Object { "$($_.fieldCode):$($_.fieldType)" })
    sceneId = $Scene.sceneId
    actionCode = $Action.actionCode
    publishCheckPassed = $PublishCheck.passed
    publishVersion = $Publish.version
    adminListColumns = $AdminColumnCodes
    runtimeListColumns = $RuntimeColumnCodes
    runtimeSearchTotal = $RuntimeSearch.page.total
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20

