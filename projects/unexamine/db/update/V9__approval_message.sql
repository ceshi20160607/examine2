-- 用途：保存基础审批旅程给申请人和审批人的站内消息；旧业务数据不回填，后续事件在原事务中写入。
-- 这里只保存通用通知文案和目标标识，消息列表不复制业务字段或审批意见；打开目标时仍走原接口鉴权。
CREATE TABLE `un_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '站内消息主键',
  `system_id` BIGINT NOT NULL COMMENT '消息所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '消息所属租户主键',
  `recipient_account_id` BIGINT NOT NULL COMMENT '收件平台账号主键',
  `kind` VARCHAR(32) NOT NULL COMMENT '审批待办或审批结果等消息类别',
  `title` VARCHAR(120) NOT NULL COMMENT '不包含业务敏感字段的消息标题',
  `module_id` BIGINT NOT NULL COMMENT '目标业务模块主键',
  `record_id` BIGINT NOT NULL COMMENT '关联业务记录主键',
  `task_id` BIGINT NOT NULL COMMENT '关联审批任务主键',
  `read_time` DATETIME(3) DEFAULT NULL COMMENT '收件人首次标记已读时间',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息生成时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_inbox` (`system_id`,`tenant_id`,`recipient_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批旅程的站内消息收件箱';
