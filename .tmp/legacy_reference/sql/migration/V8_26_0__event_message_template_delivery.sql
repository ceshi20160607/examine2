CREATE TABLE un_event_message_template (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    desired_enabled TINYINT(1) NOT NULL DEFAULT 1,
    draft_title_template VARCHAR(200) NOT NULL,
    draft_body_template VARCHAR(4000) NOT NULL,
    channels_json JSON NOT NULL,
    allowed_variables_json JSON NOT NULL,
    published_version_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_template_system_code (system_id, template_code),
    UNIQUE KEY uk_event_template_system_id (system_id, id),
    CONSTRAINT ck_event_template_version CHECK (version > 0),
    CONSTRAINT ck_event_template_enabled CHECK (desired_enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_event_message_template_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    version_no BIGINT NOT NULL,
    source_draft_version BIGINT NOT NULL,
    enabled TINYINT(1) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    body_template VARCHAR(4000) NOT NULL,
    channels_json JSON NOT NULL,
    allowed_variables_json JSON NOT NULL,
    published_at DATETIME(3) NOT NULL,
    published_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_template_version_system_id (system_id, id),
    UNIQUE KEY uk_event_template_version_no (template_id, version_no),
    UNIQUE KEY uk_event_template_source_draft (template_id, source_draft_version),
    CONSTRAINT ck_event_template_version_numbers CHECK (version_no > 0 AND source_draft_version > 0),
    CONSTRAINT ck_event_template_version_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT fk_event_template_version_template FOREIGN KEY (system_id, template_id)
        REFERENCES un_event_message_template (system_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_event_message_delivery_log (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    recipient_member_id BIGINT NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    template_version_id BIGINT NULL,
    channel VARCHAR(16) NOT NULL,
    dedupe_key VARCHAR(200) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    target_path VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    message_id BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_delivery_dedupe (
        system_id, tenant_id, recipient_member_id, channel, dedupe_key),
    UNIQUE KEY uk_event_delivery_message (message_id),
    KEY idx_event_delivery_template_status (system_id, template_code, status, created_at),
    CONSTRAINT ck_event_delivery_status CHECK (status IN ('PENDING','DELIVERED','SKIPPED','FAILED')),
    CONSTRAINT ck_event_delivery_channel CHECK (channel = 'INBOX'),
    CONSTRAINT ck_event_delivery_attempt CHECK (attempt_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE un_event_message
    ADD COLUMN target_path VARCHAR(500) NULL AFTER target_id;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY system_row.id) AS SIGNED),
    'SYSTEM', system_row.id, system_row.id, 'event.template.manage',
    'Manage system message templates', 'ACTION', 'ACTIVE',
    UTC_TIMESTAMP(3), system_row.created_by, UTC_TIMESTAMP(3), system_row.created_by, 0
FROM un_plat_system system_row
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_permission) base
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_permission existing
    WHERE existing.scope_type='SYSTEM' AND existing.scope_key=system_row.id
      AND existing.permission_code='event.template.manage');

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY role_row.scope_key, role_row.id) AS SIGNED),
    'SYSTEM', role_row.scope_key, role_row.id, permission_row.id, 'ALLOW',
    UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='SYSTEM' AND permission_row.scope_key=role_row.scope_key
 AND permission_row.permission_code='event.template.manage' AND permission_row.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_role_permission) base
WHERE role_row.scope_type='SYSTEM' AND role_row.role_type='ROOT'
  AND role_row.status='ACTIVE' AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id=role_row.id AND existing.permission_id=permission_row.id);

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system system_row
JOIN un_plat_authz_epoch epoch_row
  ON epoch_row.scope_type='SYSTEM' AND epoch_row.scope_key=system_row.id
SET system_row.permission_version=epoch_row.epoch,
    system_row.updated_at=UTC_TIMESTAMP(3),
    system_row.version=system_row.version+1;
