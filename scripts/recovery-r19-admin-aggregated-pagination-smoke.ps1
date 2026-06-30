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
    foreach ($systemId in @($script:CreatedSystemIds)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-R19 admin pagination cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-R19-$script:Suffix-$systemId"
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
    if ($script:NodeScript -and (Test-Path $script:NodeScript)) {
        Remove-Item -LiteralPath $script:NodeScript -Force -ErrorAction SilentlyContinue
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

$Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:NodeScript = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r19-admin-aggregated-pagination'
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

for ($i = 1; $i -le 21; $i++) {
    $created = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
        systemName = "R19 Page System $Suffix $i"
        systemCode = "r19_page_$($Suffix)_$i"
        tenantMode = 1
        templateCode = 'blank'
    }
    $script:CreatedSystemIds.Add([string]$created.systemId) | Out-Null
}

$TargetSystemId = [string]$script:CreatedSystemIds[0]
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TargetSystemId)) -Message 'Target system id missing.'

for ($i = 1; $i -le 21; $i++) {
    $role = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
        roleName = "R19 Page Role $Suffix $i"
        roleCode = "R19_PAGE_ROLE_$($Suffix)_$i"
        roleType = 'CUSTOM'
        status = 1
        description = 'R19 admin pagination smoke role'
    }
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$role.roleId)) -Message "Role $i create did not return roleId."
}

$PlatformSystems = Invoke-Api -Method 'Get' -Path '/api/v1/platform/systems?pageNo=1&pageSize=20' -Headers $script:AdminHeaders
$SystemRoles = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$TargetSystemId/roles?pageNo=1&pageSize=20" -Headers $script:AdminHeaders
Assert-True -Condition ([bool]$PlatformSystems.hasNext) -Message 'Prepared platform systems did not force page 2.'
Assert-True -Condition ([bool]$SystemRoles.hasNext) -Message 'Prepared system roles did not force page 2.'

$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r19-chrome-$Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath (Find-Chrome) -ArgumentList @(
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--remote-allow-origins=*',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    'about:blank'
) -PassThru -WindowStyle Hidden

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

$script:NodeScript = Join-Path $env:TEMP "unexamine-r19-admin-pagination-$Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R19_BASE_URL;
const systemId = process.env.R19_SYSTEM_ID;
const outDir = process.env.R19_EVIDENCE_DIR;
const port = process.env.R19_CDP_PORT;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function assert(condition, message, details = {}) {
  if (!condition) {
    throw new Error(`${message}: ${JSON.stringify(details)}`);
  }
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
          return new Promise((res, rej) => pending.set(id, { res, rej, method }));
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
      const hasToken = !!localStorage.getItem('unexamine.accessToken');
      const localAccountId = localStorage.getItem('unexamine.accountId');
      return { ok: hasToken && !!localAccountId && location.hash !== '#/login', hash: location.hash, hasToken, localAccountId };
    })())
  `, 12000);
}

async function clickSidebarItem(client, index, targetSelector) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const button = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'))[${index}];
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(targetSelector)}) })`, 10000);
  await delay(700);
}

function stateExpression(panelSelector) {
  return `
    JSON.stringify((() => {
      const panel = document.querySelector(${JSON.stringify(panelSelector)});
      const footer = panel?.querySelector('footer.pagination');
      const buttons = footer ? Array.from(footer.querySelectorAll('button')) : [];
      const previous = buttons[0] || null;
      const next = buttons[1] || null;
      const rows = panel ? Array.from(panel.querySelectorAll('tbody tr')).map((row) => row.innerText.trim()) : [];
      const titles = buttons.map((button) => button.title || '');
      const text = document.body.innerText || '';
      return {
        ok: !!panel && !!footer && !!next && rows.length > 0 && !/Loading\\.\\.\\./.test(text),
        panelFound: !!panel,
        footerFound: !!footer,
        pageText: footer?.querySelector('span')?.innerText || '',
        previousDisabled: previous ? previous.disabled : true,
        nextDisabled: next ? next.disabled : true,
        buttonTitles: titles,
        unsupportedReason: titles.some((title) => title.includes('\\u4e0d\\u652f\\u6301')),
        rowCount: rows.length,
        firstRow: rows[0] || '',
        hash: location.hash,
        bodySnippet: text.slice(0, 1200),
      };
    })())
  `;
}

async function clickNextPage(client, panelSelector) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const panel = document.querySelector(${JSON.stringify(panelSelector)});
        const next = panel?.querySelector('footer.pagination button:last-child');
        if (next) next.click();
      })()
    `,
    returnByValue: true,
  });
}

async function capture(client, key, extra = {}) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: fileName, ...extra };
}

async function verifyPagination(client, key, panelSelector) {
  const before = await waitUntil(client, stateExpression(panelSelector), 12000);
  assert(!before.nextDisabled, `${key} next button is disabled before paging`, before);
  assert(!before.unsupportedReason, `${key} still shows unsupported pagination reason`, before);
  await capture(client, `${key}-page1`, { before });
  await clickNextPage(client, panelSelector);
  const after = await waitUntil(client, `
    JSON.stringify((() => {
      const panel = document.querySelector(${JSON.stringify(panelSelector)});
      const footer = panel?.querySelector('footer.pagination');
      const rows = panel ? Array.from(panel.querySelectorAll('tbody tr')).map((row) => row.innerText.trim()) : [];
      const buttons = footer ? Array.from(footer.querySelectorAll('button')) : [];
      const pageText = footer?.querySelector('span')?.innerText || '';
      const firstRow = rows[0] || '';
      return {
        ok: !!panel && !!footer && rows.length > 0 && firstRow !== ${JSON.stringify(before.firstRow)} && /2/.test(pageText),
        pageText,
        firstRow,
        rowCount: rows.length,
        previousDisabled: buttons[0] ? buttons[0].disabled : true,
        nextDisabled: buttons[1] ? buttons[1].disabled : true,
      };
    })())
  `, 12000);
  assert(!after.previousDisabled, `${key} previous button is disabled after paging`, after);
  await capture(client, `${key}-page2`, { after });
  return { before, after };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 960, deviceScaleFactor: 1, mobile: false });
  await client.send('Emulation.setVisibleSize', { width: 1440, height: 960 });

  const login = await realLogin(client);

  await navigate(client, urlFor('/platform/admin'));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`, 12000);
  await clickSidebarItem(client, 2, '#platform-system');
  const platform = await verifyPagination(client, 'platform-systems', '#platform-system');

  await navigate(client, urlFor(`/systems/${systemId}/admin`));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`, 12000);
  await clickSidebarItem(client, 2, '#role-management');
  const systemRoles = await verifyPagination(client, 'system-roles', '#role-management');

  client.close();
  console.log(JSON.stringify({
    login,
    platform,
    systemRoles,
    screenshots: [
      'platform-systems-page1.png',
      'platform-systems-page2.png',
      'system-roles-page1.png',
      'system-roles-page2.png',
    ],
  }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $script:NodeScript -Encoding UTF8

$env:R19_BASE_URL = $BaseUrl
$env:R19_SYSTEM_ID = $TargetSystemId
$env:R19_EVIDENCE_DIR = $EvidenceDir
$env:R19_CDP_PORT = [string]$debugPort
$browserJson = node $script:NodeScript
if ($LASTEXITCODE -ne 0) {
    throw "Node admin pagination browser audit exited with code $LASTEXITCODE"
}
$BrowserEvidence = $browserJson | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
if ($script:NodeScript -and (Test-Path $script:NodeScript)) {
    Remove-Item -LiteralPath $script:NodeScript -Force -ErrorAction SilentlyContinue
}

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    targetSystemId = $TargetSystemId
    createdSystemCount = @($script:CreatedSystemIds).Count
    platformTotal = $PlatformSystems.total
    systemRoleTotal = $SystemRoles.total
    browser = $BrowserEvidence
    cleanup = $script:CleanupResult
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r19-admin-aggregated-pagination-result.json'
$Result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $resultPath -Encoding UTF8

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r19-admin-aggregated-pagination-2026-06-29.md'
@"
# R19 Admin Aggregated Pagination Smoke

Status: PASS

Base URL: $BaseUrl

Target system: $TargetSystemId

Evidence:

- Platform systems page 1 -> page 2 was clicked in the deployed frontend, and the first visible row changed.
- System roles page 1 -> page 2 was clicked in the deployed frontend, and the first visible row changed.
- No verified admin pagination control reported the unsupported-pagination disabled reason.
- Created systems: $(@($script:CreatedSystemIds).Count)
- Cleanup: $($script:CleanupResult -join '; ')

Screenshots:

- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/platform-systems-page1.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/platform-systems-page2.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/system-roles-page1.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/system-roles-page2.png

Machine-readable result: docs/evidence/recovery/r19-admin-aggregated-pagination-result.json
"@ | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$Result | ConvertTo-Json -Depth 80
