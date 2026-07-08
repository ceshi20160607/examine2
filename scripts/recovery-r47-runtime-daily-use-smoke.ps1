param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    return $response.data
}

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers
    } catch {
        if ($_.Exception.Message -match 'HTTP 403') {
            return @{ status = 403; path = $Path }
        }
        throw
    }
    throw "Expected HTTP 403 but request succeeded: $Method $Path"
}

function Invoke-UploadFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [hashtable]$Headers = @{},
        [string]$ContentType = 'text/plain'
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
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
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
    param([string]$Code, [string]$Name, [string]$Type = 'TEXT', [bool]$Required = $false)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        filterOperators = @('EQ', 'LIKE')
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = $Code
            desensitizeMode = 'PERMISSION'
        }
    }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Find-Chrome {
    $paths = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($path in $paths) {
        if ($path -and (Test-Path -LiteralPath $path)) {
            return $path
        }
    }
    throw 'Chrome or Edge executable was not found.'
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:CreatedSystemIds)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r47 runtime daily use cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r47-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

trap {
    $originalError = $_
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')

$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r47-runtime-daily-use'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r47-runtime-daily-use-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r47-runtime-daily-use-2026-07-01.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }
$Password = 'Aa123456!'

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R47 Runtime Daily Use $script:Suffix"
    systemCode = "r47_runtime_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null
$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r47 admin setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R47 Runtime Operator $script:Suffix"
    roleCode = "R47_RUNTIME_OPERATOR_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R47 runtime daily-use role'
}
$ReadonlyRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R47 Readonly Member $script:Suffix"
    roleCode = "R47_READONLY_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R47 forbidden mutation role'
}
$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R47 Runtime Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId, [string]$ReadonlyRole.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$Group.groupId
    moduleCode = "r47_case_$script:Suffix"
    name = "R47 Runtime Case $script:Suffix"
    status = 1
    description = 'Recovery R47 runtime daily-use module'
}
$ModuleId = [string]$Module.moduleId

$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseTitle' -Name 'R47 Case Title' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseAmount' -Name 'R47 Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseOwner' -Name 'R47 Owner' -Type 'TEXT')
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'R47 Secret Note' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'r47_default'
    sceneName = 'R47 Daily View'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId, [string]$ReadonlyRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$AmountField.fieldId, [string]$OwnerField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$OwnerField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ $ModuleId = $true }
    actionPermissions = @{
        'record.read' = $true
        'record.create' = $true
        'record.edit' = $true
        'record.delete' = $true
        'record.import' = $true
        'record.export' = $true
    }
    fieldPermissions = @{
        "$ModuleId.caseTitle" = 'WRITABLE'
        "$ModuleId.caseAmount" = 'WRITABLE'
        "$ModuleId.caseOwner" = 'WRITABLE'
        "$ModuleId.secretNote" = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'ALL' })
    denyPolicies = @()
}
$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($ReadonlyRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ $ModuleId = $true }
    actionPermissions = @{
        'record.read' = $true
        'record.create' = $false
        'record.edit' = $false
        'record.delete' = $false
        'record.import' = $false
        'record.export' = $false
    }
    fieldPermissions = @{
        "$ModuleId.caseTitle" = 'READABLE'
        "$ModuleId.caseAmount" = 'READABLE'
        "$ModuleId.caseOwner" = 'READABLE'
        "$ModuleId.secretNote" = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'ALL' })
    denyPolicies = @('record.create', 'record.edit', 'record.import', 'record.export')
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r47 module publish'
    idempotencyKey = "module-publish-r47-$script:Suffix"
}
$GroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($Group.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r47 group publish'
    idempotencyKey = "group-publish-r47-$script:Suffix"
}

$NormalLoginName = "r47_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$((Get-Date).ToString('HHmmssfff'))"
    email = "r47_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R47 Owned $script:Suffix"
    systemCode = "r47_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null
$ReadonlyLoginName = "r47_readonly_$script:Suffix"
$ReadonlyRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $ReadonlyLoginName
    mobile = "15$((Get-Date).ToString('HHmmssfff'))"
    email = "r47_readonly_$script:Suffix@example.com"
    password = $Password
    systemName = "R47 Readonly Owned $script:Suffix"
    systemCode = "r47_readonly_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$ReadonlyRegister.systemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R47 Runtime Member $script:Suffix"
    employeeNo = "R47M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r47_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$ReadonlyMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R47 Readonly Member $script:Suffix"
    employeeNo = "R47R$((Get-Date).ToString('HHmmssfff'))"
    mobile = "14$((Get-Date).ToString('HHmmssfff'))"
    email = "r47_readonly_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$ReadonlyRole.roleId)
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members/$($ReadonlyMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $ReadonlyLoginName
    bindMode = 'BIND'
}
$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = $NormalLoginName; password = $Password; loginTarget = 'PLATFORM' }
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{ systemId = $SystemId; tenantId = $TenantId; reason = 'recovery-r47 normal runtime' }
$ReadonlyLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = $ReadonlyLoginName; password = $Password; loginTarget = 'PLATFORM' }
$ReadonlyHeaders = @{ Authorization = "Bearer $($ReadonlyLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ReadonlyHeaders -Body @{ systemId = $SystemId; tenantId = $TenantId; reason = 'recovery-r47 readonly runtime' }

$EmptySearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = "no-result-$script:Suffix"
    sceneCode = 'r47_default'
}
Assert-True -Condition ([int]$EmptySearch.page.total -eq 0) -Message 'Empty runtime search should return 0 rows before data creation.'

$AttachmentFile = Join-Path $env:TEMP "r47-runtime-attachment-$script:Suffix.txt"
[System.IO.File]::WriteAllText($AttachmentFile, "r47 runtime attachment evidence $script:Suffix", [System.Text.UTF8Encoding]::new($false))
$Upload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=RUNTIME_RECORD' -FilePath $AttachmentFile -Headers $NormalHeaders -ContentType 'text/plain'
$UploadedFileId = [string]$Upload.file.fileId
$UploadedFileName = [string]$Upload.file.fileName

$RecordTitle = "R47 Browser Record $script:Suffix"
$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        caseTitle = $RecordTitle
        caseAmount = 4700
        caseOwner = 'Runtime Team'
    }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    sourceType = 'R47_CREATE'
}
$RecordId = [string]$Record.recordId
$UpdatedTitle = "R47 Updated Record $script:Suffix"
$null = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        caseTitle = $UpdatedTitle
        caseAmount = 4800
        caseOwner = 'Runtime Ops'
    }
    childRows = @{}
    sourceType = 'R47_UPDATE'
}
$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$DetailAttachmentNames = @($Detail.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileName })
$DetailHistoryRows = @($Detail.tabs | Where-Object { $_.tabCode -eq 'operationLogs' } | ForEach-Object { @($_.payload.records) })
Assert-True -Condition ($DetailAttachmentNames -contains $UploadedFileName) -Message 'Runtime detail did not read back uploaded attachment.'
Assert-True -Condition (@($DetailHistoryRows).Count -ge 2) -Message 'Runtime detail did not read back create/update history rows.'

$ImportFile = Join-Path $env:TEMP "r47-import-$script:Suffix.csv"
$Csv = "caseTitle,caseAmount,caseOwner`nImported A $script:Suffix,110,Import Team`nImported B $script:Suffix,120,Import Team`n"
[System.IO.File]::WriteAllText($ImportFile, $Csv, [System.Text.UTF8Encoding]::new($false))
$ImportUpload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=IMPORT_EXPORT' -FilePath $ImportFile -Headers $NormalHeaders -ContentType 'text/csv'
$ImportFileId = [string]$ImportUpload.file.fileId
$Precheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/imports/precheck" -Headers ($NormalHeaders + @{ 'Idempotency-Key' = "import-precheck-r47-$script:Suffix" }) -Body @{
    fileId = $ImportFileId
    templateCode = 'default'
    duplicateStrategy = 'SKIP'
    fieldMapping = @{
        caseTitle = 'caseTitle'
        caseAmount = 'caseAmount'
        caseOwner = 'caseOwner'
    }
}
Assert-True -Condition ($Precheck.passed -eq $true -and [int]$Precheck.validRows -eq 2) -Message 'Import precheck did not pass with two valid rows.'
$ImportConfirm = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/imports/confirm" -Headers ($NormalHeaders + @{ 'Idempotency-Key' = "import-confirm-r47-$script:Suffix" }) -Body @{
    precheckId = [string]$Precheck.precheckId
    duplicateStrategy = 'SKIP'
    rollbackSupported = $true
}
Assert-True -Condition ($ImportConfirm.task.status -eq 'SUCCESS' -and [int]$ImportConfirm.task.partialSuccessCount -eq 2) -Message 'Import confirm did not create two rows.'
$Export = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/exports" -Headers ($NormalHeaders + @{ 'Idempotency-Key' = "export-r47-$script:Suffix" }) -Body @{
    scope = 'ALL_MATCHED'
    selectedRecordIds = @()
    fields = @('caseTitle', 'caseAmount', 'caseOwner')
    fileFormat = 'CSV'
    desensitizeMode = 'PERMISSION'
    filters = @{}
}
Assert-True -Condition ($Export.task.status -eq 'SUCCESS' -and -not [string]::IsNullOrWhiteSpace([string]$Export.expectedResultFile.fileId)) -Message 'Runtime export did not return a successful task result file.'

$RuntimeSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = $script:Suffix
    sceneCode = 'r47_default'
    sorts = @(@{ fieldCode = 'caseAmount'; direction = 'DESC' })
}
Assert-True -Condition ([int]$RuntimeSearch.page.total -ge 3) -Message 'Runtime search did not include created and imported records.'
Assert-True -Condition (@($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into normal runtime list schema.'

$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $ReadonlyHeaders -Body @{
    fieldValues = @{ caseTitle = "Forbidden $script:Suffix"; caseAmount = 1; caseOwner = 'Readonly' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R47_FORBIDDEN'
}
$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields?pageNo=1&pageSize=20" -Headers $NormalHeaders

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r47-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--remote-allow-origins=*',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    'about:blank'
) -PassThru -WindowStyle Hidden

$ready = $false
for ($i = 0; $i -lt 50; $i++) {
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Milliseconds 200
    }
}
Assert-True -Condition $ready -Message 'Chrome DevTools endpoint did not become ready.'

$nodeScript = Join-Path $env:TEMP "unexamine-r47-runtime-daily-use-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R47_BASE_URL;
const port = process.env.R47_CDP_PORT;
const outDir = process.env.R47_EVIDENCE_DIR;
const systemId = process.env.R47_SYSTEM_ID;
const moduleName = process.env.R47_MODULE_NAME;
const recordTitle = process.env.R47_RECORD_TITLE;
const attachmentName = process.env.R47_ATTACHMENT_NAME;
const secretText = process.env.R47_SECRET_TEXT;
const role = {
  accountId: process.env.R47_ACCOUNT_ID,
  accessToken: process.env.R47_TOKEN,
  refreshToken: process.env.R47_REFRESH || '',
};

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
  return response.json();
}
async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await cdpJson('/json/list');
    return targets[0];
  }
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      close() { ws.close(); },
    });
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`));
        else item.res(message.result);
      }
    };
  });
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeout) {
    last = await evaluate(client, expression);
    if (last && last.ok) return last;
    await delay(150);
  }
  throw new Error(`Timeout waiting for ${expression}. Last=${JSON.stringify(last)}`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile,
  });
  await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
}
async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await waitFor(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 30000);
}
async function setStorage(client) {
  await navigate(client, `${baseUrl}/#/`);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});
    return JSON.stringify({ ok: true });
  })()`);
}
async function capture(client, key, viewport, extra = {}) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    return JSON.stringify({
      key: ${JSON.stringify(key)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      hasRuntimeShell: !!document.querySelector('.runtime-shell'),
      hasRuntimeMain: !!document.querySelector('.runtime-main'),
      hasRuntimeTable: !!document.querySelector('.runtime-table'),
      hasDetailPanel: !!document.querySelector('.detail-panel'),
      hasAttachmentDetail: !!document.querySelector('[data-runtime-attachment-detail="true"]'),
      hasHistoryList: !!document.querySelector('[data-runtime-history-list="true"]'),
      hasImportExportPanel: !!document.querySelector('.import-export-panel'),
      hasStepRail: !!document.querySelector('.step-rail'),
      hasTaskCard: !!document.querySelector('.task-card-detail'),
      hasRecordTitle: text.includes(${JSON.stringify(recordTitle)}),
      hasAttachmentName: text.includes(${JSON.stringify(attachmentName)}) || text.includes(${JSON.stringify(attachmentName.slice(0, 20))}),
      hasModuleName: text.includes(${JSON.stringify(moduleName)}),
      forbiddenTexts: [${JSON.stringify(secretText)}, 'System Admin', 'Platform Admin'].filter((item) => text.includes(item)),
      forbiddenSelectors: ['[data-home-config-panel="true"]', '[data-module-page-designer="true"]'].filter((selector) => !!document.querySelector(selector)),
      placeholderTexts: ['TODO', 'coming soon', 'Coming soon', 'placeholder'].filter((item) => text.includes(item)),
      overflowX,
      textSample: text.slice(0, 1200),
      ...${JSON.stringify(extra)}
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.forbiddenTexts.length) result.blockers.push('forbidden text visible');
  if (result.forbiddenSelectors.length) result.blockers.push('forbidden selector visible');
  if (result.placeholderTexts.length) result.blockers.push('placeholder copy visible');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function auditRuntime(client, viewport) {
  await setViewport(client, viewport);
  await navigate(client, `${baseUrl}/?r47=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && (document.body.innerText || '').includes(${JSON.stringify(recordTitle)}) })`);
  await evaluate(client, `(() => {
    const rows = Array.from(document.querySelectorAll('.runtime-table tbody tr, .runtime-table tr, .list-row'));
    const row = rows.find((item) => (item.innerText || '').includes(${JSON.stringify(recordTitle)}));
    if (!row) throw new Error('record row with attachment is not visible');
    row.click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(recordTitle)}) })`);
  const list = await capture(client, 'runtime-list', viewport);
  if (!list.hasRuntimeShell || !list.hasRuntimeMain || !list.hasRuntimeTable || !list.hasDetailPanel || !list.hasRecordTitle) {
    list.blockers.push('runtime list/detail did not render expected record');
  }

  await evaluate(client, `(() => {
    const tabs = Array.from(document.querySelectorAll('.detail-panel .detail-tabs button'));
    if (!tabs[2]) throw new Error('attachments tab missing');
    tabs[2].click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-attachment-detail="true"]') && (document.body.innerText || '').includes(${JSON.stringify(attachmentName.slice(0, 20))}) })`);
  const attachments = await capture(client, 'runtime-attachments', viewport);
  if (!attachments.hasAttachmentDetail || !attachments.hasAttachmentName) {
    attachments.blockers.push('attachment detail did not render uploaded file');
  }

  await evaluate(client, `(() => {
    const tabs = Array.from(document.querySelectorAll('.detail-panel .detail-tabs button'));
    if (!tabs[4]) throw new Error('history tab missing');
    tabs[4].click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-history-list="true"]') })`);
  const history = await capture(client, 'runtime-history', viewport);
  if (!history.hasHistoryList) history.blockers.push('history list did not render');

  await navigate(client, `${baseUrl}/?r47=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-page-head .inline-actions') })`);
  await evaluate(client, `(() => {
    const buttons = Array.from(document.querySelectorAll('.runtime-page-head .inline-actions button'));
    if (!buttons[1]) throw new Error('import button missing');
    buttons[1].click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.import-export-panel .step-rail') })`);
  const importPanel = await capture(client, 'runtime-import-panel', viewport);
  if (!importPanel.hasImportExportPanel || !importPanel.hasStepRail || !importPanel.hasTaskCard) {
    importPanel.blockers.push('import panel did not render structured steps/task state');
  }

  await navigate(client, `${baseUrl}/?r47=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-page-head .inline-actions') })`);
  await evaluate(client, `(() => {
    const buttons = Array.from(document.querySelectorAll('.runtime-page-head .inline-actions button'));
    if (!buttons[2]) throw new Error('export button missing');
    buttons[2].click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.import-export-panel .task-card-detail') })`);
  const exportPanel = await capture(client, 'runtime-export-panel', viewport);
  if (!exportPanel.hasImportExportPanel || !exportPanel.hasTaskCard) {
    exportPanel.blockers.push('export panel did not render task state container');
  }
  return [list, attachments, history, importPanel, exportPanel];
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  await setStorage(client);
  const results = [];
  for (const viewport of viewports) {
    results.push(...await auditRuntime(client, viewport));
  }
  client.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    results,
  };
  fs.writeFileSync(path.join(outDir, 'runtime-daily-use-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R47_BASE_URL = $BaseUrl
$env:R47_CDP_PORT = [string]$debugPort
$env:R47_EVIDENCE_DIR = $EvidenceDir
$env:R47_SYSTEM_ID = $SystemId
$env:R47_MODULE_NAME = [string]$Module.name
$env:R47_RECORD_TITLE = $UpdatedTitle
$env:R47_ATTACHMENT_NAME = $UploadedFileName
$env:R47_SECRET_TEXT = "R47 Hidden Secret $script:Suffix"
$env:R47_TOKEN = [string]$NormalLogin.accessToken
$env:R47_REFRESH = [string]$NormalLogin.refreshToken
$env:R47_ACCOUNT_ID = [string]$NormalLogin.profile.accountId

$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R47 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'runtime-daily-use-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-047'
    frc = 'FRC-3B Runtime daily-use record, file, import/export, and state closure'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    runtimeRoleId = [string]$RuntimeRole.roleId
    readonlyRoleId = [string]$ReadonlyRole.roleId
    groupPublishedVersion = [string]$GroupPublish.version
    emptySearchTotal = [int]$EmptySearch.page.total
    recordId = $RecordId
    uploadedFileId = $UploadedFileId
    uploadedFileName = $UploadedFileName
    detailAttachmentCount = @($DetailAttachmentNames).Count
    detailHistoryCount = @($DetailHistoryRows).Count
    importFileId = $ImportFileId
    importPrecheckId = [string]$Precheck.precheckId
    importValidRows = [int]$Precheck.validRows
    importTaskId = [string]$ImportConfirm.task.taskId
    importTaskStatus = [string]$ImportConfirm.task.status
    importInsertedCount = [int]$ImportConfirm.task.partialSuccessCount
    exportTaskId = [string]$Export.task.taskId
    exportTaskStatus = [string]$Export.task.status
    exportResultFileId = [string]$Export.expectedResultFile.fileId
    runtimeSearchTotal = [int]$RuntimeSearch.page.total
    hiddenFieldLeaked = @($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    forbiddenCreateStatus = $ForbiddenCreate.status
    forbiddenAdminStatus = $ForbiddenAdmin.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
}
Assert-True -Condition ($Result.browserBlockerCount -eq 0 -and $Result.browserOverflowCount -eq 0) -Message 'R47 browser audit reported blockers or horizontal overflow.'
Assert-True -Condition ($Result.hiddenFieldLeaked -eq $false) -Message 'Hidden field leaked into normal runtime list schema.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R47 Runtime Daily-Use Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId / $($Module.name)",
    "- Empty search total: $($Result.emptySearchTotal)",
    "- Record: $RecordId",
    "- Uploaded file: $UploadedFileName / $UploadedFileId",
    "- Import: precheck=$($Result.importPrecheckId), validRows=$($Result.importValidRows), task=$($Result.importTaskId), inserted=$($Result.importInsertedCount)",
    "- Export: task=$($Result.exportTaskId), resultFile=$($Result.exportResultFileId)",
    "- Runtime total: $($Result.runtimeSearchTotal), hiddenFieldLeaked=$($Result.hiddenFieldLeaked)",
    "- Permission negatives: readonlyCreate=$($Result.forbiddenCreateStatus), normalAdmin=$($Result.forbiddenAdminStatus)",
    "- Browser results: $($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r47-runtime-daily-use/runtime-daily-use-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}
