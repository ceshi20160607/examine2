# 本轮 QA 数据迁入隔离库

2026-10-19 将本轮已经通过接口验证的 `unexamine` 数据迁入 `unexamine_r15`。这是库名隔离，不变更 94 张表的结构；不执行 `docs/old_table1.sql`。

1. 停止 9999 后端，避免导出期间新增写入。
2. 在本机已有的 MySQL 容器内，用 `mysqldump --single-transaction --routines --triggers --hex-blob unexamine` 导出；创建 `unexamine_r15`，将导出文件导入新库。容器环境变量 `MYSQL_ROOT_PASSWORD` 只在容器内传给 `MYSQL_PWD`，不写入工程文件。
3. 对两个库分别以 `--no-create-info --skip-comments --skip-dump-date --compact --skip-add-locks --skip-disable-keys --skip-set-charset --skip-extended-insert --order-by-primary` 导出数据；两份文件 SHA-256 均为 `0c174a216b134d93aeaacfb6c88d943aae05c54956959581d3427756ba854d69`。两库均为 94 张表；新库缺失中文表备注、字段备注及 `*_at` 列的计数均为 0。
4. 将建库 SQL、生成器和后端默认连接统一切到 `unexamine_r15`；启动 9999，复测登录、模块记录与场景、关联流程和到期执行。新库入口验证通过后，已清理旧 `unexamine` 库及容器内临时导出文件。

历史项目库已在本轮开始时获准清理；MySQL 系统库始终保留。以后若修改已存在的数据结构，应另写增量迁移，不能按初始建库脚本删表重建。
