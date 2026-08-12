param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [Parameter(Mandatory = $true)][string]$RootUsername,
    [Parameter(Mandatory = $true)][string]$RootPassword,
    [string]$DatabaseHost = '192.168.0.211',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'examine2',
    [string]$DatabaseUsername = 'examine',
    [Parameter(Mandatory = $true)][string]$DatabasePassword,
    [string]$FixturePath = '',
    [string]$ResultPath = ''
)

$ErrorActionPreference = 'Stop'
if (-not $FixturePath) { $FixturePath = Join-Path $PSScriptRoot 'fixture.json' }
if (-not $ResultPath) { $ResultPath = Join-Path $PSScriptRoot 'task-02-runtime.json' }
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$operatorChecks = 0
$sortChecks = 0

function New-Session {
    return New-Object Microsoft.PowerShell.Commands.WebRequestSession
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
        $arguments.Body = $Body | ConvertTo-Json -Depth 40 -Compress
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
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'POST',
        [object]$Body,
        [int]$ExpectedStatus,
        [string]$ExpectedCode
    )
    $request = [System.Net.HttpWebRequest]::Create("$BaseUrl$Path")
    $request.Method = $Method
    $request.ContentType = 'application/json; charset=utf-8'
    $request.CookieContainer = $Session.Cookies
    $request.Headers.Add('X-Request-ID', [guid]::NewGuid().ToString())
    $request.Headers.Add('Idempotency-Key', [guid]::NewGuid().ToString())
    $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $request.Headers.Add('X-CSRF-Token', [uri]::UnescapeDataString($csrf)) }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 40 -Compress))
    $request.ContentLength = $bytes.Length
    $stream = $request.GetRequestStream()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
    try {
        $response = $request.GetResponse()
        $response.Close()
    } catch [Net.WebException] {
        $response = $_.Exception.Response
        $status = if ($response) { [int]$response.StatusCode } else { 0 }
        $reader = if ($response) { New-Object IO.StreamReader($response.GetResponseStream()) } else { $null }
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

function Write-Utf8Json([string]$TargetPath, [object]$Value) {
    $parent = Split-Path -Parent $TargetPath
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 40), [Text.UTF8Encoding]::new($false))
}

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed' }
    return @($output)
}

function Invoke-DatabaseExecute([string]$Statement) {
    $null = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Statement
    if ($LASTEXITCODE -ne 0) { throw 'Database statement failed' }
}

function Login-Root {
    $session = New-Session
    $null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
        account = $RootUsername; password = $RootPassword
    }
    $null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
        -Method POST -Body @{}
    return $session
}

function Login-Ordinary {
    $session = New-Session
    $null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
        account = [string]$fixture.username; password = [string]$fixture.password
    }
    $null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
        -Method POST -Body @{}
    return $session
}

function Set-SensitivePermissions([bool]$Enabled) {
    $root = Login-Root
    $roles = Invoke-Envelope -Session $root -Path "/api/v1/systems/$($fixture.systemId)/admin/roles?size=200"
    $role = @($roles.items | Where-Object { [string]$_.id -eq [string]$fixture.roleId })[0]
    Assert-True ($null -ne $role) 'Fixture role was not found'
    $codes = @($role.permissionCodes | Where-Object { $_ -notin $script:sensitivePermissionCodes })
    if ($Enabled) { $codes += $script:sensitivePermissionCodes }
    $codes = @($codes | Select-Object -Unique)
    $draft = Invoke-Envelope -Session $root `
        -Path "/api/v1/systems/$($fixture.systemId)/admin/roles/$($role.id)/draft" -Method PUT -Body @{
        name = 'P4-C2 query acceptance member'; description = 'P4-C2 ordinary-member query acceptance role'
        permissionCodes = $codes
        deniedPermissionCodes = @($role.deniedPermissionCodes); dataScopeId = [string]$role.dataScopeId
        version = [string]$role.version
    }
    $check = Invoke-Envelope -Session $root `
        -Path "/api/v1/systems/$($fixture.systemId)/admin/roles/$($role.id)/draft:check" `
        -Method POST -Idempotent -Body @{ version = [string]$draft.version }
    $published = Invoke-Envelope -Session $root `
        -Path "/api/v1/systems/$($fixture.systemId)/admin/roles/$($role.id)/draft:publish" `
        -Method POST -Idempotent -Body @{ version = [string]$check.version }
    return $published
}

function New-CnIdentity([int]$Sequence) {
    $first17 = '11010119900101' + $Sequence.ToString('000')
    $weights = @(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    $checks = @('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')
    $sum = 0
    for ($i = 0; $i -lt 17; $i++) { $sum += ([int]$first17[$i] - [int][char]'0') * $weights[$i] }
    return $first17 + $checks[$sum % 11]
}

function New-ActiveRecord {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Title,
        [hashtable]$Values
    )
    $created = Invoke-Envelope -Session $Session -Path $script:recordsPath -Method POST -Idempotent -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; title = $Title; values = $Values
    }
    return Invoke-Envelope -Session $Session -Path "$script:recordsPath/$($created.recordId):activate" `
        -Method POST -Idempotent -Body @{ expectedVersion = [long]$created.version }
}

function Invoke-RecordQuery {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [object]$Filter,
        [object[]]$Sort = @()
    )
    return Invoke-Envelope -Session $Session -Path "$script:runtimeRoot/records:query" -Method POST -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50; recordScope = 'active'
        q = $null; filter = $Filter; sort = @($Sort); columns = @(); viewId = $null
    }
}

function Assert-Query {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$FieldCode,
        [string]$Operator,
        [object]$Value,
        [string[]]$ExpectedIds
    )
    $result = Invoke-RecordQuery -Session $Session -Filter @{
        kind = 'PREDICATE'; fieldCode = $FieldCode; operator = $Operator; value = $Value
    }
    $actual = @($result.rows | ForEach-Object { [string]$_.recordId } | Sort-Object)
    $expected = @($ExpectedIds | Sort-Object)
    Assert-True (($actual -join ',') -eq ($expected -join ',')) `
        "$FieldCode/$Operator expected $($expected -join ',') but received $($actual -join ',')"
    $script:operatorChecks++
}

function Assert-Sort {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$FieldCode,
        [string[]]$ExpectedIds
    )
    $result = Invoke-RecordQuery -Session $Session -Filter $null -Sort @(@{
        fieldCode = $FieldCode; direction = 'ASC'; nulls = 'LAST'
    })
    $actual = @($result.rows | ForEach-Object { [string]$_.recordId })
    Assert-True (($actual -join ',') -eq ($ExpectedIds -join ',')) `
        "$FieldCode sort expected $($ExpectedIds -join ',') but received $($actual -join ',')"
    $script:sortChecks++
}

$expectedOperators = [ordered]@{
    PHONE = @('EQ', 'EMPTY')
    EMAIL = @('EQ', 'PREFIX', 'EMPTY')
    URL = @('EQ', 'PREFIX', 'EMPTY')
    IDENTITY = @('EQ', 'EMPTY')
    ADDRESS = @('EQ_REGION', 'PREFIX', 'EMPTY')
    GEO = @('WITHIN_BOX', 'NEAR', 'EMPTY')
    BARCODE = @('EQ', 'PREFIX', 'EMPTY')
    RICH_TEXT = @('CONTAINS', 'EMPTY')
    JSON = @('DECLARED_PATH_EQ', 'DECLARED_PATH_EXISTS')
    SECRET = @('EQ', 'EMPTY')
    STATUS = @('EQ', 'IN', 'EMPTY')
}
$indexModes = @{
    PHONE = 'UNIQUE'; EMAIL = 'UNIQUE'; URL = 'UNIQUE'; IDENTITY = 'UNIQUE'
    ADDRESS = 'FILTER'; GEO = 'FILTER'; BARCODE = 'UNIQUE'; RICH_TEXT = 'FILTER'
    JSON = 'FILTER'; SECRET = 'UNIQUE'; STATUS = 'SORT'
}
$sensitivePermissionCodes = @(
    "module.$($fixture.moduleCode).field.identity_number.sensitive.read",
    "module.$($fixture.moduleCode).field.identity_number.sensitive.query",
    "module.$($fixture.moduleCode).field.private_secret.sensitive.query"
)
$configRoot = "/api/v1/systems/$($fixture.systemId)/admin/config"
$root = Login-Root
$rootState = Invoke-Envelope -Session $root -Path $configRoot
$revision = [long]$rootState.draftRevision
$fields = @(Invoke-Envelope -Session $root -Path "$configRoot/modules/$($fixture.moduleId)/fields")
$p4c2Fields = @($fields | Where-Object { [string]$_.type -in $expectedOperators.Keys })
Assert-True ($p4c2Fields.Count -eq 11) "Expected eleven P4-C2 fields but found $($p4c2Fields.Count)"
$jsonField = @($p4c2Fields | Where-Object type -eq 'JSON')[0]
$ticketPathId = [string]$jsonField.id
$countPathId = ([long]$jsonField.id + 1).ToString()
$configurationReady = @($p4c2Fields | Where-Object {
    [string]$_.indexMode -eq $indexModes[[string]$_.type] -and [bool]$_.filterable -and
    ([bool]$_.searchable -eq ([string]$_.type -eq 'RICH_TEXT'))
}).Count -eq 11 -and @($jsonField.properties.queryPaths).Count -eq 2

if (-not $configurationReady) {
    foreach ($field in $p4c2Fields) {
        $properties = $field.properties
        if ([string]$field.type -eq 'JSON') {
            $properties = [ordered]@{
                jsonSchema = $field.properties.jsonSchema
                queryPaths = @(
                    @{ pathSnapshotId = $ticketPathId; path = '$.ticket'; type = 'STRING' },
                    @{ pathSnapshotId = $countPathId; path = '$.count'; type = 'INTEGER' }
                )
            }
        }
        $updated = Invoke-Envelope -Session $root `
            -Path "$configRoot/modules/$($fixture.moduleId)/fields/$($field.id)" -Method PUT -Body @{
            dictionaryId = $field.dictionaryId; targetModuleId = $field.targetModuleId
            code = $field.code; name = $field.name; type = [string]$field.type; sortOrder = [int]$field.sortOrder
            required = $false; hidden = [bool]$field.hidden; readonly = [bool]$field.readonly
            searchable = ([string]$field.type -eq 'RICH_TEXT'); filterable = $true
            showInList = [bool]$field.showInList; showInDetail = [bool]$field.showInDetail
            indexMode = $indexModes[[string]$field.type]; status = [string]$field.status
            properties = $properties; version = [string]$field.version; draftRevision = [string]$revision
        }
        $revision++
    }
    $check = Invoke-Envelope -Session $root -Path "$configRoot/checks" -Method POST -Body @{
        draftRevision = [string]$revision
    }
    Assert-True ([int]$check.blockerCount -eq 0) 'P4-C2 query configuration check has blockers'
    $rootState = Invoke-Envelope -Session $root -Path $configRoot
    $published = Invoke-Envelope -Session $root -Path "${configRoot}:publish" -Method POST -Idempotent -Body @{
        checkId = [string]$check.id; draftRevision = [string]$revision
        configRootVersion = [string]$rootState.version
        reason = 'P4-C2 typed query, index and sensitive authorization acceptance'
    }
    $fixture.schemaVersionId = [string]$published.version.id
} else {
    $fixture.schemaVersionId = [string]$rootState.activeVersionId
}
$fixture | Add-Member -NotePropertyName p4C2JsonPathIds -NotePropertyValue ([pscustomobject]@{
    ticket = $ticketPathId; count = $countPathId
}) -Force
Write-Utf8Json -TargetPath $FixturePath -Value $fixture

$root = Login-Root
$permissions = Invoke-Envelope -Session $root `
    -Path "/api/v1/systems/$($fixture.systemId)/admin/permissions?size=200&keyword=sensitive"
$availableSensitive = @($permissions.items | ForEach-Object { [string]$_.code })
foreach ($code in $sensitivePermissionCodes) {
    Assert-True ($code -in $availableSensitive) "Sensitive permission was not registered: $code"
}
$null = Set-SensitivePermissions -Enabled $true

$ordinary = Login-Ordinary
$runtimeRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$recordsPath = "$runtimeRoot/records"
$schema = Invoke-Envelope -Session $ordinary -Path "$runtimeRoot/record-schema"
foreach ($field in @($schema.fields | Where-Object { [string]$_.type -in $expectedOperators.Keys })) {
    $expected = @($expectedOperators[[string]$field.type])
    Assert-True (($field.operators -join ',') -eq ($expected -join ',')) `
        "Schema operators mismatch for $($field.type): $($field.operators -join ',')"
}

$priorRows = @(Invoke-DatabaseRows @"
SELECT record_id,status,version
FROM un_module_record
WHERE system_id=$($fixture.systemId) AND schema_version_id=$($fixture.schemaVersionId)
  AND (title LIKE 'typed-query-%' OR title LIKE 'duplicate-%')
  AND status IN ('DRAFT','ACTIVE','ARCHIVED');
"@)
foreach ($row in $priorRows) {
    $parts = $row -split "`t"
    $recordId = [string]$parts[0]
    $status = [string]$parts[1]
    $version = [long]$parts[2]
    $command = if ($status -eq 'DRAFT') { 'discard' } else { 'trash' }
    $null = Invoke-Envelope -Session $ordinary -Path "$recordsPath/${recordId}:$command" `
        -Method POST -Idempotent -Body @{ expectedVersion = $version }
}

$random = [Random]::new()
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$phoneA = '139' + $random.Next(10000000, 99999999).ToString()
$phoneB = '+1202555' + $random.Next(1000, 9999).ToString()
$identityA = New-CnIdentity -Sequence $random.Next(1, 499)
$identityB = New-CnIdentity -Sequence $random.Next(500, 999)
$secretA = "secret-a-$suffix"
$secretB = "secret-b-$suffix"
$barcodeA = "A-$suffix"
$barcodeB = "B-$suffix"
$statusNew = [string]$fixture.statusItemIds.new
$statusActive = [string]$fixture.statusItemIds.active
$valuesA = @{
    subject = "P4-C2 query A $suffix"; contact_phone = @($phoneA)
    contact_email = @("Alice-$suffix@Example.com"); reference_url = "HTTPS://EXAMPLE.COM/A-$suffix"
    identity_number = $identityA
    service_address = @{ countryCode = 'CN'; regionCode = 'BJ'; display = "Beijing Chaoyang $suffix"; city = 'Beijing'; detail = 'Road 1' }
    service_geo = @{ lat = 39.9000; lng = 116.4000 }
    asset_barcode = @{ symbology = 'CODE128'; payload = $barcodeA }
    rich_notes = '<p>Alpha pump safety inspection</p>'
    metadata_json = @{ ticket = "ALPHA-$suffix"; count = 7 }
    private_secret = $secretA; workflow_status = $statusNew
}
$valuesB = @{
    subject = "P4-C2 query B $suffix"; contact_phone = @($phoneB)
    contact_email = @("Betty-$suffix@Example.org"); reference_url = "https://example.org/b-$suffix"
    identity_number = $identityB
    service_address = @{ countryCode = 'US'; regionCode = 'CA'; display = "San Francisco Market $suffix"; city = 'San Francisco'; detail = 'Market Street' }
    service_geo = @{ lat = 31.2304; lng = 121.4737 }
    asset_barcode = @{ symbology = 'CODE128'; payload = $barcodeB }
    rich_notes = '<p>Beta valve routine maintenance</p>'
    metadata_json = @{ ticket = "BETA-$suffix" }
    private_secret = $secretB; workflow_status = $statusNew
}
$recordA = New-ActiveRecord -Session $ordinary -Title "typed-query-a-$suffix" -Values $valuesA
$recordBActivated = New-ActiveRecord -Session $ordinary -Title "typed-query-b-$suffix" -Values $valuesB
$valuesB.workflow_status = $statusActive
$recordB = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$($recordBActivated.recordId)" -Method PUT -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; expectedVersion = [long]$recordBActivated.version
    title = "typed-query-b-$suffix"; values = $valuesB
}
$recordD = New-ActiveRecord -Session $ordinary -Title "typed-query-empty-$suffix" -Values @{
    subject = "P4-C2 query empty $suffix"
}
$a = [string]$recordA.recordId
$b = [string]$recordB.recordId
$d = [string]$recordD.recordId

Assert-Query $ordinary 'contact_phone' 'EQ' ('+86' + $phoneA) @($a)
Assert-Query $ordinary 'contact_phone' 'EMPTY' $true @($d)
Assert-Query $ordinary 'contact_email' 'EQ' "Alice-$suffix@example.com" @($a)
Assert-Query $ordinary 'contact_email' 'PREFIX' "Alice-$suffix@" @($a)
Assert-Query $ordinary 'contact_email' 'EMPTY' $true @($d)
Assert-Query $ordinary 'reference_url' 'EQ' "https://example.com/A-$suffix" @($a)
Assert-Query $ordinary 'reference_url' 'PREFIX' 'https://example.com/' @($a)
Assert-Query $ordinary 'reference_url' 'EMPTY' $true @($d)
Assert-Query $ordinary 'identity_number' 'EQ' $identityA @($a)
Assert-Query $ordinary 'identity_number' 'EMPTY' $true @($d)
Assert-Query $ordinary 'service_address' 'EQ_REGION' @{ countryCode = 'CN'; regionCode = 'BJ' } @($a)
Assert-Query $ordinary 'service_address' 'PREFIX' 'Beijing' @($a)
Assert-Query $ordinary 'service_address' 'EMPTY' $true @($d)
Assert-Query $ordinary 'service_geo' 'WITHIN_BOX' @{ south = 39.8; west = 116.3; north = 40.0; east = 116.5 } @($a)
Assert-Query $ordinary 'service_geo' 'NEAR' @{ lat = 31.2304; lng = 121.4737; radiusMeters = 5000 } @($b)
Assert-Query $ordinary 'service_geo' 'EMPTY' $true @($d)
Assert-Query $ordinary 'asset_barcode' 'EQ' "CODE128:$barcodeA" @($a)
Assert-Query $ordinary 'asset_barcode' 'PREFIX' 'CODE128:A-' @($a)
Assert-Query $ordinary 'asset_barcode' 'EMPTY' $true @($d)
Assert-Query $ordinary 'rich_notes' 'CONTAINS' 'pump safety' @($a)
Assert-Query $ordinary 'rich_notes' 'EMPTY' $true @($d)
Assert-Query $ordinary 'metadata_json' 'DECLARED_PATH_EQ' @{
    pathSnapshotId = $ticketPathId; value = "ALPHA-$suffix"
} @($a)
Assert-Query $ordinary 'metadata_json' 'DECLARED_PATH_EXISTS' $countPathId @($a)
Assert-Query $ordinary 'private_secret' 'EQ' $secretA @($a)
Assert-Query $ordinary 'private_secret' 'EMPTY' $true @($d)
Assert-Query $ordinary 'workflow_status' 'EQ' $statusNew @($a)
Assert-Query $ordinary 'workflow_status' 'IN' @($statusNew, $statusActive) @($a, $b)
Assert-Query $ordinary 'workflow_status' 'EMPTY' $true @($d)
Assert-Sort $ordinary 'asset_barcode' @($a, $b, $d)
Assert-Sort $ordinary 'workflow_status' @($a, $b, $d)

$detailA = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$a"
$detailValues = @{}
foreach ($value in $detailA.values) { $detailValues[[string]$value.fieldCode] = $value.value }
Assert-True ([string]$detailValues.identity_number -eq $identityA) 'IDENTITY read permission did not reveal the authorized value'
Assert-True ($null -eq $detailValues.private_secret) 'SECRET plaintext was returned'

$duplicateDraft = Invoke-Envelope -Session $ordinary -Path $recordsPath -Method POST -Idempotent -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; title = "duplicate-$suffix"
    values = @{ subject = "duplicate-$suffix"; contact_phone = @($phoneA) }
}
$uniqueConflict = Invoke-ExpectedError -Session $ordinary `
    -Path "$recordsPath/$($duplicateDraft.recordId):activate" -ExpectedStatus 409 `
    -ExpectedCode 'FIELD_UNIQUE_CONFLICT' -Body @{ expectedVersion = [long]$duplicateDraft.version }
$duplicateDetail = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$($duplicateDraft.recordId)"
Assert-True ($duplicateDetail.status -eq 'DRAFT') 'Unique conflict did not roll back activation'

$dbRows = @(Invoke-DatabaseRows @"
SELECT COUNT(*),
       SUM(value_kind='HASH' AND hash_key_version IS NOT NULL AND hash_value IS NOT NULL),
       SUM(value_kind='GEO' AND LENGTH(geohash)=12 AND geo_lat IS NOT NULL AND geo_lng IS NOT NULL),
       SUM(path_snapshot_id IN ($ticketPathId,$countPathId))
FROM un_module_record_index
WHERE record_id IN ($a,$b);
"@)
$dbLine = @($dbRows[0] -split "`t")
Assert-True ([int]$dbLine[0] -gt 20) 'P4-C2 typed index rows were not created'
Assert-True ([int]$dbLine[1] -ge 4) 'Sensitive blind-index rows were not created'
Assert-True ([int]$dbLine[2] -eq 2) 'GEO exact/geohash rows were not created'
Assert-True ([int]$dbLine[3] -eq 3) 'Declared JSON path rows were not created'

$seedBase = 8100000000000000000L
$seedEnd = $seedBase + 2000
try {
    Invoke-DatabaseExecute @"
SET SESSION cte_max_recursion_depth=3000;
INSERT INTO un_module_record_index
    (id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,
     logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,
     record_status,value_kind,geohash,geo_lat,geo_lng,created_at,updated_at)
WITH RECURSIVE seq(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM seq WHERE n<2000)
SELECT $seedBase+n,i.system_id,i.tenant_id,i.record_id,i.schema_version_id,i.module_snapshot_id,
       i.logical_module_id,i.logical_field_id,i.index_generation_id,i.normalization_generation_id,
       100000+n,0,i.record_status,'GEO',i.geohash,i.geo_lat,i.geo_lng,NOW(3),NOW(3)
FROM seq JOIN un_module_record_index i ON i.record_id=$a
WHERE i.logical_field_id=$($fixture.p4C2FieldIds.service_geo) AND i.value_kind='GEO' AND i.path_snapshot_id=0;
"@
    $geoLimit = Invoke-ExpectedError -Session $ordinary -Path "$runtimeRoot/records:query" `
        -ExpectedStatus 422 -ExpectedCode 'QUERY_TOO_BROAD' -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50; recordScope = 'active'; q = $null
        filter = @{ kind = 'PREDICATE'; fieldCode = 'service_geo'; operator = 'WITHIN_BOX'; value = @{
            south = 39.8; west = 116.3; north = 40.0; east = 116.5
        } }; sort = @(); columns = @(); viewId = $null
    }
} finally {
    Invoke-DatabaseExecute "DELETE FROM un_module_record_index WHERE id BETWEEN $seedBase AND $seedEnd;"
}

$null = Set-SensitivePermissions -Enabled $false
$restricted = Login-Ordinary
$restrictedSchema = Invoke-Envelope -Session $restricted -Path "$runtimeRoot/record-schema"
$restrictedIdentity = @($restrictedSchema.fields | Where-Object fieldCode -eq 'identity_number')[0]
$restrictedSecret = @($restrictedSchema.fields | Where-Object fieldCode -eq 'private_secret')[0]
Assert-True (@($restrictedIdentity.operators).Count -eq 0) 'IDENTITY operators leaked without sensitive query permission'
Assert-True (@($restrictedSecret.operators).Count -eq 0) 'SECRET operators leaked without sensitive query permission'
$restrictedDetail = Invoke-Envelope -Session $restricted -Path "$recordsPath/$a"
$restrictedIdentityValue = @($restrictedDetail.values | Where-Object fieldCode -eq 'identity_number')[0]
Assert-True ($null -eq $restrictedIdentityValue.value) 'IDENTITY plaintext leaked without sensitive read permission'
$permissionDenied = Invoke-ExpectedError -Session $restricted -Path "$runtimeRoot/records:query" `
    -ExpectedStatus 422 -ExpectedCode 'QUERY_FIELD_UNAVAILABLE' -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50; recordScope = 'active'; q = $null
    filter = @{ kind = 'PREDICATE'; fieldCode = 'identity_number'; operator = 'EQ'; value = $identityA }
    sort = @(); columns = @(); viewId = $null
}
$null = Set-SensitivePermissions -Enabled $true

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    systemId = [string]$fixture.systemId
    schemaVersionId = [string]$fixture.schemaVersionId
    activeRecordIds = @($a, $b, $d)
    operatorChecks = $operatorChecks
    sortChecks = $sortChecks
    registeredSensitivePermissions = $sensitivePermissionCodes.Count
    authorizedIdentityRead = $true
    unauthorizedSensitiveOperators = 0
    permissionDeniedCode = [string]$permissionDenied.code
    uniqueConflictCode = [string]$uniqueConflict.code
    geoCandidateLimitCode = [string]$geoLimit.code
    typedIndexRows = [int]$dbLine[0]
    blindIndexRows = [int]$dbLine[1]
    geoRows = [int]$dbLine[2]
    jsonPathRows = [int]$dbLine[3]
    flywayVersion = [string]@((@(Invoke-DatabaseRows `
        "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;"))[0])
    health = [string](Invoke-RestMethod -Uri "$BaseUrl/management/health").status
}
Write-Utf8Json -TargetPath $ResultPath -Value $result
$result | ConvertTo-Json -Depth 20
