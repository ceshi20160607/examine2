param(
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$TargetVersion,
    [Parameter(Mandatory = $true)][string]$EnvironmentFile,
    [Parameter(Mandatory = $true)][string]$DeploymentId,
    [Parameter(Mandatory = $true)][string]$ApprovalReference,
    [Parameter(Mandatory = $true)][string]$Confirm
)
. "$PSScriptRoot/common.ps1"
$root = Resolve-DeploymentRoot $DeploymentRoot
$environmentFilePath = [System.IO.Path]::GetFullPath($EnvironmentFile)
if ([string]::IsNullOrWhiteSpace($ApprovalReference)) { throw 'Approval reference is required' }
if ($TargetVersion -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw 'Target version is invalid' }
if ($Confirm -ne "ROLLBACK $DeploymentId") { throw 'Rollback confirmation does not match deployment id' }
$target = [System.IO.Path]::GetFullPath((Join-Path $root "releases/$TargetVersion"))
$releasesRoot = [System.IO.Path]::GetFullPath((Join-Path $root 'releases'))
if (-not $target.StartsWith($releasesRoot + [System.IO.Path]::DirectorySeparatorChar) -or -not (Test-Path -LiteralPath $target -PathType Container)) {
    throw 'Rollback target is outside the release store or missing'
}
$manifest = Get-Content -Raw -LiteralPath (Join-Path $target 'manifest.json') | ConvertFrom-Json
if ($manifest.databaseStrategy -eq 'UNRECOVERABLE') { throw 'Rollback target has an unrecoverable database strategy' }
$runtimeRoot = Join-Path $root 'runtime'
$backupRoot = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$pointer = Join-Path $runtimeRoot 'current-release.txt'
$previousVersion = (Get-Content -Raw -LiteralPath $pointer).Trim()
Copy-Item -LiteralPath $pointer -Destination (Join-Path $backupRoot ("pre-rollback-$DeploymentId-$((Get-Date).ToString('yyyyMMddHHmmss')).txt"))
& "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
Set-Content -LiteralPath $pointer -Value $TargetVersion
$jar = Join-Path $target 'unexamine-server.jar'
try {
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $target -EvidenceLabel ("rollback-$DeploymentId")
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    Wait-DeploymentEndpoint -Uri ([string]$manifest.backendReadinessUrl) -TimeoutSeconds 60 | Out-Null
    Wait-DeploymentEndpoint -Uri ([string]$manifest.frontendSmokeUrl) -TimeoutSeconds 30 | Out-Null
    $record = [ordered]@{ deploymentId=$DeploymentId; targetVersion=$TargetVersion; approvalReference=$ApprovalReference; databaseStrategy=$manifest.databaseStrategy; preBackup=$true; readinessUrl=$manifest.backendReadinessUrl; realEntryUrl=$manifest.frontendSmokeUrl; result='SUCCESS'; rolledBackAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-rollback.json')
} catch {
    & "$PSScriptRoot/stop.ps1" -DeploymentRoot $root
    Set-Content -LiteralPath $pointer -Value $previousVersion
    $previousRelease = Get-CurrentReleaseDirectory $root
    Switch-DeploymentStatic -Root $root -ReleaseDirectory $previousRelease -EvidenceLabel ("failed-rollback-$DeploymentId")
    & "$PSScriptRoot/start.ps1" -DeploymentRoot $root -EnvironmentFile $environmentFilePath
    $record = [ordered]@{ deploymentId=$DeploymentId; targetVersion=$TargetVersion; approvalReference=$ApprovalReference; databaseStrategy=$manifest.databaseStrategy; preBackup=$true; result='FAILED'; failure=$_.Exception.Message; restoredVersion=$previousVersion; failedAt=(Get-Date).ToString('o') }
    $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'last-rollback.json')
    throw
}
Write-Output "Rollback to $TargetVersion succeeded; approval=$ApprovalReference"
