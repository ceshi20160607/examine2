param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$PackageDirectory,
    [Parameter(Mandatory = $true)][string]$Environment,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$package = [System.IO.Path]::GetFullPath($PackageDirectory)
$environmentFilePath = [System.IO.Path]::GetFullPath($EnvironmentFile)
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
$manifestPath = Join-Path $package 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "manifest.json is missing" }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($Confirm -ne "DEPLOY $($manifest.version) TO $Environment") { throw 'Deployment confirmation does not match target version and environment' }
if ($manifest.environment -ne $Environment) { throw 'Package environment does not match target environment' }
if ($manifest.databaseStrategy -eq 'UNRECOVERABLE') { throw 'Unrecoverable database migration cannot be deployed' }
$jar = Join-Path $package 'unexamine-server.jar'
$static = Join-Path $package 'frontend'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf) -or -not (Test-Path -LiteralPath $static -PathType Container)) {
    throw 'Fixed backend or frontend package is missing'
}
$actualHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne [string]$manifest.artifactSha256) { throw 'Backend artifact SHA-256 mismatch' }
$releasesRoot = Join-Path $root 'releases'
$runtimeRoot = Join-Path $root 'runtime'
$backupRoot = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $releasesRoot, $runtimeRoot, $backupRoot | Out-Null
$target = Join-Path $releasesRoot ([string]$manifest.version)
if (Test-Path -LiteralPath $target) {
    $storedHash = (Get-FileHash -LiteralPath (Join-Path $target 'unexamine-server.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($storedHash -ne $actualHash) { throw "Release already exists with a different immutable artifact: $target" }
} else {
    $stage = Join-Path $releasesRoot ('.stage-' + [guid]::NewGuid().ToString('N'))
    Copy-Item -LiteralPath $package -Destination $stage -Recurse
    Move-Item -LiteralPath $stage -Destination $target
}
$pointer = Join-Path $runtimeRoot 'current-release.txt'
$previousVersion = $null
if (Test-Path -LiteralPath $pointer) {
    $previousVersion = (Get-Content -Raw -LiteralPath $pointer).Trim()
    Copy-Item -LiteralPath $pointer -Destination (Join-Path $backupRoot ("current-release-$((Get-Date).ToString('yyyyMMddHHmmss')).txt"))
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
}
Set-Content -LiteralPath $pointer -Value ([string]$manifest.version)
try {
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $target -EvidenceLabel ([string]$manifest.version)
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    Wait-DeploymentEndpoint -Uri ([string]$manifest.backendReadinessUrl) -TimeoutSeconds 60 | Out-Null
    Wait-DeploymentEndpoint -Uri ([string]$manifest.frontendSmokeUrl) -TimeoutSeconds 30 | Out-Null
    $record = [ordered]@{ version=$manifest.version; environment=$Environment; artifactSha256=$actualHash; approvalReference=$ApprovalReference; readinessUrl=$manifest.backendReadinessUrl; realEntryUrl=$manifest.frontendSmokeUrl; result='SUCCESS'; deployedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-deployment.json')
} catch {
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
    if ($previousVersion) {
        Set-Content -LiteralPath $pointer -Value $previousVersion
        $previousRelease = Get-CurrentReleaseDirectory $root
        Switch-DeploymentStatic -Root $root -ReleaseDirectory $previousRelease -EvidenceLabel 'failed-deploy-restore'
        & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    } elseif (Test-Path -LiteralPath $pointer -PathType Leaf) {
        Remove-Item -LiteralPath $pointer
    }
    $record = [ordered]@{ version=$manifest.version; environment=$Environment; artifactSha256=$actualHash; approvalReference=$ApprovalReference; result='FAILED'; failure=$_.Exception.Message; restoredVersion=$previousVersion; failedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-deployment.json')
    throw
}
Write-Output "Deployment $($manifest.version) succeeded; approval=$ApprovalReference"
