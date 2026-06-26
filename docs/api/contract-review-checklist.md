# API 契约复核清单

> 用途：`docs/api/api.md` 合并后复核，不替代正式 contract-sync。
> 当前 gate：`design_user_approved=true`，`api_frozen=true`，`tasks_planned=true`。

## 1. 必须存在

- `docs/api/api.md` 有版本号、状态、冻结时间占位。
- 明确说明 Build 必须按 `docs/tasks/plan.md` 与 `TASK-*.md` 的输出路径实现。
- 四份分片均被引用：DB、后端、前端、测试。
- 所有接口统一返回 `requestId`、`traceId`，写操作返回或记录 `auditLogId`。

## 2. 一等对象

- `SystemSwitchContext`
- `TenantSwitchContext`
- `EffectivePermissionSnapshot`
- `NoMemberAccessRequest`
- `AgentPolicyScope`
- `DailyReportAutoSourceRule`
- `MessageTarget`
- `message_delivery_log`
- `SecretRef` / `SecretRotationJob`
- `AsyncTask`

## 3. 边界

- 平台层与系统层分离。
- 平台消息和平台 Agent 不直接打开或写入系统业务详情。
- 系统业务数据归属 `systemId/tenantId/moduleId`，不用旧 `appId`。
- 模块组只做导航、排序、可见性和发布关系。
- 前端不拼接权限上下文，所有权限和禁用原因由服务端返回。

## 4. 运行态能力

- 列表支持分页、筛选、排序、列设置、整行详情、批量动作限制。
- 详情支持摘要、业务 tab、附件、打印记录、操作记录、审批侧栏。
- 导入导出通过后台任务和结果文件反馈，不常驻列表下方。
- 工作管理固定为仪表盘、项目任务、普通任务、日报。
- 任务列表/看板互斥视图，列表分页，免费看板滚动加载。

## 5. 可测性

- che 11 步主剧本能映射到 API 契约。
- 所有关键失败返回 `errorCode` 和 `disabledReason`。
- 后台任务有统一状态机和结果文件/错误文件。
- 权限变更有 `permissionVersion` 和缓存失效规则。
- 日志能用 `traceId` 串联登录、业务、审批、导入导出、Agent、OpenAPI。

## 6. 冻结前不允许

- 存在 P0/P1 open issue。
- P0 入口落到 generic/default。
- Secret 明文出现在返回对象、日志、导出或备份。
- API 契约只有页面名称，没有业务对象和字段。
- 直接创建 `docs/tasks/plan.md` 或进入实现。
