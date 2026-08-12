ALTER TABLE un_plat_system
    ADD COLUMN init_failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER initialized_at,
    ADD COLUMN init_failed_at DATETIME(3) NULL AFTER init_failure_code,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD COLUMN tombstone_reason VARCHAR(1000) NULL AFTER deleted_by;

UPDATE un_plat_system
SET init_failure_code='LEGACY_INITIALIZATION_FAILURE',
    init_failed_at=COALESCE(updated_at,created_at)
WHERE status='INIT_FAILED';

UPDATE un_plat_system
SET deleted_by=COALESCE(updated_by,created_by),
    tombstone_reason='Legacy tombstone'
WHERE deleted_at IS NOT NULL;

ALTER TABLE un_plat_system
    ADD CONSTRAINT ck_plat_system_initialization_failure CHECK (
        (status='INIT_FAILED' AND init_failure_code IS NOT NULL AND init_failed_at IS NOT NULL)
        OR (status<>'INIT_FAILED' AND init_failure_code IS NULL AND init_failed_at IS NULL)),
    ADD CONSTRAINT ck_plat_system_tombstone CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL AND tombstone_reason IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL AND tombstone_reason IS NOT NULL)),
    ADD CONSTRAINT fk_plat_system_deleted_by FOREIGN KEY (deleted_by) REFERENCES un_plat_account(id);

CREATE TABLE un_plat_global_setting (
    id TINYINT NOT NULL,
    profile_json JSON NOT NULL,
    storage_policy_json JSON NOT NULL,
    security_policy_json JSON NOT NULL,
    quota_policy_json JSON NOT NULL,
    backup_policy_json JSON NOT NULL,
    release_policy_json JSON NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_plat_global_setting_actor FOREIGN KEY (updated_by) REFERENCES un_plat_account(id),
    CONSTRAINT ck_plat_global_setting_singleton CHECK (id=1),
    CONSTRAINT ck_plat_global_setting_json CHECK (
        JSON_TYPE(profile_json)='OBJECT' AND JSON_TYPE(storage_policy_json)='OBJECT'
        AND JSON_TYPE(security_policy_json)='OBJECT' AND JSON_TYPE(quota_policy_json)='OBJECT'
        AND JSON_TYPE(backup_policy_json)='OBJECT' AND JSON_TYPE(release_policy_json)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission
    (id,scope_type,scope_key,system_id,permission_code,name,resource_type,status,
     created_at,created_by,updated_at,updated_by,version)
SELECT CAST(base.min_id AS SIGNED)-1,'PLATFORM',0,NULL,
       'platform.settings.manage','Manage platform global settings','ACTION','ACTIVE',
       UTC_TIMESTAMP(3),seed.actor_id,UTC_TIMESTAMP(3),seed.actor_id,0
FROM (SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_permission) base
CROSS JOIN (SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE seed.actor_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM un_plat_permission p WHERE p.scope_type='PLATFORM' AND p.scope_key=0
      AND p.permission_code='platform.settings.manage');

INSERT INTO un_plat_role_permission
    (id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER (ORDER BY r.id) AS SIGNED),
       'PLATFORM',0,r.id,p.id,'ALLOW',UTC_TIMESTAMP(3),r.updated_by
FROM un_plat_role r JOIN un_plat_permission p
  ON p.scope_type='PLATFORM' AND p.scope_key=0
 AND p.permission_code='platform.settings.manage' AND p.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_role_permission) base
WHERE r.scope_type='PLATFORM' AND r.scope_key=0 AND r.role_type='ROOT'
  AND r.status='ACTIVE' AND r.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM un_plat_role_permission rp
                  WHERE rp.role_id=r.id AND rp.permission_id=p.id);

UPDATE un_plat_authz_epoch SET epoch=epoch+1,updated_at=UTC_TIMESTAMP(3),version=version+1
WHERE scope_type='PLATFORM' AND scope_key=0;
