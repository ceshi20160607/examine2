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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r28 command center cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r28-$script:Suffix-$systemId"
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
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R28 Command Center $script:Suffix"
    systemCode = "r28_command_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r28 admin command center setup'
}

$AdminCommands = Invoke-Api -Method 'Get' -Path "/api/v1/command-center?systemId=$script:SystemId" -Headers $script:AdminHeaders
$AdminPlatform = @($AdminCommands.items | Where-Object { $_.route -eq '/platform/admin' }) | Select-Object -First 1
$AdminSystem = @($AdminCommands.items | Where-Object { $_.route -eq "/systems/$script:SystemId/admin" }) | Select-Object -First 1
$AdminWork = @($AdminCommands.items | Where-Object { $_.route -eq "/systems/$script:SystemId/work" }) | Select-Object -First 1
Assert-True -Condition ($null -ne $AdminPlatform -and $AdminPlatform.disabled -eq $false) -Message 'Admin command center did not expose enabled platform admin command.'
Assert-True -Condition ($null -ne $AdminSystem -and $AdminSystem.disabled -eq $false) -Message 'Admin command center did not expose enabled system admin command.'
Assert-True -Condition ($null -ne $AdminWork -and $AdminWork.disabled -eq $false) -Message 'Admin command center did not expose enabled system work command.'

$AdminKeyword = Invoke-Api -Method 'Get' -Path "/api/v1/command-center?systemId=$script:SystemId&keyword=%E5%90%8E%E5%8F%B0" -Headers $script:AdminHeaders
Assert-True -Condition (@($AdminKeyword.items | Where-Object { $_.route -eq "/systems/$script:SystemId/admin" }).Count -ge 1) -Message 'Keyword search did not return system admin command.'

$NormalLoginName = "r28_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r28_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R28 Owned $script:Suffix"
    systemCode = "r28_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$Member = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R28 Normal Member $script:Suffix"
    employeeNo = "R28NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r28_member_runtime_$script:Suffix@example.com"
    status = 1
    roleIds = @()
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($Member.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r28 normal command center setup'
}

$NormalCommands = Invoke-Api -Method 'Get' -Path "/api/v1/command-center?systemId=$script:SystemId" -Headers $NormalHeaders
$NormalDashboard = @($NormalCommands.items | Where-Object { $_.route -eq "/systems/$script:SystemId/dashboard" }) | Select-Object -First 1
$NormalWork = @($NormalCommands.items | Where-Object { $_.route -eq "/systems/$script:SystemId/work" }) | Select-Object -First 1
$NormalSystemAdmin = @($NormalCommands.items | Where-Object { $_.route -eq "/systems/$script:SystemId/admin" }) | Select-Object -First 1
$NormalPlatformAdmin = @($NormalCommands.items | Where-Object { $_.route -eq '/platform/admin' }) | Select-Object -First 1
Assert-True -Condition ($null -ne $NormalDashboard -and $NormalDashboard.disabled -eq $false) -Message 'Normal member command center did not expose enabled system dashboard command.'
Assert-True -Condition ($null -ne $NormalWork -and $NormalWork.disabled -eq $false) -Message 'Normal member command center did not expose enabled system work command.'
Assert-True -Condition ($null -ne $NormalSystemAdmin -and $NormalSystemAdmin.disabled -eq $true -and -not [string]::IsNullOrWhiteSpace($NormalSystemAdmin.disabledReason)) `
    -Message 'Normal member system admin command was not disabled with a reason.'
Assert-True -Condition ($null -ne $NormalPlatformAdmin -and $NormalPlatformAdmin.disabled -eq $true -and -not [string]::IsNullOrWhiteSpace($NormalPlatformAdmin.disabledReason)) `
    -Message 'Normal member platform admin command was not disabled with a reason.'

$NormalKeyword = Invoke-Api -Method 'Get' -Path "/api/v1/command-center?systemId=$script:SystemId&keyword=%E5%B7%A5%E4%BD%9C" -Headers $NormalHeaders
Assert-True -Condition (@($NormalKeyword.items | Where-Object { $_.route -eq "/systems/$script:SystemId/work" -and $_.disabled -eq $false }).Count -ge 1) `
    -Message 'Normal member keyword search did not return enabled work command.'

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-028'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    adminCommandCount = @($AdminCommands.items).Count
    normalCommandCount = @($NormalCommands.items).Count
    adminPlatformAdminDisabled = $AdminPlatform.disabled
    adminSystemAdminDisabled = $AdminSystem.disabled
    normalDashboardDisabled = $NormalDashboard.disabled
    normalWorkDisabled = $NormalWork.disabled
    normalSystemAdminDisabled = $NormalSystemAdmin.disabled
    normalSystemAdminDisabledReason = $NormalSystemAdmin.disabledReason
    normalPlatformAdminDisabled = $NormalPlatformAdmin.disabled
    normalPlatformAdminDisabledReason = $NormalPlatformAdmin.disabledReason
    adminKeywordCount = @($AdminKeyword.items).Count
    normalKeywordCount = @($NormalKeyword.items).Count
    traceId = $NormalCommands.traceId
    cleanup = $script:CleanupResult
}
$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r28-command-center-result.json'
$Result | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
$Result | ConvertTo-Json -Depth 50
