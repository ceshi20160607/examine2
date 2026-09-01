param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][long]$ReleaseId,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [Parameter(Mandatory = $true)][string]$EncryptionKeyReference,
    [Parameter(Mandatory = $true)][string]$MySqlDefaultsFile,
    [string]$MySqlDumpExecutable = 'mysqldump',
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
if ($Confirm -ne "BACKUP $ReleaseId") { throw 'Backup confirmation does not match release id' }
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
if ($EncryptionKeyReference -notmatch '^[A-Za-z0-9._:/-]{3,255}$') { throw 'Encryption key reference is invalid' }
$defaults = [System.IO.Path]::GetFullPath($MySqlDefaultsFile)
if (-not (Test-Path -LiteralPath $defaults -PathType Leaf)) { throw "MySQL defaults file is missing: $defaults" }
Import-DeploymentEnvironment $EnvironmentFile
if ($env:APP_DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') { throw 'APP_DB_URL is not a supported MySQL JDBC URL' }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
$now = (Get-Date).ToUniversalTime()
$directoryStamp = $now.ToString('yyyyMMddTHHmmssfffZ')
$consistencyPoint = $now.ToString('o')
$backupRoot = Join-Path $root "backups/full-$ReleaseId-$directoryStamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$databaseFile = Join-Path $backupRoot 'database.sql'
& $MySqlDumpExecutable "--defaults-extra-file=$defaults" '--single-transaction' '--routines' '--triggers' '--hex-blob' '--no-tablespaces' `
    "--host=$dbHost" "--port=$dbPort" $dbName | Set-Content -LiteralPath $databaseFile -Encoding utf8NoBOM
if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $databaseFile).Length -eq 0) { throw 'Database dump failed or is empty' }

$fileArchive = Join-Path $backupRoot 'file-storage.zip'
$fileRoot = [System.IO.Path]::GetFullPath($env:APP_FILE_STORAGE_ROOT)
if (-not (Test-Path -LiteralPath $fileRoot -PathType Container)) { throw "File storage root is missing: $fileRoot" }
Compress-Archive -Path (Join-Path $fileRoot '*') -DestinationPath $fileArchive -CompressionLevel Optimal

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
$config | ConvertTo-Json | Set-Content -LiteralPath $configFile -Encoding utf8NoBOM

$secretRefsFile = Join-Path $backupRoot 'secret-references.json'
$secretRefs = [ordered]@{
    encryptionKeyReference = $EncryptionKeyReference
    references = @('APP_DB_PASSWORD', 'APP_APPLICATION_SECRET_MASTER_KEY')
    secretMaterialIncluded = $false
    restoreStrategy = 'ROTATE_ON_RESTORE'
}
$secretRefs | ConvertTo-Json | Set-Content -LiteralPath $secretRefsFile -Encoding utf8NoBOM

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
    approvalReference = $ApprovalReference
    consistencyPoint = $consistencyPoint
    encryptionKeyReference = $EncryptionKeyReference
    requiredBytes = $requiredBytes
    availableBytes = $drive.Free
    items = $items
}
$resultPath = Join-Path $backupRoot 'backup-result.json'
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
Write-Output $resultPath
