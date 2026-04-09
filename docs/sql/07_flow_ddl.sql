-- flow（流程/审批）模块表结构
-- 说明：
-- - 所有表统一使用 un_ 前缀
-- - 所有表包含：create_user_id / update_user_id / create_time / update_time
-- - 作用域隔离：system_id / tenant_id（无多租户时 tenant_id 固定 0）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS un_flow_task_actor;
DROP TABLE IF EXISTS un_flow_task;
DROP TABLE IF EXISTS un_flow_instance_variable;
DROP TABLE IF EXISTS un_flow_action_log;
DROP TABLE IF EXISTS un_flow_instance_trace;
DROP TABLE IF EXISTS un_flow_instance;

DROP TABLE IF EXISTS un_flow_biz_binding;

DROP TABLE IF EXISTS un_flow_form_template;
DROP TABLE IF EXISTS un_flow_node_template;
DROP TABLE IF EXISTS un_flow_definition_version;
DROP TABLE IF EXISTS un_flow_definition;

-- -----------------------------------------------------------------------------
-- 1) 流程模板（设计态）
-- -----------------------------------------------------------------------------
CREATE TABLE un_flow_definition (
  id               BIGINT       NOT NULL COMMENT '流程定义ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  def_code         VARCHAR(64)  NOT NULL COMMENT '流程编码（同 system 内唯一）',
  def_name         VARCHAR(255) NOT NULL COMMENT '流程名称',
  category_code    VARCHAR(64)  NULL COMMENT '分类编码（可选）',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  latest_version_no INT         NOT NULL DEFAULT 0 COMMENT '最新发布版本号（0=未发布）',
  remark           VARCHAR(512) NULL COMMENT '备注',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_def_code (system_id, tenant_id, def_code),
  KEY idx_flow_def_query (system_id, tenant_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义';

CREATE TABLE un_flow_definition_version (
  id             BIGINT       NOT NULL COMMENT '版本ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  def_id         BIGINT       NOT NULL COMMENT 'un_flow_definition.id',
  version_no     INT          NOT NULL COMMENT '版本号（递增）',
  publish_status TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布 3=已废弃',
  graph_json     JSON         NOT NULL COMMENT '流程图快照（节点/连线/配置）',
  form_json      JSON         NULL COMMENT '表单快照（可选，字段/布局）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_def_ver (def_id, version_no),
  KEY idx_flow_def_ver_query (system_id, tenant_id, def_id, publish_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程版本/发布记录';

CREATE TABLE un_flow_node_template (
  id             BIGINT       NOT NULL COMMENT '节点模板ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  template_code  VARCHAR(64)  NOT NULL COMMENT '模板编码（同 system 内唯一）',
  template_name  VARCHAR(255) NOT NULL COMMENT '模板名称',
  node_type      VARCHAR(32)  NOT NULL COMMENT '节点类型：start|approve|cc|condition|parallel|join|end|custom',
  config_json    JSON         NULL COMMENT '模板配置（参与人规则/表单权限/策略等）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node_tpl_code (system_id, tenant_id, template_code),
  KEY idx_flow_node_tpl_query (system_id, tenant_id, node_type, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点模板（可复用）';

CREATE TABLE un_flow_form_template (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表单模板（发起/审批）';

-- -----------------------------------------------------------------------------
-- 2) 与业务绑定（module/平台等）
-- -----------------------------------------------------------------------------
CREATE TABLE un_flow_biz_binding (
  id              BIGINT       NOT NULL COMMENT '绑定ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  biz_type         VARCHAR(64)  NOT NULL COMMENT '业务类型（如 module:model:customer）',
  trigger_action   VARCHAR(64)  NOT NULL COMMENT '触发动作（如 create/update/submit）',
  def_id           BIGINT       NOT NULL COMMENT '流程定义ID（un_flow_definition.id）',
  condition_json   JSON         NULL COMMENT '触发条件（可选）',
  mapping_json     JSON         NULL COMMENT '表单映射（可选）',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_biz_bind (system_id, tenant_id, biz_type, trigger_action),
  KEY idx_flow_biz_bind_def (def_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务与流程绑定';

-- -----------------------------------------------------------------------------
-- 3) 运行实例（运行态）
-- -----------------------------------------------------------------------------
CREATE TABLE un_flow_instance (
  id              BIGINT       NOT NULL COMMENT '实例ID',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  def_id           BIGINT       NOT NULL COMMENT '流程定义ID',
  def_version_no   INT          NOT NULL COMMENT '流程版本号（发布时的版本）',
  biz_type         VARCHAR(64)  NOT NULL COMMENT '业务类型',
  biz_id           VARCHAR(64)  NOT NULL COMMENT '业务ID（字符串以兼容多种ID）',
  title            VARCHAR(255) NULL COMMENT '实例标题（可选）',
  starter_plat_id  BIGINT       NULL COMMENT '发起人 platId',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=运行中 2=已结束 3=已撤回 4=已终止',
  current_node_id  VARCHAR(64)  NULL COMMENT '当前节点ID（流程图中的节点标识，可选）',
  start_time       DATETIME(3)  NOT NULL COMMENT '发起时间',
  end_time         DATETIME(3)  NULL COMMENT '结束时间',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId（一般同 starter_plat_id）',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_inst_biz (system_id, tenant_id, biz_type, biz_id),
  KEY idx_flow_inst_status (system_id, tenant_id, status, update_time),
  KEY idx_flow_inst_def (def_id, def_version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例';

CREATE TABLE un_flow_task (
  id              BIGINT       NOT NULL COMMENT '任务ID（待办/已办）',
  system_id        BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  instance_id      BIGINT       NOT NULL COMMENT '流程实例ID（un_flow_instance.id）',
  node_id          VARCHAR(64)  NOT NULL COMMENT '节点ID（流程图节点标识）',
  node_name        VARCHAR(255) NULL COMMENT '节点名称（冗余，可选）',
  task_type        VARCHAR(32)  NOT NULL DEFAULT 'approve' COMMENT '任务类型：approve|cc|custom',
  assignee_plat_id BIGINT       NULL COMMENT '处理人 platId（或签时为具体处理人）',
  candidate_json   JSON         NULL COMMENT '候选人/候选规则（会签/或签时可用）',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=待处理 2=已同意 3=已拒绝 4=已转交 5=已取消/跳过',
  due_time         DATETIME(3)  NULL COMMENT '到期时间（可选）',
  claim_time       DATETIME(3)  NULL COMMENT '领取时间（可选）',
  finish_time      DATETIME(3)  NULL COMMENT '完成时间（可选）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_task_inst (instance_id, status, update_time),
  KEY idx_flow_task_assignee (assignee_plat_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务（待办/已办）';

CREATE TABLE un_flow_task_actor (
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
  KEY idx_flow_task_actor_task (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务参与人（会签/候选/抄送）';

CREATE TABLE un_flow_instance_variable (
  id             BIGINT       NOT NULL COMMENT '变量ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  instance_id    BIGINT       NOT NULL COMMENT '实例ID（un_flow_instance.id）',
  var_key        VARCHAR(128) NOT NULL COMMENT '变量键',
  var_type       VARCHAR(32)  NOT NULL DEFAULT 'string' COMMENT '变量类型：string|number|bool|json',
  var_value      MEDIUMTEXT   NULL COMMENT '变量值（字符串/JSON）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_var (instance_id, var_key),
  KEY idx_flow_var_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实例变量/上下文';

-- -----------------------------------------------------------------------------
-- 4) 审批记录与轨迹
-- -----------------------------------------------------------------------------
CREATE TABLE un_flow_action_log (
  id             BIGINT       NOT NULL COMMENT '动作日志ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  instance_id    BIGINT       NOT NULL COMMENT '实例ID',
  task_id        BIGINT       NULL COMMENT '任务ID（可为空，如系统动作）',
  node_id        VARCHAR(64)  NULL COMMENT '节点ID（可选）',
  action         VARCHAR(32)  NOT NULL COMMENT '动作：start|approve|reject|transfer|add_sign|withdraw|terminate|cc',
  actor_plat_id  BIGINT       NULL COMMENT '执行人 platId',
  comment_text   VARCHAR(1024) NULL COMMENT '审批意见/备注',
  attachment_json JSON        NULL COMMENT '附件引用（JSON：file_id 列表，使用 upload 模块）',
  extra_json     JSON         NULL COMMENT '扩展信息（JSON）',
  action_time    DATETIME(3)  NOT NULL COMMENT '动作时间',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_action_inst (instance_id, action_time),
  KEY idx_flow_action_actor (actor_plat_id, action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批动作日志';

CREATE TABLE un_flow_instance_trace (
  id             BIGINT       NOT NULL COMMENT '轨迹ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  instance_id    BIGINT       NOT NULL COMMENT '实例ID',
  event_type     VARCHAR(32)  NOT NULL COMMENT '事件类型：enter|leave|branch|join|end|error',
  from_node_id   VARCHAR(64)  NULL COMMENT '来源节点ID（可选）',
  to_node_id     VARCHAR(64)  NULL COMMENT '目标节点ID（可选）',
  detail_json    JSON         NULL COMMENT '详情（JSON）',
  event_time     DATETIME(3)  NOT NULL COMMENT '事件时间',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_flow_trace_inst (instance_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实例流转轨迹';

SET FOREIGN_KEY_CHECKS = 1;

