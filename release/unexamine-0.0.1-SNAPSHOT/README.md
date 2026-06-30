# unexamine release package

## Directory

```text
unexamine-<version>/
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

## Backend

Edit `backend/application.yml` before starting the service.

Linux:

```bash
cd backend
chmod +x server.sh
./server.sh start
./server.sh status
./server.sh restart
./server.sh stop
```

Useful environment overrides:

- `JAVA_HOME`
- `JAVA_OPTS`
- `UNEXAMINE_DB_URL`
- `UNEXAMINE_DB_USERNAME`
- `UNEXAMINE_DB_PASSWORD`
- `UNEXAMINE_REDIS_HOST`
- `UNEXAMINE_REDIS_PORT`
- `UNEXAMINE_REDIS_PASSWORD`
- `UNEXAMINE_REDIS_DATABASE`
- `UNEXAMINE_REDIS_TIMEOUT`
- `UNEXAMINE_SERVER_PORT`
- `UNEXAMINE_CONTEXT_PATH`
- `UNEXAMINE_LOG_LEVEL`
- `UNEXAMINE_UPLOAD_LOCAL_ROOT`
- `UNEXAMINE_CORS_ALLOWED_ORIGINS`
- `UNEXAMINE_ALLOW_ACCOUNT_ID_HEADER` (default `false`; keep it disabled outside isolated local diagnostics)
- `UNEXAMINE_ACCESS_TOKEN_TTL`
- `UNEXAMINE_REFRESH_TOKEN_TTL`
- `UNEXAMINE_PLATFORM_ROOT_ENABLED`
- `UNEXAMINE_PLATFORM_ROOT_USERNAME`
- `UNEXAMINE_PLATFORM_ROOT_PASSWORD`
- `UNEXAMINE_PLATFORM_ROOT_RESET_PASSWORD`

Health check:

```bash
curl http://127.0.0.1:9999/api/v1/health
```

The backend is not considered usable until `database`, `schema`, and `redis` are all `UP`.
Redis is a hard dependency because login sessions and Bearer tokens are stored there.

Admin login smoke test:

```bash
curl http://127.0.0.1:9999/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  --data '{"loginName":"admin","password":"123123aa","loginTarget":"PLATFORM"}'
```

Windows release verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-release.ps1 `
  -BaseUrl http://127.0.0.1:9999 `
  -RedisHost 192.168.0.211 `
  -RedisPort 6379
```

Remote deployment verification after Nginx/static files are replaced:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-release.ps1 `
  -BaseUrl http://your-domain.example.com `
  -CheckDeployedFrontend `
  -SkipRedisTcp
```

`-CheckDeployedFrontend` compares the deployed `/index.html` asset names with the current `frontend/index.html` in this release. It must pass before asking users to review the page; otherwise the server is still running an old frontend bundle.

Use `-SkipRedisTcp` only when the verification machine cannot directly reach Redis but `/api/v1/health` is checked through the deployed backend. Backend health still must report `redis=UP`.

## Frontend

Deploy `frontend/` to Nginx or another static file server.

Production deployment uses same-origin API calls by default:

```javascript
window.__UNEXAMINE_API_BASE_URL__ = '';
```

Do not bake the backend host into the frontend package. Configure Nginx to serve `frontend/` and reverse proxy `/api/` to the backend service instead.

The included Nginx template proxies `/api/` to the default backend port `9999`. Change either `backend/application.yml` or the Nginx upstream only when the deployment chooses a different backend port.

Authentication uses Bearer tokens stored in Redis. Do not rely on `X-Account-Id` in production.

Default platform root account after startup:

```text
username=admin
password=123123aa
role=PLATFORM_ROOT
```

The account owns platform backend access only. System business access still requires system switch context and member mapping.

Reference template:

```text
nginx/unexamine.conf
```

Health check through Nginx:

```bash
curl http://your-domain.example.com/api/v1/health
```
