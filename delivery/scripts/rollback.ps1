param(
    [Parameter(Mandatory = $true)][string]$PreviousPackageRoot,
    [Parameter(Mandatory = $true)][string]$BackupRoot,
    [switch]$ConfirmRollback
)

. (Join-Path $PSScriptRoot 'common.ps1')
if (-not $ConfirmRollback) { throw 'Rollback restores database and files. Re-run with -ConfirmRollback.' }
$previous = (Resolve-Path -LiteralPath $PreviousPackageRoot).Path
& (Join-Path $previous 'scripts/verify-package.ps1') | Out-Null
if (-not (Test-Path (Join-Path $previous '.env') -PathType Leaf)) {
    Copy-Item -LiteralPath $script:DeliveryEnvPath -Destination (Join-Path $previous '.env')
}
& (Join-Path $previous 'scripts/restore.ps1') -BackupRoot $BackupRoot -ArtifactRoot $previous -ConfirmRestore
