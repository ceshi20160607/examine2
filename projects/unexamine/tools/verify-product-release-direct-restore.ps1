# 同一直接部署包在现有 MySQL 容器上完成备份后，核对新库恢复与文件字节。
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP 'unexamine-s19.7-direct-install'
$sourceDatabase = 'unexamine_s19_direct'
$restoredDatabase = 'unexamine_drill_s19_direct_v3'
$backupFolder = Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'full-199-*' |
    Sort-Object Name -Descending | Select-Object -First 1
$drillFolder = Get-ChildItem -LiteralPath (Join-Path $root 'drills/s19-direct-drill') -Directory -Filter 'backup-199-*' |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $backupFolder -or -not $drillFolder) { throw 'Current direct package backup or drill is missing' }
$manifest = Get-Content -Raw -LiteralPath (Join-Path $backupFolder.FullName 'backup-result.json') | ConvertFrom-Json
$verification = Get-Content -Raw -LiteralPath (Join-Path $drillFolder.FullName 'verification.json') | ConvertFrom-Json
$secrets = Get-Content -Raw -LiteralPath (Join-Path $backupFolder.FullName 'secret-references.json') | ConvertFrom-Json

function Read-Scalar([string]$Database, [string]$Sql) {
    $value = & docker exec mysql sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" --batch --skip-column-names --execute="$2"' sh $Database $Sql
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') {
        throw "Restore readback failed: $Database"
    }
    return [int]$value
}

$marker = 's19-direct-restore-marker.bin'
$sourceMarker = Join-Path $env:TEMP "unexamine-s19.7-files/$marker"
$restoredMarker = Join-Path $drillFolder.FullName "files/$marker"
$backupHashesValid = @($manifest.items | Where-Object {
    $file = Join-Path $backupFolder.FullName ([System.IO.Path]::GetFileName([string]$_.storageUri))
    (Test-Path -LiteralPath $file -PathType Leaf) -and
        (Get-Item -LiteralPath $file).Length -eq [long]$_.sizeBytes -and
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -eq [string]$_.sha256
}).Count -eq 4
$sourceMarkerSha = (Get-FileHash -LiteralPath $sourceMarker -Algorithm SHA256).Hash.ToLowerInvariant()
$restoredMarkerSha = (Get-FileHash -LiteralPath $restoredMarker -Algorithm SHA256).Hash.ToLowerInvariant()
$evidence = [ordered]@{
    step = 'S19.8'
    candidate = '0.1.0-s19.8-dev1'
    sourceDatabase = $sourceDatabase
    restoredDatabase = $restoredDatabase
    sourceFlywayVersion = Read-Scalar $sourceDatabase 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
    restoredFlywayVersion = Read-Scalar $restoredDatabase 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
    sourceTableCount = Read-Scalar $sourceDatabase "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'"
    restoredTableCount = Read-Scalar $restoredDatabase "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'"
    sourceSystemCount = Read-Scalar $sourceDatabase "SELECT COUNT(*) FROM un_sys_system WHERE code='s19_direct_deploy_system'"
    restoredSystemCount = Read-Scalar $restoredDatabase "SELECT COUNT(*) FROM un_sys_system WHERE code='s19_direct_deploy_system'"
    sourceMarkerSha256 = $sourceMarkerSha
    restoredMarkerSha256 = $restoredMarkerSha
    backupItemsVerified = $backupHashesValid
    secretMaterialIncluded = [bool]$secrets.secretMaterialIncluded
    secretRestoreStrategy = [string]$secrets.restoreStrategy
    drillStatus = [string]$verification.status
    drillDatabaseVersion = [int]$verification.restoredDatabaseVersion
    drillTableCount = [int]$verification.restoredTableCount
    currentRelease = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/current-release.txt')).Trim()
    dockerContainers = @(docker ps --format '{{.Names}}' | Sort-Object)
}
if ($evidence.sourceFlywayVersion -ne 23 -or $evidence.restoredFlywayVersion -ne 23 -or
        $evidence.sourceTableCount -ne 191 -or $evidence.restoredTableCount -ne 191 -or
        $evidence.sourceSystemCount -ne 1 -or $evidence.restoredSystemCount -ne 1 -or
        $sourceMarkerSha -ne $restoredMarkerSha -or -not $backupHashesValid -or
        $evidence.secretMaterialIncluded -or $evidence.secretRestoreStrategy -ne 'ROTATE_ON_RESTORE' -or
        $evidence.drillStatus -ne 'PASSED' -or $evidence.drillDatabaseVersion -ne 23 -or
        $evidence.drillTableCount -ne 191 -or $evidence.currentRelease -ne $evidence.candidate -or
        @($evidence.dockerContainers).Count -ne 2 -or $evidence.dockerContainers[0] -ne 'mysql' -or
        $evidence.dockerContainers[1] -ne 'redis') {
    throw "Current package restore readback failed: $($evidence | ConvertTo-Json -Depth 5)"
}
$path = 'projects/unexamine/ai/evidence/s19-direct-restore-readback.json'
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $path), ($evidence | ConvertTo-Json -Depth 5),
    [System.Text.UTF8Encoding]::new($false))
$evidence | ConvertTo-Json -Depth 5
