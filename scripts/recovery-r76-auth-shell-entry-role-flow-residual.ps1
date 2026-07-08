param(
    [string]$ResultPath = 'docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-result.json',
    [string]$SummaryPath = 'docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-2026-07-02.md'
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory = (Get-Location).Path
    )
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = 'powershell'
    $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($Command))
    $psi.Arguments = "-NoProfile -EncodedCommand $encodedCommand"
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [ordered]@{
        name = $Name
        exitCode = $process.ExitCode
        stdoutTail = if ($stdout.Length -gt 1800) { $stdout.Substring($stdout.Length - 1800) } else { $stdout }
        stderrTail = if ($stderr.Length -gt 1800) { $stderr.Substring($stderr.Length - 1800) } else { $stderr }
    }
}

function Read-Utf8File {
    param([string]$Path)
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path), [System.Text.UTF8Encoding]::new($false))
}

$checks = [System.Collections.Generic.List[object]]::new()
$appSource = Read-Utf8File 'frontend/src/app/app.ts'
$authSource = Read-Utf8File 'frontend/src/features/auth/authPages.ts'
$stateSource = Read-Utf8File 'frontend/src/app/state.ts'
$routesSource = Read-Utf8File 'frontend/src/app/routes.ts'
$platformSource = Read-Utf8File 'frontend/src/features/platform/platformShell.ts'
$systemShellSource = Read-Utf8File 'frontend/src/features/system-shell/systemShell.ts'
$authServiceSource = Read-Utf8File 'backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java'
$entryFiles = @(
    'frontend/src/app/app.ts',
    'frontend/src/app/routes.ts',
    'frontend/src/app/state.ts',
    'frontend/src/features/auth/authPages.ts',
    'frontend/src/features/platform/platformShell.ts'
)
$mojibakeTokens = @([char]0x93C3, [char]0x9427, [char]0x5A09, [char]0x9A9E, [char]0x701A, [char]0x9352, [char]0x7480, [char]0x9286, [char]0x9281, [char]0x95C6, [char]0xfffd)
$mojibakeHits = @()
foreach ($file in $entryFiles) {
    $text = Read-Utf8File $file
    if (($mojibakeTokens | Where-Object { $text.Contains([string]$_) } | Select-Object -First 1)) {
        $mojibakeHits += $file
    }
}
$guardCheck = ($appSource -match 'if \(!hasReadySession && !isPublicAuthRoute\)') -and ($appSource -match "navigate\('/login'\)") -and ($appSource -match "renderAuthPage\('/login'")
Add-Check -Checks $checks -Name 'auth guard blocks non-auth routes' -Passed $guardCheck -Detail 'A1/A4 route guard keeps non-auth routes behind login'

$platformLandingCheck = ($appSource -match 'shellState\.account\.platformRoles\.length > 0') -and ($appSource -match "return '/platform'")
Add-Check -Checks $checks -Name 'authenticated platform roles land platform first' -Passed $platformLandingCheck -Detail 'platform roles land on platform workbench before system routing'

$backendLandingCheck = ($authServiceSource -notmatch '/platform/dashboard') -and ($authServiceSource -match 'new DefaultLanding\("PLATFORM", null, null, "/platform"\)')
Add-Check -Checks $checks -Name 'backend platform landing is explicit' -Passed $backendLandingCheck -Detail 'backend no longer returns /platform/dashboard'

$frontendDashboardFallbackCheck = ($platformSource -match "route === '/platform/dashboard' \? '/platform' : route") -and ($authSource -match "route === '/platform/dashboard'")
Add-Check -Checks $checks -Name 'frontend normalizes stale platform dashboard' -Passed $frontendDashboardFallbackCheck -Detail 'older default landing is normalized without relying on shell fallback'

$registerCheck = ($authSource -match 'registerWithSystem') -and ($authSource -match 'navigate\(`/systems/\$\{result\.systemId\}/admin`\)')
Add-Check -Checks $checks -Name 'registration lands system admin' -Passed $registerCheck -Detail 'registration creates account/system and enters system admin setup'

$passwordResetCheck = ($authSource -match 'confirmPasswordReset') -and ($authSource -match '返回登录') -and ($authSource -match "navigate\('/login'\)")
Add-Check -Checks $checks -Name 'password recovery returns login' -Passed $passwordResetCheck -Detail 'A3 reset flow returns to login'

$noAutoSwitchCheck = ($stateSource -notmatch 'frontend bootstrap') -and ($stateSource -notmatch 'await switchToSystem\(firstSwitchable') -and ($stateSource -match 'shellState\.currentSystem = undefined')
Add-Check -Checks $checks -Name 'state init does not auto switch system' -Passed $noAutoSwitchCheck -Detail 'platform initialization loads switch options without creating SystemSwitchContext'

$platformRoutesCheck = ($routesSource -match '/platform/flow') -and ($routesSource -match '/platform/apps') -and ($routesSource -match '/platform/ai') -and ($routesSource -match '/platform/todos') -and ($routesSource -match '/platform/messages') -and ($routesSource -match '/platform/profile')
Add-Check -Checks $checks -Name 'platform workbench has required nav' -Passed $platformRoutesCheck -Detail 'platform routes include dashboard, flow, apps, AI, todos, messages, profile'

$platformAdminShellCheck = ($platformSource -match 'platformAdminStandalone') -and ($platformSource -match 'admin-shell-standalone') -and ($platformSource -match 'renderPlatformAdmin\(navigate\)')
Add-Check -Checks $checks -Name 'platform admin shell standalone' -Passed $platformAdminShellCheck -Detail 'platform admin route is no longer wrapped by platform workbench header'

$systemAdminShellCheck = ($systemShellSource -match 'systemAdminStandalone') -and ($systemShellSource -match 'system-admin-standalone') -and ($systemShellSource -match 'renderSystemAdmin\(navigate, adminRouteMatch\[1\]')
Add-Check -Checks $checks -Name 'system admin shell standalone' -Passed $systemAdminShellCheck -Detail 'system admin route is no longer wrapped by system runtime header'

Add-Check $checks 'entry shell visible copy has no obvious mojibake' ($mojibakeHits.Count -eq 0) "files=$($mojibakeHits -join ', ')"

$steps = @()
$frontendDir = Join-Path (Get-Location) 'frontend'
$steps += Invoke-Step -Name 'npm typecheck' -Command 'D:\dev\nodejs24\npm.cmd run typecheck' -WorkingDirectory $frontendDir
$steps += Invoke-Step -Name 'npm build' -Command 'D:\dev\nodejs24\npm.cmd run build' -WorkingDirectory $frontendDir
foreach ($step in $steps) {
    Add-Check $checks $step.name ($step.exitCode -eq 0) "exitCode=$($step.exitCode)"
}

$failed = @($checks | Where-Object { -not $_.passed })
$result = [ordered]@{
    status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    productStatus = 'R76_ACCEPTED_AS_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    task = 'REC-P0-076'
    flowIds = @('A1','A2','A3','A4','P1','S1','C1')
    userSignoff = $false
    checks = @($checks)
    childResults = $steps
}

$resultDir = Split-Path -Parent $ResultPath
if ($resultDir -and -not (Test-Path -LiteralPath $resultDir)) {
    New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
}
$json = $result | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $ResultPath), $json, [System.Text.UTF8Encoding]::new($false))

$summary = @(
    '# REC-P0-076 / R76 Auth Shell Entry Role Flow Residual',
    '',
    "- Status: $($result.status)",
    "- Product status: $($result.productStatus)",
    "- Generated at: $($result.generatedAt)",
    "- User signoff: false",
    '',
    'R76 closes an entry/shell residual discovered during the flow rebuild: platform logins now land on `/platform`, frontend initialization no longer auto-switches into the first system, platform/system admin routes render standalone admin shells, and auth/platform entry source has no obvious mojibake.',
    '',
    'This remains engineering evidence only. It does not close R75 operations residuals or final user signoff.'
)
[System.IO.File]::WriteAllLines((Join-Path (Get-Location) $SummaryPath), $summary, [System.Text.UTF8Encoding]::new($false))

$json
if ($result.status -ne 'PASS') {
    exit 1
}
