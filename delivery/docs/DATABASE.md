# 数据库、备份与恢复

后端通过包内 `sql/migration` 执行 Flyway。迁移只能前进，已应用脚本不得修改；新增变更必须使用更高且唯一的版本号。启动失败若包含 checksum mismatch，应停止，不得 repair 掩盖未授权修改。

## 备份

```powershell
pwsh -File scripts/backup.ps1 -Label before-change
```

POSIX 等价命令为 `bash scripts/backup.sh before-change`。

备份使用一致性 `mysqldump`，同时归档 `<EXAMINE_DATA_ROOT>/files`，并生成含 SHA-256 和源版本的 `backup-manifest.json`。`.env`、OpenAPI/SMTP/S3/AI 明文秘密不会进入备份；秘密管理系统需另行纳入组织备份策略。

## 恢复

```powershell
pwsh -File scripts/restore.ps1 -BackupRoot D:\examine2-data\backups\20260807-190000-before-change -ConfirmRestore
```

恢复会停止应用、校验哈希、重建目标数据库、导入 SQL、恢复文件并重新启动。它是破坏性操作，必须使用独立演练环境先验证。完成后检查 health、登录、权限、文件下载、待办、OpenAPI 和审计日志。

## RPO/RTO

单机参考目标：RPO 24 小时、RTO 4 小时；重要变更前强制即时备份。实际目标由数据量、磁盘和组织制度决定，应通过季度恢复演练记录实测耗时。若业务要求更小 RPO，应在基础设施层增加 MySQL binlog/PITR 和对象存储版本化，而不是缩短脚本超时冒充能力。

## 诊断

- 连接失败：核对容器健康、数据库名/账户、磁盘和 `.env`。
- 迁移失败：保留日志、版本包与数据库快照；不要改已应用 SQL。
- 慢查询：开启 MySQL slow query log，在相同数据规模执行 `EXPLAIN ANALYZE`；确认分页、typed index 和租户条件均命中。
