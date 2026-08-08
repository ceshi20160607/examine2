-- P4-D2 persists tenant-scoped plain-text comments without coupling collaboration
-- queries to runtime record data-scope tables. Runtime visibility is checked through
-- RuntimeRecordAccessFacade before this table is accessed.

CREATE TABLE un_collab_record_comment (
    comment_id BIGINT NOT NULL AUTO_INCREMENT,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,
    author_member_id BIGINT NOT NULL,
    body VARCHAR(4000) NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (comment_id),
    UNIQUE KEY uk_collab_comment_scope (
        system_id, tenant_id, record_id, comment_id
    ),
    UNIQUE KEY uk_collab_comment_idempotency (
        system_id, tenant_id, record_id, author_member_id, idempotency_key
    ),
    KEY idx_collab_comment_page (
        system_id, tenant_id, record_id, created_at, comment_id
    ),
    KEY idx_collab_comment_parent (
        system_id, tenant_id, record_id, parent_comment_id, created_at, comment_id
    ),
    CONSTRAINT fk_collab_comment_record
        FOREIGN KEY (system_id, tenant_id, record_id)
        REFERENCES un_module_record (system_id, tenant_id, record_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_parent
        FOREIGN KEY (system_id, tenant_id, record_id, parent_comment_id)
        REFERENCES un_collab_record_comment (
            system_id, tenant_id, record_id, comment_id
        )
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_author
        FOREIGN KEY (system_id, author_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_created_by
        FOREIGN KEY (system_id, created_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_updated_by
        FOREIGN KEY (system_id, updated_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_deleted_by
        FOREIGN KEY (system_id, deleted_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collab_comment_version CHECK (version >= 1),
    CONSTRAINT ck_collab_comment_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_collab_comment_body CHECK (
        (deleted = 0 AND body IS NOT NULL AND CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 4000)
        OR (deleted = 1 AND body IS NULL)
    ),
    CONSTRAINT ck_collab_comment_tombstone CHECK (
        (deleted = 0 AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (deleted = 1 AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
