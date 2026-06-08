# TEST-006 前后端组合 E2E 记录

- 任务: TEST-006
- 期次: P7-frontend-ui-deploy
- 执行时间: 2026-06-08
- 结论: pass

## 环境

| 项目 | 值 |
| --- | --- |
| 后端 jar | `backend/examine-web/target/unexamine.jar` |
| 后端端口 | `9999` |
| 数据库 | `192.168.0.211:3306/examine1` |
| 前端产物 | `frontend/dist/` |
| 前端生产预览 | `http://127.0.0.1:4173/` |
| 完整部署包 | `dist/unexamine-full-deploy-20260608-154616.zip` |

## 执行记录

| 步骤 | 命令或动作 | 结果 |
| --- | --- | --- |
| 后端 CORS 修复 | `WebMvcConfig.addCorsMappings` 放行本机和局域网前端预览地址 | pass |
| 后端重新打包 | `mvn.cmd -pl examine-web -am clean package -DskipTests` | pass，8 个 Maven 模块 SUCCESS |
| 后端启动 | `java -jar backend/examine-web/target/unexamine.jar` | pass，Tomcat started on port 9999 |
| 健康检查 | `curl.exe -i http://127.0.0.1:9999/actuator/health` | pass，返回 `COMMON_OK` 和 `UP` |
| E2E 账号注册 | `POST /api/v1/auth/register` | pass，创建 `e2e_141648` |
| 前端生产构建 | `npm.cmd run build` | pass，生成 `frontend/dist/` |
| 前端生产预览 | `npm.cmd run preview -- --port 4173` | pass，HTTP 200 |
| 浏览器自动登录 | 前端 URL 参数 `smokeLoginName=e2e_141648&smokePassword=***` 触发 `AUTH-002` | pass，顶部用户显示 `E2E Browser User` |
| 浏览器触发真实 API | 前端 URL 参数 `smokeApi=PLAT-001` 触发 typed SDK 调用 `/api/v1/platform/my-systems` | pass，页面回显 `success=true`、`COMMON_OK` |
| 部署语义复核 | 构建产物扫描 `frontend/dist/` | pass，默认接口为同源 `/api/v1/...`，未发现 `localhost:8080` 或用户可见 API 地址面板 |

## 截图

- 组合 E2E 截图: `frontend/docs/frontend-backend-combo-smoke.png`
- 桌面 UI smoke: `frontend/docs/frontend-ui-smoke.png`
- 移动端 UI smoke: `frontend/docs/frontend-ui-smoke-mobile.png`

## 结论

后端 jar、前端 dist 和浏览器前端到真实后端 API 的组合链路已跑通。当前可进入用户试部署与业务验收。

正式部署时不使用 URL 参数配置 API 地址；上述 `smokeLoginName`、`smokePassword`、`smokeApi` 仅用于自动化 smoke。nginx 必须按 `docs/deploy/nginx-deploy.md` 保留 `/api` 前缀转发。
