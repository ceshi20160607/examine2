param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^unexamine_r15(?:_schema_check_[0-9]{8})?$')]
    [string]$DatabaseName,
    [string]$JdkHome = 'D:\dev\jdk21'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..\..')).Path
$generatorPom = Join-Path $workspaceRoot 'template_base\mybatis-plus-generator\pom.xml'
$configPath = Join-Path $PSScriptRoot 'base-codegen.json'

if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) {
    throw "Java 21 not found at $JdkHome"
}
if (-not $env:APP_DB_PASSWORD) {
    $container = @(docker inspect mysql | ConvertFrom-Json)[0]
    if (-not $container -or -not $container.State.Running) { throw 'Existing MySQL container is not running' }
    $entry = @($container.Config.Env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1)
    if ($entry.Count -ne 1) { throw 'Set APP_DB_PASSWORD for the existing MySQL container' }
    $env:APP_DB_PASSWORD = $entry[0].Split('=', 2)[1]
}
$env:APP_DB_URL = "jdbc:mysql://127.0.0.1:3306/$DatabaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:APP_DB_USERNAME = 'root'
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

python (Join-Path $workspaceRoot 'template_base\tools\tb.py') db-prepare --project-root $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Database preparation failed' }

New-Item -ItemType Directory -Force (Join-Path $projectRoot 'backend\src\main\resources\mapper') | Out-Null
& mvn -q -f $generatorPom compile exec:java "-Dexec.args=$configPath"
if ($LASTEXITCODE -ne 0) { throw 'Base generation failed' }
& mvn -q -f (Join-Path $projectRoot 'backend\pom.xml') -DskipTests clean compile
if ($LASTEXITCODE -ne 0) { throw 'Backend compilation failed' }
