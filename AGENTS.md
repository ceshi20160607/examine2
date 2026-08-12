# Workspace boundaries

This repository contains one active implementation and one hidden legacy archive.

- `docs/user_requirement.md` is the only product requirement source.
- `projects/unexamine/` is the only active product implementation root.
- `template_base/` contains reusable deterministic engineering capabilities only.
- `.tmp/legacy_reference/` is reference-only history. Do not scan it in broad searches, edit it, build it, run it, or treat it as evidence that a current requirement is implemented.
- Consult a legacy file only for a concrete, named question. Search the narrowest known legacy path and trace any reused behavior back to a current requirement block.
- New source, migrations, tests, runtime evidence and delivery artifacts must not be written to the legacy archive.
- Run `python template_base/tools/tb.py workspace-validate --workspace workspace.json` before implementation work that changes repository boundaries.
