param(
    [string]$ResultPath = 'docs/evidence/recovery/r61-auth-entry-guard-result.json'
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
$appSource = Get-Content -Raw -LiteralPath 'frontend/src/app/app.ts'
$authSource = Get-Content -Raw -LiteralPath 'frontend/src/features/auth/authPages.ts'

Add-Check $checks 'app route guard detects public auth routes' ($appSource -match 'function isAuthRoute' -and $appSource -match '/register-with-system' -and $appSource -match '/forgot-password' -and $appSource -match '/login') 'A1/A2/A3 public auth routes are explicit'
Add-Check $checks 'no ready session redirects non-auth route to login' ($appSource -match 'hasReadySession' -and $appSource -match 'shellState\.account\.accountId' -and $appSource -match 'if \(!hasReadySession && !isPublicAuthRoute\)' -and $appSource -match "navigate\('/login'\)" -and $appSource -match "renderAuthPage\('/login'") 'platform/system/no-member routes cannot render before initialized account state'
Add-Check $checks 'authenticated users do not stay on auth pages' ($appSource -match 'if \(hasReadySession && isPublicAuthRoute\)' -and $appSource -match 'defaultAuthenticatedRoute') 'auth routes redirect to authenticated landing when token and account state are ready'
Add-Check $checks 'default authenticated route prefers current system when available' ($appSource -match 'shellState\.currentSystem\?\.systemId' -and $appSource -match '/platform') 'authenticated landing uses current system dashboard or platform'
Add-Check $checks 'registration lands in system admin first-use path' ($authSource -match 'registerWithSystem' -and $authSource -match 'navigate\(`/systems/\$\{result\.systemId\}/admin`\)') 'A2/C1 registration path enters system admin setup'

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
$json

if ($result.status -ne 'PASS') {
    exit 1
}
