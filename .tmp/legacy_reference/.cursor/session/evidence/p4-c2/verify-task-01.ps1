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
if (-not $ResultPath) { $ResultPath = Join-Path $PSScriptRoot 'task-01-runtime.json' }

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
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $Session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    try {
        $response = Invoke-RestMethod @arguments
    } catch {
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        $errorBody = $_.ErrorDetails.Message
        if (-not $errorBody -and $_.Exception.Response) {
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errorBody = $reader.ReadToEnd()
                $reader.Dispose()
            } catch { $errorBody = $null }
        }
        throw "API $Method $Path failed ($status): $errorBody"
    }
    if ($response.code -ne 'OK') {
        throw "API $Method $Path returned $($response.code): $($response.message)"
    }
    return $response.data
}

function Invoke-ExpectedFieldError {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [object]$Body
    )
    $headers = @{
        'X-Request-ID' = [guid]::NewGuid().ToString()
        'Idempotency-Key' = [guid]::NewGuid().ToString()
    }
    $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    try {
        $null = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method POST -Headers $headers -WebSession $Session `
            -ContentType 'application/json; charset=utf-8' -Body ($Body | ConvertTo-Json -Depth 30 -Compress)
    } catch {
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        $errorBody = $_.ErrorDetails.Message
        if (-not $errorBody -and $_.Exception.Response) {
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errorBody = $reader.ReadToEnd()
                $reader.Dispose()
            } catch { $errorBody = $null }
        }
        $payload = if ($errorBody) { $errorBody | ConvertFrom-Json } else { $null }
        if ($status -ne 422 -or ($payload -and $payload.code -ne 'FIELD_VALUE_INVALID')) {
            throw "Unexpected field error ($status): $errorBody"
        }
        return $(if ($payload) { $payload } else { [pscustomobject]@{ code='FIELD_VALUE_INVALID' } })
    }
    throw 'Invalid P4-C2 value unexpectedly succeeded'
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Utf8Json([string]$TargetPath, [object]$Value) {
    $parent = Split-Path -Parent $TargetPath
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
}

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw "Database query failed: $Query" }
    return @($output)
}

$baseFixture = Join-Path ([IO.Path]::GetDirectoryName($FixturePath)) 'fixture-base.json'
$baseProvision = Join-Path $PSScriptRoot '..\p4-b2\provision-fixture.ps1'
if (Test-Path -LiteralPath $FixturePath) {
    $fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
} else {
    if (-not (Test-Path -LiteralPath $baseFixture)) {
        $null = & $baseProvision -BaseUrl $BaseUrl -RootUsername $RootUsername -RootPassword $RootPassword `
            -OutputPath $baseFixture -FixturePrefix 'p4_c2' -FixtureLabel 'P4-C2' `
            -SystemTitle 'P4-C2 secure structured field acceptance' -FieldProfile BASE
    }
    if (-not (Test-Path -LiteralPath $baseFixture)) { throw 'Base fixture provisioning produced no fixture' }
    $fixture = Get-Content -LiteralPath $baseFixture -Raw | ConvertFrom-Json
}

$root = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $root -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $RootUsername; password = $RootPassword
}
$null = Invoke-Envelope -Session $root -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
    -Method POST -Body @{}
$configRoot = "/api/v1/systems/$($fixture.systemId)/admin/config"
$rootState = Invoke-Envelope -Session $root -Path $configRoot
$revision = [long]$rootState.draftRevision
$expectedTypes = @('PHONE','EMAIL','URL','IDENTITY','ADDRESS','GEO','BARCODE','RICH_TEXT','JSON','SECRET','STATUS')
$probeSchema = $null
try {
    $probe = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = Invoke-Envelope -Session $probe -Path '/api/v1/auth/login' -Method POST -Body @{
        account = [string]$fixture.username; password = [string]$fixture.password
    }
    $null = Invoke-Envelope -Session $probe -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
        -Method POST -Body @{}
    $probeSchema = Invoke-Envelope -Session $probe `
        -Path "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)/record-schema"
} catch {
    $probeSchema = $null
}
$existingFields = if ($probeSchema) {
    @($probeSchema.fields | Where-Object { $_.type -in $expectedTypes })
} else { @() }
$configurationAlreadyPublished = $existingFields.Count -eq $expectedTypes.Count

if ($configurationAlreadyPublished) {
    $fieldIds = [ordered]@{}
    foreach ($field in $existingFields) { $fieldIds[[string]$field.fieldCode] = [string]$field.logicalFieldId }
    $statusField = @($existingFields | Where-Object type -eq 'STATUS')[0]
    $statusNew = [pscustomobject]@{ id = [string](@($statusField.options | Where-Object label -eq 'New')[0].value) }
    $statusActive = [pscustomobject]@{ id = [string](@($statusField.options | Where-Object label -eq 'Active')[0].value) }
    $statusDone = [pscustomobject]@{ id = [string](@($statusField.options | Where-Object label -eq 'Done')[0].value) }
    $published = [pscustomobject]@{ version = [pscustomobject]@{ id = [string]$rootState.activeVersionId } }
} else {

function Add-ConfigResource {
    param([string]$Path, [hashtable]$Body)
    $Body.draftRevision = [string]$script:revision
    $result = Invoke-Envelope -Session $root -Path $Path -Method POST -Idempotent -Body $Body
    $script:revision++
    return $result
}

$statusDictionary = Add-ConfigResource -Path "$configRoot/dictionaries" -Body @{
    code = 'record_state'; name = 'Record state'; type = 'STATUS'; category = 'runtime'
    description = 'P4-C2 status state machine'; status = 'ENABLED'
}
$statusNew = Add-ConfigResource -Path "$configRoot/dictionaries/$($statusDictionary.id)/items" -Body @{
    parentId = $null; code = 'new'; label = 'New'; semanticKey = 'NEW'; color = '#2563a6'
    iconKey = $null; sortOrder = 0; isDefault = $true; status = 'ENABLED'
}
$statusActive = Add-ConfigResource -Path "$configRoot/dictionaries/$($statusDictionary.id)/items" -Body @{
    parentId = $null; code = 'active'; label = 'Active'; semanticKey = 'ACTIVE'; color = '#287a4b'
    iconKey = $null; sortOrder = 1; isDefault = $false; status = 'ENABLED'
}
$statusDone = Add-ConfigResource -Path "$configRoot/dictionaries/$($statusDictionary.id)/items" -Body @{
    parentId = $null; code = 'done'; label = 'Done'; semanticKey = 'DONE'; color = '#5b6472'
    iconKey = $null; sortOrder = 2; isDefault = $false; status = 'ENABLED'
}

$fieldDefinitions = @(
    @{ code='contact_phone'; name='Contact phone'; type='PHONE'; properties=@{ defaultCountry='CN' } },
    @{ code='contact_email'; name='Contact email'; type='EMAIL'; properties=@{} },
    @{ code='reference_url'; name='Reference URL'; type='URL'; properties=@{} },
    @{ code='identity_number'; name='Identity number'; type='IDENTITY'; properties=@{ identityKind='CN_RESIDENT_ID' } },
    @{ code='service_address'; name='Service address'; type='ADDRESS'; properties=@{} },
    @{ code='service_geo'; name='Service coordinates'; type='GEO'; properties=@{ coordinateSystem='WGS84' } },
    @{ code='asset_barcode'; name='Asset barcode'; type='BARCODE'; properties=@{ symbologies=@('CODE128','EAN13') } },
    @{ code='rich_notes'; name='Rich notes'; type='RICH_TEXT'; properties=@{ sanitize=$true } },
    @{ code='metadata_json'; name='Metadata JSON'; type='JSON'; properties=@{
        jsonSchema=@{ type='object'; required=@('ticket'); additionalProperties=$false; properties=@{
            ticket=@{ type='string'; minLength=1; maxLength=32 }
            count=@{ type='integer'; minimum=0; maximum=100 }
        } }
        queryPaths=@()
    } },
    @{ code='private_secret'; name='Private secret'; type='SECRET'; properties=@{ minLength=1; maxLength=4096 } },
    @{ code='workflow_status'; name='Workflow status'; type='STATUS'; dictionaryId=[string]$statusDictionary.id; properties=@{
        initialStateIds=@([string]$statusNew.id)
        transitions=@(
            @{ from=[string]$statusNew.id; to=[string]$statusActive.id },
            @{ from=[string]$statusActive.id; to=[string]$statusDone.id },
            @{ from=[string]$statusDone.id; to=[string]$statusActive.id }
        )
    } }
)

$fieldIds = [ordered]@{}
$sortOrder = 20
foreach ($definition in $fieldDefinitions) {
    $body = @{
        dictionaryId = if ($definition.dictionaryId) { $definition.dictionaryId } else { $null }
        targetModuleId = $null
        code = $definition.code; name = $definition.name; type = $definition.type; sortOrder = $sortOrder
        required = $true; hidden = $false; readonly = $false; searchable = $false; filterable = $false
        showInList = $definition.type -in @('PHONE','EMAIL','BARCODE','STATUS')
        showInDetail = $true; indexMode = 'NONE'; status = 'ENABLED'; properties = $definition.properties
    }
    $field = Add-ConfigResource -Path "$configRoot/modules/$($fixture.moduleId)/fields" -Body $body
    $fieldIds[$definition.code] = [string]$field.id
    $sortOrder++
}

$check = Invoke-Envelope -Session $root -Path "$configRoot/checks" -Method POST -Body @{
    draftRevision = [string]$revision
}
Assert-True ([int]$check.blockerCount -eq 0) 'P4-C2 configuration check has blockers'
$rootState = Invoke-Envelope -Session $root -Path $configRoot
$published = Invoke-Envelope -Session $root -Path "${configRoot}:publish" -Method POST -Idempotent -Body @{
    checkId = [string]$check.id
    draftRevision = [string]$revision
    configRootVersion = [string]$rootState.version
    reason = 'P4-C2 secure structured field task-01 acceptance'
}
}

$fixture.schemaVersionId = [string]$published.version.id
$fixture | Add-Member -NotePropertyName p4C2FieldIds -NotePropertyValue ([pscustomobject]$fieldIds) -Force
$fixture | Add-Member -NotePropertyName statusItemIds -NotePropertyValue ([pscustomobject]@{
    new = [string]$statusNew.id; active = [string]$statusActive.id; done = [string]$statusDone.id
}) -Force
Write-Utf8Json -TargetPath $FixturePath -Value $fixture
if (Test-Path -LiteralPath $baseFixture) { Remove-Item -LiteralPath $baseFixture }

$ordinary = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $ordinary -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$fixture.username; password = [string]$fixture.password
}
$null = Invoke-Envelope -Session $ordinary -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
    -Method POST -Body @{}
$runtimeRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/$($fixture.moduleCode)"
$schema = Invoke-Envelope -Session $ordinary -Path "$runtimeRoot/record-schema"
$actualTypes = @($schema.fields | Where-Object { $_.type -in $expectedTypes } | ForEach-Object { [string]$_.type })
Assert-True (($actualTypes -join ',') -eq ($expectedTypes -join ',')) `
    "Unexpected P4-C2 schema types: $($actualTypes -join ',')"

$values = [ordered]@{
    subject = 'P4-C2 secure storage'
    contact_phone = @('138 1234 5678', '+1 202-555-0123')
    contact_email = @('Owner@EXAMPLE.COM')
    reference_url = 'HTTPS://EXAMPLE.COM'
    identity_number = '110101199001011237'
    service_address = @{ countryCode='cn'; display='Beijing Chaoyang'; city='Beijing'; detail='Road 1' }
    service_geo = @{ lat=39.9000; lng=116.4000 }
    asset_barcode = @{ symbology='EAN13'; payload='4006381333931' }
    rich_notes = '<p>Hello</p><script>hidden()</script><a href="javascript:alert(1)">unsafe</a>'
    metadata_json = @{ ticket='P4-C2'; count=2 }
    private_secret = '  keep-spaces  '
    workflow_status = [string]$statusNew.id
}
$recordsPath = "$runtimeRoot/records"
$created = Invoke-Envelope -Session $ordinary -Path $recordsPath -Method POST -Idempotent -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    title = 'P4-C2 task-01 acceptance'
    values = $values
}
$activated = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$($created.recordId):activate" `
    -Method POST -Idempotent -Body @{ expectedVersion = [long]$created.version }

$valueMap = @{}
foreach ($entry in $activated.values) { $valueMap[[string]$entry.fieldCode] = $entry.value }
Assert-True (($valueMap.contact_phone -join ',') -eq '+8613812345678,+12025550123') 'PHONE mismatch'
Assert-True (($valueMap.contact_email -join ',') -eq 'Owner@example.com') 'EMAIL mismatch'
Assert-True ($valueMap.reference_url -eq 'https://example.com/') 'URL mismatch'
Assert-True ($null -eq $valueMap.identity_number -and $null -eq $valueMap.private_secret) `
    'Sensitive plaintext leaked in API response'
Assert-True ($valueMap.service_address.countryCode -eq 'CN') 'ADDRESS mismatch'
Assert-True ([decimal]$valueMap.service_geo.lat -eq [decimal]39.9) 'GEO mismatch'
Assert-True ($valueMap.asset_barcode.payload -eq '4006381333931') 'BARCODE mismatch'
Assert-True ($valueMap.rich_notes -notmatch '(?i)<script|javascript:') 'RICH_TEXT sanitizer mismatch'
Assert-True ($valueMap.metadata_json.ticket -eq 'P4-C2') 'JSON mismatch'
Assert-True ([string]$valueMap.workflow_status -eq [string]$statusNew.id) 'STATUS mismatch'

$sensitiveBeforeRows = @(Invoke-DatabaseRows @"
SELECT GROUP_CONCAT(CONCAT(field_type, ':', value_hash, ':', SHA2(encrypted_value,256))
                    ORDER BY field_type SEPARATOR '|')
FROM un_module_record_value
WHERE record_id=$($created.recordId) AND field_type IN ('IDENTITY','SECRET');
"@)
$updateValues = [ordered]@{}
foreach ($entry in $values.GetEnumerator()) {
    if ($entry.Key -notin @('identity_number','private_secret')) { $updateValues[$entry.Key] = $entry.Value }
}
$updateValues.workflow_status = [string]$statusActive.id
$updated = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$($created.recordId)" -Method PUT -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    expectedVersion = [long]$activated.version
    title = 'P4-C2 task-01 updated'
    values = $updateValues
}
$updatedStatus = @($updated.values | Where-Object fieldCode -eq 'workflow_status')[0]
Assert-True ([string]$updatedStatus.value -eq [string]$statusActive.id) 'STATUS legal transition failed'
$sensitiveAfterRows = @(Invoke-DatabaseRows @"
SELECT GROUP_CONCAT(CONCAT(field_type, ':', value_hash, ':', SHA2(encrypted_value,256))
                    ORDER BY field_type SEPARATOR '|')
FROM un_module_record_value
WHERE record_id=$($created.recordId) AND field_type IN ('IDENTITY','SECRET');
"@)
Assert-True ($sensitiveBeforeRows[0] -eq $sensitiveAfterRows[0]) `
    'Omitted sensitive values were cleared or replaced during update'

$recordsBeforeRows = @(Invoke-DatabaseRows `
    "SELECT COUNT(*) FROM un_module_record WHERE system_id=$($fixture.systemId)")
$recordsBefore = [long]$recordsBeforeRows[0]
$invalidValues = [ordered]@{} + $values
$invalidValues.identity_number = '110101199001011234'
$invalid = Invoke-ExpectedFieldError -Session $ordinary -Path $recordsPath -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    title = 'P4-C2 invalid identity must fail'
    values = $invalidValues
}
$recordsAfterRows = @(Invoke-DatabaseRows `
    "SELECT COUNT(*) FROM un_module_record WHERE system_id=$($fixture.systemId)")
$recordsAfter = [long]$recordsAfterRows[0]
Assert-True ($recordsBefore -eq $recordsAfter) 'Invalid P4-C2 request left a partial record'

$databaseRows = @(Invoke-DatabaseRows @"
SELECT COUNT(*),
       SUM(string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
           AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
           AND boolean_value IS NULL AND reference_value IS NULL),
       SUM(encrypted_value IS NOT NULL AND OCTET_LENGTH(encrypted_value) >= 30
           AND value_hash IS NOT NULL AND LENGTH(value_hash)=64
           AND encryption_key_version='enc-v1' AND hash_key_version='hash-v1'),
       SUM(LOCATE(_binary'110101199001011237', encrypted_value)=0
           AND LOCATE(_binary'keep-spaces', encrypted_value)=0)
FROM un_module_record_value
WHERE record_id=$($created.recordId) AND field_type IN ('IDENTITY','SECRET');
"@)
$databaseLine = $databaseRows[0] -split "`t"
Assert-True ([int]$databaseLine[0] -eq 2) 'Sensitive row count mismatch'
Assert-True ([int]$databaseLine[1] -eq 2) 'Sensitive plaintext columns are populated'
Assert-True ([int]$databaseLine[2] -eq 2) 'Sensitive envelope/hash/version columns mismatch'
Assert-True ([int]$databaseLine[3] -eq 2) 'Sensitive ciphertext contains plaintext probe'

$auditRows = @(Invoke-DatabaseRows @"
SELECT COUNT(*) FROM un_audit_operation
WHERE system_id=$($fixture.systemId)
  AND (CAST(before_json AS CHAR) LIKE '%110101199001011237%'
       OR CAST(after_json AS CHAR) LIKE '%110101199001011237%'
       OR CAST(before_json AS CHAR) LIKE '%keep-spaces%'
       OR CAST(after_json AS CHAR) LIKE '%keep-spaces%');
"@)
$auditLeaks = [long]$auditRows[0]
Assert-True ($auditLeaks -eq 0) 'Sensitive plaintext leaked into operation audit'

$detail = Invoke-Envelope -Session $ordinary -Path "$recordsPath/$($created.recordId)"
$valueRowResults = @(Invoke-DatabaseRows `
    "SELECT COUNT(*) FROM un_module_record_value WHERE record_id=$($created.recordId)")
$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    systemId = [string]$fixture.systemId
    schemaVersionId = [string]$fixture.schemaVersionId
    recordId = [string]$created.recordId
    recordStatus = [string]$detail.status
    workflowStatus = [string]$updatedStatus.value
    p4C2Types = $actualTypes
    valueRows = [long]$valueRowResults[0]
    sensitiveRows = [int]$databaseLine[0]
    plaintextSensitiveColumns = 0
    sensitiveOmissionPreserved = $true
    auditPlaintextLeaks = $auditLeaks
    invalidIdentityCode = [string]$invalid.code
    health = [string](Invoke-RestMethod -Uri "$BaseUrl/management/health").status
}
Write-Utf8Json -TargetPath $ResultPath -Value $result
$result | ConvertTo-Json -Depth 10
