/*
 Navicat Premium Data Transfer

 Source Server         : 8.142.109.85_3306
 Source Server Type    : MySQL
 Source Server Version : 80024
 Source Host           : 8.142.109.85:3306
 Source Schema         : unexamine2

 Target Server Type    : MySQL
 Target Server Version : 80024
 File Encoding         : 65001

 Date: 09/09/2024 15:34:53
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for un_examine
-- ----------------------------
DROP TABLE IF EXISTS `un_examine`;
CREATE TABLE `un_examine`  (
                               `id` bigint UNSIGNED NOT NULL COMMENT '审批ID',
                               `module_id` bigint NOT NULL COMMENT '模块id 关联模块--可以是业务，也可以是特殊的模块，比如oa 1 合同 2 回款 3发票   101 普通审批 102 请假审批 103 出差审批 104 加班审批 105 差旅报销 106 借款申请',
                               `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
                               `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审批名称',
                               `sort_num` int NULL DEFAULT NULL COMMENT '审批的排序',
                               `group_id` bigint NULL DEFAULT NULL COMMENT '所属分组',
                               `status` int NULL DEFAULT NULL COMMENT '1 正常 2 停用 3 删除 ',
                               `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                               `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人',
                               `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine
-- ----------------------------
INSERT INTO `un_examine` VALUES (1773263471393181697, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1773263984465666050, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1773265563864395778, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1773267571375689730, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1773295904633184258, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1778617966242947073, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine` VALUES (1778618028012462081, 1, NULL, '测试', NULL, NULL, NULL, '测试', NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_examine_group
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_group`;
CREATE TABLE `un_examine_group`  (
                                     `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分组id',
                                     `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分组名称',
                                     `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                     `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                                     `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                     `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                     PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批分组表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_group
-- ----------------------------

-- ----------------------------
-- Table structure for un_examine_node
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_node`;
CREATE TABLE `un_examine_node`  (
                                    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
                                    `examine_id` bigint NULL DEFAULT NULL COMMENT '审批id--关联的审批的高级配置，以及审批的基础信息',
                                    `module_id` bigint NULL DEFAULT NULL COMMENT '模块id 关联模块--可以是业务，也可以是特殊的模块，比如oa',
                                    `node_before_id` bigint NULL DEFAULT NULL COMMENT '审批的类型 0开始节点或者其他节点',
                                    `node_type` int NULL DEFAULT NULL COMMENT '审批的类型 0动态添加 1普通审批 2条件审批 3抄送 4转他人处理 ',
                                    `node_after_id` bigint NULL DEFAULT NULL COMMENT '审批的类型 1结束节点或者其他节点',
                                    `node_sort` int NULL DEFAULT 0 COMMENT '节点排序 默认0',
                                    `node_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '节点深度,存储节点的父级，从0开始逗号分隔',
                                    `examine_type` int NULL DEFAULT NULL COMMENT '审批人类型 0 固定人员 1 固定人员上级 2角色 3发起人自选',
                                    `examine_flag` int NULL DEFAULT 0 COMMENT '多人情况时候审批的人员审批方式  0默认一个爱一个默认顺序  1一个爱一个无序 2只要有一个',
                                    `examine_end_user_id` bigint NULL DEFAULT NULL COMMENT '上级审批截至人员 配置这个如果没有上级转该人审批 有上级这个配置失效',
                                    `condition_module_field_search` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '条件',
                                    `copy_emails` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '抄送的 email',
                                    `transfer_flag` int NULL DEFAULT 0 COMMENT '转他人处理flag 默认0 1表示这个是转他人的审批场景 2抄送的邮箱',
                                    `transfer_user_id` bigint NULL DEFAULT NULL COMMENT '类型是转他人对应的主键',
                                    `transfer_status` int NULL DEFAULT NULL COMMENT '类型是转他人 审批状态',
                                    `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                    `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人',
                                    `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1778618028238442498 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批节点表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_node
-- ----------------------------
INSERT INTO `un_examine_node` VALUES (1773267594489499648, NULL, 1, 0, 1, NULL, 0, NULL, 0, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773267594535636992, NULL, 1, 1773267594489499648, 3, NULL, 0, NULL, 0, 0, NULL, NULL, 'eee@sina.com', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773267594569191424, NULL, 1, 1773267594535636992, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':1,\'name\':\'测试\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773267594598551552, NULL, NULL, 1773267594569191424, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773267594636300288, NULL, 1, 1773267594535636992, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':2,\'name\':\'测试2\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773267594665660416, NULL, NULL, 1773267594636300288, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904879677440, 1773295904633184258, 1, 0, 1, NULL, 0, NULL, 0, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904883871744, 1773295904633184258, 1, 1773295904879677440, 3, NULL, 0, NULL, 0, 0, NULL, NULL, 'eee@sina.com', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904883871745, 1773295904633184258, 1, 1773295904883871744, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':1,\'name\':\'测试\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904883871746, 1773295904633184258, NULL, 1773295904883871745, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904888066048, 1773295904633184258, 1, 1773295904883871744, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':2,\'name\':\'测试2\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1773295904888066049, 1773295904633184258, NULL, 1773295904888066048, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966661865472, 1778617966242947073, 1, 0, 1, NULL, 0, NULL, 0, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966666059777, 1778617966242947073, 1, 1778617966661865472, 3, NULL, 0, NULL, 0, 0, NULL, NULL, 'eee@sina.com', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966670254080, 1778617966242947073, 1, 1778617966666059777, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':1,\'name\':\'测试\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966670254081, 1778617966242947073, NULL, 1778617966670254080, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966670254082, 1778617966242947073, 1, 1778617966666059777, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':2,\'name\':\'测试2\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778617966674448384, 1778617966242947073, NULL, 1778617966670254082, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028225859584, 1778618028012462081, 1, 0, 1, NULL, 0, NULL, 0, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028230053888, 1778618028012462081, 1, 1778618028225859584, 3, NULL, 0, NULL, 0, 0, NULL, NULL, 'eee@sina.com', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028230053889, 1778618028012462081, 1, 1778618028230053888, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':1,\'name\':\'测试\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028234248192, 1778618028012462081, NULL, 1778618028230053889, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028238442496, 1778618028012462081, 1, 1778618028230053888, 2, NULL, 0, NULL, 0, 0, NULL, '{\'id\':2,\'name\':\'测试2\'}', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node` VALUES (1778618028238442497, 1778618028012462081, NULL, 1778618028238442496, NULL, NULL, 0, NULL, NULL, 0, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_examine_node_user
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_node_user`;
CREATE TABLE `un_examine_node_user`  (
                                         `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
                                         `examine_id` bigint NOT NULL COMMENT '审批id',
                                         `node_id` bigint NOT NULL COMMENT '节点id',
                                         `apply_type` int NOT NULL COMMENT '适用类型 0用户 1部门 2 角色 4邮箱',
                                         `user_id` bigint NULL DEFAULT NULL COMMENT '适用用户id',
                                         `dept_id` bigint NULL DEFAULT NULL COMMENT '适用部门id',
                                         `role_id` bigint NULL DEFAULT NULL COMMENT '适用角色id',
                                         `eamil` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
                                         `sorting` int NULL DEFAULT NULL COMMENT '排序',
                                         `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                         `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                         `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1778618028225859586 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批适用用户部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_node_user
-- ----------------------------
INSERT INTO `un_examine_node_user` VALUES (1773295904879677441, 1773295904633184258, 1773295904879677440, 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node_user` VALUES (1778617966666059776, 1778617966242947073, 1778617966661865472, 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_node_user` VALUES (1778618028225859585, 1778618028012462081, 1778618028225859584, 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_examine_record
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_record`;
CREATE TABLE `un_examine_record`  (
                                      `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
                                      `examine_id` bigint NOT NULL COMMENT '审核ID',
                                      `module_id` bigint NOT NULL COMMENT '模块id 关联模块--可以是业务，也可以是特殊的模块，比如oa',
                                      `relation_id` bigint NULL DEFAULT NULL COMMENT '关联业务主键ID',
                                      `status` int NULL DEFAULT NULL COMMENT '记录状态 0 正常 1 终止 2 暂停  3 作废',
                                      `examine_status` int NULL DEFAULT NULL COMMENT '审核状态 0 未审核 1 审核通过 2 审核拒绝 3 审核中 4 已撤回 6创建',
                                      `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                      `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                      `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                      `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                      `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人',
                                      `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1778618088489619457 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审核记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_record
-- ----------------------------
INSERT INTO `un_examine_record` VALUES (1773295904633184258, 1773295904633184258, 1, 1, NULL, NULL, '测试', '2024-03-30 15:40:37', NULL, '2024-03-30 15:40:37', NULL, 1);
INSERT INTO `un_examine_record` VALUES (1773981795466952705, 1773295904633184258, 1, 1, NULL, NULL, '测试', '2024-03-30 15:53:14', NULL, '2024-03-30 15:53:14', NULL, 1);
INSERT INTO `un_examine_record` VALUES (1774002433589121024, 1773295904633184258, 1, 1, NULL, NULL, '测试', '2024-03-30 17:15:14', NULL, '2024-03-30 17:15:14', NULL, 1);
INSERT INTO `un_examine_record` VALUES (1778618088489619456, 1773295904633184258, 1, 1, NULL, NULL, '测试', '2024-04-12 10:56:12', NULL, '2024-04-12 10:56:12', NULL, 1);

-- ----------------------------
-- Table structure for un_examine_record_node
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_record_node`;
CREATE TABLE `un_examine_record_node`  (
                                           `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                           `record_id` bigint NULL DEFAULT NULL COMMENT '关联的record_id',
                                           `examine_id` bigint NULL DEFAULT NULL COMMENT '审批id--关联的审批的高级配置，以及审批的基础信息',
                                           `module_id` bigint NULL DEFAULT NULL COMMENT '模块id 关联模块--可以是业务，也可以是特殊的模块，比如oa',
                                           `node_id` bigint NULL DEFAULT NULL COMMENT '多人的情况下的父级id',
                                           `node_before_id` bigint NULL DEFAULT NULL COMMENT '审批的类型 0开始节点或者其他节点',
                                           `node_type` int NULL DEFAULT NULL COMMENT '审批的类型 0动态添加 1普通审批 2条件审批 3抄送 4转他人处理 ',
                                           `node_sort` int NULL DEFAULT 0 COMMENT '节点排序 默认0',
                                           `node_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '节点深度,存储节点的父级，从0开始逗号分隔',
                                           `examine_type` int NULL DEFAULT NULL COMMENT '审批人类型 0 固定人员 1 固定人员上级 2角色 3发起人自选',
                                           `examine_flag` int NULL DEFAULT 0 COMMENT '多人情况时候审批的人员审批方式  0默认一个爱一个默认顺序  1一个爱一个无序 2只要有一个',
                                           `examine_end_user_id` bigint NULL DEFAULT NULL COMMENT '上级审批截至人员 配置这个如果没有上级转该人审批 有上级这个配置失效',
                                           `condition_module_field_search` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '条件',
                                           `copy_emails` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '抄送的 email',
                                           `status` int NULL DEFAULT NULL COMMENT '审批状态',
                                           `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                           `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                           `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                           `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                           `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人',
                                           `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                           PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1778618159784398849 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批节点表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_record_node
-- ----------------------------
INSERT INTO `un_examine_record_node` VALUES (1778618159763427328, 1778618088489619456, 1773295904633184258, 1, NULL, 0, 1, 0, NULL, 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_record_node` VALUES (1778618159776010240, 1778618088489619456, 1773295904633184258, 1, NULL, 1773295904883871744, 2, 0, NULL, 0, 0, NULL, '{\'id\':1,\'name\':\'测试\'}', NULL, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_record_node` VALUES (1778618159784398848, 1778618088489619456, 1773295904633184258, 1, NULL, 1773295904883871744, 2, 0, NULL, 0, 0, NULL, '{\'id\':2,\'name\':\'测试2\'}', NULL, 1, NULL, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_examine_record_node_user
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_record_node_user`;
CREATE TABLE `un_examine_record_node_user`  (
                                                `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
                                                `parent_id` bigint NULL DEFAULT NULL COMMENT '父级id从0开始',
                                                `depts` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '深度从0开始',
                                                `record_id` bigint NOT NULL COMMENT '审批id',
                                                `record_node_id` bigint NOT NULL COMMENT 'recordnode节点id',
                                                `examine_id` bigint NOT NULL COMMENT '审批id',
                                                `node_id` bigint NOT NULL COMMENT '节点id',
                                                `apply_type` int NOT NULL COMMENT '适用类型 0用户 1部门 2 角色 4邮箱',
                                                `user_id` bigint NULL DEFAULT NULL COMMENT '适用用户id',
                                                `dept_id` bigint NULL DEFAULT NULL COMMENT '适用部门id',
                                                `role_id` bigint NULL DEFAULT NULL COMMENT '适用角色id',
                                                `eamil` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
                                                `status` int NULL DEFAULT NULL COMMENT '审批状态',
                                                `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审批状态备注',
                                                `sorting` int NULL DEFAULT NULL COMMENT '排序',
                                                `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                                `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                                `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批适用用户部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_record_node_user
-- ----------------------------

-- ----------------------------
-- Table structure for un_examine_setting
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_setting`;
CREATE TABLE `un_examine_setting`  (
                                       `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
                                       `examine_id` bigint NOT NULL COMMENT '审批id',
                                       `rule_type` int NULL DEFAULT NULL COMMENT '0撤回规则 1通过规则 ',
                                       `recheck_type` int NULL DEFAULT NULL COMMENT '撤回之后重新审核操作 1 从第一层开始 2 从拒绝的层级开始',
                                       `pass_type` int NULL DEFAULT NULL COMMENT '通过规则类型 1 超时自动通过 2 转他人处理',
                                       `pass_rule` int NULL DEFAULT NULL COMMENT '通过规则类型 1 该审批人一个同意该人全部同意 2 该相同审批人同意 3该审批人依次审批',
                                       `limit_time_type` int NULL DEFAULT NULL COMMENT '现时配置是否开启  0默认不开启  1开启 设置超时通过必须设置现时',
                                       `limit_time_num` int NULL DEFAULT NULL COMMENT '限时时间',
                                       `limit_time_unit` int NULL DEFAULT NULL COMMENT '限时时间单位',
                                       `apply_type` int NOT NULL DEFAULT 0 COMMENT '适用类型 0默认全公司 1用户 2部门',
                                       `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                       `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                       `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                       PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1773295904830316549 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批高级设置及异常处理规则' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_setting
-- ----------------------------
INSERT INTO `un_examine_setting` VALUES (1773263471657422850, 1773263471393181697, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773264039847256066, 1773263984465666050, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773265564149608450, 1773265563864395778, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773267571816091650, 1773267571375689730, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773295904830316546, 1773295904633184258, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773295904830316547, 1778617966242947073, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting` VALUES (1773295904830316548, 1778618028012462081, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_examine_setting_user
-- ----------------------------
DROP TABLE IF EXISTS `un_examine_setting_user`;
CREATE TABLE `un_examine_setting_user`  (
                                            `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
                                            `examine_id` bigint NOT NULL COMMENT '审批id',
                                            `apply_type` int NOT NULL COMMENT '适用类型 0用户 1部门',
                                            `user_id` bigint NULL DEFAULT NULL COMMENT '适用用户id',
                                            `dept_id` bigint NULL DEFAULT NULL COMMENT '适用部门id',
                                            `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                                            `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                                            `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                            `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                            `company_id` bigint NULL DEFAULT NULL COMMENT '企业id',
                                            PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1773295905090363397 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批适用用户部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_examine_setting_user
-- ----------------------------
INSERT INTO `un_examine_setting_user` VALUES (1773263471854555138, 1773263471393181697, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773264089608478722, 1773263984465666050, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773265564350935041, 1773265563864395778, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773267572080332802, 1773267571375689730, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773295905090363394, 1773295904633184258, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773295905090363395, 1778617966242947073, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);
INSERT INTO `un_examine_setting_user` VALUES (1773295905090363396, 1778618028012462081, 0, 1, NULL, NULL, NULL, NULL, NULL, 1);

-- ----------------------------
-- Table structure for un_module
-- ----------------------------
DROP TABLE IF EXISTS `un_module`;
CREATE TABLE `un_module`  (
                              `id` bigint NOT NULL COMMENT '分组id',
                              `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分组名称',
                              `sort_num` int NULL DEFAULT NULL COMMENT '流程的排序',
                              `parent_id` bigint NULL DEFAULT 0 COMMENT '父级id 0表示顶层的系统',
                              `depth_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列的深度',
                              `type_flag` int NULL DEFAULT 0 COMMENT '类型标识 0分组结构 1数据模块',
                              `hidden_flag` int NULL DEFAULT 1 COMMENT '是否隐藏 0隐藏 1不隐藏',
                              `root_id` bigint NULL DEFAULT NULL COMMENT '模块的根id',
                              `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
                              `color` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '颜色',
                              `create_user_id` bigint NULL DEFAULT NULL COMMENT '创建人',
                              `update_user_id` bigint NULL DEFAULT NULL COMMENT '更新人',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                              `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                              PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模块表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module
-- ----------------------------
INSERT INTO `un_module` VALUES (1, '后台系统', 1, 0, '0', 0, 1, 1, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (2, '客户系统', 2, 0, '0,1', 0, 1, 2, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (3, '笔记系统', 3, 0, '0,1', 0, 1, 3, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (4, '客户', 4, 2, '0,1,2', 1, 1, 2, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (5, '项目', 5, 2, '0,1,2', 1, 1, 2, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (6, '合同', 6, 2, '0,1,2', 1, 1, 2, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (7, 'car', 7, 3, '0,1,3', 1, 1, 3, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (8, 'node', 8, 3, '0,1,3', 1, 1, 3, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);
INSERT INTO `un_module` VALUES (9, '车险', 9, 3, '0,1,3', 1, 1, 3, NULL, NULL, 0, 1, '2024-05-28 11:35:16', NULL, 1);

-- ----------------------------
-- Table structure for un_module_data
-- ----------------------------
DROP TABLE IF EXISTS `un_module_data`;
CREATE TABLE `un_module_data`  (
                                   `id` bigint NOT NULL,
                                   `user_id` bigint NOT NULL COMMENT '用户id',
                                   `data_type` int NULL DEFAULT 1 COMMENT '数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                   `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户模块的数据权限' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_data
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_dept
-- ----------------------------
DROP TABLE IF EXISTS `un_module_dept`;
CREATE TABLE `un_module_dept`  (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `module_id` bigint NULL DEFAULT NULL COMMENT '模块Id',
                                   `parent_id` bigint NULL DEFAULT 0 COMMENT '父级ID 顶级部门为0',
                                   `deepth` longtext CHARACTER SET utf8 COLLATE utf8_general_ci NULL COMMENT 'parent_id 构建的深度',
                                   `name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '部门名称',
                                   `num` int NULL DEFAULT NULL COMMENT '排序 越大越靠后',
                                   `remark` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '部门备注',
                                   `owner_user_id` bigint NULL DEFAULT NULL COMMENT '部门负责人',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                   `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '公司id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_dept
-- ----------------------------
INSERT INTO `un_module_dept` VALUES (1, 2, 0, '0,', '总部', NULL, '', 0, NULL, 0, '2024-08-28 11:25:30', NULL, 1);
INSERT INTO `un_module_dept` VALUES (2, 3, 0, '0,', '总部', NULL, '', 0, NULL, 0, '2024-08-28 11:25:30', NULL, 1);

-- ----------------------------
-- Table structure for un_module_dict
-- ----------------------------
DROP TABLE IF EXISTS `un_module_dict`;
CREATE TABLE `un_module_dict`  (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `module_id` bigint NOT NULL COMMENT '模块ID',
                                   `group_id` bigint NOT NULL COMMENT '字典组ID',
                                   `parent_id` bigint NULL DEFAULT 0 COMMENT '父级id 0表示顶层的系统',
                                   `depth_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列的深度',
                                   `dict_id` bigint NOT NULL COMMENT '具体数据dictID',
                                   `dict_key` bigint NOT NULL COMMENT '具体数据recordID',
                                   `dict_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
                                   `sort_num` int NULL DEFAULT NULL COMMENT '排序',
                                   `status` int NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
                                   `create_time` datetime NOT NULL,
                                   `update_time` datetime NOT NULL COMMENT '更新时间',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据字典组具体数据表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_dict
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_dict_base
-- ----------------------------
DROP TABLE IF EXISTS `un_module_dict_base`;
CREATE TABLE `un_module_dict_base`  (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `module_id` bigint NOT NULL COMMENT '模块ID',
                                        `dict_key` bigint NOT NULL COMMENT '具体数据recordID',
                                        `dict_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
                                        `status` int NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
                                        `use_flag` int NOT NULL DEFAULT 1 COMMENT '修改后是否应用所有 0不应用 1应用',
                                        `create_time` datetime NOT NULL,
                                        `update_time` datetime NOT NULL COMMENT '更新时间',
                                        `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据字段基础表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_dict_base
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_dict_group
-- ----------------------------
DROP TABLE IF EXISTS `un_module_dict_group`;
CREATE TABLE `un_module_dict_group`  (
                                         `id` bigint NOT NULL AUTO_INCREMENT,
                                         `module_id` bigint NOT NULL COMMENT '模块ID',
                                         `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
                                         `status` int NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
                                         `create_time` datetime NOT NULL,
                                         `update_time` datetime NOT NULL COMMENT '更新时间',
                                         `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据字典组表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_dict_group
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_field
-- ----------------------------
DROP TABLE IF EXISTS `un_module_field`;
CREATE TABLE `un_module_field`  (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `module_id` bigint NOT NULL COMMENT '模块id',
                                    `field_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
                                    `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '字段名称',
                                    `type` int NOT NULL DEFAULT 1 COMMENT '字段类型 1 单行文本 2 多行文本 3 单选 4日期 5 数字 6 小数 7 手机  8 文件 9 多选 10 人员 11 附件 12 部门 13 日期时间 14 邮箱 15客户 16 商机 17 联系人 18 地图 19 产品类型 20 合同 21 回款计划',
                                    `remark` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字段说明',
                                    `input_tips` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '输入提示',
                                    `max_length` int NULL DEFAULT NULL COMMENT '最大长度',
                                    `default_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '默认值',
                                    `union_flag` int NULL DEFAULT 0 COMMENT '唯一 0不唯一 1唯一',
                                    `must_flag` int NULL DEFAULT 0 COMMENT '必填 0不必填 1必填',
                                    `hidden_flag` int NULL DEFAULT 0 COMMENT '隐藏 0不隐藏 1隐藏',
                                    `delete_flag` int NULL DEFAULT 0 COMMENT '删除 0不删除 1删除',
                                    `add_flag` int NULL DEFAULT 0 COMMENT '新建 0不新建 1新建',
                                    `index_flag` int NULL DEFAULT 0 COMMENT '列表 0不列表 1列表',
                                    `detail_flag` int NULL DEFAULT 0 COMMENT '详情 0不详情 1详情',
                                    `sorting` int NULL DEFAULT 1 COMMENT '排序 从小到大',
                                    `field_type` int NOT NULL DEFAULT 0 COMMENT '字段来源  0.自定义 1.原始固定 ',
                                    `dict_id` bigint NULL DEFAULT NULL COMMENT '字典id',
                                    `option_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'json类型的数据，如果是下来可以配置显示隐藏字段',
                                    `parent_id` bigint NULL DEFAULT NULL COMMENT '父级id',
                                    `depth_depth` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '列的深度',
                                    `transfer_model_id` bigint NULL DEFAULT NULL COMMENT '转化modelid',
                                    `transfer_field_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '转化字段名称',
                                    `style_percent` int NULL DEFAULT 50 COMMENT '样式百分比%',
                                    `precisions` int NULL DEFAULT NULL COMMENT '精度，允许的最大小数位',
                                    `max_num_restrict` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '限制的最大数值',
                                    `min_num_restrict` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '限制的最小数值',
                                    `axisx` int NULL DEFAULT 1 COMMENT '存储的坐标位置x轴',
                                    `axisy` int NULL DEFAULT 1 COMMENT '存储的坐标位置y轴',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `create_user_id` bigint NOT NULL DEFAULT 0 COMMENT '创建人ID',
                                    `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                    `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 58 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '自定义字段表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_field
-- ----------------------------
INSERT INTO `un_module_field` VALUES (1, 0, 'fieldnum1', ' ', 3, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (2, 0, 'fieldnum2', ' ', 3, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 2, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (3, 0, 'fieldnum3', ' ', 3, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 2, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (4, 0, 'fieldnum4', ' ', 3, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (5, 0, 'fieldnum5', ' ', 3, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (6, 0, 'fielddecimal1', ' ', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (7, 0, 'fielddecimal2', ' ', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (8, 0, 'fielddecimal3', ' ', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (9, 0, 'fielddecimal4', ' ', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (10, 0, 'fielddecimal5', ' ', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (11, 0, 'fieldlong1', ' ', 19, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (12, 0, 'fieldlong2', ' ', 19, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (13, 0, 'fieldlong3', ' ', 19, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (14, 0, 'fieldlong4', ' ', 19, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, '{\"1\":\"行驶\",\"2\":\"加油\",\"3\":\"充值\"}', NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (15, 0, 'fieldlong5', ' ', 19, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:01', NULL, 1);
INSERT INTO `un_module_field` VALUES (16, 0, 'fielddate1', ' ', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (17, 0, 'fielddate2', ' ', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, '{\"1\":\"中化加油\",\"2\":\"联盈石化\",\"3\":\"中国石油\",\"4\":\"中国石化\",\"5\":\"其他\"}', NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (18, 0, 'fielddate3', ' ', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (19, 0, 'fielddate4', '', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (20, 0, 'fielddate5', '', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (21, 0, 'fieldtext0', '', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (22, 0, 'fieldtext1', '', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (23, 0, 'fieldtext2', ' ', 1, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (24, 0, 'fieldtext3', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 2, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (25, 0, 'fieldtext4', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 2, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (26, 0, 'fieldtext5', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (27, 0, 'fieldtext6', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (28, 0, 'fieldtext7', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (29, 0, 'fieldtext8', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (30, 0, 'fieldtext9', ' ', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (31, 1, 'id', '主键', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (32, 1, 'name', '名称', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:02', NULL, 1);
INSERT INTO `un_module_field` VALUES (33, 1, 'remark', '备注', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (34, 4, 'id', '主键', 1, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (35, 4, 'num', '编号', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 2, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (36, 4, 'name', '名称', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 2, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (37, 4, 'remark', '备注', 2, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (38, 4, 'check_flag', '审批状态', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (39, 4, 'examine_record_id', '审批关联', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (40, 4, 'examine_time', '审批通过时间', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (41, 4, 'create_user_id', '创建人', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (42, 4, 'owner_user_id', '负责人', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (43, 4, 'create_time', '创建时间', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (44, 4, 'update_time', '更新时间', 7, NULL, NULL, NULL, '', 0, 0, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (45, 4, 'fielddecimal1', '金额', 4, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (46, 7, 'id', '主键', 1, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (47, 7, 'record_flag', '类型', 8, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, '{\"1\":\"行驶\",\"2\":\"加油\",\"3\":\"充值\"}', NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (48, 7, 'fielddecimal1', '公里/加油', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:03', NULL, 1);
INSERT INTO `un_module_field` VALUES (49, 7, 'fieldnum1', '剩油/充值', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (50, 7, 'fieldnum2', '加油站', 8, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, '{\"1\":\"中化加油\",\"2\":\"联盈石化\",\"3\":\"中国石油\",\"4\":\"中国石化\",\"5\":\"其他\"}', NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (51, 9, 'id', '主键', 1, NULL, NULL, NULL, '', 1, 1, 0, 0, 0, 0, 0, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (52, 9, 'record_flag', '类型', 8, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, '{\"1\":\"孟津人保\",\"2\":\"洛阳人保\",\"3\":\"太平洋\",\"4\":\"平安\",\"5\":\"阳光\",\"6\":\"大地\"}', NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (53, 9, 'fielddecimal1', '车损', 6, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (54, 9, 'fielddecimal2', '三责', 6, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (55, 9, 'remark', '其他', 1, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (56, 9, 'fielddecimal3', '总计', 6, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);
INSERT INTO `un_module_field` VALUES (57, 9, 'fielddecimal4', '优惠后', 6, NULL, NULL, NULL, '', 0, 0, 0, 0, 1, 1, 1, 1, 0, NULL, NULL, NULL, NULL, NULL, NULL, 50, NULL, NULL, NULL, 1, 1, NULL, 0, '2024-09-09 15:33:04', NULL, 1);

-- ----------------------------
-- Table structure for un_module_field_api_open
-- ----------------------------
DROP TABLE IF EXISTS `un_module_field_api_open`;
CREATE TABLE `un_module_field_api_open`  (
                                             `id` bigint NOT NULL AUTO_INCREMENT,
                                             `module_id` bigint NOT NULL COMMENT '模块ID',
                                             `field_id` bigint NULL DEFAULT NULL COMMENT '字段ID',
                                             `parent_field_id` bigint NULL DEFAULT NULL COMMENT '父字段ID 目前适配逻辑表单',
                                             `field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
                                             `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '字段名称',
                                             `type` int NOT NULL DEFAULT 1 COMMENT '字段类型 1 单行文本 2 多行文本 3 单选 4日期 5 数字 6 小数 7 手机  8 文件 9 多选 10 人员 11 附件 12 部门 13 日期时间 14 邮箱 15客户 16 商机 17 联系人 18 地图 19 产品类型 20 合同 21 回款计划',
                                             `api_type` int NOT NULL COMMENT '第三方类型 0ttc 1erp 2广告',
                                             `api_field_group` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '自定义字段数组的名称',
                                             `api_field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '自定义字段英文标识',
                                             `api_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                                             `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                             `owner_user_id` bigint NULL DEFAULT NULL COMMENT '负责人ID',
                                             `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                             `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                             `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '第三方接口 字段对照表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_field_api_open
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_field_auth
-- ----------------------------
DROP TABLE IF EXISTS `un_module_field_auth`;
CREATE TABLE `un_module_field_auth`  (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                         `field_id` bigint NULL DEFAULT NULL COMMENT '字段ID',
                                         `role_id` bigint NOT NULL DEFAULT 0 COMMENT '角色id',
                                         `auth_type` int NOT NULL DEFAULT 0 COMMENT '授权类型   0不能查看   1只能看 2可以编辑',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                         `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                         `company_id` bigint NULL DEFAULT NULL COMMENT '公司id',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '自定义字段关联角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_field_auth
-- ----------------------------

-- ----------------------------
-- Table structure for un_module_field_user
-- ----------------------------
DROP TABLE IF EXISTS `un_module_field_user`;
CREATE TABLE `un_module_field_user`  (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                         `module_id` bigint NOT NULL COMMENT '模块ID',
                                         `field_id` bigint NULL DEFAULT NULL COMMENT '字段ID',
                                         `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户id',
                                         `sort_flag` int NOT NULL DEFAULT 0 COMMENT '排序',
                                         `hidden_flag` int NOT NULL DEFAULT 0 COMMENT '是否隐藏  0不隐藏 1隐藏',
                                         `auth_type` int NOT NULL DEFAULT 0 COMMENT '授权类型   0不能查看   1只能看 2可以编辑',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                         `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                         `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1808398455014559745 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '自定义字段关联用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_field_user
-- ----------------------------
INSERT INTO `un_module_field_user` VALUES (1803023030973239296, 4, 35, 1, 0, 0, 0, '2024-06-18 19:12:43', 1, '2024-09-09 15:32:15', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1803023030977433600, 4, 36, 1, 0, 0, 0, '2024-06-18 19:12:43', 1, '2024-09-09 15:32:15', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1808398454997782528, 7, 47, 1, 0, 0, 0, '2024-07-03 15:12:45', 1, '2024-09-09 15:32:15', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1808398455001976832, 7, 48, 1, 0, 0, 0, '2024-07-03 15:12:45', 1, '2024-09-09 15:32:15', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1808398455010365440, 7, 49, 1, 0, 0, 0, '2024-07-03 15:12:45', 1, '2024-09-09 15:32:15', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1808398455014559744, 7, 50, 1, 0, 0, 0, '2024-07-03 15:12:45', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802607087616, 9, 52, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802615476224, 9, 53, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802628059136, 9, 54, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802636447744, 9, 55, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802653224960, 9, 56, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);
INSERT INTO `un_module_field_user` VALUES (1833037802665807872, 9, 57, 1, 0, 0, 0, '2024-09-09 15:00:43', 1, '2024-09-09 15:32:16', NULL, 1);

-- ----------------------------
-- Table structure for un_module_menu
-- ----------------------------
DROP TABLE IF EXISTS `un_module_menu`;
CREATE TABLE `un_module_menu`  (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
                                   `menu_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '菜单名称',
                                   `module_id` bigint NULL DEFAULT NULL COMMENT '所属模块',
                                   `menu_type` int NULL DEFAULT NULL COMMENT '菜单类型 0 列表1 详情2 添加3 编辑4 删除5 导入6 导出7 打印  10 修改状态 11 转化数据',
                                   `menu_option` longtext CHARACTER SET utf8 COLLATE utf8_general_ci NULL COMMENT '修改状态/转化数据 对应的规则的json',
                                   `sorts` int UNSIGNED NULL DEFAULT 0 COMMENT '排序（同级有效）',
                                   `status` int NULL DEFAULT 1 COMMENT '状态  0 禁用 1 启用',
                                   `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '菜单说明',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模块菜单功能权限配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_menu
-- ----------------------------
INSERT INTO `un_module_menu` VALUES (1, '列表', 4, 0, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (2, '详情', 4, 1, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (3, '添加', 4, 2, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (4, '编辑', 4, 3, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (5, '删除', 4, 4, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (6, '导入', 4, 5, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (7, '导出', 4, 6, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (8, '打印', 4, 7, NULL, 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (9, '修改某个字段/状态', 4, 10, '{\"old\":\"status\",\"new\":\"status\"}', 0, 1, NULL, 1);
INSERT INTO `un_module_menu` VALUES (10, '转化，模块下的数据转成另一个模块的数据', 4, 11, '[{\"old\":\"status\",\"new\":\"status\"},{\"old\":\"status\",\"new\":\"status\"},{\"old\":\"status\",\"new\":\"status\"}]', 0, 1, NULL, 1);

-- ----------------------------
-- Table structure for un_module_record
-- ----------------------------
DROP TABLE IF EXISTS `un_module_record`;
CREATE TABLE `un_module_record`  (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `module_id` bigint NOT NULL COMMENT '模块ID',
                                     `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据的名称',
                                     `num` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据的编号',
                                     `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据的备注',
                                     `record_flag` int NULL DEFAULT NULL COMMENT '主数据的动态类型',
                                     `status_flag` int NOT NULL DEFAULT 1 COMMENT '状态  0草稿 1正常 2逻辑删除 ',
                                     `check_flag` int NOT NULL DEFAULT 1 COMMENT '审核状态 1通过 2拒绝 3审核中 ',
                                     `examine_record_id` bigint NULL DEFAULT NULL COMMENT '进行审批的时候关联的审批实例',
                                     `examine_time` datetime NULL DEFAULT NULL COMMENT '审批通过时间',
                                     `fieldnum1` int NULL DEFAULT NULL COMMENT '主数据默认数字字段',
                                     `fieldnum2` int NULL DEFAULT NULL COMMENT '主数据默认数字字段',
                                     `fieldnum3` int NULL DEFAULT NULL COMMENT '主数据默认数字字段',
                                     `fieldnum4` int NULL DEFAULT NULL COMMENT '主数据默认数字字段',
                                     `fieldnum5` int NULL DEFAULT NULL COMMENT '主数据默认数字字段',
                                     `fielddecimal1` decimal(20, 2) NULL DEFAULT NULL COMMENT '主数据默认金额字段',
                                     `fielddecimal2` decimal(20, 2) NULL DEFAULT NULL COMMENT '主数据默认金额字段',
                                     `fielddecimal3` decimal(20, 2) NULL DEFAULT NULL COMMENT '主数据默认金额字段',
                                     `fielddecimal4` decimal(20, 2) NULL DEFAULT NULL COMMENT '主数据默认金额字段',
                                     `fielddecimal5` decimal(20, 2) NULL DEFAULT NULL COMMENT '主数据默认金额字段',
                                     `fieldlong1` bigint NULL DEFAULT NULL COMMENT '主数据默认long字段',
                                     `fieldlong2` bigint NULL DEFAULT NULL COMMENT '主数据默认long字段',
                                     `fieldlong3` bigint NULL DEFAULT NULL COMMENT '主数据默认long字段',
                                     `fieldlong4` bigint NULL DEFAULT NULL COMMENT '主数据默认long字段',
                                     `fieldlong5` bigint NULL DEFAULT NULL COMMENT '主数据默认long字段',
                                     `fielddate1` datetime NULL DEFAULT NULL COMMENT '主数据默认日期字段',
                                     `fielddate2` datetime NULL DEFAULT NULL COMMENT '主数据默认日期字段',
                                     `fielddate3` datetime NULL DEFAULT NULL COMMENT '主数据默认日期字段',
                                     `fielddate4` datetime NULL DEFAULT NULL COMMENT '主数据默认日期字段',
                                     `fielddate5` datetime NULL DEFAULT NULL COMMENT '主数据默认日期字段',
                                     `fieldtext0` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext1` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext2` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext3` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext4` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext5` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext6` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext7` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext8` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `fieldtext9` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主数据默认文本字段',
                                     `old_id` bigint NULL DEFAULT NULL COMMENT '转化的时候使用的id',
                                     `old_module_id` bigint NULL DEFAULT NULL COMMENT '转化的时候使用的模块ID',
                                     `owner_dept_id` bigint NULL DEFAULT NULL COMMENT '所属部门',
                                     `create_user_id` bigint NOT NULL DEFAULT 1 COMMENT '创建人ID',
                                     `owner_user_id` bigint NULL DEFAULT NULL COMMENT '负责人ID',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                     PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1825802591360126977 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '主数据基础表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_record
-- ----------------------------
INSERT INTO `un_module_record` VALUES (1798552009868185601, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 0, 5, NULL, NULL, NULL, 49.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185602, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 5, NULL, NULL, NULL, 25.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185603, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 300, 5, NULL, NULL, NULL, 37.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185604, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, 5, NULL, NULL, NULL, 369.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185605, 7, NULL, NULL, NULL, 3, 1, 1, NULL, NULL, 1000, 5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185606, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 47, 5, NULL, NULL, NULL, 628.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185607, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 46, 5, NULL, NULL, NULL, 642.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185608, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 45, 5, NULL, NULL, NULL, 653.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185609, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 44, 5, NULL, NULL, NULL, 670.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185610, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 42, 5, NULL, NULL, NULL, 692.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185611, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, 5, NULL, NULL, NULL, 715.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185612, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 35, 5, NULL, NULL, NULL, 752.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:02', '2024-09-09 15:32:02', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185613, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 33, 5, NULL, NULL, NULL, 786.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185614, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 32, 5, NULL, NULL, NULL, 802.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185615, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 29, 5, NULL, NULL, NULL, 832.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185616, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, 5, NULL, NULL, NULL, 864.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185617, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 24, 5, NULL, NULL, NULL, 897.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185618, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 23, 5, NULL, NULL, NULL, 909.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185619, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 22, 5, NULL, NULL, NULL, 921.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185620, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 18, 5, NULL, NULL, NULL, 977.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185621, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, 5, NULL, NULL, NULL, 1022.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185622, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 10, 5, NULL, NULL, NULL, 1044.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185623, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 5, NULL, NULL, NULL, 26.89, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185624, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 37, NULL, NULL, NULL, NULL, 1061.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185625, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 36, NULL, NULL, NULL, NULL, 1063.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185626, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 34, NULL, NULL, NULL, NULL, 1105.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185627, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 33, NULL, NULL, NULL, NULL, 1117.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185628, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 31, NULL, NULL, NULL, NULL, 1129.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185629, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, NULL, NULL, NULL, NULL, 1141.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185630, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 29, NULL, NULL, NULL, NULL, 1155.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:03', '2024-09-09 15:32:03', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185631, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, NULL, NULL, NULL, NULL, 1168.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185632, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 200, NULL, NULL, NULL, NULL, 26.89, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185633, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 52, NULL, NULL, NULL, NULL, 1205.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185634, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 50, NULL, NULL, NULL, NULL, 1219.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185635, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 42, NULL, NULL, NULL, NULL, 1370.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185636, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, NULL, NULL, NULL, NULL, 1534.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185637, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, NULL, NULL, NULL, NULL, 1549.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185638, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 23, NULL, NULL, NULL, NULL, 1599.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185639, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 19, NULL, NULL, NULL, NULL, 1633.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185640, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 16, NULL, NULL, NULL, NULL, 1662.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185641, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, NULL, NULL, NULL, NULL, 1694.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185642, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 5, NULL, NULL, NULL, 28.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185643, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, NULL, NULL, NULL, NULL, 1741.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185644, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 38, NULL, NULL, NULL, NULL, 1756.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185645, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 35, NULL, NULL, NULL, NULL, 1774.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185646, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 32, NULL, NULL, NULL, NULL, 1814.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185647, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 29, NULL, NULL, NULL, NULL, 1857.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:04', '2024-09-09 15:32:04', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185648, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, NULL, NULL, NULL, NULL, 1888.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185649, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 25, NULL, NULL, NULL, NULL, 1919.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185650, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, NULL, NULL, NULL, NULL, 2056.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185651, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 2, NULL, NULL, NULL, 25.22, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185652, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 37, NULL, NULL, NULL, NULL, 2060.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185653, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 33, NULL, NULL, NULL, NULL, 2096.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185654, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, NULL, NULL, NULL, NULL, 2186.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185655, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 21, NULL, NULL, NULL, NULL, 2251.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185656, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 17, NULL, NULL, NULL, NULL, 2297.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185657, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, NULL, NULL, NULL, NULL, 2330.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185658, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 12, NULL, NULL, NULL, NULL, 2352.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185659, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 10, NULL, NULL, NULL, NULL, 2383.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185660, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 29.95, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185661, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 38, NULL, NULL, NULL, NULL, 2415.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185662, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 32, NULL, NULL, NULL, NULL, 2452.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:05', '2024-09-09 15:32:05', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185663, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, NULL, NULL, NULL, NULL, 2522.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185664, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 21, NULL, NULL, NULL, NULL, 2581.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185665, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 16, NULL, NULL, NULL, NULL, 2637.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185666, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 15, NULL, NULL, NULL, NULL, 2655.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185667, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 26.92, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185668, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, 1, NULL, NULL, NULL, 2683.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185669, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 36, 1, NULL, NULL, NULL, 2733.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185670, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 34, 1, NULL, NULL, NULL, 2751.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185671, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 32, 1, NULL, NULL, NULL, 2787.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185672, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, 1, NULL, NULL, NULL, 2805.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185673, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, 1, NULL, NULL, NULL, 2828.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185674, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, 1, NULL, NULL, NULL, 2834.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185675, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 22, 1, NULL, NULL, NULL, 2889.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185676, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 19, 1, NULL, NULL, NULL, 2923.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185677, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 18, 1, NULL, NULL, NULL, 2937.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:06', '2024-09-09 15:32:06', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185678, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 10, 1, NULL, NULL, NULL, 3004.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185679, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 25.56, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185680, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, 1, NULL, NULL, NULL, 3009.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185681, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 34, 1, NULL, NULL, NULL, 3044.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185682, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 33, 1, NULL, NULL, NULL, 3060.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185683, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, 1, NULL, NULL, NULL, 3109.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185684, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, 1, NULL, NULL, NULL, 3124.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185685, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, 1, NULL, NULL, NULL, 3133.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185686, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 26, 1, NULL, NULL, NULL, 3150.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185687, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 24, 1, NULL, NULL, NULL, 3182.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185688, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 21, 1, NULL, NULL, NULL, 3213.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185689, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 20, 1, NULL, NULL, NULL, 3230.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185690, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 19, 1, NULL, NULL, NULL, 3247.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185691, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 17, 1, NULL, NULL, NULL, 3278.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185692, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, 1, NULL, NULL, NULL, 3311.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185693, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 13, 1, NULL, NULL, NULL, 3327.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185694, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 13, 1, NULL, NULL, NULL, 3329.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185695, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 26.99, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:07', '2024-09-09 15:32:07', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185696, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, 1, NULL, NULL, NULL, 3348.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185697, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 37, 1, NULL, NULL, NULL, 3364.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185698, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 37, 1, NULL, NULL, NULL, 3382.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1798552009868185699, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 33, 1, NULL, NULL, NULL, 3414.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1801153956043427840, 0, 'x11111', '11111', NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 5000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1808745154681638912, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 3, NULL, NULL, NULL, 20.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811696374656405504, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, 5, NULL, NULL, NULL, 3459.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811696940006641664, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, 5, NULL, NULL, NULL, 3467.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697078255095808, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 28, 5, NULL, NULL, NULL, 3478.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697593026220032, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 27.55, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697738337882112, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 39, 5, NULL, NULL, NULL, 3677.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697807246102528, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 41, 5, NULL, NULL, NULL, 3683.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697888607211520, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 38, 5, NULL, NULL, NULL, 3714.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811697944349511680, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 32, 5, NULL, NULL, NULL, 3815.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811698010380439552, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, 5, NULL, NULL, NULL, 3891.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811698092827873280, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 24, 5, NULL, NULL, NULL, 3926.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:08', '2024-09-09 15:32:08', 1);
INSERT INTO `un_module_record` VALUES (1811698161996140544, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 20, 5, NULL, NULL, NULL, 3962.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802056523452416, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 18, NULL, NULL, NULL, NULL, 3980.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802121715519488, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 14, NULL, NULL, NULL, NULL, 4033.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802291232509952, 7, NULL, NULL, NULL, 2, 1, 1, NULL, NULL, 200, 1, NULL, NULL, NULL, 27.21, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802341505437696, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 38, NULL, NULL, NULL, NULL, 4052.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802422187069440, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 37, NULL, NULL, NULL, NULL, 4084.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802479007305728, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 35, NULL, NULL, NULL, NULL, 4105.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1825802591360126976, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 31, NULL, NULL, NULL, NULL, 4146.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1833038003484889088, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 30, NULL, NULL, NULL, NULL, 4162.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1833038060972019712, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 29, NULL, NULL, NULL, NULL, 4178.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1833038122741534720, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 27, NULL, NULL, NULL, NULL, 4209.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);
INSERT INTO `un_module_record` VALUES (1833038393018290176, 7, NULL, NULL, NULL, 1, 1, 1, NULL, NULL, 23, NULL, NULL, NULL, NULL, 4247.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, '2024-09-09 15:32:09', '2024-09-09 15:32:09', 1);

-- ----------------------------
-- Table structure for un_module_record_data
-- ----------------------------
DROP TABLE IF EXISTS `un_module_record_data`;
CREATE TABLE `un_module_record_data`  (
                                          `id` bigint NOT NULL AUTO_INCREMENT,
                                          `module_id` bigint NOT NULL COMMENT '模块ID',
                                          `record_id` bigint NOT NULL COMMENT '具体数据recordID',
                                          `field_id` bigint NOT NULL COMMENT '字段ID',
                                          `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字段名称',
                                          `value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '值',
                                          `old_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '老值',
                                          `create_time` datetime NOT NULL,
                                          `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                          PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 384 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '主数据自定义字段存值表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_record_data
-- ----------------------------
INSERT INTO `un_module_record_data` VALUES (2, 4, 2, 12, 'fielddecimal1', NULL, NULL, '2024-06-05 17:55:01', 1);
INSERT INTO `un_module_record_data` VALUES (3, 4, 2, 1, 'id', '\"2\"', NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (4, 4, 2, 2, 'num', '\"1111123\"', NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (5, 4, 2, 3, 'name', '\"x1111123\"', NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (6, 4, 2, 4, 'remark', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (7, 4, 2, 5, 'check_flag', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (8, 4, 2, 6, 'examine_record_id', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (9, 4, 2, 7, 'examine_time', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (10, 4, 2, 8, 'create_user_id', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (11, 4, 2, 9, 'owner_user_id', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (12, 4, 2, 10, 'create_time', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (13, 4, 2, 11, 'update_time', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (14, 4, 2, 12, 'fielddecimal1', NULL, NULL, '2024-06-06 10:30:53', 1);
INSERT INTO `un_module_record_data` VALUES (15, 4, 1798543235782676480, 1, 'id', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (16, 4, 1798543235782676480, 2, 'num', '\"11111\"', NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (17, 4, 1798543235782676480, 3, 'name', '\"x11111\"', NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (18, 4, 1798543235782676480, 4, 'remark', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (19, 4, 1798543235782676480, 5, 'check_flag', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (20, 4, 1798543235782676480, 6, 'examine_record_id', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (21, 4, 1798543235782676480, 7, 'examine_time', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (22, 4, 1798543235782676480, 8, 'create_user_id', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (23, 4, 1798543235782676480, 9, 'owner_user_id', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (24, 4, 1798543235782676480, 10, 'create_time', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (25, 4, 1798543235782676480, 11, 'update_time', NULL, NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (26, 4, 1798543235782676480, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 10:31:37', 1);
INSERT INTO `un_module_record_data` VALUES (27, 4, 1798543235782676480, 1, 'id', '\"1798543235782676480\"', NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (28, 4, 1798543235782676480, 2, 'num', '\"11111234\"', NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (29, 4, 1798543235782676480, 3, 'name', '\"x1111123\"', NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (30, 4, 1798543235782676480, 4, 'remark', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (31, 4, 1798543235782676480, 5, 'check_flag', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (32, 4, 1798543235782676480, 6, 'examine_record_id', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (33, 4, 1798543235782676480, 7, 'examine_time', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (34, 4, 1798543235782676480, 8, 'create_user_id', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (35, 4, 1798543235782676480, 9, 'owner_user_id', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (36, 4, 1798543235782676480, 10, 'create_time', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (37, 4, 1798543235782676480, 11, 'update_time', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (38, 4, 1798543235782676480, 12, 'fielddecimal1', NULL, NULL, '2024-06-06 10:31:53', 1);
INSERT INTO `un_module_record_data` VALUES (39, 4, 1798543672044818432, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 10:33:21', 1);
INSERT INTO `un_module_record_data` VALUES (40, 4, 1798550335896293376, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 10:59:37', 1);
INSERT INTO `un_module_record_data` VALUES (41, 4, 1798551293107769344, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 11:03:36', 1);
INSERT INTO `un_module_record_data` VALUES (42, 4, 1798551654702911488, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 11:05:05', 1);
INSERT INTO `un_module_record_data` VALUES (43, 4, 1798552009868185600, 12, 'fielddecimal1', '\"5000\"', NULL, '2024-06-06 11:06:29', 1);
INSERT INTO `un_module_record_data` VALUES (44, 4, 1801153956043427840, 12, 'fieldlong2', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (45, 4, 1801153956043427840, 13, 'fieldlong3', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (46, 4, 1801153956043427840, 14, 'fieldlong4', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (47, 4, 1801153956043427840, 15, 'fieldlong5', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (48, 4, 1801153956043427840, 16, 'fielddate1', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (49, 4, 1801153956043427840, 17, 'fielddate2', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (50, 4, 1801153956043427840, 18, 'fielddate3', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (51, 4, 1801153956043427840, 19, 'fielddate4', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (52, 4, 1801153956043427840, 20, 'fielddate5', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (53, 4, 1801153956043427840, 21, 'fieldtext0', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (54, 4, 1801153956043427840, 22, 'fieldtext1', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (55, 4, 1801153956043427840, 45, 'fielddecimal1', '\"5000\"', NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (56, 4, 1801153956043427840, 46, 'id', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (57, 4, 1801153956043427840, 47, 'record_flag', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (58, 4, 1801153956043427840, 48, 'fielddecimal1', '\"5000\"', NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (59, 4, 1801153956043427840, 49, 'fieldnum1', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (60, 4, 1801153956043427840, 50, 'fieldnum2', NULL, NULL, '2024-06-13 15:25:41', 1);
INSERT INTO `un_module_record_data` VALUES (61, 7, 1808745154681638912, 12, 'fieldlong2', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (62, 7, 1808745154681638912, 13, 'fieldlong3', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (63, 7, 1808745154681638912, 14, 'fieldlong4', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (64, 7, 1808745154681638912, 15, 'fieldlong5', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (65, 7, 1808745154681638912, 16, 'fielddate1', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (66, 7, 1808745154681638912, 17, 'fielddate2', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (67, 7, 1808745154681638912, 18, 'fielddate3', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (68, 7, 1808745154681638912, 19, 'fielddate4', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (69, 7, 1808745154681638912, 20, 'fielddate5', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (70, 7, 1808745154681638912, 21, 'fieldtext0', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (71, 7, 1808745154681638912, 22, 'fieldtext1', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (72, 7, 1808745154681638912, 45, 'fielddecimal1', '\"20\"', NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (73, 7, 1808745154681638912, 46, 'id', NULL, NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (74, 7, 1808745154681638912, 47, 'record_flag', '\"2\"', NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (75, 7, 1808745154681638912, 48, 'fielddecimal1', '\"20\"', NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (76, 7, 1808745154681638912, 49, 'fieldnum1', '\"200\"', NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (77, 7, 1808745154681638912, 50, 'fieldnum2', '\"3\"', NULL, '2024-07-04 14:10:24', 1);
INSERT INTO `un_module_record_data` VALUES (78, 7, 1811696374656405504, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (79, 7, 1811696374656405504, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (80, 7, 1811696374656405504, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (81, 7, 1811696374656405504, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (82, 7, 1811696374656405504, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (83, 7, 1811696374656405504, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (84, 7, 1811696374656405504, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (85, 7, 1811696374656405504, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (86, 7, 1811696374656405504, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (87, 7, 1811696374656405504, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (88, 7, 1811696374656405504, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (89, 7, 1811696374656405504, 45, 'fielddecimal1', '\"3459\"', NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (90, 7, 1811696374656405504, 46, 'id', NULL, NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (91, 7, 1811696374656405504, 47, 'record_flag', '\"1\"', NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (92, 7, 1811696374656405504, 48, 'fielddecimal1', '\"3459\"', NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (93, 7, 1811696374656405504, 49, 'fieldnum1', '\"30\"', NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (94, 7, 1811696374656405504, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:37:30', 1);
INSERT INTO `un_module_record_data` VALUES (95, 7, 1811696940006641664, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (96, 7, 1811696940006641664, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (97, 7, 1811696940006641664, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (98, 7, 1811696940006641664, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (99, 7, 1811696940006641664, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (100, 7, 1811696940006641664, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (101, 7, 1811696940006641664, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (102, 7, 1811696940006641664, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (103, 7, 1811696940006641664, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (104, 7, 1811696940006641664, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (105, 7, 1811696940006641664, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (106, 7, 1811696940006641664, 45, 'fielddecimal1', '\"3467\"', NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (107, 7, 1811696940006641664, 46, 'id', NULL, NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (108, 7, 1811696940006641664, 47, 'record_flag', '\"1\"', NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (109, 7, 1811696940006641664, 48, 'fielddecimal1', '\"3467\"', NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (110, 7, 1811696940006641664, 49, 'fieldnum1', '\"30\"', NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (111, 7, 1811696940006641664, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:39:45', 1);
INSERT INTO `un_module_record_data` VALUES (112, 7, 1811697078255095808, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (113, 7, 1811697078255095808, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (114, 7, 1811697078255095808, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (115, 7, 1811697078255095808, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (116, 7, 1811697078255095808, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (117, 7, 1811697078255095808, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (118, 7, 1811697078255095808, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (119, 7, 1811697078255095808, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (120, 7, 1811697078255095808, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (121, 7, 1811697078255095808, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (122, 7, 1811697078255095808, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (123, 7, 1811697078255095808, 45, 'fielddecimal1', '\"3478\"', NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (124, 7, 1811697078255095808, 46, 'id', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (125, 7, 1811697078255095808, 47, 'record_flag', NULL, NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (126, 7, 1811697078255095808, 48, 'fielddecimal1', '\"3478\"', NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (127, 7, 1811697078255095808, 49, 'fieldnum1', '\"28\"', NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (128, 7, 1811697078255095808, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:40:18', 1);
INSERT INTO `un_module_record_data` VALUES (129, 7, 1811697593026220032, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (130, 7, 1811697593026220032, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (131, 7, 1811697593026220032, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (132, 7, 1811697593026220032, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (133, 7, 1811697593026220032, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (134, 7, 1811697593026220032, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (135, 7, 1811697593026220032, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (136, 7, 1811697593026220032, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (137, 7, 1811697593026220032, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (138, 7, 1811697593026220032, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (139, 7, 1811697593026220032, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (140, 7, 1811697593026220032, 45, 'fielddecimal1', '\"27.55\"', NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (141, 7, 1811697593026220032, 46, 'id', NULL, NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (142, 7, 1811697593026220032, 47, 'record_flag', '\"2\"', NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (143, 7, 1811697593026220032, 48, 'fielddecimal1', '\"27.55\"', NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (144, 7, 1811697593026220032, 49, 'fieldnum1', '\"200\"', NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (145, 7, 1811697593026220032, 50, 'fieldnum2', '\"1\"', NULL, '2024-07-12 17:42:21', 1);
INSERT INTO `un_module_record_data` VALUES (146, 7, 1811697738337882112, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (147, 7, 1811697738337882112, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (148, 7, 1811697738337882112, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (149, 7, 1811697738337882112, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (150, 7, 1811697738337882112, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (151, 7, 1811697738337882112, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (152, 7, 1811697738337882112, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (153, 7, 1811697738337882112, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (154, 7, 1811697738337882112, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (155, 7, 1811697738337882112, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (156, 7, 1811697738337882112, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (157, 7, 1811697738337882112, 45, 'fielddecimal1', '\"3677\"', NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (158, 7, 1811697738337882112, 46, 'id', NULL, NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (159, 7, 1811697738337882112, 47, 'record_flag', '\"1\"', NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (160, 7, 1811697738337882112, 48, 'fielddecimal1', '\"3677\"', NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (161, 7, 1811697738337882112, 49, 'fieldnum1', '\"39\"', NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (162, 7, 1811697738337882112, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:42:55', 1);
INSERT INTO `un_module_record_data` VALUES (163, 7, 1811697807246102528, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (164, 7, 1811697807246102528, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (165, 7, 1811697807246102528, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (166, 7, 1811697807246102528, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (167, 7, 1811697807246102528, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (168, 7, 1811697807246102528, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (169, 7, 1811697807246102528, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (170, 7, 1811697807246102528, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (171, 7, 1811697807246102528, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (172, 7, 1811697807246102528, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (173, 7, 1811697807246102528, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (174, 7, 1811697807246102528, 45, 'fielddecimal1', '\"3683\"', NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (175, 7, 1811697807246102528, 46, 'id', NULL, NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (176, 7, 1811697807246102528, 47, 'record_flag', '\"1\"', NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (177, 7, 1811697807246102528, 48, 'fielddecimal1', '\"3683\"', NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (178, 7, 1811697807246102528, 49, 'fieldnum1', '\"41\"', NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (179, 7, 1811697807246102528, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:43:12', 1);
INSERT INTO `un_module_record_data` VALUES (180, 7, 1811697888607211520, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (181, 7, 1811697888607211520, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (182, 7, 1811697888607211520, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (183, 7, 1811697888607211520, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (184, 7, 1811697888607211520, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (185, 7, 1811697888607211520, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (186, 7, 1811697888607211520, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (187, 7, 1811697888607211520, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (188, 7, 1811697888607211520, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (189, 7, 1811697888607211520, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (190, 7, 1811697888607211520, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (191, 7, 1811697888607211520, 45, 'fielddecimal1', '\"3714\"', NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (192, 7, 1811697888607211520, 46, 'id', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (193, 7, 1811697888607211520, 47, 'record_flag', NULL, NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (194, 7, 1811697888607211520, 48, 'fielddecimal1', '\"3714\"', NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (195, 7, 1811697888607211520, 49, 'fieldnum1', '\"38\"', NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (196, 7, 1811697888607211520, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:43:31', 1);
INSERT INTO `un_module_record_data` VALUES (197, 7, 1811697944349511680, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (198, 7, 1811697944349511680, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (199, 7, 1811697944349511680, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (200, 7, 1811697944349511680, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (201, 7, 1811697944349511680, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (202, 7, 1811697944349511680, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (203, 7, 1811697944349511680, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (204, 7, 1811697944349511680, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (205, 7, 1811697944349511680, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (206, 7, 1811697944349511680, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (207, 7, 1811697944349511680, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (208, 7, 1811697944349511680, 45, 'fielddecimal1', '\"3815\"', NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (209, 7, 1811697944349511680, 46, 'id', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (210, 7, 1811697944349511680, 47, 'record_flag', NULL, NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (211, 7, 1811697944349511680, 48, 'fielddecimal1', '\"3815\"', NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (212, 7, 1811697944349511680, 49, 'fieldnum1', '\"32\"', NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (213, 7, 1811697944349511680, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:43:44', 1);
INSERT INTO `un_module_record_data` VALUES (214, 7, 1811698010380439552, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (215, 7, 1811698010380439552, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (216, 7, 1811698010380439552, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (217, 7, 1811698010380439552, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (218, 7, 1811698010380439552, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (219, 7, 1811698010380439552, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (220, 7, 1811698010380439552, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (221, 7, 1811698010380439552, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (222, 7, 1811698010380439552, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (223, 7, 1811698010380439552, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (224, 7, 1811698010380439552, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (225, 7, 1811698010380439552, 45, 'fielddecimal1', '\"3891\"', NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (226, 7, 1811698010380439552, 46, 'id', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (227, 7, 1811698010380439552, 47, 'record_flag', NULL, NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (228, 7, 1811698010380439552, 48, 'fielddecimal1', '\"3891\"', NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (229, 7, 1811698010380439552, 49, 'fieldnum1', '\"27\"', NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (230, 7, 1811698010380439552, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:44:00', 1);
INSERT INTO `un_module_record_data` VALUES (231, 7, 1811698092827873280, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (232, 7, 1811698092827873280, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (233, 7, 1811698092827873280, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (234, 7, 1811698092827873280, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (235, 7, 1811698092827873280, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (236, 7, 1811698092827873280, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (237, 7, 1811698092827873280, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (238, 7, 1811698092827873280, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (239, 7, 1811698092827873280, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (240, 7, 1811698092827873280, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (241, 7, 1811698092827873280, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (242, 7, 1811698092827873280, 45, 'fielddecimal1', '\"3926\"', NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (243, 7, 1811698092827873280, 46, 'id', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (244, 7, 1811698092827873280, 47, 'record_flag', NULL, NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (245, 7, 1811698092827873280, 48, 'fielddecimal1', '\"3926\"', NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (246, 7, 1811698092827873280, 49, 'fieldnum1', '\"24\"', NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (247, 7, 1811698092827873280, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:44:20', 1);
INSERT INTO `un_module_record_data` VALUES (248, 7, 1811698161996140544, 12, 'fieldlong2', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (249, 7, 1811698161996140544, 13, 'fieldlong3', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (250, 7, 1811698161996140544, 14, 'fieldlong4', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (251, 7, 1811698161996140544, 15, 'fieldlong5', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (252, 7, 1811698161996140544, 16, 'fielddate1', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (253, 7, 1811698161996140544, 17, 'fielddate2', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (254, 7, 1811698161996140544, 18, 'fielddate3', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (255, 7, 1811698161996140544, 19, 'fielddate4', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (256, 7, 1811698161996140544, 20, 'fielddate5', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (257, 7, 1811698161996140544, 21, 'fieldtext0', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (258, 7, 1811698161996140544, 22, 'fieldtext1', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (259, 7, 1811698161996140544, 45, 'fielddecimal1', '\"3962\"', NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (260, 7, 1811698161996140544, 46, 'id', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (261, 7, 1811698161996140544, 47, 'record_flag', NULL, NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (262, 7, 1811698161996140544, 48, 'fielddecimal1', '\"3962\"', NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (263, 7, 1811698161996140544, 49, 'fieldnum1', '\"20\"', NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (264, 7, 1811698161996140544, 50, 'fieldnum2', '\"5\"', NULL, '2024-07-12 17:44:36', 1);
INSERT INTO `un_module_record_data` VALUES (265, 7, 1825802056523452416, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (266, 7, 1825802056523452416, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (267, 7, 1825802056523452416, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (268, 7, 1825802056523452416, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (269, 7, 1825802056523452416, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (270, 7, 1825802056523452416, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (271, 7, 1825802056523452416, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (272, 7, 1825802056523452416, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (273, 7, 1825802056523452416, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (274, 7, 1825802056523452416, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (275, 7, 1825802056523452416, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (276, 7, 1825802056523452416, 45, 'fielddecimal1', '\"3980\"', NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (277, 7, 1825802056523452416, 46, 'id', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (278, 7, 1825802056523452416, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (279, 7, 1825802056523452416, 48, 'fielddecimal1', '\"3980\"', NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (280, 7, 1825802056523452416, 49, 'fieldnum1', '\"18\"', NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (281, 7, 1825802056523452416, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:48:27', 1);
INSERT INTO `un_module_record_data` VALUES (282, 7, 1825802121715519488, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (283, 7, 1825802121715519488, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (284, 7, 1825802121715519488, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (285, 7, 1825802121715519488, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (286, 7, 1825802121715519488, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (287, 7, 1825802121715519488, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (288, 7, 1825802121715519488, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (289, 7, 1825802121715519488, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (290, 7, 1825802121715519488, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (291, 7, 1825802121715519488, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (292, 7, 1825802121715519488, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (293, 7, 1825802121715519488, 45, 'fielddecimal1', '\"4033\"', NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (294, 7, 1825802121715519488, 46, 'id', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (295, 7, 1825802121715519488, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (296, 7, 1825802121715519488, 48, 'fielddecimal1', '\"4033\"', NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (297, 7, 1825802121715519488, 49, 'fieldnum1', '\"14\"', NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (298, 7, 1825802121715519488, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:48:42', 1);
INSERT INTO `un_module_record_data` VALUES (299, 7, 1825802291232509952, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (300, 7, 1825802291232509952, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (301, 7, 1825802291232509952, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (302, 7, 1825802291232509952, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (303, 7, 1825802291232509952, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (304, 7, 1825802291232509952, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (305, 7, 1825802291232509952, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (306, 7, 1825802291232509952, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (307, 7, 1825802291232509952, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (308, 7, 1825802291232509952, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (309, 7, 1825802291232509952, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (310, 7, 1825802291232509952, 45, 'fielddecimal1', '\"27.21\"', NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (311, 7, 1825802291232509952, 46, 'id', NULL, NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (312, 7, 1825802291232509952, 47, 'record_flag', '\"2\"', NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (313, 7, 1825802291232509952, 48, 'fielddecimal1', '\"27.21\"', NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (314, 7, 1825802291232509952, 49, 'fieldnum1', '\"200\"', NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (315, 7, 1825802291232509952, 50, 'fieldnum2', '\"1\"', NULL, '2024-08-20 15:49:23', 1);
INSERT INTO `un_module_record_data` VALUES (316, 7, 1825802341505437696, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (317, 7, 1825802341505437696, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (318, 7, 1825802341505437696, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (319, 7, 1825802341505437696, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (320, 7, 1825802341505437696, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (321, 7, 1825802341505437696, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (322, 7, 1825802341505437696, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (323, 7, 1825802341505437696, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (324, 7, 1825802341505437696, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (325, 7, 1825802341505437696, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (326, 7, 1825802341505437696, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (327, 7, 1825802341505437696, 45, 'fielddecimal1', '\"4052\"', NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (328, 7, 1825802341505437696, 46, 'id', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (329, 7, 1825802341505437696, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (330, 7, 1825802341505437696, 48, 'fielddecimal1', '\"4052\"', NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (331, 7, 1825802341505437696, 49, 'fieldnum1', '\"38\"', NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (332, 7, 1825802341505437696, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:49:35', 1);
INSERT INTO `un_module_record_data` VALUES (333, 7, 1825802422187069440, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (334, 7, 1825802422187069440, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (335, 7, 1825802422187069440, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (336, 7, 1825802422187069440, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (337, 7, 1825802422187069440, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (338, 7, 1825802422187069440, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (339, 7, 1825802422187069440, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (340, 7, 1825802422187069440, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (341, 7, 1825802422187069440, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (342, 7, 1825802422187069440, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (343, 7, 1825802422187069440, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (344, 7, 1825802422187069440, 45, 'fielddecimal1', '\"4084\"', NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (345, 7, 1825802422187069440, 46, 'id', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (346, 7, 1825802422187069440, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (347, 7, 1825802422187069440, 48, 'fielddecimal1', '\"4084\"', NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (348, 7, 1825802422187069440, 49, 'fieldnum1', '\"37\"', NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (349, 7, 1825802422187069440, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:49:54', 1);
INSERT INTO `un_module_record_data` VALUES (350, 7, 1825802479007305728, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (351, 7, 1825802479007305728, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (352, 7, 1825802479007305728, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (353, 7, 1825802479007305728, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (354, 7, 1825802479007305728, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (355, 7, 1825802479007305728, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (356, 7, 1825802479007305728, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (357, 7, 1825802479007305728, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (358, 7, 1825802479007305728, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (359, 7, 1825802479007305728, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (360, 7, 1825802479007305728, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (361, 7, 1825802479007305728, 45, 'fielddecimal1', '\"4105\"', NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (362, 7, 1825802479007305728, 46, 'id', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (363, 7, 1825802479007305728, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (364, 7, 1825802479007305728, 48, 'fielddecimal1', '\"4105\"', NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (365, 7, 1825802479007305728, 49, 'fieldnum1', '\"35\"', NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (366, 7, 1825802479007305728, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:50:07', 1);
INSERT INTO `un_module_record_data` VALUES (367, 7, 1825802591360126976, 12, 'fieldlong2', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (368, 7, 1825802591360126976, 13, 'fieldlong3', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (369, 7, 1825802591360126976, 14, 'fieldlong4', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (370, 7, 1825802591360126976, 15, 'fieldlong5', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (371, 7, 1825802591360126976, 16, 'fielddate1', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (372, 7, 1825802591360126976, 17, 'fielddate2', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (373, 7, 1825802591360126976, 18, 'fielddate3', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (374, 7, 1825802591360126976, 19, 'fielddate4', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (375, 7, 1825802591360126976, 20, 'fielddate5', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (376, 7, 1825802591360126976, 21, 'fieldtext0', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (377, 7, 1825802591360126976, 22, 'fieldtext1', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (378, 7, 1825802591360126976, 45, 'fielddecimal1', '\"4146\"', NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (379, 7, 1825802591360126976, 46, 'id', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (380, 7, 1825802591360126976, 47, 'record_flag', '\"1\"', NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (381, 7, 1825802591360126976, 48, 'fielddecimal1', '\"4146\"', NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (382, 7, 1825802591360126976, 49, 'fieldnum1', '\"31\"', NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (383, 7, 1825802591360126976, 50, 'fieldnum2', NULL, NULL, '2024-08-20 15:50:34', 1);
INSERT INTO `un_module_record_data` VALUES (384, 7, 1833038003484889088, 12, 'fieldlong2', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (385, 7, 1833038003484889088, 13, 'fieldlong3', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (386, 7, 1833038003484889088, 14, 'fieldlong4', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (387, 7, 1833038003484889088, 15, 'fieldlong5', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (388, 7, 1833038003484889088, 16, 'fielddate1', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (389, 7, 1833038003484889088, 17, 'fielddate2', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (390, 7, 1833038003484889088, 18, 'fielddate3', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (391, 7, 1833038003484889088, 19, 'fielddate4', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (392, 7, 1833038003484889088, 20, 'fielddate5', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (393, 7, 1833038003484889088, 21, 'fieldtext0', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (394, 7, 1833038003484889088, 22, 'fieldtext1', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (395, 7, 1833038003484889088, 45, 'fielddecimal1', '\"4162\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (396, 7, 1833038003484889088, 46, 'id', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (397, 7, 1833038003484889088, 47, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (398, 7, 1833038003484889088, 48, 'fielddecimal1', '\"4162\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (399, 7, 1833038003484889088, 49, 'fieldnum1', '\"30\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (400, 7, 1833038003484889088, 50, 'fieldnum2', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (401, 7, 1833038003484889088, 51, 'id', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (402, 7, 1833038003484889088, 52, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (403, 7, 1833038003484889088, 53, 'fielddecimal1', '\"4162\"', NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (404, 7, 1833038003484889088, 54, 'fielddecimal2', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (405, 7, 1833038003484889088, 55, 'remark', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (406, 7, 1833038003484889088, 56, 'fielddecimal3', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (407, 7, 1833038003484889088, 57, 'fielddecimal4', NULL, NULL, '2024-09-09 15:01:31', 1);
INSERT INTO `un_module_record_data` VALUES (408, 7, 1833038060972019712, 12, 'fieldlong2', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (409, 7, 1833038060972019712, 13, 'fieldlong3', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (410, 7, 1833038060972019712, 14, 'fieldlong4', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (411, 7, 1833038060972019712, 15, 'fieldlong5', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (412, 7, 1833038060972019712, 16, 'fielddate1', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (413, 7, 1833038060972019712, 17, 'fielddate2', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (414, 7, 1833038060972019712, 18, 'fielddate3', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (415, 7, 1833038060972019712, 19, 'fielddate4', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (416, 7, 1833038060972019712, 20, 'fielddate5', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (417, 7, 1833038060972019712, 21, 'fieldtext0', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (418, 7, 1833038060972019712, 22, 'fieldtext1', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (419, 7, 1833038060972019712, 45, 'fielddecimal1', '\"4178\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (420, 7, 1833038060972019712, 46, 'id', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (421, 7, 1833038060972019712, 47, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (422, 7, 1833038060972019712, 48, 'fielddecimal1', '\"4178\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (423, 7, 1833038060972019712, 49, 'fieldnum1', '\"29\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (424, 7, 1833038060972019712, 50, 'fieldnum2', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (425, 7, 1833038060972019712, 51, 'id', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (426, 7, 1833038060972019712, 52, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (427, 7, 1833038060972019712, 53, 'fielddecimal1', '\"4178\"', NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (428, 7, 1833038060972019712, 54, 'fielddecimal2', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (429, 7, 1833038060972019712, 55, 'remark', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (430, 7, 1833038060972019712, 56, 'fielddecimal3', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (431, 7, 1833038060972019712, 57, 'fielddecimal4', NULL, NULL, '2024-09-09 15:01:45', 1);
INSERT INTO `un_module_record_data` VALUES (432, 7, 1833038122741534720, 12, 'fieldlong2', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (433, 7, 1833038122741534720, 13, 'fieldlong3', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (434, 7, 1833038122741534720, 14, 'fieldlong4', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (435, 7, 1833038122741534720, 15, 'fieldlong5', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (436, 7, 1833038122741534720, 16, 'fielddate1', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (437, 7, 1833038122741534720, 17, 'fielddate2', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (438, 7, 1833038122741534720, 18, 'fielddate3', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (439, 7, 1833038122741534720, 19, 'fielddate4', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (440, 7, 1833038122741534720, 20, 'fielddate5', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (441, 7, 1833038122741534720, 21, 'fieldtext0', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (442, 7, 1833038122741534720, 22, 'fieldtext1', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (443, 7, 1833038122741534720, 45, 'fielddecimal1', '\"4209\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (444, 7, 1833038122741534720, 46, 'id', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (445, 7, 1833038122741534720, 47, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (446, 7, 1833038122741534720, 48, 'fielddecimal1', '\"4209\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (447, 7, 1833038122741534720, 49, 'fieldnum1', '\"27\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (448, 7, 1833038122741534720, 50, 'fieldnum2', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (449, 7, 1833038122741534720, 51, 'id', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (450, 7, 1833038122741534720, 52, 'record_flag', '\"1\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (451, 7, 1833038122741534720, 53, 'fielddecimal1', '\"4209\"', NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (452, 7, 1833038122741534720, 54, 'fielddecimal2', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (453, 7, 1833038122741534720, 55, 'remark', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (454, 7, 1833038122741534720, 56, 'fielddecimal3', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (455, 7, 1833038122741534720, 57, 'fielddecimal4', NULL, NULL, '2024-09-09 15:01:59', 1);
INSERT INTO `un_module_record_data` VALUES (456, 7, 1833038393018290176, 12, 'fieldlong2', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (457, 7, 1833038393018290176, 13, 'fieldlong3', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (458, 7, 1833038393018290176, 14, 'fieldlong4', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (459, 7, 1833038393018290176, 15, 'fieldlong5', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (460, 7, 1833038393018290176, 16, 'fielddate1', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (461, 7, 1833038393018290176, 17, 'fielddate2', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (462, 7, 1833038393018290176, 18, 'fielddate3', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (463, 7, 1833038393018290176, 19, 'fielddate4', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (464, 7, 1833038393018290176, 20, 'fielddate5', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (465, 7, 1833038393018290176, 21, 'fieldtext0', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (466, 7, 1833038393018290176, 22, 'fieldtext1', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (467, 7, 1833038393018290176, 45, 'fielddecimal1', '\"4247\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (468, 7, 1833038393018290176, 46, 'id', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (469, 7, 1833038393018290176, 47, 'record_flag', '\"1\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (470, 7, 1833038393018290176, 48, 'fielddecimal1', '\"4247\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (471, 7, 1833038393018290176, 49, 'fieldnum1', '\"23\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (472, 7, 1833038393018290176, 50, 'fieldnum2', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (473, 7, 1833038393018290176, 51, 'id', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (474, 7, 1833038393018290176, 52, 'record_flag', '\"1\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (475, 7, 1833038393018290176, 53, 'fielddecimal1', '\"4247\"', NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (476, 7, 1833038393018290176, 54, 'fielddecimal2', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (477, 7, 1833038393018290176, 55, 'remark', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (478, 7, 1833038393018290176, 56, 'fielddecimal3', NULL, NULL, '2024-09-09 15:03:04', 1);
INSERT INTO `un_module_record_data` VALUES (479, 7, 1833038393018290176, 57, 'fielddecimal4', NULL, NULL, '2024-09-09 15:03:04', 1);

-- ----------------------------
-- Table structure for un_module_role
-- ----------------------------
DROP TABLE IF EXISTS `un_module_role`;
CREATE TABLE `un_module_role`  (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `module_id` bigint NULL DEFAULT NULL COMMENT '模块Id',
                                   `role_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '名称',
                                   `admin_flag` int NOT NULL DEFAULT 0 COMMENT '1管理员 0非管理员',
                                   `remark` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '备注',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                   `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_role
-- ----------------------------
INSERT INTO `un_module_role` VALUES (1, 2, '超级管理员', 1, NULL, NULL, 0, '2024-09-09 15:31:41', NULL, 1);
INSERT INTO `un_module_role` VALUES (2, 2, '默认角色', 0, NULL, NULL, 0, '2024-09-09 15:31:41', NULL, 1);

-- ----------------------------
-- Table structure for un_module_role_data
-- ----------------------------
DROP TABLE IF EXISTS `un_module_role_data`;
CREATE TABLE `un_module_role_data`  (
                                        `id` bigint NOT NULL,
                                        `role_id` bigint NOT NULL COMMENT '角色ID',
                                        `module_id` bigint NULL DEFAULT NULL COMMENT '所属模块',
                                        `data_type` int NULL DEFAULT 1 COMMENT '数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                        `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                        `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '模块的角色对应数据权限' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_role_data
-- ----------------------------
INSERT INTO `un_module_role_data` VALUES (1, 1, 4, 5, NULL, 0, '2024-09-09 15:31:37', NULL, 1);
INSERT INTO `un_module_role_data` VALUES (2, 2, 4, 1, NULL, 0, '2024-09-09 15:31:37', NULL, 1);

-- ----------------------------
-- Table structure for un_module_role_field
-- ----------------------------
DROP TABLE IF EXISTS `un_module_role_field`;
CREATE TABLE `un_module_role_field`  (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                         `role_id` bigint NOT NULL DEFAULT 0 COMMENT '角色id',
                                         `module_id` bigint NULL DEFAULT NULL COMMENT '所属模块',
                                         `field_id` bigint NULL DEFAULT NULL COMMENT '字段ID',
                                         `auth_type` int NOT NULL DEFAULT 0 COMMENT '授权类型   0不能查看   1只能看 2可以编辑',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                         `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                         `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模块的角色对应字段权限' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_role_field
-- ----------------------------
INSERT INTO `un_module_role_field` VALUES (1, 1, 4, 34, 0, NULL, 0, '2024-09-09 15:31:32', NULL, 1);
INSERT INTO `un_module_role_field` VALUES (2, 1, 4, 35, 1, NULL, 0, '2024-09-09 15:31:32', NULL, 1);
INSERT INTO `un_module_role_field` VALUES (3, 1, 4, 36, 2, NULL, 0, '2024-09-09 15:31:32', NULL, 1);

-- ----------------------------
-- Table structure for un_module_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `un_module_role_menu`;
CREATE TABLE `un_module_role_menu`  (
                                        `id` bigint NOT NULL,
                                        `role_id` bigint NOT NULL COMMENT '角色ID',
                                        `module_id` bigint NULL DEFAULT NULL COMMENT '所属模块',
                                        `menu_id` bigint NOT NULL COMMENT '菜单ID',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                        `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                        `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '模块的角色对应菜单权限' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_role_menu
-- ----------------------------
INSERT INTO `un_module_role_menu` VALUES (1, 1, 4, 1, NULL, 0, '2024-09-09 15:31:28', NULL, 1);
INSERT INTO `un_module_role_menu` VALUES (2, 1, 4, 2, NULL, 0, '2024-09-09 15:31:28', NULL, 1);
INSERT INTO `un_module_role_menu` VALUES (3, 1, 4, 3, NULL, 0, '2024-09-09 15:31:28', NULL, 1);

-- ----------------------------
-- Table structure for un_module_role_user
-- ----------------------------
DROP TABLE IF EXISTS `un_module_role_user`;
CREATE TABLE `un_module_role_user`  (
                                        `id` bigint NOT NULL,
                                        `module_id` bigint NOT NULL COMMENT '模块ID',
                                        `user_id` bigint NOT NULL COMMENT '用户ID',
                                        `role_id` bigint NOT NULL COMMENT '角色ID',
                                        `data_type` int NULL DEFAULT 1 COMMENT '数据权限 1、本人，2、本人及下属，3、本部门，4、本部门及下属部门，5、全部',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                        `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                        `company_id` bigint NULL DEFAULT 1 COMMENT '公司id',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户角色对应关系表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_role_user
-- ----------------------------
INSERT INTO `un_module_role_user` VALUES (1, 2, 1, 1, 1, NULL, 0, '2024-08-28 11:34:29', NULL, 1);

-- ----------------------------
-- Table structure for un_module_user
-- ----------------------------
DROP TABLE IF EXISTS `un_module_user`;
CREATE TABLE `un_module_user`  (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `module_id` bigint NOT NULL COMMENT '模块ID',
                                   `username` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '用户名',
                                   `password` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '密码',
                                   `salt` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '安全符',
                                   `img` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '头像',
                                   `realname` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '真实姓名',
                                   `num` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '员工编号',
                                   `mobile` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '手机号',
                                   `email` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '邮箱',
                                   `sex` int NULL DEFAULT NULL COMMENT '0 未选择 1 男 2 女 ',
                                   `dept_id` bigint NULL DEFAULT NULL COMMENT '部门',
                                   `post` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '岗位',
                                   `status` int NOT NULL DEFAULT 0 COMMENT '状态,0未激活,1正常,2禁用',
                                   `parent_id` bigint NULL DEFAULT 0 COMMENT '直属上级ID',
                                   `deepth` longtext CHARACTER SET utf8 COLLATE utf8_general_ci NULL COMMENT 'parent_id 构建的深度',
                                   `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
                                   `last_login_ip` varchar(30) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '最后登录IP 注意兼容IPV6',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `create_user_id` bigint NOT NULL COMMENT '创建人ID',
                                   `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `update_user_id` bigint NULL DEFAULT NULL COMMENT '修改人ID',
                                   `company_id` bigint NULL DEFAULT 1 COMMENT '企业id',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of un_module_user
-- ----------------------------
INSERT INTO `un_module_user` VALUES (1, 2, 'admin', '47080a72e9de9e49803e8e18f574415d', 'rg51zfbjocnpq0zii4ugmhlnkuf7h5n5', NULL, 'admin', '0001', NULL, NULL, NULL, NULL, NULL, 1, 0, '0,', NULL, NULL, NULL, 0, '2024-09-09 15:31:21', NULL, 1);

SET FOREIGN_KEY_CHECKS = 1;
