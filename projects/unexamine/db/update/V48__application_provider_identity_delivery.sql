-- 企业微信成员映射把平台侧用户绑定到当前应用范围内的真实成员；外部投递扩展继续复用统一消息和投递记录。
CREATE TABLE `app_provider_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台成员映射主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `external_user_id` VARCHAR(255) NOT NULL COMMENT '平台侧稳定用户标识，例如企业微信UserID',
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员',
  `target_platform_id` BIGINT NULL COMMENT '目标平台主键',
  `target_platform_member_id` BIGINT NULL COMMENT '目标平台成员主键',
  `target_system_id` BIGINT NULL COMMENT '目标系统主键',
  `target_tenant_id` BIGINT NULL COMMENT '目标租户主键',
  `target_tenant_member_id` BIGINT NULL COMMENT '目标租户成员主键',
  `status` VARCHAR(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_identity_external` (`connection_id`, `external_user_id`),
  KEY `idx_app_provider_identity_target_platform` (`target_platform_id`, `target_platform_member_id`, `status`),
  KEY `idx_app_provider_identity_target_system` (`target_system_id`, `target_tenant_id`, `target_tenant_member_id`, `status`),
  CONSTRAINT `fk_app_provider_identity_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_identity_platform_member` FOREIGN KEY (`target_platform_id`, `target_platform_member_id`) REFERENCES `plat_member` (`platform_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_tenant_member` FOREIGN KEY (`target_tenant_id`, `target_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_identity_status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT `chk_app_provider_identity_target` CHECK (
    (`target_type` = 'PLATFORM' AND `target_platform_id` IS NOT NULL AND `target_platform_member_id` IS NOT NULL
      AND `target_system_id` IS NULL AND `target_tenant_id` IS NULL AND `target_tenant_member_id` IS NULL)
    OR
    (`target_type` = 'SYSTEM' AND `target_platform_id` IS NULL AND `target_platform_member_id` IS NULL
      AND `target_system_id` IS NOT NULL AND `target_tenant_id` IS NOT NULL AND `target_tenant_member_id` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台成员映射';

CREATE TABLE `app_provider_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台消息投递扩展主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `identity_id` BIGINT NOT NULL COMMENT '目标外部平台成员映射主键',
  `message_id` BIGINT NOT NULL COMMENT '统一消息主键',
  `message_delivery_id` BIGINT NOT NULL COMMENT '统一消息投递主键',
  `idempotency_key` VARCHAR(255) NOT NULL COMMENT '同一连接内的业务幂等键',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化投递请求SHA-256摘要',
  `deep_link` VARCHAR(1000) NOT NULL COMMENT '按当前上下文生成的站内业务深链',
  `status` VARCHAR(32) NOT NULL COMMENT '投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销',
  `provider_message_id` VARCHAR(255) NULL COMMENT '平台返回的消息标识',
  `error_code` VARCHAR(100) NULL COMMENT '不含敏感信息的最近错误编码',
  `created_by_account_id` BIGINT NOT NULL COMMENT '发起人平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `sent_time` DATETIME(3) NULL COMMENT '实际发送时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_delivery_idempotency` (`connection_id`, `idempotency_key`),
  UNIQUE KEY `uk_app_provider_delivery_message_delivery` (`message_delivery_id`),
  KEY `idx_app_provider_delivery_status` (`connection_id`, `status`, `create_time`),
  CONSTRAINT `fk_app_provider_delivery_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_delivery_identity` FOREIGN KEY (`identity_id`) REFERENCES `app_provider_identity` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message_delivery` FOREIGN KEY (`message_delivery_id`) REFERENCES `msg_delivery` (`id`),
  CONSTRAINT `fk_app_provider_delivery_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_delivery_status` CHECK (`status` IN ('PENDING', 'BLOCKED_EXTERNAL', 'SENT', 'DELIVERED', 'FAILED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台消息投递扩展';
