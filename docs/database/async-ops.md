# Async Ops Schema

## Scope

Fragment: `sql/fragments/009-async-ops.sql`

This fragment owns async tasks, task events, task files, idempotency keys, ops jobs, feature flags, quotas, rate limits, backup/restore jobs, archive/restore jobs, health checks, and deploy rollback records.

## Tables

- `un_sys_async_task`: unified backend task state.
- `un_sys_async_task_event`: progress, retry, cancel, rollback, and failure events.
- `un_sys_async_task_file`: result and error file references.
- `un_sys_idempotency_key`: idempotency request cache.
- `un_ops_health_check`: system/platform health check results.
- `un_ops_feature_flag`: feature flag and gray release state.
- `un_ops_quota`: capacity quota configuration.
- `un_ops_rate_limit_policy`: rate limit configuration.
- `un_ops_backup_restore`: backup, restore, and restore drill lifecycle.
- `un_ops_archive_restore_request`: archive and restore lifecycle.
- `un_ops_deployment`: deploy and rollback evidence.
- `un_ops_api_cache_policy`: API cache policy.

## Constraints

- Long-running operations return `AsyncTask`.
- Task status machine is fixed: `QUEUED`, `RUNNING`, `SUCCESS`, `PARTIAL_SUCCESS`, `FAILED`, `CANCELED`, `ROLLBACKING`, `ROLLED_BACK`.
- Idempotency keys prevent duplicate writes and duplicate async tasks.
- Ops actions record trace ID and audit ID.

## Acceptance Notes

- Indexes cover biz type, status, requester, retryable/cancelable, trace ID, idempotency key, feature flag, quota, rate limit, and ops job lookups.
- Result and error files are references, not embedded payloads.
