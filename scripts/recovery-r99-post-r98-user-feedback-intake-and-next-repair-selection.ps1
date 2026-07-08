param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$EvidenceDir = Join-Path $Root 'docs/evidence/recovery'
$ResultPath = Join-Path $EvidenceDir 'r99-post-r98-user-feedback-intake-and-next-repair-selection-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r99-post-r98-user-feedback-intake-and-next-repair-selection-2026-07-08.md'

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing JSON file: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

function Read-TextFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing text file: $Path"
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
}

$checks = [System.Collections.Generic.List[object]]::new()
function Add-Check {
    param(
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

try {
    New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

    $state = Read-JsonFile (Join-Path $Root '.cursor/session/state.json')
    $r98 = Read-JsonFile (Join-Path $EvidenceDir 'r98-final-user-trial-readiness-and-signoff-path-result.json')
    $coverage = Read-JsonFile (Join-Path $Root 'docs/evidence/final-requirement-coverage-audit-result.json')
    $taskCards = Read-TextFile (Join-Path $Root 'docs/recovery/p0-task-cards.md')
    $ledger = Read-TextFile (Join-Path $Root 'docs/framework/next-execution-ledger.md')
    $tempFlow = Read-TextFile (Join-Path $Root 'temp_flow.md')
    $platformShell = Read-TextFile (Join-Path $Root 'frontend/src/features/platform/platformShell.ts')

    $nextTasks = @($state.build_plan.nextTasks)
    $acceptedBatches = @($state.build_plan.acceptedBatches)
    Add-Check 'state' 'R99 is the active recovery task' (($state.build_plan.currentBatch -eq 'RECOVERY-R99') -and ($nextTasks -contains 'REC-P0-099 Post-R98 User Trial Feedback Intake And Residual Repair Selection')) ("currentBatch={0}, nextTasks={1}" -f $state.build_plan.currentBatch, ($nextTasks -join '; '))
    Add-Check 'state' 'R98 is accepted engineering evidence' ($acceptedBatches -contains 'RECOVERY-R98') ('acceptedBatches contains R98={0}' -f ($acceptedBatches -contains 'RECOVERY-R98'))
    Add-Check 'signoff-boundary' 'user signoff remains false' (-not [bool]$state.gates.user_script_passed) ('state.gates.user_script_passed={0}' -f $state.gates.user_script_passed)

    Add-Check 'r98' 'R98 result is PASS and checklist exists' (($r98.status -eq 'PASS') -and (Test-Path -LiteralPath (Join-Path $EvidenceDir 'r98-user-trial-checklist-2026-07-08.md'))) ("r98.status={0}" -f $r98.status)
    Add-Check 'coverage' 'coverage remains open and visible' (([int]$coverage.missingCount -eq 0) -and ([int]$coverage.notClosedCount -gt 0)) ("missing={0}, notClosed={1}" -f $coverage.missingCount, $coverage.notClosedCount)
    Add-Check 'contract' 'R99 task card and next ledger row exist' (($taskCards -match 'REC-P0-099 Post-R98 User Trial Feedback Intake And Residual Repair Selection') -and ($ledger -match 'REC-P0-099 Post-R98 User Trial Feedback Intake And Residual Repair Selection')) 'R99 contract is present on disk.'

    $flowRequirementPresent = ($tempFlow -match '### P2 \S* Flow') -and
        ($tempFlow -match 'Flow 名称、触发源、影响系统、最近运行、失败反馈、审计状态、操作') -and
        ($tempFlow -match '禁止：用通用说明抽屉代替 Flow 详情') -and
        ($tempFlow -match '### P3 平台应用/授权系统') -and
        ($tempFlow -match '授权系统、租户、系统管理员、授权模块、到期时间、数据隔离、状态') -and
        ($tempFlow -match 'authorizationChangeId') -and
        ($tempFlow -match '不能用“进入系统”绕过顶部系统切换')
    Add-Check 'requirement-source' 'temp_flow P2/P3 depth requirements are present' $flowRequirementPresent 'P2/P3 define Flow list/detail/run evidence and Application authorization/request evidence.'

    $flowBoundaryExists = ($platformShell -match 'platformFlowPage') -and
        ($platformShell -match 'platformFlowSeparatedFromApplication') -and
        ($platformShell -match 'platformFlowList')
    Add-Check 'source-baseline' 'Platform Flow boundary markers exist' $flowBoundaryExists 'R93/R95 boundary markers are present.'

    $applicationBoundaryExists = ($platformShell -match 'platformAppsSeparatedFromSystemEntry') -and
        ($platformShell -match 'platformApplicationRow') -and
        ($platformShell -notmatch 'data-platform-apps-system-entry-card')
    Add-Check 'source-baseline' 'Platform Application remains separated from system entry' $applicationBoundaryExists 'Application page has rows and no system-entry card marker.'

    $flowDepthMissing = -not (
        ($platformShell -match 'platformFlowRow') -and
        ($platformShell -match 'platformFlowDetailPanel') -and
        ($platformShell -match 'platformFlowRunBatch') -and
        ($platformShell -match 'platformFlowRetryAction') -and
        ($platformShell -match 'platformFlowCompensationAction')
    )
    Add-Check 'next-selection' 'Platform Flow depth gap is still concrete' $flowDepthMissing 'Current source does not yet expose Flow row/detail/run-batch/retry/compensation markers required by P2.'

    $applicationDepthMissing = -not (
        ($platformShell -match 'platformAuthorizationRow') -and
        ($platformShell -match 'platformAuthorizationRequest') -and
        ($platformShell -match 'authorizationChangeId') -and
        ($platformShell -match 'requestId') -and
        ($platformShell -match 'platformAuthorizationDetailPanel')
    )
    Add-Check 'next-selection' 'Platform Application authorization gap is still concrete' $applicationDepthMissing 'Current source does not yet expose authorization rows, request/change ids, and detail panel required by P3.'

    $selectedNextTask = [ordered]@{
        taskId = 'REC-P0-100'
        title = 'Platform Flow Application Workbench Depth And Action Clarity Closure'
        reason = 'R98 prepared user trial and kept signoff open. The latest explicit user feedback and temp_flow P2/P3 require Flow and Application to be independent task modules with lists, details, request/change ids, run feedback, traceId, disabled reasons, and no system-entry bypass. R93/R95 fixed the boundary, but the current frontend still lacks that depth.'
        requirementRows = @('REQ-4.1', 'REQ-4.2', 'REQ-5.15', 'REQ-6.1', 'REQ-6.2', 'REQ-6.3', 'REQ-6.11', 'REQ-9', 'REQ-2.1')
        flowIds = @('P2', 'P3', 'P6', 'P7', 'P9', 'S1', 'E1')
        journeyRows = @('P1', 'P2', 'P3', 'J1', 'J2', 'J5', 'J8', 'J9', 'J11')
        implementationBoundary = 'Frontend platform Flow/Application workbench depth first. Use existing platform health/admin/system-context APIs where available. Do not create direct system business writes from platform Flow/Application; route system data through system switch and system admin/business shells.'
        plannedEvidence = @(
            'scripts/recovery-r100-platform-flow-application-depth.ps1',
            'scripts/recovery-r100-browser-audit.js',
            'docs/evidence/recovery/r100-platform-flow-application-depth-result.json'
        )
    }

    $failed = @($checks.ToArray() | Where-Object { -not $_.passed })
    $status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }

    $result = [ordered]@{
        status = $status
        productStatus = 'R99_ACCEPTED_AS_DECISION_EVIDENCE_ONLY'
        task = 'REC-P0-099'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        userSignoff = $false
        r98Status = [string]$r98.status
        coverage = [ordered]@{
            missingCount = [int]$coverage.missingCount
            notClosedCount = [int]$coverage.notClosedCount
        }
        selectedNextTask = $selectedNextTask
        checks = @($checks.ToArray())
        accepted = ($status -eq 'PASS')
    }
    $result | ConvertTo-Json -Depth 50 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath

    $summary = @(
        '# R99 Post-R98 Feedback Intake And Next Repair Selection',
        '',
        ('Status: `{0}` as decision evidence only.' -f $status),
        '',
        ('Generated at: `{0}`' -f $result.generatedAt),
        ('Base URL: `{0}`' -f $BaseUrl),
        ('R98 status: `{0}`' -f $result.r98Status),
        ('Coverage missing/notClosed: `{0}` / `{1}`' -f $result.coverage.missingCount, $result.coverage.notClosedCount),
        ('User signoff: `{0}`' -f $false),
        '',
        'Selected next task:',
        '',
        ('- `{0}` {1}' -f $selectedNextTask.taskId, $selectedNextTask.title),
        ('- Reason: {0}' -f $selectedNextTask.reason),
        ('- Flow ids: `{0}`' -f ($selectedNextTask.flowIds -join '`, `')),
        ('- Requirement rows: `{0}`' -f ($selectedNextTask.requirementRows -join '`, `')),
        '',
        'Boundary:',
        '',
        '- R99 does not claim final completion.',
        '- R99 does not set `gates.user_script_passed=true`.',
        '- Product coding must start from the R100 task card and keep platform Flow/Application separate from system entry.'
    )
    $summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath

    Write-Output ($result | ConvertTo-Json -Depth 50)
    if (($status -ne 'PASS') -and (-not $NoFailExit)) {
        exit 1
    }
} catch {
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R99_DECISION_EVIDENCE_FAILED'
        task = 'REC-P0-099'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        userSignoff = $false
        error = $_.Exception.Message
        checks = @($checks.ToArray())
        accepted = $false
    }
    $failure | ConvertTo-Json -Depth 50 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 50)
        exit 0
    }
    throw
}

