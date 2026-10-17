-- audit 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作审计事件主键',
  `trace_id` varchar(64) NOT NULL COMMENT '跨服务调用链追踪标识',
  `request_id` varchar(64) DEFAULT NULL COMMENT '触发操作的请求标识',
  `context_type` varchar(32) NOT NULL COMMENT '事件所属的平台或系统范围类型',
  `actor_account_id` bigint DEFAULT NULL COMMENT '执行操作的平台账号主键，系统事件也使用统一账号',
  `platform_id` bigint DEFAULT NULL COMMENT '事件所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '事件所属系统主键，平台事件为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '事件所属租户主键，平台或系统公共事件为空',
  `member_id` bigint DEFAULT NULL COMMENT '执行操作的系统成员主键，平台事件为空',
  `application_id` bigint DEFAULT NULL COMMENT '应用调用产生事件时的应用主键',
  `event_category` varchar(64) NOT NULL COMMENT '审计事件业务类别',
  `event_code` varchar(100) NOT NULL COMMENT '审计事件动作编码',
  `object_type` varchar(100) DEFAULT NULL COMMENT '被操作业务对象类型',
  `object_id` varchar(100) DEFAULT NULL COMMENT '被操作业务对象标识',
  `result_code` varchar(64) NOT NULL COMMENT '操作结果或错误编码',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '发起操作的客户端 IP 地址',
  `user_agent` varchar(1000) DEFAULT NULL COMMENT '发起操作的客户端标识',
  `permission_snapshot` json DEFAULT NULL COMMENT '执行时的权限判定快照',
  `detail_json` json NOT NULL COMMENT '不含敏感明文的审计事件详情',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_trace` (`trace_id`),
  KEY `idx_audit_object` (`object_type`,`object_id`,`occur_time`),
  KEY `idx_audit_context` (`system_id`,`tenant_id`,`occur_time`),
  KEY `idx_audit_category` (`event_category`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的业务操作审计事件';

CREATE TABLE `un_audit_field_change` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段变更审计主键',
  `audit_event_id` bigint NOT NULL COMMENT '对应的操作审计事件主键',
  `field_code` varchar(100) NOT NULL COMMENT '发生变更的业务字段编码',
  `value_type` varchar(32) NOT NULL COMMENT '字段值的数据类型',
  `before_value_json` json DEFAULT NULL COMMENT '变更前字段值，敏感信息须脱敏',
  `after_value_json` json DEFAULT NULL COMMENT '变更后字段值，敏感信息须脱敏',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '字段值敏感等级',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '字段变更审计记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_field_event` (`audit_event_id`),
  KEY `idx_audit_field_code` (`field_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的业务字段变更前后值';

CREATE TABLE `un_audit_retention_marker` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '清理留痕记录主键',
  `context_type` varchar(32) NOT NULL COMMENT '被清理对象所属的平台或系统范围类型',
  `system_id` bigint DEFAULT NULL COMMENT '被清理对象所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '被清理对象所属租户主键',
  `object_type` varchar(100) NOT NULL COMMENT '被清理业务对象类型',
  `object_id` varchar(100) NOT NULL COMMENT '被清理业务对象标识',
  `business_key` varchar(255) DEFAULT NULL COMMENT '便于后续追溯的业务编号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '清理前业务快照的摘要',
  `snapshot_json` json DEFAULT NULL COMMENT '按留存策略保留的清理前业务快照',
  `purge_reason` varchar(1000) NOT NULL COMMENT '清理原因及业务依据',
  `purged_by_account_id` bigint NOT NULL COMMENT '执行清理的平台账号主键',
  `purge_time` datetime(3) NOT NULL COMMENT '实际清理业务数据的时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '清理留痕记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_retention_object` (`context_type`,`system_id`,`tenant_id`,`object_type`,`object_id`),
  KEY `idx_audit_retention_time` (`purge_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务数据清理后的不可变留痕';
