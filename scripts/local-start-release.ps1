param(
    [int]$BackendPort = 9999,
    [int]$FrontendPort = 18131,
    [string]$DbUrl = 'jdbc:mysql://192.168.0.211:3306/examine2?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true',
    [string]$DbUsername = 'examine',
    [string]$DbPassword = 'examine',
    [string]$RedisHost = '192.168.0.211',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '123456',
    [int]$RedisDatabase = 10,
    [string]$JavaExe = 'D:\java\jdk\jdk21\bin\java.exe'
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BackendDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT\backend'
$Jar = Join-Path $BackendDir 'examine-web.jar'
$LogDir = Join-Path $BackendDir 'logs'

if (-not (Test-Path $Jar)) {
    throw "Backend jar not found: $Jar"
}
if (-not (Test-Path $JavaExe)) {
    throw "Java executable not found: $JavaExe"
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Out = Join-Path $LogDir 'powershell-console.out'
$Err = Join-Path $LogDir 'powershell-console.err'
Remove-Item -LiteralPath $Out, $Err -ErrorAction SilentlyContinue

$env:UNEXAMINE_SERVER_PORT = [string]$BackendPort
$env:UNEXAMINE_DB_URL = $DbUrl
$env:UNEXAMINE_DB_USERNAME = $DbUsername
$env:UNEXAMINE_DB_PASSWORD = $DbPassword
$env:UNEXAMINE_REDIS_HOST = $RedisHost
$env:UNEXAMINE_REDIS_PORT = [string]$RedisPort
$env:UNEXAMINE_REDIS_PASSWORD = $RedisPassword
$env:UNEXAMINE_REDIS_DATABASE = [string]$RedisDatabase
$env:UNEXAMINE_ALLOW_ACCOUNT_ID_HEADER = 'false'
$env:UNEXAMINE_CORS_ALLOWED_ORIGINS = "http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort,http://127.0.0.1:5173,http://localhost:5173"

$Args = @(
    '-Xms32m',
    '-Xmx192m',
    '-XX:+UseSerialGC',
    '-jar',
    $Jar,
    "--spring.config.additional-location=optional:file:$BackendDir/"
)

$Process = Start-Process `
    -FilePath $JavaExe `
    -ArgumentList $Args `
    -WorkingDirectory $BackendDir `
    -RedirectStandardOutput $Out `
    -RedirectStandardError $Err `
    -WindowStyle Hidden `
    -PassThru

$Process.Id | Set-Content -LiteralPath (Join-Path $BackendDir 'powershell.pid') -Encoding ASCII

$Deadline = (Get-Date).AddSeconds(60)
$Health = $null
do {
    Start-Sleep -Seconds 2
    try {
        $Health = curl.exe -s "http://127.0.0.1:$BackendPort/api/v1/health"
        if ($Health -match '"status"\s*:\s*"UP"' -and $Health -match '"schema"\s*:\s*"UP"' -and $Health -match '"redis"\s*:\s*"UP"') {
            break
        }
    } catch {
        $Health = $null
    }
} while ((Get-Date) -lt $Deadline)

if (-not ($Health -match '"status"\s*:\s*"UP"' -and $Health -match '"schema"\s*:\s*"UP"' -and $Health -match '"redis"\s*:\s*"UP"')) {
    Write-Output '--- stderr ---'
    Get-Content -LiteralPath $Err -ErrorAction SilentlyContinue | Select-Object -Last 80
    Write-Output '--- stdout ---'
    Get-Content -LiteralPath $Out -ErrorAction SilentlyContinue | Select-Object -Last 80
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        Write-Output "Stopped unhealthy backend process: $($Process.Id)"
    }
    throw "Backend health check failed: $Health"
}

[ordered]@{
    backendPid = $Process.Id
    health = $Health
    backendDir = $BackendDir
} | ConvertTo-Json
