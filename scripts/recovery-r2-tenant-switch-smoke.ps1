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

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:Headers) {
        return 'SKIPPED'
    }
    $result = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:Headers -Body @{
        reason = 'recovery-r2-tenant-switch cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r2-tenant-$script:Suffix"
    }
    return [string]$result.result
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
$script:SystemId = $null
$script:Headers = $null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$Login = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AccessToken = [string]$Login.accessToken
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AccessToken)) -Message 'Default admin login did not return an access token.'
$script:Headers = @{ Authorization = "Bearer $AccessToken" }

$Created = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:Headers -Body @{
    systemName = "R2 Tenant Switch $Suffix"
    systemCode = "r2tenant_$Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$Created.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:Headers
$CreatedOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
Assert-True -Condition ($null -ne $CreatedOption) -Message 'Created system is missing from switch options.'
$TenantA = [string]$CreatedOption.tenantId

$SwitchA = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:Headers -Body @{
    systemId = $script:SystemId
    tenantId = $TenantA
    reason = 'recovery-r2 tenant switch smoke tenant A'
}
Assert-True -Condition ([string]$SwitchA.systemId -eq $script:SystemId -and [string]$SwitchA.tenantId -eq $TenantA) `
    -Message 'System switch to tenant A returned mismatched context.'

$TenantB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/tenants" -Headers $script:Headers -Body @{
    tenantCode = "tenant_b_$Suffix"
    tenantName = "Tenant B $Suffix"
    status = 1
}
$TenantBId = [string]$TenantB.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantBId)) -Message 'Second tenant create did not return tenantId.'

$Tenants = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/tenants" -Headers $script:Headers
Assert-True -Condition (@($Tenants).Count -ge 2) -Message 'Tenant list did not include both tenants.'
Assert-True -Condition (@($Tenants | Where-Object { [string]$_.tenantId -eq $TenantA }).Count -eq 1) -Message 'Tenant A missing from tenant list.'
Assert-True -Condition (@($Tenants | Where-Object { [string]$_.tenantId -eq $TenantBId }).Count -eq 1) -Message 'Tenant B missing from tenant list.'

$SwitchB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/tenant-switch" -Headers $script:Headers -Body @{
    tenantId = $TenantBId
    reason = 'recovery-r2 tenant switch smoke tenant B'
}
Assert-True -Condition ([string]$SwitchB.tenantId -eq $TenantBId) -Message 'Tenant switch to tenant B returned mismatched tenantId.'

$CurrentB = Invoke-Api -Method 'Get' -Path '/api/v1/context/current-system' -Headers $script:Headers
Assert-True -Condition ([string]$CurrentB.systemId -eq $script:SystemId -and [string]$CurrentB.tenantId -eq $TenantBId) `
    -Message "Current system mismatch after tenant B switch: $($CurrentB | ConvertTo-Json -Depth 12 -Compress)"

$GroupsB = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:Headers
$ModulesB = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules?pageNo=1&pageSize=20" -Headers $script:Headers
$TodosB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/todos/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    typeCode = $null
    keyword = $null
}
$MessagesB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/messages/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    keyword = $null
}

$SwitchBackA = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/tenant-switch" -Headers $script:Headers -Body @{
    tenantId = $TenantA
    reason = 'recovery-r2 tenant switch smoke back to tenant A'
}
$CurrentA = Invoke-Api -Method 'Get' -Path '/api/v1/context/current-system' -Headers $script:Headers
Assert-True -Condition ([string]$SwitchBackA.tenantId -eq $TenantA -and [string]$CurrentA.tenantId -eq $TenantA) `
    -Message 'Tenant switch back to tenant A did not persist.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-003'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantA = $TenantA
    tenantB = $TenantBId
    switchA = @{
        systemId = $SwitchA.systemId
        tenantId = $SwitchA.tenantId
        roles = $SwitchA.effectiveRoleIds
    }
    switchB = @{
        systemId = $SwitchB.systemId
        tenantId = $SwitchB.tenantId
        roles = $SwitchB.tenantRoleIds
    }
    currentAfterTenantB = @{
        systemId = $CurrentB.systemId
        tenantId = $CurrentB.tenantId
        memberId = $CurrentB.systemMemberId
        roles = $CurrentB.effectiveRoleIds
        permissionSnapshot = $CurrentB.permissionSnapshotSummary
    }
    currentAfterTenantA = @{
        systemId = $CurrentA.systemId
        tenantId = $CurrentA.tenantId
        memberId = $CurrentA.systemMemberId
        roles = $CurrentA.effectiveRoleIds
        permissionSnapshot = $CurrentA.permissionSnapshotSummary
    }
    tenantCount = @($Tenants).Count
    moduleGroupCountAfterTenantB = @($GroupsB).Count
    moduleCountAfterTenantB = @($ModulesB.records).Count
    todoTotalAfterTenantB = $TodosB.page.total
    messageTotalAfterTenantB = $MessagesB.total
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20
