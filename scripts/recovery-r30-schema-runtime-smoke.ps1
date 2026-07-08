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

function Invoke-ExpectedForbidden {
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
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
        throw "Expected forbidden response but request succeeded: $Method $Path"
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        if ($status -ne 403) {
            throw "Expected HTTP 403 but got HTTP ${status}: $body"
        }
        return @{ status = $status; body = $body }
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
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
        dictTypeId = $null
        permissionMetadata = $null
        validationRule = @{ source = 'R30'; required = $Required }
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
                reason = 'recovery-r30 schema runtime cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r30-$script:Suffix-$systemId"
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

$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r30-schema-runtime'
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
    systemName = "R30 Schema Runtime $script:Suffix"
    systemCode = "r30_schema_$script:Suffix"
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
    reason = 'recovery-r30 schema setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R30 Schema Runtime Member $script:Suffix"
    roleCode = "R30_SCHEMA_RUNTIME_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R30 schema runtime readonly role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R30 Schema Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r30_contract_$script:Suffix"
    name = "R30 Contract $script:Suffix"
    status = 1
    description = 'Recovery R30 schema runtime module'
}
$ModuleId = [string]$Module.moduleId

$PublicField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'Public Name' -Type 'TEXT' -Required $true)
$SecretField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'Secret Note' -Type 'TEXT' -Required $false)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'amountValue' -Name 'Amount Value' -Type 'NUMBER' -Required $false)
$FieldIds = @([string]$PublicField.fieldId, [string]$SecretField.fieldId, [string]$AmountField.fieldId)

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R30 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$PublicField.fieldId)
    sortFieldIds = @([string]$AmountField.fieldId)
}

$Permission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:SystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "record.create" = $true
        "record.edit" = $true
    }
    fieldPermissions = @{
        "$ModuleId.publicName" = "READABLE"
        "$ModuleId.secretNote" = "HIDDEN"
        "$ModuleId.amountValue" = "READABLE"
    }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r30 module publish'
    idempotencyKey = "module-publish-r30-$script:Suffix"
}

$PagePayload = @{
    pageCode = 'main'
    pageName = "R30 Main Schema $script:Suffix"
    pageType = 'MODULE_LIST'
    route = "/systems/$script:SystemId/modules/$ModuleId"
    layoutMode = 'left-list-right-detail'
    components = @(
        @{ componentCode = 'toolbar'; componentType = 'SHORTCUT'; title = 'Page Actions'; dataSource = 'MODULE_ACTIONS'; sort = 10; visible = $true },
        @{ componentCode = 'form'; componentType = 'FORM'; title = 'Schema Form'; dataSource = 'MODULE_FIELDS'; boundFieldCode = 'publicName'; sort = 20; visible = $true },
        @{ componentCode = 'secret'; componentType = 'DETAIL'; title = 'Hidden Secret'; dataSource = 'RECORD_DETAIL'; boundFieldCode = 'secretNote'; sort = 30; visible = $true },
        @{ componentCode = 'list'; componentType = 'LIST'; title = 'Runtime Records'; dataSource = 'RUNTIME_RECORDS'; sort = 40; visible = $true }
    )
    visibleRoleIds = @()
    changeReason = 'recovery-r30 schema runtime contract'
}

$SavedPage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders -Body $PagePayload
$DraftSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/main/schema?snapshot=draft" -Headers $script:AdminHeaders
Assert-True -Condition ($DraftSchema.schemaSource -eq 'DRAFT' -and $DraftSchema.schemaVersion.StartsWith('PAGE_DRAFT_')) -Message 'Draft schema did not return draft source/version.'
$DraftCustomFields = @($DraftSchema.fields | Where-Object { @('publicName', 'secretNote', 'amountValue') -contains $_.fieldCode })
Assert-True -Condition ($DraftCustomFields.Count -eq 3) -Message 'Admin draft schema should include the three R30 custom fields.'

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/main/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message "Page publish-check did not pass: $($PublishCheck | ConvertTo-Json -Depth 30 -Compress)"

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/main/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r30 page schema publish'
    idempotencyKey = "page-publish-r30-$script:Suffix"
}

$AdminPublishedSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/main/schema?snapshot=published" -Headers $script:AdminHeaders
Assert-True -Condition ($AdminPublishedSchema.schemaSource -eq 'PUBLISHED') -Message 'Admin schema should read published snapshot.'
Assert-True -Condition ($AdminPublishedSchema.schemaVersion -eq $Published.version) -Message 'Admin schema version does not equal publish version.'
Assert-True -Condition (@($AdminPublishedSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 1) -Message 'Admin published schema should see the secret field.'
$AdminCustomReadonly = @($AdminPublishedSchema.fields | Where-Object { @('publicName', 'secretNote', 'amountValue') -contains $_.fieldCode } | Where-Object { $_.readonly -eq $true })
Assert-True -Condition ($AdminCustomReadonly.Count -eq 0) -Message 'Admin published schema should be writable for R30 custom fields.'

$NormalLoginName = "r30_schema_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r30_schema_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R30 Owned $script:Suffix"
    systemCode = "r30_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R30 Schema Member $script:Suffix"
    employeeNo = "R30SM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r30_schema_runtime_member_$script:Suffix@example.com"
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
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r30 normal member schema read'
}
Assert-True -Condition (-not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_ADMIN') -and -not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) `
    -Message "Normal member unexpectedly has admin role: $($NormalSwitch.effectiveRoleIds -join ',')"

$NormalRuntimeSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/pages/main/schema" -Headers $NormalHeaders
Assert-True -Condition ($NormalRuntimeSchema.schemaVersion -eq $AdminPublishedSchema.schemaVersion) -Message 'Runtime schema version is not the same as admin published schema version.'
Assert-True -Condition (@($NormalRuntimeSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into normal member runtime schema.'
Assert-True -Condition (@($NormalRuntimeSchema.fields | Where-Object { $_.readonly -eq $true }).Count -ge 2) -Message 'Normal runtime schema should render readable fields as readonly.'
Assert-True -Condition (@($NormalRuntimeSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -eq 0) -Message 'Component bound to hidden field leaked into runtime schema.'

$RuntimeSearch = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 10
    keyword = ''
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @()
}
Assert-True -Condition (@($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0) -Message 'Hidden field leaked into runtime list schema.'

$ForbiddenCreate = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        publicName = "R30 forbidden write $script:Suffix"
        secretNote = 'should not write'
        amountValue = 300
    }
    childRows = @{}
    attachmentIds = @()
    draftId = $null
    sourceType = 'R30_SCHEMA_SMOKE'
}

$BrowserAudit = [ordered]@{
    adminSchemaVersion = $AdminPublishedSchema.schemaVersion
    runtimeSchemaVersion = $NormalRuntimeSchema.schemaVersion
    adminFieldCodes = @($AdminPublishedSchema.fields | ForEach-Object { $_.fieldCode })
    normalFieldCodes = @($NormalRuntimeSchema.fields | ForEach-Object { $_.fieldCode })
    normalReadonlyCount = @($NormalRuntimeSchema.fields | Where-Object { $_.readonly -eq $true }).Count
    normalComponentCodes = @($NormalRuntimeSchema.components | ForEach-Object { $_.componentCode })
}
$BrowserAuditPath = Join-Path $EvidenceDir 'schema-runtime-api-audit.json'
$BrowserAudit | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditPath -Encoding UTF8

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-030'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    roleId = $RuntimeRole.roleId
    pageCode = 'main'
    savedPageStatus = $SavedPage.publishStatus
    publishCheckPassed = $PublishCheck.passed
    publishedVersion = $Published.version
    draftSchemaVersion = $DraftSchema.schemaVersion
    adminPublishedSchemaVersion = $AdminPublishedSchema.schemaVersion
    normalRuntimeSchemaVersion = $NormalRuntimeSchema.schemaVersion
    adminFieldCount = @($AdminPublishedSchema.fields).Count
    normalFieldCount = @($NormalRuntimeSchema.fields).Count
    normalReadonlyCount = @($NormalRuntimeSchema.fields | Where-Object { $_.readonly -eq $true }).Count
    hiddenFieldLeaked = @($NormalRuntimeSchema.fields | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    hiddenComponentLeaked = @($NormalRuntimeSchema.components | Where-Object { $_.componentCode -eq 'secret' }).Count -gt 0
    runtimeListHiddenFieldLeaked = @($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -gt 0
    forbiddenCreateStatus = $ForbiddenCreate.status
    permissionVersion = $Permission.permissionVersion
    evidenceDir = $EvidenceDir
    apiAuditPath = $BrowserAuditPath
    cleanup = $script:CleanupResult
}

$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r30-schema-runtime-result.json'
$Result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$SummaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r30-schema-runtime-2026-06-30.md'
@"
# R30 Schema-Driven Page Runtime

Status: PASS

Task: REC-P0-030 Schema-Driven Page Runtime

Base URL: $BaseUrl

Evidence:

- Machine result: $ResultFile
- API audit: $BrowserAuditPath

Assertions:

- Admin configured fields, scene, page components, and page schema for module $ModuleId.
- Page publish-check passed and publish version is $($Published.version).
- Admin published schema version equals the publish version.
- Normal runtime schema reads the same schema version.
- Normal runtime schema hides secretNote and removes the component bound to that hidden field.
- Normal runtime schema marks readable fields readonly.
- Runtime list schema also removes the hidden field.
- Direct normal-member record create is rejected with HTTP $($ForbiddenCreate.status).
- Cleanup: $($script:CleanupResult -join ', ')
"@ | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

$Result | ConvertTo-Json -Depth 60
