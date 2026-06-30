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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 20 -Compress)"
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

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
        throw "Expected forbidden response but request succeeded: $Method $Path"
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 403) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            throw "Expected HTTP 403 but got HTTP ${status}: $body"
        }
        return @{ status = $status }
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
        if ($path -and (Test-Path $path)) {
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
    foreach ($systemId in @($script:TargetSystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r17 normal member real-login runtime cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r17-$script:Suffix-$systemId"
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
    if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
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

$Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:TargetSystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r17-normal-member-real-login-runtime'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$Target = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R17 Runtime Target $Suffix"
    systemCode = "r17_target_$Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:TargetSystemId = [string]$Target.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:TargetSystemId }) | Select-Object -First 1
$TargetTenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TargetTenantId)) -Message 'Target tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r17 target setup'
}

$NormalLoginName = "r17_member_$Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r17_member_$Suffix@example.com"
    password = $Password
    systemName = "R17 Owned $Suffix"
    systemCode = "r17_owned_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R17 Runtime Member $Suffix"
    roleCode = "R17_RUNTIME_MEMBER_$Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R17 runtime-only member role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R17 Business Group $Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r17_ticket_$Suffix"
    name = "R17 Ticket Ledger $Suffix"
    status = 1
    description = 'Recovery R17 normal member runtime module'
}
$ModuleId = [string]$Module.moduleId

$Fields = @()
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'ticketName' -Name 'Ticket Name' -Type 'TEXT' -Required $true)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'ticketAmount' -Name 'Ticket Amount' -Type 'NUMBER' -Required $false)
$FieldIds = @($Fields | ForEach-Object { [string]$_.fieldId })

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'All Tickets'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$Fields[0].fieldId)
    sortFieldIds = @([string]$Fields[1].fieldId)
}

$Permission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:TargetSystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "*" = $true
        "record.create" = $true
        "record.edit" = $true
        "record.delete" = $true
    }
    fieldPermissions = @{ "*" = "WRITABLE" }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r17 module publish'
    idempotencyKey = "module-publish-r17-$Suffix"
}

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R17 Runtime Member $Suffix"
    employeeNo = "R17NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r17_runtime_member_$Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$NormalBinding = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r17 normal member switch precheck'
}
Assert-True -Condition (@($NormalSwitch.effectiveRoleIds) -contains "R17_RUNTIME_MEMBER_$Suffix" -or @($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_MEMBER') -Message "Normal member switch did not include runtime/member role: $($NormalSwitch.effectiveRoleIds -join ',')"
Assert-True -Condition (-not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_ADMIN') -and -not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) `
    -Message "Normal member unexpectedly has system admin role: $($NormalSwitch.effectiveRoleIds -join ',')"
$ForbiddenMemberList = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/members?pageNo=1&pageSize=20" -Headers $NormalHeaders

$RecordValue = "R17 Browser Ticket $Suffix"

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r17-chrome-$Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r17-normal-member-$Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R17_BASE_URL;
const systemId = process.env.R17_SYSTEM_ID;
const accountName = process.env.R17_ACCOUNT;
const password = process.env.R17_PASSWORD;
const recordValue = process.env.R17_RECORD_VALUE;
const outDir = process.env.R17_EVIDENCE_DIR;
const port = process.env.R17_CDP_PORT;

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
        waitFor(eventName, timeoutMs = 5000) {
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

function urlFor(route) {
  return `${baseUrl}/#${route}`;
}

async function navigate(client, url) {
  const waitLoad = client.waitFor('Page.loadEventFired', 7000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(1200);
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

async function waitUntil(client, expression, timeoutMs = 10000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) {
      return last;
    }
    await delay(250);
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}

async function realLogin(client) {
  await navigate(client, urlFor('/login'));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`, 7000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const login = document.querySelector('form.auth-form input[data-field-name="loginName"]');
        const password = document.querySelector('form.auth-form input[data-field-name="password"]');
        const button = document.querySelector('form.auth-form button.button.primary');
        login.value = ${JSON.stringify(accountName)};
        login.dispatchEvent(new Event('input', { bubbles: true }));
        login.dispatchEvent(new Event('change', { bubbles: true }));
        password.value = ${JSON.stringify(password)};
        password.dispatchEvent(new Event('input', { bubbles: true }));
        password.dispatchEvent(new Event('change', { bubbles: true }));
        button.click();
      })()
    `,
    returnByValue: true,
  });
  return waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const hasToken = !!localStorage.getItem('unexamine.accessToken');
      const localAccountId = localStorage.getItem('unexamine.accountId');
      return {
        ok: hasToken && !!localAccountId && text.includes(${JSON.stringify(accountName)}) && location.hash !== '#/login',
        hash: location.hash,
        hasToken,
        localAccountId,
        bodyIncludesAccount: text.includes(${JSON.stringify(accountName)}),
      };
    })())
  `, 12000);
}

async function clickRuntimeCreate(client) {
  await waitUntil(client, `
    JSON.stringify((() => {
      const ready = !!document.querySelector('.runtime-page') && !!document.querySelector('.runtime-page-head .inline-actions button.primary');
      return { ok: ready, hash: location.hash, text: document.body.innerText.slice(0, 500) };
    })())
  `, 12000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const button = document.querySelector('.runtime-page-head .inline-actions button.primary');
        button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.edit-panel input[data-field-code="ticketName"]') })`, 7000);
}

async function fillAndSaveRecord(client) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const name = document.querySelector('.edit-panel input[data-field-code="ticketName"]');
        const amount = document.querySelector('.edit-panel input[data-field-code="ticketAmount"]');
        name.value = ${JSON.stringify(recordValue)};
        name.dispatchEvent(new Event('input', { bubbles: true }));
        name.dispatchEvent(new Event('change', { bubbles: true }));
        if (amount) {
          amount.value = '1700';
          amount.dispatchEvent(new Event('input', { bubbles: true }));
          amount.dispatchEvent(new Event('change', { bubbles: true }));
        }
        const save = document.querySelector('.edit-panel .inline-actions button.primary');
        save.click();
      })()
    `,
    returnByValue: true,
  });
  return waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const rows = Array.from(document.querySelectorAll('.runtime-table tbody tr'));
      return {
        ok: text.includes(${JSON.stringify(recordValue)}) && rows.length >= 1 && !document.querySelector('.edit-panel'),
        rowCount: rows.length,
        hasValue: text.includes(${JSON.stringify(recordValue)}),
        text: text.slice(0, 1200),
      };
    })())
  `, 12000);
}

async function captureAndMeasure(client, key) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  const keyLiteral = JSON.stringify(key);
  return evaluateJson(client, `
    JSON.stringify((() => {
      const key = ${keyLiteral};
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const doc = document.documentElement;
      const body = document.body;
      const text = body.innerText || '';
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      const overflowX = Math.max(0, Math.ceil(scrollWidth - vw));
      const headerButtons = Array.from(document.querySelectorAll('.header-actions button')).map((button) => button.textContent.trim());
      const hasAdminEntry = headerButtons.some((label) => /后台|admin/i.test(label));
      const runtimePage = document.querySelector('.runtime-page');
      const runtimeTable = document.querySelector('.runtime-table');
      const denied = text.includes('\u65e0\u6743\u9650') || /Access denied|access denied/i.test(text);
      const adminSidebar = document.querySelector('.admin-sidebar');
      const moduleToolbar = document.querySelector('.module-builder-toolbar');
      const blockers = [];
      if (!localStorage.getItem('unexamine.accessToken')) blockers.push('missing browser token');
      if (!key.includes('denied') && !text.includes(${JSON.stringify(accountName)})) blockers.push('account label missing');
      if (overflowX > 2) blockers.push('document horizontal overflow');
      if (key.includes('runtime') && !runtimePage) blockers.push('runtime page missing');
      if (key.includes('runtime') && !runtimeTable) blockers.push('runtime table missing');
      if (key.includes('runtime') && !text.includes(${JSON.stringify(recordValue)})) blockers.push('created record value missing');
      if (key.includes('runtime') && hasAdminEntry) blockers.push('system admin entry visible for normal member');
      if (key.includes('denied') && !denied) blockers.push('access denied text missing');
      if (key.includes('denied') && adminSidebar) blockers.push('admin sidebar rendered for normal member');
      if (key.includes('denied') && moduleToolbar) blockers.push('admin module toolbar rendered for normal member');
      if (/Loading\\.\\.\\./.test(text)) blockers.push('loading text still visible');
      return {
        key,
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        hasToken: !!localStorage.getItem('unexamine.accessToken'),
        localAccountId: localStorage.getItem('unexamine.accountId'),
        headerButtons,
        headerButtonCount: headerButtons.length,
        hasAdminEntry,
        bodyIncludesAccount: text.includes(${JSON.stringify(accountName)}),
        bodyIncludesRecord: text.includes(${JSON.stringify(recordValue)}),
        denied,
        adminSidebarVisible: !!adminSidebar,
        moduleToolbarVisible: !!moduleToolbar,
        runtimeVisible: !!runtimePage,
        runtimeTableVisible: !!runtimeTable,
        scrollWidth,
        overflowX,
        viewport: { width: vw, height: vh },
        blockers,
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
  await setViewport(client, 1280, 720);

  const loginState = await realLogin(client);
  await navigate(client, urlFor(`/systems/${systemId}/modules`));
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: location.hash === '#/systems/${systemId}/modules' && !!document.querySelector('.runtime-page') && !/Loading\\.\\.\\./.test(text),
        hash: location.hash,
        text: text.slice(0, 700),
      };
    })())
  `, 15000);
  await clickRuntimeCreate(client);
  const saveState = await fillAndSaveRecord(client);
  const results = [];
  results.push(await captureAndMeasure(client, 'desktop-runtime-created-record'));

  await navigate(client, urlFor(`/systems/${systemId}/admin`));
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const denied = text.includes('\\u65e0\\u6743\\u9650') || /Access denied|access denied/i.test(text);
      return {
        ok: denied && !document.querySelector('.admin-sidebar') && !document.querySelector('.module-builder-toolbar'),
        denied,
        hasAdminSidebar: !!document.querySelector('.admin-sidebar'),
        hasModuleToolbar: !!document.querySelector('.module-builder-toolbar'),
        text: text.slice(0, 600),
        hash: location.hash,
      };
    })())
  `, 8000);
  results.push(await captureAndMeasure(client, 'desktop-admin-denied'));

  await setViewport(client, 390, 720);
  await navigate(client, urlFor(`/systems/${systemId}/modules`));
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return { ok: !!document.querySelector('.runtime-page') && text.includes(${JSON.stringify(recordValue)}) && !/Loading\\.\\.\\./.test(text), hash: location.hash };
    })())
  `, 12000);
  results.push(await captureAndMeasure(client, 'mobile-runtime-created-record'));

  client.close();
  const failures = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-020',
    baseUrl,
    systemId,
    accountName,
    recordValue,
    generatedAt: new Date().toISOString(),
    loginState,
    saveState,
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'normal-member-real-login-runtime-browser.json'), JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`Normal member browser audit failed: ${failures.map((item) => `${item.key}:${item.blockers.join('|')}`).join('; ')}`);
  }
  console.log(JSON.stringify(output, null, 2));
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R17_BASE_URL = $BaseUrl
$env:R17_SYSTEM_ID = $script:TargetSystemId
$env:R17_ACCOUNT = $NormalLoginName
$env:R17_PASSWORD = $Password
$env:R17_RECORD_VALUE = $RecordValue
$env:R17_EVIDENCE_DIR = $EvidenceDir
$env:R17_CDP_PORT = [string]$debugPort

try {
    $nodeOutput = & $nodeExe $nodeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Node normal-member browser audit exited with code $LASTEXITCODE"
    }
} finally {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $nodeScript) {
        Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
    }
}

$SearchAfterBrowser = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $RecordValue
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @()
}
Assert-True -Condition ([int]$SearchAfterBrowser.page.total -ge 1) -Message 'API readback did not find the frontend-created record.'

$resultPath = Join-Path $EvidenceDir 'normal-member-real-login-runtime-browser.json'
$browserResult = Get-Content -Raw $resultPath | ConvertFrom-Json
Assert-True -Condition ($browserResult.status -eq 'PASS') -Message "R17 browser audit failed: $(Get-Content -Raw $resultPath)"

$script:CleanupResult = Remove-CreatedSystems

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-020'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    targetSystemId = $script:TargetSystemId
    targetTenantId = $TargetTenantId
    normalAccount = $NormalLoginName
    normalOwnedSystemId = $script:NormalOwnedSystemId
    normalMemberId = $NormalMember.systemMemberId
    normalBindingId = $NormalBinding.bindingId
    runtimeRoleId = $RuntimeRole.roleId
    moduleId = $ModuleId
    sceneId = $Scene.sceneId
    permissionVersion = $Permission.permissionVersion
    normalEffectiveRoles = $NormalSwitch.effectiveRoleIds
    forbiddenMemberListStatus = $ForbiddenMemberList.status
    browserRecordValue = $RecordValue
    apiReadbackTotal = $SearchAfterBrowser.page.total
    evidenceDir = $EvidenceDir
    browserResultPath = $resultPath
    screenshotCount = @($browserResult.results).Count
    cleanup = $script:CleanupResult
}

$resultJson = $result | ConvertTo-Json -Depth 40
$resultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r17-normal-member-real-login-runtime-result.json'
$resultJson | Set-Content -LiteralPath $resultFile -Encoding UTF8

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r17-normal-member-real-login-runtime-2026-06-29.md'
@"
# R17 Normal Member Real-Login Runtime Usability Closure

Status: PASS

Base URL: $BaseUrl

Task: REC-P0-020 Normal Member Real-Login Runtime Usability Closure

Evidence:

- Browser result: $resultPath
- Machine result: $resultFile
- Screenshots: $EvidenceDir

Assertions:

- Normal member logged in through the deployed /login form.
- Browser opened the target system runtime module page from the same session.
- Frontend create form saved record value $RecordValue.
- Runtime list/detail showed the saved value on desktop and mobile.
- Header did not expose a system backend entry for the normal member.
- Direct system-admin URL rendered access denied instead of admin content.
- Backend admin member-list API rejected the normal member with HTTP $($ForbiddenMemberList.status).
- Runtime API readback found $($SearchAfterBrowser.page.total) matching record(s).
- Cleanup: $($script:CleanupResult -join ', ')
"@ | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$resultJson
