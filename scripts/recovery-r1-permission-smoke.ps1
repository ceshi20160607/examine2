param(
    [string]$BaseUrl = 'http://127.0.0.1:9999'
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

function Invoke-ExpectFailure {
    param(
        [string]$Uri,
        [hashtable]$Headers = @{},
        [int]$ExpectedHttp,
        [string]$ExpectedCode
    )
    try {
        $response = Invoke-RestMethod -Uri $Uri -Headers $Headers -TimeoutSec 15
        return [pscustomobject]@{
            ok = $false
            http = 200
            code = $response.code
            note = 'unexpected success'
        }
    } catch {
        $body = $_.ErrorDetails.Message | ConvertFrom-Json
        $http = $_.Exception.Response.StatusCode.value__
        return [pscustomobject]@{
            ok = ($http -eq $ExpectedHttp -and $body.code -eq $ExpectedCode)
            http = $http
            code = $body.code
        }
    }
}

$unauthenticatedAccountMe = Invoke-ExpectFailure `
    -Uri "$BaseUrl/api/v1/account/me" `
    -ExpectedHttp 401 `
    -ExpectedCode 'AUTH_UNAUTHORIZED'

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$unique = [guid]::NewGuid().ToString('N').Substring(0, 12)
$normalAccount = "normal_$unique"
$registered = Invoke-JsonPost -Uri "$BaseUrl/api/v1/auth/register-with-system" -Body @{
    accountName = $normalAccount
    email = "$normalAccount@example.com"
    password = '123123aa'
    systemName = "Normal System $stamp"
    systemCode = "normal_$unique"
    tenantMode = 1
    templateCode = 'default'
}

$normalHeaders = @{ Authorization = "Bearer $($registered.data.accessToken)" }
$normalProfile = Invoke-RestMethod -Uri "$BaseUrl/api/v1/account/me" -Headers $normalHeaders -TimeoutSec 15
$normalPlatformSystems = Invoke-ExpectFailure `
    -Uri "$BaseUrl/api/v1/platform/systems?pageNo=1&pageSize=1" `
    -Headers $normalHeaders `
    -ExpectedHttp 403 `
    -ExpectedCode 'PERMISSION_DENIED'
$normalPlatformHealth = Invoke-ExpectFailure `
    -Uri "$BaseUrl/api/v1/platform/health" `
    -Headers $normalHeaders `
    -ExpectedHttp 403 `
    -ExpectedCode 'PERMISSION_DENIED'
$normalSwitchOptions = Invoke-RestMethod -Uri "$BaseUrl/api/v1/platform/system-switch/options" `
    -Headers $normalHeaders -TimeoutSec 15

$adminLogin = Invoke-JsonPost -Uri "$BaseUrl/api/v1/auth/login" -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.accessToken)" }
$adminPlatformSystems = Invoke-RestMethod -Uri "$BaseUrl/api/v1/platform/systems?pageNo=1&pageSize=1" `
    -Headers $adminHeaders -TimeoutSec 15

$result = [pscustomobject]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    unauthenticatedAccountMe = $unauthenticatedAccountMe
    normalPlatformSystems = $normalPlatformSystems
    normalPlatformHealth = $normalPlatformHealth
    normalSwitchOptions = [pscustomobject]@{
        ok = ($normalSwitchOptions.code -eq 'SUCCESS' -and @($normalSwitchOptions.data).Count -ge 1)
        code = $normalSwitchOptions.code
        count = @($normalSwitchOptions.data).Count
    }
    adminPlatformSystems = [pscustomobject]@{
        ok = ($adminPlatformSystems.code -eq 'SUCCESS')
        code = $adminPlatformSystems.code
        total = $adminPlatformSystems.data.total
    }
    registeredNormalAccount = [pscustomobject]@{
        code = $registered.code
        accountId = $registered.data.accountId
        systemId = $registered.data.systemId
        platformPermissions = $normalProfile.data.platformPermissions
        systemCount = @($normalProfile.data.systems).Count
    }
}

$hasFailure = (-not $result.unauthenticatedAccountMe.ok) `
    -or (-not $result.normalPlatformSystems.ok) `
    -or (-not $result.normalPlatformHealth.ok) `
    -or (-not $result.normalSwitchOptions.ok) `
    -or (-not $result.adminPlatformSystems.ok)

if ($hasFailure) {
    $result.status = 'FAIL'
}

$result | ConvertTo-Json -Depth 20
if ($result.status -ne 'PASS') {
    exit 1
}
