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
