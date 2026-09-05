# Workspace boundaries

This repository contains one active implementation and one reusable engineering template.

- `docs/user_requirement.md` is the only product requirement source.
- `projects/unexamine/` is the only active product implementation root.
- `template_base/` contains reusable deterministic engineering capabilities only.
- Only current requirements, current implementation and reusable template capabilities belong in this workspace. Removed historical code and evidence must not be recreated or treated as a requirement source.
- Run `python template_base/tools/tb.py workspace-validate --workspace workspace.json` before implementation work that changes repository boundaries.

# Current execution handoff

- For the current completion effort, read `projects/unexamine/ai/planning/spark-execution-plan.md` and `projects/unexamine/ai/planning/spark-state.json` first. Follow their bounded work packets; do not resume old numbered cycles or infer completion from old reports.
- `docs/user_requirement.md` remains the only product requirement source. The Spark ledger is a source index and verification record, not a replacement requirement specification.
- Preserve pre-existing staged and unstaged changes. Never reset the workspace or regenerate over hand-written business code to obtain a clean baseline.
- Finish and verify the current substep before expanding scope. Record the next exact substep when handing off; do not rebuild the plan or repeat the full audit on each continuation.
