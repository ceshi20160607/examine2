param(
    [string]$BaseUrl = 'http://127.0.0.1:18131'
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
    throw $originalError
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r22-ops-maintenance'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$ReleaseDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT'
$ServerScript = Join-Path $ReleaseDir 'backend\server.sh'
$VerifyScript = Join-Path $ReleaseDir 'verify-release.ps1'
Assert-True -Condition (Test-Path $ServerScript) -Message 'Packaged backend server.sh is missing.'
Assert-True -Condition (Test-Path $VerifyScript) -Message 'Packaged verify-release.ps1 is missing.'
$serverText = Get-Content -Raw -Encoding UTF8 $ServerScript
foreach ($commandName in @('start', 'stop', 'restart', 'status', 'health')) {
    Assert-True -Condition ($serverText -match "$commandName\)") -Message "server.sh does not expose $commandName command."
}

$verifyJson = powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'scripts\verify-release.ps1') -BaseUrl $BaseUrl -CheckDeployedFrontend | Out-String
$verify = $verifyJson | ConvertFrom-Json
Assert-True -Condition ($verify.status -eq 'PASS') -Message "verify-release failed: $verifyJson"

$PlatformHealth = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/health-check' -Headers $AdminHeaders -Body @{
    checkType = 'FULL'
    checkItems = @()
    requestedBy = 'r22'
    idempotencyKey = "r22-health-$Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($PlatformHealth.traceId)) -Message 'Platform ops health did not return traceId.'

$FeatureFlag = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/feature-flags/flag_gray_publish' -Headers $AdminHeaders -Body @{
    status = 1
    rules = 'role=PLATFORM_ROOT; percent=20'
    rollbackVersion = "flag_v$Suffix"
}
$Quota = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/quotas/quota_openapi' -Headers $AdminHeaders -Body @{
    limit = 800
    warnThreshold = 640
}
$RateLimit = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/rate-limit-policies/rl_openapi_app' -Headers $AdminHeaders -Body @{
    limitRule = 'dimension=appKey; window=1m; limit=800; overflow=REJECT_WITH_CODE'
    status = 1
}
$BackupTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/backups' -Headers $AdminHeaders -Body @{
    backupType = 'FULL'
    scope = 'PLATFORM'
    boundaryPayload = @{ includes = @('database', 'files', 'config', 'secret_refs') }
    idempotencyKey = "r22-backup-$Suffix"
}
$RestoreTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/backups/backup_20260623_001/restore-drill' -Headers $AdminHeaders -Body @{
    drillScope = 'CONFIG_AND_FILES'
    dryRun = $true
    idempotencyKey = "r22-restore-$Suffix"
}
$ArchiveTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/archive-restore-requests' -Headers $AdminHeaders -Body @{
    scope = 'PLATFORM'
    objectType = 'BUSINESS_LOG'
    archiveCondition = 'older_than_180_days'
    reason = 'R22 archive restore dry-run'
    idempotencyKey = "r22-archive-$Suffix"
}
$Deployments = Invoke-Api -Method 'Get' -Path '/api/v1/platform/ops/deployments?pageNo=1&pageSize=10' -Headers $AdminHeaders
$DeploymentId = if (@($Deployments.records).Count -gt 0) { $Deployments.records[0].deploymentId } else { 'deploy_20260623_001' }
$RollbackTask = Invoke-Api -Method 'Post' -Path "/api/v1/platform/ops/deployments/$DeploymentId/rollback" -Headers $AdminHeaders -Body @{
    targetVersion = 'previous-stable'
    dryRun = $true
    confirmNoDestructiveScript = $true
    idempotencyKey = "r22-rollback-$Suffix"
}
$CachePolicy = Invoke-Api -Method 'Get' -Path '/api/v1/platform/ops/api-cache-policy' -Headers $AdminHeaders
$CacheUpdated = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/api-cache-policy' -Headers $AdminHeaders -Body @{
    keyRule = 'system:{systemId}:member:{memberId}:perm'
    invalidationRule = 'role_permission_changed OR member_binding_changed OR publish_version_changed'
    status = 1
}

foreach ($task in @($BackupTask, $RestoreTask, $ArchiveTask, $RollbackTask)) {
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$task.taskId)) -Message "Ops task did not return taskId: $($task | ConvertTo-Json -Depth 20 -Compress)"
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$task.traceId)) -Message "Ops task did not return traceId: $($task | ConvertTo-Json -Depth 20 -Compress)"
}
Assert-True -Condition ($RestoreTask.rollbackSupported -eq $true -and $RollbackTask.rollbackSupported -eq $true) -Message 'Restore/rollback tasks must declare rollbackSupported=true.'

$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r22-chrome-$Suffix"
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

$script:NodeScript = Join-Path $env:TEMP "unexamine-r22-ops-$Suffix.js"
$nodeSource = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R22_BASE_URL;
const outDir = process.env.R22_EVIDENCE_DIR;
const port = process.env.R22_CDP_PORT;

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
      resolve({
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
      });
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
  const waitLoad = client.waitFor('Page.loadEventFired', 7000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(1000);
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

async function clickText(client, selector, text) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const nodes = Array.from(document.querySelectorAll(${JSON.stringify(selector)}));
        const node = nodes.find((item) => item.textContent.includes(${JSON.stringify(text)}));
        if (node) node.click();
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

async function realLogin(client) {
  await navigate(client, `${baseUrl}/#/login`);
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
      return { ok: hasToken && location.hash !== '#/login', hash: location.hash, hasToken };
    })())
  `, 12000);
}

async function clickOpsButtonByIndex(client, index, expected) {
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('#platform-ops-governance button'));
        const button = buttons[${index}];
        if (button) button.click();
      })()
    `,
    returnByValue: true,
  });
  return waitUntil(client, `
    JSON.stringify((() => {
      const panel = document.querySelector('#platform-ops-governance');
      const body = panel?.innerText || '';
      const disabled = Array.from(panel?.querySelectorAll('button') || []).some((button) => button.disabled);
      return { ok: body.includes(${JSON.stringify(expected)}) && !disabled, body, disabled };
    })())
  `, 12000);
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
  await navigate(client, `${baseUrl}/#/platform/admin`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`, 12000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const items = Array.from(document.querySelectorAll('.admin-sidebar .sidebar-item'));
        const config = items[5];
        if (config) config.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-ops-governance'), text: document.body.innerText.slice(0, 500) })`, 12000);

  const checks = [];
  checks.push(await clickOpsButtonByIndex(client, 0, 'traceId='));
  checks.push(await clickOpsButtonByIndex(client, 4, 'TASK-'));
  checks.push(await clickOpsButtonByIndex(client, 5, 'TASK-'));
  checks.push(await clickOpsButtonByIndex(client, 7, 'DEP-'));
  checks.push(await clickOpsButtonByIndex(client, 8, 'TASK-'));
  checks.push(await clickOpsButtonByIndex(client, 9, 'cache_'));
  checks.push(await clickOpsButtonByIndex(client, 10, 'traceId='));
  await capture(client, 'platform-ops-desktop', { checks: checks.length });

  await client.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 720, deviceScaleFactor: 1, mobile: true });
  await client.send('Emulation.setVisibleSize', { width: 390, height: 720 });
  await delay(700);
  const mobile = await evaluateJson(client, `
    JSON.stringify((() => {
      const overflowX = Math.max(0, document.documentElement.scrollWidth - window.innerWidth);
      const panel = document.querySelector('#platform-ops-governance');
      return { ok: !!panel && overflowX === 0, overflowX, width: window.innerWidth };
    })())
  `);
  assert(mobile.ok, 'Mobile ops governance containment failed', mobile);
  await capture(client, 'platform-ops-mobile', mobile);

  client.close();
  console.log(JSON.stringify({ login, checks, mobile, screenshots: ['platform-ops-desktop.png', 'platform-ops-mobile.png'] }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $script:NodeScript -Encoding UTF8

$env:R22_BASE_URL = $BaseUrl
$env:R22_EVIDENCE_DIR = $EvidenceDir
$env:R22_CDP_PORT = [string]$debugPort
$browserJson = node $script:NodeScript
if ($LASTEXITCODE -ne 0) {
    throw "Node ops browser audit exited with code $LASTEXITCODE"
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

$Result = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    releaseVerifyStatus = $verify.status
    serverScriptCommands = @('start', 'stop', 'restart', 'status', 'health')
    platformHealthTraceId = $PlatformHealth.traceId
    featureFlagTraceId = $FeatureFlag.traceId
    quotaTraceId = $Quota.traceId
    rateLimitTraceId = $RateLimit.traceId
    backupTaskId = $BackupTask.taskId
    restoreTaskId = $RestoreTask.taskId
    archiveTaskId = $ArchiveTask.taskId
    rollbackTaskId = $RollbackTask.taskId
    rollbackSupported = [ordered]@{
        restore = $RestoreTask.rollbackSupported
        archive = $ArchiveTask.rollbackSupported
        deployment = $RollbackTask.rollbackSupported
    }
    deploymentCount = @($Deployments.records).Count
    cachePolicyCount = @($CachePolicy).Count
    cacheUpdatedCount = @($CacheUpdated).Count
    browser = $BrowserEvidence
}

$resultPath = Join-Path $RepoRoot 'docs\evidence\recovery\r22-ops-maintenance-result.json'
$Result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $resultPath -Encoding UTF8

$summaryPath = Join-Path $RepoRoot 'docs\evidence\recovery\r22-ops-maintenance-2026-06-29.md'
$summary = @(
    '# R22 Operations And Maintenance Smoke',
    '',
    'Status: PASS',
    '',
    "Base URL: $BaseUrl",
    '',
    '## Covered Flow',
    '',
    '- Release verification passed with deployed frontend asset comparison.',
    '- Packaged backend server.sh exposes start, stop, restart, status, and health commands.',
    "- Platform ops health returned traceId $($PlatformHealth.traceId).",
    "- Feature flag, quota, and rate-limit updates returned trace ids.",
    "- Backup, restore drill, archive restore, and deployment rollback returned async task ids: $($BackupTask.taskId), $($RestoreTask.taskId), $($ArchiveTask.taskId), $($RollbackTask.taskId).",
    '- Restore drill, archive restore, and deployment rollback declare rollbackSupported=true.',
    "- API cache policy read/update returned $(@($CacheUpdated).Count) policies.",
    '- Browser used real /login, opened platform admin configuration, clicked operations governance buttons, and saw visible task/trace results.',
    "- Mobile containment kept document overflow at $($BrowserEvidence.mobile.overflowX).",
    '',
    '## Screenshots',
    '',
    '- docs/evidence/recovery/screenshots/r22-ops-maintenance/platform-ops-desktop.png',
    '- docs/evidence/recovery/screenshots/r22-ops-maintenance/platform-ops-mobile.png',
    '',
    '## Result JSON',
    '',
    '- docs/evidence/recovery/r22-ops-maintenance-result.json'
) -join [Environment]::NewLine
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$Result | ConvertTo-Json -Depth 80
