-- 工作日志业务化：补充结构化日报内容、可见范围、选材快照和沟通记录。
-- 旧 content_text 保留为兼容摘要；迁移不删除旧数据，历史日志仍可直接读取。

ALTER TABLE `work_log`
  ADD COLUMN `completed_text` TEXT NULL COMMENT '今日已完成事项' AFTER `content_text`,
  ADD COLUMN `in_progress_text` TEXT NULL COMMENT '当前进行中事项' AFTER `completed_text`,
  ADD COLUMN `next_plan_text` TEXT NULL COMMENT '下一工作日计划' AFTER `in_progress_text`,
  ADD COLUMN `risk_text` TEXT NULL COMMENT '风险与阻塞说明' AFTER `next_plan_text`,
  ADD COLUMN `assistance_text` TEXT NULL COMMENT '需要协助的事项' AFTER `risk_text`,
  ADD COLUMN `visibility_type` VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '可见范围：本人、直属管理、项目成员、同部门或指定角色' AFTER `status`,
  ADD COLUMN `visible_role_ids_json` JSON NULL COMMENT '指定可见角色标识快照' AFTER `visibility_type`,
  ADD COLUMN `submitted_time` DATETIME(3) NULL COMMENT '最近一次提交时间' AFTER `visible_role_ids_json`,
  ADD COLUMN `withdrawn_time` DATETIME(3) NULL COMMENT '最近一次撤回时间' AFTER `submitted_time`,
  ADD KEY `idx_work_log_visibility` (`system_id`, `tenant_id`, `visibility_type`, `work_date`);

UPDATE `work_log`
SET `completed_text` = `content_text`
WHERE `completed_text` IS NULL;

CREATE TABLE `work_log_material` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志选材主键',
  `work_log_id` BIGINT NOT NULL COMMENT '工作日志主键',
  `material_type` VARCHAR(32) NOT NULL COMMENT '材料类型：任务、待办、消息或业务操作',
  `material_id` VARCHAR(100) NOT NULL COMMENT '来源材料标识',
  `material_title` VARCHAR(500) NOT NULL COMMENT '选中时的材料标题快照',
  `material_summary` VARCHAR(2000) NULL COMMENT '选中时的材料摘要快照',
  `source_time` DATETIME(3) NULL COMMENT '来源材料发生时间',
  `created_by_account_id` BIGINT NOT NULL COMMENT '选择材料的平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '材料快照创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_material` (`work_log_id`, `material_type`, `material_id`),
  KEY `idx_work_log_material_source` (`material_type`, `material_id`),
  CONSTRAINT `fk_work_log_material_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_material_actor` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志人工选材快照';

CREATE TABLE `work_log_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志沟通记录主键',
  `work_log_id` BIGINT NOT NULL COMMENT '工作日志主键',
  `comment_type` VARCHAR(32) NOT NULL COMMENT '沟通类型：评论或反馈',
  `comment_text` VARCHAR(2000) NOT NULL COMMENT '评论或反馈正文',
  `created_by_account_id` BIGINT NOT NULL COMMENT '发表人的平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发表时间',
  PRIMARY KEY (`id`),
  KEY `idx_work_log_comment` (`work_log_id`, `created_time`),
  CONSTRAINT `fk_work_log_comment_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_comment_actor` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志评论与管理反馈';
