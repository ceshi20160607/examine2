# TASK-QA-001

## meta

- task_id: TASK-QA-001
- type: test
- owner: test
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create the test strategy and acceptance evidence conventions for build tasks.

## inputs

- `docs/api/api.md`
- `docs/design/prototypes/index.html`
- `.cursor/skills/task-accept.md`
- `.cursor/skills/clean-build.md`

## outputs

- `docs/testing/test-strategy.md`
- `docs/testing/evidence-conventions.md`

## scope

### 做

- Define API, UI, permission, SSO, Agent, import/export, log, async task, and che E2E evidence requirements.
- Define evidence file naming, required command logs, screenshots, trace IDs, and acceptance owner separation.

### 不做

- Do not execute E2E before frontend and backend build tasks are complete.

## self_check_commands

```powershell
Test-Path docs/testing/test-strategy.md
Test-Path docs/testing/evidence-conventions.md
```

## acceptance

- [ ] Evidence paths are named under `docs/evidence/`.
- [ ] Evidence conventions separate implementer self-check from task acceptance.
- [ ] `task-accept` verdict is pass.

## integration_test

All implementation tasks use this test strategy for acceptance.
