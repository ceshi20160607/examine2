param(
    [string]$JdkHome = $(if ($env:TEMPLATE_BASE_JAVA_HOME) { $env:TEMPLATE_BASE_JAVA_HOME } else { 'D:\dev\jdk21' }),
    [switch]$SkipCompile
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..\..')).Path
$backendRoot = Join-Path $projectRoot 'backend'
$composePath = Join-Path $projectRoot 'deploy\compose.yaml'
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

docker compose -f $composePath up -d mysql | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Could not start the project MySQL service.' }
$mysqlContainer = docker compose -f $composePath ps -q mysql
if (!$mysqlContainer) { throw 'Could not resolve the project MySQL container.' }
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $health = docker inspect --format '{{.State.Health.Status}}' $mysqlContainer 2>$null
    if ($health -eq 'healthy') { break }
    if ($attempt -eq 30) { throw 'MySQL did not become healthy within 60 seconds.' }
    Start-Sleep -Seconds 2
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
