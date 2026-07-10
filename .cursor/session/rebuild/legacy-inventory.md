# Legacy Inventory And Cleanup Plan

## Status

Task: `REQ-R0-004 Legacy Inventory And Cleanup Plan`

Status: leader draft complete

Mode: `requirements-rebuild`

No deletion is approved by this draft. This file records classification and impact before any cleanup. This inventory task did not modify backend, frontend, or SQL files.

## Read Inputs

| Input | Inventory Takeaway |
|---|---|
| `.cursor/session/rebuild/legacy-inventory.md` | Prior draft existed and needed current `.oldbk` measurements plus deletion-candidate detail. |
| `.gitignore` | Ignores `.oldbk/`, `.tmp-browser/`, `release/`, `logs/`, `target/`, `node_modules/`, `dist/`, `build/`, `.idea/`, and `*.log`. |
| `.cursor/README.md` | Current mode is requirements rebuild; old implementation and old evidence are reference-only, not completion proof. |
| `.oldbk/README.md` | Older v1 backup is read-only reference; do not directly continue old frontend pages or old patches. |
| `.oldbk/restart-20260708-220451/ARCHIVE_MANIFEST.md` | Restart archive was created because the previous implementation drifted; archived code/evidence are reference-only. |

## Source Scan Summary

Current active root contains documentation and `.cursor` governance files, but no active `backend/`, `frontend/`, `sql/`, `scripts/`, or `data/` implementation directories.

Current `.oldbk` scan, taken on 2026-07-09 Asia/Shanghai, shows:

- `.oldbk/`: 448.70 MB, 11,561 files, 7,145 directories.
- `.oldbk/restart-20260708-220451/`: 443.35 MB, 10,015 files, 6,722 directories.
- `.oldbk/restart-20260708-220451/docs/`: 420.52 MB; most of this is archived evidence plus embedded browser profiles.
- `.oldbk/backend/`, `.oldbk/frontend/`, `.oldbk/sql/`: older reference implementation snapshot.

Concurrent scan note: an earlier pass in this task observed `.oldbk/restart-20260708-220451/frontend/node_modules/` at about 57.96 MB, but a later rescan found the path absent. This document uses the later current scan and does not infer or perform that change.

## Class Definitions

| Class | Meaning |
|---|---|
| retained source | Current source of truth or archive-boundary metadata that should remain available. |
| reference implementation | Old code, SQL, scripts, or deployment material useful only for pattern mining. |
| evidence archive | Old screenshots, logs, manifests, or recovery notes that may explain history but are not current proof. |
| generated/cache noise | Rebuildable output, browser state, IDE metadata, logs, or dependency/cache material. |
| deletion candidate | Generated/cache noise with low reference value, pending explicit cleanup approval. |
| unknown | Not enough semantic context yet; keep until inspected by owner or domain reviewer. |

## Retained Source

| Path | Size | Reference Value | Action |
|---|---:|---|---|
| `docs/user_requirement.md` | not rescanned | current broad requirement source | keep |
| `docs/user_setting.md` | not rescanned | current user setting source listed by governance | keep |
| `docs/design/prototypes/**` | not rescanned | IA/prototype reference | keep |
| `docs/temp_flow.md` | not rescanned | flow and journey anchor | keep |
| `docs/temp_flow_persion.html` | not rescanned | human-readable flow map | keep |
| `.cursor/**` | not rescanned | current rules, state, architecture, tasks | keep and update only through approved rebuild work |
| `.oldbk/README.md` | 0 MB | explains old backup boundary and read-only use | keep |
| `.oldbk/restart-20260708-220451/ARCHIVE_MANIFEST.md` | 0 MB | explains restart archive scope and reason | keep |

## `.oldbk` Level-1 Scan

| Path | Class | Size MB | Files | Dirs | Action |
|---|---|---:|---:|---:|---|
| `.oldbk/restart-20260708-220451/` | evidence archive | 443.35 | 10,015 | 6,722 | keep as archive; classify children before cleanup |
| `.oldbk/.codex/` | unknown | 2.04 | 694 | 134 | inspect before use or deletion |
| `.oldbk/backend/` | reference implementation | 1.72 | 762 | 262 | reference only |
| `.oldbk/frontend/` | reference implementation | 1.45 | 88 | 22 | reference only |
| `.oldbk/sql/` | reference implementation | 0.13 | 1 | 0 | reference only |
| `.oldbk/README.md` | retained source | 0.00 | 1 | 0 | keep |

## `.oldbk` Level-2 Scan

| Parent | Name | Class | Size MB | Files | Dirs | Action |
|---|---|---|---:|---:|---:|---|
| `.oldbk/restart-20260708-220451/` | `docs/` | evidence archive | 420.52 | 8,592 | 6,249 | keep archive; treat embedded browser profiles separately |
| `.oldbk/restart-20260708-220451/` | `.tmp-browser/` | deletion candidate | 16.69 | 570 | 235 | candidate after summary and rollback prep |
| `.oldbk/restart-20260708-220451/` | `scripts/` | reference implementation | 2.35 | 127 | 0 | reference old checks/recovery only |
| `.oldbk/restart-20260708-220451/` | `backend/` | reference implementation | 2.16 | 631 | 197 | reference only |
| `.oldbk/restart-20260708-220451/` | `frontend/` | reference implementation | 1.11 | 46 | 21 | reference only; generated children are candidates |
| `.oldbk/restart-20260708-220451/` | `.cursor-before-clean-rebuild/` | evidence archive | 0.24 | 4 | 0 | keep until governance migration is fully summarized |
| `.oldbk/restart-20260708-220451/` | `sql/` | reference implementation | 0.15 | 11 | 2 | reference old schema fragments only |
| `.oldbk/restart-20260708-220451/` | `.idea/` | deletion candidate | 0.07 | 9 | 0 | candidate after user confirms no IDE-state need |
| `.oldbk/restart-20260708-220451/` | `tilian/` | unknown | 0.04 | 14 | 4 | inspect before classification |
| `.oldbk/restart-20260708-220451/` | `deploy/` | reference implementation | 0.02 | 4 | 3 | reference old deployment shape only |
| `.oldbk/restart-20260708-220451/` | `.agents/` | unknown | 0.00 | 0 | 0 | keep or delete only after owner review |
| `.oldbk/restart-20260708-220451/` | root metadata files | retained source | 0.00 | 9 | 0 | keep archive metadata unless superseded by manifest |
| `.oldbk/frontend/` | `docs/` | reference implementation | 0.87 | 34 | 1 | reference old frontend docs only |
| `.oldbk/frontend/` | `src/` | reference implementation | 0.55 | 49 | 19 | reference old Vite/TS patterns only |
| `.oldbk/frontend/` | package/config files | reference implementation | 0.03 | 5 | 0 | reference dependency/build shape only |
| `.oldbk/backend/` | `examine-module/` | reference implementation | 0.78 | 317 | 35 | reference old module layout |
| `.oldbk/backend/` | `examine-plat/` | reference implementation | 0.22 | 101 | 35 | reference only |
| `.oldbk/backend/` | `examine-flow/` | reference implementation | 0.19 | 93 | 32 | reference only |
| `.oldbk/backend/` | `examine-app/` | reference implementation | 0.18 | 78 | 34 | reference only |
| `.oldbk/backend/` | `examine-core/` | reference implementation | 0.14 | 89 | 36 | reference only |
| `.oldbk/backend/` | `examine-upload/` | reference implementation | 0.09 | 37 | 34 | reference only |
| `.oldbk/backend/` | `examine-generator/` | reference implementation | 0.06 | 23 | 18 | reference generator approach only |
| `.oldbk/backend/` | `examine-web/` | reference implementation | 0.06 | 21 | 28 | reference only |
| `.oldbk/backend/` | `deploy/`, `docs/`, `pom.xml` | reference implementation | 0.00 | 3 | 0 | reference only |
| `.oldbk/.codex/` | `oldexamine/` | unknown | 1.69 | 655 | 119 | inspect before relying on or deleting |
| `.oldbk/.codex/` | `state.json` | evidence archive | 0.22 | 1 | 0 | historical only; not current state |
| `.oldbk/.codex/` | `oldgenerator/` | unknown | 0.09 | 27 | 12 | inspect before relying on or deleting |
| `.oldbk/.codex/` | `agents/` | evidence archive | 0.06 | 10 | 0 | old agent metadata only |
| `.oldbk/.codex/` | `config.toml` | evidence archive | 0.00 | 1 | 0 | old local config only |
| `.oldbk/sql/` | `init.sql` | reference implementation | 0.13 | 1 | 0 | reference old schema only |

## Evidence Archive Detail

| Path | Size MB | Files | Dirs | Current Reading |
|---|---:|---:|---:|---|
| `.oldbk/restart-20260708-220451/docs/evidence/` | 419.05 | 8,495 | 6,238 | Old proof/log/screenshot archive; keep until summarized. |
| `.oldbk/restart-20260708-220451/docs/evidence/recovery/` | 417.81 | 8,361 | 6,237 | Recovery evidence dominates size; most bulk is browser profile data. |
| `.oldbk/restart-20260708-220451/docs/evidence/recovery/screenshots/**/chrome-profile-*` | 386.41 | 7,444 | 6,136 | Generated browser profile state embedded under evidence; deletion candidate, while visible screenshots and logs should remain. |

## Deletion Candidate Cleanup Plan

No candidate below is approved for deletion by this document. Deletion requires an explicit cleanup task after retaining the summary listed here.

| Candidate | Size/File Count | Reference Value | Impact If Removed | Retain Summary Before Delete | Rollback Plan |
|---|---:|---|---|---|---|
| `.oldbk/restart-20260708-220451/.tmp-browser/` | 16.69 MB, 570 files, 235 dirs | Very low. Raw browser profiles/cache are not product, requirement, or implementation source. | Old browser session state, cache, cookies, and local storage become unavailable; screenshots/log evidence elsewhere remains. | Keep this path, size, file count, and profile root list in inventory. No product screenshot summary expected unless a unique screenshot is found inside. | Before deletion, move or zip to a dated quarantine outside active rebuild scope; restore the original path from quarantine if a reviewer later needs the profile. If quarantine is gone, restore from external backup/archive. |
| `.oldbk/restart-20260708-220451/docs/evidence/recovery/screenshots/**/chrome-profile-*` | 386.41 MB, 17 profile dirs, 7,444 files, 6,136 dirs | Low. Browser profiles explain how screenshots were produced but are not themselves durable evidence. | Exact historical browser state cannot be replayed; visible screenshots, JSON, markdown, and logs should still preserve review evidence. | Keep a list mapping each `chrome-profile-*` directory to its recovery case directory; keep non-profile screenshots and evidence files. | Quarantine or zip matching profile dirs before deletion; restore profile dirs under the same screenshot case path if needed. |
| `.oldbk/restart-20260708-220451/frontend/dist/` | 0.34 MB, 5 files, 1 dir | Low. Generated Vite build output; source and config are the useful references. | Old built bundle disappears; source-level reference remains. | Keep asset filenames and note that the old frontend source/config remains archived. | Restore from quarantine, or rebuild from archived frontend source after dependency restoration if exact bytes are not required. |
| `.oldbk/restart-20260708-220451/frontend/*.log` | 6 small files, about 0 MB | Low to medium. May contain old preview/build troubleshooting, but not durable source. | Raw old preview/build logs disappear; old evidence logs under `docs/evidence/` remain. | Preserve filenames and any non-empty error summary if cleanup approval wants them removed. | Quarantine or zip before deletion; restore files by original name if a troubleshooting audit requests them. |
| `.oldbk/restart-20260708-220451/.idea/` | 0.07 MB, 9 files | Very low. Local IDE metadata only. | Old IDE workspace/module settings disappear; no product or rebuild behavior should change. | Keep only path, size, and file count unless a user says IDE state is needed. | Quarantine before deletion; restore `.idea/` if a local IDE workflow requires it. |

## Unknown Review Queue

| Path | Size/File Count | Why Unknown | Next Check |
|---|---:|---|---|
| `.oldbk/.codex/oldexamine/` | 1.69 MB, 655 files | Looks like old agent/project material, but reference value is not yet separated from stale context. | Inspect names and summarize any reusable governance lessons before cleanup. |
| `.oldbk/.codex/oldgenerator/` | 0.09 MB, 27 files | Possibly old generator-related context, but not enough from two-level scan. | Compare against `.oldbk/backend/examine-generator/` before classifying. |
| `.oldbk/restart-20260708-220451/tilian/` | 0.04 MB, 14 files | Domain/purpose unclear from directory name. | Inspect file names and decide whether it is requirement evidence, old implementation, or removable noise. |
| `.oldbk/restart-20260708-220451/.agents/` | 0 MB, empty | Empty old orchestration marker. | Confirm no owner relies on the marker before deletion. |

## Current Decisions

- No deletion performed.
- Reference implementation remains read-only.
- Historical evidence is not current completion proof.
- Generated/cache noise may be deleted only after an explicit cleanup approval, retained summary, and rollback/quarantine preparation.
- Unknown paths stay retained until their reference value is inspected.

## Next Inventory Work

1. Summarize useful backend/frontend/sql patterns from reference implementation into rebuild architecture notes before any large cleanup.
2. If cleanup is approved, produce a concrete pre-delete manifest for `.tmp-browser/`, `chrome-profile-*`, `frontend/dist/`, `frontend/*.log`, and `.idea/`.
3. Resolve the unknown queue before considering broader `.oldbk` size reduction.
