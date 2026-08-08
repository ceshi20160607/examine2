-- P4-B2 completes the single-record lifecycle without physical deletion.
ALTER TABLE un_module_record
    DROP CHECK ck_record_prior_status;

ALTER TABLE un_module_record
    ADD CONSTRAINT ck_record_prior_status CHECK (
        (status = 'TRASHED'
            AND prior_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'EXPIRED')
            AND deleted_at IS NOT NULL)
        OR (status <> 'TRASHED' AND prior_status IS NULL AND deleted_at IS NULL)
    ),
    ADD KEY idx_record_draft_expiry (status, draft_expires_at, record_id);
