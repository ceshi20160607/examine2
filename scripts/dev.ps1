# 本地开发：启动后端 JAR + 前端 Vite（Windows）
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "backend\examine-web\target\unexamine.jar"
$frontend = Join-Path $root "frontend"
$nodeHome = Join-Path $root ".tools\node-v20.18.0-win-x64"

if (-not (Test-Path $jar)) {
    Write-Host "JAR 不存在，先执行 scripts/build.ps1"
    & (Join-Path $root "scripts\build.ps1")
}

if (Test-Path "D:\dev\jdk21") {
    $env:JAVA_HOME = "D:\dev\jdk21"
} elseif (Test-Path "D:\java\jdk\jdk21") {
    $env:JAVA_HOME = "D:\java\jdk\jdk21"
}

$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Backend: http://127.0.0.1:9999"
Start-Process -FilePath "java" -ArgumentList "-jar", $jar -WorkingDirectory (Split-Path $jar) -WindowStyle Minimized

if (-not (Test-Path (Join-Path $nodeHome "npm.cmd"))) {
    & (Join-Path $root "scripts\build.ps1")
}

Write-Host "Frontend: http://localhost:5173"
Set-Location $frontend
& (Join-Path $nodeHome "npm.cmd") run dev
