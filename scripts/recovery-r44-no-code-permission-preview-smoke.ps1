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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -eq 403) {
            return @{ status = $status }
        }
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        throw "Expected HTTP 403 but got HTTP ${status}: $($reader.ReadToEnd())"
    }
    throw "Expected forbidden response but request succeeded: $Method $Path"
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
        [string]$Type = 'TEXT',
        [bool]$Required = $false,
        [string]$DictTypeId = $null
    )
    $storage = switch ($Type) {
        'NUMBER' { 'DECIMAL' }
        'DATE' { 'DATETIME' }
        'DATETIME' { 'DATETIME' }
        'MULTI_SELECT' { 'JSON' }
        'ATTACHMENT' { 'JSON' }
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
        filterOperators = @('EQ', 'LIKE', 'IN')
        maskRule = 'NONE'
        permissionMetadata = @{
            readableRoleIds = @()
            writableRoleIds = @()
            runtimeReadable = $true
            runtimeWritable = $true
            maskedWhenDenied = 'HIDDEN'
            permissionVersion = "r44_field_perm_$script:Suffix"
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
                reason = 'recovery-r44 no-code permission preview cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r44-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r44-no-code-permission-preview'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r44-no-code-permission-preview-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r44-no-code-permission-preview-2026-06-30.md'
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
    systemName = "R44 NoCode Permission $script:Suffix"
    systemCode = "r44_nocode_$script:Suffix"
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
    reason = 'recovery-r44 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R44 Runtime Member $script:Suffix"
    roleCode = "R44_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R44 runtime role'
}
$HiddenRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R44 Hidden Role $script:Suffix"
    roleCode = "R44_HIDDEN_ROLE_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R44 hidden navigation role'
}

$VisibleGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R44 Visible Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$HiddenGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R44 Hidden Group $script:Suffix"
    sort = 20
    visibleRoleIds = @([string]$HiddenRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r44_visible_$script:Suffix"
    name = "R44 Visible Module $script:Suffix"
    status = 1
    description = 'Recovery R44 visible runtime module'
}
$ModuleId = [string]$Module.moduleId
$HiddenModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$HiddenGroup.groupId
    moduleCode = "r44_hidden_$script:Suffix"
    name = "R44 Hidden Module $script:Suffix"
    status = 1
    description = 'Recovery R44 hidden runtime module'
}
$HiddenModuleId = [string]$HiddenModule.moduleId

$Dict = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types" -Headers $script:AdminHeaders -Body @{
    dictCode = "r44_status_$script:Suffix"
    dictName = "R44 Status Dict $script:Suffix"
    dictKind = 'STATUS'
    status = 1
}
$DictItemOpen = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/items" -Headers $script:AdminHeaders -Body @{
    itemCode = 'OPEN'
    itemName = 'Open'
    color = '#2563eb'
    icon = 'circle'
    semantic = 'CURRENT'
    sort = 10
    defaultFlag = $true
    kanbanEnabled = $true
    status = 1
}
$DictItemDone = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/dict-types/$($Dict.dictTypeId)/items" -Headers $script:AdminHeaders -Body @{
    itemCode = 'DONE'
    itemName = 'Done'
    color = '#16a34a'
    icon = 'check'
    semantic = 'DONE'
    sort = 20
    defaultFlag = $false
    kanbanEnabled = $true
    status = 1
}

$PublicField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'R44 Public Name' -Type 'TEXT' -Required $true)
$StatusField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'statusCode' -Name 'R44 Status' -Type 'SELECT' -DictTypeId ([string]$Dict.dictTypeId))
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'R44 Secret Note' -Type 'TEXT')

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = "r44_runtime_scene_$script:Suffix"
    sceneName = "R44 Runtime Scene $script:Suffix"
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$PublicField.fieldId, [string]$StatusField.fieldId, [string]$SecretField.fieldId)
    filterFieldIds = @([string]$PublicField.fieldId, [string]$StatusField.fieldId)
    sortFieldIds = @([string]$PublicField.fieldId)
}

$RolePermission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ $ModuleId = $true; $HiddenModuleId = $false }
    actionPermissions = @{
        'record.create' = $false
        'record.edit' = $false
        'record.delete' = $false
        'record.submitApproval' = $false
    }
    fieldPermissions = @{
        publicName = 'READABLE'
        statusCode = 'READABLE'
        secretNote = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'SELF'; expression = 'ownerMemberId == currentMember' })
    denyPolicies = @('record.create')
}

$PermissionReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders
Assert-True -Condition ($PermissionReadback.actionPermissions.'record.create' -eq $false) -Message 'Role permission readback did not keep record.create=false.'
Assert-True -Condition ($PermissionReadback.fieldPermissions.secretNote -eq 'HIDDEN') -Message 'Role permission readback did not keep secretNote=HIDDEN.'

$Preview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/permissions/effective/preview" -Headers $script:AdminHeaders -Body @{
    roleIds = @([string]$RuntimeRole.roleId)
    moduleId = $ModuleId
    actionCode = 'record.create'
}
Assert-True -Condition ($Preview.allowed -eq $false) -Message 'Effective permission preview should deny record.create.'
Assert-True -Condition ($Preview.fieldMaskRules.secretNote -eq 'HIDDEN') -Message 'Effective permission preview should explain hidden secretNote.'

$AdminListSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/list-schema?sceneId=$($Scene.sceneId)" -Headers $script:AdminHeaders
$StatusFilter = @($AdminListSchema.filters | Where-Object { $_.fieldCode -eq 'statusCode' }) | Select-Object -First 1
Assert-True -Condition (@($StatusFilter.options).Count -ge 2) -Message 'Dictionary-bound status filter did not read back dict items.'

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R44 publish visible module'
    idempotencyKey = "r44-publish-module-$script:Suffix"
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($VisibleGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R44 publish visible group'
    idempotencyKey = "r44-publish-group-$script:Suffix"
}

$NormalLoginName = "r44_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r44_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R44 Owned $script:Suffix"
    systemCode = "r44_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:CreatedSystemIds.Add($NormalOwnedSystemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R44 Runtime Member $script:Suffix"
    employeeNo = "R44M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r44_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r44 normal runtime readback'
}

$NormalSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/list-schema" -Headers $NormalHeaders
Assert-True -Condition (@($NormalSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into normal runtime list schema.'
Assert-True -Condition (@($NormalSchema.columns | Where-Object { $_.fieldCode -eq 'statusCode' }).Count -eq 1) -Message 'Dictionary-bound readable field missing from normal runtime list schema.'
Assert-True -Condition (@($NormalSchema.toolbarActions | Where-Object { $_.actionCode -eq 'record.create' -and $_.enabled -eq $false }).Count -ge 1) -Message 'Normal create action should be disabled by role permission.'

$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        publicName = "R44 forbidden write $script:Suffix"
        statusCode = 'OPEN'
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R44_FORBIDDEN_WRITE'
}
$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/actions" -Headers $NormalHeaders

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r44-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$chromeArgs = @(
    "--headless=new",
    "--disable-gpu",
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    "--no-first-run",
    "--no-default-browser-check",
    "about:blank"
)
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 1200

$nodeScript = Join-Path $env:TEMP "unexamine-r44-no-code-permission-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');
const baseUrl = process.env.R44_BASE_URL;
const debugPort = process.env.R44_DEBUG_PORT;
const outDir = process.env.R44_EVIDENCE_DIR;
const systemId = process.env.R44_SYSTEM_ID;
const visibleModule = process.env.R44_VISIBLE_MODULE_NAME;
const hiddenModule = process.env.R44_HIDDEN_MODULE_NAME;
const admin = {
  accountId: process.env.R44_ADMIN_ACCOUNT_ID,
  accessToken: process.env.R44_ADMIN_TOKEN,
  refreshToken: process.env.R44_ADMIN_REFRESH
};
const normal = {
  accountId: process.env.R44_NORMAL_ACCOUNT_ID,
  accessToken: process.env.R44_NORMAL_TOKEN,
  refreshToken: process.env.R44_NORMAL_REFRESH
};

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
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        if (msg.error) reject(new Error(JSON.stringify(msg.error)));
        else resolve(msg.result);
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
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });
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

async function waitUntil(client, expression, timeout = 10000) {
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
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile
  });
  await client.send('Page.navigate', { url: `${baseUrl}/?audit=${Date.now()}#${route}` });
  await waitUntil(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitUntil(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 10000);
  if (checks.selectors.length) {
    await waitUntil(client, `JSON.stringify({ ok: ${JSON.stringify(checks.selectors)}.some((selector) => !!document.querySelector(selector)) })`, 20000).catch(() => ({ ok: false }));
  }
  const result = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const selectors = ${JSON.stringify(checks.selectors)}.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    const forbiddenTexts = ${JSON.stringify(checks.forbiddenTexts)}.filter((item) => text.includes(item));
    const requiredTexts = ${JSON.stringify(checks.requiredTexts)}.filter((item) => !text.includes(item));
    return JSON.stringify({
      ok: true,
      url: location.href,
      selectors,
      requiredTextsMissing: requiredTexts,
      forbiddenTexts,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      textSample: text.slice(0, 800)
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
    results.push(await auditRoute(client, 'admin', 'role-permission', `/systems/${systemId}/admin/role-management`, viewport, {
      selectors: ['[data-role-permission-workbench="true"]', '[data-permission-preview-panel="true"]'],
      requiredTexts: [],
      forbiddenTexts: []
    }));
  }
  await setStorage(client, normal);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'normal', 'runtime-permission', `/systems/${systemId}/modules`, viewport, {
      selectors: ['.runtime-shell', '.runtime-module-sidebar', '.runtime-main'],
      requiredTexts: [visibleModule],
      forbiddenTexts: [hiddenModule, 'R44 Secret Note', 'secretNote', '系统后台']
    }));
  }
  ws.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    results
  };
  fs.writeFileSync(path.join(outDir, 'no-code-permission-preview-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
})().catch((error) => {
  console.error(error.stack || String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R44_BASE_URL = $BaseUrl
$env:R44_DEBUG_PORT = [string]$debugPort
$env:R44_EVIDENCE_DIR = $EvidenceDir
$env:R44_SYSTEM_ID = $SystemId
$env:R44_VISIBLE_MODULE_NAME = [string]$Module.name
$env:R44_HIDDEN_MODULE_NAME = [string]$HiddenModule.name
$env:R44_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R44_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R44_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R44_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R44_NORMAL_REFRESH = [string]$NormalLogin.refreshToken
$env:R44_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId
$BrowserJson = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "Node no-code permission browser audit exited with code $LASTEXITCODE. Output: $BrowserJson"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'no-code-permission-preview-browser-audit.json'
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
    runtimeRoleId = [string]$RuntimeRole.roleId
    dictTypeId = [string]$Dict.dictTypeId
    dictItemCount = @($DictItemOpen, $DictItemDone).Count
    fieldCount = @($PublicField, $StatusField, $SecretField).Count
    permissionVersion = $RolePermission.permissionVersion
    previewAllowed = $Preview.allowed
    previewMissingPermissions = @($Preview.missingPermissions)
    previewHiddenSecret = $Preview.fieldMaskRules.secretNote
    adminStatusFilterOptionCount = @($StatusFilter.options).Count
    normalSchemaColumnCount = @($NormalSchema.columns).Count
    normalSecretColumnLeaked = @($NormalSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    normalCreateDisabled = @($NormalSchema.toolbarActions | Where-Object { $_.actionCode -eq 'record.create' -and $_.enabled -eq $false }).Count -ge 1
    forbiddenCreateStatus = $ForbiddenCreate.status
    forbiddenAdminStatus = $ForbiddenAdmin.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    cleanup = $script:CleanupResult
}

Assert-True -Condition ($Result.browserBlockerCount -eq 0) -Message 'R44 browser audit reported blockers.'
Assert-True -Condition ($Result.browserOverflowCount -eq 0) -Message 'R44 browser audit reported horizontal overflow.'

$Result | ConvertTo-Json -Depth 80 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R44 No-Code Permission Preview Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId",
    "- Dictionary: $($Dict.dictTypeId), items=$($Result.dictItemCount), status filter options=$($Result.adminStatusFilterOptionCount)",
    "- Role permission version: $($Result.permissionVersion)",
    "- Preview denied record.create: $($Result.previewAllowed -eq $false), hidden secret rule=$($Result.previewHiddenSecret)",
    "- Normal schema columns: $($Result.normalSchemaColumnCount), secret leaked=$($Result.normalSecretColumnLeaked), create disabled=$($Result.normalCreateDisabled)",
    "- Forbidden create/admin statuses: $($Result.forbiddenCreateStatus) / $($Result.forbiddenAdminStatus)",
    "- Browser results: $($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r44-no-code-permission-preview/no-code-permission-preview-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 80

if ($NoFailExit) {
    exit 0
}
