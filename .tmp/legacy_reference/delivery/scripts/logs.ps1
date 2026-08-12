param([ValidateRange(1, 10000)][int]$Tail = 200, [switch]$Follow)

. (Join-Path $PSScriptRoot 'common.ps1')
Assert-DockerCompose
$arguments = @('logs', '--tail', [string]$Tail)
if ($Follow) { $arguments += '--follow' }
Invoke-DeliveryCompose $arguments
