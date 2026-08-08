param([int]$TimeoutSeconds = 240)

& (Join-Path $PSScriptRoot 'stop.ps1') | Out-Null
& (Join-Path $PSScriptRoot 'start.ps1') -TimeoutSeconds $TimeoutSeconds
