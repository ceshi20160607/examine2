param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$JsonPath = 'session/project-progress.json',
    [string]$OutputPath = 'session/PROJECT_PROGRESS.md'
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$validator = Join-Path $PSScriptRoot 'validate-project-progress.ps1'
$outputFile = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    [System.IO.Path]::GetFullPath($OutputPath)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $rootPath $OutputPath))
}
$rootBoundary = [System.IO.Path]::GetFullPath($rootPath).TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar)
$rootPrefix = $rootBoundary + [System.IO.Path]::DirectorySeparatorChar
if ($outputFile -ne $rootBoundary -and
    -not $outputFile.StartsWith(
        $rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Canonical progress projection output must stay inside the selected root.'
}
if (-not (Test-Path -LiteralPath (Split-Path -Parent $outputFile) -PathType Container)) {
    throw "Canonical progress projection directory does not exist: $(Split-Path -Parent $outputFile)"
}

$projection = & $validator -Root $rootPath -JsonPath $JsonPath `
    -PrintCanonicalMarkdown
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)
[System.IO.File]::WriteAllText($outputFile, [string]$projection, $utf8)
Write-Output "Canonical project progress projection written: $outputFile"
