# FAST-FLOW-APPROVAL-EVIDENCE-TEMPLATES-51 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-31T14:02:00+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual
  hardening remain deferred.

## Delivered

- Added optional route, branch and ordered-stage decision-evidence policies
  with bounded attachment counts, MIME-family allow-lists and
  none/optional/required signature modes. Stage zero preserves the legacy
  route or branch projection.
- Added approval and rejection evidence input for existing File assets,
  typed or file signatures and reusable comment-template selection while
  preserving existing requests when no evidence policy applies.
- Kept the module boundary explicit through a narrow Core
  `ApprovalEvidenceFileFacade`; Flow does not query File persistence or binary
  storage directly.
- Validated tenant, permission, existence, reference eligibility, attachment
  count, MIME family and signature rules before a decision is committed.
  Durable `FLOW_DECISION_EVIDENCE` references are idempotent and compensated
  when attachment fails.
- Persisted immutable decision snapshots containing canonical file metadata,
  branch/stage identity, actor and represented-member facts, delegation,
  template version and signature metadata. History rereads the snapshot
  instead of mutable File or template heads.
- Added tenant-scoped, named and versioned approval-comment templates with
  active/inactive lifecycle, management permission checks and immutable
  version selection.
- Added V8.41 definition/runtime policy snapshots, comment-template heads and
  versions, decision evidence and evidence-file snapshots with restrictive
  foreign keys and bounded checks.
- Added function-first frontend controls for policy editing, template
  management/selection, existing-asset resolution, validation, signatures and
  immutable history readback without expanding responsive hardening scope.

## Verification

- Affected backend full regressions passed: Core `23/23`, Flow `303/303` and
  File `35/35`, totaling `361/361`.
- Targeted API/service/File verification passed `80/80`; evidence
  domain/repository/JDBC verification passed `24/24`.
- The periodic worker JDBC fixture regression passed `7/7` after receiving the
  same V8.41 evidence tables and policy columns as production.
- Backend reactor test compilation passed across all `12` modules.
- Real MySQL 8.4 schema integration passed `1/1`: all `63` migrations applied
  through V8.41 and repeat migration was clean.
- Real MySQL 8.0.44 + Redis authenticated HTTP journey passed `1/1` with all
  `63` migrations applied.
- Frontend targeted regression passed `4` files / `137` tests; the canonical
  full suite passed `54` files / `273` tests. Typecheck and Vite production
  build passed.
- Base structure, active instance, VS4 contract, strict encoding and cadence
  self-tests passed. `git diff --check` passed.

## Demonstrated journey

An administrator creates and publishes a two-stage approval whose first stage
requires a permitted attachment, file signature and reusable comment
template. Missing evidence, an unknown file, a file owned by another tenant
and a disallowed MIME family all fail without a decision or evidence row. A
delegate then approves on behalf of the represented member with a PDF,
signature and template. The exact actor/delegation/template/file facts and two
durable File references are read back from history. Revising and deactivating
the template does not change the stored version, a referenced file cannot be
deleted, and the final stage completes the instance.

## Integration corrections

- Made the transactional File evidence adapter proxyable so the real Spring
  application context can create it.
- Corrected the schema acceptance query and completed the periodic-worker H2
  fixture with V8.41 structures.
- Repaired the cadence self-test to execute Active-mode rules and reset stale
  fixture overrun/contract state, restoring all seven positive/negative
  framework cases.

## Deferred

- Durable external tasks, outbound Webhook delivery and subflows.
- Cryptographic/legal digital signatures, certificates and identity proofing.
- Direct binary ownership inside Flow.
- Nested gateways, loops, arbitrary graph traversal and cross-branch jumps.
- Responsive, accessibility and exhaustive visual-state hardening.
