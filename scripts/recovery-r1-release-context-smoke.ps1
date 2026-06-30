param(
    [string]$BaseUrl = 'http://127.0.0.1:9999',
    [string]$LoginName = 'admin',
    [string]$Password = '123123aa',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [object]$Body,
        [hashtable]$Headers = @{}
    )
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType 'application/json' `
        -Body ($Body | ConvertTo-Json -Depth 20) -TimeoutSec 30
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 20
if ($health.data.status -ne 'UP' -or $health.data.database -ne 'UP' -or $health.data.schema -ne 'UP' -or $health.data.redis -ne 'UP') {
    throw "Health is not fully UP: $($health | ConvertTo-Json -Depth 20)"
}

$login = Invoke-JsonPost -Uri "$BaseUrl/api/v1/auth/login" -Body @{
    loginName = $LoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
if ($login.code -ne 'SUCCESS' -or -not $login.data.accessToken) {
    throw "Login failed: $($login | ConvertTo-Json -Depth 20)"
}

$headers = @{
    Authorization = "Bearer $($login.data.accessToken)"
    'Idempotency-Key' = "recovery-context-$stamp"
}

$script:SystemIdForCleanup = $null
function Remove-CreatedRecoverySystem {
    if ($KeepCreatedData -or -not $script:SystemIdForCleanup) {
        return 'SKIPPED'
    }
    $cleanupHeaders = @{ Authorization = $headers.Authorization }
    $result = Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/v1/platform/systems/$script:SystemIdForCleanup" `
        -Headers $cleanupHeaders -ContentType 'application/json; charset=utf-8' `
        -Body (@{ reason = 'Recovery R1 context smoke cleanup'; idempotencyKey = "cleanup-r1-context-$stamp" } | ConvertTo-Json -Depth 10) `
        -TimeoutSec 30
    return $result.code
}

trap {
    try {
        Remove-CreatedRecoverySystem | Out-Null
    } catch {
        Write-Warning "Cleanup failed after error: $($_.Exception.Message)"
    }
    break
}

$created = Invoke-JsonPost -Uri "$BaseUrl/api/v1/platform/systems" -Headers $headers -Body @{
    systemName = "Recovery Context Smoke $stamp"
    systemCode = "ctx_$stamp"
    tenantMode = 1
    templateCode = 'default'
}
if ($created.code -ne 'SUCCESS' -or -not $created.data.systemId) {
    throw "System create failed: $($created | ConvertTo-Json -Depth 20)"
}

$headers.Remove('Idempotency-Key')
$systemId = [string]$created.data.systemId
$script:SystemIdForCleanup = $systemId
$options = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/platform/system-switch/options" -Headers $headers -TimeoutSec 20
$option = @($options.data | Where-Object { [string]$_.systemId -eq $systemId }) | Select-Object -First 1
if (-not $option) {
    throw "Created system is missing from switch options: systemId=$systemId"
}
$tenantId = [string]$option.tenantId

$switched = Invoke-JsonPost -Uri "$BaseUrl/api/v1/platform/system-switch" -Headers $headers -Body @{
    systemId = $systemId
    tenantId = $tenantId
    reason = 'recovery current context persistence smoke'
}
$current = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/context/current-system" -Headers $headers -TimeoutSec 20
if ([string]$current.data.systemId -ne $systemId -or [string]$current.data.tenantId -ne $tenantId) {
    throw "Current system mismatch after switch: expected $systemId/$tenantId got $($current.data.systemId)/$($current.data.tenantId)"
}

$tenantSwitched = Invoke-JsonPost -Uri "$BaseUrl/api/v1/systems/$systemId/tenant-switch" -Headers $headers -Body @{
    tenantId = $tenantId
    reason = 'recovery tenant context persistence smoke'
}
$currentAfterTenant = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/context/current-system" -Headers $headers -TimeoutSec 20
if ([string]$currentAfterTenant.data.systemId -ne $systemId -or [string]$currentAfterTenant.data.tenantId -ne $tenantId) {
    throw "Current system mismatch after tenant switch: expected $systemId/$tenantId got $($currentAfterTenant.data.systemId)/$($currentAfterTenant.data.tenantId)"
}

$cleanupCode = Remove-CreatedRecoverySystem

[pscustomobject]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    healthStatus = $health.data.status
    healthDatabase = $health.data.database
    healthSchema = $health.data.schema
    healthRedis = $health.data.redis
    loginCode = $login.code
    accountId = $login.data.profile.accountId
    accountName = $login.data.profile.accountName
    platformRoles = $login.data.profile.platformRoles
    switchOptions = @($options.data).Count
    createdSystemId = $systemId
    createdTenantId = $tenantId
    switchCode = $switched.code
    switchSystemId = $switched.data.systemId
    switchTenantId = $switched.data.tenantId
    currentCode = $current.code
    currentSystemId = $current.data.systemId
    currentTenantId = $current.data.tenantId
    tenantSwitchCode = $tenantSwitched.code
    currentAfterTenantSystemId = $currentAfterTenant.data.systemId
    currentAfterTenantTenantId = $currentAfterTenant.data.tenantId
    cleanupCode = $cleanupCode
} | ConvertTo-Json -Depth 20
