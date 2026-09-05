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
