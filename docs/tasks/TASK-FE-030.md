# TASK-FE-030

## meta

- task_id: TASK-FE-030
- type: implementation
- owner: frontend
- phase: build
- parallel_group: G5
- depends_on: [TASK-FE-012, TASK-FE-023, TASK-BE-020, TASK-BE-030]

## goal

Implement system runtime dynamic record pages.

## inputs

- `docs/design/prototypes/index.html`
- `docs/api/api.md`
- `frontend/docs/api-contract-map.md`

## outputs

- `frontend/src/features/runtime/records/**`
- `frontend/src/features/runtime/import-export/**`

## scope

### Do

- Implement module group top navigation, left module list, dynamic record list, right detail drawer, summary/detail tabs, approval sidebar, create/edit drawer, import/export dialogs, and async task feedback.

### Do Not

- Do not implement backend APIs.
- Do not implement todo/message center, work management, or Agent confirmation pages.
- Do not add duplicate import/export buttons or action buttons when row click is the primary detail action.

## self_check_commands

```powershell
$env:Path="D:\Tools\node-v24.15.0-win-x64;$env:Path"
npm --prefix frontend run build
```

## acceptance

- [ ] Business rows open detail by row click.
- [ ] Batch actions are disabled with visible reasons when selection is invalid.
- [ ] Detail drawer keeps list context and has approval/sidebar/attachments/history tabs where contract requires them.
- [ ] Import/export flows show precheck, task status, result file, error file, traceId, and auditLogId.
- [ ] `task-accept` verdict is pass.

## integration_test

Che E2E validates runtime list, detail, approval, import/export, and no-permission behavior.

