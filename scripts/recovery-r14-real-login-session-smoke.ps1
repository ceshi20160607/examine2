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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 16 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
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

function Remove-CreatedSystem {
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:Headers) {
        return 'SKIPPED'
    }
    $cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:Headers -Body @{
        reason = 'recovery-r14 real login session cleanup'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r14-$script:Suffix"
    }
    return [string]$cleanup.result
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
            $script:CleanupResult = Remove-CreatedSystem
        } catch {
            Write-Error "Cleanup created system failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$Suffix = Get-Date -Format 'MMddHHmmss'
$script:SystemId = $null
$script:Headers = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$CleanupResult = 'SKIPPED'
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r14-real-login-session'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($health | ConvertTo-Json -Depth 12 -Compress)"

$login = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$accessToken = [string]$login.accessToken
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($accessToken)) -Message 'Default admin API login did not return an access token.'
$script:Headers = @{ Authorization = "Bearer $accessToken" }

$system = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:Headers -Body @{
    systemName = "R14 Real Login Session $Suffix"
    systemCode = "r14_real_login_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:SystemId = [string]$system.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r14-chrome-$Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r14-real-login-$Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R14_BASE_URL;
const systemId = process.env.R14_SYSTEM_ID;
const outDir = process.env.R14_EVIDENCE_DIR;
const port = process.env.R14_CDP_PORT;

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
        login.value = 'admin';
        login.dispatchEvent(new Event('input', { bubbles: true }));
        login.dispatchEvent(new Event('change', { bubbles: true }));
        password.value = '123123aa';
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
      const buttons = Array.from(document.querySelectorAll('.header-actions button')).map((button) => button.textContent.trim());
      const hasToken = !!localStorage.getItem('unexamine.accessToken');
      const localAccountId = localStorage.getItem('unexamine.accountId');
      return {
        ok: hasToken && !!localAccountId && buttons.includes('admin') && location.hash !== '#/login',
        hash: location.hash,
        hasToken,
        localAccountId,
        headerButtons: buttons,
        bodyIncludesAdmin: text.includes('admin'),
      };
    })())
  `, 12000);
}

async function clickAdminModule(client) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'));
        const target = buttons[3] || buttons.find((button) => /module|Module/i.test(button.textContent));
        if (target) target.click();
      })()
    `,
    returnByValue: true,
  });
  await delay(1200);
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
      const adminContent = document.querySelector('.admin-content');
      const adminSidebar = document.querySelector('.admin-sidebar');
      const moduleToolbar = document.querySelector('.module-builder-toolbar');
      const workPage = document.querySelector('.workbench-page') || document.querySelector('.work-section') || document.querySelector('.work-dashboard-grid');
      const blockers = [];
      if (!localStorage.getItem('unexamine.accessToken')) blockers.push('missing browser token');
      if (!localStorage.getItem('unexamine.accountId')) blockers.push('missing browser account id');
      if (!headerButtons.includes('admin')) blockers.push('authenticated account label missing');
      if (overflowX > 2) blockers.push('document horizontal overflow');
      if (key.includes('admin') && !adminContent) blockers.push('admin content missing');
      if (key.includes('admin') && !moduleToolbar) blockers.push('module toolbar missing');
      if (key.includes('work') && !workPage) blockers.push('work content missing');
      const adminContentRect = adminContent ? adminContent.getBoundingClientRect() : null;
      const adminSidebarRect = adminSidebar ? adminSidebar.getBoundingClientRect() : null;
      if (vw <= 640 && adminSidebarRect && adminSidebarRect.height > 150) blockers.push('mobile admin sidebar too tall');
      if (vw <= 640 && adminContentRect && adminContentRect.top > vh * 0.55) blockers.push('mobile admin content below first viewport');
      if (/Loading\\.\\.\\./.test(text)) blockers.push('loading text still visible');
      return {
        key,
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        hasToken: !!localStorage.getItem('unexamine.accessToken'),
        localAccountId: localStorage.getItem('unexamine.accountId'),
        headerButtons,
        bodyIncludesAdmin: text.includes('admin'),
        scrollWidth,
        overflowX,
        adminContentTop: adminContentRect ? Math.round(adminContentRect.top) : null,
        adminSidebarHeight: adminSidebarRect ? Math.round(adminSidebarRect.height) : null,
        moduleToolbarVisible: !!moduleToolbar,
        workVisible: !!workPage,
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
  await setViewport(client, 390, 720);

  const loginState = await realLogin(client);
  const results = [];
  results.push(await captureAndMeasure(client, 'mobile-after-real-login'));

  await navigate(client, urlFor(`/systems/${systemId}/admin`));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-content') && !/Loading\\.\\.\\./.test(document.body.innerText || '') })`, 12000);
  await clickAdminModule(client);
  results.push(await captureAndMeasure(client, 'mobile-system-admin-after-real-login'));

  await navigate(client, urlFor(`/systems/${systemId}/work`));
  await waitUntil(client, `JSON.stringify({ ok: !!(document.querySelector('.workbench-page') || document.querySelector('.work-section') || document.querySelector('.work-dashboard-grid')) && !/Loading\\.\\.\\./.test(document.body.innerText || '') })`, 12000);
  results.push(await captureAndMeasure(client, 'mobile-work-after-real-login'));

  client.close();
  const failures = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-017',
    baseUrl,
    systemId,
    generatedAt: new Date().toISOString(),
    loginState,
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'real-login-session-audit.json'), JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`Real login session audit failed: ${failures.map((item) => `${item.key}:${item.blockers.join('|')}`).join('; ')}`);
  }
  console.log(JSON.stringify(output, null, 2));
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R14_BASE_URL = $BaseUrl
$env:R14_SYSTEM_ID = $script:SystemId
$env:R14_EVIDENCE_DIR = $EvidenceDir
$env:R14_CDP_PORT = [string]$debugPort

try {
    $nodeOutput = & $nodeExe $nodeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Node real-login audit exited with code $LASTEXITCODE"
    }
    $CleanupResult = Remove-CreatedSystem
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

$resultPath = Join-Path $EvidenceDir 'real-login-session-audit.json'
$result = Get-Content -Raw $resultPath | ConvertFrom-Json
Assert-True -Condition ($result.status -eq 'PASS') -Message "R14 real login session audit failed: $(Get-Content -Raw $resultPath)"

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-017'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    evidenceDir = $EvidenceDir
    resultPath = $resultPath
    screenshotCount = @($result.results).Count
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20
