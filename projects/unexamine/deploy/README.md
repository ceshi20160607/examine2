# Stable deployment and rollback

The fixed release package is a directory containing `manifest.json`, `unexamine-server.jar`, `frontend/`, `guides/`,
`scripts/`, `db/` and non-secret environment examples. Build it with `scripts/build-package.ps1` from the current JAR and
frontend `dist`; the script derives the Flyway version, hashes the JAR, frontend server and every frontend file, then creates a ZIP and
`*.zip.sha256.json` digest record. The frontend process serves `<deployment-root>/www` and proxies same-origin `/api` and `/actuator` requests to the backend.
Actual configuration and credentials are never bundled. Development, test, and production non-secret examples live under `env/`;
production credentials and the application signing key must be injected by the environment's secret provider.
For first installation, inject `APP_BOOTSTRAP_DEFAULT_ADMIN_DEPLOYMENT_KEY` with at least 24 characters before starting the
backend. The bootstrap refuses to create the default administrator without it; subsequent restarts do not reset that account.

`scripts/deploy.ps1` validates the target environment, approval phrase, immutable JAR and frontend SHA-256, database recovery strategy,
fixed artifact names and the Flyway migration files inside the JAR. For the existing local `mysql` container, pass
`-DockerMySqlContainer mysql` to deploy, backup and rollback; these commands use its built-in client and root credentials.
For an external MySQL service, pass `-MySqlDefaultsFile` for a protected host MySQL client defaults file.
Before switching the release pointer, the script reads `flyway_schema_history`, rejects failed or newer
database versions, and requires `-BackupResultPath` from `backup.ps1` whenever an existing database or configuration
version will advance. The backup must match the currently deployed source versions. It
checks all four backup files against their recorded sizes and SHA-256 values. `-PreflightOnly` runs these checks without
stopping services or changing the release pointer. After startup it checks that Flyway reached the manifest version.
A failed deployment with an unchanged database restores the previous application/static pointer. If the database advanced
or its state cannot be read, or the configuration version changed, the service stays stopped and `runtime/last-deployment.json` records the backup and required
forward-fix or explicit restore path. `scripts/rollback.ps1` requires an
explicit deployment id and the selected MySQL client mode, checks the live Flyway and target configuration versions before stopping the service, creates a pointer snapshot, restores the application/static/config compatibility group only when those versions match, restarts the fixed entry point, and persists rollback evidence; a failed rollback
restores the pre-rollback version. Runtime logs remain
under `<deployment-root>/logs`; release, deployment, and rollback pointers remain under `<deployment-root>/runtime`.
The package starts exactly two application processes: Java 21 backend and Node 24 frontend. The backend port is fixed at
`9999`; keep `APP_SERVER_PORT=9999` in the environment file. Set `APP_FRONTEND_PORT` and optionally
`APP_FRONTEND_HOST`; the default frontend binds `127.0.0.1:15173` and proxies to `127.0.0.1:9999`.
Set `APP_JAVA_EXECUTABLE` and `APP_NODE_EXECUTABLE` only when the executables are not on `PATH`.
Reuse the already running Docker MySQL and Redis through `APP_DB_URL`, `APP_DB_USERNAME`, `APP_DB_PASSWORD`,
`APP_REDIS_HOST`, `APP_REDIS_PORT`, and `SPRING_DATA_REDIS_PASSWORD`. Create only a separate application database when
an isolated installation is needed. The package neither starts nor includes MySQL, Redis or Nginx containers.

For a local release, prepare an environment file from `env/development.env.example` with the existing container credentials
and an existing database name in `APP_DB_URL`. From the package directory run:

```powershell
& .\scripts\deploy.ps1 -DeploymentRoot 'C:\unexamine' -PackageDirectory . -Environment development `
  -EnvironmentFile 'C:\unexamine\local.env' -DockerMySqlContainer mysql `
  -ApprovalReference 'local-release' -Confirm 'DEPLOY <manifest.version> TO development'
```

Open the frontend at `http://127.0.0.1:15173/`. The database and Redis containers keep running when `stop.ps1` stops
the two application processes. Use the same `-DockerMySqlContainer mysql` argument for backup, rollback and restore drills.

Destructive database scripts are intentionally not executed by these wrappers. Store them separately, require a second
confirmation, create a database backup first, and attach the execution record to the approval reference.

`scripts/backup.ps1` creates a single-transaction MySQL dump, archives file storage, writes a non-secret configuration
snapshot and a reference-only secret recovery plan, then emits four independently hashed artifacts plus a timezone-qualified
ISO-8601 consistency point. Enter that point on the operations page; the manifest persists the same instant and reads it back
in the runtime timezone.
Pass credentials through a protected MySQL `--defaults-extra-file`; they are never written to the result manifest.
The container mode streams the dump directly to the backup directory without creating another container.
Keep the named container and its data volume available throughout the backup.
`scripts/restore-drill.ps1` requires `-TargetHost`, a new `-TargetDatabaseName` beginning with `unexamine_drill_`,
and an environment code different from the source. Use `-DockerMySqlContainer mysql` with the existing local container,
or `-MySqlDefaultsFile` with a host MySQL client. It re-verifies every size and SHA-256, imports
`database.sql` into that new database, compares restored table count and Flyway version with the backup, extracts files,
checks the configuration snapshot, and proves that secret material is absent with an explicit rotate-on-restore strategy.
For a production source, the drill database host must also be different. Failed imports leave the isolated database and a
`failure.json` for diagnosis; the script does not mark the drill passed. Paste its `verification.json` into the operations
page; the backend checks the restored-version evidence and four artifact hashes against the registered manifest. Production
backup storage must provide encryption at rest for the key reference recorded by these scripts.

`scripts/security-performance.ps1` runs the persisted security baseline, prepares or activates a two-phase secret rotation,
and executes bounded database, Redis, file, job and statistics checks. The prepare action prints secret material once to the
authorized operator and never writes it to evidence; every later response contains only the reference, version and status.

For local acceptance, open the configured frontend URL after `deploy.ps1` reports backend readiness and the frontend
smoke check. `stop.ps1` stops only the two recorded application processes; existing MySQL and Redis containers stay running.

## Password recovery delivery

The sign-in page links to `/forgot-password`; a reset link opens `/reset-password?token=...` and expires after 30 minutes.
The database stores only the token digest, and a successful reset revokes all existing sessions. Anonymous requests give
the same response for unknown, disabled, and valid accounts. The frontend displays the delivery mode without identifying
which account matched.

In `development`, the default delivery mode is `LOCAL_OUTBOX`. The backend writes a mode-0600 JSON file under
`/tmp/unexamine-password-reset-outbox` (override with `APP_PASSWORD_RESET_OUTBOX_DIR`); only a server operator can read
the link. This mode can be explicitly enabled in `test` but is rejected in `production`. Do not expose the outbox through
the web server, include it in support bundles, or treat this local acceptance path as an email delivery check.

Production delivery requires `APP_PASSWORD_RESET_DELIVERY_MODE=WEBHOOK`, an HTTPS `APP_PASSWORD_RESET_PUBLIC_URL`,
an HTTPS `APP_PASSWORD_RESET_WEBHOOK_URL`, and `APP_PASSWORD_RESET_WEBHOOK_TOKEN` from the secret provider. The relay
receives a Bearer-authenticated JSON body with `recipientEmail`, `resetUrl`, and `expiresInMinutes` and must send the
message to that address. If this configuration is absent or the relay rejects a request, the API returns an explicit
503 error; no success notice is shown. Validate the real relay and mailbox before claiming external delivery complete.
