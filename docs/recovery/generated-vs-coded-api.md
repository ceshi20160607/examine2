# Generated Vs Coded API Boundary

Time: 2026-06-27 Asia/Shanghai

## Purpose

The generator is useful, but it is not a product implementation. This file defines what may be generated and what must be coded by hand.

## Generated Layer

Generated code may provide:

- Entity mapping
- Mapper
- Mapper XML
- Base service
- Base service implementation
- Simple persistence helpers
- Table/column typing

Generated code may not be accepted as a complete P0 feature.

## Coded Business Layer

The following must be coded and tested as business services:

- Authentication, password rules, token creation, token validation, logout, password reset
- Platform root bootstrap
- System creation lifecycle
- System switch and tenant switch context
- Account-member binding and role assignment semantics
- Effective permission calculation and explanation
- Field/action/data-scope enforcement
- Module create/configure/publish/check/rollback
- Runtime schema generation from published config
- Dynamic record create/update/search/detail/history
- Draft persistence and automatic sequence allocation
- Attachment binding and upload access logging
- Flow definition canvas validation and publish
- Workflow instance/task/action/idempotency
- Todo and message creation tied to business actions
- Message read/archive/filter semantics
- Notification templates and delivery logs
- Business/login/audit logs with trace/request ids
- Import/export async task lifecycle
- SecretRef and secret rotation job semantics
- SSO provider/system policy/no-member request lifecycle
- OpenAPI app scope/rate-limit/call-log/secret behavior
- Work dashboard/project/plain task/report behavior
- AI Agent model authorization, policy scope, session, confirmation, and audit behavior
- Release health gate and startup cleanup behavior

## Acceptance Boundary

Generated base CRUD is accepted only when all are true:

- It is called by a coded business service.
- The business service has a task card.
- The frontend uses the business API, not the generated base endpoint directly.
- Permission and validation are enforced outside the generated layer.
- The acceptance script proves role action, persistence, readback, and negative cases.

## Examples

### Module

Generated:

- `un_module`
- `un_module_field`
- base mapper/service

Coded:

- create module with ownership and status
- validate field code uniqueness
- publish version
- expose runtime schema
- enforce role visibility
- redraw business shell after publish

### Record

Generated:

- `un_module_dynamic_record`
- `un_module_dynamic_value`
- base mapper/service

Coded:

- create record from published schema
- validate required/typed fields
- apply field permission
- allocate automatic number
- write history
- search with data scope
- open detail with approval sidebar

### Approval

Generated:

- flow instance/task/action tables and base persistence

Coded:

- submit approval from a record action
- create workflow instance
- create pending task
- create todo and message in the same transaction or reliable outbox
- enforce approver identity
- idempotent approve/reject/transfer
- write audit and history

## Failure Rule

If a P0 behavior is implemented only by generated CRUD or a generic controller, mark it `API_ONLY` or `FLOW_BROKEN` in `current-product-audit.md` and create a coded task card.
