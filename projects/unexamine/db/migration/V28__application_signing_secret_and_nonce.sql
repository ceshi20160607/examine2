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
