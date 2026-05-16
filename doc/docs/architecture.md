# 系统架构说明

## 1. 总体结构

examine2 是一套 **低代码 + 工作流 + 附件** 的多租户业务平台，采用 Maven 多模块后端 + UniApp 手机端。

```
┌─────────────────────────────────────────────────────────────┐
│  mobile/uniapp (Vue3 + Vite + uni-ui + Pinia)               │
│  pages/*  →  api/*  →  HTTP (Bearer + X-Request-Id)         │
└───────────────────────────┬─────────────────────────────────┘
                            │ REST /v1/*
┌───────────────────────────▼─────────────────────────────────┐
│  examine-web（入口层）                                        │
│  Controller、鉴权 Filter、Flyway、Swagger/Knife4j              │
└───────────────────────────┬─────────────────────────────────┘
                            │ 调用 manage 服务
┌───────────────────────────▼─────────────────────────────────┐
│  examine-plat / examine-module / examine-flow /               │
│  examine-app / examine-upload（领域模块）                     │
│  entity · mapper · service · manage（业务逻辑归位于此）       │
└───────────────────────────┬─────────────────────────────────┘
                            │
              MySQL (Flyway) + Redis (Token 会话)
```

### 模块职责

| 模块 | 职责 |
|------|------|
| **examine-web** | HTTP 入口、系统态/平台态 Controller、全局配置、DB 迁移 |
| **examine-plat** | 平台账号、系统/租户、平台 RBAC |
| **examine-module** | 低代码元数据（App/Model/Field）、EAV 记录、字典、列表视图、导出、系统 RBAC |
| **examine-flow** | 流程模板/版本/实例/任务/轨迹 |
| **examine-upload** | 附件实体与服务（存储由 web 层 SystemUploadController 编排 local） |
| **examine-app** | 对外 OpenAPI 应用（accessKey/secret） |

**约定**：CRUD 生成代码尽量不手改；自定义逻辑放在各模块 `manage` 包；模块专属 API 在对应模块或 `examine-web` 的 `controller` 中按「系统态」暴露。

---

## 2. 鉴权与会话

### 平台登录

- `POST /v1/platform/auth/login` → Redis 中 token，响应 `Authorization: Bearer …`
- `POST /v1/platform/auth/register`：手机端 `pages/auth/register`，也可用 Swagger 注册首账号
- `POST /v1/platform/auth/refresh`：401 时手机端 `api/http.ts` 自动刷新一次

### 系统上下文

- 用户创建的系统列表：`GET /v1/platform/systems`
- 进入系统：`POST /v1/platform/context/enter-system` → 返回 `SessionPayload`（`systemId`、`tenantId` 等）
- 手机端持久化：`store/context.ts` + Pinia `stores/session.ts`
- 守卫：`utils/guard.ts` — 未登录 → 登录页；未进系统 → 系统列表

### 系统内权限

- 菜单 + `apiPattern` / `permKey` + 角色成员（`/v1/system/module/rbac/*`）
- 权限预览：`GET /v1/system/auth/perm-preview?uri=…`

平台用户均可登录；**能操作哪些系统**由用户自己创建/拥有的 `un_plat_system` 决定，不重复做「系统成员授权」表。

---

## 3. 低代码数据模型

- **元数据**：`un_module_app` / `un_module_model` / `un_module_field`
- **业务数据**：`un_module_record` + EAV `un_module_record_data`（`field_code` + `value_text`）
- **查询**：`POST /v1/system/records/query`，DSL 白名单（禁止任意 SQL）
- **字典**：`un_module_dict` / `un_module_dict_item`，供表单单选/多选
- **列表视图**：视图、列、筛选模板
- **导出**：模板、字段、同步 CSV、异步 export-job

变更历史在 facade 内写入 `un_module_record_history`；**系统态查询 API 与手机页 v1 未做**。

---

## 4. 工作流

- 模板 `flow_temp` → 版本 `flow_temp_ver` → 节点/连线/条件/全局与节点设置
- 实例 `flow_record`、任务 `flow_task`、参与人 `flow_task_actor`
- 手机端：模板管理、发布、发起、待办/抄送、任务办理、实例详情（tasks/actions/traces）

MVP 以 **关系表 + 列表编辑** 跑通；非完整 BPMN 设计器。

---

## 5. 附件

- `POST /v1/system/uploads`（multipart，`file`）
- 默认 **local** 存储：`examine.upload.local-root-path`（默认 `data/uploads`），按 `systemId/tenantId/日期` 分目录
- 手机端：`api/upload.ts` — `pickSingleFilePath` 兼容 H5 / 微信小程序 / 图片回退

---

## 6. 手机端架构（a2 结论）

### 分层

```
pages/          UI + 路由参数
  ↓
api/            按域封装（platform、meta、module、records、flow、upload、platformAuth、misc）
  ↓
api/http.ts     统一 baseURL、鉴权头、401 刷新、错误 toast
```

页面 **禁止** 直接 `httpGet/httpPost`（已迁移完成）。

### 状态

- **Pinia** `stores/session`：token、env、SessionPayload
- **store/context.ts**：SessionPayload 的 storage 读写（与 Pinia 同步）

### UI 约定

- 布局：`ui/Page.vue`、`ActionBar`、`EmptyState`、`ErrorBlock`
- 样式：`styles/theme.css`
- 异步：`composables/usePageRequest.ts`（loading + error）

### 导航

- 启动：`pages/boot/health`（`GET /ping`）
- Tab：工作台 / 应用 / 待办 / 我的
- `pages/index/index`：按登录与系统上下文重定向

---

## 7. 可观测性

- 请求头 `X-Request-Id` 贯穿手机端与后端（MDC）
- Actuator / Prometheus（后端配置见 `application.yml`）
- Flyway：`V1`–`V13` 迁移与种子数据

---

## 8. 有意未纳入 v1 的边界

| 能力 | 说明 |
|------|------|
| OpenAPI `/v1/open/*` | 给第三方集成，非手机端 |
| `/v1/platform/apps` | 对外应用密钥管理 |
| 记录历史 UI | 无系统态 list API |
| 图形化流程设计器 | 列表 + JSON 为主 |
| 记录历史 diff 对比 | 当前为 dataJson 快照列表，无 side-by-side diff |

详见 [mobile-api-coverage.md](./mobile-api-coverage.md)。
