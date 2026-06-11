# 2026-06-11 P13 Clean Build 摘要

结论：VAL-009 通过，P13 前端 clean build、后端 clean package 和浏览器可用性主链路均已通过；REV-009 通过前仍不生成 PKG-002 最终包。

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass，生成 `frontend/dist/` |
| 后端打包 | `mvn.cmd -pl examine-web -am clean package -DskipTests` pass，生成 `backend/examine-web/target/unexamine.jar` |
| 浏览器 E2E | TEST-011 pass，记录见 `docs/test_runs/p13-usability-e2e.md` |
| 修复重点 | 创建系统进入总览、SYS-001 成员/权限上下文写入、系统内导航收敛、生产 smoke 参数禁用、成功提示业务化 |
| 阶段状态 | 待 REV-009 审查和 PKG-002 打包 |

# P8 平台中心 UI 构建验证报告

# 2026-06-10 P11 Clean Build 摘要

结论：P11 前端 build、后端 compile/package 和浏览器 E2E 均已通过，P11 accepted。

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass，生成 `frontend/dist/` |
| 后端编译 | `mvn -pl examine-web -am -DskipTests compile` pass |
| 后端打包 | `mvn -pl examine-web -am -DskipTests package` pass，生成 `backend/examine-web/target/unexamine.jar` |
| 后端启动脚本 | `backend/deploy/start.sh` 已加入部署包，支持 `start`、`stop`、`sotp`、`restart`、`status` |
| 完整部署包 | `dist/unexamine-full-deploy-20260610-174753.zip`，包内包含 `backend/start.sh` |
| 浏览器 E2E | TEST-009 pass，记录见 `docs/test_runs/p11-flow-file-openapi-ui-e2e-20260610.md` |
| 阶段验收 | `docs/phases/P11-flow-file-openapi-ui-acceptance.md` |

P11 修复了流程模板契约、文件上传 FormData、跨系统模块状态污染、导出模块发布态校验、导出任务空筛选快照 500、OpenAPI 客户端保存结构等问题。当前系统已具备用户试部署条件。

## 2026-06-10 P12 UI/UX 纠偏说明

P11 部署包保留为功能试部署包。用户反馈前端缺少独立 UI/UX 设计，PM 已确认该问题成立并启动 P12：先冻结 `docs/ui/ui-design.md`，再由 frontend 按设计执行 FE-023/FE-024。P12 通过前，不再把 P11 build/package 结论表述为最终用户体验完成。

### FE-023 验证

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass |
| 生产预览 | `/` 与 `/#/systems/demo/overview` HTTP 200 |
| 说明 | 浏览器插件工具本轮未暴露，截图级 UI smoke 留给 TEST-010 |

### FE-024 验证

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass |
| 生产预览 | `/` 与 `/#/systems/demo/overview` HTTP 200 |
| prompt 扫描 | `rg -n "window\\.prompt|prompt\\(" frontend\\src\\App.ts` 无结果 |
| reviewer 预审返工 | 显式进入按钮、页面内编辑授权、权限多选和有状态 Tabs 已补齐 |
| 说明 | 本次只完成 frontend 自检；TEST-010、VAL-008、REV-008 未通过前仍禁止打包 |

# 2026-06-09 P10 Clean Build 摘要

结论：P10 前端 build、后端 package 和浏览器写操作 E2E 均已通过，P10 accepted。

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass，生成 `frontend/dist/` |
| 后端模块编译 | `mvn.cmd -pl examine-module -am -DskipTests compile` pass |
| 后端打包 | `mvn.cmd -pl examine-web -am -DskipTests package` pass，生成 `backend/examine-web/target/unexamine.jar` |
| 浏览器 E2E | TEST-008 pass，记录见 `docs/test_runs/p10-app-runtime-ui-e2e-20260609.md` |
| P10 部署包 | `dist/unexamine-full-deploy-20260609-162432.zip` |
| 阶段验收 | `docs/phases/P10-app-runtime-ui-acceptance.md` |

P10 修复了运行态 schema 字段兼容、深链路系统上下文、RUN-003 body、RUN-006/RUN-008 幂等 key 和记录标题兜底。完整项目仍需 P11/P12 后续 UI 期次。

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
| 最新部署包 | `dist/unexamine-full-deploy-20260609-004321.zip` |

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
| 本地全流程试跑 | 2026-06-09 本地前后端启动并执行浏览器页面全流程；发现 `SYS-006` 启用已停用租户 403，修复后页面回归 200 |

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

# 2026-06-09 P9 前端 clean build 摘要

结论：P9 前端 clean build、后端 package 和浏览器写操作 E2E 均已通过，P9 accepted。

| 项目 | 结果 |
| --- | --- |
| 任务 | FE-015 系统管理域真实 UI |
| 首次 clean build | fail，5173 dev server 占用 esbuild 可执行文件导致 `npm ci` 无法删除依赖 |
| 环境处理 | 已停止当前 workspace 的 vite/node dev 进程后重试 |
| `npm.cmd ci` | pass，保留 2 个 moderate audit 风险 |
| `npm.cmd run build` | pass，`tsc --noEmit && vite build` 成功，最新产物 `frontend/dist/assets/index-B7TCDdhw.js` |
| `mvn -pl examine-web -am -DskipTests package` | pass，停止旧 jar 进程后重新生成 `backend/examine-web/target/unexamine.jar` |
| 浏览器 UI smoke | pass，已生成成员、部门、系统角色、字典四张截图 |
| 浏览器写操作 E2E | pass，记录见 `docs/test_runs/p9-system-management-ui-e2e-20260609.md` |
# 2026-06-09 本机全系统验证构建摘要

结论：后端构建与全系统接口链路通过，记录见 `docs/test_runs/local-full-project-api-flow-20260609.md`。

| 项目 | 结果 |
| --- | --- |
| `mvn.cmd -pl examine-web -am test` | pass，8 个 Maven 模块共 68 个测试通过 |
| `mvn.cmd -pl examine-web -am clean package -DskipTests` | pass，生成 `backend/examine-web/target/unexamine.jar` |
| 本机后端 | pass，`http://127.0.0.1:9999` 已启动 |
| 本机前端 | pass，`http://127.0.0.1:5173` 已监听 |
| 全系统接口链路 | pass，AUTH、PLATFORM、SYSTEM、MEMBER/RBAC、DICT、MODULE/RUNTIME、FLOW、FILE、OPENAPI、AUDIT/OPS 均通过 |
| PM 注意事项 | 前端完整业务 UI 仍需按模块继续补齐，不能用本次后端接口链路 pass 代替完整前端验收 |

---

# 2026-06-11 P12 Clean Build 摘要

结论：VAL-008 通过，P12 前端 clean build 和后端 clean package 均已通过，未生成最终部署包。

| 项目 | 结果 |
| --- | --- |
| 前端 clean build | `npm.cmd run build` pass，重新生成 `frontend/dist/` |
| 前端产物 | `frontend/dist/index.html`、`frontend/dist/assets/index-DQmshVJC.css`、`frontend/dist/assets/index-HECxZvby.js` |
| 后端 clean package | `mvn.cmd -pl examine-web -am clean package -DskipTests` pass |
| 后端产物 | `backend/examine-web/target/unexamine.jar` |
| 打包闸门 | 未生成 `dist/unexamine-full-deploy-*.zip`；REV-008 未通过前 PKG-001 继续阻塞 |
| 详细报告 | `docs/build/p12-clean-build.md` |

---

# 2026-06-11 P12 最终部署包

结论：PKG-001 通过，最终部署包已生成并完成包清单核验。

| 项目 | 结果 |
| --- | --- |
| 包目录 | `dist/unexamine-full-deploy-20260611-100441-fixed/` |
| zip 包 | `dist/unexamine-full-deploy-20260611-100441-fixed.zip` |
| Linux 推荐包 | `dist/unexamine-full-deploy-20260611-100441-fixed.tar.gz` |
| zip 大小 | 39,997,421 B |
| tar.gz 大小 | 39,993,789 B |
| 前端 | `frontend/index.html`、`frontend/assets/index-DQmshVJC.css`、`frontend/assets/index-HECxZvby.js` |
| 后端 | `backend/unexamine.jar`、`backend/start.sh`，`start.sh` 已按 Unix `755` 权限打包 |
| 部署说明 | `docs/nginx-deploy.md` |
| P12 证据 | `docs/p12-clean-build.md`、`docs/p12-ui-usable-e2e.md`、`docs/p12_reviewer_verification.md`、`docs/review.json` |

---
