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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
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

function Invoke-ExpectedHttp {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [int[]]$ExpectedStatuses,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
        }
        $status = 200
        $body = 'SUCCESS'
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
    }
    if (-not ($ExpectedStatuses -contains $status)) {
        throw "Expected HTTP $($ExpectedStatuses -join '/') for $Method $Path, got $status $body"
    }
    return [ordered]@{ status = $status; body = $body }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Add-Check {
    param([string]$Area, [string]$Name, [bool]$Passed, [string]$Detail)
    $script:Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    Assert-True -Condition $Passed -Message "$Area/$Name failed: $Detail"
}

function Invoke-ChildScript {
    param([string]$Name, [string]$ScriptPath, [string[]]$Arguments)
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $allArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell @allArgs 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return [ordered]@{ name = $Name; exitCode = $exitCode; logFile = $logFile }
}

function New-FieldBody {
    param(
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [bool]$Required = $false,
        [string]$PermissionMode = 'WRITABLE'
    )
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        defaultValue = $null
        validationRules = @{}
        typeConfig = @{}
        maskRule = 'NONE'
        permissionMode = $PermissionMode
        permissionMetadata = @{
            readableRoleIds = @()
            writableRoleIds = @()
            runtimeReadable = $PermissionMode -ne 'HIDDEN'
            runtimeWritable = $PermissionMode -eq 'WRITABLE'
            maskedWhenDenied = 'HIDDEN'
            permissionVersion = "r71_field_perm_$script:Suffix"
        }
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
    $results = @()
    foreach ($systemId in $script:CreatedSystemIds) {
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r71 no-code frontend binding hierarchy cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r71-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

function Stop-Browser {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

trap {
    Stop-Browser
    if (-not $KeepCreatedData -and $script:AdminHeaders -and $script:CreatedSystemIds -and @($script:CreatedSystemIds).Count -gt 0) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            $script:CleanupResult = @("FAILED:$($_.Exception.Message)")
        }
    }
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
        cleanup = $script:CleanupResult
    }
    if ($script:ResultFile) {
        $failure | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 80)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = (Get-Date).ToString('MMddHHmmssfff')
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:CleanupResult = @('SKIPPED')
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r71-no-code-frontend-binding-and-hierarchy-residual'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r71-no-code-frontend-binding-and-hierarchy-residual-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r71-no-code-frontend-binding-and-hierarchy-residual-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'no-code-frontend-binding-browser-audit.json'
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Add-Check 'release' 'health database schema redis UP' `
    ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    ($Health | ConvertTo-Json -Depth 12 -Compress)

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R71 Frontend Binding $script:Suffix"
    systemCode = "r71_frontend_binding_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TenantId = [string]((@($SwitchOptions | Where-Object { [string]$_.systemId -eq $SystemId }) | Select-Object -First 1).tenantId)
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r71 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R71 Runtime Member $script:Suffix"
    roleCode = "R71_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
}
$HiddenRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R71 Hidden Role $script:Suffix"
    roleCode = "R71_HIDDEN_ROLE_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
}

$VisibleGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R71 Visible Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$HiddenGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R71 Hidden Group $script:Suffix"
    sort = 20
    visibleRoleIds = @([string]$HiddenRole.roleId)
    publishStatus = 'DRAFT'
}

$VisibleModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r71_visible_$script:Suffix"
    name = "R71 Visible Module $script:Suffix"
    status = 1
}
$HiddenModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$HiddenGroup.groupId
    moduleCode = "r71_hidden_$script:Suffix"
    name = "R71 Hidden Module $script:Suffix"
    status = 1
}
$DisabledModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r71_disabled_$script:Suffix"
    name = "R71 Disabled Module $script:Suffix"
    status = 0
}

foreach ($module in @($VisibleModule, $HiddenModule, $DisabledModule)) {
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($module.moduleId)/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'R71 Public Name' -Type 'TEXT' -Required $true -PermissionMode 'READABLE')
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($module.moduleId)/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'hiddenSecret' -Name 'R71 Hidden Secret' -Type 'TEXT' -Required $false -PermissionMode 'HIDDEN')
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($module.moduleId)/scenes" -Headers $script:AdminHeaders -Body @{
        sceneCode = "r71_scene_$script:Suffix"
        sceneName = "R71 Runtime Scene $script:Suffix"
        defaultScene = $true
        visibleRoleIds = @([string]$RuntimeRole.roleId)
        columnFieldIds = @()
        filterFieldIds = @()
        sortFieldIds = @()
    }
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($VisibleModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R71 publish visible module'; idempotencyKey = "r71-pub-visible-$script:Suffix" }
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($HiddenModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R71 publish hidden module'; idempotencyKey = "r71-pub-hidden-$script:Suffix" }
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($DisabledModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R71 publish disabled module metadata'; idempotencyKey = "r71-pub-disabled-$script:Suffix" }
$VisibleGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($VisibleGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R71 publish visible group'; idempotencyKey = "r71-pub-visible-group-$script:Suffix" }
$HiddenGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($HiddenGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R71 publish hidden group'; idempotencyKey = "r71-pub-hidden-group-$script:Suffix" }

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{
        ([string]$VisibleModule.moduleId) = $true
        ([string]$HiddenModule.moduleId) = $false
        ([string]$DisabledModule.moduleId) = $false
    }
    actionPermissions = @{
        'record.read' = $true
        'record.create' = $false
        'record.edit' = $false
        'record.delete' = $false
    }
    fieldPermissions = @{
        publicName = 'READABLE'
        hiddenSecret = 'HIDDEN'
    }
    dataScopeRules = @(@{ type = 'SELF'; expression = 'ownerMemberId == currentMember' })
    denyPolicies = @('record.create')
}

$NormalLoginName = "r71_member_$script:Suffix"
$Password = 'Aa123456!'
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r71_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R71 Owned $script:Suffix"
    systemCode = "r71_owned_$script:Suffix"
}
if (-not [string]::IsNullOrWhiteSpace([string]$NormalRegister.systemId)) {
    $script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null
}
$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R71 Runtime Member $script:Suffix"
    employeeNo = "R71M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "15$((Get-Date).ToString('HHmmssfff'))"
    email = "r71_runtime_member_$script:Suffix@example.com"
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
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r71 normal runtime browser'
}

$ModuleList = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules?pageNo=1&pageSize=100" -Headers $script:AdminHeaders
$VisibleMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$VisibleModule.moduleId }) | Select-Object -First 1
$HiddenMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$HiddenModule.moduleId }) | Select-Object -First 1
$DisabledMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$DisabledModule.moduleId }) | Select-Object -First 1
$MemberList = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/members?pageNo=1&pageSize=20" -Headers $script:AdminHeaders
$MemberMeta = @($MemberList.records | Where-Object { [string]$_.systemMemberId -eq [string]$NormalMember.systemMemberId }) | Select-Object -First 1
$VisibleFields = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules/$($VisibleModule.moduleId)/fields?pageNo=1&pageSize=20" -Headers $script:AdminHeaders
$VisibleSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($VisibleModule.moduleId)/records/list-schema" -Headers $NormalHeaders
$HiddenDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($HiddenModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$DisabledDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($DisabledModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$ForbiddenCreate = Invoke-ExpectedHttp -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$($VisibleModule.moduleId)/records" -Headers $NormalHeaders -ExpectedStatuses @(403) -Body @{
    fieldValues = @{ publicName = "R71 forbidden write $script:Suffix" }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R71_FORBIDDEN_WRITE'
}

Add-Check 'api-metadata' 'module navigation metadata exposes role visibility and runtime visibility' `
    ($VisibleMeta.navigation.runtimeVisible -eq $true -and (@($VisibleMeta.navigation.visibleRoleIds) -contains [string]$RuntimeRole.roleId) -and $HiddenMeta.navigation.runtimeVisible -eq $true -and -not (@($HiddenMeta.navigation.visibleRoleIds) -contains [string]$RuntimeRole.roleId) -and $DisabledMeta.navigation.runtimeVisible -eq $false) `
    "visible=$($VisibleMeta.navigation | ConvertTo-Json -Depth 8 -Compress), hidden=$($HiddenMeta.navigation | ConvertTo-Json -Depth 8 -Compress), disabled=$($DisabledMeta.navigation | ConvertTo-Json -Depth 8 -Compress)"
Add-Check 'member-binding' 'member list readback carries runtime role id' `
    (@($MemberMeta.roleIds) -contains [string]$RuntimeRole.roleId -and @($NormalSwitch.effectiveRoleIds) -contains [string]$RuntimeRole.roleId) `
    "memberRoleIds=$(@($MemberMeta.roleIds) -join ','), switchRoleIds=$(@($NormalSwitch.effectiveRoleIds) -join ',')"
Add-Check 'runtime-permission' 'normal member sees visible schema and cannot access hidden or disabled modules' `
    ((@($VisibleSchema.columns | Where-Object { $_.fieldCode -eq 'hiddenSecret' }).Count -eq 0) -and [int]$HiddenDirect.status -eq 403 -and [int]$DisabledDirect.status -eq 403 -and [int]$ForbiddenCreate.status -eq 403) `
    "columns=$(@($VisibleSchema.columns | ForEach-Object { $_.fieldCode }) -join ','), hidden=$($HiddenDirect.status), disabled=$($DisabledDirect.status), create=$($ForbiddenCreate.status)"

$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r71-chrome-$script:Suffix"
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

$nodeScript = Join-Path $env:TEMP "unexamine-r71-frontend-binding-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');

const baseUrl = process.env.R71_BASE_URL;
const port = process.env.R71_CDP_PORT;
const outDir = process.env.R71_EVIDENCE_DIR;
const ids = {
  systemId: process.env.R71_SYSTEM_ID,
  visibleGroupId: process.env.R71_VISIBLE_GROUP_ID,
  hiddenGroupId: process.env.R71_HIDDEN_GROUP_ID,
  visibleModuleId: process.env.R71_VISIBLE_MODULE_ID,
  hiddenModuleId: process.env.R71_HIDDEN_MODULE_ID,
  disabledModuleId: process.env.R71_DISABLED_MODULE_ID,
  runtimeRoleId: process.env.R71_RUNTIME_ROLE_ID,
  memberId: process.env.R71_MEMBER_ID,
};
const names = {
  visibleModule: process.env.R71_VISIBLE_MODULE_NAME,
  hiddenModule: process.env.R71_HIDDEN_MODULE_NAME,
  hiddenSecret: 'hiddenSecret',
};
const admin = {
  accountId: process.env.R71_ADMIN_ACCOUNT_ID,
  accessToken: process.env.R71_ADMIN_TOKEN,
  refreshToken: process.env.R71_ADMIN_REFRESH || '',
};
const normal = {
  accountId: process.env.R71_NORMAL_ACCOUNT_ID,
  accessToken: process.env.R71_NORMAL_TOKEN,
  refreshToken: process.env.R71_NORMAL_REFRESH || '',
};

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
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
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: viewport.mobile,
  });
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
async function click(client, selector) {
  await evaluate(client, `(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) throw new Error('selector missing: ${selector}');
    el.click();
    return JSON.stringify({ ok: true });
  })()`);
}
async function capture(client, key, viewport, checksExpression) {
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const checks = (${checksExpression})();
    return JSON.stringify({
      key: ${JSON.stringify(key)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      overflowX,
      textSample: text.slice(0, 1000),
      checks,
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  Object.entries(result.checks || {}).forEach(([name, passed]) => {
    if (!passed) result.blockers.push(name);
  });
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function auditAdmin(client, viewport) {
  await setStorage(client, admin);
  await navigate(client, `${baseUrl}/?r71=${Date.now()}#/systems/${ids.systemId}/admin/module-config`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-module-admin-row="${ids.visibleModuleId}"]') })`);
  await click(client, '[data-module-work-tab="fields"]');
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-module-field-code="publicName"]') })`);
  const moduleConfig = await capture(client, 'admin-module-config', viewport, `() => ({
    visibleGroupMarked: !!document.querySelector('[data-module-group-id="${ids.visibleGroupId}"][data-module-group-hierarchy-supported="false"]'),
    visibleModuleMarked: !!document.querySelector('[data-module-admin-row="${ids.visibleModuleId}"][data-module-admin-runtime-visible="true"]'),
    hiddenModuleMarked: !!document.querySelector('[data-module-admin-row="${ids.hiddenModuleId}"][data-module-admin-visible-role-ids*="${ids.runtimeRoleId}"]') === false,
    fieldMarked: !!document.querySelector('[data-module-field-code="publicName"][data-module-field-id]'),
    hiddenFieldMarked: !!document.querySelector('[data-module-field-code="hiddenSecret"][data-module-field-permission-mode]'),
    hierarchyBoundaryVisible: !!document.querySelector('[data-module-group-tree="true"][data-module-group-hierarchy-supported="false"]')
  })`);

  await navigate(client, `${baseUrl}/?r71=${Date.now()}#/systems/${ids.systemId}/admin/org-structure`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-member-id="${ids.memberId}"]') })`);
  const memberBinding = await capture(client, 'admin-member-binding', viewport, `() => ({
    memberMarked: !!document.querySelector('[data-member-id="${ids.memberId}"][data-member-role-ids*="${ids.runtimeRoleId}"]'),
    memberBindingStatusMarked: !!document.querySelector('[data-member-id="${ids.memberId}"][data-member-binding-status]')
  })`);

  await navigate(client, `${baseUrl}/?r71=${Date.now()}#/systems/${ids.systemId}/admin/role-management`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-role-id="${ids.runtimeRoleId}"]') && !!document.querySelector('[data-role-permission-workbench="true"]') })`);
  const roleWorkbench = await capture(client, 'admin-role-workbench', viewport, `() => ({
    roleMarked: !!document.querySelector('[data-role-id="${ids.runtimeRoleId}"]'),
    roleOptionMarked: !!document.querySelector('[data-permission-role-id="${ids.runtimeRoleId}"]'),
    moduleOptionMarked: !!document.querySelector('[data-permission-module-id="${ids.visibleModuleId}"]'),
    workbenchMarked: !!document.querySelector('[data-role-permission-workbench="true"][data-role-permission-role-ids*="${ids.runtimeRoleId}"][data-role-permission-module-ids*="${ids.visibleModuleId}"]')
  })`);
  return [moduleConfig, memberBinding, roleWorkbench];
}
async function auditRuntime(client, viewport) {
  await setStorage(client, normal);
  await navigate(client, `${baseUrl}/?r71=${Date.now()}#/systems/${ids.systemId}/modules`);
  await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('[data-runtime-module-id="${ids.visibleModuleId}"]') })`);
  return [await capture(client, `runtime-${viewport.name}`, viewport, `() => {
    const text = document.body.innerText || '';
    const table = document.querySelector('[data-runtime-table-module-id="${ids.visibleModuleId}"]');
    return {
      visibleRuntimeModuleMarked: !!document.querySelector('[data-runtime-module-id="${ids.visibleModuleId}"][data-runtime-module-runtime-visible="true"]'),
      hiddenRuntimeModuleAbsent: !document.querySelector('[data-runtime-module-id="${ids.hiddenModuleId}"]') && !text.includes(${JSON.stringify(names.hiddenModule)}),
      disabledRuntimeModuleAbsent: !document.querySelector('[data-runtime-module-id="${ids.disabledModuleId}"]'),
      hiddenFieldAbsent: !text.includes(${JSON.stringify(names.hiddenSecret)}) && !(table && (table.dataset.runtimeTableColumnFieldCodes || '').includes('hiddenSecret')),
      systemHeaderGroupMarked: !!document.querySelector('[data-system-nav-group-id="${ids.visibleGroupId}"][data-system-nav-group-hierarchy-supported="false"]')
    };
  }`)];
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const desktop = { name: 'desktop', width: 1440, height: 920, mobile: false };
  const mobile = { name: 'mobile', width: 390, height: 760, mobile: true };
  await setViewport(client, desktop);
  const results = [];
  results.push(...await auditAdmin(client, desktop));
  results.push(...await auditRuntime(client, desktop));
  await setViewport(client, mobile);
  results.push(...await auditRuntime(client, mobile));
  client.close();
  const output = {
    status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS',
    ids,
    results,
  };
  fs.writeFileSync(path.join(outDir, 'no-code-frontend-binding-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode

$env:R71_BASE_URL = $BaseUrl
$env:R71_CDP_PORT = [string]$debugPort
$env:R71_EVIDENCE_DIR = $script:EvidenceDir
$env:R71_SYSTEM_ID = $SystemId
$env:R71_VISIBLE_GROUP_ID = [string]$VisibleGroup.groupId
$env:R71_HIDDEN_GROUP_ID = [string]$HiddenGroup.groupId
$env:R71_VISIBLE_MODULE_ID = [string]$VisibleModule.moduleId
$env:R71_HIDDEN_MODULE_ID = [string]$HiddenModule.moduleId
$env:R71_DISABLED_MODULE_ID = [string]$DisabledModule.moduleId
$env:R71_RUNTIME_ROLE_ID = [string]$RuntimeRole.roleId
$env:R71_MEMBER_ID = [string]$NormalMember.systemMemberId
$env:R71_VISIBLE_MODULE_NAME = [string]$VisibleModule.name
$env:R71_HIDDEN_MODULE_NAME = [string]$HiddenModule.name
$env:R71_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R71_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R71_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R71_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId
$env:R71_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R71_NORMAL_REFRESH = [string]$NormalLogin.refreshToken

$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R71 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditFile | ConvertFrom-Json
Stop-Browser
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
$browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
Add-Check 'browser-binding' 'deployed browser exposes stable configuration and runtime markers without hidden DOM leakage' `
    ($BrowserAudit.status -eq 'PASS' -and [int]$browserBlockerCount -eq 0 -and [int]$browserOverflowCount -eq 0) `
    "results=$(@($BrowserAudit.results).Count), blockers=$browserBlockerCount, overflow=$browserOverflowCount"

if (-not $KeepCreatedData) {
    $script:CleanupResult = Remove-CreatedSystems
}

$Result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.3', 'REQ-5.3', 'REQ-5.3.1', 'REQ-5.4', 'REQ-5.5', 'REQ-5.6', 'REQ-5.7', 'REQ-5.9', 'REQ-5.10', 'REQ-6.7', 'REQ-6.10')
    flowIds = @('C1', 'C2', 'C3', 'B1', 'B2')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    systemId = $SystemId
    tenantId = $TenantId
    roleId = [string]$RuntimeRole.roleId
    hiddenRoleId = [string]$HiddenRole.roleId
    memberId = [string]$NormalMember.systemMemberId
    accountMemberBindingId = [string]$NormalSwitch.accountMemberBindingId
    visibleGroupId = [string]$VisibleGroup.groupId
    hiddenGroupId = [string]$HiddenGroup.groupId
    visibleGroupVersion = [string]$VisibleGroupPublish.version
    hiddenGroupVersion = [string]$HiddenGroupPublish.version
    visibleModuleId = [string]$VisibleModule.moduleId
    hiddenModuleId = [string]$HiddenModule.moduleId
    disabledModuleId = [string]$DisabledModule.moduleId
    visibleSchemaColumnCodes = @($VisibleSchema.columns | ForEach-Object { [string]$_.fieldCode })
    visibleFieldCount = @($VisibleFields.records).Count
    hiddenDirectStatus = $HiddenDirect.status
    disabledDirectStatus = $DisabledDirect.status
    forbiddenCreateStatus = $ForbiddenCreate.status
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = $browserOverflowCount
    browserBlockerCount = $browserBlockerCount
    browserAuditFile = 'docs/evidence/recovery/screenshots/r71-no-code-frontend-binding-and-hierarchy-residual/no-code-frontend-binding-browser-audit.json'
    unsupportedNestedModuleGroups = 'ModuleGroup has no parentId in current schema; browser-visible hierarchy boundary is marked with data-module-group-hierarchy-supported=false and data-system-nav-group-hierarchy-supported=false.'
    cleanup = $script:CleanupResult
}
$Result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-071 / R71 No-Code Frontend Binding And Hierarchy Residual Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- System: $SystemId
- Runtime role/member/binding: $($RuntimeRole.roleId) / $($NormalMember.systemMemberId) / $($NormalSwitch.accountMemberBindingId)
- Visible group/module: $($VisibleGroup.groupId) / $($VisibleModule.moduleId)
- Hidden group/module: $($HiddenGroup.groupId) / $($HiddenModule.moduleId)
- Direct API guard: hidden=$($HiddenDirect.status), disabled=$($DisabledDirect.status), forbidden-create=$($ForbiddenCreate.status)
- Visible runtime schema columns: $(@($VisibleSchema.columns | ForEach-Object { $_.fieldCode }) -join ', ')
- Browser audit: results=$(@($BrowserAudit.results).Count), overflow=$browserOverflowCount, blockers=$browserBlockerCount
- Browser audit JSON: docs/evidence/recovery/screenshots/r71-no-code-frontend-binding-and-hierarchy-residual/no-code-frontend-binding-browser-audit.json
- Cleanup: $($script:CleanupResult -join ', ')

R71 proves browser-visible object binding markers for module groups, modules, fields, roles, member-role binding, permission workbench options, runtime navigation, and runtime schema columns. It also proves the normal-member runtime DOM does not contain hidden module or hidden field identifiers.

Nested module groups remain unsupported by the current database model. The deployed browser now exposes this honestly through hierarchy-supported=false markers instead of pretending that module groups have persisted parent data.

This remains engineering evidence only. gates.user_script_passed stays false until user verification/signoff.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

Write-Output ($Result | ConvertTo-Json -Depth 100)
