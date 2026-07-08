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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
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

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 403) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            throw "Expected HTTP 403 but got HTTP ${status}: $body"
        }
        return @{ status = $status }
    }
    throw "Expected forbidden response but request succeeded: $Method $Path"
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function New-FieldBody {
    param([string]$Code, [string]$Name, [string]$Type = 'TEXT', [bool]$Required = $false)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        filterOperators = @('EQ', 'LIKE')
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = 'none'
            desensitizeMode = 'none'
        }
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
    foreach ($systemId in @($script:TargetSystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r37 runtime first navigation cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r37-$script:Suffix-$systemId"
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
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:TargetSystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$Password = 'Aa123456!'
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r37-runtime-first-navigation'
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

$Target = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R37 Runtime First Navigation $script:Suffix"
    systemCode = "r37_runtime_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:TargetSystemId = [string]$Target.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:TargetSystemId }) | Select-Object -First 1
$TenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Target tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TenantId
    reason = 'recovery-r37 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R37 Runtime Reader $script:Suffix"
    roleCode = "R37_RUNTIME_READER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R37 runtime first navigation role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R37 Runtime Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r37_case_$script:Suffix"
    name = "R37 Case Runtime $script:Suffix"
    status = 1
    description = 'Recovery R37 runtime first navigation module'
}
$ModuleId = [string]$Module.moduleId

$TitleCode = "caseTitle_$($script:Suffix.Replace('-', '_'))"
$AmountCode = "caseAmount_$($script:Suffix.Replace('-', '_'))"
$OwnerCode = "caseOwner_$($script:Suffix.Replace('-', '_'))"
$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $TitleCode -Name 'Case Title' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $AmountCode -Name 'Case Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $OwnerCode -Name 'Case Owner' -Type 'TEXT')

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R37 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$AmountField.fieldId, [string]$OwnerField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$OwnerField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$Permission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:TargetSystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.read" = $true
        "record.create" = $false
        "record.edit" = $false
        "record.delete" = $false
    }
    fieldPermissions = @{ "*" = "READABLE" }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r37 module publish'
    idempotencyKey = "module-publish-r37-$script:Suffix"
}

$TemplateCode = "print_r37_$script:Suffix"
$DraftTemplate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/print-templates" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    templateName = "R37 Runtime Print $script:Suffix"
    version = "draft_$script:Suffix"
    status = 1
    defaultTemplate = $true
    visibleRoleIds = @()
    boundFieldCodes = @($TitleCode, $AmountCode, $OwnerCode)
    detailTableFieldCodes = @($TitleCode, $AmountCode)
    signatureLabels = @('Prepared by', 'Approved by')
    headerText = 'R37 Runtime Print Header'
    footerText = 'R37 Runtime Print Footer'
    previewFileId = "preview_r37_$script:Suffix"
    pageSetup = @{ paper = 'A4'; orientation = 'PORTRAIT' }
}
Assert-True -Condition ($DraftTemplate.publishStatus -eq 'DRAFT') -Message 'Draft print template was not saved.'

$PublishedTemplate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/print-templates/$TemplateCode/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r37 print publish'
    idempotencyKey = "print-publish-r37-$script:Suffix"
}
Assert-True -Condition ($PublishedTemplate.result -eq 'PUBLISHED_PRINT_TEMPLATE') -Message 'Print template publish failed.'

$RecordValue = "R37 First Navigation Record $script:Suffix"
$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
    fieldValues = @{
        $TitleCode = $RecordValue
        $AmountCode = 3700
        $OwnerCode = 'Runtime Ops'
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R37_RUNTIME_FIRST_NAVIGATION'
}
$RecordId = [string]$Record.recordId

$NormalLoginName = "r37_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r37_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R37 Owned $script:Suffix"
    systemCode = "r37_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R37 Runtime Member $script:Suffix"
    employeeNo = "R37NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r37_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$NormalBinding = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}

$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TenantId
    reason = 'recovery-r37 normal switch precheck'
}
$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{ $TitleCode = 'R37 forbidden write' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R37_FORBIDDEN_WRITE'
}

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r37-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$chromeArgs = @(
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--remote-allow-origins=*',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    'about:blank'
)
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden

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

$nodeScript = Join-Path $env:TEMP "unexamine-r37-runtime-first-navigation-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R37_BASE_URL;
const systemId = process.env.R37_SYSTEM_ID;
const accountName = process.env.R37_ACCOUNT;
const password = process.env.R37_PASSWORD;
const recordValue = process.env.R37_RECORD_VALUE;
const outDir = process.env.R37_EVIDENCE_DIR;
const port = process.env.R37_CDP_PORT;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
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
    ws.onopen = () => resolve({
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
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`));
        else item.res(message.result);
      }
      if (message.method && eventWaiters.has(message.method)) {
        const waiter = eventWaiters.get(message.method);
        eventWaiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}

async function setViewport(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 640 });
  await client.send('Emulation.setVisibleSize', { width, height });
}

async function navigate(client, url) {
  const waitLoad = client.waitFor('Page.loadEventFired', 7000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(900);
}

async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}

async function waitUntil(client, expression, timeoutMs = 12000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(250);
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}

async function realLogin(client) {
  await navigate(client, `${baseUrl}/#/login`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`, 7000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const login = document.querySelector('form.auth-form input[data-field-name="loginName"]');
        const pwd = document.querySelector('form.auth-form input[data-field-name="password"]');
        const button = document.querySelector('form.auth-form button.button.primary');
        login.value = ${JSON.stringify(accountName)};
        login.dispatchEvent(new Event('input', { bubbles: true }));
        login.dispatchEvent(new Event('change', { bubbles: true }));
        pwd.value = ${JSON.stringify(password)};
        pwd.dispatchEvent(new Event('input', { bubbles: true }));
        pwd.dispatchEvent(new Event('change', { bubbles: true }));
        button.click();
      })()
    `,
    returnByValue: true,
  });
  return waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: !!localStorage.getItem('unexamine.accessToken') && text.includes(${JSON.stringify(accountName)}) && location.hash !== '#/login',
        hash: location.hash,
        accountVisible: text.includes(${JSON.stringify(accountName)}),
      };
    })())
  `, 12000);
}

async function capture(client, key) {
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return evaluateJson(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const doc = document.documentElement;
      const body = document.body;
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      const overflowX = Math.max(0, Math.ceil(scrollWidth - window.innerWidth));
      const rowCount = document.querySelectorAll('.runtime-table tbody tr').length;
      const detail = document.querySelector('.detail-panel');
      const printPreview = document.querySelector('[data-runtime-print-preview="true"]');
      const emptyState = document.querySelector('.runtime-empty-state');
      const loadingState = document.querySelector('.runtime-loading-state');
      const loadingText = text.includes('Loading...')
        || text.includes('\u6b63\u5728\u8bfb\u53d6')
        || text.includes('\u8bf7\u7a0d\u5019');
      const scripts = Array.from(document.scripts).map((script) => script.src).filter(Boolean);
      const blockers = [];
      if (!document.querySelector('.runtime-page')) blockers.push('runtime page missing');
      if (!document.querySelector('.runtime-table')) blockers.push('runtime table missing');
      if (!detail) blockers.push('detail panel missing');
      if (!text.includes(${JSON.stringify(recordValue)})) blockers.push('record value missing');
      if (emptyState) blockers.push('empty state rendered');
      if (loadingState || loadingText) blockers.push('loading state still visible');
      if (overflowX > 2) blockers.push('document horizontal overflow');
      if (!printPreview) blockers.push('runtime print preview missing');
      return {
        key: ${JSON.stringify(key)},
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        path: location.pathname,
        rowCount,
        hasRuntime: !!document.querySelector('.runtime-page'),
        hasTable: !!document.querySelector('.runtime-table'),
        hasDetail: !!detail,
        hasRecordValue: text.includes(${JSON.stringify(recordValue)}),
        hasPrintPreview: !!printPreview,
        hasEmptyState: !!emptyState,
        hasLoadingState: !!loadingState,
        loadingText,
        overflowX,
        viewport: { width: window.innerWidth, height: window.innerHeight },
        scripts,
        blockers,
      };
    })())
  `);
}

async function verifyRuntimeFirstNavigation(client, key, width, height) {
  await setViewport(client, width, height);
  await navigate(client, `${baseUrl}/systems/${systemId}/modules`);
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: location.hash === '#/systems/${systemId}/modules'
          && !!document.querySelector('.runtime-page')
          && !!document.querySelector('.runtime-table')
          && !!document.querySelector('.detail-panel')
          && text.includes(${JSON.stringify(recordValue)})
          && !document.querySelector('.runtime-empty-state')
          && !document.querySelector('.runtime-loading-state'),
        hash: location.hash,
        text: text.slice(0, 900),
      };
    })())
  `, 15000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.detail-panel .detail-head .inline-actions button'));
        const printButton = buttons.find((button) => button.textContent.includes('\u6253\u5370\u9884\u89c8')) || buttons[2];
        if (!printButton) throw new Error('print preview button missing');
        printButton.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `
    JSON.stringify((() => ({
      ok: !!document.querySelector('[data-runtime-print-preview="true"]') && (document.body.innerText || '').includes(${JSON.stringify(recordValue)}),
      text: (document.body.innerText || '').slice(0, 900),
    }))())
  `, 12000);
  return capture(client, key);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('DOM.enable');

  const loginState = await realLogin(client);
  const desktop = await verifyRuntimeFirstNavigation(client, 'desktop-direct-runtime-first-navigation', 1280, 720);
  const mobile = await verifyRuntimeFirstNavigation(client, 'mobile-direct-runtime-first-navigation', 390, 720);
  client.close();

  const results = [desktop, mobile];
  const failures = results.filter((item) => item.blockers.length > 0);
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-037',
    baseUrl,
    systemId,
    accountName,
    recordValue,
    generatedAt: new Date().toISOString(),
    loginState,
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'runtime-first-navigation-browser.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`Runtime first navigation failed: ${failures.map((item) => `${item.key}:${item.blockers.join('|')}`).join('; ')}`);
  }
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R37_BASE_URL = $BaseUrl
$env:R37_SYSTEM_ID = $script:TargetSystemId
$env:R37_ACCOUNT = $NormalLoginName
$env:R37_PASSWORD = $Password
$env:R37_RECORD_VALUE = $RecordValue
$env:R37_EVIDENCE_DIR = $EvidenceDir
$env:R37_CDP_PORT = [string]$debugPort

try {
    $nodeOutput = & $nodeExe $nodeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Node runtime first-navigation audit exited with code $LASTEXITCODE"
    }
} finally {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $nodeScript) {
        Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
    }
}

$SearchReadback = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $RecordValue
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @()
}
Assert-True -Condition ([int]$SearchReadback.page.total -eq 1) -Message "Normal readback should find exactly one record, got $($SearchReadback.page.total)."

$BrowserResultPath = Join-Path $EvidenceDir 'runtime-first-navigation-browser.json'
$BrowserResult = Get-Content -Raw $BrowserResultPath | ConvertFrom-Json
Assert-True -Condition ($BrowserResult.status -eq 'PASS') -Message "Browser runtime first-navigation audit failed: $(Get-Content -Raw $BrowserResultPath)"

$script:CleanupResult = Remove-CreatedSystems

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-037'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $script:TargetSystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    recordId = $RecordId
    recordValue = $RecordValue
    normalAccount = $NormalLoginName
    normalMemberId = $NormalMember.systemMemberId
    normalBindingId = $NormalBinding.bindingId
    runtimeRoleId = $RuntimeRole.roleId
    permissionVersion = $Permission.permissionVersion
    sceneId = $Scene.sceneId
    templateCode = $TemplateCode
    publishedPrintVersion = $PublishedTemplate.version
    forbiddenCreateStatus = $ForbiddenCreate.status
    apiReadbackTotal = $SearchReadback.page.total
    browserStatus = $BrowserResult.status
    browserResultCount = @($BrowserResult.results).Count
    browserOverflowCount = @($BrowserResult.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserResult.failures).Count
    assetScripts = @($BrowserResult.results | ForEach-Object { $_.scripts } | Select-Object -Unique)
    evidenceDir = $EvidenceDir
    browserResultPath = $BrowserResultPath
    cleanup = $script:CleanupResult
}

$resultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r37-runtime-first-navigation-result.json'
$result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $resultFile -Encoding UTF8

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r37-runtime-first-navigation-2026-06-30.md'
@"
# R37 Runtime First-Navigation List/Detail Usability Evidence

Status: PASS

Base URL: $BaseUrl

Task: REC-P0-037 Runtime first-navigation and list/detail usability hardening

Evidence:

- Machine result: $resultFile
- Browser result: $BrowserResultPath
- Screenshots: $EvidenceDir

Assertions:

- Normal member logs in through the deployed login form.
- Browser opens direct non-hash `/systems/$script:TargetSystemId/modules` on desktop and mobile.
- The first runtime navigation normalizes to `#/systems/$script:TargetSystemId/modules` and renders the runtime list, first row, detail panel, and published print preview without manual reload.
- The pre-created record $RecordValue is visible in list/detail/print preview on first navigation.
- No runtime empty state, loading state, or document horizontal overflow remains after first load.
- Normal-member API readback finds $($SearchReadback.page.total) matching record.
- Direct normal-member record create is rejected with HTTP $($ForbiddenCreate.status).
- Cleanup: $($script:CleanupResult -join ', ')
"@ | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$result | ConvertTo-Json -Depth 60
