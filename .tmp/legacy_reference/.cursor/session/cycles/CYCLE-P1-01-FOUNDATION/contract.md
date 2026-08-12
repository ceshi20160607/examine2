# CYCLE-P1-01-FOUNDATION 冻结执行合同

## 周期结果

从独立空白 vNext MySQL 数据库启动应用：P1 migration 只执行一次；再次启动不破坏数据；项目级 `admin / 123123aa` 被幂等初始化且只保存安全哈希；P1 被触及的 manage/bootstrap 代码只能通过生成的 `IService` 访问持久化。

周期最长 240 分钟。Generator 与 Database 写集和运行资源隔离，允许并行；Verify 必须等待两者通过。

## TASK-P1-01-GENERATOR

- owner：`backend-generator`
- verifier：`leader`
- requirements：`REQ-P1-BASE-MANAGE`
- cases：`CASE-P1-BASE-MANAGE`
- result：P1 foundation 表能够稳定生成 `entity/BaseMapper/IService/ServiceImpl/XML`，重复生成不把业务 SQL 写入生成目录。
- writes：
  - `backend/examine-generator/`
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/vnext/base/`
  - `backend/examine-plat/src/main/resources/mapper/vnext/base/`
- forbidden：manage 用例、migration、前端、旧 `com.unique.examine.plat.base`、旧业务 Service、产品语义。
- required check：

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-maven-focused-test.ps1 -Module examine-generator -Tests BaseManageGenerationContractTest
  ```

## TASK-P1-01-DATABASE

- owner：`backend-foundation`
- verifier：`leader`
- requirements：`REQ-P1-DATABASE-BOOT`、`REQ-P1-DEFAULT-ADMIN`
- cases：`CASE-P1-DATABASE-BOOT`、`CASE-P1-DEFAULT-ADMIN`
- result：P1-only vNext migration、独立数据库配置、清晰依赖诊断和通过 generated Base service 的 admin 幂等初始化。
- writes：
  - `sql/migration-vnext/`
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/vnext/manage/bootstrap/`
  - `backend/examine-web/src/main/resources/application-vnext.yml`
  - 对应 focused tests
- forbidden：legacy migration、P2 表、直接 BaseMapper/JDBC、旧 `com.unique.examine.plat.base`、性能数据、旧 `PlatformRootBootstrapService` 扩展。
- required check：

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-maven-focused-test.ps1 -Module examine-web -Tests FoundationMigrationStaticContractTest,DefaultAdminBootstrapContractTest
  ```

## TASK-P1-01-VERIFY

- owner：`test`
- verifier：`leader`
- requirements：`REQ-P1-BASE-MANAGE`、`REQ-P1-DATABASE-BOOT`、`REQ-P1-DEFAULT-ADMIN`
- case：`CASE-P1-FOUNDATION-READY`
- dependencies：Generator、Database 都有真实非零测试证据。
- writes：
  - `.cursor/session/evidence/baseline/CYCLE-P1-01-FOUNDATION/`
- forbidden：验证阶段修改功能、复用历史 evidence、全量回归、性能/容量测试。
- required check：

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-verified-test-plan.ps1 -Plan .cursor/session/baseline/test-plans/cycle-p1-01-foundation.json -EvidenceRoot .cursor/session/evidence/baseline/CYCLE-P1-01-FOUNDATION
  ```

## 集成不变量

1. P1 migration 不包含 `un_plat_role_draft` 和 `un_plat_authz_version`。
2. vNext 不在 legacy 数据库执行。
3. admin 初始化第二次不新增账号、不重置密码。
4. 明文初始密码不得出现在 SQL、日志、响应或 evidence；仅项目配置可提供初始值，Base 默认值不得包含它。
5. 新 `plat.vnext.base` 生成目录不含换密码、登录、审批、发布等业务方法；现有受污染 `plat.base` 整棵冻结，不作为 P1 生成成功证据。
6. `manage/application/controller/domain` 不直接依赖 BaseMapper、JdbcTemplate、ResultSet、`java.sql` 或内联 SQL。
7. 周期验证实际执行测试数必须大于 0。
8. P1 用例只允许依赖 `plat.vnext.base.service.I*Service`，不得 import legacy `plat.base`。
