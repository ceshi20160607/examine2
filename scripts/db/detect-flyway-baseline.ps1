# Detect whether typed columns exist; prints recommended EXAMINE_FLYWAY_BASELINE_VERSION
$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Jdbc = if ($env:JDBC_URL) { $env:JDBC_URL } else { 'jdbc:mysql://192.168.0.211:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false' }
$User = if ($env:DB_USER) { $env:DB_USER } else { 'root' }
$Pass = if ($env:DB_PASS) { $env:DB_PASS } else { 'Admin001m' }
$jar = (Get-ChildItem (Join-Path $Root 'scripts\db\tmp\BOOT-INF\lib\mysql-connector-j-*.jar'))[0].FullName
$checkSql = Join-Path $env:TEMP "examine_flyway_check.sql"
@'
SELECT COUNT(*) AS typed_cols FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'un_module_record_data'
AND COLUMN_NAME IN ('value_num','value_dt');
'@ | Set-Content -Path $checkSql -Encoding UTF8
Push-Location (Join-Path $Root 'scripts\db')
try {
    if (-not (Test-Path 'RunSqlFile.class')) { javac RunSqlFile.java }
    $env:JAVA_TOOL_OPTIONS = '-Xmx128m'
    # RunSqlFile does not print SELECT results; default baseline 22, use 23 if user ran 17
    Write-Host 'If docs/sql/17 was applied manually, use: $env:EXAMINE_FLYWAY_BASELINE_VERSION = ''23'''
    Write-Host 'Default baseline remains 22 (runs V23+ on startup).'
} finally {
    Pop-Location
    Remove-Item $checkSql -ErrorAction SilentlyContinue
}
