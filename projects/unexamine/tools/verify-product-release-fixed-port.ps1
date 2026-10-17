# 当前直连候选包的端口与业务数据读回，只访问本地隔离发布环境。
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP 'unexamine-s19.7-direct-install'
$version = '0.1.0-s19.9-dev1'
$release = Join-Path $root "releases/$version"
$package = 'projects/unexamine/release/0.1.0-s19.9-dev1'
$manifest = Get-Content -Raw -LiteralPath (Join-Path $package 'manifest.json') | ConvertFrom-Json
$backendPid = [int](Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/unexamine-server.pid')).Trim()
$frontendPid = [int](Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/unexamine-frontend.pid')).Trim()
$backendPorts = @(Get-NetTCPConnection -LocalPort 9999 -State Listen -ErrorAction SilentlyContinue)
$frontendPorts = @(Get-NetTCPConnection -LocalPort 15173 -State Listen -ErrorAction SilentlyContinue)
$oldPorts = @(Get-NetTCPConnection -LocalPort 18087 -State Listen -ErrorAction SilentlyContinue)
$backendHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:9999/actuator/health/readiness'
$proxiedHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:15173/actuator/health/readiness'
$api = Invoke-WebRequest -Uri 'http://127.0.0.1:15173/api/actuator/health/readiness' -SkipHttpErrorCheck
$apiBody = $api.Content | ConvertFrom-Json
$front = Invoke-WebRequest -Uri 'http://127.0.0.1:15173/' -UseBasicParsing
function Read-Scalar([string]$Sql) {
    $value = & docker exec mysql sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database=unexamine_s19_direct --batch --skip-column-names --execute="$1"' sh $Sql
    if ($LASTEXITCODE -ne 0 -or @($value).Count -ne 1 -or [string]$value -notmatch '^\d+$') {
        throw 'Fixed-port database readback failed'
    }
    return [int]$value
}
$result = [ordered]@{
    step = 'S19.9'
    candidate = $version
    currentRelease = (Get-Content -Raw -LiteralPath (Join-Path $root 'runtime/current-release.txt')).Trim()
    backendPort = 9999
    backendPid = $backendPid
    backendPortOwnedByBackend = @($backendPorts | Where-Object { $_.OwningProcess -eq $backendPid }).Count -gt 0
    frontendPort = 15173
    frontendPid = $frontendPid
    frontendPortOwnedByFrontend = @($frontendPorts | Where-Object { $_.OwningProcess -eq $frontendPid }).Count -gt 0
    previousPortClosed = $oldPorts.Count -eq 0
    backendHealth = [string]$backendHealth.status
    proxiedHealth = [string]$proxiedHealth.status
    frontendStatus = [int]$front.StatusCode
    apiStatus = [int]$api.StatusCode
    apiCode = [string]$apiBody.code
    flywayVersion = Read-Scalar 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'
    systemCount = Read-Scalar "SELECT COUNT(*) FROM un_sys_system WHERE code='s19_direct_deploy_system'"
    manifestPort = ([uri]$manifest.backendReadinessUrl).Port
    installedManifestSame = (Get-FileHash -LiteralPath (Join-Path $package 'manifest.json') -Algorithm SHA256).Hash -eq
        (Get-FileHash -LiteralPath (Join-Path $release 'manifest.json') -Algorithm SHA256).Hash
    jarHashValid = (Get-FileHash -LiteralPath (Join-Path $package 'unexamine-server.jar') -Algorithm SHA256).Hash.ToLowerInvariant() -eq
        [string]$manifest.artifactSha256
    frontendServerHashValid = (Get-FileHash -LiteralPath (Join-Path $package 'scripts/frontend-server.mjs') -Algorithm SHA256).Hash.ToLowerInvariant() -eq
        [string]$manifest.frontendServerSha256
    dockerContainers = @(docker ps --format '{{.Names}}' | Sort-Object)
}
if ($result.currentRelease -ne $version -or -not $result.backendPortOwnedByBackend -or
        -not $result.frontendPortOwnedByFrontend -or -not $result.previousPortClosed -or
        $result.backendHealth -ne 'UP' -or $result.proxiedHealth -ne 'UP' -or
        $result.frontendStatus -ne 200 -or $result.apiStatus -ne 401 -or
        $result.apiCode -ne 'AUTHENTICATION_REQUIRED' -or $result.flywayVersion -ne 23 -or
        $result.systemCount -ne 1 -or $result.manifestPort -ne 9999 -or
        -not $result.installedManifestSame -or -not $result.jarHashValid -or
        -not $result.frontendServerHashValid -or @($result.dockerContainers).Count -ne 2 -or
        $result.dockerContainers[0] -ne 'mysql' -or $result.dockerContainers[1] -ne 'redis') {
    throw "Fixed-port release readback failed: $($result | ConvertTo-Json -Depth 5)"
}
$path = 'projects/unexamine/ai/evidence/s19-fixed-backend-port-readback.json'
[IO.File]::WriteAllText((Join-Path (Get-Location) $path), ($result | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Depth 5
