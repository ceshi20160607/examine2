$ErrorActionPreference = 'Stop'

$apiBase = 'http://127.0.0.1:18080'
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString().Substring(7)
$username = "c65_live_$suffix"
$password = 'Correct-c65-live-password!'

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [switch]$AllowError)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$apiBase$Path"
        Headers = $headers
        ContentType = 'application/json; charset=utf-8'
        SkipHttpErrorCheck = $true
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

function Invoke-AttachmentBytes {
    param([string]$Path, [string]$Token)
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        $response = $client.GetAsync("$apiBase$Path").GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) { throw "attachment read failed: $([int]$response.StatusCode)" }
        $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    } finally { $client.Dispose() }
}

function Database-Scalar([string]$Sql) {
    [long](docker exec unexamine-mysql-1 mysql -uunexamine -punexamine_local -Dunexamine -N -e $Sql)
}

$registration = (Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
    username = $username
    password = $password
    displayName = 'C65 消息附件验收管理员'
    email = "$username@example.com"
    systemName = 'C65 统一工作入口验收系统'
    systemCode = "c65_system_$suffix"
}).data
$token = $registration.tokens.accessToken
$systemId = [long]$registration.systemId
$tenantId = [long]$registration.tenantId
$accountId = [long]$registration.accountId
$tenantMemberId = Database-Scalar "select stm.id from sys_tenant_member stm join sys_member sm on sm.id=stm.system_member_id where stm.system_id=$systemId and stm.tenant_id=$tenantId and sm.account_id=$accountId"

$project = (Invoke-Api -Method Post -Path '/api/work/projects' -Token $token -Body @{
    name = '客户交付推进'; description = '验证任务、待办和消息统一投影'; members = @()
}).data
$task = (Invoke-Api -Method Post -Path "/api/work/projects/$($project.id)/tasks" -Token $token -Body @{
    title = '跟进重点客户回访'; description = '今日完成客户回访并记录结论'; priority = 'HIGH'
    ownerTenantMemberId = $tenantMemberId; collaboratorTenantMemberIds = @()
    configuredValues = @{}
}).data

$todos = (Invoke-Api -Method Get -Path '/api/todos?status=PENDING&type=ALL' -Token $token).data
$workTodo = $todos | Where-Object { $_.sourceType -eq 'WORK_TASK' -and [long]$_.sourceId -eq [long]$task.id } | Select-Object -First 1
if (-not $workTodo) { throw 'work task was not projected to todo inbox' }
if (-not $workTodo.sourceLabel -or -not $workTodo.objectName -or -not $workTodo.initiatorName) {
    throw 'todo business presentation is incomplete'
}

$workInbox = (Invoke-Api -Method Get -Path '/api/messages?status=ACTIVE&sourceType=WORK_TASK' -Token $token).data
$workMessage = $workInbox.messages | Select-Object -First 1
if (-not $workMessage -or -not $workMessage.actorName) { throw 'work task message was not created with actor presentation' }
if ($workMessage.PSObject.Properties.Name -contains 'deliveries') { throw 'ordinary inbox leaked delivery diagnostics' }

$template = (Invoke-Api -Method Post -Path '/api/message-templates' -Token $token -Body @{
    code = "C65_CUSTOMER_NOTICE_$suffix".ToUpperInvariant(); name = '客户处理通知'; channel = 'EMAIL'
    subjectTemplate = '客户 {{customer}} 已更新'; contentTemplate = '{{actor}} 已完成客户处理'
    requiredVariables = @('customer', 'actor')
}).data
$null = Invoke-Api -Method Post -Path "/api/message-templates/$($template.id)/publish" -Token $token

$wrongContext = Invoke-Api -Method Post -Path '/api/messages/events' -Token $token -AllowError -Body @{
    templateCode = $template.code; sourceType = 'ADMIN_DIAGNOSTIC'; dedupKey = "wrong-context-$suffix"
    recipients = @(@{ type = 'PLATFORM_MEMBER'; id = 1 }); variables = @{ customer = '错误上下文'; actor = '管理员' }
    sensitivity = 'NORMAL'
}
if ($wrongContext.payload.code -ne 'MESSAGE_RECIPIENT_CONTEXT_INVALID') { throw 'message accepted a recipient outside organization context' }

$event = @{
    templateCode = $template.code; sourceType = 'ADMIN_DIAGNOSTIC'; dedupKey = "diagnostic-$suffix"
    recipients = @(@{ type = 'TENANT_MEMBER'; id = $tenantMemberId })
    variables = @{ customer = '华东重点客户'; actor = '验收管理员' }; sensitivity = 'NORMAL'
}
$sent = (Invoke-Api -Method Post -Path '/api/messages/events' -Token $token -Body $event).data
$replayed = (Invoke-Api -Method Post -Path '/api/messages/events' -Token $token -Body $event).data
if ([long]$sent.id -ne [long]$replayed.id) { throw 'message deduplication did not replay the same event' }
$diagnostics = (Invoke-Api -Method Get -Path "/api/messages/diagnostics/$($sent.id)/deliveries" -Token $token).data
if ($diagnostics.deliveries.Count -ne 2) { throw 'message diagnostics did not expose in-app and external delivery states' }

$cancelledTask = (Invoke-Api -Method Put -Path "/api/work/tasks/$($task.id)" -Token $token -Body @{
    expectedVersion = $task.version; status = 'CANCELLED'; ownerTenantMemberId = $tenantMemberId
    priority = 'HIGH'; progressPercent = 0; collaboratorTenantMemberIds = @(); configuredValues = @{}
    comment = '验收任务撤回'
}).data
$allTodos = (Invoke-Api -Method Get -Path '/api/todos?status=ALL&type=ALL' -Token $token).data
$refreshedTodo = $allTodos | Where-Object { $_.id -eq $workTodo.id } | Select-Object -First 1
if ($refreshedTodo.status -ne 'COMPLETED') { throw 'withdrawn work target did not invalidate its pending todo' }
$null = Invoke-Api -Method Post -Path '/api/messages/read-all' -Token $token
$unread = (Invoke-Api -Method Get -Path '/api/messages/unread-count' -Token $token).data
if ([long]$unread.count -ne 0) { throw 'message badge was not synchronized after read-all' }

$content = [Text.Encoding]::UTF8.GetBytes("C65 personal document $suffix")
$attachment = (Invoke-AttachmentUpload -Path '/api/business-attachments/personal' -Bytes $content -FileName '客户回访纪要.txt' -Token $token).data
if (-not $attachment.attachmentId -or $attachment.scanStatus -ne 'CLEAN') { throw 'business attachment was not atomically attached' }
$listed = (Invoke-Api -Method Get -Path '/api/business-attachments/personal' -Token $token).data
if (($listed | Where-Object { $_.attachmentId -eq $attachment.attachmentId }).Count -ne 1) { throw 'personal attachment list is inconsistent' }
$downloaded = Invoke-AttachmentBytes -Path "/api/business-attachments/$($attachment.attachmentId)/download" -Token $token
if ([Convert]::ToHexString($downloaded) -ne [Convert]::ToHexString($content)) { throw 'attachment bytes changed during storage' }

$filesBeforeInvalidTarget = Database-Scalar "select count(*) from file_object where system_id=$systemId and tenant_id=$tenantId"
$invalidTarget = Invoke-AttachmentUpload -Path '/api/business-attachments/records/missing_module/999999/fields/documents' `
    -Bytes $content -FileName '不应写入.txt' -Token $token -AllowError
$filesAfterInvalidTarget = Database-Scalar "select count(*) from file_object where system_id=$systemId and tenant_id=$tenantId"
if ($invalidTarget.status -lt 400 -or $filesBeforeInvalidTarget -ne $filesAfterInvalidTarget) {
    throw 'invalid business target created an orphan file'
}

$null = Invoke-Api -Method Delete -Path "/api/business-attachments/$($attachment.attachmentId)" -Token $token
$afterRemoval = (Invoke-Api -Method Get -Path '/api/business-attachments/personal' -Token $token).data
if (($afterRemoval | Where-Object { $_.attachmentId -eq $attachment.attachmentId }).Count -ne 0) { throw 'removed attachment remained visible' }
$auditCount = Database-Scalar "select count(*) from audit_event where system_id=$systemId and event_code in ('WORK_PROJECT_TASK_CREATED','WORK_TASK_UPDATED','MESSAGE_CREATED','MESSAGE_READ_ALL','BUSINESS_ATTACHMENT_ADDED','BUSINESS_ATTACHMENT_REMOVED')"
if ($auditCount -lt 5) { throw 'business actions did not produce the expected audit trail' }

[ordered]@{
    systemId = $systemId
    tenantId = $tenantId
    workTaskId = $task.id
    todoId = $workTodo.id
    todoObject = $workTodo.objectName
    workMessageId = $workMessage.id
    ordinaryInboxHasDeliveries = ($workMessage.PSObject.Properties.Name -contains 'deliveries')
    diagnosticMessageId = $sent.id
    diagnosticReplay = ([long]$sent.id -eq [long]$replayed.id)
    deliveryChannels = @($diagnostics.deliveries.channel)
    wrongRecipientCode = $wrongContext.payload.code
    todoStatusAfterTaskWithdrawal = $refreshedTodo.status
    unreadAfterReadAll = $unread.count
    attachmentId = $attachment.attachmentId
    attachmentBytesVerified = $true
    invalidTargetStatus = $invalidTarget.status
    orphanDelta = ($filesAfterInvalidTarget - $filesBeforeInvalidTarget)
    removedFromBusinessList = $true
    auditCount = $auditCount
} | ConvertTo-Json -Depth 10
