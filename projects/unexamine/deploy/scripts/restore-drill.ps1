param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$BackupResult,
    [Parameter(Mandatory = $true)][long]$BackupId,
    [Parameter(Mandatory = $true)][string]$Environment,
    [string]$MySqlDefaultsFile,
    [string]$MySqlExecutable = 'mysql',
    [string]$DockerMySqlContainer,
    [Parameter(Mandatory = $true)][string]$TargetHost,
    [int]$TargetPort = 3306,
    [Parameter(Mandatory = $true)][string]$TargetDatabaseName,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
if ($Confirm -ne "DRILL $BackupId IN $Environment") { throw 'Restore drill confirmation does not match backup and environment' }
if ($Environment -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw 'Restore drill environment is invalid' }
if ($TargetHost -notmatch '^[A-Za-z0-9.:-]{1,255}$' -or $TargetPort -lt 1 -or $TargetPort -gt 65535 -or
        $TargetDatabaseName -notmatch '^unexamine_drill_[A-Za-z0-9_]{3,80}$') { throw 'Restore target must be a named isolated drill database' }
if ($DockerMySqlContainer) {
    if ($DockerMySqlContainer -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') { throw 'Existing Docker MySQL container name is invalid' }
    $containerState = & docker inspect --format '{{.State.Running}}' $DockerMySqlContainer
    $publishedPorts = @(docker port $DockerMySqlContainer '3306/tcp')
    if ([string]$containerState -ne 'true' -or $LASTEXITCODE -ne 0 -or
            $TargetHost -notin @('127.0.0.1', 'localhost') -or
            @($publishedPorts | Where-Object { $_ -match (':' + [regex]::Escape([string]$TargetPort) + '$') }).Count -eq 0) {
        throw 'Restore target does not point to the selected local MySQL container port'
    }
} else {
    if ([string]::IsNullOrWhiteSpace($MySqlDefaultsFile)) { throw 'MySQL defaults file is required for a host MySQL client' }
    $defaults = [System.IO.Path]::GetFullPath($MySqlDefaultsFile)
    if (-not (Test-Path -LiteralPath $defaults -PathType Leaf)) { throw 'MySQL defaults file is missing' }
}
$resultPath = [System.IO.Path]::GetFullPath($BackupResult)
if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) { throw "Backup result is missing: $resultPath" }
$backupDirectory = Split-Path -Parent $resultPath
$manifest = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
if (@($manifest.items).Count -ne 4) { throw 'Backup result must contain exactly four items' }
$itemFiles = @{
    DATABASE = 'database.sql'
    FILE_STORAGE = 'file-storage.zip'
    CONFIGURATION = 'configuration.json'
    SECRET_REFERENCES = 'secret-references.json'
}
foreach ($item in $manifest.items) {
    if (-not $itemFiles.ContainsKey([string]$item.itemType)) { throw "Unexpected backup item: $($item.itemType)" }
    $artifact = Join-Path $backupDirectory $itemFiles[[string]$item.itemType]
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Backup artifact is missing: $artifact" }
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne [string]$item.sha256 -or (Get-Item -LiteralPath $artifact).Length -ne [long]$item.sizeBytes) {
        throw "Backup artifact verification failed: $($item.itemType)"
    }
}
$drillRoot = Join-Path $root "drills/$Environment/backup-$BackupId-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force -Path $drillRoot | Out-Null
Expand-Archive -LiteralPath (Join-Path $backupDirectory 'file-storage.zip') -DestinationPath (Join-Path $drillRoot 'files') -Force
$config = Get-Content -Raw -LiteralPath (Join-Path $backupDirectory 'configuration.json') | ConvertFrom-Json
$secretRefs = Get-Content -Raw -LiteralPath (Join-Path $backupDirectory 'secret-references.json') | ConvertFrom-Json
if ([string]$config.environmentCode -eq $Environment -or
        ([string]$config.environmentCode -eq 'production' -and [string]$config.databaseHost -eq $TargetHost)) {
    throw 'Restore drill must use an isolated environment and a separate production database host'
}
if ($secretRefs.secretMaterialIncluded -ne $false -or $secretRefs.restoreStrategy -ne 'ROTATE_ON_RESTORE') {
    throw 'Secret reference artifact contains material or lacks rotation strategy'
}
if ([string]$secretRefs.encryptionKeyReference -ne [string]$manifest.encryptionKeyReference -or
        [string]$config.configVersion -ne [string]$manifest.configVersion) {
    throw 'Backup configuration or key reference differs from the verified manifest'
}
Copy-Item -LiteralPath (Join-Path $backupDirectory 'configuration.json') -Destination (Join-Path $drillRoot 'configuration.json')
Copy-Item -LiteralPath (Join-Path $backupDirectory 'secret-references.json') -Destination (Join-Path $drillRoot 'secret-references.json')
$databaseDump = Get-Item -LiteralPath (Join-Path $backupDirectory 'database.sql')
if ($databaseDump.Length -lt 1 -or [string]$manifest.databaseVersion -notmatch '^\d+$' -or
        [int]$manifest.databaseTableCount -lt 1 -or [string]$manifest.consistencyPoint -ne [string]$config.consistencyPoint) {
    throw 'Backup database version, table count or consistency point is invalid'
}
# 真正导入到新建的隔离数据库；目标已存在时直接拒绝，绝不覆盖业务库。
function Invoke-DrillQuery {
    param([string]$Sql, [switch]$UseTarget)
    if ($DockerMySqlContainer) {
        # 恢复演练只查询现有容器中的指定隔离库，不启动新的数据库容器。
        if ($UseTarget) {
            $value = & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --batch --skip-column-names --execute="$2"' sh $TargetDatabaseName $Sql
        } else {
            $value = & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names --execute="$1"' sh $Sql
        }
    } else {
        $arguments = @("--defaults-extra-file=$defaults", "--host=$TargetHost", "--port=$TargetPort", '--batch', '--skip-column-names')
        if ($UseTarget) { $arguments += "--database=$TargetDatabaseName" }
        $arguments += "--execute=$Sql"
        $value = & $MySqlExecutable @arguments
    }
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') { throw "Restore database query failed: $Sql" }
    return [int]$value
}
$existingDatabase = Invoke-DrillQuery "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$TargetDatabaseName'"
if ($existingDatabase -ne 0) { throw 'Restore target database already exists; choose a new isolated name' }
try {
    if ($DockerMySqlContainer) {
        & docker exec $DockerMySqlContainer sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --execute="$1"' sh "CREATE DATABASE $TargetDatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null
    } else {
        & $MySqlExecutable "--defaults-extra-file=$defaults" "--host=$TargetHost" "--port=$TargetPort" "--execute=CREATE DATABASE $TargetDatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated restore database' }
    if ($DockerMySqlContainer) {
        # 原样把 SQL 字节送进现有容器中的 mysql，避免 PowerShell 文本管道改写编码。
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new('docker')
        foreach ($argument in @('exec', '-i', $DockerMySqlContainer, 'sh', '-c',
                'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --default-character-set=utf8mb4',
                'sh', $TargetDatabaseName)) { [void]$startInfo.ArgumentList.Add($argument) }
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardInput = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $importProcess = [System.Diagnostics.Process]::Start($startInfo)
        try {
            $outputTask = $importProcess.StandardOutput.ReadToEndAsync()
            $errorTask = $importProcess.StandardError.ReadToEndAsync()
            $dumpStream = [System.IO.File]::OpenRead($databaseDump.FullName)
            try { $dumpStream.CopyTo($importProcess.StandardInput.BaseStream) } finally { $dumpStream.Dispose() }
            $importProcess.StandardInput.Close()
            $importProcess.WaitForExit()
            [System.IO.File]::WriteAllText((Join-Path $drillRoot 'import.out'), $outputTask.GetAwaiter().GetResult())
            [System.IO.File]::WriteAllText((Join-Path $drillRoot 'import.err'), $errorTask.GetAwaiter().GetResult())
            if ($importProcess.ExitCode -ne 0) { throw 'SQL dump import failed; the isolated database may be partial' }
        } finally { $importProcess.Dispose() }
    } else {
        $importProcess = Start-Process -FilePath $MySqlExecutable -ArgumentList @(
            "--defaults-extra-file=$defaults", "--host=$TargetHost", "--port=$TargetPort",
            "--database=$TargetDatabaseName", '--default-character-set=utf8mb4') `
            -RedirectStandardInput $databaseDump.FullName -RedirectStandardOutput (Join-Path $drillRoot 'import.out') `
            -RedirectStandardError (Join-Path $drillRoot 'import.err') -WindowStyle Hidden -Wait -PassThru
        if ($importProcess.ExitCode -ne 0) { throw 'SQL dump import failed; the isolated database may be partial' }
    }
    $restoredTableCount = Invoke-DrillQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TargetDatabaseName' AND table_type='BASE TABLE'"
    $restoredVersion = Invoke-DrillQuery 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1' -UseTarget
    $failedMigrations = Invoke-DrillQuery 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0' -UseTarget
    if ($restoredTableCount -ne [int]$manifest.databaseTableCount -or
            $restoredVersion -ne [int]$manifest.databaseVersion -or $failedMigrations -ne 0) {
        throw 'Restored schema count or Flyway version differs from the backup manifest'
    }
} catch {
    [ordered]@{ status='FAILED'; targetDatabaseName=$TargetDatabaseName; error=$_.Exception.Message;
        treatment='Inspect the isolated database and failure; do not register this drill as passed. Use a fresh target name for retry.' } |
        ConvertTo-Json | ForEach-Object { Write-Utf8NoBom -Path (Join-Path $drillRoot 'failure.json') -Value $_ }
    throw
}
$verification = [ordered]@{
    backupId = $BackupId
    environment = $Environment
    status = 'PASSED'
    databaseRestore = 'RESTORED'
    restoredDatabaseVersion = [string]$restoredVersion
    restoredTableCount = [int]$restoredTableCount
    targetDatabaseName = $TargetDatabaseName
    targetHost = $TargetHost
    fileObjects = 'EXTRACTED'
    configurationVersion = $config.configVersion
    secretReferences = 'REFERENCE_ONLY_ROTATE_ON_RESTORE'
    verifiedAt = (Get-Date).ToString('o')
    items = @($manifest.items | ForEach-Object {
        [ordered]@{
            itemType = $_.itemType
            sizeBytes = [long]$_.sizeBytes
            sha256 = [string]$_.sha256
            verificationStatus = 'VERIFIED'
        }
    })
}
$verificationPath = Join-Path $drillRoot 'verification.json'
Write-Utf8NoBom -Path $verificationPath -Value ($verification | ConvertTo-Json -Depth 8)
Write-Output $verificationPath
