param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [string]$LoginName = 'admin',
    [string]$Password = '123123aa',
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.Body = ($Body | ConvertTo-Json -Depth 20)
    }
    Invoke-RestMethod @parameters
}

$login = Invoke-Json -Method 'Post' -Uri "$BaseUrl/api/v1/auth/login" -Body @{
    loginName = $LoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
if ($login.code -ne 'SUCCESS' -or -not $login.data.accessToken) {
    throw "Login failed: $($login | ConvertTo-Json -Depth 20)"
}

$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$systems = Invoke-Json -Method 'Get' -Uri "$BaseUrl/api/v1/platform/systems?pageNo=1&pageSize=200" -Headers $headers
if ($systems.code -ne 'SUCCESS') {
    throw "System list failed: $($systems | ConvertTo-Json -Depth 20)"
}

$targets = @($systems.data.records | Where-Object {
    $_.systemName -like 'Recovery*' -or
    $_.systemName -like 'R4 Admin System*' -or
    $_.systemName -like 'R6 Data Source System*' -or
    $_.systemName -like 'R7 Work System*' -or
    $_.systemName -like 'R9 SSO Target*' -or
    $_.systemName -like 'R9 Requester Owned*' -or
    $_.systemName -like 'R10 OpenAPI Import Export*' -or
    $_.systemName -like 'R11 Agent Target*' -or
    $_.systemName -like 'R11 Member Owned*' -or
    $_.systemName -like 'R12 Platform System*' -or
    $_.systemName -like 'R12 Register System*' -or
    $_.systemName -like 'R13 Responsive System*' -or
    $_.systemName -like 'R14 Real Login Session*' -or
    $_.systemName -like 'R16 Register First Use*' -or
    $_.systemName -like 'R49 Integration*' -or
    $_.systemName -like 'R49 Member Owned*' -or
    $_.systemName -like 'R3 *' -or
    $_.systemCode -like 'ctx_*' -or
    $_.systemCode -like 'r2mod_*' -or
    $_.systemCode -like 'r3_*' -or
    $_.systemCode -like 'r4adm_*' -or
    $_.systemCode -like 'r6ds_*' -or
    $_.systemCode -like 'r7work_*' -or
    $_.systemCode -like 'r10_oie_*' -or
    $_.systemCode -like 'r11_agent_*' -or
    $_.systemCode -like 'r11_member_owned_*' -or
    $_.systemCode -like 'r12_platform_*' -or
    $_.systemCode -like 'r12_register_*' -or
    $_.systemCode -like 'r13_responsive_*' -or
    $_.systemCode -like 'r14_real_login_*' -or
    $_.systemCode -like 'r16_register_*' -or
    $_.systemCode -like 'r49_integration_*' -or
    $_.systemCode -like 'r49_owned_*' -or
    $_.systemCode -like 'r19_page_*' -or
    $_.systemCode -like 'r20_flow_*' -or
    $_.systemCode -like 'r9sso_*' -or
    $_.systemCode -like 'r9owned_*' -or
    $_.systemCode -like 'r1_*'
})

$deleted = @()
if ($Execute) {
    foreach ($system in $targets) {
        $result = Invoke-Json -Method 'Delete' -Uri "$BaseUrl/api/v1/platform/systems/$($system.systemId)" -Headers $headers -Body @{
            reason = 'Clean recovery smoke test systems'
            idempotencyKey = "cleanup-recovery-$($system.systemId)-$(Get-Date -Format 'yyyyMMddHHmmss')"
        }
        $deleted += [pscustomobject]@{
            systemId = $system.systemId
            systemName = $system.systemName
            systemCode = $system.systemCode
            result = $result.code
        }
    }
}

[pscustomobject]@{
    status = if ($Execute) { 'EXECUTED' } else { 'DRY_RUN' }
    matchedCount = @($targets).Count
    matched = @($targets | Select-Object systemId, systemName, systemCode, status)
    deleted = $deleted
} | ConvertTo-Json -Depth 20
