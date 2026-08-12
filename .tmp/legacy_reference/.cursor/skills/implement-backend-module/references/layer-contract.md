# Backend layer contract

## Ownership

| Layer | Owns | Must not own |
|---|---|---|
| `base.entity` | Table-shaped persistence model | Product workflow or permission decisions |
| `base.mapper` + mapper XML | Generated columns, ordinary CRUD mapping | Cross-use-case orchestration |
| `base.service` | Generated generic persistence service | User-visible product semantics |
| `repository`, `adapter.jdbc`, custom mapper XML | Named special query or persistence operation not covered by Base | Navigation, UI decisions, unrelated aggregate workflows |
| `domain` | Pure business rules and state invariants | JDBC, HTTP, framework row mapping |
| `manage` or `application` | One use case: authorization, transaction, state, side effects, readback coordination | SQL text, `JdbcTemplate`, `ResultSet`, dynamic table names, generic CRUD duplication |
| `controller` or API adapter | Request parsing, validation envelope, response mapping | Transactions or business rules |

## Base-first decision

Use this order for every persistence action:

1. Reuse generated Base CRUD or query wrappers.
2. Configure Base with predicates, pagination, ordering, and supported projections.
3. Extend through a small named repository/mapper operation.
4. Override only when an accepted architecture/data contract proves Base cannot express the operation.
5. Use `project_only` only when the capability is intentionally not reusable and state why.

Do not handwrite a repository simply because the generated service was not inspected.

When legacy generated packages already contain handwritten business persistence and cannot be cleaned without expanding unrelated old features, freeze the whole legacy package and create one clean versioned package root. New use cases may depend only on that versioned Base. A scoped audit is allowed only when the accepted architecture contract names both the frozen tree and replacement root; never mix clean and contaminated generated files in one package.

## Complexity stop conditions

Return to architect/DBA review before adding more code when any condition holds:

- one use-case service coordinates unrelated aggregates or unrelated user outcomes;
- the same table/column mapping is repeated in several services;
- application code constructs table or column identifiers dynamically;
- a write transaction has no single business invariant that explains all touched tables;
- a service requires custom SQL for ordinary CRUD already available in Base;
- deleting or updating a record needs broad scans because ownership, foreign keys, lifecycle, or denormalized read models are unclear;
- the only way to test a business rule is a full application integration test.

The review chooses among a better aggregate boundary, a normalized write model, an intentional denormalized read model, a dedicated repository, or a split use case. It does not automatically redesign tables.

## Test ownership

| Delivery level | Backend responsibility |
|---|---|
| task | Affected business unit tests, affected mapper/repository test when custom persistence changed, affected compile |
| four-hour cycle | One combined API/data integration, positive readback, permission positive/negative when affected |
| module | Complete module journey through the page/API and persistent state |
| phase/project | Only the promised phase/project matrix; do not copy every task command |

Failure-path, validation, and permission-denial tests cannot by themselves close a positive business case.
