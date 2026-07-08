param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r95-platform-workbench-system-entry-density'
$ResultPath = Join-Path $EvidenceDir 'r95-platform-workbench-system-entry-density-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r95-platform-workbench-system-entry-density-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'platform-workbench-system-entry-density-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r95-platform-workbench-system-entry-density-browser.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:DebugPort = $null

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

function Read-Text {
    param([string]$RelativePath)
    return [System.IO.File]::ReadAllText((Join-Path $RepoRoot $RelativePath), [System.Text.UTF8Encoding]::new($false))
}

function Read-JsonFile {
    param([string]$Path)
    $fullPath = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $RepoRoot $Path }
    if (-not (Test-Path -LiteralPath $fullPath)) { return $null }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $fullPath | ConvertFrom-Json)
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

$children = @()
$browserAudit = $null
$r93 = $null
$errorMessage = $null

try {
    $platform = Read-Text 'frontend/src/features/platform/platformShell.ts'
    $css = Read-Text 'frontend/src/styles.css'
    $ledger = Read-Text 'docs/framework/next-execution-ledger.md'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    $state = Read-JsonFile '.cursor/session/state.json'

    $entryMatch = [regex]::Match($platform, 'function createSystemSwitchPanel\(navigate: Navigate, targetSystemId\?: string\): HTMLElement \{[\s\S]*?(?=function loadPlatformTodoPanel)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    Add-Check 'static' 'system entry function is inspectable' $entryMatch.Success 'createSystemSwitchPanel block must be present.'
    if ($entryMatch.Success) {
        $entryBody = $entryMatch.Value
        Add-Check 'static' 'system entry uses compact-list mode' ($entryBody -like "*platformSystemEntryMode: 'compact-list'*" -and $entryBody -like "*platformSystemEntryCardCount: '0'*") 'workbench panel must expose compact-list markers and zero card marker.'
        Add-Check 'static' 'system entry renders rows and search' ($entryBody -like '*platformSystemEntryRow*' -and $entryBody -like '*platformSystemEntrySearch*' -and $entryBody -like '*platformSystemEntryList*') 'entry must render a searchable row list.'
        Add-Check 'static' 'system entry does not render legacy card pile' ($entryBody -notlike '*createSystemCard*' -and $entryBody -notlike '*platformSystemCard*' -and $entryBody -notlike '*system-list*') 'workbench entry block must not render legacy system-list/system-card pile.'
    }
    Add-Check 'static' 'legacy system card function removed from platform shell' ($platform -notlike '*function createSystemCard*' -and $platform -notlike '*platformSystemCard*') 'platform shell should not retain the old system card entry implementation.'
    Add-Check 'static' 'platform apps and flow remain separated' ($platform -like '*platformAppsSeparatedFromSystemEntry*' -and $platform -like '*platformFlowSeparatedFromApplication*') 'R95 must preserve R93 module boundaries.'
    Add-Check 'style' 'compact system-entry CSS exists' ($css -like '*.platform-system-entry-panel*' -and $css -like '*.system-entry-row*' -and $css -like '*.system-entry-search*') 'styles must cover the compact entry panel, rows, and search input.'
    Add-Check 'style' 'compact system-entry CSS has responsive path' ($css -like '*@media (max-width: 980px)*' -and $css -like '*@media (max-width: 640px)*' -and $css -like '*.system-entry-row*grid-template-columns: minmax(0, 1fr)*') 'mobile rules must collapse entry rows without horizontal scrolling.'
    Add-Check 'process' 'R95 task is active in ledger/task cards' ($ledger -like '*REC-P0-095 Platform Workbench System Entry Density And List Rewrite Closure*' -and $taskCards -like '*REC-P0-095 Platform Workbench System Entry Density And List Rewrite Closure*') 'R95 must be tracked through the .cursor recovery ledger and task card.'
    Add-Check 'signoff-boundary' 'user signoff remains false' ($state -and $state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"

    $health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)

    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
    Add-Check 'release' 'verify-release child process completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $children += Invoke-ChildScript 'recovery-r93-platform-flow-app-ia-boundary' 'scripts/recovery-r93-platform-flow-app-ia-boundary.ps1' @('-BaseUrl', $BaseUrl, '-NoFailExit')
    $r93 = Read-JsonFile 'docs/evidence/recovery/r93-platform-flow-app-ia-boundary-result.json'
    Add-Check 'r93-regression' 'R93 platform Flow/Application IA boundary still passes' ($r93 -and $r93.status -eq 'PASS' -and $r93.userSignoff -eq $false) "status=$($r93.status), userSignoff=$($r93.userSignoff)"

    Start-BrowserAudit
    $env:R95_BASE_URL = $BaseUrl
    $env:R95_CDP_PORT = [string]$script:DebugPort
    $env:R95_EVIDENCE_DIR = $WorkDir
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    Remove-Item Env:R95_BASE_URL, Env:R95_CDP_PORT, Env:R95_EVIDENCE_DIR -ErrorAction SilentlyContinue
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R95 browser audit process completed' ($browserExit -eq 0 -and $browserAudit) "exitCode=$browserExit, log=$nodeLog"
    Add-Check 'browser' 'workbench compact-list and module boundary audit passes' ($browserAudit -and $browserAudit.status -eq 'PASS' -and $browserAudit.workbenchPassed -eq $true -and $browserAudit.boundaryPassed -eq $true) "status=$($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxButtons=$($browserAudit.maxMainButtonsOnWorkbench), maxOverflow=$($browserAudit.maxOverflowX)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'fatal' 'R95 script completed without fatal exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
    Remove-Item Env:R95_BASE_URL, Env:R95_CDP_PORT, Env:R95_EVIDENCE_DIR -ErrorAction SilentlyContinue
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = 'R95_PLATFORM_WORKBENCH_SYSTEM_ENTRY_DENSITY_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-095'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    r93 = if ($r93) { [ordered]@{ status = $r93.status; userSignoff = $r93.userSignoff } } else { $null }
    browserAudit = if ($browserAudit) { [ordered]@{ status = $browserAudit.status; resultCount = $browserAudit.resultCount; blockerResultCount = $browserAudit.blockerResultCount; workbenchPassed = $browserAudit.workbenchPassed; boundaryPassed = $browserAudit.boundaryPassed; maxMainButtonsOnWorkbench = $browserAudit.maxMainButtonsOnWorkbench; maxOverflowX = $browserAudit.maxOverflowX; path = 'docs/evidence/recovery/screenshots/r95-platform-workbench-system-entry-density/platform-workbench-system-entry-density-browser-audit.json' } } else { $null }
    nextRecommendedTask = if ($status -eq 'PASS') { [ordered]@{ taskId = 'REC-P0-096'; title = 'Final Remaining Partial Coverage Or User Trial Readiness Selection'; reason = 'R95 closed the platform workbench system-entry density blocker; remaining work should select the next notClosed final requirement row or prepare user trial signoff without marking it complete.' } } else { [ordered]@{ taskId = 'REC-P0-095'; title = 'Platform Workbench System Entry Density Residual Closure'; reason = ($script:Failures | Select-Object -First 3) -join ' | ' } }
    error = $errorMessage
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @(
    '# R95 Platform Workbench System Entry Density',
    '',
    "- Status: $status",
    "- BaseUrl: $BaseUrl",
    '- User signoff: false',
    "- Browser blockers: $($result.browserAudit.blockerResultCount)",
    "- Workbench/boundary pass: $($result.browserAudit.workbenchPassed) / $($result.browserAudit.boundaryPassed)",
    "- Max workbench main buttons / overflow: $($result.browserAudit.maxMainButtonsOnWorkbench) / $($result.browserAudit.maxOverflowX)",
    "- R93 regression: $($result.r93.status)",
    "- Next recommended task: $($result.nextRecommendedTask.taskId) $($result.nextRecommendedTask.title)",
    '',
    'This is engineering evidence only. It keeps final user acceptance open.',
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
$summary += '- Result: `docs/evidence/recovery/r95-platform-workbench-system-entry-density-result.json`'
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r95-platform-workbench-system-entry-density/platform-workbench-system-entry-density-browser-audit.json`'
$summary += '- Screenshots/logs: `docs/evidence/recovery/screenshots/r95-platform-workbench-system-entry-density/`'
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 100)
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }
