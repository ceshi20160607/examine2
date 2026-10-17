-- print 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_print_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打印模板草稿主键',
  `system_id` bigint NOT NULL COMMENT '模板所属系统主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `code` varchar(100) NOT NULL COMMENT '模块内模板编码',
  `name` varchar(200) NOT NULL COMMENT '模板展示名称',
  `page_size` varchar(32) NOT NULL COMMENT '纸张尺寸',
  `orientation` varchar(32) NOT NULL COMMENT '页面方向',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `template_json` json NOT NULL COMMENT '页眉、页脚、签章区和明细表格等草稿布局',
  `status` varchar(32) NOT NULL COMMENT '草稿、已发布或停用等模板状态',
  `created_by_member_id` bigint NOT NULL COMMENT '创建模板的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '草稿乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_code` (`owner_tenant_id`,`module_id`,`code`),
  KEY `idx_print_template_module_id` (`module_id`,`id`),
  KEY `idx_print_template_owner_module_id` (`owner_tenant_id`,`module_id`,`id`),
  CONSTRAINT `chk_print_template_revision` CHECK ((`draft_revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定业务模块的打印模板草稿';

CREATE TABLE `un_print_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '不可变打印模板版本主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键，用于校验发布人',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '来源模板草稿主键',
  `module_config_version_id` bigint NOT NULL COMMENT '发布时固定的模块配置版本',
  `version_number` int NOT NULL COMMENT '同一模板内递增的发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布所依据的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '模板与字段引用快照摘要',
  `snapshot_json` json NOT NULL COMMENT '页眉、页脚、明细和签章等不可变布局快照',
  `published_by_member_id` bigint NOT NULL COMMENT '发布模板的系统成员主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_version` (`template_id`,`version_number`),
  UNIQUE KEY `uk_print_template_revision` (`template_id`,`draft_revision`),
  KEY `idx_print_template_version_id` (`template_id`,`id`),
  KEY `idx_print_template_version_module_id` (`module_id`,`id`),
  KEY `idx_print_template_version_module_config` (`module_id`,`id`,`module_config_version_id`),
  CONSTRAINT `chk_print_version_number` CHECK (((`version_number` > 0) and (`draft_revision` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变打印模板发布版本与模块配置依赖';

CREATE TABLE `un_print_template_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板当前发布指针主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '模板草稿主键',
  `current_version_id` bigint NOT NULL COMMENT '当前生效的不可变模板版本',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近发布或回滚的系统成员主键',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '发布指针乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_publication` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板当前生效版本指针，可回滚至历史版本';

CREATE TABLE `un_print_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打印预览或输出作业主键',
  `system_id` bigint NOT NULL COMMENT '执行系统主键',
  `tenant_id` bigint NOT NULL COMMENT '执行租户主键',
  `module_id` bigint NOT NULL COMMENT '目标业务模块主键',
  `record_id` bigint NOT NULL COMMENT '打印目标业务记录主键',
  `record_version` int NOT NULL COMMENT '生成快照时目标记录乐观锁版本',
  `template_version_id` bigint NOT NULL COMMENT '固定使用的不可变打印模板版本',
  `module_config_version_id` bigint NOT NULL COMMENT '模板版本固定的模块配置版本',
  `permission_version_id` bigint NOT NULL COMMENT '打印时固定的权限版本',
  `background_job_id` bigint DEFAULT NULL COMMENT '异步作业业务申请主键，运行状态存 Redis',
  `authorization_snapshot_json` json NOT NULL COMMENT '打印时字段、附件、签章与数据范围权限快照',
  `record_snapshot_json` json NOT NULL COMMENT '打印时目标记录及关联字段展示值快照',
  `status` varchar(32) NOT NULL COMMENT '生成中、成功或失败状态',
  `output_file_id` bigint DEFAULT NULL COMMENT '生成的 PDF 文件主键',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因摘要',
  `created_by_member_id` bigint NOT NULL COMMENT '发起打印的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '生成完成时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '打印作业乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_print_job_record` (`record_id`,`create_time`),
  KEY `idx_print_job_status` (`status`,`create_time`),
  CONSTRAINT `chk_print_job_output` CHECK (((`status` <> _utf8mb4'SUCCEEDED') or ((`output_file_id` is not null) and (`finish_time` is not null)))),
  CONSTRAINT `chk_print_job_record_version` CHECK ((`record_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定同模块模板版本与记录的打印结果审计';
