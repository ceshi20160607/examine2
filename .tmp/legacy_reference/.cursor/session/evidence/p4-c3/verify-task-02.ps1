param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$FixturePath = "$PSScriptRoot/fixture.json",
    [string]$MembersPath = "$PSScriptRoot/task-02-members.json",
    [string]$ResultPath = "$PSScriptRoot/task-02-runtime.json",
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

function Write-Utf8Json([string]$TargetPath, [object]$Value) {
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 40), [Text.UTF8Encoding]::new($false))
}

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body,
        [string]$IdempotencyKey
    )
    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($IdempotencyKey) { $headers['Idempotency-Key'] = $IdempotencyKey }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    $arguments = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $Session
        TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 40 -Compress
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}

function Invoke-ExpectedError {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method,
        [object]$Body,
        [string]$ExpectedCode,
        [int]$ExpectedStatus,
        [string]$IdempotencyKey = ([guid]::NewGuid().ToString())
    )
    $headers = @{
        'X-Request-ID' = [guid]::NewGuid().ToString()
        'Idempotency-Key' = $IdempotencyKey
    }
    $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    try {
        $arguments = @{
            Uri = "$BaseUrl$Path"
            Method = $Method
            Headers = $headers
            WebSession = $Session
            TimeoutSec = 20
        }
        if ($null -ne $Body) {
            $arguments.ContentType = 'application/json; charset=utf-8'
            $arguments.Body = $Body | ConvertTo-Json -Depth 40 -Compress
        }
        $null = Invoke-RestMethod @arguments
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
        if ($status -ne $ExpectedStatus -or -not $payload -or $payload.code -ne $ExpectedCode) {
            throw "Expected $ExpectedCode/$ExpectedStatus but received $status/$($payload.code): $errorBody"
        }
        return $payload
    }
    throw "Expected $ExpectedCode but request succeeded"
}

function New-Session([object]$Member) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
        account = [string]$Member.username
        password = [string]$Member.password
    }
    $null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
        -Method POST -Body @{}
    return $session
}

function Invoke-Query {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [object]$Filter,
        [object[]]$Sort = @()
    )
    return Invoke-Envelope -Session $Session -Path "$workOrderRoot/records:query" -Method POST -Body @{
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
Assert-True ($health.status -eq 'UP') 'Backend is not healthy'

$workOrderRoot = "/api/v1/systems/$($fixture.systemId)/runtime/modules/work_order"
$recordPath = "$workOrderRoot/records/$($fixture.records.workOrder)"
$customerPath = "/api/v1/systems/$($fixture.systemId)/runtime/modules/customer/records/$($fixture.records.customer)"
$positive = New-Session $members.positive
$restricted = New-Session $members.restricted

$schema = Invoke-Envelope -Session $positive -Path "$workOrderRoot/record-schema"
$relationField = @($schema.fields | Where-Object fieldCode -eq 'customer')[0]
$referenceField = @($schema.fields | Where-Object fieldCode -eq 'customer_name_ref')[0]
$subtableField = @($schema.fields | Where-Object fieldCode -eq 'order_lines')[0]
Assert-True (($relationField.operators -join ',') -eq 'HAS_ANY,EMPTY') 'RELATION operator vector is not exact'
Assert-True (($referenceField.operators -join ',') -eq 'EQ,CONTAINS,PREFIX,EMPTY') `
    'REFERENCE operator vector is not exact'
Assert-True ([bool]$referenceField.sortable) 'REFERENCE result is not sortable'
Assert-True (($subtableField.operators -join ',') -eq 'DECLARED_AGGREGATE') `
    'SUBTABLE operator vector is not exact'

$detail = Invoke-Envelope -Session $positive -Path $recordPath
$customer = Invoke-Envelope -Session $positive -Path $customerPath
$relation = Invoke-Envelope -Session $positive -Path "$recordPath/relations/customer"
$subtable = Invoke-Envelope -Session $positive -Path "$recordPath/subtables/order_lines"
$referenceValue = @($detail.values | Where-Object fieldCode -eq 'customer_name_ref')[0]
Assert-True ([long]$relation.total -eq 1 -and [string]$relation.items[0].title -eq [string]$customer.title) `
    'Relation readback did not return the scoped target title'
Assert-True ([long]$subtable.total -eq 2 -and @($subtable.items).Count -eq 2) `
    'Subtable readback did not return two rows'
Assert-True ($referenceValue.value.recalculationState -eq 'READY' `
        -and [string]$referenceValue.value.result -eq [string]$customer.title) `
    'Reference readback is not current and READY'

$relationBody = @{
    expectedVersion = [long]$detail.version
    add = @(@{
        targetRecordId = [string]$fixture.records.customer
        targetExpectedVersion = [long]$customer.version
        ordinal = 0
    })
    remove = @([string]$fixture.records.customer)
    order = @([string]$fixture.records.customer)
}
$relationKey = [guid]::NewGuid().ToString()
$relationMutation = Invoke-Envelope -Session $positive -Path "$recordPath/relations/customer:mutate" `
    -Method POST -IdempotencyKey $relationKey -Body $relationBody
$relationReplay = Invoke-Envelope -Session $positive -Path "$recordPath/relations/customer:mutate" `
    -Method POST -IdempotencyKey $relationKey -Body $relationBody
Assert-True ([long]$relationMutation.version -eq ([long]$detail.version + 1)) `
    'Relation mutation did not increment the parent exactly once'
Assert-True ([string]$relationMutation.historyId -eq [string]$relationReplay.historyId `
        -and [long]$relationMutation.version -eq [long]$relationReplay.version) `
    'Relation idempotency replay was not stable'

$staleParent = Invoke-ExpectedError -Session $positive -Path "$recordPath/relations/customer:mutate" `
    -Method POST -Body $relationBody -ExpectedCode 'RECORD_VERSION_CONFLICT' -ExpectedStatus 409
$duplicateBody = @{
    expectedVersion = [long]$relationMutation.version
    add = @(@{
        targetRecordId = [string]$fixture.records.customer
        targetExpectedVersion = [long]$customer.version
        ordinal = 0
    })
    remove = @()
    order = @([string]$fixture.records.customer)
}
$duplicateTarget = Invoke-ExpectedError -Session $positive -Path "$recordPath/relations/customer:mutate" `
    -Method POST -Body $duplicateBody -ExpectedCode 'RECORD_RELATION_INVALID' -ExpectedStatus 422

$subtableBefore = Invoke-Envelope -Session $positive -Path "$recordPath/subtables/order_lines"
$firstRow = @($subtableBefore.items | Sort-Object ordinal)[0]
$secondRow = @($subtableBefore.items | Sort-Object ordinal)[1]
$description = 'Verified round trip ' + (Get-Date -Format 'yyyyMMddHHmmss')
$rowUpdate = @{
    rowId = [string]$firstRow.rowId
    expectedVersion = [long]$firstRow.version
    ordinal = 0
    values = @{
        line_description = $description
        quantity = @($firstRow.values | Where-Object fieldCode -eq 'quantity')[0].value
        line_amount = @($firstRow.values | Where-Object fieldCode -eq 'line_amount')[0].value
    }
}
$subtableBody = @{
    expectedVersion = [long]$relationMutation.version
    add = @()
    update = @($rowUpdate)
    remove = @()
    order = @([string]$firstRow.rowId, [string]$secondRow.rowId)
}
$subtableMutation = Invoke-Envelope -Session $positive -Path "$recordPath/subtables/order_lines:mutate" `
    -Method POST -IdempotencyKey ([guid]::NewGuid().ToString()) -Body $subtableBody
Assert-True ([long]$subtableMutation.version -eq ([long]$relationMutation.version + 1)) `
    'Subtable mutation did not increment the parent exactly once'
$subtableAfter = Invoke-Envelope -Session $positive -Path "$recordPath/subtables/order_lines"
$updatedRow = @($subtableAfter.items | Where-Object rowId -eq $firstRow.rowId)[0]
Assert-True ([string]@($updatedRow.values | Where-Object fieldCode -eq 'line_description')[0].value -eq $description) `
    'Subtable server readback did not preserve the updated text'
Assert-True ([string]@($updatedRow.values | Where-Object fieldCode -eq 'line_amount')[0].value.amount -eq '20.00') `
    'Subtable MONEY readback lost its declared scale'

$staleRowBody = @{
    expectedVersion = [long]$subtableMutation.version
    add = @()
    update = @($rowUpdate)
    remove = @()
    order = @([string]$firstRow.rowId, [string]$secondRow.rowId)
}
$staleRow = Invoke-ExpectedError -Session $positive -Path "$recordPath/subtables/order_lines:mutate" `
    -Method POST -Body $staleRowBody -ExpectedCode 'SUBTABLE_ROW_INVALID' -ExpectedStatus 422

$current = Invoke-Envelope -Session $positive -Path $recordPath
$subject = @($current.values | Where-Object fieldCode -eq 'subject')[0].value
$forgedReference = Invoke-ExpectedError -Session $positive -Path $recordPath -Method PUT -Body @{
    schemaVersionId = [string]$fixture.schemaVersionId
    title = [string]$current.title
    expectedVersion = [long]$current.version
    values = @{ subject = $subject; customer_name_ref = 'forged' }
    relations = @()
    subtables = @()
} -ExpectedCode 'FIELD_WRITE_FORBIDDEN' -ExpectedStatus 422
$afterNegatives = Invoke-Envelope -Session $positive -Path $recordPath
Assert-True ([long]$afterNegatives.version -eq [long]$current.version) `
    'A rejected mutation changed the parent version'

$relationQuery = Invoke-Query -Session $positive -Filter @{
    kind = 'PREDICATE'; fieldCode = 'customer'; operator = 'HAS_ANY'
    value = @([string]$fixture.records.customer)
}
$referenceQuery = Invoke-Query -Session $positive -Filter @{
    kind = 'PREDICATE'; fieldCode = 'customer_name_ref'; operator = 'EQ'; value = [string]$customer.title
} -Sort @(@{ fieldCode = 'customer_name_ref'; direction = 'ASC'; nulls = 'LAST' })
$aggregateQuery = Invoke-Query -Session $positive -Filter @{
    kind = 'PREDICATE'; fieldCode = 'order_lines'; operator = 'DECLARED_AGGREGATE'
    value = @{ aggregateId = 'total_amount'; operator = 'GT'; value = 20 }
}
Assert-True ([long]$relationQuery.total -eq 1 -and [long]$referenceQuery.total -eq 1 `
        -and [long]$aggregateQuery.total -eq 1) 'A declared server query route returned an unexpected total'

$restrictedSchema = Invoke-Envelope -Session $restricted -Path "$workOrderRoot/record-schema"
$restrictedDetail = Invoke-Envelope -Session $restricted -Path $recordPath
Assert-True (@($restrictedSchema.fields | Where-Object fieldCode -eq 'customer_name_ref').Count -eq 0) `
    'Restricted schema leaked the REFERENCE field'
Assert-True (@($restrictedDetail.values | Where-Object fieldCode -eq 'customer_name_ref').Count -eq 0) `
    'Restricted detail leaked the REFERENCE value'
$restrictedRelation = Invoke-ExpectedError -Session $restricted -Path "$recordPath/relations/customer" `
    -Method GET -Body $null -ExpectedCode 'PERMISSION_DENIED' -ExpectedStatus 403
$restrictedReferenceQuery = Invoke-ExpectedError -Session $restricted -Path "$workOrderRoot/records:query" `
    -Method POST -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId; page = 1; size = 50; recordScope = 'active'
        q = $null
        filter = @{ kind = 'PREDICATE'; fieldCode = 'customer_name_ref'; operator = 'EQ'; value = [string]$customer.title }
        sort = @(); columns = @(); viewId = $null
    } -ExpectedCode 'QUERY_FIELD_UNAVAILABLE' -ExpectedStatus 422

$beforeConcurrency = Invoke-Envelope -Session $positive -Path $recordPath
$targetNow = Invoke-Envelope -Session $positive -Path $customerPath
$concurrentBody = @{
    expectedVersion = [long]$beforeConcurrency.version
    add = @(@{ targetRecordId = [string]$fixture.records.customer; targetExpectedVersion = [long]$targetNow.version; ordinal = 0 })
    remove = @([string]$fixture.records.customer)
    order = @([string]$fixture.records.customer)
}
$jobScript = {
    param($Base, $SystemId, $Username, $Password, $Path, $BodyJson)
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    function Post($RequestPath, $Body, $IdempotencyKey) {
        $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
        if ($IdempotencyKey) { $headers['Idempotency-Key'] = $IdempotencyKey }
        $csrf = ($session.Cookies.GetCookies($Base) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
        Invoke-RestMethod -Uri "$Base$RequestPath" -Method POST -Headers $headers -WebSession $session `
            -ContentType 'application/json; charset=utf-8' -Body $Body -TimeoutSec 20
    }
    $null = Post '/api/v1/auth/login' (@{ account=$Username; password=$Password } | ConvertTo-Json -Compress) $null
    $null = Post "/api/v1/context/systems/${SystemId}:switch" '{}' $null
    try {
        $response = Post $Path $BodyJson ([guid]::NewGuid().ToString())
        [pscustomobject]@{ outcome='OK'; version=[long]$response.data.version }
    } catch {
        [pscustomobject]@{ outcome="HTTP$([int]$_.Exception.Response.StatusCode)"; version=$null }
    }
}
$concurrentPath = "$recordPath/relations/customer:mutate"
$bodyJson = $concurrentBody | ConvertTo-Json -Depth 30 -Compress
$jobs = @(
    Start-Job -ScriptBlock $jobScript -ArgumentList $BaseUrl,$fixture.systemId,$members.positive.username,$members.positive.password,$concurrentPath,$bodyJson
    Start-Job -ScriptBlock $jobScript -ArgumentList $BaseUrl,$fixture.systemId,$members.positive.username,$members.positive.password,$concurrentPath,$bodyJson
)
$null = $jobs | Wait-Job -Timeout 45
$concurrentResults = @($jobs | Receive-Job)
$jobs | Remove-Job -Force
Assert-True (@($concurrentResults | Where-Object outcome -eq 'OK').Count -eq 1 `
        -and @($concurrentResults | Where-Object outcome -eq 'HTTP409').Count -eq 1) `
    'Concurrent mutations did not produce exactly one success and one conflict'
$afterConcurrency = Invoke-Envelope -Session $positive -Path $recordPath
Assert-True ([long]$afterConcurrency.version -eq ([long]$beforeConcurrency.version + 1)) `
    'Concurrent mutations changed the parent more than once'

$databaseRows = @(Invoke-DatabaseRows @"
SELECT CONCAT(
  (SELECT COUNT(*) FROM un_module_record_relation WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND source_record_id=$($fixture.records.workOrder)), ':',
  (SELECT COUNT(*) FROM un_module_sub_record WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND parent_record_id=$($fixture.records.workOrder) AND status='ACTIVE'), ':',
  (SELECT COUNT(*) FROM un_module_sub_value WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND parent_record_id=$($fixture.records.workOrder)), ':',
  (SELECT COUNT(*) FROM un_module_record_index WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND record_id=$($fixture.records.workOrder) AND logical_field_id=$($fixture.fields.customerReference)), ':',
  (SELECT recalculation_state FROM un_module_reference_state WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId) AND record_id=$($fixture.records.workOrder) AND field_snapshot_id=$($fixture.fields.customerReference))
);
"@)
$databaseState = @([string]$databaseRows[0] -split ':')
Assert-True (($databaseState -join ':') -eq '1:2:6:1:READY') `
    "Database projection mismatch: $($databaseState -join ':')"

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    backendHealth = [string]$health.status
    schemaOperators = @{
        relation = @($relationField.operators)
        reference = @($referenceField.operators)
        subtable = @($subtableField.operators)
    }
    relationReadTotal = [long]$relation.total
    subtableReadTotal = [long]$subtable.total
    idempotentRelationVersion = [long]$relationMutation.version
    subtableRoundTripVersion = [long]$subtableMutation.version
    staleParentCode = [string]$staleParent.code
    duplicateTargetCode = [string]$duplicateTarget.code
    staleRowCode = [string]$staleRow.code
    forgedReferenceCode = [string]$forgedReference.code
    queryTotals = @{ relation=[long]$relationQuery.total; reference=[long]$referenceQuery.total; aggregate=[long]$aggregateQuery.total }
    restrictedReferenceFields = @($restrictedSchema.fields | Where-Object fieldCode -eq 'customer_name_ref').Count
    restrictedRelationCode = [string]$restrictedRelation.code
    restrictedReferenceQueryCode = [string]$restrictedReferenceQuery.code
    concurrencyOutcomes = @($concurrentResults | ForEach-Object outcome | Sort-Object)
    concurrencyFinalVersion = [long]$afterConcurrency.version
    database = @{
        relationRows = [long]$databaseState[0]
        subtableRows = [long]$databaseState[1]
        subtableValueRows = [long]$databaseState[2]
        referenceIndexRows = [long]$databaseState[3]
        referenceState = [string]$databaseState[4]
    }
}
Write-Utf8Json -TargetPath $ResultPath -Value $result
$result | ConvertTo-Json -Depth 40
