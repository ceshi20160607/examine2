# 当前产品工程

本工程从[原始表定义](../../docs/表意义.md)开始。当前只落地平台账号登录所需的 `un_plat_account` 与 `un_plat_log_login`；其余原名表将在对应功能实施时设计字段并建表。当前进度不能算全产品完成。

## 结构

- `db/001_platform_login.sql`：独立新库及首批两张表。
- `base-codegen.json`：当前已建表的 Entity、Mapper 生成配置。
- `backend/`：Spring Boot 业务入口；生成代码在 `base`，业务代码在 `manage`。
- `frontend/`：Vue 真实登录页面。

## 本地启动

使用已有 MySQL 3306；本工程数据库为 `unexamine_clean`。以 utf8mb4 客户端编码执行 `db/001_platform_login.sql`，不要执行 `docs/old_table1.sql`。

将 `APP_DB_PASSWORD` 设置为本机 MySQL 密码，首次启动时还要设置 `APP_BOOTSTRAP_PASSWORD`；它只在账号表为空时创建 `admin`。可用 `APP_BOOTSTRAP_ACCOUNT` 自定义账号。之后移除该环境变量，已有账号不会被覆盖。

```powershell
mvn -f template_base/mybatis-plus-generator/pom.xml compile exec:java -Dexec.args="projects/unexamine/base-codegen.json"
mvn -f projects/unexamine/backend/pom.xml spring-boot:run
cd projects/unexamine/frontend
npm install
npm run dev
```

后端为 9999；前端开发服务会把 `/api` 转发到后端。
