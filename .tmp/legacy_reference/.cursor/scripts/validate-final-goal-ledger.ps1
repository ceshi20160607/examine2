param(
    [string]$Root,
    [string]$Baseline = '.cursor/session/rebuild/requirement-understanding.md',
    [string]$Ledger = '.cursor/session/final-goal-ledger.md'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Root)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
    $Root = Split-Path -Parent (Split-Path -Parent $scriptDirectory)
}
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$baselinePath = Join-Path $rootPath $Baseline
$ledgerPath = Join-Path $rootPath $Ledger
$baselineText = Get-Content -LiteralPath $baselinePath -Raw -Encoding UTF8
$ledgerText = Get-Content -LiteralPath $ledgerPath -Raw -Encoding UTF8

function Get-Ids([string]$Text, [string]$Prefix) {
    return @([regex]::Matches($Text, "(?m)^\| ($([regex]::Escape($Prefix))[A-Z0-9-]+) \|") |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
}

foreach ($prefix in @('REQ-', 'JRN-', 'NFR-')) {
    $expected = Get-Ids $baselineText $prefix
    $actual = Get-Ids $ledgerText $prefix
    if (($expected -join '|') -ne ($actual -join '|')) {
        $missing = @($expected | Where-Object { $actual -notcontains $_ })
        $extra = @($actual | Where-Object { $expected -notcontains $_ })
        throw "$prefix ledger mismatch. Missing=$($missing -join ',') Extra=$($extra -join ',')"
    }
}
if ($ledgerText -notmatch 'engineering_final_gate:\s*`OPEN`' -or
    $ledgerText -notmatch 'user_accepted:\s*`false`') {
    throw 'Final gate or user acceptance boundary is invalid.'
}

[pscustomobject]@{
    status = 'PASS'
    requirements = (Get-Ids $ledgerText 'REQ-').Count
    journeys = (Get-Ids $ledgerText 'JRN-').Count
    nonFunctional = (Get-Ids $ledgerText 'NFR-').Count
    engineeringFinalGate = 'OPEN'
    userAccepted = $false
} | ConvertTo-Json
