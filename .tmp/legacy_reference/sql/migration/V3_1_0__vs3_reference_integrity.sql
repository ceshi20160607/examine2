-- VS3 hardening: database-verifiable scoped component trees and polymorphic config references.

ALTER TABLE un_module_dictionary_item
    ADD UNIQUE KEY uk_module_dict_item_system_id (system_id, id);

ALTER TABLE un_module_page_component
    ADD UNIQUE KEY uk_module_page_component_page_id (system_id, page_id, id);

ALTER TABLE un_module_page_component
    DROP FOREIGN KEY fk_module_page_component_parent;

ALTER TABLE un_module_page_component
    ADD CONSTRAINT fk_module_page_component_parent_page
        FOREIGN KEY (system_id, page_id, parent_component_id)
        REFERENCES un_module_page_component (system_id, page_id, id);

ALTER TABLE un_module_config_reference
    ADD COLUMN source_module_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'MODULE' THEN source_id END) STORED,
    ADD COLUMN source_field_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'FIELD' THEN source_id END) STORED,
    ADD COLUMN source_page_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'PAGE' THEN source_id END) STORED,
    ADD COLUMN source_component_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'COMPONENT' THEN source_id END) STORED,
    ADD COLUMN source_action_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'ACTION' THEN source_id END) STORED,
    ADD COLUMN source_rule_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'RULE' THEN source_id END) STORED,
    ADD COLUMN source_dictionary_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'DICTIONARY' THEN source_id END) STORED,
    ADD COLUMN source_dictionary_item_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN source_type = 'DICTIONARY_ITEM' THEN source_id END) STORED,
    ADD COLUMN target_module_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'MODULE' THEN target_id END) STORED,
    ADD COLUMN target_field_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'FIELD' THEN target_id END) STORED,
    ADD COLUMN target_page_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'PAGE' THEN target_id END) STORED,
    ADD COLUMN target_action_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'ACTION' THEN target_id END) STORED,
    ADD COLUMN target_rule_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'RULE' THEN target_id END) STORED,
    ADD COLUMN target_dictionary_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'DICTIONARY' THEN target_id END) STORED,
    ADD COLUMN target_dictionary_item_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'DICTIONARY_ITEM' THEN target_id END) STORED,
    ADD COLUMN target_permission_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN target_type = 'PERMISSION' THEN target_id END) STORED;

ALTER TABLE un_module_config_reference
    ADD CONSTRAINT fk_module_config_ref_source_module
        FOREIGN KEY (system_id, source_module_id) REFERENCES un_module_definition (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_field
        FOREIGN KEY (system_id, source_field_id) REFERENCES un_module_field (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_page
        FOREIGN KEY (system_id, source_page_id) REFERENCES un_module_page (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_component
        FOREIGN KEY (system_id, source_component_id) REFERENCES un_module_page_component (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_action
        FOREIGN KEY (system_id, source_action_id) REFERENCES un_module_action (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_rule
        FOREIGN KEY (system_id, source_rule_id) REFERENCES un_module_rule (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_dictionary
        FOREIGN KEY (system_id, source_dictionary_id) REFERENCES un_module_dictionary (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_source_dictionary_item
        FOREIGN KEY (system_id, source_dictionary_item_id) REFERENCES un_module_dictionary_item (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_module
        FOREIGN KEY (system_id, target_module_id) REFERENCES un_module_definition (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_field
        FOREIGN KEY (system_id, target_field_id) REFERENCES un_module_field (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_page
        FOREIGN KEY (system_id, target_page_id) REFERENCES un_module_page (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_action
        FOREIGN KEY (system_id, target_action_id) REFERENCES un_module_action (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_rule
        FOREIGN KEY (system_id, target_rule_id) REFERENCES un_module_rule (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_dictionary
        FOREIGN KEY (system_id, target_dictionary_id) REFERENCES un_module_dictionary (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_dictionary_item
        FOREIGN KEY (system_id, target_dictionary_item_id) REFERENCES un_module_dictionary_item (system_id, id),
    ADD CONSTRAINT fk_module_config_ref_target_permission
        FOREIGN KEY (target_permission_id) REFERENCES un_plat_permission (id);