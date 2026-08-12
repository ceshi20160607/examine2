# Examine2 交付文档

本目录随制品冻结，适用于同一包内的后端、前端和数据库迁移。先阅读
`DEPLOYMENT.md` 完成安装，再按角色选择管理员、用户或 OpenAPI 手册。

| 文档 | 读者 | 内容 |
|---|---|---|
| `DEPLOYMENT.md` | 运维 | 预检、安装、启停、体检、日志 |
| `CONFIGURATION.md` | 运维/安全 | 环境变量、SecretRef、多环境与生产边界 |
| `DATABASE.md` | DBA | Flyway、备份、恢复和诊断 |
| `UPGRADE.md` | 运维/DBA | 升级、失败处置和回滚 |
| `ADMIN_GUIDE.md` | 平台/系统管理员 | 管理路径和发布顺序 |
| `USER_GUIDE.md` | 普通成员 | 日常工作、移动端和失败重试 |
| `OPENAPI.md` | 集成方 | 应用、签名、幂等、限流、错误处理 |
| `PRINT_AND_SYSTEM_SETTINGS.md` | 管理员 | 打印模板、文件字段和系统设置 |
| `TROUBLESHOOTING.md` | 全角色 | FAQ、错误码和故障定位 |

包内 `VERSION.json` 是制品版本真源，`manifest.json` 是文件完整性真源。
