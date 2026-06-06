# GEN-003 base 层模板策略

- taskId: GEN-003
- 标题: base 层模板策略
- 负责角色: backend
- 所属大任务/模块: examine-generator
- 目标: 定义 MyBatis-Plus 生成模板和 package 规则，只生成贴表基础能力。
- 输入文件: `docs/api.md`、`docs/db_design.md`、`backend/examine-generator/`、`docs/generator_reference.md`、`.codex/oldgenerator/src/main/resources/template_owner/`
- 输出文件或输出目录: `backend/examine-generator/`

## 详细工作内容

- 设计 entity、mapper、service、serviceImpl 生成规则。
- 参考 `template_owner` 中的 entity、mapper、mapper.xml、service、serviceImpl 模板，剔除 Controller 和旧通用 CRUD Controller 入口。
- 定义基础实体继承、逻辑删除、审计字段、租户字段和包路径策略。
- 明确不生成对外 Controller、不生成 BO/VO/DTO、不写业务事务。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 模板策略可执行并能约束生成范围。

## 验收标准

- 生成产物只进入各模块 `base` 层。
- manage 层仍由后端业务任务实现。
- 模板只能依赖当前项目已有或本任务明确创建的基础类型，不能引用旧 `com.kakarote.*`。

## 测试/自检要求

- dry-run 检查不会输出 Controller 或 manage 类。

## 依赖任务

- GEN-002

## 可并行关系

- 不可并行；GEN-004 依赖模板策略。

## 不允许事项

- 不手写大批量基础 CRUD。
- 不在模板中混入业务权限或事务编排。
- 不生成 Controller、BO、VO、DTO 或页面代码。
