$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$javaHome = "D:\dev\jdk21-temurin"
$maven = "D:\dev\maven\bin\mvn.cmd"
$node = "D:\dev\nodejs24\node.exe"

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
}

if (-not (Test-Path $maven)) {
    $maven = "mvn"
}

if (-not (Test-Path $node)) {
    $node = "node"
}

Push-Location $root
try {
    & $maven -f backend/pom.xml -q -pl unexamine-web -am package
    & $node frontend/scripts/build.mjs
    [pscustomobject]@{
        status = "PASS"
        backend = "backend/unexamine-web/target/unexamine-web-0.0.1-SNAPSHOT.jar"
        frontend = "frontend/dist/index.html"
    } | ConvertTo-Json -Depth 3
}
finally {
    Pop-Location
}
