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
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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

function Invoke-UploadFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [hashtable]$Headers = @{}
    )

    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        foreach ($key in $Headers.Keys) {
            [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key])
        }
        $bytes = [System.IO.File]::ReadAllBytes($FilePath)
        $content = [System.Net.Http.ByteArrayContent]::new($bytes)
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/plain')
        $form.Add($content, 'file', [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync("$BaseUrl$Path", $form).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Upload failed: POST $Path -> HTTP $([int]$response.StatusCode) $body"
        }
        $json = $body | ConvertFrom-Json
        if ($json.code -ne 'SUCCESS') {
            throw "Upload failed: POST $Path -> $body"
        }
        return $json.data
    } finally {
        $form.Dispose()
        $client.Dispose()
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
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r40 runtime detail action cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r40-$script:Suffix-$systemId"
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
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$Password = 'Aa123456!'
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r40-runtime-record-detail-action'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R40 Runtime Detail $script:Suffix"
    systemCode = "r40_runtime_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r40 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R40 Runtime Operator $script:Suffix"
    roleCode = "R40_RUNTIME_OPERATOR_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R40 runtime record breadth role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R40 Runtime Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r40_case_$script:Suffix"
    name = "R40 Runtime Case $script:Suffix"
    status = 1
    description = 'Recovery R40 runtime record detail/action module'
}
$ModuleId = [string]$Module.moduleId

$TitleCode = "r40Title_$($script:Suffix.Replace('-', '_'))"
$AmountCode = "r40Amount_$($script:Suffix.Replace('-', '_'))"
$OwnerCode = "r40Owner_$($script:Suffix.Replace('-', '_'))"
$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $TitleCode -Name 'R40 Title' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $AmountCode -Name 'R40 Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $OwnerCode -Name 'R40 Owner' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/actions" -Headers $script:AdminHeaders -Body @{
    actionCode = 'record.archive'
    actionName = 'Archive'
    actionType = 'UPDATE'
    position = 'ROW'
    enabled = $true
    selectionRule = @{ selectionMode = 'SINGLE'; minSelected = 1; maxSelected = 1; requiredStatuses = @(); sameTenantRequired = $true; forbiddenReason = $null }
    resultContract = @{ resultType = 'SYNC_RESULT'; resultDrawer = 'runtimeActionResultDrawer'; returnsAuditLog = $true; returnsAsyncTask = $false; traceField = 'traceId'; userVisibleStates = @('SUCCESS', 'FAILED', 'BLOCKED') }
    idempotencyRequired = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R40 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$AmountField.fieldId, [string]$OwnerField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$OwnerField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.read" = $true
        "record.create" = $true
        "record.edit" = $true
        "record.delete" = $true
        "record.archive" = $true
        "record.submitApproval" = $false
    }
    fieldPermissions = @{ "*" = "WRITABLE"; "$ModuleId.$TitleCode" = "WRITABLE"; "$ModuleId.$AmountCode" = "WRITABLE"; "$ModuleId.$OwnerCode" = "WRITABLE" }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r40 module publish'
    idempotencyKey = "module-publish-r40-$script:Suffix"
}

$NormalLoginName = "r40_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r40_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R40 Owned $script:Suffix"
    systemCode = "r40_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R40 Runtime Member $script:Suffix"
    employeeNo = "R40NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r40_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
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
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r40 normal switch'
}

$AttachmentFile = Join-Path ([System.IO.Path]::GetTempPath()) "r40-runtime-attachment-$script:Suffix.txt"
[System.IO.File]::WriteAllText($AttachmentFile, "runtime record attachment evidence $script:Suffix", [System.Text.Encoding]::UTF8)
$Upload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=RUNTIME_RECORD' -FilePath $AttachmentFile -Headers $NormalHeaders
$UploadedFileId = [string]$Upload.file.fileId
$UploadedFileName = [string]$Upload.file.fileName
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($UploadedFileId)) -Message 'Upload did not return file id.'

$DraftTitle = "R40 Draft $script:Suffix"
$Draft = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/drafts" -Headers $NormalHeaders -Body @{
    draftId = $null
    recordId = $null
    fieldValues = @{ $TitleCode = $DraftTitle; $AmountCode = 4010; $OwnerCode = 'Runtime Team' }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    clientVersion = 'recovery-r40@1'
    sourceType = 'R40_DRAFT'
}
$DraftRead = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/drafts/$($Draft.draftId)" -Headers $NormalHeaders
Assert-True -Condition ($DraftRead.fieldValues.$TitleCode -eq $DraftTitle) -Message 'Draft readback did not preserve field values.'
Assert-True -Condition (@($DraftRead.attachmentIds) -contains $UploadedFileId) -Message 'Draft readback did not preserve attachment id.'

$RecordTitle = "R40 Record $script:Suffix"
$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{ $TitleCode = $RecordTitle; $AmountCode = 4020; $OwnerCode = 'Runtime Team' }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    draftId = $Draft.draftId
    sourceType = 'R40_CREATE'
}
$RecordId = [string]$Record.recordId

$UpdatedTitle = "R40 Record Updated $script:Suffix"
$Updated = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders -Body @{
    fieldValues = @{ $TitleCode = $UpdatedTitle; $AmountCode = 4090; $OwnerCode = 'Runtime Ops' }
    childRows = @{}
    sourceType = 'R40_UPDATE_WITHOUT_ATTACHMENT_ARRAY'
}
Assert-True -Condition ($Updated.recordId -eq $RecordId) -Message 'Update did not return record id.'

$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$DetailAttachmentIds = @($Detail.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileId })
$DetailAttachmentNames = @($Detail.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileName })
$DetailTabCodes = @($Detail.tabs | ForEach-Object { $_.tabCode })
$DetailHistoryRows = @($Detail.tabs | Where-Object { $_.tabCode -eq 'operationLogs' } | ForEach-Object { @($_.payload.records) })
Assert-True -Condition ($Detail.summary.recordId -eq $RecordId) -Message 'Detail did not return record.'
Assert-True -Condition ($DetailAttachmentIds -contains $UploadedFileId) -Message 'Detail did not preserve attachment after update without attachmentIds.'
Assert-True -Condition ($DetailAttachmentNames -contains $UploadedFileName) -Message 'Detail did not preserve attachment file name.'
Assert-True -Condition ($DetailTabCodes -contains 'attachments' -and $DetailTabCodes -contains 'print' -and $DetailTabCodes -contains 'operationLogs') -Message 'Detail did not expose attachments/print/history tabs.'
Assert-True -Condition (@($DetailHistoryRows).Count -ge 2) -Message 'Detail operationLogs tab did not include latest history rows.'

$History = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$RecordId/history?pageNo=1&pageSize=20" -Headers $NormalHeaders
Assert-True -Condition ([int]$History.total -ge 2) -Message 'History endpoint did not include create/update entries.'

$Second = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{ $TitleCode = "R40 Delete Target $script:Suffix"; $AmountCode = 1; $OwnerCode = 'Runtime Team' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R40_DELETE_TARGET'
}
$DeleteResult = Invoke-Api -Method 'Delete' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$($Second.recordId)" -Headers $NormalHeaders
Assert-True -Condition ($DeleteResult.result -eq 'DELETED') -Message 'Delete action did not return DELETED.'

$ForbiddenAdmin = Invoke-ExpectedForbidden -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/actions" -Headers $NormalHeaders

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r40-chrome-$script:Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r40-runtime-record-detail-action-$script:Suffix.js"
@'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R40_BASE_URL;
const systemId = process.env.R40_SYSTEM_ID;
const accountName = process.env.R40_ACCOUNT;
const password = process.env.R40_PASSWORD;
const recordValue = process.env.R40_RECORD_VALUE;
const attachmentName = process.env.R40_ATTACHMENT_NAME;
const attachmentPrefix = attachmentName.slice(0, 22);
const outDir = process.env.R40_EVIDENCE_DIR;
const port = process.env.R40_CDP_PORT;

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
      const scripts = Array.from(document.scripts).map((script) => script.src).filter(Boolean);
      return {
        key: ${JSON.stringify(key)},
        screenshot: ${JSON.stringify(fileName)},
        hash: location.hash,
        hasRuntime: !!document.querySelector('.runtime-page'),
        hasTable: !!document.querySelector('.runtime-table'),
        hasDetail: !!document.querySelector('.detail-panel'),
        hasEditPanel: !!document.querySelector('.edit-panel'),
        hasUploader: !!document.querySelector('[data-runtime-attachment-uploader="true"]'),
        hasAttachmentDetail: !!document.querySelector('[data-runtime-attachment-detail="true"]'),
        hasHistoryList: !!document.querySelector('[data-runtime-history-list="true"]'),
        hasRecordValue: text.includes(${JSON.stringify(recordValue)}),
        hasAttachmentName: text.includes(${JSON.stringify(attachmentName)}) || text.includes(${JSON.stringify(attachmentPrefix)}),
        overflowX,
        scripts,
        viewport: { width: window.innerWidth, height: window.innerHeight },
      };
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
        ok: location.hash === '#/systems/${systemId}/modules'
          && !!document.querySelector('.runtime-page')
          && !!document.querySelector('.runtime-table')
          && !!document.querySelector('.detail-panel')
          && text.includes(${JSON.stringify(recordValue)}),
        hash: location.hash,
        text: text.slice(0, 900),
      };
    })())
  `, 15000);

  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const buttons = Array.from(document.querySelectorAll('.detail-panel .detail-head .inline-actions button'));
        const editButton = buttons[0];
        if (!editButton) throw new Error('edit button missing');
        editButton.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.edit-panel [data-runtime-attachment-uploader="true"]') })`, 8000);
  const editResult = await capture(client, `${key}-edit-uploader`);

  await navigate(client, `${baseUrl}/systems/${systemId}/modules`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.detail-panel .detail-tabs') && (document.body.innerText || '').includes(${JSON.stringify(recordValue)}) })`, 12000);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const tabs = Array.from(document.querySelectorAll('.detail-panel .detail-tabs button'));
        if (!tabs[2]) throw new Error('attachments tab missing');
        tabs[2].click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `
    JSON.stringify((() => ({
      ok: (document.body.innerText || '').includes(${JSON.stringify(attachmentPrefix)}),
      hasAttachmentDetail: !!document.querySelector('[data-runtime-attachment-detail="true"]'),
      text: (document.body.innerText || '').slice(0, 900),
    }))())
  `, 8000);
  const attachmentResult = await capture(client, `${key}-attachment-detail`);

  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const tabs = Array.from(document.querySelectorAll('.detail-panel .detail-tabs button'));
        if (!tabs[4]) throw new Error('history tab missing');
        tabs[4].click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `
    JSON.stringify((() => ({
      ok: !!document.querySelector('[data-runtime-history-list="true"]') && (document.body.innerText || '').includes('record.update'),
      text: (document.body.innerText || '').slice(0, 1200),
    }))())
  `, 8000);
  const historyResult = await capture(client, `${key}-history`);

  return [editResult, attachmentResult, historyResult];
}

async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('DOM.enable');

  const loginState = await realLogin(client);
  const desktop = await verifyRuntime(client, 'desktop-runtime-record', 1280, 720);
  const mobile = await verifyRuntime(client, 'mobile-runtime-record', 390, 720);
  client.close();

  const results = [...desktop, ...mobile];
  const failures = results.filter((item) => item.overflowX > 2 || !item.hasRuntime || !item.hasTable || (item.key.includes('edit') ? (!item.hasEditPanel || !item.hasUploader) : !item.hasDetail) || (!item.hasAttachmentName && item.key.includes('attachment')) || (!item.hasHistoryList && item.key.includes('history')));
  const output = {
    status: failures.length === 0 ? 'PASS' : 'FAIL',
    task: 'REC-P0-040',
    baseUrl,
    systemId,
    accountName,
    recordValue,
    attachmentName,
    generatedAt: new Date().toISOString(),
    loginState,
    results,
    failures,
  };
  fs.writeFileSync(path.join(outDir, 'runtime-record-detail-action-browser.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output, null, 2));
  if (failures.length > 0) {
    throw new Error(`Runtime record browser audit failed: ${failures.map((item) => item.key).join(', ')}`);
  }
}

run().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
'@ | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }

$env:R40_BASE_URL = $BaseUrl
$env:R40_SYSTEM_ID = $script:SystemId
$env:R40_ACCOUNT = $NormalLoginName
$env:R40_PASSWORD = $Password
$env:R40_RECORD_VALUE = $UpdatedTitle
$env:R40_ATTACHMENT_NAME = $UploadedFileName
$env:R40_EVIDENCE_DIR = $EvidenceDir
$env:R40_CDP_PORT = [string]$debugPort

try {
    $BrowserJson = & $nodeExe $nodeScript | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "Node runtime record audit exited with code $LASTEXITCODE. $BrowserJson"
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

$BrowserResultPath = Join-Path $EvidenceDir 'runtime-record-detail-action-browser.json'
$BrowserAudit = Get-Content -Raw $BrowserResultPath | ConvertFrom-Json
Assert-True -Condition ($BrowserAudit.status -eq 'PASS') -Message "Browser audit failed: $(Get-Content -Raw $BrowserResultPath)"

$Archive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$RecordId/actions/record.archive" -Headers $NormalHeaders -Body @{
    parameters = @{}
    selectedRecordIds = @($RecordId)
    reason = 'recovery-r40 archive action'
    sourceType = 'R40_ACTION'
}
Assert-True -Condition ($Archive.accepted -eq $true) -Message 'Archive action was not accepted.'

$HistoryAfterAction = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/$RecordId/history?pageNo=1&pageSize=20" -Headers $NormalHeaders
$HistoryActions = @($HistoryAfterAction.records | ForEach-Object { $_.actionCode })
Assert-True -Condition ($HistoryActions -contains 'record.archive') -Message 'History did not include record.archive action.'

if (-not $KeepCreatedData) {
    $script:CleanupResult = Remove-CreatedSystems
}

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-040'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    runtimeRoleId = $RuntimeRole.roleId
    normalAccount = $NormalLoginName
    normalMemberId = $NormalMember.systemMemberId
    draftId = $Draft.draftId
    recordId = $RecordId
    uploadedFileId = $UploadedFileId
    uploadedFileName = $UploadedFileName
    detailTabCodes = $DetailTabCodes
    detailAttachmentCount = @($DetailAttachmentIds).Count
    detailHistoryCount = @($DetailHistoryRows).Count
    historyTotal = $History.total
    deleteResult = $DeleteResult.result
    forbiddenAdminStatus = $ForbiddenAdmin.status
    archiveAccepted = $Archive.accepted
    historyAfterActionTotal = $HistoryAfterAction.total
    browserStatus = $BrowserAudit.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.failures).Count
    deployedScripts = @($BrowserAudit.results | Select-Object -First 1).scripts
    browserResultPath = $BrowserResultPath
    cleanup = $script:CleanupResult
}

$ResultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r40-runtime-record-detail-action-result.json'
$Result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$EvidenceMd = Join-Path (Get-Location) 'docs\evidence\recovery\r40-runtime-record-detail-action-2026-06-30.md'
@(
    '# R40 Runtime Record Detail Action Evidence',
    '',
    "- Status: PASS",
    "- Task: REC-P0-040 / FRC-4A",
    "- System: $script:SystemId",
    "- Module: $ModuleId",
    "- Record: $RecordId",
    "- Uploaded file: $UploadedFileName / $UploadedFileId",
    "- Draft readback: $($Draft.draftId)",
    "- Detail tabs: $($DetailTabCodes -join ', ')",
    "- Detail attachment count: $(@($DetailAttachmentIds).Count)",
    "- Detail history rows: $(@($DetailHistoryRows).Count)",
    "- History total before action: $($History.total)",
    "- Delete result: $($DeleteResult.result)",
    "- Archive action accepted: $($Archive.accepted)",
    "- History total after action: $($HistoryAfterAction.total)",
    "- Normal-member admin action API denied HTTP: $($ForbiddenAdmin.status)",
    "- Browser result: $BrowserResultPath",
    "- Browser result count: $(@($BrowserAudit.results).Count)",
    "- Browser overflow count: $(@($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count)",
    "- Cleanup: $($script:CleanupResult -join ', ')",
    '',
    'This is deployed engineering evidence only. It does not set gates.user_script_passed=true.'
) | Set-Content -LiteralPath $EvidenceMd -Encoding UTF8

$Result | ConvertTo-Json -Depth 80
