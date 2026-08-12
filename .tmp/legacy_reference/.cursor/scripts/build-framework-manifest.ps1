param(
    [string]$BaseRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$FrameworkVersion = '3.2.0'
)

$ErrorActionPreference = 'Stop'
$basePath = (Resolve-Path -LiteralPath $BaseRoot).Path
$outputPath = Join-Path $basePath 'framework-manifest.json'
$includedRoots = @('agents', 'skills', 'workflows', 'templates', 'scripts')
$includedFiles = @(
    'FRAMEWORK.md',
    'GOVERNANCE.md',
    'LEADER.md',
    'INSTANCE.template.md',
    'session/state.template.json',
    'session/project-progress.template.json',
    'session/PROJECT_PROGRESS.template.md'
)

function Get-PortableRelativePath([string]$From, [string]$To) {
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $fromPath = $From.TrimEnd($separator) + $separator
    $fromUri = [Uri]::new($fromPath)
    $toUri = [Uri]::new($To)
    return [Uri]::UnescapeDataString($fromUri.MakeRelativeUri($toUri).ToString()).Replace('\', '/')
}

$files = @()
foreach ($rootName in $includedRoots) {
    $rootPath = Join-Path $basePath $rootName
    foreach ($file in Get-ChildItem -LiteralPath $rootPath -Recurse -File) {
        $relative = Get-PortableRelativePath $basePath $file.FullName
        if ($relative -match '(^|/)__pycache__(/|$)' -or $relative.EndsWith('.pyc')) {
            continue
        }
        $files += [ordered]@{
            path = $relative
            sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
}
foreach ($relative in $includedFiles) {
    $filePath = Join-Path $basePath $relative
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        throw "Manifest input does not exist: $relative"
    }
    $files += [ordered]@{
        path = $relative.Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$orderedFiles = @($files | Sort-Object path)
if (@($orderedFiles.path | Sort-Object -Unique).Count -ne $orderedFiles.Count) {
    throw 'Framework manifest contains duplicate paths.'
}

$manifest = [ordered]@{
    schemaVersion = 1
    frameworkVersion = $FrameworkVersion
    files = $orderedFiles
    instanceOwnedFiles = @(
        'INSTANCE.md',
        'README.md',
        'base.lock.json',
        'session/state.json',
        'session/project-delivery.json',
        'session/project-progress.json',
        'session/PROJECT_PROGRESS.md',
        'session/current-node.md',
        'session/delivery-roadmap.md',
        'session/pending-user-decisions.md',
        'PROJECT_FRAMEWORK.md'
    )
}

$json = ($manifest | ConvertTo-Json -Depth 8) + "`n"
[System.IO.File]::WriteAllText($outputPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Output "Framework manifest written: $outputPath ($($orderedFiles.Count) files)"
