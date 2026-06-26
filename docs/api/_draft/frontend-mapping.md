# Frontend API Mapping Draft

> Phase 2 Contract / Frontend worker 草案
> 输入来源：`.cursor/session/state.json`、`docs/design/prototype-brief.md`、`docs/design/prototypes/index.html`、`docs/design/reviews/prototype-latest-2026-06-18.md`、`docs/design/pre-coding-readiness.md`、`docs/design/user-approval.md`

## 0. 当前闸门

- 当前设计已签字：`docs/design/user-approval.md approved=true`。
- 当前阶段：`.cursor/session/state.json phase=contract`。
- 当前 API 仍未冻结：`gates.api_frozen=false`。
- 本文件只作为 API 契约冻结前的前端字段/页面映射草案，不进入 frontend/backend/sql 实现，不创建实现代码，不做视觉重设计。

## 1. 页面与 API 数据映射

### 1.1 登录 / 注册 / 找回

- 登录页需要：登录方式、MFA/验证码策略、企业 SSO 已发布身份源、登录失败限制、账号安全提示、系统直登目标摘要。
- 注册并创建系统需要：平台账号注册字段、系统名称/编码、租户模式、模板、创建结果、系统超级管理员授权、初始化引导步骤。
- 找回/重置密码需要：验证方式、验证码状态、重置 token 状态、密码策略、完成后登录跳转。
- 需冻结对象：`LoginContext`、`PlatformLoginContext`、`SystemSwitchContext`、`RegisterSystemResult`、`PasswordResetSession`、`LoginAudit`。

### 1.2 平台工作台

- 平台壳顶部需要：平台导航、当前账号、平台角色、系统切换入口、创建系统权限、待办未读数、消息未读数、AI Agent 入口权限。
- 仪表盘需要：授权系统概览、平台任务状态、健康状态、最近访问、容量/队列预警。
- 平台 Flow 需要：流程库、发起/参与流程、触发源、影响系统、运行批次、幂等键、重试/补偿、traceId。
- 平台应用需要：授权系统/应用卡片、授权状态、负责人、最近访问、授权申请/调整入口、禁用原因。
- 平台工作管理需要：仪表盘、项目任务、普通任务、日报；项目任务/普通任务支持列表与看板互斥视图。
- 平台待办需要：待办类型、来源系统、租户、节点/提醒、到期时间、状态、行级主动作和批量处理结果。
- 需冻结对象：`PlatformShellView`、`PlatformDashboardSummary`、`PlatformSystemCard`、`PlatformFlowRun`、`PlatformTodoRow`、`WorkItemRow`、`DailyReportRow`。

### 1.3 平台后台

- 平台信息需要：平台版本、部署状态、健康状态、容量、最近备份、全局开关摘要。
- 组织架构需要：左侧平台组织树、选中节点、右侧账号列表、账号安全/MFA/角色。
- 角色管理需要：平台角色、成员、菜单权限、配置权限、日志范围、授权范围、内置 `platform_admin_root` 标识。
- 仪表盘管理需要：组件、数据源、可见角色、刷新频率、脱敏策略、布局版本。
- 配置管理需要：统一认证、消息模板、AI 模型授权、功能开关、容量配额、限流、备份恢复、归档、多环境、API 缓存策略。
- 日志管理需要：左侧日志类型/审计范围树，右侧统一列表、筛选、列设置、导出、详情抽屉。
- 需冻结对象：`PlatformAdminShellView`、`PlatformOrgNode`、`PlatformAccountRow`、`PlatformRoleView`、`DashboardWidgetConfig`、`PlatformConfigItem`、`AuditLogRow`。

### 1.4 系统业务页

- 业务壳需要：当前系统、租户、系统成员、顶部模块分组、左侧组内模块、当前数据范围、待办/消息计数、后台入口权限。
- 仪表盘需要：业务统计、模块摘要、待处理、最近访问、系统健康入口。
- 业务列表需要：列表 schema、字段权限、筛选项、排序、分页、场景视图、列设置、批量动作、导入/导出权限、整行详情目标。
- 业务详情抽屉需要：详情摘要、业务字段、关联记录、附件、打印记录、操作记录、审批侧栏、动作权限和禁用原因。
- 新建/编辑需要：表单 schema、字段默认值、字段校验、草稿、附件草稿、错误字段定位、保存结果。
- 需冻结对象：`SystemBusinessShellView`、`ModuleGroupNav`、`ModuleNavItem`、`DynamicListSchema`、`BusinessRecordRow`、`BusinessDetailView`、`FormSchema`、`ActionPermissionMap`。

### 1.5 系统后台

- 系统信息需要：系统生命周期、租户模式、系统体检、上线检查、SSO 继承、成员映射、容量预警、最近备份、功能开关、归档恢复和扩容申请。
- 组织架构需要：左侧部门树、右侧员工列表、员工绑定、systemMemberId、租户/部门范围。
- 统一认证需要：身份源继承、租户域名、组织映射、员工绑定、JIT 系统成员策略、无成员映射反馈、登录日志 traceId。
- 角色管理需要：成员分配、菜单、模块、按钮、字段、数据范围、有效权限预览。
- 模块管理需要：模块组、模块、字段类型注册表、列表/筛选/场景/页面动作、导入导出、打印模板、发布版本和影响分析。
- 工作配置需要：项目任务字段、普通任务字段、日报字段、字段权限、卡片字段、看板列/泳道字段、发布检查。
- AI Agent 配置需要：平台模型继承、系统模型、`AgentPolicyScope`、确认策略、发布检查、审计边界。
- 流程管理需要：流程类型、绑定规则、节点库、画布、条件分支、节点专属属性、模拟运行、发布检查、实例/任务快照。
- 字典管理需要：字典类型、字典项、颜色/图标/语义、停用历史、引用影响、发布检查。
- 数据源/对外应用需要：数据源类型、脱敏、同步状态；OpenAPI 应用、scope、回调、限流、`OpenApiSecretRef`、轮换状态。
- 日志管理需要：登录日志/业务日志/后台任务/AI/OpenAPI 分类树、统一列表、traceId、详情和导出任务。
- 需冻结对象：`SystemAdminShellView`、`SystemInfoView`、`OrgNode`、`SystemMemberRow`、`SystemSsoPolicyView`、`RolePermissionView`、`ModuleConfigView`、`WorkflowConfigView`、`DictionaryView`、`ExternalAppView`。

### 1.6 待办

- 平台待办和系统待办都需要左侧类型树、右侧待办列表、筛选、批量处理、行级主动作。
- 系统待办进入后隐藏业务模块侧栏，不复用业务模块导航。
- 待办行需要：todoId、层级、来源对象、标题、类型、状态、到期时间、处理人、target、动作权限、禁用原因、traceId。

### 1.7 消息

- 平台消息和系统消息必须拆分。
- 平台消息只能跳平台授权、平台任务、平台日志、系统切换引导或平台 Agent 结果；不得直接打开系统业务详情。
- 系统消息只能在当前 `SystemSwitchContext` / `TenantSwitchContext` / `systemMemberId` 下整条跳业务对象。
- 消息流需要：系统/租户/模板/类型/状态/时间/关键词筛选、全部已读、归档、加载更多、跳转结果。
- 需冻结对象：`MessageCardView`、`MessageTarget`、`NotificationTemplate`、`NotificationChannel`、`MessageDeliveryLog`。

### 1.8 工作管理

- 固定四标签：仪表盘、项目任务、普通任务、日报。
- 项目任务和普通任务都需要列表/看板互斥视图；列表分页，看板滚动加载。
- 日报只展示我的日报和筛选；写日报、自动生成草稿作为操作入口。
- 自动日报草稿必须由 `DailyReportAutoSourceRule` 驱动，只读取当前成员有权限的数据，提交前人工确认。
- 需冻结对象：`WorkDashboardView`、`ProjectTaskRow`、`NormalTaskRow`、`KanbanViewConfig`、`DailyReportRow`、`DailyReportAutoSourceRule`。

### 1.9 详情抽屉

- 详情抽屉需要保持列表上下文，右侧打开。
- 摘要区需要：对象标题、模块标签、关键字段、状态、更新时间、负责人、主动作。
- 内容区需要：基础资料、专属业务 tab、附件、打印记录、操作记录。
- 审批侧栏需要：当前流程、当前节点、处理人、审批历史入口、通过/驳回/转交/加签等动作、字段权限快照。
- 需冻结对象：`DetailSummaryView`、`DetailTabSchema`、`AttachmentView`、`PrintRecordView`、`OperationLogView`、`ApprovalSidebarView`。

### 1.10 导入导出

- 导入需要：模板下载、文件上传、字段匹配、预检查、错误明细、确认导入、批次结果、错误文件、回滚申请/不可回滚原因。
- 导出需要：导出范围、字段权限、脱敏策略、格式、后台任务、结果文件、失败反馈。
- 导入导出均进入后台任务和业务日志，不在列表下方常驻面板。
- 需冻结对象：`ImportBatchView`、`ImportPrecheckResult`、`ExportRequestView`、`BackgroundTaskStatus`。

### 1.11 流程

- 流程配置需要：审批类型、触发事件、绑定模块、优先级/互斥、幂等键、版本、发布状态。
- 画布需要：节点库、节点位置、连线、条件分支标签、选中节点。
- 节点属性必须按类型拆分：审批、条件、字段更新、外部 API、超时提醒、抄送、并行/合并、结束。
- 运行态需要：流程实例快照、任务快照、审批处理、状态回写、traceId、重试/补偿。
- 需冻结对象：`WorkflowDefinitionView`、`WorkflowNodeView`、`WorkflowEdgeView`、`FlowNodeProperty`、`WorkflowInstanceSnapshot`、`ApprovalTaskSnapshot`。

### 1.12 SSO

- 平台身份源需要：协议、issuer/entityId、clientId/appId、SecretRef、回调、scope、证书/JWKS、域名白名单、属性映射、JIT、MFA、测试发布、状态。
- 系统统一认证需要：继承身份源、租户域名、组织映射、员工绑定、systemMemberId、JIT 成员策略、无成员映射申请、登录审计。
- 密钥轮换必须走 `SecretRotationJob`，不回显明文 secret。
- 认证成功但无系统成员映射必须生成 `NoMemberAccessRequest`，审核通过并分配角色/数据范围前不能进入系统业务页。
- 需冻结对象：`IdentityProviderView`、`IdentityProviderSecretRef`、`SecretRotationJob`、`SystemSsoPolicyView`、`NoMemberAccessRequest`、`LoginAudit`。

### 1.13 AI Agent

- 平台运行态 Agent 只处理平台授权、日志、任务、模型额度、系统健康和系统切换引导。
- 平台 Agent 确认只生成平台任务、平台消息和平台日志。
- 系统运行态 Agent 必须在当前系统成员上下文内查询或写入业务数据，写入前人工确认。
- 系统 Agent 策略必须保存 `AgentPolicyScope`，并记录策略版本、模型授权版本、提示词版本、权限快照、工具调用明细、外发数据快照和人工确认人。
- 需冻结对象：`PlatformAgentSession`、`PlatformAgentConfirmView`、`SystemAgentSession`、`AgentPolicyScope`、`AgentWriteConfirmView`、`AgentAuditLog`。

### 1.14 日志

- 平台/系统日志都采用同一个日志管理入口，左类型树 + 右统一列表。
- 登录日志关注账号安全、认证方式、MFA、IP、设备、地点、风险、结果、requestId、traceId。
- 业务日志关注模块、对象、动作、审批、导入导出、OpenAPI、AI Agent、字段前后值、权限快照、脱敏结果。
- 后台任务日志需要能回到任务状态、结果文件、错误文件和审计记录。
- 需冻结对象：`LogTypeTreeNode`、`AuditLogRow`、`AuditLogDetail`、`LoginAuditDetail`、`BusinessAuditDetail`。

## 2. 关键 DTO / View Model 草案

### 2.1 导航壳

- `PlatformShellView`：`currentAccount`、`platformRoles`、`navItems`、`rightActions`、`todoBadge`、`messageBadge`、`agentEnabled`、`adminEntryVisible`。
- `SystemBusinessShellView`：`systemContext`、`tenantContext`、`moduleGroups`、`activeGroupId`、`leftModules`、`activeModuleId`、`todoBadge`、`messageBadge`、`fieldPermissionVersion`、`adminEntryVisible`。
- `SystemAdminShellView`：`systemContext`、`tenantContext`、`adminMenus`、`activeMenu`、`returnHomeTarget`。

### 2.2 系统切换上下文

- `SystemSwitchContext`：`accountMemberBindingId`、`systemId`、`systemCode`、`systemName`、`tenantId`、`systemMemberId`、`effectiveRoleIds`、`dataScope`、`permissionSnapshotId`、`permissionVersion`、`messageTodoScope`、`disabledReason`。
- `TenantSwitchContext`：`tenantId`、`tenantName`、`tenantRoleIds`、`tenantDataScope`、`isTenantSwitchable`、`disabledReason`。
- `NoMemberAccessRequest`：`requestId`、`status`、`identityProvider`、`externalUserId`、`targetSystemId`、`tenantId`、`requestRole`、`approverId`、`approveResult`、`roleIds`、`dataScope`、`rejectReason`、`traceId`。

### 2.3 租户上下文

- `TenantContextView`：`tenantId`、`tenantName`、`tenantMode`、`roleIds`、`dataScope`、`quotaSummary`、`domain`、`isCurrent`、`switchDisabledReason`。

### 2.4 有效权限快照

- `EffectivePermissionSnapshot`：`snapshotId`、`permissionVersion`、`systemMemberId`、`sourceRoleIds`、`denyPolicyIds`、`menuPermissions`、`modulePermissions`、`fieldPermissions`、`actionPermissions`、`dataScopeExpression`、`explainChain`、`expiresAt`。

### 2.5 列表 Schema

- `DynamicListSchema`：`moduleId`、`moduleCode`、`sceneId`、`columns`、`filters`、`sorters`、`page`、`rowClickTarget`、`batchActions`、`toolbarActions`、`importExportConfig`、`emptyState`、`permissionSnapshotId`。
- `ListColumnSchema`：`fieldCode`、`title`、`fieldType`、`width`、`sortable`、`fixed`、`visible`、`masked`、`permission`、`dictionaryCode`。

### 2.6 字段权限

- `FieldPermissionView`：`fieldCode`、`listVisible`、`detailReadable`、`editable`、`exportable`、`masked`、`maskRule`、`required`、`disabledReason`、`approvalOverride`。

### 2.7 动作权限

- `ActionPermissionView`：`actionCode`、`label`、`visible`、`enabled`、`disabledReason`、`requiresConfirm`、`resultType`、`targetDrawer`、`taskBizType`、`idempotencyRequired`。

### 2.8 消息卡片

- `MessageCardView`：`messageId`、`level`、`scope`、`systemId`、`tenantId`、`templateCode`、`title`、`content`、`sender`、`createdAt`、`readStatus`、`archiveStatus`、`target`、`deliveryLogId`、`traceId`。
- `MessageTarget`：`targetType`、`targetId`、`targetSystemId`、`targetTenantId`、`requiresSystemSwitch`、`fallbackAction`。

### 2.9 待办行

- `TodoRowView`：`todoId`、`scope`、`type`、`title`、`sourceName`、`moduleCode`、`objectTitle`、`assigneeId`、`dueAt`、`status`、`priority`、`target`、`primaryAction`、`actionPermissions`、`traceId`。

### 2.10 详情摘要

- `DetailSummaryView`：`recordId`、`moduleId`、`moduleName`、`title`、`status`、`summaryFields`、`owner`、`updatedAt`、`tags`、`primaryActions`、`permissionSnapshotId`。

### 2.11 审批侧栏

- `ApprovalSidebarView`：`workflowInstanceId`、`workflowName`、`currentNode`、`handlers`、`status`、`historySummary`、`availableActions`、`nodeFieldPermissions`、`traceId`。

### 2.12 后台任务状态

- `BackgroundTaskStatus`：`taskId`、`bizType`、`idempotencyKey`、`status`、`progress`、`retryable`、`cancelable`、`resultFile`、`errorFile`、`traceId`、`auditLogId`、`createdBy`、`createdAt`、`failureReason`、`partialSuccessItems`、`rollbackBoundary`。

## 3. 交互约束

- 列表型业务数据行以整行点击作为详情主动作；复选框、按钮、链接、输入框、下拉框不得误触发行详情。
- 操作列不重复“详情 / 查看 / 打开 / 进入”等同义详情动作，只保留编辑、删除、转移、审批、导出、配置、演练记录、日志定位、风险复核等差异动作。
- 消息卡片整条点击跳转，不放“打开 / 查看 / 进入详情 / 查看日志”等重复小按钮。
- 平台消息不得直接打开系统业务详情；系统消息必须在当前系统成员上下文内跳转。
- 项目任务和普通任务的列表/看板互斥显示；列表分页，看板滚动加载且不分页。
- 后台配置树、日志类型树、字典分类、组织树、角色树、待办类型选中后必须刷新右侧列表或配置区。
- 分页、筛选、列设置、全部已读、归档、加载更多、批量处理、保存布局、发布检查等按钮必须有可见状态变化或结果对象。
- 导入导出不在列表下方常驻大面板，只通过工具栏触发抽屉/下拉/后台任务反馈。
- `defaultDrawer` 只代表设计缺口阻断态，不作为开发兜底详情。

## 4. 需要后端冻结的 P2 字段 / 枚举 / 状态

### 4.1 通用协议

- 统一响应：`requestId`、`traceId`、`code`、`message`、`data`、`errorFields`。
- 分页：`pageNo`、`pageSize`、`total`、`hasNext`、`sorters`、`filters`。
- 幂等：`idempotencyKey`、重复提交状态、过期时间。
- 审计：`auditLogId`、`createdBy`、`createdAt`、`updatedBy`、`updatedAt`。

### 4.2 身份与上下文

- `LoginType`：password、mfa、sms、sso、system_direct。
- `SystemSwitchStatus`：current、available、no_member、disabled、expired、pending_approval。
- `TenantMode`：single、multi。
- `NoMemberAccessRequestStatus`：submitted、reviewing、approved、rejected、canceled。
- `PermissionEffect`：allow、deny、disabled、masked。
- `DataScopeType`：self、department、department_tree、tenant、custom_expression、all。

### 4.3 列表 / 字段 / 权限

- `FieldType`：需覆盖原型字段类型注册表，包括文本、数字、金额、日期范围、单选、多选、级联、人员、部门、组织/租户、附件、图片、自动编号、关联数据、引用字段、子表、地址、评分、进度、标签、条码/二维码、签名、富文本、JSON、密码/密钥、状态、公式、汇总、AI 填充等。
- `FieldPermissionMode`：hidden、readonly、editable、masked、export_denied。
- `ListViewType`：table、kanban。
- `ActionResultType`：sync_result、drawer、background_task、publish_check、log_trace、download_file。

### 4.4 消息 / 待办

- `MessageScope`：platform、system。
- `MessageStatus`：unread、read、archived。
- `MessageTargetType`：system_switch、platform_auth、platform_task、platform_log、business_record、todo、work_item、daily_report、agent_result、audit_log。
- `TodoType`：approval、reminder、today_action、cc_read、task_failure、config_review。
- `TodoStatus`：pending、processing、done、rejected、expired、canceled。
- 消息模板字段：`templateCode`、`scope`、`templateType`、`variables`、`channels`、`targetRule`、`dedupeKey`、`readReceiptRequired`、`quietPolicy`、`retryPolicy`、`messageDeliveryLogId`。

### 4.5 后台任务 / 导入导出

- `TaskStatus`：queued、running、success、partial_success、failed、canceled、rollback_requested、rolled_back。
- `TaskBizType`：import、export、publish_check、flow_simulation、agent_write、secret_rotation、health_check、backup_restore、archive_restore、bulk_edit。
- 导入状态：uploaded、mapped、prechecked、confirmed、importing、completed、partial_completed、failed、rollback_requested。
- 导出格式：xlsx、csv、pdf、zip。
- 文件字段：`fileId`、`fileName`、`fileUrl`、`expiresAt`、`masked`、`downloadPermission`。

### 4.6 流程

- `WorkflowStatus`：draft、published、disabled、archived。
- `WorkflowNodeType`：start、approval、condition、parallel、merge、cc、field_update、external_api、timer、subflow、ai_assist、end。
- `ApprovalAction`：approve、reject、transfer、delegate、add_sign、withdraw、terminate。
- `WorkflowInstanceStatus`：running、approved、rejected、withdrawn、terminated、failed。
- 快照字段：流程版本、节点版本、字段权限快照、任务快照、状态回写结果、traceId。

### 4.7 SSO / Secret / OpenAPI

- `IdentityProviderType`：oidc、saml2、oauth2、ldap_ad、wecom、dingtalk。
- `IdentityProviderStatus`：draft、test_passed、published、disabled、expired。
- `SecretRotationStatus`：draft、new_version_created、dual_write_verifying、switched、old_version_disabled、failed、rolled_back。
- `OpenApiSecretRef` 字段：`secretRef`、`version`、`rotationStatus`、`expiresAt`、`lastUsedAt`。
- `OpenApiAppStatus`：enabled、disabled、expired、rotation_required。

### 4.8 AI Agent

- `AgentScope`：platform、system。
- `AgentActionScope`：query、draft_write、confirm_write、task_extract、daily_draft、summary、health_check。
- `AgentConfirmStatus`：pending_confirm、confirmed、rejected、partial_written、failed_compensated。
- `AgentPolicyScope` 字段：`moduleScope`、`fieldScope`、`actionScope`、`dataScopeExpression`、`egressRestriction`、`maskPolicy`、`policyVersion`。

### 4.9 日志 / 上线保障

- `LogScope`：platform、system。
- `LogType`：login、business、risk、openapi、ai_agent、background_task、import_export。
- `LogResult`：success、failed、partial_success、blocked、denied。
- `HealthStatus`：healthy、warning、failed、checking。
- `FeatureFlagStatus`：draft、enabled、gray、paused、disabled、rolled_back。
- `BackupRestoreStatus`：scheduled、running、success、failed、restore_requested、restored。
- `ArchiveStatus`：active、archived、restore_requested、restored、readonly。

## 5. PM 合并注意点

- 前端不要求视觉重设计，但要求 API 按四套壳和上下文对象冻结，不能把平台/系统、运行态/配置态混成一套菜单或一套消息。
- `SystemSwitchContext`、`TenantSwitchContext`、`EffectivePermissionSnapshot` 建议作为首批冻结对象；前端不临时拼权限上下文。
- `MessageTarget` 需要 PM/后端裁决平台消息能跳哪些平台对象，系统业务跳转必须由系统消息在成员上下文内完成。
- 后台任务状态机、SecretRef 轮换状态机、NoMemberAccessRequest 生命周期和 Agent 写入确认状态，需要在 PM 合并 `docs/api/api.md` 时统一命名，避免各模块自造枚举。
- 本草案未发现需要阻断合同合并的前端 P0/P1；上述 P2 字段和枚举需在 API freeze 前裁定。
