# P9 Frontend Clean Build

## 命令

```powershell
cd frontend
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
npm.cmd ci
npm.cmd run build
```

## 结果

| 项目 | 结果 |
| --- | --- |
| 首次 clean build | fail，5173 dev server 占用 `node_modules/@esbuild/win32-x64/esbuild.exe`，`npm ci` 无法 unlink |
| 环境修复 | 已停止当前 workspace 的 vite/node dev 进程 |
| `npm.cmd ci` | pass，提示 2 个 moderate audit，未执行 `npm audit fix --force` |
| `npm.cmd run build` | pass |
| `frontend/dist/index.html` | 445 B |
| `frontend/dist/assets/index-Bavadxer.js` | 98363 B |
| `frontend/dist/assets/index-ChSrJL4U.css` | 7765 B |

## 结论

P9 前端 clean build 通过，产物已重新生成。浏览器 E2E 仍需 `TEST-007` 单独执行，不能仅凭 build 判定 P9 accepted。
