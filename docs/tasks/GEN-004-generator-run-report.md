# GEN-004 生成执行与报告

- taskId: GEN-004
- 标题: 生成执行与报告
- 负责角色: backend
- 所属大任务/模块: examine-generator
- 目标: 执行 MyBatis-Plus 代码生成并形成生成报告。
- 输入文件: `docs/db_design.md`、`sql/init.sql`、`docs/service_info.md`、`docs/generator_reference.md`、`backend/examine-generator/`
- 输出文件或输出目录: `backend/examine-plat/src/main/java/com/unique/examine/plat/base/`、`backend/examine-module/src/main/java/com/unique/examine/module/base/`、`backend/examine-flow/src/main/java/com/unique/examine/flow/base/`、`backend/examine-upload/src/main/java/com/unique/examine/upload/base/`、`backend/examine-app/src/main/java/com/unique/examine/app/base/`、`backend/examine-core/src/main/java/com/unique/examine/core/base/`

## 详细工作内容

- 执行 SQL 导入后的表读取和代码生成。
- 支持按表前缀或指定表名执行，便于后续表结构变动后只重生成受影响 base 层代码。
- 将 base 层 entity、mapper、service、serviceImpl 输出到对应业务模块的 `base/` 包，禁止写入 `manage/` 或 `examine-web`。
- 生成命令中直接体现模块、前缀、base 包和生成路径；不再默认写报告文件。

## 完成状态定义

- 默认状态: pending。
- 完成条件: base 层生成完成，报告说明成功或明确阻塞原因。

## 验收标准

- 生成报告可复现生成过程。
- 生成报告逐项列出 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`/`un_audit_` 的目标模块与 package，并说明旧项目 `un_app_*` 只作迁移参考、不生成新 base 代码。
- 生成报告必须说明是否参考 `.codex/oldgenerator`、参考了哪些模板、剔除了哪些旧模板能力。
- 失败时不退回手写大批量 CRUD。

## 测试/自检要求

- 执行生成结果路径检查。
- 生成后至少进行后端基础编译前检查。

## 依赖任务

- GEN-003

## 可并行关系

- 不可并行；后端 manage 模块依赖生成产物。

## 不允许事项

- 不修改冻结 API。
- 不生成对外 Controller 或前端代码。
