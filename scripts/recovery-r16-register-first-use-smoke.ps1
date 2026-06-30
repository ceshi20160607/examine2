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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 12 -Compress)"
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
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:AdminHeaders) {
        return 'SKIPPED'
    }
    $cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:AdminHeaders -Body @{
        reason = 'recovery-r16 register first-use cleanup'
        impactScope = 'created_by_recovery_r16'
        idempotencyKey = "cleanup-r16-$script:Suffix"
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
    if ($script:NodeScript -and (Test-Path $script:NodeScript)) {
        Remove-Item -LiteralPath $script:NodeScript -Force -ErrorAction SilentlyContinue
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
$AccountName = "r16_register_$Suffix"
$Password = 'Aa123456!'
$SystemName = "R16 Register First Use $Suffix"
$SystemCode = "r16_register_$Suffix"
$Mobile = "18$Suffix"
$Email = "r16_register_$Suffix@example.com"
$script:SystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:NodeScript = $null
$CleanupResult = 'SKIPPED'
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r16-register-first-use'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($health | ConvertTo-Json -Depth 12 -Compress)"

$adminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($adminLogin.accessToken)" }

$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r16-register-chrome-$Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath (Find-Chrome) -ArgumentList @(
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
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

$script:NodeScript = Join-Path $env:TEMP "unexamine-r16-register-first-use-$Suffix.js"
$nodeSource = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R16_BASE_URL;
const accountName = process.env.R16_ACCOUNT_NAME;
const mobile = process.env.R16_MOBILE;
const email = process.env.R16_EMAIL;
const password = process.env.R16_PASSWORD;
const systemName = process.env.R16_SYSTEM_NAME;
const systemCode = process.env.R16_SYSTEM_CODE;
const port = process.env.R16_CDP_PORT;
const outDir = process.env.R16_EVIDENCE_DIR;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} ${pathname}`);
  return response.json();
}

async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    return (await cdpJson('/json/list'))[0];
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
      wait(eventName, timeoutMs = 5000) {
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
        message.error ? item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`)) : item.res(message.result);
      }
      const waiter = waiters.get(message.method);
      if (waiter) {
        waiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}

async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}

async function waitUntil(client, expression, timeoutMs = 12000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluate(client, expression).catch((error) => ({ ok: false, error: error.message }));
    if (last && last.ok) return last;
    await delay(250);
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}

async function navigate(client, url) {
  const waitLoad = client.wait('Page.loadEventFired', 10000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(1000);
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

async function screenshotAndMeasure(client, key) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return evaluate(client, `
    (() => {
      const vw = window.innerWidth;
      const doc = document.documentElement;
      const body = document.body;
      const text = body.innerText || '';
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      return {
        key: ${JSON.stringify(key)},
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        textIncludesOnboarding: !!document.querySelector('.onboarding-panel'),
        textIncludesInitButton: !!document.querySelector('.onboarding-panel button'),
        textIncludesSystemAdmin: Array.from(document.querySelectorAll('button')).some((button) => button.textContent.includes('系统后台')),
        hasToken: !!localStorage.getItem('unexamine.accessToken'),
        hasAccountId: !!localStorage.getItem('unexamine.accountId'),
        accountId: localStorage.getItem('unexamine.accountId'),
        overflowX: Math.max(0, Math.ceil(scrollWidth - vw)),
        adminContentVisible: !!document.querySelector('.admin-content'),
        onboardingVisible: !!document.querySelector('.onboarding-panel'),
        bodySnippet: text.slice(0, 300),
      };
    })()
  `);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const client = await connect((await newTarget()).webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await setViewport(client, 1280, 720);

  await navigate(client, `${baseUrl}/register-with-system`);
  await waitUntil(client, `({ ok: !!document.querySelector('input[data-field-name="accountName"]') })`);
  await evaluate(client, `(() => {
    const values = {
      accountName: ${JSON.stringify(accountName)},
      mobile: ${JSON.stringify(mobile)},
      email: ${JSON.stringify(email)},
      password: ${JSON.stringify(password)},
      systemName: ${JSON.stringify(systemName)},
      systemCode: ${JSON.stringify(systemCode)}
    };
    for (const [name, value] of Object.entries(values)) {
      const input = document.querySelector('input[data-field-name="' + name + '"]');
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    document.querySelector('form.auth-form button.button.primary').click();
    return true;
  })()`);

  const dashboardState = await waitUntil(client, `(() => {
    const text = document.body.innerText || '';
    const parts = location.hash.replace(/^#/, '').split('/');
    const systemId = parts[1] === 'systems' && parts[3] === 'dashboard' ? parts[2] : null;
    const onboarding = document.querySelector('.onboarding-panel');
    return {
      ok: !!systemId && !!localStorage.getItem('unexamine.accessToken') && !!onboarding && !!onboarding.querySelector('button'),
      hash: location.hash,
      systemId,
      hasToken: !!localStorage.getItem('unexamine.accessToken'),
      hasAccountId: !!localStorage.getItem('unexamine.accountId'),
      textSnippet: text.slice(0, 300)
    };
  })()`, 16000);

  const desktopDashboard = await screenshotAndMeasure(client, 'desktop-register-dashboard');
  await evaluate(client, `(() => {
    const button = document.querySelector('.onboarding-panel button');
    if (!button) return false;
    button.click();
    return true;
  })()`);
  const adminState = await waitUntil(client, `(() => {
    const text = document.body.innerText || '';
    return {
      ok: location.hash.includes('/admin') && !!document.querySelector('.admin-content'),
      hash: location.hash,
      textSnippet: text.slice(0, 300)
    };
  })()`, 12000);
  const desktopAdmin = await screenshotAndMeasure(client, 'desktop-register-admin');

  await setViewport(client, 390, 720);
  await navigate(client, `${baseUrl}/#/systems/${dashboardState.systemId}/dashboard`);
  await waitUntil(client, `(() => {
    const onboarding = document.querySelector('.onboarding-panel');
    return { ok: !!onboarding && !!onboarding.querySelector('button') };
  })()`, 12000);
  const mobileDashboard = await screenshotAndMeasure(client, 'mobile-register-dashboard');

  client.close();
  const failures = [desktopDashboard, desktopAdmin, mobileDashboard].flatMap((item) => {
    const problems = [];
    if (!item.hasToken || !item.hasAccountId) problems.push(`${item.key}: missing auth state`);
    if (item.overflowX > 2) problems.push(`${item.key}: overflowX=${item.overflowX}`);
    if (item.key.includes('dashboard') && (!item.textIncludesOnboarding || !item.textIncludesInitButton)) problems.push(`${item.key}: onboarding missing`);
    if (item.key.includes('admin') && !item.adminContentVisible) problems.push(`${item.key}: admin content missing`);
    return problems;
  });
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-019',
    baseUrl,
    accountName,
    systemName,
    systemCode,
    systemId: dashboardState.systemId,
    generatedAt: new Date().toISOString(),
    dashboardState,
    adminState,
    measurements: [desktopDashboard, desktopAdmin, mobileDashboard],
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'register-first-use-browser.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(failures.join('; '));
  }
}

run().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $script:NodeScript -Encoding UTF8

$env:R16_BASE_URL = $BaseUrl
$env:R16_ACCOUNT_NAME = $AccountName
$env:R16_MOBILE = $Mobile
$env:R16_EMAIL = $Email
$env:R16_PASSWORD = $Password
$env:R16_SYSTEM_NAME = $SystemName
$env:R16_SYSTEM_CODE = $SystemCode
$env:R16_CDP_PORT = [string]$debugPort
$env:R16_EVIDENCE_DIR = $EvidenceDir
$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
$browserJson = & $nodeExe $script:NodeScript
if ($LASTEXITCODE -ne 0) {
    throw "Node register first-use browser audit exited with code $LASTEXITCODE"
}
$browserResult = $browserJson | ConvertFrom-Json
$script:SystemId = [string]$browserResult.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'Browser register did not produce systemId.'

$registeredLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $AccountName
    password = $Password
    loginTarget = 'PLATFORM'
}
$registeredHeaders = @{ Authorization = "Bearer $($registeredLogin.accessToken)" }
$switchContext = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $registeredHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $null
    reason = 'recovery-r16-register-first-use-readback'
}
Assert-True -Condition (@($switchContext.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN') -Message 'Registered account did not receive SYSTEM_SUPER_ADMIN context.'

$departments = @(Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/org/departments" -Headers $registeredHeaders)
$defaultDepartment = @($departments | Where-Object { $_.deptCode -eq 'default_department' }) | Select-Object -First 1
Assert-True -Condition ($null -ne $defaultDepartment) -Message 'Registered first-use system does not have default_department.'

$members = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/members?pageNo=1&pageSize=20" -Headers $registeredHeaders
$ownerMember = @($members.records | Where-Object { [string]$_.deptId -eq [string]$defaultDepartment.deptId }) | Select-Object -First 1
Assert-True -Condition ($null -ne $ownerMember) -Message 'Registered owner member is not bound to default_department.'

$CleanupResult = Remove-CreatedSystem

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
if ($script:NodeScript -and (Test-Path $script:NodeScript)) {
    Remove-Item -LiteralPath $script:NodeScript -Force -ErrorAction SilentlyContinue
}

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-019'
    suffix = $Suffix
    baseUrl = $BaseUrl
    accountName = $AccountName
    systemId = $script:SystemId
    systemName = $SystemName
    browser = @{
        dashboardHash = $browserResult.dashboardState.hash
        adminHash = $browserResult.adminState.hash
        screenshotCount = @($browserResult.measurements).Count
        failures = @($browserResult.failures)
    }
    switchContext = @{
        tenantId = [string]$switchContext.tenantId
        systemMemberId = [string]$switchContext.systemMemberId
        accountMemberBindingId = [string]$switchContext.accountMemberBindingId
        effectiveRoleIds = @($switchContext.effectiveRoleIds)
    }
    defaultDepartment = @{
        deptId = [string]$defaultDepartment.deptId
        deptCode = [string]$defaultDepartment.deptCode
        ownerMemberId = [string]$ownerMember.systemMemberId
    }
    screenshots = @(
        'docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-dashboard.png',
        'docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-admin.png',
        'docs/evidence/recovery/screenshots/r16-register-first-use/mobile-register-dashboard.png'
    )
    cleanup = $CleanupResult
    generatedAt = (Get-Date).ToString('o')
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r16-register-first-use-result.json'
$result | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -Path $resultPath

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r16-register-first-use-browser-closure-2026-06-29.md'
$summary = @"
# R16 Register First-Use Browser Closure Evidence

Status: PASS

- Base URL: $BaseUrl
- Task: REC-P0-019
- Browser route: /register-with-system
- Disposable account: $AccountName
- Disposable system id: $script:SystemId
- The deployed browser submitted the real register form.
- Registration landed on /systems/$script:SystemId/dashboard.
- The dashboard showed the first-use initialization prompt and initialization action.
- The browser clicked the initialization action and opened the system backend.
- Registered account switch context includes SYSTEM_SUPER_ADMIN.
- The fresh system has `default_department`, and the owner member is bound to it.
- Cleanup result: $CleanupResult

Machine-readable result: docs/evidence/recovery/r16-register-first-use-result.json
Browser measurement: docs/evidence/recovery/screenshots/r16-register-first-use/register-first-use-browser.json
Screenshots:
- docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-dashboard.png
- docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-admin.png
- docs/evidence/recovery/screenshots/r16-register-first-use/mobile-register-dashboard.png
"@
$summary | Set-Content -Encoding UTF8 -Path $summaryPath

Write-Host ($result | ConvertTo-Json -Depth 30)
