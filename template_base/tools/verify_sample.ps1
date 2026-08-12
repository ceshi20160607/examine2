param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path,
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$generatedRoot = Join-Path $WorkspaceRoot "template_base/examples/work-order/generated"
$composePath = Join-Path $generatedRoot "deploy/compose.yaml"
$backendRoot = Join-Path $generatedRoot "backend"
$frontendRoot = Join-Path $generatedRoot "frontend"
$runtimeRoot = Join-Path $generatedRoot ".runtime"
$contractPath = Join-Path $WorkspaceRoot "template_base/examples/work-order/project.contract.json"
$manifestPath = Join-Path $generatedRoot "generation-manifest.json"

if (-not (Test-Path -LiteralPath $composePath)) {
    throw "Generated sample is missing. Run tb.py generate first."
}

New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
$backendOut = Join-Path $runtimeRoot "backend.out.log"
$backendErr = Join-Path $runtimeRoot "backend.err.log"
$frontendOut = Join-Path $runtimeRoot "frontend.out.log"
$frontendErr = Join-Path $runtimeRoot "frontend.err.log"
$evidencePath = Join-Path $runtimeRoot "delivery-evidence.json"
$backendJar = Join-Path $backendRoot "target/work-order-sample-0.1.0-SNAPSHOT.jar"
$backendProcess = $null
$frontendProcess = $null
$runtimeKept = $false

function Get-ListeningProcess {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) { return $null }
    return Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
}

function Assert-PortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)
    $process = Get-ListeningProcess -Port $Port
    if ($process) {
        throw "Port $Port is already used by PID $($process.ProcessId): $($process.CommandLine)"
    }
}

function Stop-GeneratedListener {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$ExpectedToken
    )
    $process = Get-ListeningProcess -Port $Port
    if (-not $process) { return }
    $normalizedCommand = [string]$process.CommandLine
    if ($normalizedCommand.IndexOf($ExpectedToken, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Refusing to stop unrelated listener on port ${Port}: PID $($process.ProcessId)"
    }
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)]$Process,
        [int]$Attempts = 90
    )
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if ($Process.HasExited) {
            throw "Process $($Process.Id) exited with code $($Process.ExitCode) before $Url became ready."
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "$Url did not become ready."
}

try {
    Assert-PortAvailable -Port 18080
    Assert-PortAvailable -Port 5173
    docker compose -f $composePath down --volumes --remove-orphans | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Previous Docker dependencies failed to reset."
    }
    docker compose -f $composePath up -d --wait
    if ($LASTEXITCODE -ne 0) {
        throw "Docker dependencies failed to start."
    }

    $env:JAVA_HOME = "D:/dev/jdk21-temurin"
    $env:Path = "$env:JAVA_HOME/bin;$env:Path"
    $env:APP_DB_URL = "jdbc:mysql://127.0.0.1:33077/template_base?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:APP_DB_USERNAME = "template_base"
    $env:APP_DB_PASSWORD = "template_base"
    $env:APP_REDIS_HOST = "127.0.0.1"
    $env:APP_REDIS_PORT = "36379"
    $env:E2E_BROWSER_EXECUTABLE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

    & "D:/dev/maven/bin/mvn.cmd" -q clean package -f (Join-Path $backendRoot "pom.xml")
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $backendJar)) {
        throw "Backend tests or package failed."
    }
    & "D:/dev/nodejs24/npm.cmd" install --ignore-scripts --prefix $frontendRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend dependency installation failed."
    }
    & "D:/dev/nodejs24/npm.cmd" run build --prefix $frontendRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend typecheck or production build failed."
    }

    $backendProcess = Start-Process `
        -FilePath (Join-Path $env:JAVA_HOME "bin/java.exe") `
        -ArgumentList @("-jar", $backendJar) `
        -WorkingDirectory $backendRoot `
        -RedirectStandardOutput $backendOut `
        -RedirectStandardError $backendErr `
        -PassThru `
        -WindowStyle Hidden
    $frontendProcess = Start-Process `
        -FilePath "D:/dev/nodejs24/npm.cmd" `
        -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1") `
        -WorkingDirectory $frontendRoot `
        -RedirectStandardOutput $frontendOut `
        -RedirectStandardError $frontendErr `
        -PassThru `
        -WindowStyle Hidden

    Wait-HttpReady -Url "http://127.0.0.1:18080/actuator/health" -Process $backendProcess
    Wait-HttpReady -Url "http://127.0.0.1:5173/work-orders" -Process $frontendProcess

    & "D:/dev/nodejs24/npm.cmd" run e2e --prefix $frontendRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Real browser journey failed."
    }

    $databaseReadback = docker compose -f $composePath exec -T mysql mysql --default-character-set=utf8mb4 `
        -utemplate_base -ptemplate_base template_base `
        -e "SELECT id,order_no,title,priority,urgent FROM tb_work_order;"
    if ($LASTEXITCODE -ne 0) {
        throw "Database readback failed."
    }
    Write-Output "--- persisted database row ---"
    Write-Output $databaseReadback

    $persistedCount = docker compose -f $composePath exec -T mysql mysql --default-character-set=utf8mb4 `
        -N -B -utemplate_base -ptemplate_base template_base `
        -e "SELECT COUNT(*) FROM tb_work_order WHERE order_no='TB-REAL-001';"
    if ($LASTEXITCODE -ne 0 -or [int]([string]$persistedCount).Trim() -lt 1) {
        throw "MySQL did not persist the row created through the browser."
    }

    $evidence = [ordered]@{
        schemaVersion = 1
        projectId = "work-order-sample"
        contractSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $contractPath).Hash.ToLowerInvariant()
        generationManifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
        verifiedAt = [DateTimeOffset]::Now.ToString("o")
        journeyMode = "browser-ui-to-api-to-mysql"
        mocked = $false
        checks = [ordered]@{
            backendTests = $true
            frontendBuild = $true
            backendHealth = $true
            frontendHealth = $true
            browserJourney = $true
            mysqlReadback = $true
        }
    }
    $evidenceJson = $evidence | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($evidencePath, $evidenceJson + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
    Write-Output "Runtime evidence: $evidencePath"

    if ($KeepRunning) {
        $runtimeKept = $true
        Write-Output "--- runtime kept for manual browser inspection ---"
        Write-Output "Frontend: http://127.0.0.1:5173/work-orders"
        Write-Output "Backend:  http://127.0.0.1:18080/actuator/health"
        Write-Output "Stop with: powershell -File template_base/tools/stop_sample.ps1"
    }
}
finally {
    if (-not $runtimeKept) {
        if ($frontendProcess -and -not $frontendProcess.HasExited) {
            Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
        }
        if ($backendProcess -and -not $backendProcess.HasExited) {
            Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
        }
        Stop-GeneratedListener -Port 5173 -ExpectedToken $generatedRoot
        Stop-GeneratedListener -Port 18080 -ExpectedToken $backendJar
        docker compose -f $composePath down --volumes --remove-orphans | Out-Null

        Write-Output "--- backend tail ---"
        if (Test-Path -LiteralPath $backendOut) { Get-Content -LiteralPath $backendOut -Tail 30 }
        if (Test-Path -LiteralPath $backendErr) { Get-Content -LiteralPath $backendErr -Tail 30 }
        Write-Output "--- frontend tail ---"
        if (Test-Path -LiteralPath $frontendOut) { Get-Content -LiteralPath $frontendOut -Tail 20 }
        if (Test-Path -LiteralPath $frontendErr) { Get-Content -LiteralPath $frontendErr -Tail 20 }
    }
}
