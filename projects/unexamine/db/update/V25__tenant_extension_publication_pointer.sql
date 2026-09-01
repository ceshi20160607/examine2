ALTER TABLE `cfg_tenant_extension_version`
  ADD UNIQUE KEY `uk_cfg_extension_version_owner_id` (`extension_id`, `id`);

ALTER TABLE `cfg_tenant_extension`
  ADD COLUMN `current_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `updated_by_member_id` BIGINT NULL AFTER `current_version_id`,
  ADD CONSTRAINT `fk_cfg_tenant_extension_current_version`
    FOREIGN KEY (`id`, `current_version_id`) REFERENCES `cfg_tenant_extension_version` (`extension_id`, `id`),
  ADD CONSTRAINT `fk_cfg_tenant_extension_updater`
    FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`);
