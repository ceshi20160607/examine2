param(
    [Parameter(Mandatory = $true)][string]$BackupRoot,
    [switch]$ConfirmRestore,
    [string]$ArtifactRoot
)

. (Join-Path $PSScriptRoot 'common.ps1')
if (-not $ConfirmRestore) { throw 'Restore is destructive. Re-run with -ConfirmRestore.' }
$values = Get-DeliveryEnvMap
Assert-DeliveryEnvironment $values
Assert-DockerCompose
$resolvedBackup = Assert-PathInsideDataRoot $BackupRoot $values
$manifestPath = Join-Path $resolvedBackup 'backup-manifest.json'
if (-not (Test-Path $manifestPath -PathType Leaf)) { throw 'Backup manifest is missing.' }
$manifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$sqlPath = Join-Path $resolvedBackup ([string]$manifest.database.path)
$filesArchive = Join-Path $resolvedBackup ([string]$manifest.files.path)
if ((Get-FileHash $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$manifest.database.sha256) { throw 'Database backup checksum mismatch.' }
if ((Get-FileHash $filesArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$manifest.files.sha256) { throw 'File backup checksum mismatch.' }
if ([string]::IsNullOrWhiteSpace($ArtifactRoot)) { $ArtifactRoot = $script:DeliveryPackageRoot }
$resolvedArtifact = (Resolve-Path -LiteralPath $ArtifactRoot).Path

Invoke-DeliveryCompose @('stop', 'backend', 'frontend') -ArtifactRoot $resolvedArtifact
Invoke-DeliveryCompose @('up', '-d', 'mysql', 'redis') -ArtifactRoot $resolvedArtifact
$resetSql = 'DROP DATABASE IF EXISTS `' + $values.EXAMINE_DB_NAME + '`; CREATE DATABASE `' + $values.EXAMINE_DB_NAME + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
& docker compose --project-name examine2 --project-directory $script:DeliveryPackageRoot --env-file $script:DeliveryEnvPath `
    -f $script:DeliveryComposePath exec -T mysql mysql -uroot "-p$($values.EXAMINE_DB_ROOT_PASSWORD)" -e $resetSql
if ($LASTEXITCODE -ne 0) { throw 'Database reset failed.' }
Invoke-NativeWithInputFile 'docker' @(
    'compose', '--project-name', 'examine2', '--project-directory', $script:DeliveryPackageRoot,
    '--env-file', $script:DeliveryEnvPath, '-f', $script:DeliveryComposePath,
    'exec', '-T', 'mysql', 'mysql', '-uroot', "-p$($values.EXAMINE_DB_ROOT_PASSWORD)", $values.EXAMINE_DB_NAME
) $sqlPath | Out-Null

$filesRoot = Join-Path (Get-DeliveryDataRoot $values) 'files'
if (Test-Path -LiteralPath $filesRoot) {
    Get-ChildItem -LiteralPath $filesRoot -Force | Remove-Item -Recurse -Force
} else { [void](New-Item -ItemType Directory -Path $filesRoot) }
Expand-Archive -LiteralPath $filesArchive -DestinationPath $filesRoot -Force
Invoke-DeliveryCompose @('up', '-d', '--remove-orphans') -ArtifactRoot $resolvedArtifact
$health = Wait-DeliveryHealth $values 300
[pscustomobject]@{ status = 'RESTORED'; backupRoot = $resolvedBackup; artifactRoot = $resolvedArtifact; health = $health.status }
