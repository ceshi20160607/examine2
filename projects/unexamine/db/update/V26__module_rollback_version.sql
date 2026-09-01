ALTER TABLE `cfg_module_version`
  DROP INDEX `uk_cfg_module_version_revision`,
  ADD KEY `idx_cfg_module_version_revision` (`module_id`, `draft_revision`);
