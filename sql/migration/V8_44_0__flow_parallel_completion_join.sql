-- Adjacent completion steps may form one durable all-success parallel stage.
-- Existing rows retain NULL group identity and therefore remain single-member
-- sequential stages without data rewriting.

ALTER TABLE un_flow_completion_execution
    ADD COLUMN parallel_group VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER execution_type,
    DROP INDEX uk_flow_completion_one_active,
    ADD KEY idx_flow_completion_active_stage (
        system_id, tenant_id, instance_id, parallel_group,
        status, ordinal, execution_id
    ),
    ADD KEY idx_flow_completion_stage_join (
        system_id, tenant_id, instance_id, ordinal,
        status, parallel_group, execution_id
    ),
    ADD CONSTRAINT ck_flow_completion_parallel_group CHECK (
        parallel_group IS NULL
        OR parallel_group REGEXP '^[a-z][a-z0-9_]{0,63}$'
    );

ALTER TABLE un_flow_completion_attempt
    DROP CHECK ck_flow_completion_attempt_event,
    ADD CONSTRAINT ck_flow_completion_attempt_event CHECK (
        event_type IN (
            'ACTIVATED', 'STARTED', 'STAGE_JOINED', 'CLAIMED',
            'LEASE_EXPIRED', 'RETRIED', 'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    );
