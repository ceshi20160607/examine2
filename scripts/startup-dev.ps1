# 一键修复 Flyway V14 并启动 examine-web（在仓库根目录执行）
# 用法: .\scripts\startup-dev.ps1
# 若已手工执行 docs/sql/17，请先: $env:EXAMINE_FLYWAY_BASELINE_VERSION = '23'

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot | Split-Path -Parent
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\java\jdk\jdk21' }
# 仅 Maven 用较大堆；运行时由 run-backend.ps1 的 -Xmx256m SerialGC 控制
$env:JAVA_TOOL_OPTIONS = $null
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
$env:MAVEN_OPTS = '-Xmx512m -XX:+UseSerialGC'
$env:EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE = 'false'
$env:EXAMINE_FLYWAY_REPAIR_ON_MIGRATE = 'true'

if ($env:JDBC_URL -and $env:DB_PASS) {
    Write-Host '=== 1) Flyway DB repair (V14 manual + clear failed) ===' -ForegroundColor Cyan
    & (Join-Path $Root 'scripts\db\repair-flyway-failed.ps1')
} else {
    Write-Host '=== 1) Flyway DB repair skipped (set JDBC_URL + DB_PASS to run it) ===' -ForegroundColor Yellow
}

Write-Host '=== 2) Build JAR ===' -ForegroundColor Cyan
Push-Location (Join-Path $Root 'backend')
try {
    mvn -pl examine-web -am package -DskipTests -q
} finally {
    Pop-Location
}

Write-Host '=== 3) Start backend ===' -ForegroundColor Cyan
& (Join-Path $Root 'scripts\deploy\run-backend.ps1')
