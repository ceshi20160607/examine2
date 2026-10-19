-- 通用流程与审批的基础结构。旧审批 SQL 仅用于理解字段含义，本脚本不迁移旧数据。
-- 本文件在全新 unexamine 库执行；已投产后的字段调整应编写增量迁移，不删除业务表。
USE `unexamine`;

-- 流程定义保留可编辑草稿；发布后的结构另存于 un_flow_version。
CREATE TABLE IF NOT EXISTS `un_flow` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程定义主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `name` VARCHAR(100) NOT NULL COMMENT '流程名称',
    `description` VARCHAR(500) NULL COMMENT '流程用途说明',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '定义状态：DRAFT 草稿，ENABLED 启用，DISABLED 停用',
    `current_version_id` BIGINT UNSIGNED NULL COMMENT '当前发布版本主键',
    `create_member_id` BIGINT UNSIGNED NOT NULL COMMENT '创建成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义';

CREATE TABLE IF NOT EXISTS `un_flow_node` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程草稿节点主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程定义主键',
    `node_key` VARCHAR(64) NOT NULL COMMENT '流程内稳定节点标识',
    `name` VARCHAR(100) NOT NULL COMMENT '节点名称',
    `node_type` VARCHAR(30) NOT NULL COMMENT '节点类型：START 开始，TASK 人工任务，CONDITION 条件，END 结束',
    `assignee_type` VARCHAR(30) NULL COMMENT '人工任务处理人来源：MEMBER 成员，ROLE 角色，STARTER 发起人',
    `assignee_id` BIGINT UNSIGNED NULL COMMENT '固定成员或角色主键，依处理人来源解释',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '草稿展示顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_node_key` (`flow_id`, `node_key`),
    KEY `idx_flow_node_sort` (`flow_id`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程草稿节点';

CREATE TABLE IF NOT EXISTS `un_flow_edge` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程草稿连线主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程定义主键',
    `source_node_key` VARCHAR(64) NOT NULL COMMENT '起始节点标识',
    `target_node_key` VARCHAR(64) NOT NULL COMMENT '目标节点标识',
    `condition_rule` VARCHAR(1000) NULL COMMENT '连线命中条件，空值表示默认连线',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '同一节点多条连线的匹配顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_edge_nodes` (`flow_id`, `source_node_key`, `target_node_key`),
    KEY `idx_flow_edge_source_sort` (`flow_id`, `source_node_key`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程草稿连线';

-- 发布版本只追加，不允许编辑既有版本；快照包含发布时的节点和连线。
CREATE TABLE IF NOT EXISTS `un_flow_version` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程发布版本主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程定义主键',
    `version_no` INT NOT NULL COMMENT '流程内递增版本号',
    `definition_snapshot` JSON NOT NULL COMMENT '发布时完整节点与连线快照',
    `publish_member_id` BIGINT UNSIGNED NOT NULL COMMENT '发布成员主键',
    `publish_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_version_no` (`flow_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变流程发布版本';

CREATE TABLE IF NOT EXISTS `un_flow_binding` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程绑定主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '触发流程的业务模块主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定的流程定义主键',
    `version_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定的已发布版本主键',
    `trigger_event` VARCHAR(30) NOT NULL COMMENT '触发事件，如 CREATE 或 UPDATE',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '绑定状态：ENABLED 启用，DISABLED 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_binding_event` (`tenant_id`, `module_id`, `trigger_event`, `flow_id`),
    KEY `idx_flow_binding_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程绑定';

-- 运行实例再次固化版本快照，后续重新发布或改绑定均不影响已发起流程。
CREATE TABLE IF NOT EXISTS `un_flow_example` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程实例主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '流程定义主键',
    `version_id` BIGINT UNSIGNED NOT NULL COMMENT '发起时的发布版本主键',
    `binding_id` BIGINT UNSIGNED NULL COMMENT '触发本实例的流程绑定主键',
    `module_id` BIGINT UNSIGNED NULL COMMENT '关联业务模块主键',
    `record_id` BIGINT UNSIGNED NULL COMMENT '关联业务记录主键',
    `starter_member_id` BIGINT UNSIGNED NOT NULL COMMENT '流程发起成员主键',
    `definition_snapshot` JSON NOT NULL COMMENT '实例发起时的流程定义快照',
    `current_node_key` VARCHAR(64) NULL COMMENT '当前执行节点标识',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '实例状态：RUNNING 运行中，COMPLETED 已完成，REJECTED 已拒绝，WITHDRAWN 已撤回，FAILED 异常',
    `start_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程发起时间',
    `finish_time` DATETIME(3) NULL COMMENT '流程结束时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_example_business` (`tenant_id`, `module_id`, `record_id`),
    KEY `idx_flow_example_starter` (`tenant_id`, `starter_member_id`, `start_time`),
    KEY `idx_flow_example_status` (`tenant_id`, `status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行流程实例';

CREATE TABLE IF NOT EXISTS `un_flow_task` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程待办任务主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程实例主键',
    `node_key` VARCHAR(64) NOT NULL COMMENT '执行节点标识',
    `round_no` INT NOT NULL DEFAULT 1 COMMENT '同一节点的执行轮次',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING 待处理，COMPLETED 已处理，CANCELLED 已取消，EXPIRED 已超时',
    `claim_member_id` BIGINT UNSIGNED NULL COMMENT '认领该任务的成员主键',
    `assign_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务分配时间',
    `claim_time` DATETIME(3) NULL COMMENT '任务认领时间',
    `due_time` DATETIME(3) NULL COMMENT '任务处理期限',
    `finish_time` DATETIME(3) NULL COMMENT '任务完成或关闭时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_task_round` (`example_id`, `node_key`, `round_no`),
    KEY `idx_flow_task_status` (`status`, `due_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行轮次的待办任务';

CREATE TABLE IF NOT EXISTS `un_flow_task_candidate` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程任务候选人主键',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属待办任务主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '候选处理成员主键',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '候选状态：PENDING 可处理，ACTED 已处理，CANCELLED 已取消',
    `action_time` DATETIME(3) NULL COMMENT '候选人实际处理时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_task_candidate` (`task_id`, `member_id`),
    KEY `idx_flow_candidate_inbox` (`member_id`, `status`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办任务候选处理人';

CREATE TABLE IF NOT EXISTS `un_flow_action` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程操作主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程实例主键',
    `task_id` BIGINT UNSIGNED NULL COMMENT '操作关联的待办任务主键',
    `actor_member_id` BIGINT UNSIGNED NOT NULL COMMENT '操作成员主键',
    `action_type` VARCHAR(30) NOT NULL COMMENT '操作类型，如 APPROVE、REJECT、WITHDRAW',
    `request_key` VARCHAR(64) NOT NULL COMMENT '客户端提交的幂等请求标识',
    `result_status` VARCHAR(20) NOT NULL COMMENT '操作结果：SUCCESS 成功，FAILED 失败',
    `remark` VARCHAR(1000) NULL COMMENT '操作意见或失败原因',
    `action_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_action_request` (`tenant_id`, `request_key`),
    KEY `idx_flow_action_history` (`example_id`, `action_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程操作及幂等记录';

CREATE TABLE IF NOT EXISTS `un_flow_exception` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程异常主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程实例主键',
    `task_id` BIGINT UNSIGNED NULL COMMENT '异常关联的待办任务主键',
    `exception_type` VARCHAR(40) NOT NULL COMMENT '异常类型，如 NO_ASSIGNEE、RULE_ERROR、JOB_ERROR',
    `detail` VARCHAR(1000) NOT NULL COMMENT '异常说明',
    `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处置状态：OPEN 待处理，RESOLVED 已解决',
    `handle_member_id` BIGINT UNSIGNED NULL COMMENT '处置成员主键',
    `handle_remark` VARCHAR(1000) NULL COMMENT '处置说明',
    `occur_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '异常发生时间',
    `handle_time` DATETIME(3) NULL COMMENT '异常完成处置时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_exception_example` (`example_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程异常与处置';

-- 事件历史只追加，用于还原每次状态变化，不依赖可更新的实例状态字段。
CREATE TABLE IF NOT EXISTS `un_flow_event` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程事件主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程实例主键',
    `task_id` BIGINT UNSIGNED NULL COMMENT '事件关联的待办任务主键',
    `action_id` BIGINT UNSIGNED NULL COMMENT '触发事件的操作主键',
    `event_type` VARCHAR(40) NOT NULL COMMENT '事件类型，如 STARTED、TASK_CREATED、TASK_COMPLETED、FINISHED',
    `actor_member_id` BIGINT UNSIGNED NULL COMMENT '事件发起成员主键，系统事件可为空',
    `description` VARCHAR(1000) NULL COMMENT '事件内容说明',
    `event_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_event_history` (`example_id`, `event_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变流程事件历史';

CREATE TABLE IF NOT EXISTS `un_flow_job` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流程定时任务主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属流程实例主键',
    `task_id` BIGINT UNSIGNED NULL COMMENT '定时任务关联的待办任务主键',
    `job_type` VARCHAR(30) NOT NULL COMMENT '任务类型，如 TIMEOUT、REMIND',
    `execute_time` DATETIME(3) NOT NULL COMMENT '计划执行时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态：PENDING 待执行，DONE 已执行，FAILED 失败，CANCELLED 已取消',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `last_error` VARCHAR(1000) NULL COMMENT '最近一次失败原因',
    `finish_time` DATETIME(3) NULL COMMENT '任务完成或取消时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_flow_job_due` (`status`, `execute_time`),
    KEY `idx_flow_job_example` (`example_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定时任务';

CREATE TABLE IF NOT EXISTS `un_examine_group` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批分组主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '分组展示顺序',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '分组状态：ENABLED 启用，DISABLED 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_group_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批分组';

CREATE TABLE IF NOT EXISTS `un_examine` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批定义主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `group_id` BIGINT UNSIGNED NULL COMMENT '所属审批分组主键',
    `name` VARCHAR(100) NOT NULL COMMENT '审批名称',
    `icon` VARCHAR(100) NULL COMMENT '审批展示图标',
    `description` VARCHAR(500) NULL COMMENT '审批用途说明',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '分组内展示顺序',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '定义状态：DRAFT 草稿，ENABLED 启用，DISABLED 停用',
    `create_member_id` BIGINT UNSIGNED NOT NULL COMMENT '创建成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_examine_tenant_group` (`tenant_id`, `group_id`, `status`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批定义';

CREATE TABLE IF NOT EXISTS `un_examine_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批高级配置主键',
    `examine_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批定义主键',
    `apply_scope` VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '可发起范围：ALL 全租户，SPECIFIED 指定对象',
    `allow_withdraw` TINYINT NOT NULL DEFAULT 1 COMMENT '处理中是否允许发起人撤回：1 允许，0 不允许',
    `restart_mode` VARCHAR(20) NOT NULL DEFAULT 'FIRST' COMMENT '重新发起方式：FIRST 从首节点开始，REJECTED 从拒绝节点开始',
    `timeout_minutes` INT NULL COMMENT '审批超时时长，空值表示不启用超时',
    `timeout_action` VARCHAR(20) NULL COMMENT '超时动作：REMIND 提醒，PASS 自动通过，TRANSFER 转交',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_config_definition` (`examine_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批高级配置';

CREATE TABLE IF NOT EXISTS `un_examine_exception` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批异常主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例主键',
    `example_node_id` BIGINT UNSIGNED NULL COMMENT '异常关联的实例节点主键',
    `exception_type` VARCHAR(40) NOT NULL COMMENT '异常类型，如 NO_APPROVER、RULE_ERROR、TIMEOUT_ERROR',
    `detail` VARCHAR(1000) NOT NULL COMMENT '异常说明',
    `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处置状态：OPEN 待处理，RESOLVED 已解决',
    `handle_member_id` BIGINT UNSIGNED NULL COMMENT '处置成员主键',
    `handle_remark` VARCHAR(1000) NULL COMMENT '处置说明',
    `occur_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '异常发生时间',
    `handle_time` DATETIME(3) NULL COMMENT '异常完成处置时间',
    PRIMARY KEY (`id`),
    KEY `idx_examine_exception_example` (`example_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批异常处理';

CREATE TABLE IF NOT EXISTS `un_examine_config_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批适用对象主键',
    `examine_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批定义主键',
    `target_type` VARCHAR(20) NOT NULL COMMENT '适用对象类型：MEMBER 成员，DEPARTMENT 部门',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '适用成员或部门主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_config_user_target` (`examine_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批配置适用人员与部门';

-- 审批使用顺序节点和节点条件，基础场景无需引入复杂连线图。
CREATE TABLE IF NOT EXISTS `un_examine_node` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批节点主键',
    `examine_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批定义主键',
    `node_key` VARCHAR(64) NOT NULL COMMENT '审批定义内稳定节点标识',
    `name` VARCHAR(100) NOT NULL COMMENT '节点名称',
    `node_type` VARCHAR(20) NOT NULL DEFAULT 'APPROVE' COMMENT '节点类型：APPROVE 审批，CONDITION 条件，COPY 抄送',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '节点执行顺序',
    `condition_rule` VARCHAR(1000) NULL COMMENT '进入节点的业务字段条件，空值表示直接进入',
    `approver_type` VARCHAR(30) NULL COMMENT '审批人来源：MEMBER 固定成员，DEPARTMENT 部门，ROLE 角色，SUPERIOR 上级，STARTER_CHOICE 发起人自选',
    `pass_mode` VARCHAR(20) NULL COMMENT '多人通过方式：ANY 任一人，ALL 全部，SEQUENTIAL 依次',
    `fallback_member_id` BIGINT UNSIGNED NULL COMMENT '无法解析上级时的兜底审批成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_node_key` (`examine_id`, `node_key`),
    KEY `idx_examine_node_sort` (`examine_id`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点';

CREATE TABLE IF NOT EXISTS `un_examine_node_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批节点字段配置主键',
    `node_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批节点主键',
    `field_id` BIGINT UNSIGNED NOT NULL COMMENT '关联模块字段主键',
    `is_visible` TINYINT NOT NULL DEFAULT 1 COMMENT '字段在本节点是否可见：1 可见，0 隐藏',
    `is_editable` TINYINT NOT NULL DEFAULT 0 COMMENT '字段在本节点是否可编辑：1 可编辑，0 只读',
    `is_required` TINYINT NOT NULL DEFAULT 0 COMMENT '本节点编辑时是否必填：1 必填，0 非必填',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '本节点内字段展示顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_node_field` (`node_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点字段配置';

CREATE TABLE IF NOT EXISTS `un_examine_node_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批节点处理对象主键',
    `node_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批节点主键',
    `target_type` VARCHAR(20) NOT NULL COMMENT '处理对象类型：MEMBER 成员，DEPARTMENT 部门，ROLE 角色',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '处理成员、部门或角色主键',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '依次审批时的处理顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_node_user_target` (`node_id`, `target_type`, `target_id`),
    KEY `idx_examine_node_user_sort` (`node_id`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点处理人员配置';

-- 审批实例冻结发起时的节点、字段权限和人员配置，后续定义修改不回写历史。
CREATE TABLE IF NOT EXISTS `un_examine_example` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批实例主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `examine_id` BIGINT UNSIGNED NOT NULL COMMENT '审批定义主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '关联业务模块主键',
    `record_id` BIGINT UNSIGNED NOT NULL COMMENT '关联业务记录主键',
    `applicant_member_id` BIGINT UNSIGNED NOT NULL COMMENT '审批发起成员主键',
    `definition_snapshot` JSON NOT NULL COMMENT '发起时审批配置、节点和字段权限快照',
    `current_node_key` VARCHAR(64) NULL COMMENT '当前审批节点标识',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING 审批中，APPROVED 已同意，REJECTED 已拒绝，WITHDRAWN 已撤回',
    `submit_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审批提交时间',
    `finish_time` DATETIME(3) NULL COMMENT '审批结束时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_examine_example_business` (`tenant_id`, `module_id`, `record_id`),
    KEY `idx_examine_example_applicant` (`tenant_id`, `applicant_member_id`, `submit_time`),
    KEY `idx_examine_example_status` (`tenant_id`, `status`, `submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例';

CREATE TABLE IF NOT EXISTS `un_examine_example_node` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批实例节点主键',
    `example_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例主键',
    `definition_node_id` BIGINT UNSIGNED NOT NULL COMMENT '来源审批定义节点主键',
    `node_key` VARCHAR(64) NOT NULL COMMENT '发起时的节点标识',
    `name` VARCHAR(100) NOT NULL COMMENT '发起时的节点名称',
    `node_type` VARCHAR(20) NOT NULL COMMENT '发起时的节点类型',
    `pass_mode` VARCHAR(20) NULL COMMENT '发起时的多人通过方式',
    `sort_num` INT NOT NULL COMMENT '发起时的节点执行顺序',
    `round_no` INT NOT NULL DEFAULT 1 COMMENT '该节点的执行轮次',
    `status` VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '节点状态：WAITING 未到达，PENDING 处理中，APPROVED 已同意，REJECTED 已拒绝，SKIPPED 已跳过，CANCELLED 已取消',
    `start_time` DATETIME(3) NULL COMMENT '节点开始处理时间',
    `finish_time` DATETIME(3) NULL COMMENT '节点处理结束时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_example_node_round` (`example_id`, `node_key`, `round_no`),
    KEY `idx_examine_example_node_sort` (`example_id`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例节点';

CREATE TABLE IF NOT EXISTS `un_examine_example_node_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批实例节点人员主键',
    `example_node_id` BIGINT UNSIGNED NOT NULL COMMENT '所属审批实例节点主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '实际处理成员主键',
    `sort_num` INT NOT NULL DEFAULT 0 COMMENT '依次审批时的处理顺序',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING 待处理，APPROVED 已同意，REJECTED 已拒绝，CANCELLED 已取消',
    `opinion` VARCHAR(1000) NULL COMMENT '处理意见',
    `request_key` VARCHAR(64) NULL COMMENT '本次处理的幂等请求标识',
    `assign_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '分配给处理人的时间',
    `action_time` DATETIME(3) NULL COMMENT '实际处理时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examine_example_node_member` (`example_node_id`, `member_id`),
    UNIQUE KEY `uk_examine_example_user_request` (`request_key`),
    KEY `idx_examine_example_user_inbox` (`member_id`, `status`, `assign_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例节点的实际处理人员';
