# API 契约 · MVP 冻结版

| 项 | 值 |
|----|-----|
| **版本** | `1.0.0-mvp` |
| **冻结日期** | 2026-06-22 |
| **状态** | 已冻结 · Build 唯一契约源 |
| **MVP 端点数** | 163 |
| **总定义端点** | 174（含 ENH / PLACEHOLDER） |

> **事实来源：** [`frontend/src/api/endpoints.ts`](../../frontend/src/api/endpoints.ts)、[`frontend/docs/api-contract-map.md`](../../frontend/docs/api-contract-map.md)、[`page-inventory.md`](../design/page-inventory.md) §5  
> **DB 影响：** [`db-impact.md`](./db-impact.md)  
> **草案 gap：** [`_draft/backend-gap.md`](./_draft/backend-gap.md)、[`_draft/frontend-route-map.md`](./_draft/frontend-route-map.md)

---

## 1. 通用约定

### 1.1 路径前缀

| 范围 | 前缀 |
|------|------|
| 平台 / 系统管理 / 运行态 | `/api/v1/...` |
| OpenAPI 外部网关 | `/openapi/v1/...` |

### 1.2 系统内请求头

调用 `SYS-001` 进入系统后，系统域 API 须携带：

- `Authorization: Bearer …`
- `X-Tenant-Id`
- `X-System-Id`
- `X-Member-Id`

### 1.3 模块组索引

| 模块组 | 说明 |
|--------|------|
| **AUTH** | 认证与会话 |
| **PLAT** | 平台中心 |
| **SYS** | 系统上下文 |
| **MEM** | 系统成员 |
| **RBAC** | 部门 / 角色 / 权限 |
| **DICT** | 数据字典 |
| **APP** | 应用 |
| **MOD** | 模块 |
| **FIELD** | 字段 |
| **UI** | 页面 / 菜单 / 动作发布 |
| **RUN** | 运行态（列表 / 详情 / CRUD） |
| **FLOW** | 流程模板 / 待办 / 审批 |
| **FILE** | 附件 |
| **EXP** | 导出 |
| **OPENAPI** | OpenAPI 客户端管理与外部网关 |
| **AUD** | 审计日志 |
| **OPS** | 运维 |

---

## 2. 端点清单（MVP）

### AUTH — 认证与会话

| ID | Method | Path | 说明 |
|----|--------|------|------|
| AUTH-001 | POST | `/api/v1/auth/register` | 用户注册 |
| AUTH-002 | POST | `/api/v1/auth/login` | 登录 |
| AUTH-003 | POST | `/api/v1/auth/refresh` | 刷新令牌 |
| AUTH-004 | POST | `/api/v1/auth/logout` | 退出登录 |
| AUTH-005 | GET | `/api/v1/auth/me` | 当前登录用户 |
| AUTH-006 | POST | `/api/v1/auth/password/reset` | 重置密码 |

### PLAT — 平台中心

| ID | Method | Path | 说明 |
|----|--------|------|------|
| PLAT-001 | GET | `/api/v1/platform/my-systems` | 我的系统列表 |
| PLAT-002 | POST | `/api/v1/platform/systems` | 创建系统 |
| PLAT-003 | GET | `/api/v1/platform/systems` | 系统列表（管理） |
| PLAT-004 | GET | `/api/v1/platform/systems/{systemId}` | 系统详情 |
| PLAT-005 | PATCH | `/api/v1/platform/systems/{systemId}/status` | 变更系统状态 |
| PLAT-006 | GET | `/api/v1/platform/accounts` | 平台账号列表 |
| PLAT-007 | POST | `/api/v1/platform/accounts` | 创建平台账号 |
| PLAT-008 | PATCH | `/api/v1/platform/accounts/{accountId}/status` | 变更账号状态 |
| PLAT-009 | GET | `/api/v1/platform/roles` | 平台角色列表 |
| PLAT-010 | PUT | `/api/v1/platform/roles/{roleId}/menus` | 配置角色菜单 |
| PLAT-011 | GET | `/api/v1/platform/configs` | 平台配置列表 |
| PLAT-012 | PUT | `/api/v1/platform/configs/{configKey}` | 更新平台配置 |
| PLAT-013 | GET | `/api/v1/platform/accounts/{accountId}` | 平台账号详情 |
| PLAT-014 | PUT | `/api/v1/platform/accounts/{accountId}` | 更新平台账号 |
| PLAT-015 | POST | `/api/v1/platform/accounts/{accountId}/password/reset` | 重置平台账号密码 |
| PLAT-016 | PUT | `/api/v1/platform/accounts/{accountId}/roles` | 分配平台角色 |
| PLAT-017 | POST | `/api/v1/platform/roles` | 创建平台角色 |
| PLAT-018 | PUT | `/api/v1/platform/roles/{roleId}` | 更新平台角色 |
| PLAT-019 | PATCH | `/api/v1/platform/roles/{roleId}/status` | 变更平台角色状态 |
| PLAT-020 | GET | `/api/v1/platform/permission-catalog` | 平台权限目录 |

### SYS — 系统上下文

| ID | Method | Path | 说明 |
|----|--------|------|------|
| SYS-001 | POST | `/api/v1/systems/{systemId}/enter` | 进入系统（返回菜单 / 上下文） |
| SYS-002 | GET | `/api/v1/systems/{systemId}/profile` | 系统资料 |
| SYS-003 | PUT | `/api/v1/systems/{systemId}/profile` | 更新系统资料 |
| SYS-004 | GET | `/api/v1/systems/{systemId}/tenants` | 租户列表 |
| SYS-005 | POST | `/api/v1/systems/{systemId}/tenants` | 创建租户 |
| SYS-006 | PATCH | `/api/v1/systems/{systemId}/tenants/{tenantId}/status` | 变更租户状态 |
| SYS-007 | POST | `/api/v1/systems/{systemId}/tenant-context/switch` | 切换租户上下文 |

### MEM — 系统成员

| ID | Method | Path | 说明 |
|----|--------|------|------|
| MEM-001 | GET | `/api/v1/systems/{systemId}/members` | 成员列表 |
| MEM-002 | POST | `/api/v1/systems/{systemId}/members/invitations` | 邀请成员 |
| MEM-003 | GET | `/api/v1/systems/{systemId}/members/{memberId}` | 成员详情 |
| MEM-004 | PUT | `/api/v1/systems/{systemId}/members/{memberId}` | 更新成员 |
| MEM-005 | PATCH | `/api/v1/systems/{systemId}/members/{memberId}/status` | 变更成员状态 |
| MEM-006 | PUT | `/api/v1/systems/{systemId}/members/{memberId}/roles` | 分配成员角色 |
| MEM-007 | GET | `/api/v1/systems/{systemId}/members/current` | 当前成员上下文 |

### RBAC — 部门 / 角色 / 权限

| ID | Method | Path | 说明 |
|----|--------|------|------|
| RBAC-001 | GET | `/api/v1/systems/{systemId}/rbac/departments/tree` | 部门树 |
| RBAC-002 | POST | `/api/v1/systems/{systemId}/rbac/departments` | 创建部门 |
| RBAC-003 | PUT | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | 更新部门 |
| RBAC-004 | DELETE | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | 删除部门 |
| RBAC-005 | GET | `/api/v1/systems/{systemId}/rbac/roles` | 角色列表 |
| RBAC-006 | POST | `/api/v1/systems/{systemId}/rbac/roles` | 创建角色 |
| RBAC-007 | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}` | 更新角色 |
| RBAC-008 | PATCH | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/status` | 变更角色状态 |
| RBAC-009 | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | 配置角色权限 |
| RBAC-010 | GET | `/api/v1/systems/{systemId}/rbac/effective-permissions` | 当前有效权限 |
| RBAC-011 | GET | `/api/v1/systems/{systemId}/rbac/runtime-menus` | 运行态菜单 |
| RBAC-012 | GET | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | 角色权限详情 |
| RBAC-013 | GET | `/api/v1/systems/{systemId}/rbac/permission-catalog` | 系统权限目录 |

### DICT — 数据字典

| ID | Method | Path | 说明 |
|----|--------|------|------|
| DICT-001 | GET | `/api/v1/systems/{systemId}/dict/types` | 字典类型列表 |
| DICT-002 | POST | `/api/v1/systems/{systemId}/dict/types` | 创建字典类型 |
| DICT-003 | PUT | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | 更新字典类型 |
| DICT-004 | PATCH | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/status` | 变更字典类型状态 |
| DICT-005 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | 字典项列表 |
| DICT-006 | POST | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | 创建字典项 |
| DICT-007 | PUT | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | 更新字典项 |
| DICT-008 | PATCH | `/api/v1/systems/{systemId}/dict/items/{dictItemId}/status` | 变更字典项状态 |
| DICT-009 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/usages` | 字典引用统计 |
| DICT-010 | DELETE | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | 删除字典类型 |
| DICT-011 | DELETE | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | 删除字典项 |

### APP — 应用

| ID | Method | Path | 说明 |
|----|--------|------|------|
| APP-001 | GET | `/api/v1/systems/{systemId}/apps` | 应用列表 |
| APP-002 | POST | `/api/v1/systems/{systemId}/apps` | 创建应用 |
| APP-003 | GET | `/api/v1/systems/{systemId}/apps/{appId}` | 应用详情 |
| APP-004 | PUT | `/api/v1/systems/{systemId}/apps/{appId}` | 更新应用 |
| APP-005 | PATCH | `/api/v1/systems/{systemId}/apps/{appId}/status` | 变更应用状态 |

### MOD — 模块

| ID | Method | Path | 说明 |
|----|--------|------|------|
| MOD-001 | GET | `/api/v1/systems/{systemId}/apps/{appId}/modules` | 应用下模块列表 |
| MOD-002 | POST | `/api/v1/systems/{systemId}/apps/{appId}/modules` | 创建模块 |
| MOD-003 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}` | 模块详情 |
| MOD-004 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}` | 更新模块 |
| MOD-005 | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/status` | 变更模块状态 |
| MOD-006 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish-check` | 发布前检查 |
| MOD-007 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish` | 发布模块 |

### FIELD — 字段

| ID | Method | Path | 说明 |
|----|--------|------|------|
| FIELD-001 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | 字段列表 |
| FIELD-002 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | 创建字段 |
| FIELD-003 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}` | 更新字段 |
| FIELD-004 | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}/status` | 变更字段状态 |
| FIELD-005 | GET | `/api/v1/systems/{systemId}/field-types` | 字段类型目录 |

### UI — 页面 / 菜单 / 动作

| ID | Method | Path | 说明 |
|----|--------|------|------|
| UI-001 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views` | 列表视图配置 |
| UI-002 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views/default` | 保存默认列表视图 |
| UI-003 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | 表单配置 |
| UI-004 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | 保存默认表单 |
| UI-005 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | 详情页配置 |
| UI-006 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | 保存默认详情页 |
| UI-007 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/menu` | 发布菜单 snapshot |
| UI-008 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/actions` | 配置模块动作 |

### RUN — 运行态

| ID | Method | Path | 说明 |
|----|--------|------|------|
| RUN-001 | GET | `/api/v1/systems/{systemId}/runtime/menus` | 运行态菜单 |
| RUN-002 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema` | 模块运行 schema |
| RUN-003 | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/query` | 记录分页查询 |
| RUN-004 | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records` | 新建记录 |
| RUN-005 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | 记录详情 |
| RUN-006 | PUT | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | 更新记录 |
| RUN-007 | DELETE | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | 删除记录 |
| RUN-008 | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/submit` | 提交记录 |
| RUN-009 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history` | 记录变更历史 |
| RUN-010 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/relations` | 关联记录 |

### FLOW — 流程

| ID | Method | Path | 说明 |
|----|--------|------|------|
| FLOW-001 | GET | `/api/v1/systems/{systemId}/flow/templates` | 流程模板列表 |
| FLOW-002 | POST | `/api/v1/systems/{systemId}/flow/templates` | 创建流程模板 |
| FLOW-003 | PUT | `/api/v1/systems/{systemId}/flow/templates/{templateId}/graph` | 保存流程图 |
| FLOW-004 | POST | `/api/v1/systems/{systemId}/flow/templates/{templateId}/publish-check` | 发布前检查 |
| FLOW-005 | POST | `/api/v1/systems/{systemId}/flow/templates/{templateId}/publish` | 发布流程模板 |
| FLOW-006 | PUT | `/api/v1/systems/{systemId}/flow/bindings/modules/{moduleId}` | 模块流程绑定 |
| FLOW-007 | GET | `/api/v1/systems/{systemId}/flow/tasks/todo` | 待办任务 |
| FLOW-008 | GET | `/api/v1/systems/{systemId}/flow/tasks/{taskId}` | 任务详情 |
| FLOW-009 | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/actions` | 审批动作 |
| FLOW-010 | POST | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/withdraw` | 撤回实例 |
| FLOW-011 | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}` | 流程实例详情 |
| FLOW-012 | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/diagram` | 流程实例图 |
| FLOW-013 | GET | `/api/v1/systems/{systemId}/flow/workbench/cc` | 抄送列表 |
| FLOW-014 | GET | `/api/v1/systems/{systemId}/flow/workbench/started` | 我发起的 |
| FLOW-015 | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/claim` | 认领任务 |
| FLOW-016 | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/unclaim` | 取消认领 |
| FLOW-017 | GET | `/api/v1/systems/{systemId}/flow/instances` | 流程实例列表 |
| FLOW-018 | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/history` | 实例历史 |
| FLOW-019 | GET | `/api/v1/systems/{systemId}/flow/templates/{templateId}` | 模板详情 |
| FLOW-020 | GET | `/api/v1/systems/{systemId}/flow/templates/{templateId}/graph` | 模板流程图 |
| FLOW-021 | PATCH | `/api/v1/systems/{systemId}/flow/templates/{templateId}/status` | 变更模板状态 |

### FILE — 附件

| ID | Method | Path | 说明 |
|----|--------|------|------|
| FILE-001 | POST | `/api/v1/systems/{systemId}/files/upload` | 上传文件 |
| FILE-002 | GET | `/api/v1/systems/{systemId}/files` | 文件列表 |
| FILE-003 | GET | `/api/v1/systems/{systemId}/files/{fileId}` | 文件元数据 |
| FILE-004 | GET | `/api/v1/systems/{systemId}/files/{fileId}/preview` | 预览文件 |
| FILE-005 | GET | `/api/v1/systems/{systemId}/files/{fileId}/download` | 下载文件 |
| FILE-006 | DELETE | `/api/v1/systems/{systemId}/files/{fileId}` | 删除文件 |

### EXP — 导出

| ID | Method | Path | 说明 |
|----|--------|------|------|
| EXP-001 | GET | `/api/v1/systems/{systemId}/exports/templates` | 导出模板列表 |
| EXP-002 | POST | `/api/v1/systems/{systemId}/exports/templates` | 创建导出模板 |
| EXP-003 | PUT | `/api/v1/systems/{systemId}/exports/templates/{templateId}` | 更新导出模板 |
| EXP-004 | POST | `/api/v1/systems/{systemId}/exports/jobs` | 创建导出任务 |
| EXP-005 | GET | `/api/v1/systems/{systemId}/exports/jobs` | 导出任务列表 |
| EXP-006 | GET | `/api/v1/systems/{systemId}/exports/jobs/{jobId}` | 导出任务详情 |
| EXP-007 | POST | `/api/v1/systems/{systemId}/exports/jobs/{jobId}/retry` | 重试导出 |
| EXP-008 | POST | `/api/v1/systems/{systemId}/exports/jobs/{jobId}/cancel` | 取消导出 |

### OPENAPI — 客户端管理与外部网关

**管理端（OPM）**

| ID | Method | Path | 说明 |
|----|--------|------|------|
| OPM-001 | GET | `/api/v1/systems/{systemId}/openapi/clients` | 客户端列表 |
| OPM-002 | POST | `/api/v1/systems/{systemId}/openapi/clients` | 创建客户端 |
| OPM-003 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}` | 更新客户端 |
| OPM-004 | PATCH | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/status` | 变更客户端状态 |
| OPM-005 | POST | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/credentials/rotate` | 轮换凭证 |
| OPM-006 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/scopes` | 配置 scope |
| OPM-007 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/ip-whitelist` | 配置 IP 白名单 |
| OPM-008 | GET | `/api/v1/systems/{systemId}/openapi/access-logs` | 调用日志 |
| OPM-009 | GET | `/api/v1/systems/{systemId}/openapi/scope-catalog` | scope 目录 |

**外部网关（OPN · AK/SK）**

| ID | Method | Path | 说明 |
|----|--------|------|------|
| OPN-001 | POST | `/openapi/v1/records/query` | 外部查询记录 |
| OPN-002 | GET | `/openapi/v1/records/{recordId}` | 外部读取记录 |
| OPN-003 | POST | `/openapi/v1/records` | 外部创建记录 |
| OPN-004 | PUT | `/openapi/v1/records/{recordId}` | 外部更新记录 |
| OPN-005 | POST | `/openapi/v1/records/{recordId}/submit` | 外部提交记录 |
| OPN-006 | POST | `/openapi/v1/flow/tasks/{taskId}/actions` | 外部审批动作 |
| OPN-007 | GET | `/openapi/v1/files/{fileId}/download` | 外部下载文件 |

### AUD — 审计

| ID | Method | Path | 说明 |
|----|--------|------|------|
| AUD-001 | GET | `/api/v1/systems/{systemId}/audit/operation-logs` | 系统操作日志 |
| AUD-002 | GET | `/api/v1/systems/{systemId}/audit/request-logs` | 系统请求日志 |
| AUD-003 | GET | `/api/v1/systems/{systemId}/audit/error-logs` | 系统错误日志 |
| AUD-004 | GET | `/api/v1/systems/{systemId}/audit/record-changes` | 记录变更审计 |
| AUD-005 | GET | `/api/v1/systems/{systemId}/audit/openapi-logs` | OpenAPI 调用审计 |
| AUD-006 | GET | `/api/v1/platform/audit/operation-logs` | 平台操作日志 |
| AUD-007 | GET | `/api/v1/systems/{systemId}/audit/logs/{logId}` | 系统日志详情 |
| AUD-008 | GET | `/api/v1/platform/audit/logs/{logId}` | 平台日志详情 |

### OPS — 运维

| ID | Method | Path | 说明 |
|----|--------|------|------|
| OPS-001 | GET | `/api/v1/ops/health` | 健康检查 |
| OPS-002 | GET | `/api/v1/ops/config-check` | 配置检查 |
| OPS-003 | GET | `/api/v1/ops/version` | 版本信息 |
| OPS-004 | GET | `/api/v1/ops/migration/status` | 迁移状态 |
| OPS-006 | GET | `/api/v1/ops/health/components` | 组件健康明细 |

---

## 3. 非 MVP 端点（不阻塞 Build）

| ID | Method | Path | 阶段 | 说明 |
|----|--------|------|------|------|
| APP-006 | POST | `/api/v1/systems/{systemId}/apps/{appId}/copy` | ENH | 复制应用 |
| APP-007 | GET | `/api/v1/systems/{systemId}/apps/templates` | ENH | 应用模板 |
| UI-009 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/import` | ENH | 页面配置导入 |
| FILE-007 | POST | `/api/v1/systems/{systemId}/files/chunks` | ENH | 分片上传 |
| OPS-005 | PUT | `/api/v1/ops/runtime-configs/{configKey}` | ENH | 运行时配置热更 |
| IMP-001 | POST | `/api/v1/systems/{systemId}/imports/preview` | PLACEHOLDER | 导入预览 |
| IMP-002 | POST | `/api/v1/systems/{systemId}/imports/jobs` | PLACEHOLDER | 导入任务 |
| GEN-001~004 | — | `/api/v1/ops/generator/...` | INTERNAL | 代码生成器（内部） |

---

## 4. P1 增强（已知 gap · 不阻塞 MVP）

以下项来自 `_draft/backend-gap.md` 与 `db-impact.md`，Build 阶段以前端双请求或现有字段扩展 workaround，**不删不改**现有 163 个 MVP 端点语义。

| # | 领域 | gap 描述 | MVP 策略 |
|---|------|----------|----------|
| G1 | **模块组导航** | `SYS-001` 返回 menus 需 **group→modules** 树；`UI-007` snapshot 需含 `moduleGroupId` | 后端扩展响应字段；前端 RuntimeShell 顶栏组切换 |
| G2 | **模块列表过滤** | `MOD-001` 需明确「组下模块」过滤参数 | 复用现有 MOD API + query 参数，文档标注 ADD |
| G3 | **列表场景** | 运行态「场景切换」可能缺独立 `listViews` API | 读 UI-001 已发布 snapshot；无则 MOD 配置 fallback |
| G4 | **列偏好** | 用户列显隐 / 顺序 / 宽度持久化 | 可新增 `RUN-011` 或 `MEM` 扩展 JSON pref；MVP 可 localStorage |
| G5 | **右侧详情 + 审批** | 列表同屏详情需 record + flow instance | 前端 `RUN-005` + `FLOW-011` 双请求；可选后续合并 VO |
| G6 | **仪表盘设计器** | 三栏拖拽配置可能缺 designer API | MVP JSON stub 配置 |
| G7 | **对外映射** | 外部应用字段映射 | MVP 单行映射 UI；嵌套 JSON 为增强 |
| G8 | **DB 模块组表** | `un_module_*` group 表 / `group_id` 字段待核对 init.sql | P2 migration，与 G1 同步 |

---

## 5. 页面 → API 快速索引

| 页面 / 路由域 | 主要 API |
|---------------|----------|
| 登录 / 注册 / 重置密码 | AUTH-001~006 |
| 我的系统 | PLAT-001, SYS-001 |
| 平台管理 | PLAT-003~012 |
| 系统资料 / 租户 | SYS-002~007 |
| 成员管理 | MEM-001~007 |
| 部门 / 角色 | RBAC-001~013 |
| 字典 | DICT-001~011 |
| 应用 / 模块建模 | APP-001~005, MOD-001~007, FIELD-001~005, UI-001~008 |
| 运行态列表 / 表单 | RUN-001~010 |
| 流程配置 / 工作台 | FLOW-001~021 |
| 附件 / 导出 | FILE-001~006, EXP-001~008 |
| OpenAPI 管理 | OPM-001~009 |
| 审计 | AUD-001~008 |
| 运维 | OPS-001~004, OPS-006 |

完整路由映射见 [`frontend/docs/api-contract-map.md`](../../frontend/docs/api-contract-map.md) §路由到 API 映射。

---

## 6. 冻结声明

- **不删除**已定义的 174 端点；MVP Build 以 §2 共 **163** 端点为验收基线。
- **允许扩展**：§4 所列 gap 可在不破坏现有契约的前提下追加字段或可选新端点（标注 `ADD`）。
- **禁止**：冻结后擅自变更 §2 已有 path / method / 核心语义；IA 路由变更须先更新本文件并升版。
