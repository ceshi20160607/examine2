-- source: update/V2__default_member_role.sql
-- 用途：为已有主租户补齐默认普通成员角色；新系统由创建事务直接初始化此角色。
-- 仅插入缺失角色，不改写既有授权、成员关系或业务数据；依赖 (tenant_id, code) 唯一约束保持幂等。
INSERT INTO `un_sys_role` (`system_id`, `tenant_id`, `code`, `name`, `status`)
SELECT tenant.`system_id`, tenant.`id`, 'member', '普通成员', 'ACTIVE'
FROM `un_sys_tenant` AS tenant
LEFT JOIN `un_sys_role` AS role
  ON role.`tenant_id` = tenant.`id` AND role.`code` = 'member'
WHERE tenant.`main_flag` = 1 AND role.`id` IS NULL;

-- source: update/V3__record_identity.sql
-- 用途：给通用业务记录增加可在列表与详情显示的标题和稳定编号。
-- 增量修改现有表，不删除旧记录；先为已有记录补值，再建立租户与模块内的编号唯一约束。
ALTER TABLE `un_module_record`
  ADD COLUMN `title` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '业务记录在列表与详情中显示的标题' AFTER `owner_member_id`,
  ADD COLUMN `record_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务记录在租户和模块内唯一的稳定编号' AFTER `title`;

-- 迁移已有数据：旧记录原来没有标题和编号，用主键生成可追溯占位值；新写入由业务服务生成编号。
UPDATE `un_module_record`
SET `title` = CONCAT('记录 ', `id`), `record_no` = CONCAT('R', `id`)
WHERE `record_no` = '';

ALTER TABLE `un_module_record`
  ADD UNIQUE KEY `uk_module_record_no` (`tenant_id`, `module_id`, `record_no`);

-- source: update/V4__basic_approval.sql
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

-- source: update/V5__record_files_export.sql
-- 用途：为通用业务记录增加本地文件元数据/引用，并为模块数据导出保存异步任务状态。
-- 已有记录与审批数据不变；新表从空表开始，不删除或重建现有业务表。
CREATE TABLE `un_file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `system_id` BIGINT NOT NULL COMMENT '文件所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '文件所属租户主键',
  `storage_key` VARCHAR(80) NOT NULL COMMENT '本地存储中随机生成的文件名',
  `original_name` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  `content_type` VARCHAR(120) NOT NULL COMMENT '服务端识别的安全文件类型',
  `size_bytes` BIGINT NOT NULL COMMENT '文件实际字节数',
  `sha256_hex` CHAR(64) NOT NULL COMMENT '文件内容的 SHA-256 摘要',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '文件可用或已移除状态',
  `created_by_account_id` BIGINT NOT NULL COMMENT '上传文件的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传时间',
  `delete_time` DATETIME(3) DEFAULT NULL COMMENT '文件移除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`storage_key`),
  KEY `idx_file_object_scope` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地文件内容的元数据与存储定位';

CREATE TABLE `un_file_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件引用主键',
  `file_id` BIGINT NOT NULL COMMENT '被引用的文件对象主键',
  `record_id` BIGINT NOT NULL COMMENT '引用文件的业务记录主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '附件加入业务记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_file` (`file_id`),
  KEY `idx_file_reference_record` (`record_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记录与附件的引用关系';

CREATE TABLE `un_exchange_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块数据导出任务主键',
  `system_id` BIGINT NOT NULL COMMENT '任务所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '任务所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '任务导出的业务模块主键',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '提交任务的平台账号主键',
  `filter_json` JSON NOT NULL COMMENT '提交时的标题和字段筛选条件快照',
  `columns_json` JSON NOT NULL COMMENT '提交时选择的模块字段编码快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT '排队、运行、成功或失败状态',
  `result_key` VARCHAR(80) DEFAULT NULL COMMENT '成功结果在本地存储中的随机文件名',
  `row_count` INT NOT NULL DEFAULT 0 COMMENT '导出成功的数据行数',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败时给请求者展示的原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务提交时间',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '任务开始处理时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '任务结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_job_owner` (`tenant_id`,`requested_by_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块筛选数据的异步导出任务';

-- source: update/V6__export_action_permission.sql
-- 用途：把模块导出作为可授权页面动作，避免仅凭查看权限取走批量业务数据。
-- 现有角色默认不获得导出权限；已有模块草稿补导出动作，管理员重新发布模块后才对运行态生效。
ALTER TABLE `un_sys_role_menu`
  ADD COLUMN `can_export` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许导出当前有权查看的模块记录' AFTER `can_delete`;

-- 迁移已有模块草稿：只补缺失的导出动作，不修改已有发布快照或授权。
INSERT INTO `un_module_action` (`module_id`, `code`, `name`, `action_type`, `enabled_flag`, `sort_order`)
SELECT m.`id`, 'export', '导出', 'EXPORT', 1, 3
FROM `un_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `un_module_action` a WHERE a.`module_id` = m.`id` AND a.`code` = 'export'
);

-- source: update/V7__module_import_job.sql
-- 用途：保存模块 Excel 导入的预检查、确认和错误行历史；不改写现有业务记录或字段结构。
-- 旧记录无需回填；导入仅从已发布字段模板进入，确认阶段再由原业务服务按权限写入。
CREATE TABLE `un_exchange_import_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块数据导入任务主键',
  `system_id` BIGINT NOT NULL COMMENT '导入任务所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '导入任务所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '目标业务模块主键',
  `module_version_id` BIGINT NOT NULL COMMENT '预检查时的模块发布版本主键',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '提交导入的平台账号主键',
  `mode` VARCHAR(20) NOT NULL COMMENT '只新增或按记录编号匹配更新的导入模式',
  `source_key` VARCHAR(80) NOT NULL COMMENT '本地存储中随机生成的源 Excel 文件名',
  `source_name` VARCHAR(255) NOT NULL COMMENT '用户上传的原始 Excel 文件名',
  `source_sha256` CHAR(64) NOT NULL COMMENT '预检查文件内容摘要',
  `preview_json` JSON NOT NULL COMMENT '逐行预检查结果和错误原因快照',
  `status` VARCHAR(20) NOT NULL COMMENT '已预检查、含错误、已导入或失败状态',
  `total_rows` INT NOT NULL DEFAULT 0 COMMENT 'Excel 中待导入的数据行数',
  `valid_rows` INT NOT NULL DEFAULT 0 COMMENT '预检查通过的数据行数',
  `imported_rows` INT NOT NULL DEFAULT 0 COMMENT '确认后成功落库的数据行数',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '确认失败时显示给提交人的原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传和预检查时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '确认导入或失败时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_import_source` (`source_key`),
  KEY `idx_exchange_import_owner` (`tenant_id`,`requested_by_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块 Excel 导入的预检查、确认和错误行历史';

-- source: update/V8__module_import_approval_actions.sql
-- 用途：给已有模块草稿补齐已实现的导入与发起审批动作，管理员可在下一次发布时配置。
-- 历史发布版本的 JSON 快照保持不变；运行时代码对旧快照按原有行为兼容，重新发布后按显式配置执行。
INSERT INTO `un_module_action` (`module_id`,`code`,`name`,`action_type`,`enabled_flag`,`sort_order`)
SELECT m.`id`, a.`code`, a.`name`, a.`action_type`, 1, a.`sort_order`
FROM `un_module` m
CROSS JOIN (
  SELECT 'import' AS `code`, '导入' AS `name`, 'IMPORT' AS `action_type`, 4 AS `sort_order`
  UNION ALL SELECT 'submit_approval', '发起审批', 'SUBMIT_APPROVAL', 5
) a
WHERE NOT EXISTS (
  SELECT 1 FROM `un_module_action` existing
  WHERE existing.`module_id` = m.`id` AND existing.`code` = a.`code`
);
