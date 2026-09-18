# Workspace boundaries

This repository contains one active implementation and one reusable engineering template.

- `docs/user_requirement.md` is the only product requirement source.
- `projects/unexamine/` is the only active product implementation root.
- `template_base/` contains reusable deterministic engineering capabilities only.
- Only current requirements, current implementation and reusable template capabilities belong in this workspace. Removed historical code and evidence must not be recreated or treated as a requirement source.
- Run `python template_base/tools/tb.py workspace-validate --workspace workspace.json` before implementation work that changes repository boundaries.

# Current execution handoff

- For the current completion effort, read `projects/unexamine/ai/planning/spark-execution-plan.md` and `projects/unexamine/ai/planning/spark-state.json` first. Use the current session model, including for delegated work; the retained `spark-*` filenames preserve existing references and do not select the Spark model. Follow the current bounded task packet; do not resume old numbered cycles or infer completion from old reports.
- Before implementing a substep, run `python template_base/tools/tb.py execution-context --workspace workspace.json`. The project execution control binds the current requirement rows, dependencies, source entry files and acceptance. Fix invalid bindings by reviewing the affected source; never bypass failures by changing hashes or status alone.
- Before advancing a verified substep, run `python template_base/tools/tb.py execution-verify --workspace workspace.json --receipt <current-evidence-path>`, then update the exact next scope and repeat the engineering entry within the admitted task packet. Finish packet-level integrated verification before admitting another packet. This integrity check supplements actual business verification; it does not award product completion.
- `docs/user_requirement.md` remains the only product requirement source. The current ledger is a source index and verification record, not a replacement requirement specification.
- Preserve pre-existing staged and unstaged changes. Never reset the workspace or regenerate over hand-written business code to obtain a clean baseline.
- Group related work around one observable business outcome, with total assessed effort at most 240 minutes; this is an estimate budget, not an elapsed-time timer. Stabilize shared contracts and common skeletons before implementing dependent features. Parallel work requires satisfied dependencies, explicit file ownership and non-conflicting migration, build and test resources; shared services and writes to the same fixture remain serial.
- Finish and verify the current substep before expanding scope. Independent parallel branches retain their own scope and evidence; the coordinator merges them and writes the authoritative checkpoint. Record the next exact substep when handing off; do not rebuild the plan or repeat the full audit on each continuation.
