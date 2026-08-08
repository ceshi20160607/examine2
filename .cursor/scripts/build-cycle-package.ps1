param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Z0-9][A-Z0-9-]{2,79}$')]
    [string]$CycleId,
    [string]$Root,
    [string]$BackendJar = 'backend/examine-web/target/examine-web-1.0.0-SNAPSHOT.jar',
    [string]$FrontendDist = 'frontend/dist',
    [string]$MigrationDirectory = 'sql/migration',
    [string]$DeliveryDirectory = 'delivery',
    [string]$OutputRoot = '.cursor/session/packages',
    [ValidateSet('rolling_delivery_cycle_snapshot', 'formal_checkpoint_candidate')]
    [string]$PackageKind = 'rolling_delivery_cycle_snapshot',
    [switch]$FormalCheckpointStateChanged
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Root)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
    $Root = Split-Path -Parent (Split-Path -Parent $scriptDirectory)
}
$rootPath = (Resolve-Path -LiteralPath $Root).Path

function Resolve-ProjectPath {
    param([string]$RelativePath, [string]$ExpectedType)

    $candidate = [System.IO.Path]::GetFullPath((Join-Path $rootPath $RelativePath))
    $rootPrefix = $rootPath.TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Package input escapes the project root: $RelativePath"
    }
    if ($ExpectedType -eq 'Leaf' -and -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Package input file does not exist: $candidate"
    }
    if ($ExpectedType -eq 'Container' -and -not (Test-Path -LiteralPath $candidate -PathType Container)) {
        throw "Package input directory does not exist: $candidate"
    }
    return $candidate
}

$jarPath = Resolve-ProjectPath $BackendJar 'Leaf'
$frontendPath = Resolve-ProjectPath $FrontendDist 'Container'
$migrationPath = Resolve-ProjectPath $MigrationDirectory 'Container'
$deliveryPath = Resolve-ProjectPath $DeliveryDirectory 'Container'
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $rootPath $OutputRoot))
$rootPrefix = $rootPath.TrimEnd('\') + '\'
if (-not $outputPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Package output root must stay inside the project root.'
}

$attemptId = Get-Date -Format 'yyyyMMdd-HHmmss'
$cyclePath = Join-Path $outputPath $CycleId
$stagingPath = Join-Path $cyclePath $attemptId
$archivePath = Join-Path $cyclePath "$CycleId-$attemptId.zip"
$resultPath = Join-Path $cyclePath "$CycleId-$attemptId.result.json"

if ((Test-Path -LiteralPath $stagingPath) -or (Test-Path -LiteralPath $archivePath)) {
    throw "Package attempt already exists: $attemptId"
}

$backendTarget = Join-Path $stagingPath 'backend'
$frontendTarget = Join-Path $stagingPath 'frontend'
$migrationTarget = Join-Path $stagingPath 'sql/migration'
$deployTarget = Join-Path $stagingPath 'deploy'
$scriptsTarget = Join-Path $stagingPath 'scripts'
$docsTarget = Join-Path $stagingPath 'docs'
[void](New-Item -ItemType Directory -Path $backendTarget -Force)
[void](New-Item -ItemType Directory -Path $frontendTarget -Force)
[void](New-Item -ItemType Directory -Path $migrationTarget -Force)
[void](New-Item -ItemType Directory -Path $deployTarget -Force)
[void](New-Item -ItemType Directory -Path $scriptsTarget -Force)
[void](New-Item -ItemType Directory -Path $docsTarget -Force)

Copy-Item -LiteralPath $jarPath -Destination (Join-Path $backendTarget 'examine-web.jar')
Copy-Item -Path (Join-Path $frontendPath '*') -Destination $frontendTarget -Recurse
Copy-Item -Path (Join-Path $migrationPath '*') -Destination $migrationTarget -Recurse
Copy-Item -Path (Join-Path $deliveryPath 'deploy/*') -Destination $deployTarget -Recurse
Copy-Item -Path (Join-Path $deliveryPath 'scripts/*') -Destination $scriptsTarget -Recurse
Copy-Item -Path (Join-Path $deliveryPath 'docs/*') -Destination $docsTarget -Recurse
Copy-Item -LiteralPath (Join-Path $deliveryPath 'config/.env.example') -Destination (Join-Path $stagingPath '.env.example')

$migrationFiles = @(Get-ChildItem -LiteralPath $migrationPath -File -Filter 'V*.sql')
$versionedMigrations = @($migrationFiles | ForEach-Object {
    if ($_.BaseName -match '^V(?<parts>[0-9]+(?:_[0-9]+){0,3})__') {
        [pscustomobject]@{ File = $_; Version = [version]$Matches['parts'].Replace('_', '.') }
    }
} | Sort-Object Version)
$latestMigration = if ($versionedMigrations.Count -gt 0) { $versionedMigrations[-1].File.BaseName } else { $null }
$versionValue = if ($latestMigration -and $latestMigration -match '^V([0-9_]+)__') {
    $Matches[1].Replace('_', '.')
} else { $CycleId }
$jarSha = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$version = [ordered]@{
    schemaVersion = 1
    product = 'Examine2'
    version = $versionValue
    cycleId = $CycleId
    attemptId = $attemptId
    createdAt = (Get-Date).ToString('o')
    latestMigration = $latestMigration
    migrationCount = $migrationFiles.Count
    backendJarSha256 = $jarSha
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    (Join-Path $stagingPath 'VERSION.json'),
    ($version | ConvertTo-Json -Depth 4),
    $utf8NoBom)

$runText = @'
# Examine2 delivery package

Prerequisites: Java 21, MySQL 8 and Redis 7. Configure at least:
`EXAMINE_DB_URL`, `EXAMINE_DB_USERNAME`, `EXAMINE_DB_PASSWORD`,
`EXAMINE_REDIS_HOST`, `EXAMINE_BOOTSTRAP_ROOT_USERNAME` and
`EXAMINE_BOOTSTRAP_ROOT_PASSWORD`.

To enable administrator-managed read-only MySQL table sources, also configure
the exact comma-separated `EXAMINE_DATASOURCE_JDBC_ALLOWED_TARGETS`
(`host:port`) and tenant-scoped SecretRef environment variables. Production
defaults to `EXAMINE_DATASOURCE_JDBC_TLS_MODE=VERIFY_IDENTITY`.

To enable real account-recovery mail, configure
`EXAMINE_ACCOUNT_RECOVERY_MAIL_ENABLED=true`, SMTP host/port/auth/TLS/from
settings and the trusted HTTPS `EXAMINE_PUBLIC_BASE_URL`. Loopback HTTP is
accepted only for local verification. When mail is disabled or incomplete,
the anonymous endpoint remains non-enumerating but no usable token is kept.

File storage defaults to `EXAMINE_FILE_STORAGE_MODE=LOCAL`; set
`EXAMINE_FILE_LOCAL_ROOT` to an application-owned absolute directory. For S3-compatible
storage, set the mode to `S3` and configure endpoint, region, bucket, optional
prefix/path-style, access key and secret key through the corresponding
`EXAMINE_FILE_S3_*` variables. S3 requires HTTPS; loopback HTTP additionally
requires `EXAMINE_FILE_S3_ALLOW_LOOPBACK_HTTP_FOR_TESTING=true` and is rejected
by production mode. The status API never returns credentials,
endpoint details or the local root. Single requests are limited to 20 MiB;
the multipart API accepts at most 100 MiB in 5 MiB parts. In-progress multipart
sessions are process-local and expire after 30 minutes, so an application
restart invalidates unfinished sessions; completed assets remain durable.

Event messages always keep the durable INBOX channel. To enable external
delivery, configure `EXAMINE_EVENT_SMTP_*` and/or
`EXAMINE_EVENT_WEBHOOK_ENABLED=true`, then enable the corresponding channel in
the system administration page. SMTP username/password settings are SecretRef
values (for example `env://EVENT_SMTP_USER`), never the credential itself; the
referenced environment variables must also exist at runtime. Webhook endpoint
and SecretRef are system-scoped administrator settings: production accepts
HTTPS public targets only, does not follow redirects, signs every JSON request,
and never exposes or persists response bodies or secret values.

Production startup must explicitly set `EXAMINE_DEPLOYMENT_MODE=PRODUCTION`.
That mode fails closed unless the MySQL URL uses `sslMode=VERIFY_IDENTITY`,
Redis has TLS plus authentication, secure cookies are enabled, SMTP and S3 use
authenticated TLS, and the sensitive-field key ring is a bounded regular file
under the absolute `EXAMINE_SENSITIVE_KEY_RING_ROOT`. Set
`EXAMINE_SENSITIVE_KEY_RING_FILE` to that file and protect, back up and rotate
it with deployment secret-manager/ACL controls. The application emits HSTS,
CSP frame/object restrictions, nosniff, referrer, permissions and anti-framing
headers; the production reverse proxy must terminate TLS and preserve them.

Read `docs/README.md` and `docs/DEPLOYMENT.md`. The supported single-host path
uses PowerShell 7 and Docker Compose v2:

1. Copy `.env.example` to `.env` and replace every placeholder.
2. Run `pwsh -File scripts/verify-package.ps1`.
3. Run `pwsh -File scripts/start.ps1`.
4. Run `pwsh -File scripts/health.ps1` and open the reported frontend URL.

Stable stop/restart/log/backup/restore/upgrade/rollback commands are under
`scripts/`. Fixed application logs are under
`<EXAMINE_DATA_ROOT>/logs/examine2.log`; the absolute data root is shared by
every upgraded or rolled-back version.
`VERSION.json` identifies the artifact and `manifest.json` verifies its files.
'@
[System.IO.File]::WriteAllText((Join-Path $stagingPath 'RUN.md'), $runText, $utf8NoBom)

$files = Get-ChildItem -LiteralPath $stagingPath -Recurse -File |
    Sort-Object FullName |
    ForEach-Object {
        [ordered]@{
            path = $_.FullName.Substring($stagingPath.Length + 1).Replace('\', '/')
            bytes = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
$manifest = [ordered]@{
    schemaVersion = 1
    cycleId = $CycleId
    attemptId = $attemptId
    createdAt = (Get-Date).ToString('o')
    packageKind = $PackageKind
    formalCheckpointStateChanged = [bool]$FormalCheckpointStateChanged
    files = @($files)
}
[System.IO.File]::WriteAllText(
    (Join-Path $stagingPath 'manifest.json'),
    ($manifest | ConvertTo-Json -Depth 6),
    $utf8NoBom)

Compress-Archive -Path (Join-Path $stagingPath '*') -DestinationPath $archivePath -CompressionLevel Optimal
$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
$result = [ordered]@{
    cycleId = $CycleId
    attemptId = $attemptId
    archivePath = $archivePath
    sha256 = $archiveHash
    stagingPath = $stagingPath
    fileCount = @($files).Count + 1
    packageKind = $PackageKind
    formalCheckpointStateChanged = [bool]$FormalCheckpointStateChanged
    createdAt = (Get-Date).ToString('o')
}
[System.IO.File]::WriteAllText($resultPath, ($result | ConvertTo-Json -Depth 4), $utf8NoBom)
$result | ConvertTo-Json -Depth 4
