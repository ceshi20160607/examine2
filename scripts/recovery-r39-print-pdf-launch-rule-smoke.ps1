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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 40 -Compress)"
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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
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
                reason = 'recovery-r39 print launch cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r39-$script:Suffix-$systemId"
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
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r39-print-pdf-launch-rule'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r39-print-pdf-launch-rule-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r39-print-pdf-launch-rule-2026-06-30.md'
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
    systemName = "R39 Print Launch $script:Suffix"
    systemCode = "r39_print_$script:Suffix"
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
    reason = 'recovery-r39 admin setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R39 Runtime Member $script:Suffix"
    roleCode = "R39_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R39 runtime read-only role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R39 Print Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r39_invoice_$script:Suffix"
    name = "R39 Invoice $script:Suffix"
    status = 1
    description = 'Recovery R39 print fidelity module'
}
$ModuleId = [string]$Module.moduleId

$NameCode = "invoiceName_$($script:Suffix.Replace('-', '_'))"
$AmountCode = "invoiceAmount_$($script:Suffix.Replace('-', '_'))"
$OwnerCode = "ownerDept_$($script:Suffix.Replace('-', '_'))"
$NameField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $NameCode -Name 'Invoice Name' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $AmountCode -Name 'Invoice Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $OwnerCode -Name 'Owner Dept' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R39 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$NameField.fieldId, [string]$AmountField.fieldId, [string]$OwnerField.fieldId)
    filterFieldIds = @([string]$NameField.fieldId, [string]$AmountField.fieldId)
    sortFieldIds = @([string]$NameField.fieldId)
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
        "$ModuleId.$NameCode" = "READABLE"
        "$ModuleId.$AmountCode" = "READABLE"
        "$ModuleId.$OwnerCode" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r39 module publish'
    idempotencyKey = "module-publish-r39-$script:Suffix"
}

$TemplateCode = "print_r39_$script:Suffix"
$FullTemplate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    templateName = "R39 Invoice Print $script:Suffix"
    version = "draft_$script:Suffix"
    status = 1
    defaultTemplate = $true
    visibleRoleIds = @()
    boundFieldCodes = @($NameCode, $AmountCode, $OwnerCode)
    detailTableFieldCodes = @($NameCode, $AmountCode)
    signatureLabels = @('Prepared by', 'Approved by')
    headerText = 'R39 Invoice Print Header'
    footerText = 'R39 Print Footer'
    previewFileId = "preview_r39_$script:Suffix"
    pageSetup = @{
        paper = 'A4'
        orientation = 'LANDSCAPE'
        marginTop = '12mm'
        marginRight = '10mm'
        marginBottom = '12mm'
        marginLeft = '10mm'
        repeatHeader = $true
        repeatFooter = $true
        pageBreakPolicy = 'AVOID_SECTION_BREAK'
    }
}
Assert-True -Condition ($FullTemplate.publishStatus -eq 'DRAFT' -and $FullTemplate.pageSetup.orientation -eq 'LANDSCAPE') -Message 'Full print template page setup did not save.'

$WarningTemplateCode = "print_r39_warn_$script:Suffix"
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $script:AdminHeaders -Body @{
    templateCode = $WarningTemplateCode
    templateName = "R39 Warning Print $script:Suffix"
    version = "draft_warn_$script:Suffix"
    status = 1
    defaultTemplate = $false
    visibleRoleIds = @()
    boundFieldCodes = @($NameCode)
    detailTableFieldCodes = @()
    signatureLabels = @()
    headerText = 'R39 Warning Header'
    footerText = 'R39 Warning Footer'
    pageSetup = @{ paper = 'A4'; orientation = 'PORTRAIT' }
}
$WarningCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$WarningTemplateCode/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($WarningCheck.passed -eq $true -and @($WarningCheck.warningItems | Where-Object { $_.itemCode -eq 'PRINT_DETAIL_TABLE_EMPTY' }).Count -eq 1) -Message 'Launch-rule warning split did not report missing detail table.'
Assert-True -Condition (@($WarningCheck.warningItems | Where-Object { $_.itemCode -eq 'PRINT_SIGNATURE_EMPTY' }).Count -eq 1) -Message 'Launch-rule warning split did not report missing signature area.'

$NormalLoginName = "r39_print_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r39_print_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R39 Owned $script:Suffix"
    systemCode = "r39_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R39 Print Member $script:Suffix"
    employeeNo = "R39PM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r39_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r39 normal runtime switch'
}

$RecordValue = "R39 Runtime Invoice $script:Suffix"
$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
    fieldValues = @{
        $NameCode = $RecordValue
        $AmountCode = 39800
        $OwnerCode = 'Finance Ops'
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R39_PRINT_LAUNCH_SMOKE'
}
$RecordId = [string]$Record.recordId

$DraftDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-preview" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}

$Check = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($Check.passed -eq $true -and @($Check.impactRefs | Where-Object { $_.objectType -eq 'PRINT_EXPORT' }).Count -eq 1) -Message 'Full print template publish-check did not include PRINT_EXPORT impact.'

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r39 print launch publish'
    idempotencyKey = "print-publish-r39-$script:Suffix"
}
Assert-True -Condition ($Published.result -eq 'PUBLISHED_PRINT_TEMPLATE' -and -not [string]::IsNullOrWhiteSpace($Published.version)) -Message 'Print template publish did not return a version.'

$RuntimePreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-preview" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}
$RuntimePreviewJson = $RuntimePreview | ConvertTo-Json -Depth 80 -Compress
Assert-True -Condition ($RuntimePreview.publishStatus -eq 'PUBLISHED' -and $RuntimePreview.version -eq $Published.version) -Message 'Runtime preview did not use the published template version.'
Assert-True -Condition ($RuntimePreviewJson -match [regex]::Escape($RecordValue) -and $RuntimePreview.pageSetup.orientation -eq 'LANDSCAPE') -Message 'Runtime preview did not merge record value or page setup.'

$RuntimeExport = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-export" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}
$ExportHtml = [string]$RuntimeExport.exportMeta.html
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$RuntimeExport.exportFileId)) -Message 'Runtime export did not return exportFileId.'
Assert-True -Condition ($RuntimeExport.exportMeta.format -eq 'HTML_PRINT') -Message 'Runtime export did not report HTML_PRINT format.'
Assert-True -Condition ($RuntimeExport.exportMeta.contentType -eq 'text/html; charset=utf-8') -Message 'Runtime export contentType is not text/html.'
Assert-True -Condition ($RuntimeExport.exportMeta.printCssReady -eq $true -and $RuntimeExport.exportMeta.paginationReady -eq $true) -Message 'Runtime export did not report print CSS and pagination readiness.'
Assert-True -Condition ($ExportHtml -match '@page' -and $ExportHtml -match '@media print' -and $ExportHtml -match 'page-break-inside:avoid' -and $ExportHtml -match 'thead\{display:table-header-group') -Message 'Export HTML is missing print/pagination CSS.'
Assert-True -Condition ($ExportHtml -match [regex]::Escape($RecordValue) -and $ExportHtml -match 'size:A4 landscape;margin:12mm 10mm 12mm 10mm') -Message 'Export HTML did not include record value or expected page setup CSS.'

$NormalAdminDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $NormalHeaders -Body @{
    templateCode = "illegal_$script:Suffix"
    templateName = 'Normal Member Illegal Template'
    boundFieldCodes = @($NameCode)
}

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r39-chrome-$script:Suffix"
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
$script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -WindowStyle Hidden -PassThru

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

$env:R39_BASE_URL = $BaseUrl
$env:R39_CDP_PORT = [string]$debugPort
$env:R39_EVIDENCE_DIR = $EvidenceDir
$env:R39_ADMIN = 'admin'
$env:R39_ADMIN_PASSWORD = '123123aa'
$env:R39_NORMAL = $NormalLoginName
$env:R39_NORMAL_PASSWORD = $Password
$env:R39_SYSTEM_ID = $SystemId
$env:R39_RECORD_VALUE = $RecordValue

$nodeScript = Join-Path $env:TEMP "unexamine-r39-print-launch-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R39_BASE_URL;
const port = process.env.R39_CDP_PORT;
const outDir = process.env.R39_EVIDENCE_DIR;
const systemId = process.env.R39_SYSTEM_ID;
const recordValue = process.env.R39_RECORD_VALUE;

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
async function realLogin(client, loginName, password) {
  await navigate(client, `${baseUrl}/#/login`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`, 7000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        localStorage.clear();
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
async function screenshot(client, key) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(shot.data, 'base64'));
  return fileName;
}
async function verifyAdmin(client, key, width, height) {
  await setViewport(client, width, height);
  await navigate(client, `${baseUrl}/systems/${systemId}/admin/module-config`);
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: !!document.querySelector('[data-print-designer="true"] select[aria-label="print paper"]')
          && !!document.querySelector('[data-print-designer="true"] select[aria-label="print orientation"]'),
        text: text.slice(0, 1200),
      };
    })())
  `, 18000);
  const shot = await screenshot(client, key);
  return evaluateJson(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const overflowX = Math.max(0, Math.ceil(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - window.innerWidth));
      const blockers = [];
      if (!document.querySelector('[data-print-designer="true"]')) blockers.push('print designer missing');
      if (!document.querySelector('[data-print-designer="true"] select[aria-label="print paper"]')) blockers.push('paper control missing');
      if (!document.querySelector('[data-print-designer="true"] select[aria-label="print orientation"]')) blockers.push('orientation control missing');
      if (overflowX > 2) blockers.push('horizontal overflow');
      return { key: ${JSON.stringify(key)}, screenshot: ${JSON.stringify(shot)}, overflowX, blockers, text: text.slice(0, 1200), scripts: Array.from(document.scripts).map((s) => s.src).filter(Boolean) };
    })())
  `);
}
async function verifyRuntime(client, key, width, height) {
  await setViewport(client, width, height);
  await navigate(client, `${baseUrl}/systems/${systemId}/modules`);
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: !!document.querySelector('.runtime-table')
          && !!document.querySelector('.detail-head .inline-actions button')
          && text.includes(${JSON.stringify(recordValue)}),
        text: text.slice(0, 1200),
      };
    })())
  `, 15000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const detailButtons = Array.from(document.querySelectorAll('.detail-head .inline-actions button'));
        const exportButton = detailButtons[3] || null;
        window.__r39ExportClick = exportButton ? { found: true, disabled: exportButton.disabled, text: exportButton.textContent } : { found: false };
        if (exportButton && !exportButton.disabled) {
          exportButton.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
          exportButton.click();
        }
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      return {
        ok: !!document.querySelector('[data-runtime-print-preview="true"]') && text.includes('HTML_PRINT') && text.includes(${JSON.stringify(recordValue)}),
        click: window.__r39ExportClick || null,
        text: text.slice(0, 1400),
      };
    })())
  `, 22000);
  const shot = await screenshot(client, key);
  return evaluateJson(client, `
    JSON.stringify((() => {
      const text = document.body.innerText || '';
      const overflowX = Math.max(0, Math.ceil(Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - window.innerWidth));
      const blockers = [];
      if (!document.querySelector('[data-runtime-print-preview="true"]')) blockers.push('runtime print preview missing');
      if (!text.includes('HTML_PRINT')) blockers.push('export format missing');
      if (!text.includes('CSS ready')) blockers.push('print css ready text missing');
      if (!text.includes(${JSON.stringify(recordValue)})) blockers.push('record value missing');
      if (overflowX > 2) blockers.push('horizontal overflow');
      return { key: ${JSON.stringify(key)}, screenshot: ${JSON.stringify(shot)}, overflowX, blockers, text: text.slice(0, 1600) };
    })())
  `);
}
async function run() {
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const results = [];
  await realLogin(client, process.env.R39_ADMIN, process.env.R39_ADMIN_PASSWORD);
  results.push(await verifyAdmin(client, 'desktop-admin-print-designer', 1440, 900));
  results.push(await verifyAdmin(client, 'mobile-admin-print-designer', 390, 760));
  await realLogin(client, process.env.R39_NORMAL, process.env.R39_NORMAL_PASSWORD);
  results.push(await verifyRuntime(client, 'desktop-runtime-print-export', 1440, 900));
  results.push(await verifyRuntime(client, 'mobile-runtime-print-export', 390, 760));
  client.close();
  return { status: 'PASS', results };
}
run().then((result) => console.log(JSON.stringify(result))).catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$BrowserJson = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
$BrowserAudit = $BrowserJson | ConvertFrom-Json
Assert-True -Condition ($BrowserAudit.status -eq 'PASS') -Message "Browser audit failed: $BrowserJson"
Assert-True -Condition (@($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count -eq 0) -Message "Browser audit blockers: $BrowserJson"

if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
    Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
}
if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
    Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path -LiteralPath $nodeScript) {
    Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue
}

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-039'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    recordId = $RecordId
    templateCode = $TemplateCode
    warningTemplateCode = $WarningTemplateCode
    warningCount = @($WarningCheck.warningItems).Count
    draftRuntimeDeniedStatus = $DraftDenied.status
    publishCheckPassed = [bool]$Check.passed
    publishImpactTypes = @($Check.impactRefs | ForEach-Object { $_.impactType })
    publishedVersion = $Published.version
    runtimePreviewVersion = $RuntimePreview.version
    runtimeExportFileId = $RuntimeExport.exportFileId
    exportFormat = $RuntimeExport.exportMeta.format
    exportContentType = $RuntimeExport.exportMeta.contentType
    printCssReady = [bool]$RuntimeExport.exportMeta.printCssReady
    paginationReady = [bool]$RuntimeExport.exportMeta.paginationReady
    estimatedPageCount = $RuntimeExport.exportMeta.estimatedPageCount
    exportHtmlHasPageCss = [bool]($ExportHtml -match '@page')
    exportHtmlHasMediaPrint = [bool]($ExportHtml -match '@media print')
    exportHtmlHasPageBreak = [bool]($ExportHtml -match 'page-break-inside:avoid')
    exportHtmlHasRecordValue = [bool]($ExportHtml -match [regex]::Escape($RecordValue))
    normalAdminDeniedStatus = $NormalAdminDenied.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    deployedScripts = @($BrowserAudit.results | Select-Object -First 1).scripts
    cleanup = $script:CleanupResult
}

$Result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$Summary = @(
    '# R39 Print/PDF Fidelity And Launch Rule Split Evidence',
    '',
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId",
    "- Record: $RecordId",
    "- Template: $TemplateCode",
    "- Warning template: $WarningTemplateCode",
    "- Warning count: $(@($WarningCheck.warningItems).Count)",
    "- Draft runtime denied status: $($DraftDenied.status)",
    "- Publish check passed: $($Check.passed)",
    "- Published version: $($Published.version)",
    "- Runtime preview version: $($RuntimePreview.version)",
    "- Runtime export file id: $($RuntimeExport.exportFileId)",
    "- Export format/content type: $($RuntimeExport.exportMeta.format) / $($RuntimeExport.exportMeta.contentType)",
    "- Print CSS ready: $($RuntimeExport.exportMeta.printCssReady)",
    "- Pagination ready: $($RuntimeExport.exportMeta.paginationReady)",
    "- Estimated pages: $($RuntimeExport.exportMeta.estimatedPageCount)",
    "- Normal member admin write denied status: $($NormalAdminDenied.status)",
    "- Browser result count: $(@($BrowserAudit.results).Count)",
    "- Browser overflow count: $(@($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count)",
    "- Browser blocker count: $(@($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count)",
    "- Cleanup: $($script:CleanupResult -join ', ')",
    '',
    'Boundary:',
    '',
    '- This proves print-ready HTML export with browser print CSS and pagination metadata.',
    '- It does not claim binary PDF generation unless a PDF engine or user-approved HTML boundary is added.',
    '',
    'Evidence files:',
    '',
    "- $ResultFile",
    "- $EvidenceDir"
)
$Summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$Result | ConvertTo-Json -Depth 60
