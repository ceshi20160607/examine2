# 恢复演练的失败路径只在隔离目标上执行，并核对源库及现有容器不受影响。
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP 'unexamine-s19.7-direct-install'
$script = Join-Path $root 'releases/0.1.0-s19.8-dev1/scripts/restore-drill.ps1'
$backupFolder = Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'full-199-*' |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $backupFolder) { throw 'Verified backup is missing' }
$backupResult = Join-Path $backupFolder.FullName 'backup-result.json'
$baseArgs = @{
    DeploymentRoot = $root
    BackupId = 199
    Environment = 's19-direct-drill'
    DockerMySqlContainer = 'mysql'
    TargetHost = '127.0.0.1'
    Confirm = 'DRILL 199 IN s19-direct-drill'
}
function Expect-Denial([string]$Result, [string]$Database, [int]$Port, [string]$Expected) {
    $failure = $null
    try {
        & $script @baseArgs -BackupResult $Result -TargetPort $Port -TargetDatabaseName $Database | Out-Null
    } catch { $failure = $_.Exception.Message }
    if ($failure -notlike "*$Expected*") { throw "Unexpected restore result for $Database : $failure" }
    return $failure
}

$existingTarget = Expect-Denial $backupResult 'unexamine_drill_s19_direct_v3' 3306 'already exists'
$wrongPort = Expect-Denial $backupResult 'unexamine_drill_s19_direct_v4' 3307 'selected local MySQL container port'
$tamperedFolder = Join-Path $env:TEMP 'unexamine-s19.8-tampered-backup'
if (Test-Path -LiteralPath $tamperedFolder) { throw 'Tampered backup fixture path must be new' }
Copy-Item -LiteralPath $backupFolder.FullName -Destination $tamperedFolder -Recurse
Add-Content -LiteralPath (Join-Path $tamperedFolder 'configuration.json') -Value 'tampered fixture'
$tamperedBackup = Expect-Denial (Join-Path $tamperedFolder 'backup-result.json') `
    'unexamine_drill_s19_direct_v5' 3306 'artifact verification failed'

function Read-Scalar([string]$Sql) {
    $value = & docker exec mysql sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names --execute="$1"' sh $Sql
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1) { throw 'Denial readback failed' }
    return [int]$value
}
$evidence = [ordered]@{
    step = 'S19.8'
    candidate = '0.1.0-s19.8-dev1'
    existingTargetDenied = $existingTarget
    wrongContainerPortDenied = $wrongPort
    tamperedBackupDenied = $tamperedBackup
    sourceDatabasePresent = Read-Scalar "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='unexamine_s19_direct'"
    rejectedTargetDatabaseCount = Read-Scalar "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('unexamine_drill_s19_direct_v4','unexamine_drill_s19_direct_v5')"
    currentRelease = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/current-release.txt')).Trim()
    dockerContainers = @(docker ps --format '{{.Names}}' | Sort-Object)
}
if ($evidence.sourceDatabasePresent -ne 1 -or $evidence.rejectedTargetDatabaseCount -ne 0 -or
        $evidence.currentRelease -ne $evidence.candidate -or @($evidence.dockerContainers).Count -ne 2 -or
        $evidence.dockerContainers[0] -ne 'mysql' -or $evidence.dockerContainers[1] -ne 'redis') {
    throw "Restore denial isolation failed: $($evidence | ConvertTo-Json -Depth 5)"
}
$path = 'projects/unexamine/ai/evidence/s19-direct-restore-denial.json'
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $path), ($evidence | ConvertTo-Json -Depth 5),
    [System.Text.UTF8Encoding]::new($false))
$evidence | ConvertTo-Json -Depth 5
