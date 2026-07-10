# Gates

Gate state is recorded in `.cursor/session/state.json`.

## Current Front Gate: Requirements Rebuild

Since 2026-07-09, the project is in `requirements-rebuild` mode.

Until `requirements_rebuild_accepted = true`, conductor and all agents must refuse:

- continuing old `REBUILD-P0-*` coding queues;
- creating or modifying `backend/**`, `frontend/**`, or `sql/**`;
- entering API/Contract/Build/Verify workflows;
- treating historical evidence as current engineering completion.

`requirements_rebuild_accepted` requires these durable outputs:

- `.cursor/session/rebuild/product-boundary-contract.md`
- `.cursor/session/rebuild/engineering-architecture-map.md`
- `.cursor/session/rebuild/committee-review-gate.md`
- `.cursor/session/rebuild/legacy-inventory.md`
- `.cursor/session/rebuild/backend-codegen-manage-contract.md`
- `.cursor/session/rebuild/requirement-task-breakdown.md`
- `.cursor/session/rebuild/user-review-package.md`
- `.cursor/session/rebuild/ui-system-interaction-contract.md`

Current state: the outputs are leader-draft-complete, but `requirements_rebuild_accepted` remains false until accepted.

## Gate Definitions

| Gate | Signer | Condition | Blocks |
|---|---|---|---|
| `requirements_rebuild_accepted` | leader + user | `REQ-R0-001` through `REQ-R0-008` are coherent and accepted | Design, Contract, Build, all code |
| `prd_frozen` | PM | PRD includes MVP and no open P0 discovery issue | Design |
| `design_package_complete` | conductor | design package, IA, route map, prototypes, and reviews are internally complete | user design approval |
| `design_user_approved` | user only | `docs/design/user-approval.md` has `approved: true` | Contract, Build, all code |
| `api_frozen` | PM / architecture | API and schema contracts are versioned and review issues are closed | Build |
| `tasks_planned` | PM | implementation tasks include dependency graph and parallel groups | Build implementation |
| `build_batch_accepted` | task acceptance | current build batch has real acceptance evidence | Verify |
| `user_script_passed` | test + user | user trial script passes without P0 feedback | package/release |

## Design Gates

### Gate 1: Internal Design Complete

Before any user approval request:

```json
{ "gates": { "design_package_complete": true } }
```

The team completes role reviews, closes PM issues, and keeps IA/prototype decisions inside the project instead of asking the user to fill missing structure.

### Gate 2: User Design Approval

Before contract or build:

```json
{ "gates": { "design_user_approved": false } }
```

When false, conductor must refuse:

- creating or modifying `backend/**`;
- creating or modifying `frontend/**`;
- creating or modifying `sql/**`;
- entering contract/build workflow;
- creating implementation tasks.

Allowed work:

- discovery documents;
- requirements rebuild documents;
- `docs/design/**` design and prototype work;
- internal review iteration.

## User Approval File Format

`docs/design/user-approval.md`:

```markdown
# 设计确认

- approved: true | false
- approved_at: 2026-07-09
- approved_by: user
- scope: 自定义业务系统平台 MVP
- prototypes_reviewed:
  - docs/design/prototypes/platform-home.html
  - docs/design/prototypes/system-runtime.html
- notes: 修改意见或确认说明
```

Only when `approved: true` may conductor set `gates.design_user_approved = true`.

## Gate Change Record

Every gate change must be written to `session/events/*.jsonl`:

```json
{"ts":"...","actor":"conductor","action":"gate_pass","ref":"design_user_approved","detail":"user-approval.md approved:true"}
```
