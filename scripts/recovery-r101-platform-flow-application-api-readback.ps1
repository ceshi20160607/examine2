param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$WorkDir = Join-Path $EvidenceDir 'screenshots/r101-platform-flow-application-api-readback'
$ResultPath = Join-Path $EvidenceDir 'r101-platform-flow-application-api-readback-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r101-platform-flow-application-api-readback-2026-07-08.md'
$BrowserOut = Join-Path $WorkDir 'platform-flow-application-api-readback-browser-audit.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r101-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:DebugPort = $null

function Add-Check([string]$Area, [string]$Name, [bool]$Passed, [string]$Detail, [string]$Severity = 'ERROR') {
    $script:Checks.Add([ordered]@{ area = $Area; name = $Name; passed = $Passed; severity = $Severity; detail = $Detail }) | Out-Null
    if (-not $Passed) {
        $message = "${Area}/${Name}: $Detail"
        if ($Severity -eq 'WARN') { $script:Warnings.Add($message) | Out-Null } else { $script:Failures.Add($message) | Out-Null }
    }
}
function Read-JsonFile([string]$Path) {
    $full = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $RepoRoot $Path }
    if (-not (Test-Path -LiteralPath $full)) { return $null }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $full | ConvertFrom-Json
}
function Read-Text([string]$RelativePath) {
    return [System.IO.File]::ReadAllText((Join-Path $RepoRoot $RelativePath), [System.Text.UTF8Encoding]::new($false))
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
        foreach ($asset in $assets) { if ($asset -like '*.js') { $texts += $client.DownloadString("$BaseUrl$asset") } }
        return [ordered]@{ assets = $assets; text = ($texts -join "`n") }
    } catch { return [ordered]@{ assets = @(); text = ''; error = $_.Exception.Message } }
}
function Get-FreeTcpPort { $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0); $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop(); return $port }
function Find-Chrome {
    $paths = @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe", "$env:LocalAppData\Google\Chrome\Application\chrome.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe", "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe")
    foreach ($candidate in $paths) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
    throw 'Chrome or Edge executable was not found.'
}
function Start-BrowserAudit {
    $chromePath = Find-Chrome
    $script:DebugPort = Get-FreeTcpPort
    $script:ChromeProfileDir = Join-Path $WorkDir ('chrome-profile-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
    $script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @('--headless','--disable-gpu','--disable-extensions','--disable-software-rasterizer','--no-sandbox','--no-first-run','--no-default-browser-check','--disable-background-networking','--remote-allow-origins=*',"--remote-debugging-port=$script:DebugPort","--user-data-dir=$script:ChromeProfileDir",'about:blank') -PassThru -WindowStyle Hidden
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt += 1) { try { Invoke-RestMethod -Uri "http://127.0.0.1:$script:DebugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 250 } }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
}
function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) { Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) { Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue }
}

$children = @()
$browserAudit = $null
$errorMessage = $null
$apiEvidence = [ordered]@{}
try {
    $state = Read-JsonFile '.cursor/session/state.json'
    $platformSource = Read-Text 'frontend/src/features/platform/platformShell.ts'
    $apiSource = Read-Text 'frontend/src/api/liveData.ts'
    $taskCards = Read-Text 'docs/recovery/p0-task-cards.md'
    Add-Check 'state' 'R101 is active and user signoff remains false' ($state -and $state.build_plan.currentBatch -eq 'RECOVERY-R101' -and $state.gates.user_script_passed -eq $false) "currentBatch=$($state.build_plan.currentBatch), userSignoff=$($state.gates.user_script_passed)"
    Add-Check 'contract' 'R101 task card is present' ($taskCards -like '*REC-P0-101 Platform Flow Application Persistence Permission And Readback Closure*') 'task card must describe R101.'
    Add-Check 'source' 'platform pages use API loaders instead of R100 static samples' ($platformSource -like '*loadPlatformFlows*' -and $platformSource -like '*loadPlatformAuthorizations*' -and $platformSource -notlike '*batch_pf_auth_20260708_01*' -and $platformSource -notlike '*REQ-PLAT-AUTH-NEW-100*' -and $platformSource -notlike '*platformApplicationSystemAdminConfig*') 'Flow/Application source must be API-backed and no system-admin shortcut.'
    Add-Check 'source' 'liveData exposes R101 API contract' ($apiSource -like '*/api/v1/platform/flows*' -and $apiSource -like '*/api/v1/platform/applications/authorizations*' -and $apiSource -like '*runPlatformFlowAction*' -and $apiSource -like '*runPlatformAuthorizationAction*') 'frontend API client must call R101 endpoints.'

    $health = Invoke-ApiData -Method Get -Path '/api/v1/health'
    Add-Check 'release' 'current deployed release health is UP' ($health.status -eq 'UP' -and $health.database -eq 'UP' -and $health.schema -eq 'UP' -and $health.redis -eq 'UP') ($health | ConvertTo-Json -Depth 12 -Compress)

    $admin = Invoke-ApiData -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = 'admin'; password = '123123aa'; loginTarget = 'PLATFORM' }
    $adminHeaders = @{ Authorization = "Bearer $($admin.accessToken)" }
    $suffix = (Get-Date -Format 'yyyyMMddHHmmss') + ([guid]::NewGuid().ToString('N').Substring(0,4))
    $member = Invoke-ApiData -Method Post -Path '/api/v1/auth/register-with-system' -Body @{ accountName = "r101_member_$suffix"; mobile = "139$(Get-Random -Minimum 10000000 -Maximum 99999999)"; email = "r101_$suffix@example.test"; password = '123123aa'; systemName = "R101 Member System $suffix"; systemCode = "r101_member_$suffix"; tenantMode = 1; templateCode = 'blank' }
    $memberHeaders = @{ Authorization = "Bearer $($member.accessToken)" }
    Add-Check 'auth' 'admin and ordinary member tokens are available' (($admin.accessToken) -and ($member.accessToken) -and ($member.systemId)) "memberSystem=$($member.systemId)"

    $flowList = Invoke-ApiData -Method Get -Path '/api/v1/platform/flows?pageNo=1&pageSize=20' -Headers $adminHeaders
    $flowCode = "r101_flow_$suffix"
    $createdFlow = Invoke-ApiData -Method Post -Path '/api/v1/platform/flows' -Headers $adminHeaders -Body @{ flowCode = $flowCode; flowName = "R101 Flow $suffix"; triggerSource = 'R101 acceptance'; affectedSystems = @('platform'); nodeSummary = 'api readback closure'; retryPolicy = 'retry twice'; compensationPolicy = 'create compensation task'; idempotencyKey = "idem_flow_$suffix" }
    $flowDetail = Invoke-ApiData -Method Get -Path "/api/v1/platform/flows/$($createdFlow.flowId)" -Headers $adminHeaders
    $updatedFlow = Invoke-ApiData -Method Patch -Path "/api/v1/platform/flows/$($createdFlow.flowId)" -Headers $adminHeaders -Body @{ flowName = "R101 Flow Updated $suffix"; triggerSource = 'R101 acceptance update'; affectedSystems = @('platform'); nodeSummary = 'updated api readback closure'; retryPolicy = 'retry twice'; compensationPolicy = 'create compensation task' }
    $run = Invoke-ApiData -Method Post -Path "/api/v1/platform/flows/$($createdFlow.flowId)/run-check" -Headers $adminHeaders -Body @{ reason = 'R101 run check'; idempotencyKey = "idem_run_$suffix" }
    $retry = Invoke-ApiData -Method Post -Path "/api/v1/platform/flows/$($createdFlow.flowId)/retry" -Headers $adminHeaders -Body @{ reason = 'R101 retry'; idempotencyKey = "idem_retry_$suffix" }
    $compensate = Invoke-ApiData -Method Post -Path "/api/v1/platform/flows/$($createdFlow.flowId)/compensate" -Headers $adminHeaders -Body @{ reason = 'R101 compensate'; idempotencyKey = "idem_comp_$suffix" }
    $memberFlowList = Invoke-ApiData -Method Get -Path '/api/v1/platform/flows?pageNo=1&pageSize=20' -Headers $memberHeaders
    $memberCreateDenied = Invoke-ExpectedDenied -Method Post -Path '/api/v1/platform/flows' -Headers $memberHeaders -Body @{ flowCode = "r101_denied_$suffix"; flowName = 'denied member flow' }
    $memberRetryDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/platform/flows/$($createdFlow.flowId)/retry" -Headers $memberHeaders -Body @{ reason = 'R101 denied retry'; idempotencyKey = "idem_member_retry_$suffix" }
    Add-Check 'flow-api' 'admin Flow create/update/run/retry/compensate read back persisted ids' (($flowList.total -ge 0) -and ($createdFlow.flowId) -and ($flowDetail.flowCode -eq $flowCode) -and ($updatedFlow.flowName -like '*Updated*') -and ($run.runBatchId) -and ($retry.taskId) -and ($compensate.compensationTaskId) -and ($run.boundary -eq 'NO_SYSTEM_BUSINESS_WRITE')) "flowId=$($createdFlow.flowId), run=$($run.runBatchId), retry=$($retry.taskId), comp=$($compensate.compensationTaskId)"
    Add-Check 'flow-permission' 'ordinary member can view Flow but cannot mutate/run' (($memberFlowList.total -ge 0) -and ($memberCreateDenied.status -eq 403) -and ($memberRetryDenied.status -eq 403)) "memberListTotal=$($memberFlowList.total), createDenied=$($memberCreateDenied.status), retryDenied=$($memberRetryDenied.status)"

    $authList = Invoke-ApiData -Method Get -Path '/api/v1/platform/applications/authorizations?pageNo=1&pageSize=20' -Headers $adminHeaders
    $memberAuth = Invoke-ApiData -Method Post -Path '/api/v1/platform/applications/authorizations' -Headers $memberHeaders -Body @{ applicationName = "R101 Member App $suffix"; applicationType = 'platform application'; targetSystemId = 'platform'; targetTenantId = 'platform'; moduleScope = @('platform.authorization','platform.flow'); scope = @('platform:authorization:read','platform:authorization:request'); expiryAt = '2026-12-31'; dataIsolation = 'platform scope'; approvalStatus = 'PENDING'; reason = 'R101 member request'; idempotencyKey = "idem_member_auth_$suffix" }
    $memberAdjustDenied = Invoke-ExpectedDenied -Method Patch -Path "/api/v1/platform/applications/authorizations/$($memberAuth.authorizationId)" -Headers $memberHeaders -Body @{ reason = 'R101 denied adjust'; approvalStatus = 'APPROVED' }
    $memberDisableDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/platform/applications/authorizations/$($memberAuth.authorizationId)/disable" -Headers $memberHeaders -Body @{ reason = 'R101 denied disable' }
    $adminAuth = Invoke-ApiData -Method Post -Path '/api/v1/platform/applications/authorizations' -Headers $adminHeaders -Body @{ applicationName = "R101 Admin App $suffix"; applicationType = 'platform application'; targetSystemId = 'platform'; targetTenantId = 'platform'; moduleScope = @('platform.authorization'); scope = @('platform:authorization:read'); expiryAt = '2026-12-31'; dataIsolation = 'platform scope'; approvalStatus = 'PENDING'; reason = 'R101 admin request'; idempotencyKey = "idem_admin_auth_$suffix" }
    $adjust = Invoke-ApiData -Method Patch -Path "/api/v1/platform/applications/authorizations/$($adminAuth.authorizationId)" -Headers $adminHeaders -Body @{ reason = 'R101 admin adjust'; scope = @('platform:authorization:read','platform:authorization:request'); expiryAt = '2026-12-31'; approvalStatus = 'APPROVED'; idempotencyKey = "idem_admin_adjust_$suffix" }
    $disable = Invoke-ApiData -Method Post -Path "/api/v1/platform/applications/authorizations/$($adminAuth.authorizationId)/disable" -Headers $adminHeaders -Body @{ reason = 'R101 admin disable'; idempotencyKey = "idem_admin_disable_$suffix" }
    Add-Check 'authorization-api' 'Application authorization request/adjust/disable persists and reads ids' (($authList.total -ge 0) -and ($memberAuth.requestId) -and ($memberAuth.authorizationChangeId) -and ($adjust.authorizationChangeId) -and ($disable.status -eq 'DISABLED') -and ($adjust.boundary -eq 'NO_SYSTEM_BUSINESS_WRITE')) "memberRequest=$($memberAuth.requestId), adminChange=$($adjust.authorizationChangeId), disabled=$($disable.status)"
    Add-Check 'authorization-permission' 'ordinary member can request authorization but cannot adjust/disable' (($memberAuth.authorizationId) -and ($memberAdjustDenied.status -eq 403) -and ($memberDisableDenied.status -eq 403)) "memberAuth=$($memberAuth.authorizationId), adjustDenied=$($memberAdjustDenied.status), disableDenied=$($memberDisableDenied.status)"

    $flowTodos = Invoke-ApiData -Method Post -Path '/api/v1/platform/todos/search?pageNo=1&pageSize=20' -Headers $adminHeaders -Body @{ scope = 'platform'; keyword = 'PLATFORM_FLOW'; status = 'PENDING' }
    $flowMessages = Invoke-ApiData -Method Post -Path '/api/v1/platform/messages/search?pageNo=1&pageSize=20' -Headers $adminHeaders -Body @{ keyword = $run.runBatchId; archiveStatus = 'active' }
    $memberAuthMessages = Invoke-ApiData -Method Post -Path '/api/v1/platform/messages/search?pageNo=1&pageSize=20' -Headers $memberHeaders -Body @{ keyword = $memberAuth.requestId; archiveStatus = 'active' }
    $flowLogs = Invoke-ApiData -Method Post -Path '/api/v1/platform/logs/search?pageNo=1&pageSize=20' -Headers $adminHeaders -Body @{ scope = 'PLATFORM'; keyword = $createdFlow.flowId }
    $authLogs = Invoke-ApiData -Method Post -Path '/api/v1/platform/logs/search?pageNo=1&pageSize=20' -Headers $adminHeaders -Body @{ scope = 'PLATFORM'; keyword = $adminAuth.authorizationId }
    Add-Check 'feedback' 'platform todo/message/log feedback is readable' (($flowTodos.page.total -ge 1) -and ($flowMessages.total -ge 1) -and ($memberAuthMessages.total -ge 1) -and ($flowLogs.total -ge 1) -and ($authLogs.total -ge 1)) "flowTodos=$($flowTodos.page.total), flowMessages=$($flowMessages.total), memberAuthMessages=$($memberAuthMessages.total), flowLogs=$($flowLogs.total), authLogs=$($authLogs.total)"
    $apiEvidence = [ordered]@{ flow = [ordered]@{ created = $createdFlow; updated = $updatedFlow; run = $run; retry = $retry; compensate = $compensate; memberCreateDenied = $memberCreateDenied.status; memberRetryDenied = $memberRetryDenied.status }; authorization = [ordered]@{ memberRequest = $memberAuth; adminRequest = $adminAuth; adjust = $adjust; disable = $disable; memberAdjustDenied = $memberAdjustDenied.status; memberDisableDenied = $memberDisableDenied.status }; feedback = [ordered]@{ flowTodos = $flowTodos.page.total; flowMessages = $flowMessages.total; memberAuthMessages = $memberAuthMessages.total; flowLogs = $flowLogs.total; authLogs = $authLogs.total } }

    $children += Invoke-ChildScript 'final-goal-framework-audit' 'scripts/final-goal-framework-audit.ps1' @('-NoFailExit')
    $framework = Read-JsonFile 'docs/evidence/final-goal-framework-audit-result.json'
    Add-Check 'framework' 'framework audit passes with R101 active' ($framework -and $framework.status -eq 'PASS' -and $framework.activeTaskContracts[0].taskId -eq 'REC-P0-101') "status=$($framework.status)"
    $children += Invoke-ChildScript 'final-usability-static-audit' 'scripts/final-usability-static-audit.ps1'
    $static = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    Add-Check 'static' 'final usability static audit has no blockers' ($static -and $static.status -eq 'PASS' -and [int]$static.blockerCount -eq 0) "status=$($static.status), blockers=$($static.blockerCount)"
    $children += Invoke-ChildScript 'final-requirement-coverage-audit' 'scripts/final-requirement-coverage-audit.ps1' @('-NoFailExit') @(0)
    $coverage = Read-JsonFile 'docs/evidence/final-requirement-coverage-audit-result.json'
    Add-Check 'coverage-boundary' 'coverage remains honest and user signoff stays false' ($coverage -and [int]$coverage.missingCount -eq 0 -and [int]$coverage.notClosedCount -gt 0 -and $state.gates.user_script_passed -eq $false) "missing=$($coverage.missingCount), notClosed=$($coverage.notClosedCount), userSignoff=$($state.gates.user_script_passed)"
    $deployedAssets = Read-DeployedAssetText
    $assetText = [string]$deployedAssets.text
    Add-Check 'deployed-asset' 'deployed frontend contains R101 API endpoints and stable DOM markers' ($assetText -like '*/api/v1/platform/flows*' -and $assetText -like '*/api/v1/platform/applications/authorizations*' -and $assetText -like '*platformFlowRow*' -and $assetText -like '*platformAuthorizationRow*' -and $assetText -notlike '*REQ-PLAT-AUTH-NEW-100*') "assets=$($deployedAssets.assets -join ',')"

    Start-BrowserAudit
    $env:R101_BASE_URL = $BaseUrl
    $env:R101_CDP_PORT = [string]$script:DebugPort
    $env:R101_EVIDENCE_DIR = $WorkDir
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeLog = Join-Path $WorkDir 'browser-audit.log'
    $nodeOutput = (& $nodeExe $BrowserScriptPath 2>&1 | Out-String)
    $browserExit = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    Set-Content -LiteralPath $nodeLog -Value $nodeOutput -Encoding UTF8
    Add-Check 'browser' 'R101 browser audit process completed' ($browserExit -eq 0) "exitCode=$browserExit, log=$nodeLog"
    $browserAudit = Read-JsonFile $BrowserOut
    Add-Check 'browser' 'R101 browser audit passed' ($browserAudit -and $browserAudit.status -eq 'PASS' -and [int]$browserAudit.blockerResultCount -eq 0 -and [int]$browserAudit.maxOverflowX -le 2) "status=$($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxOverflow=$($browserAudit.maxOverflowX)"
} catch {
    $errorMessage = $_.Exception.Message
    Add-Check 'script' 'R101 script completed without unhandled exception' $false $errorMessage
} finally { Stop-BrowserAudit }

$status = if ($script:Failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [ordered]@{ status = $status; productStatus = if ($status -eq 'PASS') { 'R101_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY' } else { 'R101_ENGINEERING_EVIDENCE_FAILED' }; task = 'REC-P0-101'; generatedAt = (Get-Date).ToString('o'); baseUrl = $BaseUrl; userSignoff = $false; apiEvidence = $apiEvidence; checks = @($script:Checks.ToArray()); failures = @($script:Failures.ToArray()); warnings = @($script:Warnings.ToArray()); childResults = $children; browserAudit = $browserAudit; error = $errorMessage; accepted = ($status -eq 'PASS') }
$result | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
$summary = @('# REC-P0-101 / R101 Platform Flow Application API Readback', '', "Status: $status as deployed engineering evidence only.", '', "- Base URL: $BaseUrl", '- User signoff: false', "- Browser audit: $($browserAudit.status), blockers=$($browserAudit.blockerResultCount), maxOverflow=$($browserAudit.maxOverflowX)", '', 'R101 proves platform Flow and platform Application are persisted platform objects with API readback, role permission negatives, platform todo/message/log feedback, and NO_SYSTEM_BUSINESS_WRITE boundary. It does not claim final user signoff.', '', '## Checks')
foreach ($check in $script:Checks) { $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }; $summary += "- $mark [$($check.area)] $($check.name): $($check.detail)" }
$summary -join "`r`n" | Set-Content -LiteralPath $SummaryPath -Encoding UTF8
Write-Output ($result | ConvertTo-Json -Depth 80)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }