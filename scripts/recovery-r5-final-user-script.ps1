param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [int]$BackendPort = 9999,
    [int]$FrontendPort = 18131,
    [switch]$SkipPackage,
    [switch]$StopAfterRun
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ReleaseDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT'
$BackendDir = Join-Path $ReleaseDir 'backend'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r5-final-release-result.json'
$LogDir = Join-Path $RepoRoot 'docs\evidence\recovery\r5-logs'
$Steps = New-Object System.Collections.Generic.List[object]

function Limit-Text {
    param([string]$Value, [int]$MaxLength = 4000)
    if ([string]::IsNullOrEmpty($Value) -or $Value.Length -le $MaxLength) {
        return $Value
    }
    return $Value.Substring($Value.Length - $MaxLength)
}

function Save-StepSnapshot {
    param([string]$Status = 'IN_PROGRESS')
    $payload = [ordered]@{
        status = $Status
        generatedAt = (Get-Date).ToString('o')
        baseUrl = $BaseUrl
        releaseDir = $ReleaseDir
        stepsPassed = @($Steps | Where-Object { $_.status -eq 'PASS' }).Count
        stepsFailed = @($Steps | Where-Object { $_.status -eq 'FAIL' }).Count
        stoppedAfterRun = [bool]$StopAfterRun
        steps = $Steps
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $ResultFile -Parent) | Out-Null
    $payload | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
}

function Add-StepResult {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )
    $script:Steps.Add([ordered]@{
        name = $Name
        status = $Status
        detail = (Limit-Text -Value $Detail)
        at = (Get-Date).ToString('o')
    }) | Out-Null
    Save-StepSnapshot
}

function Get-ListeningProcessIds {
    param([Parameter(Mandatory = $true)][int]$Port)
    try {
        @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        @()
    }
}

function Assert-PortFree {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listeners = @(Get-ListeningProcessIds -Port $Port)
    if ($listeners.Count -gt 0) {
        throw "Port $Port is still listening, pid=$($listeners -join ',')"
    }
}

function Stop-ReleaseFromStartResult {
    $startResult = Join-Path $BackendDir 'start-result.json'
    if (-not (Test-Path $startResult)) {
        Add-StepResult -Name 'stop-existing-release' -Status 'SKIPPED' -Detail 'start-result.json not found'
        return
    }
    $result = Get-Content -Raw $startResult | ConvertFrom-Json
    foreach ($pidValue in @($result.backendPid, $result.frontendPid)) {
        if ($pidValue) {
            Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 2
    Assert-PortFree -Port $BackendPort
    Assert-PortFree -Port $FrontendPort
    Add-StepResult -Name 'stop-existing-release' -Status 'PASS' -Detail "Stopped backendPid=$($result.backendPid), frontendPid=$($result.frontendPid)"
}

function Invoke-RecoveryScript {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [string[]]$Arguments = @(),
        [int]$TimeoutSeconds = 240
    )
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $safeName = $Name -replace '[^A-Za-z0-9_.-]', '_'
    $stdout = Join-Path $LogDir "$safeName.out.log"
    $stderr = Join-Path $LogDir "$safeName.err.log"
    Remove-Item -LiteralPath $stdout, $stderr -ErrorAction SilentlyContinue

    $argumentList = @('-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $process = Start-Process -FilePath 'powershell' -ArgumentList $argumentList -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        $detail = "Timed out after ${TimeoutSeconds}s. stdout=$stdout stderr=$stderr"
        Add-StepResult -Name $Name -Status 'FAIL' -Detail $detail
        throw "$Name failed: $detail"
    }

    $process.Refresh()
    $exitCode = if ($null -eq $process.ExitCode) { 0 } else { $process.ExitCode }
    $outText = if (Test-Path $stdout) { Get-Content -Raw $stdout } else { '' }
    $errText = if (Test-Path $stderr) { Get-Content -Raw $stderr } else { '' }
    $combinedText = $outText + "`n" + $errText
    if ($exitCode -ne 0) {
        $detail = "ExitCode=$exitCode. stdout=$stdout stderr=$stderr`n$(Limit-Text -Value $combinedText)"
        Add-StepResult -Name $Name -Status 'FAIL' -Detail $detail
        throw "$Name failed: $detail"
    }
    if ($combinedText -match '"status"\s*:\s*"FAIL"' `
        -or $combinedText -match 'Backend health check failed' `
        -or $combinedText -match 'Release verification failed' `
        -or $combinedText -match 'Command failed with exit code' `
        -or $combinedText -match 'Node .* exited with code' `
        -or $combinedText -match 'API failed:') {
        $detail = "ExitCode=$exitCode but failure output was detected. stdout=$stdout stderr=$stderr`n$(Limit-Text -Value $combinedText)"
        Add-StepResult -Name $Name -Status 'FAIL' -Detail $detail
        throw "$Name failed: $detail"
    }

    $detail = "ExitCode=0. stdout=$stdout stderr=$stderr`n$(Limit-Text -Value $outText)"
    Add-StepResult -Name $Name -Status 'PASS' -Detail $detail
    return $detail
}

function Write-FinalResult {
    param([string]$Status)
    Save-StepSnapshot -Status $Status
    Get-Content -Raw $ResultFile
}

try {
    Stop-ReleaseFromStartResult

    if (-not $SkipPackage) {
        Invoke-RecoveryScript -Name 'package-release' -ScriptPath (Join-Path $RepoRoot 'scripts\package-release.ps1') -TimeoutSeconds 300 | Out-Null
    } else {
        Add-StepResult -Name 'package-release' -Status 'SKIPPED' -Detail 'SkipPackage was set'
    }

    Invoke-RecoveryScript -Name 'r6-apply-schema' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r6-apply-schema.ps1') -TimeoutSeconds 120 | Out-Null
    Invoke-RecoveryScript -Name 'local-start-release' -ScriptPath (Join-Path $RepoRoot 'scripts\local-start-release.ps1') -TimeoutSeconds 120 | Out-Null
    Invoke-RecoveryScript -Name 'verify-release-initial' -ScriptPath (Join-Path $RepoRoot 'scripts\verify-release.ps1') -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend') -TimeoutSeconds 120 | Out-Null

    Invoke-RecoveryScript -Name 'r2-module-publish' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r2-module-publish-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r2-tenant-business-redraw' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r2-tenant-business-redraw-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r3-runtime-approval' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r3-runtime-approval-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r4-admin-breadth' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r4-admin-breadth-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r6-data-source' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r6-data-source-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r7-work-management' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r7-work-management-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r8-todo-message-center' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r8-todo-message-center-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r9-sso-no-member' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r9-sso-no-member-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r10-openapi-upload-import-export' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r10-openapi-upload-import-export-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r11-ai-agent-scope-confirmation' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r11-ai-agent-scope-confirmation-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r12-module-builder-usability' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r12-module-builder-usability-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r13-cross-shell-responsive' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r13-cross-shell-responsive-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r14-real-login-session' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r14-real-login-session-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r15-password-reset' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r15-password-reset-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r16-register-first-use' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r16-register-first-use-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r17-normal-member-real-login-runtime' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r17-normal-member-real-login-runtime-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r18-runtime-mobile-action-containment' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r18-runtime-mobile-action-containment-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r19-admin-aggregated-pagination' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r19-admin-aggregated-pagination-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 180 | Out-Null
    Invoke-RecoveryScript -Name 'r20-flow-canvas-designer' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r20-flow-canvas-designer-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r22-ops-maintenance' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r22-ops-maintenance-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null
    Invoke-RecoveryScript -Name 'r24-home-page-config' -ScriptPath (Join-Path $RepoRoot 'scripts\recovery-r24-home-page-config-smoke.ps1') -Arguments @('-BaseUrl', $BaseUrl) -TimeoutSeconds 240 | Out-Null

    Stop-ReleaseFromStartResult
    Invoke-RecoveryScript -Name 'local-restart-release' -ScriptPath (Join-Path $RepoRoot 'scripts\local-start-release.ps1') -TimeoutSeconds 120 | Out-Null
    Invoke-RecoveryScript -Name 'verify-release-after-restart' -ScriptPath (Join-Path $RepoRoot 'scripts\verify-release.ps1') -Arguments @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend') -TimeoutSeconds 120 | Out-Null

    if ($StopAfterRun) {
        Stop-ReleaseFromStartResult
        Add-StepResult -Name 'final-release-state' -Status 'PASS' -Detail 'Release stopped after final verification'
    } else {
        Add-StepResult -Name 'final-release-state' -Status 'PASS' -Detail 'Release left running for user verification'
    }

    Write-FinalResult -Status 'PASS'
} catch {
    Write-FinalResult -Status 'FAIL' | Out-Host
    throw
}
