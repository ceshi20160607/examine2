$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$javaHome = "D:\dev\jdk21-temurin"
$maven = "D:\dev\maven\bin\mvn.cmd"
$node = "D:\dev\nodejs24\node.exe"
$port = 19099
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
    $proc = Start-Process -FilePath (Join-Path $javaHome "bin\java.exe") -ArgumentList @("-jar", $jar, "--server.port=$port") -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logs "auth-shell.out.log") -RedirectStandardError (Join-Path $logs "auth-shell.err.log") -WindowStyle Hidden -PassThru

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
    if (-not $login.token) { throw "login token missing" }
    if ($login.currentContext.scope -ne "platform") { throw "login should start in platform context" }
    if ($login.systems.Count -lt 1) { throw "system switch options missing" }

    $headers = @{ Authorization = "Bearer $($login.token)" }
    $profile = Invoke-RestMethod "$baseUrl/api/auth/profile" -Headers $headers
    if ($profile.profile.username -ne "admin") { throw "profile readback mismatch" }

    $firstSystem = $login.systems[0].systemId
    $systemContext = Invoke-RestMethod "$baseUrl/api/context/switch" -Headers $headers -Method Post -ContentType "application/json" -Body (@{ target = "system"; systemId = $firstSystem } | ConvertTo-Json)
    if ($systemContext.scope -ne "system") { throw "system switch failed" }
    if (-not $systemContext.accountMemberBindingId) { throw "accountMemberBindingId missing" }

    $shell = Invoke-RestMethod "$baseUrl/api/shell?context=system" -Headers $headers
    if ($shell.context -ne "system") { throw "shell context mismatch" }
    if ($shell.navigation.Count -ne 9) { throw "navigation count mismatch" }
    if ($shell.moduleGroups.Count -lt 1) { throw "module groups missing" }
    if ($shell.flowManagement.Count -lt 8) { throw "flow management depth missing" }
    if ($shell.applicationGateway.Count -lt 6) { throw "application gateway depth missing" }

    [pscustomobject]@{
        status = "PASS"
        health = $health.status
        loginUser = $login.profile.username
        initialContext = $login.currentContext.scope
        switchedContext = $systemContext.scope
        systemId = $systemContext.systemId
        accountMemberBindingId = $systemContext.accountMemberBindingId
        navCount = $shell.navigation.Count
        moduleGroupCount = $shell.moduleGroups.Count
        flowCount = $shell.flowManagement.Count
        applicationGatewayCount = $shell.applicationGateway.Count
        frontendDist = "frontend/dist/index.html"
    } | ConvertTo-Json -Depth 4
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
    Pop-Location
}