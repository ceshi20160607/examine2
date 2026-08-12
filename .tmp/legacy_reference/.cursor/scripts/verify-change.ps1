param(
    [string[]]$BackendModule = @(),
    [switch]$Frontend,
    [switch]$FrontendBuild,
    [switch]$FullBackend,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Resolve-Java21Home {
    param([string]$Requested)

    $candidates = @(
        $Requested,
        $env:EXAMINE_JAVA_HOME,
        $env:JAVA_HOME,
        'D:\dev\jdk21',
        'C:\Program Files\Java\jdk-21'
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($candidate in $candidates) {
        $releasePath = Join-Path $candidate 'release'
        $javaPath = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $releasePath) -or -not (Test-Path -LiteralPath $javaPath)) {
            continue
        }
        $release = Get-Content -Raw -LiteralPath $releasePath
        if ($release -match 'JAVA_VERSION="21(?:\.|")') {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'JDK 21 was not found. Set EXAMINE_JAVA_HOME or pass -JavaHome.'
}

function Invoke-Checked {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Push-Location -LiteralPath $WorkingDirectory
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Executable exited with code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

if ($BackendModule.Count -eq 0 -and -not $Frontend -and -not $FrontendBuild -and -not $FullBackend) {
    throw 'Select -BackendModule, -Frontend, -FrontendBuild or -FullBackend.'
}

if ($BackendModule.Count -gt 0 -or $FullBackend) {
    $resolvedJavaHome = Resolve-Java21Home -Requested $JavaHome
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = (Join-Path $resolvedJavaHome 'bin') + ';' + $env:Path
    $maven = (Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1).Source
    if ([string]::IsNullOrWhiteSpace($maven)) {
        throw 'Maven was not found on PATH.'
    }

    foreach ($module in $BackendModule) {
        if ($module -notmatch '^examine-[a-z0-9-]+$') {
            throw "Invalid backend module name: $module"
        }
        $pom = Join-Path $projectRoot "backend\$module\pom.xml"
        if (-not (Test-Path -LiteralPath $pom)) {
            throw "Backend module does not exist: $module"
        }
        Invoke-Checked -Executable $maven -Arguments @('-f', $pom, 'test') -WorkingDirectory $projectRoot
    }

    if ($FullBackend) {
        Invoke-Checked -Executable $maven -Arguments @('test') -WorkingDirectory (Join-Path $projectRoot 'backend')
    }
}

if ($Frontend -or $FrontendBuild) {
    $npm = (Get-Command npm.cmd, npm -ErrorAction SilentlyContinue | Select-Object -First 1).Source
    if ([string]::IsNullOrWhiteSpace($npm)) {
        throw 'npm was not found on PATH.'
    }
    $frontendRoot = Join-Path $projectRoot 'frontend'
    if ($Frontend) {
        Invoke-Checked -Executable $npm -Arguments @('test') -WorkingDirectory $frontendRoot
    }
    if ($FrontendBuild) {
        Invoke-Checked -Executable $npm -Arguments @('run', 'build') -WorkingDirectory $frontendRoot
    }
}

Write-Output 'Affected-scope verification passed.'
