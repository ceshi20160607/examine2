# 启动前：把手工已执行的 Flyway 版本登记为 success=1（V14 必做；17 已跑则含 V23）
# 用法：.\scripts\db\repair-flyway-failed.ps1

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$ManualDir = Join-Path $Root 'docs\sql\manual'
$SqlFiles = @((Join-Path $ManualDir 'flyway_mark_v14_success.sql'))
if ($env:EXAMINE_FLYWAY_MARK_APPLIED -match '23') {
    $SqlFiles += (Join-Path $ManualDir 'flyway_mark_v23_success.sql')
}
$Jdbc = if ($env:JDBC_URL) { $env:JDBC_URL } else { 'jdbc:mysql://192.168.0.211:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false' }
$User = if ($env:DB_USER) { $env:DB_USER } else { 'root' }
$Pass = if ($env:DB_PASS) { $env:DB_PASS } else { 'Admin001m' }

$mysqlJar = Get-ChildItem -Path (Join-Path $Root 'scripts\db\tmp\BOOT-INF\lib\mysql-connector-j-*.jar') -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $mysqlJar) {
    Write-Error 'mysql-connector-j jar not found under scripts/db/tmp/BOOT-INF/lib/'
}
$cp = $mysqlJar.FullName

Push-Location (Join-Path $Root 'scripts\db')
try {
    javac RunSqlFile.java
    if ($LASTEXITCODE -ne 0) { throw 'javac RunSqlFile.java failed' }
    foreach ($f in $SqlFiles) {
        if (Test-Path $f) {
            Write-Host "Flyway mark success: $f" -ForegroundColor Cyan
            java -cp $cp RunSqlFile $Jdbc $User $Pass $f
        }
    }
    Write-Host 'Done. V14/V23 registered as success; start backend for V15+ migrate.' -ForegroundColor Green
} finally {
    Pop-Location
}
