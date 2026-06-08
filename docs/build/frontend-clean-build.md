# VAL-P7-FE-013 前端真实 UI Clean Build 记录

- 任务: FE-013
- 期次: P7-frontend-ui-deploy
- 执行时间: 2026-06-08
- 执行角色: validator
- 结论: pass
- target: none

## 当前前端文件状态

| 检查项 | 结果 |
| --- | --- |
| `frontend/package.json` | 存在，包含 `dev/build/preview/typecheck` |
| `frontend/tsconfig.json` | 存在 |
| `frontend/package-lock.json` | 存在，可执行 `npm.cmd ci` |
| `frontend/index.html` | 存在 |
| `frontend/src/main.ts` | 存在 |
| `frontend/src/App.ts` | 存在，作为等价根组件 |
| `frontend/src/styles.css` | 存在 |
| `frontend/vite.config.ts` | 存在 |
| `frontend/dist/` | clean build 已生成 |

## 命令结果

```powershell
cd frontend
npm.cmd ci
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
npm.cmd run build
```

结果：

- `npm.cmd ci`: pass。
- `npm.cmd run build`: pass，执行 `tsc --noEmit && vite build`。
- Vite 生产构建输出 `frontend/dist/index.html`、`frontend/dist/assets/index-BC-aPLAX.js`、`frontend/dist/assets/index-D2PQaVmV.css`。

## Browser Smoke

```powershell
npm.cmd run preview -- --port 4173
curl.exe -I --max-time 5 http://127.0.0.1:4173/
```

结果：

- 生产预览 HTTP 200。
- Chrome headless 桌面截图: `frontend/docs/frontend-ui-smoke.png`。
- Chrome headless 移动端截图: `frontend/docs/frontend-ui-smoke-mobile.png`。

## 说明

`npm.cmd ci` 提示 2 个 moderate npm audit 项，暂未执行 `npm audit fix --force`，避免强制升级破坏当前 Vite 5 构建。该风险记录为依赖治理后续项，不阻塞当前 P7 可部署前端产物。
