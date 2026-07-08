param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$DeferCleanupForBrowser
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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 60 -Compress }
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

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function New-FieldBody {
    param([string]$Code, [string]$Name, [string]$Type, [bool]$Required = $false)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        dictTypeId = $null
        permissionMetadata = $null
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
                reason = 'recovery-r31 advanced table cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r31-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

function FieldDisplay {
    param([object]$Row, [string]$FieldCode)
    $field = @($Row.fields | Where-Object { $_.fieldCode -eq $FieldCode }) | Select-Object -First 1
    if ($null -eq $field) {
        return $null
    }
    return $field.displayValue
}

trap {
    $originalError = $_
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
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r31-advanced-table'
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

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R31 Advanced Table $script:Suffix"
    systemCode = "r31_table_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r31 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R31 Advanced Table Member $script:Suffix"
    roleCode = "R31_TABLE_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R31 advanced table role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R31 Table Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r31_case_$script:Suffix"
    name = "R31 Case $script:Suffix"
    status = 1
    description = 'Recovery R31 advanced table module'
}
$ModuleId = [string]$Module.moduleId

$TitleField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'Public Name' -Type 'TEXT' -Required $true)
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'Secret Note' -Type 'TEXT')
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'amountValue' -Name 'Amount Value' -Type 'NUMBER')
$StageField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'stageName' -Name 'Stage Name' -Type 'TEXT')

$DefaultScene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R31 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$TitleField.fieldId, [string]$AmountField.fieldId, [string]$StageField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId, [string]$StageField.fieldId)
    sortFieldIds = @([string]$TitleField.fieldId)
}

$SavedScene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = "r31_compact_$script:Suffix"
    sceneName = 'R31 Compact Saved View'
    defaultScene = $false
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$AmountField.fieldId, [string]$SecretField.fieldId, [string]$TitleField.fieldId)
    filterFieldIds = @([string]$TitleField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$Permission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.create" = $false
        "record.edit" = $false
        "record.delete" = $false
    }
    fieldPermissions = @{
        "$ModuleId.publicName" = "READABLE"
        "$ModuleId.secretNote" = "HIDDEN"
        "$ModuleId.amountValue" = "READABLE"
        "$ModuleId.stageName" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r31 module publish'
    idempotencyKey = "module-publish-r31-$script:Suffix"
}

1..25 | ForEach-Object {
    $idx = $_
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
        fieldValues = @{
            publicName = ('R31 Item {0:D2}' -f $idx)
            secretNote = "secret-$idx"
            amountValue = $idx
            stageName = if ($idx % 2 -eq 0) { 'Even' } else { 'Odd' }
        }
        childRows = @{}
        attachmentIds = @()
        sourceType = 'R31_ADVANCED_TABLE_SMOKE'
    }
}

$NormalLoginName = "r31_table_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r31_table_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R31 Owned $script:Suffix"
    systemCode = "r31_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R31 Table Member $script:Suffix"
    employeeNo = "R31TM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r31_table_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r31 normal member table read'
}

$SceneReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/scenes" -Headers $NormalHeaders
Assert-True -Condition (@($SceneReadback | Where-Object { $_.sceneCode -eq $SavedScene.sceneCode }).Count -eq 1) -Message 'Saved table view was not visible after normal login.'

$Paged = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = ''
    sceneCode = $SavedScene.sceneCode
    fieldFilters = @()
    sorts = @()
}
$Columns = @($Paged.listSchema.columns)
Assert-True -Condition ($Paged.page.total -eq 25) -Message "Expected 25 records, got $($Paged.page.total)."
Assert-True -Condition (@($Paged.page.records).Count -eq 10) -Message 'Server paging did not return pageSize=10 records.'
Assert-True -Condition ($Columns[0].fieldCode -eq 'amountValue') -Message "Saved column order did not put amountValue first: $($Columns.fieldCode -join ',')"
Assert-True -Condition ($Columns[0].fixed -eq $true) -Message 'First saved-view column was not marked fixed.'
Assert-True -Condition (@($Columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked through saved view columns.'
Assert-True -Condition (@($Paged.page.records[0].fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked through saved view row fields.'
Assert-True -Condition ([int](FieldDisplay -Row $Paged.page.records[0] -FieldCode 'amountValue') -eq 1) -Message 'Scene default sort by amountValue ASC was not applied.'

$SortedDesc = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 5
    keyword = ''
    sceneCode = $SavedScene.sceneCode
    fieldFilters = @()
    sorts = @(@{ fieldCode = 'amountValue'; direction = 'DESC' })
}
Assert-True -Condition ([int](FieldDisplay -Row $SortedDesc.page.records[0] -FieldCode 'amountValue') -eq 25) -Message 'Explicit server sort DESC was not applied.'

$Filtered = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = ''
    sceneCode = $SavedScene.sceneCode
    fieldFilters = @(@{ fieldCode = 'publicName'; operator = 'LIKE'; value = 'Item 2' })
    sorts = @(@{ fieldCode = 'amountValue'; direction = 'ASC' })
}
Assert-True -Condition ($Filtered.page.total -ge 6) -Message "Expected Item 2 filter to match at least 6 rows, got $($Filtered.page.total)."
$BadFilter = @($Filtered.page.records | Where-Object { -not ((FieldDisplay -Row $_ -FieldCode 'publicName') -like '*Item 2*') })
Assert-True -Condition ($BadFilter.Count -eq 0) -Message 'Server filter returned rows outside the requested field value.'

$Relogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$ReloginHeaders = @{ Authorization = "Bearer $($Relogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ReloginHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r31 saved view persistence relogin'
}
$ReloginSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $ReloginHeaders -Body @{
    pageNo = 1
    pageSize = 5
    keyword = ''
    sceneCode = $SavedScene.sceneCode
    fieldFilters = @()
    sorts = @()
}
Assert-True -Condition (@($ReloginSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked after relogin saved-view readback.'
Assert-True -Condition ($ReloginSearch.listSchema.columns[0].fieldCode -eq 'amountValue') -Message 'Saved view order was not persisted after relogin.'

$ApiAudit = [ordered]@{
    scenes = @($SceneReadback | ForEach-Object { $_.sceneCode })
    savedSceneCode = $SavedScene.sceneCode
    defaultSceneId = $DefaultScene.sceneId
    columnOrder = @($Paged.listSchema.columns | ForEach-Object { $_.fieldCode })
    fixedColumns = @($Paged.listSchema.columns | Where-Object { $_.fixed -eq $true } | ForEach-Object { $_.fieldCode })
    firstPageAmounts = @($Paged.page.records | ForEach-Object { FieldDisplay -Row $_ -FieldCode 'amountValue' })
    descFirstAmount = FieldDisplay -Row $SortedDesc.page.records[0] -FieldCode 'amountValue'
    filteredTotal = $Filtered.page.total
    hiddenFieldInColumns = @($Paged.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count
    hiddenFieldInRows = @($Paged.page.records[0].fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count
}
$ApiAuditPath = Join-Path $EvidenceDir 'advanced-table-api-audit.json'
$ApiAudit | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ApiAuditPath -Encoding UTF8

$script:CleanupResult = if ($KeepCreatedData) {
    @('SKIPPED')
} elseif ($DeferCleanupForBrowser) {
    @('DEFERRED_FOR_BROWSER_EVIDENCE')
} else {
    Remove-CreatedSystems
}

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-031'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    roleId = $RuntimeRole.roleId
    normalLoginName = $NormalLoginName
    savedSceneCode = $SavedScene.sceneCode
    totalRecords = $Paged.page.total
    firstPageCount = @($Paged.page.records).Count
    columnOrder = @($Paged.listSchema.columns | ForEach-Object { $_.fieldCode })
    fixedFirstColumn = $Paged.listSchema.columns[0].fixed
    hiddenColumnLeaked = @($Paged.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    hiddenRowFieldLeaked = @($Paged.page.records[0].fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    sceneDefaultSortFirstAmount = FieldDisplay -Row $Paged.page.records[0] -FieldCode 'amountValue'
    explicitDescSortFirstAmount = FieldDisplay -Row $SortedDesc.page.records[0] -FieldCode 'amountValue'
    filteredTotal = $Filtered.page.total
    permissionVersion = $Permission.permissionVersion
    evidenceDir = $EvidenceDir
    apiAuditPath = $ApiAuditPath
    cleanup = $script:CleanupResult
}

$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r31-advanced-table-result.json'
$Result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$SummaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r31-advanced-table-2026-06-30.md'
@"
# R31 Advanced Table

Status: PASS

Task: REC-P0-031 Advanced Table

Base URL: $BaseUrl

Evidence:

- Machine result: $ResultFile
- API audit: $ApiAuditPath

Assertions:

- Created 25 real runtime records for module $ModuleId.
- Saved view $($SavedScene.sceneCode) persisted hidden/reordered columns and sort/filter field definitions.
- Normal member readback after login can select the saved view.
- Server paging returns 10 of 25 records.
- Saved-view schema puts amountValue first and marks it fixed.
- Hidden field secretNote does not appear in saved-view columns or row fields.
- Scene default sort returns amountValue ASC; explicit DESC sort returns amountValue 25 first.
- Server field filter returns only rows whose publicName matches Item 2.
- Cleanup: $($script:CleanupResult -join ', ')
"@ | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

$Result | ConvertTo-Json -Depth 60
