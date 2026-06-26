# TASK-QA-020

## meta

- task_id: TASK-QA-020
- type: test
- owner: test
- phase: build
- parallel_group: G6
- depends_on: [TASK-FE-030, TASK-BE-040, TASK-QA-001]

## goal

Run the che primary E2E script and produce final build-readiness evidence.

## inputs

- `docs/testing/test-strategy.md`
- `docs/testing/fixtures.md`
- `docs/api/api.md`
- `docs/design/prototypes/index.html`

## outputs

- `docs/evidence/e2e-che-script.md`
- `docs/evidence/build-readiness.md`

## scope

### 做

- Validate platform admin creates vehicle system, configures member/role/module/field/flow, `che` logs in, uses vehicle list/detail/create/edit/approval/import/export/message/work flows, and cannot access admin-only areas.
- Record failures with trace IDs and issue references.

### 不做

- Do not change product design or API contract during test execution.

## self_check_commands

```powershell
Test-Path docs/evidence/e2e-che-script.md
Test-Path docs/evidence/build-readiness.md
```

## acceptance

- [ ] Che 11-step script evidence is complete.
- [ ] Four-role permission matrix has no P0/P1 failures.
- [ ] `clean-build` evidence is linked.
- [ ] `task-accept` verdict is pass.

## integration_test

This task is the build-to-verify handoff evidence.

