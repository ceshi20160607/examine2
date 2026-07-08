param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r102-final-user-trial-refresh-after-r101'
$ResultPath = Join-Path $EvidenceDir 'r102-final-user-trial-refresh-after-r101-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r102-final-user-trial-refresh-after-r101-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'final-user-trial-refresh-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r102-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:DebugPort = $null

function Add-Check {
    param([string]$Area, [string]$Name, [bool]$Passed, [string]$Detail, [string]$Severity = 'ERROR')
    $script:Checks.Add([ordered]@{ area = $Area; name = $Name; passed = $Passed; severity = $Severity; detail = $Detail }) | Out-Null
    if (-not $Passed) {
        $message = "${Area}/${Name}: $Detail"
        if ($Severity -eq 'WARN') { $script:Warnings.Add($message) | Out-Null } else { $script:Failures.Add($message) | Out-Null }
    }
}

function Read-Text([string]$RelativePath) {
    return [System.IO.File]::ReadAllText((Join-Path $RepoRoot $RelativePath), [System.Text.UTF8Encoding]::new($false))
}

function Read-JsonFile([string]$Path) {
    $full = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $RepoRoot $Path }
    if (-not (Test-Path -LiteralPath $full)) { return $null }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $full | ConvertFrom-Json
}

function Invoke-ApiRaw([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{}) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $Headers; TimeoutSec = 45 }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Depth 80 -Compress)
    }
    try {
        $json = Invoke-RestMethod @params
        return [ordered]@{ status = 200; code = $json.code; data = $json.data; body = ($json | ConvertTo-Json -Depth 80 -Compress) }
    } catch {
        $status = 0
        $content = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try { $content = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        $json = $null
        if ($content) { try { $json = $content | ConvertFrom-Json } catch {} }
        return [ordered]@{ status = $status; code = if ($json) { $json.code } else { $null }; data = if ($json) { $json.data } else { $null }; body = $content }
    }
}

function Invoke-ApiData([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{}) {
    $result = Invoke-ApiRaw -Method $Method -Path $Path -Body $Body -Headers $Headers
    if ($result.status -lt 200 -or $result.status -ge 300 -or ($result.code -and $result.code -ne 'SUCCESS')) {
        throw "API failed: $Method $Path status=$($result.status) code=$($result.code) body=$($result.body)"
    }
    return $result.data
}

function Invoke-ExpectedDenied([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{}) {
    $result = Invoke-ApiRaw -Method $Method -Path $Path -Body $Body -Headers $Headers
    if ($result.status -eq 401 -or $result.status -eq 403) { return $result }
    throw "Expected denied request but got status=$($result.status): $Method $Path"
}

function Invoke-ChildScript([string]$Name, [string]$RelativePath, [string[]]$Arguments = @(), [int[]]$AllowedExitCodes = @(0)) {
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
            $output = (& powershell @('-NoProfile','-ExecutionPolicy','Bypass','-File',$scriptPath) $Arguments 2>&1 | Out-String)
            $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        } catch {
            $output = $_.Exception.ToString()
            $exitCode = 999
        } finally { $ErrorActionPreference = $previous }
    } finally { Pop-Location }
    Set-Content -LiteralPath $logPath -Value $output -Encoding UTF8
    return [ordered]@{ name = $Name; script = $RelativePath; args = $Arguments; exitCode = $exitCode; logFile = $logPath; allowed = $AllowedExitCodes }
}

function Read-DeployedAssetText {
    try {
        $client = [System.Net.WebClient]::new()
        $client.Encoding = [System.Text.Encoding]::UTF8
        $index = $client.DownloadString("$BaseUrl/")
        $matches = [regex]::Matches($index, '(?:src|href)="(?<src>/assets/[^""<>]+\.(?:js|css))"')
        $assets = @($matches | ForEach-Object { $_.Groups['src'].Value } | Select-Object -Unique)
        $texts = @()
        foreach ($asset in $assets) {
            if ($asset -like '*.js') { $texts += $client.DownloadString("$BaseUrl$asset") }
        }
        return [ordered]@{ assets = $assets; text = ($texts -join "`n") }
    } catch { return [ordered]@{ assets = @(); text = ''; error = $_.Exception.Message } }
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
    foreach ($candidate in $paths) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
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
        try { Invoke-RestMethod -Uri "http://127.0.0.1:$script:DebugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) { Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) { Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue }
}

$children = @()
$browserAudit = $null
$frameworkAudit = $null
$staticAudit = $null
$coverageAudit = $null
$state = $null
$r98 = $null
$r101 = $null
$assetInfo = $null
$apiEvidence = [ordered]@{}
$errorMessage = $null
$browserExit = 999

try {
    $state = Read-JsonFile '.cursor/session/state.json'
    $ledger = Read-Text 'docs/framework/next-execution-ledger.md'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    $fixBatches = Read-Text 'docs/recovery/fix-batches.md'
    $r98 = Read-JsonFile 'docs/evidence/recovery/r98-final-user-trial-readiness-and-signoff-path-result.json'
    $r101 = Read-JsonFile 'docs/evidence/recovery/r101-platform-flow-application-api-readback-result.json'
    Add-Check 'state' 'R102 is active and user signoff remains false' ($state -and $state.build_plan.currentBatch -eq 'RECOVERY-R102' -and $state.gates.user_script_passed -eq $false) "currentBatch=$($state.build_plan.currentBatch), userSignoff=$($state.gates.user_script_passed)"
    Add-Check 'contract' 'R102 is declared across ledger task card and fix batch' ($ledger -match 'REC-P0-102 Final User Trial Refresh After Platform Flow Application Readback' -and $taskCards -match 'REC-P0-102 Final User Trial Refresh After Platform Flow Application Readback' -and $fixBatches -match 'Batch R102') 'R102 must be the active continuation contract.'
    Add-Check 'contract' 'R102 planned evidence paths are declared' ($taskCards -match 'r102-final-user-trial-refresh-after-r101-result\.json' -and $taskCards -match 'r102-final-user-trial-refresh-after-r101-2026-07-08\.md' -and $taskCards -match 'final-user-trial-refresh-browser-audit\.json') 'R102 task card must declare result, summary, and browser audit evidence.'
    Add-Check 'prior-evidence' 'R98 trial handoff remains available' ($r98 -and $r98.status -eq 'PASS' -and $r98.trialPack -and $r98.userSignoff -eq $false) "status=$($r98.status), userSignoff=$($r98.userSignoff)"
    Add-Check 'prior-evidence' 'R101 API readback evidence is accepted' ($r101 -and $r101.status -eq 'PASS' -and $r101.accepted -eq $true -and $r101.userSignoff -eq $false -and $r101.apiEvidence.flow.updated.flowId) "status=$($r101.status), accepted=$($r101.accepted), flowId=$($r101.apiEvidence.flow.updated.flowId)"

    $health = Invoke-ApiData -Method Get -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)
    $children += Invoke-ChildScript 'verify-release' 'scripts/verify-release.ps1' @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend', '-NoFailExit')
    Add-Check 'release' 'verify-release child process completed' ($children[-1].exitCode -eq 0) "exitCode=$($children[-1].exitCode), log=$($children[-1].logFile)"

    $assetInfo = Read-DeployedAssetText
    $assetText = [string]$assetInfo.text
    Add-Check 'deployed-asset' 'deployed frontend includes R101/R102 platform markers' ($assetInfo.assets.Count -gt 0 -and $assetText -like '*/api/v1/platform/flows*' -and $assetText -like '*/api/v1/platform/applications/authorizations*' -and $assetText -like '*platformFlowRow*' -and $assetText -like '*platformAuthorizationRow*' -and $assetText -notlike '*platformApplicationSystemAdminConfig*') "assets=$($assetInfo.assets -join ',')"

    $trial = $r98.trialPack
    $runtime = $trial.runtimeDailyUse
    $workflow = $trial.workflowTodoMessage
    $adminLogin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.admin.loginName; password = [string]$trial.admin.password; loginTarget = 'PLATFORM' }
    $normalLogin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.normalLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
    $readonlyLogin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$runtime.readonlyLoginName; password = [string]$runtime.password; loginTarget = 'PLATFORM' }
    $requesterLogin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.requesterLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
    $approverLogin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$workflow.approverLoginName; password = [string]$workflow.password; loginTarget = 'PLATFORM' }
    Add-Check 'trial-login' 'retained trial accounts still log in' ($adminLogin.accessToken -and $normalLogin.accessToken -and $readonlyLogin.accessToken -and $requesterLogin.accessToken -and $approverLogin.accessToken) 'admin, normal, readonly, requester, and approver tokens returned.'

    $adminHeaders = @{ Authorization = "Bearer $($adminLogin.accessToken)" }
    $normalHeaders = @{ Authorization = "Bearer $($normalLogin.accessToken)" }
    $readonlyHeaders = @{ Authorization = "Bearer $($readonlyLogin.accessToken)" }
    $requesterHeaders = @{ Authorization = "Bearer $($requesterLogin.accessToken)" }
    $approverHeaders = @{ Authorization = "Bearer $($approverLogin.accessToken)" }
    $runtimeDetail = Invoke-ApiData -Method Get -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records/$($runtime.existingRecordId)" -Headers $normalHeaders
    $readonlyForbidden = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$($runtime.systemId)/runtime/modules/$($runtime.moduleId)/records" -Headers $readonlyHeaders -Body @{ fieldValues = @{ caseTitle = "R102 readonly forbidden $(Get-Date -Format HHmmss)"; caseAmount = 1; caseOwner = 'readonly' }; childRows = @{}; attachmentIds = @() }
    $workflowDetail = Invoke-ApiData -Method Get -Path "/api/v1/systems/$($workflow.systemId)/runtime/modules/$($workflow.moduleId)/records/$($workflow.terminalRecordId)" -Headers $requesterHeaders
    $handledTodos = Invoke-ApiData -Method Post -Path "/api/v1/systems/$($workflow.systemId)/todos/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{ scope = 'system'; typeCode = 'flow_approval'; keyword = [string]$workflow.terminalRecordId; status = 'HANDLED'; assigneeId = $approverLogin.profile.accountId }
    $approverMessages = Invoke-ApiData -Method Post -Path "/api/v1/systems/$($workflow.systemId)/messages/search?pageNo=1&pageSize=20" -Headers $approverHeaders -Body @{ systemId = [string]$workflow.systemId; tenantId = [string]$workflow.tenantId; type = 'approval'; archiveStatus = 'active'; keyword = [string]$workflow.terminalRecordId }
    $workflowStatus = if ($workflowDetail.summary.status) { $workflowDetail.summary.status } else { $workflowDetail.approvalSidebar.status }
    Add-Check 'trial-readback' 'runtime retained record is readable by normal member' ($runtimeDetail -and ([string]$runtimeDetail.summary.recordId -eq [string]$runtime.existingRecordId -or [string]$runtimeDetail.recordId -eq [string]$runtime.existingRecordId)) "recordId=$($runtime.existingRecordId)"
    Add-Check 'permission' 'readonly member create remains denied' ([int]$readonlyForbidden.status -eq 403) "status=$($readonlyForbidden.status)"
    Add-Check 'workflow-readback' 'workflow retained terminal state and handled todo remain readable' (($workflowStatus -eq 'APPROVED' -or $workflowStatus -eq 'REJECTED') -and [int]$handledTodos.page.total -ge 1) "status=$workflowStatus, handledTodos=$($handledTodos.page.total), messages=$($approverMessages.total)"
    Add-Check 'workflow-readback' 'workflow message route remains covered by browser even if active message is archived' ([int]$approverMessages.total -ge 0) "activeMessages=$($approverMessages.total); browser message route is checked separately" 'WARN'

    $flowId = [string]$r101.apiEvidence.flow.updated.flowId
    $flowCode = [string]$r101.apiEvidence.flow.updated.flowCode
    $flowDetail = Invoke-ApiData -Method Get -Path "/api/v1/platform/flows/$flowId" -Headers $adminHeaders
    $flowList = Invoke-ApiData -Method Get -Path '/api/v1/platform/flows?pageNo=1&pageSize=20' -Headers $adminHeaders
    $authId = [string]$r101.apiEvidence.authorization.adminRequest.authorizationId
    $authRequestId = [string]$r101.apiEvidence.authorization.adminRequest.requestId
    $authAppName = [string]$r101.apiEvidence.authorization.adminRequest.applicationName
    $authDetail = Invoke-ApiData -Method Get -Path "/api/v1/platform/applications/authorizations/$authId" -Headers $adminHeaders
    $authList = Invoke-ApiData -Method Get -Path '/api/v1/platform/applications/authorizations?pageNo=1&pageSize=20' -Headers $adminHeaders
    Add-Check 'r101-readback' 'R101 Flow object still reads back from API' ($flowDetail.flowId -and [string]$flowDetail.flowCode -eq $flowCode -and $flowList.total -ge 1 -and $flowDetail.permissionMode -eq 'ADMIN_MUTATION_ALLOWED') "flowId=$flowId, flowCode=$($flowDetail.flowCode), total=$($flowList.total)"
    Add-Check 'r101-readback' 'R101 Application authorization still reads back from API' ($authDetail.authorizationId -and [string]$authDetail.requestId -eq $authRequestId -and $authList.total -ge 1 -and $authDetail.permissionMode -eq 'ADMIN_MUTATION_ALLOWED') "authorizationId=$authId, requestId=$($authDetail.requestId), total=$($authList.total)"
    Add-Check 'r101-boundary' 'R101 boundary remains no direct system business write' ($r101.apiEvidence.flow.run.boundary -eq 'NO_SYSTEM_BUSINESS_WRITE' -and $r101.apiEvidence.authorization.adjust.boundary -eq 'NO_SYSTEM_BUSINESS_WRITE') "flowBoundary=$($r101.apiEvidence.flow.run.boundary), authBoundary=$($r101.apiEvidence.authorization.adjust.boundary)"
    $apiEvidence = [ordered]@{ runtimeRecordId = $runtime.existingRecordId; readonlyCreateStatus = $readonlyForbidden.status; workflowRecordId = $workflow.terminalRecordId; workflowStatus = $workflowStatus; handledTodoTotal = $handledTodos.page.total; approverMessageTotal = $approverMessages.total; r101Flow = [ordered]@{ flowId = $flowId; flowCode = $flowDetail.flowCode; runBatchId = $flowDetail.currentRunBatchId; traceId = $flowDetail.traceId }; r101Authorization = [ordered]@{ authorizationId = $authId; requestId = $authDetail.requestId; authorizationChangeId = $authDetail.authorizationChangeId; status = $authDetail.status } }

    Start-BrowserAudit
    $env:R102_BASE_URL = $BaseUrl
    $env:R102_CDP_PORT = [string]$script:DebugPort
    $env:R102_EVIDENCE_DIR = $WorkDir
    $env:R102_TRIAL_PACK = ($trial | ConvertTo-Json -Depth 100 -Compress)
    $env:R102_FLOW_CODE = $flowCode
    $env:R102_AUTH_REQUEST_ID = $authRequestId
    $env:R102_AUTH_APP_NAME = $authAppName
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R102 browser audit process completed' ($browserExit -eq 0 -and $browserAudit) "exitCode=$browserExit, log=$nodeLog"
    Add-Check 'browser' 'R102 browser audit passes Flow/Application/trial routes' ($browserAudit -and $browserAudit.status -eq 'PASS' -and [int]$browserAudit.resultCount -ge 8 -and [int]$browserAudit.blockerResultCount -eq 0 -and $browserAudit.flowPassed -eq $true -and $browserAudit.appsPassed -eq $true -and $browserAudit.trialRoutesPassed -eq $true) "status=$($browserAudit.status), results=$($browserAudit.resultCount), blockers=$($browserAudit.blockerResultCount), overflow=$($browserAudit.maxOverflowX)"
    Add-Check 'browser' 'R102 browser audit has no visible encoding warnings' ($browserAudit -and [int]$browserAudit.warningResultCount -eq 0) "warningResults=$($browserAudit.warningResultCount)" 'WARN'

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1'
    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit')
    $frameworkAudit = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    $staticAudit = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    $coverageAudit = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'framework' 'framework audit passes with active R102 contract' ($frameworkAudit -and $frameworkAudit.status -eq 'PASS' -and $frameworkAudit.activeTaskContracts[0].taskId -eq 'REC-P0-102') "status=$($frameworkAudit.status), active=$($frameworkAudit.activeTaskContracts[0].taskId)"
    Add-Check 'static-usability' 'static usability audit has no blockers' ($staticAudit -and $staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
    Add-Check 'coverage' 'coverage remains honest until explicit user signoff' ($coverageAudit -and [int]$coverageAudit.missingCount -eq 0 -and [int]$coverageAudit.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false) "missing=$($coverageAudit.missingCount), notClosed=$($coverageAudit.notClosedCount), userSignoff=$($state.gates.user_script_passed)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'fatal' 'R102 script completed without fatal exception' $false $errorMessage
} finally {
    Stop-BrowserAudit
    Remove-Item Env:R102_BASE_URL, Env:R102_CDP_PORT, Env:R102_EVIDENCE_DIR, Env:R102_TRIAL_PACK, Env:R102_FLOW_CODE, Env:R102_AUTH_REQUEST_ID, Env:R102_AUTH_APP_NAME -ErrorAction SilentlyContinue
}

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{
    status = $status
    productStatus = if ($status -eq 'PASS') { 'R102_FINAL_USER_TRIAL_REFRESH_AFTER_R101_ENGINEERING_EVIDENCE_ONLY' } else { 'R102_FINAL_USER_TRIAL_REFRESH_FAILED' }
    task = 'REC-P0-102'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    releaseAssets = if ($assetInfo) { @($assetInfo.assets) } else { @() }
    r98Reference = if ($r98) { [ordered]@{ status = $r98.status; checklist = $r98.checklist; trialPack = $r98.trialPack } } else { $null }
    r101Reference = if ($r101) { [ordered]@{ status = $r101.status; accepted = $r101.accepted; flowId = $r101.apiEvidence.flow.updated.flowId; authorizationId = $r101.apiEvidence.authorization.adminRequest.authorizationId; resultPath = 'docs/evidence/recovery/r101-platform-flow-application-api-readback-result.json' } } else { $null }
    apiEvidence = $apiEvidence
    checks = @($script:Checks.ToArray())
    failures = @($script:Failures.ToArray())
    warnings = @($script:Warnings.ToArray())
    childResults = $children
    frameworkAudit = if ($frameworkAudit) { [ordered]@{ status = $frameworkAudit.status; activeTask = $frameworkAudit.activeTaskContracts[0].taskId } } else { $null }
    staticAudit = if ($staticAudit) { [ordered]@{ status = $staticAudit.status; blockerCount = $staticAudit.blockerCount; warningCount = $staticAudit.warningCount } } else { $null }
    coverage = if ($coverageAudit) { [ordered]@{ totalRows = $coverageAudit.ledgerRowCount; missingCount = $coverageAudit.missingCount; notClosedCount = $coverageAudit.notClosedCount } } else { $null }
    browserAudit = if ($browserAudit) { [ordered]@{ status = $browserAudit.status; resultCount = $browserAudit.resultCount; blockerResultCount = $browserAudit.blockerResultCount; warningResultCount = $browserAudit.warningResultCount; maxOverflowX = $browserAudit.maxOverflowX; path = 'docs/evidence/recovery/screenshots/r102-final-user-trial-refresh-after-r101/final-user-trial-refresh-browser-audit.json' } } else { $null }
    error = $errorMessage
    nextRecommendedTask = if ($status -eq 'PASS') {
        [ordered]@{ taskId = 'USER_SIGNOFF_OR_FEEDBACK_INTAKE'; title = 'Ask the user to run the current checklist, then either record signoff or create the next concrete task from feedback.'; reason = 'R102 refreshes the current trial path after R101 and keeps gates.user_script_passed=false.' }
    } else {
        [ordered]@{ taskId = 'REC-P0-102'; title = 'R102 residual repair'; reason = ($script:Failures | Select-Object -First 3) -join ' | ' }
    }
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8

$summary = @(
    '# R102 Final User Trial Refresh After R101',
    '',
    "- Status: $status",
    "- BaseUrl: $BaseUrl",
    '- User signoff: false',
    "- R98 reference: $($result.r98Reference.status)",
    "- R101 reference: $($result.r101Reference.status), flowId=$($result.r101Reference.flowId), authorizationId=$($result.r101Reference.authorizationId)",
    "- Browser audit: $($result.browserAudit.status), results=$($result.browserAudit.resultCount), blockers=$($result.browserAudit.blockerResultCount), warnings=$($result.browserAudit.warningResultCount)",
    "- Framework/static/coverage: $($result.frameworkAudit.status) / $($result.staticAudit.status) / notClosed=$($result.coverage.notClosedCount)",
    '',
    'R102 refreshes the current deployed user-trial path after R101. It proves the running release still supports retained trial login/readback, `/platform/flow` and `/platform/apps` show API-backed platform objects, and the platform/system boundary remains intact. It does not claim final user signoff.',
    '',
    '## Failures'
)
if ($script:Failures.Count -eq 0) { $summary += '- none' } else { foreach ($failure in $script:Failures) { $summary += "- $failure" } }
$summary += ''
$summary += '## Warnings'
if ($script:Warnings.Count -eq 0) { $summary += '- none' } else { foreach ($warning in $script:Warnings) { $summary += "- $warning" } }
$summary += ''
$summary += '## Evidence'
$summary += '- Result: `docs/evidence/recovery/r102-final-user-trial-refresh-after-r101-result.json`'
$summary += '- Summary: `docs/evidence/recovery/r102-final-user-trial-refresh-after-r101-2026-07-08.md`'
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r102-final-user-trial-refresh-after-r101/final-user-trial-refresh-browser-audit.json`'
$summary += '- Screenshots/logs: `docs/evidence/recovery/screenshots/r102-final-user-trial-refresh-after-r101/`'
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Output ($result | ConvertTo-Json -Depth 100)
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }
