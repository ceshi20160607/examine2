# Workspace boundaries

This repository contains one active implementation and one reusable engineering template.

- `docs/user_requirement.md` is the only product requirement source.
- `projects/unexamine/` is the only active product implementation root.
- `template_base/` contains reusable deterministic engineering capabilities only.
- Only current requirements, current implementation and reusable template capabilities belong in this workspace. Removed historical code and evidence must not be recreated or treated as a requirement source.
- Run `python template_base/tools/tb.py workspace-validate --workspace workspace.json` before implementation work that changes repository boundaries.

# 开发规约

- 新建、修改表结构时，表和所有字段必须有中文备注；复杂 SQL、数据修复和迁移步骤要说明用途。数据库时间字段统一用 `*_time`，Java 属性用 `*Time`。修改已有时间字段要同步映射、查询、接口及已有数据迁移方案，不能直接删表重建。
- Java 类、方法、业务字段和关键逻辑添加解释业务含义与限制的中文注释，避免无意义逐行注释。
- 优先沿用现有模块结构和写法。简单 CRUD 或一次性逻辑不增加抽象层，只有明确复用或扩展需求才封装。
- 交付前检查注释、时间命名和不必要封装，执行相关编译或测试并报告结果。

# Current execution handoff

- On every reopened session, first read `projects/unexamine/ai/planning/product-closure-audit.md`, `projects/unexamine/ai/planning/spark-execution-plan.md` and `projects/unexamine/ai/planning/spark-state.json`, then run `python template_base/tools/tb.py project-status --workspace workspace.json`. The audit is a current evidence summary, not a new requirement source; update only changed findings instead of repeating the full audit.
- The user's standing priority is whole-product delivery against `docs/user_requirement.md` §§2.3 and 10 and the latest clarifications in A.15–A.19. While `G18_PRODUCT_RELEASE` is unfinished, select work by the same-candidate release journey and its actual blockers. Keep S18.39 and other isolated feature combinations unverified and deferred unless that journey proves they block release. A verified local step or G1–G17 gate never proves whole-product completion.
- Use the current session model, including for delegated work; the retained `spark-*` filenames preserve existing references and do not select the Spark model. Follow the product-bound current task packet; do not resume old numbered cycles or infer completion from old reports. If the packet points away from the unfinished product gate, correct the execution scope and explain the conflict before coding.
- Before implementing a substep, run `python template_base/tools/tb.py execution-context --workspace workspace.json`. The project execution control binds the current requirement rows, dependencies, source entry files and acceptance. Fix invalid bindings by reviewing the affected source; never bypass failures by changing hashes or status alone.
- Before advancing a verified substep, run `python template_base/tools/tb.py execution-verify --workspace workspace.json --receipt <current-evidence-path>`, then update the exact next scope and repeat the engineering entry within the admitted task packet. Finish packet-level integrated verification before admitting another packet. This integrity check supplements actual business verification; it does not award product completion.
- `docs/user_requirement.md` remains the only product requirement source. The current ledger is a source index and verification record, not a replacement requirement specification.
- Preserve pre-existing staged and unstaged changes. Never reset the workspace or regenerate over hand-written business code to obtain a clean baseline.
- Group related work around one observable business outcome, with total assessed effort at most 240 minutes; this is an estimate budget, not an elapsed-time timer. Stabilize shared contracts and common skeletons before implementing dependent features. Parallel work requires satisfied dependencies, explicit file ownership and non-conflicting migration, build and test resources; shared services and writes to the same fixture remain serial.
- Finish and verify the current product-bound substep before expanding scope. Independent parallel branches retain their own scope and evidence; the coordinator merges them and writes the authoritative checkpoint. Record the next exact substep and the overall release blockers when handing off; do not rebuild the plan or repeat the full audit on each continuation.
