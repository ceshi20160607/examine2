# FAST-AI-RUNTIME-REPORT-READ-95 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-05T03:58:47+08:00`
- Delivery mode: function-first parallel Core/Module owner, AI orchestration
  and frontend lanes; responsive, accessibility, export controls and visual
  adaptation remain deferred.

## Delivered

- Added the single strict `RUNTIME_REPORT_QUERY` system Agent operation. Its
  provider plan has exactly `operation,moduleCode,reportCode,page,size`, with
  bounded page/size, policy operation/module gates and rejection of extra
  identity, field, filter, sort, SQL, URL, export or ownership inputs.
- Added the narrow Core `AiRuntimeReportReadFacade` and Module-owned non-final
  `AiRuntimeReportReadAdapter`. The adapter requires live runtime/module-view
  permissions, resolves the current tenant's active report once, verifies the
  real module, intersects currently readable report fields with policy outbound
  fields in report order, and then reads the exact report publication through
  the existing `ReportRuntimeService` overload.
- Added one mutually-exclusive safe `runtimeReport` result with report/source
  version metadata, bounded page facts, a native route and ordered fields/rows.
  Rows contain only `fieldCode` plus an explicitly nullable `displayValue`;
  raw `Object value`, query hashes, internal ids and record envelopes never
  cross the owner boundary.
- Extended the existing hash/count-only Agent tool ledger with
  `V8_75_0__ai_runtime_report_tool_name.sql`. No Report/DataSource/Record table,
  permission, policy column, index, second audit store or confirmation state
  was added.
- Added one functional read-only Agent result card and policy option. It
  preserves large decimals and masked strings, renders null explicitly, keeps
  field order and links once to the native report. It adds no endpoint,
  service, router, secondary fetch, pagination, export, chart or responsive
  redesign.

## Verification

- Core/Module focused owner regression passed `10/10` (`4` Core contract and
  `6` Module adapter tests), including an explosive raw-value sentinel. Final
  Core + AI full regression passed `177/177` (`78` Core and `99` AI tests);
  strict parser/orchestration focus passed `12/12`.
- All `14` Maven modules and their tests compiled successfully.
- The independent MySQL 8.4 schema journey passed `1/1` in `66.01s`, applied
  the expected `55` post-V8.20 migrations through V8.75, validated all `97`
  migrations and proved a repeated migration performs zero work.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible provider Flow
  HTTP journey passed `1/1` in `212.2s` test time (`4:02` reactor total).
- Frontend full verification passed `101` files / `502` tests. Typecheck and
  the production build passed over `5,766` transformed modules; the only
  message was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, seven cadence cases, reusable-base Structure, instance
  Active and both VS4 `43`-endpoint machine-contract validators passed.
- No project package checkpoint was attempted.

## Demonstrated journey

The journey reuses the real published `flow_empty_route_report` active v2 and
compares an Agent page with the simultaneous native metadata and rows. Report,
source and module/version facts match; authorized fields remain in
`event_time,route` order; every non-null display string matches exactly and a
native omitted/null display becomes an explicit Agent JSON null. The Agent
result contains no raw value, record id, query hash, internal id or other
context branch.

The journey then disables the live module-view permission and proves the same
plan fails as `PERMISSION_DENIED` without a summary-provider call. It publishes
a distinct report only in the isolated tenant, switches back and proves the
Agent returns `REPORT_NOT_FOUND` with no summary. Tool rows are exactly one
`SUCCEEDED/OK/result_count=3` plus the two expected
`FAILED/result_count=0` rows. Exact success and failure/isolation windows leave
current-tenant report and source root/publication counts and version sums
unchanged, and durable Agent projections contain none of the prompt markers,
report codes, display rows or result JSON.

## Integration corrections

- Full AI regression required preserving the established record-activity
  prompt phrase that runtime identity and page 1 are server-owned. It was
  restored only for comment/history/file reads, while report paging remains an
  explicit bounded provider input.
- The Web journey's new report-count snapshot initially reused an existing
  daily-report variable name. It was renamed to distinguish Work daily reports
  from Module published reports before the full reactor was rerun.
- Global NON_NULL serialization initially omitted a null report display cell.
  The safe cell component now explicitly serializes `displayValue:null`, with
  a dedicated regression test; the native comparison accepts its existing
  missing-or-null representation while requiring the Agent's explicit null.
- The original broad zero-write window also contained an expected existing
  `CONFIG_REPORT_DRAFT` mutation. It was split into exact success and
  post-mutation failure/isolation windows so only read side effects are tested.
- These were integration and contract corrections, not project package
  checkpoint attempts.

## Completion boundary

This accepts only the runtime published-report Agent read node. It does not
accept a project package checkpoint, release gate or final user acceptance.
Configuration suggestions and the remaining evidence-ranked functional gaps
stay available; responsive and visual hardening remain late.
