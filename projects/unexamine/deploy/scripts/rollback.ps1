param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$TargetVersion,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [string]$MySqlDefaultsFile,
    [string]$MySqlExecutable = 'mysql',
    [string]$DockerMySqlContainer,
    [Parameter(Mandatory = $true)][string]$DeploymentId,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [switch]$PreflightOnly,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$environmentFilePath = [System.IO.Path]::GetFullPath($EnvironmentFile)
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
if ($TargetVersion -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw 'Target version is invalid' }
if ($Confirm -ne "ROLLBACK $DeploymentId") { throw 'Rollback confirmation does not match deployment id' }
$target = [System.IO.Path]::GetFullPath((Join-Path $root "releases/$TargetVersion"))
$releasesRoot = [System.IO.Path]::GetFullPath((Join-Path $root 'releases'))
if (-not $target.StartsWith($releasesRoot + [System.IO.Path]::DirectorySeparatorChar) -or -not (Test-Path -LiteralPath $target -PathType Container)) {
    throw 'Rollback target is outside the release store or missing'
}
$manifest = Get-Content -Raw -LiteralPath (Join-Path $target 'manifest.json') | ConvertFrom-Json
if ([string]$manifest.backendReadinessUrl -ne 'http://127.0.0.1:9999/actuator/health/readiness') {
    throw 'Rollback target backend readiness must use fixed port 9999'
}
if ($manifest.databaseStrategy -ne 'FORWARD_FIX' -or [string]$manifest.databaseVersion -notmatch '^\d+$') {
    throw 'Rollback target requires a known forward-fix database version'
}
$jar = Join-Path $target 'unexamine-server.jar'
$static = Join-Path $target 'frontend'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf) -or -not (Test-Path -LiteralPath $static -PathType Container)) {
    throw 'Rollback target backend or frontend package is missing'
}
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$manifest.artifactSha256) {
    throw 'Rollback target backend artifact SHA-256 differs from manifest'
}
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $migrationNumbers = @($archive.Entries | Where-Object { $_.FullName -match '(?:^|/)db/migration/V([0-9]+)__[^/]+\.sql$' } |
        ForEach-Object { [int]([regex]::Match($_.FullName, '/V([0-9]+)__').Groups[1].Value) } | Sort-Object -Unique)
} finally { $archive.Dispose() }
if ($migrationNumbers.Count -ne [int]$manifest.databaseVersion -or
        $migrationNumbers[-1] -ne [int]$manifest.databaseVersion) {
    throw 'Rollback target migration files differ from manifest database version'
}
# 回切前检查真实数据库与目标包版本一致，防止旧应用在新表结构上启动。
Import-DeploymentEnvironment $environmentFilePath
if ($env:APP_SERVER_PORT -ne '9999') { throw 'Backend service port must be 9999' }
if ($env:APP_CONFIG_VERSION -ne [string]$manifest.configVersion) { throw 'Rollback configuration version differs from target release' }
if ($env:APP_DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([A-Za-z0-9_]+)(?:\?|$)') {
    throw 'APP_DB_URL is not a supported MySQL JDBC URL'
}
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
    if (-not (Test-Path -LiteralPath $defaults -PathType Leaf)) { throw 'MySQL defaults file is missing' }
}
function Read-FlywayNumber {
    param([string]$Sql)
    if ($DockerMySqlContainer) {
        # 回切前直接查询已有 MySQL 容器，禁止降级到不兼容的数据库版本。
        $value = & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --batch --skip-column-names --execute="$2"' sh $dbName $Sql
    } else {
        $value = & $MySqlExecutable "--defaults-extra-file=$defaults" "--host=$dbHost" "--port=$dbPort" "--database=$dbName" '--batch' '--skip-column-names' "--execute=$Sql"
    }
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') { throw "Rollback Flyway query failed: $Sql" }
    return [int]$value
}
if ((Read-FlywayNumber "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'") -ne 1 -or
        (Read-FlywayNumber 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0') -ne 0) {
    throw 'Rollback requires a complete Flyway history without failed migrations'
}
$actualDatabaseVersion = Read-FlywayNumber 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
if ($actualDatabaseVersion -ne [int]$manifest.databaseVersion) {
    throw "Rollback target database version $($manifest.databaseVersion) differs from actual version $actualDatabaseVersion; restore a verified backup or apply a forward fix first"
}
if ($PreflightOnly) { Write-Output "Rollback preflight READY: database V$actualDatabaseVersion, config $($manifest.configVersion)"; return }
$runtimeRoot = Join-Path $root 'runtime'
$backupRoot = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$pointer = Join-Path $runtimeRoot 'current-release.txt'
$previousVersion = (Get-Content -Raw -LiteralPath $pointer).Trim()
Copy-Item -LiteralPath $pointer -Destination (Join-Path $backupRoot ("pre-rollback-$DeploymentId-$((Get-Date).ToString('yyyyMMddHHmmss')).txt"))
& "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
Set-Content -LiteralPath $pointer -Value $TargetVersion
try {
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $target -EvidenceLabel ("rollback-$DeploymentId")
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    Wait-DeploymentEndpoint -Uri ([string]$manifest.backendReadinessUrl) -TimeoutSeconds 60 | Out-Null
    Wait-DeploymentEndpoint -Uri ([string]$manifest.frontendSmokeUrl) -TimeoutSeconds 30 | Out-Null
    $record = [ordered]@{ deploymentId=$DeploymentId; targetVersion=$TargetVersion; approvalReference=$ApprovalReference; databaseStrategy=$manifest.databaseStrategy; databaseVersion=$actualDatabaseVersion; pointerSnapshot=$true; readinessUrl=$manifest.backendReadinessUrl; realEntryUrl=$manifest.frontendSmokeUrl; result='SUCCESS'; rolledBackAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-rollback.json')
} catch {
    $rollbackFailure = $_.Exception.Message
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
    Set-Content -LiteralPath $pointer -Value $previousVersion
    $previousRelease = Get-CurrentReleaseDirectory $root
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $previousRelease -EvidenceLabel ("failed-rollback-$DeploymentId")
    $previousManifest = Get-Content -Raw -LiteralPath (Join-Path $previousRelease 'manifest.json') | ConvertFrom-Json
    if ([string]$previousManifest.configVersion -ne [string]$env:APP_CONFIG_VERSION -or
            [string]$previousManifest.databaseVersion -ne [string]$actualDatabaseVersion) {
        $record = [ordered]@{ deploymentId=$DeploymentId; targetVersion=$TargetVersion; approvalReference=$ApprovalReference; result='FAILED_REQUIRES_RECOVERY'; failure=$rollbackFailure; restoredPointer=$previousVersion; databaseVersion=$actualDatabaseVersion; targetConfigVersion=[string]$manifest.configVersion; previousConfigVersion=[string]$previousManifest.configVersion; treatment='Service remains stopped. Restore the previous environment file and verify Flyway before starting its fixed release; use the verified backup or forward fix if database state changed'; failedAt=(Get-Date).ToString('o') }
        $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-rollback.json')
        throw 'Rollback failed; previous release requires its own configuration before restart'
    }
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    $record = [ordered]@{ deploymentId=$DeploymentId; targetVersion=$TargetVersion; approvalReference=$ApprovalReference; databaseStrategy=$manifest.databaseStrategy; databaseVersion=$actualDatabaseVersion; pointerSnapshot=$true; result='FAILED'; failure=$rollbackFailure; restoredVersion=$previousVersion; failedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-rollback.json')
    throw
}
Write-Output "Rollback to $TargetVersion succeeded; approval=$ApprovalReference"
