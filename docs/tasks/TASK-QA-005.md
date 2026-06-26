# TASK-QA-005

## meta

- task_id: TASK-QA-005
- type: test
- owner: test
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create reusable fixtures for contract, role, permission, tenant, SSO, message, todo, work, Agent, log, and async task tests.

## inputs

- `docs/api/api.md`
- `frontend/src/api/types.ts`
- `docs/design/prototype-brief.md`
- `docs/testing/test-strategy.md`

## outputs

- `docs/testing/fixtures.md`
- `docs/testing/contract-fixtures.md`

## scope

### Do

- Define role fixtures for `platform_admin_root`, `platform_member`, `sys_admin_vehicle`, and `che`.
- Define system, tenant, member, module, record, todo, message, work, SSO, Agent, log, and async task seed objects.
- Map each fixture to frozen contract object names.

### Do Not

- Do not execute E2E before frontend and backend build tasks are complete.
- Do not change `docs/api/api.md` or `frontend/src/api/types.ts`.

## self_check_commands

```powershell
Test-Path docs/testing/fixtures.md
Test-Path docs/testing/contract-fixtures.md
```

## acceptance

- [ ] Fixtures map to frozen API objects and design roles.
- [ ] Permission fixtures include allowed, denied, masked, hidden, and no-member states.
- [ ] Async task and message fixtures include trace IDs and audit IDs.
- [ ] `task-accept` verdict is pass.

## integration_test

Downstream QA evidence and frontend mocks use these fixtures.

