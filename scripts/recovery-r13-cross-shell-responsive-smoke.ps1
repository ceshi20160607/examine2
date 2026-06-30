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
        reason = 'recovery-r13 responsive smoke cleanup'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r13-$script:Suffix"
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
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r13-cross-shell-responsive'
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
$refreshToken = [string]$login.refreshToken
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($accessToken)) -Message 'Default admin login did not return an access token.'
$script:Headers = @{ Authorization = "Bearer $accessToken" }

$system = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:Headers -Body @{
    systemName = "R13 Responsive System $Suffix"
    systemCode = "r13_responsive_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:SystemId = [string]$system.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:Headers -Body @{
    systemId = $script:SystemId
    tenantId = $null
    reason = 'recovery-r13 responsive smoke'
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$switch.tenantId)) -Message 'System switch did not return tenantId.'

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r13-chrome-$Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r13-responsive-$Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R13_BASE_URL;
const token = process.env.R13_ACCESS_TOKEN;
const refreshToken = process.env.R13_REFRESH_TOKEN || '';
const systemId = process.env.R13_SYSTEM_ID;
const outDir = process.env.R13_EVIDENCE_DIR;
const port = process.env.R13_CDP_PORT;

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

function routeUrl(route) {
  return `${baseUrl}/#${route}`;
}

async function navigate(client, url) {
  const waitLoad = client.waitFor('Page.loadEventFired', 7000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(1300);
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

async function setStorage(client) {
  await navigate(client, `${baseUrl}/`);
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.setItem('unexamine.accountId', ${JSON.stringify(process.env.R13_ACCOUNT_ID || 'admin')});
      localStorage.setItem('unexamine.accessToken', ${JSON.stringify(token)});
      localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(refreshToken)});
    `,
    returnByValue: true,
  });
}

async function clickAdminModule(client) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'));
        const target = buttons[3] || buttons.find((button) => button.textContent.includes('模块'));
        if (target) target.click();
      })()
    `,
    returnByValue: true,
  });
  await delay(1000);
}

async function measure(client, key, viewportName, width, height) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${viewportName}-${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return evaluateJson(client, `
    JSON.stringify((() => {
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const doc = document.documentElement;
      const body = document.body;
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      const overflowX = Math.max(0, Math.ceil(scrollWidth - vw));
      const clipped = Array.from(document.querySelectorAll('.metric-card,.panel,.runtime-card,.module-builder-toolbar,.module-work-actions,.admin-content,.content-panel'))
        .map((el) => {
          const rect = el.getBoundingClientRect();
          return {
            selector: el.className || el.tagName,
            left: Math.round(rect.left),
            right: Math.round(rect.right),
            top: Math.round(rect.top),
            width: Math.round(rect.width),
          };
        })
        .filter((item) => item.right > vw + 2 || item.left < -2)
        .slice(0, 8);
      const adminSidebar = document.querySelector('.admin-sidebar');
      const adminContent = document.querySelector('.admin-content');
      const adminSidebarRect = adminSidebar ? adminSidebar.getBoundingClientRect() : null;
      const adminContentRect = adminContent ? adminContent.getBoundingClientRect() : null;
      const main = document.querySelector('main');
      const mainRect = main ? main.getBoundingClientRect() : null;
      const blockers = [];
      if (overflowX > 2) blockers.push('document horizontal overflow');
      if (clipped.length > 0) blockers.push('visible content clipped horizontally');
      if (vw <= 640 && adminSidebarRect && adminSidebarRect.height > 150) blockers.push('mobile admin sidebar too tall');
      if (vw <= 640 && adminContentRect && adminContentRect.top > vh * 0.55) blockers.push('mobile admin content below first viewport');
      if (mainRect && mainRect.top > vh * 0.7) blockers.push('main content below first viewport');
      const text = body.innerText || '';
      if (/Loading\\.\\.\\./.test(text)) blockers.push('loading text still visible');
      return {
        key: ${JSON.stringify(key)},
        viewport: ${JSON.stringify(viewportName)},
        viewportSize: { width: ${width}, height: ${height} },
        screenshot: ${JSON.stringify(fileName)},
        scrollWidth,
        overflowX,
        clipped,
        adminSidebar: adminSidebarRect ? {
          top: Math.round(adminSidebarRect.top),
          height: Math.round(adminSidebarRect.height),
          scrollWidth: adminSidebar.scrollWidth,
          clientWidth: adminSidebar.clientWidth,
        } : null,
        adminContentTop: adminContentRect ? Math.round(adminContentRect.top) : null,
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
  await setStorage(client);

  const routes = [
    { key: 'platform-workbench', route: '/platform' },
    { key: 'platform-admin', route: '/platform/admin' },
    { key: 'system-dashboard', route: `/systems/${systemId}/dashboard` },
    { key: 'system-modules', route: `/systems/${systemId}/modules` },
    { key: 'system-work', route: `/systems/${systemId}/work` },
    { key: 'system-todos', route: `/systems/${systemId}/todos` },
    { key: 'system-messages', route: `/systems/${systemId}/messages` },
    { key: 'system-admin-module', route: `/systems/${systemId}/admin`, afterNavigate: clickAdminModule },
  ];
  const viewports = [
    { name: 'desktop', width: 1280, height: 720 },
    { name: 'mobile', width: 390, height: 720 },
  ];
  const results = [];
  for (const viewport of viewports) {
    await setViewport(client, viewport.width, viewport.height);
    for (const route of routes) {
      await navigate(client, routeUrl(route.route));
      if (route.afterNavigate) {
        await route.afterNavigate(client);
      }
      results.push(await measure(client, route.key, viewport.name, viewport.width, viewport.height));
    }
  }
  client.close();
  const failures = results.filter((result) => result.blockers.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-016',
    baseUrl,
    systemId,
    generatedAt: new Date().toISOString(),
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'responsive-layout-audit.json'), JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`Responsive audit failed: ${failures.map((item) => `${item.viewport}/${item.key}:${item.blockers.join('|')}`).join('; ')}`);
  }
  console.log(JSON.stringify(output, null, 2));
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R13_BASE_URL = $BaseUrl
$env:R13_ACCESS_TOKEN = $accessToken
$env:R13_REFRESH_TOKEN = $refreshToken
$env:R13_SYSTEM_ID = $script:SystemId
$env:R13_ACCOUNT_ID = [string]$login.profile.accountId
$env:R13_EVIDENCE_DIR = $EvidenceDir
$env:R13_CDP_PORT = [string]$debugPort

try {
    $nodeOutput = & $nodeExe $nodeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Node responsive audit exited with code $LASTEXITCODE"
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

$resultPath = Join-Path $EvidenceDir 'responsive-layout-audit.json'
$result = Get-Content -Raw $resultPath | ConvertFrom-Json
Assert-True -Condition ($result.status -eq 'PASS') -Message "R13 responsive audit failed: $(Get-Content -Raw $resultPath)"

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-016'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = [string]$switch.tenantId
    evidenceDir = $EvidenceDir
    resultPath = $resultPath
    screenshotCount = @($result.results).Count
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 20
