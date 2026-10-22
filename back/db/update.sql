-- 将首装基线升级为当前结构。保留业务记录，不建立外键或触发器。
-- 辅助过程只处理固定业务表；每次执行前检查列和索引，支持重复执行。
DELIMITER $$
DROP PROCEDURE IF EXISTS un_r16_add_column$$
CREATE PROCEDURE un_r16_add_column(IN table_name_arg VARCHAR(80), IN column_name_arg VARCHAR(80), IN definition_arg TEXT)
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=table_name_arg AND column_name=column_name_arg) THEN
    SET @un_sql=CONCAT('ALTER TABLE ',table_name_arg,' ADD COLUMN ',column_name_arg,' ',definition_arg);
    PREPARE un_stmt FROM @un_sql; EXECUTE un_stmt; DEALLOCATE PREPARE un_stmt;
  END IF;
END$$
DROP PROCEDURE IF EXISTS un_r16_index$$
CREATE PROCEDURE un_r16_index(IN table_name_arg VARCHAR(80), IN index_name_arg VARCHAR(80), IN columns_arg TEXT)
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=table_name_arg AND index_name=index_name_arg) THEN
    SET @un_sql=CONCAT('ALTER TABLE ',table_name_arg,' ADD UNIQUE KEY ',index_name_arg,' (',columns_arg,')');
    PREPARE un_stmt FROM @un_sql; EXECUTE un_stmt; DEALLOCATE PREPARE un_stmt;
  END IF;
END$$
DELIMITER ;
CALL un_r16_add_column('un_sys_system_config','permission_revision','BIGINT NOT NULL DEFAULT 0 COMMENT ''权限配置提交版本''');
CALL un_r16_add_column('un_module_dict','dict_type','VARCHAR(30) NOT NULL DEFAULT ''LIST'' COMMENT ''字典类型：列表树形级联状态标签或字段选项''');
CALL un_r16_add_column('un_module_dict','published_options_json','JSON DEFAULT NULL COMMENT ''已发布的字典名称类型与选项快照''');
CALL un_r16_add_column('un_module_record_data','value_display_json','JSON DEFAULT NULL COMMENT ''既有显示快照保留，业务展示按字典查询''');
CALL un_r16_add_column('un_sys_menu','menu_type','VARCHAR(20) NOT NULL DEFAULT ''PAGE'' COMMENT ''菜单类型：目录页面或操作''');
CALL un_r16_add_column('un_sys_role_data','menu_id','BIGINT DEFAULT NULL COMMENT ''对应操作菜单主键''');
CALL un_r16_add_column('un_module_menu','display_position','VARCHAR(40) NOT NULL DEFAULT ''DETAIL_HEADER'' COMMENT ''模块内菜单展示位置''');
CALL un_r16_add_column('un_module_action','menu_id','BIGINT DEFAULT NULL COMMENT ''动作对应的唯一菜单主键''');
CALL un_r16_add_column('un_module_action','execution_type','VARCHAR(40) NOT NULL DEFAULT ''UPDATE_FIELDS'' COMMENT ''基础动作或字段更新执行类型''');
CALL un_r16_add_column('un_module_action','published_config_json','JSON DEFAULT NULL COMMENT ''运行态发布的动作执行配置''');
CALL un_r16_add_column('un_module_action','published_enabled_flag','TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''已发布动作是否启用''');
CALL un_r16_add_column('un_module_relation','relation_type','VARCHAR(20) NOT NULL DEFAULT ''RELATION'' COMMENT ''普通关联或模块转化关系''');
CALL un_r16_add_column('un_module_relation','target_module_id','BIGINT DEFAULT NULL COMMENT ''转化目标模块主键，普通关联为空''');
CALL un_r16_add_column('un_module_relation','action_menu_id','BIGINT DEFAULT NULL COMMENT ''产生转化关系的操作菜单主键''');

CREATE TABLE IF NOT EXISTS un_sys_role_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色模块字段许可主键',
  role_id BIGINT NOT NULL COMMENT '当前租户角色主键',
  module_id BIGINT NOT NULL COMMENT '字段所属业务模块主键',
  field_id BIGINT NOT NULL COMMENT '稳定模块字段主键',
  readable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许读取字段',
  writable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许写入字段',
  exportable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许导出字段',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_field (role_id, module_id, field_id), KEY ix_sys_role_field_module (module_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色对模块字段的控制';


UPDATE un_module_dict SET published_options_json=JSON_OBJECT('name',name,'type',dict_type,'options',options_json)
WHERE status='PUBLISHED' AND published_options_json IS NULL;
ALTER TABLE un_module_relation MODIFY COLUMN relation_config_id BIGINT DEFAULT NULL COMMENT '普通关联对应配置主键，转化关系为空';
CALL un_r16_index('un_module_relation','uk_module_conversion_target','source_record_id,target_module_id,relation_type');

-- 先建立每个既有模块的十个独立菜单；原角色尚未授权的新动作不会自动授权。
DROP TEMPORARY TABLE IF EXISTS un_r16_actions;
CREATE TEMPORARY TABLE un_r16_actions(code VARCHAR(40), label VARCHAR(100), position VARCHAR(40), kind VARCHAR(20), enabled TINYINT, ordinal_no INT);
INSERT INTO un_r16_actions VALUES
('CREATE','新建','LIST_TOOLBAR','ACTION',1,0),('EDIT','编辑','ROW','ACTION',1,1),
('LIST','列表','MODULE_ENTRY','PAGE',1,2),('DETAIL','详情','ROW','PAGE',1,3),
('IMPORT','导入','LIST_TOOLBAR','ACTION',0,4),('EXPORT','导出','LIST_TOOLBAR','ACTION',0,5),
('DELETE','删除','ROW','ACTION',1,6),('TRANSFER','转移','DETAIL_HEADER','ACTION',1,7),
('PRINT','打印','DETAIL_HEADER','ACTION',0,8),('CONVERT','转化','DETAIL_HEADER','ACTION',1,9);
INSERT INTO un_sys_menu(system_id,tenant_id,code,name,menu_type,status)
SELECT system_id,tenant_id,CONCAT('MODULE_',id),name,'DIRECTORY','ACTIVE' FROM un_module
WHERE 1=1
ON DUPLICATE KEY UPDATE code=un_sys_menu.code;
UPDATE un_sys_menu p JOIN un_module m ON p.system_id=m.system_id AND p.tenant_id=m.tenant_id AND p.code=CONCAT('MODULE_',m.id) SET p.menu_type='DIRECTORY';
INSERT INTO un_sys_menu(system_id,tenant_id,parent_id,code,name,menu_type,sort_no,status)
SELECT m.system_id,m.tenant_id,p.id,CONCAT('MODULE_',m.id,'_',a.code),a.label,a.kind,a.ordinal_no,'ACTIVE'
FROM un_module m JOIN un_sys_menu p ON p.tenant_id=m.tenant_id AND p.code=CONCAT('MODULE_',m.id) CROSS JOIN un_r16_actions a
WHERE 1=1
ON DUPLICATE KEY UPDATE code=un_sys_menu.code;

DELIMITER $$
DROP PROCEDURE IF EXISTS un_r16_permissions$$
CREATE PROCEDURE un_r16_permissions()
BEGIN
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_sys_role_menu' AND column_name='action_code') THEN
    START TRANSACTION;
    -- 原字段规则只在已有动作中取交集，避免迁为模块级字段权限时扩权。
    -- account_id=0 表示系统结构迁移，不冒用所属人或其他成员身份。
    INSERT INTO un_sys_log_opt(system_id,tenant_id,account_id,action_code,object_type,object_id,detail_json,trace_id)
    SELECT r.system_id,r.tenant_id,0,'ROLE_FIELD_MODEL_MIGRATION','ROLE',r.id,
      JSON_OBJECT('moduleId',d.module_id,'reason','旧动作字段许可按交集迁移，管理员可在角色字段页重新配置',
        'previousPolicies',JSON_ARRAYAGG(JSON_OBJECT('action',d.action_code,'configuration',d.scope_config))),
      CONCAT('role_field_migration_',r.id,'_',d.module_id)
    FROM un_sys_role_data d JOIN un_sys_role r ON r.id=d.role_id
    WHERE JSON_CONTAINS_PATH(d.scope_config,'one','$.fields')=1
      AND NOT EXISTS(SELECT 1 FROM un_sys_log_opt l WHERE l.trace_id=CONCAT('role_field_migration_',r.id,'_',d.module_id))
    GROUP BY r.id,r.system_id,r.tenant_id,d.module_id;
    INSERT IGNORE INTO un_sys_role_field(role_id,module_id,field_id,readable_flag,writable_flag,exportable_flag)
    SELECT d.role_id,d.module_id,f.id,
      MIN(COALESCE(JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.readable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.readable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.readable'),JSON_QUOTE(f.field_code)),0)),
      MIN(COALESCE((JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.readable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.readable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.readable'),JSON_QUOTE(f.field_code)))
        AND (JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.writable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.writable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.writable'),JSON_QUOTE(f.field_code))),0)),0
    FROM un_sys_role_data d JOIN un_module_field f ON f.module_id=d.module_id AND f.status='PUBLISHED'
    GROUP BY d.role_id,d.module_id,f.id;
    -- 旧 VIEW 同时承担列表和详情，拆成两个显式菜单授权，沿用原数据范围。
    INSERT IGNORE INTO un_sys_role_menu(role_id,menu_id,action_code)
    SELECT g.role_id,n.id,'MIGRATED'
    FROM un_sys_role_menu g JOIN un_sys_menu p ON p.id=g.menu_id
    JOIN un_module m ON p.code=CONCAT('MODULE_',m.id) AND p.system_id=m.system_id
    JOIN un_r16_actions a ON a.code=g.action_code OR (g.action_code='VIEW' AND a.code IN ('LIST','DETAIL'))
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code);
    INSERT IGNORE INTO un_sys_role_data(role_id,module_id,action_code,scope_code,scope_config,menu_id)
    SELECT d.role_id,d.module_id,'DETAIL',d.scope_code,d.scope_config,n.id
    FROM un_sys_role_data d JOIN un_module m ON m.id=d.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_DETAIL')
    WHERE d.action_code='VIEW';
    UPDATE un_sys_role_data d JOIN un_module m ON m.id=d.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',IF(d.action_code='VIEW','LIST',d.action_code))
    SET d.menu_id=n.id,d.scope_config=JSON_REMOVE(d.scope_config,'$.fields');
    IF EXISTS(SELECT 1 FROM un_sys_role_data WHERE menu_id IS NULL) THEN
      ROLLBACK; SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在未解析的旧动作，迁移已回滚';
    END IF;
    -- 无执行语义的旧模块父菜单不授予子动作；保留其原导航授权，去重为角色菜单关系。
    DELETE a FROM un_sys_role_menu a JOIN un_sys_role_menu b ON a.role_id=b.role_id AND a.menu_id=b.menu_id AND a.id>b.id;
    UPDATE un_sys_system_config SET permission_revision=permission_revision+1;
    COMMIT;
    ALTER TABLE un_sys_role_menu DROP INDEX uk_sys_role_menu_action, DROP COLUMN action_code;
    ALTER TABLE un_sys_role_data DROP INDEX uk_sys_role_data_action, DROP COLUMN action_code, DROP COLUMN module_id;
  END IF;
END$$
CALL un_r16_permissions()$$
DROP PROCEDURE un_r16_permissions$$

DROP PROCEDURE IF EXISTS un_r16_module_menus$$
CREATE PROCEDURE un_r16_module_menus()
BEGIN
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_menu' AND column_name='label') THEN
    -- 旧模块菜单已有关联时将展示资料归并到统一菜单，未关联的不删除，直接阻断要求修正。
    IF EXISTS(SELECT 1 FROM un_module_menu WHERE menu_id IS NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='旧模块菜单尚未关联统一菜单，禁止丢弃原配置';
    END IF;
    UPDATE un_sys_menu m JOIN un_module_menu b ON b.menu_id=m.id SET m.name=b.label,m.route=COALESCE(b.route,m.route);
    ALTER TABLE un_module_menu DROP COLUMN label, DROP COLUMN route;
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_action' AND column_name='action_code') THEN
    UPDATE un_module_action a JOIN un_module m ON m.id=a.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.action_code)
    SET a.menu_id=n.id,a.execution_type=a.action_code;
    IF EXISTS(SELECT 1 FROM un_module_action WHERE menu_id IS NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='旧模块动作尚未匹配菜单，禁止丢弃原配置';
    END IF;
    ALTER TABLE un_module_action DROP INDEX uk_module_action_code, DROP COLUMN action_code;
  END IF;
END$$
CALL un_r16_module_menus()$$
DROP PROCEDURE un_r16_module_menus$$
DELIMITER ;
ALTER TABLE un_sys_role_data MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '对应操作菜单主键', MODIFY COLUMN scope_config JSON DEFAULT NULL COMMENT '指定负责人和部门范围';
ALTER TABLE un_module_menu MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '统一系统菜单主键';
ALTER TABLE un_module_action MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '动作对应的唯一菜单主键';
ALTER TABLE un_module_action MODIFY COLUMN execution_type VARCHAR(40) NOT NULL COMMENT '基础动作或字段更新执行类型';
ALTER TABLE un_module_record_data MODIFY COLUMN value_display_json JSON DEFAULT NULL COMMENT '既有显示快照保留，业务展示按字典查询';
ALTER TABLE un_module_menu COMMENT='模块与统一菜单的关联和展示配置';
ALTER TABLE un_module_action COMMENT='菜单操作的模块动作执行配置';
CALL un_r16_index('un_sys_role_menu','uk_sys_role_menu','role_id,menu_id');
CALL un_r16_index('un_sys_role_data','uk_sys_role_data_menu','role_id,menu_id');
CALL un_r16_index('un_module_menu','uk_module_menu_binding','module_id,menu_id');
CALL un_r16_index('un_module_action','uk_module_action_menu','menu_id');

INSERT INTO un_module_menu(system_id,tenant_id,module_id,menu_id,display_position,sort_no,status)
SELECT m.system_id,m.tenant_id,m.id,n.id,a.position,a.ordinal_no,IF(m.status='PUBLISHED','PUBLISHED','DRAFT')
FROM un_module m CROSS JOIN un_r16_actions a
JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code)
WHERE 1=1
ON DUPLICATE KEY UPDATE menu_id=un_module_menu.menu_id;
INSERT INTO un_module_action(module_id,tenant_id,menu_id,execution_type,enabled_flag,config_json,published_config_json,published_enabled_flag,status,published_version)
SELECT m.id,m.tenant_id,n.id,a.code,a.enabled,
 JSON_OBJECT('name',a.label,'type',a.code,'position',a.position,'fieldIds',JSON_ARRAY(),'conditions',JSON_ARRAY(),'mappings',JSON_ARRAY()),
 IF(m.status='PUBLISHED',JSON_OBJECT('name',a.label,'type',a.code,'position',a.position,'fieldIds',JSON_ARRAY(),'conditions',JSON_ARRAY(),'mappings',JSON_ARRAY()),NULL),
 IF(m.status='PUBLISHED',a.enabled,0),IF(m.status='PUBLISHED','PUBLISHED','DRAFT'),IF(m.status='PUBLISHED',1,0)
FROM un_module m CROSS JOIN un_r16_actions a
JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code)
WHERE 1=1
ON DUPLICATE KEY UPDATE menu_id=un_module_action.menu_id;
DROP TEMPORARY TABLE un_r16_actions;
DROP PROCEDURE un_r16_add_column;
DROP PROCEDURE un_r16_index;
