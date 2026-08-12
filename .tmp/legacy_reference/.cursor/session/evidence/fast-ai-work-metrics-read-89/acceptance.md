# FAST-AI-WORK-METRICS-READ-89 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T23:49:19+08:00`
- Delivery mode: function-first parallel Core/Work, AI and frontend lanes;
  responsive, accessibility and exhaustive visual adaptation remain deferred.

## Delivered

- Added the exact four-field, policy-gated `WORK_PROJECT_METRICS_QUERY` plan for
  a canonical positive project id and a UTC left-closed/right-open window of
  1..31 days. Model output cannot select actor identity, tenant, system, scope,
  permission, route, SQL, sort or limit.
- Extended the narrow Core `AiWorkQueryFacade` port and implemented it in the
  Work owner. Project visibility and task visibility remain distinct: project
  manage or active membership resolves the project, while task manage controls
  tenant-wide task facts. Hidden membership, revoked Work access and foreign
  tenants retain non-disclosing owner errors.
- Extended the existing JDBC and in-memory metrics repositories with an
  optional project predicate and truthful all-time total/open/completed facts.
  The result reuses bounded UTC window metrics, daily zero-fill, Top-20
  assignees and native drill routes, with `projectId` retained by every route.
- Added the typed mutually-exclusive Agent `workMetrics` result, policy editor
  operation and one read-only frontend card. Work task navigation now restores
  the canonical `projectId` filter before issuing the native task query.
- Added `V8_70_0__ai_work_project_metrics_tool_name.sql`, which broadens only
  the existing AI tool-name constraint. No Work table, speculative index, AI
  SQL access, confirmation path or mutation control was added.

## Verification

- Affected backend full regression passed Core `64/64`, Work `93/93` and AI
  `91/91`, totaling `248/248` with no failure, error or skip. Agent Web
  controller contracts passed `3/3`.
- All `14` Maven modules and their tests compiled successfully.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible provider HTTP
  journey passed `1/1` in `280.2s` test time (`6:05` reactor total). It applied
  all `92` migrations and retained the existing Flow/Work/Event/OpenAPI/AI
  journey.
- The independent MySQL 8.4 schema test passed `1/1`, validated all `92`
  migrations through `V8_70_0`, applied `50` migrations from V8.20 and proved
  the final repeat migration performs zero work.
- Frontend full verification passed `99` files / `475` tests. Typecheck and the
  production build passed over `5,764` transformed modules; the only message
  was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, staged and unstaged `git diff --check`, seven cadence
  cases and both VS4 `43`-endpoint machine-contract validators passed.
- The canonical progress validator, reusable base Structure validator and
  instance Active validator passed after the Batch89-to-Batch90 state
  transition; progress is `80.7%` with no package checkpoint attempt.

## Demonstrated journey

An authorized member creates one visible project with an open overdue task and
one completed task plus an other-project decoy. The Agent returns exactly one
aggregate with `total=2`, `open=1`, `completed=1`, bounded daily/window facts
and the second owner as the open Top assignee. Its overdue drill route has the
exact native Work task path and returns only the target project.

The journey intentionally removes the querying member while retaining a second
active owner, then separately disables project manage and Work access and
finally queries a foreign-tenant project. The three calls fail with two
`WORK_PROJECT_NOT_FOUND` results and one `WORK_PROJECT_FORBIDDEN` result,
`result_count=0`, and no summary-provider call. The successful tool row is
`SUCCEEDED/OK/result_count=1`. Work task/project row counts and version sums do
not change, and after the expected membership removal the member row count and
version sum also remain unchanged. Durable AI projections contain none of the
raw operation markers, project titles, decoy titles, owner result JSON or drill
data.

## Integration corrections

- The first journey run exposed that non-selected `tasks` and `reports` use the
  established empty-array contract rather than nullable fields. The assertion
  now proves empty arrays while the typed result still enforces branch
  exclusivity.
- The real tool ledger confirmed the historical baseline was eight calls;
  Batch89 adds four calls, so the cumulative evidence is 12 tool rows, 29 usage
  rows, at least 27 planner calls and nine successful summary calls.
- No created-daily index was proposed or added, so the contract's EXPLAIN-before-
  index boundary was not crossed.

## Completion boundary

This accepts only the Work project metrics read node. It does not accept a
project package checkpoint, release gate or final user acceptance. The next
node is selected from the evidence-ranked remaining functional gaps; responsive,
accessibility and visual hardening stay late.
