# P4-C1 Contract Review

## Meeting

- meeting_id: `P4-C1-CONTRACT-REVIEW`
- status: `completed`
- owner: `pm`
- verifier: `leader`
- reviewed_at: `2026-07-20T14:58:01+08:00`
- requirements: `REQ-FIELD-001`, `REQ-RUNTIME-001`, `REQ-DATA-MODEL-001`, `REQ-RBAC-001`, `REQ-MOBILE-001`
- journeys: `JRN-B3`, `JRN-B4`, `JRN-B5`
- contract: `.cursor/session/current-node.md`

## Decision

P4-C1 is one complete numeric, time and selection-field slice. It delivers `PERCENT`, `MONEY`, `DATE_RANGE`, `TIME`, `TIME_RANGE`, `MULTI_SELECT`, `CASCADE`, `SWITCH`, `RATING`, `PROGRESS` and `TAG` from configuration publication through canonical persistence, typed query and ordinary-member desktop/mobile use. The current machine field/API/DB contracts settle the value shapes, bounds, operators and exclusions; no unresolved product ambiguity blocks implementation.

## Current-Gap Finding

The runtime currently accepts only the eight P4-A2 base types. The value table lacks time, boolean and currency payloads; index and unique reservations do not cover P4-C1; the query compiler and frontend controls do not implement the eleven field contracts. Treating these fields as opaque JSON or generic text inputs would break validation, sorting, uniqueness and permission guarantees. The node therefore begins with forward schema, configuration and canonical normalization before adding query or UI behavior.

## Role Review

| role | verdict | decision |
|---|---|---|
| product | PASS | The eleven types, legal values, boundaries and deferred field families match the frozen field package and create a coherent user-visible increment. |
| architect | PASS | Canonical values remain the source of truth; deterministic ordinals, typed indexes and strict DTOs prevent storage and query dialect drift. |
| dba | PASS | A new forward migration must extend populated tables without rewriting executed migrations, preserve old rows, add typed constraints and implement unique reservation lifecycle transactionally. |
| backend | PASS | Task 01 owns publication, normalization and value persistence for all eleven types; task 02 consumes that contract for index, uniqueness and every explicit operator. |
| frontend | PASS | Task 03 uses domain controls and canonical wire shapes on desktop and mobile; generic string controls or client-side query fallback are rejected. |
| uiux | PASS | Currency, ranges, options, cascade, switch, rating, progress and tag interactions have explicit responsive controls, validation and conflict states. |
| test | PASS | Boundary tables, invalid vectors, same-currency money, ordinal ordering, permissions, lifecycle, restart and all explicit operators are independently reproducible. |
| pm | PASS | Three serial tasks each have one outcome and a 240-minute estimate; later field families, relations, derived values, files and collaboration remain outside this slice. |

## Risk Decisions

- Populated-database migration is verified before runtime acceptance; existing P4-A/B records must remain readable without backfill.
- MONEY comparison and uniqueness include currency. Cross-currency comparison is invalid, not an implicit conversion.
- Range/path/list ordinals are canonical and transactionally replaced; a half range or discontinuous cascade path cannot persist.
- UNIQUE reservations apply only to the six declared sortable scalar types and follow ACTIVE/ARCHIVED versus DRAFT/TRASHED/EXPIRED lifecycle rules.
- P4-C1 passes only when all eleven types and all three tasks pass. A partial subset cannot be reported as node completion.

## Leader Gate

Verdict: **PASS**. Requirements, machine contracts, current gaps, role ownership, migration order, negative cases and completion boundary are explicit. `P4-C1-01` may start. Any proposal to widen field families or weaken canonical storage returns to PM review instead of expanding this task.
