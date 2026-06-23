# 最新原型复审

> 日期：2026-06-18
> 审阅对象：`docs/design/prototypes/index.html`
> 使用 brief：`docs/design/prototype-brief.md`，版本 `1.7.24-clean-pre-coding-review-fixes`
> 结论：当前有效版为 `1.7.24-clean-pre-coding-review-fixes`。本轮采用干净上下文开发前复审，只读落盘文件和当前原型，补齐系统壳重绘、租户切换上下文、无成员申请生命周期、Agent 策略范围、OpenAPI 密钥引用、消息筛选状态、分页按钮结果和 defaultDrawer 阻断态；仍需用户确认签字，未签字前不进入 API 和开发。

## 当前唯一开发口径（1.7.24）

> 作用：给后续 API 契约和 coding 阶段防跑偏。本文下方保留的早期复审段落是历史记录，只用于追溯问题来源；若与本节、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`docs/design/user-approval.md` 或当前原型冲突，以本节和当前 brief/原型为准。

- 设计输入唯一来源：`docs/design/prototype-brief.md`，版本 `1.7.24-clean-pre-coding-review-fixes`。
- 当前原型唯一入口：`docs/design/prototypes/index.html`。
- 开发前交接矩阵：`docs/design/pre-coding-readiness.md`，不是新的需求来源，只做证据索引，不替代 brief 和原型。
- 四套壳固定：平台工作台、平台后台、系统业务页、系统后台。
- 消息固定为顶部入口打开右侧消息流抽屉，不做平台消息/系统消息表格页；平台消息中心与系统消息中心拆开。平台消息不得直接跳系统业务详情，涉及系统业务时先进入系统切换或平台授权/平台任务对象；系统消息在系统上下文内整条点击跳转业务对象，卡片内不放重复小按钮。
- 待办固定为独立工作台：左侧待办类型，右侧待办列表和筛选；进入待办后隐藏业务模块侧栏。
- 工作管理固定为四个主标签：仪表盘、项目任务、普通任务、日报；项目任务和普通任务的列表/看板互斥切换，日报只展示我的日报和筛选。
- 后台日志固定为一个“日志管理”入口，平台后台和系统后台都采用“左侧日志类型/审计范围 + 右侧统一列表”的组织架构式布局；左侧日志类型树是唯一主分类，右侧不再重复放日志类型下拉或登录/业务页签。
- 统一认证 / 企业 SSO 固定为平台身份源 + 系统继承映射两层：登录页只消费已发布身份源；平台后台配置身份源、协议、证书/JWKS、回调、域名、属性映射、JIT、MFA、测试发布和登录审计；系统后台新增一级“统一认证”页，配置系统继承、租户域名、组织映射、员工绑定、systemMemberId、JIT 系统成员策略、无成员映射反馈和登录日志 traceId。系统统一认证左侧树必须能刷新右侧配置视图。
- 平台运行态 Agent、平台任务确认和系统运行态 Agent 固定拆分：平台 Agent 只处理平台授权、平台日志、平台任务、模型额度、系统健康和系统切换引导；不得直接打开 `vehicleList` 等系统业务模块或写入业务数据。平台 Agent 只进入 `platformAgentConfirmDrawer` 生成平台任务、平台消息和平台日志；系统 Agent 才能在系统切换后的当前系统成员上下文内查询或写入业务模块，并进入 `agentWriteConfirmDrawer` 做业务字段差异和业务日志确认。
- 后台入口固定在个人信息弹层中按权限显示；普通成员看不到系统后台和平台后台；平台普通成员看不到平台后台。
- 系统切换固定为唯一多系统入口，必须生成 `SystemSwitchContext`，包含 systemId、tenantId、systemMemberId、effectiveRoleIds、dataScope 和权限快照；平台账号没有系统成员上下文时不得直接打开系统业务页。
- 多租户系统必须生成 `TenantSwitchContext`；系统切换和租户切换必须重绘业务壳品牌、顶部模块分组、左侧模块、列表数据、待办、消息和字段权限，不得只是换标题或跳 URL。
- 系统切换列表必须体现 `accountMemberBindingId`；平台账号、系统成员、系统角色、租户数据范围之间的绑定关系不能由前端临时拼。
- 列表详情主动作固定为整行点击，但只对明确标记的数据列表或业务核心列表启用；操作列不得再用“详情 / 查看 / 打开 / 进入”承接同一详情动作，只保留处理、配置、编辑、撤回、导出、演练记录、风险复核等差异动作。
- 流程画布必须显示条件分支标签；条件、字段更新、外部 API、超时提醒等节点必须有专属配置承接。
- 统一后台任务固定包含 taskId、bizType、idempotencyKey、status、progress、cancelable、resultFile、errorFile、createdBy、createdAt、traceId、auditLogId、retryable 和回滚边界。
- 消息模板与通知渠道固定为可配置对象：templateCode、平台/系统层级、变量、站内消息/待办/邮件/短信/Webhook 渠道、跳转目标、去重键、已读回执、免打扰、失败重试和 `message_delivery_log`。
- SSO、OpenAPI、Webhook、外部模型等密钥固定使用 SecretRef 和版本表达；密钥轮换固定走 `SecretRotationJob`，不得在页面或日志回显明文。
- 无系统成员映射固定走 `NoMemberAccessRequest`；认证成功但没有 systemMemberId 时只能申请加入，不能自动进入系统业务页或分配业务权限。
- `NoMemberAccessRequest` 固定包含 requestId、status、identityProvider、externalUserId、targetSystemId、tenantId、requestRole、approverId、approveResult、roleIds、dataScope、rejectReason 和 traceId。
- 系统 AI Agent 固定保存 `AgentPolicyScope`：moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略和策略版本。
- 外部应用固定用 `OpenApiSecretRef`、版本、轮换状态、到期和最近使用表达密钥，不展示明文 appKey/secret。
- 消息筛选、全部已读、归档、分页上一页/下一页、加载更多等按钮必须有可见状态变化或结果承接。
- `defaultDrawer` 固定为设计缺口阻断态，不是开发兜底详情；P0 入口落到 default/generic 时不能进入 coding。
- 关键提交按钮固定要有明确承接：同步保存结果、后台任务、发布检查、日志追踪或专属抽屉，不能落到泛化“操作已响应”。
- 字段类型注册表固定包含存储形态、筛选/排序操作符、默认值校验、权限能力和导入导出规则。
- 签字闸门固定：`docs/design/user-approval.md` 中 `approved: false` 时，`.cursor/session/state.json` 的 `design_user_approved=false`、`api_frozen=false` 仍禁止 API、后端、前端和 SQL coding。

## 38. 最终锁版复审：八角色结论与签字后开发路线

> 时间：2026-06-23
> 版本：`1.7.24-clean-pre-coding-review-fixes`
> 复审方式：按用户要求拉起 PM、业务分析、UI/UX、计划、DBA、后端、前端、测试八类角色，只读落盘文件和当前原型，不继续依赖聊天记忆。

### 角色结论

| 角色 | 结论 | 是否需要 PM/用户变更裁决 |
|---|---|---|
| PM | LOCK_WITH_NOTES | 不需要。未发现 P0/P1 产品阻断点。 |
| 业务分析 | LOCK_WITH_NOTES | 不需要。系统切换、无成员申请、权限快照等进入 API 冻结细化。 |
| UI/UX | LOCK_WITH_NOTES | 不需要。四套壳、导航、列表、详情、待办、消息、日志、流程、后台布局可锁版。 |
| 计划/排期 | BLOCK（仅门禁） | 不是设计阻断；阻断原因是用户签字、API 冻结、任务计划 gate 尚未打开。 |
| DBA | LOCK_WITH_NOTES | 不需要。数据模型无 P0，动态字段、SecretRef、流程快照、消息目标进入 API/DB 冻结。 |
| 后端 | LOCK_WITH_NOTES | 不需要。权限快照、上下文、任务状态机、SSO/JIT、Agent 写入边界进入 API 冻结。 |
| 前端 | LOCK_WITH_NOTES | 不需要。无断链、无重复详情小按钮、消息卡片无内嵌跳转按钮。 |
| 测试 | LOCK_WITH_NOTES | 不需要。签字后补正式 E2E 脚本和验收截图/日志规范。 |

### 最终判断

- 当前原型可以作为“锁版候选”进入用户签字；八角色没有提出新的 P0/P1 设计阻断点。
- 计划角色的 BLOCK 只来自流程门禁：`docs/design/user-approval.md` 仍为 `approved: false`，`.cursor/session/state.json` 仍为 `design_user_approved=false`、`api_frozen=false`、`tasks_planned=false`。
- 因此本轮不需要提交 PM 变更裁决；需要用户明确签字后，才能进入 API 契约冻结。

### 签字后开发路线

签字后不直接 coding，先执行：

1. API 契约冻结 C1：账号、系统、租户、成员、角色、权限、系统切换、有效权限快照。
2. API 契约冻结 C2：模块组、模块、字段、字典、页面配置、流程、待办、消息、工作、AI Agent、SSO、日志、后台任务。
3. 任务计划：由冻结后的 API/数据模型拆正式 `docs/tasks/plan.md`，再划分并行批次。
4. 分批开发：先基础壳和权限上下文，再后台配置，再运行态业务，再流程/消息/工作/Agent，最后验收与回归。

设计阶段的签字后开发计划草案见：`docs/design/post-approval-development-plan.md`。该文件不是正式开发任务单；正式任务单必须在用户签字和 API 契约冻结后再生成。

## 37. 干净上下文开发前复审：系统壳、消息、分页、租户与 Agent 契约补齐

> 时间：2026-06-23
> 版本：`1.7.24-clean-pre-coding-review-fixes`

### 本轮方法

- 按用户要求不再只依赖当前聊天上下文，拉起干净视角复审：产品架构、UX、权限/数据模型、QA 验收分别只读落盘文件和当前原型。
- 复审重点从“有没有页面”提升到“能否让正常企业用户用起来、能否让 API/开发少猜字段和状态”。
- 复审结论直接回写原型、brief、design-package、user-approval、pre-coding-readiness、state、project rules 和 failure lessons。

### 本轮发现

- 系统切换能进入业务壳，但不同系统之间的品牌、模块分组、左侧模块和列表数据刷新不够明确，容易开发成只换标题。
- 多租户系统缺少 `TenantSwitchContext` 的开发口径；系统切换和租户切换之间的边界不够稳。
- 无系统成员申请已有入口，但申请状态、审批人、审批结果、角色和数据范围分配字段还不够完整。
- 系统 AI Agent 有配置页，但 `AgentPolicyScope` 不是一级契约字段，后续开发容易只做模块范围，不做字段/动作/数据范围和外发限制。
- 外部应用仍容易被读成 appKey/secret 明文配置，需要显式改为 `OpenApiSecretRef` 和轮换状态。
- 消息筛选、全部已读、归档、分页按钮、工作保存/提交等部分按钮需要可见状态结果，不能只“能点”。
- 注册创建系统后的初始化引导缺少“发布业务首页、普通成员预览、发送登录入口、回滚方案”的最后上线闭环。
- `defaultDrawer` 过去容易被当成兜底详情，本轮改为设计缺口阻断态。

### 已补齐

- 原型新增/强化 `systemShells` 和 `applySystemContext`，系统切换后刷新系统名称、顶部模块分组、左侧模块、统计和消息上下文；`systemSwitchDrawer` 展示 `accountMemberBindingId`。
- 系统业务壳新增租户切换入口和 `tenantSwitchDrawer`，系统信息页展示 `TenantSwitchContext`。
- `NoMemberAccessRequest` 补 requestId、status、identityProvider、externalUserId、targetSystemId、tenantId、requestRole、approverId、approveResult、roleIds、dataScope、rejectReason、traceId。
- 系统 AI Agent 列表和发布检查补 `AgentPolicyScope`：moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略、策略版本。
- 外部应用和密钥轮换补 `OpenApiSecretRef`、version、rotationStatus、expiresAt、lastUsedAt。
- 平台/系统消息中心补筛选状态、全部已读、归档反馈；消息卡片继续保持整条点击跳转，不加重复小按钮。
- 工作管理、项目任务、普通任务、日报和列表分页补 `data-page-action` / `data-page-result`；保存、提交、归档等动作补 `data-action-result`。
- 系统初始化引导新增“发布业务首页”步骤，并补 `publishCheckDrawer` 系统上线发布检查。
- `defaultDrawer` 文案改为设计缺口阻断态，落到该抽屉代表不能进入 coding。

### 验证结论

- 静态检查：脚本可解析；页面、screen、page、subpage、workSubpage、taskView、drawer、message jump 引用无断链；`data-toast=0`。
- 关键术语可检索：TenantSwitchContext、accountMemberBindingId、AgentPolicyScope、DailyReportAutoSourceRule、OpenApiSecretRef、消息筛选、后台任务状态、设计缺口阻断态。
- 消息卡片均有跳转目标；未发现卡片内重复“进入详情/打开任务/查看日志”小按钮。
- 浏览器复验：系统管理员登录进入系统业务页；切换到合同系统后品牌、模块分组、左侧模块、统计入口和上下文均刷新；租户切换抽屉含 TenantSwitchContext；系统消息筛选有状态反馈，消息卡片按钮 0、整条跳转 4。
- 当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 36. 开发前细节补齐：消息、密钥、无成员申请与按钮结果链路

> 时间：2026-06-23
> 版本：`1.7.23-pre-coding-detail-fixes`

### 本轮发现

- 消息中心已有系统/租户/模板筛选，但消息模板、通知渠道、去重、已读回执、失败重试和跳转目标还没有作为配置对象落到原型。
- SSO 和外部应用中有“密钥/证书/轮换”描述，但开发仍可能把密钥当普通字段保存；缺少 SecretRef 和轮换任务对象。
- 系统切换中的未授权系统容易复用平台授权详情，缺少“认证成功但没有 systemMemberId”的专属申请对象。
- 部分主按钮虽然不再用 `data-toast`，但仍可能走泛化“操作已响应”，开发看不出该接同步保存、后台任务、发布检查还是日志追踪。

### 已补齐

- 平台配置管理和系统信息页新增“消息模板与通知渠道”入口；流程管理也提供消息模板入口。新增 `notificationTemplateDrawer`，覆盖 templateCode、模板类型、变量、渠道、跳转目标、去重键、已读回执、免打扰、失败重试和 `message_delivery_log`。
- 平台 SSO 配置新增 `IdentityProviderSecretRef`；身份源列表新增“轮换密钥”；新增 `secretRotationDrawer`，覆盖 `SecretRotationJob` 的创建新版本、双写验证、切换生效、停用旧版本、失败回滚和审计字段。
- 系统切换未映射系统改为 `noMemberRequestDrawer`，显式形成 `NoMemberAccessRequest`，没有 systemMemberId、角色和数据范围时不可进入系统业务页。
- 高级筛选、列设置、转移、批量编辑、审批处理、AI 写入、字典发布、Agent 发布、体检、恢复演练、部署策略、API 契约草稿等关键按钮已补 `data-action-result` 或指向后台任务/日志/发布抽屉。
- brief、design-package、user-approval、pre-coding-readiness、project rules 和 failure lessons 已同步本轮新增边界。

### 验证结论

- 静态检查：新增抽屉引用无断链；`IdentityProviderSecretRef`、`SecretRotationJob`、`NoMemberAccessRequest`、`notificationTemplateDrawer` 和 `message_delivery_log` 均在原型中显式出现。
- 剩余未加数据动作的按钮主要是分页和消息分类筛选按钮，不是关键提交动作。
- 当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 35. 六角色原型评审与上下文边界修正

> 时间：2026-06-23 本轮按产品架构、UI/UX、身份/SSO、流程/工作、开发契约、QA 验收六个角色复审
> 版本：`1.7.22-role-review-context-fixes`

### 本轮发现

- 平台工作台消息入口复用系统消息抽屉，系统业务消息可直接打开 `vehicleDetailDrawer`，绕过系统切换和 systemMemberId 上下文。
- 系统切换列表按钮只是 `data-screen="systemBusiness"`，没有把 systemId、tenantId、systemMemberId、effectiveRoleIds、dataScope 和权限快照显式落成可验收对象。
- 待办列表存在只能点操作列办理的风险，系统待办车辆行和平台待办行缺少稳定 row-level 主动作承接。
- CSS 对所有 `tbody tr` 都显示可点击手势，说明表、配置矩阵和普通状态表会给开发错误信号。
- 系统统一认证左树更像目录，点击后没有真正刷新右侧配置视图。
- 流程画布没有可见条件分支标签，条件、字段更新、外部 API、超时提醒等节点仍容易被实现成同一套审批属性。

### 已补齐

- 平台顶部消息入口改为 `platformMessageCenterDrawer`，系统业务页继续使用 `messageCenterDrawer`。平台消息不得直接打开系统业务详情，只能进入系统切换、平台授权、平台任务或平台 Agent。
- `systemSwitchDrawer` 的进入按钮改为 `data-switch-system`，携带 systemId、systemName、tenant、systemMemberId、roles、dataScope 和 targetPage；脚本在进入系统业务页前写入 `state.systemContext`，平台角色直接访问 `systemBusiness` 会被无权限拦截。
- 系统业务顶部新增当前系统上下文标签；系统切换抽屉补 `SystemSwitchContext`、`EffectivePermissionSnapshot`、`MessageTodoScope` 三类冻结对象提示。
- 平台待办和系统待办行补 `data-row-drawer`，整行点击成为主动作；操作列保留办理、处理等差异动作。
- 全局 `tbody tr` 可点击手势移除，只对明确数据列表、业务核心列表、日志和配置对象表开启行点击样式。
- `sysSso` 页面增加 `data-sso-panel`，左侧身份源/JIT/组织映射/员工绑定/审计节点会过滤右侧配置区。
- 流程画布补条件分支线标签，条件、字段更新、外部 API、超时提醒分别进入 `flowConditionNodeDrawer`、`flowUpdateNodeDrawer`、`flowApiNodeDrawer`、`flowTimerNodeDrawer`。
- 导入导出补下载模板、错误文件、批次回滚/不可回滚原因、导出格式和结果文件入口；工作看板补列字段、泳道字段、字典版本和停用历史提示。

### 签字前剩余动作

- 仍需用户在 `docs/design/user-approval.md` 签字后，才允许进入 API 契约冻结。
- API 冻结时优先对象化 `SystemSwitchContext`、`EffectivePermissionSnapshot`、`IdentityProviderSecretRef`、`NoMemberAccessRequest`、`LoginAudit`、统一后台任务模型和流程节点快照。

## 34. 系统后台 SSO 配置与组织架构联动补齐

> 时间：2026-06-22 本轮针对用户指出“系统后台没有 SSO 配置页面，SSO 要和组织架构联携”的问题修正
> 版本：`1.7.21-system-sso-org-link`

### 本轮发现

- 1.7.20 已有平台后台统一认证身份源配置，也在系统信息页展示了 SSO 继承摘要，但系统后台没有一级 SSO 配置页。
- 系统级 SSO 的关键不是证书和协议密钥，而是把平台身份源映射到当前系统组织架构、系统员工、systemMember、角色初始策略和无权限反馈；只放系统信息页会让 API 阶段漏掉组织映射和员工绑定对象。

### 已补齐

- 系统后台左侧新增“统一认证”，放在“组织架构”之后、“角色管理”之前。
- 新增 `sysSso` 页面：左侧认证配置树，右侧展示身份源继承策略、JIT 成员创建策略、组织架构联动映射、员工绑定与角色初始策略。
- 新增 `systemSsoPolicyDrawer`：覆盖发布检查、组织同步预检、未匹配部门处理、未绑定员工处理、无权限反馈、登录日志 traceId 和 API 对象拆分。
- 系统信息页和系统体检中的 SSO 入口改为跳转 `sysSso`，不再直接复用平台 `ssoConfigDrawer`。
- 系统初始化引导新增“统一认证”步骤，放在组织架构之后、角色权限之前。
- brief、design-package、user-approval、pre-coding-readiness、原始需求、项目规约和失败教训同步沉淀。

### API 阶段优先冻结

- `system_sso_policy`：systemId、identityProviderId、tenantDomainRules、loginMode、mfaPolicy、noMemberFeedback、requestAccessEnabled。
- `org_external_mapping`：systemId、identityProviderId、externalDeptId、externalDeptCode、systemDeptId、mappingStatus、syncStrategy。
- `account_member_binding`：identityProviderId、externalUserId、platformAccountId、systemMemberId、employeeId、bindingStatus。
- `sso_jit_rule`：平台账号 JIT、系统成员待审核草稿、默认访客权限、角色分配审批。
- `sso_sync_task`：组织同步预检、差异、失败原因、可重试状态、traceId、auditLogId。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 33. 平台 Agent 任务确认与系统业务写入确认拆分

> 时间：2026-06-22 本轮针对开发前复筛发现的平台 Agent 确认页复用风险修正
> 版本：`1.7.20-platform-agent-confirm-boundary`

### 本轮发现

- 1.7.19 已把平台运行态 Agent 从系统业务 Agent 中拆出，但平台 Agent 的“生成平台任务草稿”按钮仍跳到 `agentWriteConfirmDrawer`。
- `agentWriteConfirmDrawer` 是系统业务写入确认，内容关注字段差异、审批中字段、业务日志和业务对象写入。平台 Agent 如果复用它，API 阶段容易把平台任务草稿和系统业务写入做成同一套确认接口。

### 已补齐

- 平台 Agent 的主确认按钮改为 `platformAgentConfirmDrawer`。
- 新增 `platformAgentConfirmDrawer` 专属模板，展示来源对话、确认范围、确认人、截止时间、风险级别、平台审计链路、拟生成的平台任务和禁止越界写入说明。
- `agentWriteConfirmDrawer` 保留给系统业务 Agent、工作 Agent 等需要写入当前系统业务数据或工作数据的场景。
- brief、design-package、user-approval、pre-coding-readiness、原型元数据和 session state 同步升级到 1.7.20。

### API 阶段优先冻结

- `platform_agent_task_confirm`：只生成平台 task/message/audit_log，不携带 system business module write payload。
- `system_agent_write_confirm`：必须携带 systemId、tenantId、systemMemberId、moduleId、objectId、fieldDiff、permissionSnapshot 和 auditLogId。
- 两类确认接口的权限校验、审计归属、失败补偿和消息通知必须分开，不能只靠一个 `agent_confirm` 泛接口兜底。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 32. 平台 / 系统运行态 AI Agent 边界复扫

> 时间：2026-06-22 本轮针对开发前复筛发现的平台 Agent 跨系统业务入口风险修正
> 版本：`1.7.19-agent-runtime-boundary`

### 本轮发现

- 平台工作台顶部的 AI Agent 与系统业务页顶部的 AI Agent 共用 `runtimeAgentDrawer`，而该抽屉内容是车辆档案录入、待年检统计和“打开车辆列表”。
- 平台消息流里“平台 / Agent 运行”消息也跳向 `runtimeAgentDrawer`，会让平台侧消息打开系统业务 Agent。
- 这会绕过“系统切换 -> 系统成员映射 -> 系统角色 -> 字段权限/数据范围”的边界，容易让 API 阶段误把平台身份当成系统业务身份。

### 已补齐

- 平台工作台顶部 AI Agent 改为 `platformRuntimeAgentDrawer`，内容只展示平台授权、平台任务、登录风险、模型额度、系统健康和系统切换引导。
- 系统业务页顶部仍使用 `runtimeAgentDrawer`，只有在 `systemBusiness` 上下文内才展示车辆档案录入、待年检统计和业务写入确认。
- 平台消息流中的平台 Agent 结果改为跳 `platformRuntimeAgentDrawer`，不再跳系统业务 Agent。
- brief、design-package、user-approval、pre-coding-readiness、原始需求、项目规约和失败教训同步沉淀平台/系统 Agent 运行边界。

### API 阶段优先冻结

- `agent_runtime_scope`：platform / system / work / detail。
- `agent_context`：platformUserId、systemId、tenantId、systemMemberId、roleIds、dataScope、fieldPermissionSnapshot。
- 平台 Agent 工具白名单：系统授权、平台日志、平台任务、模型额度、系统健康、系统切换引导。
- 系统 Agent 工具白名单：仅当前系统成员有权访问的模块、字段、动作、数据范围和确认策略。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 31. 统一认证 / 企业 SSO 身份源配置补齐

> 时间：2026-06-22 本轮针对用户指出的企业 SSO 配置缺口复审
> 版本：`1.7.18-sso-identity-config`

### 本轮发现

- 登录页已有“企业 SSO”按钮，`loginMethodDrawer` 也能展示 SSO 跳转、回调、账号绑定和登录审计，但这只是运行态登录入口。
- 平台后台配置管理缺少“统一认证 / 企业 SSO”身份源配置，无法表达协议、证书、回调、域名白名单、属性映射、JIT、MFA、发布状态和测试结果。
- 系统信息页只写了 SSO 连通性，没有表达系统如何继承平台身份源、如何限制租户域名、如何映射系统成员，以及无系统成员映射时怎么反馈。
- 系统体检里“短信 / SSO”的修复入口指向登录方式抽屉，容易让开发把 SSO 当成普通登录按钮，而不是平台级身份源配置对象。

### 已补齐

- 平台后台配置管理新增“统一认证与企业 SSO”配置行，入口指向 `ssoConfigDrawer`。
- 新增 `ssoConfigDrawer` 专属抽屉，覆盖 OIDC、SAML 2.0、OAuth2、LDAP / AD、企业微信 / 钉钉，字段包括 issuer/entityId、clientId/appId、回调地址、证书/JWKS、域名白名单、MFA、发布状态、系统继承范围、属性映射和 JIT 成员策略。
- `ssoConfigDrawer` 增加身份源列表和测试发布检查，明确回调连通性、证书有效期、属性映射样本、系统成员映射、无权限反馈和登录日志 traceId。
- 系统信息页补“认证方式继承”“成员映射策略”“JIT 与无权限处理”，把平台账号、系统成员、系统角色和数据范围链路表达清楚。
- 系统上线保障新增“统一认证 / SSO 继承”行，系统体检中“短信 / SSO”修复入口改为认证配置。
- `loginMethodDrawer` 明确登录页只消费平台后台已发布身份源，不承载配置发布。
- brief、design-package、user-approval、pre-coding-readiness 和项目规约同步更新 1.7.18 口径。

### API 阶段优先冻结

- `identity_provider`：身份源协议、连接参数、证书/JWKS、启停和发布状态。
- `system_sso_policy`：系统继承范围、租户域名限制、JIT 策略、无成员映射反馈。
- `account_binding`：externalUserId、email、mobile、employeeNo 到平台账号和系统成员的映射。
- `sso_test_result`：回调测试、证书有效期、属性样本、成员映射样本、失败原因和 traceId。
- `login_audit`：identityProvider、externalUserId、认证方式、MFA、映射结果、IP、设备、requestId、traceId。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 30. 平台/系统日志布局统一与多角色开发前评审

> 时间：2026-06-22 本轮按用户要求拉起产品架构、UI/UX、权限流程、开发可实现性、测试运维五个视角复审
> 版本：`1.7.17-platform-log-layout-review`

### 本轮发现

- 平台后台日志原先是两个并排卡片，虽然有登录日志和业务日志，但不像组织架构的左树右表，筛选和详情承接也偏泛。
- 初改后出现“左侧日志类型树 + 右侧日志类型下拉 + 登录/业务页签”三套主分类，评审认为会让开发产生多套状态源。
- 系统后台日志仍是单面板页签，和平台日志不一致；系统登录日志也缺少显式 traceId。
- 后台任务反馈少于 brief 要求，缺 idempotencyKey、cancelable、resultFile、errorFile、createdBy、createdAt、auditLogId 等字段。
- 原型脚本此前对所有 `tbody tr` 默认打开兜底详情，开发时容易让配置说明表、状态表误触发行详情。
- 字段类型清单还缺建模级矩阵，API 冻结时可能反复补存储形态、筛选操作符、权限和导入导出规则。

### 已补齐

- 平台日志改为左侧日志类型/审计范围，右侧统一列表、统一筛选、列设置、导出和行点击详情；移除右侧日志类型下拉和日志页签主分类。
- 系统日志同步改为同款左类型树/右统一列表；系统登录、业务、风险、导入导出/后台任务、AI Agent 均进入统一日志列表，并显式展示 traceId。
- 日志详情补 requestId、traceId、auditLogId、IP/设备、字段差异、脱敏结果、权限快照、错误原因、处理人和下一步动作。
- 日志导出提交后进入后台任务反馈；后台任务模型补 idempotencyKey、cancelable、resultFile、errorFile、createdBy、createdAt、auditLogId 等字段。
- AI 写入确认补确认人、确认时间和确认范围，避免只在日志里隐含。
- 通用状态样例补“禁用状态”，明确 disabledReason、disabledSource 和审计边界。
- 字段类型区域补存储形态、筛选/排序操作符、默认值校验、权限和导入导出矩阵。
- 行点击脚本收紧为仅明确标记的数据表或业务核心列表启用；无目标的数据行不再默认打开 genericBizDrawer。
- brief、design-package、user-approval、pre-coding-readiness 同步更新 1.7.17 口径。

### 剩余进入 API 阶段时优先冻结

- 有效权限返回结构。
- 审批实例/任务快照。
- 日志审计字段统一模型。
- 统一后台任务模型。
- 字段类型注册表和筛选表达式。
- 配置项版本/发布/回滚模型。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 27. 开发前架构框架扫描：契约治理与移动效率补齐

> 时间：2026-06-22 本轮继续扫描
> 版本：`1.7.16-pre-coding-action-dedup`
> 原型：`docs/design/prototypes/index.html`

本轮在上一轮上线保障基础上继续按“coding 后不返工”的口径复查，重点找会影响接口契约、部署验收和普通用户高频路径的缺口。

本轮发现并补齐：

- 平台配置管理和系统信息页虽然已有体检、备份、归档、配额，但还缺少多环境、部署回滚、生产占位密钥检查和破坏性脚本确认的可见结构；已新增“多环境与部署回滚”配置行和 `deploymentPolicyDrawer` 专属模板。
- API 规范、错误码、幂等和缓存策略只在需求文档中完整，原型没有专属承接，容易导致 API 契约阶段各模块临时定义；已新增“缓存策略与 API 规范”配置行和 `apiCachePolicyDrawer` 专属模板。
- 日常效率入口已有全局搜索、快捷创建、最近访问、表单草稿和错误字段定位，但缺少收藏菜单和移动端扫码/拍照上传；已补到快捷创建和附件抽屉，明确扫码/拍照进入附件草稿、业务对象绑定、权限脱敏、失败重试和业务日志链路。
- AI 写入确认中的 Agent 审计用词统一为“策略快照”，避免 API 阶段把策略版本、权限快照、提示词版本和模型授权快照拆散。
- `docs/design/design-package.md` 的“下一步”旧口径已修正：当前已有开发前复审版原型，不默认重新生成；先走 `user-approval` 签字，签字后进入 API 契约冻结和任务拆分。

复验结果：

- 静态结构：脚本语法通过；data-page/data-screen/data-subpage/data-work-subpage/data-task-view/data-jump-page/data-jump-screen/data-drawer/data-jump-drawer 均无断链。
- 静态结构：43 个专属 template、88 个抽屉承接 key；data-toast=0、genericFlowDrawer=0、直接 defaultDrawer 引用=0、message-actions=0、重复“详情”按钮=0。
- 关键词覆盖：多环境、部署回滚、缓存策略、API 规范、错误码、收藏菜单、移动端扫码、拍照上传、策略快照等本轮新增关键字均已在原型可见。
- 浏览器路径：登录页默认 loginScreen，注册/找回入口可见，720px 视口下无外层滚动条。
- 浏览器路径：platform_admin_root 登录进入平台工作台后可进入平台后台配置管理；多环境与部署回滚、API 规范与缓存策略两类入口可见，抽屉能打开并展示生产环境、错误码等开发契约字段。
- 浏览器路径：sys_admin_vehicle 进入系统后台系统信息页；多环境与部署回滚、API 规范与缓存策略入口可见，API 规范与缓存策略抽屉能打开并展示缓存策略。
- 浏览器路径：che 进入系统业务页，快捷创建抽屉能打开并展示收藏菜单、移动端扫码或拍照上传；收窄到当前可见业务页和个人菜单后，无系统后台和平台后台可见入口。
- 浏览器控制台：原型自身无 console error；外层 Statsig 网络超时来自宿主浏览器插件，不属于原型代码。

当前结论：原型继续向“开发契约可抽取”收敛。仍不能直接 coding；`approved=false`、`design_user_approved=false`、`api_frozen=false` 仍是硬闸门。

## 29. 开发前操作列去重加严复核

> 版本：`1.7.16-pre-coding-action-dedup`

### 本轮发现

只检查“详情”按钮还不够，部分列表操作列仍用“查看 / 打开 / 进入”承接和整行点击相同的详情动作，开发时容易继续做成双入口。

### 已补齐

- 日报列表：已提交日报行不再放“查看”，改为“撤回 / 导出PDF / 复用为草稿”等差异动作；整行点击仍打开日报详情。
- 待办提醒：提醒类行不再用“查看”，改为“处理”，整行点击仍进入关联对象详情。
- 配置与审计：备份恢复、部署回滚、API 规范、有效权限、打印记录、搜索结果等入口文案改为“演练记录 / 契约版本 / 权限说明 / 权限预览 / 预览 / 日志定位”等更具体动作。
- brief、design-package、签字清单同步增加“详情 / 查看 / 打开 / 进入”同义动作去重规则。

### 验证结果

- 静态复验通过：脚本语法通过；页面、screen、subpage、workSubpage、taskView、drawer、message jump 引用无断链；data-toast=0；消息卡片内按钮=0；精确重复“详情/查看/打开”按钮=0。
- 浏览器复验通过：登录页无滚动条；che 无后台入口，待办隐藏业务侧栏，消息为右侧消息流；platform_member 无平台后台入口；platform_admin_root 和 sys_admin_vehicle 均从个人信息弹层进入对应后台；系统后台 AI Agent、字典、日志和系统信息关键能力可见。
- `git diff --check` 仅有 Windows LF/CRLF warning，无空白错误。

### 结论

当前原型的详情主入口统一为整行点击；操作列只保留差异动作。仍保持设计闸门：`approved=false`、`design_user_approved=false`、`api_frozen=false`，用户签字前禁止 coding。

## 28. 开发前覆盖锁定：需求词到原型证据补齐

> 时间：2026-06-22 本轮继续扫描
> 版本：`1.7.16-pre-coding-action-dedup`
> 原型：`docs/design/prototypes/index.html`

本轮在 1.7.14 的断链和浏览器复验基础上，继续按“需求词必须能在原型中找到可开发证据”的口径复扫。发现部分能力已经存在但命名不够直接，容易让 API/开发拆分时漏掉字段、筛选或状态，因此补为显式表达。

已补齐：
- 平台配置和系统信息页将“备份与恢复/数据归档/容量与配额”明确为“备份恢复、归档恢复、容量配额”，并补恢复演练、恢复申请、traceId、审计边界。
- 系统生命周期在创建系统承接中明确包含启用系统、停用系统和删除系统，避免只用状态暗示。
- 消息中心明确为消息流，筛选项写成系统筛选、租户筛选、模板筛选；消息整条点击跳转，卡片内不放重复小按钮。
- 模块配置和列表配置补表头排序、整行点击打开详情；操作列继续只保留差异动作。
- 数据字典补“字典类型”筛选文案，字段选项字典、状态字典、标签字典不再只靠左侧分类暗示。
- AI Agent 补“平台级模型授权”和“系统级 Agent 策略”分层，系统级 Agent 不绕过角色、字段、数据范围、脱敏和人工确认。
- 通用状态文案统一为错误状态、禁用状态，便于开发抽统一组件和接口错误模型。
- 同步确认字段类型库、工作管理四标签、登录日志/业务日志、数据库直连、appKey/secret/scope、全部导出和批量操作均已在 brief、原型或签字清单中形成可开发证据。

复验结果：
- 脚本语法通过；页面、screen、subpage、workSubpage、taskView、drawer 引用无断链。
- data-toast=0、genericFlowDrawer=0、直接 defaultDrawer 引用=0、message-actions=0、重复详情按钮=0、href="#"=0。
- 本轮关键术语覆盖通过：消息流、整条点击、系统筛选、租户筛选、模板筛选、启用系统、停用系统、字典类型、表头排序、整行点击、平台级模型、系统级 Agent、容量配额、备份恢复、归档恢复、错误状态均已在原型中可见。

当前仍不进入 coding：`approved=false`、`design_user_approved=false`、`api_frozen=false`。

## 26. 开发前架构框架扫描：上线保障与关键模板补齐

> 时间：2026-06-22 本轮继续扫描
> 版本：`1.7.13-pre-coding-ready-polish`
> 原型：`docs/design/prototypes/index.html`

本轮不再只按页面断链扫描，而是按“完整系统上线后给正常企业用户长期使用”的架构框架复核：平台层、系统层、运行态业务层、配置态管理层、权限身份层、流程审批层、数据集成层、AI Agent 能力层、工作管理层以及上线保障能力。

本轮发现并补齐：

- 平台配置管理虽然有全局配置表，但原型里缺少平台体检、功能开关/灰度、容量配额、限流、备份恢复、归档和日志保留策略的可见结构；已在平台配置管理表格和 `platformOpsDrawer`、`featureFlagDrawer`、`backupRestoreDrawer`、`archivePolicyDrawer` 中补齐。
- 系统信息页虽然有系统基础资料和租户信息，但上线前真正需要的系统体检、服务连通性、最近备份、容量预警、功能开关、归档恢复和扩容申请不够显性；已补“上线保障与运行策略”区和 `systemOpsDrawer`。
- 运行态业务页缺少日常效率入口的可见承接；已补全局搜索、快捷创建/最近访问、表单草稿和错误字段定位，并说明仍受角色权限、字段脱敏和数据范围控制。
- 流程节点属性、审批处理、AI 写入确认、字典发布检查、AI Agent 发布检查、通用状态样例不能只靠 `drawerDetails` 或 default/generic 兜底；已补专属 `<template>` 结构。
- `docs/design/prototype-brief.md`、`docs/design/design-package.md`、`docs/design/user-approval.md`、`.cursor/session/state.json`、`.cursor/knowledge/project-operating-rules.md` 和 `.cursor/knowledge/failure-lessons.md` 同步沉淀本轮架构扫描规则。

静态复验结果：

- `<script>` 语法通过。
- `data-page`、`data-screen`、`data-subpage`、`data-work-subpage`、`data-task-view` 均无断链。
- 75 个抽屉引用均有承接；其中关键动作抽屉均有专属模板，普通说明型抽屉可走 `drawerDetails`，没有未解析抽屉。
- `data-toast=` 数量为 0；`genericFlowDrawer` 残留为 0；`defaultDrawer` 直接引用为 0。
- 上线保障关键词已在原型中可见：系统体检、平台体检、功能开关、灰度、备份恢复、归档、容量、配额、全局搜索、最近访问、快捷创建、表单草稿、错误字段定位。

当前结论：原型已从“页面能点”推进到“开发前可以抽接口和任务”的层级，但闸门仍关闭。正式 coding 前仍必须由用户在 `docs/design/user-approval.md` 签字，然后进入 API 契约冻结和任务拆分。

## 25. 过早 ready 纠偏：补齐泛化入口

> 时间：2026-06-19 用户指出“这么多问题还没处理完”
> 版本：`1.7.12-pre-coding-ready-polish`
> 原型：`docs/design/prototypes/index.html`

本轮纠偏结论：之前只完成了第二轮评审中的部分高风险项，并把若干应该在设计阶段明确的内容误归为 API/开发阶段，这是错误口径。按“下一步要 coding”的标准，关键入口不能只保证能点，也不能只显示通用说明，必须有字段、状态、失败反馈和可开发结构。

已继续补齐：

- 平台 Flow：`genericFlowDrawer` 不再承接平台 Flow 主入口，新增 `platformFlowDrawer`，展示触发源、影响系统、节点连线、幂等键、重试/补偿、平台任务、运行批次和 traceId。
- 注册初始化：注册创建系统后不再只 toast 并跳后台，改为进入系统后台并打开 `systemInitGuideDrawer`，覆盖系统信息、组织架构、角色权限、模块字段、流程字典、邀请成员和发布前检查。
- 登录方式：新增 `loginMethodDrawer` 专属结构，覆盖企业 SSO、短信验证码、MFA、异地登录、无系统成员映射、登录审计和禁用态。
- 有效权限：新增 `effectivePermissionDrawer` 专属结构，包含 `permissionVersion`、`sourceRoleIds`、`denyPolicyIds`、`dataScopeExpression`、`systemMemberId`、字段权限矩阵、按钮禁用原因和解释链路。
- 流程高级运行：新增 `flowAdvancedRuntimeDrawer` 并接入流程管理工具栏，覆盖转办、委托、加签、候选人领取、手动插入节点、子流程、外部 API 重试/补偿、状态机、任务版本和实例版本。
- 文档沉淀：`prototype-brief.md`、`design-package.md`、`user-approval.md`、`project-operating-rules.md`、`failure-lessons.md` 均补充“关键入口不能用通用说明替代”的约束。

复验要求：

- 后续再说“可以 coding”前，必须先验证核心入口不依赖 `genericFlowDrawer`/`defaultDrawer` 兜底。
- API 契约阶段要按本轮新增结构冻结字段和返回值，不能回退为临时 toast 或简单成功/失败。

本轮复验：

- 静态结构：脚本可解析；`data-page`、`data-screen` 无断链；核心新增抽屉 `platformFlowDrawer`、`systemInitGuideDrawer`、`loginMethodDrawer`、`effectivePermissionDrawer`、`flowAdvancedRuntimeDrawer` 均有专属模板；`data-drawer="genericFlowDrawer"` 和 `data-toast=` 数量均为 0。
- 浏览器：使用本地静态服务 `http://127.0.0.1:58952/index.html?audit=pre-coding` 验证，注册创建系统后进入 `systemAdmin` 并打开“系统初始化引导”，内容包含组织架构、角色权限、模块与字段、流程与字典、邀请成员。
- 浏览器：平台管理员登录后进入平台 Flow，点击“新建平台 Flow”打开“平台 Flow 配置”，包含 Flow 名称、触发源、影响系统、幂等键、最近运行结果和 traceId，页面中无 `genericFlowDrawer` 按钮残留。
- 浏览器：系统管理员从个人信息进入系统后台，打开流程管理的“高级运行策略”，抽屉包含转办、委托、加签、候选人领取、手动插入节点、子流程、外部 API 和状态机。
- 文件级：有效权限入口和 `effectivePermissionDrawer` 模板已接入，模板包含 permissionVersion、sourceRoleIds、denyPolicyIds、dataScopeExpression、systemMemberId、字段和动作结果、解释链路；浏览器侧栏可见性工具对该路径误判，未把它记为浏览器通过。

## 22. 再次开发前扫描：配置入口和状态样例补齐

> 时间：2026-06-18 本轮再次扫描
> 版本：`1.7.11-pre-coding-ready-polish`
> 原型：`docs/design/prototypes/index.html`

本轮按“达到可以 coding”的口径复扫，不只看页面有没有，还重点看开发会不会因为设计留白而误做空按钮、空弹窗或各自临时状态。

已调整：

- 模块管理中“配置场景”“配置动作”“发布到业务页”不再只是 toast，改为可打开的配置抽屉，分别说明列表/筛选/场景、页面动作/导入导出/打印、发布检查/灰度/回滚。
- 模块配置新增“状态与异常”入口，明确加载、空状态、无权限、错误、禁用、后台任务态的统一组件语义。
- 工作配置中的发布检查、发布配置、新增字段、保存看板规则均改为可打开的工作字段、工作看板、工作发布检查抽屉。
- 流程管理中的新增审批类型、发布检查、模拟运行、版本记录改为流程类型、发布检查、模拟运行、版本记录抽屉，补齐审批人为空、字段权限、状态回写、异常补偿等开发边界。
- AI Agent 发布检查和字典发布检查补为独立抽屉，明确模型权限、脱敏、写入确认、审计，以及字典引用影响、停用历史、状态颜色语义。
- 平台 Flow 的详情入口从 toast 改为 Flow 配置抽屉。
- brief 和 design-package 同步升级到 `1.7.11-pre-coding-ready-polish`，新增“关键配置入口不能只用 toast”和“通用状态不可后补”的验收项。
- `.cursor/knowledge/project-operating-rules.md` 同步沉淀：后台日志保持一个“日志管理”入口，内部区分登录日志和业务日志。

复验重点：

- 登录页仍无外层滚动条。
- 系统后台仍只有一个“日志管理”入口，内部区分登录日志和业务日志。
- 系统后台仍保留一级“AI Agent 配置”。
- 消息卡片仍为整条点击跳转，卡片内无重复小按钮。
- 工作管理仍保持“仪表盘 / 项目任务 / 普通任务 / 日报”四标签，项目任务和普通任务的列表/看板互斥切换。
- 模块、工作、流程、字典、AI Agent 的关键配置按钮都有抽屉或页面承接，不再只有一句提示。

当前结论：

从设计内容看，已经具备进入开发拆分前的主要信息：入口、角色、导航、业务列表、详情、审批、模块配置、工作配置、流程、字典、AI Agent、日志、状态与异常态都已落到原型和 brief。正式 coding 前仍必须由用户在 `docs/design/user-approval.md` 签字，否则闸门保持关闭。

## 23. 1 小时多角色原型评审与补强

> 时间：2026-06-19 00:31 开始，硬截止 01:31
> 参与视角：产品架构/信息架构、企业后台 UX、权限/流程/配置模型、开发落地/验收状态

本轮用户要求不再逐条人工找 bug，而是由团队视角轮询评审并在 1 小时内保存结果。已按规则启动多角色只读评审，主流程负责整合和落地。

已按评审结果补强：

- 沉淀通用规约：原型绘制、Open Design 生成、设计评审、开发前扫描前必须先以落盘文件重建上下文；多角色评审单轮 1 小时硬上限，到时保存结论并停止无界讨论。
- 平台/系统待办左侧类型从静态展示改为可点击筛选项，并补“超时/即将超时”类型。
- 高级筛选抽屉从简单字段表单升级为筛选构建器，包含部门、驾驶员、车辆状态、购置日期、年检到期、最近维保、字段/操作符/值/AND/OR 组合条件。
- 列设置抽屉升级为真实列配置清单，包含拖拽、显示开关、列宽、固定、排序、导出和字段权限结果。
- 转移、批量编辑、日志列设置、日志导出从 toast 改为可开发抽屉，补权限/状态检查、任务反馈和追踪号。
- 登录演示账号去除 `admin` 命名冲突：平台内置超管改为 `platform_admin_root`，系统管理员示例改为 `sys_admin_vehicle`。
- 角色管理新增“有效权限计算”区域，补多角色合并、显式拒绝优先、数据范围并集、字段权限与审批节点权限冲突处理。
- 流程管理新增“绑定/快照”入口，补绑定规则、优先级/互斥、幂等键、实例快照、任务快照和日志快照。
- 系统切换抽屉补真实系统列表、当前标记、系统角色、租户、进入/申请/禁用原因和切换后上下文刷新规则。
- 平台应用页移除重复系统切换入口；新增/配置授权只对平台管理员显示，平台普通成员只保留查看/申请授权。
- 平台个人信息菜单补个人资料、账号安全、消息偏好。
- 普通任务页补一行快速创建控件，并保留后补项目/标签/业务对象入口。
- `docs/design/user-approval.md` 同步到 `1.7.11-pre-coding-ready-polish` 口径，但保持 `approved:false`，不替用户签字。
- 系统业务页“资产统计”从 toast 主导航改成真实页面，包含统计卡、筛选、导出和统计列表。
- 车辆核心列表移除重复“详情”按钮，保留整行点击打开详情，行内只放编辑等差异动作。
- 字段类型注册表、状态字典流转约束、AI Agent 策略快照、统一配置依赖影响分析已补入原型说明和 brief。
- 导入/导出提交改为统一后台任务反馈抽屉，详情“更多”改为业务动作抽屉。
- 开发前静态自检继续补漏：流程节点点击、审批通过/驳回/转交/更多、AI 确认写入草稿均从 toast 改为可开发抽屉，补节点属性、审批处理表单、状态回写、幂等、失败补偿和审计日志。
- 列表行点击作为详情主动作后，操作列中的“详情”字样已去重，改成配置、处理、编辑、更新、查看日志、查看结果等差异动作，避免开发继续做重复详情按钮。
- 旧参考清理：删除 6 月 17 日旧复审和原型目录旧 brief，`docs/README.md` 指向 2026-06-18 最新复审，知识库种子账号口径统一为 `platform_admin_root`。

开发落地评审结论：

- 当前原型和 brief 已基本足以进入“用户签字后的 API 契约拆分/任务规划”。
- 仍不能直接进入后端、前端、SQL coding：`design_user_approved=false`，且 `api_frozen=false`。
- 下一阶段必须先冻结 API 契约，覆盖接口对象、请求/响应、分页筛选、权限裁剪字段、错误响应、追踪号、异步任务、审计日志、状态枚举和字典种子。

建议新增验收检查：

- `gate-check`：未签字、未 API 冻结时禁止 build/coding 任务。
- `design-sync-check`：校验 state、brief、design-package、review、user-approval 版本和结论一致。
- `no-critical-toast-check`：发布检查、模拟运行、导入导出、审批、AI 写入、流程节点、日志导出等关键动作不得只用 toast。
- `no-p0-default-drawer-check`：P0 表格行点击必须有专属详情或明确规则，不得依赖 `genericBizDrawer/defaultDrawer`。
- `drawer-route-check`：所有 `data-drawer/data-page/data-subpage/data-jump-*` 目标存在。
- `role-path-check`：che、sys_admin_vehicle、platform_member、platform_admin_root 四类角色分别验证入口、字段脱敏、后台权限和禁用原因。
- `row-action-dedup-check`：列表整行点击查看详情后，操作列不得再放重复“详情”按钮，必须是处理、编辑、配置、删除、转移、导出等差异动作。
- `critical-action-drawer-check`：流程节点、审批处理、AI 写入确认、导入导出、日志导出、发布检查等关键动作必须有页面/抽屉/后台任务承接，不能只停留在 toast。

## 21. 日志入口、系统 Agent 和登录页滚动修正

> 时间：2026-06-18 本轮继续反馈
> 版本：`1.7.10-log-agent-login-polish`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：登录日志和业务日志应该合到一个日志管理里；系统内没有清晰的 AI Agent 配置；登录页面仍然出现滚动条。

已调整：

- 系统后台左侧不再拆“登录日志 / 业务日志”两个菜单，改为一个“日志管理”入口。
- 系统日志管理页内部用“登录日志 / 业务日志”页签区分，两类日志共享统一搜索、日志类型、结果、时间、筛选、重置、列设置和导出入口。
- 系统后台保留并强化一级“AI Agent 配置”，页面展示模型来源、可访问模块、允许动作、确认策略、失败降级、审计和发布影响。
- 登录页修正外层高度：`login-wrap` 改为占满当前 screen 高度，取消 `min-height:100vh + padding` 撑高；登录卡片取消内部滚动。注册和找回密码页仍保留卡片内部滚动，避免小屏截断。
- brief、design-package、review 同步升级到 `1.7.10-log-agent-login-polish`。

复验重点：

- 系统后台左侧只能看到“日志管理”，不能再看到独立“登录日志”“业务日志”菜单。
- 点击“日志管理”后，页面内部应能切换登录日志和业务日志。
- 系统后台左侧应能看到“AI Agent 配置”，点击后进入系统级 Agent 配置页。
- 登录页首屏不应出现外层滚动条。

## 20. 开发前冻结审查与补漏

> 时间：2026-06-18 本轮预开发审查
> 版本：`1.7.9-pre-coding-audit`
> 原型：`docs/design/prototypes/index.html`

本轮按“接下来要 coding”做开发前冻结审查，不只检查点击是否可用，而是重点检查会让开发误实现、漏实现或后续返工的设计残留。

已发现并修正：

- 删除平台消息、系统消息的废弃独立页面，消息只保留顶部入口打开右侧消息中心抽屉，避免开发误做表格消息页。
- 维保记录、驾驶员从通用业务详情抽屉拆成专属详情工作区；待办中关联维保/驾驶员的事项也跳到对应专属详情。
- 系统后台新增一级“AI Agent 配置”，明确模型来源、可访问模块、允许动作、确认策略、失败降级和审计边界。
- 平台日志管理页内拆出平台登录日志和平台业务日志，并补筛选、导出、追踪号和详情入口。
- 任务看板说明从“状态列来自当前项目配置”修正为“列字段和泳道字段来自工作配置，颜色图标来自数据字典”，与字段/字典模型一致。
- brief 和 design-package 同步升级到 `1.7.9-pre-coding-audit`，避免原型和文档口径不一致。

开发前仍需人工确认的剩余风险：

- `genericBizDrawer` 和 `defaultDrawer` 仍作为动态模块或未知入口兜底存在；核心已画模块不再依赖它们，但后续新增模块开发前仍要补模块专属详情。
- 部分配置按钮仍用 toast 表示动作结果，例如模块场景配置、页面动作、发布检查、流程版本等；如果首期就开发这些深层配置，需要继续展开成可实现的抽屉或独立页面。
- 空状态、加载态、错误态、无权限态只在局部出现，还没有覆盖到所有列表、抽屉和后台配置页；开发任务拆分时必须补为统一前端组件需求。
- 平台后台和系统后台都保持“日志管理”单入口，页面内区分登录日志和业务日志；不再建议把登录日志和业务日志拆成两个左侧导航。

当前结论：

骨架已经明显接近可开发：统一登录、四类角色、平台/系统分层、业务列表和详情、审批、工作管理、字段/字典/看板、AI Agent、日志拆分都已落入原型。但不建议直接把这版当“所有细节 100% 完成”；建议签字前按上面的剩余风险再人工走查一遍，尤其是首期要开发的深层配置页。

## 19. 工作配置改为字段模型，数据字典承载状态标签

> 时间：2026-06-18 本轮继续反馈
> 版本：`1.7.8-work-field-dict-kanban-source`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：工作配置里不需要统计；工作配置里的状态、标签等应使用数据字典配置，并完善数据字典项的颜色、图标等属性；任务看板应依据配置的字段生成；工作配置主要配置项目任务字段列表、普通任务字段列表，字段中的下拉或多选可以绑定字典，看板选择这些下拉或多选字段来构建数据。

已调整：

- 工作配置页删除统计卡片，不再直接维护项目任务状态、普通任务状态和标签选项。
- 工作配置改为字段模型：项目任务字段列表、普通任务字段列表、日报字段与规则、字段属性、字段权限、看板取数字段。
- 看板构建规则改为字段驱动：列字段和泳道/分组字段只能选择已发布的下拉单选或多选字段，卡片字段来自字段列表。
- 数据字典页增强：新增状态字典、标签字典、字段选项字典；字典项包含颜色、图标、语义、排序、默认项、启停、停用历史、引用影响和看板用途。
- 工作配置抽屉同步改为字段/字典/看板取数模型，避免从模块管理打开旧版统计配置。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`.cursor/knowledge/project-operating-rules.md`、`.cursor/knowledge/failure-lessons.md` 已同步。

浏览器复验：

- 系统后台工作配置页不再出现统计卡片。
- 工作配置页能看到项目任务字段列表、普通任务字段列表、看板构建规则，并明确引用数据字典。
- 工作配置页不再出现旧的“项目任务状态方案”“普通任务状态与标签”标题。
- 数据字典页能看到状态字典、标签字典、字段选项字典，以及颜色、图标、语义、排序等字典项属性。
- 数据字典页能看到看板用途：看板列、泳道/筛选。
- 模块管理中“工作管理”配置入口已跳转到一级工作配置页。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 24. 第二轮 1 小时多角色开发前评审

> 时间：2026-06-19 08:01 启动，硬截止 09:01
> 参与视角：业务流程/普通用户、权限流程配置模型、开发验收、企业后台 UX
> 原型：`docs/design/prototypes/index.html`

本轮目标不是继续按用户逐条找 bug，而是从“完整系统要给正常企业用户长期使用、后续要直接进入 coding”倒推可开发性，重点查入口、权限、配置模型、流程画布、列表状态、任务反馈和业务流畅性。

已发现并落实：

- 平台到系统入口：平台概览和应用页不再直接跳系统业务页，统一打开系统切换；系统切换抽屉展示系统、租户、系统成员/角色、授权状态、进入/申请/禁用原因和切换后刷新上下文。
- 权限身份边界：文档和知识库明确平台账号进入系统前必须有系统成员映射、系统角色和有效数据范围；`platform_admin_root` 不是系统超管，不能绕过系统权限直接查看或修改业务数据。
- 模块模型边界：旧“系统内业务应用 / appId 父级”口径作废；模块组只负责顶部分组、组内模块导航、排序、可见角色和发布关系；业务数据归属 `systemId/tenantId/moduleId`；对外应用只做 OpenAPI 来源和审计。
- 无权限态：直接访问无权限页面补无权限抽屉/403 样例，包含缺失权限、当前角色、申请授权入口和 requestId 语义。
- 树形交互：组织树、角色树、模块树、字典分类和待办类型补节点选择与右侧过滤语义，避免开发做成静态左栏。
- 流程画布：补画布最小宽度、横向滚动和不裁节点约束，避免 1440×900 下超时提醒、结束归档等节点被裁掉。
- 列表和日志状态：车辆列表补当前筛选条件、结果数和分页状态；日志管理补登录方式、风险类型、业务模块筛选和应用结果状态。
- 消息中心：补未读/已读状态变化；车辆导出完成消息跳统一后台任务反馈，不再混入平台任务日志。
- 任务/工作卡片：看板任务卡片点击进入工作任务详情，避免只展示不可操作卡片。
- 后台任务契约：brief 和审批清单补 `taskId`、`bizType`、`idempotencyKey`、`status`、`progress`、`resultFile/errorFile`、`traceId`、`auditLogId`、失败明细和重试/回滚边界。
- 关键配置兜底抽屉：兜底渲染文案从说明页改为“业务结构与表单样例 / 字段规则结果表 / 提交结果与异常反馈”，降低开发误解成只读帮助页的风险。

仍需 API 契约阶段重点冻结：

- 有效权限返回结构建议包含 `permissionVersion`、`sourceRoleIds`、显式拒绝策略、字段权限快照、数据范围表达式和禁用原因。
- 流程高级运行能力如果纳入 P0，需要冻结手动插入节点、委托、候选人领取、子流程回调/父流程恢复、外部 API 重试补偿的状态机和审计字段。
- 字典项历史快照、状态颜色语义、停用项历史展示、AI Agent 外部发送数据快照和 hash 需要在 API/表结构中明确。

本轮复验：

- 静态脚本解析通过：`docs/design/prototypes/index.html` 内联脚本可解析。
- 静态断链检查通过：`data-drawer`、`data-page`、`data-screen` 目标无缺失。
- 关键操作检查通过：原型中不再用 `data-toast` 承接筛选、审批、导入导出、任务、日报、授权、发布、打印、附件等关键动作。
- 闸门复核通过：`docs/design/user-approval.md` 未出现实际 `- approved: true`；`.cursor/session/state.json` 未出现 `design_user_approved=true` 或 `api_frozen=true`。
- 浏览器复验限制：当前 in-app browser 的旧临时端口页已崩溃；直接打开本地 `file://` 原型被浏览器安全策略拦截，因此本轮未绕过策略做浏览器核验，仅保留静态核验结果。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 18. 工作配置、状态颜色、日志拆分和系统切换去重

> 时间：2026-06-18 本轮继续反馈
> 版本：`1.7.7-work-config-status-log-switch-cleanup`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：工作配置要在后台管理添加，配置项目任务状态/标签、普通任务状态/标签，方便看板按标签展示；整个系统状态颜色要统一；后台日志要区分登录日志和业务日志；个人信息里的授权系统和顶部系统切换重复，保留系统切换。

已调整：

- 系统后台左侧新增一级“工作配置”，放在模块管理之后；模块管理里的“工作管理”行不再作为唯一入口，配置按钮跳转工作配置页。
- 工作配置页补齐项目任务状态方案、项目标签、普通任务状态与标签、日报规则、看板方案和全系统状态颜色适配。
- 全系统状态颜色明确为灰/蓝/橙/绿/红/自定义标签六类语义，要求任务、日报、审批、导入导出、日志和状态字典统一引用。
- 系统后台日志从单个“系统日志”拆为“登录日志”和“业务日志”；登录日志关注账号、认证方式、IP、设备、风险和结果，业务日志关注模块、对象、动作、AI Agent、审批、导入导出、追踪号和结果。
- 平台个人信息菜单移除“进入授权系统”；多系统进入只保留顶部“系统切换”。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`.cursor/knowledge/project-operating-rules.md`、`.cursor/knowledge/failure-lessons.md` 已同步。

浏览器复验：

- 平台管理员进入平台工作台后，个人信息菜单只剩“平台后台 / 管理入口”和“退出登录”，没有“进入授权系统”；顶部右侧仍保留“系统切换”。
- 系统管理员进入系统后台后，左侧顺序包含“工作配置、登录日志、业务日志”。
- 点击“工作配置”能进入独立页面，页面包含“项目任务状态方案”“普通任务状态与标签”“全系统状态颜色适配”，并显示 16 个状态颜色标识。
- 点击“登录日志”能进入独立页面，表头包含账号、姓名、登录方式、IP/地点、设备、风险，筛选包含结果、登录方式和时间。
- 点击“业务日志”能进入独立页面，表格包含追踪号，筛选包含 AI Agent、审批、导入导出和 OpenAPI 等业务类型。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 1. 总体判断

这版解决了旧导航混入问题，但还没有达到“开发可照着做”的视觉和交互深度。

上一轮静态验收只验证了路由、脚本和旧导航禁用词，没有覆盖布局比例、树形控件、流程画布连线、导入导出交互位置、详情工作区精细度和 tab 数据一致性。这次用户视觉复审指出的问题成立，需要沉淀到 brief 并重生成或定点修正。

## 2. P0 问题

### 2.1 组织架构布局不合格

当前 `system-admin/org` 和 `platform-admin/org` 使用通用 `.split` + `stepper` 表达部门，左侧不是真树，也没有缩进、展开/收起、父子层级、节点成员数、选中态和节点操作。

左侧区域还复用了通用布局比例，导致右侧员工列表宽度被挤压。组织架构应该是典型“左树右表”：左侧 240-280px 固定部门树，右侧完整员工列表。

### 2.2 流程管理不像流程设计器

当前流程页面只是把开始、条件、审批、外部 API、抄送、结束几个节点放在网格里，没有连线、箭头、条件分支标签，也没有左侧节点库和真正的右侧节点属性联动。

流程配置页应该是设计器结构：上方筛选/流程版本，主体左节点库、中画布、右属性；画布必须能看出流向、分支、条件和节点类型差异。

### 2.3 运行态列表下方不应常驻导入导出面板

当前车辆列表下方直接铺了“导入预检查”和“导出后台任务”两张大面板。这会把一个操作流程误设计成列表常驻内容，不符合主流业务系统列表页设计。

导入应该从工具栏打开抽屉或流程弹窗：选择文件、字段匹配、预检查、错误行、确认导入、批次结果。导出应该从导出下拉/弹窗选择范围、字段、格式和后台任务，任务结果通过 toast、消息或任务结果弹窗反馈。

### 2.4 详情工作区还不够精致

当前详情虽然在右侧，但更像一个普通 panel，工作区没有贴顶贴底贯通，宽度和层次不够像 CRM 详情页。详情应从顶部导航下沿到页面底部展开，保留列表上下文，同时让详情主信息、摘要、tab 和审批面板形成稳定结构。

详情 tab 内容混乱：团队成员、金额/比例、附件、打印记录、操作记录大量复用 `permissionTable` 或 `matrix`，导致看起来像权限配置表，不像车辆业务详情。

## 3. P1 问题

- 角色管理左侧角色列表也用 stepper，真实角色管理应该是角色列表 + 成员/权限配置，不应像流程步骤。
- 模块管理左侧模块树同样用 stepper，应该是真树，和组织架构类似有层级、状态、发布版本。
- 流程节点库数量不足，缺并行网关、字段更新、定时器/超时、子流程、Webhook/API、结束等更完整节点。
- 审批类型配置没有独立表达为列表/详情，只散落在说明中；应包含绑定模块、触发动作、业务状态变更、通知方式、启用状态和版本。
- 详情顶部摘要仍有“客户/部门、金额/状态”这类偏 CRM/合同的字段，应改成车辆业务字段，如归属部门、负责人、车辆状态、年检到期、最近维保、审批状态。
- 运行态左侧模块入口语义错误：维保记录、驾驶员不能指向高级筛选或列设置状态页，必须是独立模块列表/占位页。
- 列设置抽屉不能复用权限矩阵，必须是列名、显示开关、拖拽、列宽、固定列等列配置控件。
- 平台后台组织架构不能混入车辆操作员等系统业务角色。
- 审批面板不能用 stepper 表达，应分为当前节点卡、处理人/超时、审批动作和审批历史时间线。

## 4. 已沉淀到 brief

已更新 `docs/design/prototype-brief.md`，新增以下硬约束：

- 组织架构左树右表比例、真树层级、展开/收起、节点操作，禁止 stepper 代替树。
- 流程设计器必须有左节点库、中间连线画布、右属性面板、分支标签和更完整节点库。
- 审批类型配置必须独立表达，绑定模块、触发动作、业务状态变更和通知方式。
- 运行态列表禁止常驻导入导出面板，导入导出必须通过弹窗/抽屉/下拉和任务反馈表达。
- 详情工作区贴顶贴底、宽度足够、头部 sticky、审批面板固定；tab 必须使用专属业务数据。
- 模块入口、列设置、模块配置七步、平台组织架构和审批面板的禁用复用规则。

## 5. 签字建议

不建议签字。

下一轮建议使用更新后的 `docs/design/prototype-brief.md` 作为唯一输入，并尽量采用无聊天上下文污染的 Open Design 新会话重新生成，重点让它先过布局合理性和业务深度，而不是只过旧导航关键词。

## 6. un11 点击性复审补充

> 时间：2026-06-18 09:21 版 `docs/design/prototypes/index.html`
> 结论：结构较上一版更集中，但点击性仍未通过；已提交 Open Design 定点修复 run `57635280-0a19-4724-b18a-8ffd2af67ee6`。

静态点击审计结果：

- 脚本语法通过。
- 顶部三段切换、模块七步切换、已有 `data-drawer` 抽屉映射基本可用。
- 左侧系统后台 11 个菜单仍是 `href="#"`，没有实际响应。
- 至少 16 个可点击样式按钮没有动作或反馈：筛选、上一页、下一页、复制为新草稿、审批人预览、批量排序、新增场景、查看影响、准备回滚、调整、下载明细、确认导入、查看明细、查看路径、更多等。
- 动态抽屉里的按钮也存在只展示不响应的问题，需要事件委托统一补齐。

本轮 Open Design 修复目标：

- 消除无行为 `href="#"`。
- 所有按钮必须具备 `data-target` / `data-step` / `data-drawer` / `data-action` / id 事件 / disabled+title / aria-label 之一。
- 左侧菜单至少切换到对应内容面板或明确打开对应抽屉/反馈。
- 动态抽屉按钮也必须可点，有 toast、抽屉、状态变化或下载/任务反馈模拟。

## 7. un11 点击性修复复验

> 时间：2026-06-18 09:47 版 `docs/design/prototypes/index.html`
> 处理：Open Design 定点修复 run `57635280-0a19-4724-b18a-8ffd2af67ee6` 已写回页面；后续浏览器自动化验证受本地工具会话限制卡在 `about:blank`，已取消 run，改以文件级结构验收为准。

本轮已补齐：

- 左侧系统后台 11 个菜单全部改为 `data-target`，并有对应内容面板。
- 顶部待办、消息、个人信息、列表筛选/分页、动态七步配置、抽屉内按钮均补充 `data-drawer` 或 `data-action`。
- 组织架构、角色管理、字典、仪表盘、数据源、对外应用、系统日志等后台入口都有实际内容区，不再只是空链接。
- 运行态列表、模块配置、流程画布的动态按钮通过统一事件委托处理，点击后有抽屉、toast、状态变化或任务反馈模拟。

本地静态验收结果：

- `<script>` 语法：1 个脚本块，全部通过 `new Function`。
- 页面目标：12 个 `data-target`，缺失目标 0。
- 抽屉入口：35 个 `data-drawer`，缺失定义 0。
- 死链接：`href="#"` 且无行为入口 0。
- 死按钮：144 个按钮均具备行为、禁用说明或可识别标识，死按钮 0。
- `git diff --check`：通过；仅 `.cursor/session/state.json` 有 LF/CRLF 提示。

当前建议：

- 这版可以继续做人工视觉和业务走查，重点看布局比例、内容是否符合业务，而不是再优先处理“按钮完全点不动”。
- 浏览器自动化未能稳定验证本地 `file://` 页面，原因是 in-app browser 阻止本地文件、agent-browser 默认会话回到 `about:blank`；这属于验证通道限制，不代表页面结构验收失败。

## 8. un11 业务骨架返工复验

> 时间：2026-06-18 10:36 版 `docs/design/prototypes/index.html`
> 结论：用户指出的登录入口缺失、普通用户视角错误、系统切换缺失、列表重复导入导出，均已在当前原型内修正并重新验证。

本轮确认的 P0 漏洞：

- 登录入口被弱化，用户无法从原型里明确看到统一登录、MFA、进入多系统的路径。
- 业务系统普通用户视角混入后台设置和流程配置，违背“普通用户只看权限内模块 + 待办 + 消息 + 个人信息”的骨架。
- 系统切换入口没有形成稳定控件，多系统账号无法表达默认系统、可进入系统、无权限系统。
- 运行态车辆列表页同时在页头和列表工具栏放置导入/导出，并且页内又嵌套一套车辆管理左侧导航，造成重复和布局浪费。

已落实到原型：

- 新增 `login-screen`：账号登录、MFA、默认系统、多系统入口、普通登录和管理员原型演示入口。
- 新增 `business-home-screen`：普通业务首页，承载仪表盘、权限内模块、我的待办和消息。
- 拆分 `business-sidebar` 与 `admin-sidebar`：业务态只显示车辆管理模块和通用入口；后台态显示系统信息、组织架构、角色、模块、流程、字典、仪表盘、数据源、对外应用、系统日志。
- 顶部导航按 `body[data-context]` 裁剪：业务态只显示登录入口、仪表盘、车辆管理；后台态只显示登录入口和返回业务首页。
- 新增 `systemSwitcher` 抽屉：车辆资产管理可进入；合同系统申请进入；CRM 和系统后台对普通业务用户禁用。
- 运行态车辆列表去掉页内重复模块导航，页头不再放导入/导出；列表工具栏只保留一组 `导入 / 导出 / 新建车辆`。
- 后台左侧“返回首页”改为返回 `business-home-screen`，不再回到后台配置概览。

本地复验结果：

- 静态脚本验收：1 个 `<script>` 语法通过；13 个 `data-target` 缺失 0；36 个 `data-drawer` 缺失 0；死链接 0；170 个按钮死按钮 0。
- Chrome headless 实渲染验收：默认进入 `runtime-screen`，`context=business`；业务态顶部只显示 `登录入口 / 仪表盘 / 车辆管理`；业务侧边栏显示，后台侧边栏隐藏。
- 运行态列表实渲染：页内 `aside` 数量 0；可见导入按钮 1、导出按钮 1、新建车辆按钮 1。
- 登录页实渲染：`context=login`；侧边栏全部隐藏；顶部只保留登录入口。
- 系统切换抽屉实渲染：标题为“张伟可进入的业务系统”；CRM 和系统后台均为禁用“不可进入”。
- 管理员原型演示入口实渲染：进入 `module-screen`，`context=admin`；后台侧边栏显示，业务侧边栏隐藏，顶部只保留登录入口和返回业务首页。

当前建议：

- 这版可以继续让你人工看业务骨架，但不建议直接签字进入开发；下一步重点看详情抽屉、流程节点配置、角色权限配置、字段/列表/筛选/场景配置页是否达到开发可照图实现的细度。
- 后续生成新 brief 或新 design 时，必须把本段作为硬约束沉淀，避免再次把普通业务页和系统后台混在同一个导航里。

## 9. 按最终系统目标主动复审与补齐

> 时间：2026-06-18 继续复审版 `docs/design/prototypes/index.html`
> 结论：这轮不再按单点问题修补，而是按“完整系统给正常企业用户长期使用，开发按设计实现”的目标倒推，补齐四套壳、业务模块入口、详情工作区和平台边界。

主动发现的问题：

- 当前原型只基本覆盖系统业务页和系统后台，平台工作台、平台后台没有形成可见壳，不满足 brief 要求的四套壳。
- 普通业务左侧的维保记录、驾驶员仍只是 toast 反馈，模块入口没有对应真实列表页，开发会不知道这些模块怎么落。
- 车辆主编号打开的是编辑表单，不是业务详情工作区；详情缺少顶部摘要、业务 tab、审批侧栏、节点按钮和历史时间线。
- 普通待办混入了模块发布阻断、流程配置缺人等后台配置任务，不符合普通业务用户的待办心智。
- `.runtime-grid` 被改成单列后影响组织架构、角色、字典等页面，左树右表会被破坏。
- 平台层和系统层的账号、角色、日志边界还不够明确，容易让平台后台看起来能直接操作车业务数据。

已处理：

- 补齐平台工作台：平台仪表盘、Flow、应用三项顶部导航，可进入车辆系统或平台后台。
- 补齐平台后台：平台信息、组织架构、角色管理、仪表盘管理、配置管理、日志管理，且只展示平台账号、授权、健康、全局配置和平台日志。
- 新增 `platformAdmin` 上下文和平台后台左侧导航；平台后台顶部只显示“登录入口 / 返回平台工作台”。
- 新增维保记录列表页、驾驶员列表页；普通业务左侧模块全部指向实际业务列表，不再使用 toast 假入口。
- 移除普通用户左侧未发布的保险费用入口。
- 车辆详情改为大右侧工作区：顶部摘要、详细资料 tab、维保/驾驶员/附件/打印/操作 tab、右侧审批流程信息、通过/驳回/转交按钮、时间线、节点字段权限。
- 车辆主编号打开详情工作区；编辑按钮仍打开编辑表单。
- 普通待办改为业务审批、维保确认、驾驶证临期，不再混入后台配置待办。
- 恢复 `.runtime-grid` 默认左树右表，车辆列表单独使用 `.runtime-grid.single`，避免破坏组织架构等后台页面。
- 补充平台账号、平台角色预览、平台日志、维保详情、驾驶员详情抽屉。
- 关闭详情抽屉时清理 `detail-mode`，避免污染后续普通抽屉。

本地复验结果：

- 静态验收：1 个脚本语法通过；24 个 `data-target` 缺失 0；42 个 `data-drawer` 缺失 0；死链接 0；234 个按钮死按钮 0。
- Chrome headless 路径验收：
  - 默认业务页：`context=business`，顶部仅登录入口/仪表盘/车辆管理，业务侧栏显示。
  - 维保记录、驾驶员：均可从业务侧栏进入真实列表页。
  - 车辆详情：详情抽屉 `detail-mode=true`，标题为京A·12345，审批侧栏和 tab 可见。
  - 详情关闭后：`detail-mode=false`，不会污染后续抽屉。
  - 系统组织架构：`context=admin`，后台侧栏显示，`runtime-grid` 为 `248px 880px` 左树右表。
  - 平台工作台：`context=platform`，无侧栏，顶部为登录入口/仪表盘/Flow/应用。
  - 平台后台：`context=platformAdmin`，平台后台侧栏显示，顶部为登录入口/返回平台工作台。
- `git diff --check`：当前 `docs/design/prototypes/index.html` 通过。

当前仍不建议直接签字开发：

- 这份仍是单文件综合原型，适合确认产品骨架和关键交互，不等于所有最终页面都已细到可直接拆任务。
- 下一步应继续重点审：模块创建七步里的字段权限/列表/筛选/场景/动作/发布是否足够开发；流程管理的审批类型配置、节点属性配置、模拟运行和发布检查是否还要展开；角色权限页是否要把菜单、按钮、字段、数据范围、成员分配做成更完整的配置界面。

## 10. 项目级架构目标沉淀与 Open Design 重生成复验

> 时间：2026-06-18 11:58 后续版
> Open Design 项目：`unexamine-project-architecture-design`
> Open Design run：`fb1bcf7f-7ad2-49de-a0ae-2f7caa9bc18d`
> 预览地址：`http://127.0.0.1:58950/api/projects/unexamine-project-architecture-design/raw/index.html`
> 本地预览：`http://127.0.0.1:58951/index.html`

本轮用户明确修正：

- “最终目标”不是页面级补齐，而是整个项目的架构层级目标。
- 原型必须先表达平台层、系统层、运行态业务层、配置态管理层、权限身份层、流程审批层、数据集成层，再落到页面。
- 设计要能作为后续开发依据，不能把审批节点配置、角色权限配置、导入导出反馈、日志筛选等关键细节留给开发随意补。

已沉淀：

- `docs/design/prototype-brief.md` 升级为 `1.7.0-project-architecture-hierarchy`，新增“项目级架构层级”最高优先级。
- `.cursor/knowledge/failure-lessons.md` 新增教训：设计前必须先冻结项目架构层级，页面只是这些层级的表达。
- Open Design 使用无旧原型污染的新项目从零生成单文件原型，并写回 `docs/design/prototypes/index.html`。
- 在 Open Design 产物基础上继续补齐：默认登录入口、组织树语义、流程画布语义、缺模板抽屉的具体结构内容。

本轮主动发现并修正的问题：

- Open Design 生成版默认直接进入业务页，和完整系统从统一登录入口开始不一致；已改为默认 `loginScreen`。
- 组织架构虽然有左树右表，但缺少稳定语义和更明确的父子层级；已补 `org-tree`、展开标识和二级车队节点。
- 流程页已有节点库、连线和属性面板，但缺少明确 `flow-canvas` 标记；已补稳定语义和连线箭头。
- 多个配置类入口会落到通用抽屉，细节不足；已补 `drawerDetails`，使平台授权、任务日志、Flow、平台信息、平台角色、系统员工、角色权限、权限预览、模块配置、发布记录、审批人预览、字典、数据源、外部应用、系统日志等都有可开发参照的结构说明、关键数据和规则反馈。

静态验收结果：

- `<script>` 语法通过。
- 默认唯一活动屏：`loginScreen`。
- 主屏：5 个；页面：26 个；tab 子页：14 个。
- 抽屉入口：31 个，未覆盖入口 0。
- 按钮：208 个，死按钮 0。
- 车辆列表行数：10 行。
- 架构特征：登录、三角色入口、系统切换、系统后台、平台后台、业务顶部模块分组、流程画布、组织树、动态抽屉详情均存在。

浏览器复验结果：

- 登录页：默认显示统一登录，che/sys_admin_vehicle/platform_member/platform_admin_root 四种演示身份和登录按钮存在。
- che 普通成员：登录进入系统业务页 `systemBusiness`，当前页 `vehicleList`；车辆列表 10 行；系统切换可见；系统后台和平台后台入口不可见。
- 车辆详情：第一行详情可打开右侧大工作区，标题为“车辆详情工作区”；有详细资料、维保记录、授权驾驶员、附件、打印记录、操作记录 tab；右侧审批侧栏和通过/驳回/转交/更多按钮可见。
- 批量选择：勾选 1 行后 selection bar 出现，删除按钮禁用并展示原因，转移、导出选中、批量编辑、取消选择可见。
- admin 系统管理员：从登录切换 admin 后进入业务页，再从个人信息进入系统后台；左侧导航顺序为系统信息、组织架构、角色管理、模块管理、流程管理、字典管理、仪表盘管理、数据源、对外应用、系统日志。
- 系统组织架构：左树右表可见，部门树宽 260px，包含华东运营中心、运营一部、一车队、二车队等父子层级。
- 系统流程管理：节点库、流程画布、右侧属性面板均可见；流程节点 9 个，连线 7 条。
- platform_admin_root 平台管理员：登录进入平台工作台；顶部只有仪表盘、Flow、应用；平台后台入口在个人信息菜单内。
- 平台后台：左侧导航为平台信息、组织架构、角色管理、仪表盘管理、配置管理、日志管理；平台组织树可见；未混入车牌号、驾驶员、车辆档案列表等系统业务数据。
- 动态抽屉：平台配置抽屉可打开，标题为“平台配置详情”，包含结构说明、关键数据与配置、规则与反馈，不再是空壳。

当前状态：

- 原型已更贴近“项目架构层级 + 可开发设计依据”的目标，可以继续让用户人工审。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`，禁止进入 API 和开发。

## 11. 四角色、配置深度与 AI Agent 需求补齐复验

> 时间：2026-06-18 14:27
> 版本：`1.7.1-platform-member-config-depth-aiagent`
> 原型：`docs/design/prototypes/index.html`
> 本地预览：`http://127.0.0.1:58951/index.html`

本轮用户确认整体骨架大体符合，但要求继续沉淀项目级目标，而不是只修页面细节。已补齐并复验：

- 登录入口：默认仍为 `loginScreen`，右侧架构说明、系统选择和统计卡片已隐藏；真实入口只保留统一登录卡片、账号密码、MFA 和演示角色选择。
- 角色体系：从三角色扩展为四角色，包含系统普通成员、系统管理员、平台普通成员、平台管理员；平台普通成员进入平台工作台但不可见平台后台入口，平台管理员在个人信息菜单中可见平台后台入口。
- 系统租户能力：系统信息页补充单租户/多租户模式、租户隔离策略、默认租户、租户级配额、模式切换影响，明确单租户隐藏租户切换，多租户启用 tenantId 隔离。
- 字段与无代码配置：模块管理页补充字段类型库，覆盖文本、手机号、邮箱、URL、数字、百分比、金额、日期范围、单选、多选、级联、状态字典、人员、部门、组织租户、附件、图片、签名、条码、关联数据、引用、子表、公式、汇总、查找、聚合、系统字段、AI 填充字段；字段表补充字段类型专属配置、权限/导出和校验。
- 字典管理：补充字典分类、普通字典、级联字典、状态字典、版本发布、引用范围、项配置、停用历史和引用影响。
- Flow：流程管理页补充审批流、办公流、自动化工作流、外部同步失败处理；节点库扩展为开始、审批、填写表单、任务节点、条件分支、并行网关、合并网关、抄送、字段更新、数据创建/更新、外部 API、定时器、等待、子流程、AI 辅助、结束；属性面板补充审批方式、字段权限、状态回写、超时、退回策略、转办/加签和模拟解释。
- AI Agent：已写入 `docs/user_requirement.md` 原始需求和 `docs/design/prototype-brief.md`，定位为后续设计域。平台后台配置本地/外部模型、脱敏、额度、数据出境策略、购买授权和系统继承；系统后台配置模块、字段、动作、数据范围、确认策略和审计。Agent 对话录入和统计查询必须受角色、数据、字段、脱敏和日志审计控制。

浏览器路径复验：

- `platform_member` 登录后进入 `platformWorkbench`，无可见平台后台入口，可进入授权系统。
- `platform_admin_root` 登录后进入 `platformWorkbench`，打开个人信息后可进入 `platformAdmin`；配置管理中 `AI Agent / 模型授权配置` 抽屉可打开，包含本地模型、外部模型、授权购买、系统继承和审计规则。
- `admin` 登录后默认进入 `systemBusiness`，从个人信息进入 `systemAdmin`；系统信息、模块管理、流程管理、字典管理均能切换，租户、字段、flow、字典关键配置均可见。

静态验收：

- `<script>` 语法通过。
- 默认活动屏幕为 `loginScreen`。
- 216 个按钮死按钮 0。
- 32 个抽屉入口全部有定义。
- 角色入口包含 `che`、`sys_admin_vehicle`、`platform_member`、`platform_admin_root`。

当前仍不建议进入开发：`docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。AI Agent 虽已写入需求和 brief，但详细设计应在平台/系统/字段/flow/权限/日志基础能力人工确认无误后再展开。

## 12. 待办、消息、登录基础、后台权限和工作记录需求补齐

> 时间：2026-06-18 后续反馈
> 版本：`1.7.2-workbench-todo-message-daily-task`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：待办和消息不能只是简单入口或卡片；登录缺少注册、找回密码等基础能力；后台入口和可操作功能应来自角色权限配置；平台用户需要创建系统入口；日报管理和任务管理要先写入原始需求。

已补齐：

- 平台待办：顶部待办进入 `platTodos` 页面，采用左侧待办类型 + 右侧待办列表；类型包含全部待办、审批待办、提醒待办、今日需处理、任务失败；右侧列表带系统、租户、状态、关键词筛选。
- 系统待办：`bizTodo` 改为左侧类型 + 右侧列表；类型包含审批待办、提醒待办、今日需处理、抄送待阅；列表带模块、节点、状态和关键词筛选。
- 平台消息：`platMessages` 改为列表 + 右侧消息详情，筛选包含系统、租户、模板、已读状态和关键词。
- 系统消息：`bizMessages` 改为列表 + 右侧消息详情，筛选包含系统、租户、模板、已读状态和关键词。
- 登录基础：登录卡片补充注册并创建系统、找回密码、企业 SSO、短信验证码登录；移除登录页右侧系统选择/架构展示结构。
- 创建系统：平台工作台顶部和应用页工具栏增加创建系统入口；创建系统抽屉说明系统名称、编码、租户模式、模板和创建后配置引导。
- 后台权限来源：平台角色页和系统角色页补充“后台入口、菜单、按钮、字段、数据范围都来自角色配置”；平台内置超级管理员 `admin` 唯一默认全权限；系统创建人自动成为系统超级管理员。
- 日报管理和任务管理：已写入 `docs/user_requirement.md` 和 `docs/design/prototype-brief.md`，定位为签字后再展开的后续设计域，不在当前基础设计签字前抢做详细原型。

静态验收：

- `<script>` 语法通过。
- 243 个按钮。
- 34 个抽屉入口全部覆盖。
- 27 个 `data-page` 目标全部存在。
- 登录基础入口、平台/系统待办、平台/系统消息筛选、创建系统入口、超级管理员说明均已存在。

当前仍不进入开发：`design_user_approved=false`。

## 13. 待办侧栏、消息抽屉、工作管理与 AI Agent 当前化复验

> 时间：2026-06-18 后续反馈
> 版本：`1.7.3-work-management-aiagent-message-drawer`
> 原型：`docs/design/prototypes/index.html`
> 本地预览：`http://127.0.0.1:58951/index.html`

本轮用户明确纠偏：待办不是业务模块下的列表状态；消息不是表格页，而是右侧抽屉的一条条消息；日报和任务应合并为工作管理；AI Agent 和工作管理都需要在需求和 HTML 中可见，供确认后进入开发。

已补齐：

- 系统待办：点击顶部“待办”后，业务模块侧栏自动隐藏；页面只显示左侧待办类型和右侧待办列表/筛选。
- 系统消息/平台消息：顶部“消息”统一打开右侧消息中心抽屉，按消息流卡片展示完整内容，不再做表格列表页；每条消息保留进入车辆详情、待办、任务、日报、日志或 Agent 结果的动作。
- 工作管理：平台工作台和系统业务页均新增“工作”入口，展示项目/任务组、任务列表、任务看板、日历、日报候选内容、任务详情抽屉和日报抽屉。
- 日报/任务模型：沉淀为统一工作管理模型，覆盖项目、任务组、任务、负责人、协作人、标签、完成度、关联业务对象、日报、日历和统计。
- AI Agent：平台工作台和系统业务页顶部新增运行态 AI Agent；工作管理中新增工作 Agent；平台后台保留模型授权配置。Agent 运行态展示对话、解析结果、字段权限、人工确认、统计查询和审计边界。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`.cursor/knowledge/failure-lessons.md` 均已从旧的“后续域/消息列表页”改为当前可见设计范围。

浏览器复验结果：

- che 登录进入系统业务页，点击“待办”后：活动页为 `bizTodo`，`.layout.focus-page=true`，车辆模块侧栏 `display:none`，待办类型 5 个。
- 系统消息：右侧抽屉标题为“消息中心”，消息卡片 4 条，抽屉内表格 0 个，跳转/处理按钮 8 个。
- 系统工作：活动页为 `workCenter`，车辆模块侧栏隐藏；任务行 2 条，日历格 7 个，日报候选 3 条。
- 系统 AI Agent：抽屉标题为“AI Agent 助手”，对话气泡 2 个，解析行 4 条，统计卡 3 个。
- 工作 Agent：抽屉标题为“工作 Agent”，对话气泡 2 个，任务草稿行 4 条，包含“写入前必须人工确认”审计规则。
- platform_member 登录进入平台工作台，平台后台入口不可见；平台工作页有任务看板 4 列、日历 7 格、项目任务 2 行。
- 平台消息：右侧抽屉标题为“消息中心”，消息卡片 4 条，抽屉内表格 0 个。
- 平台任务详情：抽屉标题为“工作任务详情”，字段 10 个，子任务/完成记录 3 行。

当前状态：

- 这版已经把本轮指出的“待办、消息、工作管理、AI Agent”沉到设计骨架和原型里。
- 仍未进入开发：`docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 14. 消息跳转与工作模块三视图复验

> 时间：2026-06-18 后续反馈
> 版本：`1.7.3-work-management-aiagent-message-drawer`
> 原型：`docs/design/prototypes/index.html`

本轮用户继续指出：消息应简单点击即跳对应业务对象；工作模块中项目任务、任务看板、日报候选内容的关系不清晰，缺少项目创建、项目下任务、任务评论回复、完成时间、预警和日报看板。

已调整：

- 消息中心：每条消息卡片增加整条点击跳转。审批消息进入对应业务数据审批详情；模块消息进入该模块数据详情；任务/日报/Agent 消息进入对应工作对象或处理结果。此处“按钮辅助保留”的旧结论已被第 15 节覆盖，当前要求为卡片内不放重复跳转按钮。
- 工作模块信息架构：平台工作和系统工作都拆成 `仪表盘 / 任务 / 日报` 三个页签。
- 仪表盘：只看概览、项目进度、今日预警、工作日历。
- 任务页：承载项目任务和普通任务，普通任务可以只有一行描述，后续再补项目、协作人、业务对象；任务列表、任务看板、任务日历是同一批任务的不同视图，不重复成两套功能。
- 日报页：新增日报看板，展示成员提交状态、完成任务、风险、关联项目和提醒入口。
- “日报候选内容”改名为“待写入日报的工作记录”，并说明它只是从已完成任务、处理过的待办、消息和业务日志中整理出的日报草稿素材，不是新任务。
- 项目详情抽屉：补充项目主信息、项目负责人、成员、周期、完成度、预警策略、任务组、里程碑和动态。
- 任务详情抽屉：补充任务类型、普通任务/项目任务、实际完成时间、预警规则、评论与回复、子任务完成时间和预警。

复验结果：

- 静态校验：脚本语法通过；抽屉入口无断链；页面入口无断链；工作页签 6 个目标均存在；消息卡片 4 条均带 `data-jump-drawer`。
- 浏览器验收：系统工作页显示 `仪表盘 / 任务 / 日报` 三个页签；任务页切换成功，任务行 3 条、看板列 4 个、包含普通任务和“同一批任务”说明；日报页切换成功，日报看板 3 行、待写入日报工作记录 3 条，并明确“不是新任务”。
- 消息中心：HTML 已具备整条消息点击跳转结构，事件监听已覆盖 `data-jump-drawer / data-jump-page / data-jump-screen`；受当前浏览器横向视口限制，真实坐标点击未稳定命中卡片，但结构和脚本已通过静态校验。

当前状态：

- 工作模块主框架更接近主流协作产品：先总览，再处理任务，再处理日报。
- 仍未进入开发：`design_user_approved=false`。

## 15. 消息去按钮、工作拆分和基础账号页复验

> 时间：2026-06-18 本轮反馈
> 版本：`1.7.4-work-split-login-pages-message-card-jump`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：消息既然整条可点击跳转，就不应再放小按钮占空间；工作模块要拆清项目任务和普通任务；项目任务列表/看板可切换，看板列来自项目状态配置；日报要有我的最近日报、手动填写和自动统计今日日报；登录注册找回密码必须有基础页面。

已调整：

- 消息中心：移除消息卡片内部重复按钮和 `.message-actions` 样式；4 条消息均保留整条点击跳转结构。
- 基础账号页：登录页的“注册并创建系统”“找回密码”改为独立页面；注册创建系统后演示流进入系统后台，创建人成为系统超级管理员。
- 工作仪表盘：平台/系统工作台都改为当月完整月历，展示任务截止、项目里程碑、日报提交和风险。
- 任务页：平台/系统工作任务页均拆出“项目任务列表”和“普通任务”；看板明确为项目任务状态视图，状态列来自项目配置，标签可做看板分类或泳道。
- 日报页：平台/系统日报页均改为“我的最近日报 + 团队日报看板 + 今日自动统计”，手动填写和自动统计今日日报都有入口。
- 后台配置：系统后台模块管理新增“工作管理”配置入口，补工作管理配置抽屉，覆盖字段、项目任务状态、普通任务状态、日报状态、标签、看板列、泳道、卡片字段和预警规则。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`.cursor/knowledge/failure-lessons.md` 已沉淀上述约束。

静态复验：

- `message-actions` 已无残留。
- `registerScreen`、`passwordResetScreen`、`workConfigDrawer` 均存在并已接入按钮/抽屉路由。
- 平台/系统工作模块均包含“项目任务列表”“普通任务”“我的最近日报”“今日自动统计”和月历。

浏览器复验：

- 登录页可进入注册并创建系统页、找回密码页；注册页展示创建业务系统，找回密码页展示确认新密码。
- 系统消息抽屉展示 4 条消息卡片，卡片内按钮数为 0，点击审批消息后打开“车辆详情工作区”。
- 系统业务页顶部“工作”可真实点击进入 `workCenter`；任务页签可切到 `sysWorkTasks`，日报页签可切到 `sysWorkDaily`。
- 系统工作仪表盘月历格为 35 个；任务页包含项目任务列表、普通任务和“列字段/泳道字段来自工作配置、颜色图标来自数据字典”；日报页包含我的最近日报、手动填写日报和今日自动统计。
- 系统管理员可从个人信息进入系统后台，模块管理中有“工作管理”配置行，点击后打开“工作管理配置”抽屉，包含字段配置、状态与标签、看板视图配置。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 17. 规约拆分、任务视图切换和日报收敛

> 时间：2026-06-18 本轮继续反馈
> 版本：`1.7.6-task-view-toggle-daily-filter-rule-split`
> 原型：`docs/design/prototypes/index.html`

本轮用户指出：通用规约要适配所有项目，本项目规约单独放；项目任务和普通任务的列表/看板应通过按钮互斥切换，不能并列显示；列表有分页，看板无分页并滚动加载；工作仪表盘预警和日历改为上下布局；日报页只展示我的日报并添加筛选项。

已调整：

- 规约拆分：`agent-operating-rules.md` 改为所有项目通用方法论；新增 `project-operating-rules.md` 承载 unexamine 项目专属规则。
- 入口同步：`.cursor/README.md`、`.cursor/knowledge/README.md`、`.cursor/architecture/context-policy.md`、`AGENTS.md` 均改为同时读取通用规约和项目规约。
- 工作仪表盘：平台/系统工作仪表盘预警与月历改为上下布局。
- 项目任务：平台/系统项目任务页改为“列表 / 看板”按钮切换。列表视图有分页；看板视图无分页，说明通过向下滚动自动加载更多。
- 普通任务：平台/系统普通任务页也改为“列表 / 看板”按钮切换。列表视图有分页；看板视图无分页并滚动加载。
- 日报：平台/系统日报页收敛为“我的日报”列表，增加日期、状态、项目、关键词筛选；写日报和自动生成草稿作为操作入口，不再常驻团队看板和自动统计大块内容。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md`、`.cursor/knowledge/failure-lessons.md` 已同步。

浏览器复验：

- 系统工作页显示四个标签：仪表盘、项目任务、普通任务、日报。
- 仪表盘布局为单列上下结构，月历 35 格。
- 项目任务默认显示列表视图，列表有分页；切到看板后列表隐藏、看板显示，看板有滚动容器。
- 普通任务默认显示列表视图，列表有分页；切到看板后列表隐藏、看板显示，看板有滚动容器。
- 日报页只有“我的日报”面板，包含 4 个筛选项和分页，不再显示团队日报看板。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。

## 16. 列表行点击、工作四标签与通用规约沉淀

> 时间：2026-06-18 本轮继续反馈
> 版本：`1.7.5-row-click-work-four-tabs-operating-rules`
> 原型：`docs/design/prototypes/index.html`

本轮用户继续指出：所有列表数据行都应点击查看详情；工作仪表盘里的月历不应被挤成窄栏，应该和项目/今日预警并排，宽度不足再上下换行；工作模块不应保留笼统“任务”标签，应拆为“仪表盘 / 项目任务 / 普通任务 / 日报”四个标签；同时要求把“只改字面问题、不做连带推理”的经验沉淀成通用规约。

已调整：

- 列表行点击：原型增加全局表格行点击逻辑，点击数据行打开对应详情抽屉；点击按钮、复选框、输入框、下拉框不触发行详情。
- 工作仪表盘：平台/系统工作仪表盘改为双栏网格，项目/今日预警与当月月历并排；宽度不足时由布局规则换行，不再把月历压成窄栏。
- 工作四标签：平台/系统工作管理都改为“仪表盘 / 项目任务 / 普通任务 / 日报”。项目任务页承载项目任务列表和看板；普通任务页单独承载一行任务、后补项目/业务对象/标签等场景。
- 文档同步：`docs/user_requirement.md`、`docs/design/prototype-brief.md`、`docs/design/design-package.md` 均同步四标签、行点击详情和月历布局约束。
- 通用规约：新增 `.cursor/knowledge/agent-operating-rules.md`，沉淀开工前落盘压缩、连带推理、主动作唯一、列表/详情、工作四标签、设计自检和验收要求。
- 启动入口：`.cursor/README.md`、`.cursor/knowledge/README.md`、`.cursor/architecture/context-policy.md`、`AGENTS.md` 均已指向该规约。
- 失败教训：`.cursor/knowledge/failure-lessons.md` 新增列表行点击、工作四标签、月历布局、长上下文落盘压缩四条。

浏览器复验：

- 系统工作页真实显示四个标签：仪表盘、项目任务、普通任务、日报。
- 工作仪表盘使用双栏布局，月历为 35 个日期格。
- 项目任务页可切换，包含项目任务列表和 4 列看板。
- 普通任务页可切换，列表有 3 条普通任务。
- 点击项目任务列表数据行打开“工作任务详情”抽屉。

当前状态：

- 本轮仍只处于设计阶段，未进入开发。
- `docs/design/user-approval.md` 尚未签字，`design_user_approved=false`。
