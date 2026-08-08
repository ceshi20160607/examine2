param([string]$Label = 'manual')

. (Join-Path $PSScriptRoot 'common.ps1')
$values = Get-DeliveryEnvMap
Assert-DeliveryEnvironment $values
Assert-DockerCompose

if ($Label -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') { throw 'Backup label is invalid.' }
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dataRoot = Get-DeliveryDataRoot $values
$backupRoot = Assert-PathInsideDataRoot (Join-Path $dataRoot "backups/$timestamp-$Label") $values
[void](New-Item -ItemType Directory -Path $backupRoot -Force)

$sqlPath = Join-Path $backupRoot 'database.sql'
$arguments = @(
    'compose', '--project-name', 'examine2', '--project-directory', $script:DeliveryPackageRoot,
    '--env-file', $script:DeliveryEnvPath, '-f', $script:DeliveryComposePath,
    'exec', '-T', 'mysql', 'sh', '-c',
    'exec mysqldump --single-transaction --routines --triggers --events -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
)
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = 'docker'
$startInfo.Arguments = (($arguments | ForEach-Object { ConvertTo-NativeArgument ([string]$_) }) -join ' ')
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::Start($startInfo)
try {
    $stream = [System.IO.File]::Create($sqlPath)
    try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
    $errorText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "mysqldump failed: $errorText" }
} finally { $process.Dispose() }

$filesRoot = Join-Path $dataRoot 'files'
$filesArchive = Join-Path $backupRoot 'files.zip'
$temporaryFilesRoot = $null
if (Test-Path -LiteralPath $filesRoot -PathType Container) {
    $archiveSource = $filesRoot
} else {
    $temporaryFilesRoot = Join-Path $backupRoot '.empty-files'
    [void](New-Item -ItemType Directory -Path $temporaryFilesRoot)
    $archiveSource = $temporaryFilesRoot
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    [System.IO.Compression.ZipFile]::CreateFromDirectory($archiveSource, $filesArchive, [System.IO.Compression.CompressionLevel]::Optimal, $false)
} finally {
    if ($null -ne $temporaryFilesRoot) { Remove-Item -LiteralPath $temporaryFilesRoot }
}

$versionPath = Join-Path $script:DeliveryPackageRoot 'VERSION.json'
$manifest = [ordered]@{
    schemaVersion = 1
    createdAt = (Get-Date).ToString('o')
    label = $Label
    sourceVersion = Get-Content -LiteralPath $versionPath -Raw -Encoding UTF8 | ConvertFrom-Json
    database = @{ path = 'database.sql'; sha256 = (Get-FileHash $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant() }
    files = @{ path = 'files.zip'; sha256 = (Get-FileHash $filesArchive -Algorithm SHA256).Hash.ToLowerInvariant() }
    secretsIncluded = $false
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $backupRoot 'backup-manifest.json'), ($manifest | ConvertTo-Json -Depth 8), $utf8)
[pscustomobject]@{ status = 'BACKED_UP'; backupRoot = $backupRoot; secretsIncluded = $false }
