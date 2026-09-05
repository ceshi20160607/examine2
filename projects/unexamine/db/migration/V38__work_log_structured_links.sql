ALTER TABLE `work_log`
  ADD COLUMN `project_id` BIGINT NULL AFTER `tenant_id`,
  ADD KEY `idx_work_log_project_date` (`project_id`, `work_date`),
  ADD CONSTRAINT `fk_work_log_project` FOREIGN KEY (`project_id`) REFERENCES `work_project` (`id`);

CREATE TABLE `work_log_task_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_log_id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_task_link` (`work_log_id`, `task_id`),
  KEY `idx_work_log_task_link_task` (`task_id`, `work_log_id`),
  CONSTRAINT `fk_work_log_task_link_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_task_link_task` FOREIGN KEY (`task_id`) REFERENCES `work_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE `work_log`
SET `project_id` = CAST(JSON_UNQUOTE(JSON_EXTRACT(`custom_values_json`, '$.projectId')) AS UNSIGNED)
WHERE JSON_EXTRACT(`custom_values_json`, '$.projectId') IS NOT NULL;

INSERT IGNORE INTO `work_log_task_link` (`work_log_id`, `task_id`)
SELECT wl.id, CAST(jt.task_id AS UNSIGNED)
FROM `work_log` wl
JOIN JSON_TABLE(wl.custom_values_json, '$.taskIds[*]' COLUMNS(task_id VARCHAR(32) PATH '$')) AS jt ON TRUE
WHERE wl.custom_values_json IS NOT NULL;
