# TASK-QA-015

## meta

- task_id: TASK-QA-015
- type: test
- owner: test
- phase: build
- parallel_group: G4-QA
- depends_on: [TASK-QA-010, TASK-BE-010, TASK-BE-011, TASK-BE-012, TASK-BE-013, TASK-BE-033]

## goal

Create clean-build G3 evidence.

## inputs

- `docs/testing/static-acceptance-checks.md`
- G3 backend outputs

## outputs

- `docs/evidence/build-g3.md`

## scope

### Do

- Run backend compile and static checks for platform identity, context, permission, audit log, and async task APIs.

### Do Not

- Do not include runtime module or frontend E2E checks.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
Test-Path docs/evidence/build-g3.md
```

## acceptance

- [ ] G3 compile and static acceptance pass.
- [ ] Evidence lists command summaries and residual risks.

