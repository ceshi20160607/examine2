# 项目进度看板

## 2026-06-11 当前更正状态

用户反馈成立：P13 只跑通了系统内业务应用、模块、字段、发布和运行台新增记录链路，不能证明原型中的平台级对外应用/外部应用流程已经正确实现。

PM 已撤回 `P13-usability-rework` 的完整可用结论：

- 当前期次：`P14-prototype-concept-rework`
- 当前状态：`P14_frontend_p0_rework_and_api_e2e_partial_pass`
- 当前阻塞：浏览器点击流 E2E、P14 reviewer 复审、最终打包闸门
- 当前结论：`fullProjectDeployable=false`
- 打包策略：P14 术语、原型流程、UI/前端和测试闭环前，不再生成新的“最终可用”部署包。

P14 已升级为完整系统基线重整，基线文件：`docs/product/integrated-system-baseline.md`。

P14 目标：

1. 冻结术语表，区分平台级对外应用、系统内业务应用、模块、OpenAPI 客户端。
2. 生成原型流程追踪矩阵，把原始流程图映射到页面、路由、接口、权限、状态和 E2E。
3. 基于完整系统剧本重新设计 UI/UX，不再按割裂模块局部验收。
4. 基于冻结后的 UI/UX 重新调整前端入口和页面文案。
5. 用真实浏览器连续验证“登录 -> 创建系统 -> 建模块字段 -> 发布 -> 运行台使用 -> 创建对外应用 -> 授权系统数据/能力 -> 外部调用 -> 调用日志/审计”的完整链路。

更新时间：2026-06-11 19:45

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 69 |
| 已完成 | 69 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 0 |

当前模式：`development`

当前期次：`P12-uiux-frontend-rework`

当前状态：`P12_accepted_final_package_ready`

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
| P8-platform-ui-crud | 平台中心可用化期 | accepted | FE-014 平台系统/账号/角色/配置 CRUD UI 已完成 | 已进入 P9 |
| P9-system-management-ui | 系统管理域可用化期 | accepted | FE-015、TEST-007、VAL-005、REV-005 已完成；成员、部门、系统角色、字典真实浏览器写操作 E2E 通过 | 已进入 P10 |
| P10-app-runtime-ui | 应用模块与运行台可用化期 | accepted | FE-016、FE-017、FE-018、TEST-008、VAL-006、REV-006 已完成；应用、模块、字段、页面配置、发布和运行台记录浏览器 E2E 通过 | 下一期进入 P11 流程、文件导出、OpenAPI 与审计运维可用化 |
| P11-flow-file-openapi-ui | 流程、文件导出、OpenAPI 与审计运维可用化期 | accepted | FE-019 至 FE-022、TEST-009、VAL-007、REV-007 已完成；功能试部署包已生成 | 用户反馈 UI 体验不足，进入 P12 |
| P12-uiux-frontend-rework | UI/UX 设计与前端可用化改造期 | accepted | UIUX-001、UIUX-002、FE-023、FE-024、TEST-010、VAL-008、REV-008、PKG-001 已完成 | 最终部署包已生成 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 `sql/init.sql` 已完成。 |
| Backend | 15 | 0 | 0 | BE-001 至 BE-015 已完成；OpenAPI accessKey 错误码回环与 P9 权限版本递增修复已验证。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| UI/UX | 2 | 0 | 0 | UIUX-001/UIUX-002 已完成，P12 UI 设计和页面级原型已输出。 |
| Frontend | 24 | 0 | 0 | FE-001 至 FE-024 已完成；业务域页面已按 UI 设计改造，待 test/validator/reviewer 复验。 |
| Test | 10 | 0 | 0 | TEST-001 至 TEST-010 已完成；TEST-010 真实浏览器复测通过。 |
| Validator | 8 | 0 | 0 | VAL-001 至 VAL-008 已完成；P12 clean build/package 通过。 |
| Reviewer | 8 | 0 | 0 | REV-001 至 REV-008 已完成；P12 UI/UX 与最终可用性审查通过。 |

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
| frontend | FE-016 | done | 应用与模块配置真实 UI | 应用/模块真实列表、创建、编辑、状态和上下文选择已完成。 |
| frontend | FE-017 | done | 字段页面配置与发布真实 UI | 字段、页面 schema、菜单动作、发布检查和发布已完成。 |
| frontend | FE-018 | done | 运行台记录真实 UI | 运行台菜单、schema、记录新建、详情、编辑、历史和提交入口已完成。 |
| test | TEST-008 | done | P10 应用配置到运行台浏览器 E2E | 浏览器页面真实写操作通过，记录见 `docs/test_runs/p10-app-runtime-ui-e2e-20260609.md`。 |
| validator | VAL-006 | done | P10 clean build 与后端 package | 前端 build、后端 compile/package 通过，记录见 `docs/build/p10-clean-build.md`。 |
| reviewer | REV-006 | done | P10 应用运行台审查 | P10 pass，完整项目仍需 P11/P12，记录见 `docs/issues/verification/development/p10_reviewer_verification.md`。 |
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

P10 已通过验收，最新部署包为 `dist/unexamine-full-deploy-20260609-162432.zip`。下一期进入 P11 流程、文件导出、OpenAPI 与审计运维可用化。P10 通过后仍不能声明完整系统可上线，后续还需 P11/P12 可视化可用化期次。
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

### FE-015 当前实现状态

- 已实现成员、部门、系统角色、字典真实 UI 渲染入口，四个路由不再走通用空态面板。
- 已按 PM 裁决兼容 `MEM-001` 数组返回，并按冻结 API 为 `DICT-010/011` 删除传 `version`。
- 已执行 P9 前端 clean build：`npm.cmd ci` pass，`npm.cmd run build` pass。
- 当前仍未判定 P9 完成：`TEST-007` 已完成 UI smoke 截图，但真实后端写操作 E2E 未完成；`VAL-005` clean build 已记录，`REV-005` 审查尚未完成。
- validator 只读复核结论：`VAL-005` 可按 clean build 窄口径判定 done；不能据此宣称 `TEST-007` pass、P9 accepted 或完整系统可上线。
- reviewer 只读复核结论：P9 仍不可 accepted；已修复角色页 `RBAC-013` 加载权限目录按钮权限禁用缺口，并把 `docs/review.json` 的过期空壳结论改为真实 TEST-007/REV-005 阻塞。

## 2026-06-09 P9 验收结论

P9 已完成并通过：成员、部门、系统角色、字典均已从通用占位页升级为真实业务 UI，并通过真实浏览器写操作 E2E。记录见 `docs/test_runs/p9-system-management-ui-e2e-20260609.md`。

本轮真实修复：

- 前端系统角色授权从原生 prompt 改为页面表单字段，`RBAC-009` 可由真实页面稳定触发。
- 后端权限版本递增从插入新行改为更新既有 `un_module_permission_version` 行，修复 `RBAC-009` 重复唯一键 500。
- 前端部门接口字段从 `deptCode/deptName` 修正为后端 `code/name`，并兼容旧字段显示。
- 成员、部门、角色、字典关键操作移除原生 prompt/confirm 依赖，改为页面表单和按钮驱动。

当前 PM 结论：P9 accepted；`fullProjectDeployable=false` 继续保持，完整系统还需 P10 应用/模块/运行台、P11 流程/文件/导出、P12 OpenAPI/审计运维等可视化阶段通过。
## 2026-06-09 P10 部署回归热修复

用户部署回归发现“创建系统后进入系统、创建模块后模块不可见/不可用”的主流程问题。已按真实部署链路复现并修复：

- 根因 1：`SYS-001` 进入系统接口在空 POST 或表单 Content-Type 请求下触发 Spring `HttpMediaTypeNotSupportedException`，被统一异常包装为 500，导致系统上下文、租户、成员和权限无法稳定建立。
- 根因 2：前端进入系统在无租户入参时可能不发送 JSON body，增加了触发后端 Content-Type 兼容问题的概率。
- 根因 3：前端自动加载缓存只按路由名记录，切换系统/应用/模块后同名页面可能不重新拉取数据，表现为新建模块后页面仍旧或为空。
- 修复结果：后端 `SYS-001` 已兼容 JSON、表单和空请求体；前端 `SYS-001` 调用统一发送 `{}`；前端自动加载缓存改为按路由路径和成员上下文区分。
- 验证结果：本地新后端连接 `192.168.0.211:3306/examine1` 后，登录 `platform_admin / 123123aa`、进入新建系统、读取默认应用、创建模块、读取模块列表均通过；浏览器打开本地前端后可进入真实系统，应用页显示默认应用，模块页显示模块列表。
- 构建结果：`mvn -pl examine-web -am -DskipTests compile` 通过，`mvn -pl examine-web -am -DskipTests package` 通过，`npm.cmd run build` 通过。
- 最新部署包：`dist/unexamine-full-deploy-20260609-211928.zip`。

## 2026-06-10 P11 验收结论

P11 已完成并通过验收：流程、文件导出、OpenAPI、审计运维均已从占位/契约模型升级为真实可用 UI，并通过本地前后端浏览器 smoke 与关键写链路验证。

本轮真实修复：
- 流程模板默认图保存、发布检查契约、发布和模块绑定链路修复并通过浏览器验证。
- 文件上传改为正确处理 FormData，文件上传后可在文件中心回显。
- 导出页进入系统后会重置跨系统 app/module/field 状态，并强制刷新模块发布状态。
- 导出页只允许已发布模块创建导出模板和任务，避免 DRAFT 模块触发后端运行态错误。
- 后端导出任务在未传 filters/sorter 时默认保存 `[]`，修复 `filter_snapshot_json` 非空约束导致的 500。
- OpenAPI 客户端创建/更新对齐后端 BO：scope、IP 白名单和限流策略均按结构化对象提交。

验证结果：
- `npm.cmd run build` pass，生成 `frontend/dist/`。
- `mvn -pl examine-web -am -DskipTests compile` pass。
- `mvn -pl examine-web -am -DskipTests package` pass，生成 `backend/examine-web/target/unexamine.jar`。
- 浏览器 route smoke 通过：`flow.templates`、`flow.workbench`、`files.center`、`exports.jobs`、`openapi.clients`、`audit.system`、`audit.platform`、`ops.health`。
- 导出任务直连和页面创建均返回 `SUCCESS`，并返回结果文件 ID。
- 最新试部署包：`dist/unexamine-full-deploy-20260610-174753.zip`，后端目录已包含 `start.sh`，支持 `start`、`stop`、`sotp`、`restart`、`status`。

PM 结论：P11 accepted。当前完整系统已具备用户试部署条件；生产增强类风险（数据库幂等存储、OpenAPI 高并发/nonce/IP 白名单矩阵、依赖审计治理）继续跟踪。

## 2026-06-10 P12 UI/UX 纠偏启动

用户反馈成立：当前前端已能连接真实接口并通过功能 smoke/E2E，但缺少独立 UI/UX 设计阶段，页面更接近工程型接口操作台，不能代表最终用户可正常使用的产品体验。

PM 已做如下调整：

- 新增 `docs/ui/ui-design.md`，冻结信息架构、双层工作台、核心流程、页面框架、组件规范、状态反馈、中文文案和 P12 验收标准。
- 新增 `docs/phases/P12-uiux-frontend-rework-plan.md`，P12 任务顺序为 UIUX-001 -> FE-023 -> FE-024 -> TEST-010 -> VAL-008 -> REV-008。
- 新增 P12 任务文件：`UIUX-001`、`UIUX-002`、`FE-023`、`FE-024`、`TEST-010`、`VAL-008`、`REV-008`、`PKG-001`。
- `docs/review.json` 已撤回最终用户体验完成结论：P11 包保留为功能试部署包，P12 通过前 `fullProjectDeployable=false`。
- 已按用户要求修正打包策略：FE-024、TEST-010、VAL-008、REV-008 未全部通过前，不再生成新部署包；PKG-001 只有最终验收通过后才允许执行。
- UIUX-002 已完成：新增 `docs/ui/prototypes/page-prototypes.md`，为 FE-024 提供页面级原型。
- FE-023 已完成：新增系统总览路由，进入系统后默认落到总览；侧栏按平台/系统分层；系统总览展示配置进度、推荐路径和快捷入口。
- FE-023 验证：`npm.cmd run build` pass，生产预览 `/` 与 `/#/systems/demo/overview` HTTP 200；截图级浏览器 E2E 留给 TEST-010。
- FE-024 已完成：应用配置步骤工作台、运行台详情 Tabs、流程工作台 Tabs、文件上传卡片、导出发布状态提示、OpenAPI 接入卡片、审计检索和运维巡检提示已落地。
- FE-024 验证：`npm.cmd run build` pass，生产预览 `/` 与 `/#/systems/demo/overview` HTTP 200。
- REV-008 预审发现 FE-024 第一轮仍有 prompt、静态 Tabs 和授权输入可用性缺口，PM 暂停 TEST-010/VAL-008 并返工。
- FE-024 返工完成：我的系统显式进入按钮、平台账号/角色页面内编辑授权、系统角色权限多选、有状态 Tabs 已落地；`window.prompt` 扫描无结果，`npm.cmd run build` pass。

当前下一步：TEST-010 已由 Orchestrator 接管并用本机 Chrome CDP 完成真实浏览器复测；VAL-008 进入待执行，REV-008 和 PKG-001 继续受闸门控制。

## 2026-06-10 FE-024 完成记录

- 前端完成应用配置步骤工作台、运行台详情 Tabs、流程工作台 Tabs、文件上传卡片、导出发布状态提示、OpenAPI 接入卡片、审计检索和运维巡检提示。
- 根据 reviewer 预审完成返工：去除 `window.prompt`、补显式进入按钮、页面内编辑/授权表单、权限多选和真实可切换 Tabs。
- 自检命令：`npm.cmd run build`，结果 pass。
- 生产预览：`/` 与 `/#/systems/demo/overview` HTTP 200。
- 当前只代表 frontend 任务完成；TEST-010、VAL-008、REV-008 未通过，`docs/review.json.fullProjectDeployable=false`，禁止生成最终部署包。

## 2026-06-10 TEST-010 当前阻塞

- test agent 019eb17a 初次验证完成 FE-024 返工点 DOM/源码断言，但真实后端链路因 JVM native memory 启动失败阻塞。
- PM 已定位启动方案：`java -Xms64m -Xmx384m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Dserver.port=18080 -jar backend/examine-web/target/unexamine.jar`，健康检查可返回 200。
- test agent 019eb17a 恢复全量 E2E 后长时间未回收；PM 已关闭，不能计为完成。
- test agent 019eb1a7 重新调度后仍超时，PM 已关闭，不能计为完成。
- `docs/test_runs/p12-ui-usable-e2e.md` 当前只证明 FE-024 返工点和前端 preview DOM 通过；真实登录、创建系统、应用发布、运行填报、审批、导出、审计主链路仍未完成。
- VAL-008、REV-008、PKG-001 继续阻塞，不允许打包。
- 后续 TEST-010 真实链路曾推进到登录、创建系统、进入系统、角色配置、应用/模块/字段/页面发布和导出成功，但发现运行台 UI 新建记录、记录查询、权限多选展示问题。
- frontend 已修复上述三个问题并通过 `npm.cmd run build`；TEST-010 状态调整为 `pending-retest`，仍需重新执行真实浏览器全链路，未通过前继续禁止 VAL-008/REV-008/PKG-001。

## 2026-06-11 TEST-010 复测通过

- PM/Orchestrator 接管不可回收的 test agent 后，使用本机 Chrome headless DevTools Protocol 执行真实浏览器 DOM 操作。
- 登录 `platform_admin / 123123aa`，从“我的系统”进入 P12 系统 `2064692771705384961`。
- 运行台模块 `2064693917962530818` 加载成功，列表可渲染历史记录，无 `undefined.find` 异常。
- 新建记录 `P12 UI retest 20260610172945` 后，记录出现在 `.data-row.records` 列表行中。
- 查询该记录后仍可见；点击提交后记录状态变为 `SUBMITTED`，页面显示 `RUN-008 操作成功：COMMON_OK`。
- 流程工作台刷新成功，`FLOW-007/FLOW-013/FLOW-014/FLOW-017` 返回成功。
- 系统角色权限目录下拉未再出现 `[object Object]`。
- TEST-010 结论：pass。下一步执行 VAL-008 clean build；未通过 VAL-008/REV-008 前仍不允许打包。

## 2026-06-11 VAL-008 Clean Build 通过

- 前端已删除旧 `frontend/dist/` 后执行 `npm.cmd run build`，结果 pass，重新生成 `frontend/dist/index.html`、CSS 和 JS 产物。
- 后端执行 `mvn.cmd -pl examine-web -am clean package -DskipTests`，结果 pass，8 个 Maven 模块 SUCCESS，重新生成 `backend/examine-web/target/unexamine.jar`。
- 构建报告：`docs/build/p12-clean-build.md`。
- 本轮未生成新的 `dist/unexamine-full-deploy-*.zip`。
- 下一步进入 REV-008；REV-008 未通过前，PKG-001 继续阻塞，不允许最终打包。

## 2026-06-11 REV-008 审查通过

- Reviewer 对照 `docs/ui/ui-design.md`、页面原型、FE-024 页面契约、TEST-010 复测记录和 VAL-008 构建报告完成审查。
- `frontend/src` 中 `window.prompt` / `prompt(` 扫描无结果。
- P12 P1 问题已闭环：UI/UX 设计闸门、业务域页面改造、真实浏览器 E2E、clean build/package 均通过。
- `docs/review.json.status=pass`，`fullProjectDeployable=true`。
- 保留的幂等存储、npm audit、OpenAPI 高并发/安全矩阵为 P2 deferred 风险，不阻塞当前最终包生成。
- 下一步执行 PKG-001，生成最终部署包。

## 2026-06-11 PKG-001 最终部署包完成

- 最终包目录：`dist/unexamine-full-deploy-20260611-100441-fixed/`。
- 最终 zip 包：`dist/unexamine-full-deploy-20260611-100441-fixed.zip`，大小 39,997,421 B。
- Linux 推荐包：`dist/unexamine-full-deploy-20260611-100441-fixed.tar.gz`，大小 39,993,789 B。
- 包内已核验 `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、`backend/start.sh`、`docs/nginx-deploy.md`、P12 build/test/review 证据和 `docs/review.json`。
- `backend/start.sh` 已修复直接执行问题：项目新增 `.gitattributes` 固化 `.sh` 为 LF；fixed zip 中 `start.sh` 外部属性为 Unix `100755`，tar.gz 中为 `-rwxr-xr-x`。
- P12 阶段验收记录：`docs/phases/P12-uiux-frontend-rework-acceptance.md`。
- 当前结论：P12 accepted，最终试部署包 ready。

## 2026-06-11 P13 用户部署反馈可用性返工

用户反馈成立：部署地址 `http://192.168.0.211:19999/` 的 P12 包仍不能称为“普通人可正常使用的完整系统”。PM、Analyst、UI/UX、Frontend 复盘确认，P12 将构建、管理员 E2E 和打包结果扩大解释为最终用户可用，验收口径需要撤回。

本轮 PM/角色结论：

- PM：P12 `fullProjectDeployable=true` 结论过宽，P13 前不得继续把 P12 包当成用户反馈后的最终交付。
- Analyst：当前应区分“可试部署 MVP”和“完整普通用户可用”，不能混用。
- UI/UX：普通用户主链路必须覆盖登录、我的系统、系统总览、应用、模块、字段、发布、运行台和中文可恢复提示。
- Frontend：`mySystemsPageModel` 未写入 SYS-001 嵌套成员/权限，创建系统后直接进入空运行台，是普通用户主链路 P0 卡点。
- Planner：已新增 P13 任务链 `UIUX-003 -> FE-025 -> TEST-011 -> VAL-009 -> REV-009 -> PKG-002`。

本轮修复：

- 前端创建系统后默认进入系统总览，不再直接跳到空运行台。
- `SYS-001` 返回的 `currentTenant/currentMember/permissions` 已在 `frontend/src/pages/my-systems/mySystemsPageModel.ts` 中兼容并写入系统上下文与权限仓库。
- 系统内侧栏隐藏无效 `current` 路由和平台级“平台审计/运维”入口。
- 生产环境不再执行 `smokeApi` 调试查询参数。
- 成功提示改成“操作已完成”，`requestId` 只作为辅助排障信息展示，避免把 `COMMON_OK` 暴露为主提示。
- 应用、模块、字段表单移除 P10/P12 测试默认值。

验证结果：

- TEST-011 浏览器 E2E 通过，记录见 `docs/test_runs/p13-usability-e2e.md`。
- 前端 `npm.cmd run build` 通过，生成 `frontend/dist/`。
- 后端 `mvn.cmd -pl examine-web -am clean package -DskipTests` 通过，生成 `backend/examine-web/target/unexamine.jar`。
- P13 build 记录见 `docs/build/p13-clean-build.md`。

REV-009 复审结论：pass。上一轮 reviewer 要求 `backend/docs/mybatis-plus-generation.md` 属于旧口径，已按当前 `AGENTS.md` 和 `backend/examine-generator/README.md` 修正为“命令即配置，不生成默认报告文件”，以 README、脚本、生成路径和编译结果作为留痕。

PKG-002 已完成：

- P13 zip 包：`dist/unexamine-full-deploy-20260611-160935-p13.zip`，大小 40,005,971 B。
- P13 Linux 推荐包：`dist/unexamine-full-deploy-20260611-160935-p13.tar.gz`，大小 39,994,975 B。
- 包内已核验 `frontend/index.html`、`backend/unexamine.jar`、`backend/start.sh`、`docs/review.json`、`docs/p13-usability-e2e.md`。
- `backend/start.sh` 权限已核验：tar.gz 中为 `-rwxr-xr-x`，zip 外部属性为 `0755`。

当前状态：P13 accepted，P13 包可用于重新部署验证；P12 fixed 包只作为旧基线保留。

## 2026-06-11 P14 PM 产品运行模型启动

用户反馈成立：P14 不能继续靠用户指出问题后追加规则，也不能让 PM 只做任务收集和阶段盖章。PM 必须具备现代主流系统的产品整合视角，先回答“这个系统给谁用、在什么工作空间里用、按什么连续剧本完成业务、失败后怎么恢复”，再组织 UI、前端、后端、测试和验收。

本轮修正：

- 新增 `docs/product/product-vision-and-operating-model.md`，作为 P14 的 PM 产品运行模型。
- P14 任务新增 `P14-APP-000`，要求 PM 先冻结产品运行模型，再进入 UI/UX 和前后端返工。
- `AGENTS.md` 已升级 PM 验收规则：缺少产品运行模型时，UI/UX 不得冻结，frontend 不得进入实现，reviewer 必须 fail。
- 当前仍保持 `fullProjectDeployable=false` 和 `package_gate=blocked`；P14 不打包，先完成产品模型、UI/UX、任务重拆和连续 E2E。

## 2026-06-11 P14 角色、UI 重设计和预判补充

本轮补充三条 PM 裁决：

- 产品模型里的角色是产品画像和权限模板，不是写死角色名。实际落地必须通过可配置角色、权限点、菜单、字段权限、数据范围和真实系统成员上下文控制。
- 当前前端不能继续小修小补。P14 必须重做信息架构、导航、页面布局、视觉层级、状态和中文文案；可复用 API client、store、动态字段渲染等底层能力。
- PM 必须在开发前主动做问题预判，把角色权限、上下文、术语、主线流程、空态异常、自定义角色、真实数据和部署链路落到 UI/UX 与任务拆分里。

## 2026-06-11 P14 一次闭环 UI/UX 与任务冻结

PM/UIUX/Planner 本轮已把 P14 从“概念纠偏”升级为“完整系统 UI 重设计与原型流程纠偏”：

- 新增 `docs/ui/p14-integrated-ui.md`，冻结平台工作空间、系统管理工作空间、业务运行工作空间、集成开放工作空间的页面、导航、视觉、状态和验收矩阵。
- 新增 `docs/tasks/P14-integrated-rework-plan.md`，按 `P14-APP-000` 至 `P14-PKG-001` 拆成产品、UI、前端、后端补缺、三条 E2E、构建、审查和打包闸门。
- 新增 `docs/issues/replies/development/p14_analyst_app_concept_reply.md`，关闭 P14-APP-001 概念复核，明确三类对象边界。
- 更新 `docs/task_plan.md` 和 `docs/phases/development-phases.md`，后续只按 P14 一次闭环任务推进，不再按零散页面补丁推进。
- 当前已完成 P14 的产品模型、概念复核、UI/UX 和任务拆分文档；下一步进入 `P14-FE-001`，先重构前端壳层、导航、视觉和页面文案。
- `package_gate` 继续保持 blocked；三条剧本、clean build、review 未通过前不生成新部署包。

## 2026-06-11 P14-FE-001 前端壳层与文案重构完成

本轮完成 `P14-FE-001`：

- 前端导航分组改为平台工作空间、系统总览、系统设置、建模配置、业务运行、协同流程、对外应用、审计与运维。
- 页面文案区分“业务应用”和“对外应用”，避免继续把系统内建模应用和外部接入应用混用。
- 系统壳层增加工作空间标识、当前系统、租户和成员上下文。
- 登录页、我的系统、系统总览、建模配置、业务运行台、对外应用、审计日志等核心提示改为中文业务语言。
- `frontend/src/styles.css` 已按 P14 工作台视觉做第一轮统一。
- 前端执行 `npm.cmd run build` 通过，产物生成在 `frontend/dist/`，但本轮不提交构建产物、不打包。

下一步：继续 `P14-FE-002`，重做系统总览、系统设置、建模配置和业务运行体验。

## 2026-06-11 P14-FE-002 系统与业务运行体验完成

本轮完成 `P14-FE-002`：

- 系统总览增加当前角色提示和就绪度检查，能说明系统是否具备普通用户使用条件。
- 系统资料、租户、成员、部门、系统角色、字典页面增加说明层，减少冷表格感。
- 成员页明确“平台账号 + 系统成员扩展”的业务模型。
- 建模配置强调业务应用、模块、字段、页面发布顺序，以及普通用户字段权限。
- 业务运行台增加“我的业务入口”和数据范围/权限说明。
- 前端 `npm.cmd run build` 通过。
- 本机 Chrome headless 打开 `http://127.0.0.1:5176` 可渲染登录页，并识别 P14 新文案。

下一步：继续 `P14-FE-003`，重做平台级对外应用和日志分层体验。

## 2026-06-11 P14-FE-003 对外应用与日志分层完成

本轮完成 `P14-FE-003`：

- 对外应用页面增加“创建对外应用 -> 授权范围 -> 安全策略 -> 调用追踪”的接入路径。
- 对外应用创建说明强调外部系统接入主体、secret 只展示一次、scope/IP/限流需要交付前确认。
- secret 展示区域增加保存提醒。
- 平台审计和系统审计页面增加日志层级说明，避免平台日志与业务系统日志混用。
- 前端 `npm.cmd run build` 通过。

下一步：进入 `P14-TEST-001/002/003`，用真实浏览器和真实接口验证剧本 A/B/C；未通过前不执行 P14 打包。

## 2026-06-11 P14 测试闸门探测

本轮完成 P14 测试前置探测，记录见 `docs/test_runs/p14-test-gate-probe.md`：

- 本地 18080/8080 后端未运行。
- 部署环境 `http://192.168.0.211:19999/api/v1/ops/health` 可达，返回 401，说明需要认证。
- 使用 `platform_admin / 123123aa` 登录部署后端成功，返回 accessToken。
- 新增 `frontend/vite.config.ts` 开发态 `/api` 与 `/openapi` 代理，可通过 `VITE_API_PROXY_TARGET` 指向部署环境做本地联调；生产 build 不受影响。
- Headless Chrome 打开本地 Vite 页面可渲染 P14 登录/平台工作空间文案。
- 地址栏 `accessToken/baseUrl/systemId` 预览参数已限制为 dev 模式，生产 build 不读取。

结论：环境具备进入 P14-TEST-001/002/003 的条件，但本记录不是完整 E2E。`package_gate` 继续 blocked。

## 2026-06-11 P14 前端 P0 修复与 API E2E

Reviewer 对 P14 前端提出的 P0 问题成立，本轮已修复：

- 普通业务用户系统总览不再展示成员、角色、建模配置等管理入口，也不再引导用户去建模配置。
- 新增平台工作空间 `#/platform/openapi` 对外应用中心，系统内页面收敛为 `#/systems/:systemId/openapi` 系统对外授权。
- 对外应用授权从手填 scope 字符串改为业务模块、动作、读写字段、数据范围、平台能力、IP 白名单和限流策略。
- 页面配置区不再展示可见 schema 摘要。
- 导出页清理 P11 测试默认值。
- `systemPath` 不再生成 `/current/` 占位链接。

验证结果：

- 前端生产构建通过：`npm.cmd run build`，产物 `frontend/dist/index.html`、`frontend/dist/assets/index-Syyl9-qs.js`。
- 后端 clean package 通过：`mvn.cmd -pl examine-web -am clean package -DskipTests`，8 个 Maven 模块 SUCCESS，产物 `backend/examine-web/target/unexamine.jar`，记录见 `docs/build/p14-clean-build.md`。
- 静态扫描未发现 `schema 摘要`、`schemaSummary`、`P11导出模板`、`p11-export`、`授权 scope`、`保存Scope`、`/current/` 等可见调试痕迹。
- 真实 API E2E 通过，记录见 `docs/test_runs/p14-integrated-api-e2e-20260611.md`：登录、建系统、进系统、建应用、建模块、建字段、保存页面、保存菜单动作、发布、运行台新增记录、记录详情/历史、OpenAPI scope 目录、创建对外应用、日志查询均通过。
- CDP 浏览器真实登录、平台对外应用中心、系统对外授权表单和业务运行台关键页面烟测通过，记录见 `docs/test_runs/p14-frontend-smoke-20260611.md`。
- Reviewer P0 回环已补：平台对外应用中心增加 `OPENAPI_POLICY_VIEW/PLAT_SYSTEM_VIEW` 权限要求，按钮受权限控制，P11 调试默认值和 `/current/` 导航占位已清理。

PM 结论：P14 已从“前端 P0 不可交付”推进到“API 主链路通过、前后端构建通过、关键页面浏览器烟测通过”。`fullProjectDeployable=false` 和 `package_gate=blocked` 保持不变，继续执行 reviewer 后才能打包。
