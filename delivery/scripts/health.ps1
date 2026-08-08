param([int]$TimeoutSeconds = 15)

. (Join-Path $PSScriptRoot 'common.ps1')
$values = Get-DeliveryEnvMap
Assert-DeliveryEnvironment $values
$health = Wait-DeliveryHealth $values $TimeoutSeconds
$containers = & docker compose --project-name examine2 --project-directory $script:DeliveryPackageRoot `
    --env-file $script:DeliveryEnvPath -f $script:DeliveryComposePath ps --format json
[pscustomobject]@{
    status = $health.status
    backend = $health.backend
    frontend = $health.frontend
    containers = @($containers)
    version = Get-Content -LiteralPath (Join-Path $script:DeliveryPackageRoot 'VERSION.json') -Raw -Encoding UTF8 | ConvertFrom-Json
}
