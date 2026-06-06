# test agent 任务拆分审查

## 审查结论

fail

任务拆分已经覆盖 TEST-001 至 TEST-005、VAL-001 至 VAL-004、REV-001 至 REV-004，测试设计、执行依赖、validator 和 reviewer 主体能力基本齐全。但 TEST 与 VAL 阶段存在并行任务输出路径重叠，违反“可并行任务输出路径不重叠”的冻结规则，会影响后续 `docs/test_report.md`、`docs/build_report.md` 的确定性生成，因此 test 角度暂不允许任务计划冻结。

## 审查范围

- `docs/task_plan.md`
- `docs/tasks/`
- `docs/api.md`
- `docs/api_review.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取旧项目目录，未运行测试，未修改任务计划、任务清单、后端、前端或 SQL 产物。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞任务冻结 |
| --- | --- | --- | --- | --- | --- |
| TTASK-001 | `TEST-003` 与 `TEST-004` 被声明可并行执行，但两个任务文件的输出均写为 `docs/test_report.md`；同时 `TEST-005` 也负责汇总输出 `docs/test_report.md`。 | 并行执行时可能互相覆盖测试报告，`TEST-005` 无法稳定汇总 E2E 主链路和权限/异常/幂等/OpenAPI 风险场景结果。 | planner/test | 将 `TEST-003`、`TEST-004` 输出改为互不重叠的测试执行记录，例如 `docs/test-results/e2e-main-chain.md`、`docs/test-results/risk-scenarios.md`，并保留只有 `TEST-005` 输出 `docs/test_report.md`；或取消二者并行。同步更新 `docs/task_plan.md` 总矩阵、依赖图和两个任务文件。 | 是 |
| TTASK-002 | `VAL-001`、`VAL-002`、`VAL-003` 被声明可并行执行，但三个任务文件的输出均包含 `docs/build_report.md`，且 `VAL-004` 也负责最终输出 `docs/build_report.md`。 | 并行验证时后端 clean compile、前端 clean build、契约同步检查结果可能互相覆盖，最终构建报告缺失命令、环境、失败摘要或 validator 结论。 | planner/validator | 将 `VAL-001`、`VAL-002`、`VAL-003` 输出改为独立构建记录，例如 `docs/build/backend-clean-compile.md`、`docs/build/frontend-clean-build.md`、`docs/build/contract-sync-check.md`，并保留只有 `VAL-004` 输出 `docs/build_report.md`；或取消三者并行。同步更新 `docs/task_plan.md` 总矩阵、并行批次和三个任务文件。 | 是 |

## 非阻塞跟踪项

| 跟踪项 | 说明 | 建议 |
| --- | --- | --- |
| TEST-001/TEST-002 设计顺序 | `TEST-002` 依赖 `TEST-001`，但并行批次中写为 `TEST-001、TEST-002 可先设计`。任务文件依赖关系是清楚的，因此不阻塞。 | 后续修订时可明确为“TEST-001 完成后，TEST-002 可与 DBA/后端/前端准备并行”。 |
| REV-001 至 REV-003 中间产物 | `REV-001`、`REV-002`、`REV-003` 输出写为 review 记录、`docs/review.json`，最终 `REV-004` 才要求合法 `docs/review.json`。由于 reviewer 任务串行，不构成并行覆盖，但中间阶段是否写最终 JSON 容易误解。 | 建议将 `REV-001` 至 `REV-003` 的输出明确为中间审查记录或 reviewer 内部输入，只有 `REV-004` 写最终 `docs/review.json`。 |
| test_report target 覆盖 | `TEST-003`、`TEST-004`、`TEST-005` 都要求 fail 时给出 target，target 枚举覆盖 backend/frontend/both/api/pm/planner/test。 | 后续生成 `docs/test_report.md` 时保持该枚举，不扩展未约定 target。 |
| validator 环境记录 | `VAL-001`、`VAL-002`、`VAL-004` 已要求记录 JDK/Maven/Node/npm 路径和版本。 | 后续 validator 阶段按 `docs/service_info.md` 记录实际路径；环境不可用时必须说明 fallback 差异和复现前置条件。 |
| 任务状态汇总 | `docs/task_plan.md` 已定义小任务、大任务、项目完成状态规则，足以判断项目状态。 | 后续执行时需要 Orchestrator 将实际状态同步到 `.codex/state.json` 或等价状态台账。 |

## 覆盖性判断

1. `TEST-001` 至 `TEST-005` 覆盖测试计划/夹具、API 契约用例、E2E 主链路、权限/异常/幂等/OpenAPI 和测试报告，内容足以支撑后续测试设计与执行；阻塞点只在并行输出路径。
2. `VAL-001` 至 `VAL-004` 已要求后端 clean compile、前端 clean build、契约同步检查和构建报告；阻塞点只在并行输出路径。
3. `REV-001` 至 `REV-004` 能覆盖架构、契约实现、质量测试构建和最终合法 `docs/review.json`，可驱动 pass/fail 回环；中间产物命名建议进一步澄清。
4. 测试设计可在实现前并行准备：`TEST-001` 依赖 `PLAN-001`，`TEST-002` 依赖 `TEST-001` 和冻结 API，可与后端、前端准备并行。
5. 测试执行依赖正确：`TEST-003`、`TEST-004` 均依赖 `BE-015`、`FE-012`、`TEST-002`，能保证后端自检、前端契约闭环和 API 用例完成后再执行。
6. 任务完成标准能够判断小任务、大任务和项目状态：任务矩阵、里程碑、状态合成规则和 review pass 条件已给出明确口径。

## 是否允许任务计划冻结

test 角度结论：不允许冻结。

需先修复 `TTASK-001` 与 `TTASK-002` 的并行输出路径冲突。修复后如不新增测试范围或契约语义，test 仅需复核 `TEST`、`VAL` 相关任务输出和并行批次即可。
