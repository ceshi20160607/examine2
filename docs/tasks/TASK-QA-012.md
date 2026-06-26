# TASK-QA-012

## meta

- task_id: TASK-QA-012
- type: test
- owner: test
- phase: build
- parallel_group: G4-QA
- depends_on: [TASK-QA-005, TASK-BE-014, TASK-FE-010]

## goal

Create role permission and route evidence.

## inputs

- `docs/testing/fixtures.md`
- `docs/api/api.md`
- `TASK-BE-014` evidence
- `TASK-FE-010` outputs

## outputs

- `docs/evidence/permission-matrix.md`
- `docs/evidence/role-route-check.md`

## scope

### Do

- Verify platform root, platform member, system creator super admin, and normal system member route/permission boundaries.

### Do Not

- Do not accept runtime business views before FE-030.

## self_check_commands

```powershell
Test-Path docs/evidence/permission-matrix.md
Test-Path docs/evidence/role-route-check.md
```

## acceptance

- [ ] Unauthorized backend entries are hidden or disabled with reason.
- [ ] Platform users cannot bypass system switch for business data.

