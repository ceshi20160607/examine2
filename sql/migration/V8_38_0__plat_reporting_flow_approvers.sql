-- Durable organization reporting lines consumed by contextual Flow approver
-- sources. Relationship history is retained when a current assignment is
-- cleared or replaced.

ALTER TABLE un_plat_department
    ADD UNIQUE KEY uk_plat_department_system_tenant_id (
        system_id, tenant_id, id
    );

CREATE TABLE un_plat_member_manager_assignment (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    manager_member_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL,
    cleared_by BIGINT NULL,
    cleared_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_member_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN member_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (system_id, tenant_id, assignment_id),
    UNIQUE KEY uk_plat_member_manager_active (
        system_id, tenant_id, active_member_id
    ),
    KEY idx_plat_member_manager_target (
        system_id, tenant_id, manager_member_id, status, member_id
    ),
    KEY idx_plat_member_manager_history (
        system_id, tenant_id, member_id, assigned_at, assignment_id
    ),
    CONSTRAINT fk_plat_member_manager_member FOREIGN KEY (
        system_id, member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_member_manager_manager FOREIGN KEY (
        system_id, manager_member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_member_manager_assigner FOREIGN KEY (
        assigned_by
    ) REFERENCES un_plat_account (
        id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_member_manager_clearer FOREIGN KEY (
        cleared_by
    ) REFERENCES un_plat_account (
        id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_plat_member_manager_identity CHECK (
        assignment_id > 0
        AND member_id > 0
        AND manager_member_id > 0
        AND member_id <> manager_member_id
        AND assigned_by > 0
    ),
    CONSTRAINT ck_plat_member_manager_status CHECK (
        status IN ('ACTIVE', 'CLEARED')
    ),
    CONSTRAINT ck_plat_member_manager_audit CHECK (
        (
            status = 'ACTIVE'
            AND cleared_by IS NULL
            AND cleared_at IS NULL
        )
        OR (
            status = 'CLEARED'
            AND cleared_by IS NOT NULL
            AND cleared_at IS NOT NULL
            AND cleared_at >= assigned_at
        )
    ),
    CONSTRAINT ck_plat_member_manager_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_plat_department_leader_assignment (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    leader_member_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL,
    cleared_by BIGINT NULL,
    cleared_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_department_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN department_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (system_id, tenant_id, assignment_id),
    UNIQUE KEY uk_plat_department_leader_active (
        system_id, tenant_id, active_department_id
    ),
    KEY idx_plat_department_leader_member (
        system_id, tenant_id, leader_member_id, status, department_id
    ),
    KEY idx_plat_department_leader_history (
        system_id, tenant_id, department_id, assigned_at, assignment_id
    ),
    CONSTRAINT fk_plat_department_leader_department FOREIGN KEY (
        system_id, tenant_id, department_id
    ) REFERENCES un_plat_department (
        system_id, tenant_id, id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_department_leader_member FOREIGN KEY (
        system_id, leader_member_id, tenant_id
    ) REFERENCES un_plat_member_tenant (
        system_id, member_id, tenant_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_department_leader_assigner FOREIGN KEY (
        assigned_by
    ) REFERENCES un_plat_account (
        id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_plat_department_leader_clearer FOREIGN KEY (
        cleared_by
    ) REFERENCES un_plat_account (
        id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_plat_department_leader_identity CHECK (
        assignment_id > 0
        AND department_id > 0
        AND leader_member_id > 0
        AND assigned_by > 0
    ),
    CONSTRAINT ck_plat_department_leader_status CHECK (
        status IN ('ACTIVE', 'CLEARED')
    ),
    CONSTRAINT ck_plat_department_leader_audit CHECK (
        (
            status = 'ACTIVE'
            AND cleared_by IS NULL
            AND cleared_at IS NULL
        )
        OR (
            status = 'CLEARED'
            AND cleared_by IS NOT NULL
            AND cleared_at IS NOT NULL
            AND cleared_at >= assigned_at
        )
    ),
    CONSTRAINT ck_plat_department_leader_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
