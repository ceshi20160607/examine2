# 故障排查与 FAQ

## 定位顺序

1. 执行 `scripts/verify-package.ps1`，排除制品损坏。
2. 执行 `scripts/health.ps1`，检查前端和后端。
3. 执行 `scripts/logs.ps1 -Tail 500`，记录最早错误、requestId/traceId 和版本。
4. 查看 `docker compose ... ps` 的 MySQL/Redis health、重启次数和磁盘。
5. 用最小角色和相同上下文重现，核对权限快照、系统/租户、对象状态及发布版本。

## 常见问题

**启动提示 CHANGE_ME**：复制 `.env.example` 后替换所有必填占位符，密码至少 16 位。

**后端一直不健康**：检查 MySQL/Redis health、端口冲突、磁盘、Flyway 错误和生产安全配置。不要删除数据卷重试。

**前端能打开但 API 失败**：确认 Nginx `/api`、`/management` 代理和后端容器；用后端 health 区分代理与应用问题。

**登录成功但看不到模块**：检查当前系统/租户、成员状态、角色发布版本、模块 active/published 和数据范围。

**OpenAPI 401/403/429**：分别检查签名时间/nonce/凭证、scope/IP/对象授权、限流；凭 requestId/traceId 查询调用日志。

**上传或下载失败**：检查大小/类型、文件权限、`<EXAMINE_DATA_ROOT>/files` 磁盘和 S3 SecretRef/TLS，禁止直接复制对象绕过数据库归属。

**升级失败**：保留旧目录和 pre-upgrade 备份，按 `UPGRADE.md` 回滚；不要改已应用迁移或执行 Flyway repair。

**误删/配置错误**：优先使用业务归档/恢复或配置版本恢复；部署级恢复会覆盖整库和文件，只在影响面明确且已确认时执行。

## 提交故障信息

提供版本、发生时间/时区、角色与上下文、操作步骤、requestId/traceId、HTTP 状态、脱敏日志和影响范围。不得提供密码、cookie、token、OpenAPI secret、SMTP/S3/AI 密钥或完整敏感业务数据。
