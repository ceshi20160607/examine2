# Message Todo Log Schema

## Scope

Fragment: `sql/fragments/006-message-todo-log.sql`

This fragment owns todos, messages, message targets, notification templates, delivery logs, audit logs, and log export metadata.

## Tables

- `un_message_todo`: platform/system todo row with type, target, action permissions, and due status.
- `un_message_target`: jump target with platform/system boundary information.
- `un_message_notification_template`: template, variables, channels, target rule, quiet policy, and retry policy.
- `un_message_message`: message stream item.
- `un_message_delivery_log`: channel-level delivery state.
- `un_audit_log_export_task`: export task request metadata.
- `un_audit_business_log`: login, business, risk, import/export, Agent, OpenAPI, and ops audit record.

## Constraints

- Platform messages must not directly open system business details.
- System messages can open business targets only inside current `SystemSwitchContext`.
- Todo is an independent workbench with type tree and right-side list; it is not a business module sidebar.
- Logs share one log management entry and are filtered by log type.

## Acceptance Notes

- Message filters cover system, tenant, template, type, read, archive, time range, and keyword.
- Audit logs store trace ID, request ID, audit ID, object, result, IP/device, permission snapshot, and desensitize result.
