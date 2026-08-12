param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$FixturePath = "$PSScriptRoot/fixture.json",
    [string]$MembersPath = "$PSScriptRoot/task-02-members.json",
    [string]$ResultPath = "$PSScriptRoot/task-02-retry.json",
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
    [IO.File]::WriteAllText($TargetPath, ($Value | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
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

function Invoke-DatabaseRows([string]$Query) {
    $output = & docker run --rm --env "MYSQL_PWD=$DatabasePassword" mysql:8.4 mysql `
        --protocol=tcp -h $DatabaseHost -P $DatabasePort -u $DatabaseUsername -D $DatabaseName `
        --batch --raw --skip-column-names -e $Query
    if ($LASTEXITCODE -ne 0) { throw "Database query failed" }
    return @($output)
}

function ReferenceValue([object]$Detail) {
    return @($Detail.values | Where-Object fieldCode -eq 'customer_name_ref')[0]
}

$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$members = Get-Content -LiteralPath $MembersPath -Raw | ConvertFrom-Json
$health = Invoke-RestMethod -Uri "$BaseUrl/management/health" -TimeoutSec 10
Assert-True ($health.status -eq 'UP') 'Backend is not healthy'

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-Envelope -Session $session -Path '/api/v1/auth/login' -Method POST -Body @{
    account = [string]$members.positive.username
    password = [string]$members.positive.password
}
$null = Invoke-Envelope -Session $session -Path "/api/v1/context/systems/$($fixture.systemId):switch" `
    -Method POST -Body @{}

$customerPath = "/api/v1/systems/$($fixture.systemId)/runtime/modules/customer/records/$($fixture.records.customer)"
$orderPath = "/api/v1/systems/$($fixture.systemId)/runtime/modules/work_order/records/$($fixture.records.workOrder)"
$retryPath = "$orderPath/references/customer_name_ref:retry"
$customerBefore = Invoke-Envelope -Session $session -Path $customerPath
$orderBefore = Invoke-Envelope -Session $session -Path $orderPath
$referenceBefore = ReferenceValue $orderBefore
Assert-True ($referenceBefore.value.recalculationState -eq 'READY') 'Reference baseline is not READY'

$propertyRows = @(Invoke-DatabaseRows @"
SELECT HEX(property_json) FROM un_module_runtime_schema_field
WHERE system_id=$($fixture.systemId) AND schema_version_id=$($fixture.schemaVersionId)
  AND module_snapshot_id=$($fixture.modules.workOrder)
  AND field_snapshot_id=$($fixture.fields.customerReference) AND field_scope='RECORD';
"@)
$originalPropertyHex = [string]$propertyRows[0]
Assert-True (-not [string]::IsNullOrWhiteSpace($originalPropertyHex)) 'Reference projection was not found'

$newName = 'Acme Customer Retry ' + (Get-Date -Format 'yyyyMMddHHmmss')
$failed = $null
$projectionRestored = $false
try {
    $null = Invoke-DatabaseRows @"
UPDATE un_module_runtime_schema_field
SET property_json=JSON_OBJECT('sourceFieldId','$($fixture.fields.customerRelation)','targetFieldId','1')
WHERE system_id=$($fixture.systemId) AND schema_version_id=$($fixture.schemaVersionId)
  AND module_snapshot_id=$($fixture.modules.workOrder)
  AND field_snapshot_id=$($fixture.fields.customerReference) AND field_scope='RECORD';
"@
    $updatedCustomer = Invoke-Envelope -Session $session -Path $customerPath -Method PUT `
        -IdempotencyKey ([guid]::NewGuid().ToString()) -Body @{
        schemaVersionId = [string]$fixture.schemaVersionId
        title = $newName
        expectedVersion = [long]$customerBefore.version
        values = @{ customer_name = $newName }
        relations = @()
        subtables = @()
    }
    Assert-True ([long]$updatedCustomer.version -eq ([long]$customerBefore.version + 1)) `
        'Target update did not increment exactly once'

    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $candidate = Invoke-Envelope -Session $session -Path $orderPath
        $candidateReference = ReferenceValue $candidate
        if ($candidateReference.value.recalculationState -eq 'FAILED') {
            $failed = $candidate
            break
        }
        Start-Sleep -Milliseconds 500
    }
    Assert-True ($null -ne $failed) 'Reference did not reach FAILED after three worker attempts'
    $failedReference = ReferenceValue $failed
    Assert-True ([long]$failed.version -eq [long]$orderBefore.version) `
        'Failed recalculation changed the dependent record version'
    Assert-True ([string]$failedReference.value.result -eq [string]$referenceBefore.value.result) `
        'Failed recalculation did not preserve the last valid result'
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$failedReference.failureCorrelationId)) `
        'FAILED readback did not expose its correlation id outside the value payload'
} finally {
    $null = Invoke-DatabaseRows @"
UPDATE un_module_runtime_schema_field
SET property_json=CONVERT(UNHEX('$originalPropertyHex') USING utf8mb4)
WHERE system_id=$($fixture.systemId) AND schema_version_id=$($fixture.schemaVersionId)
  AND module_snapshot_id=$($fixture.modules.workOrder)
  AND field_snapshot_id=$($fixture.fields.customerReference) AND field_scope='RECORD';
"@
    $projectionRestored = $true
}

$retryKey = [guid]::NewGuid().ToString()
$retry = Invoke-Envelope -Session $session -Path $retryPath -Method POST -IdempotencyKey $retryKey -Body @{
    expectedVersion = [long]$failed.version
}
Assert-True ($retry.recalculationState -eq 'PENDING') 'Retry did not return PENDING'
$retryReplay = Invoke-Envelope -Session $session -Path $retryPath -Method POST -IdempotencyKey $retryKey -Body @{
    expectedVersion = [long]$failed.version
}
Assert-True ([string]$retryReplay.correlationId -eq [string]$retry.correlationId) `
    'Retry idempotency replay returned another correlation id'

$ready = $null
$deadline = (Get-Date).AddSeconds(20)
while ((Get-Date) -lt $deadline) {
    $candidate = Invoke-Envelope -Session $session -Path $orderPath
    $candidateReference = ReferenceValue $candidate
    if ($candidateReference.value.recalculationState -eq 'READY' `
            -and [string]$candidateReference.value.result -eq $newName) {
        $ready = $candidate
        break
    }
    Start-Sleep -Milliseconds 400
}
Assert-True ($null -ne $ready) 'Reference retry did not recover to READY with the current target value'
$readyReference = ReferenceValue $ready
Assert-True ([long]$ready.version -eq ([long]$orderBefore.version + 1)) `
    'Successful retry did not increment the dependent record exactly once'
Assert-True ([long]$readyReference.value.sourceVersion -eq ([long]$customerBefore.version + 1)) `
    'Recovered reference did not record the current source version'

$taskRows = @(Invoke-DatabaseRows @"
SELECT CONCAT(status,':',attempt_count,':',source_record_version)
FROM un_module_reference_recalc_task
WHERE system_id=$($fixture.systemId) AND tenant_id=$($fixture.tenantId)
  AND source_record_id=$($fixture.records.customer)
  AND source_record_version=$($readyReference.value.sourceVersion);
"@)
$taskRow = @([string]$taskRows[0] -split ':')
Assert-True ($taskRow[0] -eq 'PASSED' -and [int]$taskRow[1] -eq 0) `
    'Retried task did not finish PASSED with reset attempt count'

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    verdict = 'pass'
    backendHealth = [string]$health.status
    projectionRestored = $projectionRestored
    customerVersionBefore = [long]$customerBefore.version
    customerVersionAfter = [long]$readyReference.value.sourceVersion
    dependentVersionBefore = [long]$orderBefore.version
    dependentVersionWhileFailed = [long]$failed.version
    dependentVersionAfterRetry = [long]$ready.version
    failedState = 'FAILED'
    failedResultPreserved = [string]$referenceBefore.value.result
    failedCorrelationIdPresent = $true
    retryState = [string]$retry.recalculationState
    retryReplayStable = $true
    readyState = [string]$readyReference.value.recalculationState
    readyResult = [string]$readyReference.value.result
    sourceVersion = [long]$readyReference.value.sourceVersion
    taskStatus = [string]$taskRow[0]
    taskAttemptCount = [int]$taskRow[1]
}
Write-Utf8Json -TargetPath $ResultPath -Value $result
$result | ConvertTo-Json -Depth 30
