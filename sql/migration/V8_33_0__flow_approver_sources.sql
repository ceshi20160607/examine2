ALTER TABLE un_flow_definition_draft
    ADD COLUMN approver_sources JSON NULL
        AFTER inclusive_branches;

ALTER TABLE un_flow_definition_version
    ADD COLUMN approver_sources JSON NULL
        AFTER inclusive_branches;
