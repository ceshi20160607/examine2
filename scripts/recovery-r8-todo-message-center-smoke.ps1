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

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:Setup -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:Setup.targetSystemId, $script:Setup.normalOwnedSystemId, $script:Setup.approverOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace([string]$systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r8-todo-message cleanup created system'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r8-$($script:Setup.suffix)-$systemId"
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

$script:Setup = $null
$script:AdminHeaders = $null
$script:CleanupResult = @()

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$SetupScript = Join-Path $PSScriptRoot 'recovery-r3-runtime-approval-smoke.ps1'
$SetupJson = & $SetupScript -BaseUrl $BaseUrl -KeepCreatedData -StopAfterSubmit | Out-String
$script:Setup = $SetupJson | ConvertFrom-Json
Assert-True -Condition ($script:Setup.status -eq 'PASS') -Message "R8 setup did not pass: $SetupJson"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$RequesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $script:Setup.requesterAccount
    password = $script:Setup.password
    loginTarget = 'PLATFORM'
}
$RequesterHeaders = @{ Authorization = "Bearer $($RequesterLogin.accessToken)" }
$ApproverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $script:Setup.approverAccount
    password = $script:Setup.password
    loginTarget = 'PLATFORM'
}
$ApproverHeaders = @{ Authorization = "Bearer $($ApproverLogin.accessToken)" }

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $RequesterHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r8 requester switch'
}
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r8 approver switch'
}

$RequesterPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $RequesterHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    priority = $null
    assigneeId = $RequesterLogin.profile.accountId
    dueRange = $null
}
$ApproverPending = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
}
Assert-True -Condition ([int]$RequesterPending.page.total -eq 0) -Message 'Requester can see the approver pending todo.'
Assert-True -Condition ([int]$ApproverPending.page.total -eq 1) -Message 'Approver pending todo search did not return exactly one record.'
$Todo = @($ApproverPending.page.records) | Select-Object -First 1
Assert-True -Condition ([string]$Todo.target.params.recordId -eq [string]$script:Setup.recordId) -Message 'Todo target does not point to the created runtime record.'

$UnreadMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = 'unread'
    archiveStatus = 'active'
    timeRange = $null
    keyword = $script:Setup.recordId
}
Assert-True -Condition ([int]$UnreadMessages.total -eq 1) -Message 'Approver unread active message search did not return exactly one record.'
$Message = @($UnreadMessages.records) | Select-Object -First 1
Assert-True -Condition ([string]$Message.target.targetSystemId -eq [string]$script:Setup.targetSystemId) -Message 'Message target system does not match setup system.'

$MarkRead = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/mark-read" -Headers $ApproverHeaders -Body @{
    messageIds = @([string]$Message.messageId)
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r8 mark single read'
}
Assert-True -Condition ([int]$MarkRead.affectedCount -eq 1) -Message 'Single mark-read did not affect the generated message.'

$ReadMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = 'read'
    archiveStatus = 'active'
    timeRange = $null
    keyword = $script:Setup.recordId
}
Assert-True -Condition ([int]$ReadMessages.total -eq 1) -Message 'Read filter did not find the marked-read message.'

$MarkAll = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/mark-all-read" -Headers $ApproverHeaders -Body @{
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    timeRange = $null
    keyword = $script:Setup.recordId
}
Assert-True -Condition ([int]$MarkAll.affectedCount -ge 0) -Message 'Mark-all-read returned an invalid affected count.'

$Archive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/archive" -Headers $ApproverHeaders -Body @{
    messageIds = @([string]$Message.messageId)
    tenantId = $script:Setup.targetTenantId
    reason = 'recovery-r8 archive generated message'
}
Assert-True -Condition ([int]$Archive.affectedCount -eq 1) -Message 'Archive did not affect the generated message.'

$ActiveAfterArchive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'active'
    timeRange = $null
    keyword = $script:Setup.recordId
}
$ArchivedAfterArchive = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/messages/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    templateCode = 'tpl_flow_approval_pending'
    type = 'approval'
    readStatus = $null
    archiveStatus = 'archived'
    timeRange = $null
    keyword = $script:Setup.recordId
}
Assert-True -Condition ([int]$ActiveAfterArchive.total -eq 0) -Message 'Archived message still appears in active message search.'
Assert-True -Condition ([int]$ArchivedAfterArchive.total -eq 1) -Message 'Archived message is not visible through archived message search.'

$TodoAction = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/$($Todo.todoId)/actions/approve" -Headers $ApproverHeaders -Body @{
    idempotencyKey = "approve-r8-$($script:Setup.suffix)"
    comment = 'Approved by recovery R8 todo action'
    reason = $null
    transferTargetId = $null
    transferTargetName = $null
}
Assert-True -Condition ($TodoAction.status -eq 'HANDLED') -Message 'Todo action did not move the todo to HANDLED.'

$PendingAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'PENDING'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
}
$HandledAfterApprove = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($script:Setup.targetSystemId)/todos/search?pageNo=1&pageSize=20" -Headers $ApproverHeaders -Body @{
    scope = 'system'
    typeCode = 'flow_approval'
    keyword = $script:Setup.recordId
    status = 'HANDLED'
    priority = $null
    assigneeId = $ApproverLogin.profile.accountId
    dueRange = $null
}
Assert-True -Condition ([int]$PendingAfterApprove.page.total -eq 0) -Message 'Approved todo still appears as pending.'
Assert-True -Condition ([int]$HandledAfterApprove.page.total -eq 1) -Message 'Approved todo did not appear as handled.'

$script:CleanupResult = Remove-CreatedSystems

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-011'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    systemId = $script:Setup.targetSystemId
    tenantId = $script:Setup.targetTenantId
    requesterAccount = $script:Setup.requesterAccount
    approverAccount = $script:Setup.approverAccount
    recordId = $script:Setup.recordId
    pendingTodoId = $Todo.todoId
    messageId = $Message.messageId
    requesterPendingTodoTotal = $RequesterPending.page.total
    approverPendingTodoTotal = $ApproverPending.page.total
    unreadMessageTotal = $UnreadMessages.total
    readMessageTotal = $ReadMessages.total
    activeAfterArchiveTotal = $ActiveAfterArchive.total
    archivedAfterArchiveTotal = $ArchivedAfterArchive.total
    handledTodoTotal = $HandledAfterApprove.page.total
    markReadTraceId = $MarkRead.traceId
    archiveTraceId = $Archive.traceId
    todoActionTraceId = $TodoAction.traceId
    cleanup = $script:CleanupResult
} | ConvertTo-Json -Depth 30
