# Test Strategy

## Scope

This strategy covers build tasks after prototype approval and API freeze. It is intentionally contract-first: every implementation slice must show evidence that it follows `docs/api/api.md`, the task output paths, and the project operating rules.

## Test Layers

1. Static acceptance: files exist, route targets exist, duplicated primary actions are removed, SecretRef rules are respected, and row-click rules are not over-applied.
2. Contract checks: TypeScript and Java protocol objects align with API domains, paging, filters, sorters, async tasks, trace IDs, audit IDs, and disabled reasons.
3. Backend build checks: Maven compile per batch and targeted smoke tests for API slices.
4. Frontend build checks: TypeScript build, shell route registry, shared components, and browser smoke once runtime views exist.
5. Permission checks: platform admin root, platform member, system creator super admin, and normal system member `che`.
6. End-to-end checks: login, system switch, module list/detail, todo, message jump, workflow approval, import/export task, work management, SSO no-member request, and Agent confirmation.

## Batch Evidence

- G0: backend scaffold compile, frontend scaffold build, SQL fragment existence, QA docs existence.
- G1-G3: SQL merge, generated base files, platform identity/auth/permission/log APIs, clean build.
- G4-G6: admin config, runtime record, flow/todo/message, import/export, SSO/OpenAPI, clean build.
- G7-G9: work management, Agent, frontend runtime, backend integration smoke.
- G10: user-facing E2E with the `che` normal member path.

## Required Result Fields

- `requestId` and `traceId` for every API response.
- `auditLogId` for writes, approval, permission denial, import/export, Secret rotation, OpenAPI, and Agent actions.
- `disabledReason` for blocked buttons and unavailable actions.
- `taskId` and status details for long-running operations.

