-- Baseline: flow DDL (converted from docs/sql/07_flow_ddl.sql)
-- Note: migration should be safe; avoid DROP statements.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS un_flow_temp (
  id               BIGINT       NOT NULL COMMENT '流程定义ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  temp_code        VARCHAR(64)  NOT NULL COMMENT '模板编码（同 system 内唯一）',
  temp_name        VARCHAR(255) NOT NULL COMMENT '模板名称',
  category_code    VARCHAR(64)  NULL COMMENT '分类编码（可选）',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  latest_ver_no    INT         NOT NULL DEFAULT 0 COMMENT '最新发布版本号（0=未发布）',
  remark           VARCHAR(512) NULL COMMENT '备注',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_temp_code (system_id, tenant_id, temp_code),
  KEY idx_flow_temp_query (system_id, tenant_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板（temp）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver (
  id             BIGINT       NOT NULL COMMENT '版本ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  temp_id        BIGINT       NOT NULL COMMENT 'un_flow_temp.id',
  ver_no         INT          NOT NULL COMMENT '版本号（递增）',
  publish_status TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布 3=已废弃',
  graph_json     JSON         NOT NULL COMMENT '流程图快照（节点/连线/配置）',
  form_json      JSON         NULL COMMENT '表单快照（可选）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_temp_ver (temp_id, ver_no),
  KEY idx_flow_temp_ver_query (system_id, tenant_id, temp_id, publish_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板版本（temp_ver）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver_node (
  id              BIGINT       NOT NULL COMMENT '版本节点ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  temp_ver_id      BIGINT       NOT NULL COMMENT 'un_flow_temp_ver.id',
  node_key         VARCHAR(64)  NOT NULL COMMENT '节点标识（同版本内唯一）',
  parent_node_key  VARCHAR(64)  NULL COMMENT '父节点标识（group 分组）',
  node_type        VARCHAR(32)  NOT NULL COMMENT '节点类型（可扩展）',
  node_name        VARCHAR(255) NULL COMMENT '节点名称（可选）',
  sort_no          INT          NOT NULL DEFAULT 0 COMMENT '编辑器排序',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  config_json      JSON         NULL COMMENT '节点配置（可选）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_temp_ver_node_key (temp_ver_id, node_key),
  KEY idx_temp_ver_node_query (system_id, tenant_id, temp_ver_id, node_type, status, update_time),
  KEY idx_temp_ver_node_parent (temp_ver_id, parent_node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='temp_ver-节点（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver_line (
  id              BIGINT       NOT NULL COMMENT '版本边ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  temp_ver_id      BIGINT       NOT NULL COMMENT 'un_flow_temp_ver.id',
  from_node_key    VARCHAR(64)  NOT NULL COMMENT '起点 node_key',
  to_node_key      VARCHAR(64)  NOT NULL COMMENT '终点 node_key',
  priority         INT          NOT NULL DEFAULT 0 COMMENT '优先级（越小越优先）',
  is_default       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认边',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=失效',
  remark           VARCHAR(255) NULL COMMENT '备注（可选）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_temp_ver_line_from (temp_ver_id, from_node_key, status, priority),
  KEY idx_temp_ver_line_to (temp_ver_id, to_node_key, status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='temp_ver-连线（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver_line_cond (
  id              BIGINT       NOT NULL COMMENT '版本边条件ID',
  line_id          BIGINT       NOT NULL COMMENT 'un_flow_temp_ver_line.id',
  group_no         INT          NOT NULL DEFAULT 0 COMMENT '分组号',
  logic_op         VARCHAR(8)   NOT NULL DEFAULT 'AND' COMMENT '逻辑：AND|OR',
  left_var         VARCHAR(128) NOT NULL COMMENT '变量名',
  cmp_op           VARCHAR(16)  NOT NULL COMMENT '比较符：EQ|NE|GT|GE|LT|LE|IN|EXISTS',
  right_type       VARCHAR(16)  NOT NULL DEFAULT 'string' COMMENT '右值类型',
  right_value      MEDIUMTEXT   NULL COMMENT '右值',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=失效',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_temp_ver_line_cond (line_id, status, group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='temp_ver-连线条件（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_record_node (
  id                 BIGINT       NOT NULL COMMENT '实例节点ID',
  system_id           BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id           BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id         BIGINT       NOT NULL COMMENT 'un_flow_record.id',
  node_key            VARCHAR(64)  NOT NULL COMMENT '节点标识（同实例内唯一）',
  parent_node_key     VARCHAR(64)  NULL COMMENT '父节点标识（group 分组）',
  node_type           VARCHAR(32)  NOT NULL COMMENT '节点类型（可扩展）',
  node_name           VARCHAR(255) NULL COMMENT '节点名称（可选）',
  sort_no             INT          NOT NULL DEFAULT 0 COMMENT '编辑器排序',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=作废',
  source_temp_ver_id  BIGINT       NULL COMMENT '来源 temp_ver_id（追溯）',
  source_def_node_id  BIGINT       NULL COMMENT '来源 def_ver_node.id（追溯）',
  config_json         JSON         NULL COMMENT '节点配置（可选）',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inst_node_key (record_id, node_key),
  KEY idx_inst_node_query (system_id, tenant_id, record_id, node_type, status, update_time),
  KEY idx_inst_node_parent (record_id, parent_node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record-节点（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_record_line (
  id                 BIGINT       NOT NULL COMMENT '实例边ID',
  system_id           BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id           BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id         BIGINT       NOT NULL COMMENT 'un_flow_record.id',
  from_node_key       VARCHAR(64)  NOT NULL COMMENT '起点 node_key',
  to_node_key         VARCHAR(64)  NOT NULL COMMENT '终点 node_key',
  priority            INT          NOT NULL DEFAULT 0 COMMENT '优先级（越小越优先）',
  is_default          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认边',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=作废',
  source_temp_ver_id  BIGINT       NULL COMMENT '来源 temp_ver_id（追溯）',
  source_def_edge_id  BIGINT       NULL COMMENT '来源 def_ver_edge.id（追溯）',
  remark              VARCHAR(255) NULL COMMENT '备注（可选）',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_inst_edge_from (record_id, from_node_key, status, priority),
  KEY idx_inst_edge_to (record_id, to_node_key, status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record-连线（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_record_line_cond (
  id                 BIGINT       NOT NULL COMMENT '实例边条件ID',
  record_id         BIGINT       NOT NULL COMMENT 'un_flow_record.id',
  line_id             BIGINT       NOT NULL COMMENT 'un_flow_record_line.id',
  group_no            INT          NOT NULL DEFAULT 0 COMMENT '分组号',
  logic_op            VARCHAR(8)   NOT NULL DEFAULT 'AND' COMMENT '逻辑：AND|OR',
  left_var            VARCHAR(128) NOT NULL COMMENT '变量名',
  cmp_op              VARCHAR(16)  NOT NULL COMMENT '比较符：EQ|NE|GT|GE|LT|LE|IN|EXISTS',
  right_type          VARCHAR(16)  NOT NULL DEFAULT 'string' COMMENT '右值类型',
  right_value         MEDIUMTEXT   NULL COMMENT '右值',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=作废',
  source_temp_ver_id  BIGINT       NULL COMMENT '来源 temp_ver_id（追溯）',
  source_def_cond_id  BIGINT       NULL COMMENT '来源 def_ver_edge_cond.id（追溯）',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_record_line_cond (line_id, status, group_no),
  KEY idx_record_line_cond_record (record_id, status, line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record-连线条件（关系表版）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver_setting (
  id                  BIGINT       NOT NULL COMMENT '版本策略ID',
  system_id            BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id            BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  temp_ver_id          BIGINT       NOT NULL COMMENT 'un_flow_temp_ver.id',
  exception_mode       VARCHAR(32)  NOT NULL DEFAULT 'fallback_admin' COMMENT '异常模式：fallback_admin|end_record',
  exception_admin_plat_id BIGINT    NULL COMMENT '异常兜底审批人 platId',
  exception_end_reason VARCHAR(255) NULL COMMENT '异常直接结束原因（可选）',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=失效',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_temp_ver_setting (temp_ver_id),
  KEY idx_temp_ver_setting_query (system_id, tenant_id, temp_ver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='temp_ver-全局设置（异常兜底等）';

CREATE TABLE IF NOT EXISTS un_flow_temp_ver_node_setting (
  id                  BIGINT       NOT NULL COMMENT '版本节点策略ID',
  system_id            BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id            BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  temp_ver_id          BIGINT       NOT NULL COMMENT 'un_flow_temp_ver.id',
  node_key             VARCHAR(64)  NOT NULL COMMENT '节点标识（同版本内）',
  exception_mode       VARCHAR(32)  NOT NULL DEFAULT 'fallback_admin' COMMENT '异常模式：fallback_admin|end_record',
  exception_admin_plat_id BIGINT    NULL COMMENT '异常兜底审批人 platId（可选，覆盖全局）',
  exception_end_reason VARCHAR(255) NULL COMMENT '异常直接结束原因（可选）',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=失效',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_temp_ver_node_setting (temp_ver_id, node_key),
  KEY idx_temp_ver_node_setting_query (system_id, tenant_id, temp_ver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='temp_ver-节点设置（异常兜底覆盖）';

CREATE TABLE IF NOT EXISTS un_flow_record_setting (
  id                  BIGINT       NOT NULL COMMENT '实例策略ID',
  system_id            BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id            BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id          BIGINT       NOT NULL COMMENT 'un_flow_record.id',
  source_temp_ver_id   BIGINT       NULL COMMENT '来源 temp_ver_id（追溯）',
  exception_mode       VARCHAR(32)  NOT NULL DEFAULT 'fallback_admin' COMMENT '异常模式：fallback_admin|end_record',
  exception_admin_plat_id BIGINT    NULL COMMENT '异常兜底审批人 platId',
  exception_end_reason VARCHAR(255) NULL COMMENT '异常直接结束原因（可选）',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=作废',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_record_setting (record_id),
  KEY idx_record_setting_query (system_id, tenant_id, record_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record-全局设置（可覆盖模板）';

CREATE TABLE IF NOT EXISTS un_flow_record_node_setting (
  id                  BIGINT       NOT NULL COMMENT '实例节点策略ID',
  system_id            BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id            BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id          BIGINT       NOT NULL COMMENT 'un_flow_record.id',
  node_key             VARCHAR(64)  NOT NULL COMMENT '节点标识（同实例内）',
  exception_mode       VARCHAR(32)  NOT NULL DEFAULT 'fallback_admin' COMMENT '异常模式：fallback_admin|end_record',
  exception_admin_plat_id BIGINT    NULL COMMENT '异常兜底审批人 platId（可选）',
  exception_end_reason VARCHAR(255) NULL COMMENT '异常直接结束原因（可选）',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=作废',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_record_node_setting (record_id, node_key),
  KEY idx_record_node_setting_query (system_id, tenant_id, record_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record-节点设置（可覆盖模板/实例）';

CREATE TABLE IF NOT EXISTS un_flow_node_temp (
  id             BIGINT       NOT NULL COMMENT '节点模板ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  template_code  VARCHAR(64)  NOT NULL COMMENT '模板编码（同 system 内唯一）',
  template_name  VARCHAR(255) NOT NULL COMMENT '模板名称',
  node_type      VARCHAR(32)  NOT NULL COMMENT '节点类型',
  config_json    JSON         NULL COMMENT '模板配置（参与人规则/表单权限/策略等）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node_tpl_code (system_id, tenant_id, template_code),
  KEY idx_flow_node_tpl_query (system_id, tenant_id, node_type, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点模板（node_temp）';

CREATE TABLE IF NOT EXISTS un_flow_form_temp (
  id             BIGINT       NOT NULL COMMENT '表单模板ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  template_code  VARCHAR(64)  NOT NULL COMMENT '模板编码（同 system 内唯一）',
  template_name  VARCHAR(255) NOT NULL COMMENT '模板名称',
  form_json      JSON         NOT NULL COMMENT '表单定义（字段/布局/校验/权限）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_form_tpl_code (system_id, tenant_id, template_code),
  KEY idx_flow_form_tpl_query (system_id, tenant_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单模板（form_temp）';

CREATE TABLE IF NOT EXISTS un_flow_binding (
  id              BIGINT       NOT NULL COMMENT '绑定ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  biz_type         VARCHAR(64)  NOT NULL COMMENT '业务类型（如 module:model:customer）',
  trigger_action   VARCHAR(64)  NOT NULL COMMENT '触发动作（如 create/update/submit）',
  temp_id          BIGINT       NOT NULL COMMENT '流程模板ID（un_flow_temp.id）',
  condition_json   JSON         NULL COMMENT '触发条件（可选）',
  mapping_json     JSON         NULL COMMENT '表单映射（可选）',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_biz_bind (system_id, tenant_id, biz_type, trigger_action),
  KEY idx_flow_binding_temp (temp_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务与流程绑定（binding）';

CREATE TABLE IF NOT EXISTS un_flow_record (
  id              BIGINT       NOT NULL COMMENT '实例ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  root_record_id BIGINT       NULL COMMENT '根实例ID',
  parent_record_id BIGINT     NULL COMMENT '父实例ID',
  parent_node_key VARCHAR(64)  NULL COMMENT '父 record 中触发 subflow 的节点 node_key',
  parent_task_id  BIGINT       NULL COMMENT '父实例中触发 subflow 的任务ID（可选）',
  temp_id          BIGINT       NOT NULL COMMENT '流程模板ID（un_flow_temp.id）',
  temp_ver_no      INT          NOT NULL COMMENT '流程模板版本号（发布时的版本）',
  graph_json       JSON         NOT NULL COMMENT '运行时流程图快照',
  form_json        JSON         NULL COMMENT '运行时表单快照（可选）',
  biz_type         VARCHAR(64)  NOT NULL COMMENT '业务类型',
  biz_id           VARCHAR(64)  NOT NULL COMMENT '业务ID',
  title            VARCHAR(255) NULL COMMENT '实例标题（可选）',
  starter_plat_id  BIGINT       NULL COMMENT '发起人 platId',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=运行中 2=已结束 3=已撤回 4=已终止',
  current_node_key VARCHAR(64)  NULL COMMENT '当前节点 node_key（可选）',
  start_time       DATETIME(3)  NOT NULL COMMENT '发起时间',
  end_time         DATETIME(3)  NULL COMMENT '结束时间',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_inst_root (system_id, tenant_id, root_record_id, update_time),
  KEY idx_flow_inst_parent (system_id, tenant_id, parent_record_id, update_time),
  KEY idx_flow_inst_biz (system_id, tenant_id, biz_type, biz_id),
  KEY idx_flow_inst_status (system_id, tenant_id, status, update_time),
  KEY idx_flow_record_temp (temp_id, temp_ver_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例记录（record）';

CREATE TABLE IF NOT EXISTS un_flow_task (
  id              BIGINT       NOT NULL COMMENT '任务ID（待办/已办）',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id      BIGINT       NOT NULL COMMENT '流程实例记录ID（un_flow_record.id）',
  node_key         VARCHAR(64)  NOT NULL COMMENT '节点 node_key',
  node_name        VARCHAR(255) NULL COMMENT '节点名称（冗余，可选）',
  task_type        VARCHAR(32)  NOT NULL DEFAULT 'approve' COMMENT '任务类型：approve|cc|custom',
  assignee_plat_id BIGINT       NULL COMMENT '处理人 platId',
  candidate_json   JSON         NULL COMMENT '候选人/候选规则',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=待处理 2=已同意 3=已拒绝 4=已转交 5=已取消/跳过',
  due_time         DATETIME(3)  NULL COMMENT '到期时间（可选）',
  claim_time       DATETIME(3)  NULL COMMENT '领取时间（可选）',
  finish_time      DATETIME(3)  NULL COMMENT '完成时间（可选）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_task_record (system_id, tenant_id, record_id, status, update_time),
  KEY idx_flow_task_assignee (system_id, tenant_id, assignee_plat_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务（待办/已办）';

CREATE TABLE IF NOT EXISTS un_flow_task_actor (
  id             BIGINT       NOT NULL COMMENT '参与人ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  task_id        BIGINT       NOT NULL COMMENT '任务ID（un_flow_task.id）',
  actor_plat_id  BIGINT       NOT NULL COMMENT '参与人 platId',
  actor_role     VARCHAR(32)  NULL COMMENT '参与角色：assignee|candidate|cc|watcher',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=有效 2=失效',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_task_actor (task_id, actor_plat_id),
  KEY idx_flow_task_actor_task (system_id, tenant_id, task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务参与人';

CREATE TABLE IF NOT EXISTS un_flow_record_var (
  id             BIGINT       NOT NULL COMMENT '变量ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id    BIGINT       NOT NULL COMMENT '实例记录ID（un_flow_record.id）',
  var_key        VARCHAR(128) NOT NULL COMMENT '变量键',
  var_type       VARCHAR(32)  NOT NULL DEFAULT 'string' COMMENT '变量类型：string|number|bool|json',
  var_value      MEDIUMTEXT   NULL COMMENT '变量值（字符串/JSON）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_var (record_id, var_key),
  KEY idx_flow_var_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='record 变量/上下文';

CREATE TABLE IF NOT EXISTS un_flow_log_action (
  id             BIGINT       NOT NULL COMMENT '动作日志ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id    BIGINT       NOT NULL COMMENT 'record ID（un_flow_record.id）',
  task_id        BIGINT       NULL COMMENT '任务ID（可为空，如系统动作）',
  node_key       VARCHAR(64)  NULL COMMENT '节点 node_key（可选）',
  action         VARCHAR(32)  NOT NULL COMMENT '动作',
  actor_plat_id  BIGINT       NULL COMMENT '执行人 platId',
  comment_text   VARCHAR(1024) NULL COMMENT '审批意见/备注',
  attachment_json JSON        NULL COMMENT '附件引用（JSON：file_id 列表）',
  extra_json     JSON         NULL COMMENT '扩展信息（JSON）',
  action_time    DATETIME(3)  NOT NULL COMMENT '动作时间',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_log_action_record (record_id, action_time),
  KEY idx_flow_action_actor (actor_plat_id, action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作日志（log_action）';

CREATE TABLE IF NOT EXISTS un_flow_log_trace (
  id             BIGINT       NOT NULL COMMENT '轨迹ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  record_id    BIGINT       NOT NULL COMMENT 'record ID（un_flow_record.id）',
  event_type     VARCHAR(32)  NOT NULL COMMENT '事件类型：enter|leave|branch|join|end|error',
  from_node_key  VARCHAR(64)  NULL COMMENT '来源节点 node_key（可选）',
  to_node_key    VARCHAR(64)  NULL COMMENT '目标节点 node_key（可选）',
  detail_json    JSON         NULL COMMENT '详情（JSON）',
  event_time     DATETIME(3)  NOT NULL COMMENT '事件时间',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_log_trace_record (record_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流转轨迹日志（log_trace）';

SET FOREIGN_KEY_CHECKS = 1;

