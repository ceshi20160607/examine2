param(
    [string]$Version = '0.0.1-SNAPSHOT',
    [string]$JavaHome = 'D:\java\jdk\jdk21',
    [string]$MavenPath = 'D:\java\apache-maven-3.8.5\bin\mvn.cmd',
    [string]$NpmPath = 'D:\java\nodejs\npm.cmd',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ReleaseDir = Join-Path $RepoRoot "release\unexamine-$Version"
$ZipPath = Join-Path $RepoRoot "release\unexamine-$Version.zip"
$BackendJar = Join-Path $RepoRoot "backend\examine-web\target\examine-web-$Version.jar"
$FrontendDist = Join-Path $RepoRoot 'frontend\dist'
$TemplateDir = Join-Path $RepoRoot 'deploy\templates'
$VerifyScript = Join-Path $RepoRoot 'scripts\verify-release.ps1'

function Assert-UnderRepo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRepo = [System.IO.Path]::GetFullPath($RepoRoot)
    if (-not $fullPath.StartsWith($fullRepo, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refuse to operate outside repository: $fullPath"
    }
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

if (-not $SkipBuild) {
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
        if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
            throw "JDK not found: $JavaHome"
        }
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;$env:Path"
    }
    Push-Location $RepoRoot
    try {
        Invoke-Native -FilePath $MavenPath -Arguments @('-f', 'backend/pom.xml', '-pl', 'examine-web', '-am', '-DskipTests', 'package')
        Invoke-Native -FilePath $NpmPath -Arguments @('--prefix', 'frontend', 'run', 'build')
    } finally {
        Pop-Location
    }
} else {
    throw "-SkipBuild is not allowed for a verified release package. Build backend and frontend before packaging."
}

if (-not (Test-Path $BackendJar)) {
    throw "Backend jar not found: $BackendJar"
}
if (-not (Test-Path $FrontendDist)) {
    throw "Frontend dist not found: $FrontendDist"
}
if (-not (Test-Path $TemplateDir)) {
    throw "Deploy template not found: $TemplateDir"
}
if (-not (Test-Path $VerifyScript)) {
    throw "Release verification script not found: $VerifyScript"
}

Assert-UnderRepo $ReleaseDir
Assert-UnderRepo $ZipPath

New-Item -ItemType Directory -Force -Path (Join-Path $RepoRoot 'release') | Out-Null
if (Test-Path $ReleaseDir) {
    Remove-Item -LiteralPath $ReleaseDir -Recurse -Force
}
if (Test-Path $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
Copy-Item -Path (Join-Path $TemplateDir '*') -Destination $ReleaseDir -Recurse -Force
Copy-Item -Path $VerifyScript -Destination (Join-Path $ReleaseDir 'verify-release.ps1') -Force

$BackendReleaseDir = Join-Path $ReleaseDir 'backend'
$FrontendReleaseDir = Join-Path $ReleaseDir 'frontend'

New-Item -ItemType Directory -Force -Path $BackendReleaseDir, $FrontendReleaseDir | Out-Null
Copy-Item -Path $BackendJar -Destination (Join-Path $BackendReleaseDir 'examine-web.jar') -Force
Copy-Item -Path (Join-Path $FrontendDist '*') -Destination $FrontendReleaseDir -Recurse -Force

$FrontendRuntimeConfig = Join-Path $FrontendReleaseDir 'config.js'
if (-not (Test-Path $FrontendRuntimeConfig)) {
    throw "Frontend runtime config not found: $FrontendRuntimeConfig"
}

New-Item -ItemType Directory -Force -Path `
    (Join-Path $BackendReleaseDir 'logs'), `
    (Join-Path $BackendReleaseDir 'data\uploads') | Out-Null

$compressed = $false
for ($i = 1; $i -le 5; $i++) {
    try {
        Compress-Archive -Path (Join-Path $ReleaseDir '*') -DestinationPath $ZipPath -Force
        $compressed = $true
        break
    } catch {
        if ($i -eq 5) {
            throw
        }
        Start-Sleep -Seconds 2
    }
}

if (-not $compressed) {
    throw "Failed to create release zip: $ZipPath"
}

$result = [ordered]@{
    status = 'PASS'
    version = $Version
    releaseDir = $ReleaseDir
    zipPath = $ZipPath
    backendJar = Join-Path $BackendReleaseDir 'examine-web.jar'
    backendConfig = Join-Path $BackendReleaseDir 'application.yml'
    frontendIndex = Join-Path $FrontendReleaseDir 'index.html'
    frontendApiMode = 'same-origin-nginx-proxy'
    nginxTemplate = Join-Path $ReleaseDir 'nginx\unexamine.conf'
    verifyScript = Join-Path $ReleaseDir 'verify-release.ps1'
}

$result | ConvertTo-Json
