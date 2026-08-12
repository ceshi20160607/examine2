param(
    [string]$Root,
    [string]$PackageRoot
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Root)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
    $Root = Split-Path -Parent (Split-Path -Parent $scriptDirectory)
}
$rootPath = (Resolve-Path -LiteralPath $Root).Path
if ([string]::IsNullOrWhiteSpace($PackageRoot)) {
    $target = Join-Path $rootPath 'delivery'
    $sourceMode = $true
} else {
    $target = (Resolve-Path -LiteralPath $PackageRoot).Path
    $sourceMode = $false
}

$required = @(
    'deploy/compose.yaml', 'deploy/nginx.conf',
    'scripts/common.ps1', 'scripts/verify-package.ps1', 'scripts/start.ps1',
    'scripts/stop.ps1', 'scripts/restart.ps1', 'scripts/health.ps1',
    'scripts/logs.ps1', 'scripts/backup.ps1', 'scripts/restore.ps1',
    'scripts/upgrade.ps1', 'scripts/rollback.ps1',
    'scripts/common.sh', 'scripts/verify-package.sh', 'scripts/start.sh',
    'scripts/stop.sh', 'scripts/restart.sh', 'scripts/health.sh',
    'scripts/logs.sh', 'scripts/backup.sh', 'scripts/restore.sh',
    'scripts/upgrade.sh', 'scripts/rollback.sh',
    'docs/README.md', 'docs/DEPLOYMENT.md', 'docs/CONFIGURATION.md',
    'docs/DATABASE.md', 'docs/UPGRADE.md', 'docs/ADMIN_GUIDE.md',
    'docs/USER_GUIDE.md', 'docs/OPENAPI.md',
    'docs/PRINT_AND_SYSTEM_SETTINGS.md', 'docs/TROUBLESHOOTING.md'
)
$envRelative = if ($sourceMode) { 'config/.env.example' } else { '.env.example' }
$required += $envRelative
if (-not $sourceMode) {
    $required += @('backend/examine-web.jar', 'frontend/index.html', 'sql/migration', 'VERSION.json', 'manifest.json')
}
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $target $relative))) {
        throw "Required delivery entry is missing: $relative"
    }
}

$parseFailures = @()
foreach ($script in Get-ChildItem -LiteralPath (Join-Path $target 'scripts') -Filter '*.ps1' -File) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) {
        $parseFailures += "$($script.Name): $($errors[0].Message)"
    }
}
if ($parseFailures.Count -gt 0) { throw "PowerShell parse failure: $($parseFailures -join '; ')" }
$bash = Get-Command bash -ErrorAction SilentlyContinue
if ($null -ne $bash) {
    foreach ($script in Get-ChildItem -LiteralPath (Join-Path $target 'scripts') -Filter '*.sh' -File) {
        & $bash.Source -n $script.FullName
        if ($LASTEXITCODE -ne 0) { throw "Bash parse failure: $($script.Name)" }
    }
}

$compose = Get-Content -LiteralPath (Join-Path $target 'deploy/compose.yaml') -Raw -Encoding UTF8
foreach ($token in @('mysql:8.4.10', 'redis:7.4-alpine', 'eclipse-temurin:21', 'nginx:1.27-alpine',
        'LOGGING_FILE_NAME', 'EXAMINE_DATA_ROOT', 'EXAMINE_ARTIFACT_ROOT')) {
    if (-not $compose.Contains($token)) { throw "Compose contract token is missing: $token" }
}

$docContracts = @{
    'DEPLOYMENT.md' = @('verify-package.ps1', 'start.ps1', 'stop.ps1', 'health.ps1', 'EXAMINE_DATA_ROOT')
    'DATABASE.md' = @('backup.ps1', 'restore.ps1', 'RPO', 'RTO', 'Flyway')
    'UPGRADE.md' = @('upgrade.ps1', 'rollback.ps1', 'pre-upgrade')
    'ADMIN_GUIDE.md' = @('SecretRef', 'Flow', 'Agent')
    'USER_GUIDE.md' = @('URL')
    'OPENAPI.md' = @('HMAC-SHA256', 'nonce', 'requestId', '429', 'SecretRef')
    'TROUBLESHOOTING.md' = @('requestId', 'traceId', 'Flyway', 'OpenAPI')
}
foreach ($contract in $docContracts.GetEnumerator()) {
    $text = Get-Content -LiteralPath (Join-Path $target "docs/$($contract.Key)") -Raw -Encoding UTF8
    foreach ($token in $contract.Value) {
        if (-not $text.Contains($token)) { throw "Documentation contract token '$token' is missing from $($contract.Key)" }
    }
}

if (-not $sourceMode) {
    $manifest = Get-Content -LiteralPath (Join-Path $target 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.packageKind -notin @('rolling_delivery_cycle_snapshot', 'formal_checkpoint_candidate')) {
        throw 'Package kind is invalid.'
    }
    foreach ($entry in $manifest.files) {
        $path = Join-Path $target ([string]$entry.path).Replace('/', '\')
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Manifest entry missing: $($entry.path)" }
        $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne [string]$entry.sha256) { throw "Manifest checksum mismatch: $($entry.path)" }
    }
}

[pscustomobject]@{
    status = 'PASS'
    mode = if ($sourceMode) { 'source' } else { 'package' }
    root = $target
    requiredEntries = $required.Count
    scriptsParsed = @(Get-ChildItem (Join-Path $target 'scripts') -Filter '*.ps1' -File).Count
    shellScriptsPresent = @(Get-ChildItem (Join-Path $target 'scripts') -Filter '*.sh' -File).Count
    documentationContracts = $docContracts.Count
} | ConvertTo-Json -Depth 4
