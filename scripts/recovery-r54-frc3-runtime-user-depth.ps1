param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        throw "$Area/$Name failed: $Detail"
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $allArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell @allArgs 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logFile
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected result file was not found: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

trap {
    if ($script:FailureResultFile) {
        $failure = [ordered]@{
            status = 'FAIL'
            productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
            generatedAt = (Get-Date).ToString('o')
            baseUrl = $BaseUrl
            error = $_.Exception.Message
            checks = @($script:Checks.ToArray())
        }
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:FailureResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r54-frc3-runtime-user-depth'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r54-frc3-runtime-user-depth-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r54-frc3-runtime-user-depth-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'runtime-user-depth-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Add-Check $script:Checks 'release' 'health reports database/schema/redis UP' `
    ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    ($Health | ConvertTo-Json -Depth 12 -Compress)

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
Add-Check $script:Checks 'frontend-static' 'no blocker or warning copy/placeholder findings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

$childResults += Invoke-ChildScript 'recovery-r47-runtime-daily-use' `
    (Join-Path $PSScriptRoot 'recovery-r47-runtime-daily-use-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R47 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r47-runtime-daily-use-result.json')
Add-Check $script:Checks 'runtime-record-file-import-export' 'R47 fresh runtime daily-use record file import export passed' `
    ($R47.status -eq 'PASS' -and [int]$R47.emptySearchTotal -eq 0 -and -not [string]::IsNullOrWhiteSpace([string]$R47.recordId) -and -not [string]::IsNullOrWhiteSpace([string]$R47.uploadedFileId) -and [int]$R47.detailAttachmentCount -ge 1 -and [int]$R47.detailHistoryCount -ge 1 -and [int]$R47.importValidRows -ge 2 -and [string]$R47.importTaskStatus -eq 'SUCCESS' -and [int]$R47.importInsertedCount -ge 2 -and [string]$R47.exportTaskStatus -eq 'SUCCESS' -and -not [string]::IsNullOrWhiteSpace([string]$R47.exportResultFileId) -and [int]$R47.runtimeSearchTotal -ge 3 -and $R47.hiddenFieldLeaked -eq $false -and [int]$R47.forbiddenCreateStatus -eq 403 -and [int]$R47.forbiddenAdminStatus -eq 403 -and [int]$R47.browserOverflowCount -eq 0 -and [int]$R47.browserBlockerCount -eq 0) `
    "record=$($R47.recordId), file=$($R47.uploadedFileId), import=$($R47.importTaskStatus)/$($R47.importInsertedCount), export=$($R47.exportTaskStatus), browser=$($R47.browserResultCount)"

$childResults += Invoke-ChildScript 'recovery-r48-workflow-message-flow' `
    (Join-Path $PSScriptRoot 'recovery-r48-workflow-message-flow-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R48 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r48-workflow-message-flow-result.json')
Add-Check $script:Checks 'message-todo-approval' 'R48 fresh message todo approval runtime closure passed' `
    ($R48.status -eq 'PASS' -and $R48.publishCheckPassed -eq $true -and $R48.simulationPassed -eq $true -and [int]$R48.requesterPendingTodoTotal -eq 0 -and [int]$R48.approverPendingTodoTotal -eq 1 -and [int]$R48.approverMessageTotal -eq 1 -and [int]$R48.requesterApproveDeniedStatus -eq 403 -and [int]$R48.normalAdminDeniedStatus -eq 403 -and [string]$R48.todoActionStatus -eq 'HANDLED' -and [string]$R48.duplicateTodoActionStatus -eq 'HANDLED' -and [string]$R48.detailTerminalStatus -eq 'APPROVED' -and [string]$R48.approvalSidebarStatus -eq 'APPROVED' -and [int]$R48.pendingAfterApproveTotal -eq 0 -and [int]$R48.handledAfterApproveTotal -eq 1 -and [int]$R48.browserOverflowCount -eq 0 -and [int]$R48.browserBlockerCount -eq 0) `
    "todo=$($R48.pendingTodoId), message=$($R48.pendingMessageId), action=$($R48.todoActionStatus), terminal=$($R48.detailTerminalStatus), browserBefore=$($R48.browserBeforeResultCount), browserAfter=$($R48.browserAfterResultCount)"

$childResults += Invoke-ChildScript 'recovery-r43-role-home-whole-path-usability' `
    (Join-Path $PSScriptRoot 'recovery-r43-role-home-whole-path-usability-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R43 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r43-role-home-whole-path-usability-result.json')
$workRoutes = @($R43.results | Where-Object { $_.key -match 'system-work' })
$todoRoutes = @($R43.results | Where-Object { $_.key -match 'system-todos' })
$messageRoutes = @($R43.results | Where-Object { $_.key -match 'system-messages' })
Add-Check $script:Checks 'role-work-message-mobile-surface' 'R43 fresh role work todo message mobile surfaces passed' `
    ($R43.status -eq 'PASS' -and [int]$R43.routeCount -ge 16 -and [int]$R43.resultCount -ge 32 -and [int]$R43.failureCount -eq 0 -and [int]$R43.warningCount -eq 0 -and $workRoutes.Count -ge 4 -and $todoRoutes.Count -ge 4 -and $messageRoutes.Count -ge 4) `
    "routes=$($R43.routeCount), results=$($R43.resultCount), workRoutes=$($workRoutes.Count), todoRoutes=$($todoRoutes.Count), messageRoutes=$($messageRoutes.Count)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R54 aggregation of fresh R47/R48/R43 deployed browser audits'
    r47 = @{
        resultFile = 'docs/evidence/recovery/r47-runtime-daily-use-result.json'
        browserAuditPath = $R47.browserAuditPath
        browserResultCount = $R47.browserResultCount
        browserOverflowCount = $R47.browserOverflowCount
        browserBlockerCount = $R47.browserBlockerCount
    }
    r48 = @{
        resultFile = 'docs/evidence/recovery/r48-workflow-message-flow-result.json'
        browserAuditPath = $R48.browserAuditPath
        browserBeforeResultCount = $R48.browserBeforeResultCount
        browserAfterResultCount = $R48.browserAfterResultCount
        browserOverflowCount = $R48.browserOverflowCount
        browserBlockerCount = $R48.browserBlockerCount
    }
    r43 = @{
        resultFile = 'docs/evidence/recovery/r43-role-home-whole-path-usability-result.json'
        routeCount = $R43.routeCount
        resultCount = $R43.resultCount
        failureCount = $R43.failureCount
        warningCount = $R43.warningCount
        workRoutes = $workRoutes.Count
        todoRoutes = $todoRoutes.Count
        messageRoutes = $messageRoutes.Count
    }
}
$browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.4', 'REQ-5.11', 'REQ-5.13', 'REQ-5.14', 'REQ-5.16', 'REQ-5.20', 'REQ-6.4', 'REQ-6.5', 'REQ-6.6', 'REQ-6.11')
    journeyRows = @('J3', 'J4', 'J8', 'J9', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r54-frc3-runtime-user-depth/runtime-user-depth-browser-audit.json'
    runtimeDailyUse = @{
        systemId = $R47.systemId
        moduleId = $R47.moduleId
        recordId = $R47.recordId
        uploadedFileId = $R47.uploadedFileId
        detailAttachmentCount = $R47.detailAttachmentCount
        detailHistoryCount = $R47.detailHistoryCount
        importTaskStatus = $R47.importTaskStatus
        importInsertedCount = $R47.importInsertedCount
        exportTaskStatus = $R47.exportTaskStatus
        exportResultFileId = $R47.exportResultFileId
        runtimeSearchTotal = $R47.runtimeSearchTotal
        hiddenFieldLeaked = $R47.hiddenFieldLeaked
    }
    messageTodoApproval = @{
        systemId = $R48.systemId
        moduleId = $R48.moduleId
        recordId = $R48.recordId
        pendingTodoId = $R48.pendingTodoId
        pendingMessageId = $R48.pendingMessageId
        todoActionStatus = $R48.todoActionStatus
        detailTerminalStatus = $R48.detailTerminalStatus
        approvalSidebarStatus = $R48.approvalSidebarStatus
        pendingAfterApproveTotal = $R48.pendingAfterApproveTotal
        handledAfterApproveTotal = $R48.handledAfterApproveTotal
    }
    workMessageSurface = @{
        systemId = $R43.systemId
        routeCount = $R43.routeCount
        resultCount = $R43.resultCount
        workRoutes = $workRoutes.Count
        todoRoutes = $todoRoutes.Count
        messageRoutes = $messageRoutes.Count
    }
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-054 / R54 FRC-3 Runtime User Depth Fresh Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.4, REQ-5.11, REQ-5.13, REQ-5.14, REQ-5.16, REQ-5.20, REQ-6.4, REQ-6.5, REQ-6.6, REQ-6.11
- Fresh runtime daily use: R47 PASS, record=$($R47.recordId), attachmentCount=$($R47.detailAttachmentCount), historyCount=$($R47.detailHistoryCount), import=$($R47.importTaskStatus)/$($R47.importInsertedCount), export=$($R47.exportTaskStatus), browserResultCount=$($R47.browserResultCount), overflow=$($R47.browserOverflowCount), blockers=$($R47.browserBlockerCount)
- Fresh message/todo/approval: R48 PASS, todo=$($R48.pendingTodoId), message=$($R48.pendingMessageId), action=$($R48.todoActionStatus), terminal=$($R48.detailTerminalStatus), browserBefore=$($R48.browserBeforeResultCount), browserAfter=$($R48.browserAfterResultCount), overflow=$($R48.browserOverflowCount), blockers=$($R48.browserBlockerCount)
- Fresh work/todo/message/mobile surface: R43 PASS, routeCount=$($R43.routeCount), resultCount=$($R43.resultCount), failures=$($R43.failureCount), warnings=$($R43.warningCount), workRoutes=$($workRoutes.Count), todoRoutes=$($todoRoutes.Count), messageRoutes=$($messageRoutes.Count)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Browser aggregation: `docs/evidence/recovery/screenshots/r54-frc3-runtime-user-depth/runtime-user-depth-browser-audit.json`

This does not close final product acceptance. Full requirement coverage and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
