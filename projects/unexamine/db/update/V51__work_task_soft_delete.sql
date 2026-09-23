-- 任务删除保留审计和历史记录，列表与详情按 delete_time 隐藏，避免物理删除破坏关联证据。

ALTER TABLE `work_task`
  ADD COLUMN `delete_time` DATETIME(3) NULL COMMENT '任务删除时间' AFTER `archive_time`,
  ADD KEY `idx_work_task_delete` (`system_id`, `tenant_id`, `delete_time`);
