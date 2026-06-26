# Contract Fixtures

## Shared Response

Every fixture response uses:

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "req_fixture_001",
  "traceId": "trace_fixture_001",
  "auditLogId": "audit_fixture_001",
  "data": {}
}
```

## Permission States

- Allowed action: `enabled=true`, no disabled reason.
- Denied action: `enabled=false`, `disabledReason=missing permission`.
- Masked field: field permission `MASKED`.
- Hidden field: field permission `HIDDEN`.
- No-member SSO: response contains `NoMemberAccessRequest` and no business context.

## Message Targets

- Platform message target: `scope=platform`, target type is `system_switch`, `platform_task`, `platform_log`, `platform_auth`, or `agent_result`.
- System message target: `scope=system`, target type can be `business_record`, `approval_task`, or `async_task`.
- Platform messages never open system business detail directly.

## Async Task States

Use all fixed states in tests: `QUEUED`, `RUNNING`, `SUCCESS`, `PARTIAL_SUCCESS`, `FAILED`, `CANCELED`, `ROLLBACKING`, `ROLLED_BACK`.

## Secret Safety

Secret fixtures include `secretRefId`, `version`, `expiresAt`, `rotationStatus`, and `lastUsedAt`. They never include plaintext secret values.

