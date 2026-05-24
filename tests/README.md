# examine2 测试（与 backend / web / mobile 平级）

| 目录 | 内容 | 命令 |
|------|------|------|
| [api/](api/) | 后端 API 端到端冒烟 | `.\tests\api\e2e-smoke.ps1` |
| [web/](web/) | Web 管理台 UI（Playwright） | `cd tests\web && npm run test:e2e` |
| [mobile/](mobile/) | 移动端（预留 H5/Appium） | 见 mobile/README |

## 前置：服务已启动

```powershell
# 终端 1：后端
.\scripts\deploy\run-backend.ps1

# 终端 2：前端（UI 测试需要）
cd web\vue3
npm run dev
```

## 一键（API + Web UI）

```powershell
$env:SMOKE_USER = 'admin'
$env:SMOKE_PASS = '123123aa'
.\tests\run-all.ps1
```

## 分层说明

- **backend** 内 `src/test/java`：JUnit 单元/模块测试（随 Maven）
- **tests/api**：跨 HTTP 的主流程，不依赖浏览器
- **tests/web**：真实浏览器点选，只覆盖关键路径
- **tests/mobile**：后续 uniapp H5 或真机

旧路径 `scripts/smoke/` 仍可用，内部转发到 `tests/api/`。

**测试完成度**（是否覆盖所有功能）：见 [TEST-PLAN.md](TEST-PLAN.md)。
