# 前端技术方案与落地建议

本文档面向「当前无前端、无法登录联调」的现状，给出可执行的技术选型、工程形态与分阶段落地路径，并与仓库内后端契约（见根目录 [README.md](../../README.md)）保持一致。

---

## 1. 背景与目标

| 现状 | 目标 |
|------|------|
| 仅有 REST API，无浏览器端 | 能登录、持会话、调用受保护接口，可视化验证平台能力 |
| 鉴权为服务端会话 + token | 前端统一携带 `Authorization: Bearer <token>`，业务请求按需带 `X-System-Id` / `X-Tenant-Id` |

**原则**：先做出「最小可用控制台壳」（登录 → 首页/占位 → 退出），再迭代业务页面；避免一上来大而全的低代码设计器。

---

## 2. 与后端对齐的契约（实现前端时必须遵守）

- **登录**：`POST /api/v1/platform/auth/login`，请求体用户名/密码，响应中含 `token`（以及 `account` 信息）。
- **后续请求**：在请求头增加 `Authorization: Bearer <token>`（与后端 [TokenAuthenticationFilter](../../examine-web/src/main/java/com/unique/examine/web/security/TokenAuthenticationFilter.java) 一致）。
- **当前用户**：`GET /api/v1/platform/auth/me`（需登录）。
- **登出**：`POST /api/v1/platform/auth/logout`（可选，用于清理服务端会话）。
- **平台态 / 系统态**：除 README 中列出的「平台公共接口」外，自建系统相关接口需带 **`X-System-Id`**；多租户时带 **`X-Tenant-Id`**（未开启多租户可为 `0`）。具体顺序与错误码以 OpenAPI 与后端实现为准。

前端应集中封装 HTTP 客户端（拦截器）：自动附加 token、按路由/全局状态注入 `systemId`/`tenantId`，并在 401 时统一跳转登录页。

---

## 3. 技术选型建议

### 3.1 推荐组合（平衡交付速度、生态与团队常见栈）

| 层级 | 选型 | 说明 |
|------|------|------|
| 语言 | **TypeScript** | 与 OpenAPI/模型对齐，减少联调低级错误 |
| 构建 | **Vite** | 冷启动快，本地代理到后端方便 |
| 框架 | **Vue 3**（Composition API）或 **React 18** | 二选一即可；团队熟悉哪个用哪个 |
| 路由 | **Vue Router** / **React Router v6** | 配合路由守卫做登录态校验 |
| 全局状态 | **Pinia**（Vue）或 **Zustand**（React） | 存 token、当前用户、`systemId`/`tenantId` 等 |
| UI 组件 | **Ant Design Vue** 或 **Ant Design (React)** | 后台类控制台成熟；备选 Element Plus（Vue）、MUI（React） |
| HTTP | **axios**（或 ofetch + 自行封装） | 拦截器里挂 Bearer 与业务 Header |
| 包管理 | **pnpm**（推荐）或 npm/yarn |  monorepo 时 pnpm workspace 更省事 |

**若团队无偏好**：Vue 3 + Vite + TypeScript + Pinia + Ant Design Vue 在国内文档与示例密度高，单人全栈迭代往往更快。

### 3.2 不推荐作为第一阶段的方案

| 方案 | 原因 |
|------|------|
| 纯服务端模板（JSP/Thymeleaf 塞在 `examine-web`） | 与前后端分离、OpenAPI 演进方向不一致，后期迁移成本高 |
| Next.js 全栈作为唯一前端 | 若部署仍是「静态资源 + 独立域名」或「丢进 Nginx」，SSR 收益有限；除非明确要 SEO 或 BFF |
| 微前端（qiankun 等） | 当前仅缺控制台壳，过度设计 |

---

## 4. 工程与仓库形态

| 形态 | 适用 | 说明 |
|------|------|------|
| **独立前端仓库** | 多人、独立发版 | `examine2` 仅保留后端；CI 分别构建 |
| **同仓库 `frontend/` 目录**（推荐起步） | 单人全栈、联调频繁 | 根目录 `pnpm -C frontend dev`，Vite `server.proxy` 指向 `http://localhost:<后端端口>` |

与 Java 同仓时，注意 **不要** 把 `node_modules` 打进 JAR；生产环境由 Nginx/CDN 托管静态资源，或通过网关反代到 OSS。

---

## 5. 本地联调要点

1. **跨域**：开发期用 Vite 代理把 `/api` 转到后端，避免浏览器 CORS 折腾；生产由网关或 Nginx 同域反代。
2. **环境变量**：`VITE_API_BASE` 指向网关或直连后端（仅开发代理可为空字符串，走相对路径 `/api`）。
3. **Swagger**：后端已开放 `/v3/api-docs`、`/swagger-ui` 等（见 `TokenAuthenticationFilter` 白名单），联调时可对照请求路径与模型。

---

## 6. 可选：从 OpenAPI 生成类型与客户端

若希望前后端字段强一致，可在构建链中增加：

- 拉取或固定 `openapi.json`（由 SpringDoc 导出）；
- 使用 **openapi-typescript** + **openapi-fetch**（或 **orval** 生成 axios hooks）生成类型与调用封装。

**建议时机**：登录与系统切换流程跑通后再引入，避免早期 API 变动带来大量生成代码冲突。

---

## 7. 分阶段落地路径（建议）

| 阶段 | 交付物 | 验收 |
|------|--------|------|
| **MVP** | 登录页、token 存储（建议 `sessionStorage` 或内存 + 刷新策略）、axios 拦截器、`/auth/me` 展示、退出 | 浏览器中能登录并看到当前账号 |
| **P1** | 布局（侧栏/顶栏）、系统列表与「进入系统」、`X-System-Id` 注入 | 切换系统后请求头正确 |
| **P2** | 多租户选择、`X-Tenant-Id`、与 README 中 400/403 行为一致的错误提示 | 租户边界可测 |
| **P3** | 按模块迭代 flow / lowcode / message / todo 等业务页 | 按产品里程碑 |

---

## 8. 安全与体验（控制台基线）

- Token 勿放 `localStorage` 若 XSS 风险不可控；更稳妥是 **httpOnly Cookie**（需后端配合改登录响应与 CSRF 策略）。在现有 Bearer 方案下，至少避免把 token 写入可被第三方脚本读取的长期存储。
- 路由守卫：无 token 禁止进入业务路由；token 失效统一处理 401。

---

## 9. 小结

- **技术栈**：TypeScript + Vite +（Vue 3 或 React 18）+ 对应路由与轻量状态管理 + Ant Design 系 + axios 拦截器。
- **工程**：优先 **同仓 `frontend/` + Vite 代理** 快速看到登录效果；与后端契约重点是 **Bearer** 与 **`X-System-Id` / `X-Tenant-Id`**。
- **节奏**：先 MVP 登录壳，再系统切换与租户，最后业务模块；OpenAPI 代码生成可在 API 稳定后接入。

若后续确定选用 Vue 或 React 其中一种，可在本目录下追加 `scaffold.md`（脚手架命令、目录约定、环境变量示例），与 `application.yml` 中的端口保持一致即可。

---

## 10. 仅移动端：Flutter / uni-app 与三端调试

若**只做手机 App**（不做 Web 控制台），并需覆盖 **iOS、Android、鸿蒙**，见专题： **[mobile-flutter-uniapp.md](./mobile-flutter-uniapp.md)**（方案对比、可行性、实现难度、调试复杂度，以及 **苹果手机真机调试** 与 **Windows 开发** 的硬约束）。
