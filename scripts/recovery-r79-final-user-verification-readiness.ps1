param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r79-final-user-verification-readiness'
$ResultPath = Join-Path $EvidenceDir 'r79-final-user-verification-readiness-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r79-final-user-verification-readiness-2026-07-06.md'

if (-not (Test-Path -LiteralPath $WorkDir)) {
    New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing JSON evidence: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

function Invoke-AuditScript {
    param(
        [string]$Name,
        [string]$RelativePath,
        [string[]]$Arguments = @()
    )

    $scriptPath = Join-Path $RepoRoot $RelativePath
    $logPath = Join-Path $WorkDir "$Name.log"
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath) + $Arguments
    Push-Location $RepoRoot
    try {
        & powershell @argList > $logPath 2> "$logPath.err"
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logPath
        errorLogFile = "$logPath.err"
    }
}

$childResults = @()
$childResults += Invoke-AuditScript -Name 'final-goal-framework-audit' -RelativePath 'scripts/final-goal-framework-audit.ps1'
$childResults += Invoke-AuditScript -Name 'final-usability-static-audit' -RelativePath 'scripts/final-usability-static-audit.ps1'
$childResults += Invoke-AuditScript -Name 'final-requirement-coverage-audit' -RelativePath 'scripts/final-requirement-coverage-audit.ps1' -Arguments @('-NoFailExit')

$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')
$r78 = Read-JsonFile (Join-Path $EvidenceDir 'r78-frc1-product-surface-human-acceptance-residual-result.json')
$framework = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-goal-framework-audit-result.json')
$static = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-usability-static-audit-result.json')
$coverage = Read-JsonFile (Join-Path $RepoRoot 'docs/evidence/final-requirement-coverage-audit-result.json')

$guidePath = 'docs/recovery/continuation-implementation-guide.md'
$requiredDocs = @(
    '.cursor/README.md',
    '.cursor/session/state.json',
    '.cursor/knowledge/agent-operating-rules.md',
    '.cursor/knowledge/project-operating-rules.md',
    '.cursor/knowledge/failure-lessons.md',
    'docs/recovery/final-usable-system-acceptance.md',
    'docs/framework/next-execution-ledger.md',
    'docs/recovery/p0-task-cards.md',
    'docs/recovery/fix-batches.md',
    $guidePath
)

$missingDocs = @()
foreach ($doc in $requiredDocs) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $doc))) {
        $missingDocs += $doc
    }
}

$nextTasks = @($state.build_plan.nextTasks)
$checks = @(
    [ordered]@{
        area = 'architecture'
        name = 'continuation guide exists and keeps the current architecture mode'
        passed = (Test-Path -LiteralPath (Join-Path $RepoRoot $guidePath))
        detail = $guidePath
    },
    [ordered]@{
        area = 'session'
        name = 'R79 is the active next executable handoff task'
        passed = (($nextTasks -join ' ') -match 'REC-P0-079')
        detail = "nextTasks=$($nextTasks -join ', ')"
    },
    [ordered]@{
        area = 'signoff-boundary'
        name = 'user signoff remains separate from engineering evidence'
        passed = ($state.gates.user_script_passed -eq $false -and $r78.userSignoff -eq $false)
        detail = "state=$($state.gates.user_script_passed), r78=$($r78.userSignoff)"
    },
    [ordered]@{
        area = 'r78'
        name = 'latest product-surface engineering evidence is accepted'
        passed = ($r78.status -eq 'PASS' -and $r78.accepted -eq $true)
        detail = "status=$($r78.status), accepted=$($r78.accepted)"
    },
    [ordered]@{
        area = 'framework'
        name = 'framework audit passes after R79 handoff'
        passed = ($framework.status -eq 'PASS')
        detail = "errors=$(@($framework.errors).Count), warnings=$(@($framework.warnings).Count)"
    },
    [ordered]@{
        area = 'static-usability'
        name = 'static usability audit has no blockers or warnings'
        passed = ($static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0 -and [int]$static.warningCount -eq 0)
        detail = "status=$($static.status), blockers=$($static.blockerCount), warnings=$($static.warningCount)"
    },
    [ordered]@{
        area = 'coverage-boundary'
        name = 'coverage remains honest until user verification'
        passed = ([int]$coverage.missingCount -eq 0 -and [int]$coverage.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false)
        detail = "missing=$($coverage.missingCount), notClosed=$($coverage.notClosedCount)"
    },
    [ordered]@{
        area = 'required-docs'
        name = 'handoff documents required by the architecture are present'
        passed = ($missingDocs.Count -eq 0)
        detail = if ($missingDocs.Count -eq 0) { 'all required docs present' } else { $missingDocs -join ', ' }
    }
)

$failedChecks = @($checks | Where-Object { -not $_.passed })
$failedChildren = @($childResults | Where-Object { $_.exitCode -ne 0 })
$status = if ($failedChecks.Count -eq 0 -and $failedChildren.Count -eq 0) { 'PASS' } else { 'FAIL' }

$verificationScript = @(
    [ordered]@{ order = 1; role = 'visitor'; route = '/login, /register-with-system, /forgot-password'; expected = 'Only authentication surfaces are visible before login.' },
    [ordered]@{ order = 2; role = 'platform admin'; route = '/platform and /platform/admin'; expected = 'Platform workspace and platform admin are separate shells.' },
    [ordered]@{ order = 3; role = 'system admin'; route = '/systems/{systemId}/admin'; expected = 'Admin can configure modules, fields, roles, workflow, pages, OpenAPI, AI, and operations without mixed runtime panels.' },
    [ordered]@{ order = 4; role = 'normal member'; route = '/systems/{systemId}/dashboard and runtime modules'; expected = 'Member can use authorized business data and cannot see or mutate admin configuration.' },
    [ordered]@{ order = 5; role = 'approver/operator/external caller'; route = 'todos, messages, OpenAPI, operations'; expected = 'Workflow, messages, external calls, logs, release health, and denied states are understandable.' },
    [ordered]@{ order = 6; role = 'user reviewer'; route = 'full deployed release'; expected = 'Only user verification can set gates.user_script_passed=true.' }
)

$result = [ordered]@{
    status = $status
    productStatus = 'R79_FINAL_USER_VERIFICATION_READINESS_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-079'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = $checks
    childResults = $childResults
    coverageBoundary = [ordered]@{
        missingCount = [int]$coverage.missingCount
        notClosedCount = [int]$coverage.notClosedCount
        userScriptPassed = [bool]$state.gates.user_script_passed
    }
    prerequisiteEvidence = [ordered]@{
        r78Status = $r78.status
        r78Accepted = [bool]$r78.accepted
        r78UserSignoff = [bool]$r78.userSignoff
    }
    continuationModel = [ordered]@{
        structure = 'cursor session -> recovery docs -> task card -> implementation -> script evidence -> user verification'
        guide = $guidePath
        nextChangeRule = 'Every later change must name the affected role journey, requirement row, generated-vs-coded boundary, permission state, readback evidence, and script before coding.'
    }
    userVerificationScript = $verificationScript
    accepted = ($status -eq 'PASS')
}

$json = $result | ConvertTo-Json -Depth 10
Set-Content -LiteralPath $ResultPath -Value $json -Encoding UTF8

$summary = @(
    '# R79 Final User Verification Readiness',
    '',
    "Status: `$status",
    '',
    'This is engineering evidence only. It prepares the system for user verification and keeps `gates.user_script_passed=false`.',
    '',
    '## What R79 Confirms',
    '',
    '- R78 product-surface evidence is accepted as engineering evidence.',
    '- Framework, static usability, and coverage audits still run from disk.',
    "- Requirement coverage remains honest: missing $($coverage.missingCount), notClosed $($coverage.notClosedCount).",
    "- The continuation guide exists at $guidePath.",
    '- The next change rule is role journey -> requirement row -> task card -> implementation -> script evidence -> user verification.',
    '',
    '## User Verification Path',
    '',
    '1. Visitor checks login, register, and password recovery.',
    '2. Platform admin checks platform workspace and platform admin are separated.',
    '3. System admin configures and publishes the app.',
    '4. Normal member uses authorized runtime data and cannot access admin configuration.',
    '5. Approver, external caller, and operator check todo/message, OpenAPI, logs, and release health.',
    '6. User signoff is the only event that may set `gates.user_script_passed=true`.',
    '',
    'Result JSON: `docs/evidence/recovery/r79-final-user-verification-readiness-result.json`'
) -join [Environment]::NewLine
Set-Content -LiteralPath $SummaryPath -Value $summary -Encoding UTF8

$json

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
