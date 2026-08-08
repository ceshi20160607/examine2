# VS1 后端验证证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass`
- command: `mvn.cmd test`

## 结果

- Maven reactor `5/5` modules success。
- `PasswordServiceTest`: `1` run，`0` failure/error。
- `Vs1JourneyIntegrationTest`: `1` run，`0` failure/error。
- 环境：真实 Undertow、MySQL `8.0.44`、Redis `7.4`、Flyway 空库迁移。

## 覆盖行为

- 原子注册账号、系统、默认租户、owner member/role/permission 和平台 session。
- 相同幂等键返回同一业务结果；密码使用 Argon2id；token 只持久化 SHA-256 hash。
- 登录、refresh rotation、logout、平台/系统 context 切换和 permission snapshot。
- 跨成员系统切换 `403`、未知系统 `404`、错误登录 `401`、缺失 CSRF `403`、退出后旧会话 `401`。
- security audit、refresh token 使用/轮换状态和数据库事实读回。

Mockito 未被项目使用并已从测试依赖排除；最终测试无动态 agent 兼容告警。
