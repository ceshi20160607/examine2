# Mobile 前端架构 + UI 统一（B+C）设计稿

日期：2026-04-24  
范围：`mobile/uniapp`（UniApp + Vue3 + TypeScript + uni-ui）

## 目标（Definition of Done）

- **架构主流化（B）**
  - 页面不直接拼接/分散调用接口，统一走 `src/api/**` 领域模块。
  - 会话/系统上下文/全局 UI 状态（loading/error/toast）具备可维护的集中管理（推荐 Pinia）。
  - 导航守卫逻辑统一、可复用；错误处理统一、用户可理解。
  - `type-check`、`build:h5`、`build:mp-weixin` 可稳定通过。
- **UI 主流化（C）**
  - 统一主题变量（颜色/字号/间距/圆角/阴影），减少页面内联 style。
  - 统一“页面骨架”：标题区、按钮组、列表区、空态/加载/错误态。
  - 关键链路页（登录、系统选择、工作台、Records 表单、Flow 发起/待办/任务、Upload）观感一致、交互一致。

## 非目标（避免过度重构）

- 不引入复杂设计器/重写所有页面为“精美商业 UI”。
- 不做与发版无关的大改（例如替换 uni-ui、引入重型 UI 框架）。
- 不强求 Web 端后台（当前以移动端为主）。

## 现状问题（从代码结果出发）

- 页面层存在 **大量内联 style**，布局/间距/组件组合不一致，维护成本高。
- API 使用方式混杂：`httpGet/httpPost`、`uni.request`、`downloadFile/uploadFile` 的 header/错误处理容易不一致（已部分修复，但仍需体系化）。
- 缺少统一的“页面骨架组件”和“空态/错误态”组件，导致 UI 观感差异大。

## 方案总览（B+C 一起做，但按顺序落地）

### Phase 1（B）：工程结构与数据流统一（优先）

1. **引入 Pinia（推荐）**
   - `stores/session.ts`：token、SessionPayload、当前 env、logout/refresh 等动作
   - `stores/ui.ts`：全局 loading/错误弹窗（可选：全局队列 toast）
2. **API 分层**
   - `src/api/http.ts` 保持为底层（request/refresh/headers/url）
   - 新增领域 API：`src/api/platform.ts`、`src/api/module.ts`、`src/api/flow.ts`、`src/api/upload.ts`
   - 页面只调用领域 API，不拼 `/v1/...` 字符串（保留极少例外）
3. **页面骨架组件**
   - `src/ui/Page.vue`：统一 padding、标题、内容区
   - `src/ui/ActionBar.vue`：统一按钮组间距/折行
   - `src/ui/EmptyState.vue`、`src/ui/ErrorBlock.vue`、`src/ui/LoadingBlock.vue`
4. **导航与守卫**
   - 统一 `ensureLogin/ensureSystemContext` 使用方式
   - 页面 mounted 时只做一次 guard；需要时封装成 composable：`useSystemGuard()`

### Phase 2（C）：主题与视觉规范统一（在 Phase 1 后）

1. **主题变量**
   - 新增 `src/styles/theme.css`（或 `scss`）定义颜色、字号、间距、圆角、阴影
   - 页面与组件优先使用 class + 主题变量，而不是散落的内联 style
2. **组件视觉统一**
   - 统一卡片、列表项、表单项、按钮的边距与信息层级
   - 对 uni-ui 组件：尽量通过容器样式调整，不 fork 组件
3. **关键页 UX 统一**
   - 登录：错误提示、loading、成功跳转一致
   - 系统选择：空态/刷新/创建入口一致
   - Records 表单：字段必填提示、日期/字典选择交互一致
   - Flow：发起/待办/任务动作后的“下一步”路径一致
   - Upload：上传/下载/预览统一提示

## 迁移策略（避免一次性大爆炸）

- 按“最常用页面”优先逐页迁移到新骨架与新 API。
- 每迁移一组页面：
  - `pnpm -C mobile/uniapp run type-check`
  - `pnpm -C mobile/uniapp run build:h5`
  - `pnpm -C mobile/uniapp run build:mp-weixin`
  - 提交一个独立 commit（便于回滚）

## 风险与应对

- **风险：Pinia 引入导致页面改动较大**  
  应对：先只把 session/system 上下文收敛到 store，页面逐步迁移，不强行一次改完。
- **风险：多端样式差异**  
  应对：主题变量 + 少量平台差异判断；优先保证 H5 与 mp-weixin 构建与核心交互。

## 验证清单（完成后必须全部通过）

- `corepack pnpm -C mobile/uniapp run type-check`
- `corepack pnpm -C mobile/uniapp run build:h5`
- `corepack pnpm -C mobile/uniapp run build:mp-weixin`
- 手工走一遍：登录 → 系统 → Apps/Models/Fields → Records → Flow（发起/待办/处理）→ Upload

