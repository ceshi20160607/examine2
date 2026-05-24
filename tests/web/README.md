# Web UI 测试（Playwright）

针对 `web/vue3` 管理台，通过 Vite 开发服（默认 `http://localhost:5173`）访问，API 由代理转发到 `9999`。

## 安装（首次）

```powershell
cd tests\web
npm install
npx playwright install chromium
```

## 运行

先启动后端 + 前端（或依赖 `reuseExistingServer` 自动起前端）：

```powershell
$env:SMOKE_USER = 'admin'
$env:SMOKE_PASS = '123123aa'
npm run test:e2e
```

调试：`npm run test:e2e:ui` 或 `npm run test:e2e:headed`。

## 环境变量

| 变量 | 默认 |
|------|------|
| `WEB_BASE_URL` | `http://localhost:5173` |
| `SMOKE_USER` / `SMOKE_PASS` | `admin` / `123123aa` |

## 用例

- `e2e/auth.spec.ts` — 登录页、登录跳转
- `e2e/systems.spec.ts` — 创建系统并进入（需 `SYSTEM_CREATE` 权限）

稳定选择器：`data-testid`（见 `LoginView.vue`、`SystemsView.vue`）。
