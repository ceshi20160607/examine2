param([Parameter(Mandatory = $true)][string]$DeploymentRoot)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$pidFile = Join-Path $root 'runtime/unexamine-server.pid'
if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { Write-Output 'Unexamine is not running'; return }
$pidValue = (Get-Content -Raw -LiteralPath $pidFile).Trim()
if ($pidValue -notmatch '^\d+$') { throw "Invalid PID file: $pidFile" }
$process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $process.Id
    if (-not $process.WaitForExit(20000)) { throw "Graceful stop timed out for PID $pidValue" }
}
Remove-Item -LiteralPath $pidFile -Force
Write-Output "Stopped Unexamine PID $pidValue"
