# P7 前端真实 UI 与部署包期验收

- 期次: P7-frontend-ui-deploy
- 验收时间: 2026-06-08
- PM 结论: pass
- 是否允许进入试部署: 是

## 验收范围

| 项目 | 结果 |
| --- | --- |
| FE-013 前端真实 UI | pass |
| TEST-006 前后端组合 E2E | pass |
| 前端 clean build | pass |
| 后端 CORS 修复与重新打包 | pass |
| 完整部署包 | `dist/unexamine-full-deploy-20260608-154616.zip` |

## 验收依据

- `frontend/docs/frontend-ui-smoke.md`
- `docs/test_runs/frontend-backend-combo-e2e.md`
- `docs/build_report.md`
- `docs/review.json`
- `docs/deploy/nginx-deploy.md`

## 剩余风险

1. 创建系统幂等当前仍为单 JVM 内存态，多实例生产部署前建议改为共享存储或数据库幂等表。
2. npm audit 提示 2 个 moderate 项，未执行强制升级，建议后续做依赖治理。
3. OpenAPI nonce replay、IP 白名单、并发压测仍属于上线前增强专项。

## PM 结论

P7 通过。当前项目具备后端 jar、前端 dist 和组合 E2E 证据，可交给用户试部署与体验验收。旧包 `dist/unexamine-full-deploy-20260608-141816.zip` 已被新版 `dist/unexamine-full-deploy-20260608-154616.zip` 取代，部署时必须按 nginx 文档保留 `/api` 前缀并代理接口文档路径。
