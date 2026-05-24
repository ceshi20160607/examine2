-- 列已手工执行、希望 Flyway 不再重跑 DDL 时：在 repair 清 success=0 之后、
-- 若启动仍要跑 V14/V23，请依赖仓库内幂等脚本即可。
-- 若需「仅登记成功、完全不执行脚本」，请用 Flyway CLI repair + migrate，或保持默认流程：
--   1) flyway_repair_startup.sql
--   2) 启动应用（幂等 V14/V23/V24）

-- 已执行 17（typed 列）时，避免 V23 重复：启动前设置
--   PowerShell: $env:EXAMINE_FLYWAY_BASELINE_VERSION = '23'

SELECT version, success FROM flyway_schema_history
WHERE version IN ('14','22','23','24') OR success = 0
ORDER BY installed_rank;
