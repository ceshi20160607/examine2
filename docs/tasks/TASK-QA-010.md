# TASK-QA-010

## meta

- task_id: TASK-QA-010
- type: test
- owner: test
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create static acceptance checks and the shared evidence template used by build batches.

## inputs

- `docs/api/api.md`
- `docs/design/prototypes/index.html`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`

## outputs

- `docs/testing/static-acceptance-checks.md`
- `docs/evidence/static-check-template.md`

## scope

### Do

- Define static checks for route existence, duplicate action removal, row-click boundaries, disabled reasons, filters, pagination, trace IDs, async tasks, permissions, and SecretRef safety.
- Define a reusable evidence template for every batch.

### Do Not

- Do not mark any implementation task accepted.
- Do not run browser E2E before the corresponding frontend task is complete.

## self_check_commands

```powershell
Test-Path docs/testing/static-acceptance-checks.md
Test-Path docs/evidence/static-check-template.md
```

## acceptance

- [ ] Checks cover platform shell, system shell, system admin, platform admin, runtime data, todo, message, work, SSO, Agent, log, and async tasks.
- [ ] Checks distinguish business data lists from static/config tables for row-click behavior.
- [ ] `task-accept` verdict is pass.

## integration_test

`clean-build` and later QA tasks cite this template.

