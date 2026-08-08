# 升级与回滚

升级前必须保留当前版本目录和足够磁盘，阅读新版本说明，并在同规模预发布环境完成安装、迁移、登录、关键旅程和恢复演练。

```powershell
# 在旧版本目录执行
pwsh -File scripts/upgrade.ps1 -NewPackageRoot D:\apps\examine2\8.92.0 -ConfirmUpgrade
```

脚本验证新包、在共享 `EXAMINE_DATA_ROOT` 生成 `pre-upgrade` 数据库/文件备份、停止旧服务、复制受保护 `.env`，然后以新制品启动和体检。新旧版本必须使用同一绝对数据根；Flyway 负责数据库前向迁移。

POSIX 等价入口为 `bash scripts/upgrade.sh /opt/examine2/8.92.0 --confirm-upgrade`；
回滚使用 `bash scripts/rollback.sh PREVIOUS_PACKAGE_ROOT BACKUP_ROOT --confirm-rollback`。

若升级失败，命令会输出精确回滚参数。由于 Flyway 不做降级，回滚不是简单换 JAR，而是恢复升级前数据库和文件：

```powershell
pwsh -File scripts/rollback.ps1 `
  -PreviousPackageRoot D:\apps\examine2\8.91.0 `
  -BackupRoot D:\apps\examine2\8.91.0\data\backups\...-pre-upgrade `
  -ConfirmRollback
```

回滚完成需验证健康、版本、登录、权限、配置发布版本、流程待办、文件、OpenAPI 及审计链。若数据库恢复已开始，不得同时启动新旧后端。若备份哈希不匹配，立即停止并从离线副本恢复，禁止跳过校验。
