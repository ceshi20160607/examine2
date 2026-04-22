## 背景与目标

在 `mobile/uniapp` 下使用 **UniApp + uni-ui** 开发手机端，目标是：

- **多端发布**：H5 / 微信小程序 / Android / iOS 都可发布。
- **最小闭环优先**：先跑通「登录 → 携带 token 请求 → 进入系统上下文 → 核心功能页」。
- **任务驱动交付**：以“可验证的任务”拆分，逐个实现并可独立验收。
- **对齐后端边界**：手机端只做展示与调用，领域逻辑留在后端模块 `*.manage/**`。

## 非目标（第一版不做）

- 极致 UI 动效与复杂主题系统（后续迭代再做）。
- 离线模式、复杂缓存一致性。
- IM/实时推送（如需，后续另开专题）。

---

## 端到端数据流（关键）

### 1) 登录与会话

- 登录入口：`/v1/platform/auth/login`
- 客户端保存：
  - `token`（Authorization: Bearer）
  - `account`（可选缓存用于展示）
- 每个请求自动注入 header：
  - `Authorization: Bearer <token>`
  - `X-Request-Id`（可选，客户端生成；便于排障）
- 401：清 token → 跳转登录

### 2) 系统上下文（systemId/tenantId）

后端大量接口依赖 `AuthContextHolder` 中的 `systemId/tenantId`。手机端必须提供“进入系统”的动作。

设计原则：

- **显式系统切换**：登录后先进入“系统选择页”，选择一个系统进入。
- **系统上下文存储**：保存 `currentSystemId`（以及可选 tenantId）到 store。
- **调用后端进入系统接口**：若后端是“切换上下文写入会话”，客户端在切换时必须调用该接口；若后端只要求传参，则客户端在每次请求带上 query/header（以实际 controller 为准）。

> 实施时以 `examine-web` 实际实现为准；如约定不一致，更新本文档并以代码为准。

---

## 信息架构（IA）与导航

第一版采用 **Tab + 二级列表页**，适配所有端：

- Tab1：**工作台**
  - 我创建的系统（或最近进入的系统）
  - 快捷入口：记录 / 待办 / 上传
- Tab2：**应用（低代码）**
  - Apps → Models → Records
  - Dicts / ListViews（配置入口可先隐藏在“更多”）
- Tab3：**待办**
  - 待办列表 / 抄送列表
- Tab4：**我的**
  - 当前账号信息
  - token 刷新 / 退出登录
  - 环境信息（dev/test/prod，便于排障）

---

## 页面清单（第一版最小可用）

### P-0 启动与健康检查
- `pages/boot/health`
- 调用后端 ping/health（以 `PingController` 或 actuator 为准）
- 验收：能显示后端连通性、请求耗时、requestId

### P-1 登录
- `pages/auth/login`
- 接口：`POST /v1/platform/auth/login`
- 验收：保存 token；跳转系统选择页

### P-2 系统选择/进入
- `pages/platform/systems`
- 接口：
  - `GET /v1/platform/systems`
  - 进入系统：以 `PlatformContextController` 实际接口为准（可能是 enter/switch）
- 验收：选择系统后可成功访问 `/v1/system/**`

### P-3 低代码：Apps/Models/Fields（元数据）
- `pages/system/module/meta/*`
- 接口：`/v1/system/module/meta/**`
- 验收：能列出 apps/models/fields（编辑能力可后置）

### P-4 业务数据：Records（核心）
- `pages/system/records/list`、`pages/system/records/detail`、`pages/system/records/edit`
- 接口：`/v1/system/records/**`
- 验收：创建/更新/查询记录可用

### P-5 Flow：待办箱（核心）
- `pages/system/flow/inbox`
- 接口：`/v1/system/flow/inbox/**`
- 验收：待办/抄送能展示；抄送可标已读

### P-6 Flow：办理
- `pages/system/flow/task`
- 接口：`/v1/system/flow/**`
- 验收：approve/reject/withdraw/terminate/transfer 可用（按后端实现）

### P-7 Upload：上传/下载
- `pages/system/upload`
- 接口：`/v1/system/upload/**`
- 验收：上传成功返回 fileId，并可打开/下载（按后端能力）

---

## API 层设计（适配多端）

### 请求封装（`src/api/http.ts`）

- 统一 `baseURL`（dev/test/prod）
- 请求拦截：
  - 注入 `Authorization`
  - 注入 `X-Request-Id`（随机短串）
- 响应拦截：
  - 401 → logout + 跳转登录
  - 403/400 → toast 提示 `message`
  - 500 → toast + 展示 `requestId`（如后端返回）

### 模块化 API（`src/api/*.ts`）

- `api/auth.ts`：platform auth
- `api/platform.ts`：systems、permissions
- `api/module-meta.ts`：apps/models/fields/relations
- `api/records.ts`
- `api/flow.ts`
- `api/upload.ts`

---

## 状态管理（store）

推荐轻量 store（Pinia 或 uni-app 自带方案均可），最少包含：

- `authStore`：token、account、登录态
- `contextStore`：currentSystemId、currentTenantId（如有）
- `uiStore`：环境/调试开关

---

## 任务拆分（实现顺序）

> 每个任务必须可手动验收，通过后再进入下一任务。

- **T-0** 初始化 UniApp 工程（uni-ui 引入）+ 路由跑通
- **T-1** HTTP 封装（baseURL/拦截器/错误处理）
- **T-2** 登录页对接 `/v1/platform/auth/login`
- **T-3** 系统列表 + 进入系统上下文
- **T-4** module meta：apps/models/fields 列表页
- **T-5** records：列表/详情/创建编辑（最小字段集）
- **T-6** flow inbox：待办/抄送列表
- **T-7** flow action：approve/reject 等
- **T-8** upload：上传并回显 fileId

---

## 风险与对策

- **系统上下文接口不一致**：以 `examine-web` 实际 controller 为准；若需新增“enter system”接口，后端补一个薄 controller（仍遵循模块边界）。
- **多端差异**：
  - 上传：小程序与 H5/form-data 差异 → 统一封装 `uploadFile` 适配层
  - 文件预览：H5 可直接打开；小程序需走 `openDocument`/下载临时文件
- **权限拦截**：module API 有路径权限拦截 → 手机端需优先完成 RBAC/默认菜单种子（后端已有 bootstrap）

---

## 需要你确认的设计点

1) 第一版导航是否按“工作台/应用/待办/我的”四个 Tab？
2) 记录（records）是否作为第一版核心（优先于 dict/listview 的配置能力）？

