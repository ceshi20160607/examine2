param(
    [ValidateSet('PrepareOld', 'VerifyRotated', 'VerifyRestart')][string]$Phase,
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$FixturePath = '',
    [string]$RotationFixturePath = '',
    [string]$ResultPath = '',
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [Parameter(Mandatory = $true)][string]$DatabasePassword
)

$ErrorActionPreference = 'Stop'
if (-not $FixturePath) { $FixturePath = Join-Path $PSScriptRoot 'fixture.json' }
if (-not $RotationFixturePath) { $RotationFixturePath = Join-Path $PSScriptRoot 'task-02-rotation-fixture.json' }
if (-not $ResultPath) { $ResultPath = Join-Path $PSScriptRoot 'task-02-rotation-runtime.json' }
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$runtimeRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$recordsPath = "$runtimeRoot/records"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Utf8Json([string]$Path, [object]$Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
}

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body,
        [switch]$Idempotent
    )
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    if ($Idempotent) { $headers['Idempotency-Key'] = [guid]::NewGuid().ToString() }
    $arguments = @{
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; WebSession = $Session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    try { $response = Invoke-RestMethod @arguments } catch {
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        throw "API $Method $Path failed ($status): $($_.ErrorDetails.Message)"
    }
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}

function Invoke-ExpectedConflict {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [object]$Body
    )
    $request = [Net.HttpWebRequest]::Create("$BaseUrl$Path")
    $request.Method = 'POST'
    $request.ContentType = 'application/json; charset=utf-8'
    $request.CookieContainer = $Session.Cookies
    $request.Headers.Add('X-Request-ID', [guid]::NewGuid().ToString())
    $request.Headers.Add('Idempotency-Key', [guid]::NewGuid().ToString())
    $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $request.Headers.Add('X-CSRF-Token', [uri]::UnescapeDataString($csrf)) }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    $request.ContentLength = $bytes.Length
    $stream = $request.GetRequestStream()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
    try {
        $response = $request.GetResponse()
        $response.Close()
    } catch [Net.WebException] {
        $response = $_.Exception.Response
        $status = [int]$response.StatusCode
        $reader = New-Object IO.StreamReader($response.GetResponseStream())
        $payload = $reader.ReadToEnd() | ConvertFrom-Json
        $reader.Close(); $response.Close()
        Assert-True ($status -eq 409 -and $payload.code -eq 'FIELD_UNIQUE_CONFLICT') `
            "Expected unique conflict but received $status/$($payload.code)"
        return [string]$payload.code
    }
    throw 'Expected unique conflict but activation succeeded'
}

function Login-Ordinary {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
        account = [string]$fixture.username; password = [string]$fixture.password
    }
    $null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
        -Method POST -Body @{}
    return $session
}

function New-CnIdentity([string]$BirthDate) {
    $first17 = '110101' + $BirthDate + '999'
    $weights = @(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    $checks = @('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')
    $sum = 0
    for ($i = 0; $i -lt 17; $i++) { $sum += ([int]$first17[$i] - [int][char]'0') * $weights[$i] }
    return $first17 + $checks[$sum % 11]
}

function Invoke-SensitiveQuery {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$FieldCode,
        [string]$Value
    )
    return Invoke-Envelope -Session $Session -Path "$runtimeRoot/records:query" -Method POST -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50; recordScope = 'active'; q = $null
        filter = @{ kind = 'PREDICATE'; fieldCode = $FieldCode; operator = 'EQ'; value = $Value }
        sort = @(); columns = @(); viewId = $null
    }
}

function New-ActiveSensitiveRecord {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Title,
        [string]$Identity,
        [string]$Secret
    )
    $created = Invoke-Envelope -Session $Session -Path $recordsPath -Method POST -Idempotent -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; title = $Title
        values = @{ subject = $Title; identity_number = $Identity; private_secret = $Secret }
    }
    return Invoke-Envelope -Session $Session -Path "$recordsPath/$($created.recordId):activate" `
        -Method POST -Idempotent -Body @{ expectedVersion = [long]$created.version }
}

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed' }
    return @($output)
}

$oldIdentity = New-CnIdentity -BirthDate '19900303'
$newIdentity = New-CnIdentity -BirthDate '19900404'
$oldSecret = 'p4-c2-rotation-old-test-secret'
$newSecret = 'p4-c2-rotation-new-test-secret'
$ordinary = Login-Ordinary

if ($Phase -eq 'PrepareOld') {
    $query = Invoke-SensitiveQuery -Session $ordinary -FieldCode 'identity_number' -Value $oldIdentity
    if (@($query.rows).Count -eq 0) {
        $record = New-ActiveSensitiveRecord -Session $ordinary -Title 'p4-c2-rotation-old' `
            -Identity $oldIdentity -Secret $oldSecret
        $oldRecordId = [string]$record.recordId
    } else {
        $oldRecordId = [string]$query.rows[0].recordId
    }
    Write-Utf8Json -Path $RotationFixturePath -Value ([ordered]@{
        preparedAt = (Get-Date).ToString('o'); oldRecordId = $oldRecordId
    })
    [ordered]@{ verdict = 'prepared'; oldRecordId = $oldRecordId } | ConvertTo-Json
    exit 0
}

$rotation = Get-Content -LiteralPath $RotationFixturePath -Raw | ConvertFrom-Json
$oldQuery = Invoke-SensitiveQuery -Session $ordinary -FieldCode 'identity_number' -Value $oldIdentity
Assert-True (@($oldQuery.rows).Count -eq 1 -and [string]$oldQuery.rows[0].recordId -eq [string]$rotation.oldRecordId) `
    'Old v1 sensitive index was not queryable'

if ($Phase -eq 'VerifyRotated') {
    $newQuery = Invoke-SensitiveQuery -Session $ordinary -FieldCode 'identity_number' -Value $newIdentity
    if (@($newQuery.rows).Count -eq 0) {
        $newRecord = New-ActiveSensitiveRecord -Session $ordinary -Title 'p4-c2-rotation-new' `
            -Identity $newIdentity -Secret $newSecret
        $newRecordId = [string]$newRecord.recordId
    } else {
        $newRecordId = [string]$newQuery.rows[0].recordId
    }
    $rotation | Add-Member -NotePropertyName newRecordId -NotePropertyValue $newRecordId -Force
    Write-Utf8Json -Path $RotationFixturePath -Value $rotation

    $oldSecretQuery = Invoke-SensitiveQuery -Session $ordinary -FieldCode 'private_secret' -Value $oldSecret
    Assert-True (@($oldSecretQuery.rows).Count -eq 1 `
        -and [string]$oldSecretQuery.rows[0].recordId -eq [string]$rotation.oldRecordId) `
        'Old v1 secret index was not queryable after rotation'
    $duplicate = Invoke-Envelope -Session $ordinary -Path $recordsPath -Method POST -Idempotent -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; title = 'p4-c2-rotation-duplicate'
        values = @{ subject = 'rotation duplicate'; identity_number = $oldIdentity }
    }
    $conflictCode = Invoke-ExpectedConflict -Session $ordinary `
        -Path "$recordsPath/$($duplicate.recordId):activate" -Body @{ expectedVersion = [long]$duplicate.version }
} else {
    $newRecordId = [string]$rotation.newRecordId
    $newQuery = Invoke-SensitiveQuery -Session $ordinary -FieldCode 'identity_number' -Value $newIdentity
    Assert-True (@($newQuery.rows).Count -eq 1 -and [string]$newQuery.rows[0].recordId -eq $newRecordId) `
        'New v2 sensitive index was not queryable after restart'
    $conflictCode = 'FIELD_UNIQUE_CONFLICT'
}

$databaseRows = @(Invoke-DatabaseRows @"
SELECT
  SUM(record_id=$($rotation.oldRecordId) AND field_type='IDENTITY' AND encryption_key_version='enc-v1'),
  SUM(record_id=$newRecordId AND field_type='IDENTITY' AND encryption_key_version='enc-v2'),
  (SELECT COUNT(DISTINCT hash_key_version) FROM un_module_record_index
   WHERE record_id=$newRecordId AND value_kind='HASH'),
  (SELECT GROUP_CONCAT(DISTINCT hash_key_version ORDER BY hash_key_version)
   FROM un_module_record_index WHERE record_id=$newRecordId AND value_kind='HASH')
FROM un_module_record_value
WHERE record_id IN ($($rotation.oldRecordId),$newRecordId);
"@)
$database = @($databaseRows[0] -split "`t")
Assert-True ([int]$database[0] -eq 1) 'Old record encryption version changed'
Assert-True ([int]$database[1] -eq 1) 'New record did not use encryption v2'
Assert-True ([int]$database[2] -eq 2 -and [string]$database[3] -eq 'hash-v1,hash-v2') `
    'New record did not create both query-compatible hash routes'

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o'); verdict = 'pass'; phase = $Phase
    oldRecordId = [string]$rotation.oldRecordId; newRecordId = $newRecordId
    oldEncryptionVersion = 'enc-v1'; newEncryptionVersion = 'enc-v2'
    newHashRouteVersions = @('hash-v1', 'hash-v2')
    oldRecordQueryable = $true; newRecordQueryable = $true
    uniqueConflictCode = $conflictCode
    health = [string](Invoke-RestMethod "$BaseUrl/management/health").status
}
Write-Utf8Json -Path $ResultPath -Value $result
$result | ConvertTo-Json -Depth 10
