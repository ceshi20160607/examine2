# R1 System Admin Section Decomposition - 2026-06-27

## Scope

Verify the deployed local release no longer maps multiple system-admin sidebar entries to the same mixed content panel.

This is the follow-up to `r1-admin-section-shell-smoke-2026-06-27.md`. The earlier pass proved that the admin page no longer stacked every panel at once. This pass verifies that the remaining coarse panels were split by sidebar intent.

## Environment

- Release frontend: `http://127.0.0.1:18131/`
- Release backend: `http://127.0.0.1:9999`
- Redis: `192.168.0.211:6379`
- Login: `admin / 123123aa`

## Code Changes

- `组织架构` now renders only the department tree and employee list.
- `角色管理` now renders only role management.
- `流程管理` now renders only flow management.
- `字典管理` now renders only data dictionary management.
- `数据源` is now a separate explicit gap page instead of being mixed into OpenAPI.
- `对外应用` now renders only OpenAPI applications.
- `工作配置` now renders only work configuration.
- `AI Agent` now renders only agent policy.
- `统一认证` now renders only system SSO.
- `日志管理` now renders only logs.

## Browser Evidence

Initial system-admin state after entering `/systems/57/admin`:

```json
{
  "activeSidebar": ["系统信息"],
  "panels": [{ "id": "system-info", "heading": "系统信息" }],
  "sidebar": [
    "系统信息",
    "组织架构",
    "角色管理",
    "模块管理",
    "流程管理",
    "字典管理",
    "仪表盘管理",
    "数据源",
    "对外应用",
    "工作配置",
    "统一认证",
    "AI Agent",
    "日志管理"
  ]
}
```

Split sidebar checks:

```json
[
  {
    "label": "组织架构",
    "activeSidebar": ["组织架构"],
    "headings": ["组织架构", "员工列表"],
    "panels": [{ "heading": "组织架构", "id": "org-structure" }]
  },
  {
    "label": "角色管理",
    "activeSidebar": ["角色管理"],
    "headings": ["角色管理", "角色管理"],
    "panels": [{ "heading": "角色管理", "id": "role-management" }]
  },
  {
    "label": "流程管理",
    "activeSidebar": ["流程管理"],
    "headings": ["流程管理", "流程管理"],
    "panels": [{ "heading": "流程管理", "id": "flow-management" }]
  },
  {
    "label": "字典管理",
    "activeSidebar": ["字典管理"],
    "headings": ["字典管理", "数据字典"],
    "panels": [{ "heading": "字典管理", "id": "dict-management" }]
  },
  {
    "label": "数据源",
    "activeSidebar": ["数据源"],
    "headings": ["数据源"],
    "panels": [{ "heading": "数据源", "id": "data-source" }]
  },
  {
    "label": "对外应用",
    "activeSidebar": ["对外应用"],
    "headings": ["对外应用"],
    "panels": [{ "heading": "对外应用", "id": "openapi-apps" }]
  },
  {
    "label": "工作配置",
    "activeSidebar": ["工作配置"],
    "headings": ["工作配置", "工作配置"],
    "panels": [{ "heading": "工作配置", "id": "work-config" }]
  },
  {
    "label": "统一认证",
    "activeSidebar": ["统一认证"],
    "headings": ["统一认证", "系统 SSO"],
    "panels": [{ "heading": "统一认证", "id": "sso-config" }]
  },
  {
    "label": "AI Agent",
    "activeSidebar": ["AI Agent"],
    "headings": ["AI Agent", "AI Agent 策略"],
    "panels": [{ "heading": "AI Agent", "id": "agent-config" }]
  },
  {
    "label": "日志管理",
    "activeSidebar": ["日志管理"],
    "headings": ["日志管理", "日志管理"],
    "panels": [{ "heading": "日志管理", "id": "log-management" }]
  }
]
```

Each checked sidebar entry renders exactly one direct `.admin-content > .panel`.

## Command Evidence

```text
D:\dev\nodejs24\npm.cmd --prefix frontend run typecheck
PASS
```

```text
D:\dev\nodejs24\npm.cmd --prefix frontend run build
PASS
```

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r0-static-audit.ps1
status=PASS
promptOrConfirmCount=0
duplicateSidebarTargetCount=0
inertPaginationCount=0
mockSampleStubCount=3
releaseDirectoryExists=true
```

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome D:\dev\jdk21 -MavenPath D:\dev\maven\bin\mvn.cmd -NpmPath D:\dev\nodejs24\npm.cmd
status=PASS
releaseDir=D:\workspace\01_project\snow\cursor\examine2\release\unexamine-0.0.1-SNAPSHOT
zipPath=D:\workspace\01_project\snow\cursor\examine2\release\unexamine-0.0.1-SNAPSHOT.zip
frontendApiMode=same-origin-nginx-proxy
```

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\unexamine-0.0.1-SNAPSHOT\verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -RedisHost 192.168.0.211 -RedisPort 6379 -CheckDeployedFrontend
status=PASS
frontend config uses same-origin API=PASS
nginx proxies /api to backend 9999=PASS
redis tcp reachable=PASS
deployed frontend matches release assets=PASS
backend health all UP=PASS
admin login succeeds=PASS
```

## Result

PASS for the system-admin coarse-panel decomposition covered above.

This is still not full project completion. `数据源` remains a real product gap because the current frontend/backend contract does not yet provide system data-source management APIs. It is now shown as a separate `待接入` page instead of being mixed with OpenAPI or message-template functions, so the missing work is visible instead of disguised as completed functionality.

