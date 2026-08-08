# examine2 工程实例

本目录使用可复用工程模板，把 `docs/` 的粗糙输入逐步转化为需求包、设计契约、开发任务、测试证据、发布包和用户验收结果。

## 状态入口

- 全项目进度简表：`.cursor/session/PROJECT_PROGRESS.md`。
- 全项目进度机器事实源：`.cursor/session/project-progress.json`。
- 运行状态唯一事实源：`.cursor/session/state.json`。
- 当前执行节点：`.cursor/session/current-node.md`。
- 分期、演示节奏和最终目标剩余范围：`.cursor/session/delivery-roadmap.md`。
- 本文件只说明稳定入口，不复制阶段、已完成节点或下一任务，避免状态漂移。

## 每轮启动顺序

1. `.cursor/INSTANCE.md`
2. `.cursor/session/state.json`
3. `.cursor/LEADER.md`
4. `.cursor/GOVERNANCE.md`
5. `.cursor/workflows/lifecycle.md`
6. `.cursor/session/pending-user-decisions.md`
7. 当前角色 `role.md`、`update.md` 和任务声明输入

未列入任务输入的历史架构、旧证据、旧任务和聊天记录不主动读取。

## Leader 与 PM

- Leader 对工程总目标、节点验收、Gate、升级和最终工程状态负责。
- PM 对项目计划、问题分流、会议组织、依赖和风险负责。
- PM 收到 backend/frontend 等角色疑问后，按影响组织提交人、product、uiux 及相关专业角色会审。
- PM 无法收敛时升级 Leader；Leader 结合项目文件和权威资料仍无法解决时，写入用户待决文件。

## 角色与上下文

公共契约：`.cursor/agents/{role}/role.md`。

项目增量：`.cursor/agents/{role}/update.md`。

每个角色使用干净上下文，只读声明文件，只写声明输出。多角色不得并发修改同一主文件，实现者不得验收自己的任务。

## 当前输入

- `docs/user_requirement.md`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`
- `docs/design/prototypes/index.html`
- `docs/user_setting.md`（仅按任务最小读取敏感配置）

## 当前阶段顺序

```text
需求理解
 -> 产品/UI/工程设计
 -> 数据库设计与生成边界
 -> 敏捷业务切片开发
 -> 独立测试与集成
 -> 发布验证
 -> 用户整体验收
```

生成 CRUD、页面存在、API 200、构建成功、截图和历史批次通过都不能单独证明系统可用。

执行层为“项目阶段 -> 目标 240 分钟功能批次 -> 10..120 分钟模块任务”。状态从 `.cursor/session/state.json#active.workstreams` 读取；无依赖且写集不重叠的模块默认并行。task 只验证受影响层，slice 验证真实入口，响应式/全量回归/重启矩阵在 phase/release hardening 统一执行。任务和切片通过均不得冒充阶段、旅程、发布或最终验收完成。
