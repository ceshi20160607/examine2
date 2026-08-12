-- Module-owned current Flow projection for runtime records.

CREATE TABLE un_module_record_flow_state (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, record_id),
    UNIQUE KEY uk_record_flow_instance (
        system_id, tenant_id, instance_id
    ),
    KEY idx_record_flow_record_fk (
        system_id, tenant_id, logical_module_id, record_id
    ),
    KEY idx_record_flow_status (
        system_id, tenant_id, logical_module_id, status, record_id
    ),
    CONSTRAINT fk_record_flow_record FOREIGN KEY (
        system_id, tenant_id, logical_module_id, record_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, logical_module_id, record_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_record_flow_identity CHECK (
        system_id > 0
        AND tenant_id > 0
        AND record_id > 0
        AND logical_module_id > 0
        AND instance_id > 0
        AND created_by > 0
        AND updated_by > 0
    ),
    CONSTRAINT ck_record_flow_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'TERMINATED')
    ),
    CONSTRAINT ck_record_flow_version CHECK (
        version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
