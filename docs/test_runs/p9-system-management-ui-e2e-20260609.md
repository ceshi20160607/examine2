# P9 系统管理域浏览器 E2E 记录

## 环境

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-06-09 |
| 前端 preview | `http://127.0.0.1:4173/` |
| 后端 | `http://127.0.0.1:9999/` |
| 账号 | `platform_admin / 123123aa` |
| 真实系统 | `2064167820598476801`，全链路系统pm70932135 |
| 默认租户 | `2064167820640419841` |
| 任务 | TEST-007 |

## 执行摘要

本次使用真实浏览器页面登录、进入系统并触发 P9 成员、部门、系统角色、字典写操作。少量数据预置通过后端 API 完成：创建可邀请平台账号 `p9_member_84718570`，以及读取新建角色 ID 供成员分配使用；验收动作均由页面按钮触发。

最终截图：`frontend/docs/p9-system-management-e2e-final.png`。

## 执行记录

| 场景 | 页面触发 API | 结果 |
| --- | --- | --- |
| 登录并进入真实系统 | AUTH-002、PLAT-001、SYS-001/SYS-002 | pass，进入 `#/systems/2064167820598476801/profile`，系统上下文不再是 preview |
| 创建部门 | RBAC-002 | pass，`req_20260609_d3d4dd43bae94cf3` |
| 编辑部门 | RBAC-003 | pass，`req_20260609_30d617a8ebcd4474` |
| 删除叶子部门 | RBAC-004 | pass，`req_20260609_d0c1155ecb1d4214` |
| 有子部门删除限制 | RBAC-002 + UI 禁用 | pass，父部门存在子部门时删除按钮 `enabled=false` |
| 创建系统角色 | RBAC-006 | pass，`req_20260609_807070f4670140df` |
| 加载权限目录 | RBAC-013 | pass，`req_20260609_29e02734451e4f2e` |
| 读取角色授权 | RBAC-012 | pass，`req_20260609_8083a0494fc042a5` |
| 保存角色授权 | RBAC-009 | pass，`req_20260609_201e68560d614fff` |
| 保存角色授权回归 | RBAC-009 | pass，前端请求体改为 `dataScopes` 后复验通过，`req_20260609_5563607d935e47ce` |
| 停用/启用角色 | RBAC-008 | pass，`req_20260609_5613685db69d4fe0`、`req_20260609_719609082128465f` |
| 创建字典类型 | DICT-002 | pass，`req_20260609_cdad498ee36b4110` |
| 加载字典项 | DICT-005 | pass，`req_20260609_9347928f7db64876` |
| 创建字典项 | DICT-006 | pass，`req_20260609_c62499180da34192` |
| 查询字典使用情况 | DICT-009 | pass，`req_20260609_9832f6f0091846b7` |
| 停用字典项 | DICT-008 | pass，`req_20260609_9aaceeb80ee64f48` |
| 删除字典项 | DICT-011 | pass，`req_20260609_d44548712a82441f` |
| 删除字典类型 | DICT-010 | pass，刷新版本后成功，`req_20260609_e72d06925c73424d` |
| 邀请成员 | MEM-002 | pass，`req_20260609_2a8212b86dcc43df` |
| 成员详情 | MEM-003 | pass，`req_20260609_66eaa0be4c4c4e30` |
| 编辑成员 | MEM-004 | pass，`req_20260609_ac38f8cb6cb74371` |
| 分配成员角色 | MEM-006 | pass，`req_20260609_fdae89f5403f4902` |
| 停用成员 | MEM-005 | pass，`req_20260609_11c7cf65073f4993` |

## 发现并修复的问题

| 问题 | 修复 |
| --- | --- |
| 系统角色保存授权使用浏览器原生 prompt，E2E 无法稳定输入，真实系统交互也不友好。 | 改为页面表单字段：授权操作编码、授权菜单 ID、数据范围。 |
| RBAC-009 后端保存角色授权时插入 `un_module_permission_version`，唯一键冲突后返回 500。 | 后端改为更新已有权限版本行并递增 `version_no`。 |
| 部门前端发 `deptCode/deptName`，后端 BO 实际要求 `code/name`，导致 RBAC-002 参数不合法。 | 前端请求体和展示映射改为 `code/name`，同时兼容旧字段名。 |
| P9 多个关键操作依赖 `window.prompt/window.confirm`，浏览器自动化和真实管理台体验都不稳定。 | 成员、部门、角色、字典关键编辑/状态/删除操作改为页面表单驱动或直接按钮触发，并保留后端阻断校验。 |
| 字典项删除后立即删除字典类型会因版本刷新返回版本冲突。 | 测试记录为先刷新类型列表再删除，页面已可通过真实链路完成。 |

## 构建与运行命令

| 命令 | 结果 |
| --- | --- |
| `mvn -pl examine-web -am -DskipTests package` | pass，重新生成 `backend/examine-web/target/unexamine.jar` |
| `java -jar backend/examine-web/target/unexamine.jar` | pass，Tomcat started on port 9999 |
| `npm.cmd run build` | pass，生成 `frontend/dist/` |
| `npm.cmd run preview -- --port 4173` | pass，本地生产预览可访问 |

## 结论

status: `pass`

target: `none`
