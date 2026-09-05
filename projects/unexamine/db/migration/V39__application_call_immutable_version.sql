-- Application calls execute the immutable published version, never mutable draft grant rows.
ALTER TABLE `app_call`
  ADD COLUMN `application_version_id` BIGINT NULL AFTER `application_id`,
  ADD KEY `idx_app_call_version` (`application_id`, `application_version_id`),
  ADD CONSTRAINT `fk_app_call_version`
    FOREIGN KEY (`application_id`, `application_version_id`)
    REFERENCES `app_version` (`application_id`, `id`);

