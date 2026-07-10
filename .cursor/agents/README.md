# examine2 角色目录

每个角色目录最多包含两份长期规约：

```text
agents/{role}/role.md
agents/{role}/update.md
```

- `role.md`：从公共模板实例化的通用角色契约。
- `update.md`：仅属于 examine2 的增量规约。

Conductor 启动角色时必须按 `role.md -> update.md -> 任务启动包` 的顺序提供上下文。项目特殊要求不得回写到 `role.md`，公共角色内容也不得复制进 `update.md`。

| role | 当前项目主责 |
|---|---|
| conductor | 阶段、节点、Gate 和调度 |
| pm | 产品边界与产品裁决 |
| analyst | 需求总账与角色旅程 |
| uiux | 信息架构、交互和设计评审 |
| planner | 工程节点与依赖 |
| dba | 数据库设计和代码生成边界 |
| backend | 服务端业务行为 |
| frontend | 用户界面和真实接口绑定 |
| test | 独立验证与验收证据 |
