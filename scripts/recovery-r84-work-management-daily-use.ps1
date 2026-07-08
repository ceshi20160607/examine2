param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$ResultPath = Join-Path $EvidenceDir 'r84-work-management-daily-use-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r84-work-management-daily-use-2026-07-07.md'
$R80ResultPath = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'

function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing JSON file: $Path" }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') { throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)" }
    return $response.data
}

function Invoke-ExpectedDenied {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body
    } catch {
        if ($_.Exception.Message -match 'HTTP 401|HTTP 403') { return @{ status = 'DENIED'; path = $Path } }
        throw
    }
    throw "Expected denied request but it succeeded: $Method $Path"
}

$checks = New-Object System.Collections.Generic.List[object]
function Add-Check([string]$Area, [string]$Name, [bool]$Passed, [string]$Detail) {
    $checks.Add([pscustomobject]@{ area = $Area; name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
}

$r80 = Read-JsonFile $R80ResultPath
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')
$trial = $r80.trialPack.runtimeDailyUse
$login = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.normalLoginName; password = [string]$trial.password; loginTarget = 'PLATFORM' }
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$systemId = [string]$trial.systemId
$stamp = Get-Date -Format 'yyyyMMddHHmmss'

$dashboardBefore = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/work/dashboard" -Headers $headers
$project = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/projects" -Headers $headers -Body @{ projectCode = "R84-$stamp"; projectName = "R84 daily work project $stamp"; status = 'TODO'; progress = 0; startDate = (Get-Date).ToString('yyyy-MM-dd') }
$projectTask = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/project-tasks" -Headers $headers -Body @{ title = "R84 project task $stamp"; projectId = $project.projectId; status = 'TODO'; progress = 10; dueAt = (Get-Date).AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss'); collaborators = @(); tagCodes = @('PROJECT'); fieldValues = @{ description = 'R84 project task readback' } }
$plainTask = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/plain-tasks" -Headers $headers -Body @{ title = "R84 plain task $stamp"; status = 'TODO'; progress = 0; dueAt = (Get-Date).AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss'); collaborators = @(); tagCodes = @('DAILY'); fieldValues = @{ description = 'R84 plain task readback' } }
$projectsPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/projects/search?pageNo=1&pageSize=20" -Headers $headers -Body @{ keyword = $project.projectName }
$projectTasksPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/project-tasks/search?pageNo=1&pageSize=20" -Headers $headers -Body @{ keyword = $projectTask.task.title }
$plainTasksPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/plain-tasks/search?pageNo=1&pageSize=20" -Headers $headers -Body @{ keyword = $plainTask.task.title }
$projectKanban = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/kanban/query" -Headers $headers -Body @{ taskType = 'PROJECT' }
$plainKanban = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/kanban/query" -Headers $headers -Body @{ taskType = 'PLAIN' }
$draft = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/daily-reports/auto-draft" -Headers $headers
$report = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/daily-reports" -Headers $headers -Body @{ reportDate = $draft.reportDate; content = $draft.content; status = 'DRAFT'; sourceIds = @($draft.draftId); submitNow = $false }
$reportsPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/daily-reports/search?pageNo=1&pageSize=20" -Headers $headers -Body @{ status = 'DRAFT' }
$dashboardAfter = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/work/dashboard" -Headers $headers
$denied = Invoke-ExpectedDenied -Method Get -Path "/api/v1/systems/$systemId/work/dashboard"

$source = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/system-shell/systemShell.ts')
$markers = @('systemWorkbench','workTabs','workDashboard','workWarnings','workCalendar','workTaskSection','workTaskList','workTaskKanban','workTaskDetail','workCreatePanel','workActionResult','workDailyReports','workDailyAutoDraft','workDailyDraft','workDailyDraftConfirm')
$missingMarkers = @($markers | Where-Object { $source -notmatch [regex]::Escape($_) })

Add-Check 'state' 'R84 is active and R83 is accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R84') -and (@($state.build_plan.acceptedBatches) -contains 'RECOVERY-R83')) ("currentBatch={0}" -f $state.build_plan.currentBatch)
Add-Check 'api-readback' 'project, project task, and plain task are created and read back' (($projectsPage.total -ge 1) -and ($projectTasksPage.total -ge 1) -and ($plainTasksPage.total -ge 1)) ("project={0}, projectTask={1}, plainTask={2}" -f $project.projectId, $projectTask.task.taskId, $plainTask.task.taskId)
Add-Check 'api-readback' 'kanban returns project and plain task columns' ((@($projectKanban.columns).Count -gt 0) -and (@($plainKanban.columns).Count -gt 0)) ("projectColumns={0}, plainColumns={1}" -f @($projectKanban.columns).Count, @($plainKanban.columns).Count)
Add-Check 'daily-report' 'auto draft requires manual confirmation and confirmed report is persisted' (($draft.manualConfirmRequired -eq $true) -and ([string]::IsNullOrWhiteSpace($report.reportId) -eq $false) -and ($reportsPage.total -ge 1)) ("draft={0}, report={1}" -f $draft.draftId, $report.reportId)
Add-Check 'dashboard' 'dashboard remains readable after work mutations' (($dashboardBefore.traceId) -and ($dashboardAfter.traceId)) ("before={0}, after={1}" -f $dashboardBefore.traceId, $dashboardAfter.traceId)
Add-Check 'permission' 'anonymous work API access is denied' ($denied.status -eq 'DENIED') $denied.path
Add-Check 'frontend-source' 'work management stable DOM markers exist in source' ($missingMarkers.Count -eq 0) ("missing={0}" -f ($missingMarkers -join ','))
Add-Check 'signoff-boundary' 'user signoff remains false' (-not [bool]$state.gates.user_script_passed) ("user_script_passed={0}" -f $state.gates.user_script_passed)

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R84_WORK_MANAGEMENT_DAILY_USE_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-084'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    accepted = ($status -eq 'PASS')
    checks = @($checks.ToArray())
    data = [ordered]@{
        systemId = $systemId
        projectId = $project.projectId
        projectTaskId = $projectTask.task.taskId
        plainTaskId = $plainTask.task.taskId
        dailyDraftId = $draft.draftId
        dailyReportId = $report.reportId
        projectKanbanColumns = @($projectKanban.columns).Count
        plainKanbanColumns = @($plainKanban.columns).Count
    }
})
$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
$summary = @('# R84 Work Management Daily Use', '', "Status: `$status", '', 'This is engineering evidence only. It does not close user signoff.', '', '## Checks', '')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''; $summary += '## Data'; $summary += ''; $summary += ('- System: `{0}`' -f $systemId); $summary += ('- Project: `{0}`' -f $project.projectId); $summary += ('- Project task: `{0}`' -f $projectTask.task.taskId); $summary += ('- Plain task: `{0}`' -f $plainTask.task.taskId); $summary += ('- Daily report: `{0}`' -f $report.reportId); $summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R84 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }


