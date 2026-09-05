$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$systemId = 44
$password = 'Correct-c68-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$apiBase$Path"
        Headers = $headers
        ContentType = 'application/json; charset=utf-8'
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 50 -Compress }
    $response = Invoke-WebRequest @parameters
    $payload = $response.Content | ConvertFrom-Json
    if ([int]$response.StatusCode -ge 400) {
        throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    $payload.data
}

function Enter-System([string]$Username) {
    $platform = Invoke-Api -Method Post -Path '/api/auth/login' -Body @{
        username = $Username
        password = $password
    }
    Invoke-Api -Method Post -Path "/api/systems/$systemId/enter" -Token $platform.accessToken -Body @{
        previousSystemId = $null
        previousTenantId = $null
    }
}

$owner = Enter-System -Username 'c68_owner_227384'
$ownerToken = $owner.tokens.accessToken
$overview = Invoke-Api -Method Get -Path '/api/admin/module-config' -Token $ownerToken
$module = $overview.modules | Where-Object name -eq '售后工单' | Select-Object -First 1
if ($null -eq $module -or $module.status -ne 'ACTIVE') { throw '售后工单模块尚未发布' }

$draft = Invoke-Api -Method Get -Path "/api/admin/module-config/modules/$($module.id)/draft" -Token $ownerToken
if ($draft.fields.Count -lt 3) { throw '售后工单字段配置不完整' }
$rulesIndexes = Invoke-Api -Method Get -Path "/api/admin/module-config/modules/$($module.id)/rules-indexes" -Token $ownerToken
$queryIndex = $rulesIndexes.indexes | Where-Object { $_.index.name -eq '工单主题快速查询' } | Select-Object -First 1
if ($null -eq $queryIndex -or -not $queryIndex.index.code.StartsWith('index_')) { throw '查询规则未由系统生成稳定编码' }

$authorization = Invoke-Api -Method Get -Path '/api/admin/system/authorization' -Token $ownerToken
$role = $authorization.roles | Where-Object name -eq '客户销售' | Select-Object -First 1
$resource = $authorization.resources | Where-Object name -eq '售后工单' | Select-Object -First 1
if ($null -eq $role -or $null -eq $resource) { throw '售后工单授权资源或客户销售角色不存在' }

$permissions = @($role.permissions | ForEach-Object {
    [pscustomobject]@{
        resourceType = $_.resourceType
        resourceCode = $_.resourceCode
        actionCode = $_.actionCode
        dataScopeType = $_.dataScopeType
        dataScopeJson = $_.dataScopeJson
    }
})
foreach ($action in @('LIST', 'DETAIL', 'CREATE', 'UPDATE', 'ARCHIVE')) {
    if (-not ($permissions | Where-Object { $_.resourceCode -eq $resource.resourceCode -and $_.actionCode -eq $action })) {
        $permissions += [pscustomobject]@{
            resourceType = 'MODULE'
            resourceCode = $resource.resourceCode
            actionCode = $action
            dataScopeType = 'SELF'
            dataScopeJson = $null
        }
    }
}

$fieldPolicies = @($role.fieldPolicies | ForEach-Object {
    [pscustomobject]@{
        resourceCode = $_.resourceCode
        fieldCode = $_.fieldCode
        channel = $_.channel
        readable = $_.readable
        writable = $_.writable
        maskStrategy = $_.maskStrategy
    }
})
foreach ($field in $draft.fields) {
    $existing = $fieldPolicies | Where-Object {
        $_.resourceCode -eq $resource.resourceCode -and $_.fieldCode -eq $field.code -and $_.channel -eq 'PAGE'
    } | Select-Object -First 1
    if ($null -eq $existing) {
        $fieldPolicies += [pscustomobject]@{
            resourceCode = $resource.resourceCode
            fieldCode = $field.code
            channel = 'PAGE'
            readable = $true
            writable = $true
            maskStrategy = $null
        }
    } else {
        $existing.readable = $true
        $existing.writable = $true
        $existing.maskStrategy = $null
    }
}

$roleDraft = Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $ownerToken -Body @{
    id = $role.id
    code = $role.code
    name = $role.name
    description = '负责本人客户与售后工单的新增、维护、归档和跟进'
    permissions = $permissions
    fieldPolicies = $fieldPolicies
    expectedVersion = $role.version
}
$publishedRole = Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($roleDraft.id)/publish" -Token $ownerToken -Body @{
    reason = '开放已发布的售后工单日常处理闭环'
    expectedVersion = $roleDraft.version
}
if ($publishedRole.status -ne 'ACTIVE') { throw '客户销售角色未成功发布' }

$seller = Enter-System -Username 'c68_seller_227384'
$catalog = Invoke-Api -Method Get -Path '/api/runtime/modules' -Token $seller.tokens.accessToken
$sellerModule = $catalog | Where-Object moduleName -eq '售后工单' | Select-Object -First 1
if ($null -eq $sellerModule) { throw '普通用户无法在运行目录找到售后工单' }

[pscustomobject]@{
    systemId = $systemId
    moduleId = $module.id
    moduleCode = $module.code
    moduleStatus = $module.status
    fieldNames = @($draft.fields.name)
    queryRule = $queryIndex.index.name
    generatedQueryCode = $queryIndex.index.code
    sellerRole = $publishedRole.name
    sellerRoleStatus = $publishedRole.status
    sellerActions = @('LIST', 'DETAIL', 'CREATE', 'UPDATE', 'ARCHIVE')
} | ConvertTo-Json -Depth 8
