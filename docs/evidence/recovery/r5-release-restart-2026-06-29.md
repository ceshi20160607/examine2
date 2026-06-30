# R5 Release Restart Evidence

Time: 2026-06-29 Asia/Shanghai

## Result

- Startup script: `scripts/local-start-release.ps1`
- Verify script: `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend`
- Status: PASS

## Runtime

- Frontend URL: `http://127.0.0.1:18131/`
- Backend URL: `http://127.0.0.1:9999`
- Backend PID: `17336`
- Frontend PID: `18904`
- Health: `status=UP`, `database=UP`, `schema=UP`, `redis=UP`
- Redis: `192.168.0.211:6379`
- Deployed frontend assets: `/assets/index-C49n4ymp.js`, `/assets/index-C8CAAU0i.css`
- Admin login check: PASS

## Boundary

This restart verifies the already-built release is currently runnable for user inspection. It does not replace the full R5 final orchestration evidence, which remains `docs/evidence/recovery/r5-final-release-2026-06-27.md` and `docs/evidence/recovery/r5-final-release-result.json`.
