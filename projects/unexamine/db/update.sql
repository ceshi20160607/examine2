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
