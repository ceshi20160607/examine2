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

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected JSON file was not found: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

function New-FieldBody {
    param(
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [bool]$Required = $false
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
        permissionMetadata = @{
            readableRoleIds = @()
            writableRoleIds = @()
            runtimeReadable = $true
            runtimeWritable = $true
            maskedWhenDenied = 'HIDDEN'
            permissionVersion = "r70_field_perm_$script:Suffix"
        }
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = $Code
            desensitizeMode = 'NONE'
        }
    }
}

function Remove-CreatedSystems {
    $results = @()
    foreach ($systemId in $script:CreatedSystemIds) {
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r70 no-code configuration residual depth cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r70-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

trap {
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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r70-no-code-configuration-residual-depth'
$script:ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r70-no-code-configuration-residual-depth-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r70-no-code-configuration-residual-depth-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'no-code-configuration-browser-audit.json'
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Add-Check 'release' 'health database schema redis UP' `
    ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    ($Health | ConvertTo-Json -Depth 12 -Compress)

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R70 NoCode Residual $script:Suffix"
    systemCode = "r70_nocode_$script:Suffix"
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
    reason = 'recovery-r70 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R70 Runtime Member $script:Suffix"
    roleCode = "R70_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'R70 runtime visible role'
}
$HiddenRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R70 Hidden Role $script:Suffix"
    roleCode = "R70_HIDDEN_ROLE_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'R70 hidden navigation role'
}

$VisibleGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R70 Visible Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$HiddenGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R70 Hidden Group $script:Suffix"
    sort = 20
    visibleRoleIds = @([string]$HiddenRole.roleId)
    publishStatus = 'DRAFT'
}
$DraftGroup = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R70 Draft Group $script:Suffix"
    sort = 30
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$VisibleModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r70_visible_$script:Suffix"
    name = "R70 Visible Module $script:Suffix"
    status = 1
    description = 'R70 visible published module'
}
$HiddenModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$HiddenGroup.groupId
    moduleCode = "r70_hidden_$script:Suffix"
    name = "R70 Hidden Module $script:Suffix"
    status = 1
    description = 'R70 hidden-role module'
}
$DraftModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r70_draft_$script:Suffix"
    name = "R70 Draft Module $script:Suffix"
    status = 1
    description = 'R70 unpublished module'
}
$DisabledModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$VisibleGroup.groupId
    moduleCode = "r70_disabled_$script:Suffix"
    name = "R70 Disabled Module $script:Suffix"
    status = 0
    description = 'R70 disabled module'
}
$DraftGroupModule = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$DraftGroup.groupId
    moduleCode = "r70_draft_group_$script:Suffix"
    name = "R70 Draft Group Module $script:Suffix"
    status = 1
    description = 'R70 module inside unpublished group'
}

foreach ($module in @($VisibleModule, $HiddenModule, $DraftModule, $DisabledModule, $DraftGroupModule)) {
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($module.moduleId)/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'R70 Public Name' -Type 'TEXT' -Required $true)
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($module.moduleId)/scenes" -Headers $script:AdminHeaders -Body @{
        sceneCode = "r70_scene_$script:Suffix"
        sceneName = "R70 Runtime Scene $script:Suffix"
        defaultScene = $true
        visibleRoleIds = @([string]$RuntimeRole.roleId)
        columnFieldIds = @()
        filterFieldIds = @()
        sortFieldIds = @()
    }
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($VisibleModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish visible module'; idempotencyKey = "r70-pub-visible-$script:Suffix" }
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($HiddenModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish hidden module'; idempotencyKey = "r70-pub-hidden-$script:Suffix" }
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($DisabledModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish disabled module metadata'; idempotencyKey = "r70-pub-disabled-$script:Suffix" }
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$($DraftGroupModule.moduleId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish module in draft group'; idempotencyKey = "r70-pub-draft-group-module-$script:Suffix" }
$VisibleGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($VisibleGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish visible group'; idempotencyKey = "r70-pub-visible-group-$script:Suffix" }
$HiddenGroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups/$($HiddenGroup.groupId)/publish" -Headers $script:AdminHeaders -Body @{ reason = 'R70 publish hidden group'; idempotencyKey = "r70-pub-hidden-group-$script:Suffix" }

$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{
        ([string]$VisibleModule.moduleId) = $true
        ([string]$HiddenModule.moduleId) = $false
        ([string]$DraftModule.moduleId) = $false
        ([string]$DisabledModule.moduleId) = $false
        ([string]$DraftGroupModule.moduleId) = $false
    }
    actionPermissions = @{
        'record.read' = $true
        'record.create' = $false
        'record.edit' = $false
        'record.delete' = $false
    }
    fieldPermissions = @{ publicName = 'READABLE' }
    dataScopeRules = @(@{ type = 'SELF'; expression = 'ownerMemberId == currentMember' })
    denyPolicies = @('record.create')
}

$ModuleList = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/modules?pageNo=1&pageSize=100" -Headers $script:AdminHeaders
$VisibleMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$VisibleModule.moduleId }) | Select-Object -First 1
$HiddenMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$HiddenModule.moduleId }) | Select-Object -First 1
$DraftMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$DraftModule.moduleId }) | Select-Object -First 1
$DisabledMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$DisabledModule.moduleId }) | Select-Object -First 1
$DraftGroupMeta = @($ModuleList.records | Where-Object { [string]$_.moduleId -eq [string]$DraftGroupModule.moduleId }) | Select-Object -First 1

Add-Check 'navigation-metadata' 'visible module has role ids and runtimeVisible true' `
    ($VisibleMeta.navigation.runtimeVisible -eq $true -and (@($VisibleMeta.navigation.visibleRoleIds) -contains [string]$RuntimeRole.roleId)) `
    ($VisibleMeta.navigation | ConvertTo-Json -Depth 12 -Compress)
Add-Check 'navigation-metadata' 'hidden module stays published but visible only to hidden role' `
    ($HiddenMeta.navigation.runtimeVisible -eq $true -and (@($HiddenMeta.navigation.visibleRoleIds) -contains [string]$HiddenRole.roleId) -and -not (@($HiddenMeta.navigation.visibleRoleIds) -contains [string]$RuntimeRole.roleId)) `
    ($HiddenMeta.navigation | ConvertTo-Json -Depth 12 -Compress)
Add-Check 'navigation-metadata' 'draft disabled and draft-group modules are runtime invisible' `
    ($DraftMeta.navigation.runtimeVisible -eq $false -and $DisabledMeta.navigation.runtimeVisible -eq $false -and $DraftGroupMeta.navigation.runtimeVisible -eq $false) `
    "draft=$($DraftMeta.navigation.runtimeVisible), disabled=$($DisabledMeta.navigation.runtimeVisible), draftGroup=$($DraftGroupMeta.navigation.runtimeVisible)"

$NormalLoginName = "r70_member_$script:Suffix"
$Password = 'Aa123456!'
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "16$((Get-Date).ToString('HHmmssfff'))"
    email = "r70_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R70 Owned $script:Suffix"
    systemCode = "r70_owned_$script:Suffix"
}
if (-not [string]::IsNullOrWhiteSpace([string]$NormalRegister.systemId)) {
    $script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null
}
$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R70 Runtime Member $script:Suffix"
    employeeNo = "R70M$((Get-Date).ToString('HHmmssfff'))"
    mobile = "15$((Get-Date).ToString('HHmmssfff'))"
    email = "r70_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r70 normal runtime direct access'
}
Add-Check 'member-binding' 'normal account switch context has binding role and member' `
    (-not [string]::IsNullOrWhiteSpace([string]$NormalSwitch.accountMemberBindingId) -and (@($NormalSwitch.effectiveRoleIds) -contains [string]$RuntimeRole.roleId)) `
    ($NormalSwitch | ConvertTo-Json -Depth 20 -Compress)

$VisibleSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($VisibleModule.moduleId)/records/list-schema" -Headers $NormalHeaders
$HiddenDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($HiddenModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$DraftDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($DraftModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$DisabledDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($DisabledModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$DraftGroupDirect = Invoke-ExpectedHttp -Method 'Get' -Path "/api/v1/systems/$SystemId/runtime/modules/$($DraftGroupModule.moduleId)/records/list-schema" -Headers $NormalHeaders -ExpectedStatuses @(403)
$ForbiddenCreate = Invoke-ExpectedHttp -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$($VisibleModule.moduleId)/records" -Headers $NormalHeaders -ExpectedStatuses @(403) -Body @{
    fieldValues = @{ publicName = "R70 forbidden write $script:Suffix" }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R70_FORBIDDEN_WRITE'
}

Add-Check 'runtime-direct-access' 'normal member can read visible published schema only' `
    (@($VisibleSchema.columns).Count -ge 1 -and [int]$HiddenDirect.status -eq 403 -and [int]$DraftDirect.status -eq 403 -and [int]$DisabledDirect.status -eq 403 -and [int]$DraftGroupDirect.status -eq 403) `
    "visibleColumns=$(@($VisibleSchema.columns).Count), hidden=$($HiddenDirect.status), draft=$($DraftDirect.status), disabled=$($DisabledDirect.status), draftGroup=$($DraftGroupDirect.status)"
Add-Check 'runtime-permission' 'normal member create is denied on visible module' `
    ([int]$ForbiddenCreate.status -eq 403) "create=$($ForbiddenCreate.status)"

$childResults += Invoke-ChildScript 'recovery-r44-no-code-permission-preview' (Join-Path $PSScriptRoot 'recovery-r44-no-code-permission-preview-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R44 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r44-no-code-permission-preview-result.json')
Add-Check 'permission-preview' 'fresh R44 permission preview still matches runtime schema' `
    ($R44.status -eq 'PASS' -and $R44.previewAllowed -eq $false -and $R44.normalSecretColumnLeaked -eq $false -and $R44.normalCreateDisabled -eq $true -and [int]$R44.forbiddenCreateStatus -eq 403 -and [int]$R44.browserBlockerCount -eq 0) `
    "previewAllowed=$($R44.previewAllowed), secretLeaked=$($R44.normalSecretColumnLeaked), browserBlockers=$($R44.browserBlockerCount)"

$childResults += Invoke-ChildScript 'recovery-r45-field-dict-menu' (Join-Path $PSScriptRoot 'recovery-r45-field-dict-menu-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R45 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r45-field-dict-menu-result.json')
$ExpectedTypes = @('LONG_TEXT','NUMBER','DATE','DATETIME','SELECT','MULTI_SELECT','USER','DEPARTMENT','ATTACHMENT','IMAGE','RELATION','CHILD_TABLE')
$ActualTypes = @($R45.fieldTypes | ForEach-Object { [string]$_ })
$ActiveOptions = @($R45.activeSchemaOptionCodes | ForEach-Object { [string]$_ })
Add-Check 'field-dictionary-menu' 'fresh R45 field types dictionary impact and disabled item exclusion pass' `
    ($R45.status -eq 'PASS' -and [int]$R45.fieldCount -ge 12 -and (@($ExpectedTypes | Where-Object { -not ($ActualTypes -contains $_) }).Count -eq 0) -and [int]$R45.dictFieldReferenceCount -ge 2 -and [int]$R45.dictDisabledItemCount -eq 1 -and -not ($ActiveOptions -contains 'DISABLED_OLD') -and [int]$R45.browserBlockerCount -eq 0) `
    "types=$($ActualTypes -join ','), options=$($ActiveOptions -join ','), browserBlockers=$($R45.browserBlockerCount)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R70 aggregate browser evidence plus direct-access API guard'
    navigationGuard = @{
        visibleModule = [string]$VisibleModule.name
        hiddenModule = [string]$HiddenModule.name
        draftModule = [string]$DraftModule.name
        disabledModule = [string]$DisabledModule.name
        hiddenDirectStatus = $HiddenDirect.status
        draftDirectStatus = $DraftDirect.status
        disabledDirectStatus = $DisabledDirect.status
        draftGroupDirectStatus = $DraftGroupDirect.status
    }
    r44 = @{
        resultFile = 'docs/evidence/recovery/r44-no-code-permission-preview-result.json'
        browserAuditPath = $R44.browserAuditPath
        browserResultCount = $R44.browserResultCount
        browserOverflowCount = $R44.browserOverflowCount
        browserBlockerCount = $R44.browserBlockerCount
    }
    r45 = @{
        resultFile = 'docs/evidence/recovery/r45-field-dict-menu-result.json'
        browserAuditPath = $R45.browserAuditPath
        browserResultCount = $R45.browserResultCount
        browserOverflowCount = $R45.browserOverflowCount
        browserBlockerCount = $R45.browserBlockerCount
    }
}
$browserAudit | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

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
    draftGroupId = [string]$DraftGroup.groupId
    visibleGroupVersion = [string]$VisibleGroupPublish.version
    hiddenGroupVersion = [string]$HiddenGroupPublish.version
    visibleModuleId = [string]$VisibleModule.moduleId
    hiddenModuleId = [string]$HiddenModule.moduleId
    draftModuleId = [string]$DraftModule.moduleId
    disabledModuleId = [string]$DisabledModule.moduleId
    draftGroupModuleId = [string]$DraftGroupModule.moduleId
    visibleSchemaColumnCount = @($VisibleSchema.columns).Count
    hiddenDirectStatus = $HiddenDirect.status
    draftDirectStatus = $DraftDirect.status
    disabledDirectStatus = $DisabledDirect.status
    draftGroupDirectStatus = $DraftGroupDirect.status
    forbiddenCreateStatus = $ForbiddenCreate.status
    fieldTypes = @($R45.fieldTypes)
    dictPublishedVersion = $R45.dictPublishedVersion
    dictDisabledItemExcluded = -not ($ActiveOptions -contains 'DISABLED_OLD')
    previewAllowed = $R44.previewAllowed
    hiddenFieldLeaked = $R44.normalSecretColumnLeaked
    browserResultCount = [int]$R44.browserResultCount + [int]$R45.browserResultCount
    browserOverflowCount = [int]$R44.browserOverflowCount + [int]$R45.browserOverflowCount
    browserBlockerCount = [int]$R44.browserBlockerCount + [int]$R45.browserBlockerCount
    browserAuditFile = 'docs/evidence/recovery/screenshots/r70-no-code-configuration-residual-depth/no-code-configuration-browser-audit.json'
    unsupportedNestedModuleGroups = 'ModuleGroup has no parentId in current schema; R70 proves dictionary hierarchy via R45 and records nested module groups as a residual model gap.'
    cleanup = $script:CleanupResult
}

Assert-True -Condition ([int]$Result.browserBlockerCount -eq 0) -Message 'R70 browser aggregate has blockers.'
Assert-True -Condition ([int]$Result.browserOverflowCount -eq 0) -Message 'R70 browser aggregate has horizontal overflow.'

$Result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $script:ResultFile -Encoding UTF8

$summary = @"
# REC-P0-070 / R70 No-Code Configuration Residual Depth Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- System: $SystemId
- Runtime role/member/binding: $($RuntimeRole.roleId) / $($NormalMember.systemMemberId) / $($NormalSwitch.accountMemberBindingId)
- Navigation metadata: visible runtimeVisible=$($VisibleMeta.navigation.runtimeVisible), hidden visibleRoleIds=$(@($HiddenMeta.navigation.visibleRoleIds) -join ', '), draft/disabled/draft-group runtimeVisible=$($DraftMeta.navigation.runtimeVisible)/$($DisabledMeta.navigation.runtimeVisible)/$($DraftGroupMeta.navigation.runtimeVisible)
- Runtime direct-access guard: hidden=$($HiddenDirect.status), draft=$($DraftDirect.status), disabled=$($DisabledDirect.status), draft-group=$($DraftGroupDirect.status), forbidden-create=$($ForbiddenCreate.status)
- Fresh permission preview: R44 status=$($R44.status), previewAllowed=$($R44.previewAllowed), hiddenFieldLeaked=$($R44.normalSecretColumnLeaked)
- Fresh field/dict/menu: R45 status=$($R45.status), fieldCount=$($R45.fieldCount), dictDisabledExcluded=$(-not ($ActiveOptions -contains 'DISABLED_OLD'))
- Browser aggregate: results=$($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)
- Browser audit JSON: docs/evidence/recovery/screenshots/r70-no-code-configuration-residual-depth/no-code-configuration-browser-audit.json
- Cleanup: $(@($Result.cleanup) -join ', ')

Nested module groups are not implemented in the current database model. This script records that honestly and proves the current supported hierarchy path through dictionary item hierarchy evidence from R45 instead of inventing unsupported module-group parent data.

This remains engineering evidence only. gates.user_script_passed stays false until user verification/signoff.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$Result | ConvertTo-Json -Depth 100
