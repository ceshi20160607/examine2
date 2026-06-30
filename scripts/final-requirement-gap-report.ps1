param(
    [string]$LedgerPath = 'docs/framework/final-requirement-coverage-ledger.md',
    [string]$OutputPath = 'docs/evidence/final-requirement-gap-report.md'
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$LedgerFile = Join-Path $RepoRoot $LedgerPath
$OutputFile = Join-Path $RepoRoot $OutputPath
$OutputDir = Split-Path -Parent $OutputFile

if (-not (Test-Path -LiteralPath $LedgerFile)) {
    throw "Coverage ledger does not exist: $LedgerFile"
}
if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

$ledgerText = Get-Content -Raw -Encoding UTF8 -LiteralPath $LedgerFile
$rows = New-Object System.Collections.Generic.List[object]
foreach ($line in ($ledgerText -split "`r?`n")) {
    if ($line -match '^\|\s*(REQ-[^|]+)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*(PROVEN|PARTIAL|OPEN|USER_EXCLUDED)\s*\|\s*([^|]*)\|\s*([^|]*)\|') {
        $rows.Add([ordered]@{
            id = $matches[1].Trim()
            source = $matches[2].Trim()
            area = $matches[3].Trim()
            status = $matches[4].Trim()
            evidence = $matches[5].Trim()
            gap = $matches[6].Trim()
        })
    }
}

$rowArray = @($rows.ToArray())
$openRows = @($rowArray | Where-Object { $_.status -eq 'OPEN' })
$partialRows = @($rowArray | Where-Object { $_.status -eq 'PARTIAL' })
$closedRows = @($rowArray | Where-Object { $_.status -in @('PROVEN', 'USER_EXCLUDED') })

$batchMap = [ordered]@{
    'FRC-1 Missing Product Surfaces' = @('REQ-5.8', 'REQ-6.1', 'REQ-6.3', 'REQ-6.8', 'REQ-9')
    'FRC-2 No-Code Configuration Depth' = @('REQ-4.3', 'REQ-5.3', 'REQ-5.3.1', 'REQ-5.4', 'REQ-5.5', 'REQ-5.6', 'REQ-5.7', 'REQ-5.9', 'REQ-5.10', 'REQ-6.7', 'REQ-6.10')
    'FRC-3 Runtime User Depth' = @('REQ-4.4', 'REQ-5.11', 'REQ-5.13', 'REQ-5.14', 'REQ-5.16', 'REQ-5.20', 'REQ-6.4', 'REQ-6.5', 'REQ-6.6', 'REQ-6.11')
    'FRC-4 Workflow, Integration, AI Depth' = @('REQ-4.5', 'REQ-5.12', 'REQ-5.15', 'REQ-5.19')
    'FRC-5 Operations, Robustness, Delivery' = @('REQ-4.6', 'REQ-7', 'REQ-8', 'REQ-10', 'REQ-14.1-14.37', 'REQ-A')
    'FRC-6 Human Acceptance Pass' = @('REQ-2.1', 'REQ-4.1', 'REQ-4.2', 'REQ-6.2')
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# Final Requirement Gap Report')
$lines.Add('')
$lines.Add("Time: $((Get-Date).ToString('o'))")
$lines.Add('')
$lines.Add('## Summary')
$lines.Add('')
$lines.Add("| Metric | Count |")
$lines.Add("|---|---:|")
$lines.Add("| Total rows | $($rowArray.Count) |")
$lines.Add("| OPEN | $($openRows.Count) |")
$lines.Add("| PARTIAL | $($partialRows.Count) |")
$lines.Add("| Closed | $($closedRows.Count) |")
$lines.Add('')
$lines.Add('## Recommended Batch Order')
$lines.Add('')
foreach ($batchName in $batchMap.Keys) {
    $ids = $batchMap[$batchName]
    $batchRows = @($rowArray | Where-Object { $ids -contains $_.id -and $_.status -in @('OPEN', 'PARTIAL') })
    if ($batchRows.Count -eq 0) {
        continue
    }
    $lines.Add("### $batchName")
    $lines.Add('')
    $lines.Add('| Req ID | Status | Area | Gap / Next Action |')
    $lines.Add('|---|---|---|---|')
    foreach ($row in $batchRows) {
        $lines.Add("| $($row.id) | $($row.status) | $($row.area) | $($row.gap) |")
    }
    $lines.Add('')
}

Set-Content -LiteralPath $OutputFile -Value $lines -Encoding UTF8
Get-Content -Raw -Encoding UTF8 -LiteralPath $OutputFile
