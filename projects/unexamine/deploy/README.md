# Stable deployment and rollback

The fixed release package is a directory containing `manifest.json`, `unexamine-server.jar`, and `frontend/`; copy
`package-manifest.example.json` to the package as `manifest.json` and replace its artifact digest with the real lowercase
SHA-256. The stable web-server document root is `<deployment-root>/www`.
Configuration is never bundled into that directory. Development, test, and production non-secret examples live under `env/`;
production credentials and the application signing key must be injected by the environment's secret provider.

`scripts/deploy.ps1` validates the target environment, approval phrase, immutable SHA-256, database recovery strategy,
fixed artifact names, atomically switches the static document root, waits for backend readiness, starts the stable entry
point, and runs a real-entry smoke check. A failed deployment restores the previous application/static pointer and persists
failure evidence without replacing the immutable release. `scripts/rollback.ps1` requires an
explicit deployment id, creates a pointer backup, restores the application/static/config compatibility group, enforces a
forward-fix or reversible database plan, restarts the fixed entry point, and persists rollback evidence; a failed rollback
restores the pre-rollback version. Runtime logs remain
under `<deployment-root>/logs`; release, deployment, and rollback pointers remain under `<deployment-root>/runtime`.

Destructive database scripts are intentionally not executed by these wrappers. Store them separately, require a second
confirmation, create a database backup first, and attach the execution record to the approval reference.

`scripts/backup.ps1` creates a single-transaction MySQL dump, archives file storage, writes a non-secret configuration
snapshot and a reference-only secret recovery plan, then emits four independently hashed artifacts plus a timezone-qualified
ISO-8601 consistency point. Enter that point on the operations page; the manifest persists the same instant and reads it back
in the runtime timezone.
Pass credentials through a protected MySQL `--defaults-extra-file`; they are never written to the result manifest.
`scripts/restore-drill.ps1` re-verifies every size and SHA-256, extracts files into an isolated drill directory, checks the
configuration snapshot and proves that secret material is absent with an explicit rotate-on-restore strategy. Paste its
`verification.json` into the operations page; the backend compares all four sizes and hashes with the registered manifest
before it persists a passed drill. Production
backup storage must provide encryption at rest for the key reference recorded by these scripts.

`scripts/security-performance.ps1` runs the persisted security baseline, prepares or activates a two-phase secret rotation,
and executes bounded database, Redis, file, job and statistics checks. The prepare action prints secret material once to the
authorized operator and never writes it to evidence; every later response contains only the reference, version and status.

For a fixed local acceptance entry, start Nginx with the frontend `dist` directory as its prefix and
`nginx.stable.conf` as the configuration. It serves port 15174 and proxies `/api` and `/actuator` to the fixed backend on 18080.
