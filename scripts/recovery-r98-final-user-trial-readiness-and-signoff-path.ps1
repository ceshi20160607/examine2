param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r98-final-user-trial-readiness-and-signoff-path'
$ResultPath = Join-Path $EvidenceDir 'r98-final-user-trial-readiness-and-signoff-path-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r98-final-user-trial-readiness-and-signoff-path-2026-07-08.md'
$ChecklistPath = Join-Path $EvidenceDir 'r98-user-trial-checklist-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'final-user-trial-readiness-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r98-browser-audit.js'
$ChecklistScriptPath = Join-Path $RepoRoot 'scripts/recovery-r98-write-checklist.js'
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

function Invoke-ExpectedForbidden {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
        }
        return [ordered]@{ status = 200; forbidden = $false; body = ($response | ConvertTo-Json -Depth 30 -Compress) }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try { $body = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        if ($status -eq 401 -or $status -eq 403) {
            return [ordered]@{ status = $status; forbidden = $true; body = $body }
        }
        throw "Expected forbidden failed differently: $Method $Path -> HTTP $status $body"
    }
}

function Read-DeployedAssets {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $matches = [regex]::Matches($index, '(?:src|href)="(?<src>/assets/[^""<>]+\.(?:js|css))"')
        return @($matches | ForEach-Object { $_.Groups['src'].Value } | Select-Object -Unique)
    } catch {
        return @()
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

function Write-UserChecklist {
    param(
        [object]$Trial,
        [object]$R97,
        [object]$Coverage,
        [object]$Browser,
        [string[]]$Assets
    )
    if (-not (Test-Path -LiteralPath $ChecklistScriptPath)) {
        throw "Missing checklist writer: $ChecklistScriptPath"
    }
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $env:R98_CHECKLIST_PATH = $ChecklistPath
    $env:R98_BASE_URL = $BaseUrl
    $env:R98_TRIAL_PACK = ($Trial | ConvertTo-Json -Depth 100 -Compress)
    $env:R98_R97_RESULT = ($R97 | ConvertTo-Json -Depth 100 -Compress)
    if ($Coverage) {
        $env:R98_COVERAGE = ($Coverage | ConvertTo-Json -Depth 100 -Compress)
    } else {
        $env:R98_COVERAGE = '{}'
    }
    if ($Browser) {
        $env:R98_BROWSER_AUDIT = ($Browser | ConvertTo-Json -Depth 100 -Compress)
    } else {
        $env:R98_BROWSER_AUDIT = '{}'
    }
    $env:R98_ASSETS = ($Assets | ConvertTo-Json -Depth 20 -Compress)
    $env:R98_GENERATED_AT = (Get-Date).ToString('o')
    $checklistOutput = (& $nodeExe $ChecklistScriptPath 2>&1 | Out-String)
    $checklistExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath (Join-Path $WorkDir 'checklist-writer.log') -Value $checklistOutput -Encoding UTF8
    if ($checklistExit -ne 0) {
        throw "Checklist writer failed with exit code $checklistExit"
    }
}

$children = @()
$browserAudit = $null
$frameworkAudit = $null
$staticAudit = $null
$coverageAudit = $null
$assetList = @()
$apiEvidence = $null
$r80 = $null
$r97 = $null
$state = $null
$errorMessage = $null
$browserExit = 999

try {
    $ledger = Read-Text 'docs/framework/next-execution-ledger.md'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    $fixBatches = Read-Text 'docs/recovery/fix-batches.md'
    $continuationGuideExists = Test-Path -LiteralPath (Join-Path $RepoRoot 'docs/recovery/continuation-implementation-guide.md')
    $state = Read-JsonFile '.cursor/session/state.json'
    $r80 = Read-JsonFile 'docs/evidence/recovery/r80-live-user-trial-workspace-result.json'
    $r97 = Read-JsonFile 'docs/evidence/recovery/r97-c1-fresh-system-initialization-path-result.json'
    Add-Check 'process' 'R98 is active in ledger task card and fix batch' ($ledger -match 'REC-P0-098 Final User Trial Script Readiness And Signoff Path' -and $taskCards -match 'REC-P0-098 Final User Trial Script Readiness And Signoff Path' -and $fixBatches -match 'Batch R98') 'R98 must be the active recovery handoff contract.'
    Add-Check 'process' 'R98 planned evidence paths are declared' ($taskCards -match 'r98-final-user-trial-readiness-and-signoff-path-result\.json' -and $taskCards -match 'r98-final-user-trial-readiness-and-signoff-path-2026-07-08\.md' -and $taskCards -match 'r98-user-trial-checklist-2026-07-08\.md') 'R98 task card must declare result, summary, and checklist evidence.'
    Add-Check 'process' 'continuation guide exists for human handoff' $continuationGuideExists 'docs/recovery/continuation-implementation-guide.md must remain available.'
    Add-Check 'signoff-boundary' 'user signoff remains false before trial handoff' ($state -and $state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"
    Add-Check 'prior-evidence' 'R80 trial pack remains available' ($r80 -and $r80.status -eq 'PASS' -and $r80.accepted -eq $true -and $r80.trialPack) "status=$($r80.status), accepted=$($r80.accepted)"
    Add-Check 'prior-evidence' 'R97 empty-system hierarchy evidence is PASS' ($r97 -and $r97.status -eq 'PASS' -and $r97.userSignoff -eq $false -and $r97.browserAudit.status -eq 'PASS') "status=$($r97.status), system=$($r97.systemId), browser=$($r97.browserAudit.status)"

    $health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)
    $assetList = Read-DeployedAssets
    Add-Check 'deployed' 'deployed frontend asset manifest is readable' ($assetList.Count -gt 0) ($assetList -join ', ')
    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
    Add-Check 'release' 'verify-release child process completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $trial = $r80.trialPack
    $runtime = $trial.runtimeDailyUse
    $workflow = $trial.workflowTodoMessage
    $adminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.admin.loginName; password = [string]$trial.admin.password; loginTarget = 'PLATFORM' }
    $normalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.normalLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
    $readonlyLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.readonlyLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
    $requesterLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.requesterLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
    $approverLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.approverLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
    Add-Check 'trial-login' 'all retained trial accounts can still log in through API' ($adminLogin.accessToken -and $normalLogin.accessToken -and $readonlyLogin.accessToken -and $requesterLogin.accessToken -and $approverLogin.accessToken) 'admin, normal, readonly, requester, and approver tokens returned.'

    $normalHeaders = @{ Authorization = "Bearer $($normalLogin.accessToken)" }
    $readonlyHeaders = @{ Authorization = "Bearer $($readonlyLogin.accessToken)" }
    $requesterHeaders = @{ Authorization = "Bearer $($requesterLogin.accessToken)" }
    $approverHeaders = @{ Authorization = "Bearer $($approverLogin.accessToken)" }
    $runtimeDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records/$($runtime.existingRecordId)" -Headers $normalHeaders
    $readonlyForbidden = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records" -Headers $readonlyHeaders -Body @{
        fieldValues = @{ caseTitle = "R98 readonly forbidden $(Get-Date -Format HHmmss)"; caseAmount = 1; caseOwner = 'readonly' }
        childRows = @{}
        attachmentIds = @()
    }
    $workflowDetail = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$($workflow.systemId)/runtime/modules/$($workflow.moduleId)/records/$($workflow.terminalRecordId)" -Headers $requesterHeaders
    $handledTodos = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($workflow.systemId)/todos/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{
        scope = 'system'
        typeCode = 'flow_approval'
        keyword = [string]$workflow.terminalRecordId
        status = 'HANDLED'
        assigneeId = $approverLogin.profile.accountId
    }
    $approverMessages = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$($workflow.systemId)/messages/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{
        systemId = [string]$workflow.systemId
        tenantId = [string]$workflow.tenantId
        type = 'approval'
        archiveStatus = 'active'
        keyword = [string]$workflow.terminalRecordId
    }
    $workflowStatus = if ($workflowDetail.summary.status) { $workflowDetail.summary.status } else { $workflowDetail.approvalSidebar.status }
    $apiEvidence = [ordered]@{
        runtimeRecordId = $runtime.existingRecordId
        runtimeRecordTitle = if ($runtimeDetail.summary) { $runtimeDetail.summary.title } else { $runtimeDetail.title }
        readonlyCreateStatus = $readonlyForbidden.status
        workflowRecordId = $workflow.terminalRecordId
        workflowStatus = $workflowStatus
        handledTodoTotal = $handledTodos.page.total
        approverMessageTotal = $approverMessages.total
    }
    Add-Check 'trial-readback' 'runtime retained record is readable by normal member' ($runtimeDetail -and ([string]$runtimeDetail.summary.recordId -eq [string]$runtime.existingRecordId -or [string]$runtimeDetail.recordId -eq [string]$runtime.existingRecordId)) ($apiEvidence | ConvertTo-Json -Depth 20 -Compress)
    Add-Check 'permission' 'readonly member is denied create mutation' ($readonlyForbidden.forbidden -eq $true -and [int]$readonlyForbidden.status -eq 403) "status=$($readonlyForbidden.status)"
    Add-Check 'workflow-readback' 'workflow terminal record and handled todo remain readable' (($workflowStatus -eq 'APPROVED' -or $workflowStatus -eq 'REJECTED') -and [int]$handledTodos.page.total -ge 1) ($apiEvidence | ConvertTo-Json -Depth 20 -Compress)

    Start-BrowserAudit
    $env:R98_BASE_URL = $BaseUrl
    $env:R98_CDP_PORT = [string]$script:DebugPort
    $env:R98_EVIDENCE_DIR = $WorkDir
    $env:R98_TRIAL_PACK = ($trial | ConvertTo-Json -Depth 100 -Compress)
    $env:R98_R97_SYSTEM_ID = [string]$r97.systemId
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R98 browser smoke process completed' ($browserExit -eq 0 -and $browserAudit) "exitCode=$browserExit, log=$nodeLog"
    Add-Check 'browser' 'login smoke for trial paths passes without blockers' ($browserAudit -and $browserAudit.status -eq 'PASS' -and [int]$browserAudit.resultCount -ge 10 -and [int]$browserAudit.blockerResultCount -eq 0) "status=$($browserAudit.status), results=$($browserAudit.resultCount), blockers=$($browserAudit.blockerResultCount), overflow=$($browserAudit.maxOverflowX)"
    Add-Check 'browser' 'browser smoke has no visible encoding warnings' ($browserAudit -and [int]$browserAudit.warningResultCount -eq 0) "warningResults=$($browserAudit.warningResultCount)" 'WARN'

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1'
    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit')
    $frameworkAudit = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    $staticAudit = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    $coverageAudit = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'framework' 'framework audit passes with active R98 contract' ($frameworkAudit -and $frameworkAudit.status -eq 'PASS') "status=$($frameworkAudit.status)"
    Add-Check 'static-usability' 'static usability audit has no blockers' ($staticAudit -and $staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
    Add-Check 'coverage' 'coverage remains honest until explicit user signoff' ($coverageAudit -and [int]$coverageAudit.missingCount -eq 0 -and [int]$coverageAudit.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false) "missing=$($coverageAudit.missingCount), notClosed=$($coverageAudit.notClosedCount), userSignoff=$($state.gates.user_script_passed)"

    Write-UserChecklist -Trial $trial -R97 $r97 -Coverage $coverageAudit -Browser $browserAudit -Assets $assetList
    Add-Check 'checklist' 'user trial checklist is generated in readable handoff path' (Test-Path -LiteralPath $ChecklistPath) $ChecklistPath
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'fatal' 'R98 script completed without fatal exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
    Remove-Item Env:R98_BASE_URL, Env:R98_CDP_PORT, Env:R98_EVIDENCE_DIR, Env:R98_TRIAL_PACK, Env:R98_R97_SYSTEM_ID -ErrorAction SilentlyContinue
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = if ($status -eq 'PASS') { 'R98_FINAL_USER_TRIAL_READINESS_ENGINEERING_EVIDENCE_ONLY' } else { 'R98_FINAL_USER_TRIAL_READINESS_FAILED' }
    task = 'REC-P0-098'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    checklist = 'docs/evidence/recovery/r98-user-trial-checklist-2026-07-08.md'
    releaseAssets = @($assetList)
    trialPack = if ($r80) { $r80.trialPack } else { $null }
    r97Reference = if ($r97) { [ordered]@{ status = $r97.status; systemId = $r97.systemId; browserStatus = $r97.browserAudit.status; resultPath = 'docs/evidence/recovery/r97-c1-fresh-system-initialization-path-result.json' } } else { $null }
    apiEvidence = $apiEvidence
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    frameworkAudit = if ($frameworkAudit) { [ordered]@{ status = $frameworkAudit.status } } else { $null }
    staticAudit = if ($staticAudit) { [ordered]@{ status = $staticAudit.status; blockerCount = $staticAudit.blockerCount; warningCount = $staticAudit.warningCount } } else { $null }
    coverage = if ($coverageAudit) { [ordered]@{ totalRows = $coverageAudit.ledgerRowCount; missingCount = $coverageAudit.missingCount; notClosedCount = $coverageAudit.notClosedCount } } else { $null }
    browserAudit = if ($browserAudit) { [ordered]@{ status = $browserAudit.status; resultCount = $browserAudit.resultCount; blockerResultCount = $browserAudit.blockerResultCount; warningResultCount = $browserAudit.warningResultCount; maxOverflowX = $browserAudit.maxOverflowX; path = 'docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/final-user-trial-readiness-browser-audit.json' } } else { $null }
    error = $errorMessage
    nextRecommendedTask = if ($status -eq 'PASS') {
        [ordered]@{ taskId = 'USER_SIGNOFF_OR_FEEDBACK_INTAKE'; title = 'Run the R98 checklist manually, then either sign off or create the next concrete recovery task from feedback.'; reason = 'R98 only prepares the human trial path and preserves gates.user_script_passed=false.' }
    } else {
        [ordered]@{ taskId = 'REC-P0-098'; title = 'Final User Trial Readiness Residual Repair'; reason = ($script:Failures | Select-Object -First 3) -join ' | ' }
    }
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @(
    '# R98 Final User Trial Readiness And Signoff Path',
    '',
    "- Status: $status",
    "- BaseUrl: $BaseUrl",
    '- User signoff: false',
    '- Checklist: `docs/evidence/recovery/r98-user-trial-checklist-2026-07-08.md`',
    "- R97 reference: $($result.r97Reference.status) / system $($result.r97Reference.systemId)",
    "- Browser audit: $($result.browserAudit.status), results=$($result.browserAudit.resultCount), blockers=$($result.browserAudit.blockerResultCount), warnings=$($result.browserAudit.warningResultCount)",
    "- Framework/static/coverage: $($result.frameworkAudit.status) / $($result.staticAudit.status) / notClosed=$($result.coverage.notClosedCount)",
    '',
    'This is engineering evidence only. It prepares the current human trial path and keeps final user acceptance open.',
    '',
    '## Failures'
)
if ($script:Failures.Count -eq 0) {
    $summary += '- none'
} else {
    foreach ($failure in $script:Failures) { $summary += "- $failure" }
}
$summary += ''
$summary += '## Warnings'
if ($script:Warnings.Count -eq 0) {
    $summary += '- none'
} else {
    foreach ($warning in $script:Warnings) { $summary += "- $warning" }
}
$summary += ''
$summary += '## Evidence'
$summary += '- Result: `docs/evidence/recovery/r98-final-user-trial-readiness-and-signoff-path-result.json`'
$summary += '- Checklist: `docs/evidence/recovery/r98-user-trial-checklist-2026-07-08.md`'
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/final-user-trial-readiness-browser-audit.json`'
$summary += '- Screenshots/logs: `docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/`'
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 100)
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }
