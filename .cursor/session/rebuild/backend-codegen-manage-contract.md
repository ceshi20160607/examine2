# Backend Codegen And Manage Contract

## Status

Task: `REQ-R0-005 Backend Codegen And Manage Contract`

Status: leader draft complete

This file freezes the backend coding approach for future implementation. It does not start coding.

Current phase: `requirements-rebuild`. Coding remains closed.

## Source Files

- `.cursor/architecture/backend-structure.md`
- `.cursor/session/rebuild/engineering-architecture-map.md`
- Old reference only: `.oldbk/backend/`, `.oldbk/restart-20260708-220451/backend/`

## Contract Summary

Future backend work is schema-first, generator-backed, and manage-handwritten:

1. DBA/schema work freezes the tables for one task before backend coding starts.
2. `examine-generator` generates the mechanical `base` layer from those tables.
3. Backend writes product behavior only in the handwritten `manage` layer.
4. API controllers expose `manage` BO/DTO/VO models, never generated `base.entity` classes.
5. Acceptance proves both boundaries: generated base is reproducible, and manage behavior is correct.

Generated base is infrastructure, not product completion. A task is not accepted until manage behavior, permissions, states, persistence/readback, and side effects are proven.

## End-To-End Flow

### 1. Schema-First Freeze

Before a backend coding task starts, the task must name:

- schema source path, normally `sql/init.sql` or a task-specific DBA migration/DDL path;
- table group and table prefix, such as `un_module_*`;
- owning backend module;
- relevant product/API contract path;
- whether the schema has already been executed against the local task database.

Backend must not invent missing tables in Java code. If a table, column, index, status enum, or relationship is unclear, the task returns to DBA/schema clarification before implementation.

### 2. Generator Base

After schema freeze, backend runs `examine-generator` for the owning module and table prefix. The command is the configuration: module name, table prefix, base package, Java source root, mapper XML root, backend root, and execute/dry-run flag must be explicit.

Generated scope is limited to:

- `base/entity`;
- `base/mapper`;
- `base/service`;
- `base/service/impl`;
- `src/main/resources/mapper/base`;
- other mechanical CRUD plumbing only if the generator owns it.

The generator must not create external API controllers. Generated entity classes are persistence models only.

Schema changes require regeneration. Hand-editing generated fields, mappers, services, or XML is forbidden except for a tiny documented patch with explicit evidence explaining why regeneration could not represent it.

### 3. Handwritten Manage

Backend writes product behavior under the owning module's `manage` package:

- `manage.controller` for external API entry points;
- `manage.service` for business orchestration;
- `manage.bo` / `manage.dto` for request input;
- `manage.vo` for response output;
- `manage.enums` when business-facing states or actions need named types.

Manage owns permission checks, context checks, transactions, domain validation, state transitions, entity-to-VO conversion, readback, and named side effects such as todo/message/log/callback writes.

Manage may call generated base services/mappers internally, but must not expose generated entities in controller method signatures, API docs, or response payloads.

### 4. Wiring

`examine-web` is startup and dependency assembly only. It may wire modules, web config, filters, interceptors, exception handling, and security infrastructure. It must not contain product business services, product controllers, or module-specific CRUD.

### 5. Acceptance

Every future backend implementation task must include evidence for:

- schema/table source and freeze point;
- exact generator command or script invocation;
- generated base paths and readback of generated files;
- handwritten manage paths;
- API contract paths;
- positive and negative permission checks;
- context separation, especially platform context vs system member context;
- persistence write and readback;
- state transition or explicit "no state" proof;
- side-effect proof or explicit "no side effects" proof.

## Generator Command Contract

Single-module generation follows this shape:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
cd backend
mvn.cmd -pl examine-generator -DskipTests exec:java `
  "-Dexec.mainClass=com.unique.examine.generator.cli.GeneratorCli" `
  "-Dexec.args=--backend-root . --module-name examine-module --table-prefix un_module_ --base-package com.unique.examine.module.base --source-root examine-module/src/main/java --mapper-xml-root examine-module/src/main/resources/mapper/base --execute"
```

For acceptance, future coding tasks must record the actual command used, not just say "ran generator".

Required command evidence:

- shell command or script path, for example `.oldbk/backend/examine-generator/scripts/generate-base-crud.ps1` as reference or the rebuilt equivalent;
- `--backend-root`;
- `--module-name`;
- `--table-prefix`;
- `--base-package`;
- `--source-root`;
- `--mapper-xml-root`;
- `--execute` or dry-run mode;
- command exit status and any generated-file summary printed by the generator.

## Module Matrix

| Module | Table Group | Base Generation Range | Handwritten Manage Business Range | Required Acceptance Evidence |
|---|---|---|---|---|
| `examine-plat` | `un_plat_*` | platform account, system, tenant, platform role, and platform log base CRUD if tables exist | platform accounts, systems, tenants, platform roles, platform log summary | generator command for `un_plat_`; generated base paths under `examine-plat`; manage controller/service/BO/VO paths; create/update/readback for account/system/tenant/role; platform-context permission allow/deny; status changes such as enabled/disabled; audit/log side effect |
| `examine-module` | `un_module_*` | module group, module, field, action, page, print template, runtime record, and module permission base CRUD | module groups, modules, fields, actions, pages, print templates, runtime records, module permissions | generator command for `un_module_`; generated base paths under `examine-module`; manage paths; schema readback for module metadata; runtime record write/readback; permission allow/deny; states such as draft/published/disabled where applicable; import/export or log side effects when applicable |
| `examine-flow` | `un_flow_*` | flow definition, canvas/node/edge, publish record, runtime instance, approval task base CRUD | flow canvas, publish checks, approval/runtime orchestration | generator command for `un_flow_`; generated base paths under `examine-flow`; manage paths; save draft and readback; publish pass/fail evidence; start instance and approve/reject evidence; todo/message/log side effects |
| `examine-app` | `un_openapi_*` | application credential, scope, authorization, callback, and call log base CRUD | Application authorization gateway, credentials, scopes, callbacks, call logs | generator command for `un_openapi_`; generated base paths under `examine-app`; manage paths; app/credential/scope persistence and readback; gateway allow/deny by permission and scope; enabled/disabled or revoked state evidence; callback/call-log side effects |
| `examine-upload` | `un_upload_*` | file, attachment, file reference, preview/download metadata base CRUD | upload, file references, preview/download permissions | generator command for `un_upload_`; generated base paths under `examine-upload`; manage paths; metadata persistence and readback; preview/download allow/deny; file reference binding/unbinding; log side effect when required |
| `examine-core` | `un_sys_*`, `un_audit_*` | shared system, audit, context, or dictionary base CRUD if tables exist | shared API result, context, audit, exception, and cross-module support | generator command for `un_sys_` and/or `un_audit_` when those tables are in scope; generated base paths under `examine-core`; context/audit support paths; platform vs system context proof; audit persistence/readback; no product business leakage |
| `examine-web` | none | none | startup, web configuration, dependency assembly only | no generator command required; changed paths limited to startup/wiring when coding is open; proof that business controllers/services remain in owning modules |
| `examine-generator` | none | owns generation CLI/templates/scripts, not generated business base for itself | generator behavior only, no runtime web feature | command output, generated marker/path proof, repeatability proof, and no runtime dependency from web to generator |
| Work / Todo / Message / AI | recommended new modules `examine-work`, `examine-todo`, `examine-message`, `examine-ai` with proposed `un_work_*`, `un_todo_*`, `un_message_*`, `un_ai_*` | none until architecture ownership is formally approved in contract phase | work statistics/tasks/reports; todo/reminder/action center; message delivery/read state; AI session/policy/tool orchestration | contract task must first record module approval, table group, permission model, states, side effects, generator command, base paths, manage paths, and evidence; do not patch this business into `examine-web` |

## Path Contract

Generated base Java paths:

```text
backend/{module}/src/main/java/com/unique/examine/{domain}/base/
  entity/
  mapper/
  service/
  service/impl/
```

Generated mapper XML paths:

```text
backend/{module}/src/main/resources/mapper/base/
```

Handwritten manage paths:

```text
backend/{module}/src/main/java/com/unique/examine/{domain}/manage/
  controller/
  service/
  bo/
  dto/
  vo/
  enums/
```

API paths:

```text
docs/api/api.md
```

or a task-declared API contract path. The task must point to the exact API section it implements.

## Future Coding Task Evidence Pack

Every backend coding task must prove the following before acceptance:

| Evidence Type | Required Proof |
|---|---|
| Schema | DDL/migration path, table list, table prefix, owning module, and schema freeze status |
| Generator command | Exact command or script invocation, explicit generator args, exit status, and generated summary |
| Generator paths | Generated Java root, mapper XML root, package name, and representative generated files read back from disk |
| Generator boundary | Evidence that base has no external controller and generated entities are not used as API models |
| Manage paths | Handwritten controller/service/BO/DTO/VO/enums paths under the owning module |
| API contract | API doc path and request/response contract using manage models |
| Persistence/readback | A write operation plus a readback operation proving stored data, not only in-memory response |
| Permission | At least one allowed actor/context and one denied actor/context for protected behavior |
| Context | Platform context and system member context handling, or proof that only one context applies |
| Status/state | Initial state, transition action, final state, and invalid transition rejection; or explicit "no state in this task" |
| Side effects | Todo/message/log/callback/audit/outbox/direct-write side effects named and verified; or explicit "no side effects in this task" |
| Transaction | Transaction boundary for multi-table writes and rollback/idempotency expectation when relevant |
| Readback | File readback for generated artifacts and data readback for business persistence |

The acceptance report must include command/path/readback/permission/status/side-effect evidence in one place. Missing evidence blocks task acceptance even when code compiles.

## Prohibitions

- Do not handwrite bulk `base/entity`, `base/mapper`, `base/service`, or mapper XML.
- Do not expose generated `base.entity` classes as controller input/output or API documentation models.
- Do not put product business implementation in `examine-web`.
- Do not use `examine-generator` as a runtime web feature.
- Do not silently edit generated output to avoid Bean conflicts; fix scanning, package naming, or generator configuration instead.
- Do not start coding while `requirements-rebuild` or design/API gates are closed.

## Gate Relationship

| Gate | Backend May Do |
|---|---|
| `design_user_approved = false` | nothing |
| `requirements-rebuild` | requirements/contracts only, no backend/frontend/sql implementation |
| `api_frozen = true` | build skeleton, run SQL, generate base, write manage, only inside task outputs |
| single coding task | only paths explicitly declared by that task |

## Current Gate

Coding remains closed. This contract only prepares the future backend coding rule.
