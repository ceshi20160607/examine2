param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$FixturePath = "$PSScriptRoot/fixture.json",
    [string]$MembersPath = "$PSScriptRoot/task-02-members.json",
    [string]$ResultPath = "$PSScriptRoot/task-02-restart.json",
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine'
)

$ErrorActionPreference = 'Stop'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body
    )
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    $arguments = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $Session
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}

function Invoke-Query([object]$Filter, [object[]]$Sort = @()) {
    return Invoke-Envelope -Session $session -Path "$root/records:query" -Method POST -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId
        page = 1
        size = 50
        recordScope = 'active'
        q = $null
        filter = $Filter
        sort = @($Sort)
        columns = @()
        viewId = $null
    }
}

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed' }
    return @($output)
}

$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$members = Get-Content -LiteralPath $MembersPath -Raw | ConvertFrom-Json
$health = Invoke-RestMethod -Uri "$BaseUrl/management/health" -TimeoutSec 10
Assert-True ($health.status -eq 'UP') 'Packaged backend is not healthy after restart'

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$members.positive.username
    password = [string]$members.positive.password
}
$null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
    -Method POST -Body @{}

$root = "/api/v1/systems/$($fixture.systemId)/runtime/modules/work_order"
$recordPath = "$root/records/$($fixture.records.workOrder)"
$schema = Invoke-Envelope -Session $session -Path "$root/record-schema"
$detail = Invoke-Envelope -Session $session -Path $recordPath
$relation = Invoke-Envelope -Session $session -Path "$recordPath/relations/customer"
$subtable = Invoke-Envelope -Session $session -Path "$recordPath/subtables/order_lines"
$reference = @($detail.values | Where-Object fieldCode -eq 'customer_name_ref')[0]
Assert-True ($reference.value.recalculationState -eq 'READY' `
        -and [long]$reference.value.sourceVersion -gt 0 `
        -and -not [string]::IsNullOrWhiteSpace([string]$reference.value.result)) `
    'Restart did not preserve the ready materialized reference'
Assert-True ([long]$relation.total -eq 1 -and [long]$subtable.total -eq 2) `
    'Restart did not preserve relation/subtable API readback'
$relationField = @($schema.fields | Where-Object fieldCode -eq 'customer')[0]
$referenceField = @($schema.fields | Where-Object fieldCode -eq 'customer_name_ref')[0]
$subtableField = @($schema.fields | Where-Object fieldCode -eq 'order_lines')[0]
Assert-True (($relationField.operators -join ',') -eq 'HAS_ANY,EMPTY' `
        -and ($referenceField.operators -join ',') -eq 'EQ,CONTAINS,PREFIX,EMPTY' `
        -and ($subtableField.operators -join ',') -eq 'DECLARED_AGGREGATE') `
    'Restart schema lost a P4-C3 query capability'

$relationQuery = Invoke-Query @{
    kind='PREDICATE'; fieldCode='customer'; operator='HAS_ANY'; value=@([string]$fixture.records.customer)
}
$referenceQuery = Invoke-Query @{
    kind='PREDICATE'; fieldCode='customer_name_ref'; operator='EQ'; value=[string]$reference.value.result
} @(@{ fieldCode='customer_name_ref'; direction='ASC'; nulls='LAST' })
$aggregateQuery = Invoke-Query @{
    kind='PREDICATE'; fieldCode='order_lines'; operator='DECLARED_AGGREGATE'
    value=@{ aggregateId='total_amount'; operator='GT'; value=20 }
}
Assert-True ([long]$relationQuery.total -eq 1 -and [long]$referenceQuery.total -eq 1 `
        -and [long]$aggregateQuery.total -eq 1) 'Restart query readback failed'

$databaseRows = @(Invoke-DatabaseRows @"
SELECT CONCAT(
  (SELECT COUNT(*) FROM un_module_record_relation WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND source_record_id=$($fixture.records.workOrder)), ':',
  (SELECT COUNT(*) FROM un_module_sub_record WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND parent_record_id=$($fixture.records.workOrder) AND status='ACTIVE'), ':',
  (SELECT COUNT(*) FROM un_module_sub_value WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND parent_record_id=$($fixture.records.workOrder)), ':',
  (SELECT COUNT(*) FROM un_module_record_index WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND record_id=$($fixture.records.workOrder) AND logical_field_id=$($fixture.fields.customerReference)), ':',
  (SELECT recalculation_state FROM un_module_reference_state WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND record_id=$($fixture.records.workOrder) AND field_snapshot_id=$($fixture.fields.customerReference)), ':',
  (SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1)
);
"@)
$database = @([string]$databaseRows[0] -split ':')
Assert-True (($database -join ':') -eq '1:2:6:1:READY:4.9.0') `
    "Restart database projection mismatch: $($database -join ':')"

$listener = netstat -ano | Select-String 'LISTENING\s+[0-9]+$' | Where-Object { $_ -match ':18080\s' } | Select-Object -First 1
Assert-True ($null -ne $listener) 'Backend listener disappeared during restart verification'
$backendPid = [long](($listener.ToString().Trim() -split '\s+')[-1])
$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    backendPid = $backendPid
    health = [string]$health.status
    workOrderVersion = [long]$detail.version
    referenceState = [string]$reference.value.recalculationState
    referenceSourceVersion = [long]$reference.value.sourceVersion
    relationRows = [long]$database[0]
    subtableRows = [long]$database[1]
    subtableValueRows = [long]$database[2]
    referenceIndexRows = [long]$database[3]
    queryTotals = @{ relation=[long]$relationQuery.total; reference=[long]$referenceQuery.total; aggregate=[long]$aggregateQuery.total }
    flywayVersion = [string]$database[5]
}
[IO.File]::WriteAllText($ResultPath, ($result | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Depth 30
