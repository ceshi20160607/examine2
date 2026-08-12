# FAST-FLOW-ORG-LEADER-MANAGER-APPROVERS-47 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-30T21:19:53+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual hardening remain deferred.

## Delivered

- Added durable, tenant-scoped direct-manager and department-leader
  assignments with one active relationship, optimistic parent/relationship
  versions, creator/clearer audit and retained cleared history.
- Enforced active same-tenant members, no self-management, complete reporting
  cycle rejection, active departments and active department membership for
  leaders.
- Added authorized independent organization maintenance endpoints with
  explicit `null` clearing and current relationship fields in member and
  department views.
- Added Flow `DEPARTMENT_LEADER` and `REQUESTER_MANAGER` sources while
  preserving existing fixed, role and department source compatibility.
- Resolved manual starts from the authenticated requester and record/import
  starts from the persisted triggering actor. Periodic definitions reject
  requester-context routes during preflight.
- Kept resolved route and branch participants immutable after start across
  later organization changes, modes, delegation, deadlines and opinion rules.
- Added function-first organization controls, Flow source editors, reopen and
  simulation summaries. Responsive/visual hardening was intentionally deferred.

## Verification

- Core, Plat and Flow full regressions passed: `20 + 30 + 258 = 308` tests.
- Platform relationship/policy/API targeted regression passed: `14/14`.
- Backend reactor test compilation passed across all 12 modules.
- Real MySQL 8.4 schema upgrade passed: `60` migrations through `V8.38.0`,
  repeat migration verified, schema integration `1/1`.
- Real MySQL 8.0.44 + Redis authenticated HTTP journey passed: `1/1`.
- Frontend targeted Linux Node 24 regression passed: `4` files / `90` tests.
- Frontend full Linux Node 24 suite passed: `54` files / `248` tests.
- Frontend typecheck and Vite production build passed.
- Base structure and active instance framework validators passed.

## Demonstrated journey

Three authenticated accounts exercise the two contextual approver sources.
The administrator assigns a direct manager and department leader, creates and
publishes both definitions, and starts instances. It then replaces each
relationship and starts new instances. The original instances remain pinned to
their original approvers, new instances use the replacements, and both the
original and replacement approvers complete valid decisions. Explicit clearing
returns `FLOW_APPROVER_SOURCE_EMPTY` before instance creation. The same journey
also proves cycle rejection, system-wide authorization snapshot refresh,
relationship history retention and side-effect-free source failure.

## Deferred

- Record-field member and previous-handler approver sources.
- Multi-level escalation and automatic reporting-tree reassignment.
- Approval attachments, signature evidence and reusable comment templates.
- External tasks, Webhooks and subflows.
- Responsive, accessibility and exhaustive visual-state hardening.
