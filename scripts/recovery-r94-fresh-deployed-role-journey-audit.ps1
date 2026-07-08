param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r94-fresh-deployed-role-journey-audit'
$ResultPath = Join-Path $EvidenceDir 'r94-fresh-deployed-role-journey-audit-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r94-fresh-deployed-role-journey-audit-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'fresh-deployed-role-journey-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r94-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null

function Add-Check {
    param(
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail,
        [string]$Severity = 'ERROR'
    )
    $script:Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        severity = $Severity
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        $message = "${Area}/${Name}: $Detail"
        if ($Severity -eq 'WARN') { $script:Warnings.Add($message) | Out-Null }
        else { $script:Failures.Add($message) | Out-Null }
    }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try { $body = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    return $response.data
}

function Invoke-ChildScript {
    param([string]$Name, [string]$RelativePath, [string[]]$Arguments = @())
    $scriptPath = Join-Path $RepoRoot $RelativePath
    $logPath = Join-Path $WorkDir "$Name.log"
    $exitCode = 999
    $output = ''
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        $output = "Missing child script: $scriptPath"
        Set-Content -LiteralPath $logPath -Value $output -Encoding UTF8
        return [ordered]@{ name = $Name; script = $RelativePath; exitCode = $exitCode; logFile = $logPath; error = $output }
    }
    Push-Location $RepoRoot
    try {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $output = (& powershell @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath) $Arguments 2>&1 | Out-String)
            $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        } catch {
            $output = $_.Exception.ToString()
            $exitCode = 999
        } finally {
            $ErrorActionPreference = $previous
        }
    } finally {
        Pop-Location
    }
    Set-Content -LiteralPath $logPath -Value $output -Encoding UTF8
    return [ordered]@{ name = $Name; script = $RelativePath; args = $Arguments; exitCode = $exitCode; logFile = $logPath }
}

function Read-JsonFile {
    param([string]$Path)
    $fullPath = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $RepoRoot $Path }
    if (-not (Test-Path -LiteralPath $fullPath)) { return $null }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $fullPath | ConvertFrom-Json)
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Find-Chrome {
    $paths = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($candidate in $paths) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    throw 'Chrome or Edge executable was not found.'
}

function Start-BrowserAudit {
    $chromePath = Find-Chrome
    $script:DebugPort = Get-FreeTcpPort
    $script:ChromeProfileDir = Join-Path $WorkDir ('chrome-profile-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
    $script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
        '--headless',
        '--disable-gpu',
        '--disable-extensions',
        '--disable-software-rasterizer',
        '--no-sandbox',
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-background-networking',
        '--remote-allow-origins=*',
        "--remote-debugging-port=$script:DebugPort",
        "--user-data-dir=$script:ChromeProfileDir",
        'about:blank'
    ) -PassThru -WindowStyle Hidden
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:$script:DebugPort/json/version" -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function New-NextRecommendation {
    param([object]$BrowserAudit)
    $densityBlockers = @()
    if ($BrowserAudit -and $BrowserAudit.results) {
        $densityBlockers = @($BrowserAudit.results | Where-Object { $_.key -eq 'platform-workbench' -and ((@($_.blockers) -join ' ') -match 'system entry card pile too large|too many main buttons') })
    }
    if ($densityBlockers.Count -gt 0) {
        return [ordered]@{
            taskId = 'REC-P0-095'
            title = 'Platform Workbench System Entry Density And List Rewrite Closure'
            reason = 'R94 found the platform workbench system-entry area still behaves like a card pile when many systems are visible.'
            preferredStrategy = 'rewrite the workbench system-entry panel into a compact searchable list/table with clear current-system context instead of patching the old card pile'
        }
    }
    if ($BrowserAudit -and [int]$BrowserAudit.mojibakeResultCount -gt 0) {
        return [ordered]@{
            taskId = 'REC-P0-095'
            title = 'Visible Copy And Platform Shell Rewrite Boundary Closure'
            reason = 'R94 found visible mojibake/corrupted copy on current deployed platform role routes.'
            preferredStrategy = 'rewrite affected platform-shell/profile/todo/message/admin copy blocks where faster than patching legacy mojibake strings'
        }
    }
    if ($BrowserAudit -and $BrowserAudit.platformApplicationBoundaryPassed -ne $true) {
        return [ordered]@{
            taskId = 'REC-P0-095'
            title = 'Platform Application Boundary Residual Rewrite Closure'
            reason = 'R94 found the platform Application page boundary still leaking system-entry behavior.'
            preferredStrategy = 'delete/rewrite the local platform Application surface if that is faster than incremental repair'
        }
    }
    if ($script:Failures.Count -gt 0) {
        return [ordered]@{
            taskId = 'REC-P0-095'
            title = 'Fresh Deployed Role Journey Residual Closure'
            reason = ($script:Failures | Select-Object -First 3) -join ' | '
            preferredStrategy = 'choose rewrite or local repair according to the shortest route back to the role journey contract'
        }
    }
    return [ordered]@{
        taskId = 'REC-P0-095'
        title = 'User Trial Readiness And Remaining Partial Coverage Selection'
        reason = 'R94 did not find a blocking deployed role-route issue in the checked surfaces, but final user signoff and partial coverage remain open.'
        preferredStrategy = 'select the next unfinished requirement row or prepare explicit user verification without claiming signoff'
    }
}

$children = @()
$framework = $null
$static = $null
$coverage = $null
$r93 = $null
$r81 = $null
$r57 = $null
$browserAudit = $null
$errorMessage = $null

try {
    $health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)

    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend', '-NoFailExit')
    Add-Check 'release' 'verify-release child process completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $framework = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    Add-Check 'framework' 'final goal framework audit passes' ($framework -and $framework.status -eq 'PASS') "status=$($framework.status), warnings=$(@($framework.warnings).Count)"

    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1' @('-NoFailExit')
    $static = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    Add-Check 'static-usability' 'static usability audit has no blocker/warning findings' ($static -and $static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0 -and [int]$static.warningCount -eq 0) "status=$($static.status), blockers=$($static.blockerCount), warnings=$($static.warningCount)"

    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit')
    $coverage = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'coverage-boundary' 'coverage audit keeps remaining partial rows visible' ($coverage -and [int]$coverage.missingCount -eq 0 -and [int]$coverage.notClosedCount -gt 0) "status=$($coverage.status), missing=$($coverage.missingCount), notClosed=$($coverage.notClosedCount)"

    $children += Invoke-ChildScript 'recovery-r93-platform-flow-app-ia-boundary' 'scripts/recovery-r93-platform-flow-app-ia-boundary.ps1' @('-BaseUrl', $BaseUrl, '-NoFailExit')
    $r93 = Read-JsonFile 'docs/evidence/recovery/r93-platform-flow-app-ia-boundary-result.json'
    Add-Check 'r93-regression' 'R93 platform Flow/Application IA boundary still passes' ($r93 -and $r93.status -eq 'PASS' -and $r93.userSignoff -eq $false) "status=$($r93.status), userSignoff=$($r93.userSignoff)"

    $children += Invoke-ChildScript 'recovery-r81-trial-login-role-use-audit' 'scripts/recovery-r81-trial-login-role-use-audit.ps1' @('-BaseUrl', $BaseUrl, '-NoFailExit')
    $r81 = Read-JsonFile 'docs/evidence/recovery/r81-trial-login-role-use-audit-result.json'
    Add-Check 'role-journey-reference' 'R81 retained deployed login role-use audit is readable when retained data still exists' ($r81 -and $r81.status -eq 'PASS' -and $r81.userSignoff -eq $false) "status=$($r81.status), userSignoff=$($r81.userSignoff), browser=$($r81.browserAudit.status)" 'WARN'

    $children += Invoke-ChildScript 'recovery-r57-frc6-human-acceptance-pass' 'scripts/recovery-r57-frc6-human-acceptance-pass.ps1' @('-BaseUrl', $BaseUrl, '-NoFailExit')
    $r57 = Read-JsonFile 'docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json'
    Add-Check 'role-journey-reference' 'R57 legacy fresh human-usable role journey aggregation remains readable when its marker contract is current' ($r57 -and $r57.status -eq 'PASS' -and $r57.userSignoff -eq $false) "status=$($r57.status), userSignoff=$($r57.userSignoff), resultCount=$($r57.humanJourney.resultCount)" 'WARN'

    Start-BrowserAudit
    $env:R94_BASE_URL = $BaseUrl
    $env:R94_CDP_PORT = [string]$script:DebugPort
    $env:R94_EVIDENCE_DIR = $WorkDir
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    Remove-Item Env:R94_BASE_URL, Env:R94_CDP_PORT, Env:R94_EVIDENCE_DIR -ErrorAction SilentlyContinue
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R94 platform browser audit process completed' ($browserExit -eq 0 -and $browserAudit) "exitCode=$browserExit, log=$nodeLog"
    Add-Check 'browser' 'platform route browser audit has no blockers' ($browserAudit -and $browserAudit.status -eq 'PASS') "status=$($browserAudit.status), blockers=$($browserAudit.blockerResultCount), mojibakeResults=$($browserAudit.mojibakeResultCount), overflow=$($browserAudit.overflowCount)"
    Add-Check 'browser' 'platform Application remains separated from system entry' ($browserAudit -and $browserAudit.platformApplicationBoundaryPassed -eq $true) "platformApplicationBoundaryPassed=$($browserAudit.platformApplicationBoundaryPassed)"

    $state = Read-JsonFile '.cursor/session/state.json'
    Add-Check 'signoff-boundary' 'R94 keeps user signoff false' ($state -and $state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'fatal' 'R94 script completed without fatal exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
    Remove-Item Env:R94_BASE_URL, Env:R94_CDP_PORT, Env:R94_EVIDENCE_DIR -ErrorAction SilentlyContinue
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$nextRecommendation = New-NextRecommendation -BrowserAudit $browserAudit
$result = [ordered]@{
    status = $status
    productStatus = 'R94_DEPLOYED_ROLE_JOURNEY_AUDIT_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-094'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    rewriteAllowedForFollowUp = $true
    rewriteBoundary = 'If a route, shell, or module is structurally inconsistent with the target architecture, the follow-up task may delete and rewrite that local implementation when faster than preserving old code.'
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    framework = if ($framework) { [ordered]@{ status = $framework.status; warnings = @($framework.warnings).Count; coverage = $framework.coverage } } else { $null }
    staticUsability = if ($static) { [ordered]@{ status = $static.status; blockers = $static.blockerCount; warnings = $static.warningCount } } else { $null }
    coverage = if ($coverage) { [ordered]@{ status = $coverage.status; missing = $coverage.missingCount; notClosed = $coverage.notClosedCount } } else { $null }
    r93 = if ($r93) { [ordered]@{ status = $r93.status; userSignoff = $r93.userSignoff } } else { $null }
    r81 = if ($r81) { [ordered]@{ status = $r81.status; userSignoff = $r81.userSignoff; browserStatus = $r81.browserAudit.status; browserBlockers = $r81.browserAudit.blockerCount } } else { $null }
    r57 = if ($r57) { [ordered]@{ status = $r57.status; userSignoff = $r57.userSignoff; resultCount = $r57.humanJourney.resultCount; failures = $r57.humanJourney.failureCount; warnings = $r57.humanJourney.warningCount } } else { $null }
    browserAudit = if ($browserAudit) { [ordered]@{ status = $browserAudit.status; resultCount = $browserAudit.resultCount; blockerResultCount = $browserAudit.blockerResultCount; mojibakeResultCount = $browserAudit.mojibakeResultCount; overflowCount = $browserAudit.overflowCount; platformApplicationBoundaryPassed = $browserAudit.platformApplicationBoundaryPassed; path = 'docs/evidence/recovery/screenshots/r94-fresh-deployed-role-journey-audit/fresh-deployed-role-journey-browser-audit.json' } } else { $null }
    nextRecommendedTask = $nextRecommendation
    error = $errorMessage
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @(
    '# R94 Fresh Deployed Role Journey Audit After R92/R93',
    '',
    "- Status: $status",
    "- BaseUrl: $BaseUrl",
    "- User signoff: false",
    "- Rewrite follow-up allowed: true, only through a task card and deployed evidence",
    "- Coverage missing/notClosed: $($result.coverage.missing) / $($result.coverage.notClosed)",
    "- Browser blockers: $($result.browserAudit.blockerResultCount), mojibake route results: $($result.browserAudit.mojibakeResultCount), overflow: $($result.browserAudit.overflowCount)",
    "- R93/R81/R57: $($result.r93.status) / $($result.r81.status) / $($result.r57.status)",
    "- Next recommended task: $($nextRecommendation.taskId) $($nextRecommendation.title)",
    '',
    'This is engineering evidence only. It audits the current deployed release and keeps final user acceptance open.',
    '',
    '## Failures'
)
if ($script:Failures.Count -eq 0) {
    $summary += '- none'
} else {
    foreach ($failure in $script:Failures) { $summary += "- $failure" }
}
$summary += ''
$summary += '## Evidence'
$summary += '- Result: `docs/evidence/recovery/r94-fresh-deployed-role-journey-audit-result.json`'
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r94-fresh-deployed-role-journey-audit/fresh-deployed-role-journey-browser-audit.json`'
$summary += '- Child logs: `docs/evidence/recovery/screenshots/r94-fresh-deployed-role-journey-audit/`'
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 100)
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }



