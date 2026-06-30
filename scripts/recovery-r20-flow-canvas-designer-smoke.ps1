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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 60 -Compress }
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
                reason = 'recovery-R20 flow canvas cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-R20-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r20-flow-canvas-designer'
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
    systemName = "R20 Flow System $Suffix"
    systemCode = "r20_flow_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null

$Flow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows" -Headers $script:AdminHeaders -Body @{
    flowCode = "flow_r20_$Suffix"
    flowName = "R20 Flow $Suffix"
    boundModuleId = $null
    triggerRule = @{
        triggerType = 'MANUAL_ACTION'
        actionCodes = @('record.submitApproval')
        conditionExpression = ''
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @()
        edges = @()
    }
    description = 'R20 browser canvas designer smoke'
}
$FlowId = [string]$Flow.flowId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($FlowId)) -Message 'Flow create did not return flowId.'

$EmptyCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/publish-check" -Headers $script:AdminHeaders -Body @{
    reason = 'R20 empty canvas expected failure'
    idempotencyKey = "flow-empty-check-r20-$Suffix"
}
Assert-True -Condition ($EmptyCheck.passed -eq $false) -Message 'Empty canvas publish-check unexpectedly passed.'

$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r20-chrome-$Suffix"
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

$script:NodeScript = Join-Path $env:TEMP "unexamine-r20-flow-canvas-$Suffix.js"
$nodeSource = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R20_BASE_URL;
const systemId = process.env.R20_SYSTEM_ID;
const outDir = process.env.R20_EVIDENCE_DIR;
const port = process.env.R20_CDP_PORT;

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

async function capture(client, key, extra = {}) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: fileName, ...extra };
}

async function clickFlowSidebar(client) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const items = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'));
        const button = items[4] || items.find((item) => item.textContent.includes('娴佺▼'));
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#flow-management') })`, 10000);
}

async function clickButtonByText(client, text, rootSelector = 'body') {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const root = document.querySelector(${JSON.stringify(rootSelector)}) || document.body;
        const button = Array.from(root.querySelectorAll('button')).find((item) => item.textContent.includes(${JSON.stringify(text)}));
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
}

async function clickDesignerHeadButton(client, index) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.flow-designer-head .inline-actions button'));
        const button = buttons[${index}];
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
}

async function configureCanvas(client) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const button = document.querySelector('#flow-management tbody tr .row-actions button');
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.flow-designer') && !!document.querySelector('.flow-node-library') })`, 12000);
  await capture(client, 'designer-empty');

  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const button = document.querySelector('.flow-node-library .button.secondary');
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: document.querySelectorAll('.flow-canvas-node').length >= 2 })`, 8000);

  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const textarea = document.querySelector('.flow-property-panel textarea');
        const save = document.querySelector('.flow-property-panel .inline-actions .button.primary');
        textarea.value = JSON.stringify({
          approvalType: 'OR_SIGN',
          assigneeType: 'ROLE',
          assigneeIds: [],
          allowTransfer: true,
          allowReject: true,
          reasonRequired: false,
          r20Property: 'browser-edited'
        }, null, 2);
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        textarea.dispatchEvent(new Event('change', { bubbles: true }));
        save.click();
      })()
    `,
    returnByValue: true,
  });
  await delay(500);
  await capture(client, 'designer-configured');

  await clickDesignerHeadButton(client, 0);
  const saved = await waitUntil(client, `
    JSON.stringify((() => {
      const nodes = document.querySelectorAll('.flow-canvas-node').length;
      const disabled = Array.from(document.querySelectorAll('.flow-designer-head .inline-actions button')).some((button) => button.disabled);
      return { ok: nodes >= 2 && !disabled, nodes, disabled };
    })())
  `, 12000);

  await clickDesignerHeadButton(client, 1);
  const simulation = await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.querySelector('.flow-designer')?.innerText || '';
      return { ok: text.includes('traceId='), text };
    })())
  `, 12000);

  await clickDesignerHeadButton(client, 2);
  const publishCheck = await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.querySelector('.flow-designer')?.innerText || '';
      const disabled = Array.from(document.querySelectorAll('.flow-designer-head .inline-actions button')).some((button) => button.disabled);
      return {
        ok: text.includes('traceId=') && !disabled && !text.includes('E_FLOW') && !text.includes('模拟通过') && !text.includes('检查中'),
        disabled,
        text
      };
    })())
  `, 12000);
  await capture(client, 'designer-publish-check', { simulation, publishCheck });
  return { saved, simulation, publishCheck };
}

async function mobileContainment(client) {
  await client.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 720, deviceScaleFactor: 1, mobile: true });
  await client.send('Emulation.setVisibleSize', { width: 390, height: 720 });
  await delay(700);
  const state = await evaluateJson(client, `
    JSON.stringify((() => {
      const overflowX = Math.max(0, document.documentElement.scrollWidth - window.innerWidth);
      const designer = document.querySelector('.flow-designer');
      const canvasPanel = document.querySelector('.flow-canvas-panel');
      return {
        ok: !!designer && overflowX === 0 && !!canvasPanel && canvasPanel.scrollWidth > canvasPanel.clientWidth,
        overflowX,
        canvasInternalScroll: canvasPanel ? canvasPanel.scrollWidth - canvasPanel.clientWidth : 0,
        width: window.innerWidth,
      };
    })())
  `);
  assert(state.ok, 'Mobile flow designer containment failed', state);
  await capture(client, 'designer-mobile', state);
  return state;
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
  await navigate(client, urlFor(`/systems/${systemId}/admin`));
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`, 12000);
  await clickFlowSidebar(client);
  const flowPanel = await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#flow-management tbody tr'), text: document.querySelector('#flow-management')?.innerText || '' })`, 10000);
  const designer = await configureCanvas(client);
  const mobile = await mobileContainment(client);

  client.close();
  console.log(JSON.stringify({
    login,
    flowPanel,
    designer,
    mobile,
    screenshots: [
      'designer-empty.png',
      'designer-configured.png',
      'designer-publish-check.png',
      'designer-mobile.png'
    ],
  }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $script:NodeScript -Encoding UTF8

$env:R20_BASE_URL = $BaseUrl
$env:R20_SYSTEM_ID = $SystemId
$env:R20_EVIDENCE_DIR = $EvidenceDir
$env:R20_CDP_PORT = [string]$debugPort
$browserJson = node $script:NodeScript
if ($LASTEXITCODE -ne 0) {
    throw "Node flow canvas browser audit exited with code $LASTEXITCODE"
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

$CanvasReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/flows/$FlowId/canvas" -Headers $script:AdminHeaders
Assert-True -Condition (@($CanvasReadback.nodes).Count -ge 2) -Message 'Saved canvas did not read back nodes.'
Assert-True -Condition (@($CanvasReadback.edges).Count -ge 1) -Message 'Saved canvas did not read back edges.'

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/publish-check" -Headers $script:AdminHeaders -Body @{
    reason = 'R20 saved canvas publish check'
    idempotencyKey = "flow-check-r20-$Suffix"
}
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message "Saved canvas publish-check did not pass: $($PublishCheck | ConvertTo-Json -Depth 30 -Compress)"

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'R20 saved canvas publish'
    idempotencyKey = "flow-publish-r20-$Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Published.snapshotId)) -Message 'Flow publish did not return snapshotId.'

$Snapshots = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/flows/$FlowId/snapshots" -Headers $script:AdminHeaders
Assert-True -Condition (@($Snapshots).Count -ge 1) -Message 'Published flow snapshots did not read back.'

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $SystemId
    flowId = $FlowId
    emptyPublishCheckPassed = $EmptyCheck.passed
    canvasNodeCount = @($CanvasReadback.nodes).Count
    canvasEdgeCount = @($CanvasReadback.edges).Count
    publishCheckTraceId = $PublishCheck.traceId
    publishedVersion = $Published.version
    snapshotCount = @($Snapshots).Count
    browser = $BrowserEvidence
    cleanup = $script:CleanupResult
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r20-flow-canvas-designer-result.json'
$Result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $resultPath -Encoding UTF8

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r20-flow-canvas-designer-2026-06-29.md'
$summary = @(
    '# R20 Flow Canvas Designer Smoke',
    '',
    'Status: PASS',
    '',
    "Base URL: $BaseUrl",
    '',
    '## Covered Flow',
    '',
    "- Created disposable system $SystemId.",
    "- Created empty draft flow $FlowId.",
    '- Empty canvas publish-check failed as expected.',
    '- Browser used real /login, opened system admin flow management, clicked canvas configuration, inserted approval/end nodes, edited node JSON properties, saved canvas, ran simulation, and ran publish-check.',
    "- API readback confirmed $(@($CanvasReadback.nodes).Count) nodes and $(@($CanvasReadback.edges).Count) edges persisted.",
    "- Publish-check passed with traceId $($PublishCheck.traceId).",
    "- Publish created version $($Published.version) and snapshot $($Published.snapshotId).",
    "- Mobile containment kept document overflow at $($BrowserEvidence.mobile.overflowX) and canvas overflow inside the canvas panel.",
    "- Cleanup: $($script:CleanupResult -join ', ').",
    '',
    '## Screenshots',
    '',
    '- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-empty.png',
    '- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-configured.png',
    '- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-publish-check.png',
    '- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-mobile.png',
    '',
    '## Result JSON',
    '',
    '- docs/evidence/recovery/r20-flow-canvas-designer-result.json'
) -join [Environment]::NewLine
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$Result | ConvertTo-Json -Depth 80
