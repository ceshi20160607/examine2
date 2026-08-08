CREATE TABLE un_event_delivery_preference (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_delivery_preference_owner (
        system_id, tenant_id, member_id, template_code, channel
    ),
    KEY idx_event_delivery_preference_member_tenant (system_id, member_id, tenant_id),
    KEY idx_event_delivery_preference_template (system_id, template_code),
    CONSTRAINT ck_event_delivery_preference_scope CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND member_id > 0
    ),
    CONSTRAINT ck_event_delivery_preference_channel CHECK (channel = 'INBOX'),
    CONSTRAINT ck_event_delivery_preference_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_event_delivery_preference_version CHECK (version > 0),
    CONSTRAINT fk_event_delivery_preference_member_tenant
        FOREIGN KEY (system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id),
    CONSTRAINT fk_event_delivery_preference_template
        FOREIGN KEY (system_id, template_code)
        REFERENCES un_event_message_template (system_id, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
