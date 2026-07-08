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
            return @{ status = 403; path = $Path }
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
    param([string]$Code, [string]$Name, [string]$Type = 'TEXT', [bool]$Required = $false)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
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
                reason = 'recovery-r46 page surface cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r46-$script:Suffix-$systemId"
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

$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r46-page-surface'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r46-page-surface-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r46-page-surface-2026-07-01.md'
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
    systemName = "R46 Page Surface $script:Suffix"
    systemCode = "r46_page_surface_$script:Suffix"
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
    reason = 'recovery-r46 admin setup'
}

$SystemRoles = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/roles?pageSize=100" -Headers $script:AdminHeaders
$SystemSuperAdminRole = @($SystemRoles.records | Where-Object { $_.roleCode -eq 'SYSTEM_SUPER_ADMIN' }) | Select-Object -First 1
Assert-True -Condition ($null -ne $SystemSuperAdminRole -and -not [string]::IsNullOrWhiteSpace([string]$SystemSuperAdminRole.roleId)) `
    -Message 'System super admin role id was not found for R46 runtime visibility setup.'

$HomeTitle = "R46 Home Surface $script:Suffix"
$HomeSubtitle = 'Page definition and runtime surface stay separated.'
$HomePayload = @{
    title = $HomeTitle
    subtitle = $HomeSubtitle
    visualTone = 'task-surface'
    widgets = @(
        @{ widgetCode = 'overview'; widgetName = 'R46 Overview'; widgetType = 'metric'; sourceType = 'WORK_DASHBOARD'; sortOrder = 10; visible = $true },
        @{ widgetCode = 'warnings'; widgetName = 'R46 Warnings'; widgetType = 'list'; sourceType = 'WORK_WARNING'; sortOrder = 20; visible = $true },
        @{ widgetCode = 'calendar'; widgetName = 'R46 Calendar'; widgetType = 'calendar'; sourceType = 'WORK_CALENDAR'; sortOrder = 30; visible = $true },
        @{ widgetCode = 'modules'; widgetName = 'R46 Runtime Modules'; widgetType = 'shortcut'; sourceType = 'RUNTIME_MODULES'; sortOrder = 40; visible = $true }
    )
    changeReason = 'recovery-r46 home surface contract'
}
$SavedHome = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$SystemId/work/home-page-config" -Headers $script:AdminHeaders -Body $HomePayload
$HomeAdminReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/work/home-page-config" -Headers $script:AdminHeaders
$HomeRuntimeReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/work/home-page" -Headers $script:AdminHeaders
$HomePublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/work/home-page-config/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($SavedHome.title -eq $HomeTitle -and $HomeAdminReadback.title -eq $HomeTitle -and $HomeRuntimeReadback.title -eq $HomeTitle) `
    -Message 'Home page title did not survive save/admin readback/runtime readback.'
Assert-True -Condition (@($HomeAdminReadback.widgets).Count -ge 4 -and $HomePublishCheck.passed -eq $true) `
    -Message 'Home page config widgets or publish-check did not pass.'

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R46 Runtime Member $script:Suffix"
    roleCode = "R46_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R46 runtime member'
}
$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R46 Operations $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId, [string]$SystemSuperAdminRole.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$Group.groupId
    moduleCode = "r46_request_$script:Suffix"
    name = "R46 Request $script:Suffix"
    status = 1
    description = 'Recovery R46 page definition module'
}
$ModuleId = [string]$Module.moduleId

$PublicField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'R46 Public Name' -Type 'TEXT' -Required $true)
$StatusField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'statusText' -Name 'R46 Status Text' -Type 'TEXT')
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'amountValue' -Name 'R46 Amount Value' -Type 'NUMBER')
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'R46 Secret Note' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'r46_default'
    sceneName = 'R46 Runtime View'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId, [string]$SystemSuperAdminRole.roleId)
    columnFieldIds = @([string]$PublicField.fieldId, [string]$StatusField.fieldId, [string]$AmountField.fieldId)
    filterFieldIds = @([string]$PublicField.fieldId, [string]$StatusField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$RolePermission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ $ModuleId = $true }
    actionPermissions = @{
        'record.read' = $true
        'record.create' = $false
        'record.edit' = $false
        'record.delete' = $false
    }
    fieldPermissions = @{
        "$ModuleId.publicName" = 'READABLE'
        "$ModuleId.statusText" = 'READABLE'
        "$ModuleId.amountValue" = 'READABLE'
        "$ModuleId.secretNote" = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'ALL' })
    denyPolicies = @('record.create')
}

$PagePayload = @{
    pageCode = 'main'
    pageName = "R46 Main Page $script:Suffix"
    pageType = 'MODULE_LIST'
    route = "/systems/$SystemId/modules/$ModuleId"
    layoutMode = 'left-list-right-detail'
    components = @(
        @{ componentCode = 'toolbar'; componentType = 'SHORTCUT'; title = 'R46 Actions'; dataSource = 'MODULE_ACTIONS'; boundFieldCode = $null; sort = 10; visible = $true; props = @{ density = 'compact' } },
        @{ componentCode = 'list'; componentType = 'LIST'; title = 'R46 Records'; dataSource = 'RUNTIME_RECORDS'; boundFieldCode = $null; sort = 20; visible = $true; props = @{ rowClick = 'detail' } },
        @{ componentCode = 'detail'; componentType = 'DETAIL'; title = 'R46 Detail'; dataSource = 'RECORD_DETAIL'; boundFieldCode = 'publicName'; sort = 30; visible = $true; props = @{ placement = 'right' } },
        @{ componentCode = 'chart'; componentType = 'CHART'; title = 'R46 Status Chart'; dataSource = 'RUNTIME_RECORDS'; boundFieldCode = 'statusText'; sort = 40; visible = $true; props = @{ chartType = 'bar' } },
        @{ componentCode = 'secret'; componentType = 'DETAIL'; title = 'R46 Secret Component'; dataSource = 'RECORD_DETAIL'; boundFieldCode = 'secretNote'; sort = 50; visible = $true; props = @{ visibility = 'permission-bound' } }
    )
    visibleRoleIds = @()
    changeReason = 'recovery-r46 page definition and component catalog'
}
$SavedPage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders -Body $PagePayload
$PageReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders
$PagePublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PagePublishCheck.passed -eq $true) -Message "Page publish-check did not pass: $($PagePublishCheck | ConvertTo-Json -Depth 40 -Compress)"
$PublishedPage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r46 page publish'
    idempotencyKey = "page-publish-r46-$script:Suffix"
}
$AdminSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/schema?snapshot=published" -Headers $script:AdminHeaders
Assert-True -Condition (@($AdminSchema.components).Count -eq 5 -and @($AdminSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 1) `
    -Message 'Admin published schema did not contain expected page components and secret field.'

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r46 module publish'
    idempotencyKey = "module-publish-r46-$script:Suffix"
}
$GroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($Group.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r46 group publish'
    idempotencyKey = "group-publish-r46-$script:Suffix"
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
    fieldValues = @{
        publicName = "R46 Visible Record $script:Suffix"
        statusText = 'Open'
        amountValue = 46
        secretNote = "R46 Hidden Secret $script:Suffix"
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R46_PAGE_SURFACE'
}

$NormalLoginName = "r46_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$((Get-Date).ToString('HHmmssfff'))"
    email = "r46_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R46 Owned $script:Suffix"
    systemCode = "r46_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:CreatedSystemIds.Add($NormalOwnedSystemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R46 Runtime Member $script:Suffix"
    employeeNo = "R46M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r46_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r46 normal runtime'
}

$NormalHomeReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/work/home-page" -Headers $NormalHeaders
$NormalRuntimePage = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/pages/main" -Headers $NormalHeaders
$NormalRuntimeSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/pages/main/schema" -Headers $NormalHeaders
$ForbiddenHomeWrite = Invoke-ExpectedForbidden -Method 'Patch' -Path "/api/v1/systems/$SystemId/work/home-page-config" -Headers $NormalHeaders -Body $HomePayload
$ForbiddenPageWrite = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages" -Headers $NormalHeaders -Body $PagePayload
Assert-True -Condition ($NormalHomeReadback.title -eq $HomeTitle) -Message 'Normal member could not read configured runtime home.'
Assert-True -Condition ($NormalRuntimePage.publishedVersion -eq $PublishedPage.version) -Message 'Normal runtime page did not read the published page version.'
Assert-True -Condition (@($NormalRuntimeSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -eq 0) -Message 'Hidden-field-bound component leaked into normal runtime schema.'
Assert-True -Condition (@($NormalRuntimeSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into normal runtime schema.'
Assert-True -Condition (@($NormalRuntimeSchema.components | Where-Object { $_.componentCode -in @('toolbar','list','detail','chart') }).Count -eq 4) `
    -Message 'Normal runtime schema lost visible page components.'

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$BrowserWorkDir = Join-Path $RepoRoot '.tmp-browser'
New-Item -ItemType Directory -Force -Path $BrowserWorkDir | Out-Null
$script:ChromeProfileDir = Join-Path $BrowserWorkDir "unexamine-r46-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
    '--headless',
    '--disable-gpu',
    '--disable-extensions',
    '--disable-software-rasterizer',
    '--no-sandbox',
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

$nodeScript = Join-Path $BrowserWorkDir "unexamine-r46-page-surface-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const http = require('http');
const path = require('path');

const baseUrl = process.env.R46_BASE_URL;
const port = process.env.R46_CDP_PORT;
const outDir = process.env.R46_EVIDENCE_DIR;
const systemId = process.env.R46_SYSTEM_ID;
const moduleName = process.env.R46_MODULE_NAME;
const homeTitle = process.env.R46_HOME_TITLE;
const pageName = process.env.R46_PAGE_NAME;
const secretText = process.env.R46_SECRET_TEXT;

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  const method = options.method || 'GET';
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: '127.0.0.1',
      port,
      path: pathname,
      method,
      timeout: 2000,
    }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`CDP HTTP ${response.statusCode} for ${pathname}: ${body}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on('timeout', () => request.destroy(new Error(`CDP timeout for ${pathname}`)));
    request.on('error', reject);
    request.end();
  });
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
  while (Date.now() - started < timeout) {
    const value = await evaluate(client, expression);
    if (value && value.ok) return value;
    await delay(150);
  }
  throw new Error(`Timeout waiting for ${expression}`);
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
async function clickByText(client, text) {
  return evaluate(client, `
    JSON.stringify((() => {
      const candidates = Array.from(document.querySelectorAll('button,a'));
      const target = candidates.find((el) => (el.innerText || '').trim() === ${JSON.stringify(text)});
      if (!target) return { clicked: false };
      if (target.disabled) return { clicked: false, disabled: true, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
      target.click();
      return { clicked: true, disabled: false, text: target.innerText || '', title: target.title || '', ariaLabel: target.getAttribute('aria-label') || '' };
    })())
  `);
}
async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/#/`);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    return JSON.stringify({ ok: true });
  })()`);
}
async function auditRoute(client, roleName, key, route, viewport, checks) {
  await setViewport(client, viewport);
  await navigate(client, `${baseUrl}/?r46=${Date.now()}#${route}`);
  if (key === 'module-config') {
    await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(moduleName)}) && (document.body.innerText || '').includes('\u9875\u9762') })`, 30000);
    const clickedPageTab = await clickByText(client, '\u9875\u9762');
    if (!clickedPageTab.clicked) {
      throw new Error(`Could not click page designer tab on module-config route. text=${(await evaluate(client, `JSON.stringify({ text: (document.body.innerText || '').slice(0, 1200) })`)).text}`);
    }
  }
  for (const selector of checks.selectors || []) {
    await waitFor(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}) })`, 60000);
  }
  if (checks.waitText) {
    await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(checks.waitText)}) })`, 60000);
  }
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const selectors = ${JSON.stringify(checks.selectors || [])}.map((selector) => ({ selector, visible: !!document.querySelector(selector) }));
    const requiredTextsMissing = ${JSON.stringify(checks.requiredTexts || [])}.filter((item) => !text.includes(item));
    const forbiddenTexts = ${JSON.stringify(checks.forbiddenTexts || [])}.filter((item) => text.includes(item));
    const forbiddenSelectors = ${JSON.stringify(checks.forbiddenSelectors || [])}.filter((selector) => !!document.querySelector(selector));
    const placeholderTexts = ['TODO', 'coming soon', 'Coming soon', 'placeholder'].filter((item) => text.includes(item));
    const assetScripts = Array.from(document.scripts).map((script) => script.src).filter(Boolean);
    return JSON.stringify({
      role: ${JSON.stringify(roleName)},
      key: ${JSON.stringify(key)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      selectors,
      requiredTextsMissing,
      forbiddenTexts,
      forbiddenSelectors,
      placeholderTexts,
      assetScripts,
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      textSample: text.slice(0, 1200)
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.selectors.some((item) => !item.visible)) result.blockers.push('expected selector missing');
  if (result.requiredTextsMissing.length) result.blockers.push('required text missing');
  if (result.forbiddenTexts.length) result.blockers.push('forbidden text visible');
  if (result.forbiddenSelectors.length) result.blockers.push('forbidden selector visible');
  if (result.placeholderTexts.length) result.blockers.push('placeholder copy visible');
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${roleName}-${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
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
  const roles = {
    admin: {
      accessToken: process.env.R46_ADMIN_TOKEN,
      refreshToken: process.env.R46_ADMIN_REFRESH,
      accountId: process.env.R46_ADMIN_ACCOUNT_ID || 'admin',
    },
    normal: {
      accessToken: process.env.R46_NORMAL_TOKEN,
      refreshToken: process.env.R46_NORMAL_REFRESH,
      accountId: process.env.R46_NORMAL_ACCOUNT_ID || 'normal',
    },
  };
  const results = [];
  await setStorage(client, roles.admin);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'admin', 'dashboard', `/systems/${systemId}/dashboard`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-overview="true"]', '[data-home-operations="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-module-page-designer="true"]', '[data-page-component-workbench="true"]'],
      forbiddenTexts: [secretText],
    }));
    results.push(await auditRoute(client, 'admin', 'dashboard-config', `/systems/${systemId}/admin/dashboard-config`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-config-panel="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText],
    }));
    results.push(await auditRoute(client, 'admin', 'module-config', `/systems/${systemId}/admin/module-config`, viewport, {
      waitText: pageName,
      selectors: ['[data-module-page-designer="true"]', '[data-page-component-workbench="true"]', '[data-page-mobile-preview="true"]'],
      requiredTexts: [moduleName, pageName, 'R46 Records', 'R46 Status Chart'],
      forbiddenTexts: [],
    }));
  }
  await setStorage(client, roles.normal);
  for (const viewport of viewports) {
    results.push(await auditRoute(client, 'normal', 'dashboard', `/systems/${systemId}/dashboard`, viewport, {
      waitText: homeTitle,
      selectors: ['[data-home-overview="true"]', '[data-home-operations="true"]'],
      requiredTexts: [homeTitle],
      forbiddenSelectors: ['[data-home-config-panel="true"]', '[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText, 'System Admin', 'Platform Admin'],
    }));
    results.push(await auditRoute(client, 'normal', 'runtime-modules', `/systems/${systemId}/modules`, viewport, {
      waitText: moduleName,
      selectors: ['.runtime-shell', '.runtime-main'],
      requiredTexts: [moduleName],
      forbiddenSelectors: ['[data-home-config-panel="true"]', '[data-module-page-designer="true"]'],
      forbiddenTexts: [secretText, 'R46 Secret Note', 'R46 Secret Component'],
    }));
  }
  client.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', results };
  fs.writeFileSync(path.join(outDir, 'page-surface-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R46_BASE_URL = $BaseUrl
$env:R46_CDP_PORT = [string]$debugPort
$env:R46_EVIDENCE_DIR = $EvidenceDir
$env:R46_SYSTEM_ID = $SystemId
$env:R46_MODULE_NAME = [string]$Module.name
$env:R46_HOME_TITLE = $HomeTitle
$env:R46_PAGE_NAME = $PagePayload.pageName
$env:R46_SECRET_TEXT = "R46 Hidden Secret $script:Suffix"
$env:R46_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R46_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R46_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R46_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R46_NORMAL_REFRESH = [string]$NormalLogin.refreshToken
$env:R46_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId

$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R46 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'page-surface-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$script:CleanupResult = Remove-CreatedSystems

$VisibleComponentCodes = @($NormalRuntimeSchema.components | ForEach-Object { $_.componentCode })
$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-046'
    frc = 'FRC-1F Page definition, component catalog, and home/runtime surface closure'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    homeTitle = $HomeTitle
    homeAdminWidgetCount = @($HomeAdminReadback.widgets).Count
    homePublishCheckPassed = $HomePublishCheck.passed
    runtimeRoleId = [string]$RuntimeRole.roleId
    rolePermissionVersion = [string]$RolePermission.permissionVersion
    groupPublishedVersion = [string]$GroupPublish.version
    pageCode = 'main'
    savedPageStatus = $SavedPage.publishStatus
    pageReadbackCount = @($PageReadback).Count
    pagePublishCheckPassed = $PagePublishCheck.passed
    pagePublishedVersion = [string]$PublishedPage.version
    adminSchemaComponentCount = @($AdminSchema.components).Count
    adminSchemaFieldCount = @($AdminSchema.fields).Count
    normalRuntimePageVersion = [string]$NormalRuntimePage.publishedVersion
    normalRuntimeComponentCodes = $VisibleComponentCodes
    normalRuntimeFieldCodes = @($NormalRuntimeSchema.fields | ForEach-Object { $_.fieldCode })
    hiddenComponentLeaked = @($NormalRuntimeSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -gt 0
    hiddenFieldLeaked = @($NormalRuntimeSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    forbiddenHomeWriteStatus = $ForbiddenHomeWrite.status
    forbiddenPageWriteStatus = $ForbiddenPageWrite.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
}
Assert-True -Condition ($Result.browserBlockerCount -eq 0 -and $Result.browserOverflowCount -eq 0) -Message 'R46 browser audit reported blockers or horizontal overflow.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R46 Page Surface Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Home title: $HomeTitle",
    "- Home widgets: $($Result.homeAdminWidgetCount), publish-check=$($Result.homePublishCheckPassed)",
    "- Module: $ModuleId / $($Module.name)",
    "- Page: main, publishedVersion=$($Result.pagePublishedVersion), admin components=$($Result.adminSchemaComponentCount)",
    "- Normal runtime components: $(@($Result.normalRuntimeComponentCodes) -join ', ')",
    "- Hidden field leaked: $($Result.hiddenFieldLeaked); hidden component leaked: $($Result.hiddenComponentLeaked)",
    "- Permission negatives: homeWrite=$($Result.forbiddenHomeWriteStatus), pageWrite=$($Result.forbiddenPageWriteStatus)",
    "- Browser results: $($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r46-page-surface/page-surface-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}
