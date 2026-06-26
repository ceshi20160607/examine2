# Evidence Conventions

## File Naming

- Task acceptance evidence: `docs/evidence/{task-id-lower}.md`
- Batch build evidence: `docs/evidence/build-{group-lower}.md`
- Smoke evidence: `docs/evidence/{domain}-smoke.md`
- User E2E evidence: `docs/evidence/e2e-che-script.md`

## Minimum Evidence

Each task evidence file must include:

- Task ID, commit/worktree summary, owner, date, and inputs used.
- Changed output paths.
- Self-check commands and important output summary.
- Screenshots or browser notes for UI tasks after browser-capable views exist.
- Known residual risks and any blocked follow-up.
- `task-accept` result from an accepter other than the implementer.

## Command Logs

Store the command text and result summary. Full logs may be linked when large. Environment failures must identify JDK, Maven, Node, port, or dependency cause before being treated as code failure.

## Traceability

Evidence must cite at least one of:

- `docs/api/api.md`
- `docs/design/prototype-brief.md`
- `docs/design/prototypes/index.html`
- `docs/tasks/TASK-*.md`
- `.cursor/knowledge/project-operating-rules.md`

