$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString().Substring(7)
$password = 'Correct-c67-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [switch]$AllowError)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method; Uri = "$apiBase$Path"; Headers = $headers
        ContentType = 'application/json; charset=utf-8'; SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 40 -Compress }
    $response = Invoke-WebRequest @parameters
    $payload = $response.Content | ConvertFrom-Json
    if (-not $AllowError -and [int]$response.StatusCode -ge 400) {
        throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    [pscustomobject]@{ status = [int]$response.StatusCode; payload = $payload; data = $payload.data }
}

function Database-Scalar([string]$Sql) {
    [long](docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e $Sql)
}

function New-ReportDefinition {
    param([hashtable]$Dimension, [array]$Filters)
    @{
        modules = @(@{ alias = 'primary'; moduleCode = $script:moduleCode })
        relations = @()
        outputFields = @(
            @{ alias = 'primary'; fieldCode = 'stage'; label = '客户阶段' },
            @{ alias = 'primary'; fieldCode = 'next_follow_up'; label = '下次跟进' }
        )
        metric = @{ operation = 'COUNT' }
        dimension = $Dimension
        filters = $Filters
        sort = @{ alias = 'primary'; fieldCode = 'updatedAt'; direction = 'DESC' }
        timeField = @{}
        maxScanRows = 500
        limit = 20
    }
}

function New-PublishedSource {
    param([string]$Code, [string]$Name, [hashtable]$Definition)
    $source = (Invoke-Api -Method Post -Path '/api/analytics/admin/data-sources' -Token $script:ownerToken -Body @{
        code = $Code; name = $Name; sourceType = 'MODULE_REPORT'; definition = $Definition
        permissionPolicy = @{ resourceType = 'MODULE'; resourceCode = $script:moduleCode; actionCode = 'LIST' }
    }).data
    $preview = (Invoke-Api -Method Get -Path "/api/analytics/admin/data-sources/$($source.id)/report-preview" -Token $script:ownerToken).data
    if (-not $preview.valid) { throw "report source $Name failed preview: $($preview.issues | ConvertTo-Json -Compress)" }
    (Invoke-Api -Method Post -Path "/api/analytics/admin/data-sources/$($source.id)/publish" -Token $script:ownerToken -Body @{
        expectedDraftRevision = $source.draftRevision
    }).data
}

function New-Customer {
    param([string]$Title, [string]$Stage, [string]$NextFollowUp, [long]$OwnerMemberId)
    (Invoke-Api -Method Post -Path "/api/runtime/modules/$script:moduleCode/records" -Token $script:ownerToken -Body @{
        recordNumber = "C67-$suffix-$([guid]::NewGuid().ToString('N').Substring(0, 5))"
        title = $Title; status = 'ACTIVE'; ownerMemberId = $OwnerMemberId; participantMemberIds = @()
        fields = @{ stage = $Stage; next_follow_up = $NextFollowUp }
    }).data
}

function Runtime-Component([object]$Runtime, [string]$Key) {
    $component = @($Runtime.components | Where-Object { $_.componentKey -eq $Key })[0]
    if ($null -eq $component) { throw "dashboard component $Key is missing" }
    $component
}

$ownerRegistration = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = "c67_owner_$suffix"; password = $password; displayName = 'C67 客户经营管理员'
    email = "c67_owner_$suffix@example.com"; systemName = 'C67 客户经营验收系统'; systemCode = "c67_system_$suffix"
}).data
$script:ownerToken = $ownerRegistration.tokens.accessToken
$systemId = [long]$ownerRegistration.systemId
$tenantId = [long]$ownerRegistration.tenantId
$ownerAccountId = [long]$ownerRegistration.accountId
$ownerMemberId = Database-Scalar "select id from sys_member where system_id=$systemId and account_id=$ownerAccountId"
$ownerTenantMemberId = Database-Scalar "select stm.id from sys_tenant_member stm join sys_member sm on sm.id=stm.system_member_id where stm.system_id=$systemId and stm.tenant_id=$tenantId and sm.account_id=$ownerAccountId"

$sellerUsername = "c67_seller_$suffix"
$sellerRegistration = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = $sellerUsername; password = $password; displayName = 'C67 销售成员'
    email = "$sellerUsername@example.com"; systemName = 'C67 销售个人系统'; systemCode = "c67_seller_system_$suffix"
}).data

$script:moduleCode = "customer_c67_$suffix"
$group = (Invoke-Api -Method Post -Path '/api/admin/module-config/groups' -Token $ownerToken -Body @{
    code = "sales_c67_$suffix"; name = '客户经营'; sortOrder = 10
}).data
$module = (Invoke-Api -Method Post -Path '/api/admin/module-config/modules' -Token $ownerToken -Body @{
    groupId = $group.id; code = $moduleCode; name = '客户'; description = '客户总量、阶段、负责人和逾期跟进验收'
}).data.module
$moduleId = [long]$module.id
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/fields" -Token $ownerToken -Body @{
    code = 'stage'; name = '客户阶段'; fieldType = 'TEXT'; required = $true; searchable = $true; sortOrder = 10; config = @{}
}
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/fields" -Token $ownerToken -Body @{
    code = 'next_follow_up'; name = '下次跟进'; fieldType = 'DATETIME'; required = $false; searchable = $true; sortOrder = 20; config = @{}
}
$draftRevision = Database-Scalar "select draft_revision from cfg_module where id=$moduleId"
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/publish" -Token $ownerToken -Body @{
    expectedDraftRevision = $draftRevision; changeSummary = 'C67 客户经营模块发布'
}

$authorization = (Invoke-Api -Method Get -Path '/api/admin/system/authorization' -Token $ownerToken).data
$role = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/roles' -Token $ownerToken -Body @{
    id = $null; code = "seller_c67_$suffix"; name = '客户销售'; description = '只查看本人负责客户'
    permissions = @(
        @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'LIST'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'DETAIL'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'TODO'; resourceCode = 'SYSTEM'; actionCode = 'VIEW'; dataScopeType = 'SELF'; dataScopeJson = $null },
        @{ resourceType = 'MESSAGE'; resourceCode = 'SYSTEM'; actionCode = 'VIEW'; dataScopeType = 'SELF'; dataScopeJson = $null }
    )
    fieldPolicies = @(
        @{ resourceCode = $moduleCode; fieldCode = 'stage'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null },
        @{ resourceCode = $moduleCode; fieldCode = 'next_follow_up'; channel = 'PAGE'; readable = $true; writable = $false; maskStrategy = $null }
    )
    expectedVersion = $null
}).data
$role = (Invoke-Api -Method Post -Path "/api/admin/system/authorization/roles/$($role.id)/publish" -Token $ownerToken -Body @{
    reason = 'C67 客户列表和统计数据范围验收'; expectedVersion = $role.version
}).data
$sellerMember = (Invoke-Api -Method Post -Path '/api/admin/system/authorization/members' -Token $ownerToken -Body @{
    account = $sellerUsername; employeeNumber = "S-$suffix"; departmentId = $authorization.departments[0].id
    managerTenantMemberId = $ownerTenantMemberId; positionTitle = '客户销售'; roleIds = @($role.id)
}).data
$sellerMemberId = [long]$sellerMember.systemMemberId
$sellerTenantMemberId = [long]$sellerMember.tenantMemberId

$sellerPlatformToken = (Invoke-Api -Method Post -Path '/api/auth/login' -Body @{ username = $sellerUsername; password = $password }).data.accessToken
$sellerEntry = (Invoke-Api -Method Post -Path "/api/systems/$systemId/enter" -Token $sellerPlatformToken -Body @{
    previousSystemId = $null; previousTenantId = $null
}).data
$sellerToken = $sellerEntry.tokens.accessToken

$sellerLead = New-Customer -Title '北辰科技' -Stage 'LEAD' -NextFollowUp '2026-09-15T09:00:00' -OwnerMemberId $sellerMemberId
$sellerOverdue = New-Customer -Title '星海制造' -Stage 'FOLLOW_UP' -NextFollowUp '2026-08-20T09:00:00' -OwnerMemberId $sellerMemberId
$ownerWon = New-Customer -Title '远山集团' -Stage 'WON' -NextFollowUp '2026-09-30T09:00:00' -OwnerMemberId $ownerMemberId

$totalSource = New-PublishedSource -Code "customer_total_$suffix" -Name '客户总量' -Definition (New-ReportDefinition -Dimension @{} -Filters @())
$stageSource = New-PublishedSource -Code "customer_stage_$suffix" -Name '客户阶段分布' -Definition (New-ReportDefinition -Dimension @{ type = 'STATUS'; alias = 'primary'; fieldCode = 'stage' } -Filters @())
$ownerSource = New-PublishedSource -Code "customer_owner_$suffix" -Name '客户负责人分布' -Definition (New-ReportDefinition -Dimension @{ type = 'PERSON'; alias = 'primary'; fieldCode = 'ownerMemberId' } -Filters @())
$overdueSource = New-PublishedSource -Code "customer_overdue_$suffix" -Name '逾期跟进客户' -Definition (New-ReportDefinition -Dimension @{} -Filters @(
    @{ alias = 'primary'; fieldCode = 'next_follow_up'; operator = 'LT'; value = '2026-09-02T00:00:00' }
))
$overdueVersionId = [long]$overdueSource.versions[0].id

$kpi = (Invoke-Api -Method Post -Path '/api/analytics/admin/kpis' -Token $ownerToken -Body @{
    code = "overdue_follow_up_$suffix"; name = '逾期跟进客户'; dataSourceVersionId = $overdueVersionId
    targetValue = 0; targetOperator = 'LTE'; periodType = 'MONTH'
    responsibleType = 'PERSON'; responsibleIds = @($sellerTenantMemberId)
    visibilityPermission = @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'LIST' }
    drillPermission = @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'LIST' }
    reminderEnabled = $true; reminderRecipientTenantMemberIds = @($sellerTenantMemberId)
    reminderBelowPercent = 100; status = 'ACTIVE'; expectedVersion = $null
}).data
$kpiResult = (Invoke-Api -Method Post -Path "/api/analytics/admin/kpis/$($kpi.id)/calculate" -Token $ownerToken -Body @{
    periodKey = '2026-09'
}).data
if ($kpiResult.status -ne 'UNDER_TARGET' -or $kpiResult.drillItems.Count -ne 1) { throw 'overdue KPI did not return the overdue customer source' }

$drill = @{ moduleCode = $moduleCode; permission = @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'LIST' } }
$components = @(
    @{ componentKey = 'customer_total'; componentType = 'METRIC'; title = '客户总量'; dataSourceId = $totalSource.id; layout = @{ width = 1 }; queryParameters = @{}; displayConfig = @{ accentColor = '#315efb'; refreshSeconds = 30 }; drillTarget = $drill; sortOrder = 0 },
    @{ componentKey = 'customer_stage'; componentType = 'CHART'; title = '客户阶段'; dataSourceId = $stageSource.id; layout = @{ width = 2 }; queryParameters = @{}; displayConfig = @{ accentColor = '#52c7a5'; refreshSeconds = 30; chartType = 'BAR' }; drillTarget = $drill; sortOrder = 10 },
    @{ componentKey = 'customer_owner'; componentType = 'CHART'; title = '负责人分布'; dataSourceId = $ownerSource.id; layout = @{ width = 2 }; queryParameters = @{}; displayConfig = @{ accentColor = '#8b7cf6'; refreshSeconds = 30; chartType = 'PIE' }; drillTarget = $drill; sortOrder = 20 },
    @{ componentKey = 'customer_overdue'; componentType = 'KPI'; title = '逾期跟进'; dataSourceId = $overdueSource.id; layout = @{ width = 2 }; queryParameters = @{ kpiId = $kpi.id; kpiVersion = $kpi.version }; displayConfig = @{ accentColor = '#ef7c8e'; refreshSeconds = 30 }; drillTarget = @{ route = "/systems/$systemId`?workspace=kpi&kpiId=$($kpi.id)" }; sortOrder = 30 }
)
$dashboard = (Invoke-Api -Method Post -Path '/api/analytics/admin/dashboards' -Token $ownerToken -Body @{
    code = "customer_home_$suffix"; name = '客户经营工作台'; description = '客户总量、阶段、负责人、逾期跟进与工作闭环'
    components = $components; expectedVersion = $null
}).data
$dashboardPreview = (Invoke-Api -Method Get -Path "/api/analytics/admin/dashboards/$($dashboard.id)/preview" -Token $ownerToken).data
if (-not $dashboardPreview.valid) { throw "customer dashboard preview failed: $($dashboardPreview.issues | ConvertTo-Json -Compress)" }
$null = Invoke-Api -Method Post -Path "/api/analytics/admin/dashboards/$($dashboard.id)/publish" -Token $ownerToken -Body @{
    expectedDraftRevision = $dashboard.draftRevision
}

$ownerRuntime = (Invoke-Api -Method Get -Path '/api/analytics/runtime' -Token $ownerToken).data
$sellerRuntime = (Invoke-Api -Method Get -Path '/api/analytics/runtime' -Token $sellerToken).data
$ownerList = (Invoke-Api -Method Get -Path "/api/runtime/modules/$moduleCode/records?page=1&pageSize=20" -Token $ownerToken).data
$sellerList = (Invoke-Api -Method Get -Path "/api/runtime/modules/$moduleCode/records?page=1&pageSize=20" -Token $sellerToken).data
$ownerTotal = [long](Runtime-Component -Runtime $ownerRuntime -Key 'customer_total').value
$sellerTotal = [long](Runtime-Component -Runtime $sellerRuntime -Key 'customer_total').value
if ($ownerTotal -ne $ownerList.total -or $sellerTotal -ne $sellerList.total -or $ownerTotal -ne 3 -or $sellerTotal -ne 2) {
    throw 'role-scoped dashboard totals do not match the ordinary customer list'
}

$sellerStage = Runtime-Component -Runtime $sellerRuntime -Key 'customer_stage'
$leadItem = @($sellerStage.items | Where-Object { $_.id -eq 'LEAD' })[0]
if ($null -eq $leadItem -or $leadItem.filter.fieldCode -ne 'stage') { throw 'stage chart did not expose a safe list filter' }
$filterJson = ConvertTo-Json -InputObject @(@{ fieldCode = 'stage'; operator = 'EQ'; value = 'LEAD' }) -Compress
$encodedFilter = [uri]::EscapeDataString($filterJson)
$drillList = (Invoke-Api -Method Get -Path "/api/runtime/modules/$moduleCode/records?page=1&pageSize=20&filters=$encodedFilter" -Token $sellerToken).data
if ($drillList.total -ne 1 -or $drillList.records[0].id -ne $sellerLead.id) { throw 'stage drill filter did not open the matching customer list' }

$updated = (Invoke-Api -Method Put -Path "/api/runtime/modules/$moduleCode/records/$($sellerOverdue.id)" -Token $ownerToken -Body @{
    title = $sellerOverdue.title; recordNumber = $sellerOverdue.recordNumber; status = 'ACTIVE'
    ownerMemberId = $sellerMemberId; participantMemberIds = @(); version = $sellerOverdue.version
    fields = @{ stage = 'LEAD'; next_follow_up = '2026-08-20T09:00:00' }
}).data
$newCustomer = New-Customer -Title '云帆电子' -Stage 'NEW' -NextFollowUp '2026-09-22T09:00:00' -OwnerMemberId $sellerMemberId
$sellerRuntimeAfterChange = (Invoke-Api -Method Get -Path '/api/analytics/runtime' -Token $sellerToken).data
$sellerTotalAfterChange = [long](Runtime-Component -Runtime $sellerRuntimeAfterChange -Key 'customer_total').value
$leadAfterChange = @((Runtime-Component -Runtime $sellerRuntimeAfterChange -Key 'customer_stage').items | Where-Object { $_.id -eq 'LEAD' })[0]
if ($sellerTotalAfterChange -ne 3 -or [long]$leadAfterChange.value -ne 2) { throw 'dashboard did not refresh after customer create/update' }

$sellerKpi = (Invoke-Api -Method Get -Path "/api/analytics/kpis/$($kpi.id)" -Token $sellerToken).data
$sellerInbox = (Invoke-Api -Method Get -Path '/api/messages?status=ACTIVE&sourceType=ALL' -Token $sellerToken).data
$kpiMessage = @($sellerInbox.messages | Where-Object { $_.sourceType -eq 'KPI_ALERT' })[0]
if ($null -eq $kpiMessage -or $kpiMessage.targetRoute -notlike "*/systems/$systemId*workspace=kpi*" -or $sellerKpi.latestResult.drillItems.Count -ne 1) {
    throw 'KPI reminder did not close the loop to the seller and overdue customer'
}
if (-not $sellerKpi.latestResult.explanation.metricDefinition -or -not $sellerKpi.latestResult.calculatedAt) {
    throw 'KPI result does not explain its source definition and update time'
}

[ordered]@{
    systemId = $systemId; tenantId = $tenantId; moduleCode = $moduleCode
    dashboardId = $dashboard.id; dashboardVersion = 1
    adminListTotal = $ownerList.total; adminDashboardTotal = $ownerTotal
    sellerListTotal = $sellerList.total; sellerDashboardTotal = $sellerTotal
    stageDrillFilter = $leadItem.filter; stageDrillRecordId = $drillList.records[0].id
    sellerTotalAfterCreate = $sellerTotalAfterChange; sellerLeadAfterUpdate = [long]$leadAfterChange.value
    updatedCustomerId = $updated.id; createdCustomerId = $newCustomer.id
    kpiId = $kpi.id; kpiStatus = $sellerKpi.latestResult.status
    kpiDrillRecordId = $sellerKpi.latestResult.drillItems[0].id
    kpiMetricDefinition = $sellerKpi.latestResult.explanation.metricDefinition
    kpiCalculatedAt = $sellerKpi.latestResult.calculatedAt
    kpiMessageId = $kpiMessage.id; kpiMessageTarget = $kpiMessage.targetRoute
} | ConvertTo-Json -Depth 12
