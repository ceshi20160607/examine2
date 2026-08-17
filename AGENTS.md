# Workspace boundaries

This repository contains one active implementation and one reusable engineering template.

- `docs/user_requirement.md` is the only product requirement source.
- `projects/unexamine/` is the only active product implementation root.
- `template_base/` contains reusable deterministic engineering capabilities only.
- Only current requirements, current implementation and reusable template capabilities belong in this workspace. Removed historical code and evidence must not be recreated or treated as a requirement source.
- Run `python template_base/tools/tb.py workspace-validate --workspace workspace.json` before implementation work that changes repository boundaries.
