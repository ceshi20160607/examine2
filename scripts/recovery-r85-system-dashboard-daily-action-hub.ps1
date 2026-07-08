param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$ResultPath = Join-Path $EvidenceDir 'r85-system-dashboard-daily-action-hub-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r85-system-dashboard-daily-action-hub-2026-07-07.md'
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

function Read-DeployedAssetText {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $assetMatches = [regex]::Matches($index, 'src="(?<src>/assets/[^""<>]+\.js)"')
        if ($assetMatches.Count -eq 0) { return '' }
        $texts = New-Object System.Collections.Generic.List[string]
        foreach ($match in $assetMatches) {
            $src = $match.Groups['src'].Value
            $texts.Add((Invoke-WebRequest -Uri "$BaseUrl$src" -UseBasicParsing -TimeoutSec 30).Content) | Out-Null
        }
        return ($texts -join "`n")
    } catch {
        return ''
    }
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
$plainTask = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/plain-tasks" -Headers $headers -Body @{ title = "R85 dashboard quick task $stamp"; status = 'TODO'; progress = 0; dueAt = (Get-Date).AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss'); collaborators = @(); tagCodes = @('DASHBOARD'); fieldValues = @{ description = 'R85 dashboard action hub readback' } }
$dashboardAfter = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/work/dashboard" -Headers $headers
$plainTasksPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/plain-tasks/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ keyword = $plainTask.task.title }
$projectTasksPage = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/work/project-tasks/search?pageNo=1&pageSize=5" -Headers $headers -Body @{}
$todos = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/todos/search?pageNo=1&pageSize=5" -Headers $headers -Body @{ scope = 'system'; status = 'PENDING' }
$messages = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=5" -Headers $headers -Body @{ systemId = $systemId; readStatus = 'unread'; archiveStatus = 'active' }
$modules = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/modules?pageNo=1&pageSize=100" -Headers $headers
$homePage = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/work/home-page" -Headers $headers
$deniedDashboard = Invoke-ExpectedDenied -Method Get -Path "/api/v1/systems/$systemId/work/dashboard"
$deniedTodos = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/todos/search?pageNo=1&pageSize=1" -Body @{ scope = 'system'; status = 'PENDING' }
$deniedMessages = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=1" -Body @{ systemId = $systemId; archiveStatus = 'active' }

$source = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/system-shell/systemShell.ts')
$deployedAssetText = Read-DeployedAssetText
$markers = @(
    'systemDashboard',
    'systemDashboardHeading',
    'systemDashboardDailyHub',
    'dashboardHasRuntimeModules',
    'systemDashboardQuickActions',
    'dashboardQuickAction',
    'systemDashboardQuickCreate',
    'systemDashboardWorkPreview',
    'dashboardWorkTask',
    'systemDashboardTodoPreview',
    'dashboardTodo',
    'systemDashboardMessagePreview',
    'dashboardMessage',
    'systemDashboardModulePreview',
    'dashboardModule'
)
$missingSourceMarkers = @($markers | Where-Object { $source -notmatch [regex]::Escape($_) })
$missingDeployedMarkers = @($markers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })

Add-Check 'state' 'R85 is active and R84 is accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R85') -and (@($state.build_plan.acceptedBatches) -contains 'RECOVERY-R84')) ("currentBatch={0}" -f $state.build_plan.currentBatch)
Add-Check 'api-readback' 'dashboard, home page, work tasks, todos, messages, and modules are readable for the trial member' (($dashboardAfter.traceId) -and ($homePage.title -ne $null) -and ($plainTasksPage.total -ge 1) -and ($todos.page.total -ge 0) -and ($messages.total -ge 0) -and ($modules.total -ge 0)) ("task={0}, todos={1}, messages={2}, modules={3}" -f $plainTask.task.taskId, $todos.page.total, $messages.total, $modules.total)
Add-Check 'daily-action-data' 'created quick task is available for dashboard work preview data' ($plainTasksPage.records[0].taskId -eq $plainTask.task.taskId) ("plainTask={0}" -f $plainTask.task.taskId)
Add-Check 'dashboard' 'work dashboard remains readable before and after quick task creation' (($dashboardBefore.traceId) -and ($dashboardAfter.traceId)) ("before={0}, after={1}" -f $dashboardBefore.traceId, $dashboardAfter.traceId)
Add-Check 'permission' 'anonymous dashboard, todo, and message APIs are denied' (($deniedDashboard.status -eq 'DENIED') -and ($deniedTodos.status -eq 'DENIED') -and ($deniedMessages.status -eq 'DENIED')) ("{0}; {1}; {2}" -f $deniedDashboard.path, $deniedTodos.path, $deniedMessages.path)
Add-Check 'frontend-source' 'system dashboard daily action hub source markers exist' ($missingSourceMarkers.Count -eq 0) ("missing={0}" -f ($missingSourceMarkers -join ','))
Add-Check 'deployed-asset' 'deployed frontend asset contains R85 dashboard markers' (($deployedAssetText.Length -gt 0) -and ($missingDeployedMarkers.Count -eq 0)) ("assetLength={0}, missing={1}" -f $deployedAssetText.Length, ($missingDeployedMarkers -join ','))
Add-Check 'signoff-boundary' 'user signoff remains false' (-not [bool]$state.gates.user_script_passed) ("user_script_passed={0}" -f $state.gates.user_script_passed)

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R85_SYSTEM_DASHBOARD_DAILY_ACTION_HUB_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-085'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    accepted = ($status -eq 'PASS')
    checks = @($checks.ToArray())
    data = [ordered]@{
        systemId = $systemId
        quickTaskId = $plainTask.task.taskId
        quickTaskTitle = $plainTask.task.title
        projectTaskPreviewTotal = $projectTasksPage.total
        plainTaskPreviewTotal = $plainTasksPage.total
        pendingTodoTotal = $todos.page.total
        unreadMessageTotal = $messages.total
        moduleTotal = $modules.total
        dashboardTraceBefore = $dashboardBefore.traceId
        dashboardTraceAfter = $dashboardAfter.traceId
    }
})
$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
$summary = @('# R85 System Dashboard Daily Action Hub', '', "Status: `$status", '', 'This is engineering evidence only. It does not close user signoff.', '', '## Checks', '')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''; $summary += '## Data'; $summary += ''; $summary += ('- System: `{0}`' -f $systemId); $summary += ('- Quick task: `{0}`' -f $plainTask.task.taskId); $summary += ('- Pending todos: `{0}`' -f $todos.page.total); $summary += ('- Unread messages: `{0}`' -f $messages.total); $summary += ('- Modules: `{0}`' -f $modules.total); $summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R85 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }
