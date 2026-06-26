# Coding Parallelization Plan

> status: draft-for-integration
> purpose: 将 `docs/tasks/plan.md` 的父任务拆成更细的 coding 执行切片，方便并行调度。
> rule: 本文件不替代 `TASK-*.md`，用于 Conductor 整合正式任务前防止任务过粗。

## 1. 结论

当前 16 个任务可以开工，但粒度仍偏粗：

- DBA 任务按领域分片合理，但每个领域还应拆成“表结构 + 索引/约束 + 数据字典/种子 + 文档校验”。
- Backend 任务过大，必须先拆 `core/web scaffold`，再按模块拆 controller/service/bo/vo/enums。
- Frontend 任务过大，必须先拆 `app shell/shared components/api client/mock fixtures`，再拆登录、平台壳、系统壳、后台配置、运行态业务。
- QA 任务需要前置化，不要等最后才写；G0 就应准备 fixtures、静态检查脚本、验收模板。

## 2. 推荐执行批次

### G0a：可立即并行

| Slice | Parent | Owner | Outputs |
|---|---|---|---|
| `DBA-001A` | `TASK-DBA-001` | dba | `sql/fragments/core/identity-account.sql` |
| `DBA-001B` | `TASK-DBA-001` | dba | `sql/fragments/core/identity-rbac.sql` |
| `DBA-002A` | `TASK-DBA-002` | dba | `sql/fragments/module/module-config.sql` |
| `DBA-002B` | `TASK-DBA-002` | dba | `sql/fragments/module/runtime-record.sql` |
| `DBA-003A` | `TASK-DBA-003` | dba | `sql/fragments/flow/flow-approval.sql` |
| `DBA-003B` | `TASK-DBA-003` | dba | `sql/fragments/message/message-log.sql` |
| `DBA-004A` | `TASK-DBA-004` | dba | `sql/fragments/integration/secret-openapi.sql` |
| `DBA-004B` | `TASK-DBA-004` | dba | `sql/fragments/ops/task-agent-ops.sql` |
| `BE-001A` | `TASK-BE-001` | backend | `backend/pom.xml`, module poms |
| `BE-001B` | `TASK-BE-001` | backend | `backend/examine-core/src/main/java/**/api/**` |
| `BE-001C` | `TASK-BE-001` | backend | `backend/examine-core/src/main/java/**/context/**` |
| `BE-001D` | `TASK-BE-001` | backend | `backend/examine-web/src/main/java/**` |
| `FE-001A` | `TASK-FE-001` | frontend | `frontend/package.json`, config files |
| `FE-001B` | `TASK-FE-001` | frontend | `frontend/src/app/**` |
| `FE-001C` | `TASK-FE-001` | frontend | `frontend/src/shared/**` |
| `FE-001D` | `TASK-FE-001` | frontend | `frontend/src/api/client.ts`, `frontend/src/mocks/**` |
| `QA-001A` | `TASK-QA-001` | test | `docs/testing/fixtures.md` |
| `QA-001B` | `TASK-QA-001` | test | `docs/testing/test-strategy.md` |
| `QA-001C` | `TASK-QA-001` | test | `docs/testing/acceptance-checklist.md` |

### G0b：G0a 合并后

| Slice | Depends on | Owner | Outputs |
|---|---|---|---|
| `DBA-010A` | all DBA fragments | dba | `sql/fragments/README.md`, fragment order |
| `DBA-010B` | `DBA-010A` | dba | `sql/init.sql` |
| `BE-001E` | `BE-001A-D` | backend | compile-ready backend scaffold |
| `FE-001E` | `FE-001A-D` | frontend | build-ready frontend scaffold |
| `QA-001D` | `QA-001A-C` | test | G0 acceptance template |

### G1：身份上下文闭环

| Slice | Parent | Owner | Outputs |
|---|---|---|---|
| `BE-010A` | `TASK-BE-010` | backend | auth/account APIs |
| `BE-010B` | `TASK-BE-010` | backend | platform system lifecycle APIs |
| `BE-010C` | `TASK-BE-010` | backend | system/tenant switch context APIs |
| `BE-010D` | `TASK-BE-010` | backend | org/member binding APIs |
| `BE-010E` | `TASK-BE-010` | backend | role/effective permission APIs |
| `FE-010A` | `TASK-FE-010` | frontend | login/register/password reset |
| `FE-010B` | `TASK-FE-010` | frontend | platform workbench/system switch |
| `FE-010C` | `TASK-FE-010` | frontend | system shell/tenant switch/permission guard |
| `QA-010A` | new | test | identity and permission smoke checklist |

### G2：配置与运行态基础

| Slice | Parent | Owner | Outputs |
|---|---|---|---|
| `BE-020A` | `TASK-BE-020` | backend | module group/module/field/dict APIs |
| `BE-020B` | `TASK-BE-020` | backend | dynamic list schema and scene APIs |
| `BE-020C` | `TASK-BE-020` | backend | record search/detail/save/draft APIs |
| `BE-020D` | `TASK-BE-020` | backend | import/export/attachment/history APIs |
| `FE-020A` | `TASK-FE-020` | frontend | system admin organization/role/module pages |
| `FE-020B` | `TASK-FE-020` | frontend | field/dict/flow/work/agent config pages |
| `FE-030A` | `TASK-FE-030` | frontend | dynamic runtime list/detail primitives |

### G3：流程、消息、工作、集成

| Slice | Parent | Owner | Outputs |
|---|---|---|---|
| `BE-030A` | `TASK-BE-030` | backend | flow definition/node/publish APIs |
| `BE-030B` | `TASK-BE-030` | backend | approval instance/task APIs |
| `BE-030C` | `TASK-BE-030` | backend | todo/message/template/delivery APIs |
| `BE-030D` | `TASK-BE-030` | backend | audit log/async task APIs |
| `BE-040A` | `TASK-BE-040` | backend | SSO/Secret/OpenAPI APIs |
| `BE-040B` | `TASK-BE-040` | backend | work management APIs |
| `BE-040C` | `TASK-BE-040` | backend | AI Agent/Ops APIs |
| `FE-030B` | `TASK-FE-030` | frontend | todo/message drawers |
| `FE-030C` | `TASK-FE-030` | frontend | work dashboard/project/plain/daily pages |
| `FE-030D` | `TASK-FE-030` | frontend | Agent confirmation drawers |

## 3. 调度原则

- 并行任务必须写不同目录或不同文件。
- 任何任务如果需要改冻结 API，必须开 issue，不得边写边改。
- `backend/examine-core` 的基础类型先完成，其他后端模块只引用，不复制。
- `frontend/src/shared` 的表格、抽屉、状态、权限提示先完成，业务页面只组合。
- 每个父任务完成前，先要求子切片全部通过自检，再跑父任务 self-check。

## 4. 下一步

1. 等 DBA / Backend / Frontend / QA worker 返回。
2. 把它们的建议合并到正式 `docs/tasks/plan.md` 和必要的 `TASK-*.md`。
3. 直接启动 G0a coding：后端 scaffold、前端 scaffold、SQL fragments、QA fixtures。

