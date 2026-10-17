param(
    [string]$JdkHome = $(if ($env:TEMPLATE_BASE_JAVA_HOME) { $env:TEMPLATE_BASE_JAVA_HOME } else { 'D:\dev\jdk21' }),
    [switch]$SkipCompile,
    [string]$MySqlContainerName = 'mysql'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..\..')).Path
$backendRoot = Join-Path $projectRoot 'backend'
$configPath = Join-Path $projectRoot 'tools\base-codegen.json'
$generatorPom = Join-Path $workspaceRoot 'template_base\mybatis-plus-generator\pom.xml'

if (!(Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) {
    throw "Java 21 not found at $JdkHome. Pass -JdkHome or set TEMPLATE_BASE_JAVA_HOME."
}
$env:JAVA_HOME = (Resolve-Path $JdkHome).Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$javaVersion = (& java --version | Select-Object -First 1)
if ($javaVersion -notmatch '^java (version ")?21([\."])') {
    throw "Base generation requires Java 21; active runtime is: $javaVersion"
}

python (Join-Path $workspaceRoot 'template_base\tools\tb.py') workspace-validate --workspace (Join-Path $workspaceRoot 'workspace.json')
if ($LASTEXITCODE -ne 0) { throw 'Workspace boundary validation failed.' }

python (Join-Path $workspaceRoot 'template_base\tools\tb.py') db-prepare --project-root $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Database scripts are not ready.' }

$container = @(docker inspect $MySqlContainerName | ConvertFrom-Json)[0]
if (-not $container -or -not $container.State.Running) { throw 'Existing local MySQL container is not running.' }
if (-not $env:APP_DB_URL) {
    $env:APP_DB_URL = 'jdbc:mysql://127.0.0.1:3306/unexamine_codegen_current?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
}
if (-not $env:APP_DB_USERNAME) { $env:APP_DB_USERNAME = 'root' }
if (-not $env:APP_DB_PASSWORD) {
    $mysqlPassword = @($container.Config.Env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1)
    if ($mysqlPassword.Count -ne 1) { throw 'Set APP_DB_PASSWORD for the existing MySQL container.' }
    $env:APP_DB_PASSWORD = $mysqlPassword[0].Split('=', 2)[1]
}
if ($env:APP_DB_USERNAME -eq 'root' -and $env:APP_DB_URL -match '^jdbc:mysql://127\.0\.0\.1:3306/([A-Za-z0-9_]+)\?') {
    # 只在现有容器里补建代码生成用隔离库，不启动或重建 MySQL 服务。
    $databaseName = $Matches[1]
    & docker exec $MySqlContainerName sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --execute="CREATE DATABASE IF NOT EXISTS $1 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"' sh $databaseName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the existing MySQL code-generation database.' }
}

function Get-TreeHash([string]$Kind) {
    $files = if ($Kind -eq 'manage') {
        Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src') -Recurse -File -Filter '*.java' |
            Where-Object { $_.FullName -match '[\\/]manage[\\/]' }
    } else {
        Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src\main') -Recurse -File |
            Where-Object {
                $_.FullName -match '[\\/]base[\\/]' -and
                ($_.Extension -eq '.java' -or $_.Extension -eq '.xml')
            }
    }
    $rows = $files | Sort-Object FullName | ForEach-Object {
        $_.FullName.Substring($backendRoot.Length + 1).Replace('\', '/') + '=' + (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($rows -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

$manageBefore = Get-TreeHash 'manage'
& mvn -q -f $generatorPom compile exec:java "-Dexec.args=$configPath"
if ($LASTEXITCODE -ne 0) { throw 'MyBatis-Plus base generation failed.' }
$manageAfter = Get-TreeHash 'manage'
if ($manageBefore -ne $manageAfter) { throw 'Generator changed manage code; this is forbidden.' }

$firstBaseHash = Get-TreeHash 'base'
& mvn -q -f $generatorPom compile exec:java "-Dexec.args=$configPath"
if ($LASTEXITCODE -ne 0) { throw 'MyBatis-Plus repeat generation failed.' }
$secondBaseHash = Get-TreeHash 'base'
if ($firstBaseHash -ne $secondBaseHash) { throw 'Base generation is not repeatable.' }

if (!$SkipCompile) {
    & mvn -q -f (Join-Path $backendRoot 'pom.xml') -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw 'Generated backend does not compile.' }
}

$manifest = Get-Content -LiteralPath (Join-Path $backendRoot 'base-codegen-manifest.json') -Raw -Encoding utf8 | ConvertFrom-Json
[pscustomobject]@{
    ok = $true
    generator = $manifest.generator
    modules = $manifest.moduleCount
    tables = $manifest.tableCount
    generatedFiles = $manifest.generatedFiles.Count
    schemaSha256 = $manifest.schemaSha256
    baseTreeSha256 = $secondBaseHash
    manageUnchanged = $true
    backendCompiled = !$SkipCompile
} | ConvertTo-Json
