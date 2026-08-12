param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$OwnerFixturePath = "$PSScriptRoot/fixture.json",
    [string]$OutputPath = "$PSScriptRoot/task-02-members.json"
)

$ErrorActionPreference = 'Stop'

function New-Session {
    return New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body,
        [switch]$Idempotent
    )
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Idempotent) { $headers['Idempotency-Key'] = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    $arguments = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers
        WebSession = $Session; TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}

function New-OwnerSession([object]$Fixture) {
    $session = New-Session
    $null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
        account = [string]$Fixture.username; password = [string]$Fixture.password
    }
    $null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($Fixture.systemId):switch" `
        -Method POST -Body @{}
    return $session
}

function New-Role {
    param(
        [object]$Fixture,
        [string]$Suffix,
        [string]$Label,
        [string[]]$PermissionCodes,
        [string]$DataScopeId
    )
    $owner = New-OwnerSession $Fixture
    $role = Invoke-Envelope -Session $owner `
        -Path "/api/v1/systems/$($Fixture.systemId)/admin/roles" -Method POST -Idempotent -Body @{
        code = "p4c3_${Label}_$Suffix"; name = "P4-C3 $Label $Suffix"
        description = "P4-C3 task 02 authorization fixture"
    }
    $draft = Invoke-Envelope -Session $owner `
        -Path "/api/v1/systems/$($Fixture.systemId)/admin/roles/$($role.id)/draft" -Method PUT -Body @{
        name = $role.name; description = $role.description; permissionCodes = $PermissionCodes
        deniedPermissionCodes = @(); dataScopeId = $DataScopeId; version = [string]$role.version
    }
    $check = Invoke-Envelope -Session $owner `
        -Path "/api/v1/systems/$($Fixture.systemId)/admin/roles/$($role.id)/draft:check" `
        -Method POST -Idempotent -Body @{ version = [string]$draft.version }
    $null = Invoke-Envelope -Session $owner `
        -Path "/api/v1/systems/$($Fixture.systemId)/admin/roles/$($role.id)/draft:publish" `
        -Method POST -Idempotent -Body @{ version = [string]$check.version }
    return [string]$role.id
}

function New-Member {
    param(
        [object]$Fixture,
        [string]$Suffix,
        [string]$Label,
        [string]$TenantId,
        [string]$RoleId
    )
    $username = "p4c3_${Label}_$Suffix"
    $password = "P4-C3-$Label-$Suffix!"
    $member = New-Session
    $null = Invoke-Envelope -Session $member -Path '/api/v1/auth/register' -Method POST -Idempotent -Body @{
        username = $username; displayName = "P4-C3 $Label $Suffix"; password = $password
        systemName = "P4-C3 $Label home $Suffix"; systemCode = "p4c3_${Label}_home_$Suffix"
    }
    $request = Invoke-Envelope -Session $member `
        -Path "/api/v1/context/systems/$($Fixture.systemId)/access-requests" -Method POST -Idempotent -Body @{
        targetTenantId = $TenantId; reason = "P4-C3 task 02 $Label authorization verification"
    }
    $owner = New-OwnerSession $Fixture
    $null = Invoke-Envelope -Session $owner `
        -Path "/api/v1/systems/$($Fixture.systemId)/admin/access-requests/$($request.id):approve" `
        -Method POST -Idempotent -Body @{
        reason = 'Approve P4-C3 task 02 fixture'; version = [string]$request.version
        tenantIds = @($TenantId); roleIds = @($RoleId)
    }
    return [ordered]@{ username = $username; password = $password; roleId = $RoleId }
}

$fixture = Get-Content -LiteralPath $OwnerFixturePath -Raw | ConvertFrom-Json
$owner = New-OwnerSession $fixture
$tenants = Invoke-Envelope -Session $owner -Path "/api/v1/systems/$($fixture.systemId)/admin/tenants?size=200"
$tenant = @($tenants.items | Where-Object isDefault)[0]
if (-not $tenant) { $tenant = @($tenants.items)[0] }
$scopes = Invoke-Envelope -Session $owner -Path "/api/v1/systems/$($fixture.systemId)/admin/data-scopes?size=200"
$allScope = @($scopes.items | Where-Object kind -eq 'ALL')[0]
if (-not $tenant -or -not $allScope) { throw 'Default tenant or ALL scope is unavailable' }

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$basePermissions = @('system.runtime.access', 'system.workbench.view',
    'module.work_order.view', 'module.work_order.create', 'module.work_order.update')
$positivePermissions = $basePermissions + @(
    'module.customer.view', 'module.customer.create', 'module.customer.update', 'module.line_template.view')
$positiveRole = New-Role -Fixture $fixture -Suffix $suffix -Label 'member' `
    -PermissionCodes $positivePermissions -DataScopeId ([string]$allScope.id)
$restrictedRole = New-Role -Fixture $fixture -Suffix $suffix -Label 'restricted' `
    -PermissionCodes $basePermissions -DataScopeId ([string]$allScope.id)

$result = [ordered]@{
    generatedAt = (Get-Date).ToString('o'); systemId = [string]$fixture.systemId
    tenantId = [string]$tenant.id
    positive = New-Member -Fixture $fixture -Suffix $suffix -Label 'member' `
        -TenantId ([string]$tenant.id) -RoleId $positiveRole
    restricted = New-Member -Fixture $fixture -Suffix $suffix -Label 'restricted' `
        -TenantId ([string]$tenant.id) -RoleId $restrictedRole
}
[IO.File]::WriteAllText($OutputPath, ($result | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
[ordered]@{
    generatedAt = $result.generatedAt; systemId = $result.systemId
    positiveUsername = $result.positive.username; restrictedUsername = $result.restricted.username
} | ConvertTo-Json
