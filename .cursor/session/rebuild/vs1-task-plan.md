# VS1 身份与系统上下文任务计划

## 1. Meta

- node: `S3-VS1-IDENTITY-CONTEXT`
- linked_design: `DESIGN-EXAMINE2-V1` version `1.0`
- owner: `planner`
- controller: `pm`
- verifier: `leader`
- status: `accepted`
- updated_at: `2026-07-10T22:46:04+08:00`

## 2. 业务完成定义

匿名用户通过真实前端和 API 注册账号/首个系统，登录后建立平台会话，切换到本人有效系统成员/默认租户上下文，并进入系统业务壳。退出、无成员、禁用成员、伪造系统路径、过期/旧 context 和重复请求均有正确结果、审计和数据读回。

本节点不实现后续模块业务，也不以占位按钮冒充功能。

## 3. 环境 Preflight

| check | result | evidence boundary |
|---|---|---|
| Java | `21.0.10` pass | 使用当前机器实际 `D:\dev\jdk21`；不依赖默认 JDK 8 |
| Maven | `3.8.5` pass | `D:\dev\maven`，Java runtime 为 21 |
| Node/npm | `24.14.0` / `11.9.0` pass | `D:\dev\nodejs24` |
| MySQL endpoint | TCP pass | 主机、账号和密码不写入本文件；运行时从本地配置/环境变量读取 |
| Redis endpoint | TCP pass | 主机和密码不写入本文件；运行时从本地配置/环境变量读取 |
| Docker | server `29.6.1` pass | 可用于 MySQL/Redis 集成测试和 clean environment |
| Pending user decisions | none | `.cursor/session/pending-user-decisions.md` |

## 4. 工作图

| task | depends_on | owner | write scope | verification | status |
|---|---|---|---|---|---|
| VS1-001 Preflight/plan | S2 accepted | planner/architect/ops | 本文件、node/state | 环境和任务边界检查 | DONE |
| VS1-002A Backend scaffold | VS1-001 | architect/backend | `backend/**` | Maven reactor build、module/ArchUnit boundary | DONE |
| VS1-002B Frontend scaffold | VS1-001 | frontend/uiux | `frontend/**` | npm lock、typecheck、unit/build | DONE |
| VS1-003 Schema | VS1-001 | dba/backend | `sql/migration/**` | Flyway against clean MySQL、schema/index readback | DONE |
| VS1-004 Generator/base | VS1-002A + VS1-003 | dba/backend | generator + generated base | exact command/report、delete/regenerate diff | DONE |
| VS1-005 Auth/context backend | VS1-004 | backend/test | core/plat/web manage/API | unit, MySQL/Redis integration, OpenAPI, negative cases | DONE |
| VS1-006 Auth/shell frontend | VS1-002B + VS1-005 API | frontend/uiux/test | frontend auth/shell/context | real API browser journey desktop/mobile | DONE |
| VS1-007 Evidence/hardening | VS1-005 + VS1-006 | test/ops | tests/evidence/docs | build, E2E, visual, security, restart/readback | DONE |
| VS1-008 Gate | VS1-007 | pm/all/leader | meeting/state/node | P0/P1 closed; Leader verdict | DONE |

VS1-002A、VS1-002B 和 VS1-003 可并行；VS1-004 必须等待 schema freeze；VS1-006 的真实旅程必须等待 VS1 API，不能用 mock 代替验收。

## 5. 文件和模块合同

### Backend

```text
backend/
  pom.xml
  examine-core/
  examine-plat/
  examine-generator/
  examine-web/
```

- `examine-core`：统一结果/错误、上下文契约、审计/幂等/outbox/job 基础。
- `examine-plat`：account/system/tenant/member/role/permission/session 的 generated base 和 handwritten manage。
- `examine-generator`：显式 CLI 生成 base 和报告，不进入运行依赖。
- `examine-web`：Undertow 启动、配置、filter/interceptor 和 wiring，无业务 controller/service。

### Frontend

```text
frontend/src/
  app/
  router/
  stores/
  services/
  layouts/
  views/auth/
  views/platform/
  views/system/
  views/access/
  components/
  styles/
```

认证路由与四套壳隔离；当前只实现平台/系统初始壳的真实状态，不创建后续功能假页面。

### Database

VS1 migration 至少覆盖：idempotency、outbox/job、security/operation audit、account/credential/session/refresh token、system/tenant/member、role/permission/binding 和 context session。表、索引、check、唯一和外键先于 generator 冻结。

## 6. API Freeze

| method/path | result | idempotency/auth |
|---|---|---|
| `POST /api/v1/auth/register` | account + first system + platform session | `Idempotency-Key` required; anonymous rate limit |
| `POST /api/v1/auth/login` | platform session + available system summary | anonymous rate limit |
| `POST /api/v1/auth/refresh` | rotated access/refresh session | refresh cookie/token rotation |
| `POST /api/v1/auth/logout` | all current context cleared | authenticated; idempotent |
| `GET /api/v1/me` | current account profile | platform/system session |
| `GET /api/v1/context/systems` | membership-filtered systems | platform session |
| `POST /api/v1/context/platform:switch` | fresh platform context with system fields cleared | authenticated; rotates context session |
| `POST /api/v1/context/systems/{systemId}:switch` | system/tenant/member/permission context | authenticated; path/member checked |
| `GET /api/v1/me/context` | current context, shells, routes and permission keys | platform/system session |

所有响应遵循设计包 envelope、真实 HTTP status、string ID、requestId/traceId。Cookie/CSRF 的最终传输按同源 Web 运行；测试可通过受控 header fixture 驱动，不改变生产边界。

## 7. 数据和安全负例

- 注册中 account/system/tenant/member/role 任一步骤异常，所有表均无残留。
- 相同 idempotency key + 相同 body 返回同一结果；body 不同返回 409。
- 错误密码不区分不存在/错误；达到阈值短时锁定；security audit 可查。
- access/refresh token 和密码不以明文进入数据库、日志或 API。
- account A 不可切换到没有 member 的 system B；禁用 system/member/tenant 不可建立 context。
- system context 请求路径与 context systemId 不一致返回 403；前端修改 header/path 不产生权限。
- 角色/权限版本变化使旧 permission snapshot 失效；退出后 platform/system token 均不可继续使用。

## 8. 证据输出

- `.cursor/session/evidence/vs1/preflight.md`
- `.cursor/session/evidence/vs1/schema.md`
- `.cursor/session/evidence/vs1/generator.md`
- `.cursor/session/evidence/vs1/backend-tests.md`
- `.cursor/session/evidence/vs1/frontend-tests.md`
- `.cursor/session/evidence/vs1/journey-e2e.md`
- `.cursor/session/meetings/S3-vs1-review.md`

证据只记录命令、版本、结构、pass/fail、非敏感摘要和 artifact 路径，不复制本地密码、token 或 Secret。
