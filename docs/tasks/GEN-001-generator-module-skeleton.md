# GEN-001 examine-generator 模块骨架

- taskId: GEN-001
- 标题: examine-generator 模块骨架
- 负责角色: backend
- 所属大任务/模块: examine-generator
- 目标: 在后端多模块工程中建立 `examine-generator` 模块骨架。
- 输入文件: `docs/prd.md`、`docs/api.md`、`docs/db_design.md`、`sql/init.sql`、`docs/service_info.md`、`docs/generator_reference.md`、`.codex/oldgenerator/`
- 输出文件或输出目录: `backend/examine-generator/`

## 详细工作内容

- 将生成器模块纳入 `backend/pom.xml` 父工程。
- 建立生成器配置、执行入口和报告输出目录结构。
- 参考 `.codex/oldgenerator` 的 Maven 依赖、`GeneratorOwner` 和 `DefaultTemplateEngine`，建立 Java 21 兼容的生成器模块。
- 确认生成器不在业务启动主包中暴露普通业务入口。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 模块可被 Maven 识别，目录结构与后端多模块约定一致。

## 验收标准

- `examine-generator` 只承载生成工具职责。
- 不影响 `examine-web` 启动边界。
- 不保留旧生成器硬编码数据库、硬编码输出目录和交互式唯一入口。

## 测试/自检要求

- 执行父工程模块识别或基础编译自检。

## 依赖任务

- BE-001
- DBA-006

## 可并行关系

- 可与 BE-002 并行；不与依赖 BE-002 的 BE-003、BE-014 同批启动。

## 不允许事项

- 不生成对外 Controller。
- 不根据旧项目实体直接反推新表结构。
- 不照搬旧包名 `com.kakarote.*`。
