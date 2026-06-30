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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
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
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r24 home page config cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r24-$script:Suffix-$systemId"
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

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r24-home-page-config'
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

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R24 Home Config $script:Suffix"
    systemCode = "r24_home_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'Created system did not return systemId.'

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r24 home page setup'
}

$HomeTitle = "R24 Home $script:Suffix"
$HomeSubtitle = "R24 configured home page survives admin save and runtime dashboard readback."
$Payload = @{
    title = $HomeTitle
    subtitle = $HomeSubtitle
    visualTone = 'calm-workbench'
    widgets = @(
        @{ widgetCode = 'overview'; widgetName = 'Work Overview'; widgetType = 'metric'; sourceType = 'WORK_DASHBOARD'; sortOrder = 10; visible = $true },
        @{ widgetCode = 'warnings'; widgetName = 'Today Warnings'; widgetType = 'list'; sourceType = 'WORK_WARNING'; sortOrder = 20; visible = $true },
        @{ widgetCode = 'calendar'; widgetName = 'Monthly Calendar'; widgetType = 'calendar'; sourceType = 'WORK_CALENDAR'; sortOrder = 30; visible = $true },
        @{ widgetCode = 'modules'; widgetName = 'Runtime Modules'; widgetType = 'shortcut'; sourceType = 'RUNTIME_MODULES'; sortOrder = 40; visible = $true }
    )
    changeReason = 'recovery-r24 persisted home page config'
}

$Saved = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$script:SystemId/work/home-page-config" -Headers $script:AdminHeaders -Body $Payload
Assert-True -Condition ($Saved.title -eq $HomeTitle) -Message 'PATCH result did not return saved home page title.'

$AdminReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/work/home-page-config" -Headers $script:AdminHeaders
Assert-True -Condition ($AdminReadback.title -eq $HomeTitle) -Message 'Admin GET did not read back saved home page title.'
Assert-True -Condition (@($AdminReadback.widgets).Count -ge 4) -Message 'Admin GET did not read back configured widgets.'

$RuntimeReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/work/home-page" -Headers $script:AdminHeaders
Assert-True -Condition ($RuntimeReadback.title -eq $HomeTitle) -Message 'Runtime GET did not read saved home page title.'

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/home-page-config/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message 'Home page publish-check did not pass.'
Assert-True -Condition (@($PublishCheck.items).Count -ge 3) -Message 'Home page publish-check did not return check items.'

$NormalLoginName = "r24_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r24_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R24 Owned $script:Suffix"
    systemCode = "r24_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$Member = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R24 Runtime Member $script:Suffix"
    employeeNo = "R24NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r24_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @()
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($Member.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r24 normal member runtime read'
}
$NormalRuntimeRead = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/work/home-page" -Headers $NormalHeaders
Assert-True -Condition ($NormalRuntimeRead.title -eq $HomeTitle) -Message 'Normal member could not read runtime home page config.'
$ForbiddenWrite = Invoke-ExpectedForbidden -Method 'Patch' -Path "/api/v1/systems/$script:SystemId/work/home-page-config" -Headers $NormalHeaders -Body @{
    title = "R24 Forbidden $script:Suffix"
    subtitle = 'normal member must not write admin config'
    visualTone = 'calm-workbench'
}

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r24-chrome-$script:Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r24-home-page-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R24_BASE_URL;
const systemId = process.env.R24_SYSTEM_ID;
const outDir = process.env.R24_EVIDENCE_DIR;
const port = process.env.R24_CDP_PORT;
const expectedTitle = process.env.R24_EXPECTED_TITLE;

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
    const waiters = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      waitFor(eventName, timeoutMs = 7000) {
        return new Promise((res) => {
          const timer = setTimeout(() => res(null), timeoutMs);
          waiters.set(eventName, (payload) => {
            clearTimeout(timer);
            res(payload);
          });
        });
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
      if (message.method && waiters.has(message.method)) {
        const waiter = waiters.get(message.method);
        waiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}

function urlFor(route) {
  return `${baseUrl}/#${route}`;
}

async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) {
    throw new Error(JSON.stringify(result.exceptionDetails));
  }
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}

async function waitUntil(client, expression, timeoutMs = 12000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(250);
  }
  throw new Error(`Timed out: ${JSON.stringify(last)}`);
}

async function navigate(client, url) {
  const loaded = client.waitFor('Page.loadEventFired', 7000);
  await client.send('Page.navigate', { url });
  await loaded;
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

async function realAdminLogin(client) {
  await navigate(client, urlFor('/login'));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`);
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
  await waitUntil(client, `JSON.stringify({ ok: !!localStorage.getItem('unexamine.accessToken') && location.hash !== '#/login' })`);
}

async function screenshotAndCheck(client, key, mode) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return evaluateJson(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const vw = window.innerWidth;
      const scrollWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
      const overflowX = Math.max(0, Math.ceil(scrollWidth - vw));
      const blockers = [];
      if (!text.includes(${JSON.stringify(expectedTitle)})) blockers.push('configured title missing');
      if (overflowX > 2) blockers.push('horizontal overflow');
      if (/Loading\\.\\.\\./.test(text)) blockers.push('loading text still visible');
      if (${JSON.stringify(mode)} === 'admin') {
        if (!document.querySelector('.admin-content')) blockers.push('admin content missing');
        if (!document.querySelector('#dashboard-config')) blockers.push('dashboard-config panel missing');
      }
      return {
        key: ${JSON.stringify(key)},
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        titleVisible: text.includes(${JSON.stringify(expectedTitle)}),
        overflowX,
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

  await realAdminLogin(client);
  await client.send('Runtime.evaluate', {
    expression: `window.__r24ExpectedTitle = ${JSON.stringify(expectedTitle)}`,
    returnByValue: true,
  });

  await navigate(client, urlFor(`/systems/${systemId}/admin`));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-content') })`);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'));
        const target = buttons[6];
        if (target) {
          target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
        }
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#dashboard-config') || (document.body.innerText || '').includes(window.__r24ExpectedTitle || '') })`);
  const admin = await screenshotAndCheck(client, 'desktop-admin-home-page-config', 'admin');

  await navigate(client, urlFor(`/systems/${systemId}/dashboard`));
  await client.send('Runtime.evaluate', {
    expression: `window.__r24ExpectedTitle = ${JSON.stringify(expectedTitle)}`,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(window.__r24ExpectedTitle || '') })`);
  const runtimeDesktop = await screenshotAndCheck(client, 'desktop-runtime-dashboard-home-page', 'runtime');

  await setViewport(client, 390, 720);
  await navigate(client, urlFor(`/systems/${systemId}/dashboard`));
  await client.send('Runtime.evaluate', {
    expression: `window.__r24ExpectedTitle = ${JSON.stringify(expectedTitle)}`,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: (document.body.innerText || '').includes(window.__r24ExpectedTitle || '') })`);
  const runtimeMobile = await screenshotAndCheck(client, 'mobile-runtime-dashboard-home-page', 'runtime');

  client.close();
  const results = [admin, runtimeDesktop, runtimeMobile];
  const failures = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-027',
    baseUrl,
    systemId,
    expectedTitle,
    generatedAt: new Date().toISOString(),
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'home-page-config-browser-audit.json'), JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`R24 browser audit failed: ${JSON.stringify(failures)}`);
  }
  console.log(JSON.stringify(output, null, 2));
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
$env:R24_BASE_URL = $BaseUrl
$env:R24_SYSTEM_ID = $script:SystemId
$env:R24_EVIDENCE_DIR = $EvidenceDir
$env:R24_CDP_PORT = [string]$debugPort
$env:R24_EXPECTED_TITLE = $HomeTitle

try {
    $nodeOutput = & $nodeExe $nodeScript
    $nodeExitCode = $LASTEXITCODE
    if ($LASTEXITCODE -ne 0) {
        $nodeOutput | Out-Host
        throw "Node R24 browser audit exited with code $nodeExitCode"
    }
    $script:CleanupResult = Remove-CreatedSystems
} finally {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($nodeExitCode -eq 0 -and (Test-Path $nodeScript)) {
        Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
    }
}

$BrowserResultPath = Join-Path $EvidenceDir 'home-page-config-browser-audit.json'
$BrowserResult = Get-Content -Raw $BrowserResultPath | ConvertFrom-Json
Assert-True -Condition ($BrowserResult.status -eq 'PASS') -Message "R24 browser audit failed: $(Get-Content -Raw $BrowserResultPath)"

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-027'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    title = $HomeTitle
    adminReadbackTitle = $AdminReadback.title
    runtimeReadbackTitle = $RuntimeReadback.title
    normalRuntimeReadTitle = $NormalRuntimeRead.title
    forbiddenWriteStatus = $ForbiddenWrite.status
    publishCheckPassed = $PublishCheck.passed
    publishCheckItems = @($PublishCheck.items).Count
    browserResultPath = $BrowserResultPath
    evidenceDir = $EvidenceDir
    screenshotCount = @($BrowserResult.results).Count
    cleanup = $script:CleanupResult
}
$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r24-home-page-config-result.json'
$Result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
$Result | ConvertTo-Json -Depth 40

