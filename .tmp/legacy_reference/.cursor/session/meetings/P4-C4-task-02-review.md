# P4-C4 Task 02 Independent Review

## Identity

- review_id: `P4-C4-TASK-02-REVIEW`
- task: `P4-C4-02`
- status: `completed`
- reviewed_at: `2026-07-21T23:49:00+08:00`
- owner: `backend`
- security_owner: `security`
- verifier: `test`
- contract: `.cursor/session/current-node.md`
- evidence: `.cursor/session/evidence/p4-c4/task-02.json`

## Independent Findings

| role | verdict | evidence-backed finding |
|---|---|---|
| backend | PASS | Deterministic typed evaluation, topological same-record recalculation, deduplicated cross-record fan-out, canonical READY/PENDING/FAILED materialization, generic retry, last-valid queries and exact version increments pass on real MySQL/Redis. Workers reject stale source versions, lock the active schema snapshot and recheck the independent current permission epoch around writes. |
| security | PASS | Runtime reads reapply current target module/record scope. Hidden LOOKUP targets are omitted, SUMMARY with a hidden contributor is wholly omitted, scoped target queries are unavailable, and no contributor id or hidden total is disclosed. The worker audit records the current permission epoch without persisting member-specific grants. |
| test | PASS | The 44-test backend reactor and exact five-type container journey pass on JDK 21. Vectors include six result schemas, typed null/divide-by-zero, 100/101 LOOKUP boundary, cycle, PENDING last-valid, retry, duplicate/stale task, concurrent CAS, active-snapshot skip, outbox failpoint rollback, typed filter/sort and permission-negative disclosure. |
| operations | PASS | A clean package was started against the persistent MySQL/Redis runtime, prepared through real HTTP, stopped, and started in a second JVM. Fresh login returned the same five declarations, five READY values and query result at Flyway 4.10.0. |

## Risks Reviewed

- Cross-record materialization is shared server state; member-specific disclosure is therefore enforced at every schema/detail/query read using the current grant, while the worker fences changes with the system authorization epoch.
- A permission epoch change during recalculation aborts the task transaction and follows the existing bounded retry/failure path; no partial derived/index/version/audit/outbox rows commit.
- Active schema publication is locked during task execution. Tasks bound to an inactive schema are consumed without rewriting historical records.
- The MySQL trigger used for the outbox failpoint exists only in the isolated container test and is dropped in `finally`; production migrations and privileges are unchanged.
- Browser configuration and responsive READY/PENDING/FAILED rendering remain owned by task 03.

## Decision

Verdict: **PASS** for `P4-C4-02`.

The evaluator, recalculation, authorization, typed query, failure/concurrency and packaged-restart boundary is complete. This decision does not accept the P4-C4 slice: task 03 browser and responsive UI evidence remains open.
