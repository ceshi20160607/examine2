$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$systemId = 44
$password = 'Correct-c68-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Token)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $response = Invoke-WebRequest -Method $Method -Uri "$apiBase$Path" -Headers $headers -ContentType 'application/json; charset=utf-8' -SkipHttpErrorCheck
    $payload = $response.Content | ConvertFrom-Json
    if ([int]$response.StatusCode -ge 400) {
        throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    $payload.data
}

function Login([string]$Username, [string]$Password) {
    $response = Invoke-WebRequest -Method Post -Uri "$apiBase/api/auth/login" -ContentType 'application/json; charset=utf-8' -Body (@{
        username = $Username
        password = $Password
    } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    $payload = $response.Content | ConvertFrom-Json
    if ([int]$response.StatusCode -ge 400) { throw "login failed for $Username" }
    $payload.data
}

function Enter-System([string]$Username) {
    $platform = Login -Username $Username -Password $password
    $response = Invoke-WebRequest -Method Post -Uri "$apiBase/api/systems/$systemId/enter" -Headers @{ Authorization = "Bearer $($platform.accessToken)" } -ContentType 'application/json; charset=utf-8' -Body (@{
        previousSystemId = $null
        previousTenantId = $null
    } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    $payload = $response.Content | ConvertFrom-Json
    if ([int]$response.StatusCode -ge 400) { throw "enter system failed for $Username" }
    $payload.data
}

$health = Invoke-WebRequest -UseBasicParsing "$apiBase/actuator/health"
if ($health.StatusCode -ne 200) { throw 'backend health check failed' }

$platform = Login -Username 'admin' -Password '123123aa'
$systems = Invoke-Api -Method Get -Path '/api/systems/directory' -Token $platform.accessToken
$targetSystem = $systems | Where-Object systemId -eq $systemId | Select-Object -First 1
if ($null -eq $targetSystem) { throw 'platform administrator cannot read system 44' }

$owner = Enter-System -Username 'c68_owner_227384'
$ownerToken = $owner.tokens.accessToken
$moduleOverview = Invoke-Api -Method Get -Path '/api/admin/module-config' -Token $ownerToken
$afterSales = $moduleOverview.modules | Where-Object name -eq '售后工单' | Select-Object -First 1
if ($null -eq $afterSales -or $afterSales.status -ne 'ACTIVE') { throw 'published 售后工单 module not found' }
$authorization = Invoke-Api -Method Get -Path '/api/admin/system/authorization' -Token $ownerToken
if (($authorization.roles | Where-Object status -eq 'ACTIVE').Count -lt 4) { throw 'published role set is incomplete' }
$aiOverview = Invoke-Api -Method Get -Path '/api/admin/system/ai' -Token $ownerToken
$aiModule = $aiOverview.modules | Where-Object name -eq '售后工单' | Select-Object -First 1
if ($null -eq $aiModule -or -not ($aiModule.fieldOptions.name -contains '工单主题')) {
    throw 'AI configuration does not return business field names'
}

$seller = Enter-System -Username 'c68_seller_227384'
$sellerToken = $seller.tokens.accessToken
$catalog = Invoke-Api -Method Get -Path '/api/runtime/modules' -Token $sellerToken
$sellerModule = $catalog | Where-Object moduleName -eq '售后工单' | Select-Object -First 1
if ($null -eq $sellerModule -or $catalog.Count -lt 3) { throw 'ordinary user module catalog is incomplete' }
$sellerActions = @($seller.permissions | Where-Object resourceCode -eq $sellerModule.moduleCode | Select-Object -ExpandProperty actionCode)
foreach ($requiredAction in @('LIST', 'DETAIL', 'CREATE', 'UPDATE', 'ARCHIVE')) {
    if ($sellerActions -notcontains $requiredAction) { throw "ordinary user is missing $requiredAction for 售后工单" }
}
$query = 'lifecycleState=ACTIVE&tenantScope=ALL&search=&filters=%5B%5D&sortField=updatedAt&sortDirection=DESC&page=1&pageSize=20'
$records = Invoke-Api -Method Get -Path "/api/runtime/modules/$($sellerModule.moduleCode)/records?$query" -Token $sellerToken
$repair = $records.records | Where-Object title -eq '总部打印机报修' | Select-Object -First 1
if ($null -eq $repair) { throw 'ordinary user cannot read the accepted 售后工单 record' }

[pscustomobject]@{
    backend = 'UP'
    platformSystem = $targetSystem.systemName
    publishedModules = @($moduleOverview.modules | Where-Object status -eq 'ACTIVE').Count
    publishedRoles = @($authorization.roles | Where-Object status -eq 'ACTIVE').Count
    ordinaryModules = @($catalog.moduleName)
    ordinaryActions = $sellerActions
    aiBusinessFields = @($aiModule.fieldOptions.name)
    acceptedRecord = $repair.title
    acceptedRecordNumber = $repair.recordNumber
} | ConvertTo-Json -Depth 8
