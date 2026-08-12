param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$RegistrationPath = "$PSScriptRoot/owner-registration.json",
    [string]$FixturePath = "$PSScriptRoot/fixture.json",
    [string]$ResultPath = "$PSScriptRoot/task-01-runtime.json",
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
    $parent = Split-Path -Parent $TargetPath
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 40), [Text.UTF8Encoding]::new($false))
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
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $Session
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
        [object]$Body,
        [string]$ExpectedCode
    )
    $headers = @{
        'X-Request-ID' = [guid]::NewGuid().ToString()
        'Idempotency-Key' = [guid]::NewGuid().ToString()
    }
    $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
    if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    try {
        $null = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method POST -Headers $headers -WebSession $Session `
            -ContentType 'application/json; charset=utf-8' -Body ($Body | ConvertTo-Json -Depth 40 -Compress)
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
        if ($status -ne 422 -or -not $payload -or $payload.code -ne $ExpectedCode) {
            throw "Expected $ExpectedCode/422 but received $status/$($payload.code): $errorBody"
        }
        return $payload
    }
    throw "Expected $ExpectedCode but request succeeded"
}

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw "Database query failed: $Query" }
    return @($output)
}

Assert-True (Test-Path -LiteralPath $RegistrationPath) "Registration fixture is missing: $RegistrationPath"
$registration = Get-Content -LiteralPath $RegistrationPath -Raw | ConvertFrom-Json
$systemId = [string]$registration.data.firstSystemId
$owner = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $owner -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$registration.username
    password = [string]$registration.password
}
$context = Invoke-Envelope -Session $owner -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}
Assert-True ([string]$context.context.systemId -eq $systemId) 'Owner could not enter the dedicated system context'

$configRoot = "/api/v1/systems/$systemId/admin/config"
$rootState = Invoke-Envelope -Session $owner -Path $configRoot
$revision = [long]$rootState.draftRevision
Assert-True (($revision -eq 0 -and -not $rootState.activeVersionId) `
        -or ($rootState.status -eq 'CLEAN' -and $rootState.activeVersionId)) `
    'Dedicated P4-C3 configuration is partially modified'

function Add-ConfigResource {
    param([string]$Path, [hashtable]$Body)
    $Body.draftRevision = [string]$script:revision
    $result = Invoke-Envelope -Session $owner -Path $Path -Method POST -Idempotent -Body $Body
    $script:revision++
    return $result
}

function Add-ModuleField {
    param(
        [string]$ModuleId,
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [int]$SortOrder,
        [bool]$Required,
        [bool]$Readonly,
        [object]$Properties,
        [string]$TargetModuleId = $null
    )
    return Add-ConfigResource -Path "$configRoot/modules/$ModuleId/fields" -Body @{
        dictionaryId = $null
        targetModuleId = $TargetModuleId
        code = $Code
        name = $Name
        type = $Type
        sortOrder = $SortOrder
        required = $Required
        hidden = $false
        readonly = $Readonly
        searchable = $false
        filterable = $false
        showInList = $true
        showInDetail = $true
        indexMode = 'NONE'
        status = 'ENABLED'
        properties = $Properties
    }
}

$configurationAlreadyPublished = $null -ne $rootState.activeVersionId
if ($configurationAlreadyPublished) {
    $modules = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules")
    $customerModule = @($modules | Where-Object code -eq 'customer')[0]
    $lineModule = @($modules | Where-Object code -eq 'line_template')[0]
    $orderModule = @($modules | Where-Object code -eq 'work_order')[0]
    Assert-True ($customerModule -and $lineModule -and $orderModule) 'Published P4-C3 modules are incomplete'
    $customerFields = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules/$($customerModule.id)/fields")
    $lineFields = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules/$($lineModule.id)/fields")
    $orderFields = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules/$($orderModule.id)/fields")
    $customerName = @($customerFields | Where-Object code -eq 'customer_name')[0]
    $lineDescription = @($lineFields | Where-Object code -eq 'line_description')[0]
    $lineQuantity = @($lineFields | Where-Object code -eq 'quantity')[0]
    $lineAmount = @($lineFields | Where-Object code -eq 'line_amount')[0]
    $orderSubject = @($orderFields | Where-Object code -eq 'subject')[0]
    $customerRelation = @($orderFields | Where-Object code -eq 'customer')[0]
    $customerReference = @($orderFields | Where-Object code -eq 'customer_name_ref')[0]
    $orderLines = @($orderFields | Where-Object code -eq 'order_lines')[0]
    Assert-True ($customerName -and $lineDescription -and $lineQuantity -and $lineAmount `
            -and $orderSubject -and $customerRelation -and $customerReference -and $orderLines) `
        'Published P4-C3 fields are incomplete'
    $schemaVersionId = [string]$rootState.activeVersionId
} else {
$group = Add-ConfigResource -Path "$configRoot/module-groups" -Body @{
    code = 'operations'
    name = 'Operations'
    description = 'P4-C3 relation and subtable acceptance'
    iconKey = 'folder'
    sortOrder = 0
    status = 'ENABLED'
}
$customerModule = Add-ConfigResource -Path "$configRoot/modules" -Body @{
    groupId = [string]$group.id
    code = 'customer'
    name = 'Customer'
    description = 'Relation target module'
    iconKey = 'users'
    sortOrder = 0
    status = 'ENABLED'
    allowComments = $false
    allowTeam = $false
}
$lineModule = Add-ConfigResource -Path "$configRoot/modules" -Body @{
    groupId = [string]$group.id
    code = 'line_template'
    name = 'Order line template'
    description = 'Subtable row schema source'
    iconKey = 'list'
    sortOrder = 1
    status = 'ENABLED'
    allowComments = $false
    allowTeam = $false
}
$orderModule = Add-ConfigResource -Path "$configRoot/modules" -Body @{
    groupId = [string]$group.id
    code = 'work_order'
    name = 'Work order'
    description = 'P4-C3 aggregate root'
    iconKey = 'clipboard'
    sortOrder = 2
    status = 'ENABLED'
    allowComments = $false
    allowTeam = $false
}

$customerName = Add-ModuleField -ModuleId ([string]$customerModule.id) -Code 'customer_name' `
    -Name 'Customer name' -Type 'TEXT' -SortOrder 0 -Required $true -Readonly $false `
    -Properties @{ maxLength = 160 }
$lineDescription = Add-ModuleField -ModuleId ([string]$lineModule.id) -Code 'line_description' `
    -Name 'Description' -Type 'TEXT' -SortOrder 0 -Required $true -Readonly $false `
    -Properties @{ maxLength = 200 }
$lineQuantity = Add-ModuleField -ModuleId ([string]$lineModule.id) -Code 'quantity' `
    -Name 'Quantity' -Type 'NUMBER' -SortOrder 1 -Required $true -Readonly $false `
    -Properties @{ precision = 12; scale = 2; minimum = 0 }
$lineAmount = Add-ModuleField -ModuleId ([string]$lineModule.id) -Code 'line_amount' `
    -Name 'Line amount' -Type 'MONEY' -SortOrder 2 -Required $true -Readonly $false `
    -Properties @{ currencies = @('CNY'); fixedCurrency = 'CNY' }
$orderSubject = Add-ModuleField -ModuleId ([string]$orderModule.id) -Code 'subject' `
    -Name 'Subject' -Type 'TEXT' -SortOrder 0 -Required $true -Readonly $false `
    -Properties @{ maxLength = 200 }
$customerRelation = Add-ModuleField -ModuleId ([string]$orderModule.id) -Code 'customer' `
    -Name 'Customer' -Type 'RELATION' -SortOrder 1 -Required $true -Readonly $false `
    -TargetModuleId ([string]$customerModule.id) -Properties @{
        multiple = $false
        displayFieldId = [string]$customerName.id
        allowCreate = $false
        reverseRelation = $true
    }
$customerReference = Add-ModuleField -ModuleId ([string]$orderModule.id) -Code 'customer_name_ref' `
    -Name 'Customer name reference' -Type 'REFERENCE' -SortOrder 2 -Required $false -Readonly $true `
    -Properties @{
        sourceFieldId = [string]$customerRelation.id
        targetFieldId = [string]$customerName.id
    }
$orderLines = Add-ModuleField -ModuleId ([string]$orderModule.id) -Code 'order_lines' `
    -Name 'Order lines' -Type 'SUBTABLE' -SortOrder 3 -Required $true -Readonly $false `
    -TargetModuleId ([string]$lineModule.id) -Properties @{
        minRows = 1
        maxRows = 20
        columnFieldIds = @([string]$lineDescription.id, [string]$lineQuantity.id, [string]$lineAmount.id)
        allowRowCreate = $true
        allowRowUpdate = $true
        allowRowDelete = $true
        allowRowReorder = $true
        aggregates = @(@{ id = 'total_amount'; function = 'SUM'; columnFieldId = [string]$lineAmount.id })
    }

$check = Invoke-Envelope -Session $owner -Path "$configRoot/checks" -Method POST -Body @{
    draftRevision = [string]$revision
}
Assert-True ([int]$check.blockerCount -eq 0) "P4-C3 publication check has $($check.blockerCount) blockers"
$rootState = Invoke-Envelope -Session $owner -Path $configRoot
$published = Invoke-Envelope -Session $owner -Path "${configRoot}:publish" -Method POST -Idempotent -Body @{
    checkId = [string]$check.id
    draftRevision = [string]$revision
    configRootVersion = [string]$rootState.version
    reason = 'P4-C3 task-01 relation and subtable persistence acceptance'
}
$schemaVersionId = [string]$published.version.id
}

$runtime = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $runtime -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$registration.username
    password = [string]$registration.password
}
$null = Invoke-Envelope -Session $runtime -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}
$orderRuntime = "/api/v1/systems/$systemId/runtime/modules/work_order"
$customerRuntime = "/api/v1/systems/$systemId/runtime/modules/customer"
$lineRuntime = "/api/v1/systems/$systemId/runtime/modules/line_template"
$schema = Invoke-Envelope -Session $runtime -Path "$orderRuntime/record-schema"
$p4Types = @($schema.fields | Where-Object { $_.type -in @('RELATION', 'REFERENCE', 'SUBTABLE') } | ForEach-Object type)
Assert-True (($p4Types -join ',') -eq 'RELATION,REFERENCE,SUBTABLE') "Unexpected P4-C3 schema types: $($p4Types -join ',')"

$customer = Invoke-Envelope -Session $runtime -Path "$customerRuntime/records" -Method POST -Idempotent -Body @{
    schemaVersionId = $schemaVersionId
    title = 'Acme Customer'
    values = @{ customer_name = 'Acme Customer' }
    relations = @()
    subtables = @()
}
$customer = Invoke-Envelope -Session $runtime -Path "$customerRuntime/records/$($customer.recordId):activate" `
    -Method POST -Idempotent -Body @{ expectedVersion = [long]$customer.version }
$lineTarget = Invoke-Envelope -Session $runtime -Path "$lineRuntime/records" -Method POST -Idempotent -Body @{
    schemaVersionId = $schemaVersionId
    title = 'Wrong relation target'
    values = @{
        line_description = 'Wrong relation target'
        quantity = 1
        line_amount = @{ amount = '1.00'; currency = 'CNY' }
    }
    relations = @()
    subtables = @()
}
$lineTarget = Invoke-Envelope -Session $runtime -Path "$lineRuntime/records/$($lineTarget.recordId):activate" `
    -Method POST -Idempotent -Body @{ expectedVersion = [long]$lineTarget.version }

$recordsBefore = [long]@(Invoke-DatabaseRows "SELECT COUNT(*) FROM un_module_record WHERE system_id=$systemId")[0]
$wrongRelation = Invoke-ExpectedError -Session $runtime -Path "$orderRuntime/records" `
    -ExpectedCode 'RECORD_RELATION_INVALID' -Body @{
    schemaVersionId = $schemaVersionId
    title = 'Wrong relation must rollback'
    values = @{ subject = 'Wrong relation must rollback' }
    relations = @(@{
        fieldCode = 'customer'
        targets = @(@{
            targetRecordId = [string]$lineTarget.recordId
            targetExpectedVersion = [long]$lineTarget.version
            ordinal = 0
        })
    })
    subtables = @(@{
        fieldCode = 'order_lines'
        rows = @(@{
            clientRowKey = 'wrong-relation-row'
            rowId = $null
            expectedVersion = $null
            ordinal = 0
            values = @{
                line_description = 'Should rollback'
                quantity = 1
                line_amount = @{ amount = '1.00'; currency = 'CNY' }
            }
        })
    })
}
$recordsAfterWrongRelation = [long]@(Invoke-DatabaseRows "SELECT COUNT(*) FROM un_module_record WHERE system_id=$systemId")[0]
Assert-True ($recordsBefore -eq $recordsAfterWrongRelation) 'Wrong relation left a partial parent record'

$wrongRow = Invoke-ExpectedError -Session $runtime -Path "$orderRuntime/records" `
    -ExpectedCode 'SUBTABLE_ROW_INVALID' -Body @{
    schemaVersionId = $schemaVersionId
    title = 'Wrong row must rollback'
    values = @{ subject = 'Wrong row must rollback' }
    relations = @(@{
        fieldCode = 'customer'
        targets = @(@{
            targetRecordId = [string]$customer.recordId
            targetExpectedVersion = [long]$customer.version
            ordinal = 0
        })
    })
    subtables = @(@{
        fieldCode = 'order_lines'
        rows = @(@{
            clientRowKey = 'missing-required-cell'
            rowId = $null
            expectedVersion = $null
            ordinal = 0
            values = @{
                line_description = 'Missing quantity'
                line_amount = @{ amount = '2.00'; currency = 'CNY' }
            }
        })
    })
}
$recordsAfterWrongRow = [long]@(Invoke-DatabaseRows "SELECT COUNT(*) FROM un_module_record WHERE system_id=$systemId")[0]
Assert-True ($recordsBefore -eq $recordsAfterWrongRow) 'Invalid subtable row left a partial parent record'

$order = Invoke-Envelope -Session $runtime -Path "$orderRuntime/records" -Method POST -Idempotent -Body @{
    schemaVersionId = $schemaVersionId
    title = 'P4-C3 atomic work order'
    values = @{ subject = 'P4-C3 atomic work order' }
    relations = @(@{
        fieldCode = 'customer'
        targets = @(@{
            targetRecordId = [string]$customer.recordId
            targetExpectedVersion = [long]$customer.version
            ordinal = 0
        })
    })
    subtables = @(@{
        fieldCode = 'order_lines'
        rows = @(
            @{
                clientRowKey = 'line-1'
                rowId = $null
                expectedVersion = $null
                ordinal = 0
                values = @{
                    line_description = 'Inspection service'
                    quantity = 2
                    line_amount = @{ amount = '12.50'; currency = 'CNY' }
                }
            },
            @{
                clientRowKey = 'line-2'
                rowId = $null
                expectedVersion = $null
                ordinal = 1
                values = @{
                    line_description = 'Replacement part'
                    quantity = 1
                    line_amount = @{ amount = '30.00'; currency = 'CNY' }
                }
            }
        )
    })
}
$order = Invoke-Envelope -Session $runtime -Path "$orderRuntime/records/$($order.recordId):activate" `
    -Method POST -Idempotent -Body @{ expectedVersion = [long]$order.version }

$counts = @(Invoke-DatabaseRows @"
SELECT CONCAT(
  (SELECT COUNT(*) FROM un_module_record_relation WHERE system_id=$systemId AND source_record_id=$($order.recordId)), ':',
  (SELECT COUNT(*) FROM un_module_sub_record WHERE system_id=$systemId AND parent_record_id=$($order.recordId)), ':',
  (SELECT COUNT(*) FROM un_module_sub_value WHERE system_id=$systemId AND parent_record_id=$($order.recordId))
);
"@)[0] -split ':'
Assert-True ([long]$counts[0] -eq 1) 'Expected one persisted relation edge'
Assert-True ([long]$counts[1] -eq 2) 'Expected two persisted subtable rows'
Assert-True ([long]$counts[2] -eq 6) 'Expected six persisted typed subtable values'

$typedValues = @(Invoke-DatabaseRows @"
SELECT GROUP_CONCAT(CONCAT(column_field_type, '=', COALESCE(string_value, decimal_value, currency_code))
  ORDER BY row_id, column_field_snapshot_id SEPARATOR '|')
FROM un_module_sub_value
WHERE system_id=$systemId AND parent_record_id=$($order.recordId);
"@)[0]
Assert-True ($typedValues -match 'TEXT=' -and $typedValues -match 'NUMBER=' -and $typedValues -match 'MONEY=') `
    "Typed subtable payload is incomplete: $typedValues"

$fixture = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $systemId
    tenantId = [string]$context.context.tenantId
    username = [string]$registration.username
    password = [string]$registration.password
    schemaVersionId = $schemaVersionId
    modules = [ordered]@{
        customer = [string]$customerModule.id
        lineTemplate = [string]$lineModule.id
        workOrder = [string]$orderModule.id
    }
    fields = [ordered]@{
        customerName = [string]$customerName.id
        lineDescription = [string]$lineDescription.id
        lineQuantity = [string]$lineQuantity.id
        lineAmount = [string]$lineAmount.id
        subject = [string]$orderSubject.id
        customerRelation = [string]$customerRelation.id
        customerReference = [string]$customerReference.id
        orderLines = [string]$orderLines.id
    }
    records = [ordered]@{
        customer = [string]$customer.recordId
        wrongTarget = [string]$lineTarget.recordId
        workOrder = [string]$order.recordId
    }
}
Write-Utf8Json -TargetPath $FixturePath -Value $fixture

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    systemId = $systemId
    schemaVersionId = $schemaVersionId
    workOrderRecordId = [string]$order.recordId
    relationRows = [long]$counts[0]
    subtableRows = [long]$counts[1]
    subtableValueRows = [long]$counts[2]
    typedValueFamilies = @('TEXT', 'NUMBER', 'MONEY')
    wrongRelationCode = [string]$wrongRelation.code
    wrongRowCode = [string]$wrongRow.code
    rollbackRecordCountStable = $recordsBefore -eq $recordsAfterWrongRelation -and $recordsBefore -eq $recordsAfterWrongRow
    backendPid = [long](Get-NetTCPConnection -LocalPort 18080 -State Listen | Select-Object -First 1 -ExpandProperty OwningProcess)
}
Write-Utf8Json -TargetPath $ResultPath -Value $result
$result | ConvertTo-Json -Depth 20
