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
        [string]$Detail,
        [string]$Severity = 'ERROR'
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        severity = $Severity
        detail = $Detail
    }) | Out-Null
    if (-not $Passed -and $Severity -eq 'ERROR') {
        throw "$Area/$Name failed: $Detail"
    }
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

function Get-RequirementRows {
    param([string]$LedgerPath)
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in (Get-Content -Encoding UTF8 -LiteralPath $LedgerPath)) {
        if ($line -notmatch '^\|\s*REQ-') {
            continue
        }
        $parts = @($line -split '\|' | ForEach-Object { $_.Trim() })
        if ($parts.Count -lt 7) {
            continue
        }
        $rows.Add([ordered]@{
            id = $parts[1]
            source = $parts[2]
            area = $parts[3]
            status = $parts[4]
            currentEvidence = $parts[5]
            gap = $parts[6]
        }) | Out-Null
    }
    return @($rows.ToArray())
}

function Join-StringArray {
    param([object[]]$Values)
    return (@($Values | ForEach-Object { [string]$_ }) -join ', ')
}

function Get-BrowserEvidence {
    param([object]$Result)
    if ($null -ne $Result.browserFirstUse) {
        return [ordered]@{
            resultCount = $Result.browserFirstUse.browserResultCount
            overflow = $Result.browserFirstUse.browserOverflowCount
            blockers = $Result.browserFirstUse.browserBlockerCount
            path = $Result.browserFirstUse.browserAuditPath
        }
    }
    return [ordered]@{
        resultCount = $Result.browserResultCount
        overflow = $Result.browserOverflowCount
        blockers = $Result.browserBlockerCount
        path = $Result.browserAuditPath
    }
}

trap {
    $failure = [ordered]@{
        status = 'FAIL'
        task = 'REC-P0-067'
        productStatus = 'R67_FINAL_CANDIDATE_REFRESH_FAILED'
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        error = $_.Exception.Message
        checks = @($script:Checks.ToArray())
        userSignoff = $false
    }
    if ($script:FailureResultFile) {
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
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r67-final-role-journey-requirement-acceptance-candidate'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r67-final-role-journey-requirement-acceptance-candidate-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r67-final-role-journey-requirement-acceptance-candidate-2026-07-02.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'final-role-journey-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')
$childResults += Invoke-ChildScript 'final-goal-framework-audit' (Join-Path $PSScriptRoot 'final-goal-framework-audit.ps1') @()
$childResults += Invoke-ChildScript 'final-requirement-coverage-audit' (Join-Path $PSScriptRoot 'final-requirement-coverage-audit.ps1') @('-NoFailExit')
$childResults += Invoke-ChildScript 'recovery-r57-human-role-shell-browser-refresh' (Join-Path $PSScriptRoot 'recovery-r57-frc6-human-acceptance-pass.ps1') @('-BaseUrl', $BaseUrl)

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
$FrameworkAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-goal-framework-audit-result.json')
$CoverageAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-requirement-coverage-audit-result.json')
$R57 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r57-frc6-human-acceptance-pass-result.json')
$R61 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r61-auth-entry-guard-result.json')
$R62 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r62-platform-system-shell-landing-result.json')
$R59 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r59-frc2-frc6-admin-configuration-human-first-use-result.json')
$R63 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r63-configured-runtime-first-use-result.json')
$R64 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r64-workflow-todo-message-first-use-result.json')
$R65 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r65-openapi-assistant-external-service-first-use-result.json')
$R66 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r66-operations-logs-release-maintenance-first-use-result.json')

Add-Check $script:Checks 'release' 'deployed release verification executed successfully' `
    (@($childResults | Where-Object { $_.name -eq 'verify-release' -and $_.exitCode -eq 0 }).Count -eq 1) `
    "baseUrl=$BaseUrl"

Add-Check $script:Checks 'static-usability' 'static usability audit has no blockers or warnings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

Add-Check $script:Checks 'framework' 'final goal framework audit keeps unfinished work visible' `
    ($FrameworkAudit.status -eq 'PASS' -and [int]$FrameworkAudit.coverage.notClosed -gt 0 -and @($FrameworkAudit.nextTasks).Count -gt 0) `
    "status=$($FrameworkAudit.status), notClosed=$($FrameworkAudit.coverage.notClosed), nextTasks=$(Join-StringArray @($FrameworkAudit.nextTasks))"

Add-Check $script:Checks 'requirements' 'coverage audit remains honestly open with no missing rows' `
    ($CoverageAudit.status -eq 'FAIL' -and [int]$CoverageAudit.missingCount -eq 0 -and [int]$CoverageAudit.notClosedCount -eq 45) `
    "status=$($CoverageAudit.status), missing=$($CoverageAudit.missingCount), notClosed=$($CoverageAudit.notClosedCount)"

Add-Check $script:Checks 'role-shell-browser' 'fresh human role shell journey has desktop/mobile coverage and no findings' `
    ($R57.status -eq 'PASS' -and [int]$R57.humanJourney.resultCount -ge 32 -and [int]$R57.humanJourney.failureCount -eq 0 -and [int]$R57.humanJourney.warningCount -eq 0) `
    "results=$($R57.humanJourney.resultCount), failures=$($R57.humanJourney.failureCount), warnings=$($R57.humanJourney.warningCount)"

Add-Check $script:Checks 'role-shell-permission' 'fresh human journey keeps admin and normal-member permission boundary' `
    ([int]$R57.humanJourney.forbiddenCreateStatus -eq 403 -and [int]$R57.humanJourney.forbiddenAdminStatus -eq 403) `
    "forbiddenCreate=$($R57.humanJourney.forbiddenCreateStatus), forbiddenAdmin=$($R57.humanJourney.forbiddenAdminStatus)"

$acceptedEvidence = @(
    @{ key = 'R61 auth-entry'; result = $R61; minBrowser = 0; rows = @('REQ-2.1', 'REQ-5.1', 'REQ-5.2', 'REQ-6.2', 'REQ-8', 'REQ-10') },
    @{ key = 'R62 platform-system-shell'; result = $R62; minBrowser = 0; rows = @('REQ-2.1', 'REQ-4.1', 'REQ-4.2', 'REQ-5.1', 'REQ-5.2', 'REQ-6.2', 'REQ-6.3') },
    @{ key = 'R59 admin-first-use'; result = $R59; minBrowser = 10; rows = @('REQ-4.3', 'REQ-5.3', 'REQ-5.3.1', 'REQ-5.4', 'REQ-5.5', 'REQ-5.6', 'REQ-5.7', 'REQ-5.9', 'REQ-5.10', 'REQ-6.2', 'REQ-6.7', 'REQ-6.10') },
    @{ key = 'R63 configured-runtime'; result = $R63; minBrowser = 7; rows = @($R63.requirementRows) },
    @{ key = 'R64 workflow-todo-message'; result = $R64; minBrowser = 8; rows = @($R64.requirementRows) },
    @{ key = 'R65 openapi-assistant'; result = $R65; minBrowser = 6; rows = @($R65.requirementRows) },
    @{ key = 'R66 operations-logs-release'; result = $R66; minBrowser = 6; rows = @($R66.requirementRows) }
)

foreach ($evidence in $acceptedEvidence) {
    $result = $evidence.result
    Add-Check $script:Checks 'accepted-evidence' "$($evidence.key) status is PASS and user signoff remains false" `
        ($result.status -eq 'PASS' -and ([string]$result.userSignoff -eq 'False' -or $null -eq $result.userSignoff)) `
        "status=$($result.status), userSignoff=$($result.userSignoff)"
    if ([int]$evidence.minBrowser -gt 0) {
        $browserEvidence = Get-BrowserEvidence $result
        Add-Check $script:Checks 'browser-evidence' "$($evidence.key) browser evidence has no overflow or blockers" `
            ([int]$browserEvidence.resultCount -ge [int]$evidence.minBrowser -and [int]$browserEvidence.overflow -eq 0 -and [int]$browserEvidence.blockers -eq 0) `
            "browser=$($browserEvidence.resultCount), overflow=$($browserEvidence.overflow), blockers=$($browserEvidence.blockers)"
    }
}

$freshEvidenceRows = @($acceptedEvidence | ForEach-Object { $_.rows } | ForEach-Object { $_ } | Sort-Object -Unique)
$ledgerRows = Get-RequirementRows -LedgerPath (Join-Path $RepoRoot 'docs\framework\final-requirement-coverage-ledger.md')
$requirementCandidateMatrix = @($ledgerRows | ForEach-Object {
    $row = $_
    $hasFresh = $freshEvidenceRows -contains $row.id
    [ordered]@{
        reqId = $row.id
        area = $row.area
        ledgerStatus = $row.status
        r67CandidateStatus = if ($hasFresh) { 'FRESH_ENGINEERING_EVIDENCE_STILL_PARTIAL' } else { 'PARTIAL_WITH_PRIOR_EVIDENCE_OR_USER_SIGNOFF_REQUIRED' }
        freshEvidence = @($acceptedEvidence | Where-Object { $_.rows -contains $row.id } | ForEach-Object { $_.key })
        canPromoteToProven = $false
        userSignoffRequired = $true
        remainingGap = $row.gap
    }
})

$freshRowCount = @($requirementCandidateMatrix | Where-Object { $_.r67CandidateStatus -eq 'FRESH_ENGINEERING_EVIDENCE_STILL_PARTIAL' }).Count
Add-Check $script:Checks 'candidate-matrix' 'all 45 requirement rows are present in R67 candidate matrix' `
    ($requirementCandidateMatrix.Count -eq 45) `
    "rows=$($requirementCandidateMatrix.Count)"
Add-Check $script:Checks 'candidate-matrix' 'R67 maps fresh evidence but does not promote requirements without user signoff' `
    ($freshRowCount -gt 0 -and @($requirementCandidateMatrix | Where-Object { $_.canPromoteToProven -eq $true }).Count -eq 0) `
    "freshRows=$freshRowCount, promoted=$(@($requirementCandidateMatrix | Where-Object { $_.canPromoteToProven -eq $true }).Count)"

$browserAggregate = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R67 aggregates fresh R57 role-shell browser evidence and R59/R63/R64/R65/R66 flow browser evidence.'
    screenshotEvidenceBoundary = 'Screenshots prove visible hierarchy, overflow, clipping, and visible state only. API/readback assertions, permission positives/negatives, requirement matrix, and explicit user signoff prove behavior and acceptance.'
    roleShell = @{
        source = 'R57 fresh rerun'
        browserAuditPath = $R57.browserAuditFile
        resultCount = $R57.humanJourney.resultCount
        failureCount = $R57.humanJourney.failureCount
        warningCount = $R57.humanJourney.warningCount
        forbiddenCreateStatus = $R57.humanJourney.forbiddenCreateStatus
        forbiddenAdminStatus = $R57.humanJourney.forbiddenAdminStatus
    }
    flowBrowserEvidence = @(
        @{ task = 'R59'; resultCount = (Get-BrowserEvidence $R59).resultCount; overflow = (Get-BrowserEvidence $R59).overflow; blockers = (Get-BrowserEvidence $R59).blockers; path = (Get-BrowserEvidence $R59).path },
        @{ task = 'R63'; resultCount = (Get-BrowserEvidence $R63).resultCount; overflow = (Get-BrowserEvidence $R63).overflow; blockers = (Get-BrowserEvidence $R63).blockers; path = (Get-BrowserEvidence $R63).path },
        @{ task = 'R64'; resultCount = (Get-BrowserEvidence $R64).resultCount; overflow = (Get-BrowserEvidence $R64).overflow; blockers = (Get-BrowserEvidence $R64).blockers; path = (Get-BrowserEvidence $R64).path },
        @{ task = 'R65'; resultCount = (Get-BrowserEvidence $R65).resultCount; overflow = (Get-BrowserEvidence $R65).overflow; blockers = (Get-BrowserEvidence $R65).blockers; path = (Get-BrowserEvidence $R65).path },
        @{ task = 'R66'; resultCount = (Get-BrowserEvidence $R66).resultCount; overflow = (Get-BrowserEvidence $R66).overflow; blockers = (Get-BrowserEvidence $R66).blockers; path = (Get-BrowserEvidence $R66).path }
    )
}
$browserAggregate | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-067'
    productStatus = 'R67_FINAL_CANDIDATE_REFRESH_PASS_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    gatesUserScriptPassedMustRemainFalse = $true
    requirementTotal = $requirementCandidateMatrix.Count
    requirementFreshEvidenceRows = $freshRowCount
    requirementStillPartialRows = @($requirementCandidateMatrix | Where-Object { $_.ledgerStatus -eq 'PARTIAL' }).Count
    requirementOpenRows = @($requirementCandidateMatrix | Where-Object { $_.ledgerStatus -eq 'OPEN' }).Count
    requirementProvenRows = @($requirementCandidateMatrix | Where-Object { $_.ledgerStatus -eq 'PROVEN' }).Count
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    audits = @{
        static = @{
            status = $StaticAudit.status
            blockerCount = $StaticAudit.blockerCount
            warningCount = $StaticAudit.warningCount
        }
        framework = @{
            status = $FrameworkAudit.status
            notClosed = $FrameworkAudit.coverage.notClosed
            nextTasks = @($FrameworkAudit.nextTasks)
        }
        coverage = @{
            status = $CoverageAudit.status
            missingCount = $CoverageAudit.missingCount
            notClosedCount = $CoverageAudit.notClosedCount
            closedCount = $CoverageAudit.closedCount
        }
    }
    primaryJourneyEvidence = @{
        authEntry = @{ task = 'R61'; status = $R61.status; resultFile = 'docs/evidence/recovery/r61-auth-entry-guard-result.json' }
        shellLanding = @{ task = 'R62'; status = $R62.status; resultFile = 'docs/evidence/recovery/r62-platform-system-shell-landing-result.json' }
        roleShellRefresh = @{ task = 'R57'; status = $R57.status; resultFile = 'docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json'; resultCount = $R57.humanJourney.resultCount }
        adminFirstUse = @{ task = 'R59'; status = $R59.status; resultFile = 'docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-result.json'; browserResultCount = (Get-BrowserEvidence $R59).resultCount }
        configuredRuntime = @{ task = 'R63'; status = $R63.status; systemId = $R63.handoff.systemId; moduleId = $R63.handoff.moduleId; recordId = $R63.recordId; browserResultCount = $R63.browserResultCount }
        workflowTodoMessage = @{ task = 'R64'; status = $R64.status; systemId = $R64.systemId; flowId = $R64.flowId; todoId = $R64.pendingTodoId; messageId = $R64.pendingMessageId; browserResultCount = $R64.browserResultCount }
        openApiAssistant = @{ task = 'R65'; status = $R65.status; systemId = $R65.systemId; openApiAppId = $R65.openApiAppId; recordId = $R65.openApiRecordId; browserResultCount = $R65.browserResultCount }
        operationsLogsRelease = @{ task = 'R66'; status = $R66.status; systemId = $R66.systemId; releaseVerifyStatus = $R66.releaseVerifyStatus; browserResultCount = $R66.browserResultCount }
    }
    browserAuditPath = 'docs/evidence/recovery/screenshots/r67-final-role-journey-requirement-acceptance-candidate/final-role-journey-browser-audit.json'
    requirementCandidateMatrix = $requirementCandidateMatrix
    remainingBlockers = @($requirementCandidateMatrix | Where-Object { $_.ledgerStatus -in @('PARTIAL', 'OPEN') } | Select-Object -First 12)
    accepted = $true
}
$result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-067 / R67 Final Role Journey And Requirement Acceptance Candidate Refresh

Status: PASS as final-candidate engineering evidence only.

- Base URL: $BaseUrl
- Release/static/framework audits: release child exit=0, static=$($StaticAudit.status) blockers=$($StaticAudit.blockerCount) warnings=$($StaticAudit.warningCount), framework=$($FrameworkAudit.status)
- Requirement coverage audit: status=$($CoverageAudit.status), missing=$($CoverageAudit.missingCount), notClosed=$($CoverageAudit.notClosedCount), closed=$($CoverageAudit.closedCount)
- Fresh role-shell browser refresh: R57 status=$($R57.status), results=$($R57.humanJourney.resultCount), failures=$($R57.humanJourney.failureCount), warnings=$($R57.humanJourney.warningCount)
- Aggregated primary journeys: R61 auth, R62 shell landing, R59 admin first-use, R63 configured runtime, R64 workflow/todo/message, R65 OpenAPI/assistant, R66 operations/logs/release
- Requirement candidate matrix: rows=$($requirementCandidateMatrix.Count), rows with fresh R59/R63-R66 evidence=$freshRowCount, promotedToProven=0
- Browser audit: `docs/evidence/recovery/screenshots/r67-final-role-journey-requirement-acceptance-candidate/final-role-journey-browser-audit.json`

This does not close final product acceptance. All requirement rows remain ledger-PARTIAL until a later task provides enough evidence for `PROVEN` or the user explicitly signs/excludes scope. `gates.user_script_passed` remains false.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 40

if ($NoFailExit) {
    exit 0
}
