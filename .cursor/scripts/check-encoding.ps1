param(
    [string[]]$Roots = @(".cursor", "docs", "backend", "frontend", "sql", "scripts", "README.md", ".editorconfig", ".gitattributes", ".gitignore", "LICENSE")
)

$ErrorActionPreference = "Stop"

$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$excludeArgs = @(
    "--hidden",
    "-g", "!/.git/**",
    "-g", "!/.oldbk/**",
    "-g", "!node_modules/**",
    "-g", "!dist/**",
    "-g", "!target/**",
    "-g", "!release/**",
    "-g", "!logs/**",
    "-g", "!/.tmp-browser/**"
)

$existingRoots = @()
foreach ($root in $Roots) {
    if (Test-Path -LiteralPath $root) {
        $existingRoots += $root
    }
}

if ($existingRoots.Count -eq 0) {
    Write-Output "No active project text roots were found."
    exit 0
}

$files = & rg --files @excludeArgs -- @existingRoots
$badEncoding = @()

foreach ($rel in $files) {
    $path = Join-Path (Get-Location) $rel
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($path)
    try {
        [void]$utf8Strict.GetString($bytes)
    }
    catch {
        $badEncoding += $rel
    }
}

if ($badEncoding.Count -gt 0) {
    Write-Output "Non UTF-8 files:"
    $badEncoding | ForEach-Object { Write-Output "  $_" }
    exit 1
}

$mojibakeCodepoints = @(
    0x6D93, 0x6434, 0x5BB8, 0x5BF0, 0x5A11, 0x935A, 0x95C2, 0x9418,
    0x9422, 0x6D60, 0x7EEF, 0x8364, 0x7CBA, 0x59AF, 0x2033, 0x6F61,
    0xFFFD
)

$markers = $mojibakeCodepoints | ForEach-Object { [regex]::Escape(([char]$_).ToString()) }
$pattern = $markers -join "|"
$matches = & rg -n @excludeArgs -- $pattern @existingRoots

if ($LASTEXITCODE -eq 0) {
    Write-Output "Mojibake markers found:"
    $matches | ForEach-Object { Write-Output $_ }
    exit 1
}

if ($LASTEXITCODE -ne 1) {
    exit $LASTEXITCODE
}

Write-Output "Encoding check passed: active project text is strict UTF-8 and no mojibake markers were found."
