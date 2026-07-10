# Requirements Rebuild Task Plan

## Current Batch

`REQUIREMENTS-R0`

## Current Stage

现在不是开发阶段，是重新整理需求和完善工程阶段。

Previous implementation batches and evidence remain historical context only. The next executable work is not `REBUILD-P0-006`; it is requirements and engineering architecture reconstruction.

## Task Queue

1. `REQ-R0-001 Product Boundary Contract`
   - Freeze the meaning of 工作台, Flow, 应用, 工作, AI, 待办, 消息, 后台, 个人信息, 业务模块, and Flow 管理.
   - Separate platform context from system context.
   - Reconcile the user's latest boundary explanation with `docs/user_requirement.md`, `docs/temp_flow.md`, `docs/temp_flow_persion.html`, and `docs/design/prototypes/index.html`.
   - Output a single product-boundary contract before any implementation task is allowed.
   - Output: `.cursor/session/rebuild/product-boundary-contract.md`.

2. `REQ-R0-002 Engineering Architecture Map`
   - Map each product domain to backend module, frontend shell/route group, database table group, permission boundary, todo/message/log side effect, and evidence requirement.
   - Keep the project organized by engineering architecture, not by scattered pages.
   - Identify where old module names still help and where they mislead.
   - Output: `.cursor/session/rebuild/engineering-architecture-map.md`.

3. `REQ-R0-003 Committee Review Gate`
   - Build the review method for product, architecture, backend, frontend, DBA, and test perspectives.
   - Define which issues require committee review.
   - Define how findings become requirement corrections, task corrections, or blocker records.
   - Confirm that normal requirement/prototype/task-breakdown work should catch these issues before coding.
   - Output: `.cursor/session/rebuild/committee-review-gate.md`.

4. `REQ-R0-004 Legacy Inventory And Cleanup Plan`
   - Inventory `.oldbk/`, `.cursor`, `docs`, prototype assets, old backend/frontend/sql, old scripts, old releases, screenshots, browser profiles, generated caches, and evidence archives.
   - Classify each group as retained source, reference implementation, evidence archive, generated/cache noise, deletion candidate, or unknown.
   - For deletion candidates, write reference value and impact before deleting anything.
   - Output: `.cursor/session/rebuild/legacy-inventory.md`.

5. `REQ-R0-005 Backend Codegen And Manage Contract`
   - Reconfirm the backend directory strategy from `.cursor/architecture/backend-structure.md`.
   - Define schema-first, generator-produced `base`, and handwritten `manage` workflow.
   - State which files can be generated, which files must be handwritten, and how acceptance checks prove the split.
   - Do not generate code in this task; only freeze the rule.
   - Output: `.cursor/session/rebuild/backend-codegen-manage-contract.md`.

6. `REQ-R0-006 Requirement-To-Task Breakdown`
   - Split future work by product domain and engineering module.
   - Each future coding task must include source requirement, affected backend/frontend/sql modules, generated base scope, manage logic scope, permissions, state transitions, readback, todo/message/log effects, and verification evidence.
   - No task may simply say "implement page" or "implement CRUD".
   - Output: `.cursor/session/rebuild/requirement-task-breakdown.md`.

7. `REQ-R0-007 User Review Package`
   - Produce a concise review package: product boundary, engineering map, legacy cleanup plan, codegen/manage workflow, UI system, and proposed first design/contract batches.
   - Keep coding gate closed until the package is accepted.
   - Output: `.cursor/session/rebuild/user-review-package.md`.

8. `REQ-R0-008 UI System And Interaction Contract`
   - Convert the user's reference screenshots and leader review into a reusable enterprise operations UI contract.
   - Freeze top navigation, dense list surfaces, right-side detail work areas, table/form states, and visual acceptance checks before coding.
   - Output: `.cursor/session/rebuild/ui-system-interaction-contract.md`.

## Current Completion Status

All `REQ-R0-001` through `REQ-R0-008` durable outputs exist and are leader-draft-complete.

The leader consistency audit exists at `.cursor/session/rebuild/leader-consistency-audit.md`.

This does not open coding. It means the requirements rebuild package is ready to serve as the basis for the next internal design preparation after the requirements gate is accepted.

## Coding Gate

Coding remains closed until `requirements_rebuild_accepted = true`.

Acceptance of each `REQ-R0-*` item requires:

- required output file exists;
- source files used are listed;
- open questions or conflicts are recorded;
- impact on future coding tasks is stated;
- no backend/frontend/sql implementation file is created or modified.

When coding opens later:

1. database/schema design is frozen for the task;
2. generator creates `base` code;
3. backend writes `manage` logic;
4. frontend binds to real APIs and state;
5. acceptance proves data persistence, permissions, states, readback, side effects, and browser behavior.

## Historical Queue

The previous queue (`REBUILD-P0-002` through `REBUILD-P0-008`) is not the active execution queue. It may be used only as history while rebuilding the new task breakdown.
