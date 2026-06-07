# oldgenerator reference

## Scan Scope

- `.codex/oldgenerator/pom.xml`
- `.codex/oldgenerator/src/main/java/com/kakarote/generator/Generator.java`
- `.codex/oldgenerator/src/main/java/com/kakarote/generator/config/GeneratorConfig.java`
- `.codex/oldgenerator/src/main/java/com/kakarote/generator/config/Generator21team.java`
- `.codex/oldgenerator/src/main/java/com/kakarote/generator/config/GeneratorOwner.java`
- `.codex/oldgenerator/src/main/java/com/kakarote/generator/engine/DefaultTemplateEngine.java`
- `.codex/oldgenerator/src/main/resources/template_owner/*.ftl`

## Useful Parts

- `GeneratorOwner` 是当前项目最接近的旧配置：使用 `un_` 表前缀、`com.unique.examine` 包名和 MyBatis-Plus `FastAutoGenerator`。
- `DefaultTemplateEngine` 可参考：基于 Freemarker，修正 `controllerMappingHyphen`，并向模板注入 `serviceName`、`mapperName`、`entityName` 等变量。
- `template_owner/entity.java.ftl`、`mapper.java.ftl`、`mapper.xml.ftl`、`service.java.ftl`、`serviceImpl.java.ftl` 可作为 base 层模板参考。
- `GeneratorConfig` 可参考基础结构；`Generator21team` 只作为 Elasticsearch/历史特殊模板参考，默认不进入 MVP 生成器。

## Must Change Before Reuse

- 禁止保留硬编码数据库：`192.168.0.6`、`aaaagenger`、`root/password`、`D:/generator`。
- 禁止交互式 `Scanner` 作为唯一入口；新生成器必须支持命令行参数或配置文件，能在开发任务里复现执行。
- 禁止生成对外 Controller；当前项目对外接口必须放在各模块 `manage.controller` 或 `examine-web` 明确聚合入口。
- 禁止把实体生成到 `entity.po` 这类旧包；新项目 base 层按各模块 `base/entity`、`base/mapper`、`base/service`、`base/service/impl`。
- 禁止生成 `un_app_*` 新表代码；旧 `un_app_*` 只作为 OpenAPI 历史迁移参考，OpenAPI 新表统一 `un_openapi_ -> examine-app/base`。
- 旧模板中的 `Result`、`BasePage`、`PageEntity`、通用 Controller CRUD 不进入 base 生成范围。

## New Generator Direction

- 模块名固定为 `examine-generator`，不是 `examine-genger`。
- 生成器读取 `docs/service_info.md` 或环境变量获得数据库连接，不写死本机地址和密码。
- 表前缀映射：
  - `un_plat_ -> backend/examine-plat/src/main/java/com/unique/examine/plat/base/`
  - `un_module_ -> backend/examine-module/src/main/java/com/unique/examine/module/base/`
  - `un_flow_ -> backend/examine-flow/src/main/java/com/unique/examine/flow/base/`
  - `un_upload_ -> backend/examine-upload/src/main/java/com/unique/examine/upload/base/`
  - `un_openapi_ -> backend/examine-app/src/main/java/com/unique/examine/app/base/`
  - `un_sys_` / `un_audit_ -> backend/examine-core/src/main/java/com/unique/examine/core/base/`
- 只生成贴表基础能力：entity、mapper、mapper.xml、service、serviceImpl。
- 生成器采用“命令即配置”，每条命令显式传入模块名、表前缀、base 包、Java 输出目录和 mapper XML 输出目录；不再维护额外表映射文件，也不默认输出报告文件。
- 可复跑命令入口维护在 `backend/examine-generator/scripts/generate-base-crud.ps1`，执行说明维护在 `backend/examine-generator/README.md`。
