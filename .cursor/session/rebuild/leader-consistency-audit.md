# Leader Consistency Audit

## Status

Status: leader draft complete

Audit time: 2026-07-09T17:54:39+08:00

Mode: `requirements-rebuild`

Coding: closed

## Scope

This audit checks whether the requirements rebuild package is coherent enough to become the basis for internal design and contract planning.

Reviewed outputs:

- `.cursor/session/rebuild/product-boundary-contract.md`
- `.cursor/session/rebuild/engineering-architecture-map.md`
- `.cursor/session/rebuild/ui-system-interaction-contract.md`
- `.cursor/session/rebuild/backend-codegen-manage-contract.md`
- `.cursor/session/rebuild/committee-review-gate.md`
- `.cursor/session/rebuild/legacy-inventory.md`
- `.cursor/session/rebuild/requirement-task-breakdown.md`
- `.cursor/session/rebuild/user-review-package.md`
- `.cursor/architecture/gates.md`
- `.cursor/architecture/ui-system.md`

## Passed Checks

| Check | Result |
|---|---|
| Product domains from the latest user explanation are represented | pass |
| Platform context and system context are separated | pass |
| Application is treated as authorization/service exposure valve, not module parent | pass |
| Flow owns flow definition/runtime, while external exposure goes through Application | pass |
| Business modules are separate from Application/OpenAPI | pass |
| Workbench, Work, Todo, Message, and AI are not hidden inside generic pages | pass |
| Dense enterprise UI direction is defined from reference screenshots | pass |
| Lists use dense table-first surfaces and details use right-side work areas | pass |
| Backend generation rule is schema-first, generated base, handwritten manage | pass |
| Old artifact `19999` is failure evidence only | pass |
| Legacy material is classified before deletion | pass |
| Future tasks must include product, UI, backend, DB, permission, state, readback, side-effect, and evidence fields | pass |
| JSON status files parse | pass |
| No backend/frontend/sql implementation files were modified by this audit | pass |

## Leader Decisions Applied

1. The old deployed page is not a target and must not be used as completion evidence.
2. The target UI is a dense enterprise operations system, close to the reference contract-management screenshots.
3. `Application` means service exposure and authorization gateway; it is not the parent of business modules.
4. `Business Module` owns module groups, fields, pages, actions, runtime records, permissions, import/export, attachments, print, and logs.
5. `Flow` owns flow definitions, canvas, nodes, publish checks, runtime instances, and approval source records.
6. `Work`, `Todo`, `Message`, and `AI` are product-level domains and should have explicit engineering ownership.
7. Recommended backend modules are `examine-work`, `examine-todo`, `examine-message`, and `examine-ai`, with proposed prefixes `un_work_*`, `un_todo_*`, `un_message_*`, and `un_ai_*`.
8. Todo and Message store current-user actionable/read models with source references; source truth stays in owner modules.
9. AI cannot bypass permissions and writes only through owner modules after confirmation and readback.
10. Committee review is required for structural contradictions, not for minor local task details.

## Remaining Coding Blockers

Coding remains blocked by design, contract, schema, and acceptance gates:

- `requirements_rebuild_accepted` is still false.
- No design package has been produced from the R0 contracts.
- No API/schema contract is frozen.
- No implementation task has a schema freeze, generator command, manage scope, evidence plan, and verifier.
- Open architecture decisions for Work/Todo/Message/AI modules, event/outbox strategy, Todo/Message source reference, Application scope model, AI policy, and dashboard ownership are not yet closed.
- Old implementation evidence remains reference-only.

These are normal blockers for this stage. They are not failures; they prevent premature coding.

## Leader Three-Cycle Governance

This process answers the user's directive: the leader organizes relevant roles, processes findings, makes durable decisions, supplements the project files, pulls the decisions again, and executes three internal cycles before reporting. If a cycle finds non-compliance, the leader supplements durable files and verifies again until the package is fit.

| Cycle | Purpose | Roles | Must Close Or Route | Durable Output |
|---|---|---|---|---|
| Cycle 1: boundary review | Confirm product, architecture, UI, backend, QA, encoding, and records boundaries. | PM, architecture, UI/FE, backend/DBA, QA/test, leader | Identify confirmed decisions versus open decisions. Confirm coding remains closed. | `project-minutes.md`, `leader-consistency-audit.md`, `user-review-package.md` |
| Cycle 2: integration | Apply confirmed decisions to durable files and assign open decisions to the next phase instead of chat memory. | leader with role outputs | Update state, gates, task template, task breakdown, and review package. Do not create scattered notes. | `state.json`, `current-status.json`, `task.md`, `requirement-task-breakdown.md`, related contracts |
| Cycle 3: verification | Re-read decisions after integration and run mechanical checks. | QA/test, leader, targeted roles as needed | Verify UTF-8, JSON parsing, no backend/frontend/sql changes, no old implementation reopening, no UI anti-pattern regression. | check output, final minutes row when a decision changes, final user-facing summary |

Leader must not ask the user to say "continue" between these cycles. User input is reserved for final acceptance, true product choices that cannot be inferred, or external facts the project files cannot decide.

## Open Decisions For Next Internal Phase

| Decision | Recommended Direction | Must Be Closed Before |
|---|---|---|
| Work/Todo/Message/AI module ownership | approve independent modules and prefixes | Cycle 1 of `CONTRACT-R1` for those domains |
| Todo/Message side effects | local event/outbox for cross-module reliability, direct writes only inside owner module | Cycle 2 contract pass before implementing Flow/Work/Module actions that create todos/messages |
| Todo/Message source reference | normalized `source_type`, `source_id`, context, route/action hint, permission recheck, source invalid behavior | Todo/Message API contract |
| Application scope model | context-scoped platform/system applications with masked secret refs, gateway authorization, and scope readback | Application API/schema contract |
| AI policy | provider/model strategy, `AgentPolicyScope`, desensitization, retention, tool-call permission, write confirmation, audit | AI API/schema contract |
| Dashboard ownership | config metadata in `examine-plat`, metric ownership in contributor modules | Workbench/design/API contract |
| Prototype/UI delta | declare what can be retained from old prototypes and what is rejected as `19999` anti-pattern | every `DESIGN-R1` and user-facing `BUILD-R*` task |
| Legacy reference extraction | summarize valuable `.oldbk/backend`, `.oldbk/frontend`, and `.oldbk/sql` patterns before cleanup or reuse | cleanup approval or implementation reuse |

## Next Internal Work Queue

1. `DESIGN-R1-001`: product IA and route map for platform workbench, platform admin, system runtime, and system admin.
2. `DESIGN-R1-002`: business module list/detail design contract using dense table + right detail.
3. `DESIGN-R1-003`: Flow/Application design contract including canvas, publish checks, service exposure, scopes, call logs, and right detail.
4. `DESIGN-R1-004`: Work/Todo/Message/AI design contract and state surfaces.
5. `DESIGN-R1-005`: Admin configuration design contract for system info, organization, roles, modules, flow, applications, dashboards, dictionaries, work config, app config, AI config, data sources, and logs.
6. `CONTRACT-R1` starts only after design package is internally coherent.

## Final Audit Result

The R0 requirements rebuild package is leader-draft-complete and coherent enough to continue into internal design preparation.

It is not ready for backend/frontend/sql coding.
