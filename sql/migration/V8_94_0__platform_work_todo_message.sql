-- Platform-runtime work and inbox are account scoped.  Deliberately no
-- system_id, tenant_id or system_member_id is present in these aggregates.

CREATE TABLE un_platform_work_project (
    id BIGINT NOT NULL,
    owner_account_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NULL,
    due_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_work_project_owner_code (owner_account_id, code),
    KEY idx_platform_work_project_owner_status (owner_account_id, status, updated_at DESC),
    CONSTRAINT fk_platform_work_project_owner FOREIGN KEY (owner_account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_work_project_scope CHECK (
        id > 0 AND owner_account_id > 0 AND created_by = owner_account_id
        AND updated_by = owner_account_id),
    CONSTRAINT ck_platform_work_project_text CHECK (
        code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_platform_work_project_status CHECK (
        status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_platform_work_project_dates CHECK (
        due_date IS NULL OR start_date IS NULL OR due_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_platform_task
    ADD COLUMN task_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'PERSONAL' AFTER source,
    ADD COLUMN project_id BIGINT NULL AFTER task_kind,
    ADD COLUMN labels_json JSON NULL AFTER project_id,
    DROP CHECK ck_platform_task_source,
    ADD CONSTRAINT ck_platform_task_source CHECK (source IN ('AGENT', 'WORK')),
    ADD CONSTRAINT ck_platform_task_work_kind CHECK (
        task_kind IN ('PERSONAL', 'PROJECT', 'GENERAL')
        AND ((task_kind = 'PROJECT' AND project_id IS NOT NULL)
          OR (task_kind <> 'PROJECT' AND project_id IS NULL))
        AND (source <> 'AGENT' OR task_kind = 'PERSONAL')),
    ADD CONSTRAINT fk_platform_task_project FOREIGN KEY (project_id)
        REFERENCES un_platform_work_project (id) ON DELETE RESTRICT,
    ADD KEY idx_platform_task_work_list (
        account_id, task_kind, status, due_at, updated_at DESC);

CREATE TABLE un_platform_work_daily_report (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    completed_text VARCHAR(4000) NOT NULL,
    plan_text VARCHAR(4000) NOT NULL,
    risk_text VARCHAR(4000) NULL,
    project_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    submitted_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_work_report_owner_date (account_id, report_date),
    KEY idx_platform_work_report_owner_list (account_id, report_date DESC, id DESC),
    CONSTRAINT fk_platform_work_report_owner FOREIGN KEY (account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_work_report_project FOREIGN KEY (project_id)
        REFERENCES un_platform_work_project (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_work_report_scope CHECK (
        id > 0 AND account_id > 0 AND created_by = account_id
        AND updated_by = account_id),
    CONSTRAINT ck_platform_work_report_status CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL)
        OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL)),
    CONSTRAINT ck_platform_work_report_text CHECK (
        CHAR_LENGTH(TRIM(completed_text)) BETWEEN 1 AND 4000
        AND CHAR_LENGTH(TRIM(plan_text)) BETWEEN 1 AND 4000
        AND (risk_text IS NULL OR CHAR_LENGTH(TRIM(risk_text)) BETWEEN 1 AND 4000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_inbox_message (
    id BIGINT NOT NULL,
    recipient_account_id BIGINT NOT NULL,
    template_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    target_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_path VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_platform_message_recipient_list (
        recipient_account_id, archived_at, read_at, created_at DESC, id DESC),
    KEY idx_platform_message_template (recipient_account_id, template_code, created_at DESC),
    CONSTRAINT fk_platform_message_recipient FOREIGN KEY (recipient_account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_message_identity CHECK (id > 0 AND recipient_account_id > 0),
    CONSTRAINT ck_platform_message_type CHECK (
        message_type IN ('AUTHORIZATION', 'TASK', 'LOG', 'SYSTEM_SWITCH', 'AGENT')),
    CONSTRAINT ck_platform_message_text CHECK (
        CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_platform_message_target CHECK (
        (target_type IS NULL AND target_id IS NULL AND target_path IS NULL)
        OR (target_type IN ('PLATFORM_AUTHORIZATION', 'PLATFORM_TASK',
                           'PLATFORM_PROJECT', 'PLATFORM_LOG', 'SYSTEM_SWITCH',
                           'PLATFORM_AGENT')
            AND target_id IS NOT NULL
            AND target_path LIKE '/platform/%'
            AND target_path NOT LIKE '/systems/%')),
    CONSTRAINT ck_platform_message_lifecycle CHECK (
        archived_at IS NULL OR read_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type,
    status, created_at, created_by, updated_at, updated_by, version
)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
           ORDER BY definition.permission_code) AS SIGNED),
       'PLATFORM', 0, NULL, definition.permission_code, definition.permission_name,
       definition.resource_type, 'ACTIVE', UTC_TIMESTAMP(3), seed.actor_id,
       UTC_TIMESTAMP(3), seed.actor_id, 0
FROM (
    SELECT 'platform.work.read' permission_code, 'Read own platform work' permission_name, 'MENU' resource_type
    UNION ALL SELECT 'platform.work.manage', 'Manage own platform work', 'ACTION'
    UNION ALL SELECT 'platform.message.read', 'Read own platform messages', 'MENU'
    UNION ALL SELECT 'platform.message.manage', 'Manage own platform messages', 'ACTION'
) definition
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_permission) base
CROSS JOIN (SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE seed.actor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_permission existing
       WHERE existing.scope_type='PLATFORM' AND existing.scope_key=0
         AND existing.permission_code=definition.permission_code);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
           ORDER BY role_row.id, permission_row.id) AS SIGNED),
       'PLATFORM', 0, role_row.id, permission_row.id, 'ALLOW',
       UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='PLATFORM' AND permission_row.scope_key=0
 AND permission_row.permission_code IN (
     'platform.work.read','platform.work.manage',
     'platform.message.read','platform.message.manage')
 AND permission_row.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_role_permission) base
WHERE role_row.scope_type='PLATFORM' AND role_row.scope_key=0
  AND role_row.role_type='ROOT' AND role_row.status='ACTIVE'
  AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
       WHERE existing.role_id=role_row.id AND existing.permission_id=permission_row.id);

-- Existing platform-runtime roles receive only self-owned runtime capabilities.
-- All corresponding owners filter by authenticated account_id, so these grants
-- do not expose another account or any system/tenant business context.
INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
           ORDER BY role_row.id, permission_row.id) AS SIGNED),
       'PLATFORM', 0, role_row.id, permission_row.id, 'ALLOW',
       UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_role_permission runtime_grant
  ON runtime_grant.role_id=role_row.id AND runtime_grant.effect='ALLOW'
JOIN un_plat_permission runtime_permission
  ON runtime_permission.id=runtime_grant.permission_id
 AND runtime_permission.permission_code='platform.runtime.access'
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='PLATFORM' AND permission_row.scope_key=0
 AND permission_row.permission_code IN (
     'platform.work.read','platform.work.manage',
     'platform.task.read','platform.task.create','platform.task.manage',
     'platform.message.read','platform.message.manage')
 AND permission_row.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_role_permission) base
WHERE role_row.scope_type='PLATFORM' AND role_row.scope_key=0
  AND role_row.status='ACTIVE' AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
       WHERE existing.role_id=role_row.id AND existing.permission_id=permission_row.id);

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='PLATFORM' AND scope_key=0;
