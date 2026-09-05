$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString().Substring(7)
$username = "c66_live_$suffix"
$password = 'Correct-c66-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [switch]$AllowError)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method; Uri = "$apiBase$Path"; Headers = $headers
        ContentType = 'application/json; charset=utf-8'; SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress }
    $response = Invoke-WebRequest @parameters
    $payload = $response.Content | ConvertFrom-Json
    if (-not $AllowError -and [int]$response.StatusCode -ge 400) {
        throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    [pscustomobject]@{ status = [int]$response.StatusCode; payload = $payload; data = $payload.data }
}

function Invoke-AttachmentUpload {
    param([string]$Path, [byte[]]$Bytes, [string]$FileName, [string]$Token, [switch]$AllowError)
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        $multipart = [System.Net.Http.MultipartFormDataContent]::new()
        $file = [System.Net.Http.ByteArrayContent]::new($Bytes)
        $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/plain')
        $multipart.Add($file, 'file', $FileName)
        $multipart.Add([System.Net.Http.StringContent]::new('DOCUMENT'), 'purpose')
        $response = $client.PostAsync("$apiBase$Path", $multipart).GetAwaiter().GetResult()
        $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        if (-not $AllowError -and -not $response.IsSuccessStatusCode) {
            throw "attachment upload failed: $($payload.code) $($payload.message)"
        }
        [pscustomobject]@{ status = [int]$response.StatusCode; payload = $payload; data = $payload.data }
    } finally { $client.Dispose() }
}

function Database-Scalar([string]$Sql) {
    [long](docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e $Sql)
}

$registration = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = $username; password = $password; displayName = 'C66 业务附件验收管理员'
    email = "$username@example.com"; systemName = 'C66 业务附件验收系统'; systemCode = "c66_system_$suffix"
}).data
$token = $registration.tokens.accessToken
$systemId = [long]$registration.systemId
$tenantId = [long]$registration.tenantId
$accountId = [long]$registration.accountId
$memberId = Database-Scalar "select id from sys_member where system_id=$systemId and account_id=$accountId"
$tenantMemberId = Database-Scalar "select stm.id from sys_tenant_member stm join sys_member sm on sm.id=stm.system_member_id where stm.system_id=$systemId and stm.tenant_id=$tenantId and sm.account_id=$accountId"

$moduleCode = "customer_c66_$suffix"
$groupCode = "${moduleCode}_group"
$group = (Invoke-Api -Method Post -Path '/api/admin/module-config/groups' -Token $token -Body @{
    code = $groupCode; name = '客户管理'; sortOrder = 10
}).data
$moduleDraft = (Invoke-Api -Method Post -Path '/api/admin/module-config/modules' -Token $token -Body @{
    groupId = $group.id; code = $moduleCode; name = '客户管理'; description = 'C66 业务附件对象权限验收'
}).data
$moduleId = [long]$moduleDraft.module.id
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/fields" -Token $token -Body @{
    code = 'customer_name'; name = '客户名称'; fieldType = 'TEXT'; required = $true; sortOrder = 10; config = @{}
}
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/fields" -Token $token -Body @{
    code = 'documents'; name = '客户资料'; fieldType = 'ATTACHMENT'; required = $false; sortOrder = 20; config = @{}
}
$draftRevision = Database-Scalar "select draft_revision from cfg_module where id=$moduleId"
$null = Invoke-Api -Method Post -Path "/api/admin/module-config/modules/$moduleId/publish" -Token $token -Body @{
    expectedDraftRevision = $draftRevision; changeSummary = 'C66 业务附件字段发布'
}

$record = (Invoke-Api -Method Post -Path "/api/runtime/modules/$moduleCode/records" -Token $token -Body @{
    recordNumber = "C66-$suffix"; title = '华东重点客户'; status = 'ACTIVE'
    ownerMemberId = $memberId; participantMemberIds = @(); fields = @{ customer_name = '华东重点客户' }
}).data
$content = [Text.Encoding]::UTF8.GetBytes("C66 customer attachment $suffix")
$recordAttachment = (Invoke-AttachmentUpload -Path "/api/business-attachments/records/$moduleCode/$($record.id)/fields/documents" -Bytes $content -FileName '客户调研资料.txt' -Token $token).data
$recordList = (Invoke-Api -Method Get -Path "/api/business-attachments/records/$moduleCode/$($record.id)/fields/documents" -Token $token).data
if (($recordList | Where-Object { $_.attachmentId -eq $recordAttachment.attachmentId }).Count -ne 1) { throw 'record attachment was not listed in its business field' }
$recordTimeline = (Invoke-Api -Method Get -Path "/api/runtime/modules/$moduleCode/records/$($record.id)/timeline" -Token $token).data.entries
if (($recordTimeline | Where-Object { $_.eventCode -eq 'BUSINESS_RECORD_ATTACHMENT_ADDED' }).Count -ne 1) { throw 'record attachment did not enter the business timeline' }

$filesBeforeInvalidField = Database-Scalar "select count(*) from file_object where system_id=$systemId and tenant_id=$tenantId"
$invalidField = Invoke-AttachmentUpload -Path "/api/business-attachments/records/$moduleCode/$($record.id)/fields/customer_name" -Bytes $content -FileName '不应保存.txt' -Token $token -AllowError
$filesAfterInvalidField = Database-Scalar "select count(*) from file_object where system_id=$systemId and tenant_id=$tenantId"
if ($invalidField.payload.code -ne 'ATTACHMENT_FIELD_TYPE_INVALID' -or $filesBeforeInvalidField -ne $filesAfterInvalidField) { throw 'non-file field accepted bytes or left an orphan file' }

$project = (Invoke-Api -Method Post -Path '/api/work/projects' -Token $token -Body @{ name = '客户交付'; description = 'C66 任务附件验收'; members = @() }).data
$task = (Invoke-Api -Method Post -Path "/api/work/projects/$($project.id)/tasks" -Token $token -Body @{
    title = '完成客户方案'; description = '提交可审阅的交付材料'; priority = 'HIGH'
    ownerTenantMemberId = $tenantMemberId; collaboratorTenantMemberIds = @(); configuredValues = @{}
}).data
$taskAttachment = (Invoke-AttachmentUpload -Path "/api/business-attachments/work-tasks/$($task.id)" -Bytes $content -FileName '客户方案.txt' -Token $token).data
$taskDetail = (Invoke-Api -Method Get -Path "/api/work/tasks/$($task.id)" -Token $token).data
if (($taskDetail.history | Where-Object { $_.actionCode -eq 'ATTACHMENT_ADDED' }).Count -ne 1) { throw 'task attachment did not enter task history' }

$flow = (Invoke-Api -Method Post -Path '/api/flows' -Token $token -Body @{ code = "c66_review_$suffix"; name = '客户方案复核'; description = 'C66 流程附件验收' }).data
$flowDraft = (Invoke-Api -Method Put -Path "/api/flows/$($flow.id)/draft" -Token $token -Body @{
    expectedVersion = 0
    nodes = @(
        @{ nodeKey = 'start'; nodeType = 'START'; name = '开始'; positionX = 0; positionY = 0; config = @{} },
        @{ nodeKey = 'end'; nodeType = 'END'; name = '结束'; positionX = 400; positionY = 0; config = @{} }
    )
    edges = @(@{ edgeKey = 'complete'; sourceNodeKey = 'start'; targetNodeKey = 'end'; priorityOrder = 10; config = @{} })
}).data
$null = Invoke-Api -Method Post -Path "/api/flows/$($flow.id)/publish" -Token $token -Body @{ expectedDraftRevision = $flowDraft.draftRevision; changeSummary = 'C66 流程附件版本' }
$instance = (Invoke-Api -Method Post -Path '/api/flow-runtime/instances' -Token $token -Body @{
    flowId = $flow.id; title = '华东客户方案复核'; businessType = 'BUSINESS_RECORD'; businessId = "$($record.id)"
    businessSnapshot = @{ moduleCode = $moduleCode; recordId = $record.id; title = $record.title }
    variables = @{}; idempotencyKey = "c66-flow-$suffix"
}).data.instance
$flowAttachment = (Invoke-AttachmentUpload -Path "/api/business-attachments/flow-instances/$($instance.id)" -Bytes $content -FileName '审批材料.txt' -Token $token).data
$flowDetail = (Invoke-Api -Method Get -Path "/api/flow-runtime/instances/$($instance.id)" -Token $token).data
if (($flowDetail.history | Where-Object { $_.eventType -eq 'ATTACHMENT_ADDED' }).Count -ne 1) { throw 'flow attachment did not enter flow history' }

$outsider = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = "c66_outside_$suffix"; password = $password; displayName = 'C66 外部组织成员'
    email = "c66_outside_$suffix@example.com"; systemName = 'C66 隔离系统'; systemCode = "c66_outside_system_$suffix"
}).data
$crossTenant = Invoke-Api -Method Get -Path "/api/business-attachments/$($recordAttachment.attachmentId)/download" -Token $outsider.tokens.accessToken -AllowError
if ($crossTenant.status -ne 404 -or $crossTenant.payload.code -ne 'BUSINESS_ATTACHMENT_NOT_FOUND') { throw 'cross-organization user discovered a business attachment' }

$null = Invoke-Api -Method Delete -Path "/api/business-attachments/$($recordAttachment.attachmentId)" -Token $token
$afterRemoval = (Invoke-Api -Method Get -Path "/api/business-attachments/records/$moduleCode/$($record.id)/fields/documents" -Token $token).data
if (($afterRemoval | Where-Object { $_.attachmentId -eq $recordAttachment.attachmentId }).Count -ne 0) { throw 'removed record attachment remained visible' }
$recordTimelineAfterRemoval = (Invoke-Api -Method Get -Path "/api/runtime/modules/$moduleCode/records/$($record.id)/timeline" -Token $token).data.entries
if (($recordTimelineAfterRemoval | Where-Object { $_.eventCode -eq 'BUSINESS_RECORD_ATTACHMENT_REMOVED' }).Count -ne 1) { throw 'record attachment removal did not enter the timeline' }

$externalSource = (Invoke-Api -Method Post -Path '/api/analytics/admin/data-sources' -Token $token -Body @{
    code = "erp_api_$suffix"; name = 'ERP 订单接口'; sourceType = 'EXTERNAL_API'
    definition = @{ connectionReference = 'erp_readonly'; requestCode = 'LIST_ORDERS'; method = 'GET'; queryParameters = @{}; responseSelector = '$.items'; limit = 100; timeoutMillis = 3000 }
    permissionPolicy = @{ resourceType = 'MODULE'; resourceCode = $moduleCode; actionCode = 'LIST' }
}).data
if ($externalSource.boundary.family -ne 'EXTERNAL_API' -or $externalSource.boundary.runtimeReady) { throw 'external API source boundary is ambiguous' }
$externalSource = (Invoke-Api -Method Post -Path "/api/analytics/admin/data-sources/$($externalSource.id)/publish" -Token $token -Body @{ expectedDraftRevision = $externalSource.draftRevision }).data
if ($externalSource.versions.Count -ne 1) { throw 'external API source was not published as an immutable version' }

$databaseSource = (Invoke-Api -Method Post -Path '/api/analytics/admin/data-sources' -Token $token -Body @{
    code = "warehouse_$suffix"; name = '仓库只读汇总'; sourceType = 'DATABASE_CONNECTION'
    definition = @{ connectionReference = 'warehouse_readonly'; queryCode = 'INVENTORY_SUMMARY'; parameters = @{}; outputFields = @('warehouse', 'quantity'); limit = 200; timeoutMillis = 5000 }
    permissionPolicy = @{}
}).data
if ($databaseSource.boundary.connectionMode -ne 'MANAGED_READ_ONLY_REFERENCE' -or $databaseSource.boundary.runtimeReady) { throw 'database source boundary is ambiguous' }
$unsafeConnection = Invoke-Api -Method Post -Path '/api/analytics/admin/data-sources' -Token $token -AllowError -Body @{
    code = "unsafe_$suffix"; name = '不安全连接'; sourceType = 'DATABASE_CONNECTION'
    definition = @{ connectionReference = 'warehouse_readonly'; queryCode = 'INVENTORY_SUMMARY'; sql = 'select * from secrets' }
    permissionPolicy = @{}
}
if ($unsafeConnection.payload.code -ne 'DASHBOARD_CONNECTION_SECRET_FORBIDDEN') { throw 'data source accepted raw SQL or connection material' }

[ordered]@{
    systemId = $systemId; tenantId = $tenantId; moduleCode = $moduleCode; recordId = $record.id
    recordAttachmentId = $recordAttachment.attachmentId; recordTimelineEvents = @($recordTimelineAfterRemoval.eventCode)
    invalidFieldCode = $invalidField.payload.code; invalidFieldOrphanDelta = ($filesAfterInvalidField - $filesBeforeInvalidField)
    workTaskId = $task.id; taskAttachmentId = $taskAttachment.attachmentId; taskAttachmentHistory = $true
    flowInstanceId = $instance.id; flowAttachmentId = $flowAttachment.attachmentId; flowAttachmentHistory = $true
    crossOrganizationStatus = $crossTenant.status; crossOrganizationCode = $crossTenant.payload.code
    removedFromRecordField = $true
    externalSourceVersion = $externalSource.versions[0].versionNumber
    externalBoundary = $externalSource.boundary
    databaseBoundary = $databaseSource.boundary
    unsafeConnectionCode = $unsafeConnection.payload.code
} | ConvertTo-Json -Depth 10
