param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r82-visible-copy-encoding-trial-usability'
$ResultFile = Join-Path $EvidenceDir 'r82-visible-copy-encoding-trial-usability-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r82-visible-copy-encoding-trial-usability-2026-07-07.md'
$R81ResultFile = Join-Path $EvidenceDir 'r81-trial-login-role-use-audit-result.json'

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing JSON file: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
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
    return [pscustomobject][ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = $errPath
    }
}

function Invoke-Npm {
    param([string]$Name, [string[]]$Arguments)
    $logPath = Join-Path $WorkDir "$Name.log"
    $errPath = Join-Path $WorkDir "$Name.err.log"
    Push-Location $RepoRoot
    try {
        & 'D:\dev\nodejs24\npm.cmd' @Arguments > $logPath 2> $errPath
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    return [pscustomobject][ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = $errPath
    }
}

function Stop-ReleasePorts {
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
}


function Invoke-LocalStartRelease {
    $logPath = Join-Path $WorkDir 'local-start-release.log'
    $errPath = Join-Path $WorkDir 'local-start-release.err.log'
    $frontendPort = ([Uri]$BaseUrl).Port
    if ($frontendPort -le 0) { $frontendPort = 18131 }
    Set-Content -LiteralPath $logPath -Encoding UTF8 -Value "Starting local release at $BaseUrl"
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'scripts/local-start-release.ps1') -BackendPort 9999 -FrontendPort $frontendPort 2>&1 | ForEach-Object { Add-Content -LiteralPath $logPath -Encoding UTF8 -Value ([string]$_) }
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } catch {
        $exitCode = 1
        Set-Content -LiteralPath $errPath -Encoding UTF8 -Value $_.Exception.Message
    } finally {
        Pop-Location
    }
    $startResult = Join-Path $RepoRoot 'release/unexamine-0.0.1-SNAPSHOT/backend/start-result.json'
    if (Test-Path -LiteralPath $startResult) {
        Add-Content -LiteralPath $logPath -Encoding UTF8 -Value ''
        Add-Content -LiteralPath $logPath -Encoding UTF8 -Value (Get-Content -Raw -Encoding UTF8 -LiteralPath $startResult)
    }
    return [pscustomobject][ordered]@{
        name = 'local-start-release'
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = $errPath
    }
}
function Test-SourceVisibleCopy {
    $files = @(
        'frontend/src/features/auth/authPages.ts',
        'frontend/src/features/system-shell/systemShell.ts',
        'frontend/src/features/runtime/records/runtimeRecords.ts'
    )
    $mojibakeMarkers = @('�', '锟', '鈥', '銆', '缁', '璐', '鐧', '鍔犺浇', '鎿嶄綔', '妯', '鏉')
    $requiredReadable = @{
        'frontend/src/features/auth/authPages.ts' = @('统一入口', '账号登录', '注册账号', '找回密码')
        'frontend/src/features/system-shell/systemShell.ts' = @('消息', '待办', '记录')
        'frontend/src/features/runtime/records/runtimeRecords.ts' = @('记录')
    }
    $results = @()
    foreach ($relative in $files) {
        $path = Join-Path $RepoRoot $relative
        $text = [System.IO.File]::ReadAllText($path, [System.Text.UTF8Encoding]::new($false, $true))
        $markers = @($mojibakeMarkers | Where-Object { $text.Contains($_) })
        $readable = @($requiredReadable[$relative] | Where-Object { $text.Contains($_) })
        $missing = @($requiredReadable[$relative] | Where-Object { -not $text.Contains($_) })
        $results += [ordered]@{
            file = $relative
            length = $text.Length
            mojibakeMarkers = $markers
            requiredReadableFound = $readable
            requiredReadableMissing = $missing
            passed = ($markers.Count -eq 0 -and $missing.Count -eq 0)
        }
    }
    return [pscustomobject][ordered]@{
        status = if (@($results | Where-Object { -not $_.passed }).Count -eq 0) { 'PASS' } else { 'FAIL' }
        files = $results
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
    $script:ChromeProfileDir = Join-Path $script:BrowserWorkDir ("r82-profile-" + [guid]::NewGuid().ToString('N'))
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
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R82_VISIBLE_COPY_ENCODING_TRIAL_USABILITY_FAILED'
        task = 'REC-P0-082'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        userSignoff = $false
        error = $_.Exception.Message
    }
    $failure | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 10)
        exit 0
    }
    throw $_
}

$r81 = Read-JsonFile $R81ResultFile
if ($r81.status -ne 'PASS' -or $r81.accepted -ne $true) {
    throw 'R81 must be accepted before R82 visible-copy cleanup.'
}

$sourceScan = Test-SourceVisibleCopy

$children = [System.Collections.Generic.List[object]]::new()
[void]$children.Add((Invoke-Npm -Name 'frontend-typecheck' -Arguments @('--prefix', 'frontend', 'run', 'typecheck')))
Stop-ReleasePorts
[void]$children.Add((Invoke-ChildScript -Name 'package-release' -RelativePath 'scripts/package-release.ps1'))
[void]$children.Add((Invoke-LocalStartRelease))
[void]$children.Add((Invoke-ChildScript -Name 'verify-release' -RelativePath 'scripts/verify-release.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')))
[void]$children.Add((Invoke-ChildScript -Name 'final-goal-framework-audit' -RelativePath 'scripts/final-goal-framework-audit.ps1'))
[void]$children.Add((Invoke-ChildScript -Name 'final-usability-static-audit' -RelativePath 'scripts/final-usability-static-audit.ps1'))
[void]$children.Add((Invoke-ChildScript -Name 'final-requirement-coverage-audit' -RelativePath 'scripts/final-requirement-coverage-audit.ps1' -Arguments @('-NoFailExit')))

$failedChildren = @($children | Where-Object { $_.exitCode -ne 0 })
if ($failedChildren.Count -gt 0) {
    throw "Child scripts failed: $(@($failedChildren | ForEach-Object { $_.name }) -join ', ')"
}

$trial = $r81.trialPack
$runtime = $trial.runtimeDailyUse
$workflow = $trial.workflowTodoMessage

Start-BrowserAudit
$nodeScript = Join-Path $script:BrowserWorkDir 'unexamine-r82-visible-copy-browser-audit.js'
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R82_BASE_URL;
const port = process.env.R82_CDP_PORT;
const outDir = process.env.R82_EVIDENCE_DIR;
const trial = JSON.parse(process.env.R82_TRIAL_PACK);
const runtime = trial.runtimeDailyUse;
const workflow = trial.workflowTodoMessage;
const admin = trial.admin;

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
  await waitFor(client, `JSON.stringify({ ok: !!document.body, state: document.readyState, length: document.body ? document.body.innerText.length : 0 })`, 30000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 10, length: document.body ? document.body.innerText.length : 0 })`, 30000);
}
async function loginViaForm(client, loginName, password, targetPath) {
  await client.send('Storage.clearDataForOrigin', { origin: baseUrl, storageTypes: 'all' });
  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/login`);
  await evaluate(client, `(() => { localStorage.clear(); sessionStorage.clear(); return JSON.stringify({ ok: true }); })()`);
  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/login`);
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
  await delay(1500);
  if (targetPath) {
    await navigate(client, `${baseUrl}/?r82=${Date.now()}#${targetPath}`);
  }
}
async function capture(client, key, expectedTexts = []) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const markerWords = ['�', '锟', '鈥', '銆', '缁', '璐', '鐧', '鍔犺浇', '鎿嶄綔', '妯', '鏉'];
    const blockerTexts = ['undefined', 'null', 'NaN'].filter((word) => text.includes(word));
    const mojibakeMarkers = markerWords.filter((word) => text.includes(word));
    return JSON.stringify({
      key: ${JSON.stringify(key)},
      hash: location.hash,
      textLength: text.length,
      textSample: text.replace(/\\s+/g, ' ').slice(0, 900),
      overflowX: Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth)),
      blockerTexts,
      mojibakeMarkers,
      hasAuthForm: !!document.querySelector('[data-auth-form="login"]'),
      hasPlatformWorkbench: !!document.querySelector('[data-platform-workbench="true"]'),
      hasRuntimeShell: !!document.querySelector('.runtime-shell'),
      hasRuntimeRecord: !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]'),
      hasWorkflowRecord: !!document.querySelector('[data-runtime-record-row="${workflow.terminalRecordId}"]'),
      hasTodoWorkbench: !!document.querySelector('[data-system-todo-workbench="true"]'),
      hasMessageCenter: !!document.querySelector('[data-system-message-center="true"]')
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.expectedTexts = expectedTexts;
  result.missingExpectedTexts = expectedTexts.filter((word) => !result.textSample.includes(word));
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.blockerTexts.length) result.blockers.push('raw blocker text visible');
  if (result.mojibakeMarkers.length) result.blockers.push('mojibake marker visible');
  if (result.missingExpectedTexts.length) result.blockers.push('expected readable copy missing');
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
  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/login`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-auth-form="login"]') })`);
  results.push(await capture(client, 'auth-login-copy-desktop', ['统一入口', '账号登录', '登录', '注册', '找回密码']));

  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/register-with-system`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-auth-form="registerWithSystem"]') })`);
  results.push(await capture(client, 'auth-register-copy-desktop', ['注册账号并创建系统', '初始化向导', '组织架构', '角色权限']));

  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/forgot-password`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-auth-form="forgotPassword"]') })`);
  results.push(await capture(client, 'auth-forgot-password-copy-desktop', ['找回密码', '发送验证码', '确认重置', '返回登录']));

  await loginViaForm(client, admin.loginName, admin.password, '/platform');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-platform-workbench="true"]') })`);
  results.push(await capture(client, 'admin-platform-copy-desktop', ['平台', '系统']));

  await loginViaForm(client, runtime.normalLoginName, runtime.password, `/systems/${runtime.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]') })`, 40000);
  results.push(await capture(client, 'runtime-normal-copy-desktop', ['记录']));

  await setViewport(client, { width: 390, height: 760, mobile: true });
  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/systems/${runtime.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.runtime-shell') && !!document.querySelector('[data-runtime-record-row="${runtime.existingRecordId}"]') })`, 40000);
  results.push(await capture(client, 'runtime-normal-copy-mobile', ['记录']));

  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await loginViaForm(client, workflow.approverLoginName, workflow.password, `/systems/${workflow.systemId}/todos`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-todo-workbench="true"]') && !!document.querySelector('[data-system-todo-layout="true"]') })`, 40000);
  results.push(await capture(client, 'workflow-approver-todo-copy-desktop', ['待办']));

  await navigate(client, `${baseUrl}/?r82=${Date.now()}#/systems/${workflow.systemId}/messages`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-message-center="true"]') })`, 40000);
  results.push(await capture(client, 'workflow-approver-message-copy-desktop', ['消息']));

  client.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    resultCount: results.length,
    overflowCount: results.filter((item) => item.overflowX > 2).length,
    mojibakeCount: results.reduce((count, item) => count + item.mojibakeMarkers.length, 0),
    blockerCount: results.reduce((count, item) => count + item.blockers.length, 0),
    results,
    screenshotEvidenceBoundary: 'Screenshots and browser text samples prove visible copy readability, overflow absence, and rendered role state only. Functional completion still depends on API/readback, permission assertions, coverage ledger, and user signoff.'
  };
  fs.writeFileSync(path.join(outDir, 'visible-copy-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
}
run().catch((error) => {
  const output = { status: 'FAIL', error: error.stack || error.message };
  fs.writeFileSync(path.join(outDir, 'visible-copy-browser-audit.json'), JSON.stringify(output, null, 2));
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Value $nodeCode -Encoding UTF8

$env:R82_BASE_URL = $BaseUrl
$env:R82_CDP_PORT = [string]$script:DebugPort
$env:R82_EVIDENCE_DIR = $WorkDir
$env:R82_TRIAL_PACK = ($trial | ConvertTo-Json -Depth 20 -Compress)

Push-Location $RepoRoot
try {
    node $nodeScript > (Join-Path $WorkDir 'browser-audit.log') 2> (Join-Path $WorkDir 'browser-audit.err.log')
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
} finally {
    Pop-Location
    Remove-Item Env:R82_BASE_URL, Env:R82_CDP_PORT, Env:R82_EVIDENCE_DIR, Env:R82_TRIAL_PACK -ErrorAction SilentlyContinue
    Stop-BrowserAudit
    Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
}
if ($browserExit -ne 0) {
    throw "Browser visible-copy audit failed with exit code $browserExit"
}

$browserAudit = Read-JsonFile (Join-Path $WorkDir 'visible-copy-browser-audit.json')
$framework = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-goal-framework-audit-result.json')
$static = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-usability-static-audit-result.json')
$coverage = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-requirement-coverage-audit-result.json')
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')

$checks = @(
    [ordered]@{
        area = 'source-encoding'
        name = 'selected product source files are UTF-8 readable and free of mojibake markers'
        passed = ($sourceScan.status -eq 'PASS')
        detail = "files=$(@($sourceScan.files).Count)"
    },
    [ordered]@{
        area = 'release'
        name = 'current source was rebuilt, packaged, deployed, and verified'
        passed = (@($children | Where-Object { $_.exitCode -ne 0 }).Count -eq 0)
        detail = "children=$(@($children).Count)"
    },
    [ordered]@{
        area = 'browser-visible-copy'
        name = 'login/register/recovery/trial role surfaces show readable copy with no mojibake markers'
        passed = ($browserAudit.status -eq 'PASS' -and [int]$browserAudit.resultCount -ge 8 -and [int]$browserAudit.mojibakeCount -eq 0)
        detail = "status=$($browserAudit.status), results=$($browserAudit.resultCount), mojibake=$($browserAudit.mojibakeCount), blockers=$($browserAudit.blockerCount)"
    },
    [ordered]@{
        area = 'framework-boundary'
        name = 'R82 remains visible-copy engineering evidence only, with user signoff still separate'
        passed = ($framework.status -eq 'PASS' -and $static.status -eq 'PASS' -and [int]$coverage.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false)
        detail = "framework=$($framework.status), static=$($static.status), notClosed=$($coverage.notClosedCount), userScriptPassed=$($state.gates.user_script_passed)"
    }
)

$failedChecks = @($checks | Where-Object { -not $_.passed })
$status = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = 'R82_VISIBLE_COPY_ENCODING_TRIAL_USABILITY_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-082'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = $checks
    childResults = $children
    sourceScan = $sourceScan
    browserAudit = [ordered]@{
        status = $browserAudit.status
        resultCount = $browserAudit.resultCount
        overflowCount = $browserAudit.overflowCount
        mojibakeCount = $browserAudit.mojibakeCount
        blockerCount = $browserAudit.blockerCount
        path = (Join-Path $WorkDir 'visible-copy-browser-audit.json')
    }
    trialPack = $trial
    accepted = ($status -eq 'PASS')
}

$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @(
    '# R82 Visible Copy Encoding And Trial Usability Cleanup',
    '',
    "Status: $status",
    '',
    'This is visible-copy engineering evidence only. It proves selected deployed trial surfaces are readable and free of obvious mojibake markers, while final requirement rows and user signoff remain open.',
    '',
    '## Evidence',
    '',
    '- Result JSON: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-result.json`',
    '- Browser text audit: `docs/evidence/recovery/screenshots/r82-visible-copy-encoding-trial-usability/visible-copy-browser-audit.json`',
    '- Screenshots: `docs/evidence/recovery/screenshots/r82-visible-copy-encoding-trial-usability/`',
    ('- Browser result count: `{0}`' -f $browserAudit.resultCount),
    ('- Browser mojibake count: `{0}`' -f $browserAudit.mojibakeCount),
    ('- Browser blockers: `{0}`' -f $browserAudit.blockerCount),
    ('- Requirement coverage remains partial: notClosed `{0}`' -f $coverage.notClosedCount),
    '- User signoff remains `false`.',
    '',
    '## Boundary',
    '',
    'Screenshots and text samples prove visual/readability only. They do not prove final functional completion without requirement coverage closure, API/readback assertions, permission positives/negatives, and user verification.'
) -join [Environment]::NewLine
Set-Content -LiteralPath $SummaryFile -Value $summary -Encoding UTF8

$result | ConvertTo-Json -Depth 20

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}





