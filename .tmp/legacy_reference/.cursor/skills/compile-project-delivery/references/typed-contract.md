# Typed delivery contract

Author one UTF-8 JSON object and validate it with `scripts/validate_delivery_plan.py`.

## Root

Required keys:

| key | contract |
|---|---|
| `schemaVersion` | Integer `1`. |
| `projectId` | Stable non-empty ID. |
| `source` | Exactly one `{id,path,sha256}`; SHA-256 is lowercase 64-hex. |
| `decisions` | Decision records. |
| `artifacts` | Frozen contracts consumed by tasks. |
| `qualityGates` | Functional activation gates; optional performance work lives outside this core plan. |
| `phases` | Ordered dependency graph of project results; future phases may remain outline-only. |
| `requirements` | Atomic source-backed requirements. |
| `acceptanceCases` | Given/When/Then cases. |
| `tasks` | Implementation and verification DAG. |
| `cycles` | Four-hour delivery cycles. |

Extra keys are rejected so contract drift is visible.

## Decisions and artifacts

```json
{
  "id": "DEC-001",
  "question": "Which detail presentation is authoritative?",
  "status": "resolved",
  "selected": "right_drawer_tabs",
  "authority": "source",
  "evidence": "docs/requirements.md:L120-L126"
}
```

Decision status is `unresolved|resolved|rejected`. Only a resolved decision may be consumed by an accepted requirement. `selected`, `authority`, and `evidence` are mandatory when resolved and must be null otherwise.

An artifact is `{id,status,sha256,path}`. Status is `draft|accepted|superseded`; tasks may consume only accepted artifacts. The validator reads every artifact path under the declared source root and rejects a stale SHA, so an Agent cannot silently implement against a changed contract.

## Atomic requirement

```json
{
  "id": "REQ-CUSTOMER-CREATE",
  "phaseId": "PHASE-CRM-CORE",
  "sourceRefs": [{"locator": "L40-L47", "quote": "销售可以新建客户"}],
  "actor": "sales_member",
  "trigger": "opens the customer creation form",
  "action": {"verb": "create", "object": "customer"},
  "outcome": "the customer appears in the customer list after reload",
  "placement": {
    "surface": "page_header",
    "location": "customer list title row",
    "defaultVisible": true
  },
  "constraints": ["customer name is required"],
  "exclusions": ["does not import customers"],
  "status": "accepted",
  "decisionIds": [],
  "dependsOnRequirementIds": [],
  "acceptanceCaseIds": ["CASE-CUSTOMER-CREATE"],
  "taskIds": ["TASK-CUSTOMER-API", "TASK-CUSTOMER-UI"]
}
```

Status is `candidate|accepted|blocked|rejected`. Only `accepted` requirements in a `detailed` phase may reach cases or tasks. `placement` freezes where the capability belongs and whether it is visible by default; it prevents every capability from becoming a navigation item or permanent button. Keep one action object rather than an action list. Split different actors, triggers, objects, state transitions, or outcomes into separate requirements. Every accepted requirement requires a source reference, explicit exclusion, required positive business case, and task. The inverse links must match exactly.

## Acceptance case

Required fields are `id`, `kind`, `status`, `required`, `expectedBusinessOutcome`, `requirementIds`, `given`, `when`, `then`, and `taskIds`.

- `kind`: `journey|success|permission|validation|failure|recovery`.
- `status`: `candidate|accepted|blocked|rejected`.
- `required`: whether this case is mandatory for its requirement to be accepted.
- `expectedBusinessOutcome`: `achieved|denied|recovered`. A positive journey must be `achieved`; a permission case must be `denied`.
- `given` and `then`: non-empty arrays of concrete conditions/assertions.
- `when`: one concrete action.

Only accepted cases may reach tasks or cycle demos. A cycle demo case must be `journey` and must reference at least one task in that cycle.

## Task

Required fields:

```json
{
  "id": "TASK-CUSTOMER-API",
  "phaseId": "PHASE-CRM-CORE",
  "result": "Create and read back a customer through the authenticated API",
  "requirementIds": ["REQ-CUSTOMER-CREATE"],
  "acceptanceCaseIds": ["CASE-CUSTOMER-CREATE"],
  "inputArtifactIds": ["ART-CUSTOMER-API"],
  "dependsOnTaskIds": [],
  "estimateMinutes": 90,
  "moduleScope": "backend/customer",
  "writeScope": ["backend/customer", "db:customer"],
  "resourceScope": ["db:customer", "test:mysql-customer"],
  "scheduling": "ready_parallel",
  "serialReason": null,
  "allowedChanges": ["customer create and read API"],
  "forbiddenChanges": ["reports and operations navigation"],
  "baseBindings": [
    {"assetId": "base.crud", "mode": "extend", "reason": "reuse generated CRUD and add customer rules"}
  ],
  "newSemanticsAllowed": false,
  "unresolvedDecisionIds": [],
  "qualityStage": "functional",
  "verificationKinds": ["unit", "api", "data"],
  "acceptanceCommand": "mvn -pl customer test",
  "status": "planned",
  "cycleId": "CYCLE-CRM-CORE",
  "activationGateId": null
}
```

Task status is `planned|ready|blocked|deferred|passed`. `qualityStage` is `functional|hardening`; verification kinds are `build|unit|api|data|permission|ui_smoke|integration|package|security|a11y`. Core delivery tasks cannot request performance/capacity checks.

Use `ready_parallel` by default. `forced_serial` requires a non-empty reason and a real dependency or overlapping normalized file/resource scope. Dependencies must form a DAG. `baseBindings` must explicitly say `reuse|configure|extend|override|project_only`; an override needs a stated reason. `newSemanticsAllowed` is always false and `unresolvedDecisionIds` is empty, so an implementation Agent cannot invent product behavior. A task may consume only accepted artifacts, requirements, and cases. Every task belongs to exactly one core delivery cycle.

## Phase and cycle

A phase is `{id,sequence,planningStatus,outcome,dependsOnPhaseIds,requirementIds,taskIds,cycleIds}`. `planningStatus` is `outline|detailed`. Outline phases freeze only dependency, outcome, and candidate requirements; they must not contain tasks or cycles. Detailed phases contain accepted requirements, cases, tasks, and cycles. This keeps the whole-project route visible without pretending distant details are settled. Links must exactly match inverse ownership, and the dependency graph must be acyclic and sequence-consistent.

A cycle is:

```json
{
  "id": "CYCLE-CRM-CORE",
  "phaseId": "PHASE-CRM-CORE",
  "sequence": 2,
  "timeboxMinutes": 240,
  "outcome": "a sales member creates and reloads a customer",
  "demo": {
    "entryPoint": "/crm/customers",
    "acceptanceCaseId": "CASE-CUSTOMER-CREATE"
  },
  "taskIds": ["TASK-CUSTOMER-API", "TASK-CUSTOMER-UI"]
}
```

Each cycle has one `outcome` string and one `demo` object. Its timebox and calculated task-DAG critical path must be at most 240 minutes. Dependencies must point to the same or an earlier cycle. Ready tasks with no dependency path and disjoint write scopes must remain `ready_parallel`.

## Performance boundary

Do not include performance, capacity, million-record, or long-concurrency tasks in this core delivery contract. After the complete project passes functional acceptance, an explicitly authorized project may create a separate optional performance plan with `verify-by-delivery-level`. Do not keep speculative performance tasks in the core backlog.
