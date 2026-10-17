param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][long]$ReleaseId,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyReference,
    [string]$MySqlDefaultsFile,
    [string]$MySqlExecutable = 'mysql',
    [string]$MySqlDumpExecutable = 'mysqldump',
    [string]$DockerMySqlContainer,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Resolve-DeploymentRoot $DeploymentRoot
if ($Confirm -ne "BACKUP $ReleaseId") { throw 'Backup confirmation does not match release id' }
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
if ($EncryptionKeyReference -notmatch '^[A-Za-z0-9._:/-]{3,255}$') { throw 'Encryption key reference is invalid' }
if ($DockerMySqlContainer) {
    if ($DockerMySqlContainer -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') { throw 'Existing Docker MySQL container name is invalid' }
    $containerState = & docker inspect --format '{{.State.Running}}' $DockerMySqlContainer
    if ($LASTEXITCODE -ne 0 -or [string]$containerState -ne 'true') { throw 'Existing Docker MySQL container is not running' }
} else {
    if ([string]::IsNullOrWhiteSpace($MySqlDefaultsFile)) { throw 'MySQL defaults file is required for a host MySQL client' }
    $defaults = [System.IO.Path]::GetFullPath($MySqlDefaultsFile)
    if (-not (Test-Path -LiteralPath $defaults -PathType Leaf)) { throw "MySQL defaults file is missing: $defaults" }
}
Import-DeploymentEnvironment $EnvironmentFile
if ($env:APP_DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') { throw 'APP_DB_URL is not a supported MySQL JDBC URL' }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
if ($dbName -notmatch '^[A-Za-z0-9_]+$') { throw 'Database name is invalid' }
if ($DockerMySqlContainer) {
    $publishedPorts = @(docker port $DockerMySqlContainer '3306/tcp')
    if ($LASTEXITCODE -ne 0 -or $dbHost -notin @('127.0.0.1', 'localhost') -or
            @($publishedPorts | Where-Object { $_ -match (':' + [regex]::Escape($dbPort) + '$') }).Count -eq 0) {
        throw 'APP_DB_URL does not point to the selected local MySQL container port'
    }
}
function Read-MySqlScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)
    if ($DockerMySqlContainer) {
        # 使用已有容器内的客户端和凭据读取迁移状态，不启动额外服务。
        $value = & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --batch --skip-column-names --execute="$2"' sh $dbName $Sql
    } else {
        $value = & $MySqlExecutable "--defaults-extra-file=$defaults" "--host=$dbHost" "--port=$dbPort" "--database=$dbName" '--batch' '--skip-column-names' "--execute=$Sql"
    }
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') { throw 'Cannot read MySQL state before backup' }
    return [string]$value
}
# 备份结果绑定当前 Flyway 和配置版本，防止升级时误用其他版本的制品。
$databaseVersion = Read-MySqlScalar 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
$failedMigrations = Read-MySqlScalar 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0'
if ($failedMigrations -ne '0') { throw 'Flyway history contains a failed migration' }
$databaseTableCount = Read-MySqlScalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'"
if ([int]$databaseTableCount -lt 1) { throw 'Cannot count database tables before backup' }
$now = (Get-Date).ToUniversalTime()
$directoryStamp = $now.ToString('yyyyMMddTHHmmssfffZ')
$consistencyPoint = $now.ToString('o')
$backupRoot = Join-Path $root "backups/full-$ReleaseId-$directoryStamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$databaseFile = Join-Path $backupRoot 'database.sql'
$dumpError = Join-Path $backupRoot 'dump.err'
if ($DockerMySqlContainer) {
    # 流式复制容器内 mysqldump 的原始字节，避免大库占用宿主机内存或容器临时磁盘。
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new('docker')
    foreach ($argument in @('exec', $DockerMySqlContainer, 'sh', '-c',
            'exec mysqldump --user=root --password="$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --hex-blob --no-tablespaces "$1"',
            'sh', $dbName)) { [void]$startInfo.ArgumentList.Add($argument) }
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $dumpProcess = [System.Diagnostics.Process]::Start($startInfo)
    try {
        $errorTask = $dumpProcess.StandardError.ReadToEndAsync()
        $dumpStream = [System.IO.File]::Create($databaseFile)
        try { $dumpProcess.StandardOutput.BaseStream.CopyTo($dumpStream) } finally { $dumpStream.Dispose() }
        $dumpProcess.WaitForExit()
        [System.IO.File]::WriteAllText($dumpError, $errorTask.GetAwaiter().GetResult())
        if ($dumpProcess.ExitCode -ne 0) { throw 'Database dump failed; inspect dump.err' }
    } finally { $dumpProcess.Dispose() }
} else {
    $dumpProcess = Start-Process -FilePath $MySqlDumpExecutable -ArgumentList @(
        "--defaults-extra-file=$defaults", '--single-transaction', '--routines', '--triggers', '--hex-blob', '--no-tablespaces',
        "--host=$dbHost", "--port=$dbPort", $dbName) -RedirectStandardOutput $databaseFile `
        -RedirectStandardError $dumpError -WindowStyle Hidden -Wait -PassThru
    if ($dumpProcess.ExitCode -ne 0) { throw 'Database dump failed; inspect dump.err' }
}
if ((Get-Item -LiteralPath $databaseFile).Length -eq 0) { throw 'Database dump is empty' }

$fileArchive = Join-Path $backupRoot 'file-storage.zip'
$fileRoot = [System.IO.Path]::GetFullPath($env:APP_FILE_STORAGE_ROOT)
if (-not (Test-Path -LiteralPath $fileRoot -PathType Container)) { throw "File storage root is missing: $fileRoot" }
if (@(Get-ChildItem -LiteralPath $fileRoot -Force).Count -eq 0) {
    $emptyArchive = [System.IO.Compression.ZipFile]::Open($fileArchive, [System.IO.Compression.ZipArchiveMode]::Create)
    $emptyArchive.Dispose()
} else {
    Compress-Archive -Path (Join-Path $fileRoot '*') -DestinationPath $fileArchive -CompressionLevel Optimal
}

$configFile = Join-Path $backupRoot 'configuration.json'
$config = [ordered]@{
    environmentCode = $env:APP_ENVIRONMENT_CODE
    releaseVersion = $env:APP_RELEASE_VERSION
    frontendVersion = $env:APP_FRONTEND_VERSION
    configVersion = $env:APP_CONFIG_VERSION
    databaseHost = $dbHost
    databaseName = $dbName
    redisHost = $env:APP_REDIS_HOST
    redisPort = $env:APP_REDIS_PORT
    consistencyPoint = $consistencyPoint
}
Write-Utf8NoBom -Path $configFile -Value ($config | ConvertTo-Json)

$secretRefsFile = Join-Path $backupRoot 'secret-references.json'
$secretRefs = [ordered]@{
    encryptionKeyReference = $EncryptionKeyReference
    references = @('APP_DB_PASSWORD', 'APP_APPLICATION_SECRET_MASTER_KEY')
    secretMaterialIncluded = $false
    restoreStrategy = 'ROTATE_ON_RESTORE'
}
Write-Utf8NoBom -Path $secretRefsFile -Value ($secretRefs | ConvertTo-Json)

$definitions = @(
    @{ type='DATABASE'; path=$databaseFile },
    @{ type='FILE_STORAGE'; path=$fileArchive },
    @{ type='CONFIGURATION'; path=$configFile },
    @{ type='SECRET_REFERENCES'; path=$secretRefsFile }
)
$items = foreach ($definition in $definitions) {
    $file = Get-Item -LiteralPath $definition.path
    [ordered]@{
        itemType = $definition.type
        storageUri = "backup://$([System.IO.Path]::GetFileName($backupRoot))/$($file.Name)"
        sizeBytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        verificationStatus = 'VERIFIED'
    }
}
$requiredBytes = [long](($items | ForEach-Object { [long]$_['sizeBytes'] } | Measure-Object -Sum).Sum)
$drive = Get-PSDrive -Name ([System.IO.Path]::GetPathRoot($root).Substring(0, 1))
$result = [ordered]@{
    schemaVersion = 1
    releaseId = $ReleaseId
    databaseVersion = [string]$databaseVersion
    databaseTableCount = [int]$databaseTableCount
    configVersion = $env:APP_CONFIG_VERSION
    approvalReference = $ApprovalReference
    consistencyPoint = $consistencyPoint
    encryptionKeyReference = $EncryptionKeyReference
    requiredBytes = $requiredBytes
    availableBytes = $drive.Free
    items = $items
}
$resultPath = Join-Path $backupRoot 'backup-result.json'
Write-Utf8NoBom -Path $resultPath -Value ($result | ConvertTo-Json -Depth 8)
Write-Output $resultPath
