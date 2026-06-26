param(
    [string]$BaseUrl = 'http://127.0.0.1:9999',
    [string]$ReleaseDir = '',
    [string]$RedisHost = '192.168.0.211',
    [int]$RedisPort = 6379,
    [string]$LoginName = 'admin',
    [string]$Password = '123123aa',
    [switch]$SkipRedisTcp,
    [switch]$CheckDeployedFrontend
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ReleaseDir)) {
    $scriptRootLooksLikeRelease = (Test-Path (Join-Path $PSScriptRoot 'frontend\config.js')) `
        -and (Test-Path (Join-Path $PSScriptRoot 'backend\application.yml'))
    if ($scriptRootLooksLikeRelease) {
        $ReleaseDir = $PSScriptRoot
    } else {
        $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
        $ReleaseDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT'
    }
}

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][bool]$Passed,
        [string]$Detail = ''
    )
    $script:checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

function Read-ErrorBody {
    param([object]$Exception)
    try {
        $stream = $Exception.Response.GetResponseStream()
        if ($null -eq $stream) {
            return $Exception.Message
        }
        $reader = [System.IO.StreamReader]::new($stream)
        return $reader.ReadToEnd()
    } catch {
        return $Exception.Message
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [string]$Method = 'GET',
        [object]$Body = $null
    )
    try {
        $args = @{
            Uri = $Uri
            Method = $Method
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $args.ContentType = 'application/json; charset=utf-8'
            $args.Body = ($Body | ConvertTo-Json -Compress -Depth 8)
        }
        $response = Invoke-WebRequest @args
        return [ordered]@{
            ok = $true
            status = [int]$response.StatusCode
            body = $response.Content
        }
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        return [ordered]@{
            ok = $false
            status = $status
            body = Read-ErrorBody $_.Exception
        }
    }
}

function Get-IndexAssets {
    param([string]$Content)
    $assets = New-Object System.Collections.Generic.List[string]
    foreach ($match in [regex]::Matches($Content, '/assets/[^"''> ]+')) {
        $assets.Add($match.Value) | Out-Null
    }
    return @($assets | Sort-Object)
}

$frontendConfig = Join-Path $ReleaseDir 'frontend\config.js'
if (Test-Path $frontendConfig) {
    $configContent = (Get-Content -LiteralPath $frontendConfig -Raw).Trim()
    Add-Check 'frontend config uses same-origin API' ($configContent -eq "window.__UNEXAMINE_API_BASE_URL__ = '';") $configContent
} else {
    Add-Check 'frontend config uses same-origin API' $false "Missing $frontendConfig"
}

$nginxConfig = Join-Path $ReleaseDir 'nginx\unexamine.conf'
if (Test-Path $nginxConfig) {
    $nginxContent = Get-Content -LiteralPath $nginxConfig -Raw
    Add-Check 'nginx proxies /api to backend 9999' ($nginxContent -match 'location\s+/api/' -and $nginxContent -match 'proxy_pass\s+http://127\.0\.0\.1:9999;') 'nginx/unexamine.conf'
} else {
    Add-Check 'nginx proxies /api to backend 9999' $false "Missing $nginxConfig"
}

if ($SkipRedisTcp) {
    Add-Check 'redis tcp reachable' $true "SKIPPED; backend health still must report redis=UP"
} else {
    $redis = Test-NetConnection -ComputerName $RedisHost -Port $RedisPort -WarningAction SilentlyContinue
    Add-Check 'redis tcp reachable' ([bool]$redis.TcpTestSucceeded) "$RedisHost`:$RedisPort"
}

if ($CheckDeployedFrontend) {
    $localIndexPath = Join-Path $ReleaseDir 'frontend\index.html'
    if (-not (Test-Path $localIndexPath)) {
        Add-Check 'deployed frontend matches release assets' $false "Missing $localIndexPath"
    } else {
        $remoteIndex = Invoke-Api -Uri "$BaseUrl/index.html"
        if (-not $remoteIndex.ok) {
            Add-Check 'deployed frontend matches release assets' $false "HTTP $($remoteIndex.status): $($remoteIndex.body)"
        } else {
            $localAssets = Get-IndexAssets (Get-Content -LiteralPath $localIndexPath -Raw)
            $remoteAssets = Get-IndexAssets $remoteIndex.body
            $matches = ($localAssets -join '|') -eq ($remoteAssets -join '|')
            Add-Check 'deployed frontend matches release assets' $matches "release=[$($localAssets -join ', ')], deployed=[$($remoteAssets -join ', ')]"
        }
    }
}

$health = Invoke-Api -Uri "$BaseUrl/api/v1/health"
if ($health.ok) {
    try {
        $healthJson = $health.body | ConvertFrom-Json
        $healthData = $healthJson.data
        $healthy = $healthData.status -eq 'UP' -and $healthData.database -eq 'UP' -and $healthData.schema -eq 'UP' -and $healthData.redis -eq 'UP'
        Add-Check 'backend health all UP' $healthy "status=$($healthData.status), database=$($healthData.database), schema=$($healthData.schema), redis=$($healthData.redis), redisError=$($healthData.redisError)"
    } catch {
        Add-Check 'backend health all UP' $false "Invalid health response: $($health.body)"
    }
} else {
    Add-Check 'backend health all UP' $false "HTTP $($health.status): $($health.body)"
}

$login = Invoke-Api -Uri "$BaseUrl/api/v1/auth/login" -Method 'POST' -Body @{
    loginName = $LoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
if ($login.ok) {
    try {
        $loginJson = $login.body | ConvertFrom-Json
        Add-Check 'admin login succeeds' ($loginJson.code -eq 'SUCCESS' -and -not [string]::IsNullOrWhiteSpace($loginJson.data.accessToken)) "requestId=$($loginJson.requestId), traceId=$($loginJson.traceId)"
    } catch {
        Add-Check 'admin login succeeds' $false "Invalid login response: $($login.body)"
    }
} else {
    Add-Check 'admin login succeeds' $false "HTTP $($login.status): $($login.body)"
}

$failed = @($checks | Where-Object { -not $_.passed })
$result = [ordered]@{
    status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    baseUrl = $BaseUrl
    releaseDir = $ReleaseDir
    checks = $checks
}

$result | ConvertTo-Json -Depth 8
if ($failed.Count -gt 0) {
    throw "Release verification failed: $($failed.name -join ', ')"
}
