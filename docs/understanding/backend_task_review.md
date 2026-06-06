# 后端任务拆分审查

## 审查结论

fail

当前任务拆分已覆盖后端主要业务域、examine-generator、base/manage 分层、冻结 API 约束和测试/验证阶段要求，但 BE-015 与 BE-008、BE-012 之间存在依赖闭环，且最终后端自检产物缺少明确路径和判定格式。backend 角度不建议冻结任务计划。

## 审查范围

- `docs/task_plan.md`
- `docs/tasks/`
- `docs/api.md`
- `docs/api_review.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取旧项目目录，未写代码，未生成 SQL，未创建 `backend/`、`frontend/` 或 `sql/`。

## 覆盖性判断

- BE-001 至 BE-015 已覆盖 Maven 多模块、core、认证会话、平台中心、系统成员/RBAC、字典、应用/模块/字段/页面配置、运行记录、流程、上传、导出、OpenAPI、审计运维、权限拦截、幂等并发和 API 自检。
- GEN-001 至 GEN-004 已覆盖生成器模块骨架、数据库连接与表映射、base 模板策略、代码生成执行和 `backend/docs/mybatis-plus-generation.md` 报告。
- `docs/task_plan.md` 已明确 `base` 层由 MyBatis-Plus 生成，`manage` 层承载 Controller、BO/DTO/VO、事务、权限、转换；也明确不得手写大批量 CRUD、不得私改冻结 API。
- BE-008 被定义为流程、上传/导出、OpenAPI 的运行记录核心前置，这个业务方向正确；但当前依赖配置把 BE-008 反向依赖 BE-015，导致无法调度。
- TEST-001 至 TEST-005、VAL-001 至 VAL-004 已覆盖 API 用例、E2E、权限异常、幂等/OpenAPI、后端 clean compile 和构建报告，但后端自身 BE-015 的输出产物还不够可验收。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞任务冻结 |
| --- | --- | --- | --- | --- | --- |
| BTR-001 | BE-008 依赖 BE-015，BE-012 也依赖 BE-015；同时 BE-015 又依赖 BE-008、BE-012 和 BE-004 至 BE-014。任务矩阵、任务文件与“BE-015 最终自检”里程碑形成依赖闭环。 | 后端实现阶段无法按拓扑顺序调度；BE-008 作为 flow/export/openapi 前置会被 BE-015 卡住，BE-012 也会被自身最终自检反向卡住。 | planner | 移除 BE-008、BE-012 对 BE-015 的依赖；将通用幂等/并发基础能力前置到 BE-002/BE-014，或拆成独立早期任务；保留 BE-015 仅作为 BE-004 至 BE-014 后的最终 API 自检任务。同步修正依赖图、总任务矩阵和对应任务文件。 | 是 |
| BTR-002 | BE-015 输出仅写“后端自检记录”，没有明确文件路径、最小命令、报告字段和 pass/fail 判定格式。 | backend 小任务完成状态难以验证；TEST/VAL 只能依赖抽象的 BE-015 完成状态，无法判断错误码、权限、事务、幂等、OpenAPI 签名是否已自检闭环。 | planner | 为 BE-015 明确固定输出，例如 `backend/docs/backend-self-check.md`；要求记录执行命令、覆盖 API 组、核心场景、失败日志摘要、未覆盖风险和结论。将 VAL-001/TEST-003/TEST-004 对 BE-015 的依赖落到该可检查产物。 | 是 |

## 非阻塞跟踪项

| itemId | 跟踪项 | 建议 |
| --- | --- | --- |
| BTR-NB-001 | GEN-002/GEN-004 对 DBA-006、SQL 导入和数据库连接的依赖主要通过 GEN-001 传递。 | 可在 GEN-002、GEN-004 的依赖任务中显式补充 DBA-006，便于后续调度器和执行人直接识别“SQL 已导入、表可读取”前置。 |
| BTR-NB-002 | BE-013 位于 BE-012 之后，但审计写入是多数业务接口的横切能力。 | 建议在 BE-002 或 BE-013 中区分“审计写入基础能力”和“审计/运维查询接口”，避免早期业务模块实现时漏写审计。 |
| BTR-NB-003 | BE-004、BE-007、BE-009、BE-012 单个任务覆盖 API 数量较多。 | 若后续实现预计超过单次可控范围，建议按 task 内子范围补充子清单，但不必改变当前任务 ID；至少在每个任务内列出 API ID 完成打勾表。 |
| BTR-NB-004 | `docs/project_understanding.md` 的阶段描述仍保留历史 `current_step=13`、`api_frozen=false` 文案，而 `.codex/state.json` 与 `docs/api_review.md` 已显示 API 冻结。 | 当前不阻塞 backend 任务审查，因为任务计划已基于 state/api_review；建议 PM 后续同步历史阶段说明，减少下游误读。 |

## 是否允许任务计划冻结

backend 角度结论：不允许冻结。

复核条件：planner 修复 BTR-001 和 BTR-002，确保后端任务依赖图无闭环、BE-015 自检产物可被 TEST/VAL 检查后，backend 可重新复核并判定是否通过。
