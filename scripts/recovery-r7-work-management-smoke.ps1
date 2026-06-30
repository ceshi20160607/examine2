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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 16 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystem {
    if ($KeepCreatedData -or [string]::IsNullOrWhiteSpace($script:SystemId) -or $null -eq $script:Headers) {
        return 'SKIPPED'
    }
    $cleanup = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$script:SystemId" -Headers $script:Headers -Body @{
        reason = 'recovery-r7-work-management cleanup created system'
        impactScope = 'created_by_current_script'
        idempotencyKey = "cleanup-r7-work-$script:Suffix"
    }
    return [string]$cleanup.result
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

$Suffix = Get-Date -Format 'MMddHHmmss'
$CleanupResult = 'SKIPPED'
$script:SystemId = $null
$script:Headers = $null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$Login = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$AccessToken = [string]$Login.accessToken
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($AccessToken)) -Message 'Default admin login did not return an access token.'
$script:Headers = @{ Authorization = "Bearer $AccessToken" }

$CreatedSystem = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:Headers -Body @{
    systemName = "R7 Work System $Suffix"
    systemCode = "r7work_$Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:SystemId = [string]$CreatedSystem.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'System create did not return systemId.'

$Switch = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:Headers -Body @{
    systemId = $script:SystemId
    tenantId = $null
    reason = 'recovery-r7 work management'
}
$TenantId = [string]$Switch.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'System switch did not return tenantId.'

$BeforeDashboard = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/work/dashboard" -Headers $script:Headers

$Project = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/projects" -Headers $script:Headers -Body @{
    projectCode = "r7_project_$Suffix"
    projectName = "R7 Project $Suffix"
    status = 'DOING'
    progress = 10
}
$ProjectId = [string]$Project.projectId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($ProjectId)) -Message 'Work project create did not return projectId.'

$ProjectTaskResult = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/project-tasks" -Headers $script:Headers -Body @{
    title = "R7 Project Task $Suffix"
    projectId = $ProjectId
    status = 'DOING'
    tagCodes = @('PROJECT')
    progress = 35
    dueAt = (Get-Date).AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss')
    fieldValues = @{
        description = 'R7 project task readback'
    }
}
$ProjectTask = $ProjectTaskResult.task
Assert-True -Condition ([string]$ProjectTask.title -eq "R7 Project Task $Suffix") -Message 'Project task create did not return expected title.'

$PlainTaskResult = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/plain-tasks" -Headers $script:Headers -Body @{
    title = "R7 Plain Task $Suffix"
    status = 'TODO'
    tagCodes = @('DAILY')
    progress = 5
    dueAt = (Get-Date).AddDays(2).ToString('yyyy-MM-ddTHH:mm:ss')
    fieldValues = @{
        description = 'R7 plain task readback'
    }
}
$PlainTask = $PlainTaskResult.task
Assert-True -Condition ([string]$PlainTask.title -eq "R7 Plain Task $Suffix") -Message 'Plain task create did not return expected title.'

$ReportDate = (Get-Date).ToString('yyyy-MM-dd')
$DailyReport = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/daily-reports" -Headers $script:Headers -Body @{
    reportDate = $ReportDate
    content = "R7 daily report $Suffix"
    status = 'DRAFT'
    sourceIds = @([string]$ProjectTask.taskId, [string]$PlainTask.taskId)
    submitNow = $false
}
Assert-True -Condition ([string]$DailyReport.content -eq "R7 daily report $Suffix") -Message 'Daily report create did not read back expected content.'

$AutoDraft = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/daily-reports/auto-draft" -Headers $script:Headers
Assert-True -Condition ([bool]$AutoDraft.manualConfirmRequired -eq $true) -Message 'Auto draft should require manual confirmation.'

$AfterDashboard = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/work/dashboard" -Headers $script:Headers
Assert-True -Condition ([int]$AfterDashboard.overview.activeProjectCount -ge 1) -Message 'Dashboard active project count did not update.'
Assert-True -Condition ([int]$AfterDashboard.overview.projectTaskCount -ge 1) -Message 'Dashboard project task count did not update.'
Assert-True -Condition ([int]$AfterDashboard.overview.plainTaskCount -ge 1) -Message 'Dashboard plain task count did not update.'

$Projects = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/projects/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    keyword = "R7 Project $Suffix"
}
$ProjectTasks = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/project-tasks/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    keyword = "R7 Project Task $Suffix"
}
$PlainTasks = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/plain-tasks/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    keyword = "R7 Plain Task $Suffix"
}
$Reports = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/daily-reports/search?pageNo=1&pageSize=20" -Headers $script:Headers -Body @{
    keyword = "R7 daily report $Suffix"
}

Assert-True -Condition (@($Projects.records | Where-Object { [string]$_.projectId -eq $ProjectId }).Count -eq 1) -Message 'Project search did not find created project.'
Assert-True -Condition (@($ProjectTasks.records | Where-Object { [string]$_.taskId -eq [string]$ProjectTask.taskId }).Count -eq 1) -Message 'Project task search did not find created task.'
Assert-True -Condition (@($PlainTasks.records | Where-Object { [string]$_.taskId -eq [string]$PlainTask.taskId }).Count -eq 1) -Message 'Plain task search did not find created task.'
Assert-True -Condition (@($Reports.records | Where-Object { [string]$_.reportId -eq [string]$DailyReport.reportId }).Count -eq 1) -Message 'Daily report search did not find created report.'

$ProjectKanban = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/kanban/query" -Headers $script:Headers -Body @{
    taskType = 'PROJECT'
}
$PlainKanban = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/kanban/query" -Headers $script:Headers -Body @{
    taskType = 'PLAIN'
}
$ProjectKanbanTitles = @($ProjectKanban.columns | ForEach-Object { $_.cards } | ForEach-Object { $_.title })
$PlainKanbanTitles = @($PlainKanban.columns | ForEach-Object { $_.cards } | ForEach-Object { $_.title })
Assert-True -Condition ($ProjectKanbanTitles -contains "R7 Project Task $Suffix") -Message 'Project kanban did not include created task.'
Assert-True -Condition ($PlainKanbanTitles -contains "R7 Plain Task $Suffix") -Message 'Plain kanban did not include created task.'

$CleanupResult = Remove-CreatedSystem

[ordered]@{
    status = 'PASS'
    task = 'REC-P0-010'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    suffix = $Suffix
    systemId = $script:SystemId
    tenantId = $TenantId
    beforeDashboard = $BeforeDashboard.overview
    afterDashboard = $AfterDashboard.overview
    projectId = $ProjectId
    projectTaskId = $ProjectTask.taskId
    plainTaskId = $PlainTask.taskId
    dailyReportId = $DailyReport.reportId
    autoDraftTraceId = $AutoDraft.traceId
    projectSearchTotal = $Projects.total
    projectTaskSearchTotal = $ProjectTasks.total
    plainTaskSearchTotal = $PlainTasks.total
    reportSearchTotal = $Reports.total
    projectKanbanColumns = @($ProjectKanban.columns).Count
    plainKanbanColumns = @($PlainKanban.columns).Count
    cleanup = $CleanupResult
} | ConvertTo-Json -Depth 30
