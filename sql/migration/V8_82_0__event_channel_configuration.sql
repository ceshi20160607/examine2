CREATE TABLE un_event_channel_configuration (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    endpoint VARCHAR(2048) NULL,
    secret_ref VARCHAR(512) NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    last_check_at DATETIME(3) NULL,
    last_check_status VARCHAR(32) NULL,
    last_check_trace_id VARCHAR(128) NULL,
    last_check_duration_ms BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_channel_configuration (system_id, channel),
    KEY idx_event_channel_configuration_enabled (system_id, enabled, channel),
    CONSTRAINT fk_event_channel_configuration_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT ck_event_channel_configuration_channel
        CHECK (channel IN ('EMAIL', 'WEBHOOK')),
    CONSTRAINT ck_event_channel_configuration_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_event_channel_configuration_timeout CHECK (timeout_ms BETWEEN 100 AND 30000),
    CONSTRAINT ck_event_channel_configuration_shape CHECK (
        (channel = 'EMAIL' AND endpoint IS NULL AND secret_ref IS NULL)
        OR
        (channel = 'WEBHOOK')
    ),
    CONSTRAINT ck_event_channel_configuration_check_status CHECK (
        last_check_status IS NULL
        OR last_check_status IN ('SENT', 'TEMPORARY_FAILURE', 'PERMANENT_FAILURE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
