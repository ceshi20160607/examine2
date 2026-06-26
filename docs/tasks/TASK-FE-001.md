# TASK-FE-001

## meta

- task_id: TASK-FE-001
- type: implementation
- owner: frontend
- phase: build
- parallel_group: G0
- depends_on: []

## goal

Create frontend scaffold, shared design primitives, routing shell, and contract-aware API foundation.

## inputs

- `docs/design/prototypes/index.html`
- `docs/design/prototype-brief.md`
- `docs/api/api.md`
- `frontend/src/api/types.ts`

## outputs

- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/src/app/**`
- `frontend/src/shared/**`

## scope

### 做

- Set up Vite/TypeScript frontend scaffold, following the existing `.oldbk/frontend` vanilla TypeScript style.
- Build app shell, theme tokens, shared table/filter/drawer/task/message primitives, route registry, and API client placeholder using existing contract types.

### 不做

- Do not build business pages beyond empty route shells.
- Do not change `frontend/src/api/types.ts` unless API contract changes.

## self_check_commands

```powershell
$env:Path="D:\Tools\node-v24.15.0-win-x64;$env:Path"
npm --prefix frontend run build
```

## acceptance

- [ ] Frontend build succeeds.
- [ ] Shared components include disabled reason, trace id display, async task result, list pagination, and row-click affordance patterns.
- [ ] `task-accept` verdict is pass.

## integration_test

Subsequent frontend tasks consume the shell and shared components.
