DROP TABLE IF EXISTS `un_admin_user`;
CREATE TABLE `un_admin_user` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
`username` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '用户名',
`password` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '密码',
`salt` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '安全符',
`img` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '头像',
`realname` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '真实姓名',
`num` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '员工编号',
`mobile` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '手机号',
`email` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '邮箱',
`sex` int(11) DEFAULT NULL COMMENT '0 未选择 1 男 2 女 ',
`dept_id` bigint(20) DEFAULT NULL COMMENT '部门',
`post` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '岗位',
`status` int(11) NOT NULL DEFAULT '0' COMMENT '状态,0未激活,1正常,2禁用',
`parent_id` bigint(20) DEFAULT '0' COMMENT '直属上级ID',
`last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
`last_login_ip` varchar(30) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '最后登录IP 注意兼容IPV6',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='用户表';

INSERT INTO `un_admin_user` (`id`, `username`, `password`, `salt`, `img`, `realname`, `num`, `mobile`, `email`, `sex`, `dept_id`, `post`, `status`, `parent_id`, `last_login_time`, `last_login_ip`, `create_time`, `create_user_id`, `update_time`, `update_user_id`) VALUES (1, 'admin', '47080a72e9de9e49803e8e18f574415d', 'rg51zfbjocnpq0zii4ugmhlnkuf7h5n5', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, NULL, NULL, NULL, 0, '2023-03-27 10:43:34', NULL);

DROP TABLE IF EXISTS `un_admin_dept`;
CREATE TABLE `un_admin_dept` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键' ,
`company_flag` int(11) DEFAULT '0' COMMENT '默认0 部门  1公司',
`parent_id` bigint(20) DEFAULT '0' COMMENT '父级ID 顶级部门为0',
`deepth` longtext CHARACTER SET utf8 COLLATE utf8_general_ci  COMMENT 'parent_id 构建的深度',
`name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '部门名称',
`num` int(11) DEFAULT NULL COMMENT '排序 越大越靠后',
`remark` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '' COMMENT '部门备注',
`owner_user_id` bigint(20) DEFAULT NULL COMMENT '部门负责人',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='部门表';


DROP TABLE IF EXISTS `un_admin_role`;
CREATE TABLE `un_admin_role` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键' ,
`role_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '名称',
`role_type` int(11) DEFAULT NULL COMMENT '0 超管 1自定义 ',
`remark` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '备注',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='角色表';

-- DROP TABLE IF EXISTS `un_admin_menu`;
-- CREATE TABLE `un_admin_menu` (
-- `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
-- `module_id` bigint(20) DEFAULT NULL COMMENT '所属模块',
-- `parent_id` int(10) unsigned DEFAULT '0' COMMENT '上级菜单ID',
--
-- `deepth` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci  COMMENT 'parent_id 构建的深度',
-- `menu_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单名称',
-- `realm` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '权限标识',
-- `realm_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限URL',
-- `menu_type` int(11) DEFAULT NULL COMMENT '菜单类型  1目录 2 菜单 3 按钮 4特殊',
-- `sorts` int(10) unsigned DEFAULT '0' COMMENT '排序（同级有效）',
-- `status` int(11) DEFAULT '1' COMMENT '状态  0 禁用 1 启用',
-- `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '菜单说明',
-- PRIMARY KEY (`id`) USING BTREE
-- ) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='菜单权限配置表';

DROP TABLE IF EXISTS `un_admin_menu`;
CREATE TABLE `un_admin_menu` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
`menu_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单名称',
`module_id` bigint(20) DEFAULT NULL COMMENT '所属模块',
`menu_type` int(11) DEFAULT NULL COMMENT '菜单类型 0 列表1 详情2 添加3 编辑4 删除5 导入6 导出7 打印  10 修改状态 11 转化数据',

`menu_option` longtext CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '修改状态/转化数据 对应的规则的json',
`realm_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限URL',

`sorts` int(10) unsigned DEFAULT '0' COMMENT '排序（同级有效）',
`status` int(11) DEFAULT '1' COMMENT '状态  0 禁用 1 启用',
`remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '菜单说明',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='菜单权限配置表';

INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (1, '列表', 4, 0, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (2, '详情', 4, 1, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (3, '添加', 4, 2, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (4, '编辑', 4, 3, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (5, '删除', 4, 4, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (6, '导入', 4, 5, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (7, '导出', 4, 6, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (8, '打印', 4, 7, NULL, 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (9, '修改某个字段/状态', 4, 10, '{\"old\":\"status\",\"new\":\"status\"}', 0, 1, NULL);
INSERT INTO `un_admin_menu` (`id`, `menu_name`, `module_id`, `menu_type`, `menu_option`, `sorts`, `status`, `remarks`) VALUES (10, '转化，模块下的数据转成另一个模块的数据', 4, 11, '[{\"old\":\"status\",\"new\":\"status\"},{\"old\":\"status\",\"new\":\"status\"},{\"old\":\"status\",\"new\":\"status\"}]', 0, 1, NULL);



DROP TABLE IF EXISTS `un_admin_role_menu`;
CREATE TABLE `un_admin_role_menu` (
`id` bigint(20) NOT NULL,
`role_id` bigint(20) NOT NULL COMMENT '角色ID',
`menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='角色菜单对应关系表';

DROP TABLE IF EXISTS `un_admin_user_role`;
CREATE TABLE `un_admin_user_role` (
`id` bigint(20) NOT NULL,
`user_id` bigint(20) NOT NULL COMMENT '用户ID',
`role_id` bigint(20) NOT NULL COMMENT '角色ID',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='用户角色对应关系表';

DROP TABLE IF EXISTS `un_admin_user_data`;
CREATE TABLE `un_admin_user_data` (
`id` bigint(20) NOT NULL,
`user_id` bigint(20) NOT NULL COMMENT '用户id',
`dept_id` bigint(20) NOT NULL COMMENT '部门id',
`data_type` int(11) DEFAULT '1' COMMENT '数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部',
`main_flag` int(11) DEFAULT '0' COMMENT '是否附属部门 默认0 附属部门  1主要部门',
`sub_flag` int(11) DEFAULT '0' COMMENT '是否子部门 默认0不包含  1包含子部门',

`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='用户部门数据权限表';
