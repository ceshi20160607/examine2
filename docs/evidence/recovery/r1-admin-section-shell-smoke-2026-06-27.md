# R1 Admin Section Shell Smoke - 2026-06-27

## Scope

Verify the deployed local release no longer renders all platform-admin and system-admin panels in one stacked page.

## Environment

- Release frontend: `http://127.0.0.1:18131/`
- Release backend: `http://127.0.0.1:9999`
- Redis: `192.168.0.211:6379`
- Login: `admin / 123123aa`

## Browser Evidence

Platform admin initial state after clicking `平台后台`:

```json
{
  "activeSidebar": ["平台信息"],
  "panels": [
    { "id": "platform-info", "heading": "平台信息" }
  ],
  "sidebar": ["平台信息", "组织架构", "系统生命周期", "角色管理", "仪表盘管理", "配置管理", "日志管理"]
}
```

Platform admin after clicking `日志管理`:

```json
{
  "activeSidebar": ["日志管理"],
  "headings": ["日志管理"],
  "panels": [
    { "id": "platform-logs", "heading": "日志管理" }
  ]
}
```

System admin initial state after entering `/systems/57/admin`:

```json
{
  "activeSidebar": ["系统信息"],
  "headings": ["系统信息"],
  "panels": [
    { "id": "system-info", "heading": "系统信息" }
  ],
  "sidebar": ["系统信息", "组织架构", "角色管理", "模块管理", "流程管理", "字典管理", "仪表盘管理", "数据源", "对外应用", "工作配置", "统一认证", "AI Agent", "日志管理"]
}
```

System admin after clicking `模块管理`:

```json
{
  "activeSidebar": ["模块管理"],
  "headings": ["模块管理"],
  "panels": [
    { "id": "module-config", "heading": "模块管理" }
  ]
}
```

## Command Evidence

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\recovery-r0-static-audit.ps1
status=PASS
promptOrConfirmCount=0
duplicateSidebarTargetCount=0
inertPaginationCount=0
```

```text
D:\dev\nodejs24\npm.cmd --prefix frontend run typecheck
PASS
```

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 -JavaHome D:\dev\jdk21 -MavenPath D:\dev\maven\bin\mvn.cmd -NpmPath D:\dev\nodejs24\npm.cmd
status=PASS
zipPath=D:\workspace\01_project\snow\cursor\examine2\release\unexamine-0.0.1-SNAPSHOT.zip
frontendApiMode=same-origin-nginx-proxy
```

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\release\unexamine-0.0.1-SNAPSHOT\verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -RedisHost 192.168.0.211 -RedisPort 6379 -CheckDeployedFrontend
status=PASS
frontend config uses same-origin API=PASS
redis tcp reachable=PASS
deployed frontend matches release assets=PASS
backend health all UP=PASS
admin login succeeds=PASS
```

## Result

PASS for the specific stacked-admin-page regression. Platform admin and system admin now render one active section in the content area and replace the section when the sidebar changes.

Remaining product risk: several sidebar entries still share coarse implementation panels internally, such as system org/role, flow/dict, data-source/openapi, work/agent, and sso/log. This is not the same stacked-page failure, but it is still an R2/R4 decomposition target.
