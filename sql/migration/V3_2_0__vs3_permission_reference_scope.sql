-- VS3 hardening: permission references must stay inside the owning system.

ALTER TABLE un_plat_permission
    ADD UNIQUE KEY uk_plat_permission_system_id (system_id, id);

ALTER TABLE un_module_config_reference
    DROP FOREIGN KEY fk_module_config_ref_target_permission,
    ADD CONSTRAINT fk_module_config_ref_target_permission_system
        FOREIGN KEY (system_id, target_permission_id)
        REFERENCES un_plat_permission (system_id, id);
