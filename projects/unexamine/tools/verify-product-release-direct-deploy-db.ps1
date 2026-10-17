# 同一安装目录在首装、同版更新和回切后，核对现有容器、两个应用进程和数据库持久化。
$ErrorActionPreference = 'Stop'
$package = Join-Path $env:TEMP 'unexamine-s19.7-direct-package-v5'
$packageV2 = Join-Path $env:TEMP 'unexamine-s19.7-direct-package-v2'
$root = Join-Path $env:TEMP 'unexamine-s19.7-direct-install'
$manifest = Get-Content -Raw -LiteralPath (Join-Path $package 'manifest.json') | ConvertFrom-Json
$backupFolder = Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'full-198-*' |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $backupFolder) { throw 'Direct deployment backup is missing' }
$backup = Get-Content -Raw -LiteralPath (Join-Path $backupFolder.FullName 'backup-result.json') | ConvertFrom-Json
$backupHashesValid = @($backup.items | Where-Object {
    $file = Join-Path $backupFolder.FullName ([System.IO.Path]::GetFileName([string]$_.storageUri))
    (Test-Path -LiteralPath $file -PathType Leaf) -and
        (Get-Item -LiteralPath $file).Length -eq [long]$_.sizeBytes -and
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -eq [string]$_.sha256
}).Count -eq 4

function Read-Scalar([string]$Sql) {
    # 查询只访问现有 mysql 容器内的隔离库；脚本不管理容器生命周期。
    $value = & docker exec mysql sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database=unexamine_s19_direct --batch --skip-column-names --execute="$1"' sh $Sql
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1) { throw "MySQL readback failed: $Sql" }
    return [string]$value
}

$backendPid = [int](Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/unexamine-server.pid'))
$frontendPid = [int](Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/unexamine-frontend.pid'))
$backend = Get-CimInstance Win32_Process -Filter "ProcessId=$backendPid"
$frontend = Get-CimInstance Win32_Process -Filter "ProcessId=$frontendPid"
$containers = @(docker ps --format '{{.Names}}' | Sort-Object)
$frontendIndex = @($manifest.frontendFiles | Where-Object { $_.path -eq 'index.html' })[0]
$evidence = [ordered]@{
    step = 'S19.7'
    candidate = '0.1.0-s19.7-dev5'
    deploymentMode = 'one-java-backend-one-node-frontend'
    database = 'unexamine_s19_direct'
    flywayVersion = [int](Read-Scalar 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1')
    existingSystemCount = [int](Read-Scalar "SELECT COUNT(*) FROM un_sys_system WHERE code='s19_direct_deploy_system'")
    dockerContainers = $containers
    backendPid = $backendPid
    frontendPid = $frontendPid
    backendProcessVerified = [bool]$backend -and $backend.CommandLine -like '*unexamine-server.jar*'
    frontendProcessVerified = [bool]$frontend -and $frontend.CommandLine -like '*frontend-server.mjs*'
    packageHasCompose = Test-Path -LiteralPath (Join-Path $package 'compose.yaml')
    packageHasNginx = (Test-Path -LiteralPath (Join-Path $package 'nginx.stable.conf')) -or
        (Test-Path -LiteralPath (Join-Path $package 'nginx.conf'))
    secondPackageHasNginx = Test-Path -LiteralPath (Join-Path $packageV2 'nginx.stable.conf')
    frontendServerHashValid = (Get-FileHash -LiteralPath (Join-Path $package 'scripts/frontend-server.mjs') -Algorithm SHA256).Hash.ToLowerInvariant() -eq $manifest.frontendServerSha256
    frontendIndexHashValid = (Get-FileHash -LiteralPath (Join-Path $package 'frontend/index.html') -Algorithm SHA256).Hash.ToLowerInvariant() -eq $frontendIndex.sha256
    backendJarHashValid = (Get-FileHash -LiteralPath (Join-Path $package 'unexamine-server.jar') -Algorithm SHA256).Hash.ToLowerInvariant() -eq $manifest.artifactSha256
    backupDatabaseVersion = [int]$backup.databaseVersion
    backupTableCount = [int]$backup.databaseTableCount
    backupHashesValid = $backupHashesValid
    backupSqlHasSystem = (Get-Content -Raw -LiteralPath (Join-Path $backupFolder.FullName 'database.sql')).Contains('s19_direct_deploy_system')
    currentVersion = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/current-release.txt')).Trim()
    deployedVersion = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/last-deployment.json') | ConvertFrom-Json).version
    rollbackVersion = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/last-rollback.json') | ConvertFrom-Json).targetVersion
    backendSha256 = $manifest.artifactSha256
    frontendServerSha256 = $manifest.frontendServerSha256
}
if ($evidence.flywayVersion -ne 23 -or $evidence.existingSystemCount -ne 1 -or
        $containers.Count -ne 2 -or $containers[0] -ne 'mysql' -or $containers[1] -ne 'redis' -or
        -not $evidence.backendProcessVerified -or -not $evidence.frontendProcessVerified -or
        $evidence.packageHasCompose -or $evidence.packageHasNginx -or $evidence.secondPackageHasNginx -or
        -not $evidence.frontendServerHashValid -or -not $evidence.frontendIndexHashValid -or
        -not $evidence.backendJarHashValid -or $evidence.backupDatabaseVersion -ne 23 -or
        $evidence.backupTableCount -lt 1 -or -not $evidence.backupHashesValid -or
        -not $evidence.backupSqlHasSystem -or $evidence.currentVersion -ne '0.1.0-s19.7-dev5' -or
        $evidence.deployedVersion -ne '0.1.0-s19.7-dev5' -or $evidence.rollbackVersion -ne '0.1.0-s19.7-dev4') {
    throw "Direct package readback failed: $($evidence | ConvertTo-Json -Depth 5)"
}
$path = 'projects/unexamine/ai/evidence/s19-direct-deployment-readback.json'
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $path), ($evidence | ConvertTo-Json -Depth 5),
    [System.Text.UTF8Encoding]::new($false))
$evidence | ConvertTo-Json -Depth 5
