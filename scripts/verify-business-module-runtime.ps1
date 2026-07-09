$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$javaHome = "D:\dev\jdk21-temurin"
$maven = "D:\dev\maven\bin\mvn.cmd"
$node = "D:\dev\nodejs24\node.exe"
$port = 19101
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
    & $node --check frontend/src/main.js

    $jar = Join-Path $root "backend\unexamine-web\target\unexamine-web-0.0.1-SNAPSHOT.jar"
    $javaExe = if (Test-Path (Join-Path $javaHome "bin\java.exe")) { Join-Path $javaHome "bin\java.exe" } else { "java" }
    $proc = Start-Process -FilePath $javaExe -ArgumentList @("-jar", $jar, "--server.port=$port") -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logs "business-module.out.log") -RedirectStandardError (Join-Path $logs "business-module.err.log") -WindowStyle Hidden -PassThru

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
    $systemId = $login.systems[0].systemId
    $context = Invoke-RestMethod "$baseUrl/api/context/switch" -Headers $headers -Method Post -ContentType "application/json" -Body (@{ target = "system"; systemId = $systemId } | ConvertTo-Json)
    if ($context.scope -ne "system") { throw "system context switch failed" }

    $modules = Invoke-RestMethod "$baseUrl/api/business/modules" -Headers $headers
    if ($modules.Count -lt 4) { throw "business module count too small" }
    $module = $modules | Where-Object { $_.moduleCode -eq "customer-record" } | Select-Object -First 1
    if (-not $module) { throw "customer-record module missing" }
    if ($module.fields.Count -lt 4 -or $module.actions.Count -lt 4 -or $module.pages.Count -lt 3 -or $module.printTemplates.Count -lt 2) { throw "module config depth missing" }

    $records = Invoke-RestMethod "$baseUrl/api/business/modules/customer-record/records" -Headers $headers
    if ($records.Count -lt 1) { throw "seed records missing" }

    $title = "runtime-record-" + (Get-Date -Format "yyyyMMddHHmmss")
    $createBody = @{ title = $title; values = @{ owner = "qa"; level = "A"; source = "verify" } } | ConvertTo-Json -Depth 5
    $created = Invoke-RestMethod "$baseUrl/api/business/modules/customer-record/records" -Headers $headers -Method Post -ContentType "application/json" -Body $createBody
    if ($created.title -ne $title -or $created.moduleCode -ne "customer-record") { throw "created record mismatch" }

    $readback = Invoke-RestMethod "$baseUrl/api/business/modules/customer-record/records/$($created.recordId)" -Headers $headers
    if ($readback.title -ne $title -or $readback.values.owner -ne "qa") { throw "record readback mismatch" }

    $stored = Get-Content (Join-Path $root "data\module-records.json") -Raw
    if (-not $stored.Contains($title)) { throw "module record persistence missing" }

    [pscustomobject]@{
        status = "PASS"
        moduleCount = $modules.Count
        verifiedModule = $module.moduleCode
        fieldCount = $module.fields.Count
        actionCount = $module.actions.Count
        pageCount = $module.pages.Count
        printTemplateCount = $module.printTemplates.Count
        createdRecordId = $created.recordId
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