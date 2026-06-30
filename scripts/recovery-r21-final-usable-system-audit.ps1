param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery'
$ResultFile = Join-Path $EvidenceDir 'r21-final-usable-system-audit-result.json'
$SummaryFile = Join-Path $EvidenceDir 'r21-final-usable-system-audit-2026-06-29.md'

function Test-File {
    param([string]$RelativePath)
    Test-Path -LiteralPath (Join-Path $RepoRoot $RelativePath)
}

function Read-JsonFile {
    param([string]$RelativePath)
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        return [ordered]@{ ok = $false; reason = 'missing'; path = $RelativePath }
    }
    try {
        $raw = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        if ($raw -match "`0") {
            return [ordered]@{ ok = $false; reason = 'contains NUL bytes'; path = $RelativePath }
        }
        $json = $raw | ConvertFrom-Json
        return [ordered]@{ ok = $true; value = $json; path = $RelativePath }
    } catch {
        return [ordered]@{ ok = $false; reason = $_.Exception.Message; path = $RelativePath }
    }
}

function New-Gate {
    param(
        [string]$Id,
        [string]$Name,
        [string]$Outcome,
        [string[]]$RequiredFiles,
        [scriptblock]$ExtraCheck = $null
    )

    $missing = @()
    foreach ($file in $RequiredFiles) {
        if (-not (Test-File -RelativePath $file)) {
            $missing += $file
        }
    }

    $extra = if ($null -ne $ExtraCheck) { & $ExtraCheck } else { [ordered]@{ ok = $true; detail = 'no extra check' } }
    $status = if ($missing.Count -eq 0 -and $extra.ok) { 'PASS' } else { 'FAIL' }

    [ordered]@{
        id = $Id
        name = $Name
        status = $status
        outcome = $Outcome
        requiredFiles = $RequiredFiles
        missingFiles = $missing
        detail = $extra.detail
    }
}

$r5 = Read-JsonFile -RelativePath 'docs/evidence/recovery/r5-final-release-result.json'
$r20 = Read-JsonFile -RelativePath 'docs/evidence/recovery/r20-flow-canvas-designer-result.json'
$r22 = Read-JsonFile -RelativePath 'docs/evidence/recovery/r22-ops-maintenance-result.json'

$gates = @(
    New-Gate -Id 'J0' -Name 'Release health' -Outcome 'Release package builds, starts, verifies, restarts, and leaves a valid deployed frontend.' -RequiredFiles @(
        'scripts/recovery-r5-final-user-script.ps1',
        'scripts/verify-release.ps1',
        'docs/evidence/recovery/r5-final-release-result.json'
    ) -ExtraCheck {
        if (-not $r5.ok) {
            return [ordered]@{ ok = $false; detail = "R5 result invalid: $($r5.reason)" }
        }
        $ok = $r5.value.status -eq 'PASS' -and [int]$r5.value.stepsFailed -eq 0
        [ordered]@{ ok = $ok; detail = "R5 status=$($r5.value.status), stepsFailed=$($r5.value.stepsFailed)" }
    }
    New-Gate -Id 'J1' -Name 'Register first system' -Outcome 'New user registers with a system and enters initialization/system-admin flow.' -RequiredFiles @(
        'docs/evidence/recovery/r16-register-first-use-browser-closure-2026-06-29.md',
        'docs/evidence/recovery/r16-register-first-use-result.json'
    )
    New-Gate -Id 'J2' -Name 'Admin builds business app' -Outcome 'System admin configures and publishes a usable module plus flow canvas.' -RequiredFiles @(
        'docs/evidence/recovery/r2-module-publish-smoke-2026-06-27.md',
        'docs/evidence/recovery/r12-module-builder-usability-2026-06-29.md',
        'docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md',
        'docs/evidence/recovery/r20-flow-canvas-designer-result.json'
    ) -ExtraCheck {
        if (-not $r20.ok) {
            return [ordered]@{ ok = $false; detail = "R20 result invalid: $($r20.reason)" }
        }
        $ok = $r20.value.status -eq 'PASS' -and [int]$r20.value.canvasNodeCount -ge 2 -and [int]$r20.value.canvasEdgeCount -ge 1
        [ordered]@{ ok = $ok; detail = "R20 status=$($r20.value.status), nodes=$($r20.value.canvasNodeCount), edges=$($r20.value.canvasEdgeCount)" }
    }
    New-Gate -Id 'J3' -Name 'Normal user daily work' -Outcome 'Normal member uses runtime module through real login and mobile-safe UI, with no admin access.' -RequiredFiles @(
        'docs/evidence/recovery/r17-normal-member-real-login-runtime-2026-06-29.md',
        'docs/evidence/recovery/r18-runtime-mobile-action-containment-2026-06-29.md'
    )
    New-Gate -Id 'J4' -Name 'Approval and notification' -Outcome 'Approval produces todo/message and closes business/todo/message state.' -RequiredFiles @(
        'docs/evidence/recovery/r3-runtime-approval-smoke-2026-06-27.md',
        'docs/evidence/recovery/r8-todo-message-center-2026-06-27.md'
    )
    New-Gate -Id 'J5' -Name 'Admin configuration depth' -Outcome 'System admin breadth is API-backed, paged, responsive, and includes flow canvas.' -RequiredFiles @(
        'docs/evidence/recovery/r4-admin-breadth-2026-06-27.md',
        'docs/evidence/recovery/r6-data-source-2026-06-27.md',
        'docs/evidence/recovery/r9-sso-no-member-2026-06-27.md',
        'docs/evidence/recovery/r11-ai-agent-scope-confirmation-2026-06-29.md',
        'docs/evidence/recovery/r13-cross-shell-responsive-usability-2026-06-29.md',
        'docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md',
        'docs/evidence/recovery/r20-flow-canvas-designer-2026-06-29.md'
    )
    New-Gate -Id 'J6' -Name 'External and import/export' -Outcome 'External app, OpenAPI, upload, import, export, logs, and result files work.' -RequiredFiles @(
        'docs/evidence/recovery/r10-openapi-upload-import-export-2026-06-29.md'
    )
    New-Gate -Id 'J7' -Name 'Operations and maintenance' -Outcome 'Deploy/restart/health/logs are proven, while backup/rollback gaps are explicitly tracked.' -RequiredFiles @(
        'docs/recovery/final-usable-system-acceptance.md',
        'docs/recovery/current-product-audit.md',
        'docs/recovery/fix-batches.md',
        'docs/evidence/recovery/r22-ops-maintenance-result.json',
        'docs/evidence/recovery/r22-ops-maintenance-2026-06-29.md'
    ) -ExtraCheck {
        if (-not $r22.ok) {
            return [ordered]@{ ok = $false; detail = "R22 result invalid: $($r22.reason)" }
        }
        $ok = $r22.value.status -eq 'PASS' `
            -and $r22.value.releaseVerifyStatus -eq 'PASS' `
            -and $r22.value.rollbackSupported.restore -eq $true `
            -and $r22.value.rollbackSupported.deployment -eq $true `
            -and [int]$r22.value.browser.mobile.overflowX -eq 0
        [ordered]@{ ok = $ok; detail = "R22 status=$($r22.value.status), releaseVerify=$($r22.value.releaseVerifyStatus), backupTask=$($r22.value.backupTaskId), rollbackTask=$($r22.value.rollbackTaskId)" }
    }
)

$failed = @($gates | Where-Object { $_.status -ne 'PASS' })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }

$result = [ordered]@{
    status = $status
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    gateCount = $gates.Count
    failedGateCount = $failed.Count
    gates = $gates
}

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summaryLines = @(
    '# R21 Final Usable System Audit',
    '',
    "Status: $status",
    '',
    "Base URL: $BaseUrl",
    '',
    '## Gates',
    ''
)
foreach ($gate in $gates) {
    $summaryLines += "- $($gate.id) $($gate.name): $($gate.status) - $($gate.detail)"
}
$summaryLines += ''
$summaryLines += '## Result JSON'
$summaryLines += ''
$summaryLines += '- docs/evidence/recovery/r21-final-usable-system-audit-result.json'
$summaryLines -join [Environment]::NewLine | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 80

if ($status -ne 'PASS' -and -not $NoFailExit) {
    exit 1
}
