param(
    [string]$JdkHome = 'D:\dev\jdk21',
    [int]$Port = 9998
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jar = Join-Path $projectRoot 'backend\target\unexamine-server-0.1.0-SNAPSHOT.jar'
$migration = (Resolve-Path (Join-Path $projectRoot 'db\migration')).Path.Replace('\', '/')
$java = Join-Path $JdkHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $jar) -or -not (Test-Path -LiteralPath $java)) {
    throw 'Built backend jar and Java 21 are required before applying pending migrations'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Temporary migration port $Port is occupied"
}
$mysql = @(docker inspect mysql | ConvertFrom-Json)[0]
$redis = @(docker inspect redis | ConvertFrom-Json)[0]
if (-not $mysql.State.Running -or -not $redis.State.Running) { throw 'Existing MySQL and Redis must be running' }
$dbEntry = @($mysql.Config.Env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1)
$redisIndex = [array]::IndexOf([string[]]$redis.Config.Cmd, '--requirepass')
if ($dbEntry.Count -ne 1 -or $redisIndex -lt 0) { throw 'Existing container credentials were not found' }
$env:APP_DB_PASSWORD = $dbEntry[0].Split('=', 2)[1]
$env:APP_REDIS_PASSWORD = $redis.Config.Cmd[$redisIndex + 1]
$logRoot = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$process = $null
try {
    # 已构建版本只负责执行新增 Flyway SQL，使用独立临时端口，不打断当前 9999 服务。
    $process = Start-Process -FilePath $java -WorkingDirectory (Join-Path $projectRoot 'backend') `
        -ArgumentList @('-jar', $jar, "--server.port=$Port", "--spring.flyway.locations=filesystem:$migration") `
        -RedirectStandardOutput (Join-Path $logRoot 'migration.out.log') `
        -RedirectStandardError (Join-Path $logRoot 'migration.err.log') -WindowStyle Hidden -PassThru
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw 'Migration process exited before becoming healthy' }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                Write-Output 'Pending Flyway migrations applied through temporary backend'
                exit 0
            }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw 'Temporary migration backend did not become healthy'
} finally {
    Remove-Item Env:APP_DB_PASSWORD, Env:APP_REDIS_PASSWORD -ErrorAction SilentlyContinue
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force }
}
