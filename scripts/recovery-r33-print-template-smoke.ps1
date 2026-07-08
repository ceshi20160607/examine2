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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
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
        if ($status -eq 403 -or $body -match 'PERMISSION_DENIED|AUTH_UNAUTHORIZED|FIELD_VALIDATION_FAILED') {
            return @{ status = $status; body = $body }
        }
        throw "Expected forbidden-like failure but got HTTP $status $body"
    }
    throw "Expected forbidden-like failure but request succeeded: $Method $Path"
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
        required = $Required
        sortable = $true
        filterOperators = @('EQ', 'LIKE')
    }
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
                reason = 'recovery-R33 print template cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-R33-$script:Suffix-$systemId"
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

$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r33-print-template'
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
$Password = '123123aa'
$NameCode = "invoiceName_$($script:Suffix.Replace('-', '_'))"
$AmountCode = "invoiceAmount_$($script:Suffix.Replace('-', '_'))"
$OwnerCode = "ownerDept_$($script:Suffix.Replace('-', '_'))"

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R33 Print Template $script:Suffix"
    systemCode = "r33_print_$script:Suffix"
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
    reason = 'recovery-r33 setup'
}

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R33 Runtime Member $script:Suffix"
    roleCode = "R33_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R33 print template runtime role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R33 Print Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r33_invoice_$script:Suffix"
    name = "R33 Invoice $script:Suffix"
    status = 1
    description = 'Recovery R33 print template module'
}
$ModuleId = [string]$Module.moduleId

$NameField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $NameCode -Name 'Invoice Name' -Type 'TEXT' -Required $true)
$AmountField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $AmountCode -Name 'Invoice Amount' -Type 'NUMBER')
$OwnerField = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code $OwnerCode -Name 'Owner Dept' -Type 'TEXT')

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'R33 Default'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = @([string]$NameField.fieldId, [string]$AmountField.fieldId, [string]$OwnerField.fieldId)
    filterFieldIds = @([string]$NameField.fieldId)
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
    reason = 'recovery-r33 module publish'
    idempotencyKey = "module-publish-r33-$script:Suffix"
}

$TemplateCode = "print_r33_$script:Suffix"
$DraftTemplate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    templateName = "R33 Invoice Print $script:Suffix"
    version = "draft_$script:Suffix"
    status = 1
    defaultTemplate = $true
    visibleRoleIds = @()
    boundFieldCodes = @($NameCode, $AmountCode, $OwnerCode)
    detailTableFieldCodes = @($NameCode, $AmountCode)
    signatureLabels = @('Prepared by', 'Approved by')
    headerText = 'R33 Invoice Print Header'
    footerText = 'R33 Print Footer'
    previewFileId = "preview_r33_$script:Suffix"
    pageSetup = @{ paper = 'A4'; orientation = 'PORTRAIT' }
}
Assert-True -Condition ($DraftTemplate.publishStatus -eq 'DRAFT' -and @($DraftTemplate.boundFieldCodes).Count -eq 3) -Message 'Draft print template was not saved with expected fields.'

$AdminPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/preview" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    sampleValues = @{
        $NameCode = "Sample Invoice $script:Suffix"
        $AmountCode = 8800
        $OwnerCode = 'Finance'
    }
}
Assert-True -Condition ($AdminPreview.publishStatus -eq 'DRAFT') -Message 'Admin preview should read draft template.'
Assert-True -Condition (($AdminPreview | ConvertTo-Json -Depth 30 -Compress) -match 'Sample Invoice') -Message 'Admin preview did not merge sample values.'

$NormalLoginName = "r33_print_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r33_print_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R33 Owned $script:Suffix"
    systemCode = "r33_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:CreatedSystemIds.Add([string]$NormalRegister.systemId) | Out-Null

$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R33 Print Member $script:Suffix"
    employeeNo = "R33PM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r33_print_runtime_member_$script:Suffix@example.com"
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
    reason = 'recovery-r33 normal member print preview'
}

$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records" -Headers $script:AdminHeaders -Body @{
    fieldValues = @{
        $NameCode = "R33 Runtime Invoice $script:Suffix"
        $AmountCode = 12800
        $OwnerCode = 'Finance Ops'
    }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'R33_PRINT_TEMPLATE_SMOKE'
}
$RecordId = [string]$Record.recordId

$DraftDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-preview" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}

$Check = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($Check.passed -eq $true -and @($Check.impactRefs).Count -ge 1) -Message 'Print template publish-check did not pass with impact refs.'

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r33 print template publish'
    idempotencyKey = "print-publish-r33-$script:Suffix"
}
Assert-True -Condition ($Published.result -eq 'PUBLISHED_PRINT_TEMPLATE' -and -not [string]::IsNullOrWhiteSpace($Published.version)) -Message 'Print template publish did not return a version.'

$RuntimePreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-preview" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}
$RuntimePreviewJson = $RuntimePreview | ConvertTo-Json -Depth 60 -Compress
Assert-True -Condition ($RuntimePreview.publishStatus -eq 'PUBLISHED') -Message 'Runtime preview did not use published template.'
Assert-True -Condition ($RuntimePreview.version -eq $Published.version) -Message 'Runtime preview version does not match published version.'
Assert-True -Condition ($RuntimePreviewJson -match 'R33 Runtime Invoice' -and $RuntimePreviewJson -match '12800') -Message 'Runtime preview did not merge record values.'

$RuntimeExport = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/runtime/modules/$ModuleId/records/$RecordId/print-export" -Headers $NormalHeaders -Body @{
    templateCode = $TemplateCode
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($RuntimeExport.exportFileId)) -Message 'Runtime export did not return exportFileId.'

$NormalAdminDenied = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $NormalHeaders -Body @{
    templateCode = "illegal_$script:Suffix"
    templateName = 'Normal Member Illegal Template'
    boundFieldCodes = @($NameCode)
}

$ApiAuditPath = Join-Path $EvidenceDir 'print-template-api-audit.json'
$ApiAudit = [ordered]@{
    systemId = $SystemId
    moduleId = $ModuleId
    recordId = $RecordId
    templateCode = $TemplateCode
    draftStatus = $DraftTemplate.publishStatus
    adminPreviewTraceId = $AdminPreview.traceId
    draftRuntimeDeniedStatus = $DraftDenied.status
    publishCheckPassed = $Check.passed
    publishedVersion = $Published.version
    runtimePreviewVersion = $RuntimePreview.version
    runtimePreviewTraceId = $RuntimePreview.traceId
    runtimeExportFileId = $RuntimeExport.exportFileId
    normalAdminDeniedStatus = $NormalAdminDenied.status
}
$ApiAudit | ConvertTo-Json -Depth 50 | Set-Content -Encoding UTF8 -Path $ApiAuditPath

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-033'
    baseUrl = $BaseUrl
    systemId = $SystemId
    moduleId = $ModuleId
    recordId = $RecordId
    templateCode = $TemplateCode
    draftStatus = $DraftTemplate.publishStatus
    publishCheckPassed = $Check.passed
    publishedVersion = $Published.version
    runtimePreviewVersion = $RuntimePreview.version
    runtimeExportFileId = $RuntimeExport.exportFileId
    draftRuntimeDeniedStatus = $DraftDenied.status
    normalAdminDeniedStatus = $NormalAdminDenied.status
    cleanup = $script:CleanupResult
    apiAuditPath = $ApiAuditPath
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r33-print-template-result.json'
$Result | ConvertTo-Json -Depth 50 | Set-Content -Encoding UTF8 -Path $resultPath

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r33-print-template-2026-06-30.md'
$summary = @(
    '# R33 Print Template Visual Designer First Loop Evidence',
    '',
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Module: $ModuleId",
    "- Record: $RecordId",
    "- Template: $TemplateCode",
    "- Draft status: $($DraftTemplate.publishStatus)",
    "- Publish check passed: $($Check.passed)",
    "- Published version: $($Published.version)",
    "- Runtime preview version: $($RuntimePreview.version)",
    "- Runtime export file id: $($RuntimeExport.exportFileId)",
    "- Draft runtime preview denied status: $($DraftDenied.status)",
    "- Normal member admin template write denied status: $($NormalAdminDenied.status)",
    "- Cleanup: $($script:CleanupResult -join ', ')",
    '',
    'Evidence files:',
    "- $resultPath",
    "- $ApiAuditPath"
)
$summary | Set-Content -Encoding UTF8 -Path $summaryPath

$Result | ConvertTo-Json -Depth 50
