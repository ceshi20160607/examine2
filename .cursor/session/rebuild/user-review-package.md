# User Review Package

## Status

Task: `REQ-R0-007 User Review Package`

Status: leader draft complete

This package is the concise handoff for user review after requirements rebuild artifacts are filled and checked.

## Current Summary

The project is paused from coding and has been reorganized around requirements, engineering architecture, UI system, legacy inventory, and future task gates.

Current active queue:

- `REQ-R0-001 Product Boundary Contract`
- `REQ-R0-002 Engineering Architecture Map`
- `REQ-R0-003 Committee Review Gate`
- `REQ-R0-004 Legacy Inventory And Cleanup Plan`
- `REQ-R0-005 Backend Codegen And Manage Contract`
- `REQ-R0-006 Requirement-To-Task Breakdown`
- `REQ-R0-007 User Review Package`
- `REQ-R0-008 UI System And Interaction Contract`

Leader orchestration entry:

- `.cursor/session/rebuild/leader-orchestration.md`
- `.cursor/session/rebuild/leader-consistency-audit.md`

## Review Checklist

| Check | Status | Evidence |
|---|---|---|
| Product boundary matches the latest user explanation | draft complete | `.cursor/session/rebuild/product-boundary-contract.md` |
| Engineering modules map to product domains | draft complete | `.cursor/session/rebuild/engineering-architecture-map.md` |
| Legacy files are classified before cleanup | draft complete | `.cursor/session/rebuild/legacy-inventory.md` |
| Committee gate prevents structural issues from leaking into coding | draft complete | `.cursor/session/rebuild/committee-review-gate.md` |
| Backend coding rule is generator base first, handwritten manage second | draft complete | `.cursor/session/rebuild/backend-codegen-manage-contract.md` |
| Future tasks are cross-layer and role-journey based | draft complete | `.cursor/session/rebuild/requirement-task-breakdown.md` |
| UI system uses dense enterprise operations style | draft complete | `.cursor/session/rebuild/ui-system-interaction-contract.md`, `.cursor/architecture/ui-system.md` |
| Leader consistency audit records pass/blocker/open-decision status | draft complete | `.cursor/session/rebuild/leader-consistency-audit.md` |
| Coding remains closed until package accepted | pass | `.cursor/session/state.json` |

## Leader Operating Mode

The user should not need to repeatedly ask the leader to continue.

For this stage, the leader continues internal cycles without asking for ordinary continuation:

1. organize role review;
2. integrate confirmed decisions into durable files;
3. pull the decisions again through verification;
4. execute three internal cycles before reporting a package; if any cycle finds non-compliance, supplement durable files and verify again until the package is fit.

User involvement is reserved for final acceptance or true product choices that cannot be inferred from existing requirements, prototypes, and contracts.

## Leader Integrated Conclusions

- The old `19999` page is failure evidence only: it is reachable, but its layout and function ownership are not accepted as target implementation.
- The correct UI direction is the reference CRM/ERP style: compact dark top navigation, dense table list, right-side detail work area, and clear object actions.
- Workbench, Flow, Application, Work, AI, Todo, Message, Admin, Personal Info, Business Modules, and Flow Management now have explicit product/engineering boundaries.
- Application is the service exposure and authorization valve, not the parent container of business modules.
- Work, Todo, Message, and AI should be independent product and engineering domains with their own module/table boundaries.
- `.oldbk` is reference-only. The largest historical bulk is generated browser profile/evidence data, not source material.
- Future coding tasks must name product domain, role journey, backend module, frontend shell, UI pattern, table group, generated base scope, handwritten manage scope, permissions, states, readback, side effects, and evidence.
- UI style is contractized and gated, but not yet implemented as frontend components, route shells, or runnable pages. Direction acceptance does not mean implementation completion.
- Old prototypes and `.oldbk/frontend` are reference-only; any reuse requires a UI-system delta review first.

## Leader Review Of Old Artifact

The old artifact at `http://192.168.0.211:19999/` is treated as a failed historical reference, not as the target product.

Current finding:

- It is reachable through Nginx and serves a static Vite entry.
- Browser rendering from the current environment timed out.
- User screenshots/report show confusing layout and unclear function ownership.
- It must not be used as completion evidence.

## Current Decision

Not ready for coding. The `REQ-R0` artifacts are leader-draft-complete and must stay as the active basis for the next design/contract work. The leader continues internal three-cycle review/integration/verification without asking for ordinary continuation. `requirements_rebuild_accepted` remains false until final package acceptance; backend/frontend/sql implementation remains closed.
