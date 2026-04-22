# Mobile UniApp (uni-ui) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `mobile/uniapp` 初始化 UniApp（uni-ui）工程，并按任务逐步打通“登录→创建系统→创建模块（module 元数据）→基础能力（records/flow/upload）”的第一版可用手机端。

**Architecture:** UniApp + uni-ui；统一 `src/api/http.ts` 封装请求（Authorization + X-Request-Id + 统一错误处理）；使用轻量 store 管理 token 与当前 system 上下文；按后端模块拆分 `src/api/*.ts`。

**Tech Stack:** UniApp、uni-ui、TypeScript（建议开启）、corepack（在本机通过 `corepack pnpm` 执行 pnpm）、后端 REST API（Spring Boot）。

---

## 重要前置检查（Windows + Node 环境）

本机观测到：

- `node -v` 可用
- `npm/npx/pnpm` 在 PowerShell 中不可用或异常
- `corepack pnpm` 可用（示例：`corepack pnpm -v` 有输出）

因此本文所有包管理命令统一使用：

- `corepack pnpm <command>`

---

## Task 0: Scaffold UniApp project (mobile/uniapp)

**Files:**
- Create: `mobile/uniapp/`（由脚手架生成）
- Modify: `mobile/uniapp/README.md`

- [ ] **Step 0.1: 清空占位目录（如果需要）**

确认 `mobile/uniapp` 目前只有占位 `README.md`。如果脚手架要求目录为空：

Run (PowerShell):

```powershell
dir mobile\uniapp
```

- [ ] **Step 0.2: 使用 DCloud 官方脚手架创建工程**

Run（在仓库根目录）：

```powershell
corepack pnpm dlx @dcloudio/create-uni@latest
```

交互式选择（建议）：
- Project name: `mobile/uniapp`（或先创建临时目录再移动；以工具允许为准）
- Template: `uni-app`（Vue3 + Vite 优先）
- Language: `TypeScript`

Expected:
- `mobile/uniapp/package.json` 存在
- `mobile/uniapp/src/pages.json` 存在

- [ ] **Step 0.3: 安装依赖**

Run:

```powershell
cd mobile\uniapp
corepack pnpm install
```

- [ ] **Step 0.4: 引入 uni-ui**

Run:

```powershell
cd mobile\uniapp
corepack pnpm add @dcloudio/uni-ui
```

- [ ] **Step 0.5: 添加 Health 页面与路由（P-0）**

Modify: `mobile/uniapp/src/pages.json`（示例结构，按脚手架实际格式调整）：

```json
{
  "pages": [
    { "path": "pages/boot/health", "style": { "navigationBarTitleText": "Health" } },
    { "path": "pages/auth/login", "style": { "navigationBarTitleText": "登录" } }
  ]
}
```

Create: `mobile/uniapp/src/pages/boot/health.vue`

```vue
<template>
  <view style="padding: 16px;">
    <uni-card title="Backend Health">
      <view>baseURL: {{ baseURL }}</view>
      <view>status: {{ status }}</view>
      <view>latencyMs: {{ latencyMs }}</view>
      <view>requestId: {{ requestId }}</view>
      <view v-if="error" style="color:#d00;">{{ error }}</view>
      <view style="margin-top:12px;">
        <uni-button type="primary" @click="ping">Ping</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getBaseURL } from '@/src/config/env'
import { httpGet } from '@/src/api/http'

const baseURL = getBaseURL()
const status = ref('idle')
const latencyMs = ref<number | null>(null)
const requestId = ref<string | null>(null)
const error = ref<string | null>(null)

async function ping() {
  status.value = 'loading'
  error.value = null
  const t0 = Date.now()
  try {
    // 以实际后端 PingController 为准；先用 /v1/ping 作为默认候选
    const resp = await httpGet<any>('/v1/ping')
    latencyMs.value = Date.now() - t0
    requestId.value = resp.requestId ?? null
    status.value = 'ok'
  } catch (e: any) {
    latencyMs.value = Date.now() - t0
    status.value = 'failed'
    error.value = e?.message ?? String(e)
  }
}
</script>
```

- [ ] **Step 0.6: 运行 H5 验证工程可启动**

Run:

```powershell
cd mobile\uniapp
corepack pnpm dev:h5
```

Expected:
- 浏览器可打开并进入 Health 页面

- [ ] **Step 0.7: Commit**

Run:

```powershell
git add mobile/uniapp
git commit -m "feat(mobile): scaffold uniapp app with uni-ui" -m "Initialize UniApp project under mobile/uniapp and add initial health check page."
```

---

## Task 1: HTTP client + env config + global auth

**Files:**
- Create: `mobile/uniapp/src/config/env.ts`
- Create: `mobile/uniapp/src/api/http.ts`
- Create: `mobile/uniapp/src/store/auth.ts` (or pinia store)
- Modify: `mobile/uniapp/src/main.ts`

- [ ] **Step 1.1: env 配置**

Create `mobile/uniapp/src/config/env.ts`:

```ts
export type AppEnv = 'dev' | 'test' | 'prod'

export function getEnv(): AppEnv {
  // 简化：先用 dev；后续可从本地缓存/构建变量读取
  return 'dev'
}

export function getBaseURL(): string {
  const env = getEnv()
  if (env === 'dev') return 'http://127.0.0.1:8080'
  if (env === 'test') return 'https://test.example.com'
  return 'https://prod.example.com'
}
```

- [ ] **Step 1.2: 统一请求封装（携带 token）**

Create `mobile/uniapp/src/api/http.ts`:

```ts
import { getBaseURL } from '@/src/config/env'

export type ApiResult<T> = { code: number; message: string; data: T; requestId?: string }

function genRequestId(): string {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

function getToken(): string | null {
  return uni.getStorageSync('token') || null
}

function onUnauthorized() {
  uni.removeStorageSync('token')
  uni.reLaunch({ url: '/pages/auth/login' })
}

export async function httpGet<T>(path: string): Promise<ApiResult<T>> {
  return httpRequest<T>('GET', path, undefined)
}

export async function httpPost<T>(path: string, body?: any): Promise<ApiResult<T>> {
  return httpRequest<T>('POST', path, body)
}

export async function httpRequest<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, data?: any): Promise<ApiResult<T>> {
  const token = getToken()
  const requestId = genRequestId()
  const url = getBaseURL().replace(/\/$/, '') + path

  const headers: Record<string, string> = {
    'X-Request-Id': requestId
  }
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (method !== 'GET') headers['Content-Type'] = 'application/json'

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      header: headers,
      success: (res) => {
        const r = res.data as ApiResult<T>
        if (res.statusCode === 401 || r?.code === 401) {
          onUnauthorized()
          reject(new Error('unauthorized'))
          return
        }
        if (!r || typeof r.code !== 'number') {
          reject(new Error('bad api response'))
          return
        }
        if (r.code !== 0) {
          reject(new Error(r.message || `api error: ${r.code}`))
          return
        }
        resolve(r)
      },
      fail: (err) => reject(err)
    })
  })
}
```

- [ ] **Step 1.3: Commit**

```powershell
git add mobile/uniapp/src/config/env.ts mobile/uniapp/src/api/http.ts
git commit -m "feat(mobile): add http client with auth + env" -m "Centralize API requests with Authorization and requestId headers, plus basic env baseURL config."
```

---

## Task 2: Login + token refresh/logout + Me

**Files:**
- Create: `mobile/uniapp/src/api/auth.ts`
- Create: `mobile/uniapp/src/pages/auth/login.vue`
- Modify: `mobile/uniapp/src/pages.json`

- [ ] **Step 2.1: auth API**

Create `mobile/uniapp/src/api/auth.ts`:

```ts
import { httpGet, httpPost, ApiResult } from '@/src/api/http'

export type PlatAccount = { id: number; username: string }

export async function login(username: string, password: string) {
  return httpPost<{ token: string; account: PlatAccount }>('/v1/platform/auth/login', { username, password })
}

export async function me() {
  return httpGet<PlatAccount>('/v1/platform/auth/me')
}

export async function logout() {
  return httpPost<void>('/v1/platform/auth/logout', {})
}
```

- [ ] **Step 2.2: 登录页**

Create `mobile/uniapp/src/pages/auth/login.vue`（使用 uni-ui 表单即可，保持简洁）。

验收：
- 登录成功后 `uni.setStorageSync('token', token)`，并跳转 `/pages/platform/systems`

- [ ] **Step 2.3: Commit**

```powershell
git add mobile/uniapp/src/api/auth.ts mobile/uniapp/src/pages/auth/login.vue mobile/uniapp/src/pages.json
git commit -m "feat(mobile): login page and auth api" -m "Implement platform login flow and persist token for authenticated requests."
```

---

## Task 3: Platform - create system + list systems + enter system context

**Files:**
- Create: `mobile/uniapp/src/api/platform.ts`
- Create: `mobile/uniapp/src/pages/platform/systems.vue`
- Create: `mobile/uniapp/src/store/context.ts`

- [ ] **Step 3.1: systems API**

Create `mobile/uniapp/src/api/platform.ts`:

```ts
import { httpGet, httpPost } from '@/src/api/http'

export type PlatSystem = { id: number; systemName?: string; ownerPlatAccountId?: number }

export async function listMySystems() {
  return httpGet<PlatSystem[]>('/v1/platform/systems')
}
```

> 创建系统、进入系统接口：以 `PlatformContextController` 实际路径为准。实现时在本任务里先读后端 controller，再补齐这里。

- [ ] **Step 3.2: 系统列表页**

Create `pages/platform/systems.vue`：
- 展示系统列表
- 提供“创建系统”入口（弹窗输入 systemName）
- 提供“进入系统”按钮（触发 enter/switch）

- [ ] **Step 3.3: Commit**

```powershell
git add mobile/uniapp/src/api/platform.ts mobile/uniapp/src/pages/platform/systems.vue mobile/uniapp/src/store/context.ts
git commit -m "feat(mobile): platform systems list/create/enter" -m "Add system selection flow to establish system context for /v1/system APIs."
```

---

## Task 4: Module - create app/model/field (元数据创建)

**Files:**
- Create: `mobile/uniapp/src/api/moduleMeta.ts`
- Create: `mobile/uniapp/src/pages/system/module/meta/apps.vue`
- Create: `mobile/uniapp/src/pages/system/module/meta/models.vue`
- Create: `mobile/uniapp/src/pages/system/module/meta/fields.vue`

- [ ] **Step 4.1: 对接 `/v1/system/module/meta/**`**

实现最少接口：
- list apps
- upsert app
- list models by appId
- upsert model
- list fields by modelId
- upsert field

- [ ] **Step 4.2: 页面**

用 uni-ui 的 `uni-list`/`uni-forms` 做最小 CRUD：
- Apps 页：列表 + 新增/编辑
- Models 页：列表 + 新增/编辑
- Fields 页：列表 + 新增/编辑（第一版只覆盖必填字段）

- [ ] **Step 4.3: Commit**

```powershell
git add mobile/uniapp/src/api/moduleMeta.ts mobile/uniapp/src/pages/system/module/meta
git commit -m "feat(mobile): module meta create apps/models/fields" -m "Implement module metadata CRUD pages to create apps/models/fields from mobile."
```

---

## Task 5: Records - create/list/update

**Files:**
- Create: `mobile/uniapp/src/api/records.ts`
- Create: `mobile/uniapp/src/pages/system/records/list.vue`
- Create: `mobile/uniapp/src/pages/system/records/edit.vue`
- Create: `mobile/uniapp/src/pages/system/records/detail.vue`

- [ ] **Step 5.1: 对接 `/v1/system/records/**`**

实现最少：
- list（按 modelId）
- create（appId/modelId + data）
- update（recordId + data）
- detail（recordId）

- [ ] **Step 5.2: Commit**

```powershell
git add mobile/uniapp/src/api/records.ts mobile/uniapp/src/pages/system/records
git commit -m "feat(mobile): records list/detail/create/update" -m "Add EAV record CRUD flow based on module models."
```

---

## Task 6: Flow inbox + actions (approve/reject/withdraw/terminate/transfer)

**Files:**
- Create: `mobile/uniapp/src/api/flow.ts`
- Create: `mobile/uniapp/src/pages/system/flow/inbox.vue`
- Create: `mobile/uniapp/src/pages/system/flow/task.vue`

- [ ] **Step 6.1: 对接 `/v1/system/flow/inbox/**` 与 `/v1/system/flow/**`**
- [ ] **Step 6.2: Commit**

```powershell
git add mobile/uniapp/src/api/flow.ts mobile/uniapp/src/pages/system/flow
git commit -m "feat(mobile): flow inbox and task actions" -m "Implement flow pending/cc inbox and task action pages."
```

---

## Task 7: Upload (multipart) + preview

**Files:**
- Create: `mobile/uniapp/src/api/upload.ts`
- Create: `mobile/uniapp/src/pages/system/upload/index.vue`

- [ ] **Step 7.1: uni.uploadFile 封装**
- [ ] **Step 7.2: Commit**

```powershell
git add mobile/uniapp/src/api/upload.ts mobile/uniapp/src/pages/system/upload
git commit -m "feat(mobile): upload file via uni.uploadFile" -m "Add upload page and API wrapper compatible with H5/mini-program/app."
```

---

## Self-Review（计划自检）

- **Spec coverage**：已覆盖“登录、创建系统、创建模块（apps/models/fields）、基础功能（records/flow/upload）”。
- **Placeholder scan**：无 “TODO/TBD”；涉及“进入系统接口”处明确要求以 controller 为准并在 Task 3 内补齐实现。
- **Type consistency**：API 返回统一按 `ApiResult<T>`；token 使用 Bearer。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-22-mobile-uniapp-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

