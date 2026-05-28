# Windows 启动：配置见 config\application.yml
$ErrorActionPreference = 'Stop'
$Dir = $PSScriptRoot
Set-Location $Dir

$ConfigDir = Join-Path $Dir 'config'
$ConfigFile = Join-Path $ConfigDir 'application.yml'
if (-not (Test-Path $ConfigFile)) {
    $ex = Join-Path $ConfigDir 'application.yml.example'
    if (Test-Path $ex) {
        Write-Error '请先: Copy-Item config\application.yml.example config\application.yml 并修改配置'
    } else {
        Write-Error "缺少配置文件: $ConfigFile"
    }
}

$envFile = Join-Path $Dir 'application.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $n, $v = $_ -split '=', 2
        Set-Item -Path "Env:$($n.Trim())" -Value $v.Trim()
    }
}

$jar = Get-ChildItem -Path $Dir -Filter 'examine-web-*.jar' | Select-Object -First 1
if (-not $jar) { throw "missing examine-web-*.jar in $Dir" }

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$xmx = if ($env:EXAMINE_JAVA_XMX) { $env:EXAMINE_JAVA_XMX } else { '512m' }
$configUri = "optional:file:${ConfigDir}/"

& $java "-Xmx$xmx", '-XX:+UseSerialGC', '-jar', $jar.FullName `
    "--spring.config.additional-location=$configUri"
