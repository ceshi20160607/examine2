# P4-B1 Contract Review

## Review Context

- node: `P4-B1-EDIT-AUTOSAVE`
- date: `2026-07-17`
- chair: `pm`
- participants: `product, architect, dba, backend, frontend, uiux, test, leader`
- sources: accepted P4-A2 evidence, `delivery-roadmap.md`, `vs4-task-plan.md`, VS4 API contracts and current runtime code

## Questions Resolved

1. P4-B1 is a user-visible edit/recovery slice, not only an autosave endpoint.
2. The current eight supported field types remain the field boundary; P4-B1 must not add placeholder relation/subtable/team payloads.
3. Draft recovery uses the server record as truth. No browser storage can reconstruct business state.
4. Manual update accepts DRAFT and ACTIVE; autosave accepts DRAFT only. Both use full-snapshot replacement and expected-version CAS.
5. A CAS conflict returns a freshly authorized current snapshot and exact current version. The client may reload or create a new DRAFT from local input, but never force-overwrite.
6. A single-flight client coordinator is mandatory because a debounce alone can still send overlapping writes and stale expected versions.
7. Discard/trash/recover and a "my drafts" list are lifecycle work and remain `P4-B2`; they are not silently omitted from the final goal.

## Role Decisions

- product: pass; the demo proves user input survives normal refresh, save failure and concurrency conflict.
- architect/dba: pass; transactional replacement of value/index rows is acceptable for the current scalar field slice, with CAS on the aggregate row and actual record status on indexes.
- backend: pass; reuse existing permission, scope, immutable config and idempotency infrastructure, extending the error envelope only for structured conflict data.
- frontend/uiux: pass; one edit drawer supports create continuation, DRAFT recovery and ACTIVE edit with explicit save state and conflict choices.
- test: pass; requires independent API/DB assertions plus ordinary-member desktop/mobile E2E, concurrent client conflict and backend restart readback.
- leader: accepted for execution with three tasks, each estimated at 240 minutes and the completion boundary retained.

## Gate Verdict

- verdict: `pass`
- requirement_ids: `REQ-RUNTIME-002`, `REQ-RUNTIME-003`, `REQ-DATA-001`, `REQ-RBAC-001`, `REQ-MOBILE-001`
- journey_ids: `JRN-B4`, `JRN-B11`
- next action: execute `P4-B1-01`; PM integrates task evidence and leader performs the node gate.
