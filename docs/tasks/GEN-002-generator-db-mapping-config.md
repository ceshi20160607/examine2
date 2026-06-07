# GEN-002 生成器数据库映射配置

- taskId: GEN-002
- 标题: 生成器数据库映射配置
- 负责角色: backend
- 所属大任务/模块: examine-generator
- 目标: 配置数据库连接、表前缀和表到业务模块的生成映射。
- 输入文件: `docs/db_design.md`、`sql/init.sql`、`docs/service_info.md`、`docs/generator_reference.md`、`.codex/oldgenerator/src/main/java/com/kakarote/generator/config/GeneratorOwner.java`
- 输出文件或输出目录: 生成器命令参数与 CLI 帮助说明

## 详细工作内容

- 读取 `docs/service_info.md` 中数据库连接来源。
- 参考 `GeneratorOwner` 的 `un_` 前缀识别方式，但改为当前项目可配置的数据源和命令参数。
- 配置 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`/`un_audit_` 到目标模块路径。
- 明确生成结果落到各业务模块 `base` 包：`un_plat_ -> examine-plat/base`，`un_module_ -> examine-module/base`，`un_flow_ -> examine-flow/base`，`un_upload_ -> examine-upload/base`，`un_openapi_ -> examine-app/base`，`un_sys_`/`un_audit_ -> examine-core/base`。旧项目 `un_app_*` 仅作为 OpenAPI 历史迁移参考，不进入生成器映射。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 表映射配置完整，缺失表或不支持模块能输出明确错误。

## 验收标准

- 所有 `sql/init.sql` 中应生成基础 CRUD 的表都能通过模块生成命令覆盖。
- OpenAPI 表生成到 `examine-app`，不是 `examine-module`，也不使用错误模块名 `examine-genger`。
- 数据源来自 `docs/service_info.md` 或环境变量，不保留旧地址、旧库名和旧密码。

## 测试/自检要求

- 执行 dry-run 或生成命令校验，不再维护逐表清单。

## 依赖任务

- GEN-001

## 可并行关系

- 不可并行；模板和执行依赖映射配置。

## 不允许事项

- 不修改 DB 设计或 SQL。
- 不把生成结果写入 `examine-web`。
- 不把旧项目 `un_app_*` 作为新项目生成表前缀。
