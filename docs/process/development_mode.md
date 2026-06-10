# 开发模式流程

## 开发治理硬闸门

每个开发批次必须先执行 `docs/process/development_governance.md`：Planner 定义任务边界，PM 明确验收口径，多角色提出问题，PM 裁决并分发，PM 不能决策的问题写入 `docs/issues/user_questions.md`，再进入实现、测试、构建、审查和阶段提交。

禁止只因为用户没有继续追问就直接交付；也禁止只跑前端页面或只跑后端接口就宣称完整系统完成。Orchestrator 必须主动验证并在 `docs/progress.md`、`docs/build_report.md`、`docs/test_runs/` 中记录结果。

## 目标

开发模式基于审阅模式冻结产物执行 DB、SQL、后端、UI/UX、前端、测试、验证和审查任务。

## 入口条件

- `docs/api_review.md` 明确 API 已冻结。
- `.codex/state.json.api_frozen = true`。
- `docs/task_plan.md` 和 `docs/tasks/` 已通过任务审阅并冻结。
- 当前执行任务必须来自 `docs/tasks/{taskId}-*.md`。

## 输入优先级

1. 当前任务文件。
2. 冻结 API：`docs/api.md`、`docs/api_review.md`。
3. 冻结任务计划：`docs/task_plan.md`。
4. PRD 和项目理解：`docs/prd.md`、`docs/project_understanding.md`。
5. 只有任务或 API 无法理解时，才回读 `docs/requirement_analysis.md` 并提交 PM 问题。

## 执行规则

- 每次只执行当前任务或被确认可并行的一组任务。
- 可并行任务必须输出路径不重叠。
- 多个任务共同形成总文档时，先写分片，再由串行汇总任务写总文档。
- 实现 agent 不得修改冻结 API；如需变更，登记问题并回到 API 契约评审。
- PM 不能决策的问题写入 `docs/issues/user_questions.md`。

## 典型顺序

1. DBA 完成 `docs/db_design.md` 和 `sql/init.sql`。
2. backend 完成 Maven 多模块骨架和 `examine-generator`。
3. backend 根据 SQL 和生成器产出 base 层，再按 manage 层任务实现业务。
4. uiux 完成 `docs/ui/ui-design.md`，冻结信息架构、交互流程、页面线框、组件规范和状态设计。
5. frontend 先完成 typed SDK 和页面到 API 映射，再按冻结 UI/UX 设计实现页面。
6. test 执行 API、E2E、权限、异常、幂等、OpenAPI 和 UI 可用性场景。
7. validator 执行 clean compile、clean build 和契约同步检查。
8. reviewer 输出合法 `docs/review.json`。

## 代码生成器接入

- 开发模式中的 `examine-generator` 必须参考 `.codex/oldgenerator` 和 `docs/generator_reference.md`。
- 优先参考 `GeneratorOwner`、`DefaultTemplateEngine` 和 `template_owner`，但只能生成 base 层 entity、mapper、mapper.xml、service、serviceImpl。
- 禁止保留旧生成器硬编码数据库、硬编码输出目录、交互式唯一入口和 Controller 模板。
- 后续表结构变动时，应通过重新执行 `examine-generator` 指定表或指定前缀生成 base 层，避免手写大量 CRUD 样板。

## 并行开发口径

- DBA-001 与 TEST-001 可以在开发模式第一批并行，因为输出不重叠。
- FE-001 理论上可在 API 冻结后并行，但完整前端页面应等 SDK、路由上下文和后端接口逐步可用后再分批推进。
- 后端 manage 业务不能早于 `docs/db_design.md`、`sql/init.sql`、BE-001 和 GEN-004；base 层必须先由生成器产生。
- UI/UX 可以基于冻结 PRD、项目理解和 API 契约先做信息架构、线框和状态设计；面向用户的前端页面实现必须依赖对应 UI/UX 设计产物。
- 前端可以基于冻结 API 先做 typed SDK 和页面级契约映射；真实页面实现必须同时依赖冻结 UI/UX 设计，接口联调、E2E 和构建验证必须等待后端对应接口完成。

## 回环规则

- API 问题回到 API 契约评审。
- 任务拆分问题回到 planner。
- DB/SQL 问题回到 dba。
- 后端问题回到 backend。
- 前端问题回到 frontend。
- UI/UX 设计缺失、页面可用性或交互流程问题回到 uiux/pm；实现偏离设计才回到 frontend。
- 测试问题回到 test。
- 构建问题按失败来源回到 backend/frontend/test。
- reviewer fail 必须给出 target 和具体文件。
