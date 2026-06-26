# Deployable Package Evidence

Time: 2026-06-24 14:35 Asia/Shanghai

## Why

裸 jar 不是完整交付物。服务交付需要外置配置和可运维脚本，便于部署时修改端口、日志、上传目录、Agent 参数等运行参数，并支持 start/stop/restart/status。

## Added Standard

New packaging standard:

- `docs/deployment/package-and-service.md`
- `scripts/package-release.ps1`
- `deploy/templates/backend/application.yml`
- `deploy/templates/backend/server.sh`
- `deploy/templates/README.md`

The rule is also沉淀到:

- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`

## Generated Package

Command:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 `
  -MavenPath 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' `
  -NpmPath 'D:\java\nodejs\npm.cmd'
```

Result: PASS

Evidence log:

- `docs/evidence/deployable-package-build.log`

Generated outputs:

- `release/unexamine-0.0.1-SNAPSHOT/`
- `release/unexamine-0.0.1-SNAPSHOT.zip`
- `release/unexamine-0.0.1-SNAPSHOT/backend/examine-web.jar`
- `release/unexamine-0.0.1-SNAPSHOT/backend/application.yml`
- `release/unexamine-0.0.1-SNAPSHOT/backend/server.sh`
- `release/unexamine-0.0.1-SNAPSHOT/frontend/index.html`

Zip size:

- `19054552` bytes

## Runtime Verification

External config was verified through:

```powershell
cd .\release\unexamine-0.0.1-SNAPSHOT\backend
.\server.sh start
```

Result:

- Start script: PASS
- Spring Boot log: `Tomcat initialized with port 18102`
- Health: `GET http://127.0.0.1:18102/api/v1/health` returned HTTP 200 and `code=SUCCESS`
- Status script: PASS, process running
- Restart script: PASS, process stopped and started with a new pid
- Stop script: PASS
- Port cleanup: `18102` clear

Evidence logs:

- `docs/evidence/deployable-start.log`
- `docs/evidence/deployable-restart.log`
- `docs/evidence/deployable-stop.log`

Linux shell scripts:

- `bash -n` syntax check for `server.sh`: PASS
- WSL printed host-network warnings on this Windows machine, but syntax check returned success.

## Notes

PowerShell script execution can be blocked by local policy. The documented invocation uses:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1
```

The release package is the deployable artifact; the backend target jar remains a build intermediate.

## 2026-06-24 Script Simplification

User feedback: `bin` should not contain many `sh` files; one shell with an action parameter is clearer.

Changed release command shape:

```bash
./server.sh start
./server.sh stop
./server.sh restart
./server.sh status
```

Verification after simplification:

- Rebuilt release package: PASS
- `release/unexamine-0.0.1-SNAPSHOT/backend` contains `examine-web.jar`, `application.yml`, and `server.sh` at the same level.
- `server.sh` syntax check: PASS
- `UNEXAMINE_SERVER_PORT=18102` external config override: PASS
- Health after start: HTTP 200 and `code=SUCCESS`
- Health after restart: HTTP 200 and `code=SUCCESS`
- Port cleanup: `18102` clear

Evidence logs:

- `docs/evidence/deployable-package-single-script-build.log`
- `docs/evidence/deployable-single-script-start.log`
- `docs/evidence/deployable-single-script-restart.log`
- `docs/evidence/deployable-single-script-stop.log`
