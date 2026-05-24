# 测试问题记录与修复（2026-05-22）

**状态：冒烟范围内问题已修复**（API **13/13** + UI **7/7**）。**全站 UI/API 未测完**，见 [TEST-PLAN.md](TEST-PLAN.md)。

## 运行方式

```powershell
# 后端 9999 + 前端由 Playwright 自动拉起 5173
cd tests
.\run-all.ps1

# 或分步
$env:SMOKE_USER='admin'; $env:SMOKE_PASS='123123aa'
.\api\e2e-smoke.ps1
cd web; npm run test:e2e
```

## 已发现问题与修复

| # | 现象 | 根因 | 修复 |
|---|------|------|------|
| 1 | API `GET /v1/system/flow/inbox/tasks/pending` 500，`Unknown column 'record_id'` | 旧库 `un_flow_task` 列为 `instance_id`/`node_id`，与实体 `record_id`/`node_key` 不一致 | Flyway **V26** + `docs/sql/19_flow_task_column_align.sql`（幂等重命名列） |
| 2 | API `POST /v1/system/flow/temp-vers/upsert` 500 | 新建版本未填 `ver_no`、`graph_json`（DB NOT NULL） | `SystemFlowTempVerController.upsert` 自动分配 `verNo`、默认 `graphJson="{}"` |
| 3 | API/UI `GET /v1/platform/permissions/me` 500 | `PlatAccountRoleMapper.xml` 在 `src/main/java` 下，Maven 未打入 jar，`BindingException: selectRoleCodesByPlatAccountId` | 父 `backend/pom.xml` 增加 resource：打包 `**/mapper/xml/**/*.xml` |
| 4 | Playwright 未执行 / chromium 缺失 | 未安装浏览器 | `cd tests/web && npx playwright install chromium` |
| 5 | UI「创建系统」后找不到按钮 | ① 创建失败显示「服务器错误」（同 #3）② 可访问名称为 `名称+id=...` 非纯名称 | 修 #3；测试改为 `button.list__btn` + `hasText` |
| 6 | （历史）`un_module_record_data` 缺 `field_code`/`value_num` | 旧 DDL / Flyway 基线跳过 | V23/V25 与手工 SQL（见 `docs/sql/`） |
| 7 | API `dept + module auth` 500 | 冒烟脚本路径写错：`/v1/system/module/auth/permissions` 不存在 | 改为 **`/v1/system/auth/permissions`** |

## 验证结果（修复后）

- **API smoke**（admin）：13/13（含 permissions、record 更新、dept、flow 列表、抄送等）
- **Playwright UI**：7/7（auth、systems、apps、flow×2、records）

## 环境注意

- 同时存在多个 `java` 进程时，9999 可能指向旧 jar；测试前建议只保留一个后端实例。
- Flyway 新环境需能执行 **V26**；已手工执行 `19_flow_task_column_align.sql` 的库可跑 `docs/sql/manual/flyway_mark_v26_success.sql`。
- 默认账号：`admin` / `123123aa`（`tests/web` 与 smoke 一致）。
