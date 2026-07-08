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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
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

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:Setup -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:Setup.targetSystemId, $script:Setup.normalOwnedSystemId, $script:Setup.approverOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace([string]$systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r48 workflow message flow cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r48-$($script:Setup.suffix)-$systemId"
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
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
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

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Setup = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r48-workflow-message-flow'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r48-workflow-message-flow-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r48-workflow-message-flow-2026-07-01.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$SetupScript = Join-Path $PSScriptRoot 'recovery-r3-runtime-approval-smoke.ps1'
$SetupJson = & $SetupScript -BaseUrl $BaseUrl -KeepCreatedData -StopAfterSubmit | Out-String
$script:Setup = $SetupJson | ConvertFrom-Json
Assert-True -Condition ($script:Setup.status -eq 'PASS') -Message "R48 setup did not pass: $SetupJson"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }
$RequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $script:Setup.requesterAccount
    password = $script:Setup.password
    loginTarget = 'PLATFORM'
}
$RequesterHeaders = @{ Authorization = "Bearer $($RequesterLogin.accessToken)" }
$ApproverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $script:Setup.approverAccount
    password = $script:Setup.password
    loginTarget = 'PLATFORM'
}
$ApproverHeaders = @{ Authorization = "Bearer $($ApproverLogin.accessToken)" }

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r48 requester switch'
}
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r48 approver switch'
}
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r48 admin switch'
}

$Flows = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/flows?pageNo=1&pageSize=20" -Headers $script:AdminHeaders
$Flow = @($Flows.records | Where-Object { $_.flowCode -eq "flow_r3_$($script:Setup.suffix)" }) | Select-Object -First 1
Assert-True -Condition ($null -ne $Flow) -Message 'R48 could not find the setup approval flow.'
$FlowId = [string]$Flow.flowId
$FlowDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/flows/$FlowId" -Headers $script:AdminHeaders
$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/flows/$FlowId/publish-check" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r48 flow publish check'
    idempotencyKey = "flow-publish-check-r48-$($script:Setup.suffix)"
}
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message 'R48 flow publish-check did not pass.'
$Simulation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/flows/$FlowId/simulate" -Headers $script:AdminHeaders -Body @{
    moduleId = [string]$script:Setup.moduleId
    recordId = [string]$script:Setup.recordId
    versionNo = 'DRAFT'
    actorMemberId = [string]$script:Setup.requesterMemberId
    fieldValues = @{
        contractAmount = 3000
        contractName = "R48 Simulation $($script:Setup.suffix)"
    }
    idempotencyKey = "flow-sim-r48-$($script:Setup.suffix)"
}
Assert-True -Condition ($Simulation.passed -eq $true -and $Simulation.runtimeInstanceCreated -eq $false) `
    -Message "R48 flow simulation failed or created a runtime instance: $($Simulation | ConvertTo-Json -Depth 40 -Compress)"

$DetailAfterSubmit = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/runtime/modules/$($script:Setup.moduleId)/records/$($script:Setup.recordId)" -Headers $RequesterHeaders
Assert-True -Condition ([string]$DetailAfterSubmit.approvalSidebar.pendingTaskId -eq [string]$script:Setup.pendingTaskId) `
    -Message 'Requester runtime detail did not expose the expected pending approval task.'

$RequesterApproveDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/approval-tasks/$($script:Setup.pendingTaskId)/approve" -Headers $RequesterHeaders -Body @{
    idempotencyKey = "requester-denied-r48-$($script:Setup.suffix)"
    comment = 'Requester should not approve approver task'
}
$NormalAdminDenied = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/flows/$FlowId" -Headers $RequesterHeaders

$RequesterPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $RequesterHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    assigneeId = $RequesterLogin.profile.accountId
}
$ApproverPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    assigneeId = $ApproverLogin.profile.accountId
}
$ApproverMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'active'
    keyword = $script:Setup.recordId
}
Assert-True -Condition ([int]$RequesterPending.page.total -eq 0) -Message 'Requester incorrectly sees the approver pending todo.'
Assert-True -Condition ([int]$ApproverPending.page.total -eq 1) -Message 'Approver did not see exactly one pending approval todo.'
Assert-True -Condition ([int]$ApproverMessages.total -eq 1) -Message 'Approver did not see exactly one approval message.'
$Todo = @($ApproverPending.page.records) | Select-Object -First 1
$Message = @($ApproverMessages.records) | Select-Object -First 1

function Start-BrowserAudit {
    param([string]$Phase)
    $chromePath = Find-Chrome
    $debugPort = Get-FreeTcpPort
    $script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r48-chrome-$Phase-$($script:Setup.suffix)"
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

$debugPort = Start-BrowserAudit -Phase 'before'
$nodeScript = Join-Path $env:TEMP "unexamine-r48-workflow-message-flow-$($script:Setup.suffix).js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R48_BASE_URL;
const port = process.env.R48_CDP_PORT;
const outDir = process.env.R48_EVIDENCE_DIR;
const systemId = process.env.R48_SYSTEM_ID;
const suffix = process.env.R48_SUFFIX;
const phase = process.env.R48_PHASE;
const roles = {
  admin: { accountId: process.env.R48_ADMIN_ACCOUNT_ID, accessToken: process.env.R48_ADMIN_TOKEN, refreshToken: process.env.R48_ADMIN_REFRESH || '' },
  approver: { accountId: process.env.R48_APPROVER_ACCOUNT_ID, accessToken: process.env.R48_APPROVER_TOKEN, refreshToken: process.env.R48_APPROVER_REFRESH || '' },
  requester: { accountId: process.env.R48_REQUESTER_ACCOUNT_ID, accessToken: process.env.R48_REQUESTER_TOKEN, refreshToken: process.env.R48_REQUESTER_REFRESH || '' },
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
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
  await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
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
async function capture(client, route, viewport) {
  await navigate(client, `${baseUrl}/?r48=${Date.now()}#${route.path}`);
  await waitFor(client, `JSON.stringify({ ok: ${route.wait} })`, 30000);
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const expectedSelectors = ${JSON.stringify(route.expectedSelectors)};
    const forbiddenSelectors = ${JSON.stringify(route.forbiddenSelectors || [])};
    const requiredTexts = ${JSON.stringify(route.requiredTexts || [])};
    const forbiddenTexts = ${JSON.stringify(route.forbiddenTexts || [])};
    return JSON.stringify({
      key: ${JSON.stringify(route.key)},
      role: ${JSON.stringify(route.role)},
      phase: ${JSON.stringify(phase)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      overflowX,
      expectedMissing: expectedSelectors.filter((selector) => !document.querySelector(selector)),
      forbiddenVisible: forbiddenSelectors.filter((selector) => document.querySelector(selector)),
      requiredTextMissing: requiredTexts.filter((item) => !text.includes(item)),
      forbiddenTextVisible: forbiddenTexts.filter((item) => text.includes(item)),
      hasSuffix: text.includes(${JSON.stringify(suffix)}),
      textSample: text.slice(0, 1500)
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.expectedMissing.length) result.blockers.push('expected selector missing');
  if (result.forbiddenVisible.length) result.blockers.push('forbidden selector visible');
  if (result.requiredTextMissing.length) result.blockers.push('required text missing');
  if (result.forbiddenTextVisible.length) result.blockers.push('forbidden text visible');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${phase}-${route.key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const routeSets = phase === 'before' ? [
    { role: 'admin', key: 'flow-management', path: `/systems/${systemId}/admin/flow-management`, wait: `!!document.querySelector('.admin-content') && !!document.querySelector('#flow-management')`, expectedSelectors: ['.admin-content', '#flow-management'], forbiddenSelectors: ['.runtime-shell'] },
    { role: 'approver', key: 'approver-todos-pending', path: `/systems/${systemId}/todos`, wait: `!!document.querySelector('[data-system-todo-workbench="true"]') && !!document.querySelector('[data-system-todo-layout="true"]')`, expectedSelectors: ['[data-system-todo-workbench="true"]', '[data-system-todo-layout="true"]'], forbiddenSelectors: ['.runtime-module-sidebar', '.admin-content'] },
    { role: 'approver', key: 'approver-messages-active', path: `/systems/${systemId}/messages`, wait: `!!document.querySelector('[data-system-message-center="true"]')`, expectedSelectors: ['[data-system-message-center="true"]'], forbiddenSelectors: ['.runtime-module-sidebar', '.admin-content'] },
    { role: 'requester', key: 'requester-runtime-pending', path: `/systems/${systemId}/modules`, wait: `!!document.querySelector('.runtime-shell')`, expectedSelectors: ['.runtime-shell', '.runtime-main'], forbiddenSelectors: ['.admin-content'] },
  ] : [
    { role: 'approver', key: 'approver-todos-handled', path: `/systems/${systemId}/todos`, wait: `!!document.querySelector('[data-system-todo-workbench="true"]') && !!document.querySelector('[data-system-todo-layout="true"]')`, expectedSelectors: ['[data-system-todo-workbench="true"]', '[data-system-todo-layout="true"]'], forbiddenSelectors: ['.runtime-module-sidebar', '.admin-content'] },
    { role: 'requester', key: 'requester-runtime-approved', path: `/systems/${systemId}/modules`, wait: `!!document.querySelector('.runtime-shell')`, expectedSelectors: ['.runtime-shell', '.runtime-main'], forbiddenSelectors: ['.admin-content'] },
    { role: 'admin', key: 'flow-management-after', path: `/systems/${systemId}/admin/flow-management`, wait: `!!document.querySelector('.admin-content') && !!document.querySelector('#flow-management')`, expectedSelectors: ['.admin-content', '#flow-management'], forbiddenSelectors: ['.runtime-shell'] },
  ];
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  const results = [];
  let currentRole = '';
  for (const viewport of viewports) {
    await setViewport(client, viewport);
    for (const route of routeSets) {
      if (route.role !== currentRole) {
        currentRole = route.role;
        await setStorage(client, roles[currentRole]);
      }
      results.push(await capture(client, route, viewport));
    }
  }
  client.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', phase, results };
  fs.writeFileSync(path.join(outDir, `workflow-message-flow-browser-audit-${phase}.json`), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

function Invoke-BrowserPhase {
    param([string]$Phase, [int]$Port)
    $env:R48_BASE_URL = $BaseUrl
    $env:R48_CDP_PORT = [string]$Port
    $env:R48_EVIDENCE_DIR = $EvidenceDir
    $env:R48_SYSTEM_ID = [string]$script:Setup.targetSystemId
    $env:R48_SUFFIX = [string]$script:Setup.suffix
    $env:R48_PHASE = $Phase
    $env:R48_ADMIN_TOKEN = [string]$AdminLogin.accessToken
    $env:R48_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
    $env:R48_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
    $env:R48_APPROVER_TOKEN = [string]$ApproverLogin.accessToken
    $env:R48_APPROVER_REFRESH = [string]$ApproverLogin.refreshToken
    $env:R48_APPROVER_ACCOUNT_ID = [string]$ApproverLogin.profile.accountId
    $env:R48_REQUESTER_TOKEN = [string]$RequesterLogin.accessToken
    $env:R48_REQUESTER_REFRESH = [string]$RequesterLogin.refreshToken
    $env:R48_REQUESTER_ACCOUNT_ID = [string]$RequesterLogin.profile.accountId
    $nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "R48 browser audit phase $Phase failed with exit code $LASTEXITCODE. Output: $nodeOutput"
    }
    return (Get-Content -Raw -Encoding UTF8 (Join-Path $EvidenceDir "workflow-message-flow-browser-audit-$Phase.json") | ConvertFrom-Json)
}

$BrowserBefore = Invoke-BrowserPhase -Phase 'before' -Port $debugPort
Stop-BrowserAudit

$TodoActionKey = "todo-approve-r48-$($script:Setup.suffix)"
$TodoAction = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/$($Todo.todoId)/actions/approve" -Headers $ApproverHeaders -Body @{
    idempotencyKey = $TodoActionKey
    comment = 'Approved through R48 todo workbench path'
}
Assert-True -Condition ($TodoAction.status -eq 'HANDLED') -Message 'Todo approval action did not move todo to HANDLED.'
$TodoActionDuplicate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/$($Todo.todoId)/actions/approve" -Headers $ApproverHeaders -Body @{
    idempotencyKey = $TodoActionKey
    comment = 'Duplicate approval through R48 todo workbench path'
}

$DetailAfterApprove = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/runtime/modules/$($script:Setup.moduleId)/records/$($script:Setup.recordId)" -Headers $RequesterHeaders
Assert-True -Condition ($DetailAfterApprove.summary.status -eq 'APPROVED') -Message 'Runtime detail did not show APPROVED after todo approval.'
Assert-True -Condition ($DetailAfterApprove.approvalSidebar.status -eq 'APPROVED') -Message 'Approval sidebar did not show APPROVED after todo approval.'

$PendingAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    assigneeId = $ApproverLogin.profile.accountId
}
$HandledAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'HANDLED'
    assigneeId = $ApproverLogin.profile.accountId
}
Assert-True -Condition ([int]$PendingAfterApprove.page.total -eq 0) -Message 'Approved todo still appears as pending.'
Assert-True -Condition ([int]$HandledAfterApprove.page.total -eq 1) -Message 'Approved todo did not appear as handled.'

$debugPort = Start-BrowserAudit -Phase 'after'
$BrowserAfter = Invoke-BrowserPhase -Phase 'after' -Port $debugPort
Stop-BrowserAudit

$CombinedBrowserAudit = [ordered]@{
    status = if ($BrowserBefore.status -eq 'PASS' -and $BrowserAfter.status -eq 'PASS') { 'PASS' } else { 'FAIL' }
    before = $BrowserBefore
    after = $BrowserAfter
}
$BrowserAuditPath = Join-Path $EvidenceDir 'workflow-message-flow-browser-audit.json'
$CombinedBrowserAudit | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $BrowserAuditPath

Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-048'
    frc = 'FRC-4B Workflow, approval, todo/message, and flow surface closure'
    baseUrl = $BaseUrl
    systemId = [string]$script:Setup.targetSystemId
    tenantId = [string]$script:Setup.targetTenantId
    moduleId = [string]$script:Setup.moduleId
    recordId = [string]$script:Setup.recordId
    flowId = $FlowId
    flowName = [string]$FlowDetail.flowName
    flowPublishStatus = [string]$FlowDetail.publishStatus
    flowCurrentVersion = [string]$FlowDetail.currentVersion
    publishCheckPassed = [bool]$PublishCheck.passed
    publishCheckImpactCount = @($PublishCheck.impactRefs).Count
    simulationPassed = [bool]$Simulation.passed
    simulationRuntimeInstanceCreated = [bool]$Simulation.runtimeInstanceCreated
    simulationStepCount = @($Simulation.stepTraces).Count
    requesterPendingTodoTotal = [int]$RequesterPending.page.total
    approverPendingTodoTotal = [int]$ApproverPending.page.total
    approverMessageTotal = [int]$ApproverMessages.total
    pendingTodoId = [string]$Todo.todoId
    pendingMessageId = [string]$Message.messageId
    pendingTaskId = [string]$script:Setup.pendingTaskId
    requesterApproveDeniedStatus = $RequesterApproveDenied.status
    normalAdminDeniedStatus = $NormalAdminDenied.status
    todoActionStatus = [string]$TodoAction.status
    todoActionTraceId = [string]$TodoAction.traceId
    duplicateTodoActionStatus = [string]$TodoActionDuplicate.status
    duplicateTodoActionTraceId = [string]$TodoActionDuplicate.traceId
    detailTerminalStatus = [string]$DetailAfterApprove.summary.status
    approvalSidebarStatus = [string]$DetailAfterApprove.approvalSidebar.status
    pendingAfterApproveTotal = [int]$PendingAfterApprove.page.total
    handledAfterApproveTotal = [int]$HandledAfterApprove.page.total
    browserBeforeResultCount = @($BrowserBefore.results).Count
    browserAfterResultCount = @($BrowserAfter.results).Count
    browserOverflowCount = @($BrowserBefore.results + $BrowserAfter.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserBefore.results + $BrowserAfter.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
}
Assert-True -Condition ($Result.browserBlockerCount -eq 0 -and $Result.browserOverflowCount -eq 0) -Message 'R48 browser audit reported blockers or horizontal overflow.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R48 Workflow Message Flow Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $($Result.systemId)",
    "- Flow: $FlowId / $($Result.flowName) / $($Result.flowCurrentVersion)",
    "- Publish check passed: $($Result.publishCheckPassed), impact refs: $($Result.publishCheckImpactCount)",
    "- Simulation passed: $($Result.simulationPassed), runtimeInstanceCreated=$($Result.simulationRuntimeInstanceCreated), steps=$($Result.simulationStepCount)",
    "- Approval setup: requesterPending=$($Result.requesterPendingTodoTotal), approverPending=$($Result.approverPendingTodoTotal), approverMessages=$($Result.approverMessageTotal)",
    "- Permission negatives: requesterApprove=$($Result.requesterApproveDeniedStatus), normalAdminFlow=$($Result.normalAdminDeniedStatus)",
    "- Todo action: todo=$($Result.pendingTodoId), status=$($Result.todoActionStatus), duplicateStatus=$($Result.duplicateTodoActionStatus)",
    "- Terminal state: detail=$($Result.detailTerminalStatus), sidebar=$($Result.approvalSidebarStatus), pendingAfter=$($Result.pendingAfterApproveTotal), handledAfter=$($Result.handledAfterApproveTotal)",
    "- Browser results: before=$($Result.browserBeforeResultCount), after=$($Result.browserAfterResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    "- Browser audit JSON: docs/evidence/recovery/screenshots/r48-workflow-message-flow/workflow-message-flow-browser-audit.json",
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile

$Result | ConvertTo-Json -Depth 100

if ($NoFailExit) {
    exit 0
}
