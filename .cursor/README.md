# examine2 工程实例

本目录是当前项目实际运行的工程系统，负责把 `docs/` 中的人类可读输入转化为需求理解、设计契约、实现任务、验证证据和用户验收材料。

## 当前状态

- 阶段：`requirements-rebuild`
- 当前节点：`S1 需求理解`
- 编码 Gate：关闭
- 下一产出：`.cursor/session/rebuild/requirement-understanding.md`

## 每次启动顺序

1. `.cursor/INSTANCE.md`
2. `.cursor/session/state.json`
3. `.cursor/CONDUCTOR.md`
4. `.cursor/session/rebuild/planning-practice.md`
5. `.cursor/workflows/lifecycle.md`
6. 当前阶段对应的 architecture/workflow 文件
7. `.cursor/agents/{role}/role.md`
8. `.cursor/agents/{role}/update.md`（存在时）
9. 本次任务声明的输入文件

不要把聊天记忆当成工程事实。需求、决定、问题、状态和证据都必须写入项目文件。

## 角色与技能

角色公共契约和项目增量位于同一角色目录：

```text
.cursor/agents/{role}/role.md
.cursor/agents/{role}/update.md
```

公共技能契约位于 `.cursor/skills/README.md`，项目专项技能位于同一目录的独立文件。技能是无状态执行能力，不拥有产品决策权。

## 项目输入

- `docs/user_requirement.md`
- `docs/user_setting.md`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`
- `docs/design/prototypes/index.html`

`docs/` 不是工程框架，也不存放工程运行状态。

## 当前实现边界

当前只允许整理需求、设计、工程契约、状态和评审产出。业务代码目录在 Gate 通过前保持不存在。

后续编码顺序固定为：数据库设计，生成基础持久化代码，手写业务行为，绑定真实前端，调试集成，角色旅程验证，用户整体验收。
