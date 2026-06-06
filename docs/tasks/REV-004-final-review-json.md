# REV-004 最终 review.json

- taskId: REV-004
- 标题: 最终 review.json
- 负责角色: reviewer
- 所属大任务/模块: Review / 最终结论
- 目标: 输出合法 `docs/review.json`，驱动 pass 或回环。
- 输入文件: `docs/prd.md`、`docs/project_understanding.md`、`docs/db_design.md`、`docs/api.md`、`docs/task_plan.md`、`docs/test_report.md`、`docs/build_report.md`、`docs/review_parts/rev-001-architecture.md`、`docs/review_parts/rev-002-contract.md`、`docs/review_parts/rev-003-quality.md`、`backend/`、`frontend/`
- 输出文件或输出目录: `docs/review.json`

## 详细工作内容

- 汇总 `docs/review_parts/` 中的架构、契约、质量、测试和构建审查结论。
- 按固定 JSON 结构输出 `status`、`target` 和 `issues`。
- fail 时明确回环目标和具体问题文件。

## 完成状态定义

- 默认状态: pending。
- 完成条件: `docs/review.json` 是合法 JSON 且符合 review 判定机制。

## 验收标准

- `status=pass` 时 `target=none` 且 `issues=[]`。
- `status=fail` 时 target 合法，issues 至少 1 条。

## 测试/自检要求

- JSON 解析通过。
- issues.file 指向具体允许目录。

## 依赖任务

- REV-003

## 可并行关系

- 不可并行；最终 review 必须最后输出。

## 不允许事项

- 不输出 Markdown 代替 JSON。
- 不使用超出约定枚举的 target。
