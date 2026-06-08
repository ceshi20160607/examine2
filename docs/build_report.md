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
| P8 权限修复 | 进入系统改为调用 `SYS-001` 获取真实系统、租户、成员和权限；`SYS_MANAGE_ALL` 映射为系统内管理权限 |
| 系统上下文请求头修复 | 系统内 API 已随 typed SDK 发送 `X-System-Id` 与 `X-Member-Id` |
| 系统资料页面 | 已从通用占位页升级为真实资料页，可加载 `SYS-002` 并保存 `SYS-003` |
| 租户页面 | 已从通用占位页升级为真实租户页，可加载 `SYS-004`，并保留创建/启停入口 |
| 最新部署包 | `dist/unexamine-full-deploy-20260608-235808.zip` |

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
| 本次真实流程 | 本地前端 `http://127.0.0.1:5173/?baseUrl=http%3A%2F%2F192.168.0.211%3A19999#/auth/login` 登录 `platform_admin` 成功，进入系统 `2063994726481473538` 成功 |
| 系统资料 API | 浏览器点击刷新触发 `GET http://192.168.0.211:19999/api/v1/systems/2063994726481473538/profile`，返回 200 |
| 系统资料保存 | 浏览器点击保存资料触发 `PUT http://192.168.0.211:19999/api/v1/systems/2063994726481473538/profile`，body 为 `{"name":"车"}`，返回 200 |
| 租户 API | 浏览器点击刷新触发 `GET http://192.168.0.211:19999/api/v1/systems/2063994726481473538/tenants`，返回 200 |

## 前端产物

| 文件 | 大小 |
| --- | --- |
| `frontend/dist/index.html` | 445 B |
| `frontend/dist/assets/index-D_A850DX.js` | 78000 B |
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
