# 启动前：把手工已执行的 Flyway 版本登记为 success=1（V14 必做；17 已跑则含 V23）
# 用法：.\scripts\db\repair-flyway-failed.ps1

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$ManualDir = Join-Path $Root 'docs\sql\manual'
$SqlFiles = @((Join-Path $ManualDir 'flyway_mark_v14_success.sql'))
if ($env:EXAMINE_FLYWAY_MARK_APPLIED -match '23') {
    $SqlFiles += (Join-Path $ManualDir 'flyway_mark_v23_success.sql')
}
if (-not $env:JDBC_URL) {
    throw 'JDBC_URL is required. Example: jdbc:mysql://host:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false'
}
$Jdbc = $env:JDBC_URL
$User = if ($env:DB_USER) { $env:DB_USER } else { 'root' }
if (-not $env:DB_PASS) {
    throw 'DB_PASS is required. Set the database password in the environment before running this repair script.'
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
$cp = Resolve-MysqlConnectorJar

Push-Location (Join-Path $Root 'scripts\db')
try {
    javac RunSqlFile.java
    if ($LASTEXITCODE -ne 0) { throw 'javac RunSqlFile.java failed' }
    foreach ($f in $SqlFiles) {
        if (Test-Path $f) {
            Write-Host "Flyway mark success: $f" -ForegroundColor Cyan
            java -cp ".;$cp" RunSqlFile $Jdbc $User $Pass $f
            if ($LASTEXITCODE -ne 0) { throw "SQL execution failed: $f" }
        }
    }
    Write-Host 'Done. V14/V23 registered as success; start backend for V15+ migrate.' -ForegroundColor Green
} finally {
    Pop-Location
}
