param(
    [string]$FixturePath = "$PSScriptRoot/task-02-restart-fixture.json",
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine'
)

$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw -Encoding UTF8 | ConvertFrom-Json
$query = @"
UPDATE un_module_record_value
SET recalculation_state='READY',failure_correlation_id=NULL,version=version+1,updated_at=NOW(6)
WHERE system_id=$($fixture.systemId) AND record_id=$($fixture.records.order)
  AND logical_field_id=$($fixture.fields.aggregate) AND field_type='AGGREGATE'
  AND recalculation_state IN ('FAILED','PENDING');
SELECT ROW_COUNT();
"@
$rows = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
    --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
    --batch --raw --skip-column-names -e $query
if ($LASTEXITCODE -ne 0) { throw 'Failed-state cleanup failed' }
$lastRow = [string](@($rows)[-1])
[ordered]@{ verdict = 'restored'; affectedCells = [int]$lastRow } | ConvertTo-Json
