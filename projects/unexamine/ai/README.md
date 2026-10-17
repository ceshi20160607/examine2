# 项目执行资料

唯一产品需求源是 `../../../docs/user_requirement.md`。本目录只保留当前需求索引、产品基线、执行断点与当前有效的验证记录。

- `requirements/requirement-intake.json`：由需求原文生成的结构索引，不独立定义业务规则。
- `planning/product-baseline.md`：用户已确认的产品主线摘要。
- `planning/spark-execution-plan.md`、`planning/spark-state.json`、`planning/execution-control.json`：当前实施顺序、准确断点与工程门禁。
- `planning/spark-requirement-ledger.json`：需求原文行索引和验证状态，不替代需求文件。
- `evidence/`：仅保存当前结构下仍有效的验证结果；旧周期数字和旧测试不能抵扣当前验收。

执行前运行 `python template_base/tools/tb.py workspace-validate --workspace workspace.json` 和 `python template_base/tools/tb.py execution-context --workspace workspace.json`。业务结果验收后再运行 `execution-verify` 并更新断点。
