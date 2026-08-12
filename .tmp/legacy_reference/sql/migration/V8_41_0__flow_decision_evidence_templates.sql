-- Governed approval-decision evidence and reusable tenant comment templates.
-- NULL policy snapshots preserve the exact pre-evidence comment-only contract.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN decision_evidence_policies JSON NULL
        AFTER decision_comment_policies,
    ADD CONSTRAINT ck_flow_draft_decision_evidence CHECK (
        decision_evidence_policies IS NULL
        OR (
            JSON_TYPE(decision_evidence_policies) = 'OBJECT'
            AND OCTET_LENGTH(decision_evidence_policies) <= 16384
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN decision_evidence_policies JSON NULL
        AFTER decision_comment_policies,
    ADD CONSTRAINT ck_flow_version_decision_evidence CHECK (
        decision_evidence_policies IS NULL
        OR (
            JSON_TYPE(decision_evidence_policies) = 'OBJECT'
            AND OCTET_LENGTH(decision_evidence_policies) <= 16384
        )
    );

ALTER TABLE un_flow_instance
    ADD COLUMN decision_evidence_policy JSON NULL
        AFTER decision_comment_policy,
    ADD CONSTRAINT ck_flow_instance_decision_evidence CHECK (
        decision_evidence_policy IS NULL
        OR (
            JSON_TYPE(decision_evidence_policy) = 'OBJECT'
            AND OCTET_LENGTH(decision_evidence_policy) <= 2048
        )
    );

ALTER TABLE un_flow_parallel_branch_execution
    ADD COLUMN decision_evidence_policy JSON NULL
        AFTER decision_comment_policy,
    ADD CONSTRAINT ck_flow_branch_decision_evidence CHECK (
        decision_evidence_policy IS NULL
        OR (
            JSON_TYPE(decision_evidence_policy) = 'OBJECT'
            AND OCTET_LENGTH(decision_evidence_policy) <= 2048
        )
    );

CREATE TABLE un_flow_decision_comment_template (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_version INT UNSIGNED NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, template_id),
    UNIQUE KEY uk_flow_comment_template_name (
        system_id, tenant_id, name
    ),
    KEY idx_flow_comment_template_status (
        system_id, tenant_id, status, updated_at, template_id
    ),
    CONSTRAINT ck_flow_comment_template_identity CHECK (
        system_id > 0
        AND tenant_id > 0
        AND template_id > 0
        AND created_by > 0
        AND updated_by > 0
        AND current_version > 0
    ),
    CONSTRAINT ck_flow_comment_template_name CHECK (
        CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 80
    ),
    CONSTRAINT ck_flow_comment_template_status CHECK (
        status IN ('ACTIVE', 'INACTIVE')
    ),
    CONSTRAINT ck_flow_comment_template_time CHECK (
        updated_at >= created_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_decision_comment_template_version (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    comment_body VARCHAR(1000) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, template_id, version_no),
    CONSTRAINT fk_flow_comment_template_version FOREIGN KEY (
        system_id, tenant_id, template_id
    ) REFERENCES un_flow_decision_comment_template (
        system_id, tenant_id, template_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_comment_template_version_identity CHECK (
        template_id > 0 AND version_no > 0 AND created_by > 0
    ),
    CONSTRAINT ck_flow_comment_template_version_name CHECK (
        CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 80
    ),
    CONSTRAINT ck_flow_comment_template_version_body CHECK (
        CHAR_LENGTH(TRIM(comment_body)) BETWEEN 1 AND 1000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_decision_evidence (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    history_sequence INT UNSIGNED NOT NULL,
    branch_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    stage_index TINYINT UNSIGNED NOT NULL,
    decision VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    signature_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    signature_file_id BIGINT NULL,
    typed_signature VARCHAR(120) NULL,
    template_id BIGINT NULL,
    template_version INT UNSIGNED NULL,
    template_name VARCHAR(80) NULL,
    actor_id BIGINT NOT NULL,
    represented_member_id BIGINT NOT NULL,
    delegation_id BIGINT NULL,
    decided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, evidence_id),
    UNIQUE KEY uk_flow_decision_evidence_history (
        system_id, tenant_id, instance_id, history_sequence
    ),
    KEY idx_flow_decision_evidence_instance (
        system_id, tenant_id, instance_id, decided_at, evidence_id
    ),
    KEY idx_flow_decision_evidence_template (
        system_id, tenant_id, template_id, template_version
    ),
    CONSTRAINT fk_flow_decision_evidence_history FOREIGN KEY (
        system_id, tenant_id, instance_id, history_sequence
    ) REFERENCES un_flow_history_event (
        system_id, tenant_id, instance_id, event_sequence
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_decision_evidence_template FOREIGN KEY (
        system_id, tenant_id, template_id, template_version
    ) REFERENCES un_flow_decision_comment_template_version (
        system_id, tenant_id, template_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_decision_evidence_delegation FOREIGN KEY (
        system_id, tenant_id, delegation_id
    ) REFERENCES un_flow_approval_delegation (
        system_id, tenant_id, delegation_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_decision_evidence_identity CHECK (
        evidence_id > 0
        AND instance_id > 0
        AND history_sequence > 1
        AND stage_index <= 9
        AND actor_id > 0
        AND represented_member_id > 0
    ),
    CONSTRAINT ck_flow_decision_evidence_branch CHECK (
        branch_code IS NULL
        OR branch_code REGEXP '^[a-z][a-z0-9_]{0,63}$'
    ),
    CONSTRAINT ck_flow_decision_evidence_decision CHECK (
        decision IN ('APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_flow_decision_evidence_signature CHECK (
        (
            signature_kind IS NULL
            AND signature_file_id IS NULL
            AND typed_signature IS NULL
        )
        OR (
            signature_kind = 'FILE'
            AND signature_file_id IS NOT NULL
            AND signature_file_id > 0
            AND typed_signature IS NULL
        )
        OR (
            signature_kind = 'TYPED'
            AND signature_file_id IS NULL
            AND typed_signature IS NOT NULL
            AND CHAR_LENGTH(TRIM(typed_signature)) BETWEEN 1 AND 120
        )
    ),
    CONSTRAINT ck_flow_decision_evidence_template CHECK (
        (
            template_id IS NULL
            AND template_version IS NULL
            AND template_name IS NULL
        )
        OR (
            template_id IS NOT NULL
            AND template_id > 0
            AND template_version IS NOT NULL
            AND template_version > 0
            AND template_name IS NOT NULL
            AND CHAR_LENGTH(TRIM(template_name)) BETWEEN 1 AND 80
        )
    ),
    CONSTRAINT ck_flow_decision_evidence_actor CHECK (
        (
            actor_id = represented_member_id
            AND delegation_id IS NULL
        )
        OR (
            actor_id <> represented_member_id
            AND delegation_id IS NOT NULL
            AND delegation_id > 0
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_decision_evidence_file (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255)
        CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_attachment BOOLEAN NOT NULL,
    is_signature BOOLEAN NOT NULL,
    attachment_order TINYINT UNSIGNED NULL,
    PRIMARY KEY (system_id, tenant_id, evidence_id, file_id),
    UNIQUE KEY uk_flow_decision_evidence_attachment_order (
        system_id, tenant_id, evidence_id, attachment_order
    ),
    CONSTRAINT fk_flow_decision_evidence_file FOREIGN KEY (
        system_id, tenant_id, evidence_id
    ) REFERENCES un_flow_decision_evidence (
        system_id, tenant_id, evidence_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_decision_evidence_file_identity CHECK (
        file_id > 0
        AND CHAR_LENGTH(TRIM(original_name)) BETWEEN 1 AND 255
        AND CHAR_LENGTH(TRIM(content_type)) BETWEEN 1 AND 255
        AND sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_flow_decision_evidence_file_roles CHECK (
        (is_attachment = TRUE OR is_signature = TRUE)
        AND (
            (is_attachment = TRUE AND attachment_order BETWEEN 0 AND 4)
            OR (is_attachment = FALSE AND attachment_order IS NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
