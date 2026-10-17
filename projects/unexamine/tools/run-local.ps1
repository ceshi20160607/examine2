param(
    [string]$JdkHome = 'D:\dev\jdk21',
    [string]$NodeHome = 'D:\dev\nodejs24',
    [string]$MySqlContainerName = 'mysql',
    [string]$RedisContainerName = 'redis'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Join-Path $projectRoot 'backend'
$frontendRoot = Join-Path $projectRoot 'frontend'
$logsRoot = Join-Path $projectRoot 'logs'
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..\..')).Path

python (Join-Path $workspaceRoot 'template_base\tools\tb.py') workspace-validate --workspace (Join-Path $workspaceRoot 'workspace.json') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Workspace validation failed' }
foreach ($containerName in @($MySqlContainerName, $RedisContainerName)) {
    $container = @(docker inspect $containerName | ConvertFrom-Json)[0]
    if (-not $container -or -not $container.State.Running) { throw "Existing container is not running: $containerName" }
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) { throw 'Java 21 was not found' }
if (-not (Test-Path -LiteralPath (Join-Path $NodeHome 'npm.cmd'))) { throw 'Node.js 24 was not found' }
New-Item -ItemType Directory -Force -Path $logsRoot | Out-Null

function Get-ListenerProcess([int]$port) {
    $connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $connection) { return $null }
    return Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.OwningProcess)"
}

$backendProcess = Get-ListenerProcess 9999
if ($backendProcess -and $backendProcess.CommandLine -notlike '*com.unique.unexamine.UnexamineApplication*') {
    throw 'Port 9999 is occupied by another program'
}
if (-not $backendProcess) {
    $mysql = @(docker inspect $MySqlContainerName | ConvertFrom-Json)[0]
    $entry = @($mysql.Config.Env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1)
    if ($entry.Count -ne 1) { throw 'Set APP_DB_PASSWORD for the existing MySQL container' }
    $redis = @(docker inspect $RedisContainerName | ConvertFrom-Json)[0]
    $passwordIndex = [array]::IndexOf([string[]]$redis.Config.Cmd, '--requirepass')
    if ($passwordIndex -lt 0) { throw 'Existing Redis password could not be found' }
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $env:APP_DB_PASSWORD = $entry[0].Split('=', 2)[1]
    $env:APP_REDIS_PASSWORD = $redis.Config.Cmd[$passwordIndex + 1]
    Start-Process -FilePath (Get-Command mvn).Source -WorkingDirectory $backendRoot -ArgumentList @('-q', 'spring-boot:run') `
        -RedirectStandardOutput (Join-Path $logsRoot 'backend.out.log') -RedirectStandardError (Join-Path $logsRoot 'backend.err.log') `
        -WindowStyle Hidden | Out-Null
    Remove-Item Env:APP_DB_PASSWORD, Env:APP_REDIS_PASSWORD -ErrorAction SilentlyContinue
}

$frontendProcess = Get-ListenerProcess 15173
if ($frontendProcess -and $frontendProcess.CommandLine -notlike '*projects\unexamine\frontend*') {
    throw 'Port 15173 is occupied by another program'
}
if (-not $frontendProcess) {
    $env:Path = "$NodeHome;$env:Path"
    Start-Process -FilePath (Join-Path $NodeHome 'npm.cmd') -WorkingDirectory $frontendRoot -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1') `
        -RedirectStandardOutput (Join-Path $logsRoot 'frontend.out.log') -RedirectStandardError (Join-Path $logsRoot 'frontend.err.log') `
        -WindowStyle Hidden | Out-Null
}

for ($attempt = 0; $attempt -lt 40; $attempt++) {
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:9999/actuator/health' -TimeoutSec 2
        $web = Invoke-WebRequest 'http://127.0.0.1:15173/' -TimeoutSec 2
        if ($health.status -eq 'UP' -and $web.StatusCode -eq 200) {
            Write-Output 'Frontend: http://127.0.0.1:15173/'
            Write-Output 'Backend:  http://127.0.0.1:9999/'
            exit 0
        }
    } catch { Start-Sleep -Milliseconds 500 }
}
throw "Services did not become ready; inspect $logsRoot"
