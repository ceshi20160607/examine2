param(
    [string]$FixturePath = "$PSScriptRoot/fixture.json",
    [string]$OutputPath = "$PSScriptRoot/task-01-runtime.json",
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [string]$DatabasePassword = 'examine'
)

$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$baseUrl = [string]$fixture.baseUrl
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-Envelope {
    param(
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body,
        [switch]$Idempotent
    )

    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($session.Cookies.GetCookies($baseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    if ($Idempotent) { $headers['Idempotency-Key'] = [guid]::NewGuid().ToString() }

    $arguments = @{
        Uri = "$baseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    try {
        $response = Invoke-RestMethod @arguments
    } catch {
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        throw "API $Method $Path failed ($status): $($_.ErrorDetails.Message)"
    }
    if ($response.code -ne 'OK') {
        throw "API $Method $Path returned $($response.code): $($response.message)"
    }
    return $response.data
}

function Invoke-ExpectedFieldError {
    param([string]$Path, [object]$Body)

    $request = [System.Net.HttpWebRequest]::Create("$baseUrl$Path")
    $request.Method = 'POST'
    $request.ContentType = 'application/json; charset=utf-8'
    $request.CookieContainer = $session.Cookies
    $request.Headers.Add('X-Request-ID', [guid]::NewGuid().ToString())
    $request.Headers.Add('Idempotency-Key', [guid]::NewGuid().ToString())
    $csrf = ($session.Cookies.GetCookies($baseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $request.Headers.Add('X-CSRF-Token', [uri]::UnescapeDataString($csrf)) }
    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $request.ContentLength = $bytes.Length
    $stream = $request.GetRequestStream()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
    try {
        $response = $request.GetResponse()
        $response.Close()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        $status = if ($response) { [int]$response.StatusCode } else { 0 }
        $reader = if ($response) { New-Object System.IO.StreamReader($response.GetResponseStream()) } else { $null }
        $raw = if ($reader) { $reader.ReadToEnd() } else { '' }
        if ($reader) { $reader.Close() }
        if ($response) { $response.Close() }
        $payload = try { $raw | ConvertFrom-Json } catch { $null }
        if ($status -ne 422 -or $null -eq $payload -or $payload.code -ne 'FIELD_VALUE_INVALID') {
            throw "Expected 422/FIELD_VALUE_INVALID but received status=$status body=$raw"
        }
        return [pscustomobject]@{
            status = $status
            code = [string]$payload.code
            path = [string]$payload.errors[0].path
        }
    }
    throw 'Invalid field request unexpectedly succeeded'
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-DatabaseScalar([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0 -or $output.Count -ne 1) {
        throw "Database scalar query failed: $Query"
    }
    return [long](@($output)[0])
}

$null = Invoke-Envelope -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$fixture.username
    password = [string]$fixture.password
}
$null = Invoke-Envelope -Path "/api/v1/context/systems/$($fixture.systemId):switch" -Method POST -Body @{}

$runtimeRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$schema = Invoke-Envelope -Path "$runtimeRoot/record-schema"
$expectedTypes = @(
    'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT',
    'CASCADE', 'SWITCH', 'RATING', 'PROGRESS', 'TAG'
)
$actualTypes = @($schema.fields | ForEach-Object { [string]$_.type })
Assert-True (($actualTypes -join ',') -eq ($expectedTypes -join ',')) `
    "Unexpected P4-C1 schema types: $($actualTypes -join ',')"

$values = @{
    completion_rate = 12.3456
    budget = @{ amount = '12.30'; currency = 'cny' }
    service_dates = @('2026-07-21', '2026-07-22')
    start_time = '09:05:07'
    service_hours = @('09:00:00', '18:00:00')
    service_labels = @([string]$fixture.optionIds.normal, [string]$fixture.optionIds.urgent)
    service_area = @([string]$fixture.optionIds.cascadeRoot, [string]$fixture.optionIds.cascadeChild)
    enabled = $true
    rating = 5
    progress = 99.25
    tags = @(([string][char]0xFF21), 'Beta')
}
$recordsPath = "$runtimeRoot/records"
$created = Invoke-Envelope -Path $recordsPath -Method POST -Idempotent -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    title = 'P4-C1 task-01 acceptance'
    values = $values
}
$activated = Invoke-Envelope -Path "$recordsPath/$($created.recordId):activate" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$created.version
}

$valueMap = @{}
foreach ($fieldValue in $activated.values) { $valueMap[[string]$fieldValue.fieldCode] = $fieldValue.value }
Assert-True ($activated.status -eq 'ACTIVE' -and [long]$activated.version -eq 1) 'Record activation failed'
Assert-True ([decimal]$valueMap.completion_rate -eq [decimal]12.3456) 'PERCENT canonical value mismatch'
Assert-True ($valueMap.budget.amount -eq '12.30' -and $valueMap.budget.currency -eq 'CNY') `
    'MONEY canonical value mismatch'
Assert-True (($valueMap.service_dates -join ',') -eq '2026-07-21,2026-07-22') 'DATE_RANGE mismatch'
Assert-True ($valueMap.start_time -eq '09:05:07') 'TIME mismatch'
Assert-True (($valueMap.service_hours -join ',') -eq '09:00:00,18:00:00') 'TIME_RANGE mismatch'
Assert-True (($valueMap.service_labels -join ',') -eq "$($fixture.optionIds.normal),$($fixture.optionIds.urgent)") `
    'MULTI_SELECT mismatch'
Assert-True (($valueMap.service_area -join ',') -eq "$($fixture.optionIds.cascadeRoot),$($fixture.optionIds.cascadeChild)") `
    'CASCADE mismatch'
Assert-True ([bool]$valueMap.enabled) 'SWITCH mismatch'
Assert-True ([int]$valueMap.rating -eq 5) 'RATING mismatch'
Assert-True ([decimal]$valueMap.progress -eq [decimal]99.25) 'PROGRESS mismatch'
Assert-True (($valueMap.tags -join ',') -eq 'A,Beta') 'TAG normalization mismatch'

$recordCountBefore = Invoke-DatabaseScalar `
    "SELECT COUNT(*) FROM un_module_record WHERE system_id=$($fixture.systemId)"
$invalidValues = @{} + $values
$invalidValues.completion_rate = 100.00001
$invalid = Invoke-ExpectedFieldError -Path $recordsPath -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    title = 'must fail'
    values = $invalidValues
}
$recordCountAfter = Invoke-DatabaseScalar `
    "SELECT COUNT(*) FROM un_module_record WHERE system_id=$($fixture.systemId)"
Assert-True ($recordCountBefore -eq $recordCountAfter) 'Invalid request left a partial record'

$detail = Invoke-Envelope -Path "$recordsPath/$($created.recordId)"
Assert-True ($detail.status -eq 'ACTIVE' -and $detail.values.Count -eq 11) 'Persisted detail mismatch'
$valueRowCount = Invoke-DatabaseScalar `
    "SELECT COUNT(*) FROM un_module_record_value WHERE record_id=$($created.recordId)"
$indexRowCount = Invoke-DatabaseScalar `
    "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=$($created.recordId)"
Assert-True ($valueRowCount -eq 16 -and $indexRowCount -eq 16) 'Typed value/index row count mismatch'

$result = [ordered]@{
    verifiedAt = (Get-Date).ToString('o')
    systemId = [string]$fixture.systemId
    schemaVersionId = [string]$fixture.schemaVersionId
    recordId = [string]$created.recordId
    status = [string]$detail.status
    version = [long]$detail.version
    canonicalTypes = $expectedTypes
    persistedFieldCount = [int]$detail.values.Count
    persistedValueRowCount = $valueRowCount
    persistedIndexRowCount = $indexRowCount
    invalidVector = $invalid
    recordCountBeforeInvalid = $recordCountBefore
    recordCountAfterInvalid = $recordCountAfter
    result = 'PASS'
}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
$result | ConvertTo-Json -Depth 10
