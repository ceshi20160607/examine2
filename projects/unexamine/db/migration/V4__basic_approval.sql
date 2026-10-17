-- 用途：为已发布模块的业务记录增加最小可运行审批；旧记录保留 ACTIVE 状态，无需回填或重建。
-- 本阶段每个模块只配置一个指定成员审批节点；版本快照独立保存，后续扩展节点时不改写历史实例。
CREATE TABLE `un_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定义主键',
  `system_id` BIGINT NOT NULL COMMENT '所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '所属主租户主键',
  `module_id` BIGINT NOT NULL COMMENT '绑定的业务模块主键',
  `name` VARCHAR(120) NOT NULL COMMENT '审批流程名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '流程状态，草稿或已发布',
  `published_version_id` BIGINT DEFAULT NULL COMMENT '当前生效的不可变发布版本主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '流程更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_module` (`tenant_id`,`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块的基础审批流程定义';

CREATE TABLE `un_flow_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批草稿节点主键',
  `flow_id` BIGINT NOT NULL COMMENT '所属流程定义主键',
  `node_name` VARCHAR(120) NOT NULL COMMENT '节点展示名称',
  `approver_member_id` BIGINT NOT NULL COMMENT '指定审批人的系统成员主键',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '节点顺序，当前基础审批只允许一个节点',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '草稿节点创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '草稿节点更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_order` (`flow_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程草稿节点与指定审批人';

CREATE TABLE `un_flow_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程发布版本主键',
  `flow_id` BIGINT NOT NULL COMMENT '所属流程定义主键',
  `version_number` INT NOT NULL COMMENT '同流程内连续版本号',
  `snapshot_json` JSON NOT NULL COMMENT '发布时节点与审批人的不可变快照',
  `published_by_account_id` BIGINT NOT NULL COMMENT '执行发布的平台账号主键',
  `published_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_version_number` (`flow_id`,`version_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程不可变发布版本';

CREATE TABLE `un_flow_example` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批运行实例主键',
  `flow_id` BIGINT NOT NULL COMMENT '流程定义主键',
  `flow_version_id` BIGINT NOT NULL COMMENT '发起时采用的发布版本主键',
  `system_id` BIGINT NOT NULL COMMENT '实例所属系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '实例所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '实例绑定的业务模块主键',
  `record_id` BIGINT NOT NULL COMMENT '实例绑定的业务记录主键',
  `attempt_number` INT NOT NULL COMMENT '同一记录的发起轮次',
  `applicant_member_id` BIGINT NOT NULL COMMENT '发起人的系统成员主键',
  `snapshot_json` JSON NOT NULL COMMENT '实例创建时复制的流程版本快照',
  `status` VARCHAR(20) NOT NULL COMMENT '实例状态，待审批、通过或拒绝',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '实例发起时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '实例结束时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '实例乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_example_attempt` (`record_id`,`attempt_number`),
  KEY `idx_flow_example_scope` (`tenant_id`,`module_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记录的一轮审批运行实例';

CREATE TABLE `un_flow_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批待办任务主键',
  `example_id` BIGINT NOT NULL COMMENT '所属流程实例主键',
  `approver_member_id` BIGINT NOT NULL COMMENT '可办理任务的系统成员主键',
  `node_name` VARCHAR(120) NOT NULL COMMENT '发起时快照中的节点名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务待办、通过或拒绝状态',
  `opinion` VARCHAR(1000) DEFAULT NULL COMMENT '办理意见，拒绝时必填',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '待办产生时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '任务办理完成时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '任务乐观锁版本，阻止重复办理',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_todo` (`approver_member_id`,`status`,`create_time`),
  KEY `idx_flow_task_example` (`example_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指定审批人的流程待办任务';

CREATE TABLE `un_flow_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批事件主键',
  `example_id` BIGINT NOT NULL COMMENT '所属流程实例主键',
  `task_id` BIGINT DEFAULT NULL COMMENT '有关联时的待办任务主键',
  `actor_member_id` BIGINT NOT NULL COMMENT '执行操作的系统成员主键',
  `event_type` VARCHAR(20) NOT NULL COMMENT '提交、同意或拒绝事件类型',
  `opinion` VARCHAR(1000) DEFAULT NULL COMMENT '操作意见或拒绝原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_event_example` (`example_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变的审批操作历史';
