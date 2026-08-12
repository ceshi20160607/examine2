param(
    [Parameter(Mandatory = $true)][long]$ExpectedVersion,
    [string]$FixturePath,
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($FixturePath)) {
    $FixturePath = Join-Path $PSScriptRoot 'task-02-restart-fixture.json'
}
$fixture = Get-Content -LiteralPath $FixturePath -Raw -Encoding UTF8 | ConvertFrom-Json
$query = @"
UPDATE un_module_record
SET version=version+1,updated_at=NOW(6)
WHERE system_id=$($fixture.systemId) AND record_id=$($fixture.records.order)
  AND version=$ExpectedVersion;
SELECT CONCAT(ROW_COUNT(),':',version)
FROM un_module_record
WHERE system_id=$($fixture.systemId) AND record_id=$($fixture.records.order);
"@
$rows = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
    --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
    --batch --raw --skip-column-names -e $query
if ($LASTEXITCODE -ne 0) { throw 'Stale-version preparation failed' }
$lastRow = [string](@($rows)[-1])
$parts = @($lastRow -split ':')
if ($parts.Count -ne 2 -or [int]$parts[0] -ne 1 -or [long]$parts[1] -ne ($ExpectedVersion + 1)) {
    throw "Target record was not advanced exactly once: $(@($rows) -join ';')"
}
[ordered]@{
    verdict = 'prepared'
    systemId = [string]$fixture.systemId
    recordId = [string]$fixture.records.order
    staleVersion = $ExpectedVersion
    currentVersion = [long]$parts[1]
} | ConvertTo-Json
