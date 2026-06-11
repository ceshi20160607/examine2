# FE-025 P13 可用性返工页面契约

## 目标

关闭用户部署反馈中的 P0/P1 可用性问题：普通用户不看接口文档，也能从登录进入系统、配置应用模块、发布并在运行台新增记录。

## 页面到接口映射

| 页面 | 主接口 | 上下文要求 | 权限要求 | P13 变化 |
| --- | --- | --- | --- | --- |
| 登录 | AUTH-002、AUTH-005 | 无 | 平台账号 | 登录后进入“我的系统”。 |
| 我的系统 | PLAT-001、PLAT-002、SYS-001 | Authorization | PLAT_SYSTEM_CREATE 或已有成员系统 | 新增“创建一个业务系统”；创建后进入系统总览。 |
| 系统总览 | SYS-001、SYS-002、SYS-004 | X-System-Id、X-Tenant-Id、X-Member-Id | 系统成员 | 作为新系统首屏，承接应用配置和运行台入口。 |
| 应用 | APP-001、APP-002 | 系统、租户、成员 | APP_VIEW、APP_CREATE | 空表单，不预填测试数据。 |
| 模块 | MOD-001、MOD-002 | 系统、租户、成员、应用 | MODULE_VIEW、MODULE_CREATE | 通过应用行按钮或步骤进入，不生成 `current` 路由。 |
| 字段 | FIELD-001、FIELD-002 | 系统、租户、成员、模块 | FIELD_VIEW、FIELD_CREATE | 空表单，不预填测试字段。 |
| 页面与发布 | UI-002、UI-004、UI-006、UI-007、UI-008、MOD-006、MOD-007 | 系统、租户、成员、模块 | PAGE_VIEW、PAGE_EDIT、MODULE_PUBLISH | 保存页面、菜单动作、发布检查、发布模块。 |
| 运行台 | RUN-001、RUN-002、RUN-003、RUN-004 | 系统、租户、成员、已发布模块 | RECORD_VIEW、RECORD_CREATE | 隐藏 schema/模块 ID 等技术文案，新增记录不预填假数据。 |

## 上下文与权限

- `SYS-001` 返回的 `currentTenant`、`currentMember`、`permissions` 必须写入 `systemContextStore` 与 `permissionStore`。
- 系统内请求必须携带 `Authorization`、`X-System-Id`、`X-Tenant-Id`、`X-Member-Id`。
- `SYS_MANAGE_ALL` 仅映射系统内管理权限，不扩展为平台级或运维权限。

## 空态、禁用态与错误态

- 无业务模块：提示“暂无可用业务模块，请联系管理员发布模块”。
- 未选择模块：提示“请选择业务模块”。
- 未打开表单：提示“请选择已发布模块后点击打开业务表单”。
- 成功反馈：主提示为“操作已完成”，请求号仅作为辅助排障信息。
- 生产环境：不执行 `smokeApi` 查询参数入口。

## TEST-011 对应断言

- 创建系统后进入系统总览。
- 应用创建按钮在系统创建人上下文中可用。
- 应用、模块、字段、页面发布、运行台新增记录均通过真实浏览器完成。
- 页面无 `current` 链接，无平台审计/运维混入系统内侧栏，无 `COMMON_OK`/`PERM_DENIED` 主提示。
