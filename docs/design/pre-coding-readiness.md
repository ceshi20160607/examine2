# 开发前可交接验收矩阵

> 版本：`1.7.24-clean-pre-coding-review-fixes`
> 状态：原型与设计证据已完成开发前复验；用户已签字，API 已冻结，任务计划已生成。
> 性质：本文是 coding 前的证据索引和交接矩阵，不是新的需求来源。设计输入仍以 `docs/design/prototype-brief.md` 和 `docs/design/prototypes/index.html` 为准。

## 1. 当前结论

2026-06-23T15:10:00+08:00 用户已确认当前原型并同意开启后续阶段；`docs/design/user-approval.md` 已改为 `approved: true`，`.cursor/session/state.json` 已进入 Build 前置完成状态。

2026-06-23T16:05:00+08:00 API review 与 contract-sync 已通过，`docs/api/api.md` 冻结为 `0.1.0-frozen`。

2026-06-23T16:20:00+08:00 正式任务计划已生成：`docs/tasks/plan.md` 与 `docs/tasks/TASK-*.md`。Build 可以按任务单输出路径开始，禁止脱离任务单修改 backend/frontend/sql。

后续顺序是：按并行组实现任务 -> 每个任务 task-accept -> 批次 clean-build -> 车系统 che 主剧本验收。

2026-06-23 已完成八角色最终锁版复审：PM、业务分析、UI/UX、DBA、后端、前端、测试均为 `LOCK_WITH_NOTES`，未发现新的 P0/P1 设计阻断点；计划角色的 `BLOCK` 仅来自签字/API/任务计划门禁未打开。签字后的开发计划草案见 `docs/design/post-approval-development-plan.md`，该文件只作为设计阶段交接草案，正式任务单必须在 API 契约冻结后生成。

## 2. 开发只读入口

| 用途 | 文件 |
|---|---|
| 唯一设计输入 | `docs/design/prototype-brief.md` |
| 当前原型 | `docs/design/prototypes/index.html` |
| 当前设计包 | `docs/design/design-package.md` |
| 签字闸门 | `docs/design/user-approval.md` |
| 当前唯一开发口径 | `docs/design/reviews/prototype-latest-2026-06-18.md` 顶部“当前唯一开发口径（1.7.24）” |
| 本交接矩阵 | `docs/design/pre-coding-readiness.md` |

历史 review 早期段落只用于追溯，不作为 coding 口径。

## 3. 架构与角色证据

| 验收项 | 当前口径 | 证据 |
|---|---|---|
| 四套壳 | 平台工作台、平台后台、系统业务页、系统后台分离 | 原型 4 个主 screen；brief 0.2；user-approval 四套壳清单 |
| 普通成员 | 只看权限内业务模块、待办、消息、个人信息 | 浏览器复验：che 无后台入口；业务侧栏仅车辆/维保/驾驶员 |
| 系统管理员 | 从个人信息弹层进入系统后台 | 浏览器复验：sys_admin_vehicle 可进 systemAdmin，左侧导航完整 |
| 平台普通成员 | 进入平台工作台和授权系统，无平台后台入口 | 浏览器复验：platform_member 无 platformAdmin 可见入口 |
| 平台管理员 | 从个人信息弹层进入平台后台 | 浏览器复验：platform_admin_root 可进 platformAdmin |
| 系统切换 | 顶部唯一多系统入口 | 原型 systemSwitchDrawer；brief 2；design-package 当前骨架 |
| 租户切换 | 多租户系统内 TenantSwitchContext；单租户隐藏入口 | tenantSwitchDrawer；sysInfo；brief 2 |
| 系统壳重绘 | 切换系统后品牌、模块分组、左侧模块、列表、待办、消息和字段权限都刷新 | systemShells/applySystemContext；systemSwitchDrawer accountMemberBindingId |

## 4. 运行态业务证据

| 验收项 | 当前口径 | 证据 |
|---|---|---|
| 业务骨架 | CRM 式顶部模块分组、左侧组内模块、右侧列表/详情 | 原型 systemBusiness；brief 0.2 / 3.3 |
| 列表能力 | 搜索、场景、高级筛选、列设置、导入、导出、排序、批量 | 原型车辆列表；brief 4；user-approval 列表能力 |
| 详情 | 右侧详情工作区，顶部摘要、tab、审批侧栏 | vehicleDetailDrawer；user-approval 详情骨架 |
| 行点击 | 整行点击为详情主动作，但只对明确标记的数据列表或业务核心列表启用 | 原型行点击脚本；review 30；brief 7.2 |
| 操作列去重 | 不再用“详情 / 查看 / 打开 / 进入”承接同一详情动作 | 静态复验：精确重复按钮 0；原型日报/待办/搜索动作已改差异动作 |
| 导入导出 | 工具栏触发抽屉，后台任务反馈 | importDrawer、exportDrawer、asyncTaskDrawer |

## 5. 待办、消息、工作管理证据

| 验收项 | 当前口径 | 证据 |
|---|---|---|
| 待办 | 左侧待办类型，右侧待办列表和筛选，隐藏业务模块侧栏 | 浏览器复验：bizTodo businessSide display=none |
| 消息 | 顶部入口打开右侧消息流抽屉，不做表格页 | messageCenterDrawer；消息卡片 4 条，表格 0 |
| 消息跳转 | 整条消息点击跳业务详情/任务/日志/Agent 结果 | 浏览器复验：审批消息打开车辆详情工作区 |
| 消息筛选与状态 | 系统/租户/模板/状态筛选、全部已读、归档均有状态反馈 | messageCenterDrawer、platformMessageCenterDrawer；脚本 data-message-action |
| 消息模板 | 消息、待办、审批、导入导出、Agent 结果统一用模板与通知渠道配置 | notificationTemplateDrawer；平台配置管理、系统信息页、流程管理均有入口；templateCode、变量、渠道、跳转目标、去重、已读、重试和 message_delivery_log 可见 |
| 工作管理 | 仪表盘、项目任务、普通任务、日报四标签 | 原型 platWork/sysWork；brief 6.8；project rules |
| 任务视图 | 项目任务/普通任务列表与看板互斥切换 | data-task-view 校验通过；brief 6.8 |
| 日报 | 只展示我的日报和筛选，写日报/自动草稿为操作入口 | 原型日报页；brief 6.8 |

## 6. 后台配置证据

| 验收项 | 当前口径 | 证据 |
|---|---|---|
| 系统后台导航 | 系统信息、组织架构、统一认证、角色、模块、工作配置、AI Agent、流程、字典、仪表盘、数据源、对外应用、日志 | 浏览器复验 systemAdmin leftNav |
| 平台后台导航 | 平台信息、组织架构、角色、仪表盘、配置、日志 | 浏览器复验 platformAdmin leftNav |
| 模块配置 | 字段、列表、筛选、场景、动作、发布、导入导出、打印模板 | moduleConfigDrawer；brief 5 |
| 工作配置 | 一级入口，配置字段/权限/看板取数字段，不维护状态标签选项；API 阶段按项目任务字段、普通任务字段、日报字段、看板规则、发布检查拆对象 | sysWorkConfig + workConfigDrawer；brief 6.8 |
| 数据字典 | 字典类型、状态/标签/字段选项、颜色、图标、引用影响 | 浏览器复验 dictTerms |
| 流程配置 | 节点库、画布、节点属性、审批类型、模拟、发布检查 | sysFlows + flowNodePropertyDrawer |
| 日志管理 | 平台和系统都保持一个入口，采用左侧日志类型/审计范围、右侧统一列表；左侧类型树是唯一主分类 | sysLogs / platLogs；brief 6.6；review 30 |
| 企业 SSO / 统一认证 | 平台后台配置身份源；系统后台一级“统一认证”页配置继承、域名范围、组织映射、员工绑定、JIT、无权限反馈；登录页只消费已发布身份源 | platConfig + ssoConfigDrawer + sysSso + systemSsoPolicyDrawer + sysOrg；brief 2 / 3.5；review 34 |
| 密钥引用与轮换 | SSO、OpenAPI、Webhook、外部模型密钥不回显明文，使用 SecretRef 和 SecretRotationJob | ssoConfigDrawer、secretRotationDrawer、externalAppDrawer、backupRestoreDrawer；IdentityProviderSecretRef / SecretRotationJob 显式出现 |
| 无成员申请 | 认证成功但无 systemMemberId 时不能进入业务页，只生成申请 | systemSwitchDrawer + noMemberRequestDrawer；NoMemberAccessRequest 显式出现 |
| 无成员申请生命周期 | requestId/status/identityProvider/externalUserId/targetSystemId/tenantId/requestRole/approverId/approveResult/roleIds/dataScope/rejectReason/traceId | noMemberRequestDrawer；systemSsoPolicyDrawer |
| AI Agent | 平台模型授权 + 系统级 Agent 策略；平台运行态 Agent 与系统运行态 Agent 拆分，平台 Agent 不直接打开系统业务模块，且只确认生成平台任务/消息/日志；系统 Agent 需在当前系统成员上下文内运行，业务写入需人工确认 | platformRuntimeAgentDrawer、platformAgentConfirmDrawer、sysAgentConfig、runtimeAgentDrawer、agentWriteConfirmDrawer |
| AgentPolicyScope | 系统 Agent 策略显式保存 moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略和策略版本 | sysAgentConfig、aiAgentConfigDrawer、agentPublishCheckDrawer |
| OpenAPI 密钥引用 | 外部应用展示 OpenApiSecretRef、版本、轮换状态、到期和最近使用，不回显明文 | externalAppDrawer、secretRotationDrawer |
| 类型型后台页布局 | 日志、字典、数据源、对外应用、工作配置至少明确左侧类型/对象选择与右侧列表/配置刷新关系 | brief 6.6 / 6.8 / 6.10；review 30 |

## 7. 上线与开发契约证据

| 验收项 | 当前口径 | 证据 |
|---|---|---|
| 单/多租户 | 系统信息表达租户模式、隔离、切换影响 | sysInfo；brief 4.2 |
| 系统生命周期 | 创建、启用、停用、删除、恢复/禁用原因、审计 | createSystemDrawer；brief 7.2 |
| 上线保障 | 体检、功能开关、灰度、容量、限流、备份恢复、归档恢复、扩容 | platform config + sysInfo；review 26/28 |
| 多环境/回滚 | 开发/测试/生产隔离，部署回滚，密钥检查 | deploymentPolicyDrawer；review 27 |
| API 规范/缓存 | requestId、traceId、分页筛选排序、错误码、幂等、缓存 key | apiCachePolicyDrawer；review 27 |
| 后台任务契约 | taskId、bizType、idempotencyKey、status、progress、cancelable、resultFile、errorFile、createdBy、createdAt、traceId、auditLogId、retryable | asyncTaskDrawer；brief 7 |
| 关键按钮承接 | 保存、发布、确认、审批、轮换、模板、体检、演练等关键按钮不落泛化响应 | 关键按钮使用 data-action-result 或 data-drawer 指向 asyncTaskDrawer/logActionDrawer/发布检查/专属抽屉 |
| 分页与加载按钮承接 | 上一页、下一页、加载更多、筛选应用、列设置保存、批量处理都有状态或结果反馈 | data-page-action、data-page-result、data-filter-action、data-action-result |
| 通用状态 | 加载、空、无权限、禁用、错误、后台任务 | stateSpecDrawer；brief 7.1 |
| 设计缺口阻断态 | defaultDrawer 只代表设计缺口阻断，不作为开发兜底详情 | defaultDrawer 文案；静态断链检查 |
| 字段类型注册表 | 存储形态、筛选/排序操作符、默认值校验、权限能力、导入导出规则 | moduleCreate 字段类型矩阵；brief 5 |
| 移动效率 | 收藏菜单、扫码、拍照上传、附件草稿链路 | quickCreateDrawer、attachmentDrawer |

## 8. 已执行验证

| 验证 | 结果 |
|---|---|
| 脚本语法 | 通过 |
| 页面/screen/page/subpage/workSubpage/taskView/drawer/message jump 引用 | 无断链 |
| data-toast | 0 |
| 消息卡片内按钮 | 0 |
| 精确重复“详情/查看/打开”按钮 | 0；后续按同义详情动作白名单复核 |
| 登录页滚动 | 720px 视口无外层滚动条 |
| che 路径 | 无后台入口，待办隐藏业务侧栏，消息为右侧消息流 |
| platform_member 路径 | 无平台后台入口 |
| platform_admin_root 路径 | 可从个人信息弹层进入平台后台 |
| sys_admin_vehicle 路径 | 可从个人信息弹层进入系统后台 |
| git diff --check | 仅 Windows LF/CRLF warning，无空白错误 |
| 企业 SSO 配置覆盖 | 通过；静态检查确认 ssoConfigDrawer、平台配置行、系统后台 sysSso、systemSsoPolicyDrawer、组织映射/员工绑定/JIT 关键字段存在；浏览器复验确认系统后台可进入统一认证页并打开系统统一认证策略抽屉 |
| 平台 / 系统 Agent 运行与确认边界 | 通过；静态检查和浏览器复验确认平台工作台和平台消息使用 platformRuntimeAgentDrawer，平台任务确认使用 platformAgentConfirmDrawer 且不含系统业务写入语义；系统业务页使用 runtimeAgentDrawer 和 agentWriteConfirmDrawer |
| 六角色评审 P0 修复 | 通过；平台消息中心已拆出 platformMessageCenterDrawer，不直接跳系统业务详情；系统切换通过 data-switch-system 生成 SystemSwitchContext 后进入业务页；平台角色直接进入 systemBusiness 会被拦截 |
| 行点击与流程节点 | 通过；全局 tbody cursor 已移除，仅显式数据列表有行点击手势；待办行已补 rowDrawer；流程画布补条件标签，条件/字段更新/API/超时节点有专属 drawerDetails |
| 1.7.23 细节补齐 | 通过；新增消息模板、密钥轮换、无成员申请和按钮结果链路 | 静态检查：新增抽屉引用无断链；IdentityProviderSecretRef、SecretRotationJob、NoMemberAccessRequest、notificationTemplateDrawer、message_delivery_log 均可检索 |
| 1.7.24 干净上下文复审补齐 | 通过；补齐系统壳重绘、TenantSwitchContext、accountMemberBindingId、AgentPolicyScope、OpenApiSecretRef、DailyReportAutoSourceRule、消息筛选状态、分页结果和 defaultDrawer 阻断语义 | 静态检查：新增引用无断链；data-toast=0；关键术语可检索；消息卡片均有跳转目标 |
| 1.7.24 浏览器复验 | 通过；系统管理员登录进入系统业务页；切换到合同系统后品牌、模块分组、左侧模块、统计入口和上下文均刷新；租户切换抽屉含 TenantSwitchContext；系统消息筛选有状态反馈，消息卡片按钮 0、整条跳转 4 | in-app browser：`http://127.0.0.1:58954/index.html` |
| 1.7.24 八角色最终锁版复审 | 通过；PM、业务分析、UI/UX、DBA、后端、前端、测试无 P0/P1 内容阻断，计划角色仅因签字/API/任务计划 gate 未打开而阻断 | 见 `docs/design/reviews/prototype-latest-2026-06-18.md` 第 38 节；签字后开发计划草案见 `docs/design/post-approval-development-plan.md` |

## 9. 签字后行动

用户确认后，将 `docs/design/user-approval.md` 改为 `approved: true`，再同步 `.cursor/session/state.json` 的 `design_user_approved=true`。之后进入 API 契约冻结，至少先产出：

1. 账号、系统、租户、成员、角色、权限模型 API。
2. 模块组、模块、字段、字典、页面配置 API。
3. 运行态列表、详情、导入导出、附件 API。
4. 流程配置、流程实例、待办、审批处理 API。
5. 工作管理、日报、任务、看板 API。
6. AI Agent 配置、运行态对话、写入确认、审计 API。
7. 统一认证 / 企业 SSO、日志、后台任务、上线保障和 OpenAPI 契约。

未签字前，不创建 backend/frontend/sql 实现任务，不进入 coding。
