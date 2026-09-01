param([Parameter(Mandatory = $true)][string]$DeploymentRoot)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$release = Get-CurrentReleaseDirectory $root
$pidFile = Join-Path $root 'runtime/unexamine-server.pid'
$state = 'STOPPED'
$pidValue = ''
if (Test-Path -LiteralPath $pidFile -PathType Leaf) {
    $pidValue = (Get-Content -Raw -LiteralPath $pidFile).Trim()
    if ($pidValue -match '^\d+$' -and (Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue)) { $state = 'RUNNING' }
}
Write-Output "$state release=$([System.IO.Path]::GetFileName($release)) pid=$pidValue"
