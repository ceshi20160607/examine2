# TASK-FE-010

## meta

- task_id: TASK-FE-010
- type: implementation
- owner: frontend
- phase: build
- parallel_group: G4
- depends_on: [TASK-FE-001, TASK-BE-014]

## goal

Implement auth pages, platform workbench shell, and system shell entry flows.

## inputs

- `docs/design/prototypes/index.html`
- `docs/api/api.md`
- `frontend/docs/api-contract-map.md`
- `frontend/src/api/**`
- `frontend/src/shared/**`

## outputs

- `frontend/src/features/auth/**`
- `frontend/src/features/platform/**`
- `frontend/src/features/system-shell/**`

## scope

### Do

- Implement login, register-and-create-system, password reset, MFA/SSO entry states, account profile entry, login audit list entry, and auth error/field focus states.
- Implement platform workbench entry shell for dashboard, Flow, apps, todo, messages, profile, create system, and system switching.
- Implement system shell entry for dashboard, configured module groups/modules, todo, messages, system switching, profile, and permission-aware backend entry.
- Wire pages to shared API/state abstractions without hard-coding localhost.

### Do Not

- Do not implement admin configuration pages or runtime business module pages.

## self_check_commands

```powershell
$env:Path="D:\java\nodejs;$env:Path"
npm --prefix frontend run build
```

## acceptance

- [ ] Login page has no unintended scroll bar at desktop baseline.
- [ ] Register flow shows create-system result and next-step entry without exposing technical ids as primary copy.
- [ ] Password reset has request and confirm states with field-level error feedback.
- [ ] Platform member and platform admin shell entries are separated by permission.
- [ ] System shell hides unauthorized backend entry and rebuilds visible modules after system switch.
- [ ] `task-accept` verdict is pass.

## integration_test

QA uses these pages as the entry for all E2E paths.
