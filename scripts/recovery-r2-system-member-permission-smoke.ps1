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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
        throw "Expected forbidden response but request succeeded: $Method $Path"
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        if ($status -ne 403) {
            throw "Expected HTTP 403 but got HTTP ${status}: $body"
        }
        return @{
            status = $status
            body = $body
        }
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @()
    }
    $results = @()
    foreach ($systemId in @($script:TargetSystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r2-system-member-permission cleanup created system'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r2-perm-$script:Suffix-$systemId"
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

$Suffix = Get-Date -Format 'MMddHHmmss'
$Password = 'Aa123456!'
$script:TargetSystemId = $null
$script:NormalOwnedSystemId = $null
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
    systemName = "R2 Permission Target $Suffix"
    systemCode = "r2perm_target_$Suffix"
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
    reason = 'recovery-r2 permission target setup'
}

$NormalLoginName = "r2_normal_$Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$Suffix"
    email = "r2_perm_$Suffix@example.com"
    password = $Password
    systemName = "R2 Permission Owned $Suffix"
    systemCode = "r2perm_owned_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$Role = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "Normal Member $Suffix"
    roleCode = 'SYSTEM_MEMBER'
    roleType = 'SYSTEM_MEMBER'
    status = 1
    description = 'Recovery R2 normal member permission negative role'
}

$Member = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "Normal Member $Suffix"
    employeeNo = "NM$Suffix"
    mobile = "16$Suffix"
    email = "normal_member_$Suffix@example.com"
    status = 1
    roleIds = @([string]$Role.roleId)
}

$Binding = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members/$($Member.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r2 normal member switch'
}
Assert-True -Condition (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_MEMBER') -Message 'Normal switch did not include SYSTEM_MEMBER.'
Assert-True -Condition (-not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_ADMIN') -and -not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) `
    -Message "Normal member unexpectedly has admin role: $($NormalSwitch.effectiveRoleIds -join ',')"

$AllowedNavigation = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/module-groups" -Headers $NormalHeaders

$ForbiddenMemberList = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/members?pageNo=1&pageSize=20" -Headers $NormalHeaders
$ForbiddenModuleCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules" -Headers $NormalHeaders -Body @{
    groupId = $null
    moduleCode = "forbidden_$Suffix"
    name = "Forbidden Module $Suffix"
    status = 1
    description = 'This request must be rejected for a normal member.'
}
$ForbiddenRoleCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $NormalHeaders -Body @{
    roleName = "Forbidden Role $Suffix"
    roleCode = "FORBIDDEN_$Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'This request must be rejected for a normal member.'
}

$CleanupResult = Remove-CreatedSystems

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-003'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    targetSystemId = $script:TargetSystemId
    targetTenantId = $TargetTenantId
    normalAccount = $NormalLoginName
    normalOwnedSystemId = $script:NormalOwnedSystemId
    normalMemberId = $Member.systemMemberId
    normalBindingId = $Binding.bindingId
    normalEffectiveRoles = $NormalSwitch.effectiveRoleIds
    allowedNavigationGroupCount = @($AllowedNavigation).Count
    forbidden = @{
        memberList = $ForbiddenMemberList.status
        moduleCreate = $ForbiddenModuleCreate.status
        roleCreate = $ForbiddenRoleCreate.status
    }
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20
