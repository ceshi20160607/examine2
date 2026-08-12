param(
    [string]$BaseRoot = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)]
    [string]$InstanceRoot,
    [ValidateSet('Apply', 'Verify')]
    [string]$Mode = 'Verify'
)

$ErrorActionPreference = 'Stop'
$basePath = (Resolve-Path -LiteralPath $BaseRoot).Path
$instancePath = (Resolve-Path -LiteralPath $InstanceRoot).Path
$manifestPath = Join-Path $basePath 'framework-manifest.json'

function Get-PortableRelativePath([string]$From, [string]$To) {
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $fromPath = $From.TrimEnd($separator) + $separator
    $fromUri = [Uri]::new($fromPath)
    $toUri = [Uri]::new($To)
    return [Uri]::UnescapeDataString($fromUri.MakeRelativeUri($toUri).ToString()).Replace('\', '/')
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'Base framework-manifest.json is missing; build it before sync.'
}
$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or [string]::IsNullOrWhiteSpace([string]$manifest.frameworkVersion)) {
    throw 'Base framework manifest is invalid.'
}

foreach ($entry in @($manifest.files)) {
    $source = Join-Path $basePath ([string]$entry.path)
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Base manifest file is missing: $($entry.path)"
    }
    $actual = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $entry.sha256) {
        throw "Base manifest hash drift: $($entry.path)"
    }
}

$lockPath = Join-Path $instancePath 'base.lock.json'
if ($Mode -eq 'Apply') {
    foreach ($entry in @($manifest.files)) {
        $source = Join-Path $basePath ([string]$entry.path)
        $target = Join-Path $instancePath ([string]$entry.path)
        $targetDirectory = Split-Path -Parent $target
        if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
            [void](New-Item -ItemType Directory -Path $targetDirectory -Force)
        }
        Copy-Item -LiteralPath $source -Destination $target -Force
    }
    Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $instancePath 'framework-manifest.json') -Force
    $existingLock = if (Test-Path -LiteralPath $lockPath -PathType Leaf) {
        Get-Content -Raw -Encoding UTF8 $lockPath | ConvertFrom-Json
    } else {
        $null
    }
    $now = [DateTimeOffset]::Now.ToString('o')
    $lock = [ordered]@{
        schemaVersion = 1
        frameworkVersion = [string]$manifest.frameworkVersion
        basePath = Get-PortableRelativePath $instancePath $basePath
        manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
        instantiatedAt = if ($null -ne $existingLock -and -not [string]::IsNullOrWhiteSpace([string]$existingLock.instantiatedAt)) { [string]$existingLock.instantiatedAt } else { $now }
        syncedAt = $now
        allowedOverlays = @($manifest.instanceOwnedFiles)
    }
    [System.IO.File]::WriteAllText(
        $lockPath,
        (($lock | ConvertTo-Json -Depth 8) + "`n"),
        [System.Text.UTF8Encoding]::new($false))
}

if (-not (Test-Path -LiteralPath $lockPath -PathType Leaf)) {
    throw 'Instance base.lock.json is missing; apply Base sync first.'
}
$lockValue = Get-Content -Raw -Encoding UTF8 $lockPath | ConvertFrom-Json
$expectedManifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($lockValue.schemaVersion -ne 1 -or
    $lockValue.frameworkVersion -ne $manifest.frameworkVersion -or
    $lockValue.manifestSha256 -ne $expectedManifestHash -or
    [string]::IsNullOrWhiteSpace([string]$lockValue.instantiatedAt) -or
    [string]::IsNullOrWhiteSpace([string]$lockValue.syncedAt)) {
    throw 'Instance Base lock does not match the current manifest.'
}
foreach ($entry in @($manifest.files)) {
    $target = Join-Path $instancePath ([string]$entry.path)
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
        throw "Instance is missing Base file: $($entry.path)"
    }
    $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $entry.sha256) {
        throw "Instance has drifted from Base: $($entry.path)"
    }
}
Write-Output "Base instance $Mode passed: $instancePath <- $basePath"
