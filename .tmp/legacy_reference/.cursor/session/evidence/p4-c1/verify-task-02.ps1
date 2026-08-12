param(
    [string]$FixturePath = "$PSScriptRoot/task-02-fixture.json",
    [string]$OutputPath = "$PSScriptRoot/task-02-runtime.json",
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
$operatorChecks = 0
$sortChecks = 0

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
        Uri = "$baseUrl$Path"; Method = $Method; Headers = $headers; WebSession = $session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 30 -Compress
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

function Invoke-ExpectedError {
    param(
        [string]$Path,
        [string]$Method = 'POST',
        [object]$Body,
        [int]$ExpectedStatus,
        [string]$ExpectedCode
    )
    $request = [System.Net.HttpWebRequest]::Create("$baseUrl$Path")
    $request.Method = $Method
    $request.ContentType = 'application/json; charset=utf-8'
    $request.CookieContainer = $session.Cookies
    $request.Headers.Add('X-Request-ID', [guid]::NewGuid().ToString())
    $request.Headers.Add('Idempotency-Key', [guid]::NewGuid().ToString())
    $csrf = ($session.Cookies.GetCookies($baseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $request.Headers.Add('X-CSRF-Token', [uri]::UnescapeDataString($csrf)) }
    $json = $Body | ConvertTo-Json -Depth 30 -Compress
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
        if ($status -ne $ExpectedStatus -or $null -eq $payload -or $payload.code -ne $ExpectedCode) {
            throw "Expected $ExpectedStatus/$ExpectedCode but received status=$status body=$raw"
        }
        return [pscustomobject]@{
            status = $status
            code = [string]$payload.code
            path = if ($payload.errors) { [string]$payload.errors[0].path } else { $null }
        }
    }
    throw "Expected $ExpectedCode but request unexpectedly succeeded"
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-DatabaseScalar([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0 -or @($output).Count -ne 1) {
        throw "Database scalar query failed: $Query"
    }
    return [long](@($output)[0])
}

function New-ActiveRecord([string]$Title, [hashtable]$Values) {
    $created = Invoke-Envelope -Path $recordsPath -Method POST -Idempotent -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId
        title = $Title
        values = $Values
    }
    return Invoke-Envelope -Path "$recordsPath/$($created.recordId):activate" -Method POST -Idempotent -Body @{
        expectedVersion = [long]$created.version
    }
}

function Invoke-RecordQuery([object]$Filter, [object[]]$Sort = @()) {
    return Invoke-Envelope -Path "$runtimeRoot/records:query" -Method POST -Body @{
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

function Assert-Query([string]$FieldCode, [string]$Operator, [object]$Value, [string[]]$ExpectedIds) {
    $result = Invoke-RecordQuery -Filter @{
        kind = 'PREDICATE'; fieldCode = $FieldCode; operator = $Operator; value = $Value
    }
    $actual = @($result.rows | ForEach-Object { [string]$_.recordId } | Sort-Object)
    $expected = @($ExpectedIds | Sort-Object)
    Assert-True (($actual -join ',') -eq ($expected -join ',')) `
        "$FieldCode/$Operator expected $($expected -join ',') but received $($actual -join ',')"
    $script:operatorChecks++
}

function Assert-Sort([string]$FieldCode, [string[]]$ExpectedIds, [string]$Currency) {
    $item = [ordered]@{ fieldCode = $FieldCode; direction = 'ASC'; nulls = 'LAST' }
    if ($Currency) { $item.currency = $Currency }
    $result = Invoke-RecordQuery -Filter $null -Sort @($item)
    $actual = @($result.rows | ForEach-Object { [string]$_.recordId })
    Assert-True (($actual -join ',') -eq ($ExpectedIds -join ',')) `
        "$FieldCode sort expected $($ExpectedIds -join ',') but received $($actual -join ',')"
    $script:sortChecks++
}

$null = Invoke-Envelope -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$fixture.username
    password = [string]$fixture.password
}
$null = Invoke-Envelope -Path "/api/v1/context/systems/$($fixture.systemId):switch" -Method POST -Body @{}
$runtimeRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$recordsPath = "$runtimeRoot/records"

$schema = Invoke-Envelope -Path "$runtimeRoot/record-schema"
$expectedOperators = @{
    PERCENT = @('EQ','NE','GT','GTE','LT','LTE','BETWEEN','EMPTY')
    MONEY = @('EQ','NE','GT','GTE','LT','LTE','BETWEEN','EMPTY')
    DATE_RANGE = @('OVERLAPS','CONTAINS','BEFORE','AFTER','EMPTY')
    TIME = @('EQ','BEFORE','AFTER','BETWEEN','EMPTY')
    TIME_RANGE = @('OVERLAPS','CONTAINS','EMPTY')
    MULTI_SELECT = @('HAS_ANY','HAS_ALL','NOT_ANY','EMPTY')
    CASCADE = @('CONTAINS_NODE','LEAF_EQ','EMPTY')
    SWITCH = @('EQ','EMPTY')
    RATING = @('EQ','NE','GT','GTE','LT','LTE','BETWEEN','EMPTY')
    PROGRESS = @('EQ','NE','GT','GTE','LT','LTE','BETWEEN','EMPTY')
    TAG = @('HAS_ANY','HAS_ALL','EMPTY')
}
foreach ($field in $schema.fields) {
    $expected = @($expectedOperators[[string]$field.type])
    Assert-True (($field.operators -join ',') -eq ($expected -join ',')) `
        "Schema operators mismatch for $($field.type)"
}

$normal = [string]$fixture.optionIds.normal
$urgent = [string]$fixture.optionIds.urgent
$root = [string]$fixture.optionIds.cascadeRoot
$child = [string]$fixture.optionIds.cascadeChild
$valuesA = @{
    completion_rate = 50; budget = @{ amount = '9.00'; currency = 'CNY' }
    service_dates = @('2026-01-01','2026-01-10'); start_time = '09:00:00'
    service_hours = @('09:00:00','12:00:00'); service_labels = @($normal)
    service_area = @($root,$child); enabled = $true; rating = 2; progress = 20
    tags = @('red','shared')
}
$valuesB = @{
    completion_rate = 60; budget = @{ amount = '10.00'; currency = 'CNY' }
    service_dates = @('2026-01-08','2026-01-20'); start_time = '12:00:00'
    service_hours = @('11:00:00','18:00:00'); service_labels = @($normal,$urgent)
    service_area = @($root); enabled = $false; rating = 4; progress = 80
    tags = @('blue','shared')
}
$valuesC = @{
    completion_rate = 70; budget = @{ amount = '10.00'; currency = 'USD' }
    service_dates = @('2026-02-01','2026-02-05'); start_time = '18:00:00'
    service_hours = @('18:00:00','20:00:00'); service_labels = @($urgent)
    service_area = @($root,$child); enabled = $true; rating = 5; progress = 100
    tags = @('green')
}

$recordA = New-ActiveRecord -Title 'typed-query-a' -Values $valuesA
$recordB = New-ActiveRecord -Title 'typed-query-b' -Values $valuesB
$recordC = New-ActiveRecord -Title 'typed-query-c' -Values $valuesC
$recordD = New-ActiveRecord -Title 'typed-query-empty' -Values @{}
$a = [string]$recordA.recordId
$b = [string]$recordB.recordId
$c = [string]$recordC.recordId
$d = [string]$recordD.recordId

Assert-Query 'completion_rate' 'EQ' 50 @($a)
Assert-Query 'completion_rate' 'NE' 50 @($b,$c)
Assert-Query 'completion_rate' 'GT' 60 @($c)
Assert-Query 'completion_rate' 'GTE' 60 @($b,$c)
Assert-Query 'completion_rate' 'LT' 60 @($a)
Assert-Query 'completion_rate' 'LTE' 60 @($a,$b)
Assert-Query 'completion_rate' 'BETWEEN' @(55,65) @($b)
Assert-Query 'completion_rate' 'EMPTY' $true @($d)

Assert-Query 'budget' 'EQ' @{ amount='9.00'; currency='CNY' } @($a)
Assert-Query 'budget' 'NE' @{ amount='9.00'; currency='CNY' } @($b)
Assert-Query 'budget' 'GT' @{ amount='9.00'; currency='CNY' } @($b)
Assert-Query 'budget' 'GTE' @{ amount='9.00'; currency='CNY' } @($a,$b)
Assert-Query 'budget' 'LT' @{ amount='10.00'; currency='CNY' } @($a)
Assert-Query 'budget' 'LTE' @{ amount='10.00'; currency='CNY' } @($a,$b)
Assert-Query 'budget' 'BETWEEN' @(@{amount='9.00';currency='CNY'},@{amount='10.00';currency='CNY'}) @($a,$b)
Assert-Query 'budget' 'EMPTY' $true @($d)

Assert-Query 'service_dates' 'OVERLAPS' @('2026-01-09','2026-01-09') @($a,$b)
Assert-Query 'service_dates' 'CONTAINS' '2026-01-15' @($b)
Assert-Query 'service_dates' 'BEFORE' '2026-01-15' @($a)
Assert-Query 'service_dates' 'AFTER' '2026-01-21' @($c)
Assert-Query 'service_dates' 'EMPTY' $true @($d)

Assert-Query 'start_time' 'EQ' '09:00:00' @($a)
Assert-Query 'start_time' 'BEFORE' '12:00:00' @($a)
Assert-Query 'start_time' 'AFTER' '12:00:00' @($c)
Assert-Query 'start_time' 'BETWEEN' @('10:00:00','18:00:00') @($b,$c)
Assert-Query 'start_time' 'EMPTY' $true @($d)

Assert-Query 'service_hours' 'OVERLAPS' @('11:30:00','12:30:00') @($a,$b)
Assert-Query 'service_hours' 'CONTAINS' '19:00:00' @($c)
Assert-Query 'service_hours' 'EMPTY' $true @($d)

Assert-Query 'service_labels' 'HAS_ANY' @($urgent) @($b,$c)
Assert-Query 'service_labels' 'HAS_ALL' @($normal,$urgent) @($b)
Assert-Query 'service_labels' 'NOT_ANY' @($urgent) @($a,$d)
Assert-Query 'service_labels' 'EMPTY' $true @($d)

Assert-Query 'service_area' 'CONTAINS_NODE' $root @($a,$b,$c)
Assert-Query 'service_area' 'LEAF_EQ' $child @($a,$c)
Assert-Query 'service_area' 'EMPTY' $true @($d)

Assert-Query 'enabled' 'EQ' $true @($a,$c)
Assert-Query 'enabled' 'EMPTY' $true @($d)

Assert-Query 'rating' 'EQ' 2 @($a)
Assert-Query 'rating' 'NE' 2 @($b,$c)
Assert-Query 'rating' 'GT' 4 @($c)
Assert-Query 'rating' 'GTE' 4 @($b,$c)
Assert-Query 'rating' 'LT' 4 @($a)
Assert-Query 'rating' 'LTE' 4 @($a,$b)
Assert-Query 'rating' 'BETWEEN' @(3,5) @($b,$c)
Assert-Query 'rating' 'EMPTY' $true @($d)

Assert-Query 'progress' 'EQ' 20 @($a)
Assert-Query 'progress' 'NE' 20 @($b,$c)
Assert-Query 'progress' 'GT' 80 @($c)
Assert-Query 'progress' 'GTE' 80 @($b,$c)
Assert-Query 'progress' 'LT' 80 @($a)
Assert-Query 'progress' 'LTE' 80 @($a,$b)
Assert-Query 'progress' 'BETWEEN' @(30,90) @($b)
Assert-Query 'progress' 'EMPTY' $true @($d)

Assert-Query 'tags' 'HAS_ANY' @('shared') @($a,$b)
Assert-Query 'tags' 'HAS_ALL' @('red','shared') @($a)
Assert-Query 'tags' 'EMPTY' $true @($d)

Assert-Sort 'completion_rate' @($a,$b,$c,$d) $null
Assert-Sort 'budget' @($a,$b,$c,$d) 'CNY'
Assert-Sort 'start_time' @($a,$b,$c,$d) $null
Assert-Sort 'enabled' @($b,$a,$c,$d) $null
Assert-Sort 'rating' @($a,$b,$c,$d) $null
Assert-Sort 'progress' @($a,$b,$c,$d) $null

$conflictingUpdateValues = @{} + $valuesB
$conflictingUpdateValues.completion_rate = 50
$updateConflict = Invoke-ExpectedError -Path "$recordsPath/$b" -Method PUT -ExpectedStatus 409 `
    -ExpectedCode 'FIELD_UNIQUE_CONFLICT' -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId
        title = 'typed-query-b-conflict'
        expectedVersion = [long]$recordB.version
        values = $conflictingUpdateValues
    }
$queryB = Invoke-RecordQuery -Filter @{
    kind='PREDICATE'; fieldCode='completion_rate'; operator='EQ'; value=60
}
Assert-True (@($queryB.rows).Count -eq 1 -and [string]$queryB.rows[0].recordId -eq $b) `
    'Conflicting active update did not roll back atomically'

$conflictValues = @{ completion_rate = 50 }
$draftE = Invoke-Envelope -Path $recordsPath -Method POST -Idempotent -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; title = 'unique-conflict-e'; values = $conflictValues
}
$activateConflict = Invoke-ExpectedError -Path "$recordsPath/$($draftE.recordId):activate" `
    -ExpectedStatus 409 -ExpectedCode 'FIELD_UNIQUE_CONFLICT' -Body @{ expectedVersion = [long]$draftE.version }
$draftEDetail = Invoke-Envelope -Path "$recordsPath/$($draftE.recordId)"
Assert-True ($draftEDetail.status -eq 'DRAFT' -and [long]$draftEDetail.version -eq 0) `
    'Conflicting activation did not roll back to DRAFT'

$trashedA = Invoke-Envelope -Path "$recordsPath/${a}:trash" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$recordA.version
}
$activeE = Invoke-Envelope -Path "$recordsPath/$($draftE.recordId):activate" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$draftE.version
}
$restoreConflict = Invoke-ExpectedError -Path "$recordsPath/${a}:restore-from-trash" `
    -ExpectedStatus 409 -ExpectedCode 'FIELD_UNIQUE_CONFLICT' -Body @{ expectedVersion = [long]$trashedA.version }
$stillTrashedA = Invoke-Envelope -Path "$recordsPath/$a"
Assert-True ($stillTrashedA.status -eq 'TRASHED') 'Conflicting restore did not roll back atomically'
$trashedE = Invoke-Envelope -Path "$recordsPath/$($activeE.recordId):trash" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$activeE.version
}
$restoredA = Invoke-Envelope -Path "$recordsPath/${a}:restore-from-trash" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$trashedA.version
}
$archivedA = Invoke-Envelope -Path "$recordsPath/${a}:archive" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$restoredA.version
}
$draftF = Invoke-Envelope -Path $recordsPath -Method POST -Idempotent -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; title = 'archive-reservation-f'; values = $conflictValues
}
$archiveConflict = Invoke-ExpectedError -Path "$recordsPath/$($draftF.recordId):activate" `
    -ExpectedStatus 409 -ExpectedCode 'FIELD_UNIQUE_CONFLICT' -Body @{ expectedVersion = [long]$draftF.version }
$unarchivedA = Invoke-Envelope -Path "$recordsPath/${a}:unarchive" -Method POST -Idempotent -Body @{
    expectedVersion = [long]$archivedA.version
}
Assert-True ($unarchivedA.status -eq 'ACTIVE') 'Final unarchive failed'

$uniqueRows = Invoke-DatabaseScalar "SELECT COUNT(*) FROM un_module_record_unique WHERE system_id=$($fixture.systemId)"
$releasedRows = Invoke-DatabaseScalar "SELECT COUNT(*) FROM un_module_record_unique WHERE record_id IN ($($activeE.recordId),$($draftF.recordId))"
Assert-True ($uniqueRows -eq 3) "Expected three active unique reservations but found $uniqueRows"
Assert-True ($releasedRows -eq 0) 'TRASHED and DRAFT records retained unique reservations'

$result = [ordered]@{
    verifiedAt = (Get-Date).ToString('o')
    systemId = [string]$fixture.systemId
    schemaVersionId = [string]$fixture.schemaVersionId
    activeRecordIds = @($a,$b,$c,$d)
    operatorChecks = $operatorChecks
    sortChecks = $sortChecks
    uniqueRows = $uniqueRows
    updateConflict = $updateConflict
    activateConflict = $activateConflict
    restoreConflict = $restoreConflict
    archiveConflict = $archiveConflict
    releasedRecordIds = @([string]$activeE.recordId,[string]$draftF.recordId)
    result = 'PASS'
}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
$result | ConvertTo-Json -Depth 10
