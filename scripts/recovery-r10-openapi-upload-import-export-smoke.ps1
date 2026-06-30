param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 24 -Compress)"
    }
    return $response.data
}

function Invoke-UploadFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [hashtable]$Headers = @{}
    )

    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        foreach ($key in $Headers.Keys) {
            [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key])
        }
        $bytes = [System.IO.File]::ReadAllBytes($FilePath)
        $content = [System.Net.Http.ByteArrayContent]::new($bytes)
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/csv')
        $form.Add($content, 'file', [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync("$BaseUrl$Path", $form).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Upload failed: POST $Path -> HTTP $([int]$response.StatusCode) $body"
        }
        $json = $body | ConvertFrom-Json
        if ($json.code -ne 'SUCCESS') {
            throw "Upload failed: POST $Path -> $body"
        }
        return $json.data
    } finally {
        $form.Dispose()
        $client.Dispose()
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function New-FieldBody {
    param(
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [bool]$Required = $false
    )
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        dictTypeId = $null
        permissionMetadata = $null
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = 'none'
            desensitizeMode = 'none'
        }
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:AdminHeaders) {
        return 'SKIPPED'
    }
    try {
        $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:AdminHeaders -Body @{
            reason = 'recovery-r10-openapi-upload-import-export cleanup created system'
            impactScope = 'created_by_current_script'
            idempotencyKey = "cleanup-r10-$script:Suffix"
        }
        return [string]$deleted.result
    } catch {
        return "FAILED:$($_.Exception.Message)"
    }
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        $script:CleanupResult = Remove-CreatedSystem
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:SystemId = $null
$script:AdminHeaders = $null
$CleanupResult = 'SKIPPED'

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R10 OpenAPI Import Export $script:Suffix"
    systemCode = "r10_oie_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r10 setup'
}

$Role = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R10 Runtime Member $script:Suffix"
    roleCode = "R10_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R10 runtime role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R10 Business Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$Role.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r10_contract_$script:Suffix"
    name = "R10 Contract Ledger $script:Suffix"
    status = 1
    description = 'Recovery R10 OpenAPI upload import export module'
}
$ModuleId = [string]$Module.moduleId

$Fields = @()
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractName' -Name 'Contract Name' -Type 'TEXT' -Required $true)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractAmount' -Name 'Contract Amount' -Type 'NUMBER' -Required $false)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'sourceTag' -Name 'Source Tag' -Type 'TEXT' -Required $false)
$FieldIds = @($Fields | ForEach-Object { [string]$_.fieldId })

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'All Contracts'
    defaultScene = $true
    visibleRoleIds = @([string]$Role.roleId)
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$Fields[0].fieldId)
    sortFieldIds = @([string]$Fields[1].fieldId)
}

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:SystemId/roles/$($Role.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{ "*" = $true; "record.create" = $true; "record.edit" = $true; "record.delete" = $true }
    fieldPermissions = @{ "*" = "WRITABLE" }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r10 module publish'
    idempotencyKey = "module-publish-r10-$script:Suffix"
}

$SecretMaterial = "material-r10-$script:Suffix"
$OpenApiApp = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "openapi-create-r10-$script:Suffix" }) -Body @{
    externalAppCode = "r10_app_$script:Suffix"
    appName = "R10 OpenAPI App $script:Suffix"
    status = 1
    scopes = @('record.data.read', 'record.data.write')
    callbackUrl = "https://callback.example/r10/$script:Suffix"
    rateLimit = @{
        dimension = 'APP'
        windowSeconds = 60
        limit = 120
        burstLimit = 30
        overflowPolicy = 'REJECT'
        status = 1
    }
    secretMaterialRef = $SecretMaterial
    requestedSecretVersion = "v1-$script:Suffix"
    idempotencyKey = "openapi-create-r10-$script:Suffix"
}
$OpenApiJson = $OpenApiApp | ConvertTo-Json -Depth 30 -Compress
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$OpenApiApp.externalAppId)) -Message 'OpenAPI app create did not return externalAppId.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$OpenApiApp.openApiSecretRef.secretRefId)) -Message 'OpenAPI app did not return secretRefId.'
Assert-True -Condition ($OpenApiJson -notlike "*$SecretMaterial*") -Message 'OpenAPI create response leaked plaintext secret material.'
Assert-True -Condition ($OpenApiJson -notlike '*secretMaterialRef*' -and $OpenApiJson -notlike '*storageRef*') -Message 'OpenAPI create response exposed secret storage fields.'

$RotatedMaterial = "material-rotated-r10-$script:Suffix"
$Rotate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps/$($OpenApiApp.externalAppId)/rotate-secret" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "openapi-rotate-r10-$script:Suffix" }) -Body @{
    newMaterialRef = $RotatedMaterial
    requestedVersion = "v2-$script:Suffix"
    dualWriteValidation = $true
    switchAfterValidation = $true
    rollbackPlan = 'keep previous SecretRef until validation passes'
    idempotencyKey = "openapi-rotate-r10-$script:Suffix"
}
$RotateJson = $Rotate | ConvertTo-Json -Depth 30 -Compress
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Rotate.jobId)) -Message 'OpenAPI secret rotation did not return jobId.'
Assert-True -Condition ($RotateJson -notlike "*$RotatedMaterial*") -Message 'OpenAPI rotate response leaked plaintext secret material.'
Assert-True -Condition ($Rotate.task.bizType -eq 'OPENAPI_SECRET_ROTATION') -Message 'OpenAPI rotate did not create a secret rotation async task.'

$SecretRefId = [string]$Rotate.openApiSecretRef.secretRefId
if ([string]::IsNullOrWhiteSpace($SecretRefId)) {
    $SecretRefId = [string]$OpenApiApp.openApiSecretRef.secretRefId
}
$OpenApiHeaders = @{
    'X-OpenAPI-App-Code' = [string]$OpenApiApp.externalAppCode
    'X-OpenAPI-Secret-Ref' = $SecretRefId
    'Idempotency-Key' = "openapi-record-create-r10-$script:Suffix"
}
$ExternalCreate = Invoke-Api -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records" -Headers $OpenApiHeaders -Body @{
    fieldValues = @{
        contractName = "OpenAPI Contract $script:Suffix"
        contractAmount = 2100
        sourceTag = 'openapi'
    }
    sourceType = 'OPENAPI_R10'
}
$OpenApiRecordId = [string]$ExternalCreate.recordId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($OpenApiRecordId)) -Message 'OpenAPI external record create did not return recordId.'

$ExternalSearch = Invoke-Api -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records/search" -Headers $OpenApiHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $script:Suffix
    fields = @('contractName', 'contractAmount', 'sourceTag')
}
Assert-True -Condition ([int]$ExternalSearch.total -ge 1 -or [int]$ExternalSearch.page.total -ge 1) -Message 'OpenAPI external search did not return created record.'
$ExternalDetail = Invoke-Api -Method 'Get' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records/$OpenApiRecordId" -Headers $OpenApiHeaders
Assert-True -Condition ([string]$ExternalDetail.recordId -eq $OpenApiRecordId) -Message 'OpenAPI external detail did not return created record.'

$CallLogs = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/call-logs/search?pageNo=1&pageSize=50" -Headers $script:AdminHeaders -Body @{
    externalAppId = [string]$OpenApiApp.externalAppId
    externalAppCode = [string]$OpenApiApp.externalAppCode
    result = 'SUCCESS'
}
$SuccessfulLogs = @($CallLogs.records | Where-Object { [string]$_.externalAppId -eq [string]$OpenApiApp.externalAppId -and [string]$_.result -eq 'SUCCESS' })
Assert-True -Condition ($SuccessfulLogs.Count -ge 3) -Message 'OpenAPI call logs did not capture create/search/detail success entries.'

$ImportFile = Join-Path ([System.IO.Path]::GetTempPath()) "r10-import-$script:Suffix.csv"
$Csv = "contract_name,contract_amount,source_tag`nImported A $script:Suffix,3100,import`nImported B $script:Suffix,3200,import`n"
[System.IO.File]::WriteAllText($ImportFile, $Csv, [System.Text.UTF8Encoding]::new($false))
$Upload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=IMPORT_EXPORT' -FilePath $ImportFile -Headers $script:AdminHeaders
$ImportFileId = [string]$Upload.file.fileId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($ImportFileId)) -Message 'CSV upload did not return fileId.'
Assert-True -Condition ([string]$Upload.file.fileName -like 'r10-import-*') -Message 'CSV upload did not preserve file name.'

$Precheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/imports/precheck" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "import-precheck-r10-$script:Suffix" }) -Body @{
    fileId = $ImportFileId
    templateCode = 'default'
    fieldMapping = @{
        contract_name = 'contractName'
        contract_amount = 'contractAmount'
        source_tag = 'sourceTag'
    }
    duplicateStrategy = 'SKIP'
}
Assert-True -Condition ($Precheck.passed -eq $true) -Message "Import precheck did not pass: $($Precheck | ConvertTo-Json -Depth 20 -Compress)"
Assert-True -Condition ([int]$Precheck.totalRows -eq 2 -and [int]$Precheck.validRows -eq 2) -Message 'Import precheck did not count CSV rows correctly.'
Assert-True -Condition ($Precheck.task.bizType -eq 'IMPORT_PRECHECK' -and $Precheck.task.status -eq 'SUCCESS') -Message 'Import precheck did not create successful async task.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Precheck.resultFile.fileId)) -Message 'Import precheck did not return result file.'

$Confirm = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/imports/confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "import-confirm-r10-$script:Suffix" }) -Body @{
    precheckId = [string]$Precheck.precheckId
    duplicateStrategy = 'SKIP'
    rollbackSupported = $true
}
Assert-True -Condition ($Confirm.task.bizType -eq 'IMPORT_CONFIRM' -and $Confirm.task.status -eq 'SUCCESS') -Message 'Import confirm did not create successful async task.'
Assert-True -Condition ([int]$Confirm.task.partialSuccessCount -eq 2) -Message 'Import confirm did not import two rows.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Confirm.task.resultFile.fileId)) -Message 'Import confirm did not return result file.'

$RuntimeSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $script:AdminHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $script:Suffix
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @(@{ fieldCode = 'contractAmount'; direction = 'DESC' })
}
Assert-True -Condition ([int]$RuntimeSearch.page.total -ge 3) -Message 'Runtime search did not include OpenAPI and imported rows.'
$RuntimeRows = @($RuntimeSearch.page.records)
$ImportedRows = @($RuntimeRows | Where-Object {
    $row = $_
    @($row.fields | Where-Object { [string]$_.fieldCode -eq 'sourceTag' -and [string]$_.value -eq 'import' }).Count -gt 0
})
Assert-True -Condition ($ImportedRows.Count -ge 2) -Message 'Runtime search did not include imported CSV rows.'

$Export = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/exports" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "export-r10-$script:Suffix" }) -Body @{
    scope = 'ALL_MATCHED'
    selectedRecordIds = @()
    fields = @('contractName', 'contractAmount', 'sourceTag')
    fileFormat = 'CSV'
    desensitizeMode = 'PERMISSION'
    filters = @{}
}
Assert-True -Condition ($Export.task.bizType -eq 'RUNTIME_RECORD_EXPORT' -and $Export.task.status -eq 'SUCCESS') -Message 'Export did not create successful async task.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Export.expectedResultFile.fileId)) -Message 'Export did not return result file.'

$CleanupResult = Remove-CreatedSystem

$Result = [ordered]@{
    result = 'PASS'
    suffix = $script:Suffix
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    openApiAppId = [string]$OpenApiApp.externalAppId
    openApiRecordId = $OpenApiRecordId
    openApiSuccessfulLogCount = $SuccessfulLogs.Count
    uploadedImportFileId = $ImportFileId
    precheckId = [string]$Precheck.precheckId
    importTaskId = [string]$Confirm.task.taskId
    exportTaskId = [string]$Export.task.taskId
    runtimeTotal = [int]$RuntimeSearch.page.total
    cleanup = $CleanupResult
}

$Result | ConvertTo-Json -Depth 20
