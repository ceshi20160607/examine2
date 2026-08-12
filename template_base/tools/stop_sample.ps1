param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
)

$ErrorActionPreference = "Stop"
$generatedRoot = Join-Path $WorkspaceRoot "template_base/examples/work-order/generated"
$composePath = Join-Path $generatedRoot "deploy/compose.yaml"
$backendJar = Join-Path $generatedRoot "backend/target/work-order-sample-0.1.0-SNAPSHOT.jar"

function Stop-GeneratedListener {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$ExpectedToken
    )
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) { return }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
    if (-not $process) { return }
    $command = [string]$process.CommandLine
    if ($command.IndexOf($ExpectedToken, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Refusing to stop unrelated listener on port ${Port}: PID $($process.ProcessId)"
    }
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Output "Stopped generated listener on port $Port (PID $($process.ProcessId))."
}

Stop-GeneratedListener -Port 5173 -ExpectedToken $generatedRoot
Stop-GeneratedListener -Port 18080 -ExpectedToken $backendJar
docker compose -f $composePath down --volumes --remove-orphans | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker dependencies failed to stop."
}
Write-Output "Stopped sample runtime and Docker dependencies."
