# 部署与运行

## 前置条件

- Windows Server 2022/Windows 11 或支持 Docker Compose v2 的 Linux 主机；脚本要求 PowerShell 7。
- Docker Engine 27+、Compose v2、至少 4 CPU/8 GiB 内存/20 GiB 可用磁盘。
- 仅将 Web 端口暴露给反向代理；数据库、Redis 和后端默认不对公网开放。
- 生产环境由受信任的 TLS 反向代理终止 HTTPS，并保存本包、`.env` 和备份目录的访问控制。

## 首次安装

1. 解压到独立、只读版本目录，例如 `D:\apps\examine2\8.92.0`。
2. 执行 `pwsh -File scripts/verify-package.ps1` 验证清单和 SHA-256。
3. 复制 `.env.example` 为 `.env`，替换全部 `CHANGE_ME`，密码至少 16 位；`EXAMINE_DATA_ROOT` 必须是版本目录之外的绝对持久路径。
4. 执行 `pwsh -File scripts/start.ps1`。脚本会启动 MySQL、Redis、后端和前端，执行 Flyway，并等待健康检查。
5. 浏览器打开 `.env` 中 `EXAMINE_WEB_PORT` 对应地址，使用 bootstrap root 登录后立即修改密码。

## 固定操作

```powershell
pwsh -File scripts/start.ps1
pwsh -File scripts/health.ps1
pwsh -File scripts/logs.ps1 -Tail 300
pwsh -File scripts/restart.ps1
pwsh -File scripts/stop.ps1
```

`stop.ps1` 仅停止容器并保留数据库卷、Redis AOF、文件和日志。固定应用日志为
`<EXAMINE_DATA_ROOT>/logs/examine2.log`，文件对象位于 `<EXAMINE_DATA_ROOT>/files`，备份位于 `<EXAMINE_DATA_ROOT>/backups`。升级/回滚的不同版本包必须复用同一个数据根。

Linux/POSIX 使用同名 shell 脚本，例如 `bash scripts/verify-package.sh`、
`bash scripts/start.sh`、`bash scripts/health.sh` 和 `bash scripts/stop.sh`；备份、
恢复、升级、回滚分别使用 `backup.sh`、`restore.sh`、`upgrade.sh`、`rollback.sh`。
shell 路径还要求 Bash、curl 和 Python 3。两套脚本遵守相同包结构与确认边界。

## 成功标准

- `health.ps1` 返回 `status=UP`；前端首页 HTTP 200。
- `docker compose ... ps` 中四个服务无反复重启。
- 日志没有 Flyway checksum、认证配置、数据库连接或 Redis 连接失败。
- 登录后版本信息与 `VERSION.json` 一致，平台后台和系统工作台均可进入。

## 停止与卸载边界

停止不会删除数据。Compose `down -v` 会永久删除数据库/Redis 卷，不属于标准脚本，执行前必须完成并校验备份。删除版本目录前保留 `.env`、整个 `EXAMINE_DATA_ROOT` 和审计所需日志。
