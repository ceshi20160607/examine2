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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 12 -Compress)"
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
    param([string]$Code, [string]$Name)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = 'TEXT'
        storageType = 'VARCHAR'
        required = $true
        sortable = $true
        dictTypeId = $null
        permissionMetadata = $null
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $true
            duplicateKey = 'none'
            desensitizeMode = 'none'
        }
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:Headers) {
        return 'SKIPPED'
    }
    $result = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:Headers -Body @{
        reason = 'recovery-r2-tenant-business-redraw cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r2-tenant-redraw-$script:Suffix"
    }
    return [string]$result.result
}

function Switch-Tenant {
    param([string]$TenantId, [string]$Reason)
    $switched = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/tenant-switch" -Headers $script:Headers -Body @{
        tenantId = $TenantId
        reason = $Reason
    }
    Assert-True -Condition ([string]$switched.tenantId -eq $TenantId) -Message "Tenant switch mismatch for $TenantId."
    $current = Invoke-Api -Method 'Get' -Path '/api/v1/context/current-system' -Headers $script:Headers
    Assert-True -Condition ([string]$current.systemId -eq $script:SystemId -and [string]$current.tenantId -eq $TenantId) `
        -Message "Current context did not persist tenant $TenantId."
    return $current
}

function New-PublishedTenantModuleWithRecord {
    param(
        [string]$TenantLabel,
        [string]$ModuleCode,
        [string]$FieldCode,
        [string]$RecordTitle
    )

    $group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:Headers -Body @{
        name = "$TenantLabel Group $script:Suffix"
        sort = 10
        visibleRoleIds = @()
        publishStatus = 'DRAFT'
    }
    $module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:Headers -Body @{
        groupId = [string]$group.groupId
        moduleCode = $ModuleCode
        name = "$TenantLabel Ledger $script:Suffix"
        status = 1
        description = "Tenant redraw smoke module for $TenantLabel"
    }
    $field = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$($module.moduleId)/fields" -Headers $script:Headers -Body (New-FieldBody -Code $FieldCode -Name "$TenantLabel Value")
    $null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$($module.moduleId)/scenes" -Headers $script:Headers -Body @{
        sceneCode = 'default'
        sceneName = 'All'
        defaultScene = $true
        visibleRoleIds = @()
        columnFieldIds = @([string]$field.fieldId)
        filterFieldIds = @([string]$field.fieldId)
        sortFieldIds = @([string]$field.fieldId)
    }
    $check = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$($module.moduleId)/publish-check" -Headers $script:Headers -Body @{
        reason = "tenant redraw publish check $TenantLabel"
        idempotencyKey = "tenant-redraw-check-$TenantLabel-$script:Suffix"
    }
    Assert-True -Condition ([bool]$check.passed) -Message "Publish check failed for $TenantLabel."
    $publish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$($module.moduleId)/publish" -Headers $script:Headers -Body @{
        reason = "tenant redraw publish $TenantLabel"
        idempotencyKey = "tenant-redraw-publish-$TenantLabel-$script:Suffix"
    }
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$publish.version)) -Message "Publish did not return version for $TenantLabel."

    $fieldValues = @{}
    $fieldValues[$FieldCode] = $RecordTitle
    $record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$($module.moduleId)/records" -Headers $script:Headers -Body @{
        fieldValues = $fieldValues
        childRows = @{}
        attachmentIds = @()
        draftId = $null
        sourceType = 'tenant-redraw-smoke'
    }
    return @{
        tenantLabel = $TenantLabel
        groupId = [string]$group.groupId
        moduleId = [string]$module.moduleId
        moduleCode = [string]$module.moduleCode
        moduleName = [string]$module.name
        fieldId = [string]$field.fieldId
        recordId = [string]$record.recordId
        recordTitle = $RecordTitle
        publishVersion = [string]$publish.version
    }
}

function Assert-TenantBusinessView {
    param(
        [hashtable]$Expected,
        [hashtable]$Unexpected,
        [string]$TenantId
    )

    $modules = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules?pageNo=1&pageSize=50" -Headers $script:Headers
    $moduleIds = @($modules.records | ForEach-Object { [string]$_.moduleId })
    Assert-True -Condition ($moduleIds -contains $Expected.moduleId) -Message "Expected module is missing after switching to tenant $TenantId."
    Assert-True -Condition (-not ($moduleIds -contains $Unexpected.moduleId)) -Message "Unexpected other-tenant module leaked after switching to tenant $TenantId."

    $searchExpected = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$($Expected.moduleId)/records/search" -Headers $script:Headers -Body @{
        pageNo = 1
        pageSize = 20
        keyword = $Expected.recordTitle
        sceneCode = 'default'
        fieldFilters = @()
        sorts = @()
    }
    Assert-True -Condition ([int]$searchExpected.page.total -eq 1) -Message "Expected tenant record was not visible for tenant $TenantId."
    $titles = @($searchExpected.page.records | ForEach-Object { [string]$_.title })
    Assert-True -Condition ($titles -contains $Expected.recordTitle) -Message "Expected tenant record title mismatch for tenant $TenantId."

    $searchUnexpected = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$($Expected.moduleId)/records/search" -Headers $script:Headers -Body @{
        pageNo = 1
        pageSize = 20
        keyword = $Unexpected.recordTitle
        sceneCode = 'default'
        fieldFilters = @()
        sorts = @()
    }
    Assert-True -Condition ([int]$searchUnexpected.page.total -eq 0) -Message "Other-tenant record leaked into tenant $TenantId search."

    return @{
        tenantId = $TenantId
        visibleModuleId = $Expected.moduleId
        hiddenOtherTenantModuleId = $Unexpected.moduleId
        visibleRecordId = $Expected.recordId
        visibleRecordTitle = $Expected.recordTitle
        searchTotal = [int]$searchExpected.page.total
    }
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystem
        } catch {
            Write-Error "Cleanup created system failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$script:Suffix = Get-Date -Format 'MMddHHmmss'
$script:SystemId = $null
$script:Headers = $null
$CleanupResult = 'SKIPPED'

$health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($health | ConvertTo-Json -Depth 12 -Compress)"

$login = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$token = [string]$login.accessToken
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($token)) -Message 'Default admin login did not return an access token.'
$script:Headers = @{ Authorization = "Bearer $token" }

$created = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:Headers -Body @{
    systemName = "R2 Tenant Redraw $script:Suffix"
    systemCode = "r2redraw_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:SystemId = [string]$created.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:Headers
$createdOption = @($options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
Assert-True -Condition ($null -ne $createdOption) -Message 'Created system missing from switch options.'
$tenantA = [string]$createdOption.tenantId

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:Headers -Body @{
    systemId = $script:SystemId
    tenantId = $tenantA
    reason = 'recovery-r2 tenant business redraw tenant A'
}
$contextA = Switch-Tenant -TenantId $tenantA -Reason 'recovery-r2 tenant business redraw tenant A confirm'

$tenantB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/tenants" -Headers $script:Headers -Body @{
    tenantCode = "tenant_b_$script:Suffix"
    tenantName = "Tenant B $script:Suffix"
    status = 1
}
$tenantBId = [string]$tenantB.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($tenantBId)) -Message 'Second tenant create did not return tenantId.'

$tenantAData = New-PublishedTenantModuleWithRecord -TenantLabel 'TenantA' -ModuleCode "tenant_a_asset_$script:Suffix" -FieldCode "tenant_a_value_$script:Suffix" -RecordTitle "Tenant A Record $script:Suffix"

$contextB = Switch-Tenant -TenantId $tenantBId -Reason 'recovery-r2 tenant business redraw tenant B'
$tenantBData = New-PublishedTenantModuleWithRecord -TenantLabel 'TenantB' -ModuleCode "tenant_b_asset_$script:Suffix" -FieldCode "tenant_b_value_$script:Suffix" -RecordTitle "Tenant B Record $script:Suffix"

$tenantBView = Assert-TenantBusinessView -Expected $tenantBData -Unexpected $tenantAData -TenantId $tenantBId
$contextBackA = Switch-Tenant -TenantId $tenantA -Reason 'recovery-r2 tenant business redraw back to tenant A'
$tenantAView = Assert-TenantBusinessView -Expected $tenantAData -Unexpected $tenantBData -TenantId $tenantA

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-003'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantA = @{
        tenantId = $tenantA
        contextMemberId = $contextA.systemMemberId
        moduleId = $tenantAData.moduleId
        recordId = $tenantAData.recordId
        recordTitle = $tenantAData.recordTitle
        view = $tenantAView
    }
    tenantB = @{
        tenantId = $tenantBId
        contextMemberId = $contextB.systemMemberId
        moduleId = $tenantBData.moduleId
        recordId = $tenantBData.recordId
        recordTitle = $tenantBData.recordTitle
        view = $tenantBView
    }
    contextBackA = @{
        tenantId = $contextBackA.tenantId
        systemMemberId = $contextBackA.systemMemberId
        permissionSnapshot = $contextBackA.permissionSnapshotSummary
    }
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
