# 清理本机调试产生的日志（已在 .gitignore，可安全删除）
$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$patterns = @('*.log', '*.out.log', '*.err.log', 'hs_err_pid*.log', 'replay_pid*.log')
$dirs = @(
    (Join-Path $Root 'backend'),
    (Join-Path $Root 'backend\examine-web')
)
$removed = 0
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) { continue }
    foreach ($pat in $patterns) {
        Get-ChildItem -Path $dir -Filter $pat -File -ErrorAction SilentlyContinue | ForEach-Object {
            Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
            $script:removed++
            Write-Host "Removed $($_.FullName)"
        }
    }
}
Write-Host "Done. Removed $removed file(s)." -ForegroundColor Green
