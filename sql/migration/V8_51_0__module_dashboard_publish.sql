-- Tenant-scoped system-home dashboard drafts and immutable publications.
-- Runtime consumers resolve only the active version and its immutable widget
-- rows. Every widget pins an immutable, tenant-scoped data-source version.

CREATE TABLE un_module_dashboard (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    dashboard_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    placement VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dashboard_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    draft_json JSON NOT NULL,
    draft_version BIGINT NOT NULL,
    active_version_id BIGINT NULL,
    active_version_no INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_dashboard_code (
        system_id, tenant_id, dashboard_code),
    UNIQUE KEY uk_module_dashboard_active_identity (
        system_id, tenant_id, id, active_version_id, active_version_no),
    KEY idx_module_dashboard_list (
        system_id, tenant_id, updated_at DESC, id DESC),
    UNIQUE KEY uk_module_dashboard_placement (
        system_id, tenant_id, placement),
    KEY idx_module_dashboard_active (
        system_id, tenant_id, active_version_id),
    CONSTRAINT fk_module_dashboard_tenant FOREIGN KEY (
        system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_dashboard_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0),
    CONSTRAINT ck_module_dashboard_code CHECK (
        dashboard_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_dashboard_placement CHECK (
        placement = 'SYSTEM_HOME'),
    CONSTRAINT ck_module_dashboard_name CHECK (
        CHAR_LENGTH(TRIM(dashboard_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_dashboard_description CHECK (
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_module_dashboard_draft CHECK (
        draft_version > 0
        AND JSON_TYPE(draft_json) = 'OBJECT'
        AND OCTET_LENGTH(draft_json) BETWEEN 2 AND 262144),
    CONSTRAINT ck_module_dashboard_active CHECK (
        (active_version_id IS NULL AND active_version_no IS NULL)
        OR (active_version_id > 0 AND active_version_no > 0)),
    CONSTRAINT ck_module_dashboard_state CHECK (
        updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_dashboard_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    dashboard_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    source_draft_version BIGINT NOT NULL,
    dashboard_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    placement VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dashboard_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    snapshot_json JSON NOT NULL,
    snapshot_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    widget_count TINYINT UNSIGNED NOT NULL,
    published_by_member_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, dashboard_id, id),
    UNIQUE KEY uk_module_dashboard_version_id (
        system_id, tenant_id, dashboard_id, id, version_no),
    UNIQUE KEY uk_module_dashboard_version_no (
        system_id, tenant_id, dashboard_id, version_no),
    UNIQUE KEY uk_module_dashboard_draft_publish (
        system_id, tenant_id, dashboard_id, source_draft_version),
    KEY idx_module_dashboard_version_list (
        system_id, tenant_id, dashboard_id, version_no DESC),
    KEY idx_module_dashboard_version_fingerprint (
        system_id, tenant_id, dashboard_id,
        snapshot_fingerprint, version_no DESC),
    CONSTRAINT fk_module_dashboard_version_root FOREIGN KEY (
        system_id, tenant_id, dashboard_id)
        REFERENCES un_module_dashboard (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_dashboard_version_publisher FOREIGN KEY (
        system_id, published_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_dashboard_version_identity CHECK (
        id > 0 AND dashboard_id > 0 AND version_no > 0
        AND source_draft_version > 0 AND published_by_member_id > 0),
    CONSTRAINT ck_module_dashboard_version_code CHECK (
        dashboard_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_dashboard_version_placement CHECK (
        placement = 'SYSTEM_HOME'),
    CONSTRAINT ck_module_dashboard_version_name CHECK (
        CHAR_LENGTH(TRIM(dashboard_name)) BETWEEN 1 AND 200
        AND (description IS NULL
          OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000)),
    CONSTRAINT ck_module_dashboard_version_snapshot CHECK (
        JSON_TYPE(snapshot_json) = 'OBJECT'
        AND OCTET_LENGTH(snapshot_json) BETWEEN 2 AND 262144
        AND snapshot_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_module_dashboard_version_widgets CHECK (
        widget_count BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_dashboard_version_widget (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    dashboard_id BIGINT NOT NULL,
    dashboard_version_id BIGINT NOT NULL,
    dashboard_version_no INT UNSIGNED NOT NULL,
    widget_ordinal TINYINT UNSIGNED NOT NULL,
    widget_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    widget_type VARCHAR(24)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    widget_title VARCHAR(200) NOT NULL,
    data_source_id BIGINT NOT NULL,
    data_source_version_id BIGINT NOT NULL,
    data_source_version_no INT UNSIGNED NOT NULL,
    data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version_id VARCHAR(200)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_limit TINYINT UNSIGNED NULL,
    grid_x TINYINT UNSIGNED NOT NULL,
    grid_y SMALLINT UNSIGNED NOT NULL,
    grid_width TINYINT UNSIGNED NOT NULL,
    grid_height SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, dashboard_id, dashboard_version_id, id),
    UNIQUE KEY uk_module_dashboard_widget_ordinal (
        system_id, tenant_id, dashboard_id,
        dashboard_version_id, widget_ordinal),
    UNIQUE KEY uk_module_dashboard_widget_code (
        system_id, tenant_id, dashboard_id,
        dashboard_version_id, widget_code),
    KEY idx_module_dashboard_widget_source (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no),
    CONSTRAINT fk_module_dashboard_widget_version FOREIGN KEY (
        system_id, tenant_id, dashboard_id,
        dashboard_version_id, dashboard_version_no)
        REFERENCES un_module_dashboard_version (
            system_id, tenant_id, dashboard_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_dashboard_widget_source_version FOREIGN KEY (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no)
        REFERENCES un_module_data_source_version (
            system_id, tenant_id, data_source_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT ck_module_dashboard_widget_identity CHECK (
        id > 0 AND dashboard_id > 0 AND dashboard_version_id > 0
        AND dashboard_version_no > 0 AND widget_ordinal < 20
        AND data_source_id > 0 AND data_source_version_id > 0
        AND data_source_version_no > 0),
    CONSTRAINT ck_module_dashboard_widget_code CHECK (
        widget_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'),
    CONSTRAINT ck_module_dashboard_widget_schema CHECK (
        CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_dashboard_widget_title CHECK (
        CHAR_LENGTH(TRIM(widget_title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_dashboard_widget_type CHECK (
        (widget_type = 'STAT_COUNT' AND row_limit IS NULL)
        OR (widget_type = 'DATA_LIST' AND row_limit BETWEEN 1 AND 20)),
    CONSTRAINT ck_module_dashboard_widget_grid CHECK (
        grid_x < 12 AND grid_width BETWEEN 1 AND 12
        AND grid_x + grid_width <= 12
        AND grid_y < 100 AND grid_height BETWEEN 1 AND 100
        AND grid_y + grid_height <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_dashboard
    ADD CONSTRAINT fk_module_dashboard_active_version FOREIGN KEY (
        system_id, tenant_id, id, active_version_id, active_version_no)
        REFERENCES un_module_dashboard_version (
            system_id, tenant_id, dashboard_id, id, version_no)
        ON DELETE RESTRICT;
