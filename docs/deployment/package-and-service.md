# Package And Service Standard

## Goal

The deliverable must be a deployable frontend-backend separated system package, not only a bare jar.

The standard release package must expose external backend configuration, provide a single backend service script next to the jar, include the frontend static package, and include an Nginx reverse-proxy reference for production deployment.

## Release Layout

`scripts/package-release.ps1` generates:

```text
release/unexamine-<version>/
  backend/
    examine-web.jar
    application.yml
    server.sh
    logs/
    data/uploads/
  frontend/
    index.html
    config.js
    assets/
  nginx/
    unexamine.conf
```

It also generates:

```text
release/unexamine-<version>.zip
```

## Backend Configuration

The backend startup script loads the external config directory next to the jar:

```text
--spring.config.additional-location=optional:file:<release>/backend/
```

Edit `backend/application.yml` for deployment-specific settings:

- `server.port`
- `server.servlet.context-path`
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.data.redis.password`
- `spring.data.redis.database`
- `spring.data.redis.timeout`
- `mybatis-plus.mapper-locations`
- `logging.file.name`
- `logging.level.root`
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`
- `unexamine.bootstrap.platform-root.enabled`
- `unexamine.bootstrap.platform-root.account-name`
- `unexamine.bootstrap.platform-root.initial-password`
- `unexamine.bootstrap.platform-root.reset-password`
- `unexamine.upload.local-root`
- `unexamine.security.cors-allowed-origins`
- `unexamine.security.allow-account-id-header`
- `unexamine.security.access-token-ttl`
- `unexamine.security.refresh-token-ttl`
- `unexamine.ai-agent.default-provider`

## Backend Commands

Linux:

```bash
cd release/unexamine-0.0.1-SNAPSHOT/backend
chmod +x server.sh
./server.sh start
./server.sh status
./server.sh health
./server.sh restart
./server.sh stop
```

`start` waits for `/api/v1/health` to report `status=UP`, `database=UP`, `schema=UP`, and `redis=UP`.
If Redis, database, or schema is unhealthy, the script stops the just-started service and prints the last health response plus recent logs.

Health check against the backend service:

```bash
./server.sh health
```

## Build

Local build:

```powershell
.\scripts\package-release.ps1
```

Use local tool paths:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 `
  -JavaHome 'D:\java\jdk\jdk21' `
  -MavenPath 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' `
  -NpmPath 'D:\java\nodejs\npm.cmd'
```

The release script must not inject a backend API host into the frontend package.

## Frontend And Nginx

Production deployment uses same-origin API calls. Keep `frontend/config.js` empty:

```javascript
window.__UNEXAMINE_API_BASE_URL__ = '';
```

The frontend calls `/api/...` on the same origin. Nginx serves `frontend/` and reverse proxies `/api/` to the backend service.

Reference Nginx template:

```text
release/unexamine-<version>/nginx/unexamine.conf
```

Production request shape:

```text
browser -> https://your-domain/
browser -> https://your-domain/api/v1/health
nginx  -> http://127.0.0.1:9999/api/v1/health
```

Do not use build-time frontend variables for production API hosts, and do not write a backend host into built frontend assets.

## Database

New deployment should initialize an empty database with:

```text
sql/init.sql
```

For old database upgrades, start the service and check `/api/v1/health` first. If it returns `schema=MISMATCH`, the current database structure does not satisfy this version and the database account with `ALTER` / `CREATE` privileges must run the matching migration script.

Current compatibility migration:

```text
sql/migrations/20260624_legacy_identity_schema_compat.sql
```

## Default Platform Root Account

On application startup, the backend ensures one built-in platform root account exists:

```text
username=admin
password=123123aa
role=PLATFORM_ROOT
```

This account is the platform super administrator. It can enter the platform backend and create systems. It is not automatically a super administrator inside every system; after it creates or switches into a system, normal system member and tenant context rules still apply.

The seed can be configured with:

```text
UNEXAMINE_PLATFORM_ROOT_ENABLED
UNEXAMINE_PLATFORM_ROOT_USERNAME
UNEXAMINE_PLATFORM_ROOT_PASSWORD
UNEXAMINE_PLATFORM_ROOT_RESET_PASSWORD
```

`UNEXAMINE_PLATFORM_ROOT_RESET_PASSWORD` defaults to `false`, so a manually changed password is not reset on every restart.

## Redis

The packaged backend must expose Redis settings in `backend/application.yml` and `/api/v1/health` must report `redis=UP` for a healthy deployment. Redis defaults follow `docs/user_setting.md`:

```text
host=192.168.0.211
port=6379
database=10
```

Runtime services can still be deepened later to use Redis for distributed locks, rate limiting, hot dictionaries, and async task coordination. Redis is already required for access/refresh token storage. A package that cannot at least connect to Redis is not considered release-ready.

If login returns `登录会话存储不可用`, first check:

```bash
./server.sh health
redis-cli -h 192.168.0.211 -p 6379 -a 123456 -n 10 PING
```

`Connection refused` means Redis is not listening on the configured host/port, or the OS/network firewall refuses the connection. Fix Redis binding/firewall/service status or change `spring.data.redis.*` in `backend/application.yml`, then restart with `./server.sh restart`.

## Authentication Boundary

Runtime APIs resolve the current account from `Authorization: Bearer <accessToken>`. Access and refresh tokens are stored in Redis and the default release config keeps `unexamine.security.allow-account-id-header=false`.

`X-Account-Id` is only a narrowly scoped local diagnostic compatibility switch. Production packages and frontend code must not depend on it.
