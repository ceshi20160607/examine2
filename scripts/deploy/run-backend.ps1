# 最简：打包并启动 examine-web（无 CI/CD）
# 用法：在仓库根目录  .\scripts\deploy\run-backend.ps1
# 可选：$env:SPRING_PROFILES_ACTIVE = "prod"
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
    Write-Error "JAVA_HOME 无效: $env:JAVA_HOME ，请设置 JAVA_HOME 后重试。"
}

Get-NetTCPConnection -LocalPort 9999 -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 1

Push-Location $Backend
try {
    & mvn -pl examine-web -am package -DskipTests -q
} finally {
    Pop-Location
}

if (-not (Test-Path $Jar)) {
    Write-Error "未找到 JAR: $Jar"
}

$profile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
Write-Host "Starting examine-web (profile=$profile) ..." -ForegroundColor Cyan
& $java -jar $Jar "--spring.profiles.active=$profile"
