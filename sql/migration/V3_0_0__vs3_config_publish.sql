-- VS3 system-scoped no-code configuration draft, publication, and runtime snapshot.
-- Normalized rows are the shared draft; runtime consumers only read the active immutable version.

CREATE TABLE un_module_config_root (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CLEAN',
    draft_revision BIGINT NOT NULL DEFAULT 0,
    draft_checksum CHAR(64) NULL,
    active_version_id BIGINT NULL,
    base_version_id BIGINT NULL,
    last_check_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_config_root_system (system_id),
    UNIQUE KEY uk_module_config_root_system_id (system_id, id),
    KEY idx_module_config_root_active (system_id, active_version_id),
    CONSTRAINT fk_module_config_root_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT ck_module_config_root_status CHECK (
        status IN ('CLEAN', 'DIRTY', 'CHECKING', 'CHECK_FAILED', 'CHECKED', 'PUBLISHING')
    ),
    CONSTRAINT ck_module_config_root_revision CHECK (draft_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_group (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    group_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    icon_key VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_group_system_code (system_id, group_code),
    UNIQUE KEY uk_module_group_system_id (system_id, id),
    KEY idx_module_group_list (system_id, desired_status, sort_order, id),
    CONSTRAINT fk_module_group_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT ck_module_group_code CHECK (group_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_group_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_group_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_group_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_definition (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    icon_key VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    allow_comments BOOLEAN NOT NULL DEFAULT FALSE,
    allow_team BOOLEAN NOT NULL DEFAULT FALSE,
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_definition_system_code (system_id, module_code),
    UNIQUE KEY uk_module_definition_system_id (system_id, id),
    KEY idx_module_definition_group (system_id, group_id, desired_status, sort_order, id),
    CONSTRAINT fk_module_definition_group FOREIGN KEY (system_id, group_id)
        REFERENCES un_module_group (system_id, id),
    CONSTRAINT ck_module_definition_code CHECK (module_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_definition_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_definition_flags CHECK (allow_comments IN (0, 1) AND allow_team IN (0, 1)),
    CONSTRAINT ck_module_definition_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_definition_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_dictionary (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    dictionary_code VARCHAR(64) NOT NULL,
    dictionary_name VARCHAR(128) NOT NULL,
    dictionary_type VARCHAR(24) NOT NULL,
    category VARCHAR(64) NULL,
    description VARCHAR(500) NULL,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_dictionary_system_code (system_id, dictionary_code),
    UNIQUE KEY uk_module_dictionary_system_id (system_id, id),
    KEY idx_module_dictionary_list (system_id, desired_status, category, dictionary_name),
    CONSTRAINT fk_module_dictionary_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT ck_module_dictionary_code CHECK (dictionary_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_dictionary_type CHECK (
        dictionary_type IN ('LIST', 'TREE', 'CASCADE', 'STATUS', 'TAG', 'FIELD_OPTION')
    ),
    CONSTRAINT ck_module_dictionary_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_dictionary_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_dictionary_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_dictionary_item (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    dictionary_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    semantic_key VARCHAR(32) NULL,
    color_value VARCHAR(32) NULL,
    icon_key VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    depth_level INT NOT NULL DEFAULT 0,
    depth_path VARCHAR(1000) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    code_locked_at DATETIME(3) NULL,
    active_marker BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN is_default = 1 AND desired_status = 'ENABLED' AND deleted_at IS NULL THEN dictionary_id
            ELSE NULL
        END
    ) STORED,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_dict_item_code (system_id, dictionary_id, item_code),
    UNIQUE KEY uk_module_dict_item_scope_id (system_id, dictionary_id, id),
    UNIQUE KEY uk_module_dict_item_default (active_marker),
    KEY idx_module_dict_item_parent (system_id, dictionary_id, parent_id, sort_order, id),
    KEY idx_module_dict_item_list (system_id, dictionary_id, desired_status, sort_order, id),
    CONSTRAINT fk_module_dict_item_dictionary FOREIGN KEY (system_id, dictionary_id)
        REFERENCES un_module_dictionary (system_id, id),
    CONSTRAINT fk_module_dict_item_parent FOREIGN KEY (system_id, dictionary_id, parent_id)
        REFERENCES un_module_dictionary_item (system_id, dictionary_id, id),
    CONSTRAINT ck_module_dict_item_code CHECK (item_code REGEXP '^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$'),
    CONSTRAINT ck_module_dict_item_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_dict_item_default CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_module_dict_item_depth CHECK (depth_level >= 0 AND depth_level <= 16),
    CONSTRAINT ck_module_dict_item_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_dict_item_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL),
    CONSTRAINT ck_module_dict_item_parent_self CHECK (parent_id IS NULL OR parent_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_dictionary_item_closure (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    dictionary_id BIGINT NOT NULL,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_dict_closure_path (system_id, dictionary_id, ancestor_id, descendant_id),
    KEY idx_module_dict_closure_desc (system_id, dictionary_id, descendant_id, depth, ancestor_id),
    CONSTRAINT fk_module_dict_closure_ancestor FOREIGN KEY (system_id, dictionary_id, ancestor_id)
        REFERENCES un_module_dictionary_item (system_id, dictionary_id, id),
    CONSTRAINT fk_module_dict_closure_descendant FOREIGN KEY (system_id, dictionary_id, descendant_id)
        REFERENCES un_module_dictionary_item (system_id, dictionary_id, id),
    CONSTRAINT ck_module_dict_closure_depth CHECK (
        depth >= 0 AND ((ancestor_id = descendant_id AND depth = 0) OR (ancestor_id <> descendant_id AND depth > 0))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_field (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    dictionary_id BIGINT NULL,
    target_module_id BIGINT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    is_readonly BOOLEAN NOT NULL DEFAULT FALSE,
    is_searchable BOOLEAN NOT NULL DEFAULT FALSE,
    is_filterable BOOLEAN NOT NULL DEFAULT FALSE,
    show_in_list BOOLEAN NOT NULL DEFAULT TRUE,
    show_in_detail BOOLEAN NOT NULL DEFAULT TRUE,
    index_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    property_json JSON NOT NULL,
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_field_module_code (system_id, module_id, field_code),
    UNIQUE KEY uk_module_field_system_id (system_id, id),
    KEY idx_module_field_list (system_id, module_id, desired_status, sort_order, id),
    KEY idx_module_field_dictionary (system_id, dictionary_id),
    KEY idx_module_field_target (system_id, target_module_id),
    CONSTRAINT fk_module_field_module FOREIGN KEY (system_id, module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT fk_module_field_dictionary FOREIGN KEY (system_id, dictionary_id)
        REFERENCES un_module_dictionary (system_id, id),
    CONSTRAINT fk_module_field_target FOREIGN KEY (system_id, target_module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT ck_module_field_code CHECK (field_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_field_type CHECK (field_type IN (
        'TEXT', 'TEXTAREA', 'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'NUMBER', 'PERCENT', 'MONEY',
        'DATE', 'DATETIME', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'RADIO', 'MULTI_SELECT', 'CASCADE',
        'SWITCH', 'MEMBER', 'DEPARTMENT', 'TENANT', 'ATTACHMENT', 'IMAGE', 'FILE_GROUP',
        'AUTO_NUMBER', 'RELATION', 'REFERENCE', 'SUBTABLE', 'ADDRESS', 'GEO', 'RATING', 'PROGRESS',
        'TAG', 'BARCODE', 'SIGNATURE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'FORMULA', 'SUMMARY',
        'CALCULATED', 'LOOKUP', 'AGGREGATE', 'AI_FILL', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'
    )),
    CONSTRAINT ck_module_field_flags CHECK (
        is_required IN (0, 1) AND is_hidden IN (0, 1) AND is_readonly IN (0, 1)
        AND is_searchable IN (0, 1) AND is_filterable IN (0, 1)
        AND show_in_list IN (0, 1) AND show_in_detail IN (0, 1)
    ),
    CONSTRAINT ck_module_field_index CHECK (index_mode IN ('NONE', 'FILTER', 'SORT', 'UNIQUE', 'STATISTIC')),
    CONSTRAINT ck_module_field_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_field_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_field_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_page (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    page_code VARCHAR(64) NOT NULL,
    page_name VARCHAR(128) NOT NULL,
    page_type VARCHAR(16) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    layout_json JSON NOT NULL,
    active_marker VARCHAR(160) GENERATED ALWAYS AS (
        CASE
            WHEN is_default = 1 AND desired_status = 'ENABLED' AND deleted_at IS NULL
                THEN CONCAT(system_id, ':', module_id, ':', page_type)
            ELSE NULL
        END
    ) STORED,
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_page_module_code (system_id, module_id, page_code),
    UNIQUE KEY uk_module_page_system_id (system_id, id),
    UNIQUE KEY uk_module_page_default (active_marker),
    KEY idx_module_page_list (system_id, module_id, page_type, desired_status, id),
    CONSTRAINT fk_module_page_module FOREIGN KEY (system_id, module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT ck_module_page_code CHECK (page_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_page_type CHECK (page_type IN ('LIST', 'FORM', 'DETAIL')),
    CONSTRAINT ck_module_page_default CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_module_page_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_page_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_page_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_page_component (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    parent_component_id BIGINT NULL,
    field_id BIGINT NULL,
    component_key VARCHAR(64) NOT NULL,
    component_type VARCHAR(24) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    grid_row INT NOT NULL DEFAULT 0,
    grid_column INT NOT NULL DEFAULT 0,
    grid_span INT NOT NULL DEFAULT 12,
    property_json JSON NOT NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_page_component_key (system_id, page_id, component_key),
    UNIQUE KEY uk_module_page_component_sid (system_id, id),
    KEY idx_module_page_component_tree (system_id, page_id, parent_component_id, sort_order, id),
    KEY idx_module_page_component_field (system_id, field_id),
    CONSTRAINT fk_module_page_component_page FOREIGN KEY (system_id, page_id)
        REFERENCES un_module_page (system_id, id),
    CONSTRAINT fk_module_page_component_parent FOREIGN KEY (system_id, parent_component_id)
        REFERENCES un_module_page_component (system_id, id),
    CONSTRAINT fk_module_page_component_field FOREIGN KEY (system_id, field_id)
        REFERENCES un_module_field (system_id, id),
    CONSTRAINT ck_module_page_component_key CHECK (component_key REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_page_component_type CHECK (
        component_type IN ('FIELD', 'SECTION', 'TABS', 'TAB', 'ACTION', 'TEXT', 'DIVIDER')
    ),
    CONSTRAINT ck_module_page_component_grid CHECK (
        grid_row >= 0 AND grid_column >= 0 AND grid_column <= 23 AND grid_span >= 1 AND grid_span <= 24
    ),
    CONSTRAINT ck_module_page_component_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_page_component_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL),
    CONSTRAINT ck_module_page_component_parent_self CHECK (parent_component_id IS NULL OR parent_component_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_action (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    action_type VARCHAR(24) NOT NULL,
    placement VARCHAR(24) NOT NULL,
    permission_code VARCHAR(160) NOT NULL,
    confirm_message VARCHAR(300) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    property_json JSON NOT NULL,
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_action_module_code (system_id, module_id, action_code),
    UNIQUE KEY uk_module_action_permission (system_id, permission_code),
    UNIQUE KEY uk_module_action_system_id (system_id, id),
    KEY idx_module_action_list (system_id, module_id, desired_status, placement, sort_order, id),
    CONSTRAINT fk_module_action_module FOREIGN KEY (system_id, module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT ck_module_action_code CHECK (action_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_action_type CHECK (
        action_type IN ('CREATE', 'UPDATE', 'DELETE', 'CUSTOM', 'APPROVAL', 'IMPORT', 'EXPORT', 'PRINT')
    ),
    CONSTRAINT ck_module_action_placement CHECK (placement IN ('TOOLBAR', 'ROW', 'DETAIL', 'BATCH')),
    CONSTRAINT ck_module_action_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_action_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_action_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_rule (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    condition_json JSON NOT NULL,
    effect_json JSON NOT NULL,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    code_locked_at DATETIME(3) NULL,
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_rule_module_code (system_id, module_id, rule_code),
    UNIQUE KEY uk_module_rule_system_id (system_id, id),
    KEY idx_module_rule_list (system_id, module_id, desired_status, priority, id),
    CONSTRAINT fk_module_rule_module FOREIGN KEY (system_id, module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT ck_module_rule_code CHECK (rule_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_module_rule_type CHECK (rule_type IN (
        'FIELD_VISIBILITY', 'FIELD_REQUIRED', 'FIELD_READ_ONLY',
        'ACTION_ENABLED', 'DELETE_ALLOWED', 'APPROVAL_REQUIRED'
    )),
    CONSTRAINT ck_module_rule_priority CHECK (priority >= 0 AND priority <= 10000),
    CONSTRAINT ck_module_rule_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_rule_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_rule_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_config_reference (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    property_path VARCHAR(300) NULL,
    created_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_config_reference (
        system_id, source_type, source_id, target_type, target_id, relation_type
    ),
    KEY idx_module_config_reference_target (system_id, target_type, target_id, source_type, source_id),
    KEY idx_module_config_reference_source (system_id, source_type, source_id),
    CONSTRAINT fk_module_config_reference_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT ck_module_config_reference_source CHECK (
        source_type IN ('MODULE', 'FIELD', 'PAGE', 'COMPONENT', 'ACTION', 'RULE', 'DICTIONARY', 'DICTIONARY_ITEM')
    ),
    CONSTRAINT ck_module_config_reference_target CHECK (
        target_type IN ('MODULE', 'FIELD', 'PAGE', 'ACTION', 'RULE', 'DICTIONARY', 'DICTIONARY_ITEM', 'PERMISSION')
    ),
    CONSTRAINT ck_module_config_reference_relation CHECK (relation_type REGEXP '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_module_config_reference_self CHECK (source_type <> target_type OR source_id <> target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_permission (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    module_id BIGINT NULL,
    resource_type VARCHAR(16) NOT NULL,
    resource_id BIGINT NOT NULL,
    permission_code VARCHAR(160) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(16) NOT NULL,
    registered_permission_id BIGINT NULL,
    desired_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_revision BIGINT NOT NULL,
    updated_revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_permission_code (system_id, permission_code),
    UNIQUE KEY uk_module_permission_resource (system_id, resource_type, resource_id, permission_code),
    UNIQUE KEY uk_module_permission_system_id (system_id, id),
    KEY idx_module_permission_module (system_id, module_id, desired_status, permission_type),
    CONSTRAINT fk_module_permission_module FOREIGN KEY (system_id, module_id)
        REFERENCES un_module_definition (system_id, id),
    CONSTRAINT ck_module_permission_resource CHECK (
        resource_type IN ('MODULE', 'FIELD', 'ACTION', 'PAGE')
    ),
    CONSTRAINT ck_module_permission_type CHECK (permission_type IN ('MENU', 'ACTION', 'FIELD')),
    CONSTRAINT ck_module_permission_status CHECK (desired_status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_module_permission_revision CHECK (created_revision >= 0 AND updated_revision >= created_revision),
    CONSTRAINT ck_module_permission_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_config_check (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    base_version_id BIGINT NULL,
    draft_revision BIGINT NOT NULL,
    draft_checksum CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    blocker_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    snapshot_size_bytes BIGINT NOT NULL DEFAULT 0,
    report_json JSON NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    checked_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_config_check_system_id (system_id, id),
    KEY idx_module_config_check_list (system_id, started_at DESC, id),
    KEY idx_module_config_check_status (system_id, status, expires_at),
    CONSTRAINT fk_module_config_check_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT ck_module_config_check_status CHECK (status IN ('RUNNING', 'PASSED', 'FAILED', 'STALE')),
    CONSTRAINT ck_module_config_check_revision CHECK (draft_revision >= 0),
    CONSTRAINT ck_module_config_check_counts CHECK (
        blocker_count >= 0 AND warning_count >= 0 AND snapshot_size_bytes >= 0
    ),
    CONSTRAINT ck_module_config_check_times CHECK (
        expires_at > started_at AND (completed_at IS NULL OR completed_at >= started_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_config_check_issue (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    check_id BIGINT NOT NULL,
    severity VARCHAR(12) NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(24) NULL,
    resource_id BIGINT NULL,
    property_path VARCHAR(300) NULL,
    issue_message VARCHAR(500) NOT NULL,
    suggested_action VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_module_check_issue_check (system_id, check_id, severity, property_path, id),
    KEY idx_module_check_issue_resource (system_id, resource_type, resource_id),
    CONSTRAINT fk_module_check_issue_check FOREIGN KEY (system_id, check_id)
        REFERENCES un_module_config_check (system_id, id),
    CONSTRAINT ck_module_check_issue_severity CHECK (severity IN ('BLOCKER', 'WARNING')),
    CONSTRAINT ck_module_check_issue_resource CHECK (
        resource_type IS NULL OR resource_type IN (
            'ROOT', 'GROUP', 'MODULE', 'FIELD', 'DICTIONARY', 'DICTIONARY_ITEM',
            'PAGE', 'COMPONENT', 'ACTION', 'RULE', 'PERMISSION'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_config_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    version_no BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    based_on_version_id BIGINT NULL,
    rollback_target_version_id BIGINT NULL,
    source_check_id BIGINT NOT NULL,
    snapshot_json JSON NOT NULL,
    snapshot_checksum CHAR(64) NOT NULL,
    snapshot_size_bytes BIGINT NOT NULL,
    impact_report_json JSON NOT NULL,
    published_at DATETIME(3) NOT NULL,
    published_by BIGINT NOT NULL,
    publish_reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_config_version_no (system_id, version_no),
    UNIQUE KEY uk_module_config_version_system_id (system_id, id),
    KEY idx_module_config_version_time (system_id, published_at DESC, id),
    KEY idx_module_config_version_checksum (system_id, snapshot_checksum),
    CONSTRAINT fk_module_config_version_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT fk_module_config_version_base FOREIGN KEY (system_id, based_on_version_id)
        REFERENCES un_module_config_version (system_id, id),
    CONSTRAINT fk_module_config_version_rollback FOREIGN KEY (system_id, rollback_target_version_id)
        REFERENCES un_module_config_version (system_id, id),
    CONSTRAINT fk_module_config_version_check FOREIGN KEY (system_id, source_check_id)
        REFERENCES un_module_config_check (system_id, id),
    CONSTRAINT ck_module_config_version_no CHECK (version_no > 0),
    CONSTRAINT ck_module_config_version_source CHECK (source_type IN ('PUBLISH', 'ROLLBACK')),
    CONSTRAINT ck_module_config_version_size CHECK (snapshot_size_bytes >= 2 AND snapshot_size_bytes <= 2097152),
    CONSTRAINT ck_module_config_version_rollback CHECK (
        (source_type = 'PUBLISH' AND rollback_target_version_id IS NULL)
        OR (source_type = 'ROLLBACK' AND rollback_target_version_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_publish_record (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    from_version_id BIGINT NULL,
    to_version_id BIGINT NOT NULL,
    target_version_id BIGINT NULL,
    check_id BIGINT NOT NULL,
    draft_revision BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    impact_report_json JSON NOT NULL,
    operated_at DATETIME(3) NOT NULL,
    operated_by BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_publish_idempotency (system_id, idempotency_key),
    UNIQUE KEY uk_module_publish_to_version (system_id, to_version_id),
    KEY idx_module_publish_record_time (system_id, operated_at DESC, id),
    CONSTRAINT fk_module_publish_from FOREIGN KEY (system_id, from_version_id)
        REFERENCES un_module_config_version (system_id, id),
    CONSTRAINT fk_module_publish_to FOREIGN KEY (system_id, to_version_id)
        REFERENCES un_module_config_version (system_id, id),
    CONSTRAINT fk_module_publish_target FOREIGN KEY (system_id, target_version_id)
        REFERENCES un_module_config_version (system_id, id),
    CONSTRAINT fk_module_publish_check FOREIGN KEY (system_id, check_id)
        REFERENCES un_module_config_check (system_id, id),
    CONSTRAINT ck_module_publish_operation CHECK (operation_type IN ('PUBLISH', 'ROLLBACK')),
    CONSTRAINT ck_module_publish_result CHECK (result = 'SUCCEEDED'),
    CONSTRAINT ck_module_publish_revision CHECK (draft_revision >= 0),
    CONSTRAINT ck_module_publish_target CHECK (
        (operation_type = 'PUBLISH' AND target_version_id IS NULL)
        OR (operation_type = 'ROLLBACK' AND target_version_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE un_module_config_root
    ADD CONSTRAINT fk_module_config_root_active FOREIGN KEY (system_id, active_version_id)
        REFERENCES un_module_config_version (system_id, id),
    ADD CONSTRAINT fk_module_config_root_base FOREIGN KEY (system_id, base_version_id)
        REFERENCES un_module_config_version (system_id, id),
    ADD CONSTRAINT fk_module_config_root_check FOREIGN KEY (system_id, last_check_id)
        REFERENCES un_module_config_check (system_id, id),
    ADD CONSTRAINT ck_module_config_root_versions CHECK (
        (active_version_id IS NULL AND base_version_id IS NULL)
        OR (active_version_id IS NOT NULL AND base_version_id IS NOT NULL)
    );

ALTER TABLE un_module_config_check
    ADD CONSTRAINT fk_module_config_check_base FOREIGN KEY (system_id, base_version_id)
        REFERENCES un_module_config_version (system_id, id);
