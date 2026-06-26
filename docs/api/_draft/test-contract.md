# API 契约冻结前测试契约草案

> Worker: Test / QA
> 阶段：Phase 2 Contract
> 输入依据：`.cursor/session/state.json`、`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/prototypes/index.html`、`docs/design/reviews/prototype-latest-2026-06-18.md`、`docs/design/pre-coding-readiness.md`、`docs/design/post-approval-development-plan.md`、`docs/design/user-approval.md`
> 输出性质：测试契约草案，供 PM 合并正式 `docs/api/api.md` 前评审；不宣布 API 冻结通过。

## 1. 当前闸门状态

- 用户已在 `docs/design/user-approval.md` 签字：`approved=true`，签字时间为 `2026-06-23T15:10:00+08:00`。
- `.cursor/session/state.json` 当前为 `phase=contract`，`mode=api-contract-drafting`。
- 当前只进入 API 契约冻结阶段；`gates.api_frozen=false`、`gates.tasks_planned=false`，不得进入 backend、frontend、SQL 实现。
- 本文件只定义测试视角下 API 契约需要怎样可测、怎样留证，不替代 PM 合并，不替代 contract-sync，不给出 pass 结论。

## 2. 统一测试口径

所有 API 契约在冻结前必须能支持以下三类结论分离：

| 结论维度 | 测试含义 | 不可替代项 |
|---|---|---|
| 后端链路通过 | API 请求、鉴权、权限、事务、日志和状态机按契约返回 | 不能等同于前端可用 |
| 前端可集成 | 字段、枚举、禁用态、分页、任务状态足够前端渲染 | 不能等同于普通用户可用 |
| 普通人可用 | che 主剧本可从登录到业务处理闭环跑通 | 不能只用管理员或 API smoke 替代 |

统一返回和留痕要求：

| 字段 | 契约要求 | 测试断言 |
|---|---|---|
| `requestId` | 每次请求必须返回；异步、日志、消息、任务中可反查 | 成功与失败响应均存在且格式稳定 |
| `traceId` | 跨服务、异步任务、登录、审批、导入导出、Agent 写入必须可追踪 | 前端响应、任务详情、日志详情能串联 |
| `auditLogId` | 写操作、权限拒绝、认证失败、审批、密钥、导入导出、Agent 写入必须返回或记录 | 可用 `auditLogId` 查到审计详情 |
| `permissionVersion` | 涉及菜单、按钮、字段、数据范围、系统/租户切换的响应必须返回或关联记录 | 权限变更后版本变化，缓存失效可测 |
| `taskId` | 异步导入、导出、发布检查、流程模拟、AI 写入、密钥轮换、体检、恢复演练必须返回 | 可查询状态、进度、结果文件、错误文件 |
| `status` | 所有业务对象、任务、申请、审批、消息、配置发布必须使用明确状态枚举 | 状态流转合法，非法状态返回业务错误 |
| `disabledReason` | 按权限、状态、配额、租户、未映射成员等禁用动作时必须返回原因 | 前端能展示禁用原因，后端仍兜底拒绝 |
| `errorCode` | 失败响应必须有分域错误码，不用泛化系统异常兜底 | P0/P1 关键失败可按错误码断言 |

## 3. API 契约验收矩阵

| 范围 | 冻结前必须可测的接口对象 | 核心验收断言 | 必须返回或记录的字段 |
|---|---|---|---|
| 认证 / 注册 / 找回 | 登录、退出、刷新 token、注册并创建系统、找回/重置密码、MFA/验证码、登录日志 | 登录页基础流程可走通；注册创建系统后创建人成为系统超级管理员并进入初始化引导；失败限制、账号停用、token 过期有明确错误 | `requestId`、`traceId`、`auditLogId`、`status`、`disabledReason`、`errorCode` |
| 系统创建和切换 | 创建系统、启用/停用/删除/恢复、我的系统、系统切换、`SystemSwitchContext`、`accountMemberBindingId` | 平台账号进入系统前必须取得 `systemMemberId`、角色、数据范围和权限快照；切换后业务壳、模块分组、左侧模块、列表、待办、消息和字段权限刷新 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`status`、`disabledReason`、`errorCode` |
| 租户切换 | 租户列表、租户上下文、`TenantSwitchContext`、单租户隐藏入口、多租户切换 | 多租户返回 `tenantId`、租户角色、数据范围和可切换原因；单租户不暴露无意义切换；切换后权限、菜单、待办、消息、字段权限刷新 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`status`、`disabledReason`、`errorCode` |
| 角色权限 | 平台角色、系统角色、成员分配、菜单/模块/动作/字段/数据范围、有效权限预览 | 超管边界明确；普通成员无后台入口；直接访问无权限接口返回 403 类业务错误；显式拒绝优先、字段权限和审批节点权限冲突可解释 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`status`、`disabledReason`、`errorCode` |
| 模块配置 | 模块组、模块、字段、字段类型注册表、字典、页面配置、列表场景、页面动作、发布版本、回滚 | 模块组只做运行态导航；业务数据归属 `systemId/tenantId/moduleId`；字段类型、导入导出规则、字段权限、列表排序和整行点击配置可测 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| 业务列表详情 | 动态列表、筛选、排序、分页、保存视图、列设置、详情、新增/编辑/删除、草稿、附件、打印 | 列表服务端分页；行点击详情主动作可由数据返回支撑；无权限字段脱敏或隐藏；禁用按钮有原因；详情保留审批、附件、操作记录 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`status`、`disabledReason`、`errorCode` |
| 审批流程 | 流程配置、节点属性、发布检查、流程实例、任务快照、审批处理、转交/加签/驳回/撤回、状态回写 | 流程配置和实例快照分离；审批办理在业务详情或待办中；重复提交幂等；拒绝/撤回/终止必须有原因；状态回写和日志可追踪 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| 待办消息 | 待办类型、待办列表、待办处理、消息流、消息筛选、全部已读、归档、消息模板、通知渠道、`message_delivery_log` | 平台消息和系统消息拆开；平台消息不能直跳系统业务详情；系统消息必须在系统成员上下文内跳转；筛选、已读、归档有状态变化 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| 工作管理 | 仪表盘、项目任务、普通任务、日报、看板规则、分页、`DailyReportAutoSourceRule`、工作配置发布 | 四标签固定；项目任务/普通任务列表与看板互斥；看板列/泳道来自已发布字段和字典；日报自动草稿只读权限内来源且人工确认 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| SSO / NoMember | 平台身份源、系统继承、组织映射、员工绑定、JIT、登录审计、`NoMemberAccessRequest` | SSO 认证成功但没有目标系统 `systemMemberId` 时只能生成申请；审核通过并分配角色/数据范围前不能进入业务页 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| AI Agent | 平台模型授权、系统 `AgentPolicyScope`、运行态对话、工具调用、写入确认、审计、外发限制、脱敏 | 平台 Agent 只生成平台任务/消息/日志；系统 Agent 只能在当前系统成员上下文内操作；写入前人工确认；策略版本和权限快照入日志 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| 导入导出 | 模板下载、字段匹配、预检查、确认导入、错误文件、批次结果、导出范围/格式、结果文件、回滚边界 | 大任务必须异步；导出受字段权限和数据范围控制；导入部分成功、失败、不可回滚原因可追踪；重复提交幂等 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |
| 日志 / 后台任务 / 运维保障 | 登录日志、业务日志、后台任务、体检、限流、配额、备份恢复、归档恢复、部署回滚、缓存策略 | 平台和系统日志一个入口内分类型；后台任务状态机统一；体检、恢复演练、密钥轮换、发布检查都有任务、日志和失败反馈 | `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` |

## 4. 首期 E2E 主剧本：车系统 che 11 步

本剧本用于 API 冻结后拆正式 E2E，不在 contract 草案阶段宣布通过。

| 步骤 | 角色 | 用户动作 | API 验收重点 | 证据 |
|---|---|---|---|---|
| 1 | `platform_admin_root` | 平台管理员登录平台工作台 | 登录返回 `requestId/traceId`；平台后台入口仅对平台管理员可见；登录日志可查 | 响应、登录日志、权限菜单 |
| 2 | `platform_admin_root` | 创建车系统 `che` / 车辆资产管理系统 | 创建系统返回系统状态、初始化任务或同步结果；创建人/授权管理员边界明确 | 系统记录、`auditLogId`、创建日志 |
| 3 | `platform_admin_root` 或系统创建人 | 完成初始化配置：系统信息、组织、角色、模块字段、流程字典、邀请成员、发布业务首页 | 初始化每步有状态；发布检查返回 `taskId/status/traceId`；失败项可定位 | 初始化状态、发布检查任务 |
| 4 | 系统管理员 | 开通成员 `che`，绑定系统员工、角色和数据范围 | 成员绑定返回 `accountMemberBindingId`、`systemMemberId`、角色、数据范围、`permissionVersion` | 成员详情、权限快照 |
| 5 | 系统管理员 | 配置车辆管理模块组、车辆档案字段、列表、权限、审批流程、导入导出 | 模块组不作为数据父级；字段权限、页面动作、流程绑定、导入导出配置发布后有版本 | 配置版本、发布日志 |
| 6 | `che` | 普通用户登录并进入车系统 | 生成 `SystemSwitchContext`；普通用户看不到系统后台/平台后台；待办消息范围随上下文聚合 | 上下文响应、无后台入口 |
| 7 | `che` | 单租户隐藏租户切换；若多租户，切换到授权租户 | `TenantSwitchContext` 包含租户角色、数据范围、是否可切换和禁用原因；切换刷新业务壳 | 租户上下文、菜单刷新 |
| 8 | `che` | 打开车辆列表，执行搜索、筛选、排序、分页、列设置 | 列表服务端分页；字段按权限返回；分页和筛选有稳定状态；行点击目标可由契约识别 | 列表响应、`permissionVersion` |
| 9 | `che` | 新增车辆、保存草稿、提交并查看详情 | 写操作返回 `auditLogId/traceId/status`；详情包含业务字段、附件、操作记录和审批入口；无权字段不返回明文 | 新增响应、详情响应、日志 |
| 10 | `che` / 审批人 | 发起审批、处理待办、通过或驳回 | 流程实例与任务快照可查；待办整行主动作可打开业务对象；重复审批幂等；驳回必须原因 | 流程实例、待办、审批日志 |
| 11 | `che` | 执行导入、导出，查看消息待办结果，并验证权限拦截 | 导入导出返回 `taskId`；消息流整条跳转任务结果；无导出或无后台权限时返回 `disabledReason/errorCode` | 任务详情、消息、403 响应 |

首期 E2E 最小角色集：

- `platform_admin_root`：平台管理员，验证平台后台、系统创建、平台级日志和授权边界。
- `sys_admin_vehicle`：系统管理员，验证系统后台配置、成员角色、流程、字段和发布。
- `che`：普通成员，验证真实业务使用主线和越权拦截。
- 可选 `platform_member`：验证平台普通成员无平台后台入口、只能查看授权和申请。

## 5. 关键反向用例

| 用例 | 预期 |
|---|---|
| `che` 直接请求系统后台接口 | 返回无权限错误，包含 `requestId/traceId/errorCode/disabledReason`，并记录审计 |
| `platform_admin_root` 未经系统切换直接访问系统业务数据 | 拒绝访问，不能绕过系统成员和系统权限 |
| SSO 登录成功但无 `systemMemberId` | 生成 `NoMemberAccessRequest`，不能进入业务页 |
| 导出无权限字段 | 字段脱敏或剔除，导出结果和日志记录权限版本 |
| 审批任务重复提交 | 返回同一状态或幂等冲突错误，不重复推进流程 |
| 平台消息尝试跳系统业务详情 | 返回系统切换或授权引导，不直接打开业务对象 |
| 密钥轮换失败 | `SecretRotationJob` 进入失败或回滚状态，日志不回显明文 |
| 后台任务部分成功 | `status=partial_success`，可下载结果文件和错误文件，可追踪失败行 |

## 6. API 冻结前需要 PM 裁决的 P2 点

以下是测试视角发现的 P2 裁决点；当前不宣布 pass。

| P2 点 | 为什么需要裁决 | 测试期望 |
|---|---|---|
| `SystemSwitchContext` 与 `EffectivePermissionSnapshot` 合并返回还是拆接口返回 | 影响前端首屏加载、缓存失效和 E2E 断言粒度 | 至少保证一次系统切换后可得到完整上下文和权限版本 |
| `permissionVersion` 的生成和失效规则 | 权限变更、字段权限、租户切换、发布配置都依赖版本可测 | 明确版本来源、缓存 key、失效时机和前端版本不一致反馈 |
| `TenantSwitchContext` 是否对单租户返回空对象还是完全不返回 | 影响前端隐藏入口和测试断言 | 单租户隐藏入口，多租户返回完整上下文和禁用原因 |
| 动态业务数据的字段值、索引值、子表、关联和历史模型边界 | 影响列表筛选、详情、导入导出和审计 | 正式 API 中给出列表值、详情值、历史值和导出值的稳定结构 |
| 消息跳转目标模型 | 平台/系统消息边界强依赖跳转目标 | 明确 `targetScope/targetType/targetId/templateCode/deliveryLogId/readStatus/archiveStatus` |
| 统一后台任务状态机 | 导入导出、发布检查、AI 写入、密钥轮换、体检都要复用 | 明确 `queued/processing/success/partial_success/failed/canceled` 及取消、重试、回滚边界 |
| `NoMemberAccessRequest` 审核流归属 | 涉及平台身份源、系统管理员审核、角色和数据范围分配 | 明确创建、审核、驳回、角色分配、通知和日志接口归属 |
| SecretRef 托管与轮换状态 | 安全与测试都不能接受明文回显 | 明确 SecretRef 创建、版本、双写验证、切换、停用旧版本和失败回滚状态 |
| AI Agent 审计保留期限和归档 | 涉及对话、提示词、模型版本、工具调用和外发数据快照 | 明确最小审计字段、脱敏规则、归档策略和日志查询接口 |
| 首期暂不实现的 SSO 协议、短信、部分运维能力 | 原型已表达完整能力，但首期实现可能分批 | 必须有禁用态、`disabledReason`、错误码和不误导用户的前端状态 |

## 7. 冻结前测试交付建议

- PM 合并正式 `docs/api/api.md` 时，应逐项标注本矩阵中每类接口对应的 API 章节。
- Backend/Frontend/DB 草案若缺少 `requestId`、`traceId`、`auditLogId`、`permissionVersion`、`taskId`、`status`、`disabledReason`、`errorCode` 的返回或记录路径，测试应退回补契约。
- `api_frozen=true` 前只能形成评审结论；正式 E2E、浏览器截图和 `docs/evidence/**` 应在任务计划和 build 批次完成后执行。
- 当前未发现新增 P0/P1 测试阻断点；上述 P2 点需要 PM 在 API 合并时裁决或明确延期策略。
