# P4-C4 Task 01 Independent Review

## Identity

- review_id: `P4-C4-TASK-01-REVIEW`
- task: `P4-C4-01`
- status: `completed`
- reviewed_at: `2026-07-21T22:14:00+08:00`
- owner: `backend`
- data_owner: `dba`
- verifier: `test`
- contract: `.cursor/session/current-node.md`
- evidence: `.cursor/session/evidence/p4-c4/task-01.json`

## Independent Findings

| role | verdict | evidence-backed finding |
|---|---|---|
| dba | PASS | V4.10.0 is forward-only, adds no tables, preserves populated V4.5 data, restores immutable property snapshots, uses composite result/type foreign keys with `ON DELETE RESTRICT`, bounds lookup rows through the existing ordinal constraint, and indexes derived rank/state. MySQL 8.4 populated replay, repeat replay and constraint-negative vectors passed. |
| backend | PASS | Exact no-default read-only declarations, bounded typed ASTs, relation/subtable dependencies, deterministic checksums, topological ranks, module-copy remapping and publication projection are implemented without evaluator or authorization behavior in generated base code. |
| test | PASS | Five-field real API publication persisted five derived snapshots across three result schemas; an editable derived declaration was rejected and an indirect cycle produced `SCHEMA_CYCLE`. The complete reactor passed 40 tests with zero failures, errors or skips on JDK 21, including empty MySQL 8.0 and populated MySQL 8.4 migration paths. |

## Risks Reviewed

- Historical pre-C4 experimental derived snapshots are normalized to a readable, explainable version-1 envelope; every newly published snapshot is produced by the exact Java analyzer.
- Legacy scalar rows retain the existing four-column runtime-field foreign key, while derived rows additionally require the seven-column field type/scope/result-schema key.
- LOOKUP materialization reuses ordered canonical value rows and the existing `ordinal BETWEEN 0 AND 99` database bound; task 02 remains responsible for evaluation and query behavior.
- Generated entities, mappers and services reflect the final schema only and contain no derived evaluator, permission or recalculation policy.

## Decision

Verdict: **PASS** for `P4-C4-01`.

The exact publication, dependency snapshot and canonical storage boundary is frozen and can be consumed by tasks 02 and 03. This decision does not accept the P4-C4 slice: evaluator/fan-out/query/restart work and responsive configuration/runtime journeys remain open.
