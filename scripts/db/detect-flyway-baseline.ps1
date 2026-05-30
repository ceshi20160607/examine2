# Detect whether typed columns exist; prints recommended EXAMINE_FLYWAY_BASELINE_VERSION
$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $env:JDBC_URL) {
    throw 'JDBC_URL is required. Example: jdbc:mysql://host:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false'
}
$Jdbc = $env:JDBC_URL
$User = if ($env:DB_USER) { $env:DB_USER } else { 'root' }
if (-not $env:DB_PASS) {
    throw 'DB_PASS is required. Set the database password in the environment before running this baseline detection script.'
}
$Pass = $env:DB_PASS

function Resolve-MysqlConnectorJar {
    if ($env:MYSQL_CONNECTOR_JAR -and (Test-Path $env:MYSQL_CONNECTOR_JAR)) {
        return (Resolve-Path $env:MYSQL_CONNECTOR_JAR).Path
    }
    $candidates = @()
    $m2 = Join-Path $env:USERPROFILE '.m2\repository\com\mysql\mysql-connector-j'
    if (Test-Path $m2) {
        $candidates += Get-ChildItem -Path $m2 -Recurse -Filter 'mysql-connector-j-*.jar' -ErrorAction SilentlyContinue
    }
    $tmp = Join-Path $Root 'scripts\db\tmp\BOOT-INF\lib'
    if (Test-Path $tmp) {
        $candidates += Get-ChildItem -Path $tmp -Filter 'mysql-connector-j-*.jar' -ErrorAction SilentlyContinue
    }
    $jar = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) {
        throw 'mysql-connector-j jar not found. Run Maven once or set MYSQL_CONNECTOR_JAR to the connector jar path.'
    }
    return $jar.FullName
}

$jar = Resolve-MysqlConnectorJar
$checkSql = Join-Path $env:TEMP "examine_flyway_check.sql"
@'
SELECT COUNT(*) AS typed_cols FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'un_module_record_data'
AND COLUMN_NAME IN ('value_num','value_dt');
'@ | Set-Content -Path $checkSql -Encoding UTF8
Push-Location (Join-Path $Root 'scripts\db')
try {
    if (-not (Test-Path 'RunSqlFile.class')) { javac RunSqlFile.java }
    if ($LASTEXITCODE -ne 0) { throw 'javac RunSqlFile.java failed' }
    $env:JAVA_TOOL_OPTIONS = '-Xmx128m'
    Write-Host 'Checking un_module_record_data typed columns ...' -ForegroundColor Cyan
    $output = java -cp ".;$jar" RunSqlFile $Jdbc $User $Pass $checkSql
    if ($LASTEXITCODE -ne 0) { throw 'baseline detection query failed' }
    $output | ForEach-Object { Write-Host $_ }
    $typedCols = 0
    $typedLine = $output | Where-Object { $_ -match 'typed_cols=([0-9]+)' } | Select-Object -First 1
    if ($typedLine -match 'typed_cols=([0-9]+)') {
        $typedCols = [int]$Matches[1]
    }
    if ($typedCols -ge 2) {
        Write-Host 'Recommended: $env:EXAMINE_FLYWAY_BASELINE_VERSION = ''23''' -ForegroundColor Green
        Write-Host 'Reason: typed columns already exist; skip V23 ALTER on existing databases.'
    } else {
        Write-Host 'Recommended: keep $env:EXAMINE_FLYWAY_BASELINE_VERSION = ''22''' -ForegroundColor Green
        Write-Host 'Reason: typed columns are absent; let Flyway run V23 on startup.'
    }
} finally {
    Pop-Location
    Remove-Item $checkSql -ErrorAction SilentlyContinue
}
