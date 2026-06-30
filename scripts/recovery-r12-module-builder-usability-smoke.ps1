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
    $allHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $allHeaders[$key] = $Headers[$key]
    }
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    try {
        $response = if ($null -eq $jsonBody) {
            Invoke-RestMethod -Method $Method -Uri $uri -Headers $allHeaders -TimeoutSec 30
        } else {
            Invoke-RestMethod -Method $Method -Uri $uri -Headers $allHeaders `
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
    param(
        [string]$Code,
        [string]$Name,
        [string]$Type,
        [bool]$Required,
        [string]$DictTypeId = $null
    )
    $storageType = switch ($Type) {
        'DATE' { 'DATETIME' }
        'ATTACHMENT' { 'JSON' }
        default { 'VARCHAR' }
    }
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = $storageType
        required = $Required
        sortable = $true
        dictTypeId = $DictTypeId
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

function Remove-CreatedSystem {
    param([string]$SystemId, [hashtable]$Headers, [string]$Reason)
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($SystemId)) {
        return 'SKIPPED'
    }
    $result = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$SystemId" -Headers $Headers -Body @{
        reason = $Reason
        impactScope = 'created_by_recovery_r12'
        idempotencyKey = "cleanup-r12-$SystemId-$(Get-Date -Format 'yyyyMMddHHmmss')"
    }
    return [string]$result.result
}

function Assert-DefaultDepartment {
    param([string]$SystemId, [hashtable]$Headers, [string]$PathName)

    $switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $Headers -Body @{
        systemId = $SystemId
        tenantId = $null
        reason = "recovery-r12-$PathName"
    }
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$switch.tenantId)) -Message "$PathName switch did not return tenantId."

    $departments = @(Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/org/departments" -Headers $Headers)
    $defaultDepartment = @($departments | Where-Object {
        $_.deptCode -eq 'default_department'
    }) | Select-Object -First 1
    Assert-True -Condition ($null -ne $defaultDepartment) -Message "$PathName did not create default_department."
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$defaultDepartment.deptName)) -Message "$PathName default_department did not return a department name."
    Assert-True -Condition ([string]$defaultDepartment.parentId -eq '0' -or [string]::IsNullOrWhiteSpace([string]$defaultDepartment.parentId)) -Message "$PathName default department should be root."

    $members = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$SystemId/members?pageNo=1&pageSize=20" -Headers $Headers
    $memberRecords = @($members.records)
    Assert-True -Condition ($memberRecords.Count -gt 0) -Message "$PathName did not create an owner member."
    $ownerMember = @($memberRecords | Where-Object { [string]$_.deptId -eq [string]$defaultDepartment.deptId }) | Select-Object -First 1
    Assert-True -Condition ($null -ne $ownerMember) -Message "$PathName owner member is not bound to the default department."

    return @{
        tenantId = [string]$switch.tenantId
        departmentId = [string]$defaultDepartment.deptId
        memberId = [string]$ownerMember.systemMemberId
    }
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        foreach ($systemId in @($script:PlatformSystemId, $script:RegisterSystemId)) {
            try {
                if (-not [string]::IsNullOrWhiteSpace([string]$systemId) -and $null -ne $script:AdminHeaders) {
                    $null = Remove-CreatedSystem -SystemId ([string]$systemId) -Headers $script:AdminHeaders -Reason 'recovery-r12 cleanup after failure'
                }
            } catch {
                Write-Error "Cleanup created system failed: $($_.Exception.Message)"
            }
        }
    }
    throw $originalError
}

$Suffix = Get-Date -Format 'MMddHHmmss'
$Password = 'Aa123456!'
$PlatformCleanup = 'SKIPPED'
$RegisterCleanup = 'SKIPPED'

$adminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AdminHeaders = @{ Authorization = "Bearer $($adminLogin.accessToken)" }
Assert-True -Condition (@($adminLogin.profile.platformRoles) -contains 'PLATFORM_ROOT') -Message 'Default admin is not PLATFORM_ROOT.'

$platformSystem = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $AdminHeaders -Body @{
    systemName = "R12 Platform System $Suffix"
    systemCode = "r12_platform_$Suffix"
    tenantMode = 1
}
$PlatformSystemId = [string]$platformSystem.systemId
$PlatformDefault = Assert-DefaultDepartment -SystemId $PlatformSystemId -Headers $AdminHeaders -PathName 'platform-create'

$group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/module-groups" -Headers $AdminHeaders -Body @{
    name = "R12 Group $Suffix"
    sort = 10
    visibleRoleIds = @()
    publishStatus = 'DRAFT'
}
$module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules" -Headers $AdminHeaders -Body @{
    groupId = [string]$group.groupId
    moduleCode = "r12_asset_$Suffix"
    name = "R12 Asset Ledger $Suffix"
    status = 1
    description = 'Recovery R12 module builder usability smoke'
}
$moduleId = [string]$module.moduleId

$dictType = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/dict-types" -Headers $AdminHeaders -Body @{
    dictCode = "r12_status_$Suffix"
    dictName = "R12 Status $Suffix"
    dictKind = 'STATUS'
    status = 1
}
$dictTypeId = [string]$dictType.dictTypeId
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/dict-types/$dictTypeId/items" -Headers $AdminHeaders -Body @{
    parentId = $null
    itemCode = 'active'
    itemName = 'Active'
    color = '#16a34a'
    icon = 'check'
    semantic = 'success'
    sort = 10
    defaultFlag = $true
    kanbanEnabled = $true
    status = 1
}

$fields = @()
$fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields" -Headers $AdminHeaders -Body (New-FieldBody -Code 'assetName' -Name 'Asset Name' -Type 'TEXT' -Required $true)
$fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields" -Headers $AdminHeaders -Body (New-FieldBody -Code 'purchaseDate' -Name 'Purchase Date' -Type 'DATE' -Required $false)
$fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields" -Headers $AdminHeaders -Body (New-FieldBody -Code 'assetStatus' -Name 'Asset Status' -Type 'SELECT' -Required $true -DictTypeId $dictTypeId)
$fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields" -Headers $AdminHeaders -Body (New-FieldBody -Code 'assetFiles' -Name 'Asset Files' -Type 'ATTACHMENT' -Required $false)
$fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields" -Headers $AdminHeaders -Body (New-FieldBody -Code 'assetNo' -Name 'Asset Number' -Type 'AUTO_NUMBER' -Required $false)

$readFields = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/fields?pageNo=1&pageSize=20" -Headers $AdminHeaders
$fieldTypes = @($readFields.records | ForEach-Object { [string]$_.fieldType })
foreach ($type in @('TEXT', 'DATE', 'SELECT', 'ATTACHMENT', 'AUTO_NUMBER')) {
    Assert-True -Condition ($fieldTypes -contains $type) -Message "Field type readback missing $type."
}

$fieldIds = @($fields | ForEach-Object { [string]$_.fieldId })
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/scenes" -Headers $AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'All R12 Assets'
    defaultScene = $true
    visibleRoleIds = @()
    columnFieldIds = $fieldIds
    filterFieldIds = @([string]$fields[0].fieldId, [string]$fields[2].fieldId)
    sortFieldIds = @([string]$fields[1].fieldId)
}
$publishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/publish-check" -Headers $AdminHeaders -Body @{
    reason = 'recovery-r12 publish check'
    idempotencyKey = "r12-check-$Suffix"
}
Assert-True -Condition ([bool]$publishCheck.passed) -Message "R12 publish check failed: $($publishCheck | ConvertTo-Json -Depth 12 -Compress)"
$publish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$PlatformSystemId/modules/$moduleId/publish" -Headers $AdminHeaders -Body @{
    reason = 'recovery-r12 module publish'
    idempotencyKey = "r12-publish-$Suffix"
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$publish.version)) -Message 'R12 publish did not return a version.'

$register = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = "r12_register_admin_$Suffix"
    mobile = "17$Suffix"
    email = "r12_register_$Suffix@example.com"
    password = $Password
    systemName = "R12 Register System $Suffix"
    systemCode = "r12_register_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$RegisterSystemId = [string]$register.systemId
$RegisterHeaders = @{ Authorization = "Bearer $($register.accessToken)" }
Assert-True -Condition (@($register.initGuideSteps) -contains 'CREATE_DEFAULT_DEPARTMENT') -Message 'register-with-system initGuideSteps do not include CREATE_DEFAULT_DEPARTMENT.'
$RegisterDefault = Assert-DefaultDepartment -SystemId $RegisterSystemId -Headers $RegisterHeaders -PathName 'register-with-system'

$PlatformCleanup = Remove-CreatedSystem -SystemId $PlatformSystemId -Headers $AdminHeaders -Reason 'recovery-r12 cleanup platform-created system'
$RegisterCleanup = Remove-CreatedSystem -SystemId $RegisterSystemId -Headers $AdminHeaders -Reason 'recovery-r12 cleanup register-created system'

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-015'
    suffix = $Suffix
    platformCreate = @{
        systemId = $PlatformSystemId
        tenantId = $PlatformDefault.tenantId
        defaultDepartmentId = $PlatformDefault.departmentId
        ownerMemberId = $PlatformDefault.memberId
        moduleId = $moduleId
        fieldTypes = $fieldTypes
        publishVersion = $publish.version
        cleanup = $PlatformCleanup
    }
    registerWithSystem = @{
        systemId = $RegisterSystemId
        tenantId = $RegisterDefault.tenantId
        defaultDepartmentId = $RegisterDefault.departmentId
        ownerMemberId = $RegisterDefault.memberId
        initGuideSteps = $register.initGuideSteps
        cleanup = $RegisterCleanup
    }
} | ConvertTo-Json -Depth 20
