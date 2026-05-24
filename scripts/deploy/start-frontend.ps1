# 后台启动 web/vue3 Vite（Playwright / 手测用）
# 用法（仓库根）: .\scripts\deploy\start-frontend.ps1
$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Vue = Join-Path $Root 'web\vue3'

if (-not (Test-Path (Join-Path $Vue 'node_modules\vite'))) {
    Write-Host 'Installing web/vue3 deps ...' -ForegroundColor Cyan
    Push-Location $Vue
    try { npm install --no-fund --no-audit } finally { Pop-Location }
}

netstat -ano 2>$null | Select-String ':5173\s' | ForEach-Object {
    if ($_ -match '\s(\d+)\s*$') { taskkill /F /PID $Matches[1] 2>$null }
}
Start-Sleep 1

$log = Join-Path $Vue 'vite-dev.log'
if (Test-Path $log) { Remove-Item $log -Force }

Write-Host "Starting Vite -> http://127.0.0.1:5173 (log: $log)" -ForegroundColor Cyan
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "npm run dev > `"$log`" 2>&1" -WorkingDirectory $Vue -WindowStyle Hidden

$ok = $false
foreach ($i in 1..40) {
    Start-Sleep -Seconds 2
    try {
        $code = (Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -TimeoutSec 2 -UseBasicParsing).StatusCode
        if ($code -eq 200) { $ok = $true; Write-Host "Frontend ready (${i}x2s)" -ForegroundColor Green; break }
    } catch {}
}
if (-not $ok) {
    Write-Warning 'Frontend not ready in 80s. Last log lines:'
    if (Test-Path $log) { Get-Content $log -Tail 15 }
    exit 1
}
