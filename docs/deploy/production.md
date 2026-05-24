# examine2 生产部署指南

本文说明如何将 **examine-web（后端）**、**mobile/uniapp**、**web/vue3（管理台）** 部署到生产环境。数据库变更默认由 **Flyway** 在启动时执行（`application-prod.yml` 已开启）；亦可用 `spring.flyway.enabled=false` 改回纯手工 SQL，见 `docs/deploy/flyway-existing-db.md`。

## 1. 环境要求

| 组件 | 版本建议 |
|------|----------|
| JDK | 17+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| Node.js（构建前端） | 18+ |

## 2. 数据库初始化（手工）

按顺序执行（路径相对仓库根目录）：

1. 平台与基础：`docs/sql/` 下平台 DDL（若独立文件）
2. 模块：`docs/sql/05_module_ddl.sql`
3. 种子（可选）：`docs/sql/06_module_seed.sql`
4. 流程：`docs/sql/07_flow_ddl.sql`、`docs/sql/08_flow_seed.sql`（可选）
5. 增量（若库已存在）：`docs/sql/manual/V21__module_role_data_scope.sql`、`V23__openapi_sign_eav_index.sql` 等 manual 目录脚本

确认库字符集 `utf8mb4`，时区与应用一致（建议 `Asia/Shanghai`）。

## 3. 后端 examine-web

### 3.1 配置

使用 profile **`prod`**，见 `backend/examine-web/src/main/resources/application-prod.yml`。

通过环境变量覆盖敏感项（示例）：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://mysql-host:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true"
export SPRING_DATASOURCE_USERNAME="examine"
export SPRING_DATASOURCE_PASSWORD="***"
export SPRING_DATA_REDIS_HOST="redis-host"
export SPRING_DATA_REDIS_PASSWORD="***"
export EXAMINE_OPENAPI_SIGNING_MASTER_KEY="***"   # 开放 API 签名密钥加密主密钥（生产必改）
```

### 3.2 构建与启动

```bash
cd backend
mvn -pl examine-web -am package -DskipTests
java -jar examine-web/target/examine-web-*.jar --spring.profiles.active=prod
```

默认端口 **9999**（可在 `application-prod.yml` 修改）。

### 3.3 健康检查

- `GET /actuator/health`
- `GET /actuator/prometheus`（若开启监控）

API 文档：dev 可开 Knife4j；prod 默认关闭，仅内网按需开启。

## 4. Web 管理台（vue3）

### 4.1 构建

```bash
cd web/vue3
cp .env.production.example .env.production
# 编辑 VITE_API_BASE 为对外 API 根地址，如 https://api.example.com
npm ci
npm run build
```

产物在 `web/vue3/dist/`，由 Nginx 托管静态文件。

### 4.2 Nginx 示例

```nginx
server {
  listen 443 ssl;
  server_name admin.example.com;
  root /var/www/examine-admin;
  index index.html;
  location / {
    try_files $uri $uri/ /index.html;
  }
  location /v1/ {
    proxy_pass http://127.0.0.1:9999;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
}
```

若 API 与静态站同域，可将 `VITE_API_BASE` 留空，由 Nginx 反代 `/v1`。

## 5. 移动端 uniapp

- 生产包在 HBuilderX / CI 打 **app-plus** 或各小程序渠道包。
- 在应用内或构建前配置 `apiBaseUrl`（见 `mobile/uniapp/src/config/env.ts`），指向 `https://api.example.com`。
- 勿将测试地址 `test.example.com` 打入正式包。

## 6. 上线冒烟清单

**自动化脚本**（API 级，覆盖下列主流程）：

```powershell
# Windows
cd tests\api
$env:EXAMINE_HOST = "http://127.0.0.1:9999"
$env:SMOKE_USER = "your_admin"    # 已有库时必填
$env:SMOKE_PASS = "your_password"
.\e2e-smoke.ps1
```

```bash
# Git Bash / Linux
cd tests/api && EXAMINE_HOST=http://127.0.0.1:9999 SMOKE_USER=admin SMOKE_PASS=*** bash e2e-smoke.sh
```

详见 `tests/README.md`、`tests/api/README.md`。

手工核对：

- [ ] 注册/登录，token 写入 Redis
- [ ] 创建系统 → 进入系统 →（多租户时）选择租户
- [ ] 创建 app / model / field，保存一条 record
- [ ] RBAC：角色 data_scope、菜单权限、运行时菜单可见
- [ ] 页面：配置 list 页 `config_json.modelId`，菜单绑定 pageId，运行时菜单进入列表
- [ ] 流程：模板 + 版本 `graph-designer` 保存/发布（脚本已覆盖 POST/GET）；binding + 新建 record 触发实例（若已配）
- [ ] 上传附件字段
- [ ] OpenAPI（若启用）：`appId/secret` 调 `/v1/open/flow/**` 与 `/v1/open/records/**`（含 query/detail）
- [ ] Web 管理台：创建系统、导出任务、平台收件箱抄送已读（可与移动端二选一冒烟）

## 7. 常见问题

- **403 请先进入自建系统**：先调 `POST /v1/platform/context/enter-system`。
- **记录看不到别人的数据**：检查角色 `data_scope` 与 `un_module_role.data_scope` 列是否存在。
- **菜单空白**：成员需分配角色，且角色需配置菜单权限（`menu-perms/set`）。
