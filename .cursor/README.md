# unexamine 协作架构 v3

本目录是当前项目唯一协作规范来源。旧实现、旧脚本、旧 release、旧 evidence 已归档到 `.oldbk/restart-20260708-220451/`，只读参考，不再作为完成证据。

## 当前模式

`requirements-rebuild`

目标：先重新整理需求、工程架构、任务拆分、评审闸门和遗留参考边界。当前不是开发阶段，不继续 `REBUILD-P0-006`，也不创建新的后端/前端/SQL 实现。

这次重构必须先按工程架构把整个系统梳理清楚，再进入代码阶段。代码阶段启动后，后端先用生成器生成 `base` 基础代码，再在 `manage` 下编写业务逻辑，参考旧目录开发方式但不沿用旧完成证据。

## 启动顺序

每次开始新任务前，先按落盘文件压缩上下文：

1. 读 `.cursor/session/state.json`
2. 读 `.cursor/architecture/requirements-rebuild.md`
3. 读 `.cursor/architecture/encoding-and-records.md`
4. 读 `.cursor/session/project-minutes.md`
5. 读 `.cursor/architecture/clean-rebuild.md`
6. 读 `.cursor/knowledge/agent-operating-rules.md`
7. 读 `.cursor/knowledge/project-operating-rules.md`
8. 读 `.cursor/knowledge/failure-lessons.md`
9. 读 `.cursor/session/rebuild/source-index.md`
10. 读 `.cursor/session/rebuild/module-boundary-map.md`
11. 读 `.cursor/session/rebuild/task-plan.md`
12. 执行 `.cursor/workflows/clean-rebuild.md`

不得依赖旧聊天长上下文；新的用户纠偏必须先写回 `.cursor` 或 retained source 派生文件，再继续实现。

确定结论只记录到 `.cursor/session/project-minutes.md`，不为每次讨论新建零散会议记录。临时文件超过当前任务仍需保留时，必须登记到该文件的 Temporary File Ledger。

## 保留来源

- `docs/user_requirement.md`
- `docs/user_setting.md`
- `docs/design/prototypes/**`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`

## 归档边界

- `.oldbk/restart-20260708-220451/` 是旧实现参考区。
- 旧 `RECOVERY-R*`、旧 release、旧 scripts、旧 docs evidence 不能作为 clean rebuild 完成证据。
- 可以参考旧代码的技术栈、已踩坑和局部写法，但必须按 clean rebuild 架构重新落地。
- 旧遗留文件先盘点分类：有参考价值的保留并总结；无参考价值的先写清删除影响，再删除；不确定的先保留。

## 组委会规则

- 工程架构、产品边界、权限模型、数据模型、角色旅程、验收标准出现问题时，走组委会式复审，不做随手修补。
- 组委会不是发现一个小问题就开一次；正常情况下，这些问题应在理解需求、参考原型、梳理功能和任务拆分时提前发现。
- 复审至少覆盖产品、架构、后端、前端、DBA、测试视角，结论写回 `.cursor` 或 retained source 派生文件。
- leader 必须主动组织相关角色完成复审、整理、处理、决策、补充和再验证；普通推进不得反复要求用户说“继续”。
- 大范围治理和重构整理采用三轮内部闭环：第 1 轮角色复审，第 2 轮落盘集成，第 3 轮一致性验证。三轮后输出包给用户看，只在最终签收或无法从现有资料推断的产品选择上请求用户裁决。

## 后续代码规则

- 当前阶段禁止进入代码实现。
- 代码阶段开始前必须先有工程模块图、任务拆分、API/数据/权限边界和验收方式。
- 后端代码先通过代码生成工具生成 `base` 层基础代码，再在 `manage` 下写逻辑功能。
- `base` 只承载 entity、mapper、service 等贴表基础代码；对外接口、权限、事务编排、DTO/VO、读回和业务逻辑写在 `manage`。

## 产品硬边界

唯一主来源：`.cursor/session/rebuild/product-boundary-contract.md`。

入口文件不再复制完整边界，避免多处漂移。任何后续任务必须先引用该契约，再拆工程模块和验收。

## UI 硬规则

- 列表主界面采用左侧标签页/左侧分类样式。
- 详情采用右侧标签页/右侧工作区样式。
- 行点击打开详情；行内按钮只承载编辑、删除、审批、导出等差异动作。

## 验收硬规则

- 真实完成必须覆盖前端、后端、数据持久化、权限、状态、读回、消息/待办/日志副作用和浏览器体验。
- API 200、生成 CRUD、静态页面、构建通过都只能作为局部证据。
- `gates.user_script_passed=true` 只能来自用户试用或签字，不能由脚本替代。

## 编码与记录硬规则

- 活跃项目文本文件统一 UTF-8，规则见 `.editorconfig` 和 `.cursor/architecture/encoding-and-records.md`。
- Windows PowerShell 读取或写入项目文本必须显式使用 `-Encoding UTF8`。
- 每次提交治理、需求、设计或源码变更前，必须扫描常见乱码标记。
- 确定性会议纪要只维护 `.cursor/session/project-minutes.md` 一个文件；待决问题留在对应契约的 Open Decisions，不写进纪要。
