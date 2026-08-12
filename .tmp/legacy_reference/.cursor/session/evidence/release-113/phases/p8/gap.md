# P8 dashboard and report phase gap report

- outcomeId: `P8_PHASE_ACCEPTANCE`
- proposed verdict: `NOT_PROVEN`
- promotion safety: `DO_NOT_PROMOTE`

## Frozen requirements

P8 owns configurable dashboards, data sources, statistics/charts, KPI and reports (`REQ-DASH-001`, `REQ-DATASOURCE-001`, `REQ-KPI-001`). The frozen product contract includes system/application/module/personal dashboards, per-widget source/stat/filter/style/refresh/click rules, multi-module sources and external API/database sources as downstream consumers.

## Evidence audit

| requirement | evidence | assessment |
|---|---|---|
| Operations metrics, ECharts trends and real Work/Flow/Todo drill-down | fast acceptance 60 | `PROVEN` |
| Native source publication, scalar/group/trend statistics and immutable dashboard versions | fast acceptances 61-63 | `PROVEN` |
| KPI targets/calculation/widgets/reminders | fast acceptances 64-66 | `PROVEN` |
| Report publication/run/XLSX/schedule/delivery | fast acceptances 67-69 | `PROVEN` |
| HTTP JSON and JDBC_TABLE source lifecycle with security bounds | fast acceptances 103-107 and Cycle108 | `PROVEN` as source lifecycle |
| Application/module/personal dashboards and personal saved view | `docs/user_requirement.md` §§906-968; `REQ-DASH-001` | `MISSING`: accepted dashboard storage/runtime is `SYSTEM_HOME`; no acceptance covers the other frozen dashboard scopes. |
| Ranking/progress/Todo/quick-entry widgets, per-widget refresh and click-through builder, drag layout | same requirement source; fast acceptances 62-65 Deferred sections | `PARTIAL/MISSING`: charts and one operations drill exist, but the complete configured widget and interaction catalog is not proven. |
| Multi-module data-source joins and use of HTTP/JDBC sources by dashboard/KPI/report | `docs/user_requirement.md` §§969-988 and A.5; Cycle108 Deferred; report acceptance Deferred | `CONTRADICTED`: Cycle108 explicitly keeps JDBC out of downstream statistics/catalogs, and report evidence explicitly defers external data-source execution. |

## Blocking gap

The current native analytics stack is functional, but it does not satisfy the frozen dashboard scopes or downstream use of external/multi-module data sources. A standalone previewable JDBC/HTTP source is not yet a dashboard/report data source.

## Required closure evidence

Add downstream source capability negotiation/execution for HTTP/JDBC (and multi-module joins), plus the missing dashboard scopes and configured interaction/widget contracts. Verify authorization, immutable pins, partial failure, drill-through and browser/runtime behavior.

