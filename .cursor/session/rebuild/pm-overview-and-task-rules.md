# PM Overview And Task Rules

## Current Directive

The previous rebuild attempts did not satisfy the target. The current responsibility is to reorganize requirements and engineering architecture before any development continues.

This is not a coding phase.

## Active Queue

Current batch: `REQUIREMENTS-R0`

1. `REQ-R0-001 Product Boundary Contract`
2. `REQ-R0-002 Engineering Architecture Map`
3. `REQ-R0-003 Committee Review Gate`
4. `REQ-R0-004 Legacy Inventory And Cleanup Plan`
5. `REQ-R0-005 Backend Codegen And Manage Contract`
6. `REQ-R0-006 Requirement-To-Task Breakdown`
7. `REQ-R0-007 User Review Package`
8. `REQ-R0-008 UI System And Interaction Contract`

The old `REBUILD-P0-006 Flow And Application Gateway` task is not current.

## PM Responsibilities In This Stage

- Maintain the whole-project view.
- Keep product domains mapped to engineering modules.
- Ensure requirement/prototype/flow inconsistencies are found before coding.
- Route structural problems through committee review, not ad hoc implementation.
- Keep legacy material classified before anything is deleted.
- Ensure future coding tasks state source requirement, backend/frontend/sql scope, generated base scope, handwritten manage scope, permission paths, state/readback, side effects, and evidence.

## Acceptance Rules For Requirements Rebuild

Each `REQ-R0-*` task must produce durable files under `.cursor/session/rebuild/` or `.cursor/architecture/`.

No task is accepted from chat-only conclusions.

Coding opens only after the user review package confirms:

- product boundary is stable;
- engineering architecture map is stable;
- legacy inventory is understood;
- committee gate is defined;
- backend codegen/manage workflow is frozen;
- future task breakdown is cross-layer and evidence-driven;
- UI system follows the dense enterprise operations pattern from the reference screenshots.
