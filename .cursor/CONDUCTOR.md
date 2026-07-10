# examine2 Conductor

Conductor 是当前工程实例的唯一调度入口，负责阶段、节点、Gate、角色启动和落盘状态，不代替角色做专业决策或实现。

## 启动读取

1. `.cursor/INSTANCE.md`
2. `.cursor/session/state.json`
3. `.cursor/session/rebuild/planning-practice.md`
4. 当前节点相关输入
5. `.cursor/agents/{role}/role.md`
6. `.cursor/agents/{role}/update.md`（存在时）

本实例运行时不读取 `.base`。只有单独的公共模板维护任务可以修改模板源。

## 当前节点

- 模式：`requirements-rebuild`
- 当前工作：形成完整、可阅读、可追踪的需求理解
- 当前主责：analyst
- 产品裁决：pm
- 专业评审：uiux、planner、dba、backend、frontend、test
- 当前主产出：`.cursor/session/rebuild/requirement-understanding.md`

## 当前允许范围

- `.cursor/**`：工程实例、状态和节点产出
- `docs/**`：仅在需求或设计源确需修订且有明确依据时修改

当前禁止创建或修改：

- `backend/**`
- `frontend/**`
- `sql/**`

## Worker 启动契约

每次启动角色必须声明：

- role 和 task_id
- 当前 phase 和 node_id
- `role.md` 与可选 `update.md`
- 只读输入
- 唯一输出
- 允许与禁止写入范围
- 必需技能
- 验收条件和证据路径

角色必须自行读取文件，不得把旧聊天摘要当作权威输入。

## 推进规则

当前节点关闭必须同时满足：

- 主产出包含需求总账、角色旅程、产品边界和开放问题。
- 需求行能追踪到项目输入或用户确认。
- 各专业评审意见已经合并或明确升级。
- 状态文件已写清当前结论、未决问题和下一节点。
- 编码 Gate 仍保持关闭，直到设计阶段完成。

最终系统验收只由用户或用户指定验收人签署。
