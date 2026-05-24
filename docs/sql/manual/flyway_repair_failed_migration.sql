-- 手工修复 Flyway「failed migration」（success=0）导致应用无法启动
-- 用法：在目标库执行本脚本后重启 examine-web（dev 已默认 repair-on-migrate=true）
--
-- 若 V14 列已手工加过：可只跑 flyway_repair_v14_already_applied.sql（只删 version=14 失败行）
--
-- 1) 删除失败记录（等价于 flyway repair 对失败项的处理）
DELETE FROM flyway_schema_history WHERE success = 0;

-- 2) 若曾改过已成功的 migration 脚本文本导致 checksum 校验失败，可对应用：
--    flyway repair（或临时 EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE=false 启动一次后再改回 true）
