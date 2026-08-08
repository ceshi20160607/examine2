param([switch]$SkipManifest)

. (Join-Path $PSScriptRoot 'common.ps1')

$required = @(
    'backend/examine-web.jar',
    'frontend/index.html',
    'sql/migration',
    'deploy/compose.yaml',
    'deploy/nginx.conf',
    '.env.example',
    'VERSION.json',
    'manifest.json'
)
foreach ($relative in $required) {
    $path = Join-Path $script:DeliveryPackageRoot $relative
    if (-not (Test-Path -LiteralPath $path)) { throw "Package entry is missing: $relative" }
}

if (-not $SkipManifest) {
    $manifestPath = Join-Path $script:DeliveryPackageRoot 'manifest.json'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $manifest.files) {
        $path = Join-Path $script:DeliveryPackageRoot ([string]$entry.path).Replace('/', '\')
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Manifest entry is missing: $($entry.path)"
        }
        $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne [string]$entry.sha256) {
            throw "Manifest checksum mismatch: $($entry.path)"
        }
    }
}

$version = Get-Content -LiteralPath (Join-Path $script:DeliveryPackageRoot 'VERSION.json') -Raw -Encoding UTF8 | ConvertFrom-Json
[pscustomobject]@{
    status = 'PASS'
    cycleId = $version.cycleId
    version = $version.version
    packageRoot = $script:DeliveryPackageRoot
}
