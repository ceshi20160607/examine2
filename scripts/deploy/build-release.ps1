# 一键打开发布包：后端 JAR + Web 静态 + 手机 H5（+ 可选微信小程序）
# 用法（仓库根）: .\scripts\deploy\build-release.ps1
# 产物: dist\release\  与 dist\examine2-release-<时间戳>.zip

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$OutRoot = Join-Path $Root 'dist\release'
$ZipPath = Join-Path $Root "dist\examine2-release-$Stamp.zip"

if (-not $env:JAVA_HOME -or $env:JAVA_HOME -match 'jdk8|jre8') {
    $env:JAVA_HOME = if ($env:EXAMINE_JAVA_HOME) { $env:EXAMINE_JAVA_HOME } else { 'D:\java\jdk\jdk21' }
}
$env:MAVEN_OPTS = '-Xmx512m -XX:+UseSerialGC'

Write-Host '=== 1/4 Backend JAR ===' -ForegroundColor Cyan
Push-Location (Join-Path $Root 'backend')
try {
    & mvn -pl examine-web -am package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw "mvn failed $LASTEXITCODE" }
} finally { Pop-Location }

$jarSrc = Join-Path $Root 'backend\examine-web\target\examine-web-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jarSrc)) { throw "JAR not found: $jarSrc" }

Write-Host '=== 2/4 Web (vue3) ===' -ForegroundColor Cyan
$Vue = Join-Path $Root 'web\vue3'
$env:VITE_API_BASE = '/api'
Push-Location $Vue
try {
    if (-not (Test-Path 'node_modules')) { npm install --no-fund --no-audit }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "web build failed" }
} finally { Pop-Location }

Write-Host '=== 3/4 Mobile H5 (uniapp) ===' -ForegroundColor Cyan
$Uni = Join-Path $Root 'mobile\uniapp'
$env:VITE_API_BASE = '/api'
Push-Location $Uni
try {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    $npmCmd = if ($pnpm) { 'pnpm' } else { 'npm' }
    if (-not (Test-Path 'node_modules')) {
        if ($pnpm) { corepack pnpm install --no-fund } else { npm install --no-fund --no-audit }
    }
    if ($pnpm) {
        corepack pnpm run build:h5
    } else {
        npm run build:h5
    }
    if ($LASTEXITCODE -ne 0) { throw "mobile h5 build failed" }
} finally { Pop-Location }

$buildMp = $env:BUILD_MP_WEIXIN -eq '1'
if ($buildMp) {
    Write-Host '=== 3b WeChat mini program ===' -ForegroundColor Cyan
    Push-Location $Uni
    try {
        if ($pnpm) { corepack pnpm run build:mp-weixin } else { npm run build:mp-weixin }
    } finally { Pop-Location }
}

Write-Host '=== 4/4 Assemble dist/release ===' -ForegroundColor Cyan
if (Test-Path $OutRoot) { Remove-Item $OutRoot -Recurse -Force }
$beOut = Join-Path $OutRoot 'backend'
$webOut = Join-Path $OutRoot 'web'
$mobH5 = Join-Path $OutRoot 'mobile\h5'
New-Item -ItemType Directory -Force -Path $beOut, $webOut, $mobH5 | Out-Null

$jarDst = Join-Path $beOut 'examine-web-0.0.1-SNAPSHOT.jar'
Copy-Item $jarSrc $jarDst -Force
# 校验 JAR 为有效 ZIP（损坏包常见：上传中断、文本模式 FTP）
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    $z = [System.IO.Compression.ZipFile]::OpenRead($jarDst)
    if ($z.Entries.Count -lt 10) { throw 'too few entries' }
    $z.Dispose()
} catch {
    throw "JAR corrupt: $jarDst ($_). Re-run mvn package."
}
Write-Host "JAR verified OK ($((Get-Item $jarDst).Length) bytes)" -ForegroundColor DarkGreen
$relTpl = Join-Path $PSScriptRoot 'release\backend'
function Copy-ShellLf([string]$name) {
    $src = Join-Path $relTpl $name
    $dst = Join-Path $beOut $name
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $text = (Get-Content $src -Raw) -replace "`r`n", "`n" -replace "`r", ""
    [System.IO.File]::WriteAllText($dst, $text, $utf8)
}
foreach ($sh in @('start.sh', 'stop.sh', 'status.sh')) { Copy-ShellLf $sh }
Copy-Item (Join-Path $relTpl 'start.ps1') $beOut
Copy-Item (Join-Path $relTpl 'application.env.example') $beOut
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

$readme = @"
# examine2 发布包 ($Stamp)

## 目录

| 路径 | 说明 |
|------|------|
| ``backend/`` | ``examine-web-0.0.1-SNAPSHOT.jar`` + 启动脚本 |
| ``web/`` | 管理台静态资源（Nginx root） |
| ``mobile/h5/`` | 手机 H5 静态（可选 Nginx ``/m/``） |
| ``nginx/examine.conf`` | Nginx 示例：``/api/`` 反代后端 |

## API 路径约定

- 前端构建 ``VITE_API_BASE=/api``
- 浏览器请求：``/api/v1/...``
- Nginx：``location /api/ { proxy_pass http://127.0.0.1:9999/; }`` → 后端 ``/v1/...``

## 后端启动

```bash
cd backend
cp application.env.example application.env
# 编辑 MySQL / Redis / 密钥
chmod +x start.sh
./start.sh
```

Windows: ``.\start.ps1``（先配置 ``application.env``）

## Nginx 部署

1. 将 ``web/`` 放到 ``/opt/examine/web``（或改 nginx 内 root）
2. 将 ``mobile/h5/`` 放到 ``/opt/examine/mobile-h5``（若启用 ``/m/``）
3. ``include`` 或复制 ``nginx/examine.conf``
4. ``nginx -t && systemctl reload nginx``

## 手机端

- **H5**：与站点同域访问 ``/m/`` 时 API 使用 ``/api``（已写入构建）
- **微信小程序**：用微信开发者工具打开 ``mobile/mp-weixin``（需 ``BUILD_MP_WEIXIN=1`` 构建）；在 App「我的」配置 API 为 ``https://你的域名/api``

"@
Set-Content -Path (Join-Path $OutRoot 'README-DEPLOY.md') -Value $readme -Encoding UTF8

if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path (Join-Path $OutRoot '*') -DestinationPath $ZipPath -Force

Write-Host "Release folder: $OutRoot" -ForegroundColor Green
Write-Host "Zip: $ZipPath" -ForegroundColor Green
