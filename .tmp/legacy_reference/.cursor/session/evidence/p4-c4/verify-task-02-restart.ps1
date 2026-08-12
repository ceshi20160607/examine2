param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$FixturePath = "$PSScriptRoot/task-02-restart-fixture.json",
    [string]$ResultPath = "$PSScriptRoot/task-02-restart.json",
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine'
)

$ErrorActionPreference = 'Stop'
function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Invoke-Envelope {
    param([Microsoft.PowerShell.Commands.WebRequestSession]$Session, [string]$Path,
          [string]$Method = 'GET', [object]$Body)
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET','HEAD','OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    $arguments = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; WebSession = $Session; TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 50 -Compress
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}
function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed' }
    return @($output)
}
function Derived-Value([object]$Detail, [string]$Code) {
    return @($Detail.values | Where-Object fieldCode -eq $Code)[0].value
}

$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$health = Invoke-RestMethod -Uri "$BaseUrl/management/health" -TimeoutSec 10
Assert-True ($health.status -eq 'UP') 'Packaged backend is not healthy after restart'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$fixture.username; password = [string]$fixture.password
}
$null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
    -Method POST -Body @{}

$configRoot = "/api/v1/systems/$($fixture.systemId)/admin/config"
$rootState = Invoke-Envelope -Session $session -Path $configRoot
Assert-True ([string]$rootState.activeVersionId -eq [string]$fixture.schemaVersionId) `
    'Restart changed the active derived schema version'
$modules = @(Invoke-Envelope -Session $session -Path "$configRoot/modules")
$orderModule = @($modules | Where-Object code -eq $fixture.moduleCode)[0]
$fields = @(Invoke-Envelope -Session $session -Path "$configRoot/modules/$($orderModule.id)/fields")
$derivedTypes = @($fields | Where-Object code -in @(
    'derived_taxed','derived_total','derived_customer_count','derived_customer_name','derived_line_total') |
    ForEach-Object type)
Assert-True (($derivedTypes -join ',') -eq 'FORMULA,CALCULATED,SUMMARY,LOOKUP,AGGREGATE') `
    'Restart lost one or more derived declarations'

$root = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$detail = Invoke-Envelope -Session $session -Path "$root/records/$($fixture.records.order)"
$derived = @($detail.values | Where-Object fieldCode -in @(
    'derived_taxed','derived_total','derived_customer_count','derived_customer_name','derived_line_total'))
Assert-True ($derived.Count -eq 5 -and @($derived | Where-Object {
        $_.value.recalculationState -ne 'READY'
    }).Count -eq 0) 'Restart did not preserve five READY derived values'
Assert-True ([decimal](Derived-Value $detail 'derived_taxed').result -eq [decimal]$fixture.expected.taxed) `
    'Restart FORMULA mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_total').result -eq [decimal]$fixture.expected.total) `
    'Restart CALCULATED mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_customer_count').result -eq [decimal]$fixture.expected.summary) `
    'Restart SUMMARY mismatch'
Assert-True ([string](Derived-Value $detail 'derived_customer_name').result[0] -eq [string]$fixture.expected.lookup) `
    'Restart LOOKUP mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_line_total').result -eq [decimal]$fixture.expected.aggregate) `
    'Restart AGGREGATE mismatch'

$query = Invoke-Envelope -Session $session -Path "$root/records:query" -Method POST -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 10; recordScope = 'active'; q = $null
    filter = @{ kind = 'AND'; children = @(
        @{ kind = 'PREDICATE'; fieldCode = 'derived_taxed'; operator = 'EQ'; value = 41.5 },
        @{ kind = 'PREDICATE'; fieldCode = 'derived_customer_name'; operator = 'EQ'; value = [string]$fixture.expected.lookup }
    ) }
    sort = @(@{ fieldCode = 'derived_total'; direction = 'DESC'; nulls = 'LAST' })
    columns = @('derived_taxed','derived_total','derived_customer_count','derived_customer_name','derived_line_total')
    viewId = $null
}
Assert-True ([long]$query.total -eq 1 -and [string]$query.rows[0].recordId -eq [string]$fixture.records.order) `
    'Restart typed derived query mismatch'

$databaseRows = @(Invoke-DatabaseRows @"
SELECT CONCAT(
  (SELECT COUNT(*) FROM un_module_runtime_schema_field WHERE system_id=$($fixture.systemId)
    AND schema_version_id=$($fixture.schemaVersionId) AND field_type IN ('FORMULA','SUMMARY','CALCULATED','LOOKUP','AGGREGATE')), ':',
  (SELECT COUNT(*) FROM un_module_record_value WHERE system_id=$($fixture.systemId)
    AND record_id=$($fixture.records.order) AND field_type IN ('FORMULA','SUMMARY','CALCULATED','LOOKUP','AGGREGATE')
    AND recalculation_state='READY'), ':',
  (SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1)
);
"@)
$database = @([string]$databaseRows[0] -split ':')
Assert-True (($database -join ':') -eq '5:5:4.10.0') `
    "Restart database projection mismatch: $($database -join ':')"
$listener = netstat -ano | Select-String 'LISTENING\s+[0-9]+$' |
    Where-Object { $_ -match ':18080\s' } | Select-Object -First 1
Assert-True ($null -ne $listener) 'Backend listener disappeared during restart verification'
$backendPid = [long](($listener.ToString().Trim() -split '\s+')[-1])
$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o'); verdict = 'pass'; backendPid = $backendPid
    health = [string]$health.status; schemaVersionId = [string]$fixture.schemaVersionId
    recordId = [string]$fixture.records.order; recordVersion = [long]$detail.version
    derivedDefinitions = [long]$database[0]; readyDerivedValues = [long]$database[1]
    queryTotal = [long]$query.total; flywayVersion = [string]$database[2]
    values = [ordered]@{
        formula = [string](Derived-Value $detail 'derived_taxed').result
        calculated = [string](Derived-Value $detail 'derived_total').result
        summary = [string](Derived-Value $detail 'derived_customer_count').result
        lookup = [string](Derived-Value $detail 'derived_customer_name').result[0]
        aggregate = [string](Derived-Value $detail 'derived_line_total').result
    }
}
[IO.File]::WriteAllText($ResultPath, ($result | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Depth 50
