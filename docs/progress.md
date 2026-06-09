# 项目进度看板

更新时间：2026-06-08

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 51 |
| 已完成 | 51 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 0 |

当前模式：`development`

当前期次：`P8-platform-ui-crud`

当前状态：`P8_platform_ui_crud_accepted`

## 分期进度

| 期次 | 名称 | 状态 | 完成情况 | 下一步 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | done | 19 个任务已完成 | 作为后续开发基础 |
| P1-generator | 生成器闭环期 | accepted | 3/3 | 已通过 PM 阶段验收 |
| P2-auth-platform | 认证与平台期 | accepted | 2/2 后端主任务，FE-004 静态联调补充完成 | 已通过 PM 阶段验收 |
| P3-system-config | 系统配置与权限期 | accepted | 4/4 后端主任务，FE-005/FE-006 静态契约联调补充完成 | 已通过 PM 阶段验收 |
| P4-runtime-mvp | 运行台 MVP 期 | accepted | 2/2 | 已通过 PM 阶段验收 |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | accepted | 5/5 后端主任务，FE-009/FE-010/FE-012 前端契约闭环完成 | 已通过 PM 阶段验收 |
| P6-final-acceptance | 集成验收与上线判断期 | blocked(frontend-ui) | 原全项目可上线结论已撤回 | 已进入 P7/P8 前端补齐回环 |
| P7-frontend-ui-deploy | 前端真实 UI 与部署包期 | accepted | 前端工程入口、dist、nginx 同源部署和组合 E2E 已完成 | 已通过 PM 阶段验收 |
| P8-platform-ui-crud | 平台中心可用化期 | accepted | FE-014 平台系统/账号/角色/配置 CRUD UI 已完成 | 进入用户试部署体验验收 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 `sql/init.sql` 已完成。 |
| Backend | 15 | 0 | 0 | BE-001 至 BE-015 已完成；OpenAPI accessKey 错误码回环已修复并通过 app 测试。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| Frontend | 13 | 0 | 0 | FE-001 至 FE-014 已完成；P8 已补平台中心真实 CRUD UI，并补齐系统资料/租户真实页面闭环。 |
| Test | 5 | 0 | 0 | TEST-001 至 TEST-005 已完成；测试报告结论 fail target=frontend。 |
| Validator | 4 | 0 | 0 | VAL-001 至 VAL-004 已完成；构建验证结论 fail，target=frontend。 |
| Reviewer | 4 | 0 | 0 | REV-001 至 REV-004 已完成；最终 review fail target=both。 |

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
| backend | BE-015 | done | 后端最终自检 | `mvn -pl examine-web -am test` 通过，`backend/docs/backend-self-check.md` 已记录接口清单、幂等、权限、OpenAPI 和失败摘要。 |
| frontend | FE-005/FE-006 | done | P3 静态契约联调 | 已对齐 BE-005、BE-006、BE-007 当前接口字段、权限点和错误码。 |
| frontend | FE-008 | done | 运行台页面与动态表单联调 | 运行台菜单、schema、记录列表、动态表单、详情、保存/编辑/删除/提交、历史和关系查询页面模型完成。 |
| frontend | FE-009 | done | 流程工作台页面模型 | FLOW-001 至 FLOW-021 页面模型、流程图解释模型、幂等键、本地必填校验、禁用态和契约证据完成。 |
| frontend | FE-010 | done | 文件与导出页面模型 | FILE-001 至 FILE-006、EXP-001 至 EXP-008 页面模型、附件字段写回、导出轮询、重试/取消禁用态和契约证据完成。 |
| frontend | FE-012 | done | 前端契约闭环自检 | 已生成 `frontend/docs/api-contract-map.md` 与 `frontend/docs/frontend-self-check.md`，无旁路请求，typecheck/build 受前端工程入口缺失限制。 |
| frontend | FE-014 | done | 平台中心 CRUD UI | 平台系统、平台账号、平台角色、平台配置已升级为真实业务页面，前端 clean build 与浏览器 smoke 通过。 |
| test | TEST-003 | done | `docs/test_runs/e2e-main-chain.md` | backend API 主链路通过；前端真实浏览器刷新与页面联动因工程入口缺失留待 TEST-005 汇总风险。 |
| test | TEST-004 | done | `docs/test_runs/permission-exception-idempotency-openapi.md` | 未登录、错误凭证、OpenAPI 缺少 AK 和创建系统幂等冲突 smoke 场景通过。 |
| test | TEST-005 | done | `docs/test_report.md` | 后端 API 集成 smoke 通过；整体测试结论 fail，target=frontend。 |
| validator | VAL-001 | done | `docs/build/backend-clean-compile.md` | `mvn -pl examine-web -am clean compile` 通过，8 个 Maven 模块 SUCCESS。 |
| validator | VAL-002 | done | `docs/build/frontend-clean-build.md` | `npm.cmd run build` 失败，缺少 `frontend/package.json`，target=frontend。 |
| validator | VAL-003 | done | `docs/build/contract-sync-check.md` | API ID、核心错误码、状态枚举同步通过；字段类型枚举未同步，target=frontend。 |
| validator | VAL-004 | done | `docs/build_report.md` | 后端 clean compile pass；前端 build 和字段类型同步 fail，target=frontend。 |
| reviewer | REV-001 | done | `docs/review_parts/rev-001-architecture.md` | 前端工程入口缺失和字段类型不同步为 P1；创建系统本机内存幂等为 P2 风险。 |
| reviewer | REV-002 | done | `docs/review_parts/rev-002-contract.md` | 前端 AUTH 鉴权/字段类型同步问题和后端 OpenAPI accessKey 错误码问题，target=both。 |
| reviewer | REV-003 | done | `docs/review_parts/rev-003-quality.md` | 前端构建/E2E 缺失、OpenAPI 负向断言过宽、并发矩阵覆盖不足，target=both。 |
| reviewer | REV-004 | done | `docs/review.json` | 最终 review fail，target=both，nextRoute=backend -> frontend -> test -> validator -> reviewer。 |
| backend | REWORK-OPENAPI-AK | done | OpenAPI accessKey 错误码 | 缺失/无效 accessKey 已改为 `OPENAPI_ACCESS_KEY_INVALID`，`mvn -pl examine-app -am test` 通过。 |
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
| BE-015 | 幂等并发与 API 自检 | done | backend | 后端接口清单、错误码、幂等、权限、OpenAPI 和主要测试命令自检 |
| TEST-003 | E2E 主链路执行 | done | test | backend API 主链路执行记录，包含创建系统、应用模块配置、发布、运行填报、提交和导出 |
| TEST-004 | 权限异常幂等 OpenAPI 测试 | done | test | 未登录、错误凭证、OpenAPI 缺少 AK 和幂等冲突执行记录 |
| TEST-005 | 测试报告 | done | test | 测试报告结论 fail，target=frontend，后端 API smoke 通过但前端 E2E 工程入口缺失 |
| VAL-001 | 后端 clean compile | done | validator | 后端 clean compile 通过，8 个 Maven 模块 SUCCESS |
| VAL-002 | 前端 clean build | done | validator | 返工复验通过：已新增 `frontend/package.json`、`frontend/tsconfig.json` 和 lockfile，`npm.cmd run build` 成功 |
| VAL-003 | 契约同步检查 | done | validator | 返工复验通过：174 个 API ID、核心错误码、14 组状态枚举、19 个字段类型和 AUTH Bearer 标记均同步 |
| VAL-004 | 构建报告 | done | validator | 构建验证结论 fail，target=frontend；后端 clean compile pass，前端工程入口和字段类型同步失败 |
| REV-001 | 架构审查 | done | reviewer | 架构审查结论 fail，target=frontend；另记录 backend 幂等生产级风险 |
| REV-002 | 契约实现审查 | done | reviewer | 契约实现审查结论 fail，target=both；前端 AUTH/字段类型和后端 OpenAPI 错误码不一致 |
| REV-003 | 质量测试构建审查 | done | reviewer | 质量测试构建审查结论 fail，target=both；前端 E2E 缺失，OpenAPI/并发覆盖不足 |
| REV-004 | 最终 review.json | done | reviewer | 最终 review fail，target=both，包含 7 个 issues |

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
18. BE-015 已完成后端最终自检：执行 `mvn -pl examine-web -am test`，core 12、plat 12、upload 4、module 21、flow 2、app 4、web 4 个测试通过，合计 59 个测试；`backend/docs/backend-self-check.md` 已记录接口清单、命令、结果、失败摘要、幂等/权限/OpenAPI 结论和 pass。
19. TEST-003 已完成 backend API 主链路执行：注册、登录、创建系统、进入系统、创建应用、创建模块、创建字段、设置标题字段、保存菜单、发布检查、发布版本、运行态 schema、创建记录、提交记录和创建导出任务均通过；记录见 `docs/test_runs/e2e-main-chain.md`。
20. TEST-004 已完成风险 smoke 场景：未登录内部 API 返回 401、错误凭证返回 `AUTH_INVALID_CREDENTIAL`、OpenAPI 缺少 AK 返回 401、创建系统相同幂等键不同请求体返回 409 `COMMON_IDEMPOTENCY_CONFLICT`；记录见 `docs/test_runs/permission-exception-idempotency-openapi.md`。
21. TEST-005 已完成测试报告：`docs/test_report.md` 结论为 fail，target=frontend；后端 API 集成 smoke 通过，前端正式浏览器 E2E 因工程入口缺失不可执行。
22. VAL-001 已完成后端 clean compile：执行 `mvn -pl examine-web -am clean compile`，8 个 Maven 模块均 SUCCESS；记录见 `docs/build/backend-clean-compile.md`。
23. VAL-002 已完成前端 clean build 验证：执行 `npm.cmd run build` 失败，关键原因是 `frontend/package.json` 不存在；记录见 `docs/build/frontend-clean-build.md`，target=frontend。
24. VAL-003 已完成契约同步检查：174 个 API ID、20 个核心错误码和 15 组状态枚举同步通过；字段类型枚举未同步，记录见 `docs/build/contract-sync-check.md`，target=frontend。
25. VAL-004 已完成构建验证报告：`docs/build_report.md` 结论为 fail，target=frontend；后端 clean compile pass，前端 build 和字段类型同步 fail。
26. REV-001 已完成架构审查：`docs/review_parts/rev-001-architecture.md` 结论为 fail，target=frontend；主要阻塞为前端工程入口缺失和字段类型枚举未同步，另记录创建系统本机内存幂等为 backend P2 风险。
27. REV-002 已完成契约实现审查：`docs/review_parts/rev-002-contract.md` 结论为 fail，target=both；发现前端 AUTH-004/AUTH-005 鉴权标记错误、字段类型枚举未同步，以及后端 OpenAPI accessKey 错误码不符合冻结契约。
28. REV-003 已完成质量测试构建审查：`docs/review_parts/rev-003-quality.md` 结论为 fail，target=both；前端构建/E2E 缺失、OpenAPI 负向断言过宽、OpenAPI/并发矩阵覆盖不足。
29. REV-004 已完成最终 review：`docs/review.json` 合法，status=fail，target=both，包含 7 个 issues，nextRoute 为 backend -> frontend -> test -> validator -> reviewer。
30. Backend 回环已修复 `REV-002-BE-OPENAPI-AK-CODE`：`OpenApiSecurityServiceImpl` 缺失/无效 accessKey 改为 `OPENAPI_ACCESS_KEY_INVALID`，并新增 `OpenApiSecurityServiceImplTest`；执行 `mvn -pl examine-app -am test` 通过，core 13、plat 12、upload 4、module 21、flow 2、app 5。
31. Frontend 回环已修复工程入口、AUTH-004/AUTH-005 鉴权标记和字段类型枚举同步：新增 `frontend/package.json`、`frontend/tsconfig.json`、`frontend/package-lock.json`，`npm.cmd run build` 通过；契约同步复验 174 个 API ID、核心错误码、14 组状态枚举、19 个字段类型和 AUTH Bearer 标记均通过。
32. Test 回环已补强 OpenAPI 安全负向断言：`OpenApiSecurityServiceImplTest` 覆盖缺失/未知 accessKey、timestamp、body hash、signature、scope 和 rate limit 专属错误码；执行 `mvn -pl examine-app -am test` 通过，core 13、plat 12、upload 4、module 21、flow 2、app 11。
33. Validator 回环原记录有误：`npm.cmd ci; npm.cmd run build` 只执行 `tsc --noEmit`，只能证明前端 typed contract 通过，不能证明可部署 UI；`docs/build_report.md` 已修正为 fail，target=pm/frontend。
34. Reviewer 回环原 pass 结论撤回：`docs/review.json` 已修正为 fail，target=both；问题为 PM 验收口径错误、前端无真实浏览器 UI、validator 混淆类型检查与部署构建、缺统一编码规则。
35. P6 阶段验收原 pass 结论撤回：`docs/phases/P6-final-acceptance.md` 已修正为 fail，`docs/phases/development-phases.md` 中 P6 状态为 blocked(frontend-ui)，新增 P7 前端真实 UI 与部署包期。
36. 已补统一编码规则：新增 `.editorconfig` 与 `docs/development/encoding.md`，并将 PM/validator/reviewer 的前端验收口径写入 `AGENTS.md`。
37. 已按可复用到后续项目的口径优化 agent 规则：PM 必须维护交付物矩阵，planner 必须区分 `contract-only` 与 `deployable-ui`，frontend/test/validator/reviewer 必须用真实 UI、浏览器 smoke/E2E 和 `frontend/dist/` 判定完整前端交付。
38. FE-013 已完成前端真实 UI 与可部署产物：新增 `frontend/index.html`、`frontend/src/main.ts`、`frontend/src/App.ts`、`frontend/src/styles.css`、`frontend/vite.config.ts` 和集中式 `frontend/src/api/fetchTransport.ts`；复用既有 typed SDK、路由、状态和 PageModel。
39. 前端 clean build 已通过：`npm.cmd ci` pass，`npm.cmd run build` pass，生成 `frontend/dist/index.html`、`frontend/dist/assets/index-C8i4nSPj.js`、`frontend/dist/assets/index-B9Ede97w.css`。
40. 前端生产预览 smoke 已通过：`http://127.0.0.1:4173/` HTTP 200，Chrome headless 已生成桌面和移动端截图，记录见 `frontend/docs/frontend-ui-smoke.md`。
41. `docs/review.json` 已更新为 P7 当前结论：`backendTrialDeployable=true`、`frontendDeployable=true`、`fullProjectDeployable=false`；剩余阻塞为 TEST-006 前后端组合 E2E。
42. TEST-006 已通过：后端重新打包并启动，前端生产预览通过浏览器触发 `AUTH-002` 登录和 `PLAT-001 /api/v1/platform/my-systems`，页面回显 `COMMON_OK`，记录见 `docs/test_runs/frontend-backend-combo-e2e.md`。
43. 已修复前后端分离 CORS：`backend/examine-web/src/main/java/com/unique/examine/web/config/WebMvcConfig.java` 增加本机和局域网前端预览域放行。
44. P7 已通过验收，完整部署包已生成：`dist/unexamine-full-deploy-20260608-154616.zip`。
45. 用户试部署回归发现旧包仍是调试形态，且 `http://192.168.0.211:19999/` 当前 nginx 会把 `/api/v1/...` 转给后端 `/v1/...`，`/doc.html` 也被前端 index 接管；已修复前端默认同源 `/api/v1/...` 部署语义，并新增 `docs/deploy/nginx-deploy.md`。
46. `platform_admin` 权限回显已修复：`AUTH-005 /me` 现在返回 `platformRoles` 与 `platformPermissions`，前端可识别 `PLAT_SUPER_ADMIN`；默认中文界面规则已写入 `AGENTS.md`，主要导航和路由标题已改为中文。
47. P8 已完成平台中心真实 CRUD UI：系统、账号、角色、配置页面具备列表、创建/编辑、启停、授权、配置更新等入口，且均通过 `platformCenter` typed PageModel 调用。
48. P8 前端 clean build 通过，浏览器 smoke 确认登录页与平台系统页中文无乱码、表单和表格结构正常。
49. 已修复 P8 系统进入权限逻辑：前端进入系统改为调用 `SYS-001` 获取真实系统、租户、成员和有效权限，不再伪造系统上下文；`SYS_MANAGE_ALL` 已在前端映射为系统内管理通配权限。
50. 已修复系统内接口上下文：typed SDK 调用会携带 `X-System-Id` 与 `X-Member-Id`；浏览器实测 `SYS-002` 系统资料查询 200、`SYS-003` 系统资料保存 200、`SYS-004` 租户查询 200。
51. 系统资料和租户页面已从通用占位工作区升级为真实业务页面，进入系统后可看到系统 ID、编码、当前租户、当前成员、权限数和租户列表。
52. 最新部署包：`dist/unexamine-full-deploy-20260608-235808.zip`。
53. 2026-06-09 已本地启动前后端并执行浏览器页面全流程试跑，记录见 `docs/test_runs/local-full-browser-flow-20260609.md`；发现并修复租户停用后无法重新启用的问题，修复后 `SYS-006` 页面回归 200。
54. 最新修复包：`dist/unexamine-full-deploy-20260609-004321.zip`。

## 下一步

当前项目已具备 P8 平台中心和系统资料/租户试部署体验条件。下一步建议在用户部署环境用 `platform_admin / 123123aa` 验证平台系统、账号、角色、配置、系统资料和租户页面；后续继续推进成员、部门、角色、字典、应用模块、运行台、流程、文件、OpenAPI、审计运维的可视化可用化。
# 项目进度看板

## 2026-06-09 PM 最新结论

本机已启动后端 `http://127.0.0.1:9999` 和前端 `http://127.0.0.1:5173`，并按系统功能链路执行验证，记录见 `docs/test_runs/local-full-project-api-flow-20260609.md`。

结论：

- 后端/接口功能链路：pass。AUTH、PLATFORM、SYSTEM、MEMBER/RBAC、DICT、MODULE/RUNTIME、FLOW、FILE、OPENAPI、AUDIT/OPS 共 10 个功能域已通过本机真实接口流。
- 后端构建验证：pass。`mvn.cmd -pl examine-web -am test` 通过，68 个测试通过；`mvn.cmd -pl examine-web -am clean package -DskipTests` 通过。
- 本次真实修复：文件预览/下载响应包装、流程草稿版本非空字段、OpenAPI 长 ID 字符串序列化、页面 schema 首次保存 `schema_json` 非空字段。
- PM 纠偏：不能再把 P8 平台页面验收直接称为“完整系统已完成”。当前后端核心功能可验收；前端完整系统仍需继续补齐成员、部门、角色、字典、应用模块、运行台、流程、文件、OpenAPI、审计运维等真实业务 UI。
- 当前推荐下一阶段：进入“前端完整业务可用化阶段”，由 PM/Planner 重新拆分可视化模块，不再要求用户反复说“继续”来推进同一期目标。

---

## 2026-06-09 开发治理修复

用户指出的问题成立：后续开发不能靠用户反复要求“继续”，也不能由 Orchestrator 直接发现问题、直接交付，绕过原定的多角色问题发现和 PM 裁决机制。

本次已将治理规则升级为硬闸门：

- 新增 `docs/process/development_governance.md`，规定每个开发批次必须经过 Planner 定界、PM 验收口径、多角色提问、PM 裁决、责任方处理、提出方复核、test/validator/reviewer 复验、PM 结论和 Git 提交。
- 更新 `AGENTS.md` 与 `docs/process/development_mode.md`，明确开发批次不能跳过问题闭环，也不能只因用户没有继续追问就停止当前已批准期次。
- 更新 `.codex/agents/pm.toml`、`planner.toml`、`dba.toml`、`backend.toml`、`frontend.toml`、`test.toml`、`validator.toml`、`reviewer.toml`，把 PM、规划、实现、测试、构建和审查职责写进 agent 约束。
- 记录本轮子 agent 调度降级：上一轮尝试唤醒 planner/frontend 子 agent 时返回 `agent not found`，因此这些 agent 的工作不能计为完成；后续必须重新调度或由 Orchestrator 明确接管并记录。

后续 P9/P10/P11/P12 不再直接进入编码。每期开始先生成或更新 development issue 文档，由 PM 裁决后再执行实现；PM 不能决策的问题写入 `docs/issues/user_questions.md` 交给用户。

## 2026-06-09 P9 启动结论

P9 已按新治理流程完成 planner、frontend、backend、test 只读审查，并由 PM 裁决进入 `P9-system-management-ui`。

当前结论：

- P9 范围：成员、部门、系统角色、字典真实业务 UI；不包含应用模块、运行台、流程、文件、OpenAPI、审计运维。
- 当前缺口：`system.members`、`system.departments`、`system.roles`、`system.dict` 路由与 PageModel 已存在，但 `frontend/src/App.ts` 仍落到通用空态面板，不能算真实 UI 完成。
- PM 裁决：`MEM-001` 分页不一致本批次先由前端适配数组返回；`DICT-010/011` 删除必须按冻结 API 传 `version`；P9 必须做浏览器 E2E。
- 已撤回误判：`docs/review.json.fullProjectDeployable` 改为 `false`，P8 只代表平台中心和系统资料/租户局部可试部署，不代表完整系统上线。
- 当前任务：`FE-015` 实现，后续 `TEST-007`、`VAL-005`、`REV-005` 复验。
