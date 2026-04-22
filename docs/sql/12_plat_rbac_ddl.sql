-- 平台级 RBAC：角色、菜单、角色-菜单、账号-角色（与自建系统内 module 权限无关）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS un_plat_account_role;
DROP TABLE IF EXISTS un_plat_role_menu;
DROP TABLE IF EXISTS un_plat_role;
DROP TABLE IF EXISTS un_plat_menu;

CREATE TABLE un_plat_menu (
  id              BIGINT        NOT NULL COMMENT '菜单ID',
  parent_id       BIGINT        NOT NULL DEFAULT 0 COMMENT '父级菜单；0=根',
  menu_name       VARCHAR(128)  NOT NULL COMMENT '菜单名称',
  menu_type       TINYINT       NOT NULL DEFAULT 2 COMMENT '1=目录 2=菜单 3=按钮',
  path            VARCHAR(255)  NULL COMMENT '前端路由或标识',
  perm_code       VARCHAR(64)   NULL COMMENT '权限码（与接口/按钮一致）；目录可为空',
  icon            VARCHAR(64)   NULL COMMENT '图标',
  sort_no         INT           NOT NULL DEFAULT 0 COMMENT '排序',
  visible_flag    TINYINT       NOT NULL DEFAULT 1 COMMENT '1=显示 0=隐藏',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 2=停用',
  remark          VARCHAR(512)  NULL COMMENT '备注',
  create_user_id  BIGINT        NULL COMMENT '创建人 platId',
  update_user_id  BIGINT        NULL COMMENT '更新人 platId',
  create_time     DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time     DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_menu_parent (parent_id, sort_no),
  KEY idx_plat_menu_perm (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台控制台菜单/权限项';

CREATE TABLE un_plat_role (
  id              BIGINT        NOT NULL COMMENT '角色ID',
  role_code       VARCHAR(64)   NOT NULL COMMENT '角色编码（唯一）',
  role_name       VARCHAR(128)  NOT NULL COMMENT '角色名称',
  remark          VARCHAR(512)  NULL COMMENT '备注',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 2=停用',
  create_user_id  BIGINT        NULL,
  update_user_id  BIGINT        NULL,
  create_time     DATETIME(3)   NOT NULL,
  update_time     DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台角色';

CREATE TABLE un_plat_role_menu (
  id              BIGINT        NOT NULL COMMENT '主键',
  role_id         BIGINT        NOT NULL COMMENT 'un_plat_role.id',
  menu_id         BIGINT        NOT NULL COMMENT 'un_plat_menu.id',
  create_user_id  BIGINT        NULL,
  update_user_id  BIGINT        NULL,
  create_time     DATETIME(3)   NOT NULL,
  update_time     DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_role_menu (role_id, menu_id),
  KEY idx_plat_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台角色与菜单（权限项）关联';

CREATE TABLE un_plat_account_role (
  id                BIGINT        NOT NULL COMMENT '主键',
  plat_account_id   BIGINT        NOT NULL COMMENT 'un_plat_account.id',
  role_id           BIGINT        NOT NULL COMMENT 'un_plat_role.id',
  create_user_id    BIGINT        NULL,
  update_user_id    BIGINT        NULL,
  create_time       DATETIME(3)   NOT NULL,
  update_time       DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_account_role (plat_account_id, role_id),
  KEY idx_plat_account_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号与角色关联';

SET FOREIGN_KEY_CHECKS = 1;
