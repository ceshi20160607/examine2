param(
    [string]$DatasourceUrl = $(if ($env:EXAMINE_GENERATOR_DATASOURCE_URL) { $env:EXAMINE_GENERATOR_DATASOURCE_URL } else { 'jdbc:mysql://192.168.0.211:3306/examine1?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true' }),
    [string]$DatasourceUsername = $(if ($env:EXAMINE_GENERATOR_DATASOURCE_USERNAME) { $env:EXAMINE_GENERATOR_DATASOURCE_USERNAME } else { 'examine' }),
    [string]$DatasourcePassword = $(if ($env:EXAMINE_GENERATOR_DATASOURCE_PASSWORD) { $env:EXAMINE_GENERATOR_DATASOURCE_PASSWORD } else { 'examine' }),
    [string]$JavaHome = 'D:\java\jdk\jdk21',
    [string]$MavenCmd = 'D:\java\apache-maven-3.8.5\bin\mvn.cmd',
    [switch]$SkipInitSql
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..\..')).Path
$initSql = (Resolve-Path -LiteralPath (Join-Path $backendRoot '..\sql\init.sql')).Path

$env:JAVA_HOME = $JavaHome
$env:Path = "$env:JAVA_HOME\bin;$(Split-Path -Parent $MavenCmd);$env:Path"
$env:EXAMINE_GENERATOR_DATASOURCE_URL = $DatasourceUrl
$env:EXAMINE_GENERATOR_DATASOURCE_USERNAME = $DatasourceUsername
$env:EXAMINE_GENERATOR_DATASOURCE_PASSWORD = $DatasourcePassword

$modules = @(
    @{
        ModuleName = 'examine-plat'
        TablePrefixes = @('un_plat_')
        BasePackage = 'com.unique.examine.plat.base'
        SourceRoot = 'examine-plat/src/main/java'
        MapperXmlRoot = 'examine-plat/src/main/resources/mapper/base'
    },
    @{
        ModuleName = 'examine-module'
        TablePrefixes = @('un_module_')
        BasePackage = 'com.unique.examine.module.base'
        SourceRoot = 'examine-module/src/main/java'
        MapperXmlRoot = 'examine-module/src/main/resources/mapper/base'
    },
    @{
        ModuleName = 'examine-flow'
        TablePrefixes = @('un_flow_')
        BasePackage = 'com.unique.examine.flow.base'
        SourceRoot = 'examine-flow/src/main/java'
        MapperXmlRoot = 'examine-flow/src/main/resources/mapper/base'
    },
    @{
        ModuleName = 'examine-upload'
        TablePrefixes = @('un_upload_')
        BasePackage = 'com.unique.examine.upload.base'
        SourceRoot = 'examine-upload/src/main/java'
        MapperXmlRoot = 'examine-upload/src/main/resources/mapper/base'
    },
    @{
        ModuleName = 'examine-app'
        TablePrefixes = @('un_openapi_')
        BasePackage = 'com.unique.examine.app.base'
        SourceRoot = 'examine-app/src/main/java'
        MapperXmlRoot = 'examine-app/src/main/resources/mapper/base'
    },
    @{
        ModuleName = 'examine-core'
        TablePrefixes = @('un_sys_', 'un_audit_')
        BasePackage = 'com.unique.examine.core.base'
        SourceRoot = 'examine-core/src/main/java'
        MapperXmlRoot = 'examine-core/src/main/resources/mapper/base'
    }
)

function Invoke-Generator {
    param(
        [hashtable]$Module,
        [switch]$WithInitSql
    )

    $generatorArgs = @(
        '--backend-root', '.',
        '--module-name', $Module.ModuleName,
        '--base-package', $Module.BasePackage,
        '--source-root', $Module.SourceRoot,
        '--mapper-xml-root', $Module.MapperXmlRoot,
        '--execute'
    )

    foreach ($prefix in $Module.TablePrefixes) {
        $generatorArgs += @('--table-prefix', $prefix)
    }

    if ($WithInitSql) {
        $generatorArgs += @('--init-sql', $initSql)
    }

    $execArgs = $generatorArgs -join ' '
    Write-Host "Generating $($Module.ModuleName): $execArgs"
    & $MavenCmd -pl examine-generator -DskipTests exec:java `
        '-Dexec.mainClass=com.unique.examine.generator.cli.GeneratorCli' `
        "-Dexec.args=$execArgs"
    if ($LASTEXITCODE -ne 0) {
        throw "Generator failed for $($Module.ModuleName), exit code: $LASTEXITCODE"
    }
}

Push-Location $backendRoot
try {
    Write-Host 'Compiling examine-generator before generation.'
    & $MavenCmd -pl examine-generator -am -DskipTests compile
    if ($LASTEXITCODE -ne 0) {
        throw "Generator compile failed, exit code: $LASTEXITCODE"
    }

    for ($index = 0; $index -lt $modules.Count; $index++) {
        Invoke-Generator -Module $modules[$index] -WithInitSql:($index -eq 0 -and -not $SkipInitSql)
    }
}
finally {
    Pop-Location
}
