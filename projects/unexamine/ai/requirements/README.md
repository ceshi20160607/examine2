# 需求分析

- `requirement-intake.json`：从唯一需求源确定性生成的 760 个原始区块索引。
- `analysis-plan.json`：覆盖全部可分析内容的 117 个互斥分析包，共 611 个可覆盖区块，延期区块为 0。

分析包不是开发周期。它们只用于完整阅读和消除重复语义；合并后的原子能力与用例才进入任务拆分。

片段文件存在且通过结构校验，只能记为“已提取、结构有效”，不能记为需求分析完成。每个片段还必须依据 `template_base/agents/requirements.md` 的统一粒度规则接受总控语义审计；审计证据保存在 `../evidence/requirement-fragment-audit.json`，并绑定分析计划及对应片段的 SHA-256。片段修改后，旧审计自动失效。
