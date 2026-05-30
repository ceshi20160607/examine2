param(
    [switch]$Foreground,
    [switch]$NoBuild
)

# 最简：打包并启动 examine-web（无 CI/CD）
# 用法：在仓库根目录  .\scripts\deploy\run-backend.ps1
# 默认后台运行；前台调试可加 -Foreground
# 可选：$env:SPRING_PROFILES_ACTIVE = "prod"
# 可选：$env:SKIP_FLYWAY_REPAIR = "1"  跳过启动前 DB 修复
$ErrorActionPreference = 'Stop'

$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Backend = Join-Path $Root 'backend'
$JarDir = Join-Path $Backend 'examine-web'
$Jar = Join-Path $JarDir 'target\unexamine-0.0.1.jar'
$LogDir = Join-Path $Root 'logs'
$PidFile = Join-Path $LogDir 'examine-web.pid'
$StdoutLog = Join-Path $LogDir 'examine-web.stdout.log'
$StderrLog = Join-Path $LogDir 'examine-web.stderr.log'
$Port = if ($env:SERVER_PORT) { [string]$env:SERVER_PORT } else { '9999' }

function Get-JavaMajor([string]$javaExe) {
    if (-not $javaExe -or -not (Test-Path $javaExe)) { return 0 }
    $line = (& cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1)
    if ($line -match 'version "1\.([0-9]+)') { return [int]$Matches[1] }
    if ($line -match 'version "([0-9]+)') { return [int]$Matches[1] }
    return 0
}

function Resolve-JavaHome {
    $candidates = @()
    if ($env:EXAMINE_JAVA_HOME) { $candidates += $env:EXAMINE_JAVA_HOME }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        'D:\java\jdk\jdk21',
        'D:\java\jdk\jdk17',
        'C:\Program Files\Java\latest\jdk-21',
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Java\jdk-17'
    )

    foreach ($candidateHome in $candidates | Where-Object { $_ } | Select-Object -Unique) {
        $javaExe = Join-Path $candidateHome 'bin\java.exe'
        if ((Get-JavaMajor $javaExe) -ge 17) {
            return $candidateHome
        }
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and (Get-JavaMajor $javaCmd.Source) -ge 17) {
        return Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
    }

    throw 'Java 17+ is required. Set EXAMINE_JAVA_HOME or JAVA_HOME to a JDK 17/21 directory.'
}

function Normalize-ProcessPathEnv {
    if (-not ($IsWindows -or $env:OS -eq 'Windows_NT')) { return }
    $vars = [System.Environment]::GetEnvironmentVariables('Process')
    $pathValue = if ($vars.Contains('Path')) {
        [string]$vars['Path']
    } elseif ($vars.Contains('PATH')) {
        [string]$vars['PATH']
    } else {
        [string]$env:Path
    }
    if ($pathValue) {
        [System.Environment]::SetEnvironmentVariable('PATH', $null, 'Process')
        [System.Environment]::SetEnvironmentVariable('Path', $pathValue, 'Process')
    }
}

$env:JAVA_HOME = Resolve-JavaHome
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:Path
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Host "Using JAVA_HOME=$env:JAVA_HOME" -ForegroundColor DarkGreen

if (-not $env:MAVEN_OPTS) {
    $env:MAVEN_OPTS = '-Xmx512m -XX:+UseSerialGC'
}
# 运行时堆用命令行 -Xmx，避免系统 JAVA_TOOL_OPTIONS 触发 G1 导致 native OOM
$heapMx = if ($env:EXAMINE_JAVA_XMX) { $env:EXAMINE_JAVA_XMX } else { '256m' }
$javaArgs = @("-Xmx$heapMx", '-XX:+UseSerialGC', '-jar', $Jar)

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

if (Test-Path $PidFile) {
    $oldPid = (Get-Content $PidFile -Raw -ErrorAction SilentlyContinue).Trim()
    if ($oldPid -and (Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue)) {
        throw "examine-web is already running, pid=$oldPid. Stop it first if you want to restart."
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

$portPattern = "^\s*TCP\s+\S+:$([regex]::Escape($Port))\s+\S+\s+LISTENING\s+\d+\s*$"
$portOwner = netstat -ano | Select-String $portPattern | Select-Object -First 1
if ($portOwner -and $portOwner.Line -match '\s+(\d+)\s*$') {
    throw "Port $Port is already in use by pid=$($Matches[1]). Stop that process or set SERVER_PORT."
}

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

if (-not $NoBuild) {
    Push-Location $Backend
    try {
        Write-Host 'Building examine-web ...' -ForegroundColor Cyan
        & mvn -pl examine-web -am clean package -DskipTests -q
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $Jar)) {
    Write-Error "JAR not found: $Jar"
}

$profile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
Write-Host "Starting examine-web (profile=$profile, port=$Port, -Xmx$heapMx SerialGC) ..." -ForegroundColor Cyan
Write-Host 'Manual V14/17: flyway_schema_history marked success; Flyway runs V15+ only.' -ForegroundColor DarkYellow
$env:JAVA_TOOL_OPTIONS = $null
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
$javaArgs += @("--spring.profiles.active=$profile", '--spring.flyway.validate-on-migrate=false')

if ($Foreground) {
    & $java @javaArgs
    exit $LASTEXITCODE
}

Normalize-ProcessPathEnv
$process = Start-Process `
    -FilePath $java `
    -ArgumentList $javaArgs `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $StdoutLog `
    -RedirectStandardError $StderrLog `
    -WindowStyle Hidden `
    -PassThru

Set-Content -Path $PidFile -Value $process.Id -Encoding ascii
Write-Host "examine-web started in background. PID=$($process.Id)" -ForegroundColor Green
Write-Host "stdout: $StdoutLog"
Write-Host "stderr: $StderrLog"
Write-Host "app log: $(Join-Path $Root 'logs\examine-web.log')"
