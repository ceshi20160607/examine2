-- One bounded executable exclusive gateway before the sequential approval route.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN gateway_branches JSON NULL
        AFTER trigger_requester_id,
    ADD CONSTRAINT ck_flow_draft_gateway CHECK (
        gateway_branches IS NULL
        OR (
            JSON_TYPE(gateway_branches) = 'ARRAY'
            AND JSON_LENGTH(gateway_branches) BETWEEN 2 AND 6
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN gateway_branches JSON NULL
        AFTER trigger_requester_id,
    ADD CONSTRAINT ck_flow_version_gateway CHECK (
        gateway_branches IS NULL
        OR (
            JSON_TYPE(gateway_branches) = 'ARRAY'
            AND JSON_LENGTH(gateway_branches) BETWEEN 2 AND 6
        )
    );
