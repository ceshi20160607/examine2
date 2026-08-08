param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$JsonPath = 'session/project-progress.json',
    [string]$MarkdownPath = 'session/PROJECT_PROGRESS.md',
    [switch]$PrintCanonicalMarkdown
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$projectRoot = Split-Path -Parent $rootPath
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)

function Resolve-InputPath([string]$PathValue) {
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $rootPath $PathValue))
}

function Assert-ExactProperties($Value, [string[]]$Expected, [string]$Label) {
    if ($null -eq $Value) {
        throw "$Label is missing."
    }
    $actualNames = @($Value.PSObject.Properties.Name | Sort-Object)
    $expectedNames = @($Expected | Sort-Object)
    if (($actualNames -join '|') -ne ($expectedNames -join '|')) {
        throw "$Label fields must be exactly: $($Expected -join ', ')."
    }
}

function Assert-Token([string]$Value, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value) -or
        $Value -notmatch '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$') {
        throw "$Label is invalid."
    }
}

function Assert-DisplayText([string]$Value, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt 200 -or
        $Value.Contains("`r") -or $Value.Contains("`n") -or $Value.Contains('`')) {
        throw "$Label is invalid."
    }
}

function Get-Integer($Value, [string]$Label, [long]$Minimum) {
    if ($null -eq $Value -or $Value -is [bool]) {
        throw "$Label must be an integer."
    }
    $text = [Convert]::ToString($Value, [Globalization.CultureInfo]::InvariantCulture)
    $parsed = 0L
    if (-not [long]::TryParse(
            $text,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed) -or $parsed -lt $Minimum) {
        throw "$Label must be an integer greater than or equal to $Minimum."
    }
    return $parsed
}

function Resolve-ArtifactPath([string]$PathValue, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($PathValue) -or
        [System.IO.Path]::IsPathRooted($PathValue)) {
        throw "$Label must use a portable relative path."
    }
    $normalized = $PathValue.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $frameworkPrefix = ".$([System.IO.Path]::DirectorySeparatorChar)"
    $cursorPrefix = ".cursor$([System.IO.Path]::DirectorySeparatorChar)"
    $basePrefix = ".base$([System.IO.Path]::DirectorySeparatorChar)"
    $projectRelative = $normalized.StartsWith($frameworkPrefix) -or
        $normalized.StartsWith($cursorPrefix) -or
        $normalized.StartsWith($basePrefix)
    $candidate = if ($projectRelative) {
        Join-Path $projectRoot $normalized
    } else {
        Join-Path $rootPath $normalized
    }
    $fullPath = [System.IO.Path]::GetFullPath($candidate)
    $rootName = Split-Path -Leaf $rootPath
    $boundary = if ($rootName -in @('.base', '.cursor')) {
        $projectRoot
    } else {
        $rootPath
    }
    $boundaryPath = [System.IO.Path]::GetFullPath($boundary).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    $boundaryPrefix = $boundaryPath + [System.IO.Path]::DirectorySeparatorChar
    if ($fullPath -ne $boundaryPath -and
        -not $fullPath.StartsWith(
            $boundaryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label escapes the project boundary."
    }
    return $fullPath
}

function Assert-ExistingArtifact([string]$PathValue, [string]$Label) {
    $artifactPath = Resolve-ArtifactPath $PathValue $Label
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        throw "$Label does not exist: $PathValue"
    }
    return $artifactPath
}

function Assert-PassEvidence(
        [string]$PathValue,
        [string]$Label,
        [bool]$AcceptanceOnly = $false) {
    $artifactPath = Assert-ExistingArtifact $PathValue $Label
    if ($AcceptanceOnly -and
        -not [System.IO.Path]::GetFileName($artifactPath).Equals(
            'acceptance.md', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must point to an acceptance.md artifact."
    }
    $content = $utf8.GetString([System.IO.File]::ReadAllBytes($artifactPath))
    $verdictAssignment = '(?im)^\s*(?:[-*]\s*)?[A-Za-z0-9 _-]*verdict\s*:\s*(?:(?:\*\*)|`)?PASS(?:_[A-Z0-9_]+)?(?:(?:\*\*)|`)?[.,;:]?(?:\s|$)'
    $verdictSection = '(?im)^\s*#{1,6}\s+Verdict\s*\r?\n(?:\s*\r?\n)*\s*(?:[-*]\s*)?(?:(?:\*\*)|`)?PASS(?:_[A-Z0-9_]+)?(?:(?:\*\*)|`)?[.,;:]?(?:\s|$)'
    if ($content -notmatch $verdictAssignment -and
        $content -notmatch $verdictSection) {
        throw "$Label is not a PASS artifact."
    }
    return $artifactPath
}

$jsonFile = Resolve-InputPath $JsonPath
if (-not (Test-Path -LiteralPath $jsonFile -PathType Leaf)) {
    throw "Project progress JSON does not exist: $jsonFile"
}
$markdownFile = $null
if (-not $PrintCanonicalMarkdown) {
    $markdownFile = Resolve-InputPath $MarkdownPath
    if (-not (Test-Path -LiteralPath $markdownFile -PathType Leaf)) {
        throw "Project progress Markdown does not exist: $markdownFile"
    }
}

$jsonText = $utf8.GetString([System.IO.File]::ReadAllBytes($jsonFile))
$progress = $jsonText | ConvertFrom-Json
Assert-ExactProperties $progress @(
    'schemaVersion', 'updatedAt', 'currentNode', 'currentOutcomeId',
    'nextOutcomeId', 'summary', 'outcomes', 'packageCheckpoints'
) 'Project progress'
if ((Get-Integer $progress.schemaVersion 'schemaVersion' 1) -ne 1) {
    throw 'Project progress schemaVersion must be 1.'
}
try {
    [void][DateTimeOffset]::Parse(
        [string]$progress.updatedAt,
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::RoundtripKind)
} catch {
    throw 'updatedAt must be an ISO-8601 timestamp.'
}

Assert-ExactProperties $progress.summary @(
    'counts', 'outcomeUnits', 'percentage'
) 'Progress summary'
Assert-ExactProperties $progress.summary.counts @(
    'total', 'completed', 'inProgress', 'remaining', 'deferred'
) 'Progress counts'
Assert-ExactProperties $progress.summary.outcomeUnits @(
    'total', 'completed', 'inProgress', 'remaining', 'deferred'
) 'Progress outcome units'

$outcomes = @($progress.outcomes)
if ($outcomes.Count -eq 0) {
    throw 'Project progress must contain at least one frozen outcome.'
}
$outcomeById = @{}
$computedCounts = @{
    total = 0L; completed = 0L; inProgress = 0L; remaining = 0L; deferred = 0L
}
$computedUnits = @{
    total = 0L; completed = 0L; inProgress = 0L; remaining = 0L; deferred = 0L
}
$statusKey = @{
    completed = 'completed'
    in_progress = 'inProgress'
    remaining = 'remaining'
    deferred = 'deferred'
}
foreach ($outcome in $outcomes) {
    Assert-ExactProperties $outcome @(
        'id', 'title', 'phase', 'weight', 'status', 'evidencePath',
        'blockingReason', 'dependsOn'
    ) 'Outcome'
    Assert-Token ([string]$outcome.id) 'Outcome id'
    Assert-DisplayText ([string]$outcome.title) "Outcome $($outcome.id) title"
    Assert-Token ([string]$outcome.phase) "Outcome $($outcome.id) phase"
    if ($outcomeById.ContainsKey([string]$outcome.id)) {
        throw "Duplicate outcome id: $($outcome.id)"
    }
    if ($outcome.status -notin @('completed', 'in_progress', 'remaining', 'deferred')) {
        throw "Outcome $($outcome.id) has an invalid status."
    }
    $weight = Get-Integer $outcome.weight "Outcome $($outcome.id) weight" 1
    $outcomeById[[string]$outcome.id] = $outcome
    $key = $statusKey[[string]$outcome.status]
    $computedCounts.total++
    $computedCounts[$key]++
    $computedUnits.total += $weight
    $computedUnits[$key] += $weight

    $dependencyIds = @($outcome.dependsOn | ForEach-Object { [string]$_ })
    if (@($dependencyIds | Sort-Object -Unique).Count -ne $dependencyIds.Count -or
        $dependencyIds -contains [string]$outcome.id) {
        throw "Outcome $($outcome.id) has duplicate or self dependencies."
    }
    if ($outcome.status -eq 'completed') {
        if ($null -ne $outcome.blockingReason) {
            throw "Completed outcome $($outcome.id) cannot have a blocking reason."
        }
        [void](Assert-PassEvidence (
            [string]$outcome.evidencePath) "Outcome $($outcome.id) evidence" $true)
    } else {
        if ($null -ne $outcome.evidencePath) {
            throw "Non-completed outcome $($outcome.id) cannot claim acceptance evidence."
        }
        if ($outcome.status -eq 'deferred' -and
            [string]::IsNullOrWhiteSpace([string]$outcome.blockingReason)) {
            throw "Deferred outcome $($outcome.id) requires a blocking reason."
        }
        if ($null -ne $outcome.blockingReason -and
            ([string]::IsNullOrWhiteSpace([string]$outcome.blockingReason) -or
             ([string]$outcome.blockingReason).Length -gt 500)) {
            throw "Outcome $($outcome.id) blocking reason is invalid."
        }
    }
}

foreach ($outcome in $outcomes) {
    foreach ($dependencyId in @($outcome.dependsOn | ForEach-Object { [string]$_ })) {
        if (-not $outcomeById.ContainsKey($dependencyId)) {
            throw "Outcome $($outcome.id) depends on unknown outcome $dependencyId."
        }
        if ($outcome.status -in @('completed', 'in_progress') -and
            $outcomeById[$dependencyId].status -ne 'completed') {
            throw "Outcome $($outcome.id) has an incomplete dependency $dependencyId."
        }
    }
}

$unresolvedDependencyIds = @($outcomes | ForEach-Object { [string]$_.id })
while ($unresolvedDependencyIds.Count -gt 0) {
    $resolvableIds = @()
    foreach ($outcomeId in $unresolvedDependencyIds) {
        $unresolvedPrerequisites = @($outcomeById[$outcomeId].dependsOn |
            Where-Object { $unresolvedDependencyIds -contains [string]$_ })
        if ($unresolvedPrerequisites.Count -eq 0) {
            $resolvableIds += $outcomeId
        }
    }
    if ($resolvableIds.Count -eq 0) {
        throw "Outcome dependency cycle detected: $($unresolvedDependencyIds -join ', ')."
    }
    $unresolvedDependencyIds = @($unresolvedDependencyIds |
        Where-Object { $resolvableIds -notcontains $_ })
}

foreach ($key in @('total', 'completed', 'inProgress', 'remaining', 'deferred')) {
    $declaredCount = Get-Integer $progress.summary.counts.$key "summary.counts.$key" 0
    $declaredUnits = Get-Integer $progress.summary.outcomeUnits.$key "summary.outcomeUnits.$key" 0
    if ($declaredCount -ne $computedCounts[$key]) {
        throw "Progress count arithmetic mismatch for $key."
    }
    if ($declaredUnits -ne $computedUnits[$key]) {
        throw "Progress outcome-unit arithmetic mismatch for $key."
    }
}
$expectedPercentage = [Math]::Round(
    (100.0 * $computedUnits.completed / $computedUnits.total),
    1,
    [MidpointRounding]::AwayFromZero)
try {
    $declaredPercentage = [Convert]::ToDouble(
        $progress.summary.percentage,
        [Globalization.CultureInfo]::InvariantCulture)
} catch {
    throw 'Progress percentage must be numeric.'
}
if ([Math]::Abs($declaredPercentage - $expectedPercentage) -gt 0.000001) {
    throw 'Progress percentage arithmetic mismatch.'
}

$inProgressOutcomes = @($outcomes | Where-Object { $_.status -eq 'in_progress' })
if ($inProgressOutcomes.Count -gt 1) {
    throw 'Only the current outcome may be in_progress.'
}
if ($inProgressOutcomes.Count -eq 1) {
    Assert-Token ([string]$progress.currentNode) 'currentNode'
    Assert-Token ([string]$progress.currentOutcomeId) 'currentOutcomeId'
    if ($progress.currentOutcomeId -ne $inProgressOutcomes[0].id) {
        throw 'currentOutcomeId does not identify the in_progress outcome.'
    }
} elseif ($null -ne $progress.currentNode -or $null -ne $progress.currentOutcomeId) {
    throw 'currentNode and currentOutcomeId must be null when no outcome is in progress.'
}
if ($null -ne $progress.nextOutcomeId) {
    Assert-Token ([string]$progress.nextOutcomeId) 'nextOutcomeId'
    if (-not $outcomeById.ContainsKey([string]$progress.nextOutcomeId) -or
        $outcomeById[[string]$progress.nextOutcomeId].status -notin @('remaining', 'deferred')) {
        throw 'nextOutcomeId must identify a remaining or deferred outcome.'
    }
}

$checkpointIds = @(
    'CP1_FUNCTIONAL_BASELINE',
    'CP2_FEATURE_COMPLETE',
    'CP3_RELEASE_CANDIDATE'
)
$checkpoints = @($progress.packageCheckpoints)
if ($checkpoints.Count -ne 3) {
    throw 'Project progress must contain exactly three package checkpoints.'
}
$attemptIds = @{}
for ($checkpointIndex = 0; $checkpointIndex -lt $checkpoints.Count; $checkpointIndex++) {
    $checkpoint = $checkpoints[$checkpointIndex]
    Assert-ExactProperties $checkpoint @(
        'id', 'state', 'requiredOutcomeIds', 'verificationCommands', 'attempts'
    ) 'Package checkpoint'
    if ($checkpoint.id -ne $checkpointIds[$checkpointIndex]) {
        throw 'Package checkpoint ids and order do not match the frozen three-checkpoint contract.'
    }
    if ($checkpoint.state -notin @('not_ready', 'ready', 'packaged', 'superseded')) {
        throw "Checkpoint $($checkpoint.id) has an invalid state."
    }
    $requiredOutcomeIds = @($checkpoint.requiredOutcomeIds | ForEach-Object { [string]$_ })
    if ($requiredOutcomeIds.Count -eq 0 -or
        @($requiredOutcomeIds | Sort-Object -Unique).Count -ne $requiredOutcomeIds.Count) {
        throw "Checkpoint $($checkpoint.id) requires unique outcome ids."
    }
    $requirementsComplete = $true
    foreach ($requiredOutcomeId in $requiredOutcomeIds) {
        if (-not $outcomeById.ContainsKey($requiredOutcomeId)) {
            throw "Checkpoint $($checkpoint.id) requires unknown outcome $requiredOutcomeId."
        }
        if ($outcomeById[$requiredOutcomeId].status -ne 'completed') {
            $requirementsComplete = $false
        }
    }

    $verifications = @($checkpoint.verificationCommands)
    if ($verifications.Count -eq 0) {
        throw "Checkpoint $($checkpoint.id) must declare verification commands."
    }
    $commandValues = @{}
    $verificationsPass = $true
    foreach ($verification in $verifications) {
        Assert-ExactProperties $verification @(
            'command', 'status', 'evidencePath'
        ) "Checkpoint $($checkpoint.id) verification"
        $command = [string]$verification.command
        if ([string]::IsNullOrWhiteSpace($command) -or $command.Length -gt 2000 -or
            $commandValues.ContainsKey($command)) {
            throw "Checkpoint $($checkpoint.id) has an invalid or duplicate verification command."
        }
        $commandValues[$command] = $true
        if ($verification.status -notin @('pending', 'pass', 'fail')) {
            throw "Checkpoint $($checkpoint.id) verification status is invalid."
        }
        if ($verification.status -eq 'pass') {
            [void](Assert-PassEvidence (
                [string]$verification.evidencePath) "Checkpoint $($checkpoint.id) verification evidence")
        } elseif ($verification.status -eq 'fail') {
            [void](Assert-ExistingArtifact (
                [string]$verification.evidencePath) "Checkpoint $($checkpoint.id) failed verification evidence")
            $verificationsPass = $false
        } else {
            if ($null -ne $verification.evidencePath) {
                throw "Checkpoint $($checkpoint.id) pending verification cannot claim evidence."
            }
            $verificationsPass = $false
        }
    }

    $attempts = @($checkpoint.attempts)
    $passedAttempts = 0
    $previousAttemptAt = [DateTimeOffset]::MinValue
    foreach ($attempt in $attempts) {
        Assert-ExactProperties $attempt @(
            'id', 'attemptedAt', 'status', 'packagePath', 'sha256', 'evidencePath'
        ) "Checkpoint $($checkpoint.id) attempt"
        Assert-Token ([string]$attempt.id) 'Package attempt id'
        if ($attemptIds.ContainsKey([string]$attempt.id)) {
            throw "Duplicate package attempt id: $($attempt.id)"
        }
        $attemptIds[[string]$attempt.id] = $true
        try {
            $attemptedAt = [DateTimeOffset]::Parse(
                [string]$attempt.attemptedAt,
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::RoundtripKind)
        } catch {
            throw "Checkpoint $($checkpoint.id) attempt timestamp is invalid."
        }
        if ($attemptedAt -lt $previousAttemptAt) {
            throw "Checkpoint $($checkpoint.id) attempts are not chronological."
        }
        $previousAttemptAt = $attemptedAt
        if ($attempt.status -eq 'pass') {
            $packageFile = Assert-ExistingArtifact (
                [string]$attempt.packagePath) "Checkpoint $($checkpoint.id) package"
            $expectedHash = "sha256:$((Get-FileHash -LiteralPath $packageFile -Algorithm SHA256).Hash.ToLowerInvariant())"
            if ($attempt.sha256 -ne $expectedHash) {
                throw "Checkpoint $($checkpoint.id) package hash is invalid."
            }
            [void](Assert-PassEvidence (
                [string]$attempt.evidencePath) "Checkpoint $($checkpoint.id) package evidence")
            $passedAttempts++
        } elseif ($attempt.status -eq 'fail') {
            if ($null -ne $attempt.packagePath -or $null -ne $attempt.sha256) {
                throw "Failed checkpoint $($checkpoint.id) attempt cannot claim a package."
            }
            [void](Assert-ExistingArtifact (
                [string]$attempt.evidencePath) "Checkpoint $($checkpoint.id) failed attempt evidence")
        } else {
            throw "Checkpoint $($checkpoint.id) attempt status is invalid."
        }
    }

    $priorCheckpointSatisfied = $checkpointIndex -eq 0 -or
        $checkpoints[$checkpointIndex - 1].state -in @('packaged', 'superseded')
    $eligible = $requirementsComplete -and $verificationsPass -and $priorCheckpointSatisfied
    switch ([string]$checkpoint.state) {
        'not_ready' {
            if ($eligible -or $passedAttempts -gt 0) {
                throw "Checkpoint $($checkpoint.id) state should not be not_ready."
            }
        }
        'ready' {
            if (-not $eligible -or $passedAttempts -gt 0) {
                throw "Checkpoint $($checkpoint.id) ready state is invalid."
            }
        }
        'packaged' {
            if (-not $eligible -or $passedAttempts -eq 0) {
                throw "Checkpoint $($checkpoint.id) packaged state is invalid."
            }
        }
        'superseded' {
            if (-not $eligible -or $passedAttempts -eq 0 -or
                $checkpointIndex -eq $checkpoints.Count - 1) {
                throw "Checkpoint $($checkpoint.id) superseded state is invalid."
            }
        }
    }
}

if ($attemptIds.Count -gt 3) {
    throw 'Project package attempt limit exceeded: at most three formal attempts are allowed.'
}

for ($checkpointIndex = 0; $checkpointIndex -lt $checkpoints.Count; $checkpointIndex++) {
    $laterPackaged = $false
    if ($checkpointIndex + 1 -lt $checkpoints.Count) {
        $laterPackaged = @($checkpoints[($checkpointIndex + 1)..($checkpoints.Count - 1)] |
            Where-Object { $_.state -in @('packaged', 'superseded') }).Count -gt 0
    }
    if ($checkpoints[$checkpointIndex].state -eq 'superseded' -and -not $laterPackaged) {
        throw "Checkpoint $($checkpoints[$checkpointIndex].id) is superseded without a later package."
    }
    if ($checkpoints[$checkpointIndex].state -eq 'packaged' -and $laterPackaged) {
        throw "Checkpoint $($checkpoints[$checkpointIndex].id) must be superseded by the later package."
    }
}

$percentageText = $expectedPercentage.ToString(
    'F1', [Globalization.CultureInfo]::InvariantCulture)
$emDash = [char]0x2014
$currentLine = '- Current work: none.'
if ($inProgressOutcomes.Count -eq 1) {
    $currentOutcome = $inProgressOutcomes[0]
    $currentLine = "- Current work: ``$($currentOutcome.id)`` $emDash $($currentOutcome.title) (node ``$($progress.currentNode)``)."
}
$nextLine = '- Next work: none.'
if ($null -ne $progress.nextOutcomeId) {
    $nextOutcome = $outcomeById[[string]$progress.nextOutcomeId]
    $nextLine = "- Next work: ``$($nextOutcome.id)`` $emDash $($nextOutcome.title)."
}
$openOutcomes = @($outcomes | Where-Object { $_.status -ne 'completed' })
$phaseParts = @()
foreach ($phaseGroup in @($openOutcomes | Group-Object phase | Sort-Object Name)) {
    $phaseUnits = 0L
    foreach ($phaseOutcome in @($phaseGroup.Group)) {
        $phaseUnits += Get-Integer $phaseOutcome.weight 'phase outcome weight' 1
    }
    $phaseParts += "``$($phaseGroup.Name)`` $($phaseGroup.Count) outcomes / $phaseUnits units"
}
$phaseSummary = if ($phaseParts.Count -eq 0) { 'none' } else { $phaseParts -join '; ' }
$lines = @(
    "# Project Progress $emDash $percentageText%",
    '',
    '<!-- project-progress-schema:1 -->',
    '',
    "- Updated: ``$($progress.updatedAt)``",
    "- Outcome counts: total ``$($computedCounts.total)``, completed ``$($computedCounts.completed)``, in progress ``$($computedCounts.inProgress)``, remaining ``$($computedCounts.remaining)``, deferred ``$($computedCounts.deferred)``.",
    "- Outcome units: total ``$($computedUnits.total)``, completed ``$($computedUnits.completed)``, in progress ``$($computedUnits.inProgress)``, remaining ``$($computedUnits.remaining)``, deferred ``$($computedUnits.deferred)``.",
    $currentLine,
    $nextLine,
    "- Remaining phases: $phaseSummary.",
    '',
    '| Package checkpoint | State | Required outcomes | Attempts |',
    '|---|---|---:|---:|'
)
foreach ($checkpoint in $checkpoints) {
    $lines += "| ``$($checkpoint.id)`` | ``$($checkpoint.state)`` | $(@($checkpoint.requiredOutcomeIds).Count) | $(@($checkpoint.attempts).Count) |"
}
$expectedMarkdown = ([string]::Join("`n", $lines)) + "`n"
if ($PrintCanonicalMarkdown) {
    Write-Output -NoEnumerate $expectedMarkdown
    return
}
$actualMarkdown = $utf8.GetString([System.IO.File]::ReadAllBytes($markdownFile)).Replace("`r`n", "`n")
if ($actualMarkdown -ne $expectedMarkdown) {
    $expectedLines = @($expectedMarkdown -split "`n")
    $actualLines = @($actualMarkdown -split "`n")
    $lineCount = [Math]::Max($expectedLines.Count, $actualLines.Count)
    for ($lineIndex = 0; $lineIndex -lt $lineCount; $lineIndex++) {
        $expectedLine = if ($lineIndex -lt $expectedLines.Count) { $expectedLines[$lineIndex] } else { '<missing>' }
        $actualLine = if ($lineIndex -lt $actualLines.Count) { $actualLines[$lineIndex] } else { '<missing>' }
        if ($expectedLine -ne $actualLine) {
            throw "PROJECT_PROGRESS.md canonical projection drift at line $($lineIndex + 1). Expected: $expectedLine Actual: $actualLine"
        }
    }
    throw 'PROJECT_PROGRESS.md is not the canonical projection of project-progress.json.'
}

Write-Output "Project progress validation passed: $jsonFile"
