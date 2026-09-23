-- 应用图标只保存平台预设代码，页面从固定图标库选择，不接收任意文件或原始配置。
-- 现有应用统一回填 api；本迁移保留应用、版本、凭证和调用历史。
ALTER TABLE `app_definition`
  ADD COLUMN `icon_code` VARCHAR(32) NOT NULL DEFAULT 'api' COMMENT '应用预设图标代码' AFTER `application_type`,
  COMMENT = '平台或系统范围内的受控访问应用定义',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用主键',
  MODIFY COLUMN `context_type` VARCHAR(32) NOT NULL COMMENT '应用所属上下文：PLATFORM平台、SYSTEM系统',
  MODIFY COLUMN `platform_id` BIGINT NULL COMMENT '所属平台主键',
  MODIFY COLUMN `owner_system_id` BIGINT NULL COMMENT '所属系统主键，平台应用为空',
  MODIFY COLUMN `owner_tenant_id` BIGINT NULL COMMENT '所属租户主键，平台应用为空',
  MODIFY COLUMN `code` VARCHAR(100) NOT NULL COMMENT '当前上下文内唯一的应用编码',
  MODIFY COLUMN `name` VARCHAR(200) NOT NULL COMMENT '应用名称',
  MODIFY COLUMN `description` VARCHAR(1000) NULL COMMENT '应用用途说明',
  MODIFY COLUMN `application_type` VARCHAR(32) NOT NULL COMMENT '应用类型：SERVICE服务调用、WEBHOOK事件回调',
  MODIFY COLUMN `draft_revision` INT NOT NULL DEFAULT 1 COMMENT '草稿修订号',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除',
  MODIFY COLUMN `created_by_account_id` BIGINT NOT NULL COMMENT '创建应用的平台账号主键',
  MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，旧字段保持兼容',
  MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，旧字段保持兼容',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '应用修改的乐观锁版本',
  ADD CONSTRAINT `ck_app_definition_icon` CHECK (`icon_code` IN ('api', 'link', 'workflow', 'database', 'robot', 'message'));
