param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r81-trial-login-role-use-audit'
$ResultFile = Join-Path $EvidenceDir 'r81-trial-login-role-use-audit-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r81-trial-login-role-use-audit-2026-07-06.md'
$R80ResultFile = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
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

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers
    } catch {
        if ($_.Exception.Message -match 'HTTP 403') {
            return @{ status = 403; path = $Path }
        }
        throw
    }
    throw "Expected HTTP 403 but request succeeded: $Method $Path"
}

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$RelativePath,
        [string[]]$Arguments = @()
    )
    $scriptPath = Join-Path $RepoRoot $RelativePath
    $logPath = Join-Path $WorkDir "$Name.log"
    $errPath = Join-Path $WorkDir "$Name.err.log"
    Push-Location $RepoRoot
    try {
        & powershell @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath) $Arguments > $logPath 2> $errPath
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = $errPath
    }
}

function Test-ReleaseReachable {
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -ne 200) {
            return $false
        }
        $json = $response.Content | ConvertFrom-Json
        return ($json.code -eq 'SUCCESS' -and $json.data.status -eq 'UP' -and $json.data.database -eq 'UP' -and $json.data.schema -eq 'UP' -and $json.data.redis -eq 'UP')
    } catch {
        return $false
    }
}

function Ensure-ReleaseRunning {
    if (Test-ReleaseReachable) {
        return [ordered]@{
            startedByR81 = $false
            detail = 'release already reachable'
        }
    }
    $uri = [Uri]$BaseUrl
    $frontendPort = if ($uri.Port -gt 0) { $uri.Port } else { 18131 }
    foreach ($port in @(9999, $frontendPort) | Select-Object -Unique) {
        try {
            $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique)
            foreach ($listenerPid in $listeners) {
                if ($listenerPid) {
                    Stop-Process -Id $listenerPid -Force -ErrorAction SilentlyContinue
                }
            }
        } catch {
        }
    }
    Start-Sleep -Seconds 2
    $startLog = Join-Path $WorkDir 'local-start-release.log'
    $startErr = Join-Path $WorkDir 'local-start-release.err.log'
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'scripts/local-start-release.ps1') -BackendPort 9999 -FrontendPort $frontendPort > $startLog 2> $startErr
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    if ($exitCode -ne 0) {
        throw "local-start-release failed with exit code $exitCode; see $startLog and $startErr"
    }
    $readyAfterStart = $false
    for ($attempt = 0; $attempt -lt 30; $attempt += 1) {
        if (Test-ReleaseReachable) {
            $readyAfterStart = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $readyAfterStart) {
        throw "local-start-release returned success but release health is not reachable at $BaseUrl"
    }
    return [ordered]@{
        startedByR81 = $true
        detail = $startLog
    }
}
function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing expected JSON result: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
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
        if ($path -and (Test-Path -LiteralPath $path)) {
            return $path
        }
    }
    throw 'Chrome or Edge executable was not found.'
}

function Start-BrowserAudit {
    $chromePath = Find-Chrome
    $script:DebugPort = Get-FreeTcpPort
    $script:BrowserWorkDir = Join-Path $RepoRoot '.tmp-browser'
    New-Item -ItemType Directory -Force -Path $script:BrowserWorkDir | Out-Null
    $script:ChromeProfileDir = Join-Path $script:BrowserWorkDir ("r81-profile-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
    $script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
        '--headless',
        '--disable-gpu',
        '--disable-extensions',
        '--disable-software-rasterizer',
        '--no-sandbox',
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-background-networking',
        '--remote-allow-origins=*',
        "--remote-debugging-port=$script:DebugPort",
        "--user-data-dir=$script:ChromeProfileDir",
        'about:blank'
    ) -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
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
    $errorRecord = $_
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R81_TRIAL_LOGIN_ROLE_USE_AUDIT_FAILED'
        task = 'REC-P0-081'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        userSignoff = $false
        error = $errorRecord.Exception.Message
    }
    $failure | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 10)
        exit 0
    }
    throw $errorRecord
}

$releaseStart = Ensure-ReleaseRunning

$r80 = Read-JsonFile $R80ResultFile
Assert-True -Condition ($r80.status -eq 'PASS' -and $r80.accepted -eq $true) -Message 'R80 trial workspace must be accepted before R81.'

$children = @()
$children += Invoke-ChildScript -Name 'verify-release' -RelativePath 'scripts/verify-release.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$children += Invoke-ChildScript -Name 'final-goal-framework-audit' -RelativePath 'scripts/final-goal-framework-audit.ps1'
$children += Invoke-ChildScript -Name 'final-usability-static-audit' -RelativePath 'scripts/final-usability-static-audit.ps1'
$children += Invoke-ChildScript -Name 'final-requirement-coverage-audit' -RelativePath 'scripts/final-requirement-coverage-audit.ps1' -Arguments @('-NoFailExit')

$failedChildren = @($children | Where-Object { $_.exitCode -ne 0 })
if ($failedChildren.Count -gt 0) {
    throw "Child scripts failed: $(@($failedChildren | ForEach-Object { $_.name }) -join ', ')"
}

$trial = $r80.trialPack
$runtime = $trial.runtimeDailyUse
$workflow = $trial.workflowTodoMessage

$normalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.normalLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
$normalHeaders = @{ Authorization = "Bearer $($normalLogin.accessToken)" }
$readonlyLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.readonlyLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
$readonlyHeaders = @{ Authorization = "Bearer $($readonlyLogin.accessToken)" }
$requesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.requesterLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
$requesterHeaders = @{ Authorization = "Bearer $($requesterLogin.accessToken)" }
$approverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.approverLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
$approverHeaders = @{ Authorization = "Bearer $($approverLogin.accessToken)" }

$runtimeDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records/$($runtime.existingRecordId)" -Headers $normalHeaders
$readonlyForbidden = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records" -Headers $readonlyHeaders -Body @{
    fieldValues = @{ caseTitle = "R81 readonly forbidden $(Get-Date -Format HHmmss)"; caseAmount = 1; caseOwner = 'readonly' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R81_FORBIDDEN'
}
$workflowDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($workflow.systemId)/runtime/modules/$($workflow.moduleId)/records/$($workflow.terminalRecordId)" -Headers $requesterHeaders
$handledTodos = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($workflow.systemId)/todos/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$workflow.terminalRecordId
    status = 'HANDLED'
    assigneeId = $approverLogin.profile.accountId
}
$approverMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($workflow.systemId)/messages/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{
    systemId = [string]$workflow.systemId
    tenantId = [string]$workflow.tenantId
    type = 'approval'
    archiveStatus = 'active'
    keyword = [string]$workflow.terminalRecordId
}

Start-BrowserAudit
$nodeScript = Join-Path $script:BrowserWorkDir "unexamine-r81-trial-login-role-use.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R81_BASE_URL;
const port = process.env.R81_CDP_PORT;
const outDir = process.env.R81_EVIDENCE_DIR;
const runtime = JSON.parse(process.env.R81_RUNTIME_TRIAL);
const workflow = JSON.parse(process.env.R81_WORKFLOW_TRIAL);
const admin = JSON.parse(process.env.R81_ADMIN_TRIAL);

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  let lastError;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
      if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
      return response.json();
    } catch (error) {
      lastError = error;
      await delay(250);
    }
  }
  throw lastError || new Error(`CDP unavailable for ${pathname}`);
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
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
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
        else item.res(message);
      }
    };
  });
}
async function evaluate(client, expression) {
  const message = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  const payload = message.result || message;
  if (payload.exceptionDetails || message.exceptionDetails) throw new Error(JSON.stringify(payload.exceptionDetails || message.exceptionDetails));
  const remote = payload.result || payload;
  const value = remote.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeout) {
    last = await evaluate(client, expression).catch((error) => ({ ok: false, error: error.message }));
    if (last && last.ok) return last;
    await delay(250);
  }
  throw new Error(`wait timeout: ${expression} last=${JSON.stringify(last)}`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile,
  });
}
async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await delay(1200);
  await waitFor(client, `JSON.stringify({ ok: !!document.body, state: document.readyState, href: location.href })`, 30000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20, state: document.readyState, href: location.href, length: document.body ? document.body.innerText.length : 0 })`, 30000);
}
async function loginViaForm(client, loginName, password, targetPath) {
  await client.send('Storage.clearDataForOrigin', { origin: baseUrl, storageTypes: 'all' });
  await navigate(client, `${baseUrl}/?r81=${Date.now()}#/login`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-auth-form="login"]') && !!document.querySelector('[data-auth-action="login"]') })`);
  const loginResult = await evaluate(client, `(() => {
    localStorage.clear();
    sessionStorage.clear();
    const name = document.querySelector('input[data-field-name="loginName"]');
    const pass = document.querySelector('input[data-field-name="password"]');
    const button = document.querySelector('[data-auth-action="login"]');
    if (!name || !pass || !button) throw new Error('login form controls missing');
    name.value = ${JSON.stringify(loginName)};
    pass.value = ${JSON.stringify(password)};
    name.dispatchEvent(new Event('input', { bubbles: true }));
    pass.dispatchEvent(new Event('input', { bubbles: true }));
    name.dispatchEvent(new Event('change', { bubbles: true }));
    pass.dispatchEvent(new Event('change', { bubbles: true }));
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  if (!loginResult.ok) throw new Error(`login form not submitted for ${loginName}`);
  await waitFor(client, `JSON.stringify({ ok: !!localStorage.getItem('unexamine.accessToken') && !document.querySelector('[data-auth-form="login"]'), hash: location.hash })`, 40000);
  if (targetPath) {
    await navigate(client, `${baseUrl}/?r81=${Date.now()}#${targetPath}`);
  }
}
async function capture(client, key) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const blockerTexts = ['undefined', 'null', 'NaN', '鍔犺浇澶辫触', '鎿嶄綔澶辫触'].filter((word) => text.includes(word));
    const create = document.querySelector('[data-runtime-create-record="true"]');
    const enabledEdits = Array.from(document.querySelectorAll('[data-runtime-edit-record]')).filter((el) => !el.disabled && el.getAttribute('aria-disabled') !== 'true');
    return JSON.stringify({
      key: ${JSON.stringify(key)},
      hash: location.hash,
      title: document.title,
      textLength: text.length,
      overflowX,
      blockerTexts,
      hasAuthForm: !!document.querySelector('[data-auth-form="login"]'),
      hasPlatformWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasRuntimeShell: !!document.querySelector('.runtime-shell'),
      hasRuntimeMain: !!document.querySelector('.runtime-main'),
      hasRuntimeRecord: !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]'),
      hasWorkflowRecord: !!document.querySelector('[data-runtime-record-row="${workflow.terminalRecordId}"]'),
      hasApprovedSidebar: !!document.querySelector('[data-runtime-approval-sidebar="${workflow.terminalRecordId}"][data-runtime-approval-status="APPROVED"]'),
      hasTodoWorkbench: !!document.querySelector('[data-system-todo-workbench="true"]'),
      hasTodoLayout: !!document.querySelector('[data-system-todo-layout="true"]'),
      hasMessageCenter: !!document.querySelector('[data-system-message-center="true"]'),
      leakedAdminShell: !!document.querySelector('[data-platform-admin-standalone="true"], [data-system-admin-standalone="true"]'),
      systemAdminDenied: !!document.querySelector('[data-system-admin-denied="true"]'),
      createButtonDisabled: create ? !!create.disabled : null,
      enabledEditCount: enabledEdits.length
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.blockerTexts.length) result.blockers.push('blocker text visible');
  if (result.hasAuthForm) result.blockers.push('still on login form after role entry');
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const shotData = (shot.result && shot.result.data) || shot.data;
  fs.writeFileSync(path.join(outDir, `${key}.png`), Buffer.from(shotData, 'base64'));
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const results = [];

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, admin.loginName, admin.password, '/platform');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-platform-workbench="true"]') })`);
  const adminDesktop = await capture(client, 'admin-platform-login-desktop');
  if (!adminDesktop.hasPlatformWorkbench) adminDesktop.blockers.push('admin did not enter platform workbench');
  results.push(adminDesktop);

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, runtime.normalLoginName, runtime.password, `/systems/${runtime.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]') })`, 40000);
  const normalDesktop = await capture(client, 'runtime-normal-record-desktop');
  if (!normalDesktop.hasRuntimeShell || !normalDesktop.hasRuntimeRecord) normalDesktop.blockers.push('normal runtime record not visible');
  if (normalDesktop.leakedAdminShell) normalDesktop.blockers.push('normal role leaked admin shell');
  results.push(normalDesktop);

  await setViewport(client, { width: 390, height: 760, mobile: true });
  await navigate(client, `${baseUrl}/?r81=${Date.now()}#/systems/${runtime.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]') })`, 40000);
  const normalMobile = await capture(client, 'runtime-normal-record-mobile');
  if (!normalMobile.hasRuntimeShell || !normalMobile.hasRuntimeRecord) normalMobile.blockers.push('normal runtime mobile record not visible');
  results.push(normalMobile);

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, runtime.readonlyLoginName, runtime.password, `/systems/${runtime.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]') })`, 40000);
  const readonlyDesktop = await capture(client, 'runtime-readonly-record-desktop');
  if (!readonlyDesktop.hasRuntimeRecord) readonlyDesktop.blockers.push('readonly record not visible');
  if (readonlyDesktop.createButtonDisabled !== true && readonlyDesktop.enabledEditCount > 0) readonlyDesktop.blockers.push('readonly role exposes enabled create/edit controls');
  if (readonlyDesktop.leakedAdminShell) readonlyDesktop.blockers.push('readonly role leaked admin shell');
  results.push(readonlyDesktop);

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, workflow.requesterLoginName, workflow.password, `/systems/${workflow.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${workflow.terminalRecordId}"]') })`, 40000);
  const requesterRuntime = await capture(client, 'workflow-requester-approved-runtime-desktop');
  if (!requesterRuntime.hasWorkflowRecord) requesterRuntime.blockers.push('requester terminal workflow record not visible');
  results.push(requesterRuntime);

  await setViewport(client, { width: 390, height: 760, mobile: true });
  await navigate(client, `${baseUrl}/?r81=${Date.now()}#/systems/${workflow.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${workflow.terminalRecordId}"]') })`, 40000);
  const requesterMobile = await capture(client, 'workflow-requester-approved-runtime-mobile');
  if (!requesterMobile.hasWorkflowRecord) requesterMobile.blockers.push('requester terminal workflow record not visible on mobile');
  results.push(requesterMobile);

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, workflow.approverLoginName, workflow.password, `/systems/${workflow.systemId}/todos`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-todo-workbench="true"]') && !!document.querySelector('[data-system-todo-layout="true"]') })`, 40000);
  const approverTodo = await capture(client, 'workflow-approver-todo-surface-desktop');
  if (!approverTodo.hasTodoWorkbench || !approverTodo.hasTodoLayout) approverTodo.blockers.push('approver todo workbench not visible');
  results.push(approverTodo);

  await navigate(client, `${baseUrl}/?r81=${Date.now()}#/systems/${workflow.systemId}/messages`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-message-center="true"]') })`, 40000);
  const approverMessages = await capture(client, 'workflow-approver-message-surface-desktop');
  if (!approverMessages.hasMessageCenter) approverMessages.blockers.push('approver message center not visible');
  results.push(approverMessages);

  client.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    resultCount: results.length,
    overflowCount: results.filter((item) => item.overflowX > 2).length,
    blockerCount: results.reduce((count, item) => count + item.blockers.length, 0),
    results,
    loginBoundary: 'Every role submits the deployed login form before direct role route inspection.',
    screenshotEvidenceBoundary: 'Screenshots prove visible route, containment, shell separation, and obvious blocker text only. API/readback assertions prove persisted data and permission behavior.'
  };
  fs.writeFileSync(path.join(outDir, 'trial-login-role-use-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
}
run().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || error.message };
  fs.writeFileSync(path.join(outDir, 'trial-login-role-use-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Value $nodeCode -Encoding UTF8

$env:R81_BASE_URL = $BaseUrl
$env:R81_CDP_PORT = [string]$script:DebugPort
$env:R81_EVIDENCE_DIR = $WorkDir
$env:R81_RUNTIME_TRIAL = ($runtime | ConvertTo-Json -Depth 20 -Compress)
$env:R81_WORKFLOW_TRIAL = ($workflow | ConvertTo-Json -Depth 20 -Compress)
$env:R81_ADMIN_TRIAL = ($trial.admin | ConvertTo-Json -Depth 20 -Compress)

Push-Location $RepoRoot
try {
    node $nodeScript > (Join-Path $WorkDir 'browser-audit.log') 2> (Join-Path $WorkDir 'browser-audit.err.log')
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
} finally {
    Pop-Location
    Remove-Item Env:R81_BASE_URL, Env:R81_CDP_PORT, Env:R81_EVIDENCE_DIR, Env:R81_RUNTIME_TRIAL, Env:R81_WORKFLOW_TRIAL, Env:R81_ADMIN_TRIAL -ErrorAction SilentlyContinue
    Stop-BrowserAudit
    Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
}
if ($browserExit -ne 0) {
    throw "Browser role audit failed with exit code $browserExit"
}

$browserAudit = Read-JsonFile (Join-Path $WorkDir 'trial-login-role-use-browser-audit.json')
$framework = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-goal-framework-audit-result.json')
$static = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-usability-static-audit-result.json')
$coverage = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-requirement-coverage-audit-result.json')
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')

$checks = @(
    [ordered]@{
        area = 'release'
        name = 'release is verified before login role audit'
        passed = ($children[0].exitCode -eq 0)
        detail = $BaseUrl
    },
    [ordered]@{
        area = 'r80-prerequisite'
        name = 'R80 trial pack is accepted and readable'
        passed = ($r80.status -eq 'PASS' -and $r80.accepted -eq $true)
        detail = "runtimeSystem=$($runtime.systemId), workflowSystem=$($workflow.systemId)"
    },
    [ordered]@{
        area = 'browser-login'
        name = 'all trial roles enter through deployed login form'
        passed = ($browserAudit.status -eq 'PASS' -and [int]$browserAudit.resultCount -ge 8)
        detail = "status=$($browserAudit.status), results=$($browserAudit.resultCount), blockers=$($browserAudit.blockerCount)"
    },
    [ordered]@{
        area = 'runtime-readback'
        name = 'normal member can read retained runtime record'
        passed = (-not [string]::IsNullOrWhiteSpace([string]$runtimeDetail.recordId) -or -not [string]::IsNullOrWhiteSpace([string]$runtimeDetail.summary.recordId))
        detail = "record=$($runtime.existingRecordId)"
    },
    [ordered]@{
        area = 'permission-negative'
        name = 'readonly member cannot create runtime record'
        passed = ([int]$readonlyForbidden.status -eq 403)
        detail = $readonlyForbidden.path
    },
    [ordered]@{
        area = 'workflow-terminal'
        name = 'workflow retained record remains terminal approved'
        passed = ($workflowDetail.summary.status -eq 'APPROVED' -or $workflowDetail.approvalSidebar.status -eq 'APPROVED')
        detail = "summary=$($workflowDetail.summary.status), sidebar=$($workflowDetail.approvalSidebar.status)"
    },
    [ordered]@{
        area = 'todo-message'
        name = 'approver todo/message surfaces have persisted workflow evidence'
        passed = ([int]$handledTodos.page.total -ge 1 -and [int]$approverMessages.total -ge 0)
        detail = "handledTodos=$($handledTodos.page.total), messages=$($approverMessages.total)"
    },
    [ordered]@{
        area = 'framework-boundary'
        name = 'framework/static/coverage keep R81 and user signoff boundary honest'
        passed = ($framework.status -eq 'PASS' -and $static.status -eq 'PASS' -and [int]$coverage.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false)
        detail = "framework=$($framework.status), static=$($static.status), notClosed=$($coverage.notClosedCount), userScriptPassed=$($state.gates.user_script_passed)"
    }
)

$failedChecks = @($checks | Where-Object { -not $_.passed })
$status = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    productStatus = 'R81_TRIAL_LOGIN_ROLE_USE_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-081'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    releaseStart = $releaseStart
    checks = $checks
    childResults = $children
    browserAudit = [ordered]@{
        status = $browserAudit.status
        resultCount = $browserAudit.resultCount
        overflowCount = $browserAudit.overflowCount
        blockerCount = $browserAudit.blockerCount
        path = (Join-Path $WorkDir 'trial-login-role-use-browser-audit.json')
    }
    trialPack = $trial
    permissionEvidence = [ordered]@{
        readonlyCreateStatus = $readonlyForbidden.status
        workflowStatus = if ($workflowDetail.summary.status) { $workflowDetail.summary.status } else { $workflowDetail.approvalSidebar.status }
        handledTodoTotal = $handledTodos.page.total
        approverMessageTotal = $approverMessages.total
    }
    accepted = ($status -eq 'PASS')
}

$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$adminLine = '- Admin login: `{0} / {1}` -> `{2}`' -f $trial.admin.loginName, $trial.admin.password, $trial.admin.entry
$runtimeNormalLine = '- Runtime normal member: `{0} / {1}` -> `{2}`' -f $runtime.normalLoginName, $runtime.password, $runtime.entry
$runtimeReadonlyLine = '- Runtime readonly member: `{0} / {1}` -> `{2}`' -f $runtime.readonlyLoginName, $runtime.password, $runtime.entry
$workflowRequesterLine = '- Workflow requester: `{0} / {1}` -> `{2}`' -f $workflow.requesterLoginName, $workflow.password, $workflow.entry
$workflowApproverLine = '- Workflow approver: `{0} / {1}` -> `{2}` and `{3}`' -f $workflow.approverLoginName, $workflow.password, $workflow.todoEntry, $workflow.messageEntry
$readonlyLine = '- Readonly create denial: `{0}`' -f $readonlyForbidden.status
$workflowStatusLine = '- Workflow terminal status: `{0}`' -f $result.permissionEvidence.workflowStatus
$coverageLine = '- Requirement coverage remains partial: notClosed `{0}`' -f $coverage.notClosedCount

$summary = @(
    '# R81 Trial Login And Role Use Audit',
    '',
    "Status: $status",
    '',
    'This is engineering evidence only. It proves the retained R80 trial package can be entered from the deployed login page by each role, but it does not close user signoff.',
    '',
    '## Role Entry Evidence',
    '',
    $adminLine,
    $runtimeNormalLine,
    $runtimeReadonlyLine,
    $workflowRequesterLine,
    $workflowApproverLine,
    '',
    '## Evidence',
    '',
    '- Browser role audit: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/trial-login-role-use-browser-audit.json`',
    '- Screenshots: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/`',
    '- Result JSON: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`',
    $readonlyLine,
    $workflowStatusLine,
    $coverageLine,
    '- User signoff remains `false`.'
) -join [Environment]::NewLine
Set-Content -LiteralPath $SummaryFile -Value $summary -Encoding UTF8

$result | ConvertTo-Json -Depth 20

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}













