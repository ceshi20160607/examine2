-- 全新独立数据库的初始结构，只创建平台账号登录功能使用的原名表。
-- 不在旧库执行；后续结构变更使用增量迁移，不删除已有业务数据。
CREATE DATABASE IF NOT EXISTS `unexamine_clean`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `unexamine_clean`;

CREATE TABLE IF NOT EXISTS `un_plat_account` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
    `account` VARCHAR(64) NOT NULL COMMENT '登录账号',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要',
    `display_name` VARCHAR(100) NOT NULL COMMENT '账号显示名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_account_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS `un_plat_log_login` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台登录日志主键',
    `account_id` BIGINT UNSIGNED NULL COMMENT '匹配到的平台账号主键',
    `login_account` VARCHAR(64) NOT NULL COMMENT '本次提交的登录账号',
    `success` TINYINT NOT NULL COMMENT '登录是否成功：1 成功，0 失败',
    `failure_reason` VARCHAR(100) NULL COMMENT '登录失败原因',
    `ip_address` VARCHAR(45) NOT NULL COMMENT '发起登录的客户端地址',
    `login_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_plat_log_login_account_time` (`login_account`, `login_time`),
    CONSTRAINT `fk_un_plat_log_login_account`
        FOREIGN KEY (`account_id`) REFERENCES `un_plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台登录日志';
