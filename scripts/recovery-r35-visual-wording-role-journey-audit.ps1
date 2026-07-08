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

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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
            duplicateKey = 'none'
            desensitizeMode = 'none'
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
                reason = 'recovery-r35 visual wording role journey cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r35-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r35-visual-wording-role-journey'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r35-visual-wording-role-journey-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r35-visual-wording-role-journey-2026-06-30.md'
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
    systemName = "R35 Visual Audit $script:Suffix"
    systemCode = "r35_visual_$script:Suffix"
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
    reason = 'recovery-r35 visual role journey setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R35 Runtime Member $script:Suffix"
    roleCode = "R35_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R35 role journey runtime member'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R35 Operations $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r35_request_$script:Suffix"
    name = "R35 Request $script:Suffix"
    status = 1
    description = 'Recovery R35 visual wording audit module'
}
$ModuleId = [string]$Module.moduleId

$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'requestTitle' -Name 'Request Title' -Type 'TEXT' -Required $true)
$StatusField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'requestStatus' -Name 'Request Status' -Type 'TEXT')
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'requestAmount' -Name 'Request Amount' -Type 'NUMBER')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R35 Daily View'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$StatusField.fieldId, [string]$AmountField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$StatusField.fieldId)
    sortFieldIds = @([string]$TitleField.fieldId)
}

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.read" = $true
        "record.create" = $false
        "record.edit" = $false
        "record.delete" = $false
    }
    fieldPermissions = @{
        "$ModuleId.requestTitle" = "READABLE"
        "$ModuleId.requestStatus" = "READABLE"
        "$ModuleId.requestAmount" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r35 module publish'
    idempotencyKey = "module-publish-r35-$script:Suffix"
}

1..8 | ForEach-Object {
    $idx = $_
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
        fieldValues = @{
            requestTitle = ('R35 Daily Request {0:D2}' -f $idx)
            requestStatus = if ($idx % 2 -eq 0) { 'Processing' } else { 'Pending' }
            requestAmount = 1000 + $idx
        }
        childRows = @{}
        attachmentIds = @()
        sourceType = 'R35_VISUAL_WORDING_AUDIT'
    }
}

$NormalLoginName = "r35_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r35_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R35 Owned $script:Suffix"
    systemCode = "r35_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:CreatedSystemIds.Add($NormalOwnedSystemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R35 Runtime Member $script:Suffix"
    employeeNo = "R35M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r35_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r35 normal member browser audit'
}

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r35-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$chromeArgs = @(
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-background-networking",
    "--remote-allow-origins=*",
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    "about:blank"
)
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden

$versionUrl = "http://127.0.0.1:$debugPort/json/version"
$ready = $false
for ($i = 0; $i -lt 50; $i++) {
    try {
        Invoke-RestMethod -Uri $versionUrl -TimeoutSec 2 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Milliseconds 200
    }
}
Assert-True -Condition $ready -Message 'Chrome DevTools endpoint did not become ready.'

$nodeScript = Join-Path $env:TEMP "unexamine-r35-visual-wording-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R35_BASE_URL;
const outDir = process.env.R35_EVIDENCE_DIR;
const port = process.env.R35_CDP_PORT;
const systemId = process.env.R35_SYSTEM_ID;
const moduleId = process.env.R35_MODULE_ID;
const resultFile = process.env.R35_RESULT_FILE;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) {
    throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
  }
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
    const eventWaiters = new Map();
    let seq = 0;
    ws.onopen = () => {
      const client = {
        send(method, params = {}) {
          const id = ++seq;
          ws.send(JSON.stringify({ id, method, params }));
          return new Promise((res, rej) => {
            pending.set(id, { res, rej, method });
          });
        },
        waitFor(eventName, timeoutMs = 7000) {
          return new Promise((res) => {
            const timer = setTimeout(() => res(null), timeoutMs);
            eventWaiters.set(eventName, (payload) => {
              clearTimeout(timer);
              res(payload);
            });
          });
        },
        close() {
          ws.close();
        },
      };
      resolve(client);
    };
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) {
          item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`));
        } else {
          item.res(message.result);
        }
      }
      if (message.method && eventWaiters.has(message.method)) {
        const waiter = eventWaiters.get(message.method);
        eventWaiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}

async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await delay(1400);
}

async function setViewport(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: width <= 640,
  });
  await client.send('Emulation.setVisibleSize', { width, height });
}

async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (result.exceptionDetails) {
    throw new Error(JSON.stringify(result.exceptionDetails));
  }
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}

async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/`);
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.removeItem('unexamine.accountId');
      localStorage.removeItem('unexamine.accessToken');
      localStorage.removeItem('unexamine.refreshToken');
      localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
      localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
      localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    `,
    returnByValue: true,
  });
  await client.send('Page.reload', { ignoreCache: true });
  await delay(1800);
}

async function waitForSettled(client) {
  for (let i = 0; i < 12; i += 1) {
    const state = await evaluateJson(client, `
      JSON.stringify((() => {
        const text = document.body?.innerText || '';
        return {
          hasLoading: /Loading\\.\\.\\./.test(text) || /\\u6B63\\u5728\\u8BFB\\u53D6/.test(text),
          textLength: text.length,
        };
      })())
    `);
    if (!state.hasLoading && state.textLength > 20) {
      return state;
    }
    await delay(500);
  }
  return null;
}

function routeUrl(route) {
  return `${baseUrl}/#${route}`;
}

async function measure(client, route, viewport) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${viewport.name}-${route.key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));

  return evaluateJson(client, `
    JSON.stringify((() => {
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const doc = document.documentElement;
      const body = document.body;
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      const overflowX = Math.max(0, Math.ceil(scrollWidth - vw));
      const text = body.innerText || '';
      const visible = (el) => {
        if (!el) return false;
        const style = window.getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const panelSelector = '.workspace-shell,.platform-admin-shell,.system-layout,.content-panel,.panel,.runtime-card,.runtime-record-shell,.runtime-list-panel,.runtime-detail-panel,.admin-content,.module-builder,.module-builder-workspace,.flow-canvas-board,.assistant-drawer';
      const clipped = Array.from(document.querySelectorAll(panelSelector))
        .filter(visible)
        .map((el) => {
          const rect = el.getBoundingClientRect();
          return {
            selector: String(el.className || el.tagName).slice(0, 120),
            left: Math.round(rect.left),
            right: Math.round(rect.right),
            top: Math.round(rect.top),
            width: Math.round(rect.width),
          };
        })
        .filter((item) => item.right > vw + 2 || item.left < -2)
        .slice(0, 10);
      const controlOverflow = Array.from(document.querySelectorAll('button,a,input,textarea,select,.sidebar-item,.tab-button,.chip,.pill'))
        .filter(visible)
        .map((el) => ({
          tag: el.tagName,
          text: (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().replace(/\\s+/g, ' ').slice(0, 80),
          scrollWidth: el.scrollWidth,
          clientWidth: el.clientWidth,
          scrollHeight: el.scrollHeight,
          clientHeight: el.clientHeight,
        }))
        .filter((item) => item.text.length > 2 && (item.scrollWidth > item.clientWidth + 2 || item.scrollHeight > item.clientHeight + 2))
        .slice(0, 10);
      const buttons = Array.from(document.querySelectorAll('button,a'))
        .filter(visible)
        .map((el) => (el.innerText || el.getAttribute('aria-label') || '').trim().replace(/\\s+/g, ' '))
        .filter(Boolean);
      const detailLike = buttons.filter((item) => /(\\u8BE6\\u60C5|\\u67E5\\u770B|\\u6253\\u5F00|\\u8FDB\\u5165)/.test(item));
      const duplicateDetailActions = Object.entries(detailLike.reduce((acc, item) => {
        acc[item] = (acc[item] || 0) + 1;
        return acc;
      }, {})).filter(([, count]) => count > 1).map(([text, count]) => ({ text, count }));
      const placeholderMatches = text.match(/(\\u5F85\\u63A5\\u5165|\\u672A\\u5B9E\\u73B0|\\u5360\\u4F4D|\\u656C\\u8BF7\\u671F\\u5F85|coming soon|TODO|defaultDrawer|genericDrawer)/ig) || [];
      const genericToastMatches = text.match(/(\\u64CD\\u4F5C\\u5DF2\\u54CD\\u5E94|\\u5DF2\\u89E6\\u53D1|successfully triggered)/ig) || [];
      const demoMatches = text.match(/(demo|mock|fixture|sample|\\u6F14\\u793A)/ig) || [];
      const mojibakeMatches = text.match(/[\\uFFFD\\uE000-\\uF8FF]|(\\u95AB|\\u7EEF|\\u934F|\\u93C9|\\u93B4|\\u59AF|\\u6D93|\\u5BF0|\\u7487|\\u7459|\\u9422|\\u6D63|\\u93B5|\\u9359|\\u5BB8|\\u6924|\\u741B|\\u9352|\\u699B|\\u7ECC|\\u9354|\\u93C3|\\u7039|\\u68E3|\\u6D60|\\u6434|\\u5A11|\\u581F|\\u5F60|\\u7F03|\\u515F|\\u70D8|\\u53A4|\\u64F3|\\u6097)/g) || [];
      const panelCount = Array.from(document.querySelectorAll('.panel,.runtime-card,.content-panel,.metric-card,.message-item,.list-line,.module-work-panel'))
        .filter(visible).length;
      const adminShellVisible = Array.from(document.querySelectorAll('.admin-sidebar,.platform-admin-shell,.admin-content')).some(visible);
      const bodyHash = window.location.hash;
      const assetScripts = Array.from(document.scripts).map((script) => script.src).filter((src) => src.includes('/assets/'));
      const blockers = [];
      const warnings = [];
      if (overflowX > 2) blockers.push('document horizontal overflow');
      if (clipped.length > 0) blockers.push('visible panel clipped horizontally');
      if (controlOverflow.length > 0) blockers.push('visible control text overflow');
      if (/Loading\\.\\.\\./.test(text) || /\\u6B63\\u5728\\u8BFB\\u53D6/.test(text)) blockers.push('loading text still visible after settle');
      if (placeholderMatches.length > 0) blockers.push('unfinished placeholder wording visible');
      if (genericToastMatches.length > 0) blockers.push('generic action result wording visible');
      if (mojibakeMatches.length > 0) blockers.push('likely mojibake visible in user-facing text');
      if (${JSON.stringify(route.expectNoAdmin)} && adminShellVisible) blockers.push('non-admin role can see admin shell');
      if (duplicateDetailActions.length > 0) warnings.push('duplicate detail-like actions visible');
      if (demoMatches.length > 0) warnings.push('demo/mock/sample wording visible');
      if (viewport.width <= 640 && buttons.length > 28) warnings.push('mobile viewport has too many visible actions');
      if (panelCount > 22) warnings.push('high panel density may feel crowded');
      return {
        role: ${JSON.stringify(route.role)},
        key: ${JSON.stringify(route.key)},
        route: ${JSON.stringify(route.route)},
        finalHash: bodyHash,
        viewport: ${JSON.stringify(viewport.name)},
        viewportSize: { width: ${viewport.width}, height: ${viewport.height} },
        screenshot: ${JSON.stringify(fileName)},
        scrollWidth,
        overflowX,
        textLength: text.length,
        textPreview: text.slice(0, 500),
        panelCount,
        buttonCount: buttons.length,
        clipped,
        controlOverflow,
        duplicateDetailActions,
        placeholderMatches: placeholderMatches.slice(0, 12),
        genericToastMatches: genericToastMatches.slice(0, 12),
        demoMatches: demoMatches.slice(0, 12),
        mojibakeCount: mojibakeMatches.length,
        mojibakeSamples: mojibakeMatches.slice(0, 20),
        adminShellVisible,
        assetScripts,
        blockers,
        warnings,
      };
    })())
  `);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('DOM.enable');

  const roles = {
    admin: {
      name: 'admin',
      accessToken: process.env.R35_ADMIN_TOKEN,
      refreshToken: process.env.R35_ADMIN_REFRESH,
      accountId: process.env.R35_ADMIN_ACCOUNT_ID || 'admin',
    },
    normal: {
      name: 'normal-member',
      accessToken: process.env.R35_NORMAL_TOKEN,
      refreshToken: process.env.R35_NORMAL_REFRESH,
      accountId: process.env.R35_NORMAL_ACCOUNT_ID || 'normal',
    },
  };
  const routeSets = [
    { role: 'admin', key: 'platform-workbench', route: '/platform' },
    { role: 'admin', key: 'platform-admin', route: '/platform/admin' },
    { role: 'admin', key: 'system-dashboard', route: `/systems/${systemId}/dashboard` },
    { role: 'admin', key: 'system-runtime-modules', route: `/systems/${systemId}/modules` },
    { role: 'admin', key: 'system-work', route: `/systems/${systemId}/work` },
    { role: 'admin', key: 'system-todos', route: `/systems/${systemId}/todos` },
    { role: 'admin', key: 'system-messages', route: `/systems/${systemId}/messages` },
    { role: 'admin', key: 'system-admin', route: `/systems/${systemId}/admin` },
    { role: 'normal', key: 'normal-platform-workbench', route: '/platform' },
    { role: 'normal', key: 'normal-platform-admin-denied', route: '/platform/admin', expectNoAdmin: true },
    { role: 'normal', key: 'normal-system-dashboard', route: `/systems/${systemId}/dashboard` },
    { role: 'normal', key: 'normal-system-runtime-modules', route: `/systems/${systemId}/modules` },
    { role: 'normal', key: 'normal-system-work', route: `/systems/${systemId}/work` },
    { role: 'normal', key: 'normal-system-todos', route: `/systems/${systemId}/todos` },
    { role: 'normal', key: 'normal-system-messages', route: `/systems/${systemId}/messages` },
    { role: 'normal', key: 'normal-system-admin-denied', route: `/systems/${systemId}/admin`, expectNoAdmin: true },
  ];
  const viewports = [
    { name: 'desktop', width: 1280, height: 720 },
    { name: 'mobile', width: 390, height: 720 },
  ];
  const results = [];
  const assetSet = new Set();

  for (const viewport of viewports) {
    await setViewport(client, viewport.width, viewport.height);
    let currentRole = '';
    for (const route of routeSets) {
      if (route.role !== currentRole) {
        currentRole = route.role;
        await setStorage(client, roles[currentRole]);
      }
      await navigate(client, routeUrl(route.route));
      await waitForSettled(client);
      const measured = await measure(client, route, viewport);
      measured.assetScripts.forEach((src) => assetSet.add(src));
      results.push(measured);
    }
  }
  client.close();

  const failures = results.filter((result) => result.blockers.length > 0);
  const warnings = results.filter((result) => result.warnings.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-035',
    frc: 'FRC-1A Visual and wording role-journey audit',
    baseUrl,
    systemId,
    moduleId,
    generatedAt: new Date().toISOString(),
    assetScripts: Array.from(assetSet),
    routeCount: routeSets.length,
    viewportCount: viewports.length,
    resultCount: results.length,
    failureCount: failures.length,
    warningCount: warnings.length,
    results,
    failures,
    warnings,
  };
  fs.writeFileSync(path.join(outDir, 'visual-wording-role-journey-audit.json'), JSON.stringify(output, null, 2));
  fs.writeFileSync(resultFile, JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R35_BASE_URL = $BaseUrl
$env:R35_EVIDENCE_DIR = $EvidenceDir
$env:R35_CDP_PORT = [string]$debugPort
$env:R35_SYSTEM_ID = $SystemId
$env:R35_MODULE_ID = $ModuleId
$env:R35_RESULT_FILE = $ResultFile
$env:R35_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R35_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R35_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R35_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R35_NORMAL_REFRESH = [string]$NormalLogin.refreshToken
$env:R35_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId

try {
    $nodeOutput = & $nodeExe $nodeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Node visual wording audit exited with code $LASTEXITCODE"
    }
} finally {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $nodeScript) {
        Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
    }
}

Assert-True -Condition (Test-Path -LiteralPath $ResultFile) -Message "Audit result was not written: $ResultFile"
$Audit = Get-Content -Raw -LiteralPath $ResultFile -Encoding UTF8 | ConvertFrom-Json
$script:CleanupResult = Remove-CreatedSystems

$summaryLines = @(
    '# R35 Visual And Wording Role-Journey Audit',
    '',
    "Status: $($Audit.status)",
    '',
    "Base URL: $BaseUrl",
    '',
    "System ID: $SystemId",
    '',
    "Module ID: $ModuleId",
    '',
    "Result JSON: docs/evidence/recovery/r35-visual-wording-role-journey-result.json",
    '',
    "Browser audit JSON: docs/evidence/recovery/screenshots/r35-visual-wording-role-journey/visual-wording-role-journey-audit.json",
    '',
    "Screenshots: docs/evidence/recovery/screenshots/r35-visual-wording-role-journey/",
    '',
    "Failure count: $($Audit.failureCount)",
    '',
    "Warning count: $($Audit.warningCount)",
    '',
    '## Failure Routes',
    ''
)
foreach ($failure in @($Audit.failures)) {
    $summaryLines += "- $($failure.viewport) / $($failure.role) / $($failure.key): $(@($failure.blockers) -join '; ')"
}
if (@($Audit.failures).Count -eq 0) {
    $summaryLines += '- none'
}
$summaryLines += ''
$summaryLines += '## Cleanup'
$summaryLines += ''
$summaryLines += "- $($script:CleanupResult -join ', ')"
$summaryLines -join [Environment]::NewLine | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$final = [ordered]@{
    status = [string]$Audit.status
    task = 'REC-P0-035'
    frc = 'FRC-1A Visual and wording role-journey audit'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    resultPath = $ResultFile
    summaryPath = $SummaryFile
    evidenceDir = $EvidenceDir
    routeCount = $Audit.routeCount
    resultCount = $Audit.resultCount
    failureCount = $Audit.failureCount
    warningCount = $Audit.warningCount
    assetScripts = $Audit.assetScripts
    cleanup = $script:CleanupResult
}
$final | ConvertTo-Json -Depth 20

if ($Audit.status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
