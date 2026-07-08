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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
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

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -eq 403) {
            return @{ status = $status }
        }
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        throw "Expected HTTP 403 but got HTTP ${status}: $($reader.ReadToEnd())"
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
            duplicateKey = $Code
            desensitizeMode = 'PERMISSION'
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
                reason = 'recovery-r38 module lifecycle cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r38-$script:Suffix-$systemId"
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
$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r38-module-lifecycle-admin-table'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r38-module-lifecycle-admin-table-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r38-module-lifecycle-admin-table-2026-06-30.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$Password = 'Aa123456!'
$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R38 Module Lifecycle $script:Suffix"
    systemCode = "r38_module_$script:Suffix"
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
    reason = 'recovery-r38 admin setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R38 Runtime Member $script:Suffix"
    roleCode = "R38_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R38 runtime read-only role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R38 Admin Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r38_request_$script:Suffix"
    name = "R38 Request $script:Suffix"
    status = 1
    description = 'Recovery R38 module lifecycle admin table proof'
}
$ModuleId = [string]$Module.moduleId

$UpdatedModule = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$SystemId/modules/$ModuleId" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r38_request_updated_$script:Suffix"
    name = "R38 Request Updated $script:Suffix"
    status = 1
    description = 'Recovery R38 lifecycle update readback'
}
Assert-True -Condition ($UpdatedModule.name -like 'R38 Request Updated*') -Message 'Module lifecycle update did not read back updated name.'

$TitleCode = "r38_title_$($script:Suffix.Replace('-', '_'))"
$AmountCode = "r38_amount_$($script:Suffix.Replace('-', '_'))"
$StatusCode = "r38_status_$($script:Suffix.Replace('-', '_'))"
$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $TitleCode -Name 'R38 Title' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $AmountCode -Name 'R38 Amount' -Type 'NUMBER')
$StatusField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $StatusCode -Name 'R38 Status Text' -Type 'TEXT')

$Action = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/actions" -Headers $script:AdminHeaders -Body @{
    actionCode = 'record.audit'
    actionName = 'Audit Record'
    actionType = 'ROW'
    position = 'ROW'
    selectionRule = @{
        selectionMode = 'SINGLE'
        minSelected = 1
        maxSelected = 1
        requiredStatuses = @()
        sameTenantRequired = $true
        forbiddenReason = 'Selection is not eligible.'
    }
    resultContract = @{
        resultType = 'SYNC_RESULT'
        returnsAuditLog = $true
        returnsAsyncTask = $false
        resultDrawer = 'actionResultDrawer'
        traceField = 'traceId'
        userVisibleStates = @('SUCCESS', 'FAILED')
    }
    enabled = $true
}
$Actions = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/actions" -Headers $script:AdminHeaders
Assert-True -Condition (@($Actions | Where-Object { $_.actionCode -eq 'record.audit' }).Count -eq 1) -Message 'Module action readback missing.'

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'admin_default'
    sceneName = 'R38 Admin Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$AmountField.fieldId, [string]$StatusField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$StatusField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}
$Schema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/list-schema?sceneId=$($Scene.sceneId)" -Headers $script:AdminHeaders
Assert-True -Condition (@($Schema.columns).Count -ge 3 -and @($Schema.filters).Count -ge 2 -and @($Schema.sorters).Count -ge 1) -Message 'Admin list schema did not include expected columns, filters, and sorters.'

$ImportExport = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/import-export-config" -Headers $script:AdminHeaders -Body @{
    importSupported = $true
    exportSupported = $true
    importTemplates = @(@{
        templateCode = 'r38_import_default'
        templateName = 'R38 Import Default'
        fileFormat = 'XLSX'
        defaultFieldCodes = @($TitleCode, $AmountCode, $StatusCode)
        requiredFieldCodes = @($TitleCode)
        desensitizeMode = 'PERMISSION'
    })
    exportTemplates = @(@{
        templateCode = 'r38_export_default'
        templateName = 'R38 Export Default'
        fileFormat = 'XLSX'
        defaultFieldCodes = @($TitleCode, $AmountCode, $StatusCode)
        desensitizeMode = 'PERMISSION'
    })
    fieldMappings = @(
        @{ sourceColumn = 'R38 Title'; fieldCode = $TitleCode; required = $true; transformRule = 'DIRECT' },
        @{ sourceColumn = 'R38 Amount'; fieldCode = $AmountCode; required = $false; transformRule = 'DIRECT' },
        @{ sourceColumn = 'R38 Status Text'; fieldCode = $StatusCode; required = $false; transformRule = 'DIRECT' }
    )
    duplicateStrategies = @('SKIP_DUPLICATE', 'UPDATE_EXISTING')
    supportedFormats = @('XLSX', 'CSV')
}
$ImportExportReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/import-export-config" -Headers $script:AdminHeaders
Assert-True -Condition ($ImportExportReadback.importSupported -and $ImportExportReadback.exportSupported -and @($ImportExportReadback.fieldMappings).Count -ge 3) -Message 'Import/export config readback failed.'

$Check = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish-check" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r38 module publish check'
    idempotencyKey = "module-check-r38-$script:Suffix"
}
Assert-True -Condition ($Check.passed -eq $true) -Message "Module publish check failed: $($Check | ConvertTo-Json -Depth 20 -Compress)"

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r38 module publish'
    idempotencyKey = "module-publish-r38-$script:Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Published.version)) -Message 'Publish did not return a version.'

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.read" = $true
        "record.create" = $false
        "record.edit" = $false
        "record.delete" = $false
    }
    fieldPermissions = @{
        "$ModuleId.$TitleCode" = "READABLE"
        "$ModuleId.$AmountCode" = "READABLE"
        "$ModuleId.$StatusCode" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$NormalLoginName = "r38_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r38_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R38 Owned $script:Suffix"
    systemCode = "r38_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R38 Runtime Member $script:Suffix"
    employeeNo = "R38NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r38_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r38 normal switch precheck'
}
$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{ $TitleCode = 'R38 forbidden write' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R38_FORBIDDEN_WRITE'
}
$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/actions" -Headers $NormalHeaders

$Rollback = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/rollback" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r38 module rollback'
    idempotencyKey = "module-rollback-r38-$script:Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$Rollback.result)) -Message 'Rollback did not return a result.'

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r38-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$chromeArgs = @(
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    'about:blank'
)
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 2

$env:R38_BASE_URL = $BaseUrl
$env:R38_CDP_PORT = [string]$debugPort
$env:R38_EVIDENCE_DIR = $EvidenceDir
$env:R38_LOGIN_NAME = 'admin'
$env:R38_PASSWORD = '123123aa'
$env:R38_SYSTEM_ID = $SystemId
$env:R38_MODULE_NAME = [string]$UpdatedModule.name

$browserScript = @'
const fs = require('fs');
const path = require('path');
const baseUrl = process.env.R38_BASE_URL;
const port = process.env.R38_CDP_PORT;
const outDir = process.env.R38_EVIDENCE_DIR;
const loginName = process.env.R38_LOGIN_NAME;
const password = process.env.R38_PASSWORD;
const systemId = process.env.R38_SYSTEM_ID;
const moduleName = process.env.R38_MODULE_NAME;

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
  return response.json();
}
async function newTarget() {
  try { return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' }); }
  catch { const targets = await cdpJson('/json/list'); return targets[0]; }
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
          eventWaiters.set(eventName, (payload) => { clearTimeout(timer); res(payload); });
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
async function waitUntil(client, expression, timeoutMs = 15000) {
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
        login.value = ${JSON.stringify(loginName)};
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
  return waitUntil(client, `JSON.stringify({ ok: !!localStorage.getItem('unexamine.accessToken') && location.hash !== '#/login', hash: location.hash })`, 12000);
}
async function verifyAdmin(client, key, width, height) {
  await setViewport(client, width, height);
  await navigate(client, `${baseUrl}/systems/${systemId}/admin/module-config`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar'), text: document.body.innerText.slice(0, 500), hash: location.hash })`, 15000);
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: !!document.querySelector('[data-admin-module-table="true"]')
          && !!document.querySelector('[data-module-work-tabs="true"]')
          && !!document.querySelector('[data-module-lifecycle-panel]')
          && text.includes(${JSON.stringify(moduleName)}),
        text: text.slice(0, 900),
      };
    })())
  `, 15000);
  await evaluateJson(client, `JSON.stringify((() => {
    const button = document.querySelector('[data-module-work-tab="scene"]');
    if (!button) throw new Error('scene tab missing');
    button.click();
    return { ok: true };
  })())`);
  await waitUntil(client, `
    JSON.stringify((() => ({
      ok: document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') === 'scene'
        && !!document.querySelector('[data-module-scene-config]')
        && !!document.querySelector('[data-module-import-export]')
    }))())
  `, 15000);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return evaluateJson(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const doc = document.documentElement;
      const body = document.body;
      const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
      const scripts = Array.from(document.scripts).map((script) => script.src).filter(Boolean);
      const blockers = [];
      if (!document.querySelector('[data-admin-module-table="true"]')) blockers.push('admin module table missing');
      if (!document.querySelector('[data-module-work-tabs="true"]')) blockers.push('module task tabs missing');
      if (document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') !== 'scene') blockers.push('scene tab not active after click');
      if (!document.querySelector('[data-module-scene-config]')) blockers.push('module scene config missing');
      if (!document.querySelector('[data-module-import-export]')) blockers.push('module import/export config missing');
      if (!text.includes(${JSON.stringify(moduleName)})) blockers.push('updated module name missing');
      if (overflowX > 2) blockers.push('document horizontal overflow');
      return {
        key: ${JSON.stringify(key)},
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        overflowX,
        hasAdminTable: !!document.querySelector('[data-admin-module-table="true"]'),
        hasModuleTaskTabs: !!document.querySelector('[data-module-work-tabs="true"]'),
        activeModuleTask: document.querySelector('[data-module-work-active-tab]')?.getAttribute('data-module-work-active-tab') || '',
        hasSceneConfig: !!document.querySelector('[data-module-scene-config]'),
        hasImportExport: !!document.querySelector('[data-module-import-export]'),
        moduleNameVisible: text.includes(${JSON.stringify(moduleName)}),
        scripts,
        blockers,
      };
    })())
  `);
}
async function run() {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await realLogin(client);
  const results = [];
  results.push(await verifyAdmin(client, 'desktop-admin-module-lifecycle', 1440, 900));
  results.push(await verifyAdmin(client, 'mobile-admin-module-lifecycle', 390, 760));
  client.close();
  return { status: 'PASS', results };
}
run().then((result) => {
  console.log(JSON.stringify(result));
}).catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@

$BrowserJson = $browserScript | & 'D:\dev\nodejs24\node.exe' | Out-String
$BrowserAudit = $BrowserJson | ConvertFrom-Json
Assert-True -Condition ($BrowserAudit.status -eq 'PASS') -Message "Browser audit failed: $BrowserJson"
Assert-True -Condition (@($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count -eq 0) -Message "Browser audit blockers: $BrowserJson"

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    updatedModuleName = $UpdatedModule.name
    actionCode = $Action.actionCode
    sceneId = $Scene.sceneId
    schemaColumnCount = @($Schema.columns).Count
    schemaFilterCount = @($Schema.filters).Count
    schemaSorterCount = @($Schema.sorters).Count
    importSupported = [bool]$ImportExport.importSupported
    exportSupported = [bool]$ImportExport.exportSupported
    importExportMappingCount = @($ImportExportReadback.fieldMappings).Count
    publishCheckPassed = [bool]$Check.passed
    publishedVersion = $Published.version
    rollbackResult = $Rollback.result
    forbiddenCreateStatus = $ForbiddenCreate.status
    forbiddenAdminStatus = $ForbiddenAdmin.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    deployedScripts = @($BrowserAudit.results | Select-Object -First 1).scripts
    cleanup = $script:CleanupResult
}

$Result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$Summary = @(
    '# R38 Module Lifecycle And Admin Table Evidence',
    '',
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId / $($UpdatedModule.name)",
    "- Admin table browser results: $($Result.browserResultCount), overflow: $($Result.browserOverflowCount), blockers: $($Result.browserBlockerCount)",
    "- API readback: columns=$($Result.schemaColumnCount), filters=$($Result.schemaFilterCount), sorters=$($Result.schemaSorterCount), mappings=$($Result.importExportMappingCount)",
    "- Publish check: $($Result.publishCheckPassed), publishedVersion=$($Result.publishedVersion), rollback=$($Result.rollbackResult)",
    "- Permission negatives: forbiddenCreate=$($Result.forbiddenCreateStatus), forbiddenAdmin=$($Result.forbiddenAdminStatus)",
    "- Cleanup: $($script:CleanupResult -join ', ')",
    '',
    'This is engineering evidence only. Final user signoff remains separate.'
)
$Summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$Result | ConvertTo-Json -Depth 40
