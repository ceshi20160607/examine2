-- 首批表用于验证“系统/租户 -> 模块字段配置 -> 模块记录 -> 按宽度展示详情”的最短数据链。
-- 只在隔离库 unexamine_r16 执行；本文件不包含旧库迁移、旧数据或删表语句。
CREATE DATABASE IF NOT EXISTS unexamine_r16
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE unexamine_r16;

CREATE TABLE IF NOT EXISTS un_sys_system (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务系统主键',
    system_code VARCHAR(64) NOT NULL COMMENT '系统唯一编码',
    system_name VARCHAR(120) NOT NULL COMMENT '系统显示名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_system_code (system_code)
) ENGINE=InnoDB COMMENT='平台内业务系统';

CREATE TABLE IF NOT EXISTS un_sys_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户主键',
    system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '系统内租户编码',
    tenant_name VARCHAR(120) NOT NULL COMMENT '租户显示名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_tenant_system_code (system_id, tenant_code),
    KEY idx_sys_tenant_system (system_id),
    CONSTRAINT fk_sys_tenant_system FOREIGN KEY (system_id) REFERENCES un_sys_system (id)
) ENGINE=InnoDB COMMENT='系统租户';

CREATE TABLE IF NOT EXISTS un_module (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块主键',
    system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
    module_code VARCHAR(64) NOT NULL COMMENT '系统内模块编码',
    module_name VARCHAR(120) NOT NULL COMMENT '模块显示名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_system_code (system_id, module_code),
    KEY idx_module_system (system_id),
    CONSTRAINT fk_module_system FOREIGN KEY (system_id) REFERENCES un_sys_system (id)
) ENGINE=InnoDB COMMENT='业务模块';

CREATE TABLE IF NOT EXISTS un_module_field (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块字段主键',
    module_id BIGINT NOT NULL COMMENT '所属模块主键',
    field_code VARCHAR(64) NOT NULL COMMENT '模块内字段编码',
    field_name VARCHAR(120) NOT NULL COMMENT '字段显示名称',
    data_type VARCHAR(24) NOT NULL COMMENT '字段值类型：text、number、date等',
    width_percent TINYINT UNSIGNED NOT NULL DEFAULT 100 COMMENT '详情展示宽度百分比，取值1至100',
    required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：1是，0否',
    enabled_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1是，0否',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_field_code (module_id, field_code),
    KEY idx_module_field_module (module_id),
    CONSTRAINT fk_module_field_module FOREIGN KEY (module_id) REFERENCES un_module (id),
    CONSTRAINT ck_module_field_width CHECK (width_percent BETWEEN 1 AND 100)
) ENGINE=InnoDB COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS un_module_field_sort (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块字段排序主键',
    field_id BIGINT NOT NULL COMMENT '所属模块字段主键',
    sort_order INT NOT NULL COMMENT '详情页字段显示顺序，从小到大',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_field_sort_field (field_id),
    CONSTRAINT fk_module_field_sort_field FOREIGN KEY (field_id) REFERENCES un_module_field (id)
) ENGINE=InnoDB COMMENT='模块字段排序';

CREATE TABLE IF NOT EXISTS un_module_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块记录主键',
    module_id BIGINT NOT NULL COMMENT '所属模块主键',
    tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
    record_title VARCHAR(200) NOT NULL COMMENT '记录标题',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '记录状态：1有效，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_module_record_scope (tenant_id, module_id, id),
    KEY idx_module_record_module (module_id),
    CONSTRAINT fk_module_record_module FOREIGN KEY (module_id) REFERENCES un_module (id),
    CONSTRAINT fk_module_record_tenant FOREIGN KEY (tenant_id) REFERENCES un_sys_tenant (id)
) ENGINE=InnoDB COMMENT='模块通用业务主记录';

CREATE TABLE IF NOT EXISTS un_module_record_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块记录字段值主键',
    record_id BIGINT NOT NULL COMMENT '所属模块记录主键',
    field_id BIGINT NOT NULL COMMENT '所属模块字段主键',
    value_text TEXT NULL COMMENT '按字段类型校验后的原始值，复杂值使用JSON文本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_record_data_field (record_id, field_id),
    KEY idx_module_record_data_field (field_id),
    CONSTRAINT fk_module_record_data_record FOREIGN KEY (record_id) REFERENCES un_module_record (id),
    CONSTRAINT fk_module_record_data_field FOREIGN KEY (field_id) REFERENCES un_module_field (id)
) ENGINE=InnoDB COMMENT='模块通用业务扩展记录';
