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
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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

function Invoke-ApiFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null
    )
    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
        if ($response.code -eq 'SUCCESS') {
            throw "Expected API failure but received SUCCESS for $Method $Path"
        }
        return $response
    } catch {
        if ($_.Exception.Response) {
            return @{ httpStatus = [int]$_.Exception.Response.StatusCode }
        }
        throw
    }
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
        reason = 'recovery-r15 password reset cleanup'
        impactScope = 'created_by_recovery_r15'
        idempotencyKey = "cleanup-r15-$script:Suffix"
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
$OldPassword = 'Aa123456!'
$NewPassword = "Bb$Suffix!"
$AccountName = "r15_reset_$Suffix"
$SystemCode = "r15_reset_system_$Suffix"
$script:SystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:NodeScript = $null
$CleanupResult = 'SKIPPED'
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r15-password-reset'
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

$register = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $AccountName
    mobile = "16$Suffix"
    email = "r15_reset_$Suffix@example.com"
    password = $OldPassword
    systemName = "R15 Password Reset $Suffix"
    systemCode = $SystemCode
    tenantMode = 1
    templateCode = 'blank'
}
$script:SystemId = [string]$register.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'Register did not return systemId.'

$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r15-reset-chrome-$Suffix"
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

for ($i = 0; $i -lt 50; $i++) {
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null
        break
    } catch {
        Start-Sleep -Milliseconds 200
    }
}

$script:NodeScript = Join-Path $env:TEMP "unexamine-r15-password-reset-$Suffix.js"
$nodeSource = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R15_BASE_URL;
const accountName = process.env.R15_ACCOUNT_NAME;
const newPassword = process.env.R15_NEW_PASSWORD;
const port = process.env.R15_CDP_PORT;
const outDir = process.env.R15_EVIDENCE_DIR;

async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} ${pathname}`);
  return response.json();
}

async function target() {
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
        return new Promise((res, rej) => pending.set(id, { res, rej }));
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
        message.error ? item.rej(new Error(JSON.stringify(message.error))) : item.res(message.result);
      }
      const waiter = waiters.get(message.method);
      if (waiter) {
        waiters.delete(message.method);
        waiter(message.params);
      }
    };
  });
}

async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}

async function waitUntil(client, expression, timeoutMs = 10000) {
  const start = Date.now();
  let last = null;
  while (Date.now() - start < timeoutMs) {
    last = await evaluate(client, expression).catch(() => null);
    if (last && (last.ok === undefined || last.ok)) return last;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const client = await connect((await target()).webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 720, deviceScaleFactor: 1, mobile: false });

  await client.send('Page.navigate', { url: `${baseUrl}/forgot-password` });
  await client.wait('Page.loadEventFired', 10000);
  await waitUntil(client, `({ ok: !!document.querySelector('input[data-field-name="loginName"]') })`, 8000);
  await evaluate(client, `(() => {
    const loginName = document.querySelector('input[data-field-name="loginName"]');
    loginName.value = ${JSON.stringify(accountName)};
    loginName.dispatchEvent(new Event('input', { bubbles: true }));
    loginName.dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('.auth-form section:first-of-type button').click();
    return true;
  })()`);
  const requestState = await waitUntil(client, `(() => {
    const ticket = document.querySelector('input[data-field-name="resetTicket"]')?.value || '';
    const code = document.querySelector('input[data-field-name="verifyCode"]')?.value || '';
    return { ok: ticket.startsWith('reset_') && code.length === 6, ticket, code };
  })()`, 10000);
  await evaluate(client, `(() => {
    const newPassword = document.querySelector('input[data-field-name="newPassword"]');
    newPassword.value = ${JSON.stringify(newPassword)};
    newPassword.dispatchEvent(new Event('input', { bubbles: true }));
    newPassword.dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('.auth-form section:nth-of-type(2) button').click();
    return true;
  })()`);
  const confirmState = await waitUntil(client, `(() => {
    const confirm = document.querySelector('.auth-form section:nth-of-type(2) button');
    const text = document.body.innerText || '';
    return { ok: !!confirm && !confirm.disabled, text };
  })()`, 10000);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(outDir, 'desktop-password-reset.png'), Buffer.from(screenshot.data, 'base64'));
  client.close();
  console.log(JSON.stringify({ requestState, confirmState: { ok: confirmState.ok }, screenshot: 'desktop-password-reset.png' }, null, 2));
}

run().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $script:NodeScript -Encoding UTF8

$env:R15_BASE_URL = $BaseUrl
$env:R15_ACCOUNT_NAME = $AccountName
$env:R15_NEW_PASSWORD = $NewPassword
$env:R15_CDP_PORT = [string]$debugPort
$env:R15_EVIDENCE_DIR = $EvidenceDir
$browserJson = node $script:NodeScript
if ($LASTEXITCODE -ne 0) {
    throw "Node password-reset browser audit exited with code $LASTEXITCODE"
}
$browserResult = $browserJson | ConvertFrom-Json

$oldLoginFailure = Invoke-ApiFailure -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $AccountName
    password = $OldPassword
    loginTarget = 'PLATFORM'
}
$newLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $AccountName
    password = $NewPassword
    loginTarget = 'PLATFORM'
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$newLogin.accessToken)) -Message 'New password login did not return access token.'

$reuseFailure = Invoke-ApiFailure -Method 'Post' -Path '/api/v1/auth/password-reset/confirm' -Body @{
    resetTicket = [string]$browserResult.requestState.ticket
    verifyCode = [string]$browserResult.requestState.code
    newPassword = "Cc$Suffix!"
}

$CleanupResult = Remove-CreatedSystem

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-018'
    suffix = $Suffix
    baseUrl = $BaseUrl
    accountName = $AccountName
    systemId = $script:SystemId
    resetTicketPrefix = ([string]$browserResult.requestState.ticket).Substring(0, 12)
    verifyCodeLength = ([string]$browserResult.requestState.code).Length
    oldPasswordRejected = $true
    newPasswordLoginAccountId = [string]$newLogin.profile.accountId
    ticketReuseRejected = $true
    oldLoginFailure = $oldLoginFailure
    reuseFailure = $reuseFailure
    browser = $browserResult
    screenshots = @('docs/evidence/recovery/screenshots/r15-password-reset/desktop-password-reset.png')
    cleanup = $CleanupResult
    generatedAt = (Get-Date).ToString('o')
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r15-password-reset-result.json'
$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -Path $resultPath

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r15-password-reset-account-closure-2026-06-29.md'
$summary = @"
# R15 Password Reset Account Closure Evidence

Status: PASS

- Base URL: $BaseUrl
- Task: REC-P0-018
- Browser route: /forgot-password
- Disposable account: $AccountName
- Disposable system id: $script:SystemId
- Browser request produced reset ticket and a 6-digit verification code.
- Browser confirm completed from the deployed password reset page.
- Old password login was rejected after reset.
- New password login returned account id $($newLogin.profile.accountId).
- Reset ticket reuse was rejected.
- Cleanup result: $CleanupResult

Machine-readable result: docs/evidence/recovery/r15-password-reset-result.json
Screenshot: docs/evidence/recovery/screenshots/r15-password-reset/desktop-password-reset.png
"@
$summary | Set-Content -Encoding UTF8 -Path $summaryPath

Write-Host ($result | ConvertTo-Json -Depth 20)
