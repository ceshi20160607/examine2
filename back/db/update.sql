-- 当前字段模型已重新编号并重新设计 un_module_field，开发库按 final.sql 清理后首装。
-- 不兼容旧字段类型、旧 config_json、子表和旧日期列，因此不提供旧模型迁移。
-- 需要保留真实业务数据时，应先由产品确认迁移方案，不能把旧模型静默转换为新模型。
-- Agent 对话追溯新增独立表；已有模型配置、业务记录和操作日志无需改写或删除。
-- 既有写入发生时未保存原始对话，无法从脱敏日志复原；迁移后新增对话才可完整追溯。
CREATE TABLE IF NOT EXISTS un_common_ai_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '智能对话记录主键',
  trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '跨预览确认和业务日志的追踪标识',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统智能对话作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统作用域的业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统作用域的租户主键',
  account_id BIGINT NOT NULL COMMENT '发起对话的平台账号主键',
  config_id BIGINT NOT NULL COMMENT '所使用的系统或平台智能配置主键',
  platform_model_id BIGINT NOT NULL COMMENT '实际调用的平台模型配置主键',
  system_policy_version INT DEFAULT NULL COMMENT '系统 Agent 发布策略版本，平台对话为空',
  platform_model_version INT NOT NULL COMMENT '平台模型发布版本',
  prompt_version VARCHAR(80) DEFAULT NULL COMMENT '系统提示词版本，平台对话为空',
  intent_code VARCHAR(40) NOT NULL COMMENT '模型识别的业务意图编码',
  request_text TEXT NOT NULL COMMENT '用户原始对话内容，仅本人经授权接口读取',
  proposal_json JSON DEFAULT NULL COMMENT '模型生成且展示给用户的结构化预览',
  status VARCHAR(20) NOT NULL COMMENT '待补充待确认已确认或失败状态',
  target_type VARCHAR(40) DEFAULT NULL COMMENT '确认后生成的业务对象类型',
  target_id BIGINT DEFAULT NULL COMMENT '确认后生成的业务对象主键',
  confirm_account_id BIGINT DEFAULT NULL COMMENT '实际确认的平台账号主键',
  confirm_time DATETIME(3) DEFAULT NULL COMMENT '用户确认时间',
  expire_time DATETIME(3) NOT NULL COMMENT '原始对话保留到期时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '原始对话创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近状态更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_ai_conversation_trace (trace_id),
  KEY ix_ai_conversation_actor_time (account_id, create_time),
  KEY ix_ai_conversation_system (system_id, tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能对话追溯记录';

-- 一次性补齐既有智能配置的对话保留天数，避免运行态长期兼容缺失字段；默认保留 90 天。
-- 仅修改缺失该最终字段的配置与发布快照，不覆盖已配置的天数和其他模型策略。
UPDATE un_common_ai SET config_json = JSON_SET(config_json, '$.conversationRetentionDays', 90)
WHERE scope_type = 'PLATFORM' AND config_json IS NOT NULL
  AND JSON_CONTAINS_PATH(config_json, 'one', '$.conversationRetentionDays') = 0;
UPDATE un_common_ai SET published_config_json = JSON_SET(published_config_json, '$.conversationRetentionDays', 90)
WHERE scope_type = 'PLATFORM' AND published_config_json IS NOT NULL
  AND JSON_CONTAINS_PATH(published_config_json, 'one', '$.conversationRetentionDays') = 0;
UPDATE un_common_ai SET config_json = JSON_SET(config_json, '$.policy.conversationRetentionDays', 90)
WHERE scope_type = 'SYSTEM' AND config_json IS NOT NULL
  AND JSON_CONTAINS_PATH(config_json, 'one', '$.policy.conversationRetentionDays') = 0;
UPDATE un_common_ai SET published_config_json = JSON_SET(published_config_json, '$.policy.conversationRetentionDays', 90)
WHERE scope_type = 'SYSTEM' AND published_config_json IS NOT NULL
  AND JSON_CONTAINS_PATH(published_config_json, 'one', '$.policy.conversationRetentionDays') = 0;

-- 适用基线：已建有 un_examine、un_examine_example 且 system_id/tenant_id 仍为 NOT NULL 的目标库。
-- 迁移目标：允许平台审批定义和实例不属于业务系统；保留现有系统审批及实例数据。
-- 旧唯一键只按 tenant_id 限制编码，改为按平台或系统与租户作用域限制，避免平台编码重复。
-- 回退前须迁出平台审批定义及实例，并确认旧唯一键下的编码不冲突；不能直接把含空值的列改回 NOT NULL。
SET @unexamine_definition_system_required := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_examine'
     AND column_name = 'system_id' AND is_nullable = 'NO'
);
SET @unexamine_definition_system_sql := IF(
  @unexamine_definition_system_required = 1,
  'ALTER TABLE un_examine MODIFY COLUMN system_id BIGINT DEFAULT NULL COMMENT ''所属业务系统主键，平台审批为空''',
  'SELECT 1'
);
PREPARE unexamine_definition_system_stmt FROM @unexamine_definition_system_sql;
EXECUTE unexamine_definition_system_stmt;
DEALLOCATE PREPARE unexamine_definition_system_stmt;

SET @unexamine_definition_tenant_required := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_examine'
     AND column_name = 'tenant_id' AND is_nullable = 'NO'
);
SET @unexamine_definition_tenant_sql := IF(
  @unexamine_definition_tenant_required = 1,
  'ALTER TABLE un_examine MODIFY COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''定义审批的租户主键，平台审批为空''',
  'SELECT 1'
);
PREPARE unexamine_definition_tenant_stmt FROM @unexamine_definition_tenant_sql;
EXECUTE unexamine_definition_tenant_stmt;
DEALLOCATE PREPARE unexamine_definition_tenant_stmt;

SET @unexamine_example_system_required := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_examine_example'
     AND column_name = 'system_id' AND is_nullable = 'NO'
);
SET @unexamine_example_system_sql := IF(
  @unexamine_example_system_required = 1,
  'ALTER TABLE un_examine_example MODIFY COLUMN system_id BIGINT DEFAULT NULL COMMENT ''审批业务所属系统主键，平台审批为空''',
  'SELECT 1'
);
PREPARE unexamine_example_system_stmt FROM @unexamine_example_system_sql;
EXECUTE unexamine_example_system_stmt;
DEALLOCATE PREPARE unexamine_example_system_stmt;

SET @unexamine_example_tenant_required := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_examine_example'
     AND column_name = 'tenant_id' AND is_nullable = 'NO'
);
SET @unexamine_example_tenant_sql := IF(
  @unexamine_example_tenant_required = 1,
  'ALTER TABLE un_examine_example MODIFY COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''审批业务所属租户主键，平台审批为空''',
  'SELECT 1'
);
PREPARE unexamine_example_tenant_stmt FROM @unexamine_example_tenant_sql;
EXECUTE unexamine_example_tenant_stmt;
DEALLOCATE PREPARE unexamine_example_tenant_stmt;

SET @unexamine_scope_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_examine'
     AND index_name = 'uk_examine_scope_code'
);
SET @unexamine_scope_index_sql := IF(
  @unexamine_scope_index_exists = 0,
  'ALTER TABLE un_examine ADD UNIQUE KEY uk_examine_scope_code ((COALESCE(system_id, 0)), (COALESCE(tenant_id, 0)), code)',
  'SELECT 1'
);
PREPARE unexamine_scope_index_stmt FROM @unexamine_scope_index_sql;
EXECUTE unexamine_scope_index_stmt;
DEALLOCATE PREPARE unexamine_scope_index_stmt;

SET @unexamine_old_scope_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_examine'
     AND index_name = 'uk_examine_code'
);
SET @unexamine_old_scope_index_sql := IF(
  @unexamine_old_scope_index_exists > 0,
  'ALTER TABLE un_examine DROP INDEX uk_examine_code',
  'SELECT 1'
);
PREPARE unexamine_old_scope_index_stmt FROM @unexamine_old_scope_index_sql;
EXECUTE unexamine_old_scope_index_stmt;
DEALLOCATE PREPARE unexamine_old_scope_index_stmt;

-- 审批实例日志用于保留审批办理、转交、回退、外部节点和终态回调的不可变操作记录。
-- 模块审批绑定支持系统级配置继承；旧表若为 NOT NULL，改为可空以与运行查询及业务规则一致。
SET @unexamine_binding_tenant_nullable := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_module_examine'
     AND column_name = 'tenant_id' AND is_nullable = 'NO'
);
SET @unexamine_binding_tenant_sql := IF(
  @unexamine_binding_tenant_nullable = 1,
  'ALTER TABLE un_module_examine MODIFY COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''审批绑定定义租户主键，空表示系统级绑定并可被租户继承''',
  'SELECT 1'
);
PREPARE unexamine_binding_tenant_stmt FROM @unexamine_binding_tenant_sql;
EXECUTE unexamine_binding_tenant_stmt;
DEALLOCATE PREPARE unexamine_binding_tenant_stmt;

CREATE TABLE IF NOT EXISTS un_examine_example_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批实例日志主键',
  example_id BIGINT NOT NULL COMMENT '所属审批实例主键',
  example_node_id BIGINT DEFAULT NULL COMMENT '关联审批实例节点主键',
  account_id BIGINT DEFAULT NULL COMMENT '实际操作平台账号主键',
  event_code VARCHAR(50) NOT NULL COMMENT '审批实例事件编码',
  detail_json JSON DEFAULT NULL COMMENT '审批意见字段变更转交回退和外部调用结果快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志创建时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_log_example (example_id, id),
  KEY ix_examine_log_node (example_node_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例操作与状态日志';

-- 为直系上级审批补充租户成员关系字段；已有成员为空时仍可使用节点显式指定的上级账号。
SET @unexamine_supervisor_column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'un_sys_tenant_member'
     AND column_name = 'supervisor_account_id'
);
SET @unexamine_supervisor_column_sql := IF(
  @unexamine_supervisor_column_exists = 0,
  'ALTER TABLE un_sys_tenant_member ADD COLUMN supervisor_account_id BIGINT DEFAULT NULL COMMENT ''当前租户成员的直系上级平台账号主键''',
  'SELECT 1'
);
PREPARE unexamine_supervisor_column_stmt FROM @unexamine_supervisor_column_sql;
EXECUTE unexamine_supervisor_column_stmt;
DEALLOCATE PREPARE unexamine_supervisor_column_stmt;

SET @unexamine_supervisor_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_sys_tenant_member'
     AND index_name = 'ix_sys_tenant_member_supervisor'
);
SET @unexamine_supervisor_index_sql := IF(
  @unexamine_supervisor_index_exists = 0,
  'ALTER TABLE un_sys_tenant_member ADD KEY ix_sys_tenant_member_supervisor (tenant_id, supervisor_account_id)',
  'SELECT 1'
);
PREPARE unexamine_supervisor_index_stmt FROM @unexamine_supervisor_index_sql;
EXECUTE unexamine_supervisor_index_stmt;
DEALLOCATE PREPARE unexamine_supervisor_index_stmt;

-- 平台角色关系的唯一约束只作用于未删除记录，避免逻辑删除历史阻断再次授权。
SET @unexamine_plat_member_role_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_plat_member_role'
     AND index_name = 'uk_plat_member_role'
);
SET @unexamine_plat_member_role_drop_sql := IF(
  @unexamine_plat_member_role_index_exists = 1,
  'ALTER TABLE un_plat_member_role DROP INDEX uk_plat_member_role',
  'SELECT 1'
);
PREPARE unexamine_plat_member_role_drop_stmt FROM @unexamine_plat_member_role_drop_sql;
EXECUTE unexamine_plat_member_role_drop_stmt;
DEALLOCATE PREPARE unexamine_plat_member_role_drop_stmt;
ALTER TABLE un_plat_member_role
  ADD UNIQUE KEY uk_plat_member_role (member_id, role_id, (IF(deleted_time IS NULL, 1, NULL)));

SET @unexamine_plat_role_menu_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_plat_role_menu'
     AND index_name = 'uk_plat_role_menu_action'
);
SET @unexamine_plat_role_menu_drop_sql := IF(
  @unexamine_plat_role_menu_index_exists = 1,
  'ALTER TABLE un_plat_role_menu DROP INDEX uk_plat_role_menu_action',
  'SELECT 1'
);
PREPARE unexamine_plat_role_menu_drop_stmt FROM @unexamine_plat_role_menu_drop_sql;
EXECUTE unexamine_plat_role_menu_drop_stmt;
DEALLOCATE PREPARE unexamine_plat_role_menu_drop_stmt;
ALTER TABLE un_plat_role_menu
  ADD UNIQUE KEY uk_plat_role_menu_action (role_id, menu_id, action_code, (IF(deleted_time IS NULL, 1, NULL)));

SET @unexamine_plat_role_data_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'un_plat_role_data'
     AND index_name = 'uk_plat_role_data_resource'
);
SET @unexamine_plat_role_data_drop_sql := IF(
  @unexamine_plat_role_data_index_exists = 1,
  'ALTER TABLE un_plat_role_data DROP INDEX uk_plat_role_data_resource',
  'SELECT 1'
);
PREPARE unexamine_plat_role_data_drop_stmt FROM @unexamine_plat_role_data_drop_sql;
EXECUTE unexamine_plat_role_data_drop_stmt;
DEALLOCATE PREPARE unexamine_plat_role_data_drop_stmt;
ALTER TABLE un_plat_role_data
  ADD UNIQUE KEY uk_plat_role_data_resource (role_id, resource_code, (IF(deleted_time IS NULL, 1, NULL)));

-- 适用基线：旧版 un_kpi_result 只记录有目标的计算；保留既有结果与目标关系，补齐指标和周期后允许无目标的全租户结果。
-- 回滚需先处理 kpi_user_id 为空的全租户历史，否则旧结构无法表达这些结果；已有历史不得直接丢弃。
ALTER TABLE un_kpi_result
  ADD COLUMN kpi_id BIGINT DEFAULT NULL COMMENT '所属关键指标主键，包含无目标的全租户计算' AFTER id,
  ADD COLUMN period_key VARCHAR(20) DEFAULT NULL COMMENT '本次计算的月份季度或年度周期编码' AFTER kpi_id;
UPDATE un_kpi_result r
  JOIN un_kpi_user u ON u.id = r.kpi_user_id
  SET r.kpi_id = u.kpi_id, r.period_key = u.period_key
  WHERE r.kpi_id IS NULL OR r.period_key IS NULL;
ALTER TABLE un_kpi_result
  MODIFY COLUMN kpi_id BIGINT NOT NULL COMMENT '所属关键指标主键，包含无目标的全租户计算',
  MODIFY COLUMN period_key VARCHAR(20) NOT NULL COMMENT '本次计算的月份季度或年度周期编码',
  MODIFY COLUMN kpi_user_id BIGINT DEFAULT NULL COMMENT '人员或组织周期目标主键，空表示全租户实际值',
  ADD KEY ix_kpi_result_kpi_period (kpi_id, period_key, calculation_time);

-- 消息模板迁移开始：保留已有系统消息和已读状态；新投递记录只追踪迁移后按模板发送的消息。
-- 既有手工消息没有模板编码，无法可靠反推模板，保持原消息供接收人查询。
CREATE TABLE IF NOT EXISTS un_sys_message_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统消息模板主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  template_code VARCHAR(80) NOT NULL COMMENT '租户内唯一模板编码',
  title_template VARCHAR(200) NOT NULL COMMENT '消息标题模板',
  content_template TEXT NOT NULL COMMENT '消息正文模板',
  variable_keys_json JSON NOT NULL COMMENT '允许填充的变量名数组',
  channel VARCHAR(30) NOT NULL COMMENT '发送渠道，目前为站内消息 IN_APP',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '消息跳转的系统业务类型',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿、已发布或停用状态',
  create_account_id BIGINT NOT NULL COMMENT '模板创建账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_message_template_code (system_id, tenant_id, template_code),
  KEY ix_sys_message_template_status (system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息模板';
CREATE TABLE IF NOT EXISTS un_sys_message_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统消息投递记录主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  template_id BIGINT NOT NULL COMMENT '使用的系统消息模板主键',
  recipient_member_id BIGINT NOT NULL COMMENT '接收系统成员主键',
  dedupe_key VARCHAR(120) DEFAULT NULL COMMENT '同一接收人业务事件的去重键，空表示不去重',
  channel VARCHAR(30) NOT NULL COMMENT '实际投递渠道',
  status VARCHAR(20) NOT NULL COMMENT '投递结果状态',
  message_id BIGINT DEFAULT NULL COMMENT '成功投递的系统消息主键',
  rendered_title VARCHAR(200) NOT NULL COMMENT '发送时渲染的标题快照',
  rendered_content TEXT NOT NULL COMMENT '发送时渲染的正文快照',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '发送时的系统业务跳转类型快照',
  target_id BIGINT DEFAULT NULL COMMENT '发送时的系统业务跳转主键快照',
  attempt_count INT NOT NULL DEFAULT 1 COMMENT '投递尝试次数',
  error_text VARCHAR(500) DEFAULT NULL COMMENT '失败原因摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次投递时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近投递时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_message_dedupe (system_id, tenant_id, recipient_member_id, dedupe_key),
  KEY ix_sys_message_delivery_template (template_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息投递记录';
-- 消息模板迁移结束。

-- 平台消息模板迁移开始：保留既有平台消息及阅读状态，新投递日志仅追踪按模板发出的消息。
CREATE TABLE IF NOT EXISTS un_plat_message_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台消息模板主键',
  template_code VARCHAR(80) NOT NULL COMMENT '平台唯一模板编码',
  title_template VARCHAR(200) NOT NULL COMMENT '消息标题模板',
  content_template TEXT NOT NULL COMMENT '消息正文模板',
  variable_keys_json JSON NOT NULL COMMENT '允许填充的变量名数组',
  channel VARCHAR(30) NOT NULL COMMENT '发送渠道，目前为站内消息 IN_APP',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '平台业务跳转类型，不允许直接跳系统详情',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿、已发布或停用状态',
  create_account_id BIGINT NOT NULL COMMENT '模板创建账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_message_template_code (template_code),
  KEY ix_plat_message_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息模板';
CREATE TABLE IF NOT EXISTS un_plat_message_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台消息投递记录主键',
  template_id BIGINT NOT NULL COMMENT '使用的平台消息模板主键',
  recipient_account_id BIGINT NOT NULL COMMENT '接收平台账号主键',
  dedupe_key VARCHAR(120) DEFAULT NULL COMMENT '同一接收人业务事件的去重键，空表示不去重',
  channel VARCHAR(30) NOT NULL COMMENT '实际投递渠道',
  status VARCHAR(20) NOT NULL COMMENT '投递结果状态',
  message_id BIGINT DEFAULT NULL COMMENT '成功投递的平台消息主键',
  rendered_title VARCHAR(200) NOT NULL COMMENT '发送时渲染的标题快照',
  rendered_content TEXT NOT NULL COMMENT '发送时渲染的正文快照',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '发送时的平台业务跳转类型快照',
  target_id BIGINT DEFAULT NULL COMMENT '发送时的平台业务跳转主键快照',
  attempt_count INT NOT NULL DEFAULT 1 COMMENT '投递尝试次数',
  error_text VARCHAR(500) DEFAULT NULL COMMENT '失败原因摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次投递时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近投递时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_message_dedupe (recipient_account_id, dedupe_key),
  KEY ix_plat_message_delivery_template (template_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息投递记录';
-- 平台消息模板迁移结束。

-- 既有投递记录均为已成功记录，从原消息回填发送快照；保留原消息和阅读状态。
-- 使用列存在性判断，允许在已初始化最终表结构的开发库重复执行本段迁移。
SET @sys_delivery_snapshot_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'un_sys_message_delivery_log' AND COLUMN_NAME = 'rendered_title') = 0,
  'ALTER TABLE un_sys_message_delivery_log ADD COLUMN rendered_title VARCHAR(200) DEFAULT NULL COMMENT ''发送时渲染的标题快照'', ADD COLUMN rendered_content TEXT DEFAULT NULL COMMENT ''发送时渲染的正文快照'', ADD COLUMN target_type VARCHAR(80) DEFAULT NULL COMMENT ''发送时的系统业务跳转类型快照'', ADD COLUMN target_id BIGINT DEFAULT NULL COMMENT ''发送时的系统业务跳转主键快照''',
  'SELECT 1');
PREPARE sys_delivery_snapshot_stmt FROM @sys_delivery_snapshot_sql;
EXECUTE sys_delivery_snapshot_stmt;
DEALLOCATE PREPARE sys_delivery_snapshot_stmt;
SET @plat_delivery_snapshot_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'un_plat_message_delivery_log' AND COLUMN_NAME = 'rendered_title') = 0,
  'ALTER TABLE un_plat_message_delivery_log ADD COLUMN rendered_title VARCHAR(200) DEFAULT NULL COMMENT ''发送时渲染的标题快照'', ADD COLUMN rendered_content TEXT DEFAULT NULL COMMENT ''发送时渲染的正文快照'', ADD COLUMN target_type VARCHAR(80) DEFAULT NULL COMMENT ''发送时的平台业务跳转类型快照'', ADD COLUMN target_id BIGINT DEFAULT NULL COMMENT ''发送时的平台业务跳转主键快照''',
  'SELECT 1');
PREPARE plat_delivery_snapshot_stmt FROM @plat_delivery_snapshot_sql;
EXECUTE plat_delivery_snapshot_stmt;
DEALLOCATE PREPARE plat_delivery_snapshot_stmt;

-- 只回填旧成功投递的原始消息，不重渲染可能已修改的模板，保证投递历史一致。
UPDATE un_sys_message_delivery_log l JOIN un_sys_message m ON m.id = l.message_id
SET l.rendered_title = m.title, l.rendered_content = m.content,
    l.target_type = m.target_type, l.target_id = m.target_id
WHERE l.rendered_title IS NULL OR l.rendered_content IS NULL;
UPDATE un_plat_message_delivery_log l JOIN un_plat_message m ON m.id = l.message_id
SET l.rendered_title = m.title, l.rendered_content = m.content,
    l.target_type = m.target_type, l.target_id = m.target_id
WHERE l.rendered_title IS NULL OR l.rendered_content IS NULL;
ALTER TABLE un_sys_message_delivery_log
  MODIFY COLUMN rendered_title VARCHAR(200) NOT NULL COMMENT '发送时渲染的标题快照',
  MODIFY COLUMN rendered_content TEXT NOT NULL COMMENT '发送时渲染的正文快照';
ALTER TABLE un_plat_message_delivery_log
  MODIFY COLUMN rendered_title VARCHAR(200) NOT NULL COMMENT '发送时渲染的标题快照',
  MODIFY COLUMN rendered_content TEXT NOT NULL COMMENT '发送时渲染的正文快照';

-- 适用基线：un_module_teamer 仍以 member_type 记录旧身份；目标：只保存记录成员的 VIEW/EDIT 权限。
-- 旧身份不能推断新权限。目标库已核对该表为 0 行；其他库若有数据须先逐条确认权限并迁移，禁止静默转换或清空。
-- 若尚未写入新权限而需回滚，应从变更前备份恢复旧表结构；新权限产生后回滚须先确定数据映射。
SET @team_empty_sql = IF((SELECT COUNT(*) FROM un_module_teamer) = 0,
  'SELECT 1', 'SELECT team_member_permission_migration_required');
PREPARE team_empty_stmt FROM @team_empty_sql;
EXECUTE team_empty_stmt;
DEALLOCATE PREPARE team_empty_stmt;
ALTER TABLE un_module_teamer
  DROP INDEX uk_module_teamer,
  DROP COLUMN member_type,
  MODIFY COLUMN account_id BIGINT NOT NULL COMMENT '获得记录查看或编辑权限的平台账号主键',
  ADD COLUMN access_level VARCHAR(10) NOT NULL COMMENT '记录团队权限：VIEW查看、EDIT编辑并查看' AFTER account_id,
  ADD COLUMN create_user_id BIGINT NOT NULL COMMENT '添加团队成员的操作账号主键' AFTER status,
  ADD COLUMN update_user_id BIGINT NOT NULL COMMENT '最近修改团队权限的操作账号主键' AFTER create_time,
  ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER update_user_id,
  ADD UNIQUE KEY uk_module_teamer (record_id, account_id),
  ADD CONSTRAINT chk_module_teamer_access CHECK (access_level IN ('VIEW', 'EDIT'));

-- 为已有模块补上“团队成员”默认菜单、展示关系和执行配置；使用模块原定义租户，不自动授予任何角色。
INSERT INTO un_sys_menu (system_id, tenant_id, parent_id, code, name, menu_type, sort_no, status)
SELECT m.system_id, m.tenant_id, p.id, CONCAT('MODULE_', m.id, '_TEAM_MEMBERS'),
       '团队成员', 'ACTION', 8, 'ACTIVE'
FROM un_module m JOIN un_sys_menu p ON p.tenant_id = m.tenant_id
    AND p.code = CONCAT('MODULE_', m.id) AND p.deleted_time IS NULL
WHERE m.deleted_time IS NULL AND NOT EXISTS (
  SELECT 1 FROM un_sys_menu x WHERE x.tenant_id = m.tenant_id
    AND x.code = CONCAT('MODULE_', m.id, '_TEAM_MEMBERS'));
INSERT INTO un_module_menu (system_id, tenant_id, module_id, menu_id, display_position, sort_no, status)
SELECT m.system_id, m.tenant_id, m.id, menu.id, 'DETAIL_HEADER', 8, 'ACTIVE'
FROM un_module m JOIN un_sys_menu menu ON menu.tenant_id = m.tenant_id
    AND menu.code = CONCAT('MODULE_', m.id, '_TEAM_MEMBERS') AND menu.deleted_time IS NULL
WHERE m.deleted_time IS NULL AND NOT EXISTS (
  SELECT 1 FROM un_module_menu x WHERE x.module_id = m.id AND x.menu_id = menu.id);
INSERT INTO un_module_action (module_id, tenant_id, menu_id, execution_type, enabled_flag, config_json, status)
SELECT m.id, m.tenant_id, menu.id, 'TEAM_MEMBERS', 1,
       JSON_OBJECT('name', '团队成员', 'type', 'TEAM_MEMBERS', 'position', 'DETAIL_HEADER',
                   'fieldIds', JSON_ARRAY(), 'conditions', JSON_ARRAY(),
                   'targetModuleId', NULL, 'mappings', JSON_ARRAY()), 'ACTIVE'
FROM un_module m JOIN un_sys_menu menu ON menu.tenant_id = m.tenant_id
    AND menu.code = CONCAT('MODULE_', m.id, '_TEAM_MEMBERS') AND menu.deleted_time IS NULL
WHERE m.deleted_time IS NULL AND NOT EXISTS (
  SELECT 1 FROM un_module_action x WHERE x.menu_id = menu.id);

-- 适用基线：字段表保存一份通用布局，尚无模块页面及页面字段表；迁移目标：各页面独立配置。
-- 先给每个已有模块建立新建、编辑、详情和列表页面，再逐项复制当前字段布局；无需重算已有业务记录值。
-- 回滚须先确认各页面配置一致，再从页面字段反向回填旧列；页面独立调整后不得无损回退为一份布局。
CREATE TABLE IF NOT EXISTS un_module_page (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块页面配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '配置所属租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  page_kind VARCHAR(20) NOT NULL COMMENT '页面用途：CREATE、EDIT、DETAIL、LIST、RELATION',
  relation_field_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联页面对应来源关联字段主键，普通页面为0',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '页面配置启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_page (module_id, tenant_id, page_kind, relation_field_id),
  KEY ix_module_page_relation (relation_field_id),
  CONSTRAINT chk_module_page_kind CHECK (page_kind IN ('CREATE','EDIT','DETAIL','LIST','RELATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块页面及关联场景配置';
CREATE TABLE IF NOT EXISTS un_module_page_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块页面字段配置主键',
  page_id BIGINT NOT NULL COMMENT '所属模块页面主键',
  field_id BIGINT NOT NULL COMMENT '页面展示的稳定模块字段主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '当前页面内的字段顺序',
  row_no INT NOT NULL DEFAULT 0 COMMENT '字段在当前页面的行号',
  column_no INT NOT NULL DEFAULT 0 COMMENT '字段在当前页面所在行的列号',
  width_percent TINYINT UNSIGNED NOT NULL DEFAULT 100 COMMENT '字段在当前页面所在行的占位宽度百分比',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '页面字段启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_page_field (page_id, field_id),
  KEY ix_module_page_field_order (page_id, status, sort_no),
  KEY ix_module_page_field_reference (field_id),
  CONSTRAINT chk_module_page_width CHECK (width_percent BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块页面字段布局';
INSERT INTO un_module_page (system_id,tenant_id,module_id,page_kind,relation_field_id,status)
SELECT m.system_id,m.tenant_id,m.id,k.page_kind,0,'ACTIVE'
FROM un_module m JOIN (
  SELECT 'CREATE' page_kind UNION ALL SELECT 'EDIT' UNION ALL SELECT 'DETAIL' UNION ALL SELECT 'LIST'
) k WHERE NOT EXISTS (SELECT 1 FROM un_module_page p WHERE p.module_id=m.id
    AND p.tenant_id=m.tenant_id AND p.page_kind=k.page_kind AND p.relation_field_id=0);
INSERT INTO un_module_page_field (page_id,field_id,sort_no,row_no,column_no,width_percent,status,deleted_time)
SELECT p.id,f.id,f.sort_no,f.row_no,f.column_no,f.width_percent,f.status,f.deleted_time
FROM un_module_page p JOIN un_module_field f ON f.module_id=p.module_id AND f.tenant_id=p.tenant_id
WHERE p.page_kind IN ('CREATE','EDIT','DETAIL','LIST') AND p.relation_field_id=0
  AND NOT EXISTS (SELECT 1 FROM un_module_page_field pf WHERE pf.page_id=p.id AND pf.field_id=f.id);
-- 本批目标库核对 13 个字段、0 个已存场景与 0 个关联字段，复制后逐页面核对数量再移除旧布局列。
SET @page_copy_sql = IF((SELECT COUNT(*) FROM un_module_page_field pf
    JOIN un_module_page p ON p.id=pf.page_id WHERE p.page_kind IN ('CREATE','EDIT','DETAIL','LIST'))
    = (SELECT COUNT(*) FROM un_module_field)*4,
  'SELECT 1','SELECT page_layout_migration_required');
PREPARE page_copy_stmt FROM @page_copy_sql;
EXECUTE page_copy_stmt;
DEALLOCATE PREPARE page_copy_stmt;
ALTER TABLE un_module_field
  DROP INDEX ix_module_field_form,
  DROP COLUMN sort_no,
  DROP COLUMN row_no,
  DROP COLUMN column_no,
  DROP COLUMN width_percent,
  ADD KEY ix_module_field_form (module_id,status,id);

-- 关联摘要改由 RELATION 页面字段配置；目标库无关联字段，其他库须先将旧摘要逐项迁入对应页面。
SET @relation_page_sql = IF((SELECT COUNT(*) FROM un_module_field WHERE field_type=10
    AND JSON_CONTAINS_PATH(config_json,'one','$.relationConfig'))=0,
  'SELECT 1','SELECT relation_page_migration_required');
PREPARE relation_page_stmt FROM @relation_page_sql;
EXECUTE relation_page_stmt;
DEALLOCATE PREPARE relation_page_stmt;
ALTER TABLE un_module_field
  MODIFY COLUMN config_json JSON DEFAULT NULL COMMENT '字段结构化配置：选项联动、自动编号和公式规则；关联展示字段由页面配置';

-- 适用基线：审批绑定与实例尚未保存页面状态组和来源绑定；审批定义、节点及历史实例不改写。
-- 状态组起初为空，由管理员在模块页面配置中决定哪些动作共用状态字段；旧实例仅对可唯一识别的模块绑定补齐来源。
CREATE TABLE IF NOT EXISTS un_module_page_status (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块页面审批状态组主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '配置所属租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  status_code VARCHAR(80) NOT NULL COMMENT '列表、详情和关联筛选共用的状态字段编码',
  status_name VARCHAR(100) NOT NULL COMMENT '审批状态字段展示名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '列表页面中状态字段的展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态字段启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_page_status (module_id, tenant_id, status_code),
  KEY ix_module_page_status_order (module_id, tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块页面审批状态展示组';
ALTER TABLE un_module_examine
  ADD COLUMN status_group_id BIGINT DEFAULT NULL COMMENT '模块页面审批状态组主键，空表示不展示独立状态字段' AFTER examine_id,
  ADD KEY ix_module_examine_status_group (status_group_id);
ALTER TABLE un_examine_example
  ADD COLUMN module_examine_id BIGINT DEFAULT NULL COMMENT '发起本实例的模块审批绑定主键，Flow等其他来源为空' AFTER examine_id,
  ADD KEY ix_examine_example_binding (module_examine_id, record_id, start_time);
-- 仅识别普通模块事件，且同模块、审批定义和租户只有一条匹配绑定时回填；Flow实例不误归为动作审批。
UPDATE un_examine_example e JOIN un_module_examine b
  ON b.system_id=e.system_id AND b.module_id=e.module_id AND b.examine_id=e.examine_id
  AND (b.tenant_id=e.tenant_id OR b.tenant_id IS NULL)
  AND (b.trigger_code=JSON_UNQUOTE(JSON_EXTRACT(e.runtime_snapshot_json,'$.eventCode'))
    OR b.trigger_code=JSON_UNQUOTE(JSON_EXTRACT(e.runtime_snapshot_json,'$.operation')))
SET e.module_examine_id=b.id
WHERE e.module_examine_id IS NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(e.runtime_snapshot_json,'$.operation')) <> 'FLOW_NODE'
  AND (SELECT COUNT(*) FROM un_module_examine b2 WHERE b2.system_id=e.system_id
    AND b2.module_id=e.module_id AND b2.examine_id=e.examine_id
    AND (b2.tenant_id=e.tenant_id OR b2.tenant_id IS NULL)
    AND (b2.trigger_code=JSON_UNQUOTE(JSON_EXTRACT(e.runtime_snapshot_json,'$.eventCode'))
      OR b2.trigger_code=JSON_UNQUOTE(JSON_EXTRACT(e.runtime_snapshot_json,'$.operation'))))=1;

-- 适用基线：审批发布和实例快照的 edges 仍使用 source、target 的开发库。
-- 一次性将节点引用改为 sourceNodeCode、targetNodeCode，保留节点、条件、线路顺序和实例历史；
-- 运行代码只读取最终字段编码。已是最终格式的快照不参与更新，脚本可重复执行。
CREATE TEMPORARY TABLE tmp_examine_definition_edges AS
SELECT id, new_edges FROM (
  SELECT e.id, j.ord,
    JSON_ARRAYAGG(
      CASE WHEN j.source_code IS NOT NULL AND j.target_code IS NOT NULL
        THEN JSON_SET(JSON_REMOVE(j.edge, '$.source', '$.target'),
          '$.sourceNodeCode', j.source_code, '$.targetNodeCode', j.target_code)
        ELSE j.edge END
    ) OVER (PARTITION BY e.id ORDER BY j.ord
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS new_edges,
    ROW_NUMBER() OVER (PARTITION BY e.id ORDER BY j.ord) AS row_no
  FROM un_examine e
  JOIN JSON_TABLE(e.published_snapshot_json, '$.edges[*]'
    COLUMNS (ord FOR ORDINALITY, edge JSON PATH '$',
      source_code VARCHAR(80) PATH '$.source',
      target_code VARCHAR(80) PATH '$.target')) j
  WHERE JSON_CONTAINS_PATH(e.published_snapshot_json, 'one', '$.edges[*].source') = 1
) ordered_edges WHERE row_no = 1;
CREATE TEMPORARY TABLE tmp_examine_instance_edges AS
SELECT id, new_edges FROM (
  SELECT e.id, j.ord,
    JSON_ARRAYAGG(
      CASE WHEN j.source_code IS NOT NULL AND j.target_code IS NOT NULL
        THEN JSON_SET(JSON_REMOVE(j.edge, '$.source', '$.target'),
          '$.sourceNodeCode', j.source_code, '$.targetNodeCode', j.target_code)
        ELSE j.edge END
    ) OVER (PARTITION BY e.id ORDER BY j.ord
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS new_edges,
    ROW_NUMBER() OVER (PARTITION BY e.id ORDER BY j.ord) AS row_no
  FROM un_examine_example e
  JOIN JSON_TABLE(e.runtime_snapshot_json, '$.definition.edges[*]'
    COLUMNS (ord FOR ORDINALITY, edge JSON PATH '$',
      source_code VARCHAR(80) PATH '$.source',
      target_code VARCHAR(80) PATH '$.target')) j
  WHERE JSON_CONTAINS_PATH(e.runtime_snapshot_json, 'one', '$.definition.edges[*].source') = 1
) ordered_edges WHERE row_no = 1;
START TRANSACTION;
UPDATE un_examine e JOIN tmp_examine_definition_edges t ON t.id = e.id
SET e.published_snapshot_json = JSON_SET(e.published_snapshot_json, '$.edges', t.new_edges);
UPDATE un_examine_example e JOIN tmp_examine_instance_edges t ON t.id = e.id
SET e.runtime_snapshot_json = JSON_SET(e.runtime_snapshot_json, '$.definition.edges', t.new_edges);
COMMIT;
DROP TEMPORARY TABLE tmp_examine_definition_edges;
DROP TEMPORARY TABLE tmp_examine_instance_edges;

-- 适用基线：旧模块记录仍将 status 默认设为 ACTIVE，且没有 check_status，保留未使用的 archived_time。
-- 最终目标：业务状态与审批状态分开保存；归档由各模块自定义状态表达，不保留全局归档时间。
-- 日常开发库已核对没有模块业务记录；有记录的独立库执行前须按最终状态逐条确定原 ACTIVE 的业务值。
ALTER TABLE un_module_record
  MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'NORMAL' COMMENT '模块业务状态编码，草稿正常和删除为系统预置状态',
  ADD COLUMN check_status INT NOT NULL DEFAULT 0 COMMENT '当前审批状态，0表示审批未提交' AFTER status,
  DROP COLUMN archived_time;

-- 适用基线：未保存运维备份元数据。
-- 用途：先建立旧开发库中已有的运维元数据表；下方一次性调整为最终 SQL 导出结构。
CREATE TABLE IF NOT EXISTS un_plat_ops_backup (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '运维备份主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台整库或系统配置作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统配置备份所属业务系统主键，平台整库时为空',
  backup_kind VARCHAR(30) NOT NULL COMMENT '平台整库及文件或系统配置备份类型',
  status VARCHAR(20) NOT NULL DEFAULT 'CREATING' COMMENT '创建中可用或失败状态',
  artifact_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '服务端随机备份文件键',
  size_bytes BIGINT DEFAULT NULL COMMENT '加密备份产物字节数',
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '加密备份产物摘要',
  error_text VARCHAR(300) DEFAULT NULL COMMENT '失败原因摘要，不记录密钥和连接串',
  create_account_id BIGINT NOT NULL COMMENT '发起备份的平台账号主键',
  restore_account_id BIGINT DEFAULT NULL COMMENT '最近执行恢复的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份发起时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '备份状态更新时间',
  restore_time DATETIME(3) DEFAULT NULL COMMENT '最近执行恢复时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_ops_backup_artifact (artifact_key),
  KEY ix_plat_ops_backup_scope (scope_type, system_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维备份元数据';

-- 适用基线：开发库已按上一版建立运维备份表，其中仅有旧加密备份测试记录。
-- 最终目标：只登记平台整库和单系统 SQL 导出，不再保留恢复字段。旧测试产物不兼容，须清理对应元数据与文件。
ALTER TABLE un_plat_ops_backup
  MODIFY COLUMN scope_type VARCHAR(20) NOT NULL COMMENT '平台整库或单个业务系统作用域',
  MODIFY COLUMN system_id BIGINT DEFAULT NULL COMMENT '系统备份所属业务系统主键，平台整库时为空',
  MODIFY COLUMN backup_kind VARCHAR(30) NOT NULL COMMENT '平台整库SQL或系统数据SQL备份类型',
  MODIFY COLUMN size_bytes BIGINT DEFAULT NULL COMMENT 'SQL备份文件字节数',
  MODIFY COLUMN sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'SQL备份文件SHA256摘要',
  MODIFY COLUMN error_text VARCHAR(300) DEFAULT NULL COMMENT '失败原因摘要，不记录数据库连接串',
  DROP COLUMN restore_account_id,
  DROP COLUMN restore_time;
