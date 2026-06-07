# 项目进度看板

更新时间：2026-06-07

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 50 |
| 已完成 | 29 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 21 |

当前模式：`development`

当前期次：`P4-runtime-mvp`

当前状态：`P4_runtime_mvp_in_progress`

## 分期进度

| 期次 | 名称 | 状态 | 完成情况 | 下一步 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | done | 19 个任务已完成 | 作为后续开发基础 |
| P1-generator | 生成器闭环期 | accepted | 3/3 | 已通过 PM 阶段验收 |
| P2-auth-platform | 认证与平台期 | accepted | 2/2 后端主任务，FE-004 静态联调补充完成 | 已通过 PM 阶段验收 |
| P3-system-config | 系统配置与权限期 | accepted | 4/4 后端主任务，FE-005/FE-006 静态契约联调补充完成 | 已通过 PM 阶段验收 |
| P4-runtime-mvp | 运行台 MVP 期 | in_progress | 1/2 | 下一步执行 FE-008 运行台页面联调 |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | pending | 0/5 后端主任务，前端待联调 | 等 P4 通过 |
| P6-final-acceptance | 集成验收与上线判断期 | pending | 0/14 | 等 P5 通过 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 `sql/init.sql` 已完成。 |
| Backend | 9 | 0 | 6 | BE-001 至 BE-008、BE-014 已完成；BE-009 至 BE-015 待按期推进。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| Frontend | 8 | 0 | 4 | FE-001 至 FE-007、FE-011 已完成；FE-008 至 FE-010、FE-012 待执行。 |
| Test | 2 | 0 | 3 | TEST-001、TEST-002 已完成；最终场景测试待后续期。 |
| Validator | 0 | 0 | 4 | 等阶段或最终构建验证。 |
| Reviewer | 0 | 0 | 4 | 等测试和构建产物。 |

## 当前 Agent 状态

| agent | taskId | 状态 | 输出 | 备注 |
| --- | --- | --- | --- | --- |
| backend | GEN-002 | done | 生成器命令参数 | 已改为命令即配置，不再维护表映射文件。 |
| backend | GEN-003 | done | `backend/examine-generator/src/main/resources/templates/base/` | base 模板策略与自定义模板已落地。 |
| backend | GEN-004 | done | 各模块 `base/` 包 | 已执行 MyBatis-Plus 生成，后端 compile 通过。 |
| backend | BE-003 | done | 认证会话接口 | 注册、登录、刷新、退出、当前用户接口完成。 |
| backend | BE-004 | done | 平台中心接口 | 平台账号、平台角色、系统创建、状态、配置和权限目录接口完成。 |
| backend | BE-014 | done | 权限与数据范围 | 统一权限服务、字段权限、数据范围和 OpenAPI scope 校验完成。 |
| backend | BE-005 | done | 系统成员 RBAC | 系统上下文、租户、成员、部门、角色、授权目录和有效权限接口完成。 |
| backend | BE-006 | done | 字典管理 | 字典类型、字典项、使用情况、引用限制和缓存版本刷新接口完成。 |
| backend | BE-007 | done | 应用模块字段页面配置 | APP、MOD、FIELD、UI、发布检查和发布版本生成接口完成。 |
| backend | BE-008 | done | 运行台记录 CRUD 与动态数据 API | RUN-001 至 RUN-010 后端入口、运行态 schema、记录增删改查、提交、历史和关系查询完成；流程提交待 BE-009 接入真实流程实例。 |
| frontend | FE-005/FE-006 | done | P3 静态契约联调 | 已对齐 BE-005、BE-006、BE-007 当前接口字段、权限点和错误码。 |
| pm | P3 acceptance | done | `docs/phases/P3-system-config-acceptance.md` | P3 已验收通过，允许进入 P4。 |

## 当前期任务

| taskId | 名称 | 状态 | 负责人 | 输出 |
| --- | --- | --- | --- | --- |
| BE-008 | 运行台记录 CRUD 与动态数据 API | done | backend | RUN-001 至 RUN-010、运行台菜单/schema、记录查询/保存/详情/删除/提交/历史/关联能力 |
| FE-008 | 运行台页面与动态表单联调 | pending | frontend | 运行台菜单、记录列表、动态表单、详情和保存/编辑/删除交互 |

## 阶段验收摘要

### P1

1. `mvn -DskipTests compile` 后端完整编译通过。
2. `sql/init.sql` 已调整为 `examine1` 库，83 张表均为 `DROP TABLE IF EXISTS` 后 `CREATE TABLE`。
3. 已通过 JDBC 将 `sql/init.sql` 执行到 `192.168.0.211:3306/examine1`。
4. `examine-generator` 已升级为命令驱动，不再维护映射文件或默认生成报告。
5. 83 个 base service 均由 `examine-generator` 自动生成基础 CRUD。

### P2

1. 验收记录：`docs/phases/P2-auth-platform-acceptance.md`。
2. FE-004 已按 BE-004 当前响应修正平台页面字段映射，并补齐 PLAT-013 至 PLAT-020 的 Bearer 与权限点。
3. 前端目录无 `package.json` 或可执行构建入口，本期只完成静态契约核对。

### P3

1. 验收记录：`docs/phases/P3-system-config-acceptance.md`。
2. BE-014、BE-005、BE-006、BE-007 均已完成并记录后端测试与 clean compile 结果。
3. FE-005/FE-006 已补充静态契约联调：RBAC/DICT 权限点、FIELD 类型词表、模块配置 VO/BO、发布检查与发布入参已对齐当前后端。
4. `git diff --check` 通过，仅提示 Git 工作区 LF/CRLF 转换 warning。

### P4

1. BE-008 已完成运行台后端基础能力：运行台菜单、模块运行态 schema、记录查询、创建、详情、更新、软删除、提交、历史和关联关系查询。
2. 提交流程当前采用占位策略：模块已绑定流程时进入 `IN_APPROVAL` 并锁定，未绑定流程时直接进入 `SUBMITTED`；BE-009 接入流程引擎后替换为真实流程实例创建。
3. 已执行 `mvn -pl examine-module -am test`，core 12、plat 12、module 18 个测试通过。

## 下一步

当前进入 `P4-runtime-mvp`，BE-008 已完成并按任务边界提交；下一步执行 FE-008 运行台页面联调。P4 全部完成后再进行阶段验收并提交。
