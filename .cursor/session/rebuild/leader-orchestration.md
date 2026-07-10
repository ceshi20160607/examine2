# Leader Orchestration

## Status

Mode: `requirements-rebuild`

Coding: closed

Leader responsibility: organize the relevant roles and keep pushing the requirements/engineering/UI framework work until the rebuild package is fit to enter clean implementation.

## Operating Rule

The user should not need to repeatedly ask "continue".

Leader/conductor must:

- maintain the active queue;
- assign work to role perspectives;
- collect durable outputs;
- resolve contradictions;
- update gates and status files;
- keep coding closed until the requirements rebuild package is complete.

## Role Workstreams

| Workstream | Role Perspective | Output |
|---|---|---|
| Product boundary | PM / product architect | `.cursor/session/rebuild/product-boundary-contract.md` |
| Engineering architecture | architect / backend / frontend | `.cursor/session/rebuild/engineering-architecture-map.md` |
| UI system | UIUX / frontend | `.cursor/session/rebuild/ui-system-interaction-contract.md`, `.cursor/architecture/ui-system.md` |
| Backend generation | backend / DBA | `.cursor/session/rebuild/backend-codegen-manage-contract.md` |
| Committee gate | test / PM / conductor | `.cursor/session/rebuild/committee-review-gate.md` |
| Legacy inventory | analyst / architect | `.cursor/session/rebuild/legacy-inventory.md` |
| Task breakdown | planner / PM | `.cursor/session/rebuild/requirement-task-breakdown.md` |
| User package | leader | `.cursor/session/rebuild/user-review-package.md` |
| Consistency audit | leader | `.cursor/session/rebuild/leader-consistency-audit.md` |

## Completion Definition For Requirements Rebuild

The package is not ready until:

- product domains are stable and no longer duplicated across files;
- engineering architecture maps every domain to modules, routes, data, permissions, states, side effects, and evidence;
- UI system contract blocks the old confusing layout pattern;
- backend codegen/manage workflow is explicit;
- legacy reference and cleanup decisions are documented;
- future tasks are cross-layer and role-journey based;
- leader consistency audit records pass/blocker/open-decision status;
- Gate `requirements_rebuild_accepted` can be considered only after the user-facing package is coherent.

## Current Next Step

Run the three-cycle governance loop: role review, durable-file integration, and verification. Do not ask the user to say "continue" between these cycles, and do not start backend/frontend/sql implementation.

## Three-Cycle Loop

| Cycle | Status | Output |
|---|---|---|
| Cycle 1: role review | complete | PM/governance, engineering, UI, and QA review results collected |
| Cycle 2: integration | complete | state, minutes, user package, task template, task breakdown, backend contract, encoding rules |
| Cycle 3: verification | complete | UTF-8 check, JSON parse, gate scan, no backend/frontend/sql changes, targeted re-review |

## Active Delegations

Historical role delegations from the R0 package are complete. Current three-cycle verification may use targeted follow-up review, but no role owns backend/frontend/sql implementation.

| Role | Agent | Owned Output |
|---|---|---|
| PM / product architecture | Euler | `.cursor/session/rebuild/product-boundary-contract.md` |
| Engineering architecture | Copernicus | `.cursor/session/rebuild/engineering-architecture-map.md` |
| UIUX / frontend experience | Volta | `.cursor/session/rebuild/ui-system-interaction-contract.md`, `.cursor/architecture/ui-system.md` |
| Backend / DBA | Jason | `.cursor/session/rebuild/backend-codegen-manage-contract.md` |
| Test / committee gate | Noether | `.cursor/session/rebuild/committee-review-gate.md` |
| Legacy inventory | Mencius | `.cursor/session/rebuild/legacy-inventory.md` |

## Delegation Status

| Role | Status | Notes |
|---|---|---|
| PM / product architecture | complete | product boundary expanded with context, domain results, engineering meaning, Application naming normalization |
| Engineering architecture | complete | product domains mapped to modules/routes/tables/permissions/states/side effects; recommends dedicated work/todo/message/AI modules |
| UIUX / frontend experience | complete | UI system contract expanded; old 19999 artifact recorded as anti-pattern; dense enterprise UI checklist added |
| Test / committee gate | complete | committee gate expanded with structural blockers, task-level exceptions, six-perspective checklist, output format |
| Backend / DBA | complete | codegen/manage contract expanded with schema-first flow, module matrix, base/manage evidence requirements |
| Legacy inventory | complete | `.oldbk` classified; browser profiles, dist, logs, and IDE files marked as cleanup candidates with impact and rollback plans |

## Integrated Role Conclusions

- Product domains are now treated as business outcomes first, then mapped to routes, modules, permissions, states, and evidence.
- Engineering architecture recommends dedicated modules for Work, Todo, Message, and AI instead of hiding them inside Flow or generic admin pages.
- UI direction is frozen as a dense enterprise operations system: dark compact top navigation, table-first list surfaces, and right-side tabbed detail work areas.
- Backend work must be schema-first, then generated `base`, then handwritten `manage`; controller/DTO/VO/permission/state/readback logic belongs in `manage`.
- Legacy code and old evidence are reference-only. Large historical browser profiles are cleanup candidates, not product evidence.
- Committee review is the route for structural contradictions; implementation tasks must not discover these basics late in coding.

## Integration Rule

Leader integrates only after checking:

- outputs are in the owned files;
- no role modified backend/frontend/sql implementation;
- no role reopens the old `REBUILD-P0-006` coding queue;
- contradictions are recorded in the user review package or the relevant contract file;
- `coding_allowed` remains false.
