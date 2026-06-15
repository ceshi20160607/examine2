# FE-015 系统管理域真实 UI 证据

## 范围

P9 覆盖系统管理域四组真实页面：

- 成员：`system.members`
- 部门：`system.departments`
- 系统角色：`system.roles`
- 字典：`system.dict`

本任务不覆盖应用模块、运行台、流程、文件、OpenAPI、审计运维。

## 页面与 API

| 页面 | 路由 | API | 真实 UI 证据 |
| --- | --- | --- | --- |
| 成员 | `/systems/:systemId/members` | `MEM-001` 至 `MEM-007` | 成员总数、邀请成员表单、成员列表、详情、编辑、启停、分配角色按钮 |
| 部门 | `/systems/:systemId/departments` | `RBAC-001` 至 `RBAC-004` | 部门总数、创建部门表单、部门树扁平展示、编辑、删除禁用原因 |
| 系统角色 | `/systems/:systemId/roles` | `RBAC-005` 至 `RBAC-013` | 角色总数、创建角色表单、权限目录加载、授权读取、授权保存 |
| 字典 | `/systems/:systemId/dict` | `DICT-001` 至 `DICT-011` | 字典类型表单、字典项表单、usage 查询、启停、version 删除、缓存版本展示 |

## 上下文与权限

- 系统内页面必须先通过 `SYS-001` 进入系统。
- typed SDK 统一携带 `Authorization`、`X-System-Id`、`X-Member-Id`，多租户场景携带 `X-Tenant-Id`。
- 未进入真实系统或仅存在 `preview-*` member 时，P9 API 调用会被前端阻断并提示先进入系统。
- 权限按钮由 `permissionStore.decide()` 判断，权限不足时禁用。

## 契约差异处理

- `MEM-001` 当前后端可能返回数组而不是分页对象，前端通过 `normalizePage()` 兼容数组返回。
- `DICT-010/011` 删除按冻结 API 传 `version` body，不采用无 body 删除。
- 字典类型停用/删除前调用 `DICT-009` usage 检查，阻塞时展示后端阻塞原因。

## 验证

- `npm.cmd run build`：pass。
- `npm.cmd ci` 后 clean build：pass。
- 静态证据：`frontend/src/App.ts` 中 `system.members`、`system.departments`、`system.roles`、`system.dict` 已分流到专用渲染函数，不再使用通用占位面板。
- 浏览器 smoke 截图：`frontend/docs/p9-members-smoke.png`、`frontend/docs/p9-departments-smoke.png`、`frontend/docs/p9-roles-smoke.png`、`frontend/docs/p9-dict-smoke.png`。
- 完整浏览器写操作 E2E 继续由 `TEST-007` 执行并记录。
