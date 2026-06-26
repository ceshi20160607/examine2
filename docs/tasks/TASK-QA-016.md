# TASK-QA-016

## meta

- task_id: TASK-QA-016
- type: test
- owner: test
- phase: build
- parallel_group: G6-QA
- depends_on: [TASK-QA-015, TASK-BE-022, TASK-BE-031]

## goal

Create clean-build G6 evidence.

## inputs

- `docs/testing/static-acceptance-checks.md`
- import/export and workflow runtime outputs

## outputs

- `docs/evidence/build-g6.md`

## scope

### Do

- Verify import/export async tasks, attachment handling, workflow approval idempotency, todo action, and audit evidence.

### Do Not

- Do not run full user E2E.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
Test-Path docs/evidence/build-g6.md
```

## acceptance

- [ ] Import/export and workflow checks pass.
- [ ] Async task status and result/error file refs are covered.

