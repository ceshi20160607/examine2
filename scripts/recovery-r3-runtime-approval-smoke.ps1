param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$StopBeforeSubmit,
    [switch]$StopAfterSubmit
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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return $response.data
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

function Invoke-ApiExpectFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$ExpectedStatus = 403,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        Assert-True -Condition ($status -eq $ExpectedStatus) -Message "Expected HTTP $ExpectedStatus for $Method $Path but got HTTP $status."
        return @{ status = $status; failedAsExpected = $true }
    }
    throw "Expected API failure for $Method $Path but request succeeded."
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
        [bool]$Required = $false,
        [string]$DictTypeId = $null
    )
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } elseif ($Type -eq 'DATE') { 'DATE' } else { 'VARCHAR' }
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

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:TargetSystemId, $script:NormalOwnedSystemId, $script:ApproverOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r3-runtime-approval cleanup created system'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r3-$script:Suffix-$systemId"
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
        $script:CleanupResult = Remove-CreatedSystems
    }
    throw $originalError
}

$Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:TargetSystemId = $null
$script:NormalOwnedSystemId = $null
$script:ApproverOwnedSystemId = $null
$script:AdminHeaders = $null
$CleanupResult = @()

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$Target = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R3 Runtime Target $Suffix"
    systemCode = "r3_target_$Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:TargetSystemId = [string]$Target.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$TargetOption = @($Options | Where-Object { [string]$_.systemId -eq $script:TargetSystemId }) | Select-Object -First 1
$TargetTenantId = [string]$TargetOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TargetTenantId)) -Message 'Target tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r3 target setup'
}

$NormalLoginName = "r3_requester_$Suffix"
$ApproverLoginName = "r3_approver_$Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "18$Suffix"
    email = "r3_requester_$Suffix@example.com"
    password = $Password
    systemName = "R3 Requester Owned $Suffix"
    systemCode = "r3_requester_owned_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$ApproverRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $ApproverLoginName
    mobile = "17$Suffix"
    email = "r3_approver_$Suffix@example.com"
    password = $Password
    systemName = "R3 Approver Owned $Suffix"
    systemCode = "r3_approver_owned_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId
$script:ApproverOwnedSystemId = [string]$ApproverRegister.systemId

$RuntimeRole = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R3 Runtime Member $Suffix"
    roleCode = "R3_RUNTIME_MEMBER_$Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R3 normal runtime member role'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R3 Business Group $Suffix"
    sort = 10
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r3_contract_$Suffix"
    name = "R3 Contract Ledger $Suffix"
    status = 1
    description = 'Recovery R3 runtime module'
}
$ModuleId = [string]$Module.moduleId

$Fields = @()
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractName' -Name 'Contract Name' -Type 'TEXT' -Required $true)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractAmount' -Name 'Contract Amount' -Type 'NUMBER' -Required $false)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractNo' -Name 'Contract Number' -Type 'AUTO_NUMBER' -Required $false)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractFiles' -Name 'Contract Files' -Type 'ATTACHMENT' -Required $false)
$FieldIds = @($Fields | ForEach-Object { [string]$_.fieldId })

$Scene = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'All Contracts'
    defaultScene = $true
    visibleRoleIds = @([string]$RuntimeRole.roleId)
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$Fields[0].fieldId, [string]$Fields[1].fieldId)
    sortFieldIds = @([string]$Fields[1].fieldId)
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/actions" -Headers $script:AdminHeaders -Body @{
    actionCode = 'record.submitApproval'
    actionName = 'Submit Approval'
    actionType = 'ROW'
    position = 'ROW'
    selectionRule = @{
        selectionMode = 'SINGLE'
        minSelected = 1
        maxSelected = 1
        requiredStatuses = @()
        sameTenantRequired = $true
        forbiddenReason = 'Select one record first'
    }
    permissionCode = "module.r3_contract_$Suffix.submit"
    resultContract = @{
        resultType = 'WORKFLOW_TASK'
        returnsAuditLog = $true
        returnsAsyncTask = $false
        resultDrawer = 'approvalResultDrawer'
        traceField = 'traceId'
        userVisibleStates = @('PENDING', 'APPROVED', 'REJECTED')
    }
    enabled = $true
}

$Permission = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:TargetSystemId/roles/$($RuntimeRole.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ "*" = $true }
    actionPermissions = @{
        "*" = $true
        "record.create" = $true
        "record.edit" = $true
        "record.delete" = $true
        "record.submitApproval" = $true
    }
    fieldPermissions = @{ "*" = "WRITABLE" }
    dataScopeRules = @(@{ type = "ALL" })
    denyPolicies = @()
}

$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r3 module publish'
    idempotencyKey = "module-publish-r3-$Suffix"
}
$GroupPublish = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/module-groups/$($Group.groupId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r3 module group publish'
    idempotencyKey = "module-group-publish-r3-$Suffix"
}

$Flow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/flows" -Headers $script:AdminHeaders -Body @{
    flowCode = "flow_r3_$Suffix"
    flowName = "R3 Approval $Suffix"
    boundModuleId = $ModuleId
    triggerRule = @{
        triggerType = 'MODULE_ACTION'
        actionCodes = @('record.submitApproval')
        conditionExpression = $null
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @(
            @{
                nodeKey = 'node_submit_review'
                nodeType = 'approval'
                nodeName = 'Submit review'
                position = @{ x = 120; y = 120; width = 180; height = 72 }
                propertyPayload = @{
                    approvalType = 'OR_SIGN'
                    assigneeType = 'ROLE'
                    assigneeIds = @([string]$RuntimeRole.roleId)
                    allowTransfer = $true
                    allowReject = $true
                    reasonRequired = $false
                }
                status = 1
            },
            @{
                nodeKey = 'node_end_passed'
                nodeType = 'end'
                nodeName = 'Approved end'
                position = @{ x = 380; y = 120; width = 180; height = 72 }
                propertyPayload = @{}
                status = 1
            }
        )
        edges = @(
            @{
                edgeKey = 'edge_submit_end'
                sourceNodeKey = 'node_submit_review'
                targetNodeKey = 'node_end_passed'
                branchLabel = 'approved'
                conditionPayload = $null
            }
        )
    }
    description = 'Recovery R3 approval flow'
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/flows/$($Flow.flowId)/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r3 flow publish'
    idempotencyKey = "flow-publish-r3-$Suffix"
}

$RequesterMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R3 Requester Member $Suffix"
    employeeNo = "R3RQ$Suffix"
    mobile = "15$Suffix"
    email = "r3_requester_member_$Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$RequesterBinding = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members/$($RequesterMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}
$ApproverMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R3 Approver Member $Suffix"
    employeeNo = "R3AP$Suffix"
    mobile = "16$Suffix"
    email = "r3_approver_member_$Suffix@example.com"
    status = 1
    roleIds = @([string]$RuntimeRole.roleId)
}
$ApproverBinding = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/members/$($ApproverMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $ApproverLoginName
    bindMode = 'BIND'
}

$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$ApproverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $ApproverLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$ApproverHeaders = @{ Authorization = "Bearer $($ApproverLogin.accessToken)" }
$NormalSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r3 normal runtime switch'
}
$ApproverSwitch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ApproverHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    reason = 'recovery-r3 approver runtime switch'
}
Assert-True -Condition (-not (@($NormalSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) -Message 'Normal runtime user unexpectedly has SYSTEM_SUPER_ADMIN.'
Assert-True -Condition (-not (@($ApproverSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN')) -Message 'Approver runtime user unexpectedly has SYSTEM_SUPER_ADMIN.'
Assert-True -Condition ([string]$NormalSwitch.systemMemberId -ne [string]$ApproverSwitch.systemMemberId) -Message 'Requester and approver resolved to the same system member.'

$ListSchema = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/list-schema?sceneCode=default" -Headers $NormalHeaders
$SchemaActions = @($ListSchema.rowActions | ForEach-Object { $_.actionCode })
Assert-True -Condition ($SchemaActions -contains 'record.submitApproval') -Message 'Normal runtime schema does not expose submit approval row action.'
Assert-True -Condition (@($ListSchema.columns).Count -ge 3) -Message 'Normal runtime schema does not expose configured fields.'

$AttachmentFile = Join-Path ([System.IO.Path]::GetTempPath()) "r3-runtime-attachment-$Suffix.txt"
[System.IO.File]::WriteAllText($AttachmentFile, "runtime attachment evidence $Suffix", [System.Text.Encoding]::UTF8)
$Upload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=RUNTIME_RECORD' -FilePath $AttachmentFile -Headers $NormalHeaders
$UploadedFileId = [string]$Upload.file.fileId
$UploadedFileName = [string]$Upload.file.fileName
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($UploadedFileId)) -Message 'Upload did not return a file id.'

$Draft = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/drafts" -Headers $NormalHeaders -Body @{
    draftId = $null
    recordId = $null
    fieldValues = @{
        contractName = "Draft Contract $Suffix"
        contractAmount = 1000
    }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    clientVersion = 'recovery-r3@1'
    sourceType = 'manual'
}
$DraftRead = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/drafts/$($Draft.draftId)" -Headers $NormalHeaders
Assert-True -Condition ($DraftRead.fieldValues.contractName -eq "Draft Contract $Suffix") -Message 'Draft readback did not preserve field values.'
Assert-True -Condition (@($DraftRead.attachmentIds) -contains $UploadedFileId) -Message 'Draft readback did not preserve uploaded attachment id.'

$SequenceA = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/sequences/allocate" -Headers $NormalHeaders -Body @{
    tenantId = $TargetTenantId
    sequenceType = 'contractNo'
    prefix = "R3-$Suffix-"
    width = 4
    count = 2
}
$SequenceB = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/sequences/allocate" -Headers $NormalHeaders -Body @{
    tenantId = $TargetTenantId
    sequenceType = 'contractNo'
    prefix = "R3-$Suffix-"
    width = 4
    count = 1
}
Assert-True -Condition ([long]$SequenceA.lastValue + 1 -eq [long]$SequenceB.firstValue) -Message 'Sequence allocation is not continuous across batches.'

$Record = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        contractName = "Runtime Contract $Suffix"
        contractAmount = 3000
        contractNo = @($SequenceA.allocatedNos)[0]
    }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    draftId = $Draft.draftId
    sourceType = 'manual'
}
$RecordId = [string]$Record.recordId

$Search = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/search" -Headers $NormalHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $Suffix
    sceneCode = 'default'
    fieldFilters = @()
    sorts = @(@{ fieldCode = 'contractAmount'; direction = 'DESC' })
}
Assert-True -Condition ([int]$Search.page.total -ge 1) -Message 'Runtime search did not return the created record.'

$Detail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
Assert-True -Condition ($Detail.summary.recordId -eq $RecordId) -Message 'Runtime detail did not return created record.'
$DetailAttachmentIds = @($Detail.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileId })
Assert-True -Condition ($DetailAttachmentIds -contains $UploadedFileId) -Message 'Runtime detail did not include uploaded attachment after record create.'
$DetailAttachmentNames = @($Detail.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileName })
Assert-True -Condition ($DetailAttachmentNames -contains $UploadedFileName) -Message 'Runtime detail did not preserve uploaded attachment file name.'

$BindResult = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId/attachments" -Headers $NormalHeaders -Body @{
    fileIds = @($UploadedFileId)
    bindMode = 'REPLACE'
    sourceType = 'recovery-r3'
    mobileCaptureMeta = @{
        source = 'smoke'
        suffix = $Suffix
    }
}
Assert-True -Condition (@($BindResult.attachments | ForEach-Object { $_.fileId }) -contains $UploadedFileId) -Message 'Attachment bind endpoint did not bind uploaded file.'

$Updated = Invoke-Api -Method 'Patch' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders -Body @{
    fieldValues = @{
        contractName = "Runtime Contract Updated $Suffix"
        contractAmount = 3600
        contractNo = @($SequenceA.allocatedNos)[0]
    }
    childRows = @{}
    attachmentIds = @($UploadedFileId)
    draftId = $null
    sourceType = 'manual'
}
Assert-True -Condition ($Updated.recordId -eq $RecordId) -Message 'Runtime update did not return target record id.'

$DetailAfterUpdate = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$AttachmentAfterUpdate = @($DetailAfterUpdate.tabs | ForEach-Object { @($_.payload.attachments) } | ForEach-Object { $_.fileId })
Assert-True -Condition ($AttachmentAfterUpdate -contains $UploadedFileId) -Message 'Runtime detail did not preserve uploaded attachment after update.'

$History = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId/history?pageNo=1&pageSize=20" -Headers $NormalHeaders
Assert-True -Condition ([int]$History.total -ge 2) -Message 'Runtime history did not include create and update entries.'

if ($StopBeforeSubmit) {
    [ordered]@{
        status = 'PASS'
        task = 'REC-P0-005,REC-P0-006-setup-before-submit'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        suffix = $Suffix
        password = $Password
        targetSystemId = $script:TargetSystemId
        targetTenantId = $TargetTenantId
        requesterAccount = $NormalLoginName
        approverAccount = $ApproverLoginName
        requesterAccountId = $NormalLogin.profile.accountId
        approverAccountId = $ApproverLogin.profile.accountId
        requesterMemberId = $RequesterMember.systemMemberId
        approverMemberId = $ApproverMember.systemMemberId
        moduleId = $ModuleId
        moduleGroupId = $Group.groupId
        moduleGroupPublishedVersion = $GroupPublish.publishedVersion
        flowId = $Flow.flowId
        flowCode = $Flow.flowCode
        recordId = $RecordId
        recordTitle = "Runtime Contract Updated $Suffix"
        runtimeSchemaActionCount = @($ListSchema.rowActions).Count
        runtimeSchemaColumnCount = @($ListSchema.columns).Count
        historyTotal = $History.total
        normalOwnedSystemId = $script:NormalOwnedSystemId
        approverOwnedSystemId = $script:ApproverOwnedSystemId
        cleanup = if ($KeepCreatedData) { @('SKIPPED') } else { Remove-CreatedSystems }
    } | ConvertTo-Json -Depth 30
    return
}

$Approval = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId/actions/record.submitApproval" -Headers $NormalHeaders -Body @{
    parameters = @{}
    selectedRecordIds = @($RecordId)
    reason = 'recovery-r3 submit approval'
    sourceType = 'manual'
}
Assert-True -Condition ([bool]$Approval.accepted) -Message 'Approval submit action was not accepted.'

$DetailAfterSubmit = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$PendingTaskId = [string]$DetailAfterSubmit.approvalSidebar.pendingTaskId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($PendingTaskId)) -Message 'Approval sidebar has no pending task id after submit.'

$RequesterTodos = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/todos/search?pageNo=1&pageSize=20" -Headers $NormalHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $null
    status = 'PENDING'
    priority = $null
    assigneeId = $NormalLogin.profile.accountId
    dueRange = $null
    traceId = $null
}
$ApproverTodos = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $null
    status = 'PENDING'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
    traceId = $null
}
$Messages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:TargetSystemId
    tenantId = $TargetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'active'
    timeRange = $null
    keyword = $null
}
Assert-True -Condition ([int]$RequesterTodos.page.total -eq 0) -Message 'Requester incorrectly received the approval todo.'
Assert-True -Condition ([int]$ApproverTodos.page.total -gt 0) -Message 'Approval submit did not create a visible todo for the approver.'
Assert-True -Condition ([int]$Messages.total -gt 0) -Message 'Approval submit did not create a visible message for the approver.'

if ($StopAfterSubmit) {
    $PendingTodo = @($ApproverTodos.page.records) | Select-Object -First 1
    $PendingMessage = @($Messages.records) | Select-Object -First 1
    [ordered]@{
        status = 'PASS'
        task = 'REC-P0-005,REC-P0-006-setup'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        suffix = $Suffix
        password = $Password
        targetSystemId = $script:TargetSystemId
        targetTenantId = $TargetTenantId
        requesterAccount = $NormalLoginName
        approverAccount = $ApproverLoginName
        requesterAccountId = $NormalLogin.profile.accountId
        approverAccountId = $ApproverLogin.profile.accountId
        requesterMemberId = $RequesterMember.systemMemberId
        approverMemberId = $ApproverMember.systemMemberId
        moduleId = $ModuleId
        moduleGroupId = $Group.groupId
        moduleGroupPublishedVersion = $GroupPublish.publishedVersion
        recordId = $RecordId
        pendingTaskId = $PendingTaskId
        pendingTodoId = $PendingTodo.todoId
        pendingMessageId = $PendingMessage.messageId
        requesterPendingTodoTotal = $RequesterTodos.page.total
        approverPendingTodoTotal = $ApproverTodos.page.total
        messageTotal = $Messages.total
        normalOwnedSystemId = $script:NormalOwnedSystemId
        approverOwnedSystemId = $script:ApproverOwnedSystemId
        cleanup = if ($KeepCreatedData) { @('SKIPPED') } else { Remove-CreatedSystems }
    } | ConvertTo-Json -Depth 30
    return
}

$ApproveKey = "approve-r3-$Suffix"
$RequesterApproveFailure = Invoke-ApiExpectFailure -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/approval-tasks/$PendingTaskId/approve" -Headers $NormalHeaders -ExpectedStatus 403 -Body @{
    idempotencyKey = "requester-approve-denied-$Suffix"
    comment = 'Requester should not approve own submitted task'
    reason = $null
    transferTargetId = $null
    transferTargetName = $null
    fieldValues = @{}
    permissionSnapshotId = $DetailAfterSubmit.permissionSnapshotVersion
}
$Approved = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/approval-tasks/$PendingTaskId/approve" -Headers $ApproverHeaders -Body @{
    idempotencyKey = $ApproveKey
    comment = 'Approved by recovery R3 smoke'
    reason = $null
    transferTargetId = $null
    transferTargetName = $null
    fieldValues = @{}
    permissionSnapshotId = $DetailAfterSubmit.permissionSnapshotVersion
}
Assert-True -Condition ($Approved.taskStatus -eq 'APPROVED' -and $Approved.instanceStatus -eq 'APPROVED') -Message 'Approval did not move task and instance to APPROVED.'

$DetailAfterApprove = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:TargetSystemId/runtime/modules/$ModuleId/records/$RecordId" -Headers $NormalHeaders
$SubmitAfterApprove = @($DetailAfterApprove.actions | Where-Object { $_.actionCode -eq 'record.submitApproval' }) | Select-Object -First 1
Assert-True -Condition ($DetailAfterApprove.summary.status -eq 'APPROVED') -Message 'Business record detail did not reflect approved terminal status.'
Assert-True -Condition ($DetailAfterApprove.approvalSidebar.visible -eq $true -and $DetailAfterApprove.approvalSidebar.status -eq 'APPROVED') -Message 'Approval sidebar did not keep approved terminal instance visible.'
Assert-True -Condition ([string]::IsNullOrWhiteSpace([string]$DetailAfterApprove.approvalSidebar.pendingTaskId)) -Message 'Approved approval sidebar still exposes a pending task id.'
Assert-True -Condition ($SubmitAfterApprove -and $SubmitAfterApprove.enabled -eq $false) -Message 'Submit approval action remained enabled after approval terminal status.'

$PendingTodosAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $RecordId
    status = 'PENDING'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
    traceId = $null
}
$HandledTodosAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $RecordId
    status = 'HANDLED'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
    traceId = $null
}
Assert-True -Condition ([int]$PendingTodosAfterApprove.page.total -eq 0) -Message 'Approved approval task still appears as a pending todo.'
Assert-True -Condition ([int]$HandledTodosAfterApprove.page.total -gt 0) -Message 'Approved approval task did not leave a handled todo readback.'

$ApprovedDuplicate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:TargetSystemId/approval-tasks/$PendingTaskId/approve" -Headers $ApproverHeaders -Body @{
    idempotencyKey = $ApproveKey
    comment = 'Approved by recovery R3 smoke'
    reason = $null
    transferTargetId = $null
    transferTargetName = $null
    fieldValues = @{}
    permissionSnapshotId = $DetailAfterSubmit.permissionSnapshotVersion
}
Assert-True -Condition ([bool]$ApprovedDuplicate.duplicate) -Message 'Duplicate approval with same idempotency key was not returned as duplicate.'

$CleanupResult = Remove-CreatedSystems
if (Test-Path -LiteralPath $AttachmentFile) {
    Remove-Item -LiteralPath $AttachmentFile -Force
}

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-005,REC-P0-006'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    targetSystemId = $script:TargetSystemId
    targetTenantId = $TargetTenantId
    requesterAccount = $NormalLoginName
    approverAccount = $ApproverLoginName
    normalOwnedSystemId = $script:NormalOwnedSystemId
    approverOwnedSystemId = $script:ApproverOwnedSystemId
    runtimeRoleId = $RuntimeRole.roleId
    requesterMemberId = $RequesterMember.systemMemberId
    requesterBindingId = $RequesterBinding.bindingId
    approverMemberId = $ApproverMember.systemMemberId
    approverBindingId = $ApproverBinding.bindingId
    moduleId = $ModuleId
    moduleGroupId = $Group.groupId
    moduleGroupPublishedVersion = $GroupPublish.publishedVersion
    sceneId = $Scene.sceneId
    permissionVersion = $Permission.permissionVersion
    runtimeSchemaActionCount = @($ListSchema.rowActions).Count
    runtimeSchemaColumnCount = @($ListSchema.columns).Count
    requesterEffectiveRoles = $NormalSwitch.effectiveRoleIds
    approverEffectiveRoles = $ApproverSwitch.effectiveRoleIds
    draftId = $Draft.draftId
    sequenceA = $SequenceA.allocatedNos
    sequenceB = $SequenceB.allocatedNos
    recordId = $RecordId
    uploadedFileId = $UploadedFileId
    uploadedFileName = $UploadedFileName
    attachmentBindCount = @($BindResult.attachments).Count
    searchTotal = $Search.page.total
    historyTotal = $History.total
    approvalAccepted = $Approval.accepted
    pendingTaskId = $PendingTaskId
    requesterPendingTodoTotal = $RequesterTodos.page.total
    approverPendingTodoTotal = $ApproverTodos.page.total
    messageTotal = $Messages.total
    requesterApproveDeniedStatus = $RequesterApproveFailure.status
    approvedTaskStatus = $Approved.taskStatus
    approvedInstanceStatus = $Approved.instanceStatus
    duplicateApproval = $ApprovedDuplicate.duplicate
    requesterAndApproverAreSameCurrentLimitation = $false
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
