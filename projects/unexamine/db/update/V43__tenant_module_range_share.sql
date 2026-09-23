-- 将现有记录共享扩展为模块数据范围共享，保留授权主键、使用日志及旧时间字段。
-- 先允许模块归属为空，再依据原记录回填；不复制业务数据、不删除历史授权。
ALTER TABLE `biz_tenant_share`
  ADD COLUMN `module_id` BIGINT NULL COMMENT '共享数据所属模块，记录授权由原记录归属回填' AFTER `target_tenant_id`,
  ADD COLUMN `scope_type` VARCHAR(32) NOT NULL DEFAULT 'RECORD' COMMENT '授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围' AFTER `record_id`,
  ADD COLUMN `scope_json` JSON NULL COMMENT '范围条件、字段类型绑定及授予时来源共享权限约束快照' AFTER `scope_type`,
  ADD COLUMN `start_time` DATETIME(3) NULL COMMENT '授权开始生效时间，为空表示授予后立即生效' AFTER `status`;

UPDATE `biz_tenant_share` s
JOIN `biz_record` r ON r.`id` = s.`record_id` AND r.`tenant_id` = s.`source_tenant_id`
SET s.`module_id` = r.`module_id`;

-- 为被修改表的全部字段补充中文业务备注；旧 *_at 映射保持兼容，新增时间仅使用 *_time。
ALTER TABLE `biz_tenant_share`
  COMMENT = '同系统跨租户记录及动态模块范围共享授权，不改变数据所有权',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '共享授权主键，历史使用记录保持引用',
  MODIFY COLUMN `system_id` BIGINT NOT NULL COMMENT '共享双方所属系统',
  MODIFY COLUMN `source_tenant_id` BIGINT NOT NULL COMMENT '拥有业务数据的来源租户',
  MODIFY COLUMN `target_tenant_id` BIGINT NOT NULL COMMENT '获得访问关系的目标租户',
  MODIFY COLUMN `module_id` BIGINT NOT NULL COMMENT '共享数据所属模块',
  MODIFY COLUMN `record_id` BIGINT NULL COMMENT '指定记录授权的原记录主键，动态范围授权为空',
  MODIFY COLUMN `permission_json` JSON NOT NULL COMMENT '明确允许的业务动作，仍与目标用户角色权限取交集',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '授权存储状态：ACTIVE有效，REVOKED已撤销',
  MODIFY COLUMN `expires_at` DATETIME(3) NULL COMMENT '授权失效时间，旧接口和字段保持兼容',
  MODIFY COLUMN `granted_by_member_id` BIGINT NOT NULL COMMENT '来源租户授予授权的系统成员',
  MODIFY COLUMN `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近授予时间，旧字段保持兼容',
  MODIFY COLUMN `revoked_at` DATETIME(3) NULL COMMENT '授权撤销时间，历史授权不删除',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '授权修改的乐观锁版本',
  ADD KEY `idx_biz_share_module_scope` (`system_id`, `target_tenant_id`, `module_id`, `scope_type`, `status`),
  ADD CONSTRAINT `fk_biz_share_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  ADD CONSTRAINT `ck_biz_share_scope` CHECK (
    (`scope_type` = 'RECORD' AND `record_id` IS NOT NULL AND `scope_json` IS NULL)
    OR (`scope_type` = 'DATA_RANGE' AND `record_id` IS NULL AND JSON_TYPE(`scope_json`) = 'OBJECT' AND `scope_json` IS NOT NULL)
  );
