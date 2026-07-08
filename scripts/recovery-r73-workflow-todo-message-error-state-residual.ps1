param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

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

function Invoke-ExpectedHttpStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int]$ExpectedStatus,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers
    } catch {
        if ($_.Exception.Message -match "HTTP $ExpectedStatus") {
            return @{ status = $ExpectedStatus; path = $Path; error = $_.Exception.Message }
        }
        throw
    }
    throw "Expected HTTP $ExpectedStatus but request succeeded: $Method $Path"
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
    $debugPort = Get-FreeTcpPort
    $script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r73-chrome-$script:Suffix"
    New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
    $script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
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
    return $debugPort
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    $script:ChromeProcess = $null
    $script:ChromeProfileDir = $null
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:CreatedSystemIds)) {
        if ([string]::IsNullOrWhiteSpace([string]$systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r73 workflow todo message cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r73-$script:Suffix-$systemId"
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
    Stop-BrowserAudit
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R73_WORKFLOW_TODO_MESSAGE_ERROR_STATE_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $originalError.Exception.Message
        cleanup = $script:CleanupResult
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw $originalError
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')

$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r73-workflow-todo-message-error-state-residual'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r73-workflow-todo-message-error-state-residual-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r73-workflow-todo-message-error-state-residual-2026-07-02.md'
$BrowserAuditFile = Join-Path $EvidenceDir 'workflow-todo-message-browser-audit.json'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$SetupScript = Join-Path $PSScriptRoot 'recovery-r3-runtime-approval-smoke.ps1'
$SetupJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $SetupScript -BaseUrl $BaseUrl -KeepCreatedData -StopBeforeSubmit | Out-String
$Setup = $SetupJson | ConvertFrom-Json
Assert-True -Condition ($Setup.status -eq 'PASS') -Message "R73 setup did not pass: $SetupJson"
foreach ($systemId in @($Setup.targetSystemId, $Setup.normalOwnedSystemId, $Setup.approverOwnedSystemId)) {
    if (-not [string]::IsNullOrWhiteSpace([string]$systemId)) {
        $script:CreatedSystemIds.Add([string]$systemId) | Out-Null
    }
}

$RequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $Setup.requesterAccount
    password = $Setup.password
    loginTarget = 'PLATFORM'
}
$RequesterHeaders = @{ Authorization = "Bearer $($RequesterLogin.accessToken)" }
$ApproverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $Setup.approverAccount
    password = $Setup.password
    loginTarget = 'PLATFORM'
}
$ApproverHeaders = @{ Authorization = "Bearer $($ApproverLogin.accessToken)" }

foreach ($item in @(
    @{ headers = $script:AdminHeaders; reason = 'recovery-r73 admin switch' },
    @{ headers = $RequesterHeaders; reason = 'recovery-r73 requester switch' },
    @{ headers = $ApproverHeaders; reason = 'recovery-r73 approver switch' }
)) {
    $null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $item.headers -Body @{
        systemId = $Setup.targetSystemId
        tenantId = $Setup.targetTenantId
        reason = $item.reason
    }
}

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/flows/$($Setup.flowId)/publish-check" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r73 flow publish check before browser first-use'
    idempotencyKey = "flow-publish-check-r73-$($Setup.suffix)"
}
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message 'R73 flow publish-check did not pass.'
$Simulation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/flows/$($Setup.flowId)/simulate" -Headers $script:AdminHeaders -Body @{
    moduleId = [string]$Setup.moduleId
    recordId = [string]$Setup.recordId
    versionNo = 'CURRENT'
    actorMemberId = [string]$Setup.requesterMemberId
    fieldValues = @{
        contractAmount = 3600
        contractName = [string]$Setup.recordTitle
    }
    idempotencyKey = "flow-sim-r73-$($Setup.suffix)"
}
Assert-True -Condition ($Simulation.passed -eq $true -and $Simulation.runtimeInstanceCreated -eq $false) `
    -Message "R73 flow simulation failed or created runtime instance: $($Simulation | ConvertTo-Json -Depth 40 -Compress)"

$debugPort = Start-BrowserAudit
$NodeScript = Join-Path $env:TEMP "unexamine-r73-workflow-todo-message-$($Setup.suffix).js"
$NodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R73_BASE_URL;
const port = process.env.R73_CDP_PORT;
const outDir = process.env.R73_EVIDENCE_DIR;
const phase = process.env.R73_PHASE;
const systemId = process.env.R73_SYSTEM_ID;
const recordId = process.env.R73_RECORD_ID;
const todoId = process.env.R73_TODO_ID || '';
const messageId = process.env.R73_MESSAGE_ID || '';
const roles = {
  requester: { accountId: process.env.R73_REQUESTER_ACCOUNT_ID, accessToken: process.env.R73_REQUESTER_TOKEN, refreshToken: process.env.R73_REQUESTER_REFRESH || '' },
  approver: { accountId: process.env.R73_APPROVER_ACCOUNT_ID, accessToken: process.env.R73_APPROVER_TOKEN, refreshToken: process.env.R73_APPROVER_REFRESH || '' },
  admin: { accountId: process.env.R73_ADMIN_ACCOUNT_ID, accessToken: process.env.R73_ADMIN_TOKEN, refreshToken: process.env.R73_ADMIN_REFRESH || '' },
};

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
  return response.json();
}
async function newTarget() {
  try { return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' }); }
  catch { return (await cdpJson('/json/list'))[0]; }
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
        else item.res(message.result);
      }
    };
  });
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeout) {
    last = await evaluate(client, expression);
    if (last && last.ok) return last;
    await delay(150);
  }
  throw new Error(`Timeout waiting for ${expression}. Last=${JSON.stringify(last)}`);
}
async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await waitFor(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 30000);
}
async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/#/`);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});
    return JSON.stringify({ ok: true });
  })()`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
  await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
}
async function screenshot(client, key) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const blockerTexts = ['undefined', 'null', 'NaN', '鍔犺浇澶辫触', '鎿嶄綔澶辫触'];
    return JSON.stringify({
      key: ${JSON.stringify(key)},
      phase: ${JSON.stringify(phase)},
      url: location.href,
      overflowX,
      blockerTexts: blockerTexts.filter((item) => text.includes(item)),
      textSample: text.slice(0, 1600)
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.blockerTexts.length) result.blockers.push('blocker text visible');
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${phase}-${key}.png`), Buffer.from(shot.data, 'base64'));
  return result;
}
async function submitFromRuntime(client) {
  await setStorage(client, roles.requester);
  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await navigate(client, `${baseUrl}/?r73=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-record-row="${recordId}"]') && !!document.querySelector('[data-runtime-edit-record="${recordId}"]') })`);
  const before = await screenshot(client, 'requester-runtime-before-submit-desktop');
  await evaluate(client, `(() => {
    const button = document.querySelector('[data-runtime-edit-record="${recordId}"]');
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-edit-panel="edit"]') && !!document.querySelector('[data-runtime-submit-approval="edit"]') })`, 10000);
  await evaluate(client, `(() => {
    const button = document.querySelector('[data-runtime-submit-approval="edit"]');
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  await delay(3000);
  const after = await screenshot(client, 'requester-runtime-after-submit-desktop');
  await setViewport(client, { width: 390, height: 720, mobile: true });
  await navigate(client, `${baseUrl}/?r73=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-approval-sidebar="${recordId}"]') })`, 30000);
  const mobile = await screenshot(client, 'requester-runtime-after-submit-mobile');
  return [before, after, mobile];
}
async function approveFromTodo(client) {
  await setStorage(client, roles.approver);
  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await navigate(client, `${baseUrl}/?r73=${Date.now()}#/systems/${systemId}/messages`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-system-message-center="true"]') && !!document.querySelector('[data-system-message-item="${messageId}"]') })`, 30000);
  const messageBefore = await screenshot(client, 'approver-message-before-approval-desktop');
  await evaluate(client, `(() => {
    const item = document.querySelector('[data-system-message-item="${messageId}"]');
    item.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: location.hash.includes('/todos') && !!document.querySelector('[data-system-todo-row="${todoId}"]') })`, 30000);
  await waitFor(client, `JSON.stringify({ ok: !!(document.querySelector('[data-system-todo-action="approve"][data-system-todo-id="${todoId}"]') || document.querySelector('[data-system-todo-action="approve"][data-todo-id="${todoId}"]') || document.querySelector('[data-system-todo-row="${todoId}"] [data-system-todo-action="approve"]')) })`, 30000);
  const todoBefore = await screenshot(client, 'approver-todo-before-approval-desktop');
  await evaluate(client, `(() => {
    const row = document.querySelector('[data-system-todo-row="${todoId}"]');
    const button = document.querySelector('[data-system-todo-action="approve"][data-system-todo-id="${todoId}"]')
      || document.querySelector('[data-system-todo-action="approve"][data-todo-id="${todoId}"]')
      || row?.querySelector('[data-system-todo-action="approve"]')
      || Array.from(row?.querySelectorAll('button') ?? []).find((item) => (item.textContent || '').includes('审批'));
    if (!button) {
      throw new Error(JSON.stringify({ ok: false, todoId, rowHtml: row?.outerHTML ?? '', actionButtons: Array.from(document.querySelectorAll('[data-system-todo-action]')).map((item) => item.outerHTML) }));
    }
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-dialog-input="value"]') && !!document.querySelector('[data-dialog-submit="true"]') })`, 10000);
  await evaluate(client, `(() => {
    const input = document.querySelector('[data-dialog-input="value"]') || document.querySelector('.modal-overlay input');
    const submit = document.querySelector('[data-dialog-submit="true"]') || document.querySelector('.modal-actions .button.primary');
    if (!input || !submit) {
      return JSON.stringify({ ok: false, hasInput: !!input, hasSubmit: !!submit });
    }
    input.value = 'Approved through R73 browser todo';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    submit.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return JSON.stringify({ ok: true });
  })()`);
  await waitFor(client, `JSON.stringify({ ok: !document.querySelector('[data-system-todo-row="${todoId}"]') })`, 40000);
  const todoAfter = await screenshot(client, 'approver-todo-after-approval-desktop');
  await setStorage(client, roles.requester);
  await setViewport(client, { width: 1440, height: 920, mobile: false });
  await navigate(client, `${baseUrl}/?r73=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-approval-sidebar="${recordId}"][data-runtime-approval-status="APPROVED"]') })`, 40000);
  const runtimeAfter = await screenshot(client, 'requester-runtime-approved-desktop');
  await setViewport(client, { width: 390, height: 720, mobile: true });
  await navigate(client, `${baseUrl}/?r73=${Date.now()}#/systems/${systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-approval-sidebar="${recordId}"][data-runtime-approval-status="APPROVED"]') })`, 30000);
  const mobileAfter = await screenshot(client, 'requester-runtime-approved-mobile');
  return [messageBefore, todoBefore, todoAfter, runtimeAfter, mobileAfter];
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const results = phase === 'submit' ? await submitFromRuntime(client) : await approveFromTodo(client);
  client.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', phase, results };
  fs.writeFileSync(path.join(outDir, `workflow-todo-message-browser-audit-${phase}.json`), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $NodeScript -Encoding UTF8 -Value $NodeCode

function Invoke-BrowserPhase {
    param([string]$Phase, [string]$TodoId = '', [string]$MessageId = '')
    $env:R73_BASE_URL = $BaseUrl
    $env:R73_CDP_PORT = [string]$debugPort
    $env:R73_EVIDENCE_DIR = $EvidenceDir
    $env:R73_PHASE = $Phase
    $env:R73_SYSTEM_ID = [string]$Setup.targetSystemId
    $env:R73_RECORD_ID = [string]$Setup.recordId
    $env:R73_TODO_ID = [string]$TodoId
    $env:R73_MESSAGE_ID = [string]$MessageId
    $env:R73_ADMIN_TOKEN = [string]$AdminLogin.accessToken
    $env:R73_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
    $env:R73_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
    $env:R73_REQUESTER_TOKEN = [string]$RequesterLogin.accessToken
    $env:R73_REQUESTER_REFRESH = [string]$RequesterLogin.refreshToken
    $env:R73_REQUESTER_ACCOUNT_ID = [string]$RequesterLogin.profile.accountId
    $env:R73_APPROVER_TOKEN = [string]$ApproverLogin.accessToken
    $env:R73_APPROVER_REFRESH = [string]$ApproverLogin.refreshToken
    $env:R73_APPROVER_ACCOUNT_ID = [string]$ApproverLogin.profile.accountId
    $nodeOutput = & 'D:\dev\nodejs24\node.exe' $NodeScript | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "R73 browser audit phase $Phase failed with exit code $LASTEXITCODE. Output: $nodeOutput"
    }
    return (Get-Content -Raw -Encoding UTF8 (Join-Path $EvidenceDir "workflow-todo-message-browser-audit-$Phase.json") | ConvertFrom-Json)
}

$BrowserSubmit = Invoke-BrowserPhase -Phase 'submit'

$DetailAfterSubmit = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($Setup.targetSystemId)/runtime/modules/$($Setup.moduleId)/records/$($Setup.recordId)" -Headers $RequesterHeaders
$PendingTaskId = [string]$DetailAfterSubmit.approvalSidebar.pendingTaskId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($PendingTaskId)) -Message 'Browser submit did not create a pending approval task.'

$RequesterPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $RequesterHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$Setup.recordId
    status = 'PENDING'
    assigneeId = $RequesterLogin.profile.accountId
}
$ApproverPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$Setup.recordId
    status = 'PENDING'
    assigneeId = $ApproverLogin.profile.accountId
}
$ApproverMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'active'
    keyword = [string]$Setup.recordId
}
Assert-True -Condition ([int]$RequesterPending.page.total -eq 0) -Message 'Requester incorrectly sees approver pending todo after browser submit.'
Assert-True -Condition ([int]$ApproverPending.page.total -eq 1) -Message 'Approver does not see exactly one pending todo after browser submit.'
Assert-True -Condition ([int]$ApproverMessages.total -eq 1) -Message 'Approver does not see exactly one approval message after browser submit.'
$Todo = @($ApproverPending.page.records) | Select-Object -First 1
$Message = @($ApproverMessages.records) | Select-Object -First 1

$ApproverUnreadBefore = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=1" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = 'unread'
    archiveStatus = 'active'
    keyword = [string]$Setup.recordId
}
Assert-True -Condition ([int]$ApproverUnreadBefore.total -eq 1 -and [int]$ApproverUnreadBefore.pageNo -eq 1 -and [int]$ApproverUnreadBefore.pageSize -eq 1) `
    -Message 'Approver unread message search did not return one active unread approval message with page metadata.'

$MarkAllRead = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/mark-all-read" -Headers $ApproverHeaders -Body @{
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    keyword = [string]$Setup.recordId
}
$ApproverUnreadAfterMarkAll = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=1" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = 'unread'
    archiveStatus = 'active'
    keyword = [string]$Setup.recordId
}
$ApproverReadAfterMarkAll = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=1" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = 'read'
    archiveStatus = 'active'
    keyword = [string]$Setup.recordId
}
Assert-True -Condition ([int]$MarkAllRead.affectedCount -eq 1 -and [int]$ApproverUnreadAfterMarkAll.total -eq 0 -and [int]$ApproverReadAfterMarkAll.total -eq 1) `
    -Message 'Mark-all-read did not persist read state under the active approval message filter.'

$RequesterApproveDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/approval-tasks/$PendingTaskId/approve" -Headers $RequesterHeaders -Body @{
    idempotencyKey = "requester-denied-r73-$($Setup.suffix)"
    comment = 'Requester should not approve approver task'
}
$RequesterFlowAdminDenied = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$($Setup.targetSystemId)/flows/$($Setup.flowId)" -Headers $RequesterHeaders

$BrowserApprove = Invoke-BrowserPhase -Phase 'approve' -TodoId ([string]$Todo.todoId) -MessageId ([string]$Message.messageId)

$DetailAfterApprove = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($Setup.targetSystemId)/runtime/modules/$($Setup.moduleId)/records/$($Setup.recordId)" -Headers $RequesterHeaders
Assert-True -Condition ($DetailAfterApprove.summary.status -eq 'APPROVED') -Message 'Runtime detail did not show APPROVED after browser todo approval.'
Assert-True -Condition ($DetailAfterApprove.approvalSidebar.status -eq 'APPROVED') -Message 'Approval sidebar did not show APPROVED after browser todo approval.'
Assert-True -Condition ([string]::IsNullOrWhiteSpace([string]$DetailAfterApprove.approvalSidebar.pendingTaskId)) -Message 'Approved approval sidebar still exposes a pending task id.'

$PendingAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$Setup.recordId
    status = 'PENDING'
    assigneeId = $ApproverLogin.profile.accountId
}
$HandledAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$Setup.recordId
    status = 'HANDLED'
    assigneeId = $ApproverLogin.profile.accountId
}
Assert-True -Condition ([int]$PendingAfterApprove.page.total -eq 0) -Message 'Approved todo still appears as pending.'
Assert-True -Condition ([int]$HandledAfterApprove.page.total -eq 1) -Message 'Approved todo did not appear as handled.'

$ArchiveMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/archive" -Headers $ApproverHeaders -Body @{
    tenantId = [string]$Setup.targetTenantId
    messageIds = @([string]$Message.messageId)
    reason = 'R73 archive approval message after handled readback'
}
$ActiveMessagesAfterArchive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=1" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    archiveStatus = 'active'
    keyword = [string]$Setup.recordId
}
$ArchivedMessagesAfterArchive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/messages/search?pageNo=1&pageSize=1" -Headers $ApproverHeaders -Body @{
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    archiveStatus = 'archived'
    keyword = [string]$Setup.recordId
}
Assert-True -Condition ([int]$ArchiveMessage.affectedCount -eq 1 -and [int]$ActiveMessagesAfterArchive.total -eq 0 -and [int]$ArchivedMessagesAfterArchive.total -eq 1) `
    -Message 'Approval message archive did not persist active/archived filter readback.'

$DuplicateTodoActionStatus = ''
$DuplicateTodoConflictStatus = 0
try {
    $DuplicateTodoAction = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/todos/$($Todo.todoId)/actions/approve" -Headers $ApproverHeaders -Body @{
        idempotencyKey = "todo-r73-duplicate-after-browser-$($Setup.suffix)"
        comment = 'Duplicate approval after browser path should be rejected because task is terminal'
    }
    $DuplicateTodoActionStatus = [string]$DuplicateTodoAction.status
} catch {
    if ($_.Exception.Message -match 'HTTP 400' -and $_.Exception.Message -match 'TASK_STATE_CONFLICT') {
        $DuplicateTodoActionStatus = 'TASK_STATE_CONFLICT'
        $DuplicateTodoConflictStatus = 400
    } else {
        throw
    }
}
Assert-True -Condition ($DuplicateTodoActionStatus -eq 'TASK_STATE_CONFLICT' -or $DuplicateTodoActionStatus -eq 'HANDLED') -Message 'Duplicate todo action did not stay terminal or return state conflict.'

$TerminalRejectConflict = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/approval-tasks/$PendingTaskId/reject" -ExpectedStatus 400 -Headers $ApproverHeaders -Body @{
    idempotencyKey = "terminal-reject-r73-$($Setup.suffix)"
    reason = 'Terminal approval task should not be rejected after approval'
}
$TerminalTransferConflict = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/api/v1/systems/$($Setup.targetSystemId)/approval-tasks/$PendingTaskId/transfer" -ExpectedStatus 400 -Headers $ApproverHeaders -Body @{
    idempotencyKey = "terminal-transfer-r73-$($Setup.suffix)"
    transferTargetId = [string]$Setup.requesterMemberId
    transferTargetName = 'requester'
    reason = 'Terminal approval task should not be transferred after approval'
}

$RejectSetupJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $SetupScript -BaseUrl $BaseUrl -KeepCreatedData -StopAfterSubmit | Out-String
$RejectSetup = $RejectSetupJson | ConvertFrom-Json
Assert-True -Condition ($RejectSetup.status -eq 'PASS') -Message "R73 reject setup did not pass: $RejectSetupJson"
foreach ($systemId in @($RejectSetup.targetSystemId, $RejectSetup.normalOwnedSystemId, $RejectSetup.approverOwnedSystemId)) {
    if (-not [string]::IsNullOrWhiteSpace([string]$systemId)) {
        $script:CreatedSystemIds.Add([string]$systemId) | Out-Null
    }
}
$RejectRequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $RejectSetup.requesterAccount
    password = $RejectSetup.password
    loginTarget = 'PLATFORM'
}
$RejectRequesterHeaders = @{ Authorization = "Bearer $($RejectRequesterLogin.accessToken)" }
$RejectApproverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $RejectSetup.approverAccount
    password = $RejectSetup.password
    loginTarget = 'PLATFORM'
}
$RejectApproverHeaders = @{ Authorization = "Bearer $($RejectApproverLogin.accessToken)" }
foreach ($item in @(
    @{ headers = $script:AdminHeaders; reason = 'recovery-r73 reject admin switch' },
    @{ headers = $RejectRequesterHeaders; reason = 'recovery-r73 reject requester switch' },
    @{ headers = $RejectApproverHeaders; reason = 'recovery-r73 reject approver switch' }
)) {
    $null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $item.headers -Body @{
        systemId = $RejectSetup.targetSystemId
        tenantId = $RejectSetup.targetTenantId
        reason = $item.reason
    }
}
$RejectResult = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($RejectSetup.targetSystemId)/approval-tasks/$($RejectSetup.pendingTaskId)/reject" -Headers $RejectApproverHeaders -Body @{
    idempotencyKey = "reject-r73-$($RejectSetup.suffix)"
    reason = 'R73 rejection terminal state evidence'
}
$RejectDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($RejectSetup.targetSystemId)/runtime/modules/$($RejectSetup.moduleId)/records/$($RejectSetup.recordId)" -Headers $RejectRequesterHeaders
$RejectPendingAfter = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($RejectSetup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $RejectApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$RejectSetup.recordId
    status = 'PENDING'
    assigneeId = $RejectApproverLogin.profile.accountId
}
$RejectHandledAfter = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($RejectSetup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $RejectApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = [string]$RejectSetup.recordId
    status = 'HANDLED'
    assigneeId = $RejectApproverLogin.profile.accountId
}
Assert-True -Condition ($RejectDetail.summary.status -eq 'REJECTED' -and $RejectDetail.approvalSidebar.status -eq 'REJECTED' -and [int]$RejectPendingAfter.page.total -eq 0 -and [int]$RejectHandledAfter.page.total -eq 1) `
    -Message 'Reject path did not reach REJECTED terminal detail/sidebar and handled todo readback.'

$CombinedBrowserAudit = [ordered]@{
    status = if ($BrowserSubmit.status -eq 'PASS' -and $BrowserApprove.status -eq 'PASS') { 'PASS' } else { 'FAIL' }
    screenshotEvidenceBoundary = 'Screenshots prove visual containment and visible controls only. API/readback assertions prove submit, todo, message, approval, permission, idempotency, and terminal state behavior.'
    submit = $BrowserSubmit
    approve = $BrowserApprove
}
$CombinedBrowserAudit | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8
Remove-Item -LiteralPath $NodeScript -Force -ErrorAction SilentlyContinue
Stop-BrowserAudit

$script:CleanupResult = Remove-CreatedSystems

$BrowserResults = @($BrowserSubmit.results + $BrowserApprove.results)
$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-073'
    productStatus = 'R73_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.5', 'REQ-5.12', 'REQ-5.16', 'REQ-5.19', 'REQ-6.9', 'REQ-9')
    userSignoff = $false
    systemId = [string]$Setup.targetSystemId
    tenantId = [string]$Setup.targetTenantId
    moduleId = [string]$Setup.moduleId
    recordId = [string]$Setup.recordId
    flowId = [string]$Setup.flowId
    publishCheckPassed = [bool]$PublishCheck.passed
    publishCheckImpactCount = @($PublishCheck.impactRefs).Count
    simulationPassed = [bool]$Simulation.passed
    simulationRuntimeInstanceCreated = [bool]$Simulation.runtimeInstanceCreated
    simulationStepCount = @($Simulation.stepTraces).Count
    pendingTaskId = $PendingTaskId
    pendingTodoId = [string]$Todo.todoId
    pendingMessageId = [string]$Message.messageId
    requesterPendingTodoTotal = [int]$RequesterPending.page.total
    approverPendingTodoTotal = [int]$ApproverPending.page.total
    approverMessageTotal = [int]$ApproverMessages.total
    approverUnreadBeforeTotal = [int]$ApproverUnreadBefore.total
    markAllReadAffectedCount = [int]$MarkAllRead.affectedCount
    approverUnreadAfterMarkAllTotal = [int]$ApproverUnreadAfterMarkAll.total
    approverReadAfterMarkAllTotal = [int]$ApproverReadAfterMarkAll.total
    requesterApproveDeniedStatus = [int]$RequesterApproveDenied.status
    requesterFlowAdminDeniedStatus = [int]$RequesterFlowAdminDenied.status
    detailTerminalStatus = [string]$DetailAfterApprove.summary.status
    approvalSidebarStatus = [string]$DetailAfterApprove.approvalSidebar.status
    pendingAfterApproveTotal = [int]$PendingAfterApprove.page.total
    handledAfterApproveTotal = [int]$HandledAfterApprove.page.total
    archiveMessageAffectedCount = [int]$ArchiveMessage.affectedCount
    activeMessagesAfterArchiveTotal = [int]$ActiveMessagesAfterArchive.total
    archivedMessagesAfterArchiveTotal = [int]$ArchivedMessagesAfterArchive.total
    duplicateTodoActionStatus = [string]$DuplicateTodoActionStatus
    duplicateTodoConflictStatus = [int]$DuplicateTodoConflictStatus
    terminalRejectConflictStatus = [int]$TerminalRejectConflict.status
    terminalTransferConflictStatus = [int]$TerminalTransferConflict.status
    rejectSystemId = [string]$RejectSetup.targetSystemId
    rejectRecordId = [string]$RejectSetup.recordId
    rejectTaskId = [string]$RejectSetup.pendingTaskId
    rejectActionCode = [string]$RejectResult.actionCode
    rejectDetailTerminalStatus = [string]$RejectDetail.summary.status
    rejectApprovalSidebarStatus = [string]$RejectDetail.approvalSidebar.status
    rejectPendingAfterTotal = [int]$RejectPendingAfter.page.total
    rejectHandledAfterTotal = [int]$RejectHandledAfter.page.total
    browserResultCount = @($BrowserResults).Count
    browserOverflowCount = @($BrowserResults | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserResults | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = 'docs/evidence/recovery/screenshots/r73-workflow-todo-message-error-state-residual/workflow-todo-message-browser-audit.json'
    evidenceDir = 'docs/evidence/recovery/screenshots/r73-workflow-todo-message-error-state-residual'
    cleanup = $script:CleanupResult
    accepted = $true
}
Assert-True -Condition ($Result.browserOverflowCount -eq 0 -and $Result.browserBlockerCount -eq 0) -Message 'R73 browser audit reported blockers or horizontal overflow.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$Summary = @"
# REC-P0-073 / R73 Workflow Todo Message First-Use And Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.5, REQ-5.12, REQ-5.16, REQ-5.19, REQ-6.9, REQ-9
- System/module/record: system=$($Result.systemId), module=$($Result.moduleId), record=$($Result.recordId)
- Flow: flow=$($Result.flowId), publishCheck=$($Result.publishCheckPassed), simulation=$($Result.simulationPassed), simulationRuntimeInstanceCreated=$($Result.simulationRuntimeInstanceCreated), steps=$($Result.simulationStepCount)
- Browser requester submit: pendingTask=$($Result.pendingTaskId), pendingTodo=$($Result.pendingTodoId), pendingMessage=$($Result.pendingMessageId)
- Message read/archive: unreadBefore=$($Result.approverUnreadBeforeTotal), markAllReadAffected=$($Result.markAllReadAffectedCount), unreadAfter=$($Result.approverUnreadAfterMarkAllTotal), readAfter=$($Result.approverReadAfterMarkAllTotal), archiveAffected=$($Result.archiveMessageAffectedCount), activeAfterArchive=$($Result.activeMessagesAfterArchiveTotal), archivedAfterArchive=$($Result.archivedMessagesAfterArchiveTotal)
- Permission negatives: requesterApprove=$($Result.requesterApproveDeniedStatus), requesterFlowAdmin=$($Result.requesterFlowAdminDeniedStatus)
- Browser approver todo/message: requesterPending=$($Result.requesterPendingTodoTotal), approverPending=$($Result.approverPendingTodoTotal), approverMessages=$($Result.approverMessageTotal), pendingAfter=$($Result.pendingAfterApproveTotal), handledAfter=$($Result.handledAfterApproveTotal)
- Terminal state: detail=$($Result.detailTerminalStatus), sidebar=$($Result.approvalSidebarStatus), duplicateTodoAction=$($Result.duplicateTodoActionStatus), terminalReject=$($Result.terminalRejectConflictStatus), terminalTransfer=$($Result.terminalTransferConflictStatus)
- Reject path: system=$($Result.rejectSystemId), record=$($Result.rejectRecordId), task=$($Result.rejectTaskId), action=$($Result.rejectActionCode), detail=$($Result.rejectDetailTerminalStatus), sidebar=$($Result.rejectApprovalSidebarStatus), pendingAfter=$($Result.rejectPendingAfterTotal), handledAfter=$($Result.rejectHandledAfterTotal)
- Browser audit: results=$($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount), path=$($Result.browserAuditPath)
- Cleanup: $(@($Result.cleanup) -join ', ')

This does not close final product acceptance. Broader workflow variants, AI/OpenAPI depth, operations breadth, full requirement coverage, and user signoff remain open.
"@
$Summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}

