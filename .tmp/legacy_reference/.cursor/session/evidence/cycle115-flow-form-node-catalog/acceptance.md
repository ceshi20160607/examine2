# Cycle115 FL115-B Flow form and node-catalog acceptance

- scope: `P5_FLOW_FORM_NODE_CATALOG_GAP`
- frozen requirements: `REQ-FLOW-001/002/003`, `docs/user_requirement.md` §5.12
- gap source: `.cursor/session/evidence/release-113/phases/p5/gap.md`
- implementation verdict: `PASS`
- Flow module verification: `408_TESTS_PASS`
- Work owner verification: `98_TESTS_PASS`
- unified Web/MySQL verification: `PASS`

## Outcome

This cycle adds a versioned Flow-extension graph to the existing definition,
publication and instance model. It does not create a second publication path.
The existing `FlowMutationService` now automatically invokes extension impact
preflight during draft check and publishes the extension snapshot immediately
after the existing definition version in the same transactional publication
method. Runtime reads always use the instance's exact
`definitionId + definitionVersion`; they never read the latest draft or latest
published graph.

The gap is closed for immutable node field policy, approval-time business-form
read/edit/writeback/history, dependency impact facts and durable execution state
for the complete frozen node catalog. COPY, notification/message, task,
Webhook, external task, subflow and AI confirmation/materialization now invoke
their existing authoritative owner paths. Webhook/external/subflow are mapped
into the already durable completion protocol instead of creating a duplicate
worker stack. Automation dispatch and upstream AI inference remain explicit
boundaries listed below; no `requested=true` field is treated as external
success.

## Existing publication-chain integration

1. Draft extension writes require the current existing definition draft
   revision and store a SHA-256 checksum.
2. Existing draft check calls `FlowExtensionService.preflight(...)` from the
   normal `FlowMutationService.check(...)` path. Stale graph revisions and
   incompatible dependencies are publication blockers; inbound cross-application
   consumers are warnings.
3. Existing `FlowMutationService.publish(...)` remains the only publication
   command. After `workflow.publish(definitionId)`, it calls
   `FlowExtensionService.published(...)`; the immutable extension version is
   keyed by the exact version returned by the existing publisher.
4. `FlowExtensionService` is non-final and its production constructor is
   explicitly `@Autowired`, so Spring's transactional CGLIB proxy can be created
   even though a package-private clock constructor exists for deterministic tests.
5. Runtime form and node execution resolve only
   `repository.published(systemId, tenantId, instance.definitionId(),
   instance.definitionVersion())`.
6. Saving an extension graph synchronizes its Webhook, subflow and external
   nodes into reserved `ext_*` completion steps on the same existing definition
   draft. Publication therefore freezes those steps with the exact parent
   version. Manual execute/resume rejects these lifecycle-owned nodes, ensuring
   one side-effect chain only.

Primary code:

- `backend/examine-flow/src/main/java/com/unique/examine/flow/service/FlowMutationService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/extension/FlowExtensionService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/extension/JdbcFlowExtensionRepository.java`

## Business-form and field-policy closure

- Four immutable modes are supported: `VISIBLE`, `EDITABLE`, `REQUIRED`,
  `HIDDEN`.
- Policies are validated in the draft graph, copied into the published graph,
  then materialized once per `instanceId + nodeCode` with the initial form JSON.
- Form access requires the exact current approver, an instance record binding,
  a form-capable node, an exact published definition version and a matching
  business module.
- Reads intersect the node policy with the module runtime field capability.
  Hidden/unreadable fields are omitted; editable is true only when both layers
  permit it.
- Writes reject fields not editable in either layer, enforce required values,
  require both expected record version and expected snapshot version, advance
  the snapshot with compare-and-set, call the real module record runtime, and
  persist before/changes/after history with actor and record versions.
- The Web adapter preserves account/system/tenant/member identity and calls
  `RecordRuntimeService.detail/schema/update/create`; it does not bypass module
  authorization or field capability checks.
- Explicit negative coverage exists for non-approver access, hidden writes,
  module-readonly field writes, missing required values, stale record versions
  and stale form-snapshot versions.

Primary code:

- `backend/examine-flow/src/main/java/com/unique/examine/flow/extension/FlowBusinessFormPort.java`
- `backend/examine-web/src/main/java/com/unique/examine/web/flow/RuntimeFlowBusinessFormAdapter.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/api/FlowExtensionController.java`

## Publication dependency and cross-application impact

The graph declares typed dependencies with `EXACT` or `MINIMUM` version rules:

| dependency type | live resolver | dependent nodes |
|---|---|---|
| `FLOW_DEFINITION` | published Flow definition version | subflow |
| `MODULE_CONFIGURATION` | active module configuration version | optional record/config consumers |
| `OPENAPI_APPLICATION` | active OpenAPI application/version | webhook |
| `MESSAGE_TEMPLATE` | published enabled message-template version | notification, message |
| `AI_POLICY` | active enabled AI policy version | AI assist |

Outbound missing/inactive/version-incompatible dependencies block publication.
Owner-executed Flow-definition, message-template and AI-policy dependencies must
resolve inside the Flow's own system and tenant; a cross-tenant declaration is
rejected instead of checking one owner and executing another. OpenAPI impact
facts may remain cross-application because the frozen Webhook step owns its safe
HTTPS endpoint and secret reference.
Inbound analysis scans the latest immutable extension versions and reports the
published system, tenant, definition version, application and node that consume
the current Flow. Cross-application consumers are visible warnings rather than
silent dependencies.

## Frozen node catalog coverage

Every frozen type has a non-empty configuration contract, deterministic
execution transition and durable execution/event ledger. Initial execution is
CAS-created; resume requires the expected execution version and waiting status.
Every externally callable execute/resume operation requires the exact current
approver.

| frozen type | validated executable behavior | durable result / real effect |
|---|---|---|
| `START` | start node identity and outgoing edge | `CONTINUED`, started event |
| `APPROVAL` | approval mode, business module, optional field policies | `WAITING_HUMAN`; real version-fixed form read/write |
| `CONDITIONAL_APPROVAL` | non-empty condition, business module, field policies | `WAITING_HUMAN`; real version-fixed form read/write |
| `COPY` | positive active-member recipients | real idempotent Flow copy plus Event member message; stable per-recipient effect key |
| `CONDITIONAL_BRANCH` | at least two routes and caller-selected route | `CONTINUED`, selected route ledger |
| `PARALLEL_GATEWAY` | at least two configured routes | `CONTINUED`, activated route set ledger |
| `INCLUSIVE_GATEWAY` | at least two routes and non-empty selected routes | `CONTINUED`, activated route set ledger |
| `MERGE_GATEWAY` | reachable merge node | `CONTINUED`, completed-route join fact |
| `SUBFLOW` | positive local child definition and exact Flow dependency | mapped to the existing durable subflow launch/reconcile chain with exact child version and duplicate-launch protection |
| `AUTOMATION` | named operation | `CONTINUED`, operation request; operation-dispatch adapter boundary |
| `FORM` | form action, business module and non-empty field policies | `WAITING_HUMAN`; real form snapshot/edit/writeback/history |
| `TASK` | `WORK_TASK`, active assignee, title and optional project/description/due time | real idempotent Work owner task creation; `WAITING_HUMAN` until Work reports `COMPLETED`; visible through existing Work Todo adapter |
| `NOTIFICATION` | template code, positive recipients and compatible local message-template dependency | real versioned Event template delivery and delivery-log/message receipt with stable dedupe key |
| `FIELD_UPDATE` | bound business module and non-empty values | real `RecordRuntimeService.update` through the port, with record-version CAS |
| `DATA_CREATE_UPDATE` | `CREATE`/`UPDATE`, target module and values | real `RecordRuntimeService.create/update` through the port |
| `WAIT` | event key | `WAITING_EVENT`; explicit versioned resume |
| `TIMER` | absolute resume time or positive delay | `WAITING_TIMER`; early resume is rejected against the snapshot deadline |
| `MESSAGE` | template code, positive recipients and compatible local message-template dependency | real versioned Event template delivery and delivery-log/message receipt with stable dedupe key |
| `WEBHOOK` | safe HTTPS URL, timeout/retry/backoff and active OpenAPI application dependency | mapped to existing durable Webhook completion worker with lease, retry, audit and exact parent version |
| `EXTERNAL` | topic, lease, attempts and bounded result size | mapped to existing external-task completion claim/complete/fail worker; no manual duplicate execution |
| `AI_ASSIST` | policy code, target field, active local AI policy dependency, mandatory human confirmation | exact source snapshot and opaque confirmation are sealed by `AiFieldFillFacade`; resume performs the real idempotent module record materialization/history write |
| `END` | no outgoing edge | `COMPLETED`, terminal event |

Graph validation additionally requires exactly one start, at least one end,
valid outgoing edges, full reachability, unique node codes, unique field policies,
unique dependencies and a compatible dependency for every dependency-bearing
node. This prevents enabling an empty placeholder node.

## Persistence and migration

Migration: `sql/migration/V8_87_0__flow_form_node_catalog.sql`

| table | purpose |
|---|---|
| `un_flow_definition_extension_draft` | revision-bound mutable extension graph |
| `un_flow_definition_extension_version` | immutable graph/checksum keyed by existing definition version |
| `un_flow_instance_form_snapshot` | first-open policies and initial business form, with snapshot CAS version |
| `un_flow_form_write_history` | append-only actor/before/change/after record history |
| `un_flow_node_execution` | current node execution state and optimistic version |
| `un_flow_node_execution_event` | append-only execute/resume transition history |

All primary/foreign keys carry system and tenant scope. Runtime rows restrict
deletion, JSON shapes are checked, and version/history monotonicity is enforced.
The H2/MySQL-mode JDBC journey proves draft save, immutable version publication,
first-writer snapshot materialization, snapshot CAS, form history, execution CAS
and ordered transition history using actual SQL rather than an in-memory matrix.

## API surface

Under `/api/v1/systems/{systemId}/flow`:

- `GET /node-catalog`
- `PUT /definitions/{definitionId}/extension-draft`
- `GET /definitions/{definitionId}/publish-impact`
- `GET|PUT /instances/{instanceId}/nodes/{nodeCode}/form`
- `GET /instances/{instanceId}/nodes/{nodeCode}/form/history`
- `POST /instances/{instanceId}/nodes/{nodeCode}:execute`
- `POST /instances/{instanceId}/nodes/{nodeCode}:resume`
- `GET /instances/{instanceId}/nodes/{nodeCode}/execution-history`

The controller reuses existing Flow permissions and requires idempotency keys for
business-record form, initial execute and resume operations; resume also uses
execution-version CAS. Domain services add the instance/current-
approver and field-level authorization boundaries.

## Real owner effects, idempotency and failure behavior

- COPY calls the existing `FlowInteractionMutationService.copy` owner. The
  derived effect key includes instance id, exact definition version, node code
  and recipient, so changed client keys cannot duplicate the copy or its Event
  message.
- Notification and message nodes call `ResultNotificationFacade`; each recipient
  receives a durable Event delivery receipt and the same version-pinned effect
  key is the Event dedupe key. Event's existing retry worker owns temporary
  delivery failures.
- TASK calls the new narrow core `WorkTaskCreationFacade`, implemented by Work's
  `WorkTaskCreationAdapter`. Work validates `work.task.create`, tenant scope,
  active assignee and optional project membership, stores one task through the
  existing service, completes the shared idempotency fact, writes operation
  audit and enqueues `WORK_TASK_CREATED` in the caller transaction. Replay
  returns the same task; mismatched payload is rejected. Flow resume re-reads
  Work state and cannot continue until Todo completion changes it to
  `COMPLETED`.
- Webhook, external and subflow nodes are not run by the extension execution
  ledger. `FlowExtensionCompletionMapper` creates exactly one existing
  `ApprovalCompletionStep` per node; existing completion services provide
  lease ownership, attempt history, retry/backoff, compensation and terminal
  audit. Subflow dependency version is `EXACT` and frozen in the step.
- AI assist reads an authorized record source snapshot, prepares an opaque
  owner-sealed field-fill command, and only resume with explicit confirmation
  executes it. The stable execute key gives the module owner exactly-once
  materialization even after a transport retry.
- Successful extension effects write operation audit in the mutation
  transaction. Failed attempts use the existing independent failure-audit
  boundary. Extension execution CAS is persisted only after the owner effect;
  a CAS rollback also rolls back transactional owner success facts.

## Verification

Commands were run with JDK 21 and without `clean`:

1. `mvn.cmd -q -f backend/pom.xml -pl examine-flow -am -DskipTests compile`
   - result: `PASS`
2. Focused extension tests
   - result: `9 tests, 0 failures, 0 errors, 0 skipped`
3. `mvn.cmd -q -f backend/pom.xml -pl examine-flow -am test`
   - examine-flow result: `408 tests, 0 failures, 0 errors, 0 skipped`
4. `mvn.cmd -q -f backend/pom.xml -pl examine-work -am test`
   - examine-work result: `98 tests, 0 failures, 0 errors, 0 skipped`
5. Unified Web/MySQL regression and package gates
   - Flow API journey and Web application integration: `PASS`
   - full backend discovery: `1,772 tests`; three stale cross-module expectations were corrected and focused-rerun to `PASS`
   - packaged cold start: 110/110 migrations, health `UP`, login `OK`, frontend HTTP 200

New focused coverage:

- `FlowNodeCatalogExecutionTest`: all 22 frozen types, full-catalog graph,
  timer/external/AI unsafe-resume negatives.
- `FlowExtensionServiceTest`: Spring constructor context, exact instance version,
  current-approver negative, dual field authorization, required/hidden behavior,
  record/snapshot conflicts, write history, real-effect port call and dependency
  plus inbound impact report.
- `JdbcFlowExtensionRepositoryJourneyTest`: real SQL persistence, immutable
  published version, first-writer snapshot, CAS conflicts and ordered histories.
- `FlowFormNodeCatalogMigrationContractTest`: scoped restrictive schema contract.
- `FlowNodeEffectServiceTest` (added after the recorded 404-test run): stable
  cross-client COPY/Event/Work/AI effect keys, owner replay, task completion
  gate and failed-effect audit.
- `WorkTaskCreationAdapterTest`: root-owned focused Maven run `PASS`; covers
  permission, active-member/tenant scope, idempotency conflict/replay, audit,
  outbox and owner state readback.
- `WorkTodoAdapterTest` now creates its source through
  `WorkTaskCreationFacade` and proves the resulting Work task is read and
  completed through the existing Todo chain.
- `FlowExtensionServiceTest` now proves lifecycle graph synchronization,
  immutable exact subflow version, one completion execution per mapped node and
  rejection of manual duplicate lifecycle execution.

## Deliberate owner boundaries

1. Automation operation dispatch remains an explicit adapter boundary. AI
   inference remains upstream of AI Assist: this node accepts a governed result
   plus provenance, then owns confirmation and real record materialization; it
   does not itself select or call a model. COPY, Event delivery, Work task,
   Webhook, external task, subflow and AI record-write boundaries are connected.
2. This cycle intentionally does not perform unified front-end UI or responsive
   work. The requested priority is executable backend functionality and stable
   engineering framework first.
