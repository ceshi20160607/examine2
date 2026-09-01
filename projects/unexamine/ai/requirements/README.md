# 需求分析

- `requirement-intake.json`：从唯一需求源确定性生成的 719 个原始区块索引。
- `analysis-plan.json`：覆盖全部可分析内容的 117 个互斥分析包，共 564 个可覆盖区块，延期区块为 0。
- `fragment-index.json`：绑定全部 117 个语义分片及其 SHA-256。
- `requirement-consolidation.json`：记录跨章节重复候选到最终原子需求的语义别名，不能用脚本猜测。
- `requirement-analysis.json`：由别名映射确定性生成的最终原子需求、验收条件、决策和全源覆盖。

分析包不是开发周期。它们只用于完整阅读和消除重复语义；合并后的原子能力与用例才进入任务拆分。

片段文件存在且通过结构校验，只能记为“已提取、结构有效”，不能记为需求分析完成。每个片段还必须依据 `template_base/agents/requirements.md` 的统一粒度规则接受总控语义审计；审计证据保存在 `../evidence/requirement-fragment-audit.json`，并绑定分析计划及对应片段的 SHA-256。片段修改后，旧审计自动失效。

全部分片审核后运行 `requirements-consolidate`。工具只执行人工确认的别名映射，合并来源块、验收条件和决策并再次执行最终需求校验；没有进入 `requirement-analysis.json` 的候选不能驱动架构和任务。
