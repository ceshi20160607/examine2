# Committee Review Gate

## Status

Task: `REQ-R0-003 Committee Review Gate`

Status: leader draft complete

Mode: requirements-rebuild. Coding remains closed.

## Purpose

The committee gate prevents structural product and engineering problems from being discovered one by one during coding.

It is a pre-acceptance and escalation gate, not an implementation role. It decides whether a requirement, task plan, or acceptance contract is structurally safe enough for downstream task execution.

The gate must keep ordinary task-level issues inside task cards while escalating cross-role, cross-layer, or product-shaping problems to committee review.

## Inputs

- `.cursor/session/rebuild/committee-review-gate.md`
- `.cursor/architecture/acceptance.md`
- `.cursor/templates/task.md`
- `.cursor/session/rebuild/leader-ui-function-review.md`

Additional requirement, architecture, task, or evidence files may be reviewed only when they are explicitly named by the finding.

## Review Principles

1. Committee review is required when the decision changes product scope, role journeys, shell separation, data ownership, permission model, module ownership, acceptance model, or coding readiness.
2. Task-level handling is enough when the issue can be fixed inside one task card without changing product boundary, engineering contracts, shared data model, or acceptance rules.
3. Screenshots are visual evidence only. They cannot replace requirement truth, persistence proof, permission proof, or business completion proof.
4. Old deployed artifacts and old batch evidence are diagnostic or historical evidence only unless current acceptance evidence proves the rebuilt requirement.
5. Implementers may provide self-check evidence, but an independent verifier or committee decision is required for acceptance.
6. A task cannot start implementation until its requirement confirmation contract, generated-versus-coded boundary, evidence contract, V6 readiness lock, and applicable V7 human-usable gate are complete.

## Review Perspectives

| Perspective | Reviews |
|---|---|
| Product | product boundary, role journeys, user job, module ownership |
| Architecture | shell separation, context switch, module boundaries, cross-cutting contracts |
| Backend | module ownership, table groups, generated base scope, handwritten manage logic |
| Frontend | shell/routes, left-list/right-detail layout, state surfaces, no prototype fallback |
| DBA | schema ownership, table prefixes, indexes, data isolation, migration order |
| Test | acceptance path, permissions positive/negative, state/readback, side effects, browser evidence |

## Committee Review Required

Escalate to committee review when any issue below is present or suspected:

- product domain ambiguity, especially Flow/Application/business module confusion;
- platform/system context mixing;
- missing system member or tenant context;
- missing data ownership or table group;
- unclear permission model;
- missing todo/message/log side effects;
- generic drawer replacing a required product-specific state;
- task split by page/API instead of role journey and engineering module;
- coding task that cannot name generated base scope and manage scope;
- legacy material proposed for deletion without impact.
- disagreement between product, architecture, data, frontend, backend, or test evidence;
- task output would affect shared routes, shared API contracts, shared tables, permissions, shell separation, or acceptance rules;
- the task asks the user to decide ordinary engineering details because the requirement contract is incomplete;
- the old URL, old screenshots, generated CRUD, or success toast is being used as completion evidence;
- final goal, journey gate, or requirement ledger coverage would be over-claimed;
- a P0 issue blocks the named role from completing the business outcome;
- acceptance cannot start from the real role entry point or cannot prove readback, persistence, state, and permission where applicable;
- a user-facing route lacks a named UI system pattern or conflicts with the required enterprise operations visual direction;
- admin/runtime controls are mixed in one shell or a platform/system switch is ambiguous.

## Task-Level Handling Only

Handle the issue inside the relevant task card, review comment, or acceptance failure when it is local and does not change shared contracts:

- typo or copy polish;
- minor layout spacing;
- single-field wording correction;
- local doc formatting;
- task-local bug that does not change product boundary, data model, permission model, or acceptance model.
- missing task evidence path when the expected evidence type is already defined by the task template;
- a local validation message, empty state, disabled reason, or error copy that can be corrected without changing the state model;
- one task card missing a command, path, owner, or output entry that can be filled from existing requirement and architecture files;
- browser evidence that needs rerun because of environment timeout, when the acceptance target and proof method are already clear;
- small route label or table column naming fixes that do not change module ownership, user job, or permission meaning;
- local implementation defects discovered during verification, when the requirement contract is already complete and unchanged.

## Pre-Acceptance Requirements

Before a task can be accepted, the verifier must confirm:

- the task links to a final goal ledger or journey gate when it is part of a broad objective;
- the role, entry point, and business outcome are explicit;
- declared outputs exist and are non-empty;
- generated plumbing is separated from coded business behavior;
- frontend, backend, data, permission, state, readback, and side-effect evidence pass where applicable;
- browser/API evidence starts from the realistic role entry point;
- UI work names the framework UI pattern and proves the visual contract with browser evidence;
- screenshots are treated as visual evidence only;
- permission positive and negative cases are covered where applicable;
- persistence is proven by reload, re-login, restart, API readback, or data readback where applicable;
- no new open P0 issue points to the task;
- implementer and verifier are not the same actor.

## Perspective Checklists

### Product

- [ ] The target role is named and matches the business journey.
- [ ] The entry point starts from login, system switch, route, menu, or action that a real user can reach.
- [ ] The user job is a business outcome, not a page, API, file, or generated entity.
- [ ] Product domains are not confused, especially Flow, Application, Work, Todo, Message, AI, Admin, and business modules.
- [ ] Platform workbench, platform admin, system runtime, and system admin are separated when relevant.
- [ ] Required side effects such as todo, message, log, activity, attachment, print, or operation records are named where relevant.
- [ ] Prototype or old artifact evidence is treated as reference or diagnostic evidence, not as current completion.
- [ ] User signoff boundary is explicit when subjective usability or final goal acceptance is involved.

### Architecture

- [ ] Shell separation and context switch rules are explicit.
- [ ] Module boundaries match role journeys and engineering ownership.
- [ ] Shared contracts are named for routes, API payloads, permissions, state, readback, and side effects.
- [ ] Generated base scope and handwritten manage scope are separated.
- [ ] Cross-cutting contracts do not mix platform/system runtime/admin concerns.
- [ ] UI system and interaction contracts are referenced for user-facing work.
- [ ] A task is not split only by page or API when the real risk is journey or module integration.
- [ ] Acceptance level is clear: task, batch, journey, or final goal.

### Backend

- [ ] Backend module ownership is named.
- [ ] Generated plumbing is excluded from completion unless coded business behavior uses it.
- [ ] API contracts include request, response, error, permission, and readback expectations.
- [ ] Business actions include state change, persistence, and readback.
- [ ] Permission checks cover allowed and forbidden cases.
- [ ] Todo, message, log, audit, or activity effects are included where relevant.
- [ ] Backend behavior does not rely on frontend-only success toasts.
- [ ] Recovery, retry, validation, and backend error states are defined when applicable.

### Frontend

- [ ] The route shell is named and does not mix platform admin, system admin, or runtime controls.
- [ ] User-facing work names its UI system pattern.
- [ ] Dense list, saved views, filters, column settings, row click, and distinct action buttons are specified where relevant.
- [ ] Detail surfaces preserve list context through a right-side work area or equivalent approved pattern.
- [ ] The detail surface is product-specific and tabbed where the business object requires related records.
- [ ] Empty, loading, disabled, validation, backend error, async, success/failure, and retry states are specified where relevant.
- [ ] Desktop and narrow/mobile layout checks are planned when the route is user-facing.
- [ ] Visual evidence checks hierarchy, clipping, overflow, visible copy, and no overlapping text.

### DBA

- [ ] Data ownership and table group are named.
- [ ] Table prefixes and module ownership match the architecture map.
- [ ] Tenant, system member, organization, project, or other isolation keys are defined where relevant.
- [ ] Indexes support the declared list filters, lookups, joins, and readback paths.
- [ ] Migration order avoids breaking generated base tables, handwritten tables, and seed/reference data.
- [ ] Persistence/readback evidence can identify the exact records that changed.
- [ ] Deletion, cleanup, retention, and test-data policy are named where relevant.
- [ ] Data model changes do not over-claim requirement ledger coverage.

### Test

- [ ] Evidence starts from the realistic role entry point.
- [ ] The acceptance level is named and does not treat batch acceptance as final-goal acceptance.
- [ ] Positive and negative permission cases are covered where applicable.
- [ ] State/readback/persistence checks prove the visible result after action.
- [ ] Browser evidence is paired with API or data evidence when persistence or permission matters.
- [ ] Screenshots are used only for visual hierarchy, clipping, overflow, copy, and role-specific surface checks.
- [ ] Independent verifier is named and is not the implementer.
- [ ] Failure handling routes the gap to design, contract, build, verify, or user decision according to the issue.

## Review Output

Every committee review must write the following format:

```markdown
# Committee Review: <short title>

- review_id:
- reviewed_at:
- status: draft | final
- coding_status: closed | open-for-named-tasks
- acceptance_level_affected: task | batch | journey | final-goal | none
- source_files_reviewed:
  - path:
- trigger:
- perspectives:
  - product:
  - architecture:
  - backend:
  - frontend:
  - dba:
  - test:

## Findings

| ID | Severity | Perspective | Finding | Evidence | Required action | Owner | Affected tasks |
|---|---|---|---|---|---|---|---|
| CR-001 | P0/P1/P2 | product/architecture/backend/frontend/dba/test |  |  |  |  |  |

## Decision

- verdict: blocker | requirement-correction | task-correction | no-action
- coding remains closed: yes/no
- acceptance remains blocked: yes/no
- requirement files to update:
- task cards to update:
- evidence required before unblock:
- user decision required: yes/no + reason
```

The review may add a short summary, but the structured fields above are required.

## Blocking Conditions

Coding and acceptance remain blocked when any condition below is true:

- any open P0 committee finding exists;
- the role, entry point, or business outcome is missing or not realistic;
- product domain, module ownership, shell separation, or platform/system context is ambiguous;
- data ownership, table group, tenant/system-member isolation, or permission model is missing;
- generated plumbing and coded business behavior are not separated;
- task evidence would prove only a page, API, component, generated CRUD, screenshot, or toast instead of a role-facing business outcome;
- permission positive/negative evidence, persistence/readback evidence, or required state evidence is missing;
- user-facing work lacks a UI system pattern or contradicts the required dense enterprise operations visual direction;
- admin/runtime controls are mixed in a way that changes the user's journey or authority boundary;
- old artifact evidence is proposed as current acceptance evidence;
- implementer and verifier are the same actor;
- requirement ledger, journey gate, or final goal status would be updated beyond what evidence proves.

Committee review can unblock coding only when all P0 findings are closed, all required requirement/task corrections are written to project files, and the remaining issues are explicitly downgraded to task-level handling with owners and evidence requirements.

## Decision Types

| Decision | Meaning | Follow-up |
|---|---|---|
| blocker | Structural gap prevents coding or acceptance. | Keep coding closed; update requirement, architecture, task plan, or acceptance contract first. |
| requirement-correction | Requirement or UI/interaction contract must change before task execution. | Update requirement rebuild artifacts and re-run relevant readiness checks. |
| task-correction | The issue is real but local to one or more task cards. | Update task contract, evidence plan, or acceptance assertions before implementation. |
| no-action | No committee-level change is required. | Continue with existing gate status and task-level acceptance rules. |

## Current Decision

Coding remains closed. Requirements rebuild artifacts, UI system and interaction contracts, task cards, and acceptance preconditions must be completed and reviewed before implementation.
