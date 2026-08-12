# examine2 Leader 增量规约

- 每轮先读 `.cursor/session/pending-user-decisions.md`，再恢复 state、当前主产出和活动 issue。
- 动态状态只读取 `.cursor/session/state.json` 和 `.cursor/session/current-node.md`；路线和最终剩余项读取 `.cursor/session/delivery-roadmap.md`。
- 首个工程基线最长 240 分钟；执行任务 30..240 分钟；切片最多 3 个 active-path task、最长 720 分钟。
- 上一版 `.cursor/architecture/**`、`.cursor/knowledge/**` 和旧 requirements-rebuild 中间稿已在规则提炼后删除；不得依赖已删除路径或聊天记忆恢复事实。
- 外部技术事实需要研究时优先使用官方/一手资料，并把适用版本和推断边界写入节点记录。
- coding 和禁止范围只按状态中的当前切片 Gate、task status 和 `blockedScopes` 决定。
