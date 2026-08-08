param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$Instance,
    [ValidateSet('Structure', 'Active', 'Release')]
    [string]$Mode = 'Structure',
    [string[]]$ForbiddenPattern = @()
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).Path

$requiredFiles = @(
    'README.md',
    'LEADER.md',
    'GOVERNANCE.md',
    'agents/README.md',
    'agents/_role-template.md',
    'skills/README.md',
    'skills/playbooks.md',
    'workflows/lifecycle.md',
    'templates/engineering-node.md',
    'templates/requirement-package.md',
    'templates/design-package.md',
    'templates/final-goal-ledger.md',
    'templates/journey-gate.md',
    'templates/task.md',
    'templates/issue.md',
    'templates/meeting-decision.md',
    'templates/acceptance.md',
    'templates/pending-user-decision.md',
    'scripts/validate-project-progress.ps1',
    'scripts/render-project-progress.ps1'
)

if ($Instance) {
    $requiredFiles += 'INSTANCE.md'
    $requiredFiles += 'session/state.json'
    $requiredFiles += 'session/project-progress.json'
    $requiredFiles += 'session/PROJECT_PROGRESS.md'
} else {
    $requiredFiles += 'INSTANCE.template.md'
    $requiredFiles += 'session/state.template.json'
    $requiredFiles += 'session/project-progress.template.json'
    $requiredFiles += 'session/PROJECT_PROGRESS.template.md'
    $requiredFiles += 'templates/progress-package-contract.md'
}

$roles = @(
    'leader', 'pm', 'product', 'analyst', 'architect', 'uiux',
    'planner', 'dba', 'backend', 'frontend', 'test', 'ops'
)

foreach ($role in $roles) {
    $requiredFiles += "agents/$role/role.md"
}

$missing = @()
foreach ($relative in $requiredFiles) {
    $path = Join-Path $rootPath $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $missing += $relative
    }
}
if ($missing.Count -gt 0) {
    throw "Missing framework files: $($missing -join ', ')"
}

foreach ($relative in $requiredFiles) {
    $path = Join-Path $rootPath $relative
    $text = [System.IO.File]::ReadAllText($path)
    if ($text -match '(\r?\n){2,}\z') {
        throw "Framework file has extra blank lines at EOF: $relative"
    }
}

$utf8 = New-Object System.Text.UTF8Encoding($false, $true)
$textFiles = Get-ChildItem -LiteralPath $rootPath -Recurse -File | Where-Object {
    $_.Extension -in @('.md', '.json', '.jsonl', '.ps1')
}
foreach ($file in $textFiles) {
    try {
        [void]$utf8.GetString([System.IO.File]::ReadAllBytes($file.FullName))
    } catch {
        throw "File is not strict UTF-8: $($file.FullName)"
    }
}

$progressValidator = Join-Path $rootPath 'scripts/validate-project-progress.ps1'
if ($Instance) {
    & $progressValidator -Root $rootPath | Out-Null
} else {
    & $progressValidator -Root $rootPath `
        -JsonPath 'session/project-progress.template.json' `
        -MarkdownPath 'session/PROJECT_PROGRESS.template.md' | Out-Null
}
$requiredSectionNumbers = 1..12

$skillsText = Get-Content -Raw -Encoding UTF8 (Join-Path $rootPath 'skills/README.md')
$playbooksText = Get-Content -Raw -Encoding UTF8 (Join-Path $rootPath 'skills/playbooks.md')
foreach ($role in $roles) {
    $rolePath = Join-Path $rootPath "agents/$role/role.md"
    $roleText = Get-Content -Raw -Encoding UTF8 $rolePath
    foreach ($sectionNumber in $requiredSectionNumbers) {
        if ($roleText -notmatch "(?m)^## $sectionNumber\.") {
            throw "Role '$role' is missing section number '$sectionNumber'."
        }
    }

    $skillSection = [regex]::Match(
        $roleText,
        '(?s)## 7\.[^\r\n]*\r?\n(.*?)\r?\n## 8\.'
    )
    if (-not $skillSection.Success) {
        throw "Role '$role' has no parseable required-skill section."
    }
    $skillIds = [regex]::Matches($skillSection.Groups[1].Value, '`([^`]+)`')
    foreach ($match in $skillIds) {
        $skillId = $match.Groups[1].Value
        if (-not $skillsText.Contains("``$skillId``")) {
            throw "Role '$role' references unknown skill '$skillId'."
        }
        if ($playbooksText -notmatch "(?m)^## $([regex]::Escape($skillId))$") {
            throw "Role '$role' references skill '$skillId' without an execution playbook."
        }
    }
}

$statePath = if ($Instance) {
    Join-Path $rootPath 'session/state.json'
} else {
    Join-Path $rootPath 'session/state.template.json'
}
$stateObject = Get-Content -Raw -Encoding UTF8 $statePath | ConvertFrom-Json

if ($null -eq $stateObject.projectProgress) {
    throw 'State is missing the projectProgress contract entry.'
}
$projectProgressFields = @($stateObject.projectProgress.PSObject.Properties.Name | Sort-Object)
$expectedProjectProgressFields = @(
    'packageCheckpointLimit', 'projection', 'source', 'validator'
) | Sort-Object
if (($projectProgressFields -join '|') -ne ($expectedProjectProgressFields -join '|') -or
    [string]::IsNullOrWhiteSpace([string]$stateObject.projectProgress.source) -or
    [string]::IsNullOrWhiteSpace([string]$stateObject.projectProgress.projection) -or
    [string]::IsNullOrWhiteSpace([string]$stateObject.projectProgress.validator) -or
    $stateObject.projectProgress.packageCheckpointLimit -ne 3) {
    throw 'State projectProgress must declare source, projection, validator and exactly three package checkpoints.'
}

$cadenceChecks = @{
    'workflows/lifecycle.md' = @('cadence_contract: baseline_target<=240m; task_estimate=10..120m; delivery_cycle=240m; cycle_package=exactly_once; verification=affected_tasks_then_cycle_then_release; parallel=disjoint_modules_by_default')
    'GOVERNANCE.md' = @('governance_contract: routine|standard|critical; task_estimate=10..120m; delivery_cycle=240m; cycle_package=exactly_once; task_package=forbidden; parallel_default=disjoint_modules')
    'templates/task.md' = @('status_ref:', 'delivery_cycle_ref:', 'estimate_minutes: 10..120', 'module_scope:', 'write_scope:', 'quality_stage:', 'responsive_scope:', 'package_scope: forbidden_for_task')
    'templates/engineering-node.md' = @('status_ref:', 'slice_gate_ref:', 'cycle_id:', 'delivery_cycle_minutes: `240`', 'rolling_package_path:', 'task_count: `dependency_graph`')
    'agents/leader/role.md' = @('cadence_contract: reject_coding_task>120m; delivery_cycle=240m; cycle_package=exactly_once; task_package=forbidden')
    'agents/pm/role.md' = @('cadence_contract: task<=120m; delivery_cycle=240m; cycle_package=exactly_once; task_package=forbidden; parallel_default=disjoint_modules')
    'agents/planner/role.md' = @('cadence_contract: task_estimate=10..120m; delivery_cycle=240m; cycle_package=exactly_once; task_package=forbidden; parallel_default=disjoint_modules')
}
foreach ($relative in $cadenceChecks.Keys) {
    $content = Get-Content -Raw -Encoding UTF8 (Join-Path $rootPath $relative)
    foreach ($token in $cadenceChecks[$relative]) {
        if (-not $content.Contains($token)) {
            throw "Framework cadence contract missing '$token' in $relative."
        }
    }
}

if ($null -eq $stateObject.deliveryCadence) {
    throw 'State is missing deliveryCadence.'
}
if ($stateObject.schemaVersion -lt 4) {
    throw 'State schemaVersion must be at least 4.'
}
if ([string]::IsNullOrWhiteSpace([string]$stateObject.lifecyclePhase) -or
    [string]::IsNullOrWhiteSpace([string]$stateObject.deliveryPhase) -or
    $stateObject.PSObject.Properties.Name -contains 'phase') {
    throw 'State must separate lifecyclePhase and deliveryPhase and must not use the ambiguous phase field.'
}
if ($stateObject.deliveryCadence.taskEstimateMinMinutes -ne 10 -or
    $stateObject.deliveryCadence.taskEstimateMaxMinutes -ne 120 -or
    $stateObject.deliveryCadence.factCheckpointFormula -ne 'routine:none;critical:min(30m,estimate*50%)' -or
    $stateObject.deliveryCadence.replanCheckpointFormula -ne 'active>120m:single_replan' -or
    $stateObject.deliveryCadence.overrunReviewFormula -ne 'estimate*100%' -or
    $stateObject.deliveryCadence.demoPeriodTargetMinutes -ne 240 -or
    $stateObject.deliveryCadence.overrunPolicy -ne 'warn_replan_without_blocking_disjoint_work' -or
    $stateObject.deliveryCadence.schedulingPolicy -ne 'disjoint_modules_parallel_by_default') {
    throw 'State delivery cadence must enforce short module tasks, layered verification and disjoint-module parallelism.'
}
if ($null -eq $stateObject.gates.requirementsBaselineAccepted) {
    throw 'State is missing requirementsBaselineAccepted.'
}
if ($null -eq $stateObject.sliceRequirementGates) {
    throw 'State is missing sliceRequirementGates.'
}
$workstreams = @($stateObject.active.workstreams)
if ($workstreams.Count -gt 0) {
    $workstreamIds = @($workstreams.id)
    if (@($workstreamIds | Sort-Object -Unique).Count -ne $workstreamIds.Count) {
        throw 'Active workstream ids must be unique.'
    }
    $allWorkstreamTaskIds = @()
    $crossWorkstreamWrites = @{}
    foreach ($workstream in $workstreams) {
        if ([string]::IsNullOrWhiteSpace([string]$workstream.id) -or
            [string]::IsNullOrWhiteSpace([string]$workstream.goal) -or
            $workstream.status -notin @('planned', 'ready', 'in_progress', 'passed', 'blocked')) {
            throw 'Active workstream metadata is invalid.'
        }
        foreach ($task in @($workstream.tasks)) {
            $allWorkstreamTaskIds += [string]$task.id
            foreach ($field in @('id', 'result', 'moduleScope', 'writeScope', 'dependsOnArtifacts',
                    'estimateMinutes', 'qualityStage', 'responsiveScope', 'acceptanceCommand')) {
                if ($task.PSObject.Properties.Name -notcontains $field) {
                    throw "Workstream task $($task.id) is missing $field."
                }
            }
            if ([string]::IsNullOrWhiteSpace([string]$task.id) -or
                [string]::IsNullOrWhiteSpace([string]$task.result) -or
                [string]::IsNullOrWhiteSpace([string]$task.moduleScope) -or
                [string]::IsNullOrWhiteSpace([string]$task.acceptanceCommand) -or
                $task.estimateMinutes -lt 10 -or $task.estimateMinutes -gt 120 -or
                $task.qualityStage -notin @('functional', 'hardening') -or
                $task.responsiveScope -notin @('deferred', 'in_scope') -or
                @($task.writeScope).Count -eq 0) {
                throw "Workstream task $($task.id) has an invalid short-task contract."
            }
            if ($workstream.status -in @('ready', 'in_progress')) {
                foreach ($writePath in @($task.writeScope)) {
                    $normalizedWrite = ([string]$writePath).Replace('\', '/').TrimEnd('/').ToLowerInvariant()
                    foreach ($existing in @($crossWorkstreamWrites.GetEnumerator())) {
                        if ($existing.Value -ne $workstream.id -and
                            ($existing.Key -eq $normalizedWrite -or
                             $existing.Key.StartsWith("$normalizedWrite/") -or
                             $normalizedWrite.StartsWith("$($existing.Key)/"))) {
                            throw "Ready workstreams $($existing.Value) and $($workstream.id) overlap at $writePath."
                        }
                    }
                    $crossWorkstreamWrites[$normalizedWrite] = [string]$workstream.id
                }
            }
        }
    }
    if (@($allWorkstreamTaskIds | Sort-Object -Unique).Count -ne $allWorkstreamTaskIds.Count) {
        throw 'Workstream task ids must be globally unique.'
    }
}
if ($Mode -eq 'Structure') {
    if ($Instance) {
        $instancePath = Join-Path $rootPath 'INSTANCE.md'
        $instanceText = Get-Content -Raw -Encoding UTF8 $instancePath
        if ($instanceText -match '\{\{[A-Z0-9_]+\}\}') {
            throw 'Instance configuration still contains unresolved placeholders.'
        }
        foreach ($relative in @('session/current-node.md', 'session/delivery-roadmap.md')) {
            $dynamicMirrorText = Get-Content -Raw -Encoding UTF8 (Join-Path $rootPath $relative)
            if ($dynamicMirrorText -match '(?m)^\s*-\s*(status|phase|started_at|updated_at):\s*' -or
                $dynamicMirrorText -match '(?m)^\|[^\r\n]*\|\s*status\s*\|') {
                throw "$relative mirrors mutable runtime state instead of referencing session/state.json."
            }
        }
    }
    foreach ($pattern in $ForbiddenPattern) {
        $matches = $textFiles | Select-String -Pattern $pattern
        if ($matches) {
            $locations = $matches | ForEach-Object { "$($_.Path):$($_.LineNumber)" }
            throw "Forbidden pattern '$pattern' found at: $($locations -join ', ')"
        }
    }
    Write-Output "Framework structure validation passed: $rootPath"
    exit 0
}
$stateProjectRoot = Split-Path -Parent $rootPath
function Resolve-StateArtifactPath([string]$PathValue) {
    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }
    $normalized = $PathValue.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    if ($normalized.StartsWith(".$([System.IO.Path]::DirectorySeparatorChar)")) {
        return [System.IO.Path]::GetFullPath((Join-Path $stateProjectRoot $normalized))
    }
    $rootCandidate = [System.IO.Path]::GetFullPath((Join-Path $rootPath $normalized))
    if (Test-Path -LiteralPath $rootCandidate) {
        return $rootCandidate
    }
    return [System.IO.Path]::GetFullPath((Join-Path $stateProjectRoot $normalized))
}
function Assert-StateArtifact([string]$PathValue, [string]$HashValue, [string]$Label) {
    $artifactPath = Resolve-StateArtifactPath $PathValue
    if ($null -eq $artifactPath -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        throw "$Label has no evidence artifact."
    }
    $expectedHash = "sha256:$((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant())"
    if ($HashValue -ne $expectedHash) {
        throw "$Label evidence hash does not match its artifact."
    }
}
function Assert-EvidenceRecord($Record, [string]$Label) {
    if ($null -eq $Record) {
        throw "$Label evidence record is missing."
    }
    foreach ($field in @('path', 'sha256', 'verdict')) {
        if ($Record.PSObject.Properties.Name -notcontains $field) {
            throw "$Label evidence record is missing $field."
        }
    }
    if ($Record.verdict -ne 'pass') {
        throw "$Label evidence verdict is not pass."
    }
    Assert-StateArtifact ([string]$Record.path) ([string]$Record.sha256) $Label
}
function Assert-CheckpointEvent($Event, [string]$Label) {
    if ($null -eq $Event) {
        throw "$Label checkpoint event is missing."
    }
    foreach ($field in @('occurredAt', 'activeSpentMinutes', 'decision', 'evidencePath', 'evidenceSha256')) {
        if ($Event.PSObject.Properties.Name -notcontains $field) {
            throw "$Label checkpoint event is missing $field."
        }
    }
    [void][DateTimeOffset]::Parse([string]$Event.occurredAt)
    if ($Event.activeSpentMinutes -lt 0 -or [string]::IsNullOrWhiteSpace([string]$Event.decision)) {
        throw "$Label checkpoint event is invalid."
    }
    Assert-StateArtifact ([string]$Event.evidencePath) ([string]$Event.evidenceSha256) $Label
}
function Get-EffectiveTargetAt($Entity, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace([string]$Entity.targetAt)) {
        return $null
    }
    $effectiveTarget = [DateTimeOffset]::Parse([string]$Entity.targetAt)
    $previousDetectedAt = [DateTimeOffset]::MinValue
    foreach ($overrun in @($Entity.overrunHistory)) {
        foreach ($field in @('detectedAt', 'reason', 'elapsedMinutes', 'remainingEstimateMinutes', 'revisedTargetAt', 'decision', 'scheduleAdjustment', 'approvedBy', 'evidencePath', 'evidenceSha256')) {
            if ($overrun.PSObject.Properties.Name -notcontains $field) {
                throw "$Label overrun record is missing $field."
            }
        }
        $detectedAt = [DateTimeOffset]::Parse([string]$overrun.detectedAt)
        $revisedTargetAt = [DateTimeOffset]::Parse([string]$overrun.revisedTargetAt)
        if ($detectedAt -lt $previousDetectedAt -or $detectedAt -lt $effectiveTarget -or
            $revisedTargetAt -le $effectiveTarget -or $revisedTargetAt -le $detectedAt -or
            $overrun.elapsedMinutes -lt 0 -or $overrun.elapsedMinutes -gt $Entity.activeSpentMinutes -or
            $overrun.remainingEstimateMinutes -le 0 -or
            $overrun.decision -notin @('continue_to_completion', 'split_natural_boundary_keep_parent_open') -or
            $overrun.approvedBy -notin @('pm', 'leader') -or
            [string]::IsNullOrWhiteSpace([string]$overrun.reason) -or
            [string]::IsNullOrWhiteSpace([string]$overrun.scheduleAdjustment)) {
            throw "$Label overrun record is invalid."
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$Entity.startedAt) -and
            $detectedAt -lt [DateTimeOffset]::Parse([string]$Entity.startedAt)) {
            throw "$Label overrun was recorded before work started."
        }
        Assert-StateArtifact ([string]$overrun.evidencePath) ([string]$overrun.evidenceSha256) "$Label overrun"
        $effectiveTarget = $revisedTargetAt
        $previousDetectedAt = $detectedAt
    }
    return $effectiveTarget
}

$openBlockingIssueIds = @($stateObject.issues.openP0) + @($stateObject.issues.openP1)
if (@($openBlockingIssueIds | Sort-Object -Unique).Count -ne $openBlockingIssueIds.Count) {
    throw 'Open blocking issue ids must be unique.'
}
if ($Instance) {
    $registryPath = Resolve-StateArtifactPath ([string]$stateObject.issues.registry)
    if ($null -eq $registryPath -or -not (Test-Path -LiteralPath $registryPath -PathType Leaf)) {
        throw 'State issue registry does not exist.'
    }
    $registryEntries = @(
        Get-Content -Encoding UTF8 $registryPath |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_ | ConvertFrom-Json }
    )
    $registryIds = @($registryEntries.id)
    if (@($registryIds | Sort-Object -Unique).Count -ne $registryIds.Count) {
        throw 'Issue registry ids must be unique.'
    }
    $registryBlockingIds = @($registryEntries | Where-Object {
        $_.impact -in @('P0', 'P1') -and $_.status -in @('open', 'discussing', 'escalated')
    } | ForEach-Object { [string]$_.id })
    if ((@($registryBlockingIds | Sort-Object) -join '|') -ne (@($openBlockingIssueIds | Sort-Object) -join '|')) {
        throw 'State open P0/P1 ids must exactly match the open blocking issue registry rows.'
    }
    foreach ($issueId in $openBlockingIssueIds) {
        $issue = $registryEntries | Where-Object { $_.id -eq $issueId } | Select-Object -First 1
        if ($null -eq $issue -or $issue.status -notin @('open', 'discussing', 'escalated')) {
            throw "Open blocking issue $issueId is missing or closed in the issue registry."
        }
    }
}

foreach ($sliceGateEntry in @($stateObject.sliceRequirementGates.PSObject.Properties)) {
    $sliceGate = $sliceGateEntry.Value
    foreach ($gateField in @('status', 'requirementIds', 'reviewVerdict', 'evidencePath', 'evidenceSha256')) {
        if ($sliceGate.PSObject.Properties.Name -notcontains $gateField) {
            throw "Slice requirement gate $($sliceGateEntry.Name) is missing $gateField."
        }
    }
    if ($sliceGate.status -eq 'accepted') {
        if (@($sliceGate.requirementIds).Count -eq 0 -or $sliceGate.reviewVerdict -ne 'pass') {
            throw "Accepted slice requirement gate $($sliceGateEntry.Name) has no passed requirement review."
        }
        $sliceGateEvidencePath = Resolve-StateArtifactPath ([string]$sliceGate.evidencePath)
        if ($null -eq $sliceGateEvidencePath -or -not (Test-Path -LiteralPath $sliceGateEvidencePath -PathType Leaf)) {
            throw "Accepted slice requirement gate $($sliceGateEntry.Name) has no evidence artifact."
        }
        $expectedSliceGateHash = "sha256:$((Get-FileHash -LiteralPath $sliceGateEvidencePath -Algorithm SHA256).Hash.ToLowerInvariant())"
        if ($sliceGate.evidenceSha256 -ne $expectedSliceGateHash) {
            throw "Accepted slice requirement gate $($sliceGateEntry.Name) evidence hash does not match its artifact."
        }
    }
}

$activeSliceId = $null
$currentSliceGate = $null
$effectiveSliceTarget = $null
if ($null -ne $stateObject.active.slice) {
    foreach ($sliceRuntimeField in @('startedAt', 'targetAt', 'pausedAt', 'completedAt', 'remainingEstimateMinutes', 'activeSpentMinutes', 'checkpointEvents', 'overrunHistory', 'blockedWaitingEvidence', 'demoManifest')) {
        if ($stateObject.active.slice.PSObject.Properties.Name -notcontains $sliceRuntimeField) {
            throw "Active slice is missing runtime field $sliceRuntimeField."
        }
    }
    if ($stateObject.active.slice.targetMinutes -le 0 -or $stateObject.active.slice.targetMinutes -gt 720) {
        throw 'Active slice target must be within 1..720 minutes.'
    }
    if ($stateObject.active.slice.activeSpentMinutes -lt 0 -or $stateObject.active.slice.remainingEstimateMinutes -lt 0) {
        throw 'Active slice spent and remaining estimates cannot be negative.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.integrationOwner)) {
        throw 'Active slice has no integration owner.'
    }
    $activeSliceId = [string]$stateObject.active.slice.id
    $sliceGateProperty = $stateObject.sliceRequirementGates.PSObject.Properties[$activeSliceId]
    if ($null -eq $sliceGateProperty) {
        throw "Active slice $activeSliceId has no slice requirement gate."
    }
    $currentSliceGate = $sliceGateProperty.Value
    foreach ($gateField in @('status', 'requirementIds', 'reviewVerdict', 'evidencePath', 'evidenceSha256')) {
        if ($currentSliceGate.PSObject.Properties.Name -notcontains $gateField) {
            throw "Active slice requirement gate $activeSliceId is missing $gateField."
        }
    }
    if ($currentSliceGate.status -eq 'accepted') {
        if (@($currentSliceGate.requirementIds).Count -eq 0 -or $currentSliceGate.reviewVerdict -ne 'pass') {
            throw "Accepted slice requirement gate $activeSliceId has no passed requirement review."
        }
        Assert-StateArtifact ([string]$currentSliceGate.evidencePath) ([string]$currentSliceGate.evidenceSha256) "Accepted slice requirement gate $activeSliceId"
    }
    if (@($stateObject.active.slice.overrunHistory).Count -gt 0 -and
        [string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.targetAt)) {
        throw "Active slice $activeSliceId has overrun history without an initial target."
    }
}

$taskExecutions = @($stateObject.active.taskExecution)
$taskIds = @($taskExecutions.id)
if ($taskExecutions.Count -gt 0) {
    $allowedTaskStatuses = @('pending', 'ready', 'in_progress', 'review', 'passed', 'blocked_waiting')
    $allowedTaskKinds = @('enabler', 'implementation', 'verification')
    if (@($taskIds | Sort-Object -Unique).Count -ne $taskIds.Count) {
        throw 'Task execution ids must be unique.'
    }
    if ($taskExecutions.Count -lt 2 -or $taskExecutions.Count -gt 3) {
        throw 'An active delivery slice must contain exactly 2..3 tasks.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.demoPath)) {
        throw 'An active delivery slice with tasks must declare a real demo path.'
    }
    $taskEstimateTotal = ($taskExecutions | Measure-Object -Property estimateMinutes -Sum).Sum
    $taskActiveSpentTotal = ($taskExecutions | Measure-Object -Property activeSpentMinutes -Sum).Sum
    if ($taskEstimateTotal -gt $stateObject.active.slice.targetMinutes -or $taskEstimateTotal -gt 720) {
        throw 'Task estimates exceed the active slice planning target.'
    }
    if ($taskActiveSpentTotal -ne $stateObject.active.slice.activeSpentMinutes) {
        throw 'Active slice spent minutes must equal the sum of active task spent minutes.'
    }
    $sliceFactThreshold = [Math]::Min(60, $stateObject.active.slice.targetMinutes * 0.5)
    $sliceReplanThreshold = [Math]::Min(180, $stateObject.active.slice.targetMinutes * 0.75)
    if ($stateObject.active.slice.activeSpentMinutes -ge $sliceFactThreshold) {
        Assert-CheckpointEvent $stateObject.active.slice.checkpointEvents.fact "Slice $activeSliceId fact"
    }
    if ($stateObject.active.slice.activeSpentMinutes -ge $sliceReplanThreshold) {
        Assert-CheckpointEvent $stateObject.active.slice.checkpointEvents.replan "Slice $activeSliceId replan"
    }
    foreach ($checkpoint in @($stateObject.active.slice.checkpointEvents.PSObject.Properties)) {
        if ($null -ne $checkpoint.Value -and $checkpoint.Value.activeSpentMinutes -gt $stateObject.active.slice.activeSpentMinutes) {
            throw "Slice $activeSliceId checkpoint $($checkpoint.Name) exceeds recorded active time."
        }
    }

    $startedTasks = @($taskExecutions | Where-Object { $_.status -in @('in_progress', 'review', 'passed', 'blocked_waiting') })
    if ($startedTasks.Count -gt 0) {
        if ([string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.startedAt) -or
            [string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.targetAt)) {
            throw 'A started slice must declare startedAt and its original targetAt.'
        }
        $sliceStartedAt = [DateTimeOffset]::Parse([string]$stateObject.active.slice.startedAt)
        $sliceInitialTargetAt = [DateTimeOffset]::Parse([string]$stateObject.active.slice.targetAt)
        if ($sliceInitialTargetAt -le $sliceStartedAt -or
            ($sliceInitialTargetAt - $sliceStartedAt).TotalMinutes -gt $stateObject.active.slice.targetMinutes) {
            throw 'Active slice initial target exceeds its planning estimate.'
        }
        $effectiveSliceTarget = Get-EffectiveTargetAt $stateObject.active.slice "Slice $activeSliceId"
        if ($stateObject.active.slice.gate -ne 'passed' -and @($stateObject.active.slice.overrunHistory).Count -gt 0) {
            $latestSliceOverrun = @($stateObject.active.slice.overrunHistory)[-1]
            if ($stateObject.active.slice.remainingEstimateMinutes -ne $latestSliceOverrun.remainingEstimateMinutes) {
                throw "Slice $activeSliceId remaining estimate does not match its latest reforecast."
            }
        }
        if ($stateObject.active.slice.activeSpentMinutes -gt $stateObject.active.slice.targetMinutes -and
            @($stateObject.active.slice.overrunHistory).Count -eq 0) {
            throw "Slice $activeSliceId exceeded its estimate without an overrun reforecast."
        }
        if (@($taskExecutions | Where-Object { $_.status -in @('in_progress', 'review') }).Count -gt 0 -and
            [DateTimeOffset]::Now -gt $effectiveSliceTarget) {
            throw "Slice $activeSliceId exceeded its current target; continue the slice after recording a new reforecast."
        }
    }

    $codingTasks = @($taskExecutions | Where-Object { $_.status -in @('ready', 'in_progress', 'review', 'passed') })
    if ($codingTasks.Count -gt 0 -and -not $stateObject.gates.requirementsBaselineAccepted) {
        throw 'Coding tasks cannot start before the project requirements baseline is accepted.'
    }
    if ($codingTasks.Count -gt 0 -and $currentSliceGate.status -ne 'accepted') {
        throw "Coding tasks cannot start before active slice requirement gate '$activeSliceId' is accepted."
    }
    if ($codingTasks.Count -gt 0 -and $openBlockingIssueIds.Count -gt 0) {
        throw 'Coding tasks cannot be ready or started while P0/P1 issues remain open.'
    }

    foreach ($task in $taskExecutions) {
        if ($task.status -notin $allowedTaskStatuses) {
            throw "Task $($task.id) has an invalid status."
        }
        if ($task.estimateMinutes -lt 30 -or $task.estimateMinutes -gt 240) {
            throw "Task $($task.id) must have a 30..240 minute pre-execution estimate."
        }
        if ($task.kind -notin $allowedTaskKinds) {
            throw "Task $($task.id) has an invalid kind."
        }
        if ($task.activeSpentMinutes -lt 0 -or $task.remainingEstimateMinutes -lt 0) {
            throw "Task $($task.id) has invalid spent or remaining estimate minutes."
        }
        foreach ($runtimeField in @('startedAt', 'targetAt', 'pausedAt', 'completedAt', 'remainingEstimateMinutes', 'checkpointEvents', 'overrunHistory', 'blockedWaitingEvidence', 'stopReason', 'remainingScope', 'runnableBaseline')) {
            if ($task.PSObject.Properties.Name -notcontains $runtimeField) {
                throw "Task $($task.id) is missing runtime field $runtimeField."
            }
        }
        foreach ($contractField in @('owner', 'verifier', 'contractPath', 'requirementIds', 'journeyIds', 'evidenceManifest', 'evidenceManifestSha256', 'evidence', 'executionMode', 'schedulingReason', 'criticalPathRank', 'integrationOrder', 'estimatedParallelSavingsMinutes', 'estimatedIntegrationCostMinutes')) {
            if ($task.PSObject.Properties.Name -notcontains $contractField) {
                throw "Task $($task.id) is missing contract field $contractField."
            }
        }
        if ([string]::IsNullOrWhiteSpace([string]$task.owner) -or
            [string]::IsNullOrWhiteSpace([string]$task.verifier) -or
            $task.owner -eq $task.verifier -or
            @($task.requirementIds).Count -eq 0 -or
            @($task.journeyIds).Count -eq 0) {
            throw "Task $($task.id) must have distinct owner/verifier plus requirement and journey ids."
        }
        if ($task.executionMode -notin @('serial', 'parallel') -or
            [string]::IsNullOrWhiteSpace([string]$task.schedulingReason) -or
            $task.criticalPathRank -le 0 -or $task.integrationOrder -le 0 -or
            $task.estimatedParallelSavingsMinutes -lt 0 -or $task.estimatedIntegrationCostMinutes -lt 0) {
            throw "Task $($task.id) has an invalid scheduling decision."
        }
        if ([string]::IsNullOrWhiteSpace([string]$task.parallelGroup)) {
            throw "Task $($task.id) has no parallel group declaration."
        }
        if ($task.executionMode -eq 'serial' -and $task.parallelGroup -ne 'none') {
            throw "Serial task $($task.id) cannot declare a parallel group."
        }
        if ($task.executionMode -eq 'parallel' -and
            ($task.parallelGroup -eq 'none' -or $task.estimatedParallelSavingsMinutes -le $task.estimatedIntegrationCostMinutes)) {
            throw "Parallel task $($task.id) must declare a group with positive net scheduling benefit."
        }

        $effectiveTaskTarget = $null
        if ($task.status -in @('in_progress', 'review', 'passed', 'blocked_waiting')) {
            if ([string]::IsNullOrWhiteSpace([string]$task.startedAt) -or
                [string]::IsNullOrWhiteSpace([string]$task.targetAt)) {
                throw "Started task $($task.id) must declare startedAt and its original targetAt."
            }
            $taskStartedAt = [DateTimeOffset]::Parse([string]$task.startedAt)
            $taskInitialTargetAt = [DateTimeOffset]::Parse([string]$task.targetAt)
            if ($taskInitialTargetAt -le $taskStartedAt -or
                ($taskInitialTargetAt - $taskStartedAt).TotalMinutes -gt $task.estimateMinutes) {
                throw "Task $($task.id) initial target exceeds its estimate."
            }
            $effectiveTaskTarget = Get-EffectiveTargetAt $task "Task $($task.id)"
            if (@($task.overrunHistory).Count -gt 0 -and $task.status -ne 'passed') {
                $latestTaskOverrun = @($task.overrunHistory)[-1]
                if ($task.remainingEstimateMinutes -ne $latestTaskOverrun.remainingEstimateMinutes) {
                    throw "Task $($task.id) remaining estimate does not match its latest reforecast."
                }
            }
            if ($task.activeSpentMinutes -gt $task.estimateMinutes -and @($task.overrunHistory).Count -eq 0) {
                throw "Task $($task.id) exceeded its estimate without an overrun reforecast."
            }
            if ($task.status -in @('in_progress', 'review') -and [DateTimeOffset]::Now -gt $effectiveTaskTarget) {
                throw "Task $($task.id) exceeded its current target; continue the same task after recording a new reforecast."
            }
        } elseif (@($task.overrunHistory).Count -gt 0) {
            throw "Unstarted task $($task.id) cannot have overrun history."
        }

        $factThreshold = [Math]::Min(60, $task.estimateMinutes * 0.5)
        $replanThreshold = [Math]::Min(180, $task.estimateMinutes * 0.75)
        if ($task.activeSpentMinutes -ge $factThreshold) {
            Assert-CheckpointEvent $task.checkpointEvents.fact "Task $($task.id) fact"
        }
        if ($task.activeSpentMinutes -ge $replanThreshold) {
            Assert-CheckpointEvent $task.checkpointEvents.replan "Task $($task.id) replan"
        }
        if ($task.status -in @('review', 'passed')) {
            Assert-CheckpointEvent $task.checkpointEvents.completion "Task $($task.id) completion"
        }
        foreach ($checkpoint in @($task.checkpointEvents.PSObject.Properties)) {
            if ($null -ne $checkpoint.Value -and $checkpoint.Value.activeSpentMinutes -gt $task.activeSpentMinutes) {
                throw "Task $($task.id) checkpoint $($checkpoint.Name) exceeds recorded active time."
            }
        }
        if ($task.status -eq 'blocked_waiting') {
            if ([string]::IsNullOrWhiteSpace([string]$task.stopReason) -or
                [string]::IsNullOrWhiteSpace([string]$task.remainingScope) -or
                [string]::IsNullOrWhiteSpace([string]$task.runnableBaseline) -or
                [string]::IsNullOrWhiteSpace([string]$task.pausedAt)) {
                throw "Blocked task $($task.id) must preserve a paused, runnable state."
            }
            $taskPausedAt = [DateTimeOffset]::Parse([string]$task.pausedAt)
            if ($taskPausedAt -gt $effectiveTaskTarget) {
                throw "Blocked task $($task.id) was paused after its current target without reforecasting."
            }
            Assert-EvidenceRecord $task.blockedWaitingEvidence "Task $($task.id) external wait"
        }
        # The active node contract is intentionally replaced when the next
        # delivery batch starts. Completed tasks are frozen by their acceptance
        # evidence hashes below; only executable tasks must still match the
        # current contract artifact.
        if ($task.status -in @('ready', 'in_progress', 'review')) {
            $taskContractPath = Resolve-StateArtifactPath ([string]$task.contractPath)
            if ($null -eq $taskContractPath -or -not (Test-Path -LiteralPath $taskContractPath -PathType Leaf)) {
                throw "Executable task $($task.id) has no contract artifact."
            }
            $expectedTaskContractHash = "sha256:$((Get-FileHash -LiteralPath $taskContractPath -Algorithm SHA256).Hash.ToLowerInvariant())"
            if ($task.contractHash -ne $expectedTaskContractHash) {
                throw "Executable task $($task.id) contract hash does not match its artifact."
            }
        }
        foreach ($dependencyId in @($task.dependsOn)) {
            if ($dependencyId -notin $taskIds -or $dependencyId -eq $task.id) {
                throw "Task $($task.id) has an invalid dependency."
            }
            if ($task.status -in @('ready', 'in_progress', 'review', 'passed')) {
                $dependencyTask = $taskExecutions | Where-Object { $_.id -eq $dependencyId } | Select-Object -First 1
                if ($dependencyTask.status -ne 'passed') {
                    throw "Task $($task.id) is executable before dependency $dependencyId passed."
                }
            }
        }
        if (@($task.writes).Count -eq 0) {
            throw "Task $($task.id) has no declared write set."
        }
        if ($task.kind -eq 'enabler') {
            $consumer = $taskExecutions | Where-Object { $_.id -eq $task.consumedByTaskId } | Select-Object -First 1
            if ($null -eq $consumer -or $consumer.kind -ne 'implementation') {
                throw "Enabler $($task.id) has no same-slice implementation consumer."
            }
        }
        if ($task.status -eq 'passed') {
            if ([string]::IsNullOrWhiteSpace([string]$task.completedAt)) {
                throw "Passed task $($task.id) has no completion timestamp."
            }
            $taskCompletedAt = [DateTimeOffset]::Parse([string]$task.completedAt)
            if ($taskCompletedAt -gt $effectiveTaskTarget) {
                throw "Passed task $($task.id) completed after its current target without reforecasting."
            }
            if ($task.remainingEstimateMinutes -ne 0) {
                throw "Passed task $($task.id) must have zero remaining estimate."
            }
            Assert-StateArtifact ([string]$task.evidenceManifest) ([string]$task.evidenceManifestSha256) "Passed task $($task.id) manifest"
            foreach ($evidenceKind in @('api', 'db', 'ui', 'browser', 'restart')) {
                Assert-EvidenceRecord $task.evidence.$evidenceKind "Passed task $($task.id) $evidenceKind"
            }
            $taskCompletionProperty = $stateObject.completionLedger.tasks.PSObject.Properties[[string]$task.id]
            if ($null -eq $taskCompletionProperty -or $taskCompletionProperty.Value.verdict -ne 'pass' -or
                $taskCompletionProperty.Value.sliceId -ne $activeSliceId) {
                throw "Passed task $($task.id) has no matching completion ledger record."
            }
        }
    }

    $integrationOrders = @($taskExecutions.integrationOrder)
    if (@($integrationOrders | Sort-Object -Unique).Count -ne $integrationOrders.Count) {
        throw 'Task integration order values must be unique.'
    }
    $remainingDependencies = @{}
    foreach ($task in $taskExecutions) {
        $remainingDependencies[$task.id] = @($task.dependsOn)
    }
    while ($remainingDependencies.Count -gt 0) {
        $remainingIds = @($remainingDependencies.Keys)
        $removableIds = @($remainingIds | Where-Object {
            $dependencyIds = @($remainingDependencies[$_])
            @($dependencyIds | Where-Object { $_ -in $remainingIds }).Count -eq 0
        })
        if ($removableIds.Count -eq 0) {
            throw 'Task dependency graph contains a cycle.'
        }
        foreach ($removableId in $removableIds) {
            $remainingDependencies.Remove($removableId)
        }
    }

    $parallelGroups = @($taskExecutions | Where-Object { $_.executionMode -eq 'parallel' } | Group-Object parallelGroup)
    foreach ($group in $parallelGroups) {
        $groupTasks = @($group.Group)
        if ($groupTasks.Count -lt 2) {
            throw "Parallel group $($group.Name) must contain at least two tasks."
        }
        $groupSavings = ($groupTasks | Measure-Object -Property estimatedParallelSavingsMinutes -Sum).Sum
        $groupIntegrationCost = ($groupTasks | Measure-Object -Property estimatedIntegrationCostMinutes -Sum).Sum
        if ($groupSavings -le $groupIntegrationCost) {
            throw "Parallel group $($group.Name) has no positive net scheduling benefit."
        }
        $hashes = @($groupTasks.contractHash | Sort-Object -Unique)
        if ($hashes.Count -ne 1 -or $hashes[0] -notmatch '^sha256:[a-f0-9]{64}$') {
            throw "Parallel group $($group.Name) has no single frozen contract hash."
        }
        $groupIds = @($groupTasks.id)
        $seenWrites = @{}
        foreach ($task in $groupTasks) {
            if ($null -eq $task.runtimeIsolation) {
                throw "Parallel task $($task.id) has no runtime isolation declaration."
            }
            foreach ($isolationField in @('migrationTarget', 'generatedOutput', 'lockfileOwner', 'databaseSchema', 'testDataNamespace', 'port', 'runtimeId')) {
                if ($task.runtimeIsolation.PSObject.Properties.Name -notcontains $isolationField -or
                    [string]::IsNullOrWhiteSpace([string]$task.runtimeIsolation.$isolationField)) {
                    throw "Parallel task $($task.id) has incomplete runtime isolation."
                }
            }
            if (@($task.dependsOn | Where-Object { $_ -in $groupIds }).Count -gt 0) {
                throw "Parallel group $($group.Name) contains an internal dependency."
            }
            foreach ($writePath in @($task.writes)) {
                $normalized = ([string]$writePath).ToLowerInvariant()
                $overlap = @($seenWrites.Keys | Where-Object {
                    $_ -eq $normalized -or $_.StartsWith("$normalized/") -or $normalized.StartsWith("$_/")
                })
                if ($overlap.Count -gt 0) {
                    throw "Parallel group $($group.Name) has overlapping write path '$writePath'."
                }
                $seenWrites[$normalized] = $task.id
            }
        }
        foreach ($isolationField in @('migrationTarget', 'generatedOutput', 'databaseSchema', 'testDataNamespace', 'port', 'runtimeId')) {
            $values = @($groupTasks | ForEach-Object { [string]$_.runtimeIsolation.$isolationField })
            $exclusiveValues = @($values | Where-Object { -not $_.StartsWith('shared-readonly:') })
            if (@($exclusiveValues | Sort-Object -Unique).Count -ne $exclusiveValues.Count) {
                throw "Parallel group $($group.Name) reuses runtime isolation field $isolationField."
            }
        }
        $lockOwners = @($groupTasks | ForEach-Object { [string]$_.runtimeIsolation.lockfileOwner } | Where-Object { $_ -ne 'none' })
        if ($lockOwners.Count -gt 1 -or ($lockOwners.Count -eq 1 -and $lockOwners[0] -notin $groupIds)) {
            throw "Parallel group $($group.Name) has an invalid lockfile owner."
        }
    }
}

if ($null -ne $stateObject.active.slice -and $stateObject.active.slice.gate -eq 'passed') {
    if ($taskExecutions.Count -eq 0 -or
        @($taskExecutions | Where-Object { $_.status -ne 'passed' }).Count -gt 0 -or
        $currentSliceGate.status -ne 'accepted' -or
        $openBlockingIssueIds.Count -gt 0) {
        throw 'A delivery slice requires passed tasks, accepted requirements and no P0/P1 issues before it can pass.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.completedAt)) {
        throw 'A passed delivery slice has no completion timestamp.'
    }
    $sliceCompletedAt = [DateTimeOffset]::Parse([string]$stateObject.active.slice.completedAt)
    if ($null -eq $effectiveSliceTarget) {
        $effectiveSliceTarget = Get-EffectiveTargetAt $stateObject.active.slice "Slice $activeSliceId"
    }
    if ($sliceCompletedAt -gt $effectiveSliceTarget) {
        throw "Passed slice $activeSliceId completed after its current target without reforecasting."
    }
    if ($stateObject.active.slice.remainingEstimateMinutes -ne 0) {
        throw "Passed slice $activeSliceId must have zero remaining estimate."
    }
    Assert-CheckpointEvent $stateObject.active.slice.checkpointEvents.completion "Slice $activeSliceId completion"
    Assert-EvidenceRecord $stateObject.active.slice.demoManifest "Slice $activeSliceId demo"
    [void][DateTimeOffset]::Parse([string]$stateObject.active.slice.demoManifest.executedAt)
    if ([string]::IsNullOrWhiteSpace([string]$stateObject.active.slice.demoManifest.entryPoint)) {
        throw "Slice $activeSliceId demo has no real entry point."
    }
    $activeSliceCompletion = $stateObject.completionLedger.slices.PSObject.Properties[$activeSliceId]
    if ($null -eq $activeSliceCompletion -or $activeSliceCompletion.Value.verdict -ne 'pass' -or
        (@($activeSliceCompletion.Value.requiredTaskIds | Sort-Object) -join '|') -ne (@($taskIds | Sort-Object) -join '|')) {
        throw "Passed slice $activeSliceId has no exact completion ledger record."
    }
}
if ($null -eq $stateObject.completionLedger) {
    throw 'State is missing completionLedger.'
}
foreach ($ledgerField in @('tasks', 'slices', 'phases', 'journeys', 'requiredJourneyIds', 'integrationEvidencePath', 'integrationEvidenceSha256', 'releaseEvidencePath', 'releaseEvidenceSha256')) {
    if ($stateObject.completionLedger.PSObject.Properties.Name -notcontains $ledgerField) {
        throw "Completion ledger is missing $ledgerField."
    }
}
foreach ($taskRecordProperty in @($stateObject.completionLedger.tasks.PSObject.Properties)) {
    $taskRecord = $taskRecordProperty.Value
    if ($taskRecord.verdict -ne 'pass' -or [string]::IsNullOrWhiteSpace([string]$taskRecord.sliceId)) {
        throw "Completion task $($taskRecordProperty.Name) is not passed or has no slice."
    }
    Assert-StateArtifact ([string]$taskRecord.evidencePath) ([string]$taskRecord.evidenceSha256) "Completion task $($taskRecordProperty.Name)"
}
foreach ($sliceRecordProperty in @($stateObject.completionLedger.slices.PSObject.Properties)) {
    $sliceRecord = $sliceRecordProperty.Value
    if ($sliceRecord.verdict -ne 'pass' -or @($sliceRecord.requiredTaskIds).Count -eq 0) {
        throw "Completion slice $($sliceRecordProperty.Name) is not passed or has no required tasks."
    }
    foreach ($requiredTaskId in @($sliceRecord.requiredTaskIds)) {
        $requiredTaskRecord = $stateObject.completionLedger.tasks.PSObject.Properties[[string]$requiredTaskId]
        if ($null -eq $requiredTaskRecord -or $requiredTaskRecord.Value.verdict -ne 'pass' -or
            $requiredTaskRecord.Value.sliceId -ne $sliceRecordProperty.Name) {
            throw "Completion slice $($sliceRecordProperty.Name) has incomplete task $requiredTaskId."
        }
    }
    Assert-StateArtifact ([string]$sliceRecord.evidencePath) ([string]$sliceRecord.evidenceSha256) "Completion slice $($sliceRecordProperty.Name)"
}
foreach ($phaseRecordProperty in @($stateObject.completionLedger.phases.PSObject.Properties)) {
    $phaseRecord = $phaseRecordProperty.Value
    if ($phaseRecord.verdict -ne 'pass' -or @($phaseRecord.requiredSliceIds).Count -eq 0) {
        throw "Completion phase $($phaseRecordProperty.Name) is not passed or has no required slices."
    }
    foreach ($requiredSliceId in @($phaseRecord.requiredSliceIds)) {
        $requiredSliceRecord = $stateObject.completionLedger.slices.PSObject.Properties[[string]$requiredSliceId]
        if ($null -eq $requiredSliceRecord -or $requiredSliceRecord.Value.verdict -ne 'pass' -or
            $requiredSliceRecord.Value.phaseId -ne $phaseRecordProperty.Name) {
            throw "Completion phase $($phaseRecordProperty.Name) has incomplete slice $requiredSliceId."
        }
    }
    Assert-StateArtifact ([string]$phaseRecord.evidencePath) ([string]$phaseRecord.evidenceSha256) "Completion phase $($phaseRecordProperty.Name)"
}
foreach ($journeyRecordProperty in @($stateObject.completionLedger.journeys.PSObject.Properties)) {
    $journeyRecord = $journeyRecordProperty.Value
    if ($journeyRecord.verdict -ne 'pass' -or @($journeyRecord.requiredPhaseIds).Count -eq 0) {
        throw "Completion journey $($journeyRecordProperty.Name) is not passed or has no required phases."
    }
    foreach ($requiredPhaseId in @($journeyRecord.requiredPhaseIds)) {
        $requiredPhaseRecord = $stateObject.completionLedger.phases.PSObject.Properties[[string]$requiredPhaseId]
        if ($null -eq $requiredPhaseRecord -or $requiredPhaseRecord.Value.verdict -ne 'pass' -or
            $journeyRecordProperty.Name -notin @($requiredPhaseRecord.Value.journeyIds)) {
            throw "Completion journey $($journeyRecordProperty.Name) has incomplete phase $requiredPhaseId."
        }
    }
    Assert-StateArtifact ([string]$journeyRecord.evidencePath) ([string]$journeyRecord.evidenceSha256) "Completion journey $($journeyRecordProperty.Name)"
}
if ($stateObject.gates.integrationPassed) {
    if (@($stateObject.completionLedger.requiredJourneyIds).Count -eq 0) {
        throw 'Integration pass requires at least one required journey.'
    }
    foreach ($requiredJourneyId in @($stateObject.completionLedger.requiredJourneyIds)) {
        $requiredJourneyRecord = $stateObject.completionLedger.journeys.PSObject.Properties[[string]$requiredJourneyId]
        if ($null -eq $requiredJourneyRecord -or $requiredJourneyRecord.Value.verdict -ne 'pass') {
            throw "Integration pass has incomplete journey $requiredJourneyId."
        }
    }
    Assert-StateArtifact ([string]$stateObject.completionLedger.integrationEvidencePath) ([string]$stateObject.completionLedger.integrationEvidenceSha256) 'Integration pass'
}
if ($stateObject.gates.releasePassed) {
    if (-not $stateObject.gates.integrationPassed) {
        throw 'Release pass requires integration pass.'
    }
    Assert-StateArtifact ([string]$stateObject.completionLedger.releaseEvidencePath) ([string]$stateObject.completionLedger.releaseEvidenceSha256) 'Release pass'
}
if ($stateObject.completionBoundary.engineeringFinalGate -eq 'PASS' -and -not $stateObject.gates.releasePassed) {
    throw 'Engineering final PASS requires release pass.'
}
if ($stateObject.gates.userAccepted) {
    if (-not $stateObject.gates.releasePassed -or
        $stateObject.completionBoundary.engineeringFinalGate -ne 'PASS' -or
        [string]::IsNullOrWhiteSpace([string]$stateObject.completionBoundary.userSignoffPath)) {
        throw 'User acceptance requires release pass, engineering final pass and a real signoff path.'
    }
    Assert-StateArtifact ([string]$stateObject.completionBoundary.userSignoffPath) ([string]$stateObject.completionBoundary.userSignoffSha256) 'User signoff'
}
if ([bool]$stateObject.gates.userAccepted -ne [bool]$stateObject.completionBoundary.userAccepted) {
    throw 'User acceptance is inconsistent between gates and completionBoundary.'
}

if ($Instance) {
    $instancePath = Join-Path $rootPath 'INSTANCE.md'
    $instanceText = Get-Content -Raw -Encoding UTF8 $instancePath
    if ($instanceText -match '\{\{[A-Z0-9_]+\}\}') {
        throw 'Instance configuration still contains unresolved placeholders.'
    }
    foreach ($relative in @('session/current-node.md', 'session/delivery-roadmap.md')) {
        $dynamicMirrorText = Get-Content -Raw -Encoding UTF8 (Join-Path $rootPath $relative)
        if ($dynamicMirrorText -match '(?m)^\s*-\s*(status|phase|started_at|updated_at):\s*' -or
            $dynamicMirrorText -match '(?m)^\|[^\r\n]*\|\s*status\s*\|') {
            throw "$relative mirrors mutable runtime state instead of referencing session/state.json."
        }
    }
}

foreach ($pattern in $ForbiddenPattern) {
    $matches = $textFiles | Select-String -Pattern $pattern
    if ($matches) {
        $locations = $matches | ForEach-Object { "$($_.Path):$($_.LineNumber)" }
        throw "Forbidden pattern '$pattern' found at: $($locations -join ', ')"
    }
}

Write-Output "Framework validation passed: $rootPath"
