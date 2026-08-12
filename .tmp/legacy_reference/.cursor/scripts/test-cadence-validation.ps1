param(
    [string]$WorkspaceRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$sourceInstance = Join-Path $workspace '.cursor'
$currentContractHash = 'sha256:' + (
    Get-FileHash -Algorithm SHA256 (
        Join-Path $sourceInstance 'session/current-node.md'
    )
).Hash.ToLowerInvariant()
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('examine2-cadence-' + [guid]::NewGuid().ToString('N'))
$results = [System.Collections.Generic.List[string]]::new()

function New-Case([string]$Name) {
    $path = Join-Path $tempRoot $Name
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    Copy-Item -LiteralPath $sourceInstance -Destination (Join-Path $path '.cursor') -Recurse -Force
    return $path
}

function Read-State([string]$CasePath) {
    return Get-Content -Raw -Encoding UTF8 (Join-Path $CasePath '.cursor/session/state.json') | ConvertFrom-Json
}

function Write-State([string]$CasePath, $State) {
    $State | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 (Join-Path $CasePath '.cursor/session/state.json')
}

function Invoke-Validator([string]$CasePath) {
    try {
        $scriptPath = Join-Path $CasePath '.cursor/scripts/validate-framework.ps1'
        $root = Join-Path $CasePath '.cursor'
        $output = & $scriptPath -Root $root -Instance -Mode Active 2>&1
        return @{ Passed = $true; Output = ($output -join "`n") }
    } catch {
        return @{ Passed = $false; Output = $_.Exception.Message }
    }
}

function Expect-Reject([string]$Name, [scriptblock]$Mutate, [string]$Expected) {
    $casePath = New-Case $Name
    $state = Read-State $casePath
    & $Mutate $state
    Write-State $casePath $state
    $result = Invoke-Validator $casePath
    if ($result.Passed -or -not $result.Output.Contains($Expected)) {
        throw "Case $Name did not reject as expected. Output: $($result.Output)"
    }
    $results.Add("PASS reject $Name -> $Expected")
}

function Set-ValidOverrun($State) {
    $now = [DateTimeOffset]::Now
    $started = $now.AddHours(-5)
    $initialTarget = $now.AddHours(-1)
    $acceptedGate = @($State.sliceRequirementGates.PSObject.Properties |
        Where-Object { $_.Value.status -eq 'accepted' -and $_.Value.evidencePath -and $_.Value.evidenceSha256 } |
        Select-Object -First 1)
    if ($acceptedGate.Count -ne 1) {
        throw 'Cadence fixture requires at least one accepted slice requirement gate with evidence.'
    }
    $evidencePath = $acceptedGate[0].Value.evidencePath
    $evidenceHash = $acceptedGate[0].Value.evidenceSha256
    $fact = [pscustomobject]@{
        occurredAt = $started.AddMinutes(60).ToString('o')
        activeSpentMinutes = 60
        decision = 'facts_checked'
        evidencePath = $evidencePath
        evidenceSha256 = $evidenceHash
    }
    $replan = [pscustomobject]@{
        occurredAt = $started.AddMinutes(180).ToString('o')
        activeSpentMinutes = 180
        decision = 'critical_path_rechecked'
        evidencePath = $evidencePath
        evidenceSha256 = $evidenceHash
    }

    $task = $State.active.taskExecution[0]
    $task.contractHash = $currentContractHash
    $task.status = 'in_progress'
    $task.startedAt = $started.ToString('o')
    $task.targetAt = $initialTarget.ToString('o')
    $task.completedAt = $null
    $task.activeSpentMinutes = 250
    $task.remainingEstimateMinutes = 90
    $task.checkpointEvents.fact = $fact
    $task.checkpointEvents.replan = $replan
    $task.overrunHistory = @([pscustomobject]@{
        detectedAt = $now.AddMinutes(-50).ToString('o')
        reason = 'Migration verification required more work than estimated.'
        elapsedMinutes = 250
        remainingEstimateMinutes = 90
        revisedTargetAt = $now.AddHours(2).ToString('o')
        decision = 'continue_to_completion'
        scheduleAdjustment = 'Keep dependent tasks pending and move their targets after this task passes.'
        approvedBy = 'pm'
        evidencePath = $evidencePath
        evidenceSha256 = $evidenceHash
    })

    foreach ($pendingTask in @($State.active.taskExecution | Select-Object -Skip 1)) {
        $pendingTask.status = 'pending'
        $pendingTask.startedAt = $null
        $pendingTask.targetAt = $null
        $pendingTask.completedAt = $null
        $pendingTask.activeSpentMinutes = 0
        $pendingTask.remainingEstimateMinutes = $pendingTask.estimateMinutes
        $pendingTask.checkpointEvents.fact = $null
        $pendingTask.checkpointEvents.replan = $null
        $pendingTask.checkpointEvents.completion = $null
        $pendingTask.overrunHistory = @()
    }
    $State.active.slice.gate = 'in_progress'
    $State.active.slice.startedAt = $started.ToString('o')
    $State.active.slice.targetAt = $started.AddMinutes(720).ToString('o')
    $State.active.slice.completedAt = $null
    $State.active.slice.activeSpentMinutes = 250
    $State.active.slice.remainingEstimateMinutes = 470
    $State.active.slice.checkpointEvents.fact = $fact
    $State.active.slice.checkpointEvents.replan = $replan
    $State.active.slice.checkpointEvents.completion = $null
    $State.active.slice.overrunHistory = @()
}

try {
    $activeTaskId = (Read-State (New-Case 'active-task-id')).active.taskExecution[0].id
    Expect-Reject 'initial-estimate-241' {
        param($state)
        $state.active.taskExecution[0].estimateMinutes = 241
        $state.active.taskExecution[1].estimateMinutes = 239
    } '30..240 minute pre-execution estimate'

    $validPath = New-Case 'valid-overrun-continues'
    $validState = Read-State $validPath
    Set-ValidOverrun $validState
    Write-State $validPath $validState
    $validResult = Invoke-Validator $validPath
    if (-not $validResult.Passed) {
        throw "Valid overrun was rejected: $($validResult.Output)"
    }
    $results.Add('PASS accept valid-overrun-continues')

    Expect-Reject 'overrun-without-record' {
        param($state)
        Set-ValidOverrun $state
        $state.active.taskExecution[0].overrunHistory = @()
        $state.active.taskExecution[0].remainingEstimateMinutes = 0
    } 'exceeded its estimate without an overrun reforecast'

    Expect-Reject 'overrun-missing-schedule-adjustment' {
        param($state)
        Set-ValidOverrun $state
        $state.active.taskExecution[0].overrunHistory[0].PSObject.Properties.Remove('scheduleAdjustment')
    } 'overrun record is missing scheduleAdjustment'

    Expect-Reject 'reforecast-target-expired' {
        param($state)
        Set-ValidOverrun $state
        $state.active.taskExecution[0].overrunHistory[0].revisedTargetAt = [DateTimeOffset]::Now.AddMinutes(-10).ToString('o')
    } 'exceeded its current target; continue the same task after recording a new reforecast'

    Expect-Reject 'serial-declares-parallel-group' {
        param($state)
        $state.active.taskExecution[0].parallelGroup = 'fake-parallel'
    } "Serial task $activeTaskId cannot declare a parallel group"

    Expect-Reject 'parallel-no-net-benefit' {
        param($state)
        $state.active.taskExecution[0].executionMode = 'parallel'
        $state.active.taskExecution[0].parallelGroup = 'fake-parallel'
        $state.active.taskExecution[0].estimatedParallelSavingsMinutes = 10
        $state.active.taskExecution[0].estimatedIntegrationCostMinutes = 10
        $state.active.taskExecution[1].executionMode = 'parallel'
        $state.active.taskExecution[1].parallelGroup = 'fake-parallel'
        $state.active.taskExecution[1].estimatedParallelSavingsMinutes = 10
        $state.active.taskExecution[1].estimatedIntegrationCostMinutes = 10
    } 'positive net scheduling benefit'

    $results
} finally {
    $resolvedTemp = [IO.Path]::GetFullPath($tempRoot)
    $systemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolvedTemp.StartsWith($systemTemp, [StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolvedTemp)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
