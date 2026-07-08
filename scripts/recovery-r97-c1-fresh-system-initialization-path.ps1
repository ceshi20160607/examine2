param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r97-c1-fresh-system-initialization-path'
$ResultPath = Join-Path $EvidenceDir 'r97-c1-fresh-system-initialization-path-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r97-c1-fresh-system-initialization-path-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'c1-fresh-system-initialization-path-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r97-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:DebugPort = $null

$requiredC1Steps = @(
    'system-info',
    'organization-members',
    'roles-permissions',
    'module-groups-modules',
    'fields-dictionaries',
    'page-list-detail-actions',
    'workflow-messages',
    'work-configuration',
    'integration-openapi-ai',
    'publish-runtime-preview'
)

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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
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

function Read-DeployedAssetText {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $assetMatches = [regex]::Matches($index, 'src="(?<src>/assets/[^""<>]+\.js)"')
        if ($assetMatches.Count -eq 0) { return '' }
        $texts = [System.Collections.Generic.List[string]]::new()
        foreach ($match in $assetMatches) {
            $texts.Add((Invoke-WebRequest -Uri "$BaseUrl$($match.Groups['src'].Value)" -UseBasicParsing -TimeoutSec 30).Content) | Out-Null
        }
        return ($texts -join "`n")
    } catch {
        return ''
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
$frameworkAudit = $null
$staticAudit = $null
$coverageAudit = $null
$systemId = ''
$systemCode = ''
$switchContext = $null
$freshSystemReadback = $null
$errorMessage = $null

try {
    $systemShell = Read-Text 'frontend/src/features/system-shell/systemShell.ts'
    $systemAdmin = Read-Text 'frontend/src/features/system-admin/systemAdmin.ts'
    $css = Read-Text 'frontend/src/styles.css'
    $ledger = Read-Text 'docs/framework/next-execution-ledger.md'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    $state = Read-JsonFile '.cursor/session/state.json'

    $missingAdminStepKeys = @($requiredC1Steps | Where-Object { $systemAdmin -notmatch [regex]::Escape($_) })
    Add-Check 'static' 'R97 empty dashboard markers exist' ($systemShell -match 'r97EmptySystemDashboard' -and $systemShell -match 'emptyDashboardPrimarySurface' -and $systemShell -match 'dashboardActionHubSuppressed' -and $systemShell -match 'dashboardRuntimePanelsSuppressed') 'dashboard must expose initialization-first markers and suppression markers.'
    Add-Check 'static' 'R97 empty dashboard branch returns before runtime panels' ($systemShell -match 'r97EmptyDashboardHeading' -and $systemShell -match 'createSystemInitializationPrompt\(navigate\)' -and $systemShell -match 'return;\s*\}\s*root\.replaceChildren') 'empty system admin dashboard must render only initialization prompt before normal dashboard panels.'
    Add-Check 'static' 'R97 C1 guide exposes all 10 step keys' ($systemAdmin -match 'r97C1Guide' -and $systemAdmin -match 'c1StepCount' -and $missingAdminStepKeys.Count -eq 0) "missing=$($missingAdminStepKeys -join ',')"
    Add-Check 'static' 'R97 C1 guide has readback and actions' ($systemAdmin -match 'step-readback' -and $systemAdmin -match 'r97C1StepAction' -and $systemAdmin -match 'c1StepBlocked') 'guide must render state readback, direct actions, and blocked status per step.'
    Add-Check 'style' 'R97 responsive styles exist' ($css -match 'system-first-use-dashboard' -and $css -match 'c1-initialization-guide' -and $css -match 'onboarding-check-item-detailed' -and $css -match 'max-width: 720px') 'R97 CSS must cover empty dashboard, C1 checklist, and mobile layout.'
    Add-Check 'process' 'R97 is active in ledger and task cards' ($ledger -match 'REC-P0-097 C1 Fresh System Initialization Path And Empty Dashboard Hierarchy Closure' -and $taskCards -match 'REC-P0-097 C1 Fresh System Initialization Path And Empty Dashboard Hierarchy Closure') 'active task must be tracked through recovery ledger and task card.'
    Add-Check 'signoff-boundary' 'user signoff remains false' ($state -and $state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"

    $health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)

    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
    Add-Check 'release' 'verify-release child process completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $deployedAssetText = Read-DeployedAssetText
    $deployedMarkers = @('r97EmptySystemDashboard', 'dashboardActionHubSuppressed', 'dashboardRuntimePanelsSuppressed', 'r97C1Guide', 'c1StepCount', 'integration-openapi-ai', 'publish-runtime-preview')
    $missingDeployedMarkers = @($deployedMarkers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })
    Add-Check 'deployed' 'deployed frontend asset contains R97 markers' (($deployedAssetText.Length -gt 0) -and $missingDeployedMarkers.Count -eq 0) "assetLength=$($deployedAssetText.Length), missing=$($missingDeployedMarkers -join ',')"

    $stamp = Get-Date -Format 'yyyyMMddHHmmss'
    $suffix = [guid]::NewGuid().ToString('N').Substring(0, 6)
    $systemCode = "r97_c1_$stamp$suffix"
    $adminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = 'admin'; password = '123123aa'; loginTarget = 'PLATFORM' }
    $adminHeaders = @{ Authorization = "Bearer $($adminLogin.accessToken)" }
    $createdSystem = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $adminHeaders -Body @{ systemName = "R97 C1 Fresh System $stamp"; systemCode = $systemCode; tenantMode = 1; templateCode = 'blank' }
    $systemId = [string]$createdSystem.systemId
    $switchContext = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $adminHeaders -Body @{ systemId = $systemId; tenantId = $null; reason = 'R97 C1 fresh system initialization audit' }
    $departments = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$systemId/org/departments" -Headers $adminHeaders
    $members = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$systemId/members?pageNo=1&pageSize=20" -Headers $adminHeaders
    $roles = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$systemId/roles?pageNo=1&pageSize=20" -Headers $adminHeaders
    $modules = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$systemId/modules?pageNo=1&pageSize=50" -Headers $adminHeaders
    $groups = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$systemId/module-groups" -Headers $adminHeaders
    $freshSystemReadback = [ordered]@{
        departmentCount = @($departments).Count
        memberCount = @($members.records).Count
        roleCount = @($roles.records).Count
        moduleCount = @($modules.records).Count
        moduleGroupCount = @($groups).Count
        effectiveRoleIds = @($switchContext.effectiveRoleIds)
    }
    Add-Check 'api-readback' 'fresh system has owner context and no runtime modules' (($systemId.Length -gt 0) -and (@($switchContext.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN') -and @($members.records).Count -ge 1 -and @($roles.records).Count -ge 1 -and @($modules.records).Count -eq 0) ($freshSystemReadback | ConvertTo-Json -Depth 20 -Compress)

    Start-BrowserAudit
    $env:R97_BASE_URL = $BaseUrl
    $env:R97_CDP_PORT = [string]$script:DebugPort
    $env:R97_EVIDENCE_DIR = $WorkDir
    $env:R97_SYSTEM_ID = $systemId
    $env:R97_ADMIN_ACCESS_TOKEN = [string]$adminLogin.accessToken
    $env:R97_ADMIN_REFRESH_TOKEN = [string]$adminLogin.refreshToken
    $env:R97_ADMIN_ACCOUNT_ID = [string]$adminLogin.profile.accountId
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R97 browser audit process completed' ($browserExit -eq 0 -and $browserAudit) "exitCode=$browserExit, log=$nodeLog"
    Add-Check 'browser' 'empty dashboard and C1 guide audit pass' ($browserAudit -and $browserAudit.status -eq 'PASS' -and $browserAudit.emptyDashboardPassed -eq $true -and $browserAudit.c1GuidePassed -eq $true) "status=$($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxOverflow=$($browserAudit.maxOverflowX)"

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1'
    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit')
    $frameworkAudit = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    $staticAudit = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    $coverageAudit = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'framework' 'framework audit still passes with active R97 contract' ($frameworkAudit -and $frameworkAudit.status -eq 'PASS') "status=$($frameworkAudit.status)"
    Add-Check 'static-usability' 'static usability audit has no blockers' ($staticAudit -and $staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
    Add-Check 'coverage' 'coverage remains honest until user signoff' ($coverageAudit -and [int]$coverageAudit.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false) "notClosed=$($coverageAudit.notClosedCount), userSignoff=$($state.gates.user_script_passed)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'fatal' 'R97 script completed without fatal exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
    Remove-Item Env:R97_BASE_URL, Env:R97_CDP_PORT, Env:R97_EVIDENCE_DIR, Env:R97_SYSTEM_ID, Env:R97_ADMIN_ACCESS_TOKEN, Env:R97_ADMIN_REFRESH_TOKEN, Env:R97_ADMIN_ACCOUNT_ID -ErrorAction SilentlyContinue
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = if ($status -eq 'PASS') { 'R97_C1_FRESH_SYSTEM_INITIALIZATION_PATH_ENGINEERING_EVIDENCE_ONLY' } else { 'R97_C1_FRESH_SYSTEM_INITIALIZATION_PATH_FAILED' }
    task = 'REC-P0-097'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    systemId = $systemId
    systemCode = $systemCode
    freshSystemReadback = $freshSystemReadback
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    frameworkAudit = if ($frameworkAudit) { [ordered]@{ status = $frameworkAudit.status } } else { $null }
    staticAudit = if ($staticAudit) { [ordered]@{ status = $staticAudit.status; blockerCount = $staticAudit.blockerCount; warningCount = $staticAudit.warningCount } } else { $null }
    coverage = if ($coverageAudit) { [ordered]@{ totalRows = $coverageAudit.ledgerRowCount; missingCount = $coverageAudit.missingCount; notClosedCount = $coverageAudit.notClosedCount } } else { $null }
    browserAudit = if ($browserAudit) { [ordered]@{ status = $browserAudit.status; resultCount = $browserAudit.resultCount; blockerResultCount = $browserAudit.blockerResultCount; emptyDashboardPassed = $browserAudit.emptyDashboardPassed; c1GuidePassed = $browserAudit.c1GuidePassed; maxOverflowX = $browserAudit.maxOverflowX; path = 'docs/evidence/recovery/screenshots/r97-c1-fresh-system-initialization-path/c1-fresh-system-initialization-path-browser-audit.json' } } else { $null }
    error = $errorMessage
    nextRecommendedTask = if ($status -eq 'PASS') {
        [ordered]@{ taskId = 'REC-P0-098'; title = 'Final User Trial Script Readiness And Signoff Path'; reason = 'R97 closes the most confusing first-use empty-system path; next work should prepare or run the user-facing trial script while keeping requirement coverage and signoff separate.' }
    } else {
        [ordered]@{ taskId = 'REC-P0-097'; title = 'C1 Fresh System Initialization Path Residual Closure'; reason = ($script:Failures | Select-Object -First 3) -join ' | ' }
    }
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @(
    '# R97 C1 Fresh System Initialization Path',
    '',
    "- Status: $status",
    "- BaseUrl: $BaseUrl",
    "- Fresh system: $systemId / $systemCode",
    '- User signoff: false',
    "- Browser audit: $($result.browserAudit.status)",
    "- Empty dashboard / C1 guide: $($result.browserAudit.emptyDashboardPassed) / $($result.browserAudit.c1GuidePassed)",
    "- Max overflow: $($result.browserAudit.maxOverflowX)",
    "- Framework/static/coverage: $($result.frameworkAudit.status) / $($result.staticAudit.status) / notClosed=$($result.coverage.notClosedCount)",
    "- Next recommended task: $($result.nextRecommendedTask.taskId) $($result.nextRecommendedTask.title)",
    '',
    'This is engineering evidence only. Final user acceptance remains open until explicit user verification/signoff.',
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
$summary += '- Result: `docs/evidence/recovery/r97-c1-fresh-system-initialization-path-result.json`'
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r97-c1-fresh-system-initialization-path/c1-fresh-system-initialization-path-browser-audit.json`'
$summary += '- Screenshots/logs: `docs/evidence/recovery/screenshots/r97-c1-fresh-system-initialization-path/`'
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 100)
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }
