-- A configured DIRECT action saves normally and does not require an unrelated Flow.
ALTER TABLE `flow_trigger_binding`
  DROP FOREIGN KEY `fk_flow_binding_definition`;
ALTER TABLE `flow_trigger_binding` MODIFY COLUMN `flow_id` BIGINT NULL;
ALTER TABLE `flow_trigger_binding` ADD CONSTRAINT `fk_flow_binding_definition`
    FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`);
