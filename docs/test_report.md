# P7 测试报告

- 任务: TEST-006
- 执行时间: 2026-06-08
- 汇总输入: `docs/test_runs/e2e-main-chain.md`、`docs/test_runs/permission-exception-idempotency-openapi.md`、`docs/test_runs/frontend-backend-combo-e2e.md`
- 结论: pass
- target: none

## 结论说明

P7 已完成后端 jar、前端 dist 和浏览器前端到真实后端 API 的组合验证。前端不再只是 typed SDK/PageModel，已经具备真实浏览器入口、生产构建产物和组合 E2E 证据。

## 执行命令

| 命令 | 结果 |
| --- | --- |
| `mvn.cmd -pl examine-web -am clean package -DskipTests` | 通过；8 个 Maven 模块 SUCCESS |
| `java -jar backend/examine-web/target/unexamine.jar` | 通过；Tomcat started on port 9999 |
| `curl.exe -i http://127.0.0.1:9999/actuator/health` | 通过；返回 `COMMON_OK`、`UP` |
| `npm.cmd run build` | 通过；`tsc --noEmit && vite build` |
| `npm.cmd run preview -- --port 4173` | 通过；前端生产预览 HTTP 200 |
| Chrome headless 打开前端 smoke URL | 通过；页面回显 `AUTH-002` 登录后触发 `PLAT-001` 的 `COMMON_OK` 响应 |

## TEST-006 汇总

| 场景 | 结果 |
| --- | --- |
| 后端 jar 启动并连接数据库 | 通过 |
| 前端 dist 生产预览 | 通过 |
| 浏览器前端登录 | 通过；前端触发 `AUTH-002`，顶部用户显示 `E2E Browser User` |
| 浏览器前端触发后端 API | 通过；typed SDK 调用 `PLAT-001 /api/v1/platform/my-systems`，返回 `COMMON_OK` |
| CORS | 已修复；前端 `127.0.0.1:4173` 可访问后端 `127.0.0.1:9999` |
| 完整部署包 | 已生成 `dist/unexamine-full-deploy-20260608-154616.zip` |

## 失败与修复摘要

| 问题 | target | 状态 |
| --- | --- | --- |
| 前端生产预览访问后端返回 `Failed to fetch` | backend | 已修复，`WebMvcConfig` 增加 CORS 配置 |

## 未覆盖风险

| 风险 | 影响 | 建议处理 |
| --- | --- | --- |
| 创建系统幂等当前为单 JVM 内存态 | 多实例、重启和跨节点部署时不能保证生产级幂等一致性 | P2 上线前改为共享存储或数据库幂等表 |
| npm audit 提示 2 个 moderate 项 | 依赖安全治理仍需跟进 | 后续评估升级影响，避免直接 `--force` 破坏构建 |
| OpenAPI nonce replay、IP 白名单、OpenAPI 幂等冲突和高并发专项未完整自动化 | 安全和并发边界仍有专项覆盖空间 | 用户试部署后进入上线前 hardening |

## 测试结论

TEST-006 pass。当前项目具备用户试部署条件。

## 用户试部署回归

2026-06-08 复查 `http://192.168.0.211:19999/` 后发现旧包和 nginx 配置存在部署问题：页面仍是调试形态，且 nginx 将 `/api/v1/...` 转发为后端 `/v1/...`，接口文档 `/doc.html` 也被 SPA fallback 接管。当前已修复前端部署版默认同源调用规则，并补充 `docs/deploy/nginx-deploy.md`；用户重新部署新版包后，需要按该文档调整 nginx。

## P9 系统管理域 E2E

- 任务: TEST-007
- 执行时间: 2026-06-09
- 记录: `docs/test_runs/p9-system-management-ui-e2e-20260609.md`
- 结论: pass
- target: none

P9 已通过真实浏览器页面触发成员、部门、系统角色、字典写操作。覆盖登录进入真实系统、成员邀请/详情/编辑/分配角色/停用、部门创建/编辑/删除和删除限制、角色创建/权限目录/读取授权/保存授权/启停、字典类型和字典项创建/usage/启停/version 删除。
