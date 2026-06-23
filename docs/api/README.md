# API 契约（Build 期）

> **状态:** 自 `.oldbk` 恢复实现后的**事实标准**，待 PM 冻结为 `api.md` 全文。

## 来源

| 文件 | 说明 |
|------|------|
| [`frontend/docs/api-contract-map.md`](../../frontend/docs/api-contract-map.md) | 174 端点路由映射 |
| [`frontend/src/api/endpoints.ts`](../../frontend/src/api/endpoints.ts) | Typed SDK 定义 |
| [`frontend/src/api/types.ts`](../../frontend/src/api/types.ts) | 请求/响应类型 |

## 分组概览

- **AUTH** — 登录、注册、刷新、me  
- **PLAT** — 我的系统、平台账号/角色/配置  
- **SYS / MEM / RBAC / DICT** — 系统上下文、成员、角色、字典  
- **APP / MOD / FIELD / UI** — 应用、模块、字段、页面发布  
- **RUN** — 运行态列表/详情/保存  
- **FLOW** — 流程模板、待办、审批  
- **FILE / EXP** — 附件、导出  
- **OPM / AUD / OPS** — OpenAPI、审计、运维  

## 系统内请求头

进入系统后（`SYS-001`）业务 API 须携带：

- `Authorization: Bearer …`
- `X-Tenant-Id`
- `X-System-Id`
- `X-Member-Id`

## 与新 UI 的对应

| 原型页 | 主要 API |
|--------|----------|
| runtime-list | RUN-002~010, SYS-001 |
| admin-modeling | FIELD-*, UI-*, MOD-* |
| admin-roles | RBAC-005~013 |
| admin-flow | FLOW-001~006 |

完整路径见 `api-contract-map.md` §SDK 端点分组。
