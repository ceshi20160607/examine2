param(
    [string]$RequirementPath = 'docs/user_requirement.md',
    [string]$LedgerPath = 'docs/framework/final-requirement-coverage-ledger.md',
    [string]$OutputPath = 'docs/evidence/final-requirement-coverage-audit-result.json',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RequirementFile = Join-Path $RepoRoot $RequirementPath
$LedgerFile = Join-Path $RepoRoot $LedgerPath
$ResultFile = Join-Path $RepoRoot $OutputPath
$ResultDir = Split-Path -Parent $ResultFile

if (-not (Test-Path -LiteralPath $RequirementFile)) {
    throw "Requirement file does not exist: $RequirementFile"
}
if (-not (Test-Path -LiteralPath $LedgerFile)) {
    throw "Coverage ledger does not exist: $LedgerFile"
}
if (-not (Test-Path -LiteralPath $ResultDir)) {
    New-Item -ItemType Directory -Force -Path $ResultDir | Out-Null
}

$requirementText = Get-Content -Raw -Encoding UTF8 -LiteralPath $RequirementFile
$ledgerText = Get-Content -Raw -Encoding UTF8 -LiteralPath $LedgerFile

$requiredIds = New-Object System.Collections.Generic.List[string]

# Product structure and full function list.
foreach ($match in [regex]::Matches($requirementText, '(?m)^##\s+(4\.[1-6]|5\.(?:[1-9]|1[0-9]|20)(?:\.1)?)\b')) {
    $requiredIds.Add("REQ-$($match.Groups[1].Value)")
}

# UI interaction sections.
foreach ($match in [regex]::Matches($requirementText, '(?m)^##\s+(6\.(?:[1-9]|1[0-1]))\b')) {
    $requiredIds.Add("REQ-$($match.Groups[1].Value)")
}

# Broad technical/delivery sections.
foreach ($id in @('7', '8', '9', '10')) {
    if ($requirementText -match "(?m)^##\s+$id\b") {
        $requiredIds.Add("REQ-$id")
    }
}

# Launch capability rules are accepted as one grouped gate for now, but must exist.
if ($requirementText -match '(?m)^##\s+14\.1\b') {
    $requiredIds.Add('REQ-14.1-14.37')
}

# Appendix A clarifications are accepted as one grouped gate for now, but must exist.
if ($requirementText -match '(?m)^##\s+附录 A') {
    $requiredIds.Add('REQ-A')
}

$uniqueRequiredIds = @($requiredIds.ToArray() | Sort-Object -Unique)
$rows = New-Object System.Collections.Generic.List[object]
foreach ($line in ($ledgerText -split "`r?`n")) {
    if ($line -match '^\|\s*(REQ-[^|]+)\s*\|[^|]*\|[^|]*\|\s*(PROVEN|PARTIAL|OPEN|USER_EXCLUDED)\s*\|') {
        $rows.Add([ordered]@{
            id = $matches[1].Trim()
            status = $matches[2].Trim()
            line = $line
        })
    }
}

$rowArray = @($rows.ToArray())
$coveredIds = @($rowArray | ForEach-Object { $_.id })
$missing = @($uniqueRequiredIds | Where-Object { $coveredIds -notcontains $_ })
$invalidStatus = @($rowArray | Where-Object { $_.status -notin @('PROVEN', 'PARTIAL', 'OPEN', 'USER_EXCLUDED') })
$notClosed = @($rowArray | Where-Object { $_.status -in @('PARTIAL', 'OPEN') })
$closed = @($rowArray | Where-Object { $_.status -in @('PROVEN', 'USER_EXCLUDED') })

$status = if ($missing.Count -eq 0 -and $invalidStatus.Count -eq 0 -and $notClosed.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    generatedAt = (Get-Date).ToString('o')
    requirementPath = $RequirementPath
    ledgerPath = $LedgerPath
    requiredCount = $uniqueRequiredIds.Count
    ledgerRowCount = $rowArray.Count
    closedCount = $closed.Count
    notClosedCount = $notClosed.Count
    missingCount = $missing.Count
    missing = @($missing)
    notClosed = @($notClosed | ForEach-Object {
        [ordered]@{
            id = $_.id
            status = $_.status
        }
    })
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $ResultFile -Value $json -Encoding UTF8
$json

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
