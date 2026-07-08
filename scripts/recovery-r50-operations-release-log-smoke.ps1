param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    return $response.data
}

function Invoke-ExpectedHttpStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int[]]$ExpectedStatus,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        Assert-True -Condition ($ExpectedStatus -contains $status) -Message "Expected HTTP $($ExpectedStatus -join '/') for $Method $Path but got HTTP $status. Body=$body"
        return @{ status = $status; body = $body }
    }
    throw "Expected HTTP $($ExpectedStatus -join '/') but request succeeded: $Method $Path"
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function ConvertFrom-JsonOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Output,
        [Parameter(Mandatory = $true)][string]$Label
    )
    $start = $Output.IndexOf('{')
    $end = $Output.LastIndexOf('}')
    if ($start -lt 0 -or $end -lt $start) {
        throw "$Label did not emit a JSON object. Output=$Output"
    }
    return $Output.Substring($start, $end - $start + 1) | ConvertFrom-Json
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Get-IndexAssets {
    param([string]$Content)
    $assets = New-Object System.Collections.Generic.List[string]
    foreach ($match in [regex]::Matches($Content, '/assets/[^"''> ]+')) {
        $assets.Add($match.Value) | Out-Null
    }
    return @($assets | Sort-Object)
}

function Invoke-WebText {
    param([string]$Uri)
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Get -UseBasicParsing -TimeoutSec 60
        return [string]$response.Content
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        throw "Web request failed: GET $Uri -> HTTP $status $body"
    }
}

function Find-Chrome {
    $paths = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($path in $paths) {
        if ($path -and (Test-Path -LiteralPath $path)) {
            return $path
        }
    }
    throw 'Chrome or Edge executable was not found.'
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace([string]$systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r50 operations release log cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r50-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Assert-LogByTrace {
    param(
        [string]$Scope,
        [string]$TraceId,
        [string]$ExpectedActionPart,
        [string]$ExpectedResult,
        [string]$SystemId = $null
    )
    $path = if ($Scope -eq 'SYSTEM') { "/api/v1/systems/$SystemId/logs/search?pageNo=1&pageSize=20" } else { '/api/v1/platform/logs/search?pageNo=1&pageSize=20' }
    $logs = Invoke-Api -Method 'Post' -Path $path -Headers $script:AdminHeaders -Body @{ traceId = $TraceId }
    $records = @($logs.records)
    Assert-True -Condition ($records.Count -ge 1) -Message "No $Scope audit log found for traceId=$TraceId"
    $record = $records[0]
    Assert-True -Condition ([string]$record.traceId -eq $TraceId) -Message "Audit log trace mismatch: $($record | ConvertTo-Json -Depth 20 -Compress)"
    Assert-True -Condition ([string]$record.action -like "*$ExpectedActionPart*") -Message "Audit action mismatch for ${TraceId}: $($record.action)"
    Assert-True -Condition ([string]$record.result -eq $ExpectedResult) -Message "Audit result mismatch for ${TraceId}: $($record.result)"
    $detailPath = if ($Scope -eq 'SYSTEM') { "/api/v1/systems/$SystemId/logs/$($record.logId)" } else { "/api/v1/platform/logs/$($record.logId)" }
    $detail = Invoke-Api -Method 'Get' -Path $detailPath -Headers $script:AdminHeaders
    Assert-True -Condition ([string]$detail.traceId -eq $TraceId -and [string]$detail.auditLogId -eq [string]$record.auditLogId) -Message "Audit detail did not read back trace/audit ids for $TraceId"
    return $record
}

trap {
    $originalError = $_
    Stop-BrowserAudit
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    $failure = [ordered]@{
        status = 'FAIL'
        task = 'REC-P0-050'
        frc = 'FRC-5B Operations, logs, release, and launch-rule closure'
        baseUrl = $BaseUrl
        generatedAt = (Get-Date).ToString('o')
        error = $originalError.Exception.Message
        cleanup = @($script:CleanupResult)
    }
    $failure | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -Path $ResultFile
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw $originalError
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r50-operations-release-log'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r50-operations-release-log-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r50-operations-release-log-2026-07-01.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$ReleaseDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT'
$ServerScript = Join-Path $ReleaseDir 'backend\server.sh'
Assert-True -Condition (Test-Path -LiteralPath $ServerScript) -Message 'Packaged backend server.sh is missing.'
$serverText = Get-Content -Raw -Encoding UTF8 $ServerScript
foreach ($commandName in @('start', 'stop', 'restart', 'status', 'health')) {
    Assert-True -Condition ($serverText -match "$commandName\)") -Message "server.sh does not expose $commandName command."
}
$localIndexPath = Join-Path $ReleaseDir 'frontend\index.html'
Assert-True -Condition (Test-Path -LiteralPath $localIndexPath) -Message "Release frontend index.html is missing: $localIndexPath"
$localAssets = Get-IndexAssets (Get-Content -LiteralPath $localIndexPath -Raw)
$remoteAssets = Get-IndexAssets (Invoke-WebText -Uri "$BaseUrl/index.html")
Assert-True -Condition (($localAssets -join '|') -eq ($remoteAssets -join '|')) `
    -Message "Deployed frontend assets differ from release assets. release=[$($localAssets -join ', ')], deployed=[$($remoteAssets -join ', ')]"
$verify = [pscustomobject]@{ status = 'PASS' }

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = "trc_r50_system_create_$script:Suffix" }) -Body @{
    systemName = "R50 Operations $script:Suffix"
    systemCode = "r50_ops_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenant id missing.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r50 operations setup'
}

$Password = 'Aa123456!'
$NormalLoginName = "r50_normal_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "17$((Get-Date).ToString('HHmmssfff'))"
    email = "r50_normal_$script:Suffix@example.com"
    password = $Password
    systemName = "R50 Normal Owned $script:Suffix"
    systemCode = "r50_normal_$script:Suffix"
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId
$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }

$tracePlatformHealth = "trc_r50_platform_health_$script:Suffix"
$PlatformHealth = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/health-check' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $tracePlatformHealth }) -Body @{
    checkType = 'FULL'
    idempotencyKey = "r50-platform-health-$script:Suffix"
}
$traceFeature = "trc_r50_feature_flag_$script:Suffix"
$FeatureFlag = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/feature-flags/flag_gray_publish' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceFeature }) -Body @{
    status = 1
    rules = 'role=PLATFORM_ROOT; percent=30'
    rollbackVersion = "flag_v$script:Suffix"
}
$traceQuota = "trc_r50_quota_$script:Suffix"
$Quota = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/quotas/quota_openapi' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceQuota }) -Body @{
    limit = 900
    warnThreshold = 720
}
$traceRate = "trc_r50_rate_$script:Suffix"
$RateLimit = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/rate-limit-policies/rl_openapi_app' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceRate }) -Body @{
    limitRule = 'dimension=appKey; window=1m; limit=900; overflow=REJECT_WITH_CODE'
    status = 1
}
$traceBackup = "trc_r50_backup_$script:Suffix"
$BackupTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/backups' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceBackup }) -Body @{
    backupType = 'FULL'
    scope = 'PLATFORM'
    boundaryPayload = @{ includes = @('database', 'files', 'config', 'secret_refs') }
    idempotencyKey = "r50-backup-$script:Suffix"
}
$traceRestore = "trc_r50_restore_$script:Suffix"
$RestoreTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/backups/backup_20260623_001/restore-drill' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceRestore }) -Body @{
    drillScope = 'CONFIG_AND_FILES'
    dryRun = $true
    idempotencyKey = "r50-restore-$script:Suffix"
}
$traceArchive = "trc_r50_archive_$script:Suffix"
$ArchiveTask = Invoke-Api -Method 'Post' -Path '/api/v1/platform/ops/archive-restore-requests' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceArchive }) -Body @{
    scope = 'PLATFORM'
    objectType = 'BUSINESS_LOG'
    archiveCondition = 'older_than_180_days'
    reason = 'R50 archive restore dry-run'
    idempotencyKey = "r50-archive-$script:Suffix"
}
$Deployments = Invoke-Api -Method 'Get' -Path '/api/v1/platform/ops/deployments?pageNo=1&pageSize=10' -Headers $script:AdminHeaders
$DeploymentId = if (@($Deployments.records).Count -gt 0) { $Deployments.records[0].deploymentId } else { 'deploy_20260623_001' }
$traceRollback = "trc_r50_rollback_$script:Suffix"
$RollbackTask = Invoke-Api -Method 'Post' -Path "/api/v1/platform/ops/deployments/$DeploymentId/rollback" -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceRollback }) -Body @{
    targetVersion = 'previous-stable'
    dryRun = $true
    confirmNoDestructiveScript = $true
    idempotencyKey = "r50-rollback-$script:Suffix"
}
$CachePolicy = Invoke-Api -Method 'Get' -Path '/api/v1/platform/ops/api-cache-policy' -Headers $script:AdminHeaders
$traceCache = "trc_r50_cache_$script:Suffix"
$CacheUpdated = Invoke-Api -Method 'Patch' -Path '/api/v1/platform/ops/api-cache-policy' -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceCache }) -Body @{
    keyRule = 'system:{systemId}:member:{memberId}:perm'
    invalidationRule = 'role_permission_changed OR member_binding_changed OR publish_version_changed'
    status = 1
}
$traceSystemHealth = "trc_r50_system_health_$script:Suffix"
$SystemHealth = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/ops/health-check" -Headers ($script:AdminHeaders + @{ 'X-Trace-Id' = $traceSystemHealth }) -Body @{
    checkType = 'SYSTEM'
    idempotencyKey = "r50-system-health-$script:Suffix"
}

foreach ($task in @($BackupTask, $RestoreTask, $ArchiveTask, $RollbackTask)) {
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$task.taskId)) -Message "Ops task did not return taskId: $($task | ConvertTo-Json -Depth 20 -Compress)"
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$task.traceId)) -Message "Ops task did not return traceId: $($task | ConvertTo-Json -Depth 20 -Compress)"
}
Assert-True -Condition ($RestoreTask.rollbackSupported -eq $true -and $ArchiveTask.rollbackSupported -eq $true -and $RollbackTask.rollbackSupported -eq $true) -Message 'Restore/archive/rollback tasks must declare rollbackSupported=true.'
Assert-True -Condition (@($Deployments.records).Count -ge 1 -and @($CachePolicy).Count -ge 1 -and @($CacheUpdated).Count -ge 1) -Message 'Deployment or API cache policy read/update did not return expected records.'

$PlatformAuditRecords = @()
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $tracePlatformHealth -ExpectedActionPart 'platform.ops.health-check' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceFeature -ExpectedActionPart 'platform.ops.feature-flags' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceQuota -ExpectedActionPart 'platform.ops.quotas' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceRate -ExpectedActionPart 'platform.ops.rate-limit-policies' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceBackup -ExpectedActionPart 'platform.ops.backups' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceRestore -ExpectedActionPart 'platform.ops.backups' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceArchive -ExpectedActionPart 'platform.ops.archive-restore-requests' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceRollback -ExpectedActionPart 'platform.ops.deployments' -ExpectedResult 'SUCCESS'
$PlatformAuditRecords += Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceCache -ExpectedActionPart 'platform.ops.api-cache-policy' -ExpectedResult 'SUCCESS'
$SystemAuditRecord = Assert-LogByTrace -Scope 'SYSTEM' -SystemId $script:SystemId -TraceId $traceSystemHealth -ExpectedActionPart 'systems.' -ExpectedResult 'SUCCESS'

$traceDeniedPlatform = "trc_r50_denied_platform_$script:Suffix"
$NormalPlatformDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path '/api/v1/platform/ops/health-check' -Headers ($NormalHeaders + @{ 'X-Trace-Id' = $traceDeniedPlatform }) -ExpectedStatus @(403) -Body @{
    checkType = 'FULL'
    idempotencyKey = "r50-denied-platform-$script:Suffix"
}
$traceDeniedSystem = "trc_r50_denied_system_$script:Suffix"
$NormalSystemDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/api/v1/systems/$script:SystemId/ops/health-check" -Headers ($NormalHeaders + @{ 'X-Trace-Id' = $traceDeniedSystem }) -ExpectedStatus @(403) -Body @{
    checkType = 'SYSTEM'
    idempotencyKey = "r50-denied-system-$script:Suffix"
}
$DeniedPlatformLog = Assert-LogByTrace -Scope 'PLATFORM' -TraceId $traceDeniedPlatform -ExpectedActionPart 'platform.ops.health-check' -ExpectedResult 'FAILURE'
$DeniedSystemLog = Assert-LogByTrace -Scope 'SYSTEM' -SystemId $script:SystemId -TraceId $traceDeniedSystem -ExpectedActionPart 'systems.' -ExpectedResult 'FAILURE'

$debugPort = Get-FreeTcpPort
$TempRoot = Join-Path $RepoRoot '.tmp-browser'
New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null
$script:ChromeProfileDir = Join-Path $TempRoot "unexamine-r50-chrome-$script:Suffix"
New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
$script:ChromeProcess = Start-Process -FilePath (Find-Chrome) -ArgumentList @(
    '--headless',
    '--disable-gpu',
    '--disable-extensions',
    '--disable-software-rasterizer',
    '--no-sandbox',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--remote-allow-origins=*',
    "--remote-debugging-port=$debugPort",
    "--user-data-dir=$script:ChromeProfileDir",
    'about:blank'
) -PassThru -WindowStyle Hidden

$versionUrl = "http://127.0.0.1:$debugPort/json/version"
$ready = $false
for ($i = 0; $i -lt 50; $i++) {
    try {
        Invoke-RestMethod -Uri $versionUrl -TimeoutSec 2 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Milliseconds 200
    }
}
Assert-True -Condition $ready -Message 'Chrome DevTools endpoint did not become ready.'

$nodeScript = Join-Path $TempRoot "unexamine-r50-ops-$script:Suffix.js"
$nodeSource = @'
const fs = require('fs');
const path = require('path');
const http = require('http');

const baseUrl = process.env.R50_BASE_URL;
const outDir = process.env.R50_EVIDENCE_DIR;
const port = process.env.R50_CDP_PORT;
const systemId = process.env.R50_SYSTEM_ID;

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function assert(condition, message, details = {}) {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(details)}`);
}
async function cdpJson(pathname, options = {}) {
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: '127.0.0.1',
      port,
      path: pathname,
      method: options.method || 'GET',
    }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`CDP HTTP ${response.statusCode} for ${pathname}`));
          return;
        }
        try {
          resolve(JSON.parse(body));
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on('error', reject);
    request.end();
  });
}
async function newTarget() {
  try {
    return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' });
  } catch {
    const targets = await cdpJson('/json/list');
    return targets[0];
  }
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    const eventWaiters = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      waitFor(eventName, timeoutMs = 5000) {
        return new Promise((res) => {
          const timer = setTimeout(() => res(null), timeoutMs);
          eventWaiters.set(eventName, (payload) => {
            clearTimeout(timer);
            res(payload);
          });
        });
      },
      close() { ws.close(); },
    });
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        message.error ? item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`)) : item.res(message.result);
      }
      if (message.method && eventWaiters.has(message.method)) {
        const waiter = eventWaiters.get(message.method);
        eventWaiters.delete(message.method);
        waiter(message.params || {});
      }
    };
  });
}
async function navigate(client, url) {
  const waitLoad = client.waitFor('Page.loadEventFired', 8000);
  await client.send('Page.navigate', { url });
  await waitLoad;
  await delay(900);
}
async function evaluateJson(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitUntil(client, expression, timeoutMs = 60000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    last = await evaluateJson(client, expression);
    if (last && last.ok) return last;
    await delay(300);
  }
  throw new Error(`waitUntil timed out. Last=${JSON.stringify(last)}`);
}
async function clickSelector(client, selector) {
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector(${JSON.stringify(selector)}) })`);
  await client.send('Runtime.evaluate', {
    expression: `(() => { document.querySelector(${JSON.stringify(selector)})?.click(); })()`,
    returnByValue: true,
  });
}
async function realLogin(client) {
  await navigate(client, `${baseUrl}/#/login`);
  await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('form.auth-form input[data-field-name="loginName"]') })`);
  await client.send('Runtime.evaluate', {
    expression: `
      (() => {
        const login = document.querySelector('form.auth-form input[data-field-name="loginName"]');
        const password = document.querySelector('form.auth-form input[data-field-name="password"]');
        const button = document.querySelector('form.auth-form button.button.primary');
        login.value = 'admin';
        login.dispatchEvent(new Event('input', { bubbles: true }));
        login.dispatchEvent(new Event('change', { bubbles: true }));
        password.value = '123123aa';
        password.dispatchEvent(new Event('input', { bubbles: true }));
        password.dispatchEvent(new Event('change', { bubbles: true }));
        button.click();
      })()
    `,
    returnByValue: true,
  });
  await waitUntil(client, `JSON.stringify({ ok: !!localStorage.getItem('unexamine.accessToken') && location.hash !== '#/login' })`);
}
async function capture(client, key, extra = {}) {
  const metrics = await evaluateJson(client, `
    JSON.stringify((() => {
      const overflowX = Math.max(0, document.documentElement.scrollWidth - window.innerWidth);
      const blockers = [];
      if (document.body.innerText.includes('undefined')) blockers.push('undefined-copy');
      if (document.body.innerText.includes('NaN')) blockers.push('nan-copy');
      return { overflowX, blockers, hash: location.hash, textSample: document.body.innerText.slice(0, 300) };
    })())
  `);
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  const fileName = `${key}.png`;
  fs.writeFileSync(path.join(outDir, fileName), Buffer.from(screenshot.data, 'base64'));
  return { key, screenshot: fileName, ...metrics, ...extra };
}

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await realLogin(client);
  const results = [];
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  for (const viewport of viewports) {
    await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
    await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
    await navigate(client, `${baseUrl}/#/platform/admin`);
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-sidebar') && !!document.querySelector('.admin-content') })`);
    await clickSelector(client, '.admin-sidebar .sidebar-item[data-admin-section="platform-config"]');
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-ops-governance') })`);
    results.push(await capture(client, `platform-ops-${viewport.name}`, { viewport: viewport.name }));
    await clickSelector(client, '.admin-sidebar .sidebar-item[data-admin-section="platform-logs"]');
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('#platform-logs') })`);
    results.push(await capture(client, `platform-logs-${viewport.name}`, { viewport: viewport.name }));
    await navigate(client, `${baseUrl}/#/systems/${systemId}/admin/log-management`);
    await waitUntil(client, `JSON.stringify({ ok: !!document.querySelector('.admin-content') && !!document.querySelector('#log-management') })`);
    results.push(await capture(client, `system-logs-${viewport.name}`, { viewport: viewport.name }));
  }
  results.forEach((item) => assert(item.overflowX <= 2 && item.blockers.length === 0, 'Browser containment failed', item));
  fs.writeFileSync(path.join(outDir, 'operations-release-log-browser-audit.json'), JSON.stringify({ results }, null, 2));
  client.close();
  console.log(JSON.stringify({ results }, null, 2));
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
'@
$nodeSource | Set-Content -LiteralPath $nodeScript -Encoding UTF8

$env:R50_BASE_URL = $BaseUrl
$env:R50_EVIDENCE_DIR = $EvidenceDir
$env:R50_CDP_PORT = [string]$debugPort
$env:R50_SYSTEM_ID = $script:SystemId
$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R50 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAudit = Get-Content -Raw -Encoding UTF8 (Join-Path $EvidenceDir 'operations-release-log-browser-audit.json') | ConvertFrom-Json
Stop-BrowserAudit
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$script:CleanupResult = Remove-CreatedSystems
$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-050'
    frc = 'FRC-5B Operations, logs, release, and launch-rule closure'
    baseUrl = $BaseUrl
    releaseVerifyStatus = $verify.status
    serverScriptCommands = @('start', 'stop', 'restart', 'status', 'health')
    systemId = $script:SystemId
    tenantId = $TenantId
    platformHealthTraceId = $PlatformHealth.traceId
    systemHealthTraceId = $SystemHealth.traceId
    featureFlagTraceId = $FeatureFlag.traceId
    quotaTraceId = $Quota.traceId
    rateLimitTraceId = $RateLimit.traceId
    backupTaskId = $BackupTask.taskId
    restoreTaskId = $RestoreTask.taskId
    archiveTaskId = $ArchiveTask.taskId
    rollbackTaskId = $RollbackTask.taskId
    rollbackSupported = [ordered]@{
        restore = $RestoreTask.rollbackSupported
        archive = $ArchiveTask.rollbackSupported
        deployment = $RollbackTask.rollbackSupported
    }
    deploymentCount = @($Deployments.records).Count
    cachePolicyCount = @($CachePolicy).Count
    cacheUpdatedCount = @($CacheUpdated).Count
    platformAuditReadbackCount = @($PlatformAuditRecords).Count
    systemAuditReadbackCount = 1
    deniedPlatformStatus = [int]$NormalPlatformDenied.status
    deniedSystemStatus = [int]$NormalSystemDenied.status
    deniedPlatformAuditResult = [string]$DeniedPlatformLog.result
    deniedSystemAuditResult = [string]$DeniedSystemLog.result
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = Join-Path $EvidenceDir 'operations-release-log-browser-audit.json'
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
}
Assert-True -Condition ($Result.browserOverflowCount -eq 0 -and $Result.browserBlockerCount -eq 0) -Message 'R50 browser audit reported blockers or horizontal overflow.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R50 Operations Release Log Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- Release verify: $($Result.releaseVerifyStatus)",
    "- Server script commands: $($Result.serverScriptCommands -join ', ')",
    "- System: $($Result.systemId)",
    "- Ops traces: platform=$($Result.platformHealthTraceId), system=$($Result.systemHealthTraceId), feature=$($Result.featureFlagTraceId), quota=$($Result.quotaTraceId), rate=$($Result.rateLimitTraceId)",
    "- Async tasks: backup=$($Result.backupTaskId), restore=$($Result.restoreTaskId), archive=$($Result.archiveTaskId), rollback=$($Result.rollbackTaskId)",
    "- Audit readback: platform=$($Result.platformAuditReadbackCount), system=$($Result.systemAuditReadbackCount), deniedPlatform=$($Result.deniedPlatformAuditResult), deniedSystem=$($Result.deniedSystemAuditResult)",
    "- Browser results: count=$($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    '- Browser audit JSON: docs/evidence/recovery/screenshots/r50-operations-release-log/operations-release-log-browser-audit.json',
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile
$Result | ConvertTo-Json -Depth 100
if ($NoFailExit) {
    exit 0
}
