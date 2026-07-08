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
                reason = 'recovery-r63 configured runtime cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r63-$script:Suffix-$systemId"
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
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R63_CONFIGURED_RUNTIME_FIRST_USE_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $originalError.Exception.Message
        cleanup = $script:CleanupResult
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
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

$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r63-configured-runtime-first-use'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r63-configured-runtime-first-use-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r63-configured-runtime-first-use-2026-07-02.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

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
    systemName = "R63 Configured Runtime $script:Suffix"
    systemCode = "r63_configured_runtime_$script:Suffix"
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
    reason = 'recovery-r63 admin configuration setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R63 Runtime Operator $script:Suffix"
    roleCode = "R63_RUNTIME_OPERATOR_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R63 normal member runtime role'
}
$ReadonlyRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R63 Readonly Member $script:Suffix"
    roleCode = "R63_READONLY_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R63 forbidden mutation role'
}
$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R63 Business Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId, [string]$ReadonlyRole.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$Group.groupId
    moduleCode = "r63_request_$script:Suffix"
    name = "R63 Business Request $script:Suffix"
    status = 1
    description = 'Recovery R63 configured module handed off to runtime'
}
$ModuleId = [string]$Module.moduleId

$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseTitle' -Name 'R63 Title' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseAmount' -Name 'R63 Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'caseOwner' -Name 'R63 Owner' -Type 'TEXT')
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'R63 Secret Note' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'r63_default'
    sceneName = 'R63 Runtime View'
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
        'record.delete' = $false
        'record.import' = $true
        'record.export' = $true
    }
    fieldPermissions = @{
        "$ModuleId.title" = 'WRITABLE'
        "$ModuleId.status" = 'WRITABLE'
        "$ModuleId.ownerDept" = 'WRITABLE'
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
        "$ModuleId.title" = 'READABLE'
        "$ModuleId.status" = 'READABLE'
        "$ModuleId.ownerDept" = 'READABLE'
        "$ModuleId.caseTitle" = 'READABLE'
        "$ModuleId.caseAmount" = 'READABLE'
        "$ModuleId.caseOwner" = 'READABLE'
        "$ModuleId.secretNote" = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'ALL' })
    denyPolicies = @('record.create', 'record.edit', 'record.import', 'record.export')
}

$ModulePublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r63 module publish'
    idempotencyKey = "module-publish-r63-$script:Suffix"
}
$GroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($Group.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r63 group publish'
    idempotencyKey = "group-publish-r63-$script:Suffix"
}
$PublishedVersion = [string]$ModulePublish.version
if ([string]::IsNullOrWhiteSpace($PublishedVersion)) {
    $PublishedVersion = [string]$GroupPublish.version
}

$NormalLoginName = "r63_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$((Get-Date).ToString('HHmmssfff'))"
    email = "r63_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R63 Owned $script:Suffix"
    systemCode = "r63_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null
$ReadonlyLoginName = "r63_readonly_$script:Suffix"
$ReadonlyRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $ReadonlyLoginName
    mobile = "15$((Get-Date).ToString('HHmmssfff'))"
    email = "r63_readonly_$script:Suffix@example.com"
    password = $Password
    systemName = "R63 Readonly Owned $script:Suffix"
    systemCode = "r63_readonly_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$ReadonlyRegister.systemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R63 Runtime Member $script:Suffix"
    employeeNo = "R63M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r63_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$ReadonlyMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R63 Readonly Member $script:Suffix"
    employeeNo = "R63R$((Get-Date).ToString('HHmmssfff'))"
    mobile = "14$((Get-Date).ToString('HHmmssfff'))"
    email = "r63_readonly_member_$script:Suffix@example.com"
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
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{ systemId = $SystemId; tenantId = $TenantId; reason = 'recovery-r63 normal runtime' }
$ReadonlyLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = $ReadonlyLoginName; password = $Password; loginTarget = 'PLATFORM' }
$ReadonlyHeaders = @{ Authorization = "Bearer $($ReadonlyLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ReadonlyHeaders -Body @{ systemId = $SystemId; tenantId = $TenantId; reason = 'recovery-r63 readonly runtime' }

$EmptySearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = "no-result-$script:Suffix"
    sceneCode = 'r63_default'
}
Assert-True -Condition ([int]$EmptySearch.page.total -eq 0) -Message 'Runtime search should start empty for the configured module.'

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$BrowserWorkDir = Join-Path $RepoRoot '.tmp-browser'
New-Item -ItemType Directory -Force -Path $BrowserWorkDir | Out-Null
$script:ChromeProfileDir = Join-Path $BrowserWorkDir "unexamine-r63-chrome-$script:Suffix"
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
Assert-True -Condition $ready -Message "Chrome DevTools endpoint did not become ready on port $debugPort."

$nodeScript = Join-Path $BrowserWorkDir "unexamine-r63-configured-runtime-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R63_BASE_URL;
const port = process.env.R63_CDP_PORT;
const outDir = process.env.R63_EVIDENCE_DIR;
const systemId = process.env.R63_SYSTEM_ID;
const moduleId = process.env.R63_MODULE_ID;
const moduleName = process.env.R63_MODULE_NAME;
const createdTitle = process.env.R63_CREATED_TITLE;
const updatedTitle = process.env.R63_UPDATED_TITLE;
const secretText = process.env.R63_SECRET_TEXT;
const role = {
  accountId: process.env.R63_ACCOUNT_ID,
  accessToken: process.env.R63_TOKEN,
  refreshToken: process.env.R63_REFRESH || '',
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
      hasDetailPanel: !!document.querySelector('[data-runtime-detail-panel="true"]'),
      hasCreateButton: !!document.querySelector('[data-runtime-create-record="true"]'),
      hasCreatePanel: !!document.querySelector('[data-runtime-edit-panel="create"]'),
      hasEditPanel: !!document.querySelector('[data-runtime-edit-panel="edit"]'),
      hasCreatedTitle: text.includes(${JSON.stringify(createdTitle)}),
      hasUpdatedTitle: text.includes(${JSON.stringify(updatedTitle)}),
      hasModuleName: text.includes(${JSON.stringify(moduleName)}),
      forbiddenTexts: [${JSON.stringify(secretText)}, 'System Admin', 'Platform Admin'].filter((item) => text.includes(item)),
      forbiddenSelectors: ['[data-module-page-designer="true"]', '[data-module-work-tabs="true"]', '[data-home-config-panel="true"]'].filter((selector) => !!document.querySelector(selector)),
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
  if (result.forbiddenSelectors.length) result.blockers.push('admin/config selector visible in runtime');
  if (result.placeholderTexts.length) result.blockers.push('placeholder copy visible');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function setField(client, fieldCode, value) {
  await evaluate(client, `(() => {
    const input = document.querySelector('input[data-field-code="${fieldCode}"]');
    if (!input) throw new Error('field missing: ${fieldCode}');
    input.value = ${JSON.stringify(value)};
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return JSON.stringify({ ok: true });
  })()`);
}
async function click(client, selector) {
  await evaluate(client, `(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) throw new Error('selector missing: ${selector}');
    el.click();
    return JSON.stringify({ ok: true });
  })()`);
}
async function auditDesktopCreateEdit(client) {
  const viewport = { name: 'desktop', width: 1440, height: 920, mobile: false };
  await setViewport(client, viewport);
  await navigate(client, `${baseUrl}/?r63=${Date.now()}#/systems/${systemId}/dashboard`);
  const dashboard = await capture(client, 'dashboard-after-publish', viewport);
  if (dashboard.forbiddenSelectors.length) dashboard.blockers.push('dashboard leaked admin configuration selectors');

  await navigate(client, `${baseUrl}/?r63=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-create-record="true"]') && (document.body.innerText || '').includes(${JSON.stringify(moduleName)}) })`);
  const emptyList = await capture(client, 'runtime-empty-list', viewport);
  if (!emptyList.hasRuntimeShell || !emptyList.hasRuntimeMain || !emptyList.hasCreateButton || !emptyList.hasModuleName) {
    emptyList.blockers.push('runtime empty list did not show configured module and create action');
  }

  await click(client, '[data-runtime-create-record="true"]');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-edit-panel="create"]') })`);
  await setField(client, 'title', createdTitle);
  await setField(client, 'status', 'OPEN');
  await setField(client, 'ownerDept', 'Runtime Team');
  await setField(client, 'caseTitle', createdTitle);
  await setField(client, 'caseAmount', '6300');
  await setField(client, 'caseOwner', 'Browser normal member');
  const createPanel = await capture(client, 'runtime-create-panel', viewport);
  if (!createPanel.hasCreatePanel) createPanel.blockers.push('create panel missing');
  await click(client, '[data-runtime-save-record="create"]');
  await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(createdTitle)}) && !!document.querySelector('[data-runtime-detail-panel="true"]') })`, 30000);
  const created = await capture(client, 'runtime-created-readback', viewport);
  if (!created.hasRuntimeTable || !created.hasDetailPanel || !created.hasCreatedTitle) {
    created.blockers.push('created record did not read back in list/detail');
  }

  await click(client, '[data-runtime-edit-record]');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-edit-panel="edit"]') })`);
  await setField(client, 'title', updatedTitle);
  await setField(client, 'status', 'OPEN');
  await setField(client, 'ownerDept', 'Runtime Team Updated');
  await setField(client, 'caseTitle', updatedTitle);
  await setField(client, 'caseAmount', '6399');
  await setField(client, 'caseOwner', 'Browser edited member');
  const editPanel = await capture(client, 'runtime-edit-panel', viewport);
  if (!editPanel.hasEditPanel) editPanel.blockers.push('edit panel missing');
  await click(client, '[data-runtime-save-record="edit"]');
  await waitFor(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(${JSON.stringify(updatedTitle)}) && !!document.querySelector('[data-runtime-detail-panel="true"]') })`, 30000);
  const edited = await capture(client, 'runtime-edited-readback', viewport);
  if (!edited.hasRuntimeTable || !edited.hasDetailPanel || !edited.hasUpdatedTitle) {
    edited.blockers.push('edited record did not read back in list/detail');
  }
  return [dashboard, emptyList, createPanel, created, editPanel, edited];
}
async function auditMobileReadback(client) {
  const viewport = { name: 'mobile', width: 390, height: 760, mobile: true };
  await setViewport(client, viewport);
  await navigate(client, `${baseUrl}/?r63=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && (document.body.innerText || '').includes(${JSON.stringify(updatedTitle)}) })`, 30000);
  const mobile = await capture(client, 'runtime-mobile-readback', viewport);
  if (!mobile.hasRuntimeShell || !mobile.hasRuntimeTable || !mobile.hasUpdatedTitle) {
    mobile.blockers.push('mobile runtime did not show updated configured record');
  }
  return [mobile];
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await setStorage(client);
  const results = [];
  results.push(...await auditDesktopCreateEdit(client));
  results.push(...await auditMobileReadback(client));
  client.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    createdTitle,
    updatedTitle,
    results,
  };
  fs.writeFileSync(path.join(outDir, 'configured-runtime-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$CreatedTitle = "R63 Browser Created $script:Suffix"
$UpdatedTitle = "R63 Browser Updated $script:Suffix"
$SecretText = "R63 Hidden Secret $script:Suffix"
$env:R63_BASE_URL = $BaseUrl
$env:R63_CDP_PORT = [string]$debugPort
$env:R63_EVIDENCE_DIR = $EvidenceDir
$env:R63_SYSTEM_ID = $SystemId
$env:R63_MODULE_ID = $ModuleId
$env:R63_MODULE_NAME = [string]$Module.name
$env:R63_CREATED_TITLE = $CreatedTitle
$env:R63_UPDATED_TITLE = $UpdatedTitle
$env:R63_SECRET_TEXT = $SecretText
$env:R63_TOKEN = [string]$NormalLogin.accessToken
$env:R63_REFRESH = [string]$NormalLogin.refreshToken
$env:R63_ACCOUNT_ID = [string]$NormalLogin.profile.accountId

$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R63 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'configured-runtime-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$RuntimeSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = $UpdatedTitle
    sceneCode = 'r63_default'
}
$UpdatedRows = @($RuntimeSearch.page.records | Where-Object { ($_.fieldValues.caseTitle -eq $UpdatedTitle) -or ($_.title -eq $UpdatedTitle) })
Assert-True -Condition ($UpdatedRows.Count -ge 1) -Message 'API readback did not find the browser-edited runtime record.'
$RecordId = [string]$UpdatedRows[0].recordId
$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$BaseFieldValues = @{}
foreach ($property in $Detail.baseFields.PSObject.Properties) {
    $BaseFieldValues[$property.Name] = [string]$property.Value
}

$HiddenFieldLeakedInList = @($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
$HiddenFieldLeakedInDetail = ($BaseFieldValues.Keys -contains 'secretNote') -or (($Detail | ConvertTo-Json -Depth 30 -Compress) -match [regex]::Escape($SecretText))
$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $ReadonlyHeaders -Body @{
    fieldValues = @{ caseTitle = "Forbidden $script:Suffix"; caseAmount = 1; caseOwner = 'Readonly'; secretNote = $SecretText }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R63_FORBIDDEN'
}
$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields?pageNo=1&pageSize=20" -Headers $NormalHeaders

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    productStatus = 'R63_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-063'
    baseUrl = $BaseUrl
    generatedAt = (Get-Date).ToString('o')
    requirementRows = @('REQ-4.4', 'REQ-5.11', 'REQ-5.13', 'REQ-5.14', 'REQ-5.16', 'REQ-5.20', 'REQ-6.2', 'REQ-6.4', 'REQ-6.5', 'REQ-6.6', 'REQ-6.11')
    journeyRows = @('B1', 'B2', 'C1', 'C2', 'C3', 'J3', 'J8', 'J9', 'J11')
    handoff = [ordered]@{
        systemId = $SystemId
        tenantId = $TenantId
        moduleGroupId = [string]$Group.groupId
        moduleId = $ModuleId
        publishedVersion = $PublishedVersion
        normalRoleId = [string]$RuntimeRole.roleId
        normalMemberId = [string]$NormalMember.systemMemberId
        normalAccountId = [string]$NormalLogin.profile.accountId
        accountMemberBindingId = [string]$NormalSwitch.accountMemberBindingId
    }
    trialCredentials = [ordered]@{
        adminLoginName = 'admin'
        normalLoginName = $NormalLoginName
        readonlyLoginName = $ReadonlyLoginName
        password = $Password
    }
    browserCreatedTitle = $CreatedTitle
    browserUpdatedTitle = $UpdatedTitle
    recordId = $RecordId
    runtimeSearchTotal = [int]$RuntimeSearch.page.total
    runtimeSchemaColumnCount = @($RuntimeSearch.listSchema.columns).Count
    hiddenFieldLeakedInList = $HiddenFieldLeakedInList
    hiddenFieldLeakedInDetail = $HiddenFieldLeakedInDetail
    forbiddenCreateStatus = $ForbiddenCreate.status
    forbiddenAdminStatus = $ForbiddenAdmin.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
    accepted = $true
    userSignoff = $false
}

Assert-True -Condition ($Result.runtimeSearchTotal -ge 1) -Message 'Edited runtime record was not searchable.'
Assert-True -Condition ($Result.hiddenFieldLeakedInList -eq $false -and $Result.hiddenFieldLeakedInDetail -eq $false) -Message 'Hidden field leaked into runtime output.'
Assert-True -Condition ($Result.forbiddenCreateStatus -eq 403 -and $Result.forbiddenAdminStatus -eq 403) -Message 'Permission negatives did not return 403.'
Assert-True -Condition ($Result.browserBlockerCount -eq 0 -and $Result.browserOverflowCount -eq 0) -Message 'R63 browser audit reported blockers or horizontal overflow.'

$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $script:ResultFile

$summaryLines = @(
    '# R63 Configured Runtime First-Use',
    '',
    "- Status: $($Result.status)",
    "- Product status: $($Result.productStatus)",
    "- Base URL: $BaseUrl",
    "- Handoff: system=$SystemId, module=$ModuleId, publishedVersion=$PublishedVersion, role=$($RuntimeRole.roleId), member=$($NormalMember.systemMemberId), binding=$($NormalSwitch.accountMemberBindingId)",
    "- Browser flow: created='$CreatedTitle', edited='$UpdatedTitle', record=$RecordId",
    "- API readback: total=$($Result.runtimeSearchTotal), schemaColumns=$($Result.runtimeSchemaColumnCount), hiddenList=$($Result.hiddenFieldLeakedInList), hiddenDetail=$($Result.hiddenFieldLeakedInDetail)",
    "- Permission negatives: readonlyCreate=$($Result.forbiddenCreateStatus), normalAdmin=$($Result.forbiddenAdminStatus)",
    "- Browser results: $($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r63-configured-runtime-first-use/configured-runtime-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is deployed engineering evidence only. It does not set gates.user_script_passed=true.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}
