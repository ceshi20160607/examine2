param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [Parameter(Mandatory = $true)][string]$RootUsername,
    [Parameter(Mandatory = $true)][string]$RootPassword,
    [string]$OutputPath = "$PSScriptRoot/fixture.json",
    [string]$FixturePrefix = 'p4_b2',
    [string]$FixtureLabel = 'P4-B2',
    [string]$SystemTitle = 'P4 生命周期验收',
    [ValidateSet('BASE', 'P4_C1')][string]$FieldProfile = 'BASE',
    [switch]$EnableQueryIndexes,
    [bool]$P4C1FieldsRequired = $true,
    [string]$UniqueFieldCode,
    [string[]]$AdditionalPermissionCodes = @()
)

$ErrorActionPreference = 'Stop'
$AdditionalPermissionCodes = @($AdditionalPermissionCodes | ForEach-Object { $_ -split ',' } | Where-Object { $_ })

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Path,
        [string]$Method = 'GET',
        [object]$Body,
        [switch]$Idempotent
    )

    $headers = @{ 'X-Request-ID' = [guid]::NewGuid().ToString() }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $csrf = ($Session.Cookies.GetCookies($BaseUrl) | Where-Object Name -eq 'EXAMINE_CSRF').Value
        if ($csrf) { $headers['X-CSRF-Token'] = [uri]::UnescapeDataString($csrf) }
    }
    if ($Idempotent) { $headers['Idempotency-Key'] = [guid]::NewGuid().ToString() }

    $arguments = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        WebSession = $Session
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    try {
        $response = Invoke-RestMethod @arguments
    } catch {
        $errorBody = $_.ErrorDetails.Message
        $status = try { [int]$_.Exception.Response.StatusCode } catch { 0 }
        throw "API $Method $Path failed ($status): $errorBody"
    }
    if ($response.code -ne 'OK') {
        throw "API $Method $Path returned $($response.code): $($response.message)"
    }
    return $response.data
}

function New-Session {
    return New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$accountPrefix = $FixturePrefix -replace '_', ''
$root = New-Session
$null = Invoke-Envelope -Session $root -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $RootUsername
    password = $RootPassword
}
$null = Invoke-Envelope -Session $root -Path '/api/v1/context/platform:switch' -Method POST -Body @{}

$system = Invoke-Envelope -Session $root -Path '/api/v1/platform/admin/systems' -Method POST -Idempotent -Body @{
    code = "${FixturePrefix}_$suffix"
    name = "$SystemTitle $suffix"
    description = "$FixtureLabel dedicated runtime acceptance fixture"
    tenantMode = 'SINGLE'
}
$systemId = [string]$system.id
$null = Invoke-Envelope -Session $root -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}
$configRoot = "/api/v1/systems/$systemId/admin/config"
$revision = 0

function Add-ConfigResource {
    param([string]$Path, [hashtable]$Body)
    $Body.draftRevision = [string]$script:revision
    $result = Invoke-Envelope -Session $root -Path $Path -Method POST -Idempotent -Body $Body
    $script:revision++
    return $result
}

$group = Add-ConfigResource -Path "$configRoot/module-groups" -Body @{
    code = 'operations'; name = '运营管理'; description = ''; iconKey = 'folder'
    sortOrder = 0; status = 'ENABLED'
}
$module = Add-ConfigResource -Path "$configRoot/modules" -Body @{
    groupId = [string]$group.id; code = 'work_order'; name = '现场工单'; description = ''
    iconKey = 'clipboard'; sortOrder = 0; status = 'ENABLED'; allowComments = $true; allowTeam = $true
}
$dictionary = Add-ConfigResource -Path "$configRoot/dictionaries" -Body @{
    code = 'priority'; name = '优先级'; type = 'LIST'; category = 'work'
    description = '工单优先级'; status = 'ENABLED'
}
$normalItem = Add-ConfigResource -Path "$configRoot/dictionaries/$($dictionary.id)/items" -Body @{
    parentId = $null; code = 'normal'; label = '普通'; semanticKey = 'NORMAL'; color = '#2563a6'
    iconKey = $null; sortOrder = 0; isDefault = $true; status = 'ENABLED'
}
$urgentItem = Add-ConfigResource -Path "$configRoot/dictionaries/$($dictionary.id)/items" -Body @{
    parentId = $null; code = 'urgent'; label = '紧急'; semanticKey = 'URGENT'; color = '#c33b32'
    iconKey = $null; sortOrder = 1; isDefault = $false; status = 'ENABLED'
}

$cascadeDictionary = $null
$cascadeRootItem = $null
$cascadeChildItem = $null
if ($FieldProfile -eq 'P4_C1') {
    $cascadeDictionary = Add-ConfigResource -Path "$configRoot/dictionaries" -Body @{
        code = 'service_area'; name = '服务区域'; type = 'CASCADE'; category = 'work'
        description = '工单服务区域'; status = 'ENABLED'
    }
    $cascadeRootItem = Add-ConfigResource -Path "$configRoot/dictionaries/$($cascadeDictionary.id)/items" -Body @{
        parentId = $null; code = 'east'; label = '华东'; semanticKey = 'EAST'; color = $null
        iconKey = $null; sortOrder = 0; isDefault = $false; status = 'ENABLED'
    }
    $cascadeChildItem = Add-ConfigResource -Path "$configRoot/dictionaries/$($cascadeDictionary.id)/items" -Body @{
        parentId = [string]$cascadeRootItem.id; code = 'shanghai'; label = '上海'; semanticKey = 'SHANGHAI'
        color = $null; iconKey = $null; sortOrder = 0; isDefault = $false; status = 'ENABLED'
    }
}

$baseFieldDefinitions = @(
    @{ code='subject'; name='工单主题'; type='TEXT'; sortOrder=0; required=$true; showInList=$true; properties=@{ maxLength=200 } },
    @{ code='description'; name='处理说明'; type='TEXTAREA'; sortOrder=1; required=$false; showInList=$false; properties=@{ rows=4; maxLength=2000 } },
    @{ code='amount'; name='预计费用'; type='NUMBER'; sortOrder=2; required=$false; showInList=$true; properties=@{ precision=12; scale=2 } },
    @{ code='due_date'; name='到期日期'; type='DATE'; sortOrder=3; required=$false; showInList=$false; properties=@{} },
    @{ code='scheduled_at'; name='计划时间'; type='DATETIME'; sortOrder=4; required=$false; showInList=$false; properties=@{} },
    @{ code='priority'; name='优先级'; type='RADIO'; sortOrder=5; required=$false; showInList=$true; properties=@{ displayStyle='TAG' }; dictionaryId=[string]$dictionary.id },
    @{ code='assignee'; name='负责人'; type='MEMBER'; sortOrder=6; required=$false; showInList=$true; properties=@{ selectionScope='CURRENT_TENANT' } },
    @{ code='department'; name='负责部门'; type='DEPARTMENT'; sortOrder=7; required=$false; showInList=$false; properties=@{ selectionScope='CURRENT_TENANT' } }
)
$p4C1FieldDefinitions = @(
    @{ code='completion_rate'; name='完成率'; type='PERCENT'; sortOrder=0; required=$true; showInList=$true; properties=@{ scale=4 } },
    @{ code='budget'; name='预算'; type='MONEY'; sortOrder=1; required=$true; showInList=$true; properties=@{ currencies=@('CNY','USD') } },
    @{ code='service_dates'; name='服务日期'; type='DATE_RANGE'; sortOrder=2; required=$true; showInList=$true; properties=@{} },
    @{ code='start_time'; name='开始时间'; type='TIME'; sortOrder=3; required=$true; showInList=$true; properties=@{} },
    @{ code='service_hours'; name='服务时段'; type='TIME_RANGE'; sortOrder=4; required=$true; showInList=$false; properties=@{} },
    @{ code='service_labels'; name='服务标签'; type='MULTI_SELECT'; sortOrder=5; required=$true; showInList=$true; properties=@{ maxSelections=100 }; dictionaryId=[string]$dictionary.id },
    @{ code='service_area'; name='服务区域'; type='CASCADE'; sortOrder=6; required=$true; showInList=$true; properties=@{}; dictionaryId=[string]$cascadeDictionary.id },
    @{ code='enabled'; name='是否启用'; type='SWITCH'; sortOrder=7; required=$true; showInList=$true; properties=@{} },
    @{ code='rating'; name='评分'; type='RATING'; sortOrder=8; required=$true; showInList=$true; properties=@{ maxRating=5; step=1 } },
    @{ code='progress'; name='进度'; type='PROGRESS'; sortOrder=9; required=$true; showInList=$true; properties=@{ scale=2; step=1 } },
    @{ code='tags'; name='自定义标签'; type='TAG'; sortOrder=10; required=$true; showInList=$true; properties=@{} }
)
$fieldDefinitions = if ($FieldProfile -eq 'P4_C1') { $p4C1FieldDefinitions } else { $baseFieldDefinitions }

$fieldIds = @{}
foreach ($definition in $fieldDefinitions) {
    $indexMode = if ($UniqueFieldCode -and $definition.code -eq $UniqueFieldCode) {
        if ($definition.type -notin @('PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS')) {
            throw "UniqueFieldCode $UniqueFieldCode does not identify a supported scalar field"
        }
        'UNIQUE'
    } elseif (-not $EnableQueryIndexes) {
        'NONE'
    } elseif ($definition.type -in @(
        'TEXT', 'NUMBER', 'DATE', 'DATETIME', 'PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS')) {
        'SORT'
    } else {
        'FILTER'
    }
    $body = @{
        dictionaryId = if ($definition.dictionaryId) { $definition.dictionaryId } else { $null }
        targetModuleId = $null
        code = $definition.code; name = $definition.name; type = $definition.type
        sortOrder = $definition.sortOrder
        required = if ($FieldProfile -eq 'P4_C1') { $P4C1FieldsRequired } else { $definition.required }
        hidden = $false; readonly = $false
        searchable = if ($FieldProfile -eq 'P4_C1') { $false } else { $true }
        filterable = $true
        showInList = $definition.showInList; showInDetail = $true; indexMode = $indexMode
        status = 'ENABLED'; properties = $definition.properties
    }
    $field = Add-ConfigResource -Path "$configRoot/modules/$($module.id)/fields" -Body $body
    $fieldIds[$definition.code] = [string]$field.id
}

$actions = @(
    @{ code='archive'; name='归档'; confirmMessage='确认归档这条记录？'; sortOrder=10; successMessage='记录已归档' },
    @{ code='unarchive'; name='取消归档'; confirmMessage=''; sortOrder=20; successMessage='记录已恢复为使用中' },
    @{ code='restore_trash'; name='恢复'; confirmMessage=''; sortOrder=30; successMessage='记录已恢复' },
    @{ code='recover_draft'; name='恢复草稿'; confirmMessage=''; sortOrder=40; successMessage='草稿已恢复' }
)
foreach ($action in $actions) {
    $null = Add-ConfigResource -Path "$configRoot/modules/$($module.id)/actions" -Body @{
        code = $action.code; name = $action.name; type = 'CUSTOM'; placement = 'DETAIL'
        confirmMessage = $action.confirmMessage; sortOrder = $action.sortOrder; status = 'ENABLED'
        properties = @{ style='DEFAULT'; successMessage=$action.successMessage }
    }
}

$check = Invoke-Envelope -Session $root -Path "$configRoot/checks" -Method POST -Body @{ draftRevision = [string]$revision }
$rootState = Invoke-Envelope -Session $root -Path $configRoot
$published = Invoke-Envelope -Session $root -Path "${configRoot}:publish" -Method POST -Idempotent -Body @{
    checkId = [string]$check.id
    draftRevision = [string]$revision
    configRootVersion = [string]$rootState.version
    reason = "$FixtureLabel dedicated runtime acceptance publication"
}
$root = New-Session
$null = Invoke-Envelope -Session $root -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $RootUsername
    password = $RootPassword
}
$null = Invoke-Envelope -Session $root -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}

$tenants = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/tenants?size=200"
$tenant = @($tenants.items | Where-Object isDefault)[0]
if (-not $tenant) { $tenant = @($tenants.items)[0] }
$scopes = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/data-scopes?size=200"
$allScope = @($scopes.items | Where-Object kind -eq 'ALL')[0]
if (-not $tenant -or -not $allScope) { throw 'Default tenant or ALL data scope was not initialized' }
$department = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/departments" -Method POST -Idempotent -Body @{
    name = '业务运营部'; code = "business_operations_$suffix"; parentId = $null
}
$root = New-Session
$null = Invoke-Envelope -Session $root -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $RootUsername
    password = $RootPassword
}
$null = Invoke-Envelope -Session $root -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}

$role = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/roles" -Method POST -Idempotent -Body @{
    code = "${FixturePrefix}_member_$suffix"
    name = "$FixtureLabel 普通成员 $suffix"
    description = "$FixtureLabel ordinary runtime acceptance role"
}
$permissionCodes = @(
    'system.runtime.access', 'system.workbench.view',
    'module.work_order.view', 'module.work_order.create', 'module.work_order.update', 'module.work_order.delete',
    'module.work_order.action.archive', 'module.work_order.action.unarchive',
    'module.work_order.action.restore_trash', 'module.work_order.action.recover_draft'
) + $AdditionalPermissionCodes | Select-Object -Unique
$roleDraft = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/roles/$($role.id)/draft" -Method PUT -Body @{
    name = $role.name; description = $role.description
    permissionCodes = $permissionCodes; deniedPermissionCodes = @()
    dataScopeId = [string]$allScope.id; version = [string]$role.version
}
$roleCheck = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/roles/$($role.id)/draft:check" -Method POST -Idempotent -Body @{
    version = [string]$roleDraft.version
}
$rolePublished = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/roles/$($role.id)/draft:publish" -Method POST -Idempotent -Body @{
    version = [string]$roleCheck.version
}
$root = New-Session
$null = Invoke-Envelope -Session $root -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $RootUsername
    password = $RootPassword
}
$null = Invoke-Envelope -Session $root -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}

$memberUsername = "${accountPrefix}_member_$suffix"
$memberPassword = "$FixtureLabel-Member-$suffix!"
$ordinary = New-Session
$null = Invoke-Envelope -Session $ordinary -Path '/api/v1/auth/register' -Method POST -Idempotent -Body @{
    username = $memberUsername
    displayName = "$FixtureLabel 验收成员 $suffix"
    password = $memberPassword
    systemName = "$FixtureLabel 成员个人系统 $suffix"
    systemCode = "${FixturePrefix}_member_home_$suffix"
}
$accessRequest = Invoke-Envelope -Session $ordinary -Path "/api/v1/context/systems/$systemId/access-requests" -Method POST -Idempotent -Body @{
    targetTenantId = [string]$tenant.id
    reason = "$FixtureLabel ordinary-member browser acceptance"
}
$null = Invoke-Envelope -Session $root -Path "/api/v1/systems/$systemId/admin/access-requests/$($accessRequest.id):approve" -Method POST -Idempotent -Body @{
    reason = "Approve $FixtureLabel runtime acceptance"
    version = [string]$accessRequest.version
    tenantIds = @([string]$tenant.id)
    roleIds = @([string]$role.id)
}
$ordinary = New-Session
$null = Invoke-Envelope -Session $ordinary -Path '/api/v1/auth/login' -Method POST -Body @{
    account = $memberUsername
    password = $memberPassword
}
$ordinaryContext = Invoke-Envelope -Session $ordinary -Path "/api/v1/context/systems/${systemId}:switch" -Method POST -Body @{}
$schema = Invoke-Envelope -Session $ordinary -Path "/api/v1/systems/$systemId/runtime/modules/work_order/record-schema"

$fixture = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $systemId
    systemName = [string]$system.name
    tenantId = [string]$tenant.id
    departmentId = [string]$department.id
    moduleId = [string]$module.id
    moduleCode = 'work_order'
    schemaVersionId = [string]$published.version.id
    fieldIds = $fieldIds
    roleId = [string]$role.id
    roleVersion = [string]$rolePublished.publishedVersion
    username = $memberUsername
    password = $memberPassword
    permissionCodes = $permissionCodes
    effectivePermissions = @($ordinaryContext.context.permissions)
    schemaActions = @($schema.actions)
    queryIndexesEnabled = [bool]$EnableQueryIndexes
    uniqueFieldCode = $UniqueFieldCode
    p4C1FieldsRequired = $P4C1FieldsRequired
    fieldProfile = $FieldProfile
    optionIds = [ordered]@{
        normal = [string]$normalItem.id
        urgent = [string]$urgentItem.id
        cascadeRoot = if ($cascadeRootItem) { [string]$cascadeRootItem.id } else { $null }
        cascadeChild = if ($cascadeChildItem) { [string]$cascadeChildItem.id } else { $null }
    }
}
$fixture | ConvertTo-Json -Depth 10 | Set-Content -Path $OutputPath -Encoding UTF8
$fixture | ConvertTo-Json -Depth 10
