param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r80-live-user-trial-workspace'
$ResultFile = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-2026-07-06.md'

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$RelativePath,
        [string[]]$Arguments = @()
    )

    $scriptPath = Join-Path $RepoRoot $RelativePath
    $logPath = Join-Path $WorkDir "$Name.log"
    $errPath = Join-Path $WorkDir "$Name.err.log"
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath) + $Arguments
    Push-Location $RepoRoot
    try {
        & powershell @argList > $logPath 2> $errPath
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = $errPath
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing expected JSON result: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

trap {
    $errorRecord = $_
    $failure = [ordered]@{
        status = 'FAIL'
        productStatus = 'R80_LIVE_USER_TRIAL_WORKSPACE_FAILED'
        task = 'REC-P0-080'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        userSignoff = $false
        error = $errorRecord.Exception.Message
    }
    $failure | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 10)
        exit 0
    }
    throw $errorRecord
}

$children = @()
$children += Invoke-ChildScript -Name 'verify-release' -RelativePath 'scripts/verify-release.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$children += Invoke-ChildScript -Name 'final-goal-framework-audit' -RelativePath 'scripts/final-goal-framework-audit.ps1'
$children += Invoke-ChildScript -Name 'final-usability-static-audit' -RelativePath 'scripts/final-usability-static-audit.ps1'
$children += Invoke-ChildScript -Name 'final-requirement-coverage-audit' -RelativePath 'scripts/final-requirement-coverage-audit.ps1' -Arguments @('-NoFailExit')
$children += Invoke-ChildScript -Name 'configured-runtime-trial-seed-r63' -RelativePath 'scripts/recovery-r63-configured-runtime-first-use.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-KeepCreatedData')
$children += Invoke-ChildScript -Name 'workflow-todo-message-trial-seed-r64' -RelativePath 'scripts/recovery-r64-workflow-todo-message-first-use.ps1' -Arguments @('-BaseUrl', $BaseUrl, '-KeepCreatedData')

$failedChildren = @($children | Where-Object { $_.exitCode -ne 0 })
if ($failedChildren.Count -gt 0) {
    throw "Child scripts failed: $(@($failedChildren | ForEach-Object { $_.name }) -join ', ')"
}

$r63 = Read-JsonFile (Join-Path $EvidenceDir 'r63-configured-runtime-first-use-result.json')
$r64 = Read-JsonFile (Join-Path $EvidenceDir 'r64-workflow-todo-message-first-use-result.json')
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')
$framework = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-goal-framework-audit-result.json')
$static = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-usability-static-audit-result.json')
$coverage = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-requirement-coverage-audit-result.json')

$checks = @(
    [ordered]@{
        area = 'release'
        name = 'release is verified before seeding trial workspace'
        passed = ($children[0].exitCode -eq 0)
        detail = $BaseUrl
    },
    [ordered]@{
        area = 'framework'
        name = 'framework audit recognizes R80 trial workspace task'
        passed = ($framework.status -eq 'PASS')
        detail = "errors=$(@($framework.errors).Count), warnings=$(@($framework.warnings).Count)"
    },
    [ordered]@{
        area = 'static-usability'
        name = 'static usability remains clean before trial handoff'
        passed = ($static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0 -and [int]$static.warningCount -eq 0)
        detail = "status=$($static.status), blockers=$($static.blockerCount), warnings=$($static.warningCount)"
    },
    [ordered]@{
        area = 'runtime-trial'
        name = 'configured runtime trial system remains after R63'
        passed = ($r63.status -eq 'PASS' -and @($r63.cleanup) -contains 'SKIPPED' -and -not [string]::IsNullOrWhiteSpace([string]$r63.handoff.systemId))
        detail = "system=$($r63.handoff.systemId), module=$($r63.handoff.moduleId), normal=$($r63.trialCredentials.normalLoginName)"
    },
    [ordered]@{
        area = 'workflow-trial'
        name = 'workflow/todo/message trial system remains after R64'
        passed = ($r64.status -eq 'PASS' -and @($r64.cleanup) -contains 'SKIPPED' -and -not [string]::IsNullOrWhiteSpace([string]$r64.systemId))
        detail = "system=$($r64.systemId), module=$($r64.moduleId), requester=$($r64.trialCredentials.requesterLoginName), approver=$($r64.trialCredentials.approverLoginName)"
    },
    [ordered]@{
        area = 'signoff-boundary'
        name = 'trial workspace does not close user signoff'
        passed = ($state.gates.user_script_passed -eq $false -and [int]$coverage.notClosedCount -gt 0)
        detail = "userScriptPassed=$($state.gates.user_script_passed), notClosed=$($coverage.notClosedCount)"
    }
)

$failedChecks = @($checks | Where-Object { -not $_.passed })
$status = if ($failedChecks.Count -eq 0) { 'PASS' } else { 'FAIL' }

$trialPack = [ordered]@{
    baseUrl = $BaseUrl
    admin = [ordered]@{
        loginName = 'admin'
        password = '123123aa'
        entry = "$BaseUrl/#/platform"
    }
    runtimeDailyUse = [ordered]@{
        systemId = [string]$r63.handoff.systemId
        tenantId = [string]$r63.handoff.tenantId
        moduleId = [string]$r63.handoff.moduleId
        normalLoginName = [string]$r63.trialCredentials.normalLoginName
        readonlyLoginName = [string]$r63.trialCredentials.readonlyLoginName
        password = [string]$r63.trialCredentials.password
        entry = "$BaseUrl/#/systems/$($r63.handoff.systemId)/modules"
        existingRecordId = [string]$r63.recordId
    }
    workflowTodoMessage = [ordered]@{
        systemId = [string]$r64.systemId
        tenantId = [string]$r64.tenantId
        moduleId = [string]$r64.moduleId
        flowId = [string]$r64.flowId
        requesterLoginName = [string]$r64.trialCredentials.requesterLoginName
        approverLoginName = [string]$r64.trialCredentials.approverLoginName
        password = [string]$r64.trialCredentials.password
        entry = "$BaseUrl/#/systems/$($r64.systemId)/modules"
        todoEntry = "$BaseUrl/#/systems/$($r64.systemId)/todos"
        messageEntry = "$BaseUrl/#/systems/$($r64.systemId)/messages"
        terminalRecordId = [string]$r64.recordId
    }
}

$result = [ordered]@{
    status = $status
    productStatus = 'R80_LIVE_USER_TRIAL_WORKSPACE_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-080'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = $checks
    childResults = $children
    trialPack = $trialPack
    accepted = ($status -eq 'PASS')
}

$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$adminLine = '- Admin: `admin / 123123aa`, entry `{0}`' -f $trialPack.admin.entry
$runtimeNormalLine = '- Runtime normal member: `{0} / {1}`, system `{2}`, module `{3}`' -f $trialPack.runtimeDailyUse.normalLoginName, $trialPack.runtimeDailyUse.password, $trialPack.runtimeDailyUse.systemId, $trialPack.runtimeDailyUse.moduleId
$runtimeReadonlyLine = '- Runtime readonly member: `{0} / {1}`' -f $trialPack.runtimeDailyUse.readonlyLoginName, $trialPack.runtimeDailyUse.password
$workflowRequesterLine = '- Workflow requester: `{0} / {1}`, system `{2}`, module `{3}`' -f $trialPack.workflowTodoMessage.requesterLoginName, $trialPack.workflowTodoMessage.password, $trialPack.workflowTodoMessage.systemId, $trialPack.workflowTodoMessage.moduleId
$workflowApproverLine = '- Workflow approver: `{0} / {1}`, todo entry `{2}`' -f $trialPack.workflowTodoMessage.approverLoginName, $trialPack.workflowTodoMessage.password, $trialPack.workflowTodoMessage.todoEntry

$summary = @(
    '# R80 Live User Trial Workspace',
    '',
    "Status: $status",
    '',
    'This is engineering evidence only. It seeds real trial data and keeps `gates.user_script_passed=false`.',
    '',
    '## Trial Entries',
    '',
    $adminLine,
    $runtimeNormalLine,
    $runtimeReadonlyLine,
    $workflowRequesterLine,
    $workflowApproverLine,
    '',
    '## Evidence',
    '',
    '- Release verification passed before seeding.',
    '- R63 configured runtime trial data was kept.',
    '- R64 workflow/todo/message trial data was kept.',
    '- R79 signoff boundary stayed false.',
    '',
    'Result JSON: `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`'
) -join [Environment]::NewLine
Set-Content -LiteralPath $SummaryFile -Value $summary -Encoding UTF8

$result | ConvertTo-Json -Depth 20

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
