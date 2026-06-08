# P6 集成验收与上线判断期修正记录

- 期次: P6-final-acceptance
- 验收时间: 2026-06-08
- 验收角色: pm
- 修正时间: 2026-06-08
- 原结论: pass
- 修正结论: fail
- target: pm/frontend
- 状态: blocked(frontend-ui)

## 修正原因

P6 原验收把 `frontend` 的 typed API SDK、PageModel、路由/状态模型和 `tsc --noEmit` 类型检查误判为“前端 clean build 可部署”。经复核，当前 `frontend/` 缺少真实浏览器 UI 工程入口和部署产物：

- 无 `index.html`
- 无 `src/main.*`
- 无 Vue/React 页面组件挂载
- 无 Vite/Webpack 等浏览器应用构建
- 无 `dist/` 可部署前端产物

因此，P6 不能代表“全项目可上线”，只能代表后端接口包和前端契约模型阶段通过。PM 验收口径、validator 构建口径和 reviewer 复审口径均存在失职，必须进入 P7 补真实前端 UI。

## 验收范围

| 范围 | 任务 | 结论 |
| --- | --- | --- |
| 后端最终自检 | BE-015 | pass |
| 前端契约闭环 | FE-012 | pass，仅代表 typed contract，不代表可部署 UI |
| 测试执行与报告 | TEST-003、TEST-004、TEST-005 | pass |
| 构建验证 | VAL-001、VAL-002、VAL-003、VAL-004 | fail，前端 UI 构建产物缺失 |
| 质量审查 | REV-001、REV-002、REV-003、REV-004 | fail，PM/validator/reviewer 曾误判前端完成 |

## 关键验收证据

1. 后端主链路 smoke 已覆盖注册、登录、创建系统、应用模块配置、发布、运行记录、提交和导出任务创建，记录见 `docs/test_runs/e2e-main-chain.md`。
2. OpenAPI 安全负向断言已覆盖缺失/未知 accessKey、timestamp、body hash、signature、scope 和 rate limit，记录见 `docs/test_runs/permission-exception-idempotency-openapi.md`。
3. `mvn -pl examine-app -am test` 通过，core 13、plat 12、upload 4、module 21、flow 2、app 11 个测试通过。
4. `mvn -pl examine-web -am clean compile` 通过，8 个 Maven 模块均 SUCCESS。
5. `npm.cmd ci; npm.cmd run build` 通过，但该命令仅执行 `tsc --noEmit`，只能证明前端契约模型类型检查通过，不能证明浏览器前端可部署。
6. 契约同步检查通过：174 个 API ID、20 个核心错误码、14 组状态枚举、19 个字段类型和 AUTH-004/AUTH-005 Bearer 标记均匹配。
7. 原 `docs/review.json` pass 结论已撤回，修正为 fail，target=both。

## 阻塞项

| 风险 | 当前结论 | 后续建议 |
| --- | --- | --- |
| 缺少可部署前端 UI | 阻塞完整项目上线 | 启动 P7，补真实浏览器前端工程、页面组件、构建脚本、`dist/` 和浏览器 smoke/E2E |
| PM/validator/reviewer 验收口径错误 | 阻塞完整项目上线判断 | 已补 AGENTS 规则和编码规则，后续验收必须区分 typed contract、browser app、deployable frontend |
| 创建系统幂等仍为单 JVM 内存态 | 不阻塞当前 MVP 验收 | 上线多节点前迁移到共享存储或数据库幂等表 |
| OpenAPI nonce replay、IP 白名单、OpenAPI 幂等冲突和高并发专项未全量自动化 | 不阻塞当前 MVP 验收 | 作为上线前增强测试或压测专项补齐 |

## 修正后 PM 结论

P6 原“全项目通过验收”结论撤回。当前代码允许打包后端 jar 进行接口试部署，但完整项目不可判定为可上线。下一步必须进入 `P7-frontend-ui-deploy`，完成真实前端 UI 和 `dist/` 产物后，才能重新执行 test、validator 和 reviewer。
