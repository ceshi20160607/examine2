# FE-013 前端真实 UI 与可部署产物

- 任务 ID: FE-013
- 所属期次: P7-frontend-ui-deploy
- 负责角色: frontend
- 状态: pending
- 优先级: P0

## 目标

基于已有 `frontend/src/api/`、`frontend/src/pages/*PageModel.ts`、`frontend/src/router/` 和 `frontend/src/stores/`，补齐真实浏览器前端工程，生成可部署 `dist/`。

## 输入文件

- `docs/api.md`
- `frontend/src/api/`
- `frontend/src/pages/`
- `frontend/src/router/`
- `frontend/src/stores/`
- `frontend/docs/api-contract-map.md`
- `docs/development/encoding.md`

## 输出文件或目录

- `frontend/index.html`
- `frontend/src/main.ts`
- `frontend/src/App.vue` 或等价根组件
- `frontend/src/pages/**/*.vue` 或等价真实页面组件
- `frontend/vite.config.*` 或等价构建配置
- `frontend/dist/`
- `frontend/docs/frontend-ui-smoke.md`

## 完成标准

1. 前端具备真实浏览器应用入口，能本地启动并挂载路由。
2. 登录、我的系统、平台中心、系统配置、模块配置、运行台、流程、文件导出、OpenAPI/审计/运维至少具备可点击页面骨架和主要数据状态。
3. 页面通过 typed SDK 调用后端，不散落 `fetch`、`axios` 硬编码 URL。
4. clean build 生成 `frontend/dist/`。
5. 浏览器 smoke/E2E 覆盖登录、进入系统、查看模块配置/运行台、触发一个后端 API、错误态和权限禁用态。
6. `docs/build_report.md` 必须记录真实前端 build 命令和 `dist/` 产物，不得只用 `tsc --noEmit` 判定通过。

## 验证命令

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
```

如引入 Vite/Vue，必须补充对应 dev/build 脚本，并在 `frontend/docs/frontend-ui-smoke.md` 记录浏览器验证 URL、截图或关键断言。
