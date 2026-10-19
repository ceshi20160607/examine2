-- 用途：首条业务旅程的统一平台账号；系统成员身份另存在系统模块，不凭账号直接进入业务系统。
CREATE TABLE un_plat_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台账号标识',
  username VARCHAR(64) NOT NULL COMMENT '全平台唯一登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT '带盐的单向口令哈希',
  display_name VARCHAR(100) NOT NULL COMMENT '用户显示名称',
  email VARCHAR(255) NULL COMMENT '电子邮箱',
  mobile VARCHAR(32) NULL COMMENT '手机号码',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1启用，0停用',
  last_login_time DATETIME(3) NULL COMMENT '最近成功登录时间',
  create_time DATETIME(3) NOT NULL COMMENT '账号创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '账号修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_account_username (username),
  KEY idx_plat_account_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一平台账号';
