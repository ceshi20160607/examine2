# examine-generator 执行说明

`examine-generator` 采用“命令即配置”：每次执行命令显式传入模块名、表前缀、base 包、Java 输出目录和 mapper XML 输出目录，不维护 `table-module-map.yml`，也不生成 `mybatis-plus-generation.md` 报告。

## 一键生成全部 base CRUD

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\examine-generator\scripts\generate-base-crud.ps1
```

默认会先执行 `sql/init.sql` 到 `192.168.0.211:3306/examine1`，再按模块生成 `base` 层代码。只想重新生成代码、不重新导入 SQL 时：

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\examine-generator\scripts\generate-base-crud.ps1 -SkipInitSql
```

## 单模块生成命令示例

在 `backend/` 目录执行：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
$env:EXAMINE_GENERATOR_DATASOURCE_URL='jdbc:mysql://192.168.0.211:3306/examine1?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true'
$env:EXAMINE_GENERATOR_DATASOURCE_USERNAME='examine'
$env:EXAMINE_GENERATOR_DATASOURCE_PASSWORD='examine'

D:\java\apache-maven-3.8.5\bin\mvn.cmd -pl examine-generator -DskipTests exec:java `
  "-Dexec.mainClass=com.unique.examine.generator.cli.GeneratorCli" `
  "-Dexec.args=--backend-root . --module-name examine-plat --table-prefix un_plat_ --base-package com.unique.examine.plat.base --source-root examine-plat/src/main/java --mapper-xml-root examine-plat/src/main/resources/mapper/base --execute"
```

如果要新增模块，只需要复制一条命令，修改 `--module-name`、`--table-prefix`、`--base-package`、`--source-root` 和 `--mapper-xml-root`。
