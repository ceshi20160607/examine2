param(
    [string]$OutputPath = "docs/evidence/recovery/r0-static-audit-current.json"
)

$ErrorActionPreference = "Stop"

function Find-Matches {
    param(
        [string[]]$Paths,
        [string]$Pattern
    )

    $results = @()
    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            continue
        }
        $files = Get-ChildItem -LiteralPath $path -Recurse -File -Include *.ts,*.tsx,*.js,*.java,*.css,*.html
        foreach ($file in $files) {
            if ($file.FullName -match "\\(target|dist|node_modules)\\") {
                continue
            }
            $matches = Select-String -LiteralPath $file.FullName -Pattern $Pattern -AllMatches
            foreach ($match in $matches) {
                $results += [ordered]@{
                    path = $file.FullName.Replace((Get-Location).Path + [System.IO.Path]::DirectorySeparatorChar, "")
                    line = $match.LineNumber
                    text = $match.Line.Trim()
                }
            }
        }
    }
    return $results
}

function Find-SidebarTargets {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return @()
    }
    $targets = @{}
    $matches = Select-String -LiteralPath $Path -Pattern "createSidebarButton\('([^']+)', '([^']+)'" -AllMatches
    foreach ($match in $matches) {
        $label = $match.Matches[0].Groups[1].Value
        $target = $match.Matches[0].Groups[2].Value
        if (-not $targets.ContainsKey($target)) {
            $targets[$target] = @()
        }
        $targets[$target] += $label
    }
    $duplicates = @()
    foreach ($key in $targets.Keys) {
        if ($targets[$key].Count -gt 1) {
            $duplicates += [ordered]@{
                target = $key
                labels = $targets[$key]
                path = $Path
            }
        }
    }
    return $duplicates
}

function Find-InertPagination {
    $paths = @(
        "frontend/src/features/platform/platformShell.ts",
        "frontend/src/features/platform-admin/platformAdmin.ts",
        "frontend/src/features/system-admin/systemAdmin.ts",
        "frontend/src/features/system-shell/systemShell.ts",
        "frontend/src/shared/table.ts"
    )
    $results = @()
    foreach ($path in $paths) {
        if (-not (Test-Path $path)) {
            continue
        }
        $content = Get-Content -Raw -Encoding UTF8 $path
        $hasPagination = $content.Contains("createPagination")
        $hasPreviousNext = $content.Contains("pageNo <= 1") -and $content.Contains("hasNext")
        $hasPageCallback = $content -match "onPage|nextPage|createPageButton|addEventListener\('click'.*page"
        if ($hasPagination -and $hasPreviousNext -and -not $hasPageCallback) {
            $results += [ordered]@{
                path = $path
                reason = "Renders previous/next pagination controls without detectable page-change callback."
            }
        }
    }
    return $results
}

$promptMatches = Find-Matches -Paths @("frontend/src") -Pattern "window\.prompt|window\.confirm|alert\("
$mockMatches = Find-Matches -Paths @("frontend/src", "backend") -Pattern "\bmock\b|\bstub\b|sample"
$sidebarDuplicates = @()
$sidebarDuplicates += Find-SidebarTargets -Path "frontend/src/features/system-admin/systemAdmin.ts"
$sidebarDuplicates += Find-SidebarTargets -Path "frontend/src/features/platform-admin/platformAdmin.ts"
$inertPagination = Find-InertPagination
$releaseExists = Test-Path "release"

$result = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    status = if ($promptMatches.Count -eq 0 -and $sidebarDuplicates.Count -eq 0 -and $inertPagination.Count -eq 0 -and $releaseExists) { "PASS" } else { "FAIL" }
    checks = [ordered]@{
        promptOrConfirmCount = $promptMatches.Count
        promptOrConfirm = $promptMatches
        duplicateSidebarTargetCount = $sidebarDuplicates.Count
        duplicateSidebarTargets = $sidebarDuplicates
        inertPaginationCount = $inertPagination.Count
        inertPagination = $inertPagination
        mockSampleStubCount = $mockMatches.Count
        mockSampleStub = $mockMatches
        releaseDirectoryExists = $releaseExists
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8

Write-Output $json
if ($result.status -ne "PASS") {
    exit 1
}
