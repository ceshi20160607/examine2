-- Inclusive all-match split reuses durable branch executions at runtime.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN inclusive_branches JSON NULL
        AFTER parallel_branches,
    ADD CONSTRAINT ck_flow_draft_inclusive_gateway CHECK (
        (
            inclusive_branches IS NULL
            OR (gateway_branches IS NULL AND parallel_branches IS NULL)
        )
        AND (
            inclusive_branches IS NULL
            OR (
                JSON_TYPE(inclusive_branches) = 'ARRAY'
                AND JSON_LENGTH(inclusive_branches) BETWEEN 2 AND 5
            )
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN inclusive_branches JSON NULL
        AFTER parallel_branches,
    ADD CONSTRAINT ck_flow_version_inclusive_gateway CHECK (
        (
            inclusive_branches IS NULL
            OR (gateway_branches IS NULL AND parallel_branches IS NULL)
        )
        AND (
            inclusive_branches IS NULL
            OR (
                JSON_TYPE(inclusive_branches) = 'ARRAY'
                AND JSON_LENGTH(inclusive_branches) BETWEEN 2 AND 5
            )
        )
    );
