CREATE TABLE IF NOT EXISTS un_module_dept (
    id             BIGINT       NOT NULL COMMENT '部门ID',
    system_id      BIGINT       NOT NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 0,
    app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
    parent_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门，0=根',
    dept_code      VARCHAR(64)  NOT NULL COMMENT '部门编码（同 app 内唯一）',
    dept_name      VARCHAR(128) NOT NULL COMMENT '部门名称',
    sort_no        INT          NOT NULL DEFAULT 0,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 2=停用',
    remark         VARCHAR(255) NULL,
    create_user_id BIGINT       NULL,
    update_user_id BIGINT       NULL,
    create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_dept_code (app_id, dept_code),
    KEY idx_module_dept_query (app_id, parent_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用部门';
