# P14 完整系统 UI 重设计与原型流程纠偏期验收

验收时间：2026-06-11 21:45

验收结论：accepted。

## 验收范围

- 产品运行模型：`docs/product/product-vision-and-operating-model.md`
- 完整系统基线：`docs/product/integrated-system-baseline.md`
- 集成 UI/UX：`docs/ui/p14-integrated-ui.md`
- P14 任务计划：`docs/tasks/P14-integrated-rework-plan.md`
- 前端实现：平台工作空间、系统工作空间、业务运行台、平台级对外应用、日志分层
- 后端补缺：运行菜单同步 RBAC、字段权限解析、配置替换幂等、OpenAPI client 权限快照、OpenAPI 日志 total
- 测试验证：剧本 A/B/C、普通业务用户隔离、OpenAPI 调用和 requestId 日志追踪
- 打包交付：前端 dist、后端 jar、`start.sh`、nginx 文档和 P14 证据

## 关键证据

| 类型 | 记录 |
| --- | --- |
| API E2E | `docs/test_runs/p14-integrated-api-e2e-20260611.md` |
| 浏览器 smoke | `docs/test_runs/p14-frontend-smoke-20260611.md` |
| 普通用户与 OpenAPI 补证 | `docs/test_runs/p14-ordinary-user-openapi-e2e-20260611.md` |
| clean build | `docs/build/p14-clean-build.md` |
| reviewer 复审 | `docs/review.json`、`docs/issues/verification/development/p14_reviewer_verification.md` |

## 部署包

| 项目 | 路径 |
| --- | --- |
| Windows/通用部署包 | `dist/unexamine-full-deploy-20260611-213200-p14.zip` |
| Linux 部署包 | `dist/unexamine-full-deploy-20260611-213200-p14.tar.gz` |

包清单已核验，不包含 `.codex/tmp/`。`tar.gz` 内 `backend/start.sh` 权限为 `rwxr-xr-x`，zip 元数据为 `100755`。

## PM 结论

P14 已满足“普通人可以登录、建系统、建模块数据、发布、普通成员使用运行台、平台级对外应用授权系统数据并被外部调用、日志可追踪”的完整系统验收口径。后续新增优化或用户部署反馈应作为新期次进入 PM/Planner 拆分，不再覆盖 P14 验收结论。
