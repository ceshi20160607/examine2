# 部署与发版指南

## 1. 环境依赖

| 组件 | 版本建议 | 说明 |
|------|----------|------|
| JDK | 17+ | 后端运行 |
| Maven | 3.8+ | 构建 |
| MySQL | 8.x | 库名默认 `examine` |
| Redis | 6+ | Token 会话 |
| Node | 18+ | 手机端构建 |
| pnpm | 9+（corepack） | `mobile/uniapp` |

## 2. 后端

### 2.1 配置

编辑 `backend/examine-web/src/main/resources/application.yml`（或 `application-dev.yml`）：

- `spring.datasource.*`：MySQL 连接
- `spring.data.redis.*`：Redis
- `server.port`：默认 **9999**
- `examine.upload.local-root-path`：上传目录（默认 `data/uploads`）

### 2.2 数据库

Flyway 在启动时自动执行 `classpath:db/migration`（V1–V13）。

手动建库示例：

```sql
CREATE DATABASE IF NOT EXISTS examine DEFAULT CHARACTER SET utf8mb4;
```

### 2.3 构建与运行

```powershell
cd backend
mvn -q test
mvn -q package -DskipTests
java -jar examine-web/target/examine-web-*.jar
```

验证：

- `GET http://127.0.0.1:9999/ping`
- Swagger：一般为 `http://127.0.0.1:9999/doc.html`（以实际 Knife4j 配置为准）

### 2.4 首个平台账号

Flyway **未** 预置默认密码账号。请任选其一：

1. **Swagger**：`POST /v1/platform/auth/register`，body：`{"username":"admin","password":"你的密码"}`
2. 或在已实现的管理端注册（若后续补充）

然后使用 `POST /v1/platform/auth/login` 获取 token。

## 3. 手机端

### 3.1 配置后端地址

`mobile/uniapp/src/config/env.ts`：

- `dev`：`http://127.0.0.1:9999`（与后端 port 一致）
- `test` / `prod`：发版前改为真实域名

「我的」页可切换 `dev/test/prod`（写入本地 storage）。

### 3.2 安装与构建

```powershell
corepack pnpm -C mobile/uniapp install
corepack pnpm -C mobile/uniapp run type-check
corepack pnpm -C mobile/uniapp run build:h5
corepack pnpm -C mobile/uniapp run build:mp-weixin
```

产物：

- H5：`mobile/uniapp/dist/build/h5`
- 微信小程序：`mobile/uniapp/dist/build/mp-weixin`（用微信开发者工具导入）

### 3.3 本地开发

```powershell
corepack pnpm -C mobile/uniapp dev:h5
```

## 4. 发版检查清单

- [ ] MySQL、Redis 可达，Flyway 无报错
- [ ] `/ping` 正常
- [ ] 注册/登录成功
- [ ] 创建系统并进入，工作台显示 `systemId`
- [ ] 手机端 `env` 与后端 URL 一致
- [ ] 主路径验收（见 [requirements.md](./requirements.md) §6）
- [ ] H5 / 小程序构建无 error（sass 告警可忽略）

## 5. 运维说明

- **上传目录**：需对运行用户可写；备份时包含 `data/uploads`（或自定义 `local-root-path`）
- **日志**：关注 `X-Request-Id` 与业务 `code != 0` 的 message
- **推送**：用户明确仅需 commit 时不必 push；发版由运维拉取镜像/ Jar 部署

## 6. 仓库文档索引

| 路径 | 内容 |
|------|------|
| `doc/docs/README.md` | 本目录索引 |
| `doc/docs/architecture.md` | 架构 |
| `doc/docs/requirements.md` | 需求范围 |
| `doc/docs/mobile-api-coverage.md` | API 覆盖 |
| `mobile/uniapp/README.md` | 手机端功能列表与回归步骤 |
