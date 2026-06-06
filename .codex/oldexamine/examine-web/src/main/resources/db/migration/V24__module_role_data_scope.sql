-- 角色数据权限范围（幂等；原 docs/sql/manual/V21__module_role_data_scope.sql）
SET NAMES utf8mb4;

SET @db = DATABASE();
SET @c := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_role' AND COLUMN_NAME = 'data_scope'
);
SET @sql := IF(
    @c = 0,
    'ALTER TABLE un_module_role ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1 COMMENT ''数据权限：1本人 2本人及下属 3本部门 4本部门及下级 5全部'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
