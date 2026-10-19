# Unexamine 当前工程

本轮按 [94 张原名表的用途](../../docs/表意义.md) 实现基础业务。需求边界见 [用户需求](../../docs/user_requirement.md)，后端接口批次和验收点见 [后端计划](backend-plan.md)。当前在后端阶段；后端真实接口验收后再开发前端。

## 目录

- `db/00_database.sql` 与 `db/01_*.sql` 至 `db/04_*.sql`：新库及 94 张基础表；每张表和每个字段有中文备注。
- `base-codegen.json`：按实际已建表生成 Entity 和 Mapper 的配置。
- `backend/src/main/java/.../base`：生成代码；`manage`：具体业务接口。
- `identity-api.md`、`module-api.md`、`flow-api.md`、`other-api.md`：按业务旅程整理的后端接口合同。
- `template_base/mybatis-plus-generator`：通用生成器，不包含产品需求或业务判断。

## 本地运行

复用本机 MySQL 3306 和 Redis 6379，产品只使用 `unexamine` 库。首次建库按文件编号顺序执行 `db/` 中的五个 SQL 文件。`docs/old_table1.sql` 有旧数据和删表语句，只用作字段参数及早期审批逻辑参考，不能执行。

将 `JAVA_HOME` 设为 JDK 21，`APP_DB_PASSWORD` 设为本机 MySQL 密码。首次启动空账号库时需设置至少 12 位的 `APP_BOOTSTRAP_PASSWORD`，会创建平台 `admin`；已有账号不会被覆盖。可用 `APP_BOOTSTRAP_ACCOUNT` 自定义账号，初始化后可移除引导密码。文件默认保存在后端工作目录的 `data/uploads`，可用 `APP_FILE_ROOT` 指定持久目录；配置 AI 凭据时需设置 32 字节密钥的 Base64 值 `APP_AI_ENCRYPTION_KEY`。

```powershell
mvn -f template_base/mybatis-plus-generator/pom.xml compile exec:java '-Dexec.args=projects/unexamine/base-codegen.json'
Set-Location projects/unexamine/backend
mvn spring-boot:run
```

后端端口为 9999。建表及代码生成只证明数据模型可用，不能视为业务接口已验收。
