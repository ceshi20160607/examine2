param(
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$FrontendDirectory,
    [Parameter(Mandatory = $true)][string]$GuidesDirectory,
    [Parameter(Mandatory = $true)][string]$PackageDirectory,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Environment,
    [Parameter(Mandatory = $true)][string]$ConfigVersion,
    [Parameter(Mandatory = $true)][string]$BackendReadinessUrl,
    [Parameter(Mandatory = $true)][string]$FrontendSmokeUrl
)
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
if ($Version -notmatch '^[0-9A-Za-z._-]{1,100}$' -or $Environment -notmatch '^[0-9A-Za-z._-]{1,100}$' -or
    $ConfigVersion -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw 'Release version, environment or configuration version is invalid' }
# 产品后端端口已固定，制包时拒绝带旧健康检查地址的清单。
if ($BackendReadinessUrl -ne 'http://127.0.0.1:9999/actuator/health/readiness') {
    throw 'Backend readiness must use fixed port 9999'
}
$jar = [System.IO.Path]::GetFullPath($JarPath)
$frontend = [System.IO.Path]::GetFullPath($FrontendDirectory)
$guides = [System.IO.Path]::GetFullPath($GuidesDirectory)
$deployRoot = Split-Path -Parent $PSScriptRoot
$databaseRoot = [System.IO.Path]::GetFullPath((Join-Path $deployRoot '../db'))
$package = [System.IO.Path]::GetFullPath($PackageDirectory)
if (-not (Test-Path -LiteralPath $jar -PathType Leaf) -or -not (Test-Path -LiteralPath $frontend -PathType Container) -or
    -not (Test-Path -LiteralPath $guides -PathType Container)) { throw 'Backend, frontend or guides source is missing' }
if (Test-Path -LiteralPath $package) { throw 'Package directory already exists; choose a new immutable version and path' }
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $versions = @($archive.Entries | Where-Object { $_.FullName -match '(?:^|/)db/migration/V([0-9]+)__[^/]+\.sql$' } |
        ForEach-Object { [int]([regex]::Match($_.FullName, '/V([0-9]+)__').Groups[1].Value) } | Sort-Object -Unique)
} finally { $archive.Dispose() }
if ($versions.Count -eq 0 -or $versions.Count -ne $versions[-1]) { throw 'Backend JAR has an incomplete Flyway migration sequence' }

# 发布包只复制当前构建制品与手册；配置和凭据由目标环境单独提供。
New-Item -ItemType Directory -Path (Split-Path -Parent $package) -Force | Out-Null
New-Item -ItemType Directory -Path $package -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $package 'unexamine-server.jar')
Copy-Item -LiteralPath $frontend -Destination (Join-Path $package 'frontend') -Recurse
Copy-Item -LiteralPath $guides -Destination (Join-Path $package 'guides') -Recurse
Copy-Item -LiteralPath (Join-Path $deployRoot 'README.md') -Destination (Join-Path $package 'README.md')
Copy-Item -LiteralPath (Join-Path $deployRoot 'scripts') -Destination (Join-Path $package 'scripts') -Recurse
Copy-Item -LiteralPath (Join-Path $deployRoot 'env') -Destination (Join-Path $package 'env') -Recurse
New-Item -ItemType Directory -Path (Join-Path $package 'db') | Out-Null
foreach ($name in @('README.md', 'init.sql', 'update.sql', 'final.sql')) {
    Copy-Item -LiteralPath (Join-Path $databaseRoot $name) -Destination (Join-Path (Join-Path $package 'db') $name)
}
$frontendRoot = Join-Path $package 'frontend'
$frontendFiles = @(Get-ChildItem -LiteralPath $frontendRoot -File -Recurse | ForEach-Object {
    [ordered]@{
        path = $_.FullName.Substring($frontendRoot.Length + 1).Replace([System.IO.Path]::DirectorySeparatorChar, '/')
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
} | Sort-Object path)
$manifest = [ordered]@{
    schemaVersion = 3
    version = $Version
    frontendVersion = $Version
    environment = $Environment
    databaseVersion = [string]$versions[-1]
    databaseStrategy = 'FORWARD_FIX'
    configVersion = $ConfigVersion
    artifactSha256 = (Get-FileHash -LiteralPath (Join-Path $package 'unexamine-server.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    frontendFiles = $frontendFiles
    frontendServerSha256 = (Get-FileHash -LiteralPath (Join-Path $package 'scripts/frontend-server.mjs') -Algorithm SHA256).Hash.ToLowerInvariant()
    backendReadinessUrl = $BackendReadinessUrl
    frontendSmokeUrl = $FrontendSmokeUrl
}
[System.IO.File]::WriteAllText((Join-Path $package 'manifest.json'), ($manifest | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))
Assert-FrontendArtifact -Directory $frontendRoot -Manifest $manifest
Assert-FrontendServer -ReleaseDirectory $package -Manifest $manifest
$zipPath = "$package.zip"
if (Test-Path -LiteralPath $zipPath) { throw 'Package archive already exists' }
Compress-Archive -Path (Join-Path $package '*') -DestinationPath $zipPath -CompressionLevel Optimal
$archiveHash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
[ordered]@{ version=$Version; archive=(Split-Path -Leaf $zipPath); sha256=$archiveHash } |
    ConvertTo-Json | ForEach-Object { [System.IO.File]::WriteAllText("$zipPath.sha256.json", $_, (New-Object System.Text.UTF8Encoding($false))) }
$manifest | ConvertTo-Json -Depth 8
