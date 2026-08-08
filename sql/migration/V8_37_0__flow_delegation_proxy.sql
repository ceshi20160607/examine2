-- Durable, tenant-scoped approval delegation with explicit on-behalf decision audit.

CREATE TABLE un_flow_approval_delegation (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    delegation_id BIGINT NOT NULL,
    delegator_member_id BIGINT NOT NULL,
    delegate_member_id BIGINT NOT NULL,
    definition_id BIGINT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_by BIGINT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, delegation_id),
    KEY idx_flow_delegation_outgoing (
        system_id, tenant_id, delegator_member_id, status,
        starts_at, ends_at, delegation_id
    ),
    KEY idx_flow_delegation_incoming (
        system_id, tenant_id, delegate_member_id, status,
        starts_at, ends_at, delegation_id
    ),
    KEY idx_flow_delegation_definition (
        system_id, tenant_id, definition_id, status,
        starts_at, ends_at, delegation_id
    ),
    CONSTRAINT fk_flow_delegation_delegator FOREIGN KEY (
        system_id, delegator_member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_delegation_delegate FOREIGN KEY (
        system_id, delegate_member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_delegation_creator FOREIGN KEY (
        system_id, created_by, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_delegation_revoker FOREIGN KEY (
        system_id, revoked_by, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_delegation_definition FOREIGN KEY (
        system_id, tenant_id, definition_id
    ) REFERENCES un_flow_definition_draft (
        system_id, tenant_id, definition_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_delegation_identity CHECK (
        delegation_id > 0
        AND delegator_member_id > 0
        AND delegate_member_id > 0
        AND delegator_member_id <> delegate_member_id
        AND created_by > 0
        AND (definition_id IS NULL OR definition_id > 0)
    ),
    CONSTRAINT ck_flow_delegation_window CHECK (
        ends_at > starts_at
        AND ends_at <= DATE_ADD(starts_at, INTERVAL 180 DAY)
    ),
    CONSTRAINT ck_flow_delegation_status CHECK (
        status IN ('SCHEDULED', 'ACTIVE', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_flow_delegation_revocation CHECK (
        (
            status IN ('SCHEDULED', 'ACTIVE', 'EXPIRED')
            AND revoked_by IS NULL
            AND revoked_at IS NULL
        )
        OR (
            status = 'REVOKED'
            AND revoked_by IS NOT NULL
            AND revoked_at IS NOT NULL
            AND revoked_at >= created_at
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_flow_history_event
    ADD COLUMN represented_member_id BIGINT NULL
        AFTER actor_id,
    ADD COLUMN delegation_id BIGINT NULL
        AFTER represented_member_id,
    ADD KEY idx_flow_history_represented (
        system_id, tenant_id, represented_member_id, occurred_at, instance_id
    ),
    ADD CONSTRAINT fk_flow_history_represented_member FOREIGN KEY (
        system_id, represented_member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_flow_history_delegation FOREIGN KEY (
        system_id, tenant_id, delegation_id
    ) REFERENCES un_flow_approval_delegation (
        system_id, tenant_id, delegation_id
    ) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_flow_history_delegation_actor CHECK (
        (
            represented_member_id IS NULL
            AND delegation_id IS NULL
        )
        OR (
            represented_member_id IS NOT NULL
            AND represented_member_id > 0
            AND delegation_id IS NOT NULL
            AND delegation_id > 0
            AND actor_id <> represented_member_id
        )
    );
