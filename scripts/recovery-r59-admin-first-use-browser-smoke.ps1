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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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
        if ($path -and (Test-Path -LiteralPath $path)) {
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
                reason = 'recovery-r59 admin first-use browser smoke cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r59-$script:Suffix-$systemId"
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
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R59_BROWSER_ADMIN_FIRST_USE_FAILED'
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r59-admin-first-use'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r59-admin-first-use-browser-result.json'
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

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R59 Admin First Use $script:Suffix"
    systemCode = "r59_admin_first_use_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r59 admin first-use browser setup'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R59 Core Group $script:Suffix"
    sort = 10
    visibleRoleIds = @()
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$Group.groupId
    moduleCode = "r59_core_$script:Suffix"
    name = "R59 Core Module $script:Suffix"
    status = 1
    description = 'Recovery R59 first-use module'
}
$ModuleId = [string]$Module.moduleId

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r59-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
    '--headless=new',
    '--disable-gpu',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    '--no-first-run',
    '--no-default-browser-check',
    'about:blank'
) -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 1200

$nodeScript = Join-Path $env:TEMP "unexamine-r59-admin-first-use-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');
const baseUrl = process.env.R59_BASE_URL;
const debugPort = process.env.R59_DEBUG_PORT;
const outDir = process.env.R59_EVIDENCE_DIR;
const systemId = process.env.R59_SYSTEM_ID;
const moduleName = process.env.R59_MODULE_NAME;
const admin = {
  accountId: process.env.R59_ADMIN_ACCOUNT_ID,
  accessToken: process.env.R59_ADMIN_TOKEN,
  refreshToken: process.env.R59_ADMIN_REFRESH
};

async function json(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`${url} -> ${response.status}`);
  return response.json();
}

class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.nextId = 1;
    this.pending = new Map();
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const callbacks = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? callbacks.reject(new Error(JSON.stringify(msg.error))) : callbacks.resolve(msg.result);
      }
    };
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
}

async function connect() {
  let target;
  try {
    target = await json(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await json(`http://127.0.0.1:${debugPort}/json/list`);
    target = targets[0];
  }
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  const client = new Cdp(ws);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  return { client, ws };
}

async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}

async function waitUntil(client, expression, timeout = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const raw = await evaluate(client, expression);
    const value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (value && value.ok) return value;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`waitUntil timeout: ${expression}`);
}

async function setStorage(client) {
  await client.send('Page.navigate', { url: baseUrl + '/#/' });
  await waitUntil(client, `JSON.stringify({ ok: location.origin === ${JSON.stringify(baseUrl)} })`, 5000);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(admin.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(admin.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(admin.refreshToken || '')});
    return JSON.stringify({ ok: true });
  })()`);
}

async function inspect(client, expectedTab) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const count = (selector) => document.querySelectorAll(selector).length;
    const active = document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') || '';
    return JSON.stringify({
      active,
      hasModuleName: text.includes(${JSON.stringify(moduleName)}),
      tabButtons: Array.from(document.querySelectorAll('[data-module-work-tab]')).map((el) => el.getAttribute('data-module-work-tab')),
      panels: {
        lifecycle: count('[data-module-lifecycle-panel]'),
        fields: count('.field-builder'),
        scene: count('[data-module-scene-config]'),
        page: count('[data-module-page-designer]'),
        print: count('[data-print-designer]')
      },
      overflowX: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      textSample: text.slice(0, 900)
    });
  })()`);
  const parsed = JSON.parse(raw);
  parsed.expectedTab = expectedTab;
  parsed.blockers = [];
  if (parsed.active !== expectedTab) parsed.blockers.push(`active tab ${parsed.active} != ${expectedTab}`);
  if (!parsed.hasModuleName) parsed.blockers.push('created module name is not visible');
  for (const tab of ['lifecycle', 'fields', 'scene', 'page', 'print']) {
    const count = parsed.panels[tab] || 0;
    if (tab === expectedTab && count < 1) parsed.blockers.push(`${tab} panel missing`);
    if (tab !== expectedTab && count > 0) parsed.blockers.push(`${tab} panel leaked while ${expectedTab} active`);
  }
  if (parsed.overflowX > 2) parsed.blockers.push('horizontal overflow');
  return parsed;
}

async function clickTab(client, tab) {
  await evaluate(client, `(() => {
    const button = document.querySelector('[data-module-work-tab="${tab}"]');
    if (!button) throw new Error('tab button missing: ${tab}');
    button.click();
    return JSON.stringify({ ok: true });
  })()`);
  await waitUntil(client, `JSON.stringify({ ok: document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') === '${tab}' })`, 20000);
}

async function auditViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile
  });
  await client.send('Page.navigate', { url: `${baseUrl}/?r59=${Date.now()}#/systems/${systemId}/admin/module-config` });
  await waitUntil(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-module-work-tabs="true"]') && (document.body.innerText || '').includes(${JSON.stringify(moduleName)}) })`, 30000);
  const results = [];
  for (const tab of ['lifecycle', 'fields', 'scene', 'page', 'print']) {
    if (tab !== 'lifecycle') await clickTab(client, tab);
    if (tab === 'page') {
      await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-module-page-designer]') })`, 30000);
    }
    if (tab === 'print') {
      await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('[data-print-designer]') })`, 30000);
    }
    results.push({ viewport: viewport.name, ...(await inspect(client, tab)) });
    const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
    fs.writeFileSync(path.join(outDir, `module-${tab}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  }
  return results;
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const { client, ws } = await connect();
  await setStorage(client);
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 760, mobile: true }
  ];
  const results = [];
  for (const viewport of viewports) {
    results.push(...await auditViewport(client, viewport));
  }
  ws.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    results
  };
  fs.writeFileSync(path.join(outDir, 'admin-first-use-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
})().catch((error) => {
  console.error(error.stack || String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R59_BASE_URL = $BaseUrl
$env:R59_DEBUG_PORT = [string]$debugPort
$env:R59_EVIDENCE_DIR = $EvidenceDir
$env:R59_SYSTEM_ID = $SystemId
$env:R59_MODULE_NAME = [string]$Module.name
$env:R59_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R59_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R59_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$BrowserJson = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "Node R59 admin first-use browser audit exited with code $LASTEXITCODE. Output: $BrowserJson"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'admin-first-use-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    productStatus = 'R59_ADMIN_FIRST_USE_BROWSER_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    moduleName = [string]$Module.name
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    activeTabsCovered = @($BrowserAudit.results | ForEach-Object { $_.active } | Sort-Object -Unique)
    browserAuditPath = $BrowserAuditPath
    cleanup = $script:CleanupResult
    accepted = $false
    userSignoff = $false
}

Assert-True -Condition ($Result.browserBlockerCount -eq 0) -Message 'R59 browser audit reported blockers.'
Assert-True -Condition ($Result.browserOverflowCount -eq 0) -Message 'R59 browser audit reported horizontal overflow.'

$Result | ConvertTo-Json -Depth 80 | Set-Content -Encoding UTF8 -Path $script:ResultFile
$Result | ConvertTo-Json -Depth 80

if ($NoFailExit) {
    exit 0
}
