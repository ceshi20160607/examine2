# Build a deployable release package:
# backend JAR + web static assets + mobile H5 assets.
# Usage from repository root:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\deploy\build-release.ps1

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$DistRoot = Join-Path $Root 'dist'
$OutRoot = Join-Path $DistRoot 'release'
$ZipPath = Join-Path $DistRoot "unexamine-release-$Stamp.zip"

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
        $java = Join-Path $candidateHome 'bin\java.exe'
        if ((Get-JavaMajor $java) -ge 17) {
            return $candidateHome
        }
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and (Get-JavaMajor $javaCmd.Source) -ge 17) {
        return Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
    }

    throw 'Java 17+ is required to build examine2. Set EXAMINE_JAVA_HOME or JAVA_HOME to a JDK 17/21 directory.'
}

function Resolve-NodeCommand([string]$Name, [switch]$Optional) {
    $candidates = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        @("$Name.cmd", "$Name.exe", $Name)
    } else {
        @($Name)
    }
    foreach ($candidate in $candidates) {
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    if ($Optional) { return $null }
    throw "$Name is required. Install Node.js and ensure $Name is on PATH."
}

$env:JAVA_HOME = Resolve-JavaHome
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:Path
$env:MAVEN_OPTS = '-Xmx512m -XX:+UseSerialGC'
Write-Host "Using JAVA_HOME=$env:JAVA_HOME" -ForegroundColor DarkGreen
$npm = Resolve-NodeCommand 'npm'
$pnpm = Resolve-NodeCommand 'pnpm' -Optional

Write-Host '=== 1/4 Backend JAR ===' -ForegroundColor Cyan
Push-Location (Join-Path $Root 'backend')
try {
    & mvn -pl examine-web -am clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw "mvn failed $LASTEXITCODE" }
} finally {
    Pop-Location
}

$jarSrc = Join-Path $Root 'backend\examine-web\target\unexamine-0.0.1.jar'
if (-not (Test-Path $jarSrc)) { throw "JAR not found: $jarSrc" }

Write-Host '=== 2/4 Web (vue3) ===' -ForegroundColor Cyan
$Vue = Join-Path $Root 'web\vue3'
$env:VITE_API_BASE = '/api'
Push-Location $Vue
try {
    if (-not (Test-Path 'node_modules')) { & $npm install --no-fund --no-audit }
    & $npm run build
    if ($LASTEXITCODE -ne 0) { throw 'web build failed' }
} finally {
    Pop-Location
}

Write-Host '=== 3/4 Mobile H5 (uniapp) ===' -ForegroundColor Cyan
$Uni = Join-Path $Root 'mobile\uniapp'
$env:VITE_API_BASE = '/api'
Push-Location $Uni
try {
    if (-not (Test-Path 'node_modules')) {
        if ($pnpm) { & $pnpm install --no-fund } else { & $npm install --no-fund --no-audit }
    }
    if ($pnpm) {
        & $pnpm run build:h5
    } else {
        & $npm run build:h5
    }
    if ($LASTEXITCODE -ne 0) { throw 'mobile h5 build failed' }
} finally {
    Pop-Location
}

$buildMp = $env:BUILD_MP_WEIXIN -eq '1'
if ($buildMp) {
    Write-Host '=== 3b WeChat mini program ===' -ForegroundColor Cyan
    Push-Location $Uni
    try {
        if ($pnpm) { & $pnpm run build:mp-weixin } else { & $npm run build:mp-weixin }
        if ($LASTEXITCODE -ne 0) { throw 'mp-weixin build failed' }
    } finally {
        Pop-Location
    }
}

Write-Host '=== 4/4 Assemble dist/release ===' -ForegroundColor Cyan
if (Test-Path $OutRoot) { Remove-Item $OutRoot -Recurse -Force }
$beOut = Join-Path $OutRoot 'backend'
$webOut = Join-Path $OutRoot 'web'
$mobH5 = Join-Path $OutRoot 'mobile\h5'
New-Item -ItemType Directory -Force -Path $beOut, $webOut, $mobH5 | Out-Null

$jarDst = Join-Path $beOut 'unexamine-0.0.1.jar'
Copy-Item $jarSrc $jarDst -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    $jarZip = [System.IO.Compression.ZipFile]::OpenRead($jarDst)
    if ($jarZip.Entries.Count -lt 10) { throw 'too few entries' }
    $jarZip.Dispose()
} catch {
    throw "JAR corrupt: $jarDst ($_). Re-run mvn package."
}
Write-Host "JAR verified OK ($((Get-Item $jarDst).Length) bytes)" -ForegroundColor DarkGreen

$relTpl = Join-Path $PSScriptRoot 'release\backend'
function Copy-ShellLf([string]$name) {
    $src = Join-Path $relTpl $name
    $dst = Join-Path $beOut $name
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $text = (Get-Content $src -Raw) -replace "`r`n", "`n" -replace "`r", ''
    [System.IO.File]::WriteAllText($dst, $text, $utf8)
}
Copy-ShellLf 'unexamine.sh'
Copy-Item (Join-Path $relTpl 'unexamine.ps1') $beOut

$configTpl = Join-Path $PSScriptRoot 'release\config'
$configOut = Join-Path $beOut 'config'
New-Item -ItemType Directory -Force -Path $configOut | Out-Null
Copy-Item (Join-Path $configTpl 'application.yml.example') (Join-Path $configOut 'application.yml.example')

$nginxOut = Join-Path $OutRoot 'nginx'
New-Item -ItemType Directory -Force -Path $nginxOut | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'nginx\examine.conf') (Join-Path $nginxOut 'examine.conf')

$webDist = Join-Path $Vue 'dist'
if (-not (Test-Path $webDist)) { throw "web dist missing: $webDist" }
Copy-Item -Path (Join-Path $webDist '*') -Destination $webOut -Recurse

$h5Dist = Join-Path $Uni 'dist\build\h5'
if (-not (Test-Path $h5Dist)) {
    $alt = Get-ChildItem (Join-Path $Uni 'dist') -Recurse -Directory -Filter 'h5' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($alt) { $h5Dist = $alt.FullName }
}
if (-not (Test-Path $h5Dist)) { throw "mobile h5 dist missing under $Uni\dist" }
Copy-Item -Path (Join-Path $h5Dist '*') -Destination $mobH5 -Recurse

if ($buildMp) {
    $mpSrc = Join-Path $Uni 'dist\build\mp-weixin'
    if (Test-Path $mpSrc) {
        $mpOut = Join-Path $OutRoot 'mobile\mp-weixin'
        New-Item -ItemType Directory -Force -Path $mpOut | Out-Null
        Copy-Item -Path (Join-Path $mpSrc '*') -Destination $mpOut -Recurse
    }
}

$readme = @'
# unexamine release package ({{STAMP}})

## Contents

| Path | Description |
|------|-------------|
| `backend/` | `unexamine-0.0.1.jar`, `unexamine.sh`, `unexamine.ps1`, and `config/application.yml.example` |
| `web/` | Admin web static files, suitable for an Nginx root |
| `mobile/h5/` | Mobile H5 static files, usually mounted under `/m/` |
| `nginx/examine.conf` | Nginx example, including `/api/` reverse proxy |

## API Path

- Frontend builds use `VITE_API_BASE=/api`.
- Browser requests use `/api/v1/...`.
- Nginx should proxy `/api/` to `http://127.0.0.1:9999/`, so backend receives `/v1/...`.

## Backend

Linux:

```bash
cd backend
chmod +x unexamine.sh
./unexamine.sh init-config
# Edit MySQL / Redis settings in config/application.yml.
./unexamine.sh start
./unexamine.sh status
./unexamine.sh stop
```

Windows:

```powershell
cd backend
.\unexamine.ps1 init-config
# Edit MySQL / Redis settings in config\application.yml.
.\unexamine.ps1 start
.\unexamine.ps1 status
.\unexamine.ps1 stop
```

## Nginx

1. Put `web/` under `/opt/examine/web` or your preferred Nginx root.
2. Put `mobile/h5/` under `/opt/examine/mobile-h5` if `/m/` is enabled.
3. Copy or include `nginx/examine.conf`.
4. Run `nginx -t && systemctl reload nginx`.

## Mobile

- H5 uses `/api` when hosted on the same domain.
- WeChat mini program output is generated only when `BUILD_MP_WEIXIN=1`.
'@
$readme = $readme.Replace('{{STAMP}}', $Stamp)
Set-Content -Path (Join-Path $OutRoot 'README-DEPLOY.md') -Value $readme -Encoding UTF8

if (-not (Test-Path $DistRoot)) { New-Item -ItemType Directory -Force -Path $DistRoot | Out-Null }
Get-ChildItem -Path $DistRoot -Filter 'unexamine-release-*.zip' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem -Path $DistRoot -Filter 'examine2-release-*.zip' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Compress-Archive -Path (Join-Path $OutRoot '*') -DestinationPath $ZipPath -Force

Write-Host "Release folder: $OutRoot" -ForegroundColor Green
Write-Host "Zip: $ZipPath" -ForegroundColor Green
