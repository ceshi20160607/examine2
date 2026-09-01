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
