param(
    [ValidateSet('init-config', 'start', 'stop', 'restart', 'status', 'fg')]
    [string]$Mode = 'start'
)

$ErrorActionPreference = 'Stop'
$Dir = $PSScriptRoot
Set-Location $Dir

$AppName = 'unexamine'
$LogDir = Join-Path $Dir 'logs'
$PidFile = Join-Path $LogDir "$AppName.pid"
$ConsoleOut = Join-Path $LogDir "$AppName.console.out.log"
$ConsoleErr = Join-Path $LogDir "$AppName.console.err.log"
$ConfigDir = Join-Path $Dir 'config'
$ConfigFile = Join-Path $ConfigDir 'application.yml'

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
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

function Test-Running {
    if (-not (Test-Path $PidFile)) { return $false }
    $pidText = (Get-Content $PidFile -Raw).Trim()
    if (-not $pidText) { return $false }
    return [bool](Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue)
}

function Show-Status {
    if (Test-Running) {
        Write-Host "running pid=$((Get-Content $PidFile -Raw).Trim()) log=$ConsoleOut"
        return $true
    }
    if (Test-Path $PidFile) {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
    Write-Host 'not running'
    return $false
}

function Stop-App {
    if (-not (Test-Path $PidFile)) {
        Write-Host 'not running (no pid file)'
        return
    }

    $pidText = (Get-Content $PidFile -Raw).Trim()
    $process = if ($pidText) { Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue } else { $null }
    if ($process) {
        Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 1
            if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) { break }
        }
        if (Get-Process -Id $process.Id -ErrorAction SilentlyContinue) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
        Write-Host "stopped pid=$($process.Id)"
    } else {
        Write-Host 'stale pid file removed'
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

function Assert-Config {
    if (-not (Test-Path $ConfigFile)) {
        $example = Join-Path $ConfigDir 'application.yml.example'
        if (Test-Path $example) {
            Write-Error "Missing config\application.yml. Run '.\unexamine.ps1 init-config', then edit MySQL / Redis settings."
        }
        Write-Error "Missing config file: $ConfigFile"
    }
    $configText = Get-Content $ConfigFile -Raw
    if ($configText -match 'change-me|replace-with-random-32-plus-chars') {
        Write-Host 'Config still contains placeholders. Edit these lines before starting:' -ForegroundColor Yellow
        Select-String -Path $ConfigFile -Pattern 'change-me|replace-with-random-32-plus-chars' | ForEach-Object {
            Write-Host "$($_.LineNumber):$($_.Line)"
        }
        Write-Error "Edit config first: $ConfigFile"
    }
}

function New-RandomKey {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        return -join ($bytes | ForEach-Object { $_.ToString('x2') })
    } finally {
        $rng.Dispose()
    }
}

function Init-Config {
    if (-not (Test-Path $ConfigDir)) {
        New-Item -ItemType Directory -Path $ConfigDir | Out-Null
    }
    if (Test-Path $ConfigFile) {
        Write-Host "config\application.yml already exists: $ConfigFile"
        Write-Host 'Not overwritten.'
        return
    }
    $example = Join-Path $ConfigDir 'application.yml.example'
    if (-not (Test-Path $example)) {
        throw "Missing config template: $example"
    }
    $text = Get-Content $example -Raw
    $text = $text.Replace('replace-with-random-32-plus-chars', (New-RandomKey))
    Set-Content -Path $ConfigFile -Value $text -Encoding utf8
    Write-Host "created $ConfigFile"
    Write-Host 'A random examine.openapi.signing-master-key has been generated.'
    Write-Host "Now edit datasource/redis settings, then run: .\unexamine.ps1 start"
    Select-String -Path $ConfigFile -Pattern 'change-me|replace-with-random-32-plus-chars' -ErrorAction SilentlyContinue
}

function Resolve-Java {
    $candidates = @()
    if ($env:EXAMINE_JAVA_HOME) { $candidates += (Join-Path $env:EXAMINE_JAVA_HOME 'bin\java.exe') }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $candidates += $javaCmd.Source }

    foreach ($java in $candidates | Where-Object { $_ } | Select-Object -Unique) {
        if (-not (Test-Path $java)) { continue }
        $javaVersion = & cmd /c "`"$java`" -version 2>&1" | Select-Object -First 1
        $major = 0
        if ($javaVersion -match 'version "1\.([0-9]+)') { $major = [int]$Matches[1] }
        elseif ($javaVersion -match 'version "([0-9]+)') { $major = [int]$Matches[1] }
        if ($major -ge 17) {
            return $java
        }
    }

    Write-Error 'Java 17+ is required. Set EXAMINE_JAVA_HOME or JAVA_HOME to a JDK 17/21 directory.'
}

function Build-JavaArgs {
    $jar = Join-Path $Dir 'unexamine-0.0.1.jar'
    if (-not (Test-Path $jar)) { throw "missing unexamine-0.0.1.jar in $Dir" }

    $xmx = if ($env:EXAMINE_JAVA_XMX) { $env:EXAMINE_JAVA_XMX } else { '512m' }
    $configUri = "optional:file:${ConfigDir}/"
    return @(
        "-Xmx$xmx",
        '-XX:+UseSerialGC',
        '-jar',
        $jar,
        "--spring.config.additional-location=$configUri"
    )
}

function Start-App {
    Assert-Config
    if (Test-Running) {
        Write-Error "already running pid=$((Get-Content $PidFile -Raw).Trim())"
    }

    Normalize-ProcessPathEnv
    $java = Resolve-Java
    $javaArgs = Build-JavaArgs
    $process = Start-Process `
        -FilePath $java `
        -ArgumentList $javaArgs `
        -WorkingDirectory $Dir `
        -RedirectStandardOutput $ConsoleOut `
        -RedirectStandardError $ConsoleErr `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $PidFile -Value $process.Id -Encoding ascii
    Start-Sleep -Seconds 1
    if (Test-Running) {
        Write-Host "started pid=$($process.Id)"
        Write-Host "stdout: $ConsoleOut"
        Write-Host "stderr: $ConsoleErr"
        return
    }
    Write-Error "start failed. See $ConsoleErr"
}

switch ($Mode) {
    'init-config' { Init-Config }
    'start' { Start-App }
    'stop' { Stop-App }
    'restart' { Stop-App; Start-App }
    'status' { if (Show-Status) { exit 0 } else { exit 1 } }
    'fg' {
        Assert-Config
        $java = Resolve-Java
        $javaArgs = Build-JavaArgs
        & $java @javaArgs
        exit $LASTEXITCODE
    }
}
