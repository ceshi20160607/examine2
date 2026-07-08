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
        throw "Expected forbidden response for $Method $Path but request succeeded."
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 403) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            throw "Expected HTTP 403 for $Method $Path but got HTTP $status $body"
        }
        return [ordered]@{ status = $status; path = $Path }
    }
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
        maskRule = 'NONE'
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
                reason = 'recovery-R41 page designer cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-R41-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r41-page-home-component-usability'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r41-page-home-component-usability-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r41-page-home-component-usability-2026-06-30.md'
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
    systemName = "R41 Page Designer $script:Suffix"
    systemCode = "r41_page_$script:Suffix"
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
    reason = 'recovery-R41 admin setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R41 Readonly Member $script:Suffix"
    roleCode = "R41_READONLY_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R41 readonly runtime role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R41 Operations $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r41_request_$script:Suffix"
    name = "R41 Request $script:Suffix"
    status = 1
    description = 'Recovery R41 page designer module entry proof'
}
$ModuleId = [string]$Module.moduleId

$PublicField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'Public Name' -Type 'TEXT' -Required $true)
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'Secret Note' -Type 'TEXT')
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'amountValue' -Name 'Amount Value' -Type 'NUMBER')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R41 Default View'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$PublicField.fieldId, [string]$AmountField.fieldId)
    filterFieldIds = @([string]$PublicField.fieldId)
    sortFieldIds = @([string]$PublicField.fieldId)
}

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
        "$ModuleId.publicName" = "READABLE"
        "$ModuleId.secretNote" = "HIDDEN"
        "$ModuleId.amountValue" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-R41 module publish'
    idempotencyKey = "module-publish-R41-$script:Suffix"
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
    fieldValues = @{
        publicName = "R41 Visible Record $script:Suffix"
        secretNote = "R41 Hidden Secret $script:Suffix"
        amountValue = 3600
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R41_PAGE_DESIGNER_READONLY'
}

$PagePayload = @{
    pageCode = 'main'
    pageName = "R41 Runtime Main $script:Suffix"
    pageType = 'MODULE_LIST'
    route = "/systems/$SystemId/modules/$ModuleId"
    layoutMode = 'left-list-right-detail'
    components = @(
        @{ componentCode = 'toolbar'; componentType = 'SHORTCUT'; title = 'Page Actions'; dataSource = 'MODULE_ACTIONS'; sort = 10; visible = $true },
        @{ componentCode = 'form'; componentType = 'FORM'; title = 'Readonly Form'; dataSource = 'MODULE_FIELDS'; boundFieldCode = 'publicName'; sort = 20; visible = $true },
        @{ componentCode = 'secret'; componentType = 'DETAIL'; title = 'Hidden Secret'; dataSource = 'RECORD_DETAIL'; boundFieldCode = 'secretNote'; sort = 30; visible = $true },
        @{ componentCode = 'list'; componentType = 'LIST'; title = 'Runtime Records'; dataSource = 'RUNTIME_RECORDS'; sort = 40; visible = $true }
    )
    visibleRoleIds = @()
    changeReason = 'recovery-R41 module workflow page designer'
}

$SavedPage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders -Body $PagePayload
$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message "Page publish-check did not pass: $($PublishCheck | ConvertTo-Json -Depth 30 -Compress)"
$PublishedPage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-R41 page publish'
    idempotencyKey = "page-publish-R41-$script:Suffix"
}

$AdminSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/pages/main/schema?snapshot=published" -Headers $script:AdminHeaders
Assert-True -Condition (@($AdminSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 1) -Message 'Admin schema should include hidden-capable secret field.'

$NormalLoginName = "r41_page_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r41_page_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R41 Owned $script:Suffix"
    systemCode = "r41_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:CreatedSystemIds.Add($NormalOwnedSystemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R41 Runtime Member $script:Suffix"
    employeeNo = "R41M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "R41_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-R41 normal member readonly browser audit'
}

$NormalSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/pages/main/schema" -Headers $NormalHeaders
Assert-True -Condition ($NormalSchema.schemaVersion -eq $AdminSchema.schemaVersion) -Message 'Normal runtime schema version does not match admin published schema.'
Assert-True -Condition (@($NormalSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into normal runtime schema.'
Assert-True -Condition (@($NormalSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -eq 0) -Message 'Hidden component leaked into normal runtime schema.'
Assert-True -Condition (@($NormalSchema.fields | Where-Object { $_.readonly -eq $true }).Count -ge 2) -Message 'Normal runtime schema should mark readable fields readonly.'

$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        publicName = "R41 forbidden write $script:Suffix"
        secretNote = 'should not write'
        amountValue = 99
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R41_FORBIDDEN_CREATE'
}

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-R41-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$chromeArgs = @(
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-background-networking",
    "--remote-allow-origins=*",
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    "about:blank"
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

$nodeScript = Join-Path $env:TEMP "unexamine-R41-page-designer-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R41_BASE_URL;
const outDir = process.env.R41_EVIDENCE_DIR;
const port = process.env.R41_CDP_PORT;
const systemId = process.env.R41_SYSTEM_ID;
const moduleId = process.env.R41_MODULE_ID;
const moduleName = process.env.R41_MODULE_NAME;
const pageName = process.env.R41_PAGE_NAME;

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
  const result = await client.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}

async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await delay(1600);
}

async function setViewport(client, width, height) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: width <= 640,
  });
  await client.send('Emulation.setVisibleSize', { width, height });
}

async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/`);
  await client.send('Runtime.evaluate', {
    expression: `
      localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
      localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
      localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken || '')});
    `,
    returnByValue: true,
  });
  await client.send('Page.reload', { ignoreCache: true });
  await delay(1600);
}

async function clickByText(client, text) {
  return evaluate(client, `
    JSON.stringify((() => {
      const candidates = Array.from(document.querySelectorAll('button,a'));
      const target = candidates.find((el) => (el.innerText || '').trim() === ${JSON.stringify(text)});
      if (!target) return { clicked: false };
      target.click();
      return { clicked: true, text: target.innerText };
    })())
  `);
}

async function setInputValue(client, fieldCode, value) {
  return evaluate(client, `
    JSON.stringify((() => {
      const input = document.querySelector('input[data-field-code="' + ${JSON.stringify(fieldCode)} + '"]');
      if (!input) return { ok: false };
      input.value = ${JSON.stringify(value)};
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      return { ok: true, fieldCode: input.dataset.fieldCode, value: input.value };
    })())
  `);
}

async function waitForText(client, text, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const found = await evaluate(client, `
      JSON.stringify({ found: (document.body.innerText || '').includes(${JSON.stringify(text)}), text: (document.body.innerText || '').slice(0, 800) })
    `);
    if (found.found) return found;
    await delay(400);
  }
  return evaluate(client, `JSON.stringify({ found: false, text: (document.body.innerText || '').slice(0, 1200) })`);
}

async function screenshot(client, fileName) {
  const image = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(image.data, 'base64'));
}

async function inspect(client, key) {
  return evaluate(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const doc = document.documentElement;
      const body = document.body;
      const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
      const fields = Array.from(document.querySelectorAll('.edit-panel input[data-field-code]')).map((input) => ({
        fieldCode: input.dataset.fieldCode,
        disabled: input.disabled,
        readonly: input.dataset.readonly === 'true',
        value: input.value || '',
      }));
      const modulePanels = Array.from(document.querySelectorAll('.module-work-panel,.module-page-designer-slot,.page-designer-panel')).map((el) => (el.innerText || '').slice(0, 400));
      const assetScripts = Array.from(document.scripts).map((script) => script.src).filter((src) => src.includes('/assets/'));
      return {
        key: ${JSON.stringify(key)},
        hash: location.hash,
        overflowX,
        textPreview: text.slice(0, 1200),
        assetScripts,
        hasHomeOverviewMarker: !!document.querySelector('[data-home-overview="true"]'),
        hasHomeOperationsMarker: !!document.querySelector('[data-home-operations="true"]'),
        hasHomeConfigMarker: !!document.querySelector('[data-home-config-panel="true"]'),
        hasModulePageDesignerMarker: !!document.querySelector('[data-module-page-designer="true"]'),
        hasRuntimeSchemaFormMarker: !!document.querySelector('[data-runtime-schema-form="true"]'),
        hasRuntimeValidationErrors: !!document.querySelector('[data-runtime-validation-errors="true"]'),
        invalidFieldCodes: Array.from(document.querySelectorAll('[data-field-error]')).map((el) => el.dataset.fieldError).filter(Boolean),
        hasEnglishReadonlyReason: text.includes('Current role can read this field but cannot write it.'),
        hasModulePageDesigner: text.includes('\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668'),
        hasHomeAndPageDesignerTitle: text.includes('\u9996\u9875\u4e0e\u9875\u9762\u8bbe\u8ba1'),
        hasHomeConfigTitle: text.includes('\u9996\u9875\u914d\u7f6e'),
        hasModuleName: text.includes(${JSON.stringify(moduleName)}),
        hasPageName: text.includes(${JSON.stringify(pageName)}),
        hasSchemaReadSuccess: text.includes('Schema \u8bfb\u53d6\u6210\u529f'),
        hasSecretText: text.includes('Secret Note') || text.includes('secretNote') || text.includes('Hidden Secret'),
        fields,
        modulePanels,
      };
    })())
  `);
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');

  const roles = {
    admin: {
      accessToken: process.env.R41_ADMIN_TOKEN,
      refreshToken: process.env.R41_ADMIN_REFRESH,
      accountId: process.env.R41_ADMIN_ACCOUNT_ID || 'admin',
    },
    normal: {
      accessToken: process.env.R41_NORMAL_TOKEN,
      refreshToken: process.env.R41_NORMAL_REFRESH,
      accountId: process.env.R41_NORMAL_ACCOUNT_ID || 'normal',
    },
  };

  const result = { results: [] };

  await setViewport(client, 1280, 720);
  await setStorage(client, roles.admin);
  await navigate(client, `${baseUrl}/#/systems/${systemId}/dashboard`);
  await waitForText(client, '\u4eca\u65e5\u6982\u89c8');
  await screenshot(client, 'desktop-admin-system-home.png');
  result.results.push(await inspect(client, 'desktop-admin-system-home'));

  await navigate(client, `${baseUrl}/#/systems/${systemId}/modules`);
  await waitForText(client, moduleName);
  const clickedAdminCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedAdminCreate.clicked) throw new Error('Could not click admin runtime create button.');
  await waitForText(client, 'Record Form');
  const clickedEmptySave = await clickByText(client, '\u4fdd\u5b58\u8bb0\u5f55');
  if (!clickedEmptySave.clicked) throw new Error('Could not click admin runtime save button.');
  await waitForText(client, '\u8868\u5355\u6821\u9a8c\u672a\u901a\u8fc7');
  await screenshot(client, 'desktop-admin-runtime-validation.png');
  result.results.push(await inspect(client, 'desktop-admin-runtime-validation'));
  const browserTitle = `R41 Browser Created ${Date.now()}`;
  const filledTitle = await setInputValue(client, 'title', browserTitle);
  const filledStatus = await setInputValue(client, 'status', 'DRAFT');
  const filledRequired = await setInputValue(client, 'publicName', browserTitle);
  if (!filledTitle.ok || !filledStatus.ok || !filledRequired.ok) throw new Error('Could not fill required runtime fields.');
  const clickedFilledSave = await clickByText(client, '\u4fdd\u5b58\u8bb0\u5f55');
  if (!clickedFilledSave.clicked) throw new Error('Could not click admin runtime save button after filling required field.');
  await waitForText(client, '\u8bb0\u5f55\u5df2\u4fdd\u5b58');
  await screenshot(client, 'desktop-admin-runtime-validation-saved.png');
  result.results.push(await inspect(client, 'desktop-admin-runtime-validation-saved'));

  await navigate(client, `${baseUrl}/#/systems/${systemId}/admin`);
  await waitForText(client, '\u7cfb\u7edf\u521d\u59cb\u5316\u6e05\u5355');
  await delay(600);
  const clickedModule = await clickByText(client, '\u6a21\u5757\u7ba1\u7406');
  if (!clickedModule.clicked) throw new Error('Could not click module management sidebar.');
  const moduleDesignerVisible = await waitForText(client, '\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668');
  if (!moduleDesignerVisible.found) throw new Error(`Module page designer did not appear after module management click: ${moduleDesignerVisible.text}`);
  const clickedSchema = await clickByText(client, 'Schema \u9884\u89c8');
  if (!clickedSchema.clicked) throw new Error('Could not click Schema preview from module management.');
  const schemaVisible = await waitForText(client, 'Schema \u8bfb\u53d6\u6210\u529f');
  if (!schemaVisible.found) throw new Error(`Schema preview result did not appear in module management: ${schemaVisible.text}`);
  await screenshot(client, 'desktop-admin-module-page-designer.png');
  result.results.push(await inspect(client, 'desktop-admin-module-page-designer'));

  const clickedDashboard = await clickByText(client, '\u4eea\u8868\u76d8\u7ba1\u7406');
  if (!clickedDashboard.clicked) throw new Error('Could not click dashboard config sidebar.');
  await waitForText(client, '\u9996\u9875\u914d\u7f6e');
  await screenshot(client, 'desktop-admin-dashboard-config.png');
  result.results.push(await inspect(client, 'desktop-admin-dashboard-config'));

  await setViewport(client, 390, 720);
  await navigate(client, `${baseUrl}/#/systems/${systemId}/admin`);
  await waitForText(client, '\u7cfb\u7edf\u521d\u59cb\u5316\u6e05\u5355');
  await delay(600);
  const clickedMobileModule = await clickByText(client, '\u6a21\u5757\u7ba1\u7406');
  if (!clickedMobileModule.clicked) throw new Error('Could not click mobile module management sidebar.');
  const mobileModuleDesignerVisible = await waitForText(client, '\u6a21\u5757\u9875\u9762\u8bbe\u8ba1\u5668');
  if (!mobileModuleDesignerVisible.found) throw new Error(`Mobile module page designer did not appear: ${mobileModuleDesignerVisible.text}`);
  await screenshot(client, 'mobile-admin-module-page-designer.png');
  result.results.push(await inspect(client, 'mobile-admin-module-page-designer'));

  await setStorage(client, roles.normal);
  await setViewport(client, 1280, 720);
  await navigate(client, `${baseUrl}/#/systems/${systemId}/modules`);
  await waitForText(client, moduleName);
  const clickedCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedCreate.clicked) throw new Error('Could not click normal runtime create button.');
  await waitForText(client, '\u53ea\u8bfb');
  await screenshot(client, 'desktop-normal-runtime-readonly-form.png');
  result.results.push(await inspect(client, 'desktop-normal-runtime-readonly-form'));

  await setViewport(client, 390, 720);
  await navigate(client, `${baseUrl}/#/systems/${systemId}/modules`);
  await waitForText(client, moduleName);
  const clickedMobileCreate = await clickByText(client, '\u65b0\u5efa\u8bb0\u5f55');
  if (!clickedMobileCreate.clicked) throw new Error('Could not click mobile normal runtime create button.');
  await waitForText(client, '\u53ea\u8bfb');
  await screenshot(client, 'mobile-normal-runtime-readonly-form.png');
  result.results.push(await inspect(client, 'mobile-normal-runtime-readonly-form'));

  client.close();
  return result;
}

run().then((result) => {
  console.log(JSON.stringify(result, null, 2));
}).catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$env:R41_BASE_URL = $BaseUrl
$env:R41_EVIDENCE_DIR = $EvidenceDir
$env:R41_CDP_PORT = [string]$debugPort
$env:R41_SYSTEM_ID = $SystemId
$env:R41_MODULE_ID = $ModuleId
$env:R41_MODULE_NAME = $Module.name
$env:R41_PAGE_NAME = $PagePayload.pageName
$env:R41_ADMIN_TOKEN = $AdminLogin.accessToken
$env:R41_ADMIN_REFRESH = $AdminLogin.refreshToken
$env:R41_ADMIN_ACCOUNT_ID = $AdminLogin.profile.accountId
$env:R41_NORMAL_TOKEN = $NormalLogin.accessToken
$env:R41_NORMAL_REFRESH = $NormalLogin.refreshToken
$env:R41_NORMAL_ACCOUNT_ID = $NormalLogin.profile.accountId

$nodeOutput = & node $nodeScript
$nodeExit = $LASTEXITCODE
if ($nodeExit -ne 0) {
    throw "R41 browser audit failed with exit code $nodeExit. Output: $nodeOutput"
}
$BrowserAudit = $nodeOutput | ConvertFrom-Json
$BrowserAuditPath = Join-Path $EvidenceDir 'page-designer-browser-audit.json'
$BrowserAudit | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $BrowserAuditPath -Encoding UTF8

$AdminHomeResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-admin-system-home' }) | Select-Object -First 1
$AdminValidationResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-admin-runtime-validation' }) | Select-Object -First 1
$AdminValidationSavedResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-admin-runtime-validation-saved' }) | Select-Object -First 1
$AdminModuleResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-admin-module-page-designer' }) | Select-Object -First 1
$DashboardResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-admin-dashboard-config' }) | Select-Object -First 1
$NormalReadonlyResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'desktop-normal-runtime-readonly-form' }) | Select-Object -First 1
$MobileReadonlyResult = @($BrowserAudit.results | Where-Object { $_.key -eq 'mobile-normal-runtime-readonly-form' }) | Select-Object -First 1

Assert-True -Condition ($AdminHomeResult.hasHomeOverviewMarker -eq $true -and $AdminHomeResult.hasHomeOperationsMarker -eq $true) `
    -Message 'System dashboard did not render the configured home overview and operations panels.'
Assert-True -Condition ($AdminValidationResult.hasRuntimeSchemaFormMarker -eq $true -and $AdminValidationResult.hasRuntimeValidationErrors -eq $true -and @($AdminValidationResult.invalidFieldCodes | Where-Object { $_ -eq 'publicName' }).Count -ge 1) `
    -Message 'Admin runtime form did not show schema form validation for required publicName.'
Assert-True -Condition ($AdminValidationSavedResult.hasRuntimeValidationErrors -eq $false) `
    -Message 'Admin runtime validation errors remained after filling the required field and saving.'
Assert-True -Condition ($AdminModuleResult.hasModulePageDesigner -eq $true -and $AdminModuleResult.hasModuleName -eq $true -and $AdminModuleResult.hasPageName -eq $true -and $AdminModuleResult.hasSchemaReadSuccess -eq $true) `
    -Message 'Admin module workflow did not show page designer, selected module, page, and schema preview together.'
Assert-True -Condition ($DashboardResult.hasHomeConfigTitle -eq $true -and $DashboardResult.hasHomeConfigMarker -eq $true -and $DashboardResult.hasModulePageDesigner -eq $false -and $DashboardResult.hasHomeAndPageDesignerTitle -eq $false) `
    -Message 'Dashboard config still mixes module page designer wording into the home configuration surface.'
Assert-True -Condition ($AdminModuleResult.hasModulePageDesignerMarker -eq $true) -Message 'Module page designer marker was not present in the admin module workflow.'
Assert-True -Condition (@($NormalReadonlyResult.fields).Count -ge 2 -and $NormalReadonlyResult.hasRuntimeSchemaFormMarker -eq $true) -Message 'Normal runtime readonly form did not render expected readable fields.'
Assert-True -Condition (@($NormalReadonlyResult.fields | Where-Object { $_.disabled -eq $false -or $_.readonly -eq $false }).Count -eq 0) `
    -Message 'Normal runtime form has writable fields even though role is readonly.'
Assert-True -Condition ($NormalReadonlyResult.hasEnglishReadonlyReason -eq $false -and $MobileReadonlyResult.hasEnglishReadonlyReason -eq $false) `
    -Message 'Readonly disabled reason still contains English technical copy.'
Assert-True -Condition ($NormalReadonlyResult.hasSecretText -eq $false) -Message 'Hidden secret field/component leaked in normal runtime desktop form.'
Assert-True -Condition ($MobileReadonlyResult.overflowX -eq 0) -Message "Mobile readonly form overflowX was $($MobileReadonlyResult.overflowX)."
Assert-True -Condition (@($BrowserAudit.results | Where-Object { $_.overflowX -gt 0 }).Count -eq 0) -Message 'One or more FRC-1C browser routes overflow horizontally.'

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-041'
    frc = 'FRC-1C Page/home and component usability closure'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    pageCode = 'main'
    savedPageStatus = $SavedPage.publishStatus
    publishCheckPassed = $PublishCheck.passed
    publishedVersion = $PublishedPage.version
    adminSchemaVersion = $AdminSchema.schemaVersion
    normalSchemaVersion = $NormalSchema.schemaVersion
    normalReadonlyCount = @($NormalSchema.fields | Where-Object { $_.readonly -eq $true }).Count
    hiddenFieldLeaked = @($NormalSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    hiddenComponentLeaked = @($NormalSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -gt 0
    forbiddenCreateStatus = $ForbiddenCreate.status
    adminHomeMarkers = [ordered]@{
        overview = $AdminHomeResult.hasHomeOverviewMarker
        operations = $AdminHomeResult.hasHomeOperationsMarker
    }
    adminValidation = [ordered]@{
        schemaForm = $AdminValidationResult.hasRuntimeSchemaFormMarker
        validationErrors = $AdminValidationResult.hasRuntimeValidationErrors
        invalidFieldCodes = @($AdminValidationResult.invalidFieldCodes)
        savedValidationErrors = $AdminValidationSavedResult.hasRuntimeValidationErrors
    }
    assetScripts = @($AdminModuleResult.assetScripts)
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 0 }).Count
    evidenceDir = $EvidenceDir
    browserAuditPath = $BrowserAuditPath
    cleanup = $script:CleanupResult
}
$Result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

@"
# R41 Page Home And Component Usability

Status: PASS

Task: REC-P0-041 / FRC-1C

Base URL: $BaseUrl

Evidence:

- Machine result: $ResultFile
- Browser audit: $BrowserAuditPath
- Screenshots: $EvidenceDir

Assertions:

- System admin configures and publishes module page `main` for module `$ModuleId`.
- System dashboard renders home overview and operations panels from the real runtime shell.
- Runtime schema form shows required-field validation for `publicName`, focuses a real field, and clears validation after a successful save.
- Page publish-check passed and published version is `$($PublishedPage.version)`.
- Module management shows `妯″潡椤甸潰璁捐鍣╜, selected module, selected page, and schema preview together.
- Dashboard/home configuration no longer contains the module page designer surface.
- Normal member runtime schema reads the same version, hides `secretNote`, removes the hidden component, and marks readable fields readonly.
- Normal member direct create is rejected with HTTP $($ForbiddenCreate.status).
- Desktop and mobile browser readonly form screenshots have no horizontal overflow.
- Cleanup: $($script:CleanupResult -join ', ')
"@ | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$Result | ConvertTo-Json -Depth 80
if ($Result.status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}

