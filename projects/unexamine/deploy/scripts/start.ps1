param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
Import-DeploymentEnvironment $EnvironmentFile
$release = Get-CurrentReleaseDirectory $root
$jar = Join-Path $release 'unexamine-server.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Fixed backend package is missing: $jar" }
$manifestPath = Join-Path $release 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Release manifest is missing: $manifestPath" }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$actualHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne [string]$manifest.artifactSha256) { throw 'Stored backend artifact SHA-256 mismatch' }
Assert-ReleaseFrontend -Directory (Join-Path $release 'frontend') -Manifest $manifest
Assert-FrontendServer -ReleaseDirectory $release -Manifest $manifest
if ($env:APP_ENVIRONMENT_CODE -ne [string]$manifest.environment) { throw 'Environment file and release manifest target different environments' }
if ($env:APP_CONFIG_VERSION -ne [string]$manifest.configVersion) { throw 'Environment file and release manifest have incompatible configuration versions' }
# 后端只监听 9999；启动前阻止旧配置把前端与后端拆到不同端口。
if ($env:APP_SERVER_PORT -ne '9999' -or
        [string]$manifest.backendReadinessUrl -ne 'http://127.0.0.1:9999/actuator/health/readiness') {
    throw 'Backend service port must be 9999 in environment and release manifest'
}
[Environment]::SetEnvironmentVariable('APP_RELEASE_VERSION', [string]$manifest.version, 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_VERSION', [string]$manifest.frontendVersion, 'Process')
[Environment]::SetEnvironmentVariable('APP_ARTIFACT_SHA256', $actualHash, 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_SMOKE_URL', [string]$manifest.frontendSmokeUrl, 'Process')
$logRoot = Join-Path $root 'logs'
$runRoot = Join-Path $root 'runtime'
New-Item -ItemType Directory -Force -Path $logRoot, $runRoot | Out-Null
$pidFile = Join-Path $runRoot 'unexamine-server.pid'
$frontendPidFile = Join-Path $runRoot 'unexamine-frontend.pid'
foreach ($marker in @($pidFile, $frontendPidFile)) {
    if (Test-Path -LiteralPath $marker) {
        $existing = (Get-Content -Raw -LiteralPath $marker).Trim()
        if ($existing -match '^\d+$' -and (Get-Process -Id ([int]$existing) -ErrorAction SilentlyContinue)) {
            throw "Unexamine is already running with PID $existing"
        }
        Remove-Item -LiteralPath $marker
    }
}
$stdout = Join-Path $logRoot 'unexamine-server.log'
$stderr = Join-Path $logRoot 'unexamine-server-error.log'
if ($env:APP_RUNTIME_MODE -eq 'DOCKER') { throw 'Run the packaged frontend and backend directly; Docker is only used for existing MySQL and Redis' }
$frontendPort = if ($env:APP_FRONTEND_PORT) { [string]$env:APP_FRONTEND_PORT } else { '15173' }
if ($frontendPort -notmatch '^\d+$' -or [int]$frontendPort -lt 1024 -or [int]$frontendPort -gt 65535 -or
        $frontendPort -eq '9999') {
    throw 'Frontend and backend ports must be distinct valid non-privileged ports'
}
$frontendScript = Join-Path $release 'scripts/frontend-server.mjs'
if (-not (Test-Path -LiteralPath $frontendScript -PathType Leaf)) { throw "Frontend server is missing: $frontendScript" }
$www = Join-Path $root 'www'
if (-not (Test-Path -LiteralPath (Join-Path $www 'index.html') -PathType Leaf)) { throw 'Published frontend index is missing' }
$java = if ($env:APP_JAVA_EXECUTABLE) { $env:APP_JAVA_EXECUTABLE } else { 'java' }
$node = if ($env:APP_NODE_EXECUTABLE) { $env:APP_NODE_EXECUTABLE } else { 'node' }
[Environment]::SetEnvironmentVariable('APP_FRONTEND_ROOT', $www, 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_HOST', $(if ($env:APP_FRONTEND_HOST) { $env:APP_FRONTEND_HOST } else { '127.0.0.1' }), 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_PORT', $frontendPort, 'Process')
$process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $root `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
Set-Content -LiteralPath $pidFile -Value $process.Id
$frontendProcess = $null
try {
    $frontendProcess = Start-Process -FilePath $node -ArgumentList ('"' + $frontendScript + '"') -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logRoot 'unexamine-frontend.log') `
        -RedirectStandardError (Join-Path $logRoot 'unexamine-frontend-error.log') -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $frontendPidFile -Value $frontendProcess.Id
    Start-Sleep -Seconds 1
    $process.Refresh()
    $frontendProcess.Refresh()
    if ($process.HasExited -or $frontendProcess.HasExited) { throw 'Backend or frontend process exited during startup' }
} catch {
    if ($frontendProcess -and -not $frontendProcess.HasExited) { Stop-Process -Id $frontendProcess.Id }
    if (-not $process.HasExited) { Stop-Process -Id $process.Id }
    Remove-Item -LiteralPath $pidFile, $frontendPidFile -ErrorAction SilentlyContinue
    throw
}
Write-Output "Started backend PID $($process.Id) and frontend PID $($frontendProcess.Id); logs: $logRoot"
