# API smoke + Web Playwright（需后端 9999；UI 另需前端 5173 或由 Playwright 拉起 dev）
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot

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
