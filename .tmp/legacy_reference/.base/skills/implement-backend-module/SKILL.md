---
name: implement-backend-module
description: Implement or refactor Java/Spring backend modules by reusing generated Base persistence, keeping SQL inside mapper/repository adapters, keeping manage/application services focused on business use cases, and selecting affected unit tests. Use before or during backend feature work, when a service contains inline SQL or ResultSet mapping, when generated CRUD is being bypassed, when a manage service becomes large, or when a data model may be forcing accidental complexity.
---

# Implement Backend Module

Implement one frozen backend task without turning persistence plumbing into handwritten business code.

## Workflow

1. Read the task's requirement IDs, acceptance case IDs, data/API contract, allowed changes, forbidden changes, module scope, and Base bindings. Stop if product semantics or data ownership remain unresolved.
2. Read [references/layer-contract.md](references/layer-contract.md). Inventory the affected tables, generated Base assets, aggregate/use-case boundary, special queries, transaction, permission, state, errors, and side effects.
3. Reuse or regenerate table-shaped Base code before writing business code. Select and record one binding for each persistence need: `reuse`, `configure`, `extend`, `override`, or `project_only`. Treat `override` and `project_only` as exceptions requiring a reason.
4. Put authorization, validation, transaction control, state changes, and side-effect orchestration in a small use-case service. Keep controllers as transport adapters.
5. Use generated `BaseMapper`/`IService` operations for ordinary single-table CRUD. Put genuinely special queries in a named mapper XML or repository/JDBC adapter. Never put SQL, `JdbcTemplate`, `ResultSet`, row mapping, dynamic table names, or schema discovery in controller, manage, application, or domain code.
6. Stop and return to data-contract review when a service must compensate for unclear ownership, repeatedly joins unrelated aggregates, builds identifiers dynamically, duplicates row mapping, or cannot state one transaction reason. Do not hide a modeling problem inside a larger service.
7. Add focused unit tests for business rules and affected repository/mapper tests for custom persistence. At task level, run only affected compilation and affected tests; defer combined integration to the four-hour cycle.
8. Run the static audit before handing off:

   ```powershell
   python scripts/audit_backend_module.py path/to/backend-module
   ```

   When an accepted architecture contract freezes an existing legacy tree and names one clean replacement package, scope the audit explicitly:

   ```powershell
   python scripts/audit_backend_module.py path/to/backend-module --source-prefix com/example/product/vnext
   ```

9. Report Base reuse, handwritten behavior, custom SQL reasons, transaction boundary, affected test command, audit result, and remaining scope. The independent verifier decides pass/fail.

## Non-negotiable rules

- Generated Base owns table-shaped Entity, Mapper, mapper XML, service interface, and service implementation.
- Business code may extend Base behavior but may not duplicate it by default.
- Manage/application/domain/controller code contains no inline SQL or JDBC row mapping.
- Custom SQL has a named business query purpose and lives behind a repository/mapper boundary.
- One use-case service owns one coherent business result; unrelated actions do not accumulate in a generic manager.
- A complex service triggers an aggregate/table review before further expansion.
- A build or CRUD response does not prove the business case; persisted readback is verified at cycle/module level.
- Do not run full regression, packaging, browser matrices, performance, capacity, or large-data tests for a backend task.

## Audit behavior

The audit fails on SQL/JDBC leakage outside recognized persistence adapters and on generated Base code containing business-layer or raw-SQL dependencies. It warns about oversized use-case services and broad custom-SQL adapters so a reviewer can require a data-model or responsibility split without inventing an arbitrary product change.

`--source-prefix` is not a general suppression switch. The excluded legacy tree must be named in an accepted project architecture contract, the replacement prefix must exist, and a prefix containing zero Java files fails the audit.

After changing the audit rules, run `python scripts/audit_backend_module.py --self-test` before using them on a project module.
