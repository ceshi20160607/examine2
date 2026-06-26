# TASK-QA-018

## meta

- task_id: TASK-QA-018
- type: test
- owner: test
- phase: build
- parallel_group: G9-QA
- depends_on: [TASK-QA-012, TASK-QA-016, TASK-FE-030, TASK-BE-040]

## goal

Create pre-E2E build and readiness evidence.

## inputs

- `docs/testing/static-acceptance-checks.md`
- `TASK-FE-030` outputs
- `TASK-BE-040` evidence

## outputs

- `docs/evidence/build-g9.md`
- `docs/evidence/pre-e2e-readiness.md`

## scope

### Do

- Run backend build, frontend build, route/static acceptance, and browser smoke preparation.

### Do Not

- Do not mark user script passed.

## self_check_commands

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
Test-Path docs/evidence/build-g9.md
Test-Path docs/evidence/pre-e2e-readiness.md
```

## acceptance

- [ ] Pre-E2E evidence confirms build artifacts are ready.
- [ ] Remaining risks are explicit before `TASK-QA-020`.

