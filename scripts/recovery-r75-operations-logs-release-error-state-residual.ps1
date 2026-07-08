param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        throw "$Area/$Name failed: $Detail"
    }
}

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logFile
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected JSON file was not found: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
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

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

trap {
    Stop-BrowserAudit
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R75_OPERATIONS_LOGS_RELEASE_ERROR_STATE_RESIDUAL_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 40)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r75-operations-logs-release-error-state-residual'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r75-operations-logs-release-error-state-residual-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r75-operations-logs-release-error-state-residual-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'operations-logs-release-error-browser-audit.json'
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$platformAdminSource = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend\src\features\platform-admin\platformAdmin.ts')
$systemAdminSource = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend\src\features\system-admin\systemAdmin.ts')

foreach ($marker in @(
    'opsAction',
    'opsResult',
    'opsTaskId',
    'opsDryRun',
    'opsRollbackSupported',
    'opsDeploymentList',
    'opsCachePolicyList',
    'platformLogsPanel',
    'platformOpsGovernance'
)) {
    Add-Check $script:Checks 'source' "platform ops marker $marker exists" ($platformAdminSource -like "*$marker*") $marker
}
foreach ($marker in @('systemLogsPanel', 'systemLogTable')) {
    Add-Check $script:Checks 'source' "system log marker $marker exists" ($systemAdminSource -like "*$marker*") $marker
}

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
if ($env:R75_REUSE_R66 -eq 'true' -and (Test-Path -LiteralPath (Join-Path $RepoRoot 'docs\evidence\recovery\r66-operations-logs-release-maintenance-first-use-result.json'))) {
    $childResults += [ordered]@{
        name = 'recovery-r66-operations-logs-release-maintenance-first-use'
        exitCode = 0
        logFile = 'REUSED_FOR_R75_DEBUG_ONLY'
    }
} else {
    $childResults += Invoke-ChildScript 'recovery-r66-operations-logs-release-maintenance-first-use' `
        (Join-Path $PSScriptRoot 'recovery-r66-operations-logs-release-maintenance-first-use.ps1') @('-BaseUrl', $BaseUrl)
}

$r66 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r66-operations-logs-release-maintenance-first-use-result.json')

Add-Check $script:Checks 'release' 'deployed frontend assets match release package' `
    ($r66.releaseVerifyStatus -eq 'PASS') `
    "releaseVerify=$($r66.releaseVerifyStatus)"
Add-Check $script:Checks 'operations' 'R66 operation tasks have traceable task ids' `
    (-not [string]::IsNullOrWhiteSpace([string]$r66.backupTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$r66.restoreTaskId) -and -not [string]::IsNullOrWhiteSpace([string]$r66.rollbackTaskId)) `
    "backup=$($r66.backupTaskId), restore=$($r66.restoreTaskId), rollback=$($r66.rollbackTaskId)"
Add-Check $script:Checks 'logs' 'R66 platform and system logs read back by trace' `
    ([int]$r66.platformAuditReadbackCount -ge 9 -and [int]$r66.systemAuditReadbackCount -ge 1) `
    "platform=$($r66.platformAuditReadbackCount), system=$($r66.systemAuditReadbackCount)"
Add-Check $script:Checks 'permission' 'R66 forbidden operation denials are failure logs' `
    ([int]$r66.deniedPlatformStatus -eq 403 -and [int]$r66.deniedSystemStatus -eq 403 -and [string]$r66.deniedPlatformAuditResult -eq 'FAILURE' -and [string]$r66.deniedSystemAuditResult -eq 'FAILURE') `
    "platform=$($r66.deniedPlatformStatus)/$($r66.deniedPlatformAuditResult), system=$($r66.deniedSystemStatus)/$($r66.deniedSystemAuditResult)"

$debugPort = Get-FreeTcpPort
$BrowserWorkDir = Join-Path $RepoRoot '.tmp-browser'
New-Item -ItemType Directory -Force -Path $BrowserWorkDir | Out-Null
$script:ChromeProfileDir = Join-Path $BrowserWorkDir "unexamine-r75-chrome-$((Get-Date).ToString('HHmmssfff'))"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath (Find-Chrome) -ArgumentList @(
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
Add-Check $script:Checks 'browser' 'headless browser devtools ready' $ready "port=$debugPort"

$nodeScript = Join-Path $BrowserWorkDir "unexamine-r75-ops-browser-$((Get-Date).ToString('HHmmssfff')).js"
$nodeSource = @'
const fs = require('fs');
const http = require('http');
const path = require('path');

const baseUrl = process.env.R75_BASE_URL;
const outDir = process.env.R75_EVIDENCE_DIR;
const port = process.env.R75_CDP_PORT;
const systemId = process.env.R75_SYSTEM_ID;
let stage = 'boot';

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function assert(condition, message, details = {}) {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(details)}`);
}
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
    const waiters = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      waitFor(eventName, timeoutMs = 8000) {
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
      if (message.method && waiters.has(message.method)) {
        const waiter = waiters.get(message.method);
        waiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}
async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(`Runtime.evaluate failed at ${stage}: ${JSON.stringify(result.exceptionDetails)} expression=${expression}`);
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitUntil(client, expression, timeoutMs = 20000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(350);
  }
  throw new Error(`waitUntil timed out at ${stage}. Last=${JSON.stringify(last)}`);
}
async function navigate(client, url) {
  stage = `navigate ${url}`;
  const waitLoad = client.waitFor('Page.loadEventFired', 10000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(900);
}
async function login(client) {
  stage = 'login api session';
  await navigate(client, `${baseUrl}/#/login`);
  const loginResult = await evaluateJson(client, `(async () => {
    localStorage.removeItem('unexamine.accessToken');
    localStorage.removeItem('unexamine.refreshToken');
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ loginName: 'admin', password: '123123aa', loginTarget: 'PLATFORM' }),
    });
    const json = await response.json();
    const data = json.data || {};
    if (!response.ok || json.code !== 'SUCCESS' || !data.accessToken) {
      return { ok: false, status: response.status, json };
    }
    localStorage.setItem('unexamine.accessToken', data.accessToken);
    if (data.refreshToken) localStorage.setItem('unexamine.refreshToken', data.refreshToken);
    if (data.profile?.accountId) localStorage.setItem('unexamine.accountId', data.profile.accountId);
    return JSON.stringify({ ok: true, accountId: data.profile?.accountId || '' });
  })()`);
  assert(loginResult.ok, 'login api failed', loginResult);
  stage = 'authenticated reload';
  const waitLoad = client.waitFor('Page.loadEventFired', 12000);
  await client.send('Page.navigate', { url: `${baseUrl}/index.html#/platform/admin` });
  await waitLoad;
  await delay(1200);
}
async function clickSidebar(client, text, section) {
  stage = `click sidebar ${text}`;
  const selector = `[data-admin-section="${section}"]`;
  await client.send('Runtime.evaluate', { expression: `
    (() => {
      const selector = ${JSON.stringify(selector)};
      const label = ${JSON.stringify(text)};
      const button = document.querySelector(selector) || Array.from(document.querySelectorAll('.admin-sidebar button')).find((item) => item.textContent.includes(label));
      if (!button) throw new Error('sidebar button not found: ' + label);
      button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    })()
  `, returnByValue: true, awaitPromise: true });
}
async function clickAction(client, action) {
  stage = `click action ${action}`;
  const selector = `[data-ops-action="${action}"]`;
  await client.send('Runtime.evaluate', { expression: `
    (() => {
      const selector = ${JSON.stringify(selector)};
      const action = ${JSON.stringify(action)};
      const button = document.querySelector(selector);
      if (!button) throw new Error('ops action not found: ' + action);
      button.click();
    })()
  `, returnByValue: true });
}
async function capture(client, key, extra = {}) {
  const metrics = await evaluateJson(client, `JSON.stringify((function(){var overflowX=Math.max(0,document.documentElement.scrollWidth-window.innerWidth);var blockers=[];var text=document.body.innerText||'';if(text.indexOf('undefined')>=0)blockers.push('undefined-copy');if(text.indexOf('NaN')>=0)blockers.push('nan-copy');return {overflowX:overflowX,blockers:blockers,hash:location.hash};})())`);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const screenshotFile = `${key}.png`;
  fs.writeFileSync(path.join(outDir, screenshotFile), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: screenshotFile, ...metrics, ...extra };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await login(client);
  const results = [];

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 760, mobile: true },
  ]) {
    await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
    await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
    await navigate(client, `${baseUrl}/#/platform/admin`);
    stage = `platform admin shell ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-platform-admin-shell="true"]') || !!document.querySelector('[data-platform-admin-standalone="true"]') })`);
    await clickSidebar(client, '配置管理', 'platform-config');
    stage = `platform ops panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({
      ok: !!document.querySelector('#platform-ops-governance[data-platform-ops-governance="true"]') && document.querySelectorAll('[data-ops-action]').length >= 10,
      hash: location.hash,
      buttons: Array.from(document.querySelectorAll('.admin-sidebar button')).map((button) => button.textContent),
      content: document.querySelector('.admin-content')?.innerText?.slice(0, 800),
      hasOps: !!document.querySelector('#platform-ops-governance'),
      opsActionCount: document.querySelectorAll('[data-ops-action]').length
    })`);
    await clickAction(client, 'platform-health');
    stage = `platform health result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="platform-health"]')?.dataset.opsTraceId?.length > 0 })`);
    const healthState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="platform-health"]').dataset)`);
    await clickAction(client, 'backup');
    stage = `backup result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="backup"]')?.dataset.opsTaskId?.length > 0 })`);
    const backupState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="backup"]').dataset)`);
    await clickAction(client, 'restore-drill');
    stage = `restore drill result ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="restore-drill"]')?.dataset.opsDryRun === 'true' && document.querySelector('[data-ops-result="restore-drill"]')?.dataset.opsRollbackSupported === 'true' })`);
    const restoreState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="restore-drill"]').dataset)`);
    await clickAction(client, 'deployments');
    stage = `deployment list ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: Number(document.querySelector('[data-ops-deployment-list]')?.dataset.opsDeploymentCount || 0) >= 1 })`);
    await clickAction(client, 'cache-read');
    stage = `cache read ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: Number(document.querySelector('[data-ops-cache-policy-list]')?.dataset.opsCachePolicyCount || 0) >= 1 })`);
    await clickAction(client, 'cache-update');
    stage = `cache update ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-ops-result="cache-update"]')?.dataset.opsTraceId?.length > 0 })`);
    const cacheState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-ops-result="cache-update"]').dataset)`);
    results.push(await capture(client, `platform-ops-r75-${viewport.name}`, { viewport: viewport.name, healthState, backupState, restoreState, cacheState }));

    await clickSidebar(client, '日志管理', 'platform-logs');
    stage = `platform logs panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-logs[data-platform-logs-panel="true"]') })`);
    const platformLogState = await evaluateJson(client, `JSON.stringify(document.querySelector('#platform-logs').dataset)`);
    results.push(await capture(client, `platform-logs-r75-${viewport.name}`, { viewport: viewport.name, platformLogState }));

    await navigate(client, `${baseUrl}/#/systems/${systemId}/admin/log-management`);
    stage = `system logs panel ${viewport.name}`;
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-logs-panel="true"]') })`);
    const systemLogState = await evaluateJson(client, `JSON.stringify(document.querySelector('[data-system-logs-panel="true"]').dataset)`);
    results.push(await capture(client, `system-logs-r75-${viewport.name}`, { viewport: viewport.name, systemLogState }));
  }

  results.forEach((item) => assert(item.overflowX <= 2 && item.blockers.length === 0, 'Browser containment failed', item));
  fs.writeFileSync(path.join(outDir, 'operations-logs-release-error-browser-audit.json'), JSON.stringify({ status: 'PASS', results }, null, 2));
  client.close();
  console.log(JSON.stringify({ status: 'PASS', results }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$env:R75_BASE_URL = $BaseUrl
$env:R75_EVIDENCE_DIR = $script:EvidenceDir
$env:R75_CDP_PORT = [string]$debugPort
$env:R75_SYSTEM_ID = [string]$r66.systemId
$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R75 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
Stop-BrowserAudit

$browserAudit = Read-JsonFile $BrowserAuditFile
$browserResults = @($browserAudit.results)
Add-Check $script:Checks 'browser' 'deployed operations/log pages expose residual-state markers' `
    ($browserAudit.status -eq 'PASS' -and $browserResults.Count -ge 6) `
    "status=$($browserAudit.status), results=$($browserResults.Count)"
Add-Check $script:Checks 'browser' 'deployed browser has no overflow or unfinished copy blockers' `
    (@($browserResults | Where-Object { [int]$_.overflowX -gt 2 -or @($_.blockers).Count -gt 0 }).Count -eq 0) `
    "results=$($browserResults.Count)"
Add-Check $script:Checks 'browser' 'operations browser result includes task and dry-run evidence' `
    (@($browserResults | Where-Object { $_.key -like 'platform-ops-r75-*' -and $_.backupState.opsTaskId -and $_.restoreState.opsDryRun -eq 'true' -and $_.restoreState.opsRollbackSupported -eq 'true' }).Count -ge 2) `
    "platformOpsResults=$(@($browserResults | Where-Object { $_.key -like 'platform-ops-r75-*' }).Count)"

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'R75_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-075'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.6', 'REQ-5.17', 'REQ-5.18', 'REQ-7', 'REQ-8', 'REQ-10', 'REQ-14.1-14.37', 'REQ-A')
    flowIds = @('O1', 'O2', 'O3', 'O4')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    releaseVerifyStatus = [string]$r66.releaseVerifyStatus
    serverScriptCommands = @($r66.serverScriptCommands)
    systemId = [string]$r66.systemId
    platformHealthTraceId = [string]$r66.platformHealthTraceId
    systemHealthTraceId = [string]$r66.systemHealthTraceId
    backupTaskId = [string]$r66.backupTaskId
    restoreTaskId = [string]$r66.restoreTaskId
    archiveTaskId = [string]$r66.archiveTaskId
    rollbackTaskId = [string]$r66.rollbackTaskId
    platformAuditReadbackCount = [int]$r66.platformAuditReadbackCount
    systemAuditReadbackCount = [int]$r66.systemAuditReadbackCount
    deniedPlatformStatus = [int]$r66.deniedPlatformStatus
    deniedSystemStatus = [int]$r66.deniedSystemStatus
    deniedPlatformAuditResult = [string]$r66.deniedPlatformAuditResult
    deniedSystemAuditResult = [string]$r66.deniedSystemAuditResult
    browserResultCount = $browserResults.Count
    browserOverflowCount = @($browserResults | Where-Object { [int]$_.overflowX -gt 2 }).Count
    browserBlockerCount = @($browserResults | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = 'docs/evidence/recovery/screenshots/r75-operations-logs-release-error-state-residual/operations-logs-release-error-browser-audit.json'
    accepted = $true
}
$result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-075 / R75 Operations Logs Release Maintenance Error-State Residual

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.6, REQ-5.17, REQ-5.18, REQ-7, REQ-8, REQ-10, REQ-14.1-14.37, REQ-A
- Flow ids: O1, O2, O3, O4
- Release: verify=$($result.releaseVerifyStatus), commands=$(@($result.serverScriptCommands) -join ',')
- Operations tasks: backup=$($result.backupTaskId), restore=$($result.restoreTaskId), archive=$($result.archiveTaskId), rollback=$($result.rollbackTaskId)
- Logs: platformAudit=$($result.platformAuditReadbackCount), systemAudit=$($result.systemAuditReadbackCount), platformTrace=$($result.platformHealthTraceId), systemTrace=$($result.systemHealthTraceId)
- Permission negatives: platform=$($result.deniedPlatformStatus)/$($result.deniedPlatformAuditResult), system=$($result.deniedSystemStatus)/$($result.deniedSystemAuditResult)
- Browser residual markers: results=$($result.browserResultCount), overflow=$($result.browserOverflowCount), blockers=$($result.browserBlockerCount)
- Browser audit: $($result.browserAuditPath)

This does not close final product acceptance. Full requirement coverage and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 60

if ($NoFailExit) {
    exit 0
}
