-- Immutable structured mention recipients for record comments.

CREATE TABLE un_collab_record_comment_mention (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    mentioned_member_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, record_id, comment_id, mentioned_member_id
    ),
    KEY idx_collab_comment_mention_recipient (
        system_id, tenant_id, mentioned_member_id, created_at, comment_id
    ),
    CONSTRAINT fk_collab_comment_mention_comment
        FOREIGN KEY (system_id, tenant_id, record_id, comment_id)
        REFERENCES un_collab_record_comment (
            system_id, tenant_id, record_id, comment_id
        )
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_mention_team_member
        FOREIGN KEY (system_id, tenant_id, record_id, mentioned_member_id)
        REFERENCES un_collab_record_team_member (
            system_id, tenant_id, record_id, member_id
        )
        ON DELETE RESTRICT,
    CONSTRAINT fk_collab_comment_mention_created_by
        FOREIGN KEY (system_id, created_by, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collab_comment_mention_no_self CHECK (
        mentioned_member_id <> created_by
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
