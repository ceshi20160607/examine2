# 本机构建脚本（Windows）
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root ".tools"
$mavenHome = Join-Path $tools "apache-maven-3.8.8"
$nodeHome = Join-Path $tools "node-v20.18.0-win-x64"

function Ensure-Maven {
    if (Test-Path (Join-Path $mavenHome "bin\mvn.cmd")) { return }
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "maven.zip"
    Write-Host "Downloading Maven 3.8.8..."
    Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
}

function Ensure-Node {
    if (Test-Path (Join-Path $nodeHome "node.exe")) { return }
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "node.zip"
    Write-Host "Downloading Node 20..."
    Invoke-WebRequest -Uri "https://nodejs.org/dist/v20.18.0/node-v20.18.0-win-x64.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
}

if (Test-Path "D:\java\jdk\jdk21") {
    $env:JAVA_HOME = "D:\java\jdk\jdk21"
} elseif (Test-Path "D:\dev\jdk21") {
    $env:JAVA_HOME = "D:\dev\jdk21"
} elseif (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $props = java -XshowSettings:properties -version 2>&1 | Out-String
    if ($props -match "java.home = (.+)") {
        $env:JAVA_HOME = $Matches[1].Trim()
    } else {
        $javaExe = (Get-Command java -ErrorAction Stop).Source
        $env:JAVA_HOME = Split-Path (Split-Path $javaExe)
    }
}

$mvn = if (Get-Command mvn -ErrorAction SilentlyContinue) { "mvn" } else {
    Ensure-Maven
    Join-Path $mavenHome "bin\mvn.cmd"
}

Ensure-Node
$env:Path = "$(Split-Path $mvn);$nodeHome;$env:JAVA_HOME\bin;$env:Path"

Write-Host "== Backend compile =="
Set-Location (Join-Path $root "backend")
& $mvn clean compile -DskipTests
if ($LASTEXITCODE -ge 1) { exit $LASTEXITCODE }

Write-Host "== Frontend build =="
Set-Location (Join-Path $root "frontend")
& (Join-Path $nodeHome "npm.cmd") install
& (Join-Path $nodeHome "npm.cmd") run build
if ($LASTEXITCODE -ge 1) { exit $LASTEXITCODE }

Write-Host "== Done =="
Write-Host "Backend: backend/examine-web/target/"
Write-Host "Frontend: frontend/dist/"
