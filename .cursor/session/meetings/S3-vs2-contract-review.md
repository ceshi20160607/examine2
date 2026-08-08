# MEETING-S3-002：VS2 组织权限合同会审

## 1. 身份

- node_id: `S3-VS2-ORG-PERMISSION`
- organized_by: `pm`
- date: `2026-07-10`
- participants: `product, uiux, architect, dba, backend, test`
- authority: `leader`
- status: `completed`
- reviewed_output: `.cursor/session/rebuild/vs2-task-plan.md` draft

## 2. 独立意见结论

- product/uiux: VS2 必须闭合 PA1/PA2/PA3/S2/S3/C2/C3/C5，平台/系统/租户三级入口由权限决定；移动只承担租户切换、申请和状态查看，复杂管理限桌面。
- architect/dba/backend: 现有 VS1 缺少平台 RBAC、显式 tenant membership、部门 closure、数据范围、申请、authz epoch、operation audit 和 scope-safe guard；不得直接 CRUD。
- test: schema/API/状态/并发合同未冻结前不可编码；真实受保护资源、deny、跨 scope、V1 升级、重启和浏览器旅程均为 P0/P1 Gate。

## 3. P0 决定

| topic | decision | reason/impact |
|---|---|---|
| 权限生效 | 角色权限配置采用 `DRAFT -> CHECKED -> PUBLISHED`；发布事务替换 active role-permission/data-scope、写不可变版本、提升 authz epoch。成员/角色绑定和生命周期变化立即生效并提升 epoch | 满足配置草稿/发布/历史；草稿不污染运行 |
| 平台 principal | 平台组织和角色直接绑定 `account`；系统组织和角色绑定 `member`，不把现有 system member 改造成 nullable platform member | 语义清楚，避免破坏 VS1；平台/系统事实物理分离 |
| 租户授权 | `member_tenant` 是可切换租户唯一事实；系统级 owner/admin binding 可授权全租户，但 context 仍必须落到一个 active tenant | 关闭仅依赖 default tenant/nullable role binding 的歧义 |
| 数据范围 | scope kinds 冻结为 `ALL/SELF/PRIMARY_DEPARTMENT/DEPARTMENT_TREE/SELECTED_DEPARTMENTS/SELECTED_MEMBERS/FIELD_RULE`；多角色 allow 范围并集，再与 system/tenant/member 状态取交集；无范围默认拒绝 | C5 有真实算法；显式 permission DENY 始终优先 |
| Access request | `SUBMITTED -> APPROVED/REJECTED/CANCELLED/EXPIRED`，终态不可重审；同账号/系统/目标租户最多一个活动申请；批准事务才创建/恢复 member、tenant access 和 role binding | 禁止申请即授权和并发双批准 |
| System lifecycle | `INITIALIZING -> ACTIVE/INIT_FAILED`; `ACTIVE <-> DISABLED`; `DISABLED -> ARCHIVED`; `ARCHIVED -> DISABLED`; 仅 `ARCHIVED/INIT_FAILED` 且影响检查通过可软删 | 区分访问生命周期与配置发布状态 |
| Tenant lifecycle | `ACTIVE <-> DISABLED`; `DISABLED -> ARCHIVED`; `ARCHIVED -> DISABLED`; 默认租户不可直接停用/归档 | 防止默认上下文被破坏 |
| C5 真实资源 | VS2 用真实平台/系统后台入口、租户/成员 mutation、系统 description 字段和成员列表数据范围验证 MENU/ACTION/FIELD/DATA；模块业务权限留在 VS3/VS4 扩展 | 不用权限预览冒充生效，也不越界创建假模块 |
| 错误码 | `CONTEXT_SYSTEM_MISMATCH`, `CONTEXT_TENANT_MISMATCH`, `SYSTEM_MEMBER_REQUIRED`, `AUTHZ_SNAPSHOT_STALE`, `PERMISSION_DENIED`, `VERSION_CONFLICT`, `STATE_TRANSITION_INVALID`, `ACCESS_REQUEST_CONFLICT`, `RESOURCE_NOT_FOUND` | 403/404/409/422 语义稳定且防对象枚举 |
| Audit/outbox | core 扩展 operation audit；成功 mutation 同事务记录 before/after/result，拒绝/失败独立记录；关键变更写 outbox | 管理行为可追踪且失败不丢审计 |

## 4. 冻结数据合同

VS2 新增 `un_plat_account_role`, `un_plat_department`, `un_plat_department_closure`, `un_plat_member_department`, `un_plat_member_tenant`, `un_plat_data_scope`, `un_plat_data_scope_target`, `un_plat_role_draft`, `un_plat_authz_version`, `un_plat_authz_epoch`, `un_plat_access_request`, `un_plat_quota`, `un_plat_tenant_domain`, `un_plat_system_setting`，以及 core 的 `un_sys_feature_flag`。

现有表增加必要 scope/system/tenant 冗余和组合约束：

- `member_role` 增加 `system_id` 和规范化 `tenant_key`，禁止 nullable unique 漏洞。
- `role_permission` 增加 `scope_type/scope_key`，角色和权限必须属于同 scope。
- `role` 增加 active `data_scope_id`、`is_builtin`、`published_version`。
- `context_session` 保存 role/data-scope snapshot；版本以 `authz_epoch` 为唯一事实，旧 `system.permission_version` 同步保留兼容但不独立求值。
- 部门使用 closure 防环；一个 principal 只有一个 primary department，可有辅助部门。
- platform account role 与 system member role 分表；所有系统 owner query 必带 `system_id`，租户事实再带 `tenant_id`。

## 5. 冻结 API 族

- Context: `/api/v1/context/tenants`, `/api/v1/context/tenants/{tenantId}:switch`, `/api/v1/context/systems/{systemId}/access-requests`, `/api/v1/context/access-requests`。
- Platform admin: `/api/v1/platform/admin/systems|accounts|departments|roles|permissions|permission-evaluations`，生命周期命令带 impact/reason/version/idempotency。
- System admin: `/api/v1/systems/{systemId}/admin/settings|tenants|departments|members|roles|permissions|data-scopes|access-requests|permission-evaluations`。
- 所有 ID 用 string；列表有 page/size；更新带 `version`；创建、审核和危险状态命令带 `Idempotency-Key`；mutation 返回最新对象/version/requestId/traceId 或明确 job。

## 6. Root 引导与边界

平台 Root 只通过可选环境变量 `EXAMINE_BOOTSTRAP_ROOT_USERNAME/PASSWORD/DISPLAY_NAME` 首次引导；源码、migration、日志和证据不含值。重复启动不重复账号/角色/绑定，也不覆盖已存在账号密码。普通注册者只成为其系统 owner，不成为平台管理员。

本节点不创建模块、字段、页面、Flow、OpenAPI 或业务记录。Firefox/WebKit 如本机运行时不可取得，必须在 Gate 中明确为未执行 P1，不能伪造通过；Chromium 桌面/移动和真实 API 仍是最低本地 Gate。

## 7. 决定

- decision: `VS2_CONTRACT_FROZEN`
- unresolved_p0: `none`
- user_decision_needed: `no`
- coding_allowed: `true`（只限 VS2 声明范围）
- next_action: `VS2-002 Flyway schema and generator`
