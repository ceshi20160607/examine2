# API 端到端冒烟

对运行中的 **examine-web** 执行 HTTP 级冒烟（原 `scripts/smoke` 已迁入此目录）。

## Windows

```powershell
$env:EXAMINE_HOST = "http://127.0.0.1:9999"
$env:SMOKE_USER = "admin"
$env:SMOKE_PASS = "123123aa"
.\tests\api\e2e-smoke.ps1
```

## Linux / macOS

```bash
export EXAMINE_HOST=http://127.0.0.1:9999
export SMOKE_USER=admin
export SMOKE_PASS=your-password
bash tests/api/e2e-smoke.sh
```

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `EXAMINE_HOST` | `http://127.0.0.1:9999` | API 根地址 |
| `SMOKE_USER` | （空） | 设置则登录，否则注册新用户 |
| `SMOKE_PASS` | `SmokePass123!` | 密码 |
| `SMOKE_SYSTEM_ID` | （空） | 跳过创建，直接进入指定 systemId |
| `SKIP_OPEN_API` | `1` | `0` 且配置 AK/SK 时跑开放 API |

## 覆盖项

ping / auth / system / meta+records / rbac / inbox / flow graph-designer（详见脚本内步骤）。

失败时非 0 退出。
