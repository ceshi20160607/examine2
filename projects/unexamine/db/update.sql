-- source: update/V19__tenant_main_constraint.sql
ALTER TABLE `sys_tenant`
  DROP CHECK `chk_sys_tenant_main_marker`,
  ADD CONSTRAINT `chk_sys_tenant_main_marker`
    CHECK ((`is_main` = 1 AND `main_marker` IS NOT NULL AND `main_marker` = 'MAIN')
      OR (`is_main` = 0 AND `main_marker` IS NULL));

-- source: update/V20__access_request_context.sql
ALTER TABLE `sys_access_request`
  ADD COLUMN `identity_provider` VARCHAR(100) NOT NULL DEFAULT 'LOCAL' AFTER `account_id`,
  ADD COLUMN `external_user_id` VARCHAR(255) NULL AFTER `identity_provider`,
  ADD COLUMN `requested_role` VARCHAR(100) NULL AFTER `request_reason`,
  ADD COLUMN `approved_role_ids_json` JSON NULL AFTER `decision_comment`,
  ADD COLUMN `approved_data_scope_json` JSON NULL AFTER `approved_role_ids_json`,
  ADD COLUMN `request_trace_id` VARCHAR(64) NULL AFTER `approved_data_scope_json`,
  ADD COLUMN `decision_trace_id` VARCHAR(64) NULL AFTER `request_trace_id`,
  ADD KEY `idx_sys_access_external_identity` (`identity_provider`, `external_user_id`),
  ADD KEY `idx_sys_access_request_trace` (`request_trace_id`);

UPDATE `sys_access_request`
SET `request_trace_id` = CONCAT('migration-', `id`)
WHERE `request_trace_id` IS NULL;

ALTER TABLE `sys_access_request`
  MODIFY COLUMN `request_trace_id` VARCHAR(64) NOT NULL;

-- source: update/V21__sso_provider_version_lifecycle.sql
CREATE TABLE `plat_sso_provider_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `provider_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `protocol` VARCHAR(32) NOT NULL,
  `issuer` VARCHAR(500) NOT NULL,
  `client_id` VARCHAR(255) NOT NULL,
  `client_secret_ref` VARCHAR(255) NULL,
  `protocol_config_json` JSON NOT NULL,
  `allowed_domains_json` JSON NOT NULL,
  `attribute_mapping_json` JSON NOT NULL,
  `jit_policy_json` JSON NOT NULL,
  `mfa_policy_json` JSON NOT NULL,
  `callback_uris_json` JSON NOT NULL,
  `test_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED',
  `test_report_json` JSON NULL,
  `tested_at` DATETIME(3) NULL,
  `tested_by_account_id` BIGINT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `published_by_account_id` BIGINT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_provider_version` (`provider_id`, `version_number`),
  KEY `idx_plat_sso_version_status` (`status`, `test_status`),
  CONSTRAINT `fk_plat_sso_version_provider` FOREIGN KEY (`provider_id`) REFERENCES `plat_sso_provider` (`id`),
  CONSTRAINT `fk_plat_sso_version_tester` FOREIGN KEY (`tested_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_plat_sso_version_publisher` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `plat_sso_provider`
  ADD COLUMN `published_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `published_version_number` INT NULL AFTER `published_version_id`,
  ADD KEY `idx_plat_sso_published_version` (`published_version_id`),
  ADD CONSTRAINT `fk_plat_sso_published_version`
    FOREIGN KEY (`published_version_id`) REFERENCES `plat_sso_provider_version` (`id`);

-- source: update/V22__dictionary_version_lifecycle.sql
CREATE TABLE `cfg_dictionary_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `dictionary_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_version_number` (`dictionary_id`, `version_number`),
  UNIQUE KEY `uk_cfg_dictionary_version_dict_id` (`dictionary_id`, `id`),
  KEY `idx_cfg_dictionary_version_context` (`system_id`, `owner_tenant_id`, `dictionary_id`),
  CONSTRAINT `fk_cfg_dictionary_version_dictionary`
    FOREIGN KEY (`owner_tenant_id`, `dictionary_id`) REFERENCES `cfg_dictionary` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_version_publisher`
    FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_dictionary_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `dictionary_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_publication` (`dictionary_id`),
  KEY `idx_cfg_dictionary_publication_context` (`system_id`, `owner_tenant_id`),
  CONSTRAINT `fk_cfg_dictionary_publication_dictionary`
    FOREIGN KEY (`owner_tenant_id`, `dictionary_id`) REFERENCES `cfg_dictionary` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_publication_version`
    FOREIGN KEY (`dictionary_id`, `current_version_id`) REFERENCES `cfg_dictionary_version` (`dictionary_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_publication_publisher`
    FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V23__runtime_unique_index_key.sql
ALTER TABLE `biz_record_index`
  ADD COLUMN `unique_key_hash` VARCHAR(64) NULL AFTER `index_key_hash`,
  ADD UNIQUE KEY `uk_biz_record_index_unique_value`
    (`tenant_id`, `module_id`, `query_index_id`, `unique_key_hash`);

-- source: update/V24__module_rule_test_result.sql
ALTER TABLE `cfg_module_rule`
  ADD COLUMN `test_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED' AFTER `message_template`,
  ADD COLUMN `last_test_input_json` JSON NULL AFTER `test_status`,
  ADD COLUMN `last_test_result_json` JSON NULL AFTER `last_test_input_json`,
  ADD COLUMN `last_tested_by_member_id` BIGINT NULL AFTER `last_test_result_json`,
  ADD COLUMN `last_tested_at` DATETIME(3) NULL AFTER `last_tested_by_member_id`,
  ADD CONSTRAINT `fk_cfg_module_rule_last_tester`
    FOREIGN KEY (`system_id`, `last_tested_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`);

-- source: update/V25__tenant_extension_publication_pointer.sql
ALTER TABLE `cfg_tenant_extension_version`
  ADD UNIQUE KEY `uk_cfg_extension_version_owner_id` (`extension_id`, `id`);

ALTER TABLE `cfg_tenant_extension`
  ADD COLUMN `current_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `updated_by_member_id` BIGINT NULL AFTER `current_version_id`,
  ADD CONSTRAINT `fk_cfg_tenant_extension_current_version`
    FOREIGN KEY (`id`, `current_version_id`) REFERENCES `cfg_tenant_extension_version` (`extension_id`, `id`),
  ADD CONSTRAINT `fk_cfg_tenant_extension_updater`
    FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`);

-- source: update/V26__module_rollback_version.sql
ALTER TABLE `cfg_module_version`
  DROP INDEX `uk_cfg_module_version_revision`,
  ADD KEY `idx_cfg_module_version_revision` (`module_id`, `draft_revision`);

-- source: update/V27__application_call_contract.sql
-- Complete the durable application-call security and observability contract.

ALTER TABLE `app_call`
  ADD COLUMN `request_timestamp` DATETIME(3) NOT NULL AFTER `trace_id`,
  ADD COLUMN `nonce` VARCHAR(128) NOT NULL AFTER `request_timestamp`,
  ADD COLUMN `permission_snapshot_json` JSON NULL AFTER `request_hash`,
  ADD COLUMN `response_json` JSON NULL AFTER `response_code`,
  ADD COLUMN `target_reference` VARCHAR(255) NULL AFTER `response_json`,
  ADD COLUMN `replay_count` INT NOT NULL DEFAULT 0 AFTER `target_reference`,
  ADD UNIQUE KEY `uk_app_call_nonce` (`application_id`, `nonce`);

-- source: update/V28__application_signing_secret_and_nonce.sql
-- Keep signing material encrypted behind a reference and persist every accepted nonce.

ALTER TABLE `app_credential`
  ADD COLUMN `signing_secret_ref` VARCHAR(2000) NULL AFTER `secret_hash`;

CREATE TABLE `app_call_nonce` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `credential_version` INT NOT NULL,
  `nonce` VARCHAR(128) NOT NULL,
  `request_id` VARCHAR(64) NOT NULL,
  `request_timestamp` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_call_nonce_value` (`application_id`, `nonce`),
  KEY `idx_app_call_nonce_expiry` (`expires_at`),
  CONSTRAINT `fk_app_call_nonce_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V29__application_call_rejection_log.sql
-- A rejected call may fail before a matching grant exists; retain that attempt for audit.

ALTER TABLE `app_call`
  DROP FOREIGN KEY `fk_app_call_grant`;

ALTER TABLE `app_call`
  MODIFY COLUMN `grant_id` BIGINT NULL;

ALTER TABLE `app_call`
  ADD CONSTRAINT `fk_app_call_grant` FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`);

-- source: update/V30__application_grant_history_link.sql
-- Draft grants are mutable; completed call evidence must survive grant replacement.

ALTER TABLE `app_call`
  DROP FOREIGN KEY `fk_app_call_grant`;

ALTER TABLE `app_call`
  ADD CONSTRAINT `fk_app_call_grant`
  FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`) ON DELETE SET NULL;

-- source: update/V31__message_event_deduplication.sql
-- Message events must be idempotent within one platform/system/tenant context.
ALTER TABLE `msg_message`
  ADD COLUMN `dedup_context_key` VARCHAR(160)
    GENERATED ALWAYS AS (
      CONCAT(`context_type`, ':', IFNULL(`platform_id`, 0), ':', IFNULL(`system_id`, 0), ':', IFNULL(`tenant_id`, 0))
    ) STORED,
  ADD UNIQUE KEY `uk_msg_message_event_dedup` (`dedup_context_key`, `source_type`, `source_id`);

-- source: update/V32__module_import_action.sql
-- C43: every module exposes import as an explicit, publishable action.

INSERT INTO `cfg_module_action` (
  `system_id`, `owner_tenant_id`, `module_id`, `code`, `name`, `action_type`,
  `location`, `sort_order`, `status`, `config_json`, `version`
)
SELECT
  m.`system_id`, m.`owner_tenant_id`, m.`id`, 'IMPORT', '导入', 'BUILTIN',
  'LIST_TOOLBAR', 100, 'ACTIVE', JSON_OBJECT(), 0
FROM `cfg_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'IMPORT'
);

UPDATE `cfg_module` m
SET m.`draft_revision` = m.`draft_revision` + 1,
    m.`updated_at` = CURRENT_TIMESTAMP(3)
WHERE EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'IMPORT'
)
AND EXISTS (
  SELECT 1 FROM `cfg_module_publication` p
  WHERE p.`module_id` = m.`id`
);

-- source: update/V33__module_export_action.sql
-- C44: every module exposes permission-scoped export as an explicit, publishable action.

INSERT INTO `cfg_module_action` (
  `system_id`, `owner_tenant_id`, `module_id`, `code`, `name`, `action_type`,
  `location`, `sort_order`, `status`, `config_json`, `version`
)
SELECT
  m.`system_id`, m.`owner_tenant_id`, m.`id`, 'EXPORT', '导出', 'BUILTIN',
  'LIST_TOOLBAR', 110, 'ACTIVE', JSON_OBJECT(), 0
FROM `cfg_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
);

UPDATE `cfg_module` m
SET m.`draft_revision` = m.`draft_revision` + 1,
    m.`updated_at` = CURRENT_TIMESTAMP(3)
WHERE EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
)
AND EXISTS (
  SELECT 1 FROM `cfg_module_publication` p
  WHERE p.`module_id` = m.`id`
);

-- source: update/V34__operations_observability.sql
-- Persisted request observations make requestId searchable without server access.
-- Query strings, authorization headers, cookies and secret values are intentionally not stored.
CREATE TABLE `ops_request_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_id` VARCHAR(64) NOT NULL,
  `trace_id` VARCHAR(64) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `account_id` BIGINT NULL,
  `client_source` VARCHAR(32) NOT NULL,
  `http_method` VARCHAR(16) NOT NULL,
  `request_path` VARCHAR(500) NOT NULL,
  `status_code` INT NOT NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `duration_millis` BIGINT NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ops_request_id` (`request_id`, `occurred_at`),
  KEY `idx_ops_request_context` (`system_id`, `tenant_id`, `occurred_at`),
  KEY `idx_ops_request_result` (`status_code`, `result_code`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V35__organization_reporting_directory.sql
-- Unified tenant organization directory: position and reporting line are business relations,
-- while stable member identifiers remain internal implementation details.
CREATE TABLE `sys_member_reporting_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `tenant_member_id` BIGINT NOT NULL,
  `manager_tenant_member_id` BIGINT NULL,
  `position_title` VARCHAR(100) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_reporting_member` (`tenant_id`, `tenant_member_id`),
  KEY `idx_sys_member_reporting_manager` (`tenant_id`, `manager_tenant_member_id`),
  CONSTRAINT `fk_sys_member_reporting_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_member` FOREIGN KEY (`tenant_id`, `tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_manager` FOREIGN KEY (`tenant_id`, `manager_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V36__backfill_system_root_department.sql
-- Systems created before the atomic bootstrap receive the same usable organization root.
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
WHERE tenant.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id` AND department.`parent_id` IS NULL
  );

UPDATE `sys_tenant_member` membership
JOIN `sys_department` root_department
  ON root_department.`tenant_id` = membership.`tenant_id`
 AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL;

-- source: update/V37__flow_work_business_context.sql
-- Flow runtime layers and business-object work context.
-- Published definitions remain in flow_version; runtime execution, history and jobs are separated here.

ALTER TABLE `flow_instance`
  ADD COLUMN `started_by_tenant_member_id` BIGINT NULL AFTER `started_by_account_id`,
  ADD KEY `idx_flow_instance_starter_member` (`tenant_id`, `started_by_tenant_member_id`),
  ADD CONSTRAINT `fk_flow_instance_starter_member`
    FOREIGN KEY (`started_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_task`
  ADD COLUMN `assignee_tenant_member_id` BIGINT NULL AFTER `assignee_account_id`,
  ADD KEY `idx_flow_task_assignee_member` (`assignee_tenant_member_id`, `status`, `due_at`),
  ADD CONSTRAINT `fk_flow_task_assignee_member`
    FOREIGN KEY (`assignee_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_action`
  ADD COLUMN `acted_by_tenant_member_id` BIGINT NULL AFTER `acted_by_account_id`,
  ADD KEY `idx_flow_action_actor_member` (`acted_by_tenant_member_id`, `acted_at`),
  ADD CONSTRAINT `fk_flow_action_actor_member`
    FOREIGN KEY (`acted_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_exception`
  ADD COLUMN `resolved_by_tenant_member_id` BIGINT NULL AFTER `resolved_by_account_id`,
  ADD CONSTRAINT `fk_flow_exception_resolver_member`
    FOREIGN KEY (`resolved_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

CREATE TABLE `flow_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `parent_execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `node_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `entered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `left_at` DATETIME(3) NULL,
  `result_code` VARCHAR(64) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flow_execution_active` (`instance_id`, `status`, `entered_at`),
  CONSTRAINT `fk_flow_execution_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_execution_parent` FOREIGN KEY (`parent_execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_history_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `task_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `event_name` VARCHAR(200) NOT NULL,
  `actor_account_id` BIGINT NULL,
  `actor_tenant_member_id` BIGINT NULL,
  `before_status` VARCHAR(32) NULL,
  `after_status` VARCHAR(32) NULL,
  `detail_json` JSON NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_flow_history_instance` (`instance_id`, `occurred_at`),
  KEY `idx_flow_history_task` (`task_id`, `occurred_at`),
  CONSTRAINT `fk_flow_history_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_history_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`),
  CONSTRAINT `fk_flow_history_task` FOREIGN KEY (`task_id`) REFERENCES `flow_task` (`id`),
  CONSTRAINT `fk_flow_history_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_flow_history_actor_member` FOREIGN KEY (`actor_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `job_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `payload_json` JSON NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `next_run_at` DATETIME(3) NOT NULL,
  `locked_at` DATETIME(3) NULL,
  `locked_by` VARCHAR(100) NULL,
  `last_error_code` VARCHAR(100) NULL,
  `last_error_message` VARCHAR(2000) NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_job_idempotency` (`instance_id`, `idempotency_key`),
  KEY `idx_flow_job_due` (`status`, `next_run_at`),
  CONSTRAINT `fk_flow_job_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_job_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `work_project`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD CONSTRAINT `fk_work_project_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_project_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_project_member_tenant_member` (`tenant_member_id`, `project_id`),
  ADD CONSTRAINT `fk_work_project_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_task_owner_member` (`owner_tenant_member_id`, `status`, `due_at`),
  ADD KEY `idx_work_task_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_task_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_task_member_tenant_member` (`tenant_member_id`, `task_id`),
  ADD CONSTRAINT `fk_work_task_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_log`
  ADD COLUMN `author_tenant_member_id` BIGINT NULL AFTER `author_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_log_author_member` (`author_tenant_member_id`, `work_date`),
  ADD KEY `idx_work_log_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_log_author_member`
    FOREIGN KEY (`author_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

UPDATE `flow_instance` fi
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = fi.started_by_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET fi.started_by_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND fi.started_by_tenant_member_id IS NULL;

UPDATE `flow_task` ft
JOIN `flow_instance` fi ON fi.id = ft.instance_id
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = ft.assignee_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET ft.assignee_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND ft.assignee_tenant_member_id IS NULL;

UPDATE `work_project` wp
JOIN `sys_member` sm ON sm.system_id = wp.system_id AND sm.account_id = wp.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wp.tenant_id
SET wp.owner_tenant_member_id = stm.id
WHERE wp.context_type = 'SYSTEM' AND wp.owner_tenant_member_id IS NULL;

UPDATE `work_task` wt
JOIN `sys_member` sm ON sm.system_id = wt.system_id AND sm.account_id = wt.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wt.tenant_id
SET wt.owner_tenant_member_id = stm.id
WHERE wt.context_type = 'SYSTEM' AND wt.owner_tenant_member_id IS NULL;

UPDATE `work_log` wl
JOIN `sys_member` sm ON sm.system_id = wl.system_id AND sm.account_id = wl.author_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wl.tenant_id
SET wl.author_tenant_member_id = stm.id
WHERE wl.context_type = 'SYSTEM' AND wl.author_tenant_member_id IS NULL;

DELIMITER $$
CREATE TRIGGER `trg_flow_history_event_immutable_update`
BEFORE UPDATE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
CREATE TRIGGER `trg_flow_history_event_immutable_delete`
BEFORE DELETE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
DELIMITER ;

-- source: update/V38__work_log_structured_links.sql
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

-- source: update/V39__application_call_immutable_version.sql
-- Application calls execute the immutable published version, never mutable draft grant rows.
ALTER TABLE `app_call`
  ADD COLUMN `application_version_id` BIGINT NULL AFTER `application_id`,
  ADD KEY `idx_app_call_version` (`application_id`, `application_version_id`),
  ADD CONSTRAINT `fk_app_call_version`
    FOREIGN KEY (`application_id`, `application_version_id`)
    REFERENCES `app_version` (`application_id`, `id`);

-- source: update/V40__direct_action_without_flow.sql
-- A configured DIRECT action saves normally and does not require an unrelated Flow.
ALTER TABLE `flow_trigger_binding`
  DROP FOREIGN KEY `fk_flow_binding_definition`;
ALTER TABLE `flow_trigger_binding` MODIFY COLUMN `flow_id` BIGINT NULL;
ALTER TABLE `flow_trigger_binding` ADD CONSTRAINT `fk_flow_binding_definition`
    FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`);

-- source: update/V41__conversion_completion_clock_repair.sql
-- 修复已确认的转化时间混用：旧容器以 UTC 写 finished_at，创建和成功审计由 +08:00 数据库连接写入。
-- 只处理完成早于创建、加八小时不早于创建，且与同一转化成功审计相差不超过两秒的记录。
-- 不修改来源/目标正文、关系状态、版本、映射快照及原成功审计。未满足证据条件的数据保持原样。
-- 先追加含原值、修复值的永久审计，供追溯和按记录恢复；禁止对所有历史时间统一加八小时。
INSERT INTO audit_event
    (trace_id, request_id, context_type, system_id, tenant_id, event_category,
     event_code, object_type, object_id, result_code, detail_json)
SELECT 'migration-v41-conversion-clock', 'migration-v41-conversion-clock', 'SYSTEM',
       c.system_id, c.tenant_id, 'DATA_REPAIR', 'CONVERSION_CLOCK_REPAIRED',
       'BUSINESS_RECORD_CONVERSION', CAST(c.id AS CHAR), 'SUCCESS',
       JSON_OBJECT('beforeTime', DATE_FORMAT(c.finished_at, '%Y-%m-%d %H:%i:%s.%f'),
                   'afterTime', DATE_FORMAT(DATE_ADD(c.finished_at, INTERVAL 8 HOUR), '%Y-%m-%d %H:%i:%s.%f'),
                   'reason', '转化完成时间与数据库业务时区对齐，原值保留用于恢复')
FROM biz_record_conversion c
WHERE c.status IN ('SUCCEEDED', 'UNLINKED')
  AND c.finished_at < c.created_at
  AND DATE_ADD(c.finished_at, INTERVAL 8 HOUR) >= c.created_at
  AND EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.system_id = c.system_id AND a.tenant_id = c.tenant_id
        AND a.event_code = 'BUSINESS_RECORD_CONVERTED' AND a.result_code = 'SUCCESS'
        AND a.object_type = 'BUSINESS_RECORD' AND a.object_id = CAST(c.source_record_id AS CHAR)
        AND JSON_EXTRACT(a.detail_json, '$.conversionId') = c.id
        AND ABS(TIMESTAMPDIFF(MICROSECOND, DATE_ADD(c.finished_at, INTERVAL 8 HOUR), a.occurred_at)) <= 2000000)
  AND NOT EXISTS (
      SELECT 1 FROM audit_event r
      WHERE r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
        AND r.object_id = CAST(c.id AS CHAR) AND r.request_id = 'migration-v41-conversion-clock');

-- 仅应用刚才审计中的明确修复；原值校验保证重跑不会重复平移或覆盖其他修复。
UPDATE biz_record_conversion c
JOIN audit_event r ON r.object_id = CAST(c.id AS CHAR)
  AND r.system_id = c.system_id AND r.tenant_id = c.tenant_id
  AND r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
  AND r.request_id = 'migration-v41-conversion-clock'
SET c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.afterTime')) AS DATETIME(3))
WHERE c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.beforeTime')) AS DATETIME(3));

-- 恢复方案：从修复审计取 beforeTime，仅在当前值仍等于 afterTime 时按 conversion id 恢复；
-- 原成功审计及修复审计均保留，并为恢复另记审计。不能重写已被后续人工修复的时间。

-- source: update/V42__tenant_default_organization_repair.sql
-- 补齐创建租户时遗漏的默认组织，只处理有效系统的有效租户；不修改手工根部门或已有部门数据。
-- 没有任何顶级部门且 root 编码未被占用才补根；停用根、多根、编码冲突均留待人工核对，不重启用或任意选根。
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
WHERE tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id`
      AND (department.`parent_id` IS NULL OR department.`code` = 'root')
  );

-- 有效成员未配置部门时归入本租户唯一的有效顶级部门；保留已有归属并递增版本，防止旧页面覆盖修复结果。
-- 只补真正活动的账号/系统成员，不把禁用历史成员重新纳入组织；多根或停用根不会被任意作为默认部门。
UPDATE `sys_tenant_member` membership
JOIN `sys_tenant` tenant ON tenant.`id` = membership.`tenant_id` AND tenant.`system_id` = membership.`system_id`
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
JOIN `sys_member` member ON member.`id` = membership.`system_member_id` AND member.`system_id` = membership.`system_id`
JOIN `plat_account` account ON account.`id` = member.`account_id`
JOIN `sys_department` root_department ON root_department.`tenant_id` = membership.`tenant_id`
  AND root_department.`system_id` = membership.`system_id` AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL
  AND tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND member.`status` = 'ACTIVE' AND account.`status` = 'ACTIVE' AND root_department.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` other_root
    WHERE other_root.`tenant_id` = tenant.`id` AND other_root.`parent_id` IS NULL
      AND other_root.`id` <> root_department.`id`
  );

-- source: update/V43__tenant_module_range_share.sql
-- 将现有记录共享扩展为模块数据范围共享，保留授权主键、使用日志及旧时间字段。
-- 先允许模块归属为空，再依据原记录回填；不复制业务数据、不删除历史授权。
ALTER TABLE `biz_tenant_share`
  ADD COLUMN `module_id` BIGINT NULL COMMENT '共享数据所属模块，记录授权由原记录归属回填' AFTER `target_tenant_id`,
  ADD COLUMN `scope_type` VARCHAR(32) NOT NULL DEFAULT 'RECORD' COMMENT '授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围' AFTER `record_id`,
  ADD COLUMN `scope_json` JSON NULL COMMENT '范围条件、字段类型绑定及授予时来源共享权限约束快照' AFTER `scope_type`,
  ADD COLUMN `start_time` DATETIME(3) NULL COMMENT '授权开始生效时间，为空表示授予后立即生效' AFTER `status`;

UPDATE `biz_tenant_share` s
JOIN `biz_record` r ON r.`id` = s.`record_id` AND r.`tenant_id` = s.`source_tenant_id`
SET s.`module_id` = r.`module_id`;

-- 为被修改表的全部字段补充中文业务备注；旧 *_at 映射保持兼容，新增时间仅使用 *_time。
ALTER TABLE `biz_tenant_share`
  COMMENT = '同系统跨租户记录及动态模块范围共享授权，不改变数据所有权',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '共享授权主键，历史使用记录保持引用',
  MODIFY COLUMN `system_id` BIGINT NOT NULL COMMENT '共享双方所属系统',
  MODIFY COLUMN `source_tenant_id` BIGINT NOT NULL COMMENT '拥有业务数据的来源租户',
  MODIFY COLUMN `target_tenant_id` BIGINT NOT NULL COMMENT '获得访问关系的目标租户',
  MODIFY COLUMN `module_id` BIGINT NOT NULL COMMENT '共享数据所属模块',
  MODIFY COLUMN `record_id` BIGINT NULL COMMENT '指定记录授权的原记录主键，动态范围授权为空',
  MODIFY COLUMN `permission_json` JSON NOT NULL COMMENT '明确允许的业务动作，仍与目标用户角色权限取交集',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '授权存储状态：ACTIVE有效，REVOKED已撤销',
  MODIFY COLUMN `expires_at` DATETIME(3) NULL COMMENT '授权失效时间，旧接口和字段保持兼容',
  MODIFY COLUMN `granted_by_member_id` BIGINT NOT NULL COMMENT '来源租户授予授权的系统成员',
  MODIFY COLUMN `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近授予时间，旧字段保持兼容',
  MODIFY COLUMN `revoked_at` DATETIME(3) NULL COMMENT '授权撤销时间，历史授权不删除',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '授权修改的乐观锁版本',
  ADD KEY `idx_biz_share_module_scope` (`system_id`, `target_tenant_id`, `module_id`, `scope_type`, `status`),
  ADD CONSTRAINT `fk_biz_share_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  ADD CONSTRAINT `ck_biz_share_scope` CHECK (
    (`scope_type` = 'RECORD' AND `record_id` IS NOT NULL AND `scope_json` IS NULL)
    OR (`scope_type` = 'DATA_RANGE' AND `record_id` IS NULL AND JSON_TYPE(`scope_json`) = 'OBJECT' AND `scope_json` IS NOT NULL)
  );

-- source: update/V44__application_icon_preset.sql
-- 应用图标只保存平台预设代码，页面从固定图标库选择，不接收任意文件或原始配置。
-- 现有应用统一回填 api；本迁移保留应用、版本、凭证和调用历史。
ALTER TABLE `app_definition`
  ADD COLUMN `icon_code` VARCHAR(32) NOT NULL DEFAULT 'api' COMMENT '应用预设图标代码' AFTER `application_type`,
  COMMENT = '平台或系统范围内的受控访问应用定义',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用主键',
  MODIFY COLUMN `context_type` VARCHAR(32) NOT NULL COMMENT '应用所属上下文：PLATFORM平台、SYSTEM系统',
  MODIFY COLUMN `platform_id` BIGINT NULL COMMENT '所属平台主键',
  MODIFY COLUMN `owner_system_id` BIGINT NULL COMMENT '所属系统主键，平台应用为空',
  MODIFY COLUMN `owner_tenant_id` BIGINT NULL COMMENT '所属租户主键，平台应用为空',
  MODIFY COLUMN `code` VARCHAR(100) NOT NULL COMMENT '当前上下文内唯一的应用编码',
  MODIFY COLUMN `name` VARCHAR(200) NOT NULL COMMENT '应用名称',
  MODIFY COLUMN `description` VARCHAR(1000) NULL COMMENT '应用用途说明',
  MODIFY COLUMN `application_type` VARCHAR(32) NOT NULL COMMENT '应用类型：SERVICE服务调用、WEBHOOK事件回调',
  MODIFY COLUMN `draft_revision` INT NOT NULL DEFAULT 1 COMMENT '草稿修订号',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除',
  MODIFY COLUMN `created_by_account_id` BIGINT NOT NULL COMMENT '创建应用的平台账号主键',
  MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，旧字段保持兼容',
  MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，旧字段保持兼容',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '应用修改的乐观锁版本',
  ADD CONSTRAINT `ck_app_definition_icon` CHECK (`icon_code` IN ('api', 'link', 'workflow', 'database', 'robot', 'message'));

-- source: update/V45__application_credential_expiry_backfill.sql
-- 为旧版本签发且没有失效时间的应用凭证补齐一年有效期。
-- 只更新空值，保留原生效时间、撤销状态、密钥版本和历史调用关联。
UPDATE `app_credential`
SET `expires_at` = DATE_ADD(COALESCE(`valid_from`, `created_at`, CURRENT_TIMESTAMP(3)), INTERVAL 365 DAY)
WHERE `expires_at` IS NULL;

-- source: update/V46__application_bridge_identity.sql
-- 应用跨系统身份映射：草稿保存结构化来源身份与目标执行身份，发布版本只冻结业务引用。
CREATE TABLE `app_bridge_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用身份映射主键',
  `application_id` BIGINT NOT NULL COMMENT '所属应用主键',
  `source_system_id` BIGINT NOT NULL COMMENT '允许调用的来源系统主键',
  `source_tenant_id` BIGINT NOT NULL COMMENT '允许调用的来源租户主键',
  `source_tenant_member_id` BIGINT NOT NULL COMMENT '允许调用的来源租户成员主键',
  `target_system_id` BIGINT NOT NULL COMMENT '目标执行系统主键',
  `target_tenant_id` BIGINT NOT NULL COMMENT '目标执行租户主键',
  `target_tenant_member_id` BIGINT NOT NULL COMMENT '目标执行租户成员主键',
  `status` VARCHAR(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_bridge_identity_source` (`application_id`, `source_system_id`, `source_tenant_id`, `source_tenant_member_id`),
  KEY `idx_app_bridge_identity_target` (`application_id`, `target_system_id`, `target_tenant_id`, `status`),
  CONSTRAINT `fk_app_bridge_identity_application` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_bridge_identity_source_system` FOREIGN KEY (`source_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_bridge_identity_source_tenant` FOREIGN KEY (`source_system_id`, `source_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_source_member` FOREIGN KEY (`source_tenant_id`, `source_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_target_system` FOREIGN KEY (`target_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_bridge_identity_target_tenant` FOREIGN KEY (`target_system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_target_member` FOREIGN KEY (`target_tenant_id`, `target_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_bridge_identity_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用跨系统调用身份映射';

-- source: update/V47__application_provider_connection.sql
-- 外部平台接入复用现有应用授权边界；连接、密钥版本、事件路由和回调去重分别持久化。
CREATE TABLE `app_provider_connection` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台连接主键',
  `application_id` BIGINT NOT NULL COMMENT '所属受控应用主键',
  `provider_code` VARCHAR(32) NOT NULL COMMENT '平台编码：WECOM企业微信、FEISHU飞书、DINGTALK钉钉、WECHAT微信',
  `product_code` VARCHAR(64) NOT NULL COMMENT '平台产品形态编码',
  `connection_key` VARCHAR(64) NOT NULL COMMENT '不可猜测的公开回调连接标识',
  `display_name` VARCHAR(200) NOT NULL COMMENT '连接显示名称',
  `tenant_key` VARCHAR(255) NOT NULL COMMENT '平台租户标识，例如企业微信CorpID',
  `application_key` VARCHAR(255) NULL COMMENT '平台应用标识，例如企业微信AgentID',
  `status` VARCHAR(32) NOT NULL COMMENT '连接状态：DRAFT草稿、ACTIVE启用、DISABLED停用',
  `diagnostic_status` VARCHAR(32) NOT NULL COMMENT '诊断状态：NOT_RUN未执行、LOCAL_PASSED本地协议通过、FAILED失败、EXTERNAL_PASSED外部联通通过',
  `diagnostic_summary` VARCHAR(1000) NULL COMMENT '不含敏感材料的最近诊断摘要',
  `diagnostic_time` DATETIME(3) NULL COMMENT '最近诊断时间',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_connection_key` (`connection_key`),
  UNIQUE KEY `uk_app_provider_connection_tenant` (`application_id`, `provider_code`, `product_code`, `tenant_key`),
  KEY `idx_app_provider_connection_status` (`application_id`, `status`),
  CONSTRAINT `fk_app_provider_connection_application` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_provider_connection_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_connection_provider` CHECK (`provider_code` IN ('WECOM', 'FEISHU', 'DINGTALK', 'WECHAT')),
  CONSTRAINT `chk_app_provider_connection_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'DISABLED')),
  CONSTRAINT `chk_app_provider_connection_diagnostic` CHECK (`diagnostic_status` IN ('NOT_RUN', 'LOCAL_PASSED', 'FAILED', 'EXTERNAL_PASSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台连接';

CREATE TABLE `app_provider_secret` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台密钥版本主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `secret_type` VARCHAR(32) NOT NULL COMMENT '密钥用途：API_SECRET接口密钥、CALLBACK_TOKEN回调令牌、ENCODING_AES_KEY回调加密密钥',
  `secret_version` INT NOT NULL COMMENT '同一连接同一用途的递增版本号',
  `secret_ciphertext` VARCHAR(4000) NOT NULL COMMENT '使用应用主密钥加密后的密文',
  `secret_hint` VARCHAR(32) NOT NULL COMMENT '仅用于辨认版本的脱敏尾号',
  `status` VARCHAR(32) NOT NULL COMMENT '密钥状态：ACTIVE启用、REVOKED已撤销',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `revoked_time` DATETIME(3) NULL COMMENT '撤销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_secret_version` (`connection_id`, `secret_type`, `secret_version`),
  KEY `idx_app_provider_secret_active` (`connection_id`, `secret_type`, `status`),
  CONSTRAINT `fk_app_provider_secret_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_secret_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_secret_type` CHECK (`secret_type` IN ('API_SECRET', 'CALLBACK_TOKEN', 'ENCODING_AES_KEY')),
  CONSTRAINT `chk_app_provider_secret_status` CHECK (`status` IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台密钥版本';

CREATE TABLE `app_provider_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台事件路由主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `event_code` VARCHAR(100) NOT NULL COMMENT '规范化平台事件编码',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '已发布应用授权资源类型',
  `resource_id` VARCHAR(100) NOT NULL COMMENT '已发布应用授权资源业务编码',
  `action_code` VARCHAR(64) NOT NULL COMMENT '已发布应用授权动作编码',
  `target_system_id` BIGINT NULL COMMENT '目标系统主键',
  `target_tenant_id` BIGINT NULL COMMENT '目标租户主键',
  `status` VARCHAR(32) NOT NULL COMMENT '路由状态：ACTIVE启用、DISABLED停用',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_route_event` (`connection_id`, `event_code`),
  KEY `idx_app_provider_route_target` (`resource_type`, `resource_id`, `action_code`, `status`),
  CONSTRAINT `fk_app_provider_route_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_route_system` FOREIGN KEY (`target_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_provider_route_tenant` FOREIGN KEY (`target_system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `chk_app_provider_route_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台事件路由';

CREATE TABLE `app_provider_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台回调事件主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `provider_event_id` VARCHAR(255) NOT NULL COMMENT '平台事件幂等标识或服务端稳定摘要',
  `event_code` VARCHAR(100) NOT NULL COMMENT '规范化平台事件编码',
  `sender_external_id` VARCHAR(255) NULL COMMENT '平台侧发送人标识',
  `payload_hash` VARCHAR(64) NOT NULL COMMENT '解密后原始载荷SHA-256摘要',
  `status` VARCHAR(32) NOT NULL COMMENT '处理状态：RECEIVED已接收、PROCESSED已完成、IGNORED无路由、FAILED失败',
  `application_call_id` BIGINT NULL COMMENT '进入应用网关后生成的调用主键',
  `response_code` VARCHAR(64) NULL COMMENT '安全的处理结果编码',
  `received_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
  `processed_time` DATETIME(3) NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_event_dedup` (`connection_id`, `provider_event_id`),
  KEY `idx_app_provider_event_status` (`connection_id`, `status`, `received_time`),
  CONSTRAINT `fk_app_provider_event_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_event_call` FOREIGN KEY (`application_call_id`) REFERENCES `app_call` (`id`),
  CONSTRAINT `chk_app_provider_event_status` CHECK (`status` IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台回调事件';

-- source: update/V48__application_provider_identity_delivery.sql
-- 企业微信成员映射把平台侧用户绑定到当前应用范围内的真实成员；外部投递扩展继续复用统一消息和投递记录。
CREATE TABLE `app_provider_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台成员映射主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `external_user_id` VARCHAR(255) NOT NULL COMMENT '平台侧稳定用户标识，例如企业微信UserID',
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员',
  `target_platform_id` BIGINT NULL COMMENT '目标平台主键',
  `target_platform_member_id` BIGINT NULL COMMENT '目标平台成员主键',
  `target_system_id` BIGINT NULL COMMENT '目标系统主键',
  `target_tenant_id` BIGINT NULL COMMENT '目标租户主键',
  `target_tenant_member_id` BIGINT NULL COMMENT '目标租户成员主键',
  `status` VARCHAR(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_identity_external` (`connection_id`, `external_user_id`),
  KEY `idx_app_provider_identity_target_platform` (`target_platform_id`, `target_platform_member_id`, `status`),
  KEY `idx_app_provider_identity_target_system` (`target_system_id`, `target_tenant_id`, `target_tenant_member_id`, `status`),
  CONSTRAINT `fk_app_provider_identity_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_identity_platform_member` FOREIGN KEY (`target_platform_id`, `target_platform_member_id`) REFERENCES `plat_member` (`platform_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_tenant_member` FOREIGN KEY (`target_tenant_id`, `target_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_identity_status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT `chk_app_provider_identity_target` CHECK (
    (`target_type` = 'PLATFORM' AND `target_platform_id` IS NOT NULL AND `target_platform_member_id` IS NOT NULL
      AND `target_system_id` IS NULL AND `target_tenant_id` IS NULL AND `target_tenant_member_id` IS NULL)
    OR
    (`target_type` = 'SYSTEM' AND `target_platform_id` IS NULL AND `target_platform_member_id` IS NULL
      AND `target_system_id` IS NOT NULL AND `target_tenant_id` IS NOT NULL AND `target_tenant_member_id` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台成员映射';

CREATE TABLE `app_provider_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台消息投递扩展主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `identity_id` BIGINT NOT NULL COMMENT '目标外部平台成员映射主键',
  `message_id` BIGINT NOT NULL COMMENT '统一消息主键',
  `message_delivery_id` BIGINT NOT NULL COMMENT '统一消息投递主键',
  `idempotency_key` VARCHAR(255) NOT NULL COMMENT '同一连接内的业务幂等键',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化投递请求SHA-256摘要',
  `deep_link` VARCHAR(1000) NOT NULL COMMENT '按当前上下文生成的站内业务深链',
  `status` VARCHAR(32) NOT NULL COMMENT '投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销',
  `provider_message_id` VARCHAR(255) NULL COMMENT '平台返回的消息标识',
  `error_code` VARCHAR(100) NULL COMMENT '不含敏感信息的最近错误编码',
  `created_by_account_id` BIGINT NOT NULL COMMENT '发起人平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `sent_time` DATETIME(3) NULL COMMENT '实际发送时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_delivery_idempotency` (`connection_id`, `idempotency_key`),
  UNIQUE KEY `uk_app_provider_delivery_message_delivery` (`message_delivery_id`),
  KEY `idx_app_provider_delivery_status` (`connection_id`, `status`, `create_time`),
  CONSTRAINT `fk_app_provider_delivery_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_delivery_identity` FOREIGN KEY (`identity_id`) REFERENCES `app_provider_identity` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message_delivery` FOREIGN KEY (`message_delivery_id`) REFERENCES `msg_delivery` (`id`),
  CONSTRAINT `fk_app_provider_delivery_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_delivery_status` CHECK (`status` IN ('PENDING', 'BLOCKED_EXTERNAL', 'SENT', 'DELIVERED', 'FAILED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台消息投递扩展';

-- source: update/V49__file_storage_credential_capacity.sql
-- 对象存储访问密钥使用随机向量加密，密文长度不能受早期短引用字段限制。
ALTER TABLE `file_storage_backend`
  MODIFY COLUMN `credential_ref` TEXT NULL COMMENT '对象存储访问凭证密文，仅服务端解密，接口不得回传';

-- source: update/V50__work_task_lifecycle.sql
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

-- source: update/V51__work_task_soft_delete.sql
-- 任务删除保留审计和历史记录，列表与详情按 delete_time 隐藏，避免物理删除破坏关联证据。

ALTER TABLE `work_task`
  ADD COLUMN `delete_time` DATETIME(3) NULL COMMENT '任务删除时间' AFTER `archive_time`,
  ADD KEY `idx_work_task_delete` (`system_id`, `tenant_id`, `delete_time`);

-- source: update/V52__work_log_business_flow.sql
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

-- source: update/V53__todo_contact_rule.sql
-- 今日联系规则把已发布模块的日期字段投影为当前负责人的待办，不复制业务记录，也不要求用户填写 JSON。
CREATE TABLE `todo_contact_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '今日联系规则主键',
  `context_type` VARCHAR(32) NOT NULL COMMENT '配置上下文类型：平台或系统',
  `platform_id` BIGINT NOT NULL COMMENT '所属平台主键',
  `system_id` BIGINT NULL COMMENT '所属系统主键，平台配置时为空',
  `tenant_id` BIGINT NULL COMMENT '所属租户主键，平台配置时为空',
  `scope_key` VARCHAR(160) NOT NULL COMMENT '平台、系统和租户组成的稳定配置范围键',
  `name` VARCHAR(200) NOT NULL COMMENT '规则显示名称',
  `module_code` VARCHAR(100) NOT NULL COMMENT '已发布业务模块编码',
  `module_name_snapshot` VARCHAR(200) NOT NULL COMMENT '保存规则时的业务模块名称快照',
  `contact_field_code` VARCHAR(100) NOT NULL COMMENT '用于计算联系日期的字段编码',
  `contact_field_name_snapshot` VARCHAR(200) NOT NULL COMMENT '保存规则时的日期字段名称快照',
  `contact_field_type` VARCHAR(32) NOT NULL COMMENT '联系字段类型：日期或日期时间',
  `lead_days` INT NOT NULL DEFAULT 0 COMMENT '提前生成待办的天数，零表示字段日期当天',
  `priority` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '生成待办的优先级',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '规则状态：草稿、已发布或已停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建规则的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '规则创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '规则最后更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_todo_contact_rule_scope` (`scope_key`, `module_code`, `contact_field_code`, `lead_days`),
  KEY `idx_todo_contact_rule_runtime` (`system_id`, `tenant_id`, `status`),
  CONSTRAINT `fk_todo_contact_rule_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_todo_contact_rule_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_todo_contact_rule_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`),
  CONSTRAINT `fk_todo_contact_rule_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务模块日期字段的今日联系待办生成规则';
