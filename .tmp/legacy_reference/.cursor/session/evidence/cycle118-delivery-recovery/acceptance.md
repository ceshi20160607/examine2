# Cycle 118 delivery, backup, restore, upgrade and rollback rehearsal

- Verdict: **PASS**
- status: `PASS`
- captured_at: `2026-08-08T11:46:00+08:00`
- formal checkpoint impact: none; CP3 remains `not_ready`

## Package and runtime

- Latest executable rolling package: `.cursor/session/packages/CYCLE-FINAL-DELIVERY-118-ROLLING/20260808-114525`
- Package kind: `rolling_delivery_cycle_snapshot`
- Latest archive SHA-256: `919cd7bf993005545a21bd86cfcd622a131abd289f624c2f5ba7179f0039f005`
- Package contains 363 manifest-tracked files, 11 PowerShell scripts, 11 POSIX scripts and 7 documentation contracts.
- Runtime: MySQL 8.4, Redis 7.4, Java 21 backend and Nginx frontend; health returned `UP`.

## Rehearsed operations

| Operation | Result |
|---|---|
| package/source validation | PASS |
| health and fixed log retrieval | PASS |
| backup | PASS: `database.sql`, `files.zip`, `backup-manifest.json`; secrets excluded |
| destructive restore | PASS; backend/frontend stopped, database recreated, checksums verified, data imported, health `UP` |
| restored data readback | `cycle118_acceptance` system count `1`; successful Flyway migrations `117` |
| complete restart | PASS; MySQL/Redis/backend/frontend stopped and restarted, health `UP` |
| package upgrade | PASS: `20260808-113105` -> `20260808-113543`, automatic pre-upgrade backup |
| rollback | PASS: upgrade backup restored onto `20260808-113105`, health `UP` |
| upgrade after rollback | PASS; final runtime returned to the newer rolling package |

## Defect found and fixed

The first backup attempt exposed that `Compress-Archive files/*` creates no archive when the storage directory exists but is empty. `delivery/scripts/backup.ps1` now uses `ZipFile.CreateFromDirectory`, which creates a valid archive for both empty and populated storage roots. The complete backup/restore/upgrade/rollback rehearsal passed after the fix.

The current `20260808-114525` package adds the final focus-management frontend fix on top of the rehearsed delivery scripts and starts successfully. This is rolling operational evidence; the immutable formal release candidate and explicit user sign-off remain open.
