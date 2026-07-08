param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$BrowserDir = Join-Path $EvidenceDir 'screenshots/r88-runtime-efficiency-entry-search-recent-draft'
$ResultPath = Join-Path $EvidenceDir 'r88-runtime-efficiency-entry-search-recent-draft-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md'
$R80ResultPath = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r88-browser-audit.js'
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
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers
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
$trial = $r80.trialPack.runtimeDailyUse
$systemId = [string]$trial.systemId
$tenantId = [string]$trial.tenantId
$moduleId = [string]$trial.moduleId
$existingRecordId = [string]$trial.existingRecordId
$stamp = Get-Date -Format 'yyyyMMddHHmmss'

$login = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.normalLoginName; password = [string]$trial.password; loginTarget = 'PLATFORM' }
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$null = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $headers -Body @{ systemId = $systemId; tenantId = $tenantId; reason = 'recovery R88 runtime efficiency evidence' }
$readonlyLogin = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ loginName = [string]$trial.readonlyLoginName; password = [string]$trial.password; loginTarget = 'PLATFORM' }
$readonlyHeaders = @{ Authorization = "Bearer $($readonlyLogin.accessToken)" }
$null = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $readonlyHeaders -Body @{ systemId = $systemId; tenantId = $tenantId; reason = 'recovery R88 readonly denial evidence' }

$searchBefore = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records/search" -Headers $headers -Body @{ pageNo = 1; pageSize = 10; keyword = ''; fieldFilters = @(); sorts = @() }
$draftTitle = "R88 Draft $stamp"
$createdTitle = "R88 Created $stamp"
$draft = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/drafts" -Headers $headers -Body @{
    fieldValues = @{ caseTitle = $draftTitle; caseAmount = 88; caseOwner = 'R88 Normal' }
    childRows = @{}
    attachmentIds = @()
    clientVersion = 'recovery-r88'
    sourceType = 'WEB_FORM'
}
$mutation = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records" -Headers $headers -Body @{
    draftId = $draft.draftId
    fieldValues = @{ caseTitle = $createdTitle; caseAmount = 188; caseOwner = 'R88 Normal' }
    childRows = @{}
    attachmentIds = @()
    sourceType = 'WEB_FORM'
}
$searchAfter = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records/search" -Headers $headers -Body @{ pageNo = 1; pageSize = 10; keyword = $createdTitle; fieldFilters = @(); sorts = @() }
$createdRows = @($searchAfter.page.records | Where-Object { ($_.fieldValues.caseTitle -eq $createdTitle) -or ($_.title -eq $createdTitle) -or ($_.recordId -eq $mutation.recordId) })
$detail = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records/$($mutation.recordId)" -Headers $headers
$deniedReadonlyCreate = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records" -Headers $readonlyHeaders -Body @{ fieldValues = @{ caseTitle = "R88 Forbidden $stamp"; caseAmount = 1; caseOwner = 'Readonly' }; childRows = @{}; attachmentIds = @(); sourceType = 'WEB_FORM' }
$deniedAnonymousSearch = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records/search" -Body @{ pageNo = 1; pageSize = 1; keyword = $createdTitle; fieldFilters = @(); sorts = @() }
$deniedAnonymousDraft = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/drafts" -Body @{ fieldValues = @{ caseTitle = 'anonymous' }; childRows = @{}; sourceType = 'WEB_FORM' }

$sourceRuntime = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/runtime/records/runtimeRecords.ts')
$sourceShell = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/system-shell/systemShell.ts')
$sourceCss = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/styles.css')
$sourceText = "$sourceRuntime`n$sourceShell`n$sourceCss"
$deployedAssetText = Read-DeployedAssetText
$markers = @(
    'systemDashboardRuntimeEfficiencyR88',
    'dashboardRuntimeSearchR88',
    'dashboardRuntimeQuickCreateR88',
    'dashboardRuntimeRecentItemR88',
    'dashboardRuntimeDraftItemR88',
    'runtimeEfficiencyR88',
    'runtimeEfficiencyRecentList',
    'runtimeEfficiencyDraftList',
    'runtimeSaveDraftR88',
    'runtimeSaveRecordR88',
    'runtimeFormResultR88',
    'runtimeFieldErrorR88',    'runtime-efficiency-strip',
    'dashboard-runtime-efficiency'
)
$missingSourceMarkers = @($markers | Where-Object { $sourceText -notmatch [regex]::Escape($_) })
$deployedMarkers = @($markers | Where-Object { $_ -notmatch 'runtime-efficiency-strip|dashboard-runtime-efficiency' })
$missingDeployedMarkers = @($deployedMarkers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })

$browserAuditPath = Join-Path $BrowserDir 'runtime-efficiency-browser-audit.json'
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
    $env:R88_BASE_URL = $BaseUrl
    $env:R88_CDP_PORT = [string]$debugPort
    $env:R88_BROWSER_OUT = $browserAuditPath
    $env:R88_SYSTEM_ID = $systemId
    $env:R88_MODULE_ID = $moduleId
    $env:R88_DRAFT_ID = [string]$draft.draftId
    $env:R88_RECORD_ID = [string]$existingRecordId
    $env:R88_DRAFT_TITLE = $draftTitle
    $env:R88_RECORD_TITLE = "R88 Recent $existingRecordId"
    $env:R88_ACCESS_TOKEN = $login.accessToken
    $env:R88_REFRESH_TOKEN = $login.refreshToken
    $env:R88_ACCOUNT_ID = $login.profile.accountId
    $nodeOutput = & 'D:\dev\nodejs24\node.exe' $BrowserScriptPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "R88 browser audit failed: $nodeOutput" }
} finally {
    if ($chrome -and -not $chrome.HasExited) { Stop-Process -Id $chrome.Id -Force -ErrorAction SilentlyContinue }
}
$browserAudit = Read-JsonFile $browserAuditPath
$browserResults = @($browserAudit.results)
$browserOverflowCount = @($browserResults | Where-Object { $_.overflowX -gt 1 }).Count
$browserBlockerCount = @($browserResults | Where-Object { $_.blockerText }).Count
$dashboardBrowserOk = @($browserResults | Where-Object { $_.dashboardEfficiency -and $_.dashboardRecentItems -ge 1 -and $_.dashboardDraftItems -ge 1 -and $_.dashboardSearch }).Count -ge 2
$runtimeBrowserOk = @($browserResults | Where-Object { $_.runtimeEfficiency -and $_.runtimeDraftItems -ge 1 -and $_.runtimeQuickCreate -and $_.draftRestored -eq $draftTitle }).Count -ge 2
$validationBrowserOk = @($browserResults | Where-Object { $_.kind -like '*validation*' -and $_.fieldErrors -ge 1 -and $_.validationField }).Count -ge 1

Add-Check 'state' 'R88 is active and R87 remains accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R88') -and (@($state.build_plan.acceptedBatches) -contains 'RECOVERY-R87')) ("currentBatch={0}" -f $state.build_plan.currentBatch)
Add-Check 'api-search' 'normal member runtime search and created record readback work' (($searchBefore.page.total -ge 0) -and ($createdRows.Count -ge 1) -and ([string]$mutation.recordId -ne '')) ("before={0}, createdRows={1}, record={2}" -f $searchBefore.page.total, $createdRows.Count, $mutation.recordId)
Add-Check 'api-draft' 'runtime draft save returns a resumable draft id' ([string]$draft.draftId -ne '') ("draftId={0}" -f $draft.draftId)
Add-Check 'permission' 'readonly and anonymous runtime writes/searches are denied' (($deniedReadonlyCreate.status -eq 'DENIED') -and ($deniedAnonymousSearch.status -eq 'DENIED') -and ($deniedAnonymousDraft.status -eq 'DENIED')) ("readonly={0}; anonymousSearch={1}; anonymousDraft={2}" -f $deniedReadonlyCreate.path, $deniedAnonymousSearch.path, $deniedAnonymousDraft.path)
Add-Check 'frontend-source' 'R88 dashboard/runtime efficiency source markers exist' ($missingSourceMarkers.Count -eq 0) ("missing={0}" -f ($missingSourceMarkers -join ','))
Add-Check 'deployed-asset' 'deployed frontend asset contains R88 runtime efficiency markers' (($deployedAssetText.Length -gt 0) -and ($missingDeployedMarkers.Count -eq 0)) ("assetLength={0}, missing={1}" -f $deployedAssetText.Length, ($missingDeployedMarkers -join ','))
Add-Check 'browser-dashboard' 'browser dashboard exposes runtime search/recent/draft entries on desktop and mobile' $dashboardBrowserOk ("results={0}" -f ($browserResults | ConvertTo-Json -Depth 8 -Compress))
Add-Check 'browser-runtime' 'browser runtime page restores draft and exposes recent/draft/quick-create strip' $runtimeBrowserOk ("draftTitle={0}" -f $draftTitle)
Add-Check 'browser-validation' 'browser create form marks a required field error after empty save' $validationBrowserOk ("validationResults={0}" -f ($browserResults | Where-Object { $_.kind -like '*validation*' } | ConvertTo-Json -Depth 8 -Compress))
Add-Check 'browser-containment' 'browser R88 pages have no horizontal overflow or blocker text' (($browserOverflowCount -eq 0) -and ($browserBlockerCount -eq 0)) ("overflow={0}, blockers={1}" -f $browserOverflowCount, $browserBlockerCount)
Add-Check 'signoff-boundary' 'user signoff remains false' (-not [bool]$state.gates.user_script_passed) ("user_script_passed={0}" -f $state.gates.user_script_passed)

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R88_RUNTIME_EFFICIENCY_ENTRY_SEARCH_RECENT_DRAFT_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-088'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    accepted = ($status -eq 'PASS')
    checks = @($checks.ToArray())
    data = [ordered]@{
        systemId = $systemId
        tenantId = $tenantId
        moduleId = $moduleId
        existingRecordId = $existingRecordId
        draftId = $draft.draftId
        createdRecordId = $mutation.recordId
        createdSearchRows = $createdRows.Count
        browserAudit = $browserAuditPath
        browserOverflowCount = $browserOverflowCount
        browserBlockerCount = $browserBlockerCount
    }
})
$result | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
$summary = @('# R88 Runtime Efficiency Entry Search Recent Draft', '', "Status: `$status", '', 'This is engineering evidence only. It does not close user signoff.', '', '## Checks', '')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''
$summary += '## Data'
$summary += ''
$summary += ('- System: `{0}`' -f $systemId)
$summary += ('- Module: `{0}`' -f $moduleId)
$summary += ('- Draft: `{0}`' -f $draft.draftId)
$summary += ('- Created record: `{0}`' -f $mutation.recordId)
$summary += ('- Browser audit: `{0}`' -f $browserAuditPath)
$summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R88 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }