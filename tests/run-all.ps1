# API smoke + Web Playwright（需后端 9999；UI 另需前端 5173 或由 Playwright 拉起 dev）
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot

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

Write-Host '=== API smoke ===' -ForegroundColor Cyan
& (Join-Path $Root 'api\e2e-smoke.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host '=== Web UI (Playwright) ===' -ForegroundColor Cyan
$RepoRoot = Split-Path $Root -Parent
$Vue3 = Join-Path $RepoRoot 'web\vue3'
$StartFe = Join-Path $RepoRoot 'scripts\deploy\start-frontend.ps1'
if (-not (Test-Path (Join-Path $Vue3 'node_modules'))) {
    Write-Host 'Installing web/vue3 dependencies ...' -ForegroundColor Cyan
    Push-Location $Vue3
    try { npm install --no-fund --no-audit } finally { Pop-Location }
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
    if (-not (Test-Path 'node_modules')) {
        Write-Host 'Installing tests/web dependencies ...'
        npm install --no-fund --no-audit
    }
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Host 'ALL TESTS OK' -ForegroundColor Green
