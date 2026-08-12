-- Extend the existing confirmation ledger with the two bounded Batch99 kinds.
-- No executable configuration payload or permission code is stored in a new column.

ALTER TABLE un_ai_config_artifact_proposal
    DROP CHECK ck_ai_config_artifact_kind,
    ADD CONSTRAINT ck_ai_config_artifact_kind CHECK (
        artifact_kind IN (
            'SELECTION_FIELD',
            'PAGE_LAYOUT',
            'FILTER_SCENARIO',
            'FIELD_PERMISSION_STAGE'));
