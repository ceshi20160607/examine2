-- 用途：给通用业务记录增加可在列表与详情显示的标题和稳定编号。
-- 增量修改现有表，不删除旧记录；先为已有记录补值，再建立租户与模块内的编号唯一约束。
ALTER TABLE `un_module_record`
  ADD COLUMN `title` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '业务记录在列表与详情中显示的标题' AFTER `owner_member_id`,
  ADD COLUMN `record_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务记录在租户和模块内唯一的稳定编号' AFTER `title`;

-- 迁移已有数据：旧记录原来没有标题和编号，用主键生成可追溯占位值；新写入由业务服务生成编号。
UPDATE `un_module_record`
SET `title` = CONCAT('记录 ', `id`), `record_no` = CONCAT('R', `id`)
WHERE `record_no` = '';

ALTER TABLE `un_module_record`
  ADD UNIQUE KEY `uk_module_record_no` (`tenant_id`, `module_id`, `record_no`);
