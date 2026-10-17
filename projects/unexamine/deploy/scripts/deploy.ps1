param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$PackageDirectory,
    [Parameter(Mandatory = $true)][string]$Environment,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [string]$MySqlDefaultsFile,
    [string]$MySqlExecutable = 'mysql',
    [string]$DockerMySqlContainer,
    [string]$BackupResultPath,
    [switch]$PreflightOnly,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Resolve-DeploymentRoot $DeploymentRoot
$package = [System.IO.Path]::GetFullPath($PackageDirectory)
$environmentFilePath = [System.IO.Path]::GetFullPath($EnvironmentFile)
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
$manifestPath = Join-Path $package 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "manifest.json is missing" }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($Confirm -ne "DEPLOY $($manifest.version) TO $Environment") { throw 'Deployment confirmation does not match target version and environment' }
if ($manifest.environment -ne $Environment) { throw 'Package environment does not match target environment' }
if ($manifest.databaseStrategy -ne 'FORWARD_FIX') { throw 'Release must declare the supported forward-fix database recovery strategy' }
# 发布前固定后端健康检查端口，避免切换后仍探测旧服务。
if ([string]$manifest.backendReadinessUrl -ne 'http://127.0.0.1:9999/actuator/health/readiness') {
    throw 'Backend readiness must use fixed port 9999'
}
$jar = Join-Path $package 'unexamine-server.jar'
$static = Join-Path $package 'frontend'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf) -or -not (Test-Path -LiteralPath $static -PathType Container)) {
    throw 'Fixed backend or frontend package is missing'
}
$actualHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne [string]$manifest.artifactSha256) { throw 'Backend artifact SHA-256 mismatch' }
Assert-ReleaseFrontend -Directory $static -Manifest $manifest
Assert-FrontendServer -ReleaseDirectory $package -Manifest $manifest

# 启动新包会自动运行 Flyway；在切换指针和停止旧服务前核对当前版本及真实备份制品。
Import-DeploymentEnvironment $environmentFilePath
if ($env:APP_SERVER_PORT -ne '9999') { throw 'Backend service port must be 9999' }
if ($env:APP_ENVIRONMENT_CODE -ne [string]$manifest.environment) { throw 'Environment file and release target different environments' }
if ($env:APP_CONFIG_VERSION -ne [string]$manifest.configVersion) { throw 'Environment file and release have different configuration versions' }
if ($env:APP_DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([A-Za-z0-9_]+)(?:\?|$)') { throw 'APP_DB_URL is not a supported MySQL JDBC URL' }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
if ($DockerMySqlContainer) {
    $publishedPorts = @(docker port $DockerMySqlContainer '3306/tcp')
    if ($LASTEXITCODE -ne 0 -or $dbHost -notin @('127.0.0.1', 'localhost') -or
            @($publishedPorts | Where-Object { $_ -match (':' + [regex]::Escape($dbPort) + '$') }).Count -eq 0) {
        throw 'APP_DB_URL does not point to the selected local MySQL container port'
    }
}
if ($DockerMySqlContainer) {
    if ($DockerMySqlContainer -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') { throw 'Existing Docker MySQL container name is invalid' }
    $containerState = & docker inspect --format '{{.State.Running}}' $DockerMySqlContainer
    if ($LASTEXITCODE -ne 0 -or [string]$containerState -ne 'true') { throw 'Existing Docker MySQL container is not running' }
} else {
    if ([string]::IsNullOrWhiteSpace($MySqlDefaultsFile)) { throw 'MySQL defaults file is required for a host MySQL client' }
    $defaults = [System.IO.Path]::GetFullPath($MySqlDefaultsFile)
    if (-not (Test-Path -LiteralPath $defaults -PathType Leaf)) { throw "MySQL defaults file is missing: $defaults" }
}
function Read-FlywayNumber {
    param([string]$Sql)
    if ($DockerMySqlContainer) {
        # 只查询现有容器内的目标库，发布脚本不管理 MySQL 容器生命周期。
        $value = & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --batch --skip-column-names --execute="$2"' sh $dbName $Sql
    } else {
        $value = & $MySqlExecutable "--defaults-extra-file=$defaults" "--host=$dbHost" "--port=$dbPort" "--database=$dbName" '--batch' '--skip-column-names' "--execute=$Sql"
    }
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') { throw "Flyway history query failed: $Sql" }
    return [int]$value
}
$historyExists = Read-FlywayNumber "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'"
$currentDatabaseVersion = 0
if ($historyExists -eq 1) {
    if ((Read-FlywayNumber 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0') -gt 0) { throw 'Flyway history contains a failed migration' }
    $currentDatabaseVersion = Read-FlywayNumber 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
}
if ([string]$manifest.databaseVersion -notmatch '^\d+$') { throw 'Release manifest databaseVersion must be numeric' }
$targetDatabaseVersion = [int]$manifest.databaseVersion
if ($targetDatabaseVersion -lt $currentDatabaseVersion) { throw 'Release database version is older than the current database' }
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $migrationNumbers = @($archive.Entries | Where-Object { $_.FullName -match '(?:^|/)db/migration/V([0-9]+)__[^/]+\.sql$' } |
        ForEach-Object { [int]([regex]::Match($_.FullName, '/V([0-9]+)__').Groups[1].Value) } | Sort-Object -Unique)
} finally { $archive.Dispose() }
if ($migrationNumbers.Count -eq 0 -or $migrationNumbers[-1] -ne $targetDatabaseVersion -or
        $migrationNumbers.Count -ne $targetDatabaseVersion) { throw 'Release migration files do not match manifest databaseVersion' }

$sourceConfigVersion = [string]$manifest.configVersion
$currentPointer = Join-Path $root 'runtime/current-release.txt'
if (Test-Path -LiteralPath $currentPointer -PathType Leaf) {
    $currentRelease = Get-CurrentReleaseDirectory $root
    $currentManifest = Get-Content -Raw -LiteralPath (Join-Path $currentRelease 'manifest.json') | ConvertFrom-Json
    $sourceConfigVersion = [string]$currentManifest.configVersion
}
$configChanged = $sourceConfigVersion -ne [string]$manifest.configVersion

$verifiedBackupPath = $null
if ($currentDatabaseVersion -gt 0 -and ($targetDatabaseVersion -gt $currentDatabaseVersion -or $configChanged)) {
    if ([string]::IsNullOrWhiteSpace($BackupResultPath)) { throw 'A verified pre-upgrade backup result is required before database migration' }
    $verifiedBackupPath = [System.IO.Path]::GetFullPath($BackupResultPath)
    $backupsDirectory = [System.IO.Path]::GetFullPath((Join-Path $root 'backups'))
    if (-not $verifiedBackupPath.StartsWith($backupsDirectory + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $verifiedBackupPath -PathType Leaf) -or
            [System.IO.Path]::GetFileName($verifiedBackupPath) -ne 'backup-result.json') { throw 'Backup result must be an existing manifest inside deployment backups' }
    $backup = Get-Content -Raw -LiteralPath $verifiedBackupPath | ConvertFrom-Json
    if ([string]$backup.databaseVersion -ne [string]$currentDatabaseVersion -or
            [string]$backup.configVersion -ne $sourceConfigVersion) { throw 'Backup was made from a different database or configuration version' }
    $backupDirectory = [System.IO.Path]::GetDirectoryName($verifiedBackupPath)
    $backupFolder = [System.IO.Path]::GetFileName($backupDirectory)
    $expectedFiles = @{ DATABASE='database.sql'; FILE_STORAGE='file-storage.zip'; CONFIGURATION='configuration.json'; SECRET_REFERENCES='secret-references.json' }
    if (@($backup.items).Count -ne 4 -or [string]::IsNullOrWhiteSpace([string]$backup.consistencyPoint)) { throw 'Backup manifest is incomplete' }
    foreach ($item in $backup.items) {
        $type = [string]$item.itemType
        if (-not $expectedFiles.ContainsKey($type)) { throw 'Backup manifest contains an unknown or duplicate item' }
        $filename = $expectedFiles[$type]
        if ([string]$item.storageUri -ne "backup://$backupFolder/$filename" -or [string]$item.verificationStatus -ne 'VERIFIED') { throw 'Backup item identity or status is invalid' }
        $path = Join-Path $backupDirectory $filename
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Backup item is missing: $type" }
        $file = Get-Item -LiteralPath $path
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($file.Length -ne [long]$item.sizeBytes -or $hash -ne [string]$item.sha256) { throw "Backup item changed after verification: $type" }
        $expectedFiles.Remove($type) | Out-Null
    }
    if ($expectedFiles.Count -ne 0) { throw 'Backup manifest lacks a required item' }
    $backupConfiguration = Get-Content -Raw -LiteralPath (Join-Path $backupDirectory 'configuration.json') | ConvertFrom-Json
    if ([string]$backupConfiguration.databaseName -ne $dbName -or
            [string]$backupConfiguration.databaseHost -ne $dbHost -or
            [string]$backupConfiguration.environmentCode -ne $Environment -or
            [string]$backupConfiguration.configVersion -ne $sourceConfigVersion -or
            [string]$backupConfiguration.consistencyPoint -ne [string]$backup.consistencyPoint) {
        throw 'Backup configuration snapshot does not match the current deployment source'
    }
}
if ($PreflightOnly) {
    [ordered]@{ result='READY'; currentDatabaseVersion=$currentDatabaseVersion; targetDatabaseVersion=$targetDatabaseVersion; sourceConfigVersion=$sourceConfigVersion; targetConfigVersion=[string]$manifest.configVersion; backupResultPath=$verifiedBackupPath } | ConvertTo-Json
    return
}
$releasesRoot = Join-Path $root 'releases'
$runtimeRoot = Join-Path $root 'runtime'
$backupRoot = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $releasesRoot, $runtimeRoot, $backupRoot | Out-Null
$target = Join-Path $releasesRoot ([string]$manifest.version)
if (Test-Path -LiteralPath $target) {
    $storedHash = (Get-FileHash -LiteralPath (Join-Path $target 'unexamine-server.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($storedHash -ne $actualHash) { throw "Release already exists with a different immutable artifact: $target" }
    $storedManifestHash = (Get-FileHash -LiteralPath (Join-Path $target 'manifest.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $candidateManifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($storedManifestHash -ne $candidateManifestHash) { throw "Release already exists with a different manifest: $target" }
} else {
    $stage = Join-Path $releasesRoot ('.stage-' + [guid]::NewGuid().ToString('N'))
    Copy-Item -LiteralPath $package -Destination $stage -Recurse
    Move-Item -LiteralPath $stage -Destination $target
}
$pointer = Join-Path $runtimeRoot 'current-release.txt'
$previousVersion = $null
if (Test-Path -LiteralPath $pointer) {
    $previousVersion = (Get-Content -Raw -LiteralPath $pointer).Trim()
    Copy-Item -LiteralPath $pointer -Destination (Join-Path $backupRoot ("current-release-$((Get-Date).ToString('yyyyMMddHHmmss')).txt"))
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
}
Set-Content -LiteralPath $pointer -Value ([string]$manifest.version)
try {
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $target -EvidenceLabel ([string]$manifest.version)
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    Wait-DeploymentEndpoint -Uri ([string]$manifest.backendReadinessUrl) -TimeoutSeconds 60 | Out-Null
    Wait-DeploymentEndpoint -Uri ([string]$manifest.frontendSmokeUrl) -TimeoutSeconds 30 | Out-Null
    if ((Read-FlywayNumber 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1') -ne $targetDatabaseVersion) {
        throw 'Started backend database version does not match release manifest'
    }
    $record = [ordered]@{ version=$manifest.version; environment=$Environment; artifactSha256=$actualHash; approvalReference=$ApprovalReference; readinessUrl=$manifest.backendReadinessUrl; realEntryUrl=$manifest.frontendSmokeUrl; result='SUCCESS'; deployedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-deployment.json')
} catch {
    $deploymentFailure = $_.Exception.Message
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
    # MySQL DDL 不保证事务回滚；版本已前进时保留备份和故障记录，等待前向修复或显式恢复。
    $afterVersion = $null
    try { $afterVersion = Read-FlywayNumber 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1' } catch { }
    if ($null -eq $afterVersion -or $afterVersion -gt $currentDatabaseVersion -or $configChanged) {
        $record = [ordered]@{ version=$manifest.version; environment=$Environment; artifactSha256=$actualHash; approvalReference=$ApprovalReference; result='FAILED_REQUIRES_RECOVERY'; failure=$deploymentFailure; databaseVersionBefore=$currentDatabaseVersion; databaseVersionAfter=$afterVersion; sourceConfigVersion=$sourceConfigVersion; targetConfigVersion=[string]$manifest.configVersion; backupResultPath=$verifiedBackupPath; treatment='Inspect Flyway history and configuration version; apply a forward fix or restore the verified backup and prior environment settings before restarting'; failedAt=(Get-Date).ToString('o') }
        $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-deployment.json')
        throw 'Deployment failed after database migration or database state became unknown; see last-deployment.json for recovery steps'
    }
    if ($previousVersion) {
        Set-Content -LiteralPath $pointer -Value $previousVersion
        $previousRelease = Get-CurrentReleaseDirectory $root
        Switch-DeploymentStatic -Root $root -ReleaseDirectory $previousRelease -EvidenceLabel 'failed-deploy-restore'
        & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    } elseif (Test-Path -LiteralPath $pointer -PathType Leaf) {
        Remove-Item -LiteralPath $pointer
    }
    $record = [ordered]@{ version=$manifest.version; environment=$Environment; artifactSha256=$actualHash; approvalReference=$ApprovalReference; result='FAILED'; failure=$deploymentFailure; restoredVersion=$previousVersion; failedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-deployment.json')
    throw
}
Write-Output "Deployment $($manifest.version) succeeded; approval=$ApprovalReference"
