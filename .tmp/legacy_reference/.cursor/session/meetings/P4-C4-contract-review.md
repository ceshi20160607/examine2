# P4-C4 Derived Field Contract Review

## Meeting

- meeting_id: `P4-C4-CONTRACT-REVIEW`
- status: `completed`
- organized_by: `pm`
- reviewed_at: `2026-07-21T19:00:00+08:00`
- contract: `.cursor/session/current-node.md`
- contract_sha256: `sha256:36ea0bb79ae970275d1519583f543d38a61265acc45533386cc0c818e2cc2055`
- participants: `product, architect, security, dba, backend, frontend, uiux, test, pm`

## Independent Findings

| role | verdict | finding |
|---|---|---|
| product | PASS | The node closes exactly FORMULA, SUMMARY, CALCULATED, LOOKUP and AGGREGATE and keeps system fields/files/later product scope deferred. |
| architect | PASS | Structured typed declarations, immutable dependency snapshots, topological evaluation and materialized server results match the VS4 ledger. |
| security | PASS | No executable code or I/O is accepted; cross-record materialization rechecks disclosure and does not expose contributing ids or hidden values. |
| dba | PASS | The contract requires forward-only schema, RESTRICT keys, bounded fan-out/task indexes, populated replay and database-first generation. |
| backend | PASS | Transactional local evaluation, deduplicated asynchronous fan-out, stale-task protection and typed query/error boundaries are implementable from P4-C3. |
| frontend | PASS | Dedicated expression/dependency controls and read-only state renderers have exact API ownership; no generic JSON fallback is allowed. |
| uiux | PASS | Scalar and collection display, inferred type/dependency feedback and responsive pending/failure states are bounded and testable. |
| test | PASS | Six result schemas, cycle/type/cardinality/permission/failure/concurrency/restart vectors have explicit observable outcomes. |
| pm | PASS | Three 240-minute tasks fit the 720-minute slice and preserve the next-node boundary. |

## Decision

Verdict: **PASS**. The P4-C4 slice requirement gate is accepted.

Implementation may begin with `P4-C4-01`. Tasks 02 and 03 remain pending until task 01 freezes persistence and wire behavior, then may run in the declared isolated parallel group. No user product decision is required because the node narrows already accepted VS4 field-ledger behavior without expanding scope.

## Reopen Triggers

- Any free-form executable expression or client-computed canonical result.
- Any derived field without explicit result schema, dependency snapshot or cycle/type validation.
- Any cross-record materialization that leaks hidden target ids, values or totals.
- Any ad-hoc query route not declared by result schema or AGGREGATE policy.
