# P8 平台中心 UI 构建验证报告

- 任务: VAL-P8-FE-014
- 执行时间: 2026-06-08
- 执行角色: validator
- 结论: pass
- target: none

## P8 前端验证

| 项目 | 结果 |
| --- | --- |
| clean build 命令 | `npm.cmd run build` |
| build 脚本 | `tsc --noEmit && vite build` |
| `frontend/dist/` | 已重新生成 |
| 平台中心 UI | 平台系统、平台账号、平台角色、平台配置已升级为真实业务表单和数据表格 |
| 浏览器 smoke | `#/auth/login` 和 `#/platform/systems` 页面结构正常，中文无乱码 |
| 最新部署包 | `dist/unexamine-full-deploy-20260608-171500.zip` |

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
| `frontend/dist/assets/index-Dlu7mWZI.js` | 74486 B |
| `frontend/dist/assets/index-CVxrMMA5.css` | 7444 B |

## 组合验证

| 项目 | 结果 |
| --- | --- |
| 前端生产预览 | `http://127.0.0.1:4173/` HTTP 200 |
| 浏览器登录 | 前端触发 `AUTH-002` 成功，用户显示 `E2E Browser User` |
| 浏览器触发后端 API | 前端 typed SDK 调用 `PLAT-001 /api/v1/platform/my-systems`，返回 `COMMON_OK` |
| 截图 | `frontend/docs/frontend-backend-combo-smoke.png` |
| 完整部署包 | `dist/unexamine-full-deploy-20260608-164002.zip` |

## Validator 结论

P8 已在 P7 可部署前端基础上补齐平台中心核心 CRUD UI。部署版前端默认走 nginx 同源 `/api/v1/...`，平台中心按钮和接口调用继续通过 typed PageModel 与权限动作控制。当前结论为 pass。
