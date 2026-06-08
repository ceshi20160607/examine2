# FE-013 前端 UI Smoke 记录

- 任务: FE-013
- 期次: P7-frontend-ui-deploy
- 执行时间: 2026-06-08
- 结论: pass

## 产物

| 项目 | 结果 |
| --- | --- |
| `frontend/index.html` | 已生成真实浏览器入口 |
| `frontend/src/main.ts` | 已挂载根应用 |
| `frontend/src/App.ts` | 已实现等价根组件、路由渲染、Shell 导航、连接面板和页面工作区 |
| `frontend/src/styles.css` | 已实现桌面和移动端布局 |
| `frontend/vite.config.ts` | 已配置 Vite dev/preview 端口 |
| `frontend/dist/` | clean build 已生成 |

## 验证命令

```powershell
cd frontend
npm.cmd ci
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
npm.cmd run build
npm.cmd run preview -- --port 4173
```

## 命令结果

- `npm.cmd ci`: pass，提示 2 个 moderate npm audit 项，未执行 `npm audit fix --force`，避免强制升级破坏构建。
- `npm.cmd run build`: pass，执行 `tsc --noEmit && vite build`。
- `curl.exe -I --max-time 5 http://127.0.0.1:4173/`: HTTP 200。

## 生产构建产物

| 文件 | 大小 |
| --- | --- |
| `frontend/dist/index.html` | 445 B |
| `frontend/dist/assets/index-BC-aPLAX.js` | 57299 B |
| `frontend/dist/assets/index-D2PQaVmV.css` | 5662 B |

## 浏览器截图

- 桌面截图: `frontend/docs/frontend-ui-smoke.png`
- 移动端截图: `frontend/docs/frontend-ui-smoke-mobile.png`

## 覆盖结论

1. 真实浏览器入口、主应用挂载、hash 路由和可部署 `dist/` 已补齐。
2. 登录、我的系统、平台中心、系统管理、RBAC、应用配置、运行台、流程、文件导出、OpenAPI、审计运维均可通过左侧导航进入页面工作区。
3. 页面通过集中式 typed SDK 和 `createFetchTransport` 调用后端；业务页面未散落 `fetch`、`axios`、`XMLHttpRequest` 或手写 URL。
4. 生产预览可打开，桌面和移动端截图未见空白页或明显文本重叠。
