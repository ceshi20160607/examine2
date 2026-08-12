param(
    [Parameter(Mandatory = $true)]
    [string]$PackageRoot,
    [string]$JavaPath = 'D:\dev\jdk21\bin\java.exe',
    [string]$PythonPath = 'D:\dev\python3\python.exe'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$packagePath = (Resolve-Path -LiteralPath $PackageRoot).Path
$jarPath = Join-Path $packagePath 'backend\examine-web.jar'
$migrationPath = Join-Path $packagePath 'sql\migration'
$frontendPath = Join-Path $packagePath 'frontend'
foreach ($required in @($JavaPath, $PythonPath, $jarPath, $migrationPath, $frontendPath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Cold-start input is missing: $required"
    }
}

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Wait-Http([string]$Uri, [System.Diagnostics.Process]$Process) {
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Milliseconds 750
        if ($null -ne $Process -and $Process.HasExited) {
            throw "Process $($Process.Id) exited early with $($Process.ExitCode)"
        }
        try {
            $response = Invoke-WebRequest -Uri $Uri -TimeoutSec 3 -UseBasicParsing
            if ($response.StatusCode -eq 200) { return $response }
        } catch {
        }
    } while ((Get-Date) -lt $deadline)
    throw "HTTP endpoint did not become ready: $Uri"
}

$token = [guid]::NewGuid().ToString('N').Substring(0, 12)
$mysqlName = "examine2-cold-$token-mysql"
$redisName = "examine2-cold-$token-redis"
$mysqlPort = Get-FreePort
$redisPort = Get-FreePort
$appPort = Get-FreePort
$frontendPort = Get-FreePort
$database = "examine2_cold_$token"
$dbUser = 'examine'
$dbPassword = 'Cold-Db-Password-96@'
$dbRootPassword = 'Cold-Root-Password-96@'
$rootUsername = "cold_root_$token"
$rootPassword = 'Cold-App-Root-Password-96@'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "examine2-cold-$token"
$appProcess = $null
$frontendProcess = $null

New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
try {
    Invoke-Docker @(
        'run', '-d', '--name', $mysqlName,
        '-e', "MYSQL_DATABASE=$database",
        '-e', "MYSQL_USER=$dbUser",
        '-e', "MYSQL_PASSWORD=$dbPassword",
        '-e', "MYSQL_ROOT_PASSWORD=$dbRootPassword",
        '-p', "127.0.0.1:${mysqlPort}:3306",
        'mysql:8.0.44'
    ) | Out-Null
    Invoke-Docker @(
        'run', '-d', '--name', $redisName,
        '-p', "127.0.0.1:${redisPort}:6379",
        'redis:7.4-alpine', 'redis-server', '--appendonly', 'yes'
    ) | Out-Null

    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Milliseconds 750
        & docker exec -e "MYSQL_PWD=$dbRootPassword" $mysqlName `
            mysqladmin -uroot ping --silent *> $null
        if ($LASTEXITCODE -eq 0) { break }
    } while ((Get-Date) -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw 'Cold-start MySQL did not become ready' }

    $redisReady = (Invoke-Docker @('exec', $redisName, 'redis-cli', 'ping') |
        Select-Object -Last 1).Trim()
    if ($redisReady -ne 'PONG') { throw "Cold-start Redis returned $redisReady" }

    $env:EXAMINE_DEPLOYMENT_MODE = 'LOCAL'
    $env:EXAMINE_DB_URL = "jdbc:mysql://127.0.0.1:${mysqlPort}/${database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $env:EXAMINE_DB_USERNAME = $dbUser
    $env:EXAMINE_DB_PASSWORD = $dbPassword
    $env:EXAMINE_REDIS_HOST = '127.0.0.1'
    $env:EXAMINE_REDIS_PORT = [string]$redisPort
    $env:EXAMINE_REDIS_PASSWORD = ''
    $env:EXAMINE_SERVER_PORT = [string]$appPort
    $env:EXAMINE_BOOTSTRAP_ROOT_USERNAME = $rootUsername
    $env:EXAMINE_BOOTSTRAP_ROOT_PASSWORD = $rootPassword
    $env:EXAMINE_BOOTSTRAP_ROOT_DISPLAY_NAME = 'Cold Start Root'
    $env:EXAMINE_SECURE_COOKIES = 'false'
    $env:EXAMINE_FLYWAY_LOCATIONS = 'filesystem:' +
        ($migrationPath.Replace('\', '/'))
    $env:EXAMINE_FILE_LOCAL_ROOT = Join-Path $tempRoot 'files'

    $appProcess = Start-Process -FilePath $JavaPath -ArgumentList @('-jar', $jarPath) `
        -WorkingDirectory $packagePath `
        -RedirectStandardOutput (Join-Path $tempRoot 'app.out.log') `
        -RedirectStandardError (Join-Path $tempRoot 'app.err.log') `
        -WindowStyle Hidden -PassThru
    Wait-Http "http://127.0.0.1:${appPort}/management/health" $appProcess | Out-Null
    $healthJson = Invoke-RestMethod `
        -Uri "http://127.0.0.1:${appPort}/management/health" -TimeoutSec 5
    if ($healthJson.status -ne 'UP') { throw "Health status is $($healthJson.status)" }

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginBody = @{ account = $rootUsername; password = $rootPassword } |
        ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:${appPort}/api/v1/auth/login" `
        -Method Post -ContentType 'application/json' -Body $loginBody `
        -Headers @{ 'X-Request-ID' = [guid]::NewGuid().ToString() } `
        -WebSession $session -TimeoutSec 20
    if ($login.code -ne 'OK') { throw "Cold-start login returned $($login.code)" }

    $frontendProcess = Start-Process -FilePath $PythonPath `
        -ArgumentList @('-m', 'http.server', $frontendPort, '--bind', '127.0.0.1',
            '--directory', $frontendPath) `
        -WorkingDirectory $frontendPath `
        -RedirectStandardOutput (Join-Path $tempRoot 'frontend.out.log') `
        -RedirectStandardError (Join-Path $tempRoot 'frontend.err.log') `
        -WindowStyle Hidden -PassThru
    $frontend = Wait-Http "http://127.0.0.1:${frontendPort}/" $frontendProcess

    $migrationFiles = @(Get-ChildItem -LiteralPath $migrationPath -Filter 'V*.sql' -File).Count
    $schemaOutput = Invoke-Docker @(
        'exec', '-e', "MYSQL_PWD=$dbRootPassword", $mysqlName,
        'mysql', '-uroot', '-N', '-B', $database, '-e',
        'SELECT COUNT(*),MAX(installed_rank),(SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1),SUM(success=0) FROM flyway_schema_history;'
    )
    $schema = ([string]($schemaOutput | Select-Object -Last 1)).Trim() -split "`t"
    if ([int]$schema[0] -ne $migrationFiles -or [int]$schema[1] -ne $migrationFiles `
            -or [int]$schema[3] -ne 0) {
        throw "Flyway state did not match package migrations: $($schema -join ',')"
    }

    [pscustomobject]@{
        verifiedAt = (Get-Date).ToString('o')
        packageRoot = $packagePath
        jarSha256 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
        health = $healthJson.status
        login = $login.code
        frontendHttpStatus = $frontend.StatusCode
        migrationFiles = $migrationFiles
        appliedMigrations = [int]$schema[0]
        installedRank = [int]$schema[1]
        latestVersion = $schema[2]
        failedMigrations = [int]$schema[3]
        mysqlImage = 'mysql:8.0.44'
        redisImage = 'redis:7.4-alpine'
    } | ConvertTo-Json -Depth 8
} finally {
    foreach ($process in @($frontendProcess, $appProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $process.Id -Timeout 20 -ErrorAction SilentlyContinue
        }
    }
    foreach ($container in @($redisName, $mysqlName)) {
        & docker rm -f $container *> $null
    }
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
