# VS2 平台/系统组织权限任务计划

## 1. Meta

- node: `S3-VS2-ORG-PERMISSION`
- linked_design: `DESIGN-EXAMINE2-V1` version `1.0`
- owner: `planner`
- controller: `pm`
- verifier: `leader`
- status: `completed`
- contract_status: `FROZEN`
- updated_at: `2026-07-11T10:39:26+08:00`

## 2. 业务完成定义

平台 Root/Admin 能在平台后台治理系统生命周期、平台组织账号和平台角色；系统 Owner/Admin 能在当前系统后台治理租户、部门、成员、角色、权限和数据范围；普通账号可对未加入系统提交申请，管理员审核后获得成员/租户访问；多租户成员可切换授权租户。服务端解释最终权限来源，显式 deny 优先，权限或成员状态变化使旧 context 失效。跨平台/系统/租户请求必须拒绝并审计。

覆盖 `JRN-PA1/PA2/PA3/S2/S3/C2/C3/C5`，关联 `REQ-PLATFORM-002/REQ-SYSTEM-001/REQ-TENANT-001/REQ-ORG-001/REQ-RBAC-001/REQ-RBAC-002/REQ-CTX-002/REQ-AUDIT-001/REQ-SECURITY-001`。

## 3. 工作图

| task | depends_on | owner | write scope | verification | status |
|---|---|---|---|---|---|
| VS2-001 Contract review | VS1 accepted | product/uiux/architect/dba/backend/test/pm | 本计划、review | 独立意见、冲突、API/schema/UX/acceptance freeze | DONE |
| VS2-002 Schema/generator | VS2-001 | dba/backend | `sql/migration/V2*`, generated base, reports | clean MySQL upgrade + empty replay + delete/regenerate | DONE |
| VS2-003 Permission/bootstrap | VS2-002 | backend/ops/test | plat manage/config, session | env-only root bootstrap、grant/deny、version invalidation | DONE |
| VS2-004 Platform governance | VS2-003 | backend/frontend/uiux | platform admin API/routes/views | PA1-PA3 real browser/API/DB readback | DONE |
| VS2-005 System governance | VS2-003 | backend/frontend/uiux | system admin API/routes/views | C2/C3/C5 real browser/API/DB readback | DONE |
| VS2-006 Request/tenant context | VS2-003 + VS2-005 | backend/frontend/test | access/context manage | S2/S3 approve/reject/switch and stale context | DONE |
| VS2-007 Frontend integration | VS2-004 + VS2-005 + VS2-006 API | frontend/uiux/test | frontend admin/access/context | desktop/mobile real API journeys | DONE |
| VS2-008 Evidence/hardening | VS2-002..007 | test/ops | tests/evidence/docs | build, integration, E2E, security, migration, restart | DONE |
| VS2-009 Gate | VS2-008 | pm/all/leader | meeting/state/node | P0/P1 closed; Leader verdict | DONE |

VS2-004 与 VS2-005 可在公共 permission/context 合同冻结后并行；平台与系统前端写入路由/公共 session store 时必须串行合并。没有真实 API 和数据库读回不能验收管理页。

## 4. Draft 数据合同

保留 VS1 account/system/tenant/member/role/permission/binding/context 表，VS2 冻结新增：

- `un_plat_department`, `un_plat_department_closure`, `un_plat_member_department`：scope-safe 组织树、闭包防环、平台账号/系统成员映射。
- `un_plat_account_role`, `un_plat_member_tenant`：平台角色绑定和显式租户访问。
- `un_plat_data_scope`, `un_plat_data_scope_target`：七类数据范围及目标。
- `un_plat_role_draft`, `un_plat_authz_version`, `un_plat_authz_epoch`：权限草稿检查发布、不可变历史和 context 失效。
- `un_plat_access_request`, `un_plat_quota`, `un_plat_tenant_domain`, `un_plat_system_setting`，以及 `un_sys_feature_flag`。
- existing role/member_role/role_permission/context/system 增加冻结会审声明的版本、snapshot 和组合约束。

所有表继续使用 string API ID、雪花主键、scope/system/tenant 约束、乐观锁、审计字段和软删边界。不能用前端传入 scope 或 JSON 权限代替服务端关系求值。

## 5. Draft API 合同

| zone | API group | 结果 |
|---|---|---|
| context | `/api/v1/context/tenants`, `/api/v1/context/tenants/{tenantId}:switch` | 只列授权租户并建立新 permission snapshot |
| access | `/api/v1/systems/{systemId}/access-requests` | 申请、查询本人状态、取消 |
| platform admin | `/api/v1/platform/admin/systems|accounts|departments|roles|permissions` | 生命周期、组织账号、角色授权和有效权限预览 |
| system admin | `/api/v1/systems/{systemId}/admin/settings|tenants|departments|members|roles|data-scopes|access-requests|permissions` | 当前系统/租户治理与审批 |

所有 mutation 使用 CSRF、server context、permission guard、version 或幂等键；错误返回真实 HTTP status 和统一 envelope。最终路径/DTO 在 VS2-001 会审后冻结。

## 6. 安全和状态 Gate

- 平台 Root 通过环境 Secret 首次引导，源码和 migration 不含默认密码；重复启动幂等。
- 平台管理员不能自动读取/修改系统业务；系统管理员不能治理平台或其他系统。
- 租户管理员和成员不能跨租户；owner 的全租户能力必须来自角色/数据范围合同，不来自前端标记。
- deny 优先于 allow；禁用 role/member/tenant/system 或更新授权后提升 permission version，使旧 context 失效。
- 申请审批条件更新防重复；批准事务原子创建/恢复 member、tenant access、role binding 和 audit。
- 删除/禁用前执行影响检查；owner/root 最后一个有效管理员不得被误删或自锁。

## 7. 证据目标

- `.cursor/session/evidence/vs2/schema.md`
- `.cursor/session/evidence/vs2/generator.md`
- `.cursor/session/evidence/vs2/backend-tests.md`
- `.cursor/session/evidence/vs2/frontend-tests.md`
- `.cursor/session/evidence/vs2/journey-e2e.md`
- `.cursor/session/evidence/vs2/acceptance.md`
- `.cursor/session/meetings/S3-vs2-review.md`
