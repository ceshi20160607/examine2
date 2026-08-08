-- System-admin inheritance and confirmed directory synchronization for published platform identity providers.
-- Provider secrets remain owned by un_plat_identity_provider; this slice stores only provider ids and projections.

ALTER TABLE un_plat_identity_binding
    DROP INDEX uk_plat_identity_binding_external,
    DROP INDEX uk_plat_identity_binding_account,
    ADD COLUMN scope_system_key BIGINT GENERATED ALWAYS AS (IFNULL(system_id, 0)) STORED,
    ADD COLUMN scope_tenant_key BIGINT GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED,
    ADD UNIQUE KEY uk_plat_identity_binding_external_scope
        (provider_id, external_user_id, scope_system_key, scope_tenant_key),
    ADD UNIQUE KEY uk_plat_identity_binding_account_scope
        (provider_id, account_id, scope_system_key, scope_tenant_key);

CREATE TABLE un_plat_system_identity_policy (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    allowed_domains_json JSON NOT NULL,
    jit_system_member BOOLEAN NOT NULL DEFAULT FALSE,
    unmatched_action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REQUIRE_REVIEW',
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_interval_minutes INT NULL,
    next_sync_at DATETIME(3) NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    last_confirmed_snapshot_id BIGINT NULL,
    last_sync_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_system_identity_policy_scope (system_id, tenant_id, provider_id),
    KEY idx_plat_system_identity_policy_schedule (status, schedule_enabled, next_sync_at),
    CONSTRAINT fk_plat_system_identity_policy_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_plat_system_identity_policy_provider FOREIGN KEY (provider_id)
        REFERENCES un_plat_identity_provider (id),
    CONSTRAINT ck_plat_system_identity_policy_status CHECK (status IN ('DRAFT','ACTIVE','DISABLED')),
    CONSTRAINT ck_plat_system_identity_policy_unmatched CHECK (unmatched_action IN ('REQUIRE_REVIEW','SKIP')),
    CONSTRAINT ck_plat_system_identity_policy_schedule CHECK (
        (schedule_enabled = FALSE AND schedule_interval_minutes IS NULL AND next_sync_at IS NULL)
        OR (schedule_enabled = TRUE AND schedule_interval_minutes BETWEEN 15 AND 10080 AND next_sync_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_identity_sync_snapshot (
    id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    source_version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    department_count INT NOT NULL DEFAULT 0,
    employee_count INT NOT NULL DEFAULT 0,
    matched_count INT NOT NULL DEFAULT 0,
    create_count INT NOT NULL DEFAULT 0,
    unmatched_count INT NOT NULL DEFAULT 0,
    confirmed_at DATETIME(3) NULL,
    confirmed_by BIGINT NULL,
    applied_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_identity_sync_snapshot_source (policy_id, source_version),
    KEY idx_plat_identity_sync_snapshot_status (policy_id, status, created_at),
    CONSTRAINT fk_plat_identity_sync_snapshot_policy FOREIGN KEY (policy_id)
        REFERENCES un_plat_system_identity_policy (id),
    CONSTRAINT fk_plat_identity_sync_snapshot_confirmer FOREIGN KEY (confirmed_by)
        REFERENCES un_plat_account (id),
    CONSTRAINT ck_plat_identity_sync_snapshot_status CHECK (status IN ('DRAFT','CONFIRMED','QUEUED','APPLIED','PARTIAL_FAILED')),
    CONSTRAINT ck_plat_identity_sync_snapshot_counts CHECK (
        department_count >= 0 AND employee_count >= 0 AND matched_count >= 0
        AND create_count >= 0 AND unmatched_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_identity_sync_item (
    id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    item_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    parent_external_id VARCHAR(255) NULL,
    email_normalized VARCHAR(254) NULL,
    department_external_id VARCHAR(255) NULL,
    target_department_id BIGINT NULL,
    target_account_id BIGINT NULL,
    target_member_id BIGINT NULL,
    proposed_action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issue_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    attributes_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_identity_sync_item_external (snapshot_id, item_kind, external_id),
    KEY idx_plat_identity_sync_item_issue (snapshot_id, issue_code, item_kind),
    CONSTRAINT fk_plat_identity_sync_item_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES un_plat_identity_sync_snapshot (id) ON DELETE CASCADE,
    CONSTRAINT fk_plat_identity_sync_item_department FOREIGN KEY (target_department_id)
        REFERENCES un_plat_department (id),
    CONSTRAINT fk_plat_identity_sync_item_account FOREIGN KEY (target_account_id)
        REFERENCES un_plat_account (id),
    CONSTRAINT fk_plat_identity_sync_item_member FOREIGN KEY (target_member_id)
        REFERENCES un_plat_member (id),
    CONSTRAINT ck_plat_identity_sync_item_kind CHECK (item_kind IN ('DEPARTMENT','EMPLOYEE')),
    CONSTRAINT ck_plat_identity_sync_item_action CHECK (proposed_action IN ('BIND','CREATE','UPDATE','SKIP','UNMATCHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_identity_sync_failure (
    id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    snapshot_item_id BIGINT NOT NULL,
    failure_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_message VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_identity_sync_failure_item (job_id, snapshot_item_id),
    KEY idx_plat_identity_sync_failure_job (job_id, created_at),
    CONSTRAINT fk_plat_identity_sync_failure_job FOREIGN KEY (job_id)
        REFERENCES un_sys_job (id) ON DELETE CASCADE,
    CONSTRAINT fk_plat_identity_sync_failure_item FOREIGN KEY (snapshot_item_id)
        REFERENCES un_plat_identity_sync_item (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
