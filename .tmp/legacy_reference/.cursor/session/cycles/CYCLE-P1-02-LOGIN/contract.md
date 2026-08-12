# CYCLE-P1-02-LOGIN 冻结执行合同

## 周期结果

项目默认管理员能够在 `/login` 提交账号和密码，服务端只保存访问令牌与刷新令牌的 SHA-256 哈希，并返回 HttpOnly Cookie；登录后进入简洁的“我的系统”工作台，刷新页面仍保持同一账号，退出后当前会话族失效并返回登录页。

本周期最长 240 分钟。后端认证与前端登录工作区写集完全分离，允许并行；周期验证必须等待两项实现任务通过。功能完成优先于响应式适配，不进行全量回归、性能或容量测试。

## TASK-P1-02-AUTH-BACKEND

- owner：`backend-auth`
- verifier：`leader`
- requirements：`REQ-P1-LOGIN`、`REQ-P1-LOGOUT`
- cases：`CASE-P1-LOGIN`、`CASE-P1-LOGIN-INVALID`、`CASE-P1-LOGOUT`
- result：实现 login、refresh、current context、logout 四个 vNext 用例；生成平台权限快照；写入会话、刷新令牌和安全审计；令牌重放撤销整个会话族。
- writes：
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/vnext/manage/auth/`
  - `backend/examine-web/src/main/java/com/unique/examine/web/vnext/auth/`
  - `backend/examine-web/src/test/java/com/unique/examine/web/P1AuthUseCaseContractTest.java`
  - 冲突旧适配器仅允许增加 `@Profile("!vnext")`：`AuthController`、`MeController`、`EnterpriseSsoController`、`AuthenticationFilter`
- dependencies：只允许使用生成的 `IVNextPlat*Service`、core generated `ISecurityService`、`PasswordService`、`SecurityProperties` 和通用 Web 响应/异常基础设施。
- forbidden：修改旧 Authentication/Registration/Session Service 内部；直接依赖 BaseMapper/JDBC/SQL；把令牌返回给 JavaScript；实现注册、SSO、MFA、找回密码、系统切换或其他功能。
- required check：

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-maven-focused-test.ps1 -Module examine-web -Tests P1AuthUseCaseContractTest
  ```

## TASK-P1-02-LOGIN-UI

- owner：`frontend-login`
- verifier：`leader`
- requirements：`REQ-P1-LOGIN`、`REQ-P1-LOGOUT`
- cases：`CASE-P1-LOGIN`、`CASE-P1-LOGIN-INVALID`、`CASE-P1-LOGOUT`
- result：实现紧凑登录页、明确错误/加载状态、只包含“我的系统”和退出的 P1 平台壳；刷新后恢复当前账号。
- writes：
  - `frontend/src/vnext/auth/`
  - `frontend/src/vnext/platform-shell/`
  - `frontend/src/router/index.ts`
  - 登录所需的 `frontend/src/stores/session.ts`、`frontend/src/types/session.ts`、`frontend/src/services/api.ts`
  - `frontend/tests/unit/p1-login-foundation.spec.ts`
  - `frontend/tests/e2e/p1-login-cycle.spec.ts`
- route rule：运行态路由只暴露当前已交付的 `/login`、`/platform/systems`、根跳转和兜底；旧页面代码冻结但不可从路由或导航进入。
- forbidden：注册、找回密码、SSO、MFA、运营、独立报表、收藏、关注、AI、模块运行态、占位页面；1440×900 参考视口以外的统一适配。
- required check：

  ```text
  npm.cmd --prefix frontend test -- p1-login-foundation.spec.ts
  ```

## TASK-P1-02-VERIFY

- owner：`test`
- verifier：`leader`
- requirements：`REQ-P1-LOGIN`、`REQ-P1-LOGOUT`
- cases：`CASE-P1-LOGIN`、`CASE-P1-LOGIN-INVALID`、`CASE-P1-LOGOUT`
- dependencies：后端、前端任务均有真实非零测试证据。
- writes：`.cursor/session/evidence/baseline/CYCLE-P1-02-LOGIN/`
- forbidden：验证阶段修改功能、复用历史 evidence、全量回归、性能或容量测试。
- required check：

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-verified-test-plan.ps1 -Plan .cursor/session/baseline/test-plans/cycle-p1-02-login.json -EvidenceRoot .cursor/session/evidence/baseline/CYCLE-P1-02-LOGIN
  ```

## 集成不变量

1. `/api/v1/auth/login`、`refresh`、`logout` 与 `/api/v1/me/context` 在 vNext profile 下只能有一套适配器。
2. access/refresh token 只存在于 HttpOnly SameSite Cookie；数据库、日志、响应和 evidence 不得出现明文令牌或默认管理员明文密码。
3. 无效密码、禁用账号和限流结果不得生成可用会话；失败计数与安全审计必须可读回。
4. 登录事务同时提交 context session、refresh token、安全审计；任一写入失败不得发出 Cookie。
5. refresh token 单次使用；重放已使用令牌必须撤销同一 token family 和关联 session。
6. 平台权限由 account-role-role-permission-permission 关系解析并持久化为快照，不得硬编码管理员结果。
7. manage/controller/domain 不直接依赖 BaseMapper、JdbcTemplate、ResultSet、`java.sql` 或内联 SQL。
8. 登录后运行态不得显示运营、独立报表、收藏、关注、SSO、AI、占位页或其他未交付入口。
9. 周期验证实际执行测试数必须大于 0，且必须包含真实浏览器入口。
