-- A rejected call may fail before a matching grant exists; retain that attempt for audit.

ALTER TABLE `app_call`
  DROP FOREIGN KEY `fk_app_call_grant`;

ALTER TABLE `app_call`
  MODIFY COLUMN `grant_id` BIGINT NULL;

ALTER TABLE `app_call`
  ADD CONSTRAINT `fk_app_call_grant` FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`);
