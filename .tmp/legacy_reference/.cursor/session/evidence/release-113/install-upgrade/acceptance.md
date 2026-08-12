# REL113-B/C clean-install and upgrade acceptance

- Verdict: `PASS`
- Executed: `2026-08-06T21:28:24+08:00` to `2026-08-06T21:32:39+08:00`
- Target: accepted Cycle112 archive, SHA-256 `c116b4e24e73096dc47aee53b9d92259a18ef81e2a43a2c172a9139c6917f9e0`
- Runtime: packaged `backend/examine-web.jar`, Java 21, MySQL `8.4.10`, Redis `7.4-alpine`
- Scope isolation: no business code, shared release state/progress/current-node, formal checkpoint attempt, or formal package was changed.

## REL113-B: clean install and restart read-back

The accepted Cycle112 package was started against an empty, independently named MySQL/Redis pair. Health reached `UP`; Flyway recorded exactly `104/104` successful migrations, installed rank `104`, current version `8.82.0`, zero failed migrations and `193` application tables.

The packaged backend then executed a representative authenticated write/read journey:

1. bootstrap-root login returned `OK` in `PLATFORM` context and retained `platform.system.manage`;
2. `POST /api/v1/platform/admin/systems` created durable system code `rel113-clean-sentinel`;
3. the Java process was stopped and the same packaged backend restarted against the same database;
4. a fresh login and `GET /api/v1/platform/admin/systems` returned the exact persisted system ID/code;
5. the restart kept `104/104`, rank `104`, version `8.82.0` and zero failed migrations.

## REL113-C: preserved-data upgrade

The retained package inventory proves Cycle108 is the earliest accepted executable package currently available with a matched backend binary and migration directory. It was therefore selected as the earliest operationally supportable baseline, rather than claiming an older source-only fixture as a release baseline.

The real path passed:

- Cycle108 started clean at `100/100`, rank `100`, version `8.78.0`, zero failed migrations and `190` application tables.
- Through the Cycle108 public API, the root account created system `rel113-upgrade-sentinel`, switched into it and obtained `SYSTEM_ADMIN` plus `SYSTEM_RUNTIME` shells and an exact 50-permission set.
- Cycle108 stopped; Cycle112 started on the same MySQL data and applied exactly four forward migrations.
- The target reached `104/104`, rank `104`, version `8.82.0`, zero failed migrations and `193` application tables.
- The original credential still logged in; root account ID, system ID/code/name, exact 50-permission set and exact shell set were preserved.
- The three new tables `un_plat_password_recovery_token`, `un_event_message_delivery_attempt`, `un_event_channel_configuration` and file index `idx_file_object_scope_status_created` were present.

## Boundary and follow-up evidence

Upgrade support before V8.78.0 is not claimed. Older migration/fixture coverage exists in source, but no older accepted executable binary plus matched migration package is retained under `.cursor/session/packages`. To promote an earlier baseline, recover or rebuild a versioned immutable package with provenance/hash, seed durable data through that version's public API, and rerun this same clean/upgrade contract from that package to Cycle112. This is a release-evidence boundary, not a failure of the proven V8.78.0-to-V8.82.0 path.

All four exact containers, both Java application processes plus restarts, six dedicated ports and the temporary runtime/log directory were removed. The cleanup probe found no residual named container, listener or Java process.

Authoritative machine-readable evidence:

- `result.json`
- `baseline-inventory.json`
- Reproducible gate: `.cursor/scripts/release-install-upgrade-gate.ps1`
