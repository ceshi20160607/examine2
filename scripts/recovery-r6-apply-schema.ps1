param(
    [string]$DbUrl = 'jdbc:mysql://192.168.0.211:3306/examine2?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true',
    [string]$DbUsername = 'examine',
    [string]$DbPassword = 'examine',
    [string]$JavaHome = 'D:\dev\jdk21',
    [string]$MysqlConnectorJar = ''
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($MysqlConnectorJar)) {
    $candidates = @(
        'C:\Users\ceshi\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar',
        'D:\dev\maven_repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar'
    )
    $MysqlConnectorJar = @($candidates | Where-Object { Test-Path $_ } | Select-Object -First 1)
}
if ([string]::IsNullOrWhiteSpace($MysqlConnectorJar) -or -not (Test-Path $MysqlConnectorJar)) {
    throw "MySQL connector jar not found."
}

$JavaExe = Join-Path $JavaHome 'bin\java.exe'
$JavacExe = Join-Path $JavaHome 'bin\javac.exe'
if (-not (Test-Path $JavaExe) -or -not (Test-Path $JavacExe)) {
    throw "JDK not found: $JavaHome"
}

$workDir = Join-Path ([System.IO.Path]::GetTempPath()) "unexamine-r6-schema"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
$javaFile = Join-Path $workDir 'ApplyRecoverySchema.java'

@'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ApplyRecoverySchema {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: ApplyRecoverySchema <url> <username> <password>");
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        String sql = """
CREATE TABLE IF NOT EXISTS un_system_data_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  source_code VARCHAR(80) NOT NULL COMMENT 'field',
  source_name VARCHAR(120) NOT NULL COMMENT 'field',
  source_type VARCHAR(40) NOT NULL COMMENT 'field',
  connection_config JSON NOT NULL COMMENT 'field',
  auth_config JSON NULL COMMENT 'field',
  sync_config JSON NULL COMMENT 'field',
  desensitize_config JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'field',
  published_version VARCHAR(80) NULL COMMENT 'field',
  last_check_status VARCHAR(40) NULL COMMENT 'field',
  last_check_trace_id VARCHAR(80) NULL COMMENT 'field',
  last_checked_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_data_source_code (system_id, tenant_id, source_code),
  KEY idx_data_source_system (system_id, tenant_id, source_type, status)
) COMMENT='table'
""";
        try (Connection connection = DriverManager.getConnection(args[0], args[1], args[2]);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        System.out.println("{\"status\":\"PASS\",\"table\":\"un_system_data_source\"}");
    }
}
'@ | Set-Content -LiteralPath $javaFile -Encoding ASCII

& $JavacExe -encoding UTF-8 -cp $MysqlConnectorJar $javaFile
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
& $JavaExe -cp "$workDir;$MysqlConnectorJar" ApplyRecoverySchema $DbUrl $DbUsername $DbPassword
if ($LASTEXITCODE -ne 0) {
    throw "schema migration failed with exit code $LASTEXITCODE"
}
