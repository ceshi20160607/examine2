-- Repair older manual installs where un_module_menu existed before menu-level API ACL columns were added.
-- MySQL versions differ on ALTER TABLE ... ADD COLUMN IF NOT EXISTS support, so use information_schema guards.
SET @db = DATABASE();

SET @c_visible := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_menu' AND COLUMN_NAME = 'visible_flag'
);
SET @sql_visible := IF(
    @c_visible = 0,
    'ALTER TABLE un_module_menu ADD COLUMN visible_flag TINYINT NOT NULL DEFAULT 1 COMMENT ''是否可见：1=可见 0=隐藏'' AFTER sort_no',
    'SELECT 1'
);
PREPARE stmt_visible FROM @sql_visible;
EXECUTE stmt_visible;
DEALLOCATE PREPARE stmt_visible;

SET @c_perm := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_menu' AND COLUMN_NAME = 'perm_key'
);
SET @sql_perm := IF(
    @c_perm = 0,
    'ALTER TABLE un_module_menu ADD COLUMN perm_key VARCHAR(255) NULL COMMENT ''菜单级功能权限'' AFTER visible_flag',
    'SELECT 1'
);
PREPARE stmt_perm FROM @sql_perm;
EXECUTE stmt_perm;
DEALLOCATE PREPARE stmt_perm;

SET @c_api := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_menu' AND COLUMN_NAME = 'api_pattern'
);
SET @sql_api := IF(
    @c_api = 0,
    'ALTER TABLE un_module_menu ADD COLUMN api_pattern VARCHAR(255) NULL COMMENT ''后端 Ant 路径'' AFTER perm_key',
    'SELECT 1'
);
PREPARE stmt_api FROM @sql_api;
EXECUTE stmt_api;
DEALLOCATE PREPARE stmt_api;

SET @c_fields := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_menu' AND COLUMN_NAME = 'module_fields_json'
);
SET @sql_fields := IF(
    @c_fields = 0,
    'ALTER TABLE un_module_menu ADD COLUMN module_fields_json JSON NULL COMMENT ''模块入口字段展示配置'' AFTER api_pattern',
    'SELECT 1'
);
PREPARE stmt_fields FROM @sql_fields;
EXECUTE stmt_fields;
DEALLOCATE PREPARE stmt_fields;
