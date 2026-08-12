param(
    [Parameter(Mandatory = $true)][string]$NewPackageRoot,
    [switch]$ConfirmUpgrade
)

. (Join-Path $PSScriptRoot 'common.ps1')
if (-not $ConfirmUpgrade) { throw 'Upgrade changes the running version. Re-run with -ConfirmUpgrade.' }
$newRoot = (Resolve-Path -LiteralPath $NewPackageRoot).Path
if ($newRoot -eq $script:DeliveryPackageRoot) { throw 'NewPackageRoot must reference a different package.' }
& (Join-Path $newRoot 'scripts/verify-package.ps1') | Out-Null
$backup = & (Join-Path $PSScriptRoot 'backup.ps1') -Label 'pre-upgrade'
& (Join-Path $PSScriptRoot 'stop.ps1') | Out-Null
Copy-Item -LiteralPath $script:DeliveryEnvPath -Destination (Join-Path $newRoot '.env') -Force
try {
    & (Join-Path $newRoot 'scripts/start.ps1') | Out-Null
    [pscustomobject]@{ status = 'UPGRADED'; from = $script:DeliveryPackageRoot; to = $newRoot; backupRoot = $backup.backupRoot }
} catch {
    Write-Error "Upgrade failed. Roll back with: pwsh -File `"$PSScriptRoot/rollback.ps1`" -PreviousPackageRoot `"$script:DeliveryPackageRoot`" -BackupRoot `"$($backup.backupRoot)`" -ConfirmRollback"
    throw
}
