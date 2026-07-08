param(
    [string]$CoverageLedgerPath = 'docs/framework/final-requirement-coverage-ledger.md',
    [string]$GapReportPath = 'docs/evidence/final-requirement-gap-report.md',
    [string]$NextExecutionLedgerPath = 'docs/framework/next-execution-ledger.md',
    [string]$TaskCardsPath = 'docs/recovery/p0-task-cards.md',
    [string]$FixBatchesPath = 'docs/recovery/fix-batches.md',
    [string]$SessionStatePath = '.cursor/session/state.json',
    [string]$FrameworkPath = '.cursor/architecture/final-goal-framework.md',
    [string]$WorkflowPath = '.cursor/workflows/final-goal-recovery.md',
    [string]$TaskTemplatePath = '.cursor/templates/task.md',
    [string]$FinalUsableAcceptancePath = 'docs/recovery/final-usable-system-acceptance.md',
    [string]$FlowBlueprintPath = 'docs/framework/final-system-flow-blueprint.md',
    [string]$ResultPath = 'docs/evidence/final-goal-framework-audit-result.json',
    [string]$SummaryPath = 'docs/evidence/final-goal-framework-audit.md',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail,
        [string]$Severity = 'ERROR'
    )
    $Checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        severity = $Severity
        detail = $Detail
    }) | Out-Null
}

function Read-RequiredFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file missing: $Path"
    }
    return Get-Content -Raw -LiteralPath $Path
}

function Get-MarkdownH2SectionByPrefix {
    param(
        [string]$Text,
        [string]$HeadingPrefix
    )
    $pattern = '(?ms)^##\s+' + [regex]::Escape($HeadingPrefix) + '\b.*?(?=^##\s+|\z)'
    $match = [regex]::Match($Text, $pattern)
    if ($match.Success) {
        return $match.Value
    }
    return ''
}

function Test-ContractRow {
    param(
        [string]$Section,
        [string]$Label
    )
    $pattern = '\|\s*' + [regex]::Escape($Label) + '\s*\|'
    return $Section -match $pattern
}

$checks = [System.Collections.Generic.List[object]]::new()
$ledgerText = Read-RequiredFile $CoverageLedgerPath
$gapReportText = Read-RequiredFile $GapReportPath
$nextText = Read-RequiredFile $NextExecutionLedgerPath
$taskText = Read-RequiredFile $TaskCardsPath
$fixText = Read-RequiredFile $FixBatchesPath
$stateText = Read-RequiredFile $SessionStatePath
$frameworkText = Read-RequiredFile $FrameworkPath
$workflowText = Read-RequiredFile $WorkflowPath
$taskTemplateText = Read-RequiredFile $TaskTemplatePath
$finalUsableText = Read-RequiredFile $FinalUsableAcceptancePath
$flowBlueprintText = Read-RequiredFile $FlowBlueprintPath
$state = $stateText | ConvertFrom-Json

$rows = @()
foreach ($line in ($ledgerText -split "`r?`n")) {
    if ($line -match '^\|\s*(REQ-[^|]+)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*(PROVEN|PARTIAL|OPEN|USER_EXCLUDED)\s*\|\s*([^|]*)\|\s*([^|]*)\|') {
        $rows += [ordered]@{
            id = $matches[1].Trim()
            source = $matches[2].Trim()
            area = $matches[3].Trim()
            status = $matches[4].Trim()
            evidence = $matches[5].Trim()
            gap = $matches[6].Trim()
        }
    }
}

$openRows = @($rows | Where-Object { $_.status -eq 'OPEN' })
$partialRows = @($rows | Where-Object { $_.status -eq 'PARTIAL' })
$closedRows = @($rows | Where-Object { $_.status -in @('PROVEN', 'USER_EXCLUDED') })
$notClosedRows = @($rows | Where-Object { $_.status -in @('OPEN', 'PARTIAL') })

Add-Check $checks 'coverage ledger parses requirement rows' ($rows.Count -gt 0) "rows=$($rows.Count)"
Add-Check $checks 'no final user signoff while requirements remain open' (-not ($state.gates.user_script_passed -eq $true -and $notClosedRows.Count -gt 0)) "user_script_passed=$($state.gates.user_script_passed), notClosed=$($notClosedRows.Count)"

$nextHasOpenWork = $nextText -match '\|\s*J\d+\s*\|[^|]*\|[^|]*\|\s*(OPEN|PARTIAL|PARTIAL_PASS_ENGINEERING|FAIL_EXPECTED)\s*\|' `
    -or $nextText -match '## Executable Next Work'
Add-Check $checks 'next execution ledger exposes unfinished work' ($notClosedRows.Count -eq 0 -or $nextHasOpenWork) "notClosed=$($notClosedRows.Count), hasOpenWork=$nextHasOpenWork"

$nextTasks = @($state.build_plan.nextTasks)
Add-Check $checks 'session state has next executable tasks while requirements remain partial' ($notClosedRows.Count -eq 0 -or $nextTasks.Count -gt 0) "nextTasks=$($nextTasks -join ', ')"

$contractFields = @(
    'User role',
    'Business outcome',
    'Prototype reference',
    'Frontend scope',
    'Backend scope',
    'Generator scope',
    'Data scope',
    'Permission rule',
    'States',
    'Explicitly not complete if',
    'Acceptance script',
    'Evidence paths'
)
foreach ($field in $contractFields) {
    Add-Check $checks "task card contract contains $field" ($taskText -match [regex]::Escape($field)) $field
}

$requiredStatusLanguage = @('open', 'planned', 'in_progress', 'blocked', 'accepted')
foreach ($word in $requiredStatusLanguage) {
    Add-Check $checks "fix batches status language includes $word" ($fixText -match [regex]::Escape($word)) $word 'WARN'
}

$hasV4 = $nextText -match 'Framework v4' -and $ledgerText -match 'PARTIAL'
Add-Check $checks 'framework v4 ledger marker is present' $hasV4 'next ledger mentions Framework v4 and coverage ledger keeps PARTIAL gate visible'

$hasV5Framework = $frameworkText -match 'V5 Requirement-First Lock' `
    -and $frameworkText -match 'Screenshots are useful only for visual evidence' `
    -and $frameworkText -match 'requirement confirmation contract'
Add-Check $checks 'framework v5 requirement-first lock is present' $hasV5Framework 'framework must separate requirement truth from screenshot visual evidence'

$hasV5Template = $taskTemplateText -match 'Requirement Confirmation Contract' `
    -and $taskTemplateText -match 'Screenshot evidence boundary' `
    -and $taskTemplateText -match 'Acceptance assertions'
Add-Check $checks 'task template requires requirement confirmation contract' $hasV5Template 'task template must force requirement source, assertions, and screenshot boundary'

$hasV5Workflow = $workflowText -match 'requirement confirmation contract' `
    -and $workflowText -match 'screenshots only as visual evidence' `
    -and $workflowText -match 'cannot confirm requirement scope'
Add-Check $checks 'workflow rejects screenshot-only confirmation' $hasV5Workflow 'workflow must block coding from screenshots without requirement contract'

$hasV6Framework = $frameworkText -match 'V6 Usable-System Operating Model' `
    -and $frameworkText -match 'historical engineering PASS does not survive new user feedback' `
    -and $frameworkText -match 'requirement row\s*->\s*role journey'
Add-Check $checks 'framework v6 usable-system remediation lock is present' $hasV6Framework 'framework must reopen acceptance from user feedback and force requirement-row journey execution'

$hasV6CurrentEngineering = $nextText -match 'Framework v6 usable-system remediation lock' `
    -and $finalUsableText -match 'REOPENED_BY_USER_FEEDBACK' `
    -and ($nextText -match 'Framework V6 usable-system remediation' -or $nextText -match 'REC-P0-052' -or $nextText -match 'REC-P0-057' -or $nextText -match 'REC-P0-060')
Add-Check $checks 'next ledger exposes usable-system remediation as executable work' $hasV6CurrentEngineering 'next ledger must not keep old slice work as the primary task after user feedback'

$hasV7Framework = $frameworkText -match 'V7 Human-Usable System Gate' `
    -and $frameworkText -match 'one primary task surface' `
    -and $frameworkText -match 'No-Prompt Execution Boundary' `
    -and $frameworkText -match 'Useless Artifact Cleanup Rule'
Add-Check $checks 'framework v7 human-usable gate is present' $hasV7Framework 'framework must force human role journeys, no-prompt execution boundary, and cleanup of false-completion artifacts'

$hasV7Workflow = $workflowText -match 'human-usable role journey' `
    -and $workflowText -match 'Do not ask the user for ordinary engineering choices' `
    -and $workflowText -match 'page stacking, mixed role shells, ambiguous tips'
Add-Check $checks 'workflow v7 human-usable recovery rules are present' $hasV7Workflow 'workflow must choose human journey acceptance after still-unusable feedback and avoid unnecessary product prompts'

$hasV7Template = $taskTemplateText -match 'V7 Human-Usable Gate' `
    -and $taskTemplateText -match 'role_shell_separation' `
    -and $taskTemplateText -match 'page_stacking_risk' `
    -and $taskTemplateText -match 'ambiguous_copy_or_tip_risk'
Add-Check $checks 'task template v7 human-usable fields are present' $hasV7Template 'task template must force role shell, page stacking, copy/tip, and post-action usability checks'

$hasV7Record = $finalUsableText -match 'V7 Human-Usable Gate Upgrade' `
    -and $finalUsableText -match 'REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure' `
    -and $finalUsableText -match 'User signoff: still `false`'
Add-Check $checks 'final usable-system acceptance records v7 upgrade' $hasV7Record 'final acceptance must keep V7 human usability and user signoff boundary visible'

$hasV8Framework = $frameworkText -match 'V8 Flow Blueprint Rebuild Contract' `
    -and $frameworkText -match 'final-system-flow-blueprint\.md' `
    -and $frameworkText -match 'flow id'
Add-Check $checks 'framework v8 flow blueprint lock is present' $hasV8Framework 'framework must require the flow blueprint and flow ids before coding'

$hasV8Blueprint = $flowBlueprintText -match 'Flow A1: Login' `
    -and $flowBlueprintText -match 'Flow A2: Register And Create First System' `
    -and $flowBlueprintText -match 'Flow A3: Password Recovery' `
    -and $flowBlueprintText -match 'Shell Model' `
    -and $flowBlueprintText -match 'Flow S1: Enter System' `
    -and $flowBlueprintText -match 'Flow C1: First-Use Configuration Guide' `
    -and $flowBlueprintText -match 'Flow B2: Runtime Module Navigation' `
    -and $flowBlueprintText -match 'Flow E1: OpenAPI Caller' `
    -and $flowBlueprintText -match 'Flow AI2: System Agent' `
    -and $flowBlueprintText -match 'Flow O1: Release And Health'
Add-Check $checks 'flow blueprint covers auth, shells, system, runtime, integration, AI, and operations' $hasV8Blueprint 'blueprint must describe the system from entry to operation, not just one page slice'

$r60Section = Get-MarkdownH2SectionByPrefix -Text $taskText -HeadingPrefix 'REC-P0-060'
$r60HasContract = -not [string]::IsNullOrWhiteSpace($r60Section) `
    -and $r60Section -match 'Requirement Confirmation Contract' `
    -and $r60Section -match 'final-system-flow-blueprint\.md' `
    -and $r60Section -match 'flow id'
Add-Check $checks 'REC-P0-060 task card locks flow blueprint before coding' $r60HasContract 'R60 must make the flow blueprint an active framework task'

$activeFlowBlueprintTask = ($nextTasks -join ' ') -match 'REC-P0-060|REC-P0-061|REC-P0-062|REC-P0-059|REC-P0-063|REC-P0-064|REC-P0-065|REC-P0-066|REC-P0-067|REC-P0-068|REC-P0-069|REC-P0-070|REC-P0-071|REC-P0-072|REC-P0-073|REC-P0-074|REC-P0-075|REC-P0-077|REC-P0-078|REC-P0-079|REC-P0-080|REC-P0-081|REC-P0-082|REC-P0-083|REC-P0-084|REC-P0-085|REC-P0-086|REC-P0-087|REC-P0-088|REC-P0-089|REC-P0-090|REC-P0-091|REC-P0-092' `
    -and $nextText -match 'REC-P0-060 Flow Blueprint And Rebuild Contract Lock' `
    -and $nextText -match 'REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure' `
    -and $nextText -match 'REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure' `
    -and $nextText -match 'REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure' `
    -and $nextText -match 'REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure' `
    -and $nextText -match 'REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh' `
    -and $nextText -match 'REC-P0-068 Page Visual Designer Fresh Evidence Closure' `
    -and $nextText -match 'REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision' `
    -and $nextText -match 'REC-P0-070 No-Code Configuration Residual Depth Closure' `
    -and $nextText -match 'REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure' `
    -and $nextText -match 'REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure' `
    -and $nextText -match 'REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure' `
    -and $nextText -match 'REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure' `
    -and $nextText -match 'REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure' `
    -and $nextText -match 'REC-P0-077 Final Requirement Candidate Refresh After Residual Closure' `
    -and $nextText -match 'REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure' `
    -and $nextText -match 'REC-P0-079 Final User Verification Readiness And Continuation Contract' `
    -and $nextText -match 'REC-P0-080 Live User Trial Workspace Seed' `
    -and $nextText -match 'REC-P0-081 Trial Login And Role Use Audit' `
    -and $nextText -match 'REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup' `
    -and $nextText -match 'REC-P0-083 User Trial Feedback Intake And Next Change Selection' `
    -and $nextText -match 'REC-P0-084 System Work Management Daily Use Closure' `
    -and $nextText -match 'REC-P0-085 System Dashboard Daily Action Hub Closure' `
    -and $nextText -match 'REC-P0-086 Page Designer Drag Canvas And Runtime Component Contract Closure' `
    -and $nextText -match 'REC-P0-087 System Todo Message Workbench Usability Closure' `
    -and $nextText -match 'REC-P0-088 Runtime Efficiency Entry Search Recent Draft Closure' `
    -and $nextText -match 'REC-P0-089 Runtime File Import Export Recovery Detail Closure' `
    -and $nextText -match 'REC-P0-090 System Org Member Role Binding First-Use Closure' `
    -and $nextText -match 'REC-P0-091 Role Permission Matrix Impact Preview Audit Closure' `
    -and $nextText -match 'REC-P0-092 Workflow Designer Advanced Node Publish Impact Closure' `
    -and $finalUsableText -match 'final-system-flow-blueprint\.md' `
    -and $fixText -match 'Batch R60' `
    -and $fixText -match 'Batch R63' `
    -and $fixText -match 'Batch R64' `
    -and $fixText -match 'Batch R65' `
    -and $fixText -match 'Batch R66' `
    -and $fixText -match 'Batch R67' `
    -and $fixText -match 'Batch R68' `
    -and $fixText -match 'Batch R69' `
    -and $fixText -match 'Batch R70' `
    -and $fixText -match 'Batch R71' `
    -and $fixText -match 'Batch R72' `
    -and $fixText -match 'Batch R73' `
    -and $fixText -match 'Batch R74' `
    -and $fixText -match 'Batch R75' `
    -and $fixText -match 'Batch R77' `
    -and $fixText -match 'Batch R78' `
    -and $fixText -match 'Batch R79' `
    -and $fixText -match 'Batch R80' `
    -and $fixText -match 'Batch R81' `
    -and $fixText -match 'Batch R82' `
    -and $fixText -match 'Batch R83' `
    -and $fixText -match 'Batch R84' `
    -and $fixText -match 'Batch R85' `
    -and $fixText -match 'Batch R86' `
    -and $fixText -match 'Batch R87' `
    -and $fixText -match 'Batch R88' `
    -and $fixText -match 'Batch R89' `
    -and $fixText -match 'Batch R88' `
    -and $fixText -match 'Batch R89' `
    -and $fixText -match 'Batch R90' `
    -and $fixText -match 'Batch R91' `
    -and $fixText -match 'Batch R92'
Add-Check $checks 'active next task follows flow-blueprint execution after user correction' $activeFlowBlueprintTask 'session, ledger, final acceptance, and fix batches must keep R60 recorded and keep subsequent tasks flow-derived'

$finalAcceptanceReopened = $finalUsableText -match 'REOPENED_BY_USER_FEEDBACK' `
    -and $finalUsableText -match 'PARTIAL_PASS_ENGINEERING' `
    -and $finalUsableText -match 'gates\.user_script_passed.*false'
Add-Check $checks 'final usable-system acceptance is reopened by user feedback' $finalAcceptanceReopened 'historical journey PASS rows must be downgraded to engineering evidence while user signoff is false'

$allJourneyPassRows = @()
foreach ($line in ($finalUsableText -split "`r?`n")) {
    if ($line -match '^\|\s*J\d+\s*\|.*\|\s*`?PASS`?\s*\|') {
        $allJourneyPassRows += $line.Trim()
    }
}
Add-Check $checks 'final usable-system journey rows do not claim final PASS after user feedback' ($allJourneyPassRows.Count -eq 0) (($allJourneyPassRows | Select-Object -First 5) -join ' | ')

$nextTaskContractMappings = @(
    [ordered]@{
        Pattern = 'Workflow Designer Advanced Node Publish Impact Closure|REC-P0-092'
        TaskId = 'REC-P0-092'
        FrcId = 'REC-P0-092'
        RequiredCoverageRows = @(
            'REQ-4.5',
            'REQ-5.12',
            'REQ-5.16',
            'REQ-5.19',
            'REQ-6.9',
            'REQ-6.11',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r92-workflow-advanced-node-publish-impact.ps1',
            'docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-result.json',
            'docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-2026-07-08.md'
        )
        RequireVisualOnlyScreenshotBoundary = $true
    },    [ordered]@{
        Pattern = 'Permission Matrix Impact Preview Audit Closure|REC-P0-091'
        TaskId = 'REC-P0-091'
        FrcId = 'REC-P0-091'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.7',
            'REQ-5.10',
            'REQ-6.2',
            'REQ-6.4',
            'REQ-6.10',
            'REQ-2.1'
        )
    },
    [ordered]@{
        Pattern = 'Org Member Role Binding First-Use Closure|REC-P0-090'
        TaskId = 'REC-P0-090'
        FrcId = 'REC-P0-090'
        RequiredCoverageRows = @(
            'REQ-4.1',
            'REQ-4.3',
            'REQ-5.2',
            'REQ-5.7',
            'REQ-5.10',
            'REQ-6.2',
            'REQ-6.10',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r90-org-member-role-binding-first-use.ps1',
            'docs/evidence/recovery/r90-org-member-role-binding-first-use-result.json',
            'docs/evidence/recovery/r90-org-member-role-binding-first-use-2026-07-07.md'
        )
        RequireVisualOnlyScreenshotBoundary = $true
    },    [ordered]@{
        Pattern = 'Runtime Efficiency Entry Search Recent Draft Closure|REC-P0-088'
        TaskId = 'REC-P0-088'
        FrcId = 'REC-P0-088'
        RequiredCoverageRows = @(
            'REQ-4.4',
            'REQ-5.11',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.11',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r88-runtime-efficiency-entry-search-recent-draft.ps1',
            'docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-result.json',
            'docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md'
        )
        RequireVisualOnlyScreenshotBoundary = $true
    },    [ordered]@{
        Pattern = 'Todo Message Workbench Usability Closure|REC-P0-087'
        TaskId = 'REC-P0-087'
        FrcId = 'REC-P0-087'
        RequiredCoverageRows = @(
            'REQ-5.16',
            'REQ-5.20',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.11',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r87-todo-message-workbench-usability.ps1',
            'docs/evidence/recovery/r87-todo-message-workbench-usability-result.json',
            'docs/evidence/recovery/r87-todo-message-workbench-usability-2026-07-07.md'
        )
        RequireVisualOnlyScreenshotBoundary = $true
    },
    [ordered]@{
        Pattern = 'Page Designer Drag Canvas And Runtime Component Contract Closure|REC-P0-086'
        TaskId = 'REC-P0-086'
        FrcId = 'REC-P0-086'
        RequiredCoverageRows = @(
            'REQ-5.8',
            'REQ-6.1',
            'REQ-6.8',
            'REQ-6.11',
            'REQ-9',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r86-page-designer-drag-canvas-runtime-contract.ps1',
            'docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-result.json',
            'docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-2026-07-07.md'
        )
        RequireVisualOnlyScreenshotBoundary = $true
    },
    [ordered]@{
        Pattern = 'System Dashboard Daily Action Hub Closure|REC-P0-085'
        TaskId = 'REC-P0-085'
        FrcId = 'REC-P0-085'
        RequiredCoverageRows = @(
            'REQ-5.8',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-5.20',
            'REQ-5.16',
            'REQ-6.11',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r85-system-dashboard-daily-action-hub.ps1',
            'docs/evidence/recovery/r85-system-dashboard-daily-action-hub-result.json',
            'docs/evidence/recovery/r85-system-dashboard-daily-action-hub-2026-07-07.md'
        )
    },
    [ordered]@{
        Pattern = 'System Work Management Daily Use Closure|REC-P0-084'
        TaskId = 'REC-P0-084'
        FrcId = 'REC-P0-084'
        RequiredCoverageRows = @(
            'REQ-5.20',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.11',
            'REQ-2.1'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r84-work-management-daily-use.ps1',
            'docs/evidence/recovery/r84-work-management-daily-use-result.json',
            'docs/evidence/recovery/r84-work-management-daily-use-2026-07-07.md'
        )
    },
    [ordered]@{
        Pattern = 'User Trial Feedback Intake And Next Change Selection|REC-P0-083'
        TaskId = 'REC-P0-083'
        FrcId = 'REC-P0-083'
        RequiredCoverageRows = @(
            'REQ-2.1',
            'REQ-6.1',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.8',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Selection contract',
            'Acceptance assertions',
            'Signoff boundary',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r83-user-trial-feedback-intake.ps1',
            'docs/evidence/recovery/r83-user-trial-feedback-intake-result.json',
            'docs/evidence/recovery/r83-user-trial-feedback-intake-2026-07-07.md'
        )
    },[ordered]@{
        Pattern = 'Visible Copy Encoding And Trial Usability Cleanup|REC-P0-082'
        TaskId = 'REC-P0-082'
        FrcId = 'REC-P0-082'
        RequiredCoverageRows = @(
            'REQ-2.1',
            'REQ-6.1',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.8',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r82-visible-copy-encoding-trial-usability.ps1',
            'docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-result.json',
            'docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-2026-07-07.md',
            'docs/evidence/recovery/screenshots/r82-visible-copy-encoding-trial-usability/visible-copy-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Operations Logs Release Maintenance Error-State Residual Closure|REC-P0-075'
        TaskId = 'REC-P0-075'
        FrcId = 'REC-P0-075'
        RequiredCoverageRows = @(
            'REQ-4.6',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-7',
            'REQ-8',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r75-operations-logs-release-error-state-residual.ps1',
            'docs/evidence/recovery/r75-operations-logs-release-error-state-residual-result.json',
            'docs/evidence/recovery/r75-operations-logs-release-error-state-residual-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r75-operations-logs-release-error-state-residual/operations-logs-release-error-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'OpenAPI AI External-Service Error-State Residual Closure|REC-P0-074'
        TaskId = 'REC-P0-074'
        FrcId = 'REC-P0-074'
        RequiredCoverageRows = @(
            'REQ-5.15',
            'REQ-5.19',
            'REQ-9',
            'REQ-14.1-14.37'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r74-openapi-ai-external-service-error-state-residual.ps1',
            'docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-result.json',
            'docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r74-openapi-ai-external-service-error-state-residual/openapi-ai-external-service-error-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Workflow Todo Message Integration Error-State Residual Closure|REC-P0-073'
        TaskId = 'REC-P0-073'
        FrcId = 'REC-P0-073'
        RequiredCoverageRows = @(
            'REQ-4.5',
            'REQ-5.12',
            'REQ-5.16',
            'REQ-5.19',
            'REQ-6.9',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r73-workflow-todo-message-error-state-residual.ps1',
            'docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-result.json',
            'docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r73-workflow-todo-message-error-state-residual/workflow-todo-message-error-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Runtime Daily-Use File Import Export Error-State Residual Closure|REC-P0-072'
        TaskId = 'REC-P0-072'
        FrcId = 'REC-P0-072'
        RequiredCoverageRows = @(
            'REQ-4.4',
            'REQ-5.11',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-5.16',
            'REQ-5.20',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.11'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r72-runtime-file-import-export-error-state-residual.ps1',
            'docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-result.json',
            'docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r72-runtime-file-import-export-error-state-residual/runtime-file-import-export-error-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'No-Code Frontend Binding And Hierarchy Residual Closure|REC-P0-071'
        TaskId = 'REC-P0-071'
        FrcId = 'REC-P0-071'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-6.7',
            'REQ-6.10'
        )
        RequiredEvidence = @(
            'scripts/recovery-r71-no-code-frontend-binding-and-hierarchy-residual.ps1',
            'docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-result.json',
            'docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r71-no-code-frontend-binding-and-hierarchy-residual/no-code-frontend-binding-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'No-Code Configuration Residual Depth Closure|REC-P0-070'
        TaskId = 'REC-P0-070'
        FrcId = 'REC-P0-070'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-6.7',
            'REQ-6.10'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r70-no-code-configuration-residual-depth.ps1',
            'docs/evidence/recovery/r70-no-code-configuration-residual-depth-result.json',
            'docs/evidence/recovery/r70-no-code-configuration-residual-depth-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r70-no-code-configuration-residual-depth/no-code-configuration-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Requirement Evidence Promotion And Residual Gap Decision|REC-P0-069'
        TaskId = 'REC-P0-069'
        FrcId = 'REC-P0-069'
                RequiredCoverageRows = @(
            'REQ-2.1',
            'REQ-4.1',
            'REQ-4.2',
            'REQ-4.3',
            'REQ-4.4',
            'REQ-4.5',
            'REQ-4.6',
            'REQ-5.1',
            'REQ-5.2',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.8',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-5.11',
            'REQ-5.12',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-5.15',
            'REQ-5.16',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-5.19',
            'REQ-5.20',
            'REQ-6.1',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.7',
            'REQ-6.8',
            'REQ-6.9',
            'REQ-6.10',
            'REQ-6.11',
            'REQ-7',
            'REQ-8',
            'REQ-9',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r69-requirement-evidence-promotion-and-gap-decision.ps1',
            'docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-result.json',
            'docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Page Visual Designer Fresh Evidence|REC-P0-068'
        TaskId = 'REC-P0-068'
        FrcId = 'REC-P0-068'
        RequiredCoverageRows = @(
            'REQ-5.8',
            'REQ-6.1',
            'REQ-6.8'
        )
        RequiredContractRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r68-page-visual-designer-fresh-evidence.ps1',
            'docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-result.json',
            'docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'Final Role Journey And Requirement Acceptance Candidate|REC-P0-067'
        TaskId = 'REC-P0-067'
        FrcId = 'FINAL'
        RequiredCoverageRows = @(
            'REQ-2.1',
            'REQ-4.1',
            'REQ-4.2',
            'REQ-4.3',
            'REQ-4.4',
            'REQ-4.5',
            'REQ-4.6',
            'REQ-5.1',
            'REQ-5.2',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.8',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-5.11',
            'REQ-5.12',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-5.15',
            'REQ-5.16',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-5.19',
            'REQ-5.20',
            'REQ-6.1',
            'REQ-6.2',
            'REQ-6.3',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.7',
            'REQ-6.8',
            'REQ-6.9',
            'REQ-6.10',
            'REQ-6.11',
            'REQ-7',
            'REQ-8',
            'REQ-9',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r67-final-role-journey-requirement-acceptance-candidate.ps1',
            'docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-result.json',
            'docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r67-final-role-journey-requirement-acceptance-candidate/final-role-journey-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'O1/O2/O3 Operations Logs Release Maintenance|REC-P0-066'
        TaskId = 'REC-P0-066'
        FrcId = 'O1/O2/O3'
        RequiredCoverageRows = @(
            'REQ-4.6',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-7',
            'REQ-8',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r66-operations-logs-release-maintenance-first-use.ps1',
            'docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-result.json',
            'docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r66-operations-logs-release-maintenance-first-use/operations-logs-release-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'E1/AI2 OpenAPI Assistant External-Service|REC-P0-065'
        TaskId = 'REC-P0-065'
        FrcId = 'E1/AI2'
        RequiredCoverageRows = @(
            'REQ-5.15',
            'REQ-5.19',
            'REQ-9',
            'REQ-14.1-14.37'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r65-openapi-assistant-external-service-first-use.ps1',
            'docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-result.json',
            'docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r65-openapi-assistant-external-service-first-use/openapi-assistant-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'C4/B4/B5 Workflow Todo Message|REC-P0-064'
        TaskId = 'REC-P0-064'
        FrcId = 'C4/B4/B5'
        RequiredCoverageRows = @(
            'REQ-4.5',
            'REQ-5.12',
            'REQ-5.16',
            'REQ-5.19',
            'REQ-6.9',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r64-workflow-todo-message-first-use.ps1',
            'docs/evidence/recovery/r64-workflow-todo-message-first-use-result.json',
            'docs/evidence/recovery/r64-workflow-todo-message-first-use-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r64-workflow-todo-message-first-use/workflow-todo-message-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'B1/B2 Configured Runtime|REC-P0-063'
        TaskId = 'REC-P0-063'
        FrcId = 'B1/B2'
        RequiredCoverageRows = @(
            'REQ-4.4',
            'REQ-5.11',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-5.16',
            'REQ-5.20',
            'REQ-6.2',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.11'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r63-configured-runtime-first-use.ps1',
            'docs/evidence/recovery/r63-configured-runtime-first-use-result.json',
            'docs/evidence/recovery/r63-configured-runtime-first-use-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r63-configured-runtime-first-use/configured-runtime-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-2 No-Code|REC-P0-053'
        TaskId = 'REC-P0-053'
        FrcId = 'FRC-2'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-6.7',
            'REQ-6.10'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r53-frc2-no-code-configuration-coherence.ps1',
            'docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json',
            'docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r53-frc2-no-code-configuration/no-code-configuration-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-1|REC-P0-052'
        TaskId = 'REC-P0-052'
        FrcId = 'FRC-1'
        RequiredCoverageRows = @(
            'REQ-5.8',
            'REQ-6.1',
            'REQ-6.3',
            'REQ-6.8',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r52-frc1-deployed-surface-closure.ps1',
            'docs/evidence/recovery/r52-frc1-deployed-surface-closure-result.json',
            'docs/evidence/recovery/r52-frc1-deployed-surface-closure-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r52-frc1-deployed-surface/frc1-deployed-surface-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-2C|REC-P0-045'
        TaskId = 'REC-P0-045'
        FrcId = 'FRC-2C'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.3.1',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.9',
            'REQ-6.7'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r45-field-dict-menu-smoke.ps1',
            'docs/evidence/recovery/r45-field-dict-menu-result.json',
            'docs/evidence/recovery/r45-field-dict-menu-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r45-field-dict-menu/field-dict-menu-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-1F|REC-P0-046'
        TaskId = 'REC-P0-046'
        FrcId = 'FRC-1F'
        RequiredCoverageRows = @(
            'REQ-5.8',
            'REQ-6.1',
            'REQ-6.3',
            'REQ-6.8',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r46-page-surface-smoke.ps1',
            'docs/evidence/recovery/r46-page-surface-result.json',
            'docs/evidence/recovery/r46-page-surface-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r46-page-surface/page-surface-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-3 Runtime|REC-P0-054'
        TaskId = 'REC-P0-054'
        FrcId = 'FRC-3'
        RequiredCoverageRows = @(
            'REQ-4.4',
            'REQ-5.11',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-5.16',
            'REQ-5.20',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.11'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r54-frc3-runtime-user-depth.ps1',
            'docs/evidence/recovery/r54-frc3-runtime-user-depth-result.json',
            'docs/evidence/recovery/r54-frc3-runtime-user-depth-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r54-frc3-runtime-user-depth/runtime-user-depth-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-3B|REC-P0-047'
        TaskId = 'REC-P0-047'
        FrcId = 'FRC-3B'
        RequiredCoverageRows = @(
            'REQ-4.4',
            'REQ-5.11',
            'REQ-5.13',
            'REQ-5.14',
            'REQ-6.4',
            'REQ-6.5',
            'REQ-6.6',
            'REQ-6.11'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r47-runtime-daily-use-smoke.ps1',
            'docs/evidence/recovery/r47-runtime-daily-use-result.json',
            'docs/evidence/recovery/r47-runtime-daily-use-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r47-runtime-daily-use/runtime-daily-use-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-4 Workflow|REC-P0-055'
        TaskId = 'REC-P0-055'
        FrcId = 'FRC-4'
        RequiredCoverageRows = @(
            'REQ-4.5',
            'REQ-5.12',
            'REQ-5.15',
            'REQ-5.19'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1',
            'docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-result.json',
            'docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-5 Operations|REC-P0-056'
        TaskId = 'REC-P0-056'
        FrcId = 'FRC-5'
        RequiredCoverageRows = @(
            'REQ-4.6',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-7',
            'REQ-8',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r56-frc5-operations-robustness-delivery.ps1',
            'docs/evidence/recovery/r56-frc5-operations-robustness-delivery-result.json',
            'docs/evidence/recovery/r56-frc5-operations-robustness-delivery-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r56-frc5-operations-robustness-delivery/operations-robustness-delivery-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-2/FRC-6 Admin Configuration|REC-P0-059'
        TaskId = 'REC-P0-059'
        FrcId = 'FRC-2/FRC-6'
        RequiredCoverageRows = @(
            'REQ-4.3',
            'REQ-5.3',
            'REQ-5.3.1',
            'REQ-5.4',
            'REQ-5.5',
            'REQ-5.6',
            'REQ-5.7',
            'REQ-5.9',
            'REQ-5.10',
            'REQ-6.2',
            'REQ-6.7',
            'REQ-6.10'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r59-frc2-frc6-admin-configuration-human-first-use.ps1',
            'docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-result.json',
            'docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-2026-07-02.md',
            'docs/evidence/recovery/screenshots/r59-frc2-frc6-admin-configuration-human-first-use/admin-configuration-human-first-use-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-6 Human Acceptance|REC-P0-057'
        TaskId = 'REC-P0-057'
        FrcId = 'FRC-6'
        RequiredCoverageRows = @(
            'REQ-2.1',
            'REQ-4.1',
            'REQ-4.2',
            'REQ-6.2'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r57-frc6-human-acceptance-pass.ps1',
            'docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json',
            'docs/evidence/recovery/r57-frc6-human-acceptance-pass-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r57-frc6-human-acceptance-pass/human-acceptance-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-4B|REC-P0-048'
        TaskId = 'REC-P0-048'
        FrcId = 'FRC-4B'
        RequiredCoverageRows = @(
            'REQ-4.5',
            'REQ-5.12',
            'REQ-5.16',
            'REQ-5.19',
            'REQ-6.9',
            'REQ-9'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r48-workflow-message-flow-smoke.ps1',
            'docs/evidence/recovery/r48-workflow-message-flow-result.json',
            'docs/evidence/recovery/r48-workflow-message-flow-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r48-workflow-message-flow/workflow-message-flow-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-4C|REC-P0-049'
        TaskId = 'REC-P0-049'
        FrcId = 'FRC-4C'
        RequiredCoverageRows = @(
            'REQ-5.15',
            'REQ-5.19',
            'REQ-9',
            'REQ-14.1-14.37'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r49-openapi-assistant-integration-smoke.ps1',
            'docs/evidence/recovery/r49-openapi-assistant-integration-result.json',
            'docs/evidence/recovery/r49-openapi-assistant-integration-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r49-openapi-assistant-integration/openapi-assistant-integration-browser-audit.json'
        )
    },
    [ordered]@{
        Pattern = 'FRC-5B|REC-P0-050'
        TaskId = 'REC-P0-050'
        FrcId = 'FRC-5B'
        RequiredCoverageRows = @(
            'REQ-4.6',
            'REQ-5.17',
            'REQ-5.18',
            'REQ-7',
            'REQ-8',
            'REQ-10',
            'REQ-14.1-14.37',
            'REQ-A'
        )
        RequiredRows = @(
            'Requirement source',
            'Target role',
            'Entry point',
            'User job',
            'Data contract',
            'Permission contract',
            'State contract',
            'Copy contract',
            'Acceptance assertions',
            'Screenshot evidence boundary'
        )
        RequiredEvidence = @(
            'scripts/recovery-r50-operations-release-log-smoke.ps1',
            'docs/evidence/recovery/r50-operations-release-log-result.json',
            'docs/evidence/recovery/r50-operations-release-log-2026-07-01.md',
            'docs/evidence/recovery/screenshots/r50-operations-release-log/operations-release-log-browser-audit.json'
        )
    }
)

$activeTaskContracts = [System.Collections.Generic.List[object]]::new()
foreach ($nextTask in $nextTasks) {
    $nextTaskText = [string]$nextTask
    $mapping = $null
    foreach ($candidate in $nextTaskContractMappings) {
        if ($nextTaskText -match $candidate.Pattern) {
            $mapping = $candidate
            break
        }
    }

    if ($null -eq $mapping) {
        $isNonFrcTask = -not ($nextTaskText -match '\bFRC-\d')
        Add-Check $checks "active next task has requirement-contract mapping: $nextTaskText" $isNonFrcTask 'non-FRC task or unmapped task; add a mapping when it becomes executable' 'WARN'
        continue
    }

    $section = Get-MarkdownH2SectionByPrefix -Text $taskText -HeadingPrefix $mapping.TaskId
    $sectionExists = -not [string]::IsNullOrWhiteSpace($section)
    Add-Check $checks "active task card exists for $($mapping.TaskId)" $sectionExists "nextTask=$nextTaskText"

    if (-not $sectionExists) {
        continue
    }

    $missingRows = @()
    foreach ($row in $mapping.RequiredRows) {
        if (-not (Test-ContractRow -Section $section -Label $row)) {
            $missingRows += $row
        }
    }
    Add-Check $checks "active task card requirement contract is complete for $($mapping.TaskId)" ($missingRows.Count -eq 0) "missingRows=$($missingRows -join ', ')"

    $hasVisualOnlyBoundary = $section -match 'Screenshots prove' `
        -and $section -match 'do not prove' `
        -and $section -match 'API/readback assertions'
    Add-Check $checks "active task card screenshot boundary is visual-only for $($mapping.TaskId)" $hasVisualOnlyBoundary 'screenshot boundary must reject functional completion without assertions'

    $missingEvidence = @()
    foreach ($evidencePath in $mapping.RequiredEvidence) {
        if ($section -notmatch [regex]::Escape($evidencePath)) {
            $missingEvidence += $evidencePath
        }
    }
    Add-Check $checks "active task card evidence paths are planned for $($mapping.TaskId)" ($missingEvidence.Count -eq 0) "missingEvidence=$($missingEvidence -join ', ')"

    if ($mapping.FrcId -like '*FRC-6*') {
        $v7TaskFields = @(
            'V7 Human-Usable Gate',
            'primary_role_journey',
            'real_entry_sequence',
            'one_primary_task_surface',
            'role_shell_separation',
            'page_stacking_risk',
            'ambiguous_copy_or_tip_risk',
            'data_readback_after_action',
            'permission_positive_and_negative',
            'reload_relogin_restart_requirement',
            'user_signoff_boundary'
        )
        $missingV7TaskFields = @()
        foreach ($field in $v7TaskFields) {
            if ($section -notmatch [regex]::Escape($field)) {
                $missingV7TaskFields += $field
            }
        }
        Add-Check $checks "active FRC-6 task card has v7 human-usable fields for $($mapping.TaskId)" ($missingV7TaskFields.Count -eq 0) "missingV7TaskFields=$($missingV7TaskFields -join ', ')"
    }

    $missingCoverageRows = @()
    $closedCoverageRows = @()
    $emptyCoverageGaps = @()
    $missingTaskCoverageRefs = @()
    $missingNextLedgerRefs = @()
    $missingGapReportRefs = @()
    foreach ($coverageRowId in $mapping.RequiredCoverageRows) {
        $coverageRowMatches = @($rows | Where-Object { $_.id -eq $coverageRowId })
        if ($coverageRowMatches.Count -eq 0) {
            $missingCoverageRows += $coverageRowId
        } else {
            $coverageRow = $coverageRowMatches[0]
            if ($coverageRow.status -notin @('OPEN', 'PARTIAL')) {
                $closedCoverageRows += "$coverageRowId=$($coverageRow.status)"
            }
            if ([string]::IsNullOrWhiteSpace([string]$coverageRow.gap)) {
                $emptyCoverageGaps += $coverageRowId
            }
        }

        if ($section -notmatch [regex]::Escape($coverageRowId)) {
            $missingTaskCoverageRefs += $coverageRowId
        }

        $nextLedgerPattern = [regex]::Escape($mapping.FrcId) + '[^\r\n]*' + [regex]::Escape($coverageRowId)
        if ($nextText -notmatch $nextLedgerPattern) {
            $missingNextLedgerRefs += $coverageRowId
        }

        if ($gapReportText -notmatch [regex]::Escape($coverageRowId)) {
            $missingGapReportRefs += $coverageRowId
        }
    }
    Add-Check $checks "active task coverage rows exist in coverage ledger for $($mapping.TaskId)" ($missingCoverageRows.Count -eq 0) "missingCoverageRows=$($missingCoverageRows -join ', ')"
    Add-Check $checks "active task coverage rows remain unfinished for $($mapping.TaskId)" ($closedCoverageRows.Count -eq 0) "closedCoverageRows=$($closedCoverageRows -join ', ')"
    Add-Check $checks "active task coverage rows keep concrete gaps for $($mapping.TaskId)" ($emptyCoverageGaps.Count -eq 0) "emptyCoverageGaps=$($emptyCoverageGaps -join ', ')"
    Add-Check $checks "active task card references every required coverage row for $($mapping.TaskId)" ($missingTaskCoverageRefs.Count -eq 0) "missingTaskCoverageRefs=$($missingTaskCoverageRefs -join ', ')"
    Add-Check $checks "next execution ledger row references every active coverage row for $($mapping.TaskId)" ($missingNextLedgerRefs.Count -eq 0) "missingNextLedgerRefs=$($missingNextLedgerRefs -join ', ')"
    Add-Check $checks "gap report exposes every active coverage row for $($mapping.TaskId)" ($missingGapReportRefs.Count -eq 0) "missingGapReportRefs=$($missingGapReportRefs -join ', ')"

    $activeTaskContracts.Add([ordered]@{
        nextTask = $nextTaskText
        taskId = $mapping.TaskId
        frcId = $mapping.FrcId
        requiredCoverageRows = $mapping.RequiredCoverageRows
        sectionFound = $sectionExists
        missingContractRows = $missingRows
        missingEvidencePaths = $missingEvidence
        missingCoverageRows = $missingCoverageRows
        closedCoverageRows = $closedCoverageRows
        emptyCoverageGaps = $emptyCoverageGaps
        missingTaskCoverageRefs = $missingTaskCoverageRefs
        missingNextLedgerRefs = $missingNextLedgerRefs
        missingGapReportRefs = $missingGapReportRefs
        screenshotBoundaryVisualOnly = $hasVisualOnlyBoundary
    }) | Out-Null
}

$badCompletionTerms = @()
foreach ($line in ($nextText -split "`r?`n")) {
    if ($line -match '\b(final|complete|done)\b' -and $line -notmatch 'not complete|cannot|Completion Rule|final requirement|final product|final goal|final user|final orchestration|final-system-flow-blueprint|Future reusable framework extraction|this product is complete|Final usable-system audit|Final Role Journey|final candidate refresh|final journey evidence consistency|Framework v\d|static usability audit|remains engineering evidence|user signoff (is|are) open') {
        $badCompletionTerms += $line.Trim()
    }
}
Add-Check $checks 'next ledger does not imply completion from engineering evidence' ($badCompletionTerms.Count -eq 0) (($badCompletionTerms | Select-Object -First 5) -join ' | ') 'WARN'

$errors = @($checks | Where-Object { -not $_.passed -and $_.severity -eq 'ERROR' })
$warnings = @($checks | Where-Object { -not $_.passed -and $_.severity -eq 'WARN' })
$status = if ($errors.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    generatedAt = (Get-Date).ToString('o')
    coverageLedgerPath = $CoverageLedgerPath
    gapReportPath = $GapReportPath
    nextExecutionLedgerPath = $NextExecutionLedgerPath
    taskCardsPath = $TaskCardsPath
    fixBatchesPath = $FixBatchesPath
    sessionStatePath = $SessionStatePath
    frameworkPath = $FrameworkPath
    workflowPath = $WorkflowPath
    taskTemplatePath = $TaskTemplatePath
    coverage = [ordered]@{
        totalRows = $rows.Count
        open = $openRows.Count
        partial = $partialRows.Count
        closed = $closedRows.Count
        notClosed = $notClosedRows.Count
    }
    nextTasks = $nextTasks
    activeTaskContracts = $activeTaskContracts
    errors = $errors
    warnings = $warnings
    checks = $checks
}

$resultDir = Split-Path -Parent $ResultPath
if ($resultDir) {
    New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
}
$result | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath

$summary = @(
    '# Final Goal Framework Audit',
    '',
    "- Status: $status",
    "- Generated at: $($result.generatedAt)",
    "- Requirement rows: $($rows.Count)",
    "- Open rows: $($openRows.Count)",
    "- Partial rows: $($partialRows.Count)",
    "- Closed rows: $($closedRows.Count)",
    "- Next tasks: $($nextTasks -join ', ')",
    "- Active task contracts checked: $($activeTaskContracts.Count)",
    "- Errors: $($errors.Count)",
    "- Warnings: $($warnings.Count)",
    '',
    'This audit checks the execution framework, not product completion. A PASS here only means the framework currently keeps unfinished requirements and next work visible.'
)
$summary | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath

$result | ConvertTo-Json -Depth 20
if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
