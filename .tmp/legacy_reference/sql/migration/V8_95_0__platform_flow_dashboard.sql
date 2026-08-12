-- Platform Flow/dashboard aggregates have no system_id or tenant_id. They are
-- independent platform owner models, not synthetic tenants in the system engines.
CREATE TABLE un_platform_flow_definition (
    id BIGINT NOT NULL, code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL, description VARCHAR(1000) NULL,
    draft_json JSON NOT NULL, draft_version BIGINT UNSIGNED NOT NULL,
    active_version_id BIGINT NULL, active_version_number INT UNSIGNED NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL, updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY(id), UNIQUE KEY uk_platform_flow_definition_code(code),
    KEY idx_platform_flow_definition_status(status,updated_at DESC,id DESC),
    CONSTRAINT fk_platform_flow_definition_creator FOREIGN KEY(created_by) REFERENCES un_plat_account(id),
    CONSTRAINT fk_platform_flow_definition_updater FOREIGN KEY(updated_by) REFERENCES un_plat_account(id),
    CONSTRAINT ck_platform_flow_definition_code CHECK(code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_platform_flow_definition_status CHECK(status IN('DRAFT','PUBLISHED','DISABLED')),
    CONSTRAINT ck_platform_flow_definition_version CHECK(draft_version>0 AND version>=0),
    CONSTRAINT ck_platform_flow_definition_active CHECK(
      (active_version_id IS NULL AND active_version_number IS NULL AND status='DRAFT') OR
      (active_version_id IS NOT NULL AND active_version_number>0 AND status IN('PUBLISHED','DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_flow_version (
    id BIGINT NOT NULL, definition_id BIGINT NOT NULL, version_number INT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL, checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(6) NOT NULL, published_by BIGINT NOT NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_platform_flow_version_number(definition_id,version_number),
    CONSTRAINT fk_platform_flow_version_definition FOREIGN KEY(definition_id) REFERENCES un_platform_flow_definition(id),
    CONSTRAINT fk_platform_flow_version_publisher FOREIGN KEY(published_by) REFERENCES un_plat_account(id),
    CONSTRAINT ck_platform_flow_version_checksum CHECK(checksum REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_platform_flow_definition ADD CONSTRAINT fk_platform_flow_definition_active
    FOREIGN KEY(active_version_id) REFERENCES un_platform_flow_version(id) ON DELETE RESTRICT;

CREATE TABLE un_platform_flow_instance (
    id BIGINT NOT NULL, definition_id BIGINT NOT NULL, definition_version_id BIGINT NOT NULL,
    starter_account_id BIGINT NOT NULL, status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_json JSON NOT NULL, result_json JSON NULL,
    started_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY(id), KEY idx_platform_flow_instance_starter(starter_account_id,started_at DESC,id DESC),
    KEY idx_platform_flow_instance_definition(definition_id,started_at DESC,id DESC),
    CONSTRAINT fk_platform_flow_instance_definition FOREIGN KEY(definition_id) REFERENCES un_platform_flow_definition(id),
    CONSTRAINT fk_platform_flow_instance_version FOREIGN KEY(definition_version_id) REFERENCES un_platform_flow_version(id),
    CONSTRAINT fk_platform_flow_instance_starter FOREIGN KEY(starter_account_id) REFERENCES un_plat_account(id),
    CONSTRAINT ck_platform_flow_instance_status CHECK(status IN('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_platform_flow_instance_result CHECK(
      (status='RUNNING' AND result_json IS NULL AND completed_at IS NULL) OR
      (status IN('COMPLETED','FAILED') AND result_json IS NOT NULL AND completed_at>=started_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_dashboard (
    id BIGINT NOT NULL, code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL, description VARCHAR(1000) NULL,
    draft_json JSON NOT NULL, draft_version BIGINT UNSIGNED NOT NULL,
    active_version_id BIGINT NULL, active_version_number INT UNSIGNED NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL, updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY(id), UNIQUE KEY uk_platform_dashboard_code(code),
    KEY idx_platform_dashboard_runtime(status,updated_at DESC,id DESC),
    CONSTRAINT fk_platform_dashboard_creator FOREIGN KEY(created_by) REFERENCES un_plat_account(id),
    CONSTRAINT fk_platform_dashboard_updater FOREIGN KEY(updated_by) REFERENCES un_plat_account(id),
    CONSTRAINT ck_platform_dashboard_code CHECK(code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_platform_dashboard_status CHECK(status IN('DRAFT','PUBLISHED','DISABLED')),
    CONSTRAINT ck_platform_dashboard_active CHECK(
      (active_version_id IS NULL AND active_version_number IS NULL AND status='DRAFT') OR
      (active_version_id IS NOT NULL AND active_version_number>0 AND status IN('PUBLISHED','DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_dashboard_version (
    id BIGINT NOT NULL, dashboard_id BIGINT NOT NULL, version_number INT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL, checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(6) NOT NULL, published_by BIGINT NOT NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_platform_dashboard_version_number(dashboard_id,version_number),
    CONSTRAINT fk_platform_dashboard_version_root FOREIGN KEY(dashboard_id) REFERENCES un_platform_dashboard(id),
    CONSTRAINT fk_platform_dashboard_version_publisher FOREIGN KEY(published_by) REFERENCES un_plat_account(id),
    CONSTRAINT ck_platform_dashboard_version_checksum CHECK(checksum REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_platform_dashboard ADD CONSTRAINT fk_platform_dashboard_active
    FOREIGN KEY(active_version_id) REFERENCES un_platform_dashboard_version(id) ON DELETE RESTRICT;

INSERT INTO un_plat_permission(id,scope_type,scope_key,system_id,permission_code,name,resource_type,status,created_at,created_by,updated_at,updated_by,version)
SELECT CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER(ORDER BY d.code) AS SIGNED),
 'PLATFORM',0,NULL,d.code,d.name,d.resource_type,'ACTIVE',UTC_TIMESTAMP(3),seed.actor_id,UTC_TIMESTAMP(3),seed.actor_id,0
FROM (
 SELECT 'platform.dashboard.manage' code,'Manage platform dashboards' name,'ACTION' resource_type
 UNION ALL SELECT 'platform.dashboard.view','View platform dashboard','MENU'
 UNION ALL SELECT 'platform.flow.manage','Manage platform flows','ACTION'
 UNION ALL SELECT 'platform.flow.read','Read platform flows','MENU'
 UNION ALL SELECT 'platform.flow.start','Start platform flows','ACTION'
) d
CROSS JOIN(SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_permission) base
CROSS JOIN(SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE seed.actor_id IS NOT NULL AND NOT EXISTS(
 SELECT 1 FROM un_plat_permission p WHERE p.scope_type='PLATFORM' AND p.scope_key=0 AND p.permission_code=d.code);

-- Existing ROOT roles receive management and runtime rights.
INSERT INTO un_plat_role_permission(id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER(ORDER BY r.id,p.id) AS SIGNED),
 'PLATFORM',0,r.id,p.id,'ALLOW',UTC_TIMESTAMP(3),r.updated_by
FROM un_plat_role r JOIN un_plat_permission p ON p.scope_type='PLATFORM' AND p.scope_key=0
 AND p.permission_code IN('platform.dashboard.manage','platform.dashboard.view','platform.flow.manage','platform.flow.read','platform.flow.start') AND p.status='ACTIVE'
CROSS JOIN(SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_role_permission) base
WHERE r.scope_type='PLATFORM' AND r.scope_key=0 AND r.role_type='ROOT' AND r.status='ACTIVE' AND r.deleted_at IS NULL
AND NOT EXISTS(SELECT 1 FROM un_plat_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

-- Existing ordinary platform-runtime roles receive only runtime capabilities.
INSERT INTO un_plat_role_permission(id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER(ORDER BY r.id,p.id) AS SIGNED),
 'PLATFORM',0,r.id,p.id,'ALLOW',UTC_TIMESTAMP(3),r.updated_by
FROM un_plat_role r
JOIN un_plat_role_permission runtime_rp ON runtime_rp.role_id=r.id AND runtime_rp.effect='ALLOW'
JOIN un_plat_permission runtime_p ON runtime_p.id=runtime_rp.permission_id AND runtime_p.permission_code='platform.runtime.access'
JOIN un_plat_permission p ON p.scope_type='PLATFORM' AND p.scope_key=0
 AND p.permission_code IN('platform.dashboard.view','platform.flow.read','platform.flow.start') AND p.status='ACTIVE'
CROSS JOIN(SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_role_permission) base
WHERE r.scope_type='PLATFORM' AND r.scope_key=0 AND r.status='ACTIVE' AND r.deleted_at IS NULL
AND NOT EXISTS(SELECT 1 FROM un_plat_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

UPDATE un_plat_authz_epoch SET epoch=epoch+1,updated_at=UTC_TIMESTAMP(3),version=version+1
WHERE scope_type='PLATFORM' AND scope_key=0;
