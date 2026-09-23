-- 补齐项目、任务组和任务主链路所需的结构化字段；成员类型继续复用 work_task_member，过程记录继续复用 work_task_history。

ALTER TABLE `work_project`
  COMMENT = '工作项目',
  ADD COLUMN `customer_name` VARCHAR(200) NULL COMMENT '客户名称' AFTER `description`,
  ADD COLUMN `internal_department_name` VARCHAR(200) NULL COMMENT '内部部门名称' AFTER `customer_name`,
  ADD COLUMN `stage` VARCHAR(64) NULL COMMENT '项目阶段' AFTER `internal_department_name`,
  ADD COLUMN `labels_json` JSON NULL COMMENT '项目标签列表' AFTER `stage`,
  ADD COLUMN `business_type` VARCHAR(64) NULL COMMENT '关联业务类型' AFTER `labels_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL COMMENT '关联业务记录标识' AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL COMMENT '关联业务记录标题' AFTER `business_id`,
  ADD KEY `idx_work_project_business` (`system_id`, `tenant_id`, `business_type`, `business_id`);

ALTER TABLE `work_task_group`
  COMMENT = '项目任务组',
  ADD COLUMN `owner_account_id` BIGINT NULL COMMENT '任务组负责人账号标识' AFTER `status`,
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL COMMENT '任务组负责人租户成员标识' AFTER `owner_account_id`,
  ADD COLUMN `due_time` DATETIME(3) NULL COMMENT '任务组截止时间' AFTER `owner_tenant_member_id`,
  ADD COLUMN `progress_percent` DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '任务组完成百分比' AFTER `due_time`,
  ADD KEY `idx_work_task_group_owner` (`owner_account_id`, `status`, `due_time`),
  ADD CONSTRAINT `fk_work_task_group_owner`
    FOREIGN KEY (`owner_account_id`) REFERENCES `plat_account` (`id`),
  ADD CONSTRAINT `fk_work_task_group_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task`
  COMMENT = '工作任务',
  ADD COLUMN `estimated_hours` DECIMAL(8, 2) NULL COMMENT '预计工时' AFTER `progress_percent`,
  ADD COLUMN `actual_hours` DECIMAL(8, 2) NULL COMMENT '实际工时' AFTER `estimated_hours`,
  ADD COLUMN `labels_json` JSON NULL COMMENT '任务标签列表' AFTER `actual_hours`,
  ADD COLUMN `checklist_json` JSON NULL COMMENT '任务检查清单' AFTER `labels_json`,
  ADD COLUMN `completion_note` VARCHAR(2000) NULL COMMENT '完成说明' AFTER `completed_at`,
  ADD COLUMN `completion_submitted_time` DATETIME(3) NULL COMMENT '提交完成时间' AFTER `completion_note`,
  ADD COLUMN `completion_accepted_time` DATETIME(3) NULL COMMENT '验收通过时间' AFTER `completion_submitted_time`,
  ADD COLUMN `archive_time` DATETIME(3) NULL COMMENT '归档时间' AFTER `completion_accepted_time`,
  ADD KEY `idx_work_task_archive` (`system_id`, `tenant_id`, `archive_time`);
