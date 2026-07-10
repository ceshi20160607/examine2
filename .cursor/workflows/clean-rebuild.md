# Clean Rebuild Workflow

## Inputs

- `.cursor/architecture/clean-rebuild.md`
- `.cursor/session/state.json`
- `.cursor/session/rebuild/source-index.md`
- `.cursor/session/rebuild/module-boundary-map.md`
- `.cursor/session/rebuild/task-plan.md`
- `docs/user_requirement.md`
- `docs/design/prototypes/**`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`

## Steps

1. Restore disk context from `.cursor` and retained source files.
2. Keep `.oldbk/restart-20260708-220451/` as reference-only.
3. If `mode=requirements-rebuild`, execute the `REQ-R0-*` queue and do not code.
4. Execute the current `nextTasks[0]` from `.cursor/session/state.json` only when its gate is open.
5. Before coding, make sure the task maps to a product boundary, role journey, data object, permission boundary, and acceptance evidence.
6. After each task, write evidence and update `state.json` with the next executable task.

## Phase Order

0. Requirements rebuild and engineering architecture.
1. Source baseline and IA lock.
2. Clean project scaffold.
3. Auth, account, system switch, and shell context.
4. Admin/config foundation.
5. Business module configuration and runtime.
6. Flow and Application authorization.
7. Work, todo, message, AI assistance.
8. Release, E2E, and user trial readiness.

## Stop Conditions

Stop coding and update `.cursor` first if:

- a page cannot be mapped to retained requirements/prototype/flow sources;
- Application and Flow boundaries blur again;
- platform and system contexts mix without a context switch;
- list/detail layout violates left-list and right-detail tab rules;
- a task only proves generated CRUD or API 200 without usable role behavior.
