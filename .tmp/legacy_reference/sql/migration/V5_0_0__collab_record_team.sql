-- P4-D1 introduces the isolated collaboration persistence boundary.
-- The aggregate header is locked for every mutation; member rows retain join/audit time.

ALTER TABLE un_module_record
    ADD UNIQUE KEY uk_record_collab_scope (system_id, tenant_id, record_id);

CREATE TABLE un_collab_record_team (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, record_id),
    KEY idx_collab_team_updated (system_id, tenant_id, updated_at, record_id),
    CONSTRAINT fk_collab_team_record
        FOREIGN KEY (system_id, tenant_id, record_id)
        REFERENCES un_module_record (system_id, tenant_id, record_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_team_created_by
        FOREIGN KEY (system_id, created_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_team_updated_by
        FOREIGN KEY (system_id, updated_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collab_team_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_collab_record_team_member (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    team_role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN team_role = 'OWNER' THEN 1 ELSE NULL END
    ) STORED,
    row_version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, record_id, member_id),
    UNIQUE KEY uk_collab_team_member (
        system_id, tenant_id, record_id, member_id
    ),
    UNIQUE KEY uk_collab_team_single_owner (
        system_id, tenant_id, record_id, owner_slot
    ),
    KEY idx_collab_team_member_lookup (
        system_id, tenant_id, member_id, team_role, record_id
    ),
    KEY idx_collab_team_role (
        system_id, tenant_id, record_id, team_role, member_id
    ),
    CONSTRAINT fk_collab_team_member_team
        FOREIGN KEY (system_id, tenant_id, record_id)
        REFERENCES un_collab_record_team (system_id, tenant_id, record_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_team_member_access
        FOREIGN KEY (system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_team_member_created_by
        FOREIGN KEY (system_id, created_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_team_member_updated_by
        FOREIGN KEY (system_id, updated_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collab_team_role CHECK (
        team_role IN ('OWNER', 'COLLABORATOR', 'VIEWER', 'FOLLOWER')
    ),
    CONSTRAINT ck_collab_team_owner_slot CHECK (
        (team_role = 'OWNER' AND owner_slot = 1)
        OR (team_role <> 'OWNER' AND owner_slot IS NULL)
    ),
    CONSTRAINT ck_collab_team_member_version CHECK (row_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
