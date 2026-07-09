$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$javaHome = "D:\dev\jdk21-temurin"
$maven = "D:\dev\maven\bin\mvn.cmd"
$node = "D:\dev\nodejs24\node.exe"
$port = 19100
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Path $logs -Force | Out-Null

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
}
if (-not (Test-Path $maven)) { $maven = "mvn" }
if (-not (Test-Path $node)) { $node = "node" }

Push-Location $root
$proc = $null
try {
    & $maven -f backend/pom.xml -q -pl unexamine-web -am package
    & $node frontend/scripts/build.mjs | Out-Host

    $jar = Join-Path $root "backend\unexamine-web\target\unexamine-web-0.0.1-SNAPSHOT.jar"
    $javaExe = if (Test-Path (Join-Path $javaHome "bin\java.exe")) { Join-Path $javaHome "bin\java.exe" } else { "java" }
    $proc = Start-Process -FilePath $javaExe -ArgumentList @("-jar", $jar, "--server.port=$port") -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logs "admin-config.out.log") -RedirectStandardError (Join-Path $logs "admin-config.err.log") -WindowStyle Hidden -PassThru

    $baseUrl = "http://127.0.0.1:$port"
    $health = $null
    for ($i = 0; $i -lt 40; $i++) {
        try {
            $health = Invoke-RestMethod "$baseUrl/api/v1/health" -TimeoutSec 2
            if ($health.status -eq "UP") { break }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $health -or $health.status -ne "UP") { throw "health check failed" }

    $login = Invoke-RestMethod "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ username = "admin"; password = "123123aa" } | ConvertTo-Json)
    $headers = @{ Authorization = "Bearer $($login.token)" }

    $configs = Invoke-RestMethod "$baseUrl/api/admin/config" -Headers $headers
    if ($configs.Count -lt 13) { throw "admin config count too small: $($configs.Count)" }
    $required = @("basic", "org", "role", "module", "flow", "application", "dashboard", "dict", "work", "app-config", "ai", "datasource", "log")
    foreach ($code in $required) {
        if (-not ($configs | Where-Object { $_.code -eq $code })) { throw "missing admin config: $code" }
    }

    $desc = "admin-config-readback-" + (Get-Date -Format "yyyyMMddHHmmss")
    $updated = Invoke-RestMethod "$baseUrl/api/admin/config/basic" -Headers $headers -Method Patch -ContentType "application/json" -Body (@{ description = $desc; status = "published" } | ConvertTo-Json)
    if ($updated.description -ne $desc -or $updated.status -ne "published") { throw "patch response mismatch" }

    $readback = Invoke-RestMethod "$baseUrl/api/admin/config/basic" -Headers $headers
    if ($readback.description -ne $desc -or $readback.status -ne "published") { throw "readback mismatch" }

    $stored = Get-Content (Join-Path $root "data\admin-config.json") -Raw
    if (-not $stored.Contains($desc)) { throw "file persistence readback missing" }

    [pscustomobject]@{
        status = "PASS"
        configCount = $configs.Count
        patchedCode = $readback.code
        patchedStatus = $readback.status
        persisted = $true
        frontendDist = "frontend/dist/index.html"
    } | ConvertTo-Json -Depth 4
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}