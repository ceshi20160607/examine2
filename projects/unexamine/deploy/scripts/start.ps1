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
if ($env:APP_ENVIRONMENT_CODE -ne [string]$manifest.environment) { throw 'Environment file and release manifest target different environments' }
if ($env:APP_CONFIG_VERSION -ne [string]$manifest.configVersion) { throw 'Environment file and release manifest have incompatible configuration versions' }
[Environment]::SetEnvironmentVariable('APP_RELEASE_VERSION', [string]$manifest.version, 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_VERSION', [string]$manifest.frontendVersion, 'Process')
[Environment]::SetEnvironmentVariable('APP_ARTIFACT_SHA256', $actualHash, 'Process')
[Environment]::SetEnvironmentVariable('APP_FRONTEND_SMOKE_URL', [string]$manifest.frontendSmokeUrl, 'Process')
$logRoot = Join-Path $root 'logs'
$runRoot = Join-Path $root 'runtime'
New-Item -ItemType Directory -Force -Path $logRoot, $runRoot | Out-Null
$pidFile = Join-Path $runRoot 'unexamine-server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $existing = (Get-Content -Raw -LiteralPath $pidFile).Trim()
    if ($existing -match '^\d+$' -and (Get-Process -Id ([int]$existing) -ErrorAction SilentlyContinue)) {
        throw "Unexamine is already running with PID $existing"
    }
}
$stdout = Join-Path $logRoot 'unexamine-server.log'
$stderr = Join-Path $logRoot 'unexamine-server-error.log'
$process = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) -WorkingDirectory $root `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
Set-Content -LiteralPath $pidFile -Value $process.Id
Write-Output "Started Unexamine PID $($process.Id); logs: $stdout"
