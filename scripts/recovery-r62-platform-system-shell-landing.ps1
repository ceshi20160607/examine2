param(
    [string]$ResultPath = 'docs/evidence/recovery/r62-platform-system-shell-landing-result.json'
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
        stdoutTail = if ($stdout.Length -gt 1200) { $stdout.Substring($stdout.Length - 1200) } else { $stdout }
        stderrTail = if ($stderr.Length -gt 1200) { $stderr.Substring($stderr.Length - 1200) } else { $stderr }
    }
}

$checks = [System.Collections.Generic.List[object]]::new()
$platformSource = Get-Content -Raw -LiteralPath 'frontend/src/features/platform/platformShell.ts'
$systemSource = Get-Content -Raw -LiteralPath 'frontend/src/features/system-shell/systemShell.ts'
$appSource = Get-Content -Raw -LiteralPath 'frontend/src/app/app.ts'
$routesSource = Get-Content -Raw -LiteralPath 'frontend/src/app/routes.ts'

Add-Check $checks 'platform shell exposes required landing navigation' (
    $platformSource -match "createNavButton" `
    -and $platformSource -match "'/platform'" `
    -and $platformSource -match "'/platform/flow'" `
    -and $platformSource -match "'/platform/apps'" `
    -and $platformSource -match "createNavButton\('AI', '/platform/ai'" `
    -and $platformSource -match "'/platform/todos'" `
    -and $platformSource -match "'/platform/messages'"
) 'P1/P2 platform workspace has dashboard, flow, apps, AI, todos, messages, and profile entry'

Add-Check $checks 'platform AI page is a real routed capability surface' (
    $platformSource -match "route === '/platform/ai'" `
    -and $platformSource -match 'function createPlatformAiPage' `
    -and $platformSource -match "runPlatformHealthCheck\('AI'\)" `
    -and $platformSource -match 'platformAiPage' `
    -and $platformSource -match 'shellState\.account\.platformRoles' `
    -and $platformSource -match 'shellState\.availableSystems'
) 'PA1 platform AI entry uses live shell state and health check instead of a dead placeholder'

Add-Check $checks 'platform AI keeps platform/system authority boundary explicit' (
    $platformSource -match 'createPlatformAiPage' `
    -and $platformSource -match 'platformAiHealthPanel' `
    -and $platformSource -match 'canEnterPlatformAdmin' `
    -and $platformSource -match "navigate\('/platform/admin'\)"
) 'AI entry does not imply direct cross-system data bypass and config is platform-admin gated'

Add-Check $checks 'route registry includes visible platform and system shell entries' (
    $routesSource -match "/platform/flow" `
    -and $routesSource -match "/platform/apps" `
    -and $routesSource -match "/platform/ai" `
    -and $routesSource -match "/platform/todos" `
    -and $routesSource -match "/platform/messages" `
    -and $routesSource -match "/platform/profile" `
    -and $routesSource -match "/systems/:systemId/messages" `
    -and $routesSource -match "/systems/:systemId/profile"
) 'route registry no longer hides core landing-shell entries from framework audits'

Add-Check $checks 'platform admin route remains guarded' (
    $appSource -match "route === '/platform/admin' && !canEnterPlatformAdmin\(\)" `
    -and $platformSource -match "route === '/platform/admin' && canEnterPlatformAdmin\(\)" `
    -and $platformSource -match "navigate\('/platform/admin'\)"
) 'PA1 platform admin entry is visible only through role-aware shell and direct route guard'

Add-Check $checks 'business shells require initialized account state' (
    $appSource -match 'hasReadySession' `
    -and $appSource -match 'shellState\.account\.accountId' `
    -and $appSource -match "localStorage\.removeItem\('unexamine\.accessToken'\)" `
    -and $appSource -match "navigate\('/login'\)"
) 'non-auth routes cannot render platform/system shells with only a stale token and empty account state'

Add-Check $checks 'system shell keeps runtime/admin separation and switch context' (
    $systemSource -match "route\.endsWith\('/admin'\) && !canEnterSystemAdmin\(\)" `
    -and $systemSource -match 'createSystemAccessDenied' `
    -and $systemSource -match 'switchToSystem\(requestedSystemId' `
    -and $systemSource -match "navigate\('/platform'\)" `
    -and $systemSource -match 'createTenantSwitcher'
) 'S1/S2 system route guard, switch context, tenant switcher, and platform return remain explicit'

Add-Check $checks 'system shell exposes required role landing navigation' (
    $systemSource -match "/dashboard" `
    -and $systemSource -match "/work" `
    -and $systemSource -match "/todos" `
    -and $systemSource -match "/messages" `
    -and $systemSource -match "navigate\('/platform'\)" `
    -and $systemSource -match "/admin"
) 'system member/admin shells expose dashboard, work, todos, messages, switch, profile, and guarded admin'

$steps = @()
$frontendDir = Join-Path (Get-Location) 'frontend'
$steps += Invoke-Step -Name 'npm typecheck' -Command 'npm.cmd run typecheck' -WorkingDirectory $frontendDir
$steps += Invoke-Step -Name 'npm build' -Command 'npm.cmd run build' -WorkingDirectory $frontendDir

foreach ($step in $steps) {
    Add-Check $checks $step.name ($step.exitCode -eq 0) "exitCode=$($step.exitCode)"
}

$failed = @($checks | Where-Object { -not $_.passed })
$result = [ordered]@{
    status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    flowIds = @('P1','P2','PA1','S1','S2','B1','J1','J2','J3','J8','J9','J11')
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
$json

if ($result.status -ne 'PASS') {
    exit 1
}
