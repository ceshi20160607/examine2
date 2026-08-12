param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$RegistrationPath = "$PSScriptRoot/../p4-c3/owner-registration.json",
    [string]$BaseFixturePath = "$PSScriptRoot/../p4-c3/fixture.json",
    [string]$FixturePath = "$PSScriptRoot/task-02-restart-fixture.json"
)

$ErrorActionPreference = 'Stop'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Utf8Json([string]$TargetPath, [object]$Value) {
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
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
        Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; WebSession = $Session; TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 50 -Compress
    }
    try {
        $response = Invoke-RestMethod @arguments
    } catch {
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        throw "API $Method $Path failed ($status): $($_.ErrorDetails.Message)"
    }
    if ($response.code -ne 'OK') { throw "API $Method $Path returned $($response.code)" }
    return $response.data
}

function Add-Field {
    param(
        [string]$ModuleId, [string]$Code, [string]$Name, [string]$Type,
        [int]$SortOrder, [bool]$Readonly, [string]$IndexMode, [object]$Properties
    )
    $body = @{
        dictionaryId = $null; targetModuleId = $null; code = $Code; name = $Name; type = $Type
        sortOrder = $SortOrder; required = $false; hidden = $false; readonly = $Readonly
        searchable = $false; filterable = $true; showInList = $true; showInDetail = $true
        indexMode = $IndexMode; status = 'ENABLED'; properties = $Properties
        draftRevision = [string]$script:revision
    }
    $field = Invoke-Envelope -Session $owner -Path "$configRoot/modules/$ModuleId/fields" `
        -Method POST -Idempotent -Body $body
    $script:revision++
    return $field
}

function Wait-DerivedReady([string]$Path) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $detail = Invoke-Envelope -Session $owner -Path $Path
        $derived = @($detail.values | Where-Object fieldCode -in @(
            'derived_taxed', 'derived_total', 'derived_customer_count',
            'derived_customer_name', 'derived_line_total'))
        if ($derived.Count -eq 5 -and @($derived | Where-Object {
                $_.value.recalculationState -ne 'READY'
            }).Count -eq 0) {
            return $detail
        }
        Start-Sleep -Seconds 1
    }
    throw 'Derived results did not become READY within 30 seconds'
}

function Derived-Value([object]$Detail, [string]$Code) {
    return @($Detail.values | Where-Object fieldCode -eq $Code)[0].value
}

$health = Invoke-RestMethod -Uri "$BaseUrl/management/health" -TimeoutSec 10
Assert-True ($health.status -eq 'UP') 'Packaged backend is not healthy'
$registration = Get-Content -LiteralPath $RegistrationPath -Raw | ConvertFrom-Json
$baseFixture = Get-Content -LiteralPath $BaseFixturePath -Raw | ConvertFrom-Json
$systemId = [string]$registration.data.firstSystemId
$owner = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $owner -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$registration.username; password = [string]$registration.password
}
$null = Invoke-Envelope -Session $owner -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}

$configRoot = "/api/v1/systems/$systemId/admin/config"
$rootState = Invoke-Envelope -Session $owner -Path $configRoot
Assert-True ($rootState.status -eq 'CLEAN' -and $rootState.activeVersionId) `
    'P4-C3 base configuration must be clean and published'
$script:revision = [long]$rootState.draftRevision
$modules = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules")
$customerModule = @($modules | Where-Object code -eq 'customer')[0]
$orderModule = @($modules | Where-Object code -eq 'work_order')[0]
Assert-True ($customerModule -and $orderModule) 'P4-C3 base modules are missing'
$customerFields = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules/$($customerModule.id)/fields")
$orderFields = @(Invoke-Envelope -Session $owner -Path "$configRoot/modules/$($orderModule.id)/fields")
$customerName = @($customerFields | Where-Object code -eq 'customer_name')[0]
$customerRelation = @($orderFields | Where-Object code -eq 'customer')[0]
$orderLines = @($orderFields | Where-Object code -eq 'order_lines')[0]
Assert-True ($customerName -and $customerRelation -and $orderLines) 'P4-C3 dependency fields are missing'
$existingAmount = @($orderFields | Where-Object code -eq 'derived_amount')[0]
if ($existingAmount) {
    $amount = $existingAmount
    $taxed = @($orderFields | Where-Object code -eq 'derived_taxed')[0]
    $total = @($orderFields | Where-Object code -eq 'derived_total')[0]
    $summary = @($orderFields | Where-Object code -eq 'derived_customer_count')[0]
    $lookup = @($orderFields | Where-Object code -eq 'derived_customer_name')[0]
    $aggregate = @($orderFields | Where-Object code -eq 'derived_line_total')[0]
    Assert-True ($taxed -and $total -and $summary -and $lookup -and $aggregate) `
        'Published P4-C4 restart configuration is incomplete'
    $schemaVersionId = [string]$rootState.activeVersionId
} else {
$amount = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_amount' -Name 'Derived amount' `
    -Type 'NUMBER' -SortOrder 10 -Readonly $false -IndexMode 'SORT' `
    -Properties @{ precision = 18; scale = 2; minimum = 0 }
$taxed = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_taxed' -Name 'Derived taxed' `
    -Type 'FORMULA' -SortOrder 11 -Readonly $true -IndexMode 'SORT' -Properties @{
        resultSchema = 'DECIMAL'; astVersion = 1
        expressionAst = @{ fieldId = [string]$amount.id }
    }
$total = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_total' -Name 'Derived total' `
    -Type 'CALCULATED' -SortOrder 12 -Readonly $true -IndexMode 'SORT' -Properties @{
        resultSchema = 'DECIMAL'; astVersion = 1
        expressionAst = @{ op = 'ADD'; args = @(
            @{ fieldId = [string]$taxed.id }, @{ literalType = 'INTEGER'; value = 1 }
        ) }
    }
$summary = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_customer_count' `
    -Name 'Derived customer count' -Type 'SUMMARY' -SortOrder 13 -Readonly $true -IndexMode 'SORT' `
    -Properties @{ resultSchema = 'INTEGER'; relationFieldId = [string]$customerRelation.id; reduction = 'COUNT' }
$lookup = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_customer_name' `
    -Name 'Derived customer name' -Type 'LOOKUP' -SortOrder 14 -Readonly $true -IndexMode 'FILTER' `
    -Properties @{
        resultSchema = 'STRING'; relationFieldId = [string]$customerRelation.id
        targetFieldId = [string]$customerName.id; distinct = $true
    }
$aggregate = Add-Field -ModuleId ([string]$orderModule.id) -Code 'derived_line_total' `
    -Name 'Derived line total' -Type 'AGGREGATE' -SortOrder 15 -Readonly $true -IndexMode 'SORT' `
    -Properties @{
        resultSchema = 'DECIMAL'; subtableFieldId = [string]$orderLines.id; aggregateId = 'total_amount'
    }

$check = Invoke-Envelope -Session $owner -Path "$configRoot/checks" -Method POST -Body @{
    draftRevision = [string]$script:revision
}
Assert-True ($check.status -eq 'PASSED' -and [int]$check.blockerCount -eq 0) `
    "P4-C4 publication check failed: $($check.issues | ConvertTo-Json -Compress)"
$rootState = Invoke-Envelope -Session $owner -Path $configRoot
$published = Invoke-Envelope -Session $owner -Path "${configRoot}:publish" -Method POST -Idempotent -Body @{
    checkId = [string]$check.id; draftRevision = [string]$script:revision
    configRootVersion = [string]$rootState.version; reason = 'P4-C4 task-02 packaged restart acceptance'
}
$schemaVersionId = [string]$published.version.id
}

$owner = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $owner -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$registration.username; password = [string]$registration.password
}
$null = Invoke-Envelope -Session $owner -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}

$customerRoot = "/api/v1/systems/$systemId/runtime/modules/customer"
$orderRoot = "/api/v1/systems/$systemId/runtime/modules/work_order"
$schema = Invoke-Envelope -Session $owner -Path "$orderRoot/record-schema"
$derivedTypes = @($schema.fields | Where-Object fieldCode -in @(
    'derived_taxed', 'derived_total', 'derived_customer_count',
    'derived_customer_name', 'derived_line_total') | ForEach-Object type)
Assert-True (($derivedTypes -join ',') -eq 'FORMULA,CALCULATED,SUMMARY,LOOKUP,AGGREGATE') `
    "Unexpected derived schema: $($derivedTypes -join ',')"

$customer = Invoke-Envelope -Session $owner -Path "$customerRoot/records" -Method POST -Idempotent -Body @{
    schemaVersionId = $schemaVersionId; title = 'P4-C4 restart customer'
    values = @{ customer_name = 'P4-C4 Restart Acme' }; relations = @(); subtables = @()
}
$customer = Invoke-Envelope -Session $owner -Path "$customerRoot/records/$($customer.recordId):activate" `
    -Method POST -Idempotent -Body @{ expectedVersion = [long]$customer.version }
$order = Invoke-Envelope -Session $owner -Path "$orderRoot/records" -Method POST -Idempotent -Body @{
    schemaVersionId = $schemaVersionId; title = 'P4-C4 restart order'
    values = @{ subject = 'P4-C4 restart order'; derived_amount = 41.5 }
    relations = @(@{
        fieldCode = 'customer'; targets = @(@{
            targetRecordId = [string]$customer.recordId; targetExpectedVersion = [long]$customer.version; ordinal = 0
        })
    })
    subtables = @(@{
        fieldCode = 'order_lines'; rows = @(@{
            clientRowKey = 'p4-c4-restart-line'; rowId = $null; expectedVersion = $null; ordinal = 0
            values = @{
                line_description = 'Restart line'; quantity = 1
                line_amount = @{ amount = '8.25'; currency = 'CNY' }
            }
        })
    })
}
$detailPath = "$orderRoot/records/$($order.recordId)"
$detail = Wait-DerivedReady -Path $detailPath
$order = Invoke-Envelope -Session $owner -Path "$detailPath`:activate" -Method POST -Idempotent `
    -Body @{ expectedVersion = [long]$detail.version }
$detail = Wait-DerivedReady -Path $detailPath

Assert-True ([decimal](Derived-Value $detail 'derived_taxed').result -eq 41.5) 'FORMULA result mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_total').result -eq 42.5) 'CALCULATED result mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_customer_count').result -eq 1) 'SUMMARY result mismatch'
Assert-True ([string](Derived-Value $detail 'derived_customer_name').result[0] -eq 'P4-C4 Restart Acme') `
    'LOOKUP result mismatch'
Assert-True ([decimal](Derived-Value $detail 'derived_line_total').result -eq 8.25) 'AGGREGATE result mismatch'

$query = Invoke-Envelope -Session $owner -Path "$orderRoot/records:query" -Method POST -Body @{
    schemaVersionId = $schemaVersionId; page = 1; size = 10; recordScope = 'active'; q = $null
    filter = @{ kind = 'AND'; children = @(
        @{ kind = 'PREDICATE'; fieldCode = 'derived_taxed'; operator = 'EQ'; value = 41.5 },
        @{ kind = 'PREDICATE'; fieldCode = 'derived_customer_name'; operator = 'EQ'; value = 'P4-C4 Restart Acme' }
    ) }
    sort = @(@{ fieldCode = 'derived_total'; direction = 'DESC'; nulls = 'LAST' })
    columns = @('derived_taxed','derived_total','derived_customer_count','derived_customer_name','derived_line_total')
    viewId = $null
}
Assert-True ([long]$query.total -eq 1 -and [string]$query.rows[0].recordId -eq [string]$order.recordId) `
    'Typed derived query failed before restart'

$fixture = [ordered]@{
    generatedAt = (Get-Date).ToString('o'); baseUrl = $BaseUrl
    systemId = $systemId; tenantId = [string]$baseFixture.tenantId
    username = [string]$registration.username; password = [string]$registration.password
    schemaVersionId = $schemaVersionId; moduleCode = 'work_order'
    fields = [ordered]@{
        amount = [string]$amount.id; taxed = [string]$taxed.id; total = [string]$total.id
        summary = [string]$summary.id; lookup = [string]$lookup.id; aggregate = [string]$aggregate.id
    }
    records = [ordered]@{ customer = [string]$customer.recordId; order = [string]$order.recordId }
    expected = [ordered]@{
        taxed = '41.5'; total = '42.5'; summary = '1'; lookup = 'P4-C4 Restart Acme'; aggregate = '8.25'
    }
    preparedRecordVersion = [long]$detail.version; queryTotal = [long]$query.total
}
Write-Utf8Json -TargetPath $FixturePath -Value $fixture
$fixture | ConvertTo-Json -Depth 50
