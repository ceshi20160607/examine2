param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$BrowserDir = Join-Path $EvidenceDir 'screenshots/r87-todo-message-workbench-usability'
$ResultPath = Join-Path $EvidenceDir 'r87-todo-message-workbench-usability-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r87-todo-message-workbench-usability-2026-07-07.md'
$R80ResultPath = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r87-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $BrowserDir | Out-Null

function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing JSON file: $Path" }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
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
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') { throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)" }
    return $response.data
}

function Invoke-ExpectedDenied {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body
    } catch {
        if ($_.Exception.Message -match 'HTTP 401|HTTP 403') { return @{ status = 'DENIED'; path = $Path } }
        throw
    }
    throw "Expected denied request but it succeeded: $Method $Path"
}

function Read-DeployedAssetText {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $assetMatches = [regex]::Matches($index, 'src="(?<src>/assets/[^""<>]+\.js)"')
        if ($assetMatches.Count -eq 0) { return '' }
        $texts = New-Object System.Collections.Generic.List[string]
        foreach ($match in $assetMatches) {
            $src = $match.Groups['src'].Value
            $texts.Add((Invoke-WebRequest -Uri "$BaseUrl$src" -UseBasicParsing -TimeoutSec 30).Content) | Out-Null
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

$checks = New-Object System.Collections.Generic.List[object]
function Add-Check([string]$Area, [string]$Name, [bool]$Passed, [string]$Detail) {
    $checks.Add([pscustomobject]@{ area = $Area; name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
}

$r80 = Read-JsonFile $R80ResultPath
$state = Read-JsonFile (Join-Path $RepoRoot '.cursor/session/state.json')
$trial = $r80.trialPack.workflowTodoMessage
$login = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.approverLoginName; password = [string]$trial.password; loginTarget = 'PLATFORM' }
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$systemId = [string]$trial.systemId
$tenantId = [string]$trial.tenantId
$null = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $headers -Body @{ systemId = $systemId; tenantId = $tenantId; reason = 'recovery R87 todo/message workbench evidence' }

$pendingTodos = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/todos/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ scope = 'system'; status = 'PENDING' }
$handledTodos = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/todos/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ scope = 'system'; status = 'HANDLED' }
$activeMessagesBefore = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ systemId = $systemId; archiveStatus = 'active' }
$archivedMessagesBefore = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ systemId = $systemId; archiveStatus = 'archived' }
$messageAction = $null
$activeMessage = @($activeMessagesBefore.records | Select-Object -First 1)
if ($activeMessage.Count -gt 0) {
    $messageId = [string]$activeMessage[0].messageId
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/mark-read" -Headers $headers -Body @{ messageIds = @($messageId); reason = 'R87 message readback evidence' }
    $readMessages = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ systemId = $systemId; readStatus = 'read'; archiveStatus = 'active' }
    $archiveResult = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/archive" -Headers $headers -Body @{ messageIds = @($messageId); reason = 'R87 message archive readback evidence' }
    $archivedMessagesAfter = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=10" -Headers $headers -Body @{ systemId = $systemId; archiveStatus = 'archived' }
    $messageAction = [ordered]@{ messageId = $messageId; readTotal = $readMessages.total; archiveAffected = $archiveResult.affectedCount; archivedAfter = $archivedMessagesAfter.total; traceId = $archiveResult.traceId }
} else {
    $archivedMessagesAfter = $archivedMessagesBefore
}

$deniedTodos = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/todos/search?pageNo=1&pageSize=1" -Body @{ scope = 'system'; status = 'PENDING' }
$deniedMessages = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/messages/search?pageNo=1&pageSize=1" -Body @{ systemId = $systemId; archiveStatus = 'active' }

$sourceShell = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/system-shell/systemShell.ts')
$sourceCss = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/styles.css')
$deployedAssetText = Read-DeployedAssetText
$markers = @('systemTodoWorkbenchR87','systemTodoDetailPanel','systemTodoActionResult','systemTodoOpenTarget','systemMessageWorkbenchR87','systemMessageToolbar','systemMessageMarkRead','systemMessageArchive','systemMessageActionResult','todo-workbench-layout','message-meta-grid')
$sourceText = "$sourceShell`n$sourceCss"
$missingSourceMarkers = @($markers | Where-Object { $sourceText -notmatch [regex]::Escape($_) })
$missingDeployedMarkers = @($markers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })

$browserAuditPath = Join-Path $BrowserDir 'todo-message-workbench-browser-audit.json'
$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$profileDir = Join-Path $BrowserDir "chrome-profile-$(Get-Date -Format 'yyyyMMddHHmmss')"
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null
$chromeArgs = @('--headless', '--disable-gpu', '--disable-extensions', '--no-sandbox', '--no-first-run', '--remote-allow-origins=*', "--remote-debugging-port=$debugPort", "--user-data-dir=$profileDir", 'about:blank')
$chrome = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
try {
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) {
        try { Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
    $env:R87_BASE_URL = $BaseUrl
    $env:R87_CDP_PORT = [string]$debugPort
    $env:R87_BROWSER_OUT = $browserAuditPath
    $env:R87_SYSTEM_ID = $systemId
    $env:R87_ACCESS_TOKEN = $login.accessToken
    $env:R87_REFRESH_TOKEN = $login.refreshToken
    $env:R87_ACCOUNT_ID = $login.profile.accountId
    $nodeOutput = & node $BrowserScriptPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "R87 browser audit failed: $nodeOutput" }
} finally {
    if ($chrome -and -not $chrome.HasExited) { Stop-Process -Id $chrome.Id -Force -ErrorAction SilentlyContinue }
}
$browserAudit = Read-JsonFile $browserAuditPath
$browserResults = @($browserAudit.results)
$browserOverflowCount = @($browserResults | Where-Object { $_.overflowX -gt 1 }).Count
$browserBlockerCount = @($browserResults | Where-Object { $_.blockerText }).Count
$todoBrowserOk = @($browserResults | Where-Object { $_.todoWorkbench -and $_.todoLayout -and $_.todoDetailPanel }).Count -ge 2
$messageBrowserOk = @($browserResults | Where-Object { $_.messageCenter -and $_.messageToolbar }).Count -ge 2
$messageItemEvidence = @($browserResults | Where-Object { $_.messageItems -ge 1 -and $_.messageMarkReadButtons -ge 1 -and $_.messageArchiveButtons -ge 1 }).Count -ge 1

Add-Check 'state' 'R87 is active and R86 remains accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R87') -and (@($state.build_plan.acceptedBatches) -contains 'RECOVERY-R86')) ("currentBatch={0}" -f $state.build_plan.currentBatch)
Add-Check 'api-readback' 'approver scoped todo and message APIs are readable' (($pendingTodos.page.total -ge 0) -and ($handledTodos.page.total -ge 0) -and (($activeMessagesBefore.total + $archivedMessagesBefore.total) -ge 1)) ("pendingTodos={0}, handledTodos={1}, activeMessagesBefore={2}, archivedBefore={3}" -f $pendingTodos.page.total, $handledTodos.page.total, $activeMessagesBefore.total, $archivedMessagesBefore.total)
Add-Check 'message-state' 'message read/archive state is backed by API readback when active message exists, or retained archived state exists' (($null -ne $messageAction -and $messageAction.archiveAffected -ge 1 -and $messageAction.archivedAfter -ge 1) -or ($archivedMessagesAfter.total -ge 1)) ("action={0}, archivedAfter={1}" -f ($messageAction | ConvertTo-Json -Depth 10 -Compress), $archivedMessagesAfter.total)
Add-Check 'permission' 'anonymous todo and message APIs are denied' (($deniedTodos.status -eq 'DENIED') -and ($deniedMessages.status -eq 'DENIED')) ("{0}; {1}" -f $deniedTodos.path, $deniedMessages.path)
Add-Check 'frontend-source' 'R87 todo/message workbench source markers exist' ($missingSourceMarkers.Count -eq 0) ("missing={0}" -f ($missingSourceMarkers -join ','))
Add-Check 'deployed-asset' 'deployed frontend asset contains R87 markers' (($deployedAssetText.Length -gt 0) -and ($missingDeployedMarkers.Count -eq 0)) ("assetLength={0}, missing={1}" -f $deployedAssetText.Length, ($missingDeployedMarkers -join ','))
Add-Check 'browser-todo' 'browser todo workbench exposes layout and detail/empty markers on desktop and mobile' $todoBrowserOk ("results={0}" -f ($browserResults | ConvertTo-Json -Depth 8 -Compress))
Add-Check 'browser-message' 'browser message workbench exposes toolbar and message action markers' ($messageBrowserOk -and ($messageItemEvidence -or $archivedMessagesAfter.total -ge 1)) ("messageItemEvidence={0}, archivedAfter={1}" -f $messageItemEvidence, $archivedMessagesAfter.total)
Add-Check 'browser-containment' 'browser todo/message pages have no horizontal overflow or blocker text' (($browserOverflowCount -eq 0) -and ($browserBlockerCount -eq 0)) ("overflow={0}, blockers={1}" -f $browserOverflowCount, $browserBlockerCount)
Add-Check 'signoff-boundary' 'user signoff remains false' (-not [bool]$state.gates.user_script_passed) ("user_script_passed={0}" -f $state.gates.user_script_passed)

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R87_TODO_MESSAGE_WORKBENCH_USABILITY_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-087'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    accepted = ($status -eq 'PASS')
    checks = @($checks.ToArray())
    data = [ordered]@{
        systemId = $systemId
        tenantId = $tenantId
        pendingTodoTotal = $pendingTodos.page.total
        handledTodoTotal = $handledTodos.page.total
        activeMessageBeforeTotal = $activeMessagesBefore.total
        archivedMessageAfterTotal = $archivedMessagesAfter.total
        messageAction = $messageAction
        browserAudit = $browserAuditPath
        browserOverflowCount = $browserOverflowCount
        browserBlockerCount = $browserBlockerCount
    }
})
$result | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
$summary = @('# R87 Todo Message Workbench Usability', '', "Status: `$status", '', 'This is engineering evidence only. It does not close user signoff.', '', '## Checks', '')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''
$summary += '## Data'
$summary += ''
$summary += ('- System: `{0}`' -f $systemId)
$summary += ('- Pending todos: `{0}`' -f $pendingTodos.page.total)
$summary += ('- Handled todos: `{0}`' -f $handledTodos.page.total)
$summary += ('- Active messages before: `{0}`' -f $activeMessagesBefore.total)
$summary += ('- Archived messages after: `{0}`' -f $archivedMessagesAfter.total)
$summary += ('- Browser audit: `{0}`' -f $browserAuditPath)
$summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R87 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }