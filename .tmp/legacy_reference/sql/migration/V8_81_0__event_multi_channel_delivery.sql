ALTER TABLE un_event_message_delivery_log
    DROP CHECK ck_event_delivery_channel,
    ADD CONSTRAINT ck_event_delivery_channel
        CHECK (channel IN ('INBOX', 'EMAIL', 'WEBHOOK')),
    ADD COLUMN masked_destination VARCHAR(200) NULL AFTER failure_message,
    ADD COLUMN duration_ms BIGINT NULL AFTER masked_destination,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER duration_ms,
    ADD CONSTRAINT ck_event_delivery_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD KEY idx_event_delivery_admin (
        system_id, tenant_id, channel, status, template_code, created_at, id
    );

ALTER TABLE un_event_delivery_preference
    DROP CHECK ck_event_delivery_preference_channel,
    ADD CONSTRAINT ck_event_delivery_preference_channel
        CHECK (channel IN ('INBOX', 'EMAIL', 'WEBHOOK'));

CREATE TABLE un_event_message_delivery_attempt (
    id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT NULL,
    trace_id VARCHAR(64) NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_delivery_attempt (delivery_id, attempt_no),
    KEY idx_event_delivery_attempt_scope (system_id, tenant_id, delivery_id, attempt_no),
    CONSTRAINT ck_event_delivery_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_event_delivery_attempt_status
        CHECK (status IN ('PENDING', 'DELIVERED', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_event_delivery_attempt_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT fk_event_delivery_attempt_delivery
        FOREIGN KEY (delivery_id) REFERENCES un_event_message_delivery_log (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_event_message_delivery_attempt (
    id, delivery_id, system_id, tenant_id, attempt_no, status, duration_ms, trace_id,
    failure_code, failure_message, started_at, completed_at
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY delivery.id) AS SIGNED),
    delivery.id, delivery.system_id, delivery.tenant_id, delivery.attempt_count,
    delivery.status, NULL, NULL, delivery.failure_code, delivery.failure_message,
    delivery.created_at, delivery.completed_at
FROM un_event_message_delivery_log delivery
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_event_message_delivery_attempt
) base
WHERE delivery.attempt_count > 0;
