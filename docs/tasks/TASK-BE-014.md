# TASK-BE-014

## meta

- task_id: TASK-BE-014
- type: implementation
- owner: backend
- phase: build
- parallel_group: G4
- depends_on: [TASK-BE-010, TASK-BE-011, TASK-BE-012, TASK-BE-013]

## goal

Create platform integration smoke evidence for auth, system lifecycle, context, and permission slices.

## inputs

- `docs/api/api.md`
- `TASK-BE-010` to `TASK-BE-013` outputs

## outputs

- `docs/evidence/backend-plat-smoke.md`

## scope

### Do

- Run compile and targeted API/service smoke checks for login, system creation, system switch, tenant switch, role permission, and permission preview.

### Do Not

- Do not accept downstream module/runtime tasks.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
Test-Path docs/evidence/backend-plat-smoke.md
```

## acceptance

- [ ] Evidence cites command results and residual risks.
- [ ] No P0/P1 platform blockers remain.

