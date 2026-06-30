# R1 Release Local Start Verification - 2026-06-27

## Scope

Verify the local standalone release startup path after the user confirmed Redis `192.168.0.211:6379` is available and reported that the standalone deployment still looked unchanged/confused.

This evidence belongs to `REC-P0-008 Release Package And Final User Script`. It does not close final acceptance because the full user script is still pending.

## Changes

- `scripts/local-start-release.ps1` now checks whether the backend and frontend ports are already listening before starting a new process.
- The startup script writes a deterministic `backend/start-result.json` file with backend pid, frontend pid, frontend URL, backend directory, and health response.
- If backend health fails, the script stops the newly started backend process.
- If frontend local proxy startup fails, the script stops both the frontend proxy and backend process.

## Positive Startup Evidence

Command:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-start-release.ps1 -JavaExe D:\dev\jdk21\bin\java.exe -NodeExe D:\dev\nodejs24\node.exe
```

Observed result:

```json
{
  "exitCode": 0,
  "backendPid": 9256,
  "frontendPid": 17472,
  "frontendUrl": "http://127.0.0.1:18131/",
  "health": {
    "status": "UP",
    "database": "UP",
    "schema": "UP",
    "redis": "UP"
  },
  "resultFile": "D:\\workspace\\01_project\\snow\\cursor\\examine2\\release\\unexamine-0.0.1-SNAPSHOT\\backend\\start-result.json"
}
```

Listening ports after startup:

```text
9999  -> pid 9256
18131 -> pid 17472
```

Note: In this Codex shell host, the script's success stdout was still not captured even after writing explicit success output. The deterministic result file above is the current reliable startup result channel. Failure output is captured normally, as shown below.

## Repeat Start Negative Evidence

Command:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-start-release.ps1 -JavaExe D:\dev\jdk21\bin\java.exe -NodeExe D:\dev\nodejs24\node.exe
```

Result:

```text
exitCode=1
Backend port 9999 is already listening, pid=9256. Stop the old release before starting a new one.
```

This proves the script no longer accepts an already-running old backend as a successful fresh deployment.

## Release Verification Evidence

Command:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\unexamine-0.0.1-SNAPSHOT\verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -RedisHost 192.168.0.211 -RedisPort 6379 -CheckDeployedFrontend
```

Result:

```json
{
  "status": "PASS",
  "baseUrl": "http://127.0.0.1:18131",
  "checks": [
    { "name": "frontend config uses same-origin API", "passed": true },
    { "name": "nginx proxies /api to backend 9999", "passed": true },
    { "name": "redis tcp reachable", "passed": true, "detail": "192.168.0.211:6379" },
    { "name": "deployed frontend matches release assets", "passed": true },
    { "name": "backend health all UP", "passed": true },
    { "name": "admin login succeeds", "passed": true }
  ]
}
```

## Result

PASS for default Redis release verification and stale-port startup protection.

Remaining work: `REC-P0-008` is still open because the full final user acceptance script from login through module, record, approval, todo, message, admin, and release stop has not been executed and signed off.

