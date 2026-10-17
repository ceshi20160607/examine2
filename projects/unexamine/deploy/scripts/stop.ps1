param([Parameter(Mandatory = $true)][string]$DeploymentRoot)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$runRoot = Join-Path $root 'runtime'
$stopped = @()
foreach ($service in @(
    @{ name='frontend'; marker='unexamine-frontend.pid'; command='frontend-server.mjs' },
    @{ name='backend'; marker='unexamine-server.pid'; command='unexamine-server.jar' }
)) {
    $pidFile = Join-Path $runRoot $service.marker
    if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { continue }
    $pidValue = (Get-Content -Raw -LiteralPath $pidFile).Trim()
    if ($pidValue -notmatch '^\d+$') { throw "Invalid PID file: $pidFile" }
    $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($process) {
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$pidValue"
        if (-not $processInfo -or $processInfo.CommandLine -notlike "*$($service.command)*") {
            throw "PID $pidValue no longer belongs to Unexamine $($service.name)"
        }
        Stop-Process -Id $process.Id
        if (-not $process.WaitForExit(20000)) { throw "Graceful stop timed out for PID $pidValue" }
    }
    Remove-Item -LiteralPath $pidFile
    $stopped += "$($service.name) PID $pidValue"
}
if ($stopped.Count) { Write-Output "Stopped $($stopped -join ', ')" }
else { Write-Output 'Unexamine is not running' }
