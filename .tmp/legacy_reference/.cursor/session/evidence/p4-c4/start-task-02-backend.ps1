param(
    [ValidateSet('prepare','restart')][string]$Phase,
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine',
    [string]$RedisHost = '192.168.0.211',
    [string]$RedisPassword = '123456',
    [int]$RedisDatabase = 10,
    [int]$Port = 18080
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot/../../../..").Path
$env:EXAMINE_DB_URL = "jdbc:mysql://${DatabaseHost}:${DatabasePort}/${DatabaseName}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:EXAMINE_DB_USERNAME = $DatabaseUsername
$env:EXAMINE_DB_PASSWORD = $DatabasePassword
$env:EXAMINE_REDIS_HOST = $RedisHost
$env:EXAMINE_REDIS_PORT = '6379'
$env:EXAMINE_REDIS_PASSWORD = $RedisPassword
$env:EXAMINE_REDIS_DATABASE = [string]$RedisDatabase
$env:EXAMINE_SERVER_PORT = [string]$Port
$env:EXAMINE_SECURE_COOKIES = 'false'
$env:EXAMINE_FLYWAY_LOCATIONS = 'filesystem:' + ($workspace -replace '\\','/') + '/sql/migration'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog = Join-Path $PSScriptRoot "backend-$Phase-$stamp.out.log"
$errLog = Join-Path $PSScriptRoot "backend-$Phase-$stamp.err.log"
$jar = Join-Path $workspace 'backend/examine-web/target/examine-web-1.0.0-SNAPSHOT.jar'
$process = Start-Process -FilePath 'D:\dev\jdk21\bin\java.exe' -ArgumentList '-jar', $jar `
    -WorkingDirectory (Join-Path $workspace 'backend') -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog -WindowStyle Hidden -PassThru
$healthy = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($process.HasExited) { break }
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/management/health" -TimeoutSec 2
        if ($health.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
$result = [ordered]@{
    phase = $Phase; pid = $process.Id; healthy = $healthy; exited = $process.HasExited
    outLog = $outLog; errLog = $errLog
}
$result | ConvertTo-Json -Depth 10
if (-not $healthy) {
    if (Test-Path $outLog) { Get-Content $outLog -Tail 80 }
    if (Test-Path $errLog) { Get-Content $errLog -Tail 80 }
    exit 1
}
