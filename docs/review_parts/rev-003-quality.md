# REV-003 质量测试构建审查

- 任务: REV-003
- 执行时间: 2026-06-08
- 负责角色: reviewer
- 结论: fail
- target: both

## Findings

### P1 前端构建和浏览器 E2E 未执行，当前不能作为上线质量通过

- file: `docs/build_report.md:60`
- evidence: VAL-002 记录 `frontend/package.json`、`frontend/tsconfig.json` 缺失，`npm.cmd run build` 失败；TEST-005 记录前端浏览器刷新、页面联动、按钮禁用态未执行。
- impact: 质量报告无法证明前端真实渲染、路由、刷新、权限禁用、错误态和 typed SDK 调用在浏览器环境可用。
- target: frontend
- recommendation: 补齐前端工程入口并执行 clean build/typecheck/browser E2E 后，再重跑 TEST-003 页面部分、VAL-002 和 REV-003。

### P1 OpenAPI accessKey 负向用例断言过宽，放过了契约错误码不一致

- file: `docs/test_report.md:43`
- evidence: TEST-004 将 OpenAPI 缺少 AK 返回 401 `COMMON_UNAUTHORIZED` 记录为通过；但 `docs/api.md` 冻结要求 accessKey 缺失或非法返回 `OPENAPI_ACCESS_KEY_INVALID`。
- impact: 测试只检查“不是 500 且有 requestId”，没有按冻结 API 断言模块化错误码，导致 REV-002 发现的 backend 契约问题没有被测试阶段提前拦住。
- target: test
- recommendation: TEST-004 增加 OpenAPI 安全负向断言矩阵，至少覆盖缺 AK、非法 AK、停用客户端、签名错误、timestamp 超窗、nonce 重放、body hash 不一致、scope 越权和限流，并断言冻结错误码。

### P2 OpenAPI 和并发场景只完成 smoke，未达到上线高风险覆盖

- file: `docs/test_report.md:63`
- evidence: TEST-005 明确 OpenAPI timestamp/nonce/body hash/scope/IP/限流未做全矩阵，并发场景未覆盖自动编号、流程任务并发处理、导出任务并发领取。
- impact: 后端 clean compile 和主链路 smoke 通过不等于高风险边界通过；上线后 OpenAPI 安全边界和并发一致性仍有回归风险。
- target: test
- recommendation: 在修复契约问题后补充接口自动化或专项脚本，至少覆盖 OpenAPI 安全矩阵和三个并发敏感点。

### P2 创建系统幂等 smoke 通过但生产级一致性未覆盖

- file: `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/service/impl/PlatformCenterServiceImpl.java:83`
- evidence: 当前测试只验证单进程同一幂等键不同请求体返回 409；REV-001 已记录实现是本机静态 `ConcurrentHashMap`。
- impact: 多实例、重启、事务提交失败和缓存过期场景未被测试覆盖，不能证明生产级幂等能力。
- target: backend
- recommendation: 若 P6 要按生产上线标准验收，应把创建系统幂等迁移到共享存储或数据库后，再增加跨进程/重启/过期测试。

## Pass Items

| 检查项 | 结论 |
| --- | --- |
| 后端单元测试 | `mvn -pl examine-web -am test` 已通过，core 13、plat 12、upload 4、module 21、flow 2、app 4、web 4。 |
| 后端 clean compile | VAL-001 已执行 clean compile，8 个模块 SUCCESS。 |
| 后端主链路 HTTP smoke | TEST-003 已跑通注册、登录、系统、应用、模块、字段、发布、运行记录提交和导出任务创建。 |
| 空白检查 | `git diff --check` 通过，仅有 LF/CRLF 工作区转换 warning。 |
| 前端源码产物扫描 | VAL-002 未发现 `.vue.js`、临时 `.d.ts` 或编译 `.js` 混入 `frontend/src`。 |

## Review Conclusion

质量测试构建审查不通过，target=both。前端构建/E2E 是上线阻塞；后端 OpenAPI/幂等问题需要配合测试用例加强，避免修复后再次被过宽断言放过。
