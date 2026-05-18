# 端到端冒烟脚本

对运行中的 **examine-web** 执行 API 级冒烟，覆盖 `docs/deploy/production.md` §6 主流程。

## 前置条件

- 后端已启动（默认 `http://127.0.0.1:9999`）
- MySQL / Redis 可用；库已初始化（Flyway 或 `docs/sql` 种子）
- **创建系统** 需要平台权限 `SYSTEM_CREATE`：
  - 空库下**首个注册用户**自动为平台超级管理员，可创建系统；
  - 已有账号的库请使用管理员登录（见环境变量）。

## Windows（推荐）

```powershell
cd scripts\smoke
$env:EXAMINE_HOST = "http://127.0.0.1:9999"
# 已有库时用管理员（首个注册账号或 plat_super_admin）
$env:SMOKE_USER = "admin"
$env:SMOKE_PASS = "your-password"
.\e2e-smoke.ps1
```

## Git Bash / Linux / macOS

```bash
cd scripts/smoke
export EXAMINE_HOST=http://127.0.0.1:9999
export SMOKE_USER=admin
export SMOKE_PASS=your-password
bash e2e-smoke.sh
```

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `EXAMINE_HOST` | `http://127.0.0.1:9999` | API 根地址 |
| `SMOKE_USER` | （空） | 若设置则**登录**，否则**注册**新用户 |
| `SMOKE_PASS` | `SmokePass123!` | 密码（≥6 位） |
| `SMOKE_SYSTEM_ID` | （空） | 跳过创建系统，直接进入指定 systemId |
| `SKIP_OPEN_API` | `1` | `0` 且配置 AK/SK 时跑开放 API 用例 |
| `OPEN_AK` / `OPEN_SK` / `OPEN_ACTING_PLAT_ID` | | 开放 API 明文 SK 模式 |
| `OPEN_TARGET_SYSTEM_ID` | 当前系统 | 平台级 client 必填 |

## 覆盖项

1. `/ping`、`/actuator/health`（若开启）
2. 注册或登录、`/auth/me`
3. 创建系统（或有权限时复用已有系统）→ `enter-system`
4. app / model / field → record CRUD + `includeFieldCodes` 查询
5. RBAC 角色 `data_scope`、运行时菜单
6. 上传、平台收件箱、流程待办列表
7. 可选：开放 API records query（需 AK/SK）

失败时以非 0 退出码结束，并打印失败步骤名称。
