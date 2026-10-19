# Unexamine 当前工程

本轮按 [94 张原名表的用途](../../docs/表意义.md) 实现基础业务。需求边界见 [用户需求](../../docs/user_requirement.md)，后端接口批次和验收点见 [后端计划](backend-plan.md)。当前在后端阶段；后端真实接口验收后再开发前端。

## 目录

- `db/00_database.sql` 与 `db/01_*.sql` 至 `db/04_*.sql`：新库及 94 张基础表；每张表和每个字段有中文备注。
- `base-codegen.json`：把 94 张已建表逐张指定到业务模块，生成各模块自己的 Entity 和 Mapper。
- `backend/examine-plat`、`examine-sys`、`examine-common`、`examine-module`、`examine-flow`、`examine-approval`、`examine-work`、`examine-dashboard`、`examine-print`、`examine-app`：各业务域的 `base` 与 `manage` 在同一包层级；`backend/examine-web`：唯一启动入口和 Web 配置。逐表归属见 [业务模块](business-modules.md)。
- 后端接口合同按业务域独立维护：[平台](plat-api.md)、[系统与租户](sys-api.md)、[公共资源](common-api.md)、[可配置模块](module-api.md)、[通用流程](flow-api.md)、[审批](approval-api.md)、[工作协作](work-api.md)、[数据展示与 KPI](dashboard-api.md)、[打印](print-api.md)、[应用接入](app-api.md)。
- `template_base/mybatis-plus-generator`：通用生成器，不包含产品需求或业务判断。
- `ai/planning/rebuild-plan.md` 与 `rebuild-state.json`：简短接续断点；根目录 `workspace.json` 与 `template_base/tools/tb.py` 提供状态查看和验证记录。

## 本地运行

复用本机 MySQL 3306 和 Redis 6379，产品只使用隔离库 `unexamine_r15`。首次建库按文件编号顺序执行 `db/` 中的五个 SQL 文件。已有本轮 QA 数据从 `unexamine` 迁入的步骤见 [迁移记录](db/MIGRATION-r15.md)。`docs/old_table1.sql` 有旧数据和删表语句，只用作字段参数及早期审批逻辑参考，不能执行。

将 `JAVA_HOME` 设为 JDK 21，`APP_DB_PASSWORD` 设为本机 MySQL 密码。首次启动空账号库时需设置至少 12 位的 `APP_BOOTSTRAP_PASSWORD`，会创建平台 `admin`；已有账号不会被覆盖。可用 `APP_BOOTSTRAP_ACCOUNT` 自定义账号，初始化后可移除引导密码。文件默认保存在后端工作目录的 `runtime/uploads`，可用 `APP_FILE_ROOT` 指定持久目录；配置 AI 凭据时需设置 32 字节密钥的 Base64 值 `APP_AI_ENCRYPTION_KEY`。

```powershell
mvn -f template_base/mybatis-plus-generator/pom.xml compile exec:java '-Dexec.args=projects/unexamine/base-codegen.json'
mvn -f projects/unexamine/backend/pom.xml clean package
Set-Location projects/unexamine/backend
java -jar examine-web/target/examine-web-0.1.0.jar
```

后端端口为 9999。启动后可打开 [Swagger UI](http://localhost:9999/swagger-ui.html) 查看按业务模块分组的接口，或访问 [OpenAPI JSON](http://localhost:9999/v3/api-docs) 供前端生成客户端。每个 Controller 使用 `@Tag` 标明业务分组，每个路由使用 `@Operation(summary = ...)` 标明用途；接口文档与代码保持同一提交更新。建表及代码生成只证明数据模型可用，不能视为业务接口已验收。
