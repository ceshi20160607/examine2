-- 为已有系统增加权限版本。每次授权变化递增版本，Redis 失效失败也不能复用旧授权缓存。
SET @un_migration_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_sys_system_config' AND column_name='permission_revision'),
  'SELECT 1',
  'ALTER TABLE un_sys_system_config ADD COLUMN permission_revision BIGINT NOT NULL DEFAULT 0 COMMENT ''权限配置版本，变更后隔离旧缓存'' AFTER multi_tenant_enabled');
PREPARE un_migration_stmt FROM @un_migration_sql;
EXECUTE un_migration_stmt;
DEALLOCATE PREPARE un_migration_stmt;

-- 修正先前写入数据库的动作列乱码备注，仅调整定义备注并保留所有授权数据。
ALTER TABLE un_sys_role_data MODIFY COLUMN action_code VARCHAR(80) NOT NULL DEFAULT 'VIEW' COMMENT '查看编辑删除等模块动作编码';
ALTER TABLE un_sys_role_data MODIFY COLUMN scope_config JSON DEFAULT NULL COMMENT '负责人部门范围及动作字段读写许可';

-- 字典草稿与运行态发布快照分别保存，升级保留旧的已发布选项及版本。
SET @un_migration_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_dict' AND column_name='dict_type'),
  'SELECT 1',
  'ALTER TABLE un_module_dict ADD COLUMN dict_type VARCHAR(30) NOT NULL DEFAULT ''LIST'' COMMENT ''字典类型：列表树形级联状态标签或字段选项'' AFTER name');
PREPARE un_migration_stmt FROM @un_migration_sql;
EXECUTE un_migration_stmt;
DEALLOCATE PREPARE un_migration_stmt;
SET @un_migration_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_dict' AND column_name='published_options_json'),
  'SELECT 1',
  'ALTER TABLE un_module_dict ADD COLUMN published_options_json JSON DEFAULT NULL COMMENT ''已发布的字典名称类型与选项快照'' AFTER options_json');
PREPARE un_migration_stmt FROM @un_migration_sql;
EXECUTE un_migration_stmt;
DEALLOCATE PREPARE un_migration_stmt;
UPDATE un_module_dict SET published_options_json=JSON_OBJECT('name',name,'type',dict_type,'options',options_json)
WHERE status='PUBLISHED' AND published_options_json IS NULL;

-- 选项名称快照与业务值分开存储，升级不推测或改写既有记录的历史名称。
SET @un_migration_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_record_data' AND column_name='value_display_json'),
  'SELECT 1',
  'ALTER TABLE un_module_record_data ADD COLUMN value_display_json JSON DEFAULT NULL COMMENT ''保存时的选项名称快照，保留历史展示'' AFTER value_json');
PREPARE un_migration_stmt FROM @un_migration_sql;
EXECUTE un_migration_stmt;
DEALLOCATE PREPARE un_migration_stmt;
