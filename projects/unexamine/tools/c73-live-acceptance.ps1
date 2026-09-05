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
$authorization = Invoke-Api -Method Get -Path '/api/admin/system/authorization' -Token $owner.tokens.accessToken
$role = $authorization.roles | Where-Object name -eq '客户销售' | Select-Object -First 1
if ($null -eq $role) { throw '客户销售角色不存在' }

$customerCode = ($authorization.resources | Where-Object name -eq '客户' | Select-Object -First 1).resourceCode
if (-not $customerCode) { throw '客户模块资源不存在' }

$permissions = @($role.permissions)
foreach ($action in @('CREATE', 'UPDATE', 'ARCHIVE')) {
    if (-not ($permissions | Where-Object { $_.resourceCode -eq $customerCode -and $_.actionCode -eq $action })) {
        $permissions += [pscustomobject]@{
            resourceType = 'MODULE'
            resourceCode = $customerCode
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
        writable = if ($_.resourceCode -eq $customerCode -and $_.fieldCode -eq 'customer_name') { $true } else { $_.writable }
        maskStrategy = $_.maskStrategy
    }
})

$draft = Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $owner.tokens.accessToken -Body @{
    id = $role.id
    code = $role.code
    name = $role.name
    description = '负责本人客户的新增、维护、归档与跟进'
    permissions = $permissions
    fieldPolicies = $fieldPolicies
    expectedVersion = $role.version
}
if ($draft.status -ne 'DRAFT') { throw '角色调整后未形成待发布草稿' }

$published = Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($draft.id)/publish" -Token $owner.tokens.accessToken -Body @{
    reason = '补齐客户销售的日常客户维护闭环'
    expectedVersion = $draft.version
}
if ($published.status -ne 'ACTIVE') { throw '客户销售角色未成功发布' }

$seller = Enter-System -Username 'c68_seller_227384'
$runtime = Invoke-Api -Method Get -Path "/api/runtime/modules/$customerCode/records?page=1&pageSize=20" -Token $seller.tokens.accessToken
if ($runtime.total -lt 1) { throw '业务成员无法读取本人客户列表' }

[pscustomobject]@{
    systemId = $systemId
    moduleCode = $customerCode
    role = $published.name
    roleStatus = $published.status
    verifiedActions = @('LIST', 'DETAIL', 'CREATE', 'UPDATE', 'ARCHIVE')
    visibleRecords = $runtime.total
} | ConvertTo-Json -Depth 5
