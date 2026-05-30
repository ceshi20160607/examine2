# API smoke + Web Playwright（需后端 9999；UI 另需前端 5173 或由 Playwright 拉起 dev）
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$RepoRoot = Split-Path $Root -Parent

function Resolve-NodeCommand([string]$Name) {
    $candidates = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        @("$Name.cmd", "$Name.exe", $Name)
    } else {
        @($Name)
    }
    foreach ($candidate in $candidates) {
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    throw "$Name is required. Install Node.js and ensure $Name is on PATH."
}

$npm = Resolve-NodeCommand 'npm'

if (-not $env:SMOKE_USER -and -not $env:SMOKE_SYSTEM_ID) {
    $env:SMOKE_USER = 'admin'
    if (-not $env:SMOKE_PASS) { $env:SMOKE_PASS = '123123aa' }
    Write-Host "SMOKE_USER not set -> using default admin smoke account" -ForegroundColor DarkCyan
}

# 与 run-backend.ps1 一致：避免 JAVA_HOME=JDK8 导致运行时 ClassFormatError
if (-not $env:JAVA_HOME -or $env:JAVA_HOME -match 'jdk8|jre8') {
    $env:JAVA_HOME = if ($env:EXAMINE_JAVA_HOME) { $env:EXAMINE_JAVA_HOME } else { 'D:\java\jdk\jdk21' }
}

$redisUp = $false
try {
    $redisUp = (Test-NetConnection 127.0.0.1 -Port 6379 -WarningAction SilentlyContinue).TcpTestSucceeded
} catch {}
if (-not $redisUp) {
    Write-Host 'Redis 6379 down -> EXAMINE_SESSION_STORE=memory (restart backend with this env if login fails)' -ForegroundColor Yellow
    $env:EXAMINE_SESSION_STORE = 'memory'
} else {
    Remove-Item Env:EXAMINE_SESSION_STORE -ErrorAction SilentlyContinue
    if (-not $env:SKIP_OPEN_API) { $env:SKIP_OPEN_API = '0' }
    Write-Host 'Redis up -> session store redis, open api smoke enabled (SKIP_OPEN_API=0)' -ForegroundColor DarkCyan
}

$backendUp = $false
try {
    $backendUp = (Invoke-WebRequest -Uri 'http://127.0.0.1:9999/ping' -TimeoutSec 2 -UseBasicParsing).StatusCode -eq 200
} catch {}
if (-not $backendUp) {
    $StartBackend = Join-Path $RepoRoot 'scripts\deploy\run-backend.ps1'
    if (-not (Test-Path $StartBackend)) { Write-Error "Backend down and missing $StartBackend" }
    Write-Host 'Backend 9999 down -> starting local backend ...' -ForegroundColor Cyan
    & $StartBackend -NoBuild
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 2
        try {
            $backendUp = (Invoke-WebRequest -Uri 'http://127.0.0.1:9999/ping' -TimeoutSec 2 -UseBasicParsing).StatusCode -eq 200
            if ($backendUp) { Write-Host "Backend ready (${i}x2s)" -ForegroundColor Green; break }
        } catch {}
    }
    if (-not $backendUp) { Write-Error 'Backend not ready in 120s' }
}

Write-Host '=== API smoke ===' -ForegroundColor Cyan
& (Join-Path $Root 'api\e2e-smoke.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host '=== Web UI (Playwright) ===' -ForegroundColor Cyan
$Vue3 = Join-Path $RepoRoot 'web\vue3'
$StartFe = Join-Path $RepoRoot 'scripts\deploy\start-frontend.ps1'
if (-not (Test-Path (Join-Path $Vue3 'node_modules'))) {
    Write-Host 'Installing web/vue3 dependencies ...' -ForegroundColor Cyan
    Push-Location $Vue3
    try { & $npm install --no-fund --no-audit } finally { Pop-Location }
}
$feUp = $false
try {
    $feUp = (Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -TimeoutSec 2 -UseBasicParsing).StatusCode -eq 200
} catch {}
if (-not $feUp) {
    if (-not (Test-Path $StartFe)) { Write-Error "Frontend down and missing $StartFe" }
    & $StartFe
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$env:E2E_SKIP_WEB_SERVER = '1'
Push-Location (Join-Path $Root 'web')
try {
    $runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $pwTmp = Join-Path $RepoRoot "tmp\playwright-$runStamp"
    New-Item -ItemType Directory -Force -Path $pwTmp | Out-Null
    $env:PLAYWRIGHT_OUTPUT_DIR = Join-Path $pwTmp 'test-results'
    $env:PLAYWRIGHT_HTML_REPORT = Join-Path $pwTmp 'html-report'
    if (-not (Test-Path 'node_modules')) {
        Write-Host 'Installing tests/web dependencies ...'
        & $npm install --no-fund --no-audit
    }
    & $npm run test:e2e
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Host 'ALL TESTS OK' -ForegroundColor Green
