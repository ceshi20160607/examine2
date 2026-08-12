# Fast Collab and Flow frontend batch 05 acceptance

## Verdict

`PASS_REFRESH_SAFE_FLOW_AND_RECORD_TEAM_UI`

Flow now has tenant-scoped refresh-safe read contracts and a usable system route. Record-team collaboration is
mounted in the existing runtime record detail and can initialize and mutate persisted team state. This is a
functional acceptance; responsive, browser, accessibility and exhaustive visual hardening remain deferred.

## Delivered

- Flow read contracts:
  - `GET /api/v1/systems/{systemId}/flow/definitions?page=&size=`
  - `GET /api/v1/systems/{systemId}/flow/instances?page=&size=`
  - `GET /api/v1/systems/{systemId}/flow/instances/{instanceId}`
  - existing instance history read
- Flow lists use stable descending order, bounded pagination and JDBC queries scoped by authenticated
  `system_id + tenant_id`.
- `/systems/:systemId/flows` supports definition create/revise/publish, instance start, approve/reject, server
  readback and history. Definition and instance state is cleared and reloaded when the active tenant changes.
- Record-team collaboration adds an explicit idempotent `team:initialize` command. The authenticated member becomes
  owner only on first creation; repeated initialization preserves the existing owner and version.
- `RecordTeamPanel` is mounted in the existing record-detail drawer and supports get/initialize/add/change/remove
  and ownership transfer with module permission checks, loading, empty, read-only and error states.
- Frontend initialization was corrected to consume the actual `{ created, team }` backend response rather than a
  mocked bare team object.

## Verification

- `examine-flow`: 19 tests passed.
- `examine-collab`: 23 tests passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed with authenticated RANDOM_PORT HTTP, MySQL 8.0.44, Redis 7.4 and all
  23 Flyway migrations. It covers create/list/revise/publish/start/list/detail/approve/history and cross-tenant
  empty-list/404 behavior.
- `CollabApiJourneyIntegrationTest`: 1/1 passed with a real runtime record and the same HTTP/MySQL/Redis baseline. It
  covers missing-team 404, first/repeated initialization, get/add/change/remove/transfer, post-transfer idempotency,
  cross-tenant 404 and final MySQL rows.
- Frontend full suite: 16 files, 52 tests passed.
- Frontend production build: typecheck and Vite build passed. Flow is emitted as an independent lazy chunk.
- No four-minute full backend reactor rerun was required because production wiring outside the affected Flow,
  Collab and frontend integration paths did not change.

## Honest boundary

- Flow is a single-node, fixed-approver workflow. It does not yet provide a visual designer, conditions, parallel
  branches, delegation, simulation, timers or business-record status writeback.
- The Flow HTTP journey uses one ROOT member as requester and approver; module tests cover permission and
  non-approver rejection.
- Collab initialization has no runtime record-existence port inside `examine-collab`; the real HTTP journey proves a
  valid record path, but the isolated command currently creates collaboration metadata for any numeric record ID
  that satisfies database constraints.
- Flow and Collab still use raw member IDs because an ordinary-member directory picker is not exposed.
- Comments, history navigation, batch operations, browser E2E, mobile/breakpoint adaptation, accessibility and
  exhaustive visual-state matrices remain open.
