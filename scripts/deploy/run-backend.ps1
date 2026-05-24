# 最简：打包并启动 examine-web（无 CI/CD）
# 用法：在仓库根目录  .\scripts\deploy\run-backend.ps1
# 可选：$env:SPRING_PROFILES_ACTIVE = "prod"
# 可选：$env:SKIP_FLYWAY_REPAIR = "1"  跳过启动前 DB 修复
$ErrorActionPreference = 'Stop'

$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Backend = Join-Path $Root 'backend'
$JarDir = Join-Path $Backend 'examine-web'
$Jar = Join-Path $JarDir 'target\examine-web-0.0.1-SNAPSHOT.jar'

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'D:\java\jdk\jdk21'
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $java)) {
    Write-Error "JAVA_HOME invalid: $env:JAVA_HOME"
}

if (-not $env:MAVEN_OPTS) {
    $env:MAVEN_OPTS = '-Xmx512m -XX:+UseSerialGC'
}
# 运行时堆用命令行 -Xmx，避免系统 JAVA_TOOL_OPTIONS 触发 G1 导致 native OOM
$heapMx = if ($env:EXAMINE_JAVA_XMX) { $env:EXAMINE_JAVA_XMX } else { '256m' }
$javaArgs = @("-Xmx$heapMx", '-XX:+UseSerialGC', '-jar', $Jar)

netstat -ano | Select-String ':\s*9999\s' | ForEach-Object {
    if ($_ -match '\s+(\d+)\s*$') {
        Stop-Process -Id $Matches[1] -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 1

if ($env:SKIP_FLYWAY_REPAIR -ne '1') {
    $repairScript = Join-Path $Root 'scripts\db\repair-flyway-failed.ps1'
    if (Test-Path $repairScript) {
        Write-Host 'Running Flyway repair SQL (clear success=0 only) ...' -ForegroundColor Cyan
        try {
            & $repairScript
        } catch {
            Write-Warning "Flyway repair skipped: $($_.Exception.Message)"
        }
    }
}

Push-Location $Backend
try {
    Write-Host 'Building examine-web ...' -ForegroundColor Cyan
    & mvn -pl examine-web -am package -DskipTests -q
} finally {
    Pop-Location
}

if (-not (Test-Path $Jar)) {
    Write-Error "JAR not found: $Jar"
}

$profile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
Write-Host "Starting examine-web (profile=$profile, -Xmx$heapMx SerialGC) ..." -ForegroundColor Cyan
Write-Host 'Manual V14/17: flyway_schema_history marked success; Flyway runs V15+ only.' -ForegroundColor DarkYellow
$env:JAVA_TOOL_OPTIONS = $null
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
$javaArgs += @("--spring.profiles.active=$profile", '--spring.flyway.validate-on-migrate=false')
& $java @javaArgs
