param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$BrowserDir = Join-Path $EvidenceDir 'screenshots/r89-runtime-file-import-export-recovery-detail'
$ResultPath = Join-Path $EvidenceDir 'r89-runtime-file-import-export-recovery-detail-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r89-runtime-file-import-export-recovery-detail-2026-07-07.md'
$R80ResultPath = Join-Path $EvidenceDir 'r80-live-user-trial-workspace-result.json'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r89-browser-audit.js'
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

function Invoke-FileHttpStatus {
    param([string]$Path, [hashtable]$Headers)
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    try {
        foreach ($key in $Headers.Keys) { [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key]) }
        $response = $client.GetAsync("$BaseUrl$Path", [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        [int]$response.StatusCode
    } finally {
        $client.Dispose()
    }
}

function Invoke-UploadFile {
    param([string]$Path, [string]$FilePath, [hashtable]$Headers = @{}, [string]$ContentType = 'text/csv')
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        foreach ($key in $Headers.Keys) { [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key]) }
        $bytes = [System.IO.File]::ReadAllBytes($FilePath)
        $content = [System.Net.Http.ByteArrayContent]::new($bytes)
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $form.Add($content, 'file', [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync("$BaseUrl$Path", $form).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) { throw "Upload failed: POST $Path -> HTTP $([int]$response.StatusCode) $body" }
        $json = $body | ConvertFrom-Json
        if ($json.code -ne 'SUCCESS') { throw "Upload failed: POST $Path -> $body" }
        return $json.data
    } finally {
        $form.Dispose()
        $client.Dispose()
    }
}

function Add-Check([System.Collections.Generic.List[object]]$Checks, [string]$Area, [string]$Name, [bool]$Passed, [string]$Detail) {
    $Checks.Add([pscustomobject]@{ area = $Area; name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
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

$checks = [System.Collections.Generic.List[object]]::new()
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
$null = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $headers -Body @{ systemId = $systemId; tenantId = $tenantId; reason = 'recovery R89 runtime import export recovery detail evidence' }

$ImportFile = Join-Path $env:TEMP "R89-import-$stamp.csv"
$Csv = "caseTitle,caseAmount,caseOwner`nR89 Imported $stamp,189,R89 Import`n"
[System.IO.File]::WriteAllText($ImportFile, $Csv, [System.Text.UTF8Encoding]::new($false))
$ImportUpload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=IMPORT_EXPORT' -FilePath $ImportFile -Headers $headers -ContentType 'text/csv'
$ImportFileId = [string]$ImportUpload.file.fileId
$Precheck = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/imports/precheck" -Headers ($headers + @{ 'Idempotency-Key' = "import-precheck-R89-$stamp" }) -Body @{
    fileId = $ImportFileId
    templateCode = 'default'
    duplicateStrategy = 'SKIP'
    fieldMapping = @{ caseTitle = 'caseTitle'; caseAmount = 'caseAmount'; caseOwner = 'caseOwner' }
}
$ImportConfirm = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/imports/confirm" -Headers ($headers + @{ 'Idempotency-Key' = "import-confirm-R89-$stamp" }) -Body @{
    precheckId = [string]$Precheck.precheckId
    duplicateStrategy = 'SKIP'
    rollbackSupported = $true
}

$BadPrecheck = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/imports/precheck" -Headers ($headers + @{ 'Idempotency-Key' = "import-precheck-bad-R89-$stamp" }) -Body @{
    fileId = ''
    templateCode = 'default'
    duplicateStrategy = 'REJECT'
    fieldMapping = @{}
}

$ExportAll = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/exports" -Headers ($headers + @{ 'Idempotency-Key' = "export-all-R89-$stamp" }) -Body @{
    scope = 'ALL_MATCHED'
    selectedRecordIds = @()
    fields = @('caseTitle', 'caseAmount', 'caseOwner')
    fileFormat = 'XLSX'
    desensitizeMode = 'PERMISSION'
    filters = @{}
}
$ExportSelected = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/exports" -Headers ($headers + @{ 'Idempotency-Key' = "export-selected-R89-$stamp" }) -Body @{
    scope = 'SELECTED'
    selectedRecordIds = @($existingRecordId)
    fields = @('caseTitle', 'caseAmount', 'caseOwner')
    fileFormat = 'XLSX'
    desensitizeMode = 'PERMISSION'
    filters = @{}
}
$ExportTemplate = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/exports" -Headers ($headers + @{ 'Idempotency-Key' = "export-template-R89-$stamp" }) -Body @{
    scope = 'TEMPLATE_ONLY'
    selectedRecordIds = @()
    fields = @('caseTitle', 'caseAmount', 'caseOwner')
    fileFormat = 'XLSX'
    desensitizeMode = 'PERMISSION'
    filters = @{}
}

$ExportDownloadStatus = Invoke-FileHttpStatus -Path "/api/v1/uploads/files/$($ExportAll.expectedResultFile.fileId)/download" -Headers $headers
$SelectedDownloadStatus = Invoke-FileHttpStatus -Path "/api/v1/uploads/files/$($ExportSelected.expectedResultFile.fileId)/download" -Headers $headers
$TemplateDownloadStatus = Invoke-FileHttpStatus -Path "/api/v1/uploads/files/$($ExportTemplate.expectedResultFile.fileId)/download" -Headers $headers
$BadErrorDownloadStatus = Invoke-FileHttpStatus -Path "/api/v1/uploads/files/$($BadPrecheck.errorFile.fileId)/download" -Headers $headers
$AnonymousExportDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/exports" -Body @{ scope = 'ALL_MATCHED'; selectedRecordIds = @(); fields = @('caseTitle'); fileFormat = 'XLSX'; desensitizeMode = 'PERMISSION'; filters = @{} }
$AnonymousImportDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/imports/precheck" -Body @{ fileId = $ImportFileId; templateCode = 'default'; duplicateStrategy = 'SKIP'; fieldMapping = @{} }
$RuntimeSearch = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records/search" -Headers $headers -Body @{ pageNo = 1; pageSize = 10; keyword = 'R89 Imported'; fieldFilters = @(); sorts = @() }

$BrowserImportFile = Join-Path $env:TEMP "R89-browser-import-$stamp.csv"
$BrowserCsv = "caseTitle,caseAmount,caseOwner`nR89 Browser Imported $stamp,289,R89 Browser`n"
[System.IO.File]::WriteAllText($BrowserImportFile, $BrowserCsv, [System.Text.UTF8Encoding]::new($false))
$BrowserImportUpload = Invoke-UploadFile -Path '/api/v1/uploads/files?sourceType=IMPORT_EXPORT' -FilePath $BrowserImportFile -Headers $headers -ContentType 'text/csv'
$BrowserImportFileId = [string]$BrowserImportUpload.file.fileId

$sourceImportExport = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/runtime/import-export/importExportPanel.ts')
$sourceRuntime = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/features/runtime/records/runtimeRecords.ts')
$sourceCss = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $RepoRoot 'frontend/src/styles.css')
$sourceText = "$sourceImportExport`n$sourceRuntime`n$sourceCss"
$deployedAssetText = Read-DeployedAssetText
$markers = @(
    'runtimeRecoveryR89',
    'runtimeImportRecoveryDetail',
    'runtimeTaskResultDownload',
    'runtimeTaskErrorDownload',
    'runtimeRollbackUnsupportedReason',
    'runtimeSelectedExportState',
    'runtimeExportOpenR89',
    'runtimeImportOpenR89',
    'runtime-file-actions',
    'runtime-recovery-detail-r89'
)
$missingSourceMarkers = @($markers | Where-Object { $sourceText -notmatch [regex]::Escape($_) })
$deployedMarkers = @($markers | Where-Object { $_ -notmatch 'runtime-file-actions|runtime-recovery-detail-r89' })
$missingDeployedMarkers = @($deployedMarkers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })

$BrowserAuditPath = Join-Path $BrowserDir 'runtime-file-import-export-recovery-browser-audit.json'
$chromePath = Find-Chrome
$debugPort = Get-FreeTcpPort
$profileDir = Join-Path $BrowserDir "chrome-profile-$(Get-Date -Format 'yyyyMMddHHmmss')"
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null
$chromeArgs = @('--headless=new', '--disable-gpu', '--disable-extensions', '--no-sandbox', '--no-first-run', '--remote-allow-origins=*', "--remote-debugging-port=$debugPort", "--user-data-dir=$profileDir", 'about:blank')
$chrome = Start-Process -FilePath $chromePath -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
try {
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) {
        try { Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
    $env:R89_BASE_URL = $BaseUrl
    $env:R89_CDP_PORT = [string]$debugPort
    $env:R89_BROWSER_OUT = $BrowserAuditPath
    $env:R89_SYSTEM_ID = $systemId
    $env:R89_MODULE_ID = $moduleId
    $env:R89_IMPORT_FILE_ID = $BrowserImportFileId
    $env:R89_ACCESS_TOKEN = $login.accessToken
    $env:R89_REFRESH_TOKEN = $login.refreshToken
    $env:R89_ACCOUNT_ID = $login.profile.accountId
    $nodeOutput = & 'D:\dev\nodejs24\node.exe' $BrowserScriptPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "R89 browser audit failed: $nodeOutput" }
} finally {
    if ($chrome -and -not $chrome.HasExited) { Stop-Process -Id $chrome.Id -Force -ErrorAction SilentlyContinue }
}
$browserAudit = Read-JsonFile $BrowserAuditPath
$browserResults = @($browserAudit.results)
$browserOverflowCount = @($browserResults | Where-Object { $_.overflowX -gt 1 }).Count
$browserBlockerCount = @($browserResults | Where-Object { $_.blockerText }).Count
$exportBrowserOk = @($browserResults | Where-Object { $_.kind -eq 'export-result-desktop' -and $_.exportPanel -and $_.recoveryState -eq 'ready' -and $_.resultDownloads -ge 1 -and $_.fileActions -ge 2 }).Count -ge 1
$exportEmptyOk = @($browserResults | Where-Object { $_.kind -like 'export-empty*' -and $_.selectedExportState -eq 'empty' -and $_.selectedEmptyReason }).Count -ge 2
$importBrowserOk = @($browserResults | Where-Object { $_.kind -eq 'import-confirm-desktop' -and $_.importPanel -and $_.recoveryState -eq 'ready' -and $_.precheckId -and $_.taskStatus -eq 'SUCCESS' }).Count -ge 1

Add-Check $checks 'state' 'R89 is active and R88 remains accepted engineering evidence' (($state.build_plan.currentBatch -eq 'RECOVERY-R89') -and (@($state.build_plan.acceptedBatches) -contains 'RECOVERY-R88') -and (-not [bool]$state.gates.user_script_passed)) ("currentBatch={0}; user_script_passed={1}" -f $state.build_plan.currentBatch, $state.gates.user_script_passed)
Add-Check $checks 'api-import' 'runtime import precheck and confirm return task recovery detail' (($Precheck.passed -eq $true) -and ($ImportConfirm.task.status -eq 'SUCCESS') -and ([int]$ImportConfirm.task.partialSuccessCount -ge 1) -and ($ImportConfirm.task.rollbackSupported -eq $true)) ("precheck={0}; task={1}; rollback={2}" -f $Precheck.precheckId, $ImportConfirm.task.taskId, $ImportConfirm.task.rollbackSupported)
Add-Check $checks 'api-import-failure' 'failed import precheck returns error file and failed task' (($BadPrecheck.passed -eq $false) -and ($BadPrecheck.task.status -eq 'FAILED') -and -not [string]::IsNullOrWhiteSpace([string]$BadPrecheck.errorFile.fileId) -and ($BadErrorDownloadStatus -eq 200)) ("badPrecheck={0}; errorFile={1}; download={2}" -f $BadPrecheck.precheckId, $BadPrecheck.errorFile.fileId, $BadErrorDownloadStatus)
Add-Check $checks 'api-export' 'all selected and template exports return downloadable result files' (($ExportAll.task.status -eq 'SUCCESS') -and ($ExportSelected.task.status -eq 'SUCCESS') -and ($ExportTemplate.task.status -eq 'SUCCESS') -and ($ExportDownloadStatus -eq 200) -and ($SelectedDownloadStatus -eq 200) -and ($TemplateDownloadStatus -eq 200)) ("all={0}; selected={1}; template={2}" -f $ExportAll.expectedResultFile.fileId, $ExportSelected.expectedResultFile.fileId, $ExportTemplate.expectedResultFile.fileId)
Add-Check $checks 'permission' 'anonymous import and export requests are denied' (($AnonymousExportDenied.status -eq 'DENIED') -and ($AnonymousImportDenied.status -eq 'DENIED')) ("export={0}; import={1}" -f $AnonymousExportDenied.path, $AnonymousImportDenied.path)
Add-Check $checks 'runtime-search' 'imported row is searchable and hidden field stays absent' (([int]$RuntimeSearch.page.total -ge 1) -and (@($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count -eq 0)) ("total={0}; hiddenSecretNote={1}" -f $RuntimeSearch.page.total, (@($RuntimeSearch.listSchema.columns | Where-Object { $_.fieldCode -eq 'secretNote' }).Count))
Add-Check $checks 'frontend-source' 'R89 source markers exist for recovery detail and file actions' ($missingSourceMarkers.Count -eq 0) ("missing={0}" -f ($missingSourceMarkers -join ','))
Add-Check $checks 'deployed-asset' 'deployed frontend asset contains R89 runtime recovery markers' (($deployedAssetText.Length -gt 0) -and ($missingDeployedMarkers.Count -eq 0)) ("assetLength={0}; missing={1}" -f $deployedAssetText.Length, ($missingDeployedMarkers -join ','))
Add-Check $checks 'browser-export' 'browser export panel exposes empty selected state and result file actions' ($exportBrowserOk -and $exportEmptyOk) ("results={0}" -f ($browserResults | ConvertTo-Json -Depth 8 -Compress))
Add-Check $checks 'browser-import' 'browser import panel exposes precheck confirm and recovery detail' $importBrowserOk ("results={0}" -f ($browserResults | ConvertTo-Json -Depth 8 -Compress))
Add-Check $checks 'browser-containment' 'browser R89 panels have no horizontal overflow or blocker text' (($browserOverflowCount -eq 0) -and ($browserBlockerCount -eq 0)) ("overflow={0}; blockers={1}" -f $browserOverflowCount, $browserBlockerCount)

$failed = @($checks | Where-Object { -not $_.passed })
$status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]([ordered]@{
    status = $status
    productStatus = 'R89_RUNTIME_FILE_IMPORT_EXPORT_RECOVERY_DETAIL_ENGINEERING_EVIDENCE_ONLY'
    task = 'REC-P0-089'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    accepted = ($status -eq 'PASS')
    checks = @($checks.ToArray())
    data = [ordered]@{
        systemId = $systemId
        tenantId = $tenantId
        moduleId = $moduleId
        importPrecheckId = $Precheck.precheckId
        importTaskId = $ImportConfirm.task.taskId
        failedPrecheckId = $BadPrecheck.precheckId
        failedErrorFileId = $BadPrecheck.errorFile.fileId
        exportAllTaskId = $ExportAll.task.taskId
        exportAllResultFileId = $ExportAll.expectedResultFile.fileId
        exportSelectedTaskId = $ExportSelected.task.taskId
        exportTemplateTaskId = $ExportTemplate.task.taskId
        browserAudit = $BrowserAuditPath
        browserOverflowCount = $browserOverflowCount
        browserBlockerCount = $browserBlockerCount
    }
})
$result | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -LiteralPath $ResultPath
$summary = @('# R89 Runtime File Import Export Recovery Detail', '', "Status: `$status", '', 'This is engineering evidence only. It does not close user signoff.', '', '## Checks', '')
foreach ($check in $checks) {
    $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
    $summary += ('- `{0}` {1}: {2} - {3}' -f $mark, $check.area, $check.name, $check.detail)
}
$summary += ''
$summary += '## Data'
$summary += ''
$summary += ('- System: `{0}`' -f $systemId)
$summary += ('- Module: `{0}`' -f $moduleId)
$summary += ('- Import task: `{0}`' -f $ImportConfirm.task.taskId)
$summary += ('- Failed precheck error file: `{0}`' -f $BadPrecheck.errorFile.fileId)
$summary += ('- Export result file: `{0}`' -f $ExportAll.expectedResultFile.fileId)
$summary += ('- Browser audit: `{0}`' -f $BrowserAuditPath)
$summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R89 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }