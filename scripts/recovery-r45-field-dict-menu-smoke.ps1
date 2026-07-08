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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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
            return @{ status = 403 }
        }
        throw
    }
    throw "Expected HTTP 403 but request succeeded: $Method $Path"
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
        [string]$DictTypeId = $null,
        [string]$DefaultValue = $null,
        [hashtable]$ValidationRules = @{},
        [hashtable]$TypeConfig = @{},
        [bool]$Required = $false
    )
    $storage = switch ($Type) {
        'NUMBER' { 'DECIMAL' }
        'DATE' { 'DATETIME' }
        'DATETIME' { 'DATETIME' }
        'MULTI_SELECT' { 'JSON' }
        'ATTACHMENT' { 'JSON' }
        'IMAGE' { 'JSON' }
        'RELATION' { 'JSON' }
        'CHILD_TABLE' { 'JSON' }
        'LONG_TEXT' { 'TEXT' }
        default { 'VARCHAR' }
    }
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = $storage
        required = $Required
        sortable = $true
        dictTypeId = $DictTypeId
        defaultValue = $DefaultValue
        validationRules = $ValidationRules
        typeConfig = $TypeConfig
        maskRule = 'NONE'
        permissionMetadata = @{
            readableRoleIds = @()
            writableRoleIds = @()
            runtimeReadable = $true
            runtimeWritable = $true
            maskedWhenDenied = 'HIDDEN'
            permissionVersion = "r45_field_perm_$script:Suffix"
        }
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
                reason = 'recovery-r45 field dictionary menu cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r45-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r45-field-dict-menu'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r45-field-dict-menu-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r45-field-dict-menu-2026-07-01.md'
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
    systemName = "R45 Field Dict Menu $script:Suffix"
    systemCode = "r45_field_dict_$script:Suffix"
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
    reason = 'recovery-r45 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R45 Runtime Member $script:Suffix"
    roleCode = "R45_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R45 runtime role'
}
$HiddenRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R45 Hidden Role $script:Suffix"
    roleCode = "R45_HIDDEN_ROLE_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R45 hidden menu role'
}

$VisibleGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R45 Visible Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$HiddenGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R45 Hidden Group $script:Suffix"
    sort = 20
    visibleRoleIds = @([string]$HiddenRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r45_visible_$script:Suffix"
    name = "R45 Visible Module $script:Suffix"
    status = 1
    description = 'Recovery R45 visible runtime module'
}
$ModuleId = [string]$Module.moduleId
$HiddenModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$HiddenGroup.groupId
    moduleCode = "r45_hidden_$script:Suffix"
    name = "R45 Hidden Module $script:Suffix"
    status = 1
    description = 'Recovery R45 hidden runtime module'
}
$HiddenModuleId = [string]$HiddenModule.moduleId

$Dict = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types" -Headers $script:AdminHeaders -Body @{
    dictCode = "r45_status_$script:Suffix"
    dictName = "R45 Status Dict $script:Suffix"
    dictKind = 'STATUS'
    status = 1
}
$ActiveItem = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/items" -Headers $script:AdminHeaders -Body @{
    itemCode = 'ACTIVE'
    itemName = 'Active'
    color = '#16a34a'
    icon = 'check'
    semantic = 'CURRENT'
    sort = 10
    defaultFlag = $true
    kanbanEnabled = $true
    status = 1
}
$PendingItem = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/items" -Headers $script:AdminHeaders -Body @{
    itemCode = 'PENDING'
    itemName = 'Pending'
    color = '#f59e0b'
    icon = 'clock'
    semantic = 'WAITING'
    sort = 20
    defaultFlag = $false
    kanbanEnabled = $true
    status = 1
}
$DisabledItem = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/items" -Headers $script:AdminHeaders -Body @{
    itemCode = 'DISABLED_OLD'
    itemName = 'Disabled Old'
    color = '#ef4444'
    icon = 'ban'
    semantic = 'DISABLED'
    sort = 30
    defaultFlag = $false
    kanbanEnabled = $false
    status = 0
}

$FieldCodePrefix = "r45$((Get-Date).ToString('HHmmssfff'))"
$LongFieldCode = "${FieldCodePrefix}Long"
$AmountFieldCode = "${FieldCodePrefix}Amount"
$DateFieldCode = "${FieldCodePrefix}Date"
$DatetimeFieldCode = "${FieldCodePrefix}Time"
$SelectFieldCode = "${FieldCodePrefix}Status"
$MultiSelectFieldCode = "${FieldCodePrefix}Tags"
$UserFieldCode = "${FieldCodePrefix}User"
$DeptFieldCode = "${FieldCodePrefix}Dept"
$AttachmentFieldCode = "${FieldCodePrefix}Files"
$ImageFieldCode = "${FieldCodePrefix}Images"
$RelationFieldCode = "${FieldCodePrefix}Relation"
$ChildFieldCode = "${FieldCodePrefix}Child"
$FieldBodies = @()
$FieldBodies += (New-FieldBody -Code $LongFieldCode -Name 'R45 Long Note' -Type 'LONG_TEXT' -DefaultValue 'default note' -ValidationRules @{ maxLength = 500 } -TypeConfig @{ rows = 4 })
$FieldBodies += (New-FieldBody -Code $AmountFieldCode -Name 'R45 Amount Number' -Type 'NUMBER' -DefaultValue '12.50' -ValidationRules @{ min = 0; max = 9999 } -TypeConfig @{ precision = 2 })
$FieldBodies += (New-FieldBody -Code $DateFieldCode -Name 'R45 Plan Date' -Type 'DATE' -ValidationRules @{ requiredWhen = 'published' })
$FieldBodies += (New-FieldBody -Code $DatetimeFieldCode -Name 'R45 Plan Time' -Type 'DATETIME')
$FieldBodies += (New-FieldBody -Code $SelectFieldCode -Name 'R45 Status' -Type 'SELECT' -DictTypeId ([string]$Dict.dictTypeId) -DefaultValue 'ACTIVE')
$FieldBodies += (New-FieldBody -Code $MultiSelectFieldCode -Name 'R45 Status Tags' -Type 'MULTI_SELECT' -DictTypeId ([string]$Dict.dictTypeId) -TypeConfig @{ maxSelected = 3 })
$FieldBodies += (New-FieldBody -Code $UserFieldCode -Name 'R45 Owner User' -Type 'USER')
$FieldBodies += (New-FieldBody -Code $DeptFieldCode -Name 'R45 Owner Dept' -Type 'DEPARTMENT')
$FieldBodies += (New-FieldBody -Code $AttachmentFieldCode -Name 'R45 Attachments' -Type 'ATTACHMENT')
$FieldBodies += (New-FieldBody -Code $ImageFieldCode -Name 'R45 Images' -Type 'IMAGE')
$FieldBodies += (New-FieldBody -Code $RelationFieldCode -Name 'R45 Relation' -Type 'RELATION' -TypeConfig @{ targetModule = 'self' })
$FieldBodies += (New-FieldBody -Code $ChildFieldCode -Name 'R45 Child Rows' -Type 'CHILD_TABLE' -TypeConfig @{ childSchema = @('name','qty') })
$CreatedFields = @()
foreach ($body in $FieldBodies) {
    try {
        $CreatedFields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body $body
    } catch {
        throw "Create field failed for fieldCode=$($body.fieldCode), fieldType=$($body.fieldType): $($_.Exception.Message)"
    }
}

$FieldsReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields?pageNo=1&pageSize=100" -Headers $script:AdminHeaders
$ReadbackTypes = @($FieldsReadback.records | ForEach-Object { $_.fieldType } | Sort-Object -Unique)
foreach ($expectedType in @('LONG_TEXT','NUMBER','DATE','DATETIME','SELECT','MULTI_SELECT','USER','DEPARTMENT','ATTACHMENT','IMAGE','RELATION','CHILD_TABLE')) {
    Assert-True -Condition ($ReadbackTypes -contains $expectedType) -Message "Missing field type readback: $expectedType"
}
$AmountField = @($FieldsReadback.records | Where-Object { $_.fieldCode -eq $AmountFieldCode }) | Select-Object -First 1
Assert-True -Condition ([string]$AmountField.defaultValue -eq '12.50') -Message 'Field defaultValue did not read back.'
Assert-True -Condition ([string]$AmountField.validationRules.min -eq '0') -Message 'Field validationRules did not read back.'
Assert-True -Condition ([string]$AmountField.typeConfig.precision -eq '2') -Message 'Field typeConfig did not read back.'

$DictImpact = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/impact" -Headers $script:AdminHeaders
Assert-True -Condition ($DictImpact.fieldReferenceCount -ge 2) -Message 'Dictionary impact did not count field references.'
Assert-True -Condition ($DictImpact.disabledItemCount -eq 1) -Message 'Dictionary impact did not count disabled items.'
$DictPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R45 dictionary publish'
    idempotencyKey = "r45-dict-publish-$script:Suffix"
}
Assert-True -Condition ([string]$DictPublish.version -match '^DICT_TYPE_v') -Message 'Dictionary publish version was not generated.'

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = "r45_runtime_scene_$script:Suffix"
    sceneName = "R45 Runtime Scene $script:Suffix"
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @($CreatedFields | ForEach-Object { [string]$_.fieldId })
    filterFieldIds = @($CreatedFields | Where-Object { $_.fieldCode -in @($SelectFieldCode,$MultiSelectFieldCode,$AmountFieldCode) } | ForEach-Object { [string]$_.fieldId })
    sortFieldIds = @($CreatedFields | Where-Object { $_.fieldCode -eq $AmountFieldCode } | ForEach-Object { [string]$_.fieldId })
}

$AdminListSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/list-schema?sceneId=$($Scene.sceneId)" -Headers $script:AdminHeaders
$StatusFilter = @($AdminListSchema.filters | Where-Object { $_.fieldCode -eq $SelectFieldCode }) | Select-Object -First 1
$OptionCodes = @($StatusFilter.options | ForEach-Object { $_.itemCode })
Assert-True -Condition ($OptionCodes -contains 'ACTIVE' -and $OptionCodes -contains 'PENDING') -Message 'Active dictionary items missing from list schema.'
Assert-True -Condition (-not ($OptionCodes -contains 'DISABLED_OLD')) -Message 'Disabled dictionary item leaked into runtime list schema options.'

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R45 publish visible module'
    idempotencyKey = "r45-publish-module-$script:Suffix"
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$HiddenModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R45 publish hidden module'
    idempotencyKey = "r45-publish-hidden-module-$script:Suffix"
}
$VisibleGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($VisibleGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R45 publish visible group'
    idempotencyKey = "r45-publish-visible-group-$script:Suffix"
}
$HiddenGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($HiddenGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R45 publish hidden group'
    idempotencyKey = "r45-publish-hidden-group-$script:Suffix"
}

$RolePermission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ $ModuleId = $true; $HiddenModuleId = $false }
    actionPermissions = @{ 'record.create' = $false }
    fieldPermissions = @{}
    dataScopeRules = @(@{ type = 'SELF'; expression = 'ownerMemberId == currentMember' })
    denyPolicies = @('record.create')
}

$NormalLoginName = "r45_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$((Get-Date).ToString('HHmmssfff'))"
    email = "r45_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R45 Owned $script:Suffix"
    systemCode = "r45_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:CreatedSystemIds.Add($NormalOwnedSystemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R45 Runtime Member $script:Suffix"
    employeeNo = "R45M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r45_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}

$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r45 normal runtime readback'
}

$NormalSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/list-schema" -Headers $NormalHeaders
Assert-True -Condition (@($NormalSchema.columns | Where-Object { $_.fieldCode -eq $SelectFieldCode }).Count -eq 1) -Message 'Normal runtime schema missing dictionary field.'
Assert-True -Condition (@($NormalSchema.columns | Where-Object { $_.fieldCode -eq $ChildFieldCode }).Count -eq 1) -Message 'Normal runtime schema missing child-table boundary field.'
$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/impact" -Headers $NormalHeaders

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r45-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
    '--headless=new',
    '--disable-gpu',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    '--no-first-run',
    '--no-default-browser-check',
    'about:blank'
) -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 1200

$nodeScript = Join-Path $env:TEMP "unexamine-r45-field-dict-menu-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');
const baseUrl = process.env.R45_BASE_URL;
const debugPort = process.env.R45_DEBUG_PORT;
const outDir = process.env.R45_EVIDENCE_DIR;
const systemId = process.env.R45_SYSTEM_ID;
const visibleModule = process.env.R45_VISIBLE_MODULE_NAME;
const hiddenModule = process.env.R45_HIDDEN_MODULE_NAME;
const admin = { accountId: process.env.R45_ADMIN_ACCOUNT_ID, accessToken: process.env.R45_ADMIN_TOKEN, refreshToken: process.env.R45_ADMIN_REFRESH };
const normal = { accountId: process.env.R45_NORMAL_ACCOUNT_ID, accessToken: process.env.R45_NORMAL_TOKEN, refreshToken: process.env.R45_NORMAL_REFRESH };

async function json(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`${url} -> ${response.status}`);
  return response.json();
}
class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.nextId = 1;
    this.pending = new Map();
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const callbacks = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? callbacks.reject(new Error(JSON.stringify(msg.error))) : callbacks.resolve(msg.result);
      }
    };
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
}
async function connect() {
  let target;
  try {
    target = await json(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await json(`http://127.0.0.1:${debugPort}/json/list`);
    target = targets[0];
  }
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  const client = new Cdp(ws);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  return { client, ws };
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function waitUntil(client, expression, timeout = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const raw = await evaluate(client, expression);
    const value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (value && value.ok) return value;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`waitUntil timeout: ${expression}`);
}
async function setStorage(client, role) {
  await client.send('Page.navigate', { url: baseUrl + '/#/' });
  await waitUntil(client, `JSON.stringify({ ok: location.origin === ${JSON.stringify(baseUrl)} })`, 5000);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    return JSON.stringify({ ok: true });
  })()`);
}
async function auditRoute(client, roleName, key, route, viewport, checks) {
  await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
  await client.send('Page.navigate', { url: `${baseUrl}/?audit=${Date.now()}#${route}` });
  await waitUntil(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitUntil(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 15000);
  if (route.includes('/admin/module-config')) {
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-module-work-tabs="true"]') })`, 30000);
    await evaluate(client, `(() => {
      const button = document.querySelector('[data-module-work-tab="fields"]');
      if (!button) throw new Error('fields tab missing');
      button.click();
      return JSON.stringify({ ok: true });
    })()`);
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') === 'fields' && !!document.querySelector('.field-type-grid') })`, 30000);
  }
  if (checks.selectors.length) {
    await waitUntil(client, `JSON.stringify({ ok: ${JSON.stringify(checks.selectors)}.some((selector) => !!document.querySelector(selector)) })`, 30000);
  }
  if (checks.requiredTexts.length) {
    await waitUntil(client, `JSON.stringify({ ok: ${JSON.stringify(checks.requiredTexts)}.every((item) => (document.body.innerText || '').includes(item)) })`, 30000);
  }
  const result = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const requiredTextsMissing = ${JSON.stringify(checks.requiredTexts)}.filter((item) => !text.includes(item));
    const forbiddenTexts = ${JSON.stringify(checks.forbiddenTexts)}.filter((item) => text.includes(item));
    const selectors = ${JSON.stringify(checks.selectors)}.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    return JSON.stringify({
      ok: true,
      url: location.href,
      selectors,
      requiredTextsMissing,
      forbiddenTexts,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      textSample: text.slice(0, 1000)
    });
  })()`);
  const parsed = JSON.parse(result);
  parsed.role = roleName;
  parsed.key = key;
  parsed.viewport = viewport.name;
  parsed.blockers = [];
  if (parsed.selectors.some((item) => !item.visible)) parsed.blockers.push('expected selector missing');
  if (parsed.requiredTextsMissing.length) parsed.blockers.push('required text missing');
  if (parsed.forbiddenTexts.length) parsed.blockers.push('forbidden text visible');
  if (parsed.overflowX > 2) parsed.blockers.push('horizontal overflow');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${roleName}-${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return parsed;
}
(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const { client, ws } = await connect();
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true }
  ];
  const results = [];
  await setStorage(client, admin);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'admin', 'module-config', `/systems/${systemId}/admin/module-config`, viewport, {
      selectors: ['.field-type-grid', '.module-builder-workspace'],
      requiredTexts: ['\u957f\u6587\u672c', '\u5b50\u8868'],
      forbiddenTexts: []
    }));
    results.push(await auditRoute(client, 'admin', 'dict-management', `/systems/${systemId}/admin/dict-management`, viewport, {
      selectors: ['#dict-management', '.row-actions'],
      requiredTexts: ['\u5f71\u54cd\u5206\u6790', '\u53d1\u5e03'],
      forbiddenTexts: []
    }));
  }
  await setStorage(client, normal);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'normal', 'runtime-menu', `/systems/${systemId}/modules`, viewport, {
      selectors: ['.runtime-shell', '.runtime-main'],
      requiredTexts: [visibleModule],
      forbiddenTexts: [hiddenModule, 'admin/module-config']
    }));
  }
  ws.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', results };
  fs.writeFileSync(path.join(outDir, 'field-dict-menu-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
})().catch((error) => {
  console.error(error.stack || String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R45_BASE_URL = $BaseUrl
$env:R45_DEBUG_PORT = [string]$debugPort
$env:R45_EVIDENCE_DIR = $EvidenceDir
$env:R45_SYSTEM_ID = $SystemId
$env:R45_VISIBLE_MODULE_NAME = [string]$Module.name
$env:R45_HIDDEN_MODULE_NAME = [string]$HiddenModule.name
$env:R45_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R45_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R45_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R45_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R45_NORMAL_REFRESH = [string]$NormalLogin.refreshToken
$env:R45_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId
$BrowserJson = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "Node field dictionary menu browser audit exited with code $LASTEXITCODE. Output: $BrowserJson"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'field-dict-menu-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    hiddenModuleId = $HiddenModuleId
    visibleGroupId = [string]$VisibleGroup.groupId
    hiddenGroupId = [string]$HiddenGroup.groupId
    visibleGroupVersion = [string]$VisibleGroupPublish.version
    hiddenGroupVersion = [string]$HiddenGroupPublish.version
    runtimeRoleId = [string]$RuntimeRole.roleId
    dictTypeId = [string]$Dict.dictTypeId
    dictPublishedVersion = [string]$DictPublish.version
    dictFieldReferenceCount = [int]$DictImpact.fieldReferenceCount
    dictDisabledItemCount = [int]$DictImpact.disabledItemCount
    dictItems = @($ActiveItem.itemCode, $PendingItem.itemCode, $DisabledItem.itemCode)
    activeSchemaOptionCodes = $OptionCodes
    fieldCount = @($CreatedFields).Count
    fieldTypes = $ReadbackTypes
    amountDefaultValue = [string]$AmountField.defaultValue
    amountValidationMin = [string]$AmountField.validationRules.min
    amountTypePrecision = [string]$AmountField.typeConfig.precision
    sceneId = [string]$Scene.sceneId
    rolePermissionVersion = [string]$RolePermission.permissionVersion
    normalSchemaColumnCount = @($NormalSchema.columns).Count
    forbiddenAdminStatus = $ForbiddenAdmin.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    cleanup = $script:CleanupResult
}

Assert-True -Condition ($Result.browserBlockerCount -eq 0) -Message 'R45 browser audit reported blockers.'
Assert-True -Condition ($Result.browserOverflowCount -eq 0) -Message 'R45 browser audit reported horizontal overflow.'

$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R45 Field Type, Dictionary Impact, And Menu Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId",
    "- Field types: $(@($Result.fieldTypes) -join ', ')",
    "- Dictionary: $($Dict.dictTypeId), publishedVersion=$($Result.dictPublishedVersion), refs=$($Result.dictFieldReferenceCount), disabled=$($Result.dictDisabledItemCount)",
    "- Active list-schema dictionary options: $(@($Result.activeSchemaOptionCodes) -join ', ')",
    "- Menu group versions: visible=$($Result.visibleGroupVersion), hidden=$($Result.hiddenGroupVersion)",
    "- Normal schema columns: $($Result.normalSchemaColumnCount)",
    "- Forbidden normal-member admin status: $($Result.forbiddenAdminStatus)",
    "- Browser results: $($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r45-field-dict-menu/field-dict-menu-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}
