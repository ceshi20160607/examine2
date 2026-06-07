# 项目进度看板

更新时间：2026-06-07

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 50 |
| 已完成 | 38 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 12 |

当前模式：`development`

当前期次：`P6-final-acceptance`

当前状态：`P6_final_acceptance_in_progress`

## 分期进度

| 期次 | 名称 | 状态 | 完成情况 | 下一步 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | done | 19 个任务已完成 | 作为后续开发基础 |
| P1-generator | 生成器闭环期 | accepted | 3/3 | 已通过 PM 阶段验收 |
| P2-auth-platform | 认证与平台期 | accepted | 2/2 后端主任务，FE-004 静态联调补充完成 | 已通过 PM 阶段验收 |
| P3-system-config | 系统配置与权限期 | accepted | 4/4 后端主任务，FE-005/FE-006 静态契约联调补充完成 | 已通过 PM 阶段验收 |
| P4-runtime-mvp | 运行台 MVP 期 | accepted | 2/2 | 已通过 PM 阶段验收 |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | accepted | 5/5 后端主任务，FE-009/FE-010/FE-012 前端契约闭环完成 | 已通过 PM 阶段验收 |
| P6-final-acceptance | 集成验收与上线判断期 | in_progress | 1/14 前端契约闭环已完成，BE-015 待执行 | 启动 BE-015 后端最终自检 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 `sql/init.sql` 已完成。 |
| Backend | 14 | 0 | 1 | BE-001 至 BE-014 已完成；BE-015 待最终自检。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| Frontend | 12 | 0 | 0 | FE-001 至 FE-012 已完成；正式 build/typecheck 待 VAL-002 处理前端工程入口缺口。 |
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
| backend | BE-009 | done | 流程模板实例任务 API | FLOW 模板、草稿图、发布检查、模块绑定、流程实例、待办任务、审批动作和运行记录状态联动完成。 |
| backend | BE-010 | done | 上传文件引用 API | FILE 上传、列表、详情、预览、下载、删除、临时文件和引用计数规则完成。 |
| backend | BE-011 | done | 导出任务 API | EXP 导出模板、任务创建、同步生成结果文件、失败重试、取消和任务日志完成。 |
| backend | BE-012 | done | OpenAPI 安全与业务接口 | OPM-001 至 OPM-009、OPN-001 至 OPN-007、AK/SK 签名、nonce、scope、限流、幂等和调用日志完成。 |
| backend | BE-013 | done | 审计运维 API | AUD/OPS 只读入口、OpenAPI 日志桥接、健康检查、配置检查、版本和 migration 状态完成。 |
| frontend | FE-005/FE-006 | done | P3 静态契约联调 | 已对齐 BE-005、BE-006、BE-007 当前接口字段、权限点和错误码。 |
| frontend | FE-008 | done | 运行台页面与动态表单联调 | 运行台菜单、schema、记录列表、动态表单、详情、保存/编辑/删除/提交、历史和关系查询页面模型完成。 |
| frontend | FE-009 | done | 流程工作台页面模型 | FLOW-001 至 FLOW-021 页面模型、流程图解释模型、幂等键、本地必填校验、禁用态和契约证据完成。 |
| frontend | FE-010 | done | 文件与导出页面模型 | FILE-001 至 FILE-006、EXP-001 至 EXP-008 页面模型、附件字段写回、导出轮询、重试/取消禁用态和契约证据完成。 |
| frontend | FE-012 | done | 前端契约闭环自检 | 已生成 `frontend/docs/api-contract-map.md` 与 `frontend/docs/frontend-self-check.md`，无旁路请求，typecheck/build 受前端工程入口缺失限制。 |
| pm | P5 acceptance | done | `docs/phases/P5-workflow-files-openapi-acceptance.md` | P5 已验收通过，允许进入 P6。 |
| pm | P3 acceptance | done | `docs/phases/P3-system-config-acceptance.md` | P3 已验收通过，允许进入 P4。 |

## 当前期任务

| taskId | 名称 | 状态 | 负责人 | 输出 |
| --- | --- | --- | --- | --- |
| BE-009 | 流程模板实例任务 API | done | backend | FLOW-001 至 FLOW-021 后端入口、模板发布、模块绑定、流程实例、待办、审批动作和运行记录状态联动 |
| BE-010 | 上传文件引用 API | done | backend | 临时文件、文件引用、预览下载权限和失败补偿 |
| BE-011 | 导出任务 API | done | backend | 导出模板、任务、重试和结果文件闭环 |
| BE-012 | OpenAPI 安全与业务接口 | done | backend | OPM/OPN 管理与外部接口、AK/SK、签名、scope、IP、限流、幂等和调用日志闭环 |
| BE-013 | 审计运维 API | done | backend | AUD/OPS 审计查询、OpenAPI 日志桥接、健康、配置、版本和 migration 状态 |
| FE-009 | 流程工作台页面 | done | frontend | 流程模板、待办、抄送、我的申请、实例详情、流程图、审批历史和任务处理页面模型 |
| FE-010 | 文件与导出页面 | done | frontend | 上传、文件中心、预览下载、导出模板、导出任务、轮询、重试、取消和结果文件下载页面模型 |
| FE-011 | OpenAPI 审计运维页面 | done | frontend | OpenAPI、审计和运维静态页面模型已在 P0/P3 补充 |
| FE-012 | 前端自检与契约闭环 | done | frontend | API 契约映射汇总、页面证据汇总、枚举/幂等/错误码同步和旁路请求扫描 |
| BE-015 | 幂等并发与 API 自检 | pending | backend | 后端接口清单、错误码、幂等、权限、OpenAPI 和主要测试命令自检 |

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
4. FE-008 已完成运行台页面模型和契约证据；前端目录仍无 `package.json`/`tsconfig.json`，正式 build/typecheck 不可用。
5. 验收记录：`docs/phases/P4-runtime-mvp-acceptance.md`。

### P5

1. BE-009 已完成流程后端 MVP：流程模板创建/查询、草稿图保存/读取、发布检查、发布、模块绑定、实例详情、实例图、历史、待办、领取/取消领取、同意/拒绝/退回/转交/终止/撤回。
2. BE-009 已将 BE-008 的提交流程占位策略替换为真实流程实例创建：模块绑定流程时创建流程实例和首个审批任务，并回写记录 `flowInstanceId`、`IN_APPROVAL` 和锁定状态；未绑定流程仍直接进入 `SUBMITTED`。
3. 已执行 `mvn -pl examine-flow -am test`，core 12、plat 12、module 18、flow 2 个测试通过。
4. BE-010 已完成上传文件后端 MVP：文件上传、列表、详情、预览、下载、删除、临时文件、引用绑定/解绑服务、引用计数和已引用删除限制。
5. 已执行 `mvn -pl examine-upload -am test`，core 12、plat 12、upload 4 个测试通过。
6. BE-011 已完成导出后端 MVP：导出模板创建/更新/列表、导出任务创建/列表/详情/重试/取消、任务状态日志、字段/权限/筛选快照和同步结果文件生成。
7. 已执行 `mvn -pl examine-module -am test`，core 12、plat 12、upload 4、module 21 个测试通过。
8. BE-012 已完成 OpenAPI 后端 MVP：客户端管理、凭证一次性展示/轮换、scope、IP 白名单、调用日志、canonical request、body hash、timestamp、nonce、签名、scope、限流、幂等状态更新，以及外部记录/流程/文件接口转发。
9. 已执行 `mvn -pl examine-app -am test`，core 12、plat 12、upload 4、module 21、flow 2、app 4 个测试通过。
10. BE-013 已完成审计运维后端 MVP：AUD-001 至 AUD-008、OPS-001 至 OPS-004 和 OPS-006 已落地；OPS-005 属 ENH 写配置接口，按本期只读约束未实现。
11. 已执行 `mvn -pl examine-web -am test`，core 12、plat 12、upload 4、module 21、flow 2、app 4、web 4 个测试通过。
12. FE-009 已完成流程工作台前端页面模型：覆盖 FLOW-001 至 FLOW-021、流程图解释模型、冻结幂等清单、拒绝/退回/终止原因必填、转交成员必填、领取/取消领取/撤回禁用态和页面契约证据。
13. 已执行旁路请求静态检查，`frontend/src/pages/flow/` 未新增 `fetch`、`axios`、`XMLHttpRequest` 或手写 URL；当前前端目录仍无 `package.json`/`tsconfig.json`，正式 build/typecheck 不可用。
14. FE-010 已完成文件与导出前端页面模型：覆盖 FILE-001 至 FILE-006、EXP-001 至 EXP-008、动态字段附件写回、预览/下载/删除禁用态、导出任务轮询建议、失败重试/取消禁用态和页面契约证据。
15. 已执行旁路请求静态检查，`frontend/src/pages/files/` 与 `frontend/src/pages/export/` 未新增 `fetch`、`axios`、`XMLHttpRequest` 或手写 URL；当前前端目录仍无 `package.json`/`tsconfig.json`，正式 build/typecheck 不可用。
16. FE-012 已完成前端契约闭环：`frontend/docs/api-contract-map.md` 汇总 174 个 SDK 端点、26 条路由映射和 FE-002 至 FE-011 页面证据；真实 API ID 均能在 `API_ENDPOINTS` 中找到。
17. FE-012 已执行源码旁路请求扫描，`frontend/src` 未发现 `fetch`、`axios`、`XMLHttpRequest`、`new Request` 或硬编码 URL；`frontend/docs/frontend-self-check.md` 记录 typecheck/lint/build 因缺少 `package.json`、`tsconfig.json` 和 `tsc` 被阻塞。

## 下一步

当前 `P5-workflow-files-openapi` 已通过 PM 阶段验收，当前进入 `P6-final-acceptance`。下一步直接执行 BE-015 后端最终自检，然后进入 TEST/VAL/REV 最终验收链路。
