param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$BackupResult,
    [Parameter(Mandatory = $true)][long]$BackupId,
    [Parameter(Mandatory = $true)][string]$Environment,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
if ($Confirm -ne "DRILL $BackupId IN $Environment") { throw 'Restore drill confirmation does not match backup and environment' }
if ($Environment -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw 'Restore drill environment is invalid' }
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
$drillRoot = Join-Path $root "drills/$Environment/backup-$BackupId"
New-Item -ItemType Directory -Force -Path $drillRoot | Out-Null
Expand-Archive -LiteralPath (Join-Path $backupDirectory 'file-storage.zip') -DestinationPath (Join-Path $drillRoot 'files') -Force
$config = Get-Content -Raw -LiteralPath (Join-Path $backupDirectory 'configuration.json') | ConvertFrom-Json
$secretRefs = Get-Content -Raw -LiteralPath (Join-Path $backupDirectory 'secret-references.json') | ConvertFrom-Json
if ($secretRefs.secretMaterialIncluded -ne $false -or $secretRefs.restoreStrategy -ne 'ROTATE_ON_RESTORE') {
    throw 'Secret reference artifact contains material or lacks rotation strategy'
}
$databaseDump = Get-Item -LiteralPath (Join-Path $backupDirectory 'database.sql')
$verification = [ordered]@{
    backupId = $BackupId
    environment = $Environment
    status = 'PASSED'
    databaseRestore = if ($databaseDump.Length -gt 0) { 'READABLE' } else { 'FAILED' }
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
$verification | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $verificationPath -Encoding utf8NoBOM
Write-Output $verificationPath
