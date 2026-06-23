# 签字后开发计划草案

> 版本：`1.7.24-clean-pre-coding-review-fixes`
> 状态：设计锁版建议已通过多角色最终复审；等待用户签字。
> 说明：本文是签字后的开发路线草案，不是正式任务单。正式 `docs/tasks/plan.md` 与 `TASK-*.md` 必须在 `design_user_approved=true` 且 API 契约冻结后生成。

## 1. 最终复审结论

| 角色 | 结论 | 阻断 |
|---|---|---|
| PM / 解决方案 | LOCK_WITH_NOTES | 无设计 P0/P1；仅等待用户签字 |
| 需求分析 | LOCK_WITH_NOTES | 无范围变更裁决；P2 进入 API 契约细化 |
| UI/UX | LOCK_WITH_NOTES | 无 UI/IA P0/P1；仅保留实现注意项 |
| 实施规划 | BLOCK | 仅流程闸门阻断：未签字不能进入 API/任务/coding |
| DBA | LOCK_WITH_NOTES | 无数据模型 P0；P1 进入 DB/API 契约冻结 |
| 后端 | LOCK_WITH_NOTES | 无技术 P0；签字后先 API 契约 |
| 前端 | LOCK_WITH_NOTES | 无前端 P0；需要 typed API 和 schema 契约 |
| 测试 | LOCK_WITH_NOTES | 无设计 P0；签字后补测试契约和 11 步 E2E |

综合判断：原型内容可作为最终设计锁版候选。没有需要提交 PM/用户做范围变更裁决的设计问题。唯一阻断是用户签字闸门：`docs/design/user-approval.md approved=false`、`.cursor/session/state.json design_user_approved=false`。

## 2. 签字后阶段顺序

1. Design Gate：用户确认后，将 `docs/design/user-approval.md` 改为 `approved: true`，同步 `.cursor/session/state.json gates.design_user_approved=true`。
2. Contract C1 并行草案：DBA 数据影响、Backend API 草案、Frontend mapping、Test contract 并行产出。
3. Contract C2 合并冻结：PM/Conductor 合并 `docs/api/api.md`，角色复核，跑 contract-sync，无 P0 后设置 `api_frozen=true`。
4. Planner：创建正式 `docs/tasks/plan.md` 和 `TASK-*.md`，定义批次、依赖、负责人和验收。
5. Build：按批次并行实现，每个任务 task-accept，批次结束 clean-build。
6. Verify：车系统 / che 主剧本、四角色权限矩阵、风险回归、用户试用。

## 3. API 契约优先级

1. 通用协议：统一返回、错误码、分页、筛选、排序、幂等键、requestId、traceId、auditLogId。
2. 身份与上下文：登录、注册建系统、平台账号、系统成员、系统/租户切换、`SystemSwitchContext`、`TenantSwitchContext`、`EffectivePermissionSnapshot`。
3. 权限与配置：平台角色、系统角色、字段权限、数据范围、模块组、模块、字段、字典、页面动作、发布版本。
4. 运行态业务：动态列表、详情、草稿、CRUD、动作权限、附件、导入导出、打印。
5. 流程 / 待办 / 消息：流程配置、实例快照、任务快照、审批处理、待办、消息模板、通知渠道、`message_delivery_log`。
6. SSO / OpenAPI / Secret：身份源、系统继承、`NoMemberAccessRequest`、`SecretRotationJob`、`OpenApiSecretRef`、外部应用。
7. 工作管理与 AI Agent：项目任务、普通任务、日报、看板规则、`DailyReportAutoSourceRule`、模型授权、`AgentPolicyScope`、运行对话、写入确认、审计。
8. 运维保障：后台任务、日志、缓存、容量配额、限流、备份恢复、归档恢复、多环境与部署回滚。

## 4. 并行开发批次草案

| 批次 | 目标 | 可并行角色 / 工作流 | 关键依赖 |
|---|---|---|---|
| B0 基础工程 | Maven / 前端壳 / SQL 基线 / generator / common core | backend、frontend、dba、test | API 契约冻结 |
| B1 身份上下文 | 账号、登录、系统、租户、成员、角色、有效权限 | backend、dba、frontend、test | B0 |
| B2 配置模型 | 模块组、模块、字段、字典、页面配置、动作、发布版本 | backend、dba、frontend | B1 权限基础 |
| B3 运行态业务 | 业务列表、详情、草稿、导入导出、附件、打印、审批入口 | backend、frontend、test | B1、B2 |
| B4 协作链路 | 流程、审批、待办、消息模板、后台任务、日志 | backend、dba、frontend、test | B1、B2 |
| B5 扩展能力 | 工作管理、AI Agent、SSO、OpenAPI、密钥轮换、上线保障 | backend、dba、frontend、test | B1-B4 部分能力 |

## 5. 后端任务切片草案

- `examine-core`：通用协议、上下文、权限门面、审计 trace、后台任务模型。
- `examine-plat`：登录注册、平台账号/角色、系统创建、平台工作台、平台后台。
- `examine-module`：系统/租户切换、成员、RBAC、模块配置、字段、字典、页面发布。
- `examine-runtime`：动态业务列表、详情、草稿、附件、导入导出、打印。
- `examine-flow`：流程配置、实例、任务、审批处理、状态回写。
- `examine-message-log`：待办、消息模板、通知渠道、投递日志、业务日志、登录日志。
- `examine-integration`：SSO、SecretRef、OpenAPI、数据源、密钥轮换。
- `examine-ai-work`：工作管理、AI Agent 策略、运行态对话、写入确认。

## 6. 前端任务切片草案

- Shell / 路由 / 权限：登录、注册、找回、平台工作台、平台后台、系统业务页、系统后台、路由守卫、无权限态。
- 通用组件：顶部栏、侧栏、表格、筛选、分页、抽屉、消息流、待办列表、后台任务、状态组件。
- 系统业务运行台：模块分组、左侧模块、业务列表/详情、新建编辑、导入导出、附件、审批侧栏、全局搜索、快捷创建。
- 平台侧：平台仪表盘、Flow、应用授权、工作管理、平台待办、平台消息、平台后台配置/日志。
- 系统后台：系统信息、组织、统一认证、角色、模块配置、工作配置、AI Agent、流程、字典、数据源、对外应用、日志。
- API mapping / typed SDK：请求响应类型、权限模型、schema 适配器、mock fixtures。

## 7. 数据库任务切片草案

- `DB-CORE`：表前缀、公共审计字段、ID/编码、软删、租户字段、基础审计表。
- `DB-IDENTITY`：平台账号、系统、租户、成员绑定、角色权限、有效权限快照、SSO、NoMemberAccessRequest。
- `DB-MODULE`：模块组、模块、字段、字典、页面、动作、动态记录、索引值表、自动编号。
- `DB-FLOW-WORK`：流程配置/快照/实例/任务、待办、工作管理、日报自动来源规则。
- `DB-MSG-LOG-TASK`：消息模板、通知渠道、message_delivery_log、后台任务、登录/业务日志、归档策略。
- `DB-SEC-OPENAPI-AI`：SecretRef、SecretRotationJob、OpenAPI 应用/scope/限流/日志、AgentPolicyScope、AI 审计。
- `DB-UPLOAD-OPS`：文件、引用、预览、备份恢复、容量配额、功能开关、部署回滚配置。

## 8. 测试任务切片草案

- QA-contract：API 测试契约和 mock 数据。
- QA-static：断链、generic/default、data-toast、按钮结果链路检查。
- QA-role-e2e：`platform_admin_root`、`platform_member`、`sys_admin_vehicle`、`che` 四角色入口和越权拦截。
- QA-che-e2e：车系统普通用户 11 步主剧本。
- QA-admin-config：系统后台配置、流程、字典、工作配置。
- QA-security-ops：SSO、密钥轮换、OpenAPI、日志、备份回滚。
- QA-evidence：统一 `docs/evidence/**` 截图、日志、pass/fail 证据格式。

## 9. API 阶段必须裁定的 P2 细节

- `SystemSwitchContext` 与 `EffectivePermissionSnapshot` 是合并返回还是拆对象返回。
- `EffectivePermissionSnapshot` 的版本号、缓存失效、显式拒绝优先级、数据范围表达式。
- 动态业务数据存储模型：record、value、index、child_row、relation、history、自动编号和唯一约束。
- SecretRef 明文托管位置、轮换状态机、失败回滚、最近调用检查。
- 消息跳转目标模型：平台/系统层级、targetType、targetId、templateCode、deliveryLog、read/archive 状态。
- 统一后台任务状态机、幂等键、取消/重试/回滚边界、结果文件生命周期。
- 流程快照模型、状态回写、外部 API 重试/补偿和幂等键。
- AI Agent 原始对话、工具调用、提示词版本、模型授权版本保留期限和归档策略。
- 首期不实现的短信或部分 SSO 协议，必须有禁用态和错误反馈。

## 10. 锁版前后禁止事项

- 未签字前，不创建 backend/frontend/sql，不创建正式任务单。
- 未 API 冻结前，不进入实现。
- P0 入口不得落到 `defaultDrawer` / generic 兜底。
- 前端不得临时拼权限上下文；系统/租户/权限上下文必须由后端契约返回。
- 平台消息和平台 Agent 不得直接打开或写入系统业务数据。
