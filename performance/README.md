# Examine2 performance gate

This gate implements the frozen `VS4-PERF-SEED-V1:20260715` contract. It does not accept a reduced dataset as release evidence.

## 1. Deterministic seed

The generator is streaming and never reads the wall clock. Formal generation writes 1,000,000 records, 20 typed values per record, five single-value indexes, two two-entry multi-value indexes, searchable tokens, 10,000 accounts/members/tenant grants/primary department memberships, 50 departments with deterministic closure paths, 600,000 relation rows, 300,000 reference states, 1,000,000 sub rows and values, 100,000 collaboration teams with 300,000 members, and 500,000 comments. The only prerequisites are the immutable system/tenant/module/schema snapshots (including the declared relation/subtable/reference fields) and the system owner account from `seed-spec.json`; no capacity population is left as an operator-maintained fixture.

```text
node performance/generate-seed.mjs --output evidence/performance/seed
node performance/verify-seed.mjs --input evidence/performance/seed --release
```

`seed-manifest.json` records the frozen contract hash, generator/spec hashes, each table's row count/min-max ID/SHA-256, and a canonical manifest hash. `--records 100` is useful for a harness smoke only; the release verifier rejects it. Load the TSV files with the emitted `load-seed.mysql.sql` after resolving its paths for the target MySQL client. `fixture-fragment.json` supplies the deterministic IDs and manifest for the load runner.

## 2. HTTP load

Required inputs:

- `fixture.json`: system/module/schema identifiers, deterministic record IDs and a `seedManifest` proving 1,000,000 records.
- `auth.json`: an ordinary member Cookie header and CSRF token created for the isolated performance environment.
- `cases/read-cases.json`: Q01-Q12 canonical query bodies; Q12 expands to `Q12_SC01` through `Q12_SC07`, each with an independent ordinary-member Cookie/CSRF pair under `fixture.scopeQueries`.
- the standard single-instance resource profile recorded in the fixture manifest.

Run read and write independently:

```text
node performance/run-load.mjs --mode read --fixture evidence/performance/fixture.json --auth evidence/performance/auth.json --output evidence/performance/read
node performance/run-load.mjs --mode write --fixture evidence/performance/fixture.json --auth evidence/performance/auth.json --output evidence/performance/write
```

The runner checks the real `ApiResponse` envelope (`code=OK` plus `data`), uses closed-loop workers, a separate warmup, 1 ms exact histogram buckets, nearest-rank percentiles, raw JSONL samples and zero-tolerance HTTP/schema errors. Its `HDR_EQUIVALENT_EXACT_1MS` artifact includes the raw-sample hash and a self hash so the aggregate validator can reproduce every bucket and percentile. A release run enforces 100 read VUs/20 write VUs, at least five minutes, every Q01-Q11 and Q12 scope variant sample floor, 10,000 list samples, 3,000 detail samples and 1,000 samples per write class. `--smoke` only validates the harness and is never accepted as performance evidence.

## 3. Database plans and actual rows examined

Export application SQL tracing as a JSON array. Every entry must contain `id`, `canonicalAst`, `bindings`, `generatedSql`, `executableSql` (the same bound single SELECT), and `expectedCardinality`. Required IDs are Q01-Q11 plus Q12_SC01..Q12_SC07. Then run:

```text
node performance/collect-db-evidence.mjs --query-artifacts evidence/performance/query-artifacts.json --defaults-extra-file C:/secure/mysql-client.cnf --database examine --slow-log C:/mysql/slow.log --output evidence/performance/db
```

The client option file keeps credentials out of commands/evidence. MySQL must have `performance_schema.events_statements_history_long` enabled and the supplied slow log must include every tagged query. The collector saves raw `EXPLAIN FORMAT=JSON`, raw `EXPLAIN ANALYZE`, actual `ROWS_EXAMINED`, temp/filesort findings, and P95. Static estimates are never accepted as actual rows examined.

## 4. Browser shell

Run against the production frontend build and warmed backend API. The storage state must belong to an ordinary member. The selector must identify the critical list element that is visible and interactive, not a layout shell.

```text
node performance/collect-shell-p75.mjs --base-url http://127.0.0.1:4173 --route /systems/123/apps/performance_records --ready-selector "[data-performance-ready=true]" --storage-state evidence/performance/member-storage-state.json --output evidence/performance/browser
```

Formal mode requires at least 30 independent 1440x900 contexts, clears browser cache per run, and saves one Playwright trace per sample. `--smoke --runs 3` checks wiring only.

## 5. Aggregate gate

Place `seed/`, `read/`, `write/`, `db/`, and `browser/` under one evidence directory, then run:

```text
node performance/validate-evidence.mjs --evidence evidence/performance
```

The aggregate gate recomputes hashes/histograms/percentiles and refuses reduced seed, smoke output, missing Q/scope samples, missing slow-log or performance-schema evidence, prohibited plans, missing browser traces, insufficient duration/concurrency, or a threshold failure.

Formal 100 VU/20 VU runs are intentionally deferred until source freeze. A small smoke result must never be reported as a performance PASS.
