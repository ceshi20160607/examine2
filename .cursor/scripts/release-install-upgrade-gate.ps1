param(
    [string]$Root = '',
    [string]$EvidenceDirectory = '',
    [string]$TargetCycle = 'CYCLE-RELEASE-HARDENING-117',
    [string]$TargetPackageRelative = '.cursor\session\packages\CYCLE-RELEASE-HARDENING-117\20260807-180915',
    [string]$TargetArchiveRelative = '.cursor\session\packages\CYCLE-RELEASE-HARDENING-117\CYCLE-RELEASE-HARDENING-117-20260807-180915.zip',
    [string]$TargetArchiveSha256 = 'e2fb087a23ee7479502bbf7ea1f111ded6e830591c2af1ec06ce09dbfdee2f0a',
    [int]$TargetMigrationCount = 112,
    [string]$TargetLatestVersion = '8.91.0',
    [string]$BaselineCycle = 'CYCLE-DATASOURCE-MODULE-108',
    [string]$BaselinePackageRelative = '.cursor\session\packages\CYCLE-DATASOURCE-MODULE-108\20260806-123307',
    [string]$BaselineArchiveRelative = '.cursor\session\packages\CYCLE-DATASOURCE-MODULE-108\CYCLE-DATASOURCE-MODULE-108-20260806-123307.zip',
    [string]$BaselineArchiveSha256 = 'c8d63a64f12f6dbfdef71bfb1fc546c9fc7f30d8d92924288cb3044ca2b64128',
    [int]$BaselineMigrationCount = 100,
    [string]$BaselineLatestVersion = '8.78.0'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
} else {
    $Root = (Resolve-Path $Root).Path
}

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $Root '.cursor\session\evidence\release-117\install-upgrade'
}

$java = 'D:\dev\jdk21\bin\java.exe'
$cleanPackage = Join-Path $Root $TargetPackageRelative
$cleanArchive = Join-Path $Root $TargetArchiveRelative
$baselinePackage = Join-Path $Root $BaselinePackageRelative
$baselineArchive = Join-Path $Root $BaselineArchiveRelative

$cleanMysql = 'examine2-rel116-clean-mysql'
$cleanRedis = 'examine2-rel116-clean-redis'
$upgradeMysql = 'examine2-rel116-upgrade-mysql'
$upgradeRedis = 'examine2-rel116-upgrade-redis'
$containerNames = @($cleanMysql, $cleanRedis, $upgradeMysql, $upgradeRedis)

$cleanMysqlPort = 24316
$cleanRedisPort = 27316
$cleanAppPort = 28316
$upgradeMysqlPort = 24317
$upgradeRedisPort = 27317
$upgradeAppPort = 28317
$ports = @($cleanMysqlPort, $cleanRedisPort, $cleanAppPort, $upgradeMysqlPort, $upgradeRedisPort, $upgradeAppPort)

$dbUser = 'examine'
$dbPassword = 'Rel116-Db-Password-96@'
$dbRootPassword = 'Rel116-Db-Root-Password-96@'
$rootUsername = 'rel116_root'
$rootPassword = 'Rel116-Root-Password-96@'

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('examine2-rel116-install-upgrade-' + [guid]::NewGuid().ToString('N'))
$processes = New-Object System.Collections.Generic.List[System.Diagnostics.Process]
$resultPath = Join-Path $EvidenceDirectory 'result.json'

function Assert-Equal([object]$Actual, [object]$Expected, [string]$Label) {
    if ([string]$Actual -ne [string]$Expected) {
        throw "$Label expected '$Expected' but was '$Actual'"
    }
}

function Assert-True([bool]$Condition, [string]$Label) {
    if (-not $Condition) {
        throw "Assertion failed: $Label"
    }
}

function Assert-PortFree([int]$Port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($null -ne $listener) {
        throw "Port $Port is already in use by PID $($listener.OwningProcess)"
    }
}

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Start-DatabasePair(
    [string]$MysqlName,
    [int]$MysqlPort,
    [string]$RedisName,
    [int]$RedisPort,
    [string]$DatabaseName
) {
    Invoke-Docker @(
        'run', '-d', '--name', $MysqlName,
        '-e', "MYSQL_DATABASE=$DatabaseName",
        '-e', "MYSQL_USER=$dbUser",
        '-e', "MYSQL_PASSWORD=$dbPassword",
        '-e', "MYSQL_ROOT_PASSWORD=$dbRootPassword",
        '-p', "127.0.0.1:${MysqlPort}:3306",
        'mysql:8.4.10'
    ) | Out-Null
    Invoke-Docker @(
        'run', '-d', '--name', $RedisName,
        '-p', "127.0.0.1:${RedisPort}:6379",
        'redis:7.4-alpine', 'redis-server', '--appendonly', 'yes'
    ) | Out-Null

    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Milliseconds 750
        & docker exec -e "MYSQL_PWD=$dbRootPassword" $MysqlName mysqladmin -uroot ping --silent *> $null
        if ($LASTEXITCODE -eq 0) { break }
    } while ((Get-Date) -lt $deadline)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL container $MysqlName did not become ready"
    }

    $redisReady = (Invoke-Docker @('exec', $RedisName, 'redis-cli', 'ping') | Select-Object -Last 1).Trim()
    Assert-Equal $redisReady 'PONG' "$RedisName readiness"
}

function Start-App(
    [string]$PackageRoot,
    [int]$MysqlPort,
    [int]$RedisPort,
    [int]$AppPort,
    [string]$DatabaseName,
    [string]$LogPrefix
) {
    $env:EXAMINE_DB_URL = "jdbc:mysql://127.0.0.1:${MysqlPort}/${DatabaseName}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $env:EXAMINE_DB_USERNAME = $dbUser
    $env:EXAMINE_DB_PASSWORD = $dbPassword
    $env:EXAMINE_REDIS_HOST = '127.0.0.1'
    $env:EXAMINE_REDIS_PORT = [string]$RedisPort
    $env:EXAMINE_REDIS_PASSWORD = ''
    $env:EXAMINE_SERVER_PORT = [string]$AppPort
    $env:EXAMINE_BOOTSTRAP_ROOT_USERNAME = $rootUsername
    $env:EXAMINE_BOOTSTRAP_ROOT_PASSWORD = $rootPassword
    $env:EXAMINE_BOOTSTRAP_ROOT_DISPLAY_NAME = 'Release Gate Root'
    $env:EXAMINE_SECURE_COOKIES = 'false'
    $env:EXAMINE_FLYWAY_LOCATIONS = 'filesystem:' + ((Join-Path $PackageRoot 'sql\migration').Replace('\', '/'))
    $env:EXAMINE_FILE_LOCAL_ROOT = Join-Path $tempRoot ("files-$LogPrefix")

    $stdout = Join-Path $tempRoot "$LogPrefix.out.log"
    $stderr = Join-Path $tempRoot "$LogPrefix.err.log"
    $jar = Join-Path $PackageRoot 'backend\examine-web.jar'
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) `
        -WorkingDirectory $PackageRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -WindowStyle Hidden -PassThru
    $processes.Add($process)

    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Milliseconds 750
        if ($process.HasExited) {
            $tail = if (Test-Path $stdout) { (Get-Content $stdout -Tail 40) -join [Environment]::NewLine } else { '' }
            $errTail = if (Test-Path $stderr) { (Get-Content $stderr -Tail 40) -join [Environment]::NewLine } else { '' }
            throw "Application $LogPrefix exited early ($($process.ExitCode)). stdout: $tail stderr: $errTail"
        }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:${AppPort}/management/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { return $process }
        } catch {
        }
    } while ((Get-Date) -lt $deadline)
    throw "Application $LogPrefix did not become healthy before timeout"
}

function Stop-App([System.Diagnostics.Process]$Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        Wait-Process -Id $Process.Id -Timeout 20 -ErrorAction SilentlyContinue
    }
}

function Invoke-MySqlScalar([string]$Container, [string]$Database, [string]$Query) {
    $output = Invoke-Docker @(
        'exec', '-e', "MYSQL_PWD=$dbRootPassword", $Container,
        'mysql', '-uroot', '-N', '-B', $Database, '-e', $Query
    )
    return ([string]($output | Select-Object -Last 1)).Trim()
}

function Get-SchemaState([string]$Container, [string]$Database) {
    return [ordered]@{
        appliedCount = [int](Invoke-MySqlScalar $Container $Database 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1')
        latestInstalledRank = [int](Invoke-MySqlScalar $Container $Database 'SELECT MAX(installed_rank) FROM flyway_schema_history WHERE success=1')
        latestVersion = Invoke-MySqlScalar $Container $Database 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1'
        failedCount = [int](Invoke-MySqlScalar $Container $Database 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0')
        applicationTableCount = [int](Invoke-MySqlScalar $Container $Database "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'un_%'")
    }
}

function New-Session([int]$Port) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $body = @{ account = $rootUsername; password = $rootPassword } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:${Port}/api/v1/auth/login" -Method Post `
        -ContentType 'application/json' -Headers @{ 'X-Request-ID' = [guid]::NewGuid().ToString() } `
        -Body $body -WebSession $session -TimeoutSec 20
    Assert-Equal $login.code 'OK' "login on $Port"
    return [pscustomobject]@{ WebSession = $session; Login = $login.data }
}

function Invoke-Api(
    [object]$Session,
    [int]$Port,
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [string]$IdempotencyKey = ''
) {
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if (-not [string]::IsNullOrWhiteSpace($IdempotencyKey)) {
        $headers['Idempotency-Key'] = $IdempotencyKey
    }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $cookies = $Session.WebSession.Cookies.GetCookies("http://127.0.0.1:${Port}/")
        $csrf = $cookies['EXAMINE_CSRF']
        if ($null -ne $csrf) { $headers['X-CSRF-Token'] = $csrf.Value }
    }

    $arguments = @{
        Uri = "http://127.0.0.1:${Port}${Path}"
        Method = $Method
        Headers = $headers
        WebSession = $Session.WebSession
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    $response = Invoke-RestMethod @arguments
    Assert-Equal $response.code 'OK' "$Method $Path"
    return $response.data
}

function Create-System([object]$Session, [int]$Port, [string]$Code, [string]$Name) {
    return Invoke-Api $Session $Port 'POST' '/api/v1/platform/admin/systems' @{
        code = $Code
        name = $Name
        description = 'Durable install/upgrade release-gate sentinel'
        tenantMode = 'SINGLE'
    } ([guid]::NewGuid().ToString())
}

function Sorted-Strings([object[]]$Values) {
    return @($Values | ForEach-Object { [string]$_ } | Sort-Object)
}

function Assert-StringArraysEqual([object[]]$Actual, [object[]]$Expected, [string]$Label) {
    $actualText = (Sorted-Strings $Actual) -join "`n"
    $expectedText = (Sorted-Strings $Expected) -join "`n"
    Assert-Equal $actualText $expectedText $Label
}

function Assert-StringArrayContainsAll([object[]]$Actual, [object[]]$Expected, [string]$Label) {
    $actualSet = @(Sorted-Strings $Actual)
    $missing = @(Sorted-Strings $Expected | Where-Object { $_ -notin $actualSet })
    Assert-Equal $missing.Count 0 "$Label; missing values: $($missing -join ', ')"
}

New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

$result = [ordered]@{
    gate = 'REL117_INSTALL_UPGRADE'
    verdict = 'FAIL'
    executedAt = (Get-Date).ToString('o')
    targetPackage = [ordered]@{}
    baselineSelection = [ordered]@{}
    cleanInstall = [ordered]@{}
    upgrade = [ordered]@{}
    cleanup = [ordered]@{}
}

try {
    Assert-True (Test-Path $java -PathType Leaf) 'Java 21 executable exists'
    foreach ($path in @($cleanPackage, $cleanArchive, $baselinePackage, $baselineArchive)) {
        Assert-True (Test-Path $path) "required package artifact exists: $path"
    }
    foreach ($port in $ports) { Assert-PortFree $port }
    foreach ($name in $containerNames) {
        $existing = & docker ps -a --filter "name=^/${name}$" --format '{{.Names}}'
        if ($existing -contains $name) { throw "Container $name already exists" }
    }

    $cleanHash = (Get-FileHash -Algorithm SHA256 $cleanArchive).Hash.ToLowerInvariant()
    $baselineHash = (Get-FileHash -Algorithm SHA256 $baselineArchive).Hash.ToLowerInvariant()
    Assert-Equal $cleanHash $TargetArchiveSha256 "$TargetCycle archive SHA-256"
    Assert-Equal $baselineHash $BaselineArchiveSha256 "$BaselineCycle archive SHA-256"

    $acceptedPackageDirectories = @(Get-ChildItem (Join-Path $Root '.cursor\session\packages') -Directory | Sort-Object Name | ForEach-Object Name)
    Assert-True ($acceptedPackageDirectories -contains $BaselineCycle) "$BaselineCycle package directory is present"
    Assert-Equal ((Get-ChildItem (Join-Path $baselinePackage 'sql\migration') -File).Count) $BaselineMigrationCount "$BaselineCycle migration file count"
    Assert-Equal ((Get-ChildItem (Join-Path $cleanPackage 'sql\migration') -File).Count) $TargetMigrationCount "$TargetCycle migration file count"
    Assert-True ($TargetMigrationCount -gt $BaselineMigrationCount) 'target migration count is newer than baseline'
    $migrationDelta = $TargetMigrationCount - $BaselineMigrationCount

    $result.targetPackage = [ordered]@{
        cycle = $TargetCycle
        archive = $TargetArchiveRelative.Replace('\', '/')
        sha256 = $cleanHash
        migrationFiles = $TargetMigrationCount
        latestVersion = $TargetLatestVersion
    }
    $result.baselineSelection = [ordered]@{
        cycle = $BaselineCycle
        reason = "oldest accepted executable package currently retained under .cursor/session/packages; matched backend binary and $BaselineMigrationCount-migration set"
        archive = $BaselineArchiveRelative.Replace('\', '/')
        sha256 = $baselineHash
        migrationFiles = $BaselineMigrationCount
        historicalPackageDirectories = $acceptedPackageDirectories
        earliestSupportedVersion = $BaselineLatestVersion
    }

    # Clean install and application restart read-back.
    Start-DatabasePair $cleanMysql $cleanMysqlPort $cleanRedis $cleanRedisPort 'examine2_rel116_clean'
    $cleanApp = Start-App $cleanPackage $cleanMysqlPort $cleanRedisPort $cleanAppPort 'examine2_rel116_clean' 'clean-first'
    $cleanSchema = Get-SchemaState $cleanMysql 'examine2_rel116_clean'
    Assert-Equal $cleanSchema.appliedCount $TargetMigrationCount 'clean install applied migrations'
    Assert-Equal $cleanSchema.latestInstalledRank $TargetMigrationCount 'clean install latest rank'
    Assert-Equal $cleanSchema.latestVersion $TargetLatestVersion 'clean install latest version'
    Assert-Equal $cleanSchema.failedCount 0 'clean install failed migration count'

    $cleanSession = New-Session $cleanAppPort
    Assert-Equal $cleanSession.Login.context.type 'PLATFORM' 'clean install login context'
    Assert-True ($cleanSession.Login.context.permissions -contains 'platform.system.manage') 'clean root has platform.system.manage'
    $cleanCreated = Create-System $cleanSession $cleanAppPort 'rel116-clean-sentinel' 'REL116 Clean Sentinel'
    $cleanSystemId = [string]$cleanCreated.id
    Assert-True (-not [string]::IsNullOrWhiteSpace($cleanSystemId)) 'clean install system was created'
    Stop-App $cleanApp

    $cleanRestart = Start-App $cleanPackage $cleanMysqlPort $cleanRedisPort $cleanAppPort 'examine2_rel116_clean' 'clean-restart'
    $cleanRestartSession = New-Session $cleanAppPort
    $cleanList = Invoke-Api $cleanRestartSession $cleanAppPort 'GET' '/api/v1/platform/admin/systems?page=1&size=20'
    $cleanReadBack = @($cleanList.items | Where-Object { [string]$_.id -eq $cleanSystemId })
    Assert-Equal $cleanReadBack.Count 1 'clean restart durable system read-back count'
    Assert-Equal $cleanReadBack[0].code 'rel116-clean-sentinel' 'clean restart durable system code'
    $cleanRestartSchema = Get-SchemaState $cleanMysql 'examine2_rel116_clean'
    Assert-Equal $cleanRestartSchema.appliedCount $TargetMigrationCount 'clean restart migration count remains stable'
    Assert-Equal $cleanRestartSchema.latestVersion $TargetLatestVersion 'clean restart latest version remains stable'
    Assert-Equal $cleanRestartSchema.failedCount 0 'clean restart failed migration count'
    Stop-App $cleanRestart

    $result.cleanInstall = [ordered]@{
        verdict = 'PASS'
        mysqlImage = 'mysql:8.4.10'
        redisImage = 'redis:7.4-alpine'
        backend = 'packaged backend/examine-web.jar'
        health = 'UP'
        schema = $cleanSchema
        restartSchema = $cleanRestartSchema
        representativeApi = [ordered]@{
            login = '200/OK PLATFORM'
            createSystem = '200/OK'
            readAfterRestart = '200/OK exact id/code match'
            durableSystemId = $cleanSystemId
            durableSystemCode = 'rel116-clean-sentinel'
        }
    }

    # Start the oldest retained accepted binary, seed through its API,
    # then upgrade the same database with the target package.
    Start-DatabasePair $upgradeMysql $upgradeMysqlPort $upgradeRedis $upgradeRedisPort 'examine2_rel116_upgrade'
    $baselineApp = Start-App $baselinePackage $upgradeMysqlPort $upgradeRedisPort $upgradeAppPort 'examine2_rel116_upgrade' 'upgrade-baseline'
    $baselineSchema = Get-SchemaState $upgradeMysql 'examine2_rel116_upgrade'
    Assert-Equal $baselineSchema.appliedCount $BaselineMigrationCount 'baseline applied migrations'
    Assert-Equal $baselineSchema.latestInstalledRank $BaselineMigrationCount 'baseline latest rank'
    Assert-Equal $baselineSchema.latestVersion $BaselineLatestVersion 'baseline latest version'
    Assert-Equal $baselineSchema.failedCount 0 'baseline failed migration count'

    $baselineSession = New-Session $upgradeAppPort
    $baselineAccountId = [string]$baselineSession.Login.account.id
    $upgradeCreated = Create-System $baselineSession $upgradeAppPort 'rel116-upgrade-sentinel' 'REL116 Upgrade Sentinel'
    $upgradeSystemId = [string]$upgradeCreated.id
    $baselineSystemContext = Invoke-Api $baselineSession $upgradeAppPort 'POST' "/api/v1/context/systems/${upgradeSystemId}:switch"
    Assert-Equal $baselineSystemContext.context.type 'SYSTEM' 'baseline switched context type'
    Assert-Equal ([string]$baselineSystemContext.context.systemId) $upgradeSystemId 'baseline switched system id'
    $baselinePermissions = Sorted-Strings $baselineSystemContext.context.permissions
    $baselineShells = Sorted-Strings $baselineSystemContext.context.shells
    Assert-True ($baselinePermissions.Count -gt 0) 'baseline system permissions are nonempty'
    Assert-True ($baselineShells -contains 'SYSTEM_ADMIN') 'baseline root has SYSTEM_ADMIN shell'
    Stop-App $baselineApp

    $targetApp = Start-App $cleanPackage $upgradeMysqlPort $upgradeRedisPort $upgradeAppPort 'examine2_rel116_upgrade' 'upgrade-target'
    $upgradedSchema = Get-SchemaState $upgradeMysql 'examine2_rel116_upgrade'
    Assert-Equal $upgradedSchema.appliedCount $TargetMigrationCount 'upgraded applied migrations'
    Assert-Equal $upgradedSchema.latestInstalledRank $TargetMigrationCount 'upgraded latest rank'
    Assert-Equal $upgradedSchema.latestVersion $TargetLatestVersion 'upgraded latest version'
    Assert-Equal $upgradedSchema.failedCount 0 'upgraded failed migration count'
    $upgradeRangeQuery = "SELECT COUNT(*) FROM flyway_schema_history WHERE installed_rank BETWEEN $($BaselineMigrationCount + 1) AND $TargetMigrationCount AND success=1"
    Assert-Equal ([int](Invoke-MySqlScalar $upgradeMysql 'examine2_rel116_upgrade' $upgradeRangeQuery)) $migrationDelta "upgrade applied $migrationDelta target migrations"
    Assert-True ($upgradedSchema.applicationTableCount -ge $baselineSchema.applicationTableCount) 'upgrade did not remove application tables'

    $targetSession = New-Session $upgradeAppPort
    Assert-Equal ([string]$targetSession.Login.account.id) $baselineAccountId 'root account id preserved after upgrade'
    $targetSystems = Invoke-Api $targetSession $upgradeAppPort 'GET' '/api/v1/platform/admin/systems?page=1&size=20'
    $preservedSystem = @($targetSystems.items | Where-Object { [string]$_.id -eq $upgradeSystemId })
    Assert-Equal $preservedSystem.Count 1 'upgraded durable system read-back count'
    Assert-Equal $preservedSystem[0].code 'rel116-upgrade-sentinel' 'upgraded durable system code'
    Assert-Equal $preservedSystem[0].name 'REL116 Upgrade Sentinel' 'upgraded durable system name'

    $targetSystemContext = Invoke-Api $targetSession $upgradeAppPort 'POST' "/api/v1/context/systems/${upgradeSystemId}:switch"
    Assert-Equal $targetSystemContext.context.type 'SYSTEM' 'upgraded switched context type'
    Assert-Equal ([string]$targetSystemContext.context.systemId) $upgradeSystemId 'upgraded switched system id'
    $targetPermissions = Sorted-Strings $targetSystemContext.context.permissions
    Assert-StringArrayContainsAll $targetPermissions $baselinePermissions 'baseline system permissions preserved'
    $addedPermissions = @($targetPermissions | Where-Object { $_ -notin $baselinePermissions })
    Assert-StringArraysEqual $targetSystemContext.context.shells $baselineShells 'system shell semantics preserved'
    Stop-App $targetApp

    $result.upgrade = [ordered]@{
        verdict = 'PASS'
        path = "$BaselineCycle/V$BaselineLatestVersion ($BaselineMigrationCount/$BaselineMigrationCount) -> $TargetCycle/V$TargetLatestVersion ($TargetMigrationCount/$TargetMigrationCount)"
        baselineSchema = $baselineSchema
        targetSchema = $upgradedSchema
        migrationsAppliedDuringUpgrade = $migrationDelta
        schemaContract = [ordered]@{
            successfulMigrationRange = "$($BaselineMigrationCount + 1)..$TargetMigrationCount"
            applicationTablesNotRemoved = $true
        }
        preservation = [ordered]@{
            rootAccountId = $baselineAccountId
            systemId = $upgradeSystemId
            systemCode = 'rel116-upgrade-sentinel'
            systemName = 'REL116 Upgrade Sentinel'
            loginWithOriginalCredential = 'PASS'
            baselinePermissionSetPreserved = $true
            baselinePermissionCount = $baselinePermissions.Count
            targetPermissionCount = $targetPermissions.Count
            addedPermissions = $addedPermissions
            exactShellSetPreserved = $true
            shells = $baselineShells
        }
    }

    $result.verdict = 'PASS'
} catch {
    $result.failure = [ordered]@{
        type = $_.Exception.GetType().FullName
        message = $_.Exception.Message
    }
    throw
} finally {
    foreach ($process in $processes) {
        try { Stop-App $process } catch { }
    }
    foreach ($name in $containerNames) {
        try {
            $existing = & docker ps -a --filter "name=^/${name}$" --format '{{.Names}}'
            if ($existing -contains $name) { & docker rm -f $name *> $null }
        } catch { }
    }
    try {
        if (Test-Path $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
    } catch { }

    $remainingContainers = @(& docker ps -a --format '{{.Names}}' | Where-Object { $_ -in $containerNames })
    $remainingPorts = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $ports } | Select-Object -ExpandProperty LocalPort -Unique)
    $remainingProcesses = @($processes | Where-Object { -not $_.HasExited } | ForEach-Object Id)
    $result.cleanup = [ordered]@{
        exactContainerNamesRemoved = ($remainingContainers.Count -eq 0)
        exactPortsReleased = ($remainingPorts.Count -eq 0)
        exactJavaProcessesStopped = ($remainingProcesses.Count -eq 0)
        temporaryRuntimeDirectoryRemoved = (-not (Test-Path $tempRoot))
        remainingContainers = $remainingContainers
        remainingPorts = $remainingPorts
        remainingProcesses = $remainingProcesses
    }
    $result.completedAt = (Get-Date).ToString('o')
    $result | ConvertTo-Json -Depth 20 | Set-Content -Path $resultPath -Encoding UTF8
}

Write-Output $resultPath
