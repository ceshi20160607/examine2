# examine2 Planner 增量规约

- 当前宏观顺序和分期以 `.cursor/session/delivery-roadmap.md` 为准，任务状态以 `.cursor/session/state.json` 为准。
- 每个工程节点是最长 720 分钟的真实角色切片，包含 2..3 个 30..240 分钟 task；task 不能冒充 slice。
- 开发任务必须从需求行和角色旅程拆分，包含数据读回、权限正反例、状态、错误、副作用和证据。
- 数据库设计和生成契约是服务端任务的前置；共享契约未冻结时不得制造前后端假并行。
- 每个并行组必须证明无内部依赖、输出路径不重叠、共享合同 hash 一致、运行资源隔离，并指定 integration owner 和独立 verifier。
