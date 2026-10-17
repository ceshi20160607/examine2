param(
    [string]$JdkHome = $(if ($env:TEMPLATE_BASE_JAVA_HOME) { $env:TEMPLATE_BASE_JAVA_HOME } else { 'D:\dev\jdk21' }),
    [string]$MySqlContainerName = 'mysql'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..\..')).Path
$configPath = Join-Path $PSScriptRoot 'base-codegen.json'
$generatorPom = Join-Path $workspaceRoot 'template_base\mybatis-plus-generator\pom.xml'
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'backend\src\main\resources\mapper') | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) {
    throw "Java 21 not found at $JdkHome"
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$mysql = @(docker inspect $MySqlContainerName | ConvertFrom-Json)[0]
if (-not $mysql -or -not $mysql.State.Running) { throw 'Existing MySQL container is not running' }
$env:APP_DB_URL = 'jdbc:mysql://127.0.0.1:3306/unexamine_r15?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:APP_DB_USERNAME = 'root'
if (-not $env:APP_DB_PASSWORD) {
    $passwordEntry = @($mysql.Config.Env | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1)
    if ($passwordEntry.Count -ne 1) { throw 'Set APP_DB_PASSWORD for the existing MySQL container' }
    $env:APP_DB_PASSWORD = $passwordEntry[0].Split('=', 2)[1]
}

python (Join-Path $workspaceRoot 'template_base\tools\tb.py') workspace-validate --workspace (Join-Path $workspaceRoot 'workspace.json')
if ($LASTEXITCODE -ne 0) { throw 'Workspace validation failed' }
python (Join-Path $workspaceRoot 'template_base\tools\tb.py') db-prepare --project-root $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Database preparation failed' }

& mvn -q -f $generatorPom compile exec:java "-Dexec.args=$configPath"
if ($LASTEXITCODE -ne 0) { throw 'Base generation failed' }
& mvn -q -f (Join-Path $projectRoot 'backend\pom.xml') -DskipTests compile
if ($LASTEXITCODE -ne 0) { throw 'Backend compilation failed' }
