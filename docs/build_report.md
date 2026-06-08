# P7 前后端组合构建验证报告

- 任务: VAL-P7-FE-013
- 执行时间: 2026-06-08
- 执行角色: validator
- 结论: pass
- target: none

## 后端验证

| 项目 | 结果 |
| --- | --- |
| 命令 | `mvn.cmd -pl examine-web -am clean package -DskipTests` |
| 结果 | pass，8 个 Maven 模块 SUCCESS |
| CORS | `backend/examine-web/src/main/java/com/unique/examine/web/config/WebMvcConfig.java` 已支持前后端分离预览 |
| 产物 | `backend/examine-web/target/unexamine.jar` |
| 健康检查 | `http://127.0.0.1:9999/actuator/health` 返回 `COMMON_OK`、`UP` |

## 前端验证

| 项目 | 结果 |
| --- | --- |
| `npm.cmd ci` | pass |
| clean build 命令 | `npm.cmd run build` |
| build 脚本 | `tsc --noEmit && vite build` |
| `frontend/index.html` | 存在 |
| `frontend/src/main.ts` | 存在 |
| 根组件 | `frontend/src/App.ts` |
| 真实页面/工作区 | 已覆盖登录、我的系统、平台中心、系统配置、模块配置、运行台、流程、文件导出、OpenAPI、审计运维导航和工作区 |
| `frontend/dist/` | 已生成 |
| 生产预览 | `http://127.0.0.1:4173/` HTTP 200 |
| 浏览器 smoke | 桌面与移动端 Chrome headless 截图通过 |

## 前端产物

| 文件 | 大小 |
| --- | --- |
| `frontend/dist/index.html` | 445 B |
| `frontend/dist/assets/index-C8i4nSPj.js` | 56381 B |
| `frontend/dist/assets/index-B9Ede97w.css` | 6278 B |

## 组合验证

| 项目 | 结果 |
| --- | --- |
| 前端生产预览 | `http://127.0.0.1:4173/` HTTP 200 |
| 浏览器登录 | 前端触发 `AUTH-002` 成功，用户显示 `E2E Browser User` |
| 浏览器触发后端 API | 前端 typed SDK 调用 `PLAT-001 /api/v1/platform/my-systems`，返回 `COMMON_OK` |
| 截图 | `frontend/docs/frontend-backend-combo-smoke.png` |
| 完整部署包 | `dist/unexamine-full-deploy-20260608-154616.zip` |

## Validator 结论

P7 已补齐真实浏览器前端入口、应用挂载、导航工作区、生产构建、后端 CORS 支持、前后端组合 E2E 和完整部署包。部署版前端默认走 nginx 同源 `/api/v1/...`，不再暴露 API 地址配置面板；当前结论为 pass。
