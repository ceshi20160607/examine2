param([int]$TimeoutSeconds = 60)

. (Join-Path $PSScriptRoot 'common.ps1')
Assert-DockerCompose
Invoke-DeliveryCompose @('stop', '--timeout', [string]$TimeoutSeconds)
[pscustomobject]@{ status = 'STOPPED'; dataPreserved = $true }
