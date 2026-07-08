param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r100-platform-flow-application-depth'
$ResultPath = Join-Path $EvidenceDir 'r100-platform-flow-application-depth-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r100-platform-flow-application-depth-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'platform-flow-application-depth-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r100-browser-audit.js'
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
    param([string]$Name, [string]$RelativePath, [string[]]$Arguments = @(), [int[]]$AllowedExitCodes = @(0))
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
    return [ordered]@{ name = $Name; script = $RelativePath; args = $Arguments; exitCode = $exitCode; logFile = $logPath; allowed = $AllowedExitCodes }
}

function Invoke-Api {
    param([string]$Method, [string]$Path)
    try {
        $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -TimeoutSec 45
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try { $body = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -and $response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    if ($response.data) { return $response.data }
    return $response
}

function Read-DeployedAssetText {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $matches = [regex]::Matches($index, '(?:src|href)="(?<src>/assets/[^""<>]+\.(?:js|css))"')
        $assets = @($matches | ForEach-Object { $_.Groups['src'].Value } | Select-Object -Unique)
        $texts = @()
        foreach ($asset in $assets) {
            if ($asset -like '*.js') {
                $texts += (Invoke-WebRequest -Uri "$BaseUrl$asset" -UseBasicParsing -TimeoutSec 30).Content
            }
        }
        return [ordered]@{ assets = $assets; text = ($texts -join "`n") }
    } catch {
        return [ordered]@{ assets = @(); text = ''; error = $_.Exception.Message }
    }
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
$errorMessage = $null

try {
    $platform = Read-Text 'frontend/src/features/platform/platformShell.ts'
    $css = Read-Text 'frontend/src/styles.css'
    $ledger = Read-Text 'docs/framework/next-execution-ledger.md'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    $state = Read-JsonFile '.cursor/session/state.json'
    $r99 = Read-JsonFile 'docs/evidence/recovery/r99-post-r98-user-feedback-intake-and-next-repair-selection-result.json'

    $flowMatch = [regex]::Match($platform, 'function createPlatformFlowPage\(navigate: Navigate\): HTMLElement \{[\s\S]*?(?=function createPlatformAiPage)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    $appsMatch = [regex]::Match($platform, 'function createPlatformAppsPage\(navigate: Navigate\): HTMLElement \{[\s\S]*?(?=function createPlatformFlowPage)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    Add-Check 'state' 'R100 is active in session state' ($state -and $state.build_plan.currentBatch -eq 'RECOVERY-R100' -and @($state.build_plan.nextTasks) -contains 'REC-P0-100 Platform Flow Application Workbench Depth And Action Clarity Closure' -and $state.gates.user_script_passed -eq $false) "currentBatch=$($state.build_plan.currentBatch), userSignoff=$($state.gates.user_script_passed)"
    Add-Check 'r99' 'R99 selection evidence is PASS' ($r99 -and $r99.status -eq 'PASS' -and $r99.selectedNextTask.taskId -eq 'REC-P0-100' -and $r99.userSignoff -eq $false) "status=$($r99.status), selected=$($r99.selectedNextTask.taskId)"
    Add-Check 'contract' 'R100 ledger and task card are present' ($ledger -like '*REC-P0-100 Platform Flow Application Workbench Depth And Action Clarity Closure*' -and $taskCards -like '*REC-P0-100 Platform Flow Application Workbench Depth And Action Clarity Closure*') 'ledger/task card must carry R100.'

    Add-Check 'source' 'Flow function is inspectable' $flowMatch.Success 'createPlatformFlowPage must be present.'
    if ($flowMatch.Success) {
        $flowBody = $flowMatch.Value
        Add-Check 'source' 'Flow workbench has rows/detail/run actions' ($flowBody -like '*platformFlowWorkbench*' -and $platform -like '*platformFlowRow*' -and $platform -like '*platformFlowDetailPanel*' -and $platform -like '*platformFlowRunBatch*' -and $platform -like '*platformFlowRetryAction*' -and $platform -like '*platformFlowCompensationAction*') 'Flow page must expose R100 stable markers.'
        Add-Check 'source' 'Flow page does not render apps or system entry' ($flowBody -notlike '*platformAppsPage*' -and $flowBody -notlike '*createSystemSwitchPanel*' -and $flowBody -notlike '*platformSystemCard*') 'Flow must remain independent.'
    }
    Add-Check 'source' 'Application function is inspectable' $appsMatch.Success 'createPlatformAppsPage must be present.'
    if ($appsMatch.Success) {
        $appsBody = $appsMatch.Value
        Add-Check 'source' 'Application authorization markers exist' ($appsBody -like '*platformAuthorizationWorkbench*' -and $platform -like '*platformAuthorizationRow*' -and $platform -like '*platformAuthorizationRequest*' -and $platform -like '*platformAuthorizationDetailPanel*' -and $platform -like '*authorizationChangeId*' -and $platform -like '*requestId*') 'Application page must expose authorization/request/change markers.'
        Add-Check 'source' 'Application page does not render system entry' ($appsBody -notlike '*createSystemSwitchPanel*' -and $appsBody -notlike '*platformSystemCard*') 'Application must not become system entry.'
    }
    Add-Check 'style' 'R100 platform workbench CSS exists' ($css -like '*platform-workbench-table-shell*' -and $css -like '*platform-workbench-detail*' -and $css -like '*platform-action-result*' -and $css -like '*R100 platform workbench responsive*') 'CSS must cover table/detail/result responsive layout.'

    $health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)

    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend', '-NoFailExit')
    Add-Check 'release' 'verify-release child completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $deployedAssets = Read-DeployedAssetText
    $deployedText = [string]$deployedAssets.text
    Add-Check 'deployed-asset' 'deployed frontend asset contains R100 Flow markers' ($deployedText -like '*platformFlowRow*' -and $deployedText -like '*platformFlowDetailPanel*' -and $deployedText -like '*platformFlowRetryAction*' -and $deployedText -like '*platformFlowCompensationAction*') "assets=$($deployedAssets.assets -join ',')"
    Add-Check 'deployed-asset' 'deployed frontend asset contains R100 Application markers' ($deployedText -like '*platformAuthorizationRow*' -and $deployedText -like '*platformAuthorizationRequest*' -and $deployedText -like '*platformAuthorizationDetailPanel*' -and $deployedText -like '*authorizationChangeId*' -and $deployedText -like '*requestId*') "assets=$($deployedAssets.assets -join ',')"

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $framework = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    Add-Check 'framework' 'framework audit passes with R100 active' ($framework -and $framework.status -eq 'PASS' -and $framework.activeTaskContracts[0].taskId -eq 'REC-P0-100') "status=$($framework.status)"

    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1'
    $static = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    Add-Check 'static' 'final usability static audit has no blockers' ($static -and $static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0) "status=$($static.status), blockers=$($static.blockerCount), warnings=$($static.warningCount)"

    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit') @(0)
    $coverage = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'coverage-boundary' 'coverage remains honest and open' ($coverage -and [int]$coverage.missingCount -eq 0 -and [int]$coverage.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false) "missing=$($coverage.missingCount), notClosed=$($coverage.notClosedCount), userSignoff=$($state.gates.user_script_passed)"

    Start-BrowserAudit
    $env:R100_BASE_URL = $BaseUrl
    $env:R100_CDP_PORT = [string]$script:DebugPort
    $env:R100_EVIDENCE_DIR = $WorkDir
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    Add-Check 'browser' 'R100 browser audit process completed' ($browserExit -eq 0) "exitCode=$browserExit, log=$nodeLog"
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'Flow/Application browser audit passed' ($browserAudit -and $browserAudit.status -eq 'PASS' -and [int]$browserAudit.blockerResultCount -eq 0 -and [int]$browserAudit.maxOverflowX -le 2) "status=$($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxOverflow=$($browserAudit.maxOverflowX)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'script' 'R100 script completed without unhandled exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = if ($status -eq 'PASS') { 'R100_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY' } else { 'R100_DEPLOYED_ENGINEERING_EVIDENCE_FAILED' }
    task = 'REC-P0-100'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    browserAudit = $browserAudit
    error = $errorMessage
    accepted = ($status -eq 'PASS')
}
$result | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @()
$summary += '# REC-P0-100 / R100 Platform Flow Application Workbench Depth'
$summary += ''
$summary += "Status: $status as deployed engineering evidence only."
$summary += ''
$summary += "- Base URL: $BaseUrl"
$summary += "- Browser audit: $($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxOverflow=$($browserAudit.maxOverflowX)"
$summary += "- User signoff: false"
$summary += ''
$summary += 'R100 proves the current deployed frontend exposes platform Flow rows/detail/run feedback/retry/compensation markers and platform Application authorization rows/request/change/detail markers without system-entry leakage. It does not claim final product completion.'
$summary += ''
$summary += '## Checks'
foreach ($check in $script:Checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += "- $mark [$($check.area)] $($check.name): $($check.detail)"
}
$summary -join "`r`n" | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 60)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }
