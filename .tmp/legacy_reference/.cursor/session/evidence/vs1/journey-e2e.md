# VS1 真实用户旅程证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass`
- test: `frontend/tests/e2e/vs1.spec.ts`

## 自动浏览器旅程

Playwright 使用本机 Edge `149`（通过可选 `E2E_BROWSER_EXECUTABLE`，默认配置仍支持 Playwright Chromium），在最终 jar、空库 Flyway 和真实 Redis 上执行：

1. 匿名打开注册页并创建账号和首个系统。
2. 自动进入该系统工作台，确认身份上下文和权限版本可见。
3. 打开个人菜单返回平台，确认系统列表包含刚创建系统。
4. 再次进入系统，退出登录并回到登录页。
5. 断言页面无横向溢出、topbar 无裁切、菜单可点击、无非预期 console warning/error、无非预期 HTTP 4xx/5xx。

| project | viewport | result |
|---|---|---|
| desktop | `1440x900` | pass |
| mobile | `390x844` | pass |

匿名恢复会话请求的 `/api/v1/me/context 401` 是明确允许的认证合同；其余失败响应均会使测试失败。缺失 favicon 导致的 404 已修复。
