param(
    [string]$BaseUrl = "http://127.0.0.1:18131",
    [switch]$NoFailExit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$EvidenceDir = Join-Path $Root 'docs/evidence/recovery'
$ResultPath = Join-Path $EvidenceDir 'r83-user-trial-feedback-intake-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r83-user-trial-feedback-intake-2026-07-07.md'

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing JSON file: $Path"
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

$checks = New-Object System.Collections.Generic.List[object]
function Add-Check {
    param(
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $checks.Add([pscustomobject]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

$state = Read-JsonFile (Join-Path $Root '.cursor/session/state.json')
$r81 = Read-JsonFile (Join-Path $EvidenceDir 'r81-trial-login-role-use-audit-result.json')
$r82 = Read-JsonFile (Join-Path $EvidenceDir 'r82-visible-copy-encoding-trial-usability-result.json')
$coverage = Read-JsonFile (Join-Path $Root 'docs/evidence/final-requirement-coverage-audit-result.json')

$nextTasks = @($state.build_plan.nextTasks)
$acceptedBatches = @($state.build_plan.acceptedBatches)
Add-Check 'state' 'R83 is the active next task and R82 is accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R83') -and ($nextTasks -contains 'REC-P0-083 User Trial Feedback Intake And Next Change Selection') -and ($acceptedBatches -contains 'RECOVERY-R82')) ("currentBatch={0}, nextTasks={1}" -f $state.build_plan.currentBatch, ($nextTasks -join ';'))

Add-Check 'baseline' 'R81 role-use evidence remains PASS on current deployment' (($r81.status -eq 'PASS') -and ([bool]$r81.accepted) -and (-not [bool]$r81.userSignoff)) ("status={0}, accepted={1}, baseUrl={2}" -f $r81.status, $r81.accepted, $r81.baseUrl)
Add-Check 'baseline' 'R82 visible-copy evidence remains PASS on current deployment' (($r82.status -eq 'PASS') -and ([bool]$r82.accepted) -and (-not [bool]$r82.userSignoff) -and ($r82.browserAudit.mojibakeCount -eq 0) -and ($r82.browserAudit.blockerCount -eq 0)) ("status={0}, browserResults={1}, mojibake={2}, blockers={3}" -f $r82.status, $r82.browserAudit.resultCount, $r82.browserAudit.mojibakeCount, $r82.browserAudit.blockerCount)

Add-Check 'coverage-boundary' 'coverage remains honest and not closed by engineering evidence' (($coverage.missingCount -eq 0) -and ($coverage.notClosedCount -eq 45) -and ($coverage.closedCount -eq 0)) ("missing={0}, notClosed={1}, closed={2}" -f $coverage.missingCount, $coverage.notClosedCount, $coverage.closedCount)
Add-Check 'signoff-boundary' 'user signoff remains false until explicit user verification' ((-not [bool]$state.gates.user_script_passed) -and (-not [bool]$r81.userSignoff) -and (-not [bool]$r82.userSignoff)) ("state.user_script_passed={0}" -f $state.gates.user_script_passed)

$taskText = Get-Content -Raw -LiteralPath (Join-Path $Root 'docs/recovery/p0-task-cards.md')
$fixText = Get-Content -Raw -LiteralPath (Join-Path $Root 'docs/recovery/fix-batches.md')
$ledgerText = Get-Content -Raw -LiteralPath (Join-Path $Root 'docs/framework/next-execution-ledger.md')
Add-Check 'intake-contract' 'R83 task card, fix batch, and next ledger are present' (($taskText -match 'REC-P0-083 User Trial Feedback Intake And Next Change Selection') -and ($fixText -match 'Batch R83') -and ($ledgerText -match 'REC-P0-083 User Trial Feedback Intake And Next Change Selection')) 'R83 contract is present on disk'

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }

$checkArray = @($checks.ToArray())
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R83_USER_TRIAL_FEEDBACK_INTAKE_CONTROL_EVIDENCE_ONLY'
    task = 'REC-P0-083'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = $checkArray
    prerequisites = [pscustomobject]@{
        r81Status = $r81.status
        r81GeneratedAt = $r81.generatedAt
        r82Status = $r82.status
        r82GeneratedAt = $r82.generatedAt
        coverageNotClosed = $coverage.notClosedCount
        coverageMissing = $coverage.missingCount
    }
    accepted = ($status -eq 'PASS')
})

$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath

$summary = @(
    '# R83 User Trial Feedback Intake',
    '',
    ('Status: `{0}`' -f $status),
    '',
    ('Generated at: `{0}`' -f $result.generatedAt),
    '',
    'This is continuation/control evidence only. It preserves the boundary that engineering evidence cannot set user signoff.',
    '',
    '## Checks',
    ''
)
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''
$summary += '## Boundary'
$summary += ''
$summary += ('- Coverage not closed: `{0}`' -f $coverage.notClosedCount)
$summary += ('- User signoff: `{0}`' -f $false)
$summary += '- Next product coding must start from one user feedback item or one unfinished requirement row.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath

Write-Host ("R83 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) {
    exit 1
}
