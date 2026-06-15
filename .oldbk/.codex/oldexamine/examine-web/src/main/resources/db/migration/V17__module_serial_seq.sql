-- 自定义编号字段序号（按 field + 重置周期）
CREATE TABLE IF NOT EXISTS un_module_serial_seq (
    id          BIGINT       NOT NULL COMMENT '主键',
    system_id   BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    app_id      BIGINT       NOT NULL,
    model_id    BIGINT       NOT NULL,
    field_code  VARCHAR(64)  NOT NULL,
    reset_key   VARCHAR(32)  NOT NULL DEFAULT 'never' COMMENT 'never|yyyyMMdd|yyyyMM',
    seq_no      BIGINT       NOT NULL DEFAULT 0,
    update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_serial_seq (system_id, tenant_id, model_id, field_code, reset_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段自定义编号序号';
