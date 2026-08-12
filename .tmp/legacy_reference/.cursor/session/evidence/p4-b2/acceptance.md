# P4-B2 Lifecycle And Recovery Acceptance

## Verdict

PASS for the bounded `P4-B2-LIFECYCLE-RECOVERY` slice.

An explicitly authorized non-root member can archive and unarchive an ACTIVE record, move ACTIVE or DRAFT records to TRASHED without deleting typed values, restore each record to its exact prior state, observe a due DRAFT become EXPIRED through the scheduled MySQL worker, recover it to a writable DRAFT with a renewed 30-day expiry, and read the same committed states after a packaged-backend restart.

## Demo

- Entry: `http://127.0.0.1:5173/systems/2079042783503958018/workbench?module=work_order`
- Account: `p4b2_member_8a1d6898`
- Password: `P4-B2-Member-8a1d6898!`
- Fixture: `.cursor/session/evidence/p4-b2/fixture.json`
- Dedicated target: `P4 生命周期验收 8a1d6898`; the accepted P4-A2 system was not republished or repurposed.

## Accepted Tasks

- `P4-B2-01`: forward migration plus scoped, idempotent and CAS-guarded archive/unarchive/trash/restore/discard state machine in `task-01.json`.
- `P4-B2-02`: bounded restart-safe due-draft worker, system audit/outbox and authorized expired-draft recovery in `task-02.json`.
- `P4-B2-03`: server-driven lifecycle controls, destructive confirmation, desktop/mobile real journeys, DB readback and backend restart in `task-03.json`.

## Independent Evidence

- Remote database migrated from Flyway `4.0.0` to `4.1.0`; startup validated seven migrations and the restarted process reported health `UP`.
- Testcontainers cumulative runtime integration passed twice from an empty MySQL schema through `v4.1.0`, including lifecycle state/CAS/idempotency, five independent permission negatives, worker first-run/second-run behavior, audit/outbox and renewed expiry.
- Frontend production build passed and Vitest passed `6` files / `19` tests.
- P4-B2 browser journey passed independently at desktop `1440x900` and mobile `390x844`; no unexpected console/page error, HTTP 5xx or document overflow was observed.
- Packaged-backend restart readback passed for ACTIVE record `2079048561459707905` and recovered DRAFT `2079048614144360450` using a fresh ordinary-member login.
- Cumulative P4-A2 desktop/mobile and P4-B1 desktop/mobile journeys passed after the P4-B2 changes.

## Persistence Readback

- ACTIVE restore clears `prior_status`, `deleted_at`, `deleted_by` and `draft_expires_at` while retaining the same eight typed values.
- DRAFT discard stores prior DRAFT and delete metadata; restore clears them and preserves the same eight values.
- Automatic expiry clears `draft_expires_at`, writes EXPIRED index/search state and records a SYSTEM-source audit/outbox event.
- Recover returns EXPIRED to DRAFT, renews expiry beyond 29 days, permits a subsequent save and does not duplicate transition events.
- Desktop and mobile acceptance aggregates produced `22` and `24` outbox rows respectively; audit readback contains every required lifecycle operation.

## Resolved Findings

- A lifecycle confirmation opened below its parent Ant Drawer, making the confirm button unreachable. The modal now uses an isolated `z-index: 2000`, and E2E asserts that it is above the active drawer before clicking.
- Fixture publication correctly invalidated stale authorization snapshots. Provisioning now re-authenticates after config and role publication instead of continuing with an obsolete session.
- P4-B1's legacy reload assertion still targeted retained closed Drawer DOM. It now targets only `.ant-drawer-open`, and the desktop rerun passed.

## Completion Boundary

This acceptance closes only P4-B2 single-record lifecycle and recovery. It does not claim filter/search/saved views, batch operations, additional canonical field families, collaboration, Flow, task center, reports, release readiness, complete P4, final-system completion or user final acceptance. The next planned product slice is `P4-B3` query, paging, sorting, search and saved views; its contract must be reviewed before implementation.
