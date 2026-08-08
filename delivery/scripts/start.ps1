param([int]$TimeoutSeconds = 240)

. (Join-Path $PSScriptRoot 'common.ps1')

$values = Get-DeliveryEnvMap
Assert-DeliveryEnvironment $values
Assert-DockerCompose
& (Join-Path $PSScriptRoot 'verify-package.ps1') | Out-Null

$dataRoot = Get-DeliveryDataRoot $values
foreach ($relative in @('files', 'logs', 'backups')) {
    $path = Join-Path $dataRoot $relative
    if (-not (Test-Path -LiteralPath $path)) { [void](New-Item -ItemType Directory -Path $path) }
}

Invoke-DeliveryCompose @('up', '-d', '--remove-orphans')
$health = Wait-DeliveryHealth $values $TimeoutSeconds
[pscustomobject]@{
    status = 'STARTED'
    backend = $health.backend
    frontend = $health.frontend
    log = (Join-Path $dataRoot 'logs/examine2.log')
}
