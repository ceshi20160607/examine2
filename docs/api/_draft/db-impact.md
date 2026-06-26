# 数据库影响草案

> Phase 2 Contract / DBA worker 草案。本文只服务 API 契约冻结前的数据库影响识别，不是正式数据库设计，不创建 SQL，不进入 backend/frontend/sql 实现。

## 1. 当前闸门

- 当前已签字：`docs/design/user-approval.md` 为 `approved: true`，签字时间 `2026-06-23T15:10:00+08:00`。
- 当前阶段：`.cursor/session/state.json` 为 `phase=contract`、`mode=api-contract-drafting`。
- 当前仍未冻结 API：`gates.api_frozen=false`，`tasks_planned=false`。
- 本阶段只产出契约草案，后续由 PM 合并 `docs/api/api.md` 并通过 contract-sync 后，才允许进入任务拆分和实现。
- 本草案未发现与当前落盘设计文件冲突的 P0/P1 数据模型阻断点；下方 P2 点需要 PM 在 API 合并时裁决。

## 2. 全局建模原则

- 业务数据归属统一为 `systemId / tenantId / moduleId`，不得使用旧 `appId` 作为业务数据父级。
- 模块组只做运行态导航、排序、可见角色和发布关系，不承载业务数据父子关系。
- 对外应用只表示 OpenAPI 调用来源、scope、回调、限流和审计来源，不承载系统内模块树。
- SSO、OpenAPI、Webhook、外部模型等密钥必须使用 SecretRef 和版本，页面、日志、导出、备份均不得回显明文。
- 所有核心表族默认需要审计字段：`createdBy`、`createdAt`、`updatedBy`、`updatedAt`、`requestId`、`traceId`；涉及审批、密钥、权限、导入导出、AI 写入的运行表还应记录 `auditLogId`。
- 配置类对象默认需要草稿、发布、版本和回滚能力；运行类对象默认需要快照，避免后续配置变更影响历史事实。
- 大日志、任务明细、消息投递、导入导出明细、AI 工具调用明细需要预留归档策略和冷热分层索引策略。

## 3. 表族影响清单

### 3.1 平台账号与系统

建议对象：平台账号、登录凭证、会话/刷新令牌、系统、系统生命周期事件、账号与系统成员绑定、`SystemSwitchContext`。

- 关键字段：账号名、手机号、邮箱、密码摘要、MFA 状态、账号状态、系统编码、系统状态、租户模式、创建人系统超级管理员标记、`accountMemberBindingId`、`systemId`、`tenantId`、`systemMemberId`、`effectiveRoleIds`、`dataScope`、权限快照引用。
- 唯一约束：平台账号用户名/手机号/邮箱按平台维度唯一；系统编码全平台唯一；账号与系统成员绑定按 `accountId + systemId + tenantId + systemMemberId` 唯一。
- 索引：账号登录标识、系统状态、系统创建人、最近进入时间、`accountId + systemId`、`accountMemberBindingId`。
- 租户隔离：平台账号属平台层，不带业务租户；进入系统后必须通过绑定关系得到 `systemId/tenantId/systemMemberId`，平台超管也不能绕过系统成员上下文访问业务数据。
- 审计与版本：系统启用、停用、删除、恢复需要生命周期事件和操作审计；`SystemSwitchContext` 建议作为可追踪的会话上下文或短期快照对象，记录生成时间、来源和权限快照版本。

### 3.2 租户与成员

建议对象：租户、租户成员、系统成员、部门/组织树、员工档案、成员邀请、`TenantSwitchContext`。

- 关键字段：`systemId`、`tenantId`、租户编码、租户状态、域名/访问地址、配额、部门父子关系、系统成员状态、员工绑定信息、租户角色集合、租户数据范围、`isTenantSwitchable`、`disabledReason`。
- 唯一约束：同系统内租户编码唯一；同系统内员工号/手机号/邮箱按业务规则唯一；同租户内部门编码唯一；成员绑定按 `systemId + tenantId + accountId` 或 `systemId + tenantId + employeeId` 唯一。
- 索引：`systemId + tenantId`、部门父子路径、成员状态、账号反查成员、租户域名、租户切换候选列表。
- 租户隔离：多租户系统的成员、部门、业务数据、待办、消息、权限快照均带 `tenantId`；单租户系统仍建议保留默认租户，前端隐藏切换入口。
- 审计与版本：租户模式切换、成员停用、部门迁移、租户配额调整需要审计；`TenantSwitchContext` 需要记录角色、数据范围、可切换原因和失效时间。

### 3.3 角色权限

建议对象：平台角色、系统角色、角色成员关系、菜单/模块/动作权限、字段权限、数据范围策略、显式拒绝策略、权限预览、`EffectivePermissionSnapshot`。

- 关键字段：角色编码、角色层级、授权对象类型、授权对象 ID、allow/deny、字段可见/可编辑/可导出、动作权限、数据范围表达式、来源角色、显式拒绝策略、`permissionVersion`。
- 唯一约束：同层级内角色编码唯一；同角色对同授权对象和动作的策略唯一；字段权限按 `roleId + moduleId + fieldId` 唯一。
- 索引：成员角色查询、角色授权对象、`systemId + tenantId + systemMemberId + permissionVersion`、模块权限、字段权限。
- 租户隔离：平台角色不直接授予系统业务数据权限；系统角色和数据范围必须带 `systemId/tenantId`，后端按快照兜底校验。
- 审计与版本：角色授权变更需要生成新权限版本并清理缓存；`EffectivePermissionSnapshot` 必须可解释来源角色、拒绝策略、字段权限矩阵、数据范围表达式和按钮禁用原因。

### 3.4 模块组、模块、字段、字典

建议对象：模块组、模块、字段定义、字段类型注册、页面配置、列表/筛选/场景配置、页面动作、数据字典类型、字典项、配置发布版本、引用影响。

- 关键字段：`systemId`、模块组编码、模块编码、字段编码、字段类型、存储形态、校验规则、脱敏规则、筛选/排序操作符、字典引用、页面动作编码、发布状态、版本号。
- 唯一约束：同系统内模块组编码唯一；同系统内模块编码唯一；同模块内字段编码唯一；同系统内字典类型编码唯一；同字典类型下字典项编码唯一。
- 索引：模块组排序、模块发布状态、字段列表排序、字段类型、字典类型、字典项状态、配置版本。
- 租户隔离：配置通常归属 `systemId`，可按 `tenantId` 扩展覆盖；运行时业务数据只引用 `moduleId/fieldId/dictItemId`，不继承模块组为父级。
- 审计与版本：字段、列表、筛选、页面动作、字典、模块发布均需要草稿/已发布/回滚版本；字典项停用要保留历史和引用影响。

### 3.5 动态业务记录

建议对象：业务记录主表、字段值表、索引值表、子表行、关联关系、记录历史、表单草稿、自动编号序号、附件引用、打印记录。

- 关键字段：`systemId`、`tenantId`、`moduleId`、记录 ID、业务编号、记录状态、拥有者/部门、字段值、索引值、子表行号、关联目标、草稿状态、自动编号类型和当前序号。
- 唯一约束：模块内业务编号按 `systemId + tenantId + moduleId + recordNo` 唯一；字段值表按 `recordId + fieldId + rowId` 控制唯一；自动编号按 `systemId + tenantId + moduleId + sequenceType` 唯一。
- 索引：列表常用筛选字段进入索引值表；`systemId + tenantId + moduleId + deleted`；创建时间、更新时间、拥有者、部门、状态、关联对象、全文关键词。
- 租户隔离：所有业务记录、值、索引、子表、历史、附件引用必须带 `systemId/tenantId/moduleId` 或可由主记录强约束推导；查询不能依赖前端传来的成员上下文。
- 审计与版本：记录历史保存字段前后值、来源、操作人、权限快照、traceId；草稿与正式记录分状态；删除默认软删除；审批和打印使用记录快照。

### 3.6 流程、审批、待办、消息

建议对象：流程定义、流程版本、节点配置、绑定规则、发布检查、流程实例、实例快照、审批任务快照、待办、消息模板、通知渠道、消息、`message_delivery_log`。

- 关键字段：流程编码、审批类型、触发事件、绑定模块、优先级、互斥键、节点类型、条件表达式、字段更新配置、外部 API 配置、超时策略、实例状态、任务状态、模板编码、渠道、跳转目标、去重键、已读/归档状态。
- 唯一约束：同系统内流程编码唯一；同模块同触发事件的绑定规则按优先级和互斥策略约束；消息模板 `templateCode + level + systemId` 唯一；消息去重按 `dedupeKey + receiverId + targetType + targetId` 控制。
- 索引：待办按接收人、状态、类型、到期时间；流程实例按业务对象、状态、发起人；消息按接收人、系统、租户、模板、已读、归档、时间；投递日志按 messageId、渠道、状态、traceId。
- 租户隔离：系统消息、待办、流程实例必须带 `systemId/tenantId`；平台消息与系统消息拆表或用层级字段硬隔离，平台消息不得直接指向系统业务详情。
- 审计与版本：流程运行保存定义版本和节点快照；审批处理保存处理人、意见、附件、字段权限快照；消息投递保存失败重试、回执、免打扰、归档和投递日志。

### 3.7 工作管理

建议对象：项目、任务组、项目任务、普通任务、任务视图配置、看板规则、评论/回复、日报、日报草稿、`DailyReportAutoSourceRule`。

- 关键字段：项目编码、负责人、协作人、任务类型、状态字段、标签字段、完成度、截止时间、预警规则、关联业务对象、日报日期、日报状态、自动来源规则、来源对象范围、人工确认人。
- 唯一约束：同系统/租户内项目编码唯一；任务编号按系统/租户/任务类型唯一；日报按 `systemMemberId + reportDate` 可配置唯一或允许多版本草稿。
- 索引：负责人、协作人、项目、状态、标签、截止时间、关联业务对象、日报日期、日报状态。
- 租户隔离：项目、任务、日报、评论、自动草稿来源均按当前 `systemId/tenantId/systemMemberId` 权限范围读取，不能跨租户汇总。
- 审计与版本：看板列、泳道和颜色来自已发布字段/字典版本；日报自动草稿必须记录来源任务、待办、消息、业务日志和审批记录快照，提交前人工确认。

### 3.8 SSO、Secret、OpenAPI

建议对象：身份源、身份源发布版本、系统身份源继承策略、组织映射、员工绑定、`NoMemberAccessRequest`、SecretRef、Secret 版本、`SecretRotationJob`、对外应用、`OpenApiSecretRef`、OpenAPI scope、回调、限流、调用日志、幂等记录。

- 关键字段：identityProvider、协议类型、issuer/entityId、clientId/appId 引用、SecretRef、证书/JWKS 引用、域名白名单、属性映射、JIT 策略、externalUserId、targetSystemId、申请状态、roleIds、dataScope、secretVersion、rotationStatus、expiresAt、lastUsedAt、scope、rateLimit、idempotencyKey。
- 唯一约束：身份源编码全平台唯一；系统身份源继承按 `systemId + identityProviderId` 唯一；外部身份绑定按 `identityProvider + externalUserId` 唯一；对外应用编码同系统唯一；OpenAPI 幂等键按 `appId + idempotencyKey` 唯一。
- 索引：身份源状态、系统继承策略、外部账号绑定、无成员申请状态、密钥到期、OpenAPI appKey 引用、调用日志 traceId、限流维度。
- 租户隔离：身份源在平台层配置，系统继承时落 `systemId/tenantId` 策略；OpenAPI 调用必须解析为系统、租户、scope 和审计来源。
- 审计与版本：SecretRef 不回显明文；密钥轮换记录新版本、双写验证、切换生效、停用旧版本、失败回滚和最近调用检查；NoMemberAccessRequest 审批通过前不得产生业务权限。

### 3.9 AI Agent

建议对象：平台模型授权、模型凭证引用、系统 Agent 配置、`AgentPolicyScope`、提示词版本、对话会话、工具调用、写入确认、AI 运行日志、外发数据快照。

- 关键字段：模型供应商、模型授权版本、`ModelCredentialRef`、系统策略版本、moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略、promptVersion、permissionSnapshotId、confirmationStatus、confirmedBy、toolCallTrace。
- 唯一约束：同系统内 Agent 策略编码或版本唯一；同一次写入确认的幂等键唯一；模型凭证引用按平台配置版本管理。
- 索引：系统 Agent 策略状态、会话创建人、业务对象、确认状态、模型授权版本、策略版本、traceId。
- 租户隔离：系统 Agent 只能在当前 `SystemSwitchContext/TenantSwitchContext` 下访问业务数据；平台 Agent 只生成平台任务、平台消息和平台日志，不写系统业务数据。
- 审计与版本：AI 写入必须保存原始对话、字段差异、权限裁剪、人工确认、失败补偿、业务日志 traceId；保留期限和归档策略需在 API 阶段裁决。

### 3.10 日志、后台任务、运维保障

建议对象：登录日志、业务日志、审计日志、后台任务、任务明细、导入导出批次、文件、文件引用、备份任务、恢复演练、归档策略、功能开关、容量配额、限流策略、部署回滚配置、缓存失效记录。

- 关键字段：logLevel、logType、actor、moduleId、objectId、action、result、IP、设备、requestId、traceId、auditLogId、taskId、bizType、idempotencyKey、status、progress、resultFile、errorFile、quotaType、rateLimitKey、backupVersion、archiveScope。
- 唯一约束：后台任务按 `bizType + idempotencyKey` 唯一；功能开关按作用域和 key 唯一；容量配额按 `scope + quotaType` 唯一；限流策略按维度和接口唯一。
- 索引：日志时间、操作人、对象、结果、traceId；后台任务状态、创建人、业务类型；导入导出批次、文件引用、备份版本、归档状态。
- 租户隔离：平台日志与系统日志分层；系统业务日志、任务、文件引用、归档策略必须带 `systemId/tenantId`；平台级运维数据不得暴露系统业务明细。
- 审计与版本：后台任务保存排队、处理中、成功、部分成功、失败、取消、重试和回滚边界；备份恢复和部署回滚必须记录破坏性确认、执行结果、审计日志和 traceId。

## 4. 必须落成可建模对象

- `SystemSwitchContext`：系统切换后端返回或持久化的上下文对象，包含 `systemId`、`tenantId`、`systemMemberId`、`accountMemberBindingId`、有效角色、数据范围、权限快照和消息/待办聚合范围。
- `TenantSwitchContext`：租户切换上下文，包含 `tenantId`、`tenantRoleIds`、`tenantDataScope`、`isTenantSwitchable`、`disabledReason`。
- `EffectivePermissionSnapshot`：有效权限快照，保存版本、来源角色、显式拒绝、字段权限、动作权限、数据范围表达式和解释链路。
- `NoMemberAccessRequest`：认证成功但无系统成员映射的申请对象，包含 requestId、status、identityProvider、externalUserId、targetSystemId、tenantId、requestRole、approverId、approveResult、roleIds、dataScope、rejectReason、traceId。
- `AgentPolicyScope`：系统 Agent 策略范围，包含 moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略和策略版本。
- `DailyReportAutoSourceRule`：日报自动草稿来源规则，约束任务、待办、消息、业务日志和审批记录的权限内读取范围。
- `message_delivery_log`：消息投递日志，记录渠道、状态、失败原因、重试、回执、免打扰、归档和 traceId。
- SecretRef 系列对象：`IdentityProviderSecretRef`、`OpenApiSecretRef`、`ModelCredentialRef` 和 `SecretRotationJob`，所有密钥只展示引用、版本、状态、到期和最近使用。

## 5. API 阶段需 PM 裁决的 P2 点

- `SystemSwitchContext` 与 `EffectivePermissionSnapshot` 是同接口合并返回，还是上下文对象和权限快照拆接口返回。
- `EffectivePermissionSnapshot` 的版本号生成、缓存失效、显式拒绝优先级和数据范围表达式语法。
- 动态业务数据的首期存储形态：record/value/index/child_row/relation/history/sequence 是否一次性铺齐，还是按运行态列表、详情、导入导出分批落库。
- 模块配置多租户覆盖策略：配置只属 `systemId`，还是允许租户级覆盖字段、列表、字典和页面动作。
- 消息目标模型：平台/系统层级、targetType、targetId、跳转前置上下文、read/archive 状态和 `message_delivery_log` 的拆分边界。
- Secret 明文托管位置、加密方式、轮换状态机、失败回滚、最近调用检查和备份恢复边界。
- `NoMemberAccessRequest` 审批通过后的系统成员创建、员工绑定、角色分配和数据范围是否同事务落库。
- AI Agent 原始对话、工具调用、外发数据快照、提示词版本、模型授权版本的保留期限与归档策略。
- 后台任务状态机、取消/重试/回滚边界、结果文件生命周期和任务明细归档策略。
- 首期不支持的 SSO 协议、短信渠道、Webhook 渠道或 AI 模型供应商，需要 API 返回明确禁用态、错误码和前端可显示原因。
