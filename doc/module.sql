-- DROP TABLE IF EXISTS `un_module_group`;

DROP TABLE IF EXISTS `un_module`;
CREATE TABLE `un_module`  (
`id` bigint(20) NOT NULL COMMENT '分组id',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分组名称',

`sort_num` int(11) NULL DEFAULT NULL COMMENT '流程的排序',
`parent_id` bigint(20) NULL DEFAULT '0' COMMENT '父级id 0表示顶层的系统',
`depth_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列的深度',

`type_flag` int(11) NULL DEFAULT '0' COMMENT '类型标识 0分组结构 1数据模块',
`hidden_flag` int(11) NULL DEFAULT '1' COMMENT '是否隐藏 0隐藏 1不隐藏',

`root_id` bigint(20) NULL DEFAULT NULL COMMENT '模块的根id',

`create_user_id` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
`update_user_id` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
`create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
`update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
`company_id` bigint(20) NULL DEFAULT NULL COMMENT '企业id',
PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模块表' ROW_FORMAT = Dynamic;

-- ------------------------
-- 数据
-- ------------------------
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (1, 'xxx系统', 1, 0, '0', 0, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (2, '客户系统', 2, 1, '0,1', 0, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (3, '工单系统', 3, 1, '0,1', 0, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (4, '客户', 4, 2, '0,1,2', 1, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (5, '项目', 5, 2, '0,1,2', 1, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);
INSERT INTO `un_module` (`id`, `name`, `sort_num`, `parent_id`, `depth_depth`, `type_flag`, `hidden_flag`, `root_id`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (6, '合同', 6, 2, '0,1,2', 1, 1, 0, 0, 1, '2024-05-28 11:35:16', NULL, 0);


DROP TABLE IF EXISTS `un_module_operate`;
CREATE TABLE `un_module_operate`  (
`id` bigint(20) NOT NULL COMMENT 'id',
`module_id` bigint(20) NOT NULL COMMENT '模块id',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '功能名称',

`flag` int(11) DEFAULT '0' COMMENT '功能基础类型 0系统默认 1业务分配 2自定义',
`create_user_id` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
`update_user_id` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
`create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
`update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
`company_id` bigint(20) NULL DEFAULT NULL COMMENT '企业id',
PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模块操作功能表' ROW_FORMAT = Dynamic;

-- ------------------------
-- 数据
-- ------------------------
INSERT INTO `un_module_operate` (`id`, `module_id`, `name`, `flag`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (1, 0, '列表', 0, 0, 0, '2024-05-28 11:24:51', '2024-05-28 11:24:51', 0);
INSERT INTO `un_module_operate` (`id`, `module_id`, `name`, `flag`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (2, 0, '添加', 0, 0, 0, '2024-05-28 11:24:51', '2024-05-28 11:24:51', 0);
INSERT INTO `un_module_operate` (`id`, `module_id`, `name`, `flag`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (3, 0, '修改', 0, 0, 0, '2024-05-28 11:24:51', '2024-05-28 11:24:51', 0);
INSERT INTO `un_module_operate` (`id`, `module_id`, `name`, `flag`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (4, 0, '删除', 0, 0, 0, '2024-05-28 11:24:51', '2024-05-28 11:24:51', 0);
INSERT INTO `un_module_operate` (`id`, `module_id`, `name`, `flag`, `create_user_id`, `update_user_id`, `create_time`, `update_time`, `company_id`) VALUES (5, 0, '转移', 0, 0, 0, '2024-05-28 11:24:51', '2024-05-28 11:24:51', 0);


DROP TABLE IF EXISTS `un_module_field`;
CREATE TABLE `un_module_field` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`module_id` bigint(20) NOT NULL COMMENT '模块id',
`field_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '字段名称',
`type` int(11) NOT NULL DEFAULT '1' COMMENT '字段类型 1 单行文本 2 多行文本 3 单选 4日期 5 数字 6 小数 7 手机  8 文件 9 多选 10 人员 11 附件 12 部门 13 日期时间 14 邮箱 15客户 16 商机 17 联系人 18 地图 19 产品类型 20 合同 21 回款计划',
`remark` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段说明',
`input_tips` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '输入提示',
`max_length` int(11) DEFAULT NULL COMMENT '最大长度',
`default_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '默认值',

`union_flag` int(1) DEFAULT '0' COMMENT '唯一 0不唯一 1唯一',
`must_flag` int(1) DEFAULT '0' COMMENT '必填 0不必填 1必填',
`hidden_flag` int(1) DEFAULT '0' COMMENT '隐藏 0不隐藏 1隐藏',
`delete_flag` int(1) DEFAULT '0' COMMENT '删除 0不删除 1删除',

`add_flag` int(1) DEFAULT '0' COMMENT '新建 0不新建 1新建',
`index_flag` int(1) DEFAULT '0' COMMENT '列表 0不列表 1列表',
`detail_flag` int(1) DEFAULT '0' COMMENT '详情 0不详情 1详情',

-- `type_env` int(8) unsigned zerofill COMMENT '唯一,必填,隐藏,删除,列表,新建,详情,其他',
`sorting` int(11) DEFAULT '1' COMMENT '排序 从小到大',
`field_type` int(11) NOT NULL DEFAULT '0' COMMENT '字段来源  0.自定义 1.原始固定 ',

`dict_id` bigint(20) DEFAULT NULL COMMENT '字典id',
`option_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'json类型的数据，如果是下来可以配置显示隐藏字段',

`parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父级id',
`depth_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列的深度',

`transfer_model_id` bigint(20) DEFAULT NULL COMMENT '转化modelid',
`transfer_field_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '转化字段名称',

`style_percent` int(11) DEFAULT '50' COMMENT '样式百分比%',
`precisions` int(11) DEFAULT NULL COMMENT '精度，允许的最大小数位',
`max_num_restrict` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '限制的最大数值',
`min_num_restrict` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '限制的最小数值',

`axisx` int(11) DEFAULT '1' COMMENT '存储的坐标位置x轴',
`axisy` int(11) DEFAULT '1' COMMENT '存储的坐标位置y轴',

`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='自定义字段表';
-- ------------------------
-- 数据
-- ------------------------
INSERT INTO `un_module_field` (`id`, `module_id`, `field_name`, `name`, `type`, `remark`, `input_tips`, `max_length`, `default_value`, `union_flag`, `must_flag`, `hidden_flag`, `delete_flag`, `add_flag`, `index_flag`, `detail_flag`, `sorting`, `field_type`, `dictId`, `option_data`, `parent_id`, `depth_depth`, `transfer_model_id`, `transfer_field_name`, `style_percent`, `precisions`, `max_num_restrict`, `min_num_restrict`, `axisx`, `axisy`, `create_time`, `create_user_id`, `update_time`, `update_user_id`) VALUES (1, 4, 'num', '', 1, NULL, NULL, NULL, '', 1, 1, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-05-28 14:09:24', NULL);
INSERT INTO `un_module_field` (`id`, `module_id`, `field_name`, `name`, `type`, `remark`, `input_tips`, `max_length`, `default_value`, `union_flag`, `must_flag`, `hidden_flag`, `delete_flag`, `add_flag`, `index_flag`, `detail_flag`, `sorting`, `field_type`, `dictId`, `option_data`, `parent_id`, `depth_depth`, `transfer_model_id`, `transfer_field_name`, `style_percent`, `precisions`, `max_num_restrict`, `min_num_restrict`, `axisx`, `axisy`, `create_time`, `create_user_id`, `update_time`, `update_user_id`) VALUES (2, 4, 'name', '', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-05-28 14:09:25', NULL);
INSERT INTO `un_module_field` (`id`, `module_id`, `field_name`, `name`, `type`, `remark`, `input_tips`, `max_length`, `default_value`, `union_flag`, `must_flag`, `hidden_flag`, `delete_flag`, `add_flag`, `index_flag`, `detail_flag`, `sorting`, `field_type`, `dictId`, `option_data`, `parent_id`, `depth_depth`, `transfer_model_id`, `transfer_field_name`, `style_percent`, `precisions`, `max_num_restrict`, `min_num_restrict`, `axisx`, `axisy`, `create_time`, `create_user_id`, `update_time`, `update_user_id`) VALUES (3, 4, 'remark', '', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-05-28 14:09:25', NULL);


DROP TABLE IF EXISTS `un_module_field_auth`;
CREATE TABLE `un_module_field_auth` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`field_id` bigint(20) DEFAULT NULL COMMENT '字段ID',
`role_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '角色id',
`auth_type` int(11) NOT NULL DEFAULT '0' COMMENT '授权类型   0不能查看   1只能看 2可以编辑',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='自定义字段关联角色表';


DROP TABLE IF EXISTS `un_module_field_user`;
CREATE TABLE `un_module_field_user` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`field_id` bigint(20) DEFAULT NULL COMMENT '字段ID',
`user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '用户id',
`hidden_flag` int(11) NOT NULL DEFAULT '0' COMMENT '是否隐藏  0不隐藏 1隐藏',
`auth_type` int(11) NOT NULL DEFAULT '0' COMMENT '授权类型   0不能查看   1只能看 2可以编辑',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`update_user_id` bigint(20) DEFAULT NULL COMMENT '修改人ID',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='自定义字段关联用户表';

-- 对接第三方的动态字段配置
DROP TABLE IF EXISTS `un_module_field_api_open`;
CREATE TABLE `un_module_field_api_open` (
`id` bigint(20) NOT NULL AUTO_INCREMENT,
`module_id` bigint(20) NOT NULL COMMENT '模块ID',
`field_id` bigint(20) DEFAULT NULL COMMENT '字段ID',
`parent_field_id` bigint(20) DEFAULT NULL COMMENT '父字段ID 目前适配逻辑表单',
`field_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '字段名称',
`type` int(11) NOT NULL DEFAULT '1' COMMENT '字段类型 1 单行文本 2 多行文本 3 单选 4日期 5 数字 6 小数 7 手机  8 文件 9 多选 10 人员 11 附件 12 部门 13 日期时间 14 邮箱 15客户 16 商机 17 联系人 18 地图 19 产品类型 20 合同 21 回款计划',
`api_type` int(11) NOT NULL COMMENT '第三方类型 0ttc 1erp 2广告',
`api_field_group` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '自定义字段数组的名称',
`api_field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
`api_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`owner_user_id` bigint(20) DEFAULT NULL COMMENT '负责人ID',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`update_time` datetime DEFAULT NULL COMMENT '更新时间',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='第三方接口 字段对照表';


-- 字典表
DROP TABLE IF EXISTS `un_module_dict`;
CREATE TABLE `un_module_dict` (
`id` bigint(20) NOT NULL AUTO_INCREMENT,
`module_id` bigint(20) NOT NULL COMMENT '模块ID',
`group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组名称',
`dict_key` int(11) NOT NULL COMMENT '字典key',
`dict_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典value',

`hidden_flag` int(11) NOT NULL DEFAULT '0' COMMENT '是否隐藏  0不隐藏 1隐藏',
`remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',

`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`owner_user_id` bigint(20) DEFAULT NULL COMMENT '负责人ID',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`update_time` datetime DEFAULT NULL COMMENT '更新时间',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典表';


-- 主数据基础表
DROP TABLE IF EXISTS `un_module_record`;
CREATE TABLE `un_module_record` (
`id` bigint(20) NOT NULL AUTO_INCREMENT,
`module_id` bigint(20) NOT NULL COMMENT '模块ID',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据的名称',
`num` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据的编号',
`remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主数据的备注',
`status_flag` int(11) NOT NULL DEFAULT '1' COMMENT '状态  0草稿 1正常 2逻辑删除 ',
`check_flag` int(11) NOT NULL DEFAULT '1' COMMENT '审核状态 1通过 2拒绝 3审核中 ',
`examine_record_id` bigint(20) NOT NULL COMMENT '进行审批的时候关联的审批实例',
`examine_time` datetime DEFAULT NULL COMMENT '审批通过时间',

`fieldnum1` int(10) NOT NULL COMMENT '主数据默认数字字段',
`fieldnum2` int(10) NOT NULL COMMENT '主数据默认数字字段',
`fieldnum3` int(10) NOT NULL COMMENT '主数据默认数字字段',
`fieldnum4` int(10) NOT NULL COMMENT '主数据默认数字字段',
`fieldnum5` int(10) NOT NULL COMMENT '主数据默认数字字段',

`fielddecimal1` decimal(20,2) NOT NULL COMMENT '主数据默认金额字段',
`fielddecimal2` decimal(20,2) NOT NULL COMMENT '主数据默认金额字段',
`fielddecimal3` decimal(20,2) NOT NULL COMMENT '主数据默认金额字段',
`fielddecimal4` decimal(20,2) NOT NULL COMMENT '主数据默认金额字段',
`fielddecimal5` decimal(20,2) NOT NULL COMMENT '主数据默认金额字段',

`fieldlong1` bigint(20) NOT NULL COMMENT '主数据默认long字段',
`fieldlong2` bigint(20) NOT NULL COMMENT '主数据默认long字段',
`fieldlong3` bigint(20) NOT NULL COMMENT '主数据默认long字段',
`fieldlong4` bigint(20) NOT NULL COMMENT '主数据默认long字段',
`fieldlong5` bigint(20) NOT NULL COMMENT '主数据默认long字段',

`fielddate1` datetime DEFAULT NULL COMMENT '主数据默认日期字段',
`fielddate2` datetime DEFAULT NULL COMMENT '主数据默认日期字段',
`fielddate3` datetime DEFAULT NULL COMMENT '主数据默认日期字段',
`fielddate4` datetime DEFAULT NULL COMMENT '主数据默认日期字段',
`fielddate5` datetime DEFAULT NULL COMMENT '主数据默认日期字段',

`fieldtext0` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext1` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext2` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext3` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext4` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext5` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext6` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext7` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext8` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',
`fieldtext9` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主数据默认文本字段',

`owner_dept_id` bigint(20) NOT NULL COMMENT '所属部门',
`create_user_id` bigint(20) NOT NULL COMMENT '创建人ID',
`owner_user_id` bigint(20) DEFAULT NULL COMMENT '负责人ID',
`create_time` datetime DEFAULT NULL COMMENT '创建时间',
`update_time` datetime DEFAULT NULL COMMENT '更新时间',
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='主数据基础表';

-- 主数据基础表
DROP TABLE IF EXISTS `un_module_record_data`;
CREATE TABLE `un_module_record_data` (
`id` bigint(20) NOT NULL AUTO_INCREMENT,
`module_id` bigint(20) NOT NULL COMMENT '模块ID',
`record_id` bigint(20) NOT NULL COMMENT '具体数据recordID',
`field_id` bigint(20) NOT NULL COMMENT '字段ID',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字段名称',

`value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '值',
`old_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '老值',

`create_time` datetime NOT NULL,
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='主数据自定义字段存值表';

