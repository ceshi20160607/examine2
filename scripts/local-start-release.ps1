param(
    [int]$BackendPort = 9999,
    [int]$FrontendPort = 18131,
    [string]$DbUrl = 'jdbc:mysql://192.168.0.211:3306/examine2?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true',
    [string]$DbUsername = 'examine',
    [string]$DbPassword = 'examine',
    [string]$RedisHost = '192.168.0.211',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '123456',
    [int]$RedisDatabase = 10,
    [string]$JavaExe = 'D:\dev\jdk21\bin\java.exe',
    [string]$NodeExe = 'D:\dev\nodejs24\node.exe'
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ReleaseDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT'
$BackendDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT\backend'
$FrontendDir = Join-Path $RepoRoot 'release\unexamine-0.0.1-SNAPSHOT\frontend'
$Jar = Join-Path $BackendDir 'examine-web.jar'
$LogDir = Join-Path $BackendDir 'logs'

function Get-ListeningProcessIds {
    param([Parameter(Mandatory = $true)][int]$Port)
    try {
        @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        @()
    }
}

function Assert-PortAvailable {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $listeners = @(Get-ListeningProcessIds -Port $Port)
    if ($listeners.Count -gt 0) {
        throw "$Name port $Port is already listening, pid=$($listeners -join ','). Stop the old release before starting a new one."
    }
}

function Stop-StartedProcess {
    param([System.Diagnostics.Process]$Process)
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $Jar)) {
    throw "Backend jar not found: $Jar"
}
if (-not (Test-Path $JavaExe)) {
    throw "Java executable not found: $JavaExe"
}
if (-not (Test-Path $FrontendDir)) {
    throw "Frontend directory not found: $FrontendDir"
}
if (-not (Test-Path $NodeExe)) {
    throw "Node executable not found: $NodeExe"
}
Assert-PortAvailable -Port $BackendPort -Name 'Backend'
Assert-PortAvailable -Port $FrontendPort -Name 'Frontend'

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Out = Join-Path $LogDir 'powershell-console.out'
$Err = Join-Path $LogDir 'powershell-console.err'
Remove-Item -LiteralPath $Out, $Err -ErrorAction SilentlyContinue

$env:UNEXAMINE_SERVER_PORT = [string]$BackendPort
$env:UNEXAMINE_DB_URL = $DbUrl
$env:UNEXAMINE_DB_USERNAME = $DbUsername
$env:UNEXAMINE_DB_PASSWORD = $DbPassword
$env:UNEXAMINE_REDIS_HOST = $RedisHost
$env:UNEXAMINE_REDIS_PORT = [string]$RedisPort
$env:UNEXAMINE_REDIS_PASSWORD = $RedisPassword
$env:UNEXAMINE_REDIS_DATABASE = [string]$RedisDatabase
$env:UNEXAMINE_ALLOW_ACCOUNT_ID_HEADER = 'false'
$env:UNEXAMINE_CORS_ALLOWED_ORIGINS = "http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort,http://127.0.0.1:5173,http://localhost:5173"

$Args = @(
    '-Xms32m',
    '-Xmx192m',
    '-XX:+UseSerialGC',
    '-jar',
    $Jar,
    "--spring.config.additional-location=optional:file:$BackendDir/"
)

$Process = Start-Process `
    -FilePath $JavaExe `
    -ArgumentList $Args `
    -WorkingDirectory $BackendDir `
    -RedirectStandardOutput $Out `
    -RedirectStandardError $Err `
    -WindowStyle Hidden `
    -PassThru

$Process.Id | Set-Content -LiteralPath (Join-Path $BackendDir 'powershell.pid') -Encoding ASCII

$Deadline = (Get-Date).AddSeconds(60)
$Health = $null
do {
    Start-Sleep -Seconds 2
    try {
        $Health = curl.exe -s "http://127.0.0.1:$BackendPort/api/v1/health"
        if ($Health -match '"status"\s*:\s*"UP"' -and $Health -match '"schema"\s*:\s*"UP"' -and $Health -match '"redis"\s*:\s*"UP"') {
            break
        }
    } catch {
        $Health = $null
    }
} while ((Get-Date) -lt $Deadline)

if (-not ($Health -match '"status"\s*:\s*"UP"' -and $Health -match '"schema"\s*:\s*"UP"' -and $Health -match '"redis"\s*:\s*"UP"')) {
    Write-Output '--- stderr ---'
    Get-Content -LiteralPath $Err -ErrorAction SilentlyContinue | Select-Object -Last 80
    Write-Output '--- stdout ---'
    Get-Content -LiteralPath $Out -ErrorAction SilentlyContinue | Select-Object -Last 80
    Stop-StartedProcess -Process $Process
    Write-Output "Stopped unhealthy backend process: $($Process.Id)"
    throw "Backend health check failed: $Health"
}

$FrontendServer = Join-Path $LogDir 'local-frontend-server.cjs'
$FrontendOut = Join-Path $LogDir 'frontend-console.out'
$FrontendErr = Join-Path $LogDir 'frontend-console.err'
Remove-Item -LiteralPath $FrontendOut, $FrontendErr -ErrorAction SilentlyContinue
@'
const fs = require('fs');
const http = require('http');
const path = require('path');

const frontendDir = process.env.UNEXAMINE_FRONTEND_DIR;
const backendPort = Number(process.env.UNEXAMINE_BACKEND_PORT || '9999');
const frontendPort = Number(process.env.UNEXAMINE_FRONTEND_PORT || '18131');
const mimeTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.json', 'application/json; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.webp', 'image/webp'],
]);

function sendStatic(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
  let pathname = decodeURIComponent(url.pathname);
  if (pathname === '/') {
    pathname = '/index.html';
  }
  let target = path.resolve(frontendDir, `.${pathname}`);
  if (!target.startsWith(path.resolve(frontendDir))) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  if (!fs.existsSync(target) || fs.statSync(target).isDirectory()) {
    target = path.join(frontendDir, 'index.html');
  }
  res.writeHead(200, {
    'Content-Type': mimeTypes.get(path.extname(target).toLowerCase()) || 'application/octet-stream',
    'Cache-Control': path.basename(target) === 'config.js' ? 'no-store' : 'no-cache',
  });
  fs.createReadStream(target).pipe(res);
}

function proxyApi(req, res) {
  const upstream = http.request({
    hostname: '127.0.0.1',
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `127.0.0.1:${backendPort}` },
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 'LOCAL_PROXY_ERROR', message: error.message }));
  });
  req.pipe(upstream);
}

http.createServer((req, res) => {
  const requestUrl = req.url || '';
  if (requestUrl.startsWith('/api/') || requestUrl.startsWith('/openapi/')) {
    proxyApi(req, res);
    return;
  }
  sendStatic(req, res);
}).listen(frontendPort, '127.0.0.1', () => {
  console.log(`Frontend listening on http://127.0.0.1:${frontendPort}`);
});
'@ | Set-Content -LiteralPath $FrontendServer -Encoding UTF8

$env:UNEXAMINE_FRONTEND_DIR = $FrontendDir
$env:UNEXAMINE_BACKEND_PORT = [string]$BackendPort
$env:UNEXAMINE_FRONTEND_PORT = [string]$FrontendPort

$FrontendProcess = Start-Process `
    -FilePath $NodeExe `
    -ArgumentList @($FrontendServer) `
    -WorkingDirectory $ReleaseDir `
    -RedirectStandardOutput $FrontendOut `
    -RedirectStandardError $FrontendErr `
    -WindowStyle Hidden `
    -PassThru

$FrontendProcess.Id | Set-Content -LiteralPath (Join-Path $BackendDir 'frontend-powershell.pid') -Encoding ASCII

$FrontendDeadline = (Get-Date).AddSeconds(15)
$FrontendReady = $false
do {
    Start-Sleep -Seconds 1
    try {
        $FrontendHtml = curl.exe -s "http://127.0.0.1:$FrontendPort/"
        if ($FrontendHtml -match '<div id="app"') {
            $FrontendReady = $true
            break
        }
    } catch {
        $FrontendReady = $false
    }
} while ((Get-Date) -lt $FrontendDeadline)

if (-not $FrontendReady) {
    Write-Output '--- frontend stderr ---'
    Get-Content -LiteralPath $FrontendErr -ErrorAction SilentlyContinue | Select-Object -Last 80
    Stop-StartedProcess -Process $FrontendProcess
    Stop-StartedProcess -Process $Process
    throw "Frontend local server health check failed: http://127.0.0.1:$FrontendPort/"
}

$Result = [ordered]@{
    backendPid = $Process.Id
    frontendPid = $FrontendProcess.Id
    frontendUrl = "http://127.0.0.1:$FrontendPort/"
    health = $Health
    backendDir = $BackendDir
    resultFile = (Join-Path $BackendDir 'start-result.json')
} | ConvertTo-Json -Depth 8
$ResultFile = Join-Path $BackendDir 'start-result.json'
$Result | Set-Content -LiteralPath $ResultFile -Encoding UTF8
cmd.exe /c "echo Release started. && echo Frontend: http://127.0.0.1:$FrontendPort/ && echo Backend: http://127.0.0.1:$BackendPort && echo Backend PID: $($Process.Id) && echo Frontend PID: $($FrontendProcess.Id) && echo Result: $ResultFile && type ""$ResultFile"""
