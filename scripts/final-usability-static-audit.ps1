param(
    [string]$FrontendSrc = 'frontend/src',
    [string]$OutputPath = 'docs/evidence/final-usability-static-audit-result.json',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SourceRoot = Join-Path $RepoRoot $FrontendSrc
$ResultFile = Join-Path $RepoRoot $OutputPath
$ResultDir = Split-Path -Parent $ResultFile

if (-not (Test-Path -LiteralPath $SourceRoot)) {
    throw "Frontend source path does not exist: $SourceRoot"
}
if (-not (Test-Path -LiteralPath $ResultDir)) {
    New-Item -ItemType Directory -Force -Path $ResultDir | Out-Null
}

$patterns = @(
    @{ id = 'mojibake-replacement'; severity = 'blocker'; regex = '\uFFFD'; reason = 'Visible replacement character indicates encoding or copy corruption.' },
    @{ id = 'mojibake-latin1'; severity = 'blocker'; regex = '[\u00E6\u00C3]'; reason = 'Likely mojibake in production source.' },
    @{ id = 'generic-success-toast'; severity = 'blocker'; regex = '\u64CD\u4F5C\u5DF2\u54CD\u5E94|\u6210\u529F\u54CD\u5E94|\u5DF2\u89E6\u53D1'; reason = 'Generic success wording can hide unfinished behavior.' },
    @{ id = 'browser-blocking-dialog'; severity = 'blocker'; regex = '\b(window\.)?(alert|prompt|confirm)\s*\('; reason = 'Production UI must use task-specific panels, forms, or result states, not blocking browser dialogs.' },
    @{ id = 'default-generic-drawer'; severity = 'blocker'; regex = 'defaultDrawer|genericDrawer|generic detail|\u901A\u7528\u8BE6\u60C5|\u9ED8\u8BA4\u8BE6\u60C5'; reason = 'P0 routes must not use generic/default details as functional closure.' },
    @{ id = 'unfinished-visible-state'; severity = 'blocker'; regex = '\u5F85\u63A5\u5165|\u672A\u5B9E\u73B0|\u5360\u4F4D|\u656C\u8BF7\u671F\u5F85|coming soon'; reason = 'Required production routes must not expose unfinished placeholders.' },
    @{ id = 'demo-mock-fixture-text'; severity = 'warning'; regex = '\u6F14\u793A|demo|mock|fixture|sample'; reason = 'Production source should not expose stale demo/mock/fixture behavior outside explicit test files.' }
)

$allowedWarningPaths = @(
    'api/types.ts'
)

$files = Get-ChildItem -LiteralPath $SourceRoot -Recurse -File |
    Where-Object { $_.Extension -in @('.ts', '.tsx', '.js', '.jsx', '.css', '.html') -and $_.FullName -notmatch '\\node_modules\\' }

$findings = New-Object System.Collections.Generic.List[object]
foreach ($file in $files) {
    $relative = $file.FullName
    if ($relative.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $relative = $relative.Substring($RepoRoot.Length).TrimStart('\', '/')
    }
    $relative = $relative.Replace('\', '/')
    $lines = Get-Content -LiteralPath $file.FullName -Encoding UTF8
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        foreach ($pattern in $patterns) {
            if ($line -match $pattern.regex) {
                $severity = $pattern.severity
                if ($severity -eq 'warning' -and $allowedWarningPaths -contains $relative.Substring('frontend/src/'.Length)) {
                    continue
                }
                $findings.Add([ordered]@{
                    id = $pattern.id
                    severity = $severity
                    file = $relative
                    line = $i + 1
                    reason = $pattern.reason
                    text = $line.Trim()
                })
            }
        }
    }
}

$findingArray = @($findings.ToArray())
$blockers = @($findingArray | Where-Object { $_.severity -eq 'blocker' })
$warnings = @($findingArray | Where-Object { $_.severity -eq 'warning' })
$status = if ($blockers.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    generatedAt = (Get-Date).ToString('o')
    source = $FrontendSrc
    scannedFiles = @($files).Count
    blockerCount = $blockers.Count
    warningCount = $warnings.Count
    findings = $findingArray
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $ResultFile -Value $json -Encoding UTF8
$json

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
