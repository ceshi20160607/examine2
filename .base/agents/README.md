# 角色目录

每个角色使用独立目录：

```text
agents/{role}/role.md
agents/{role}/update.md  # 仅项目实例按需创建
```

`role.md` 是公共契约，必须包含职责、非职责、输入、输出、技能、Gate、升级和完成标准。

`update.md` 是实例增量，只允许补充项目路径、技术限制、特殊产出或更严格的 Gate。加载顺序为公共契约、实例增量、任务启动包。

角色不能通过 `update.md` 静默取得其他角色或用户的决策权。

| 角色 | 主责 | 常用技能 |
|---|---|---|
| conductor | 调度与状态 | gate-review、record-sync |
| pm | 产品目标与范围 | requirement-consolidation、decision-review |
| analyst | 需求理解 | source-trace、requirement-consolidation |
| uiux | 设计与可用性 | design-review、journey-review |
| planner | 计划与依赖 | dependency-check、scope-check |
| dba | 数据设计 | data-contract-review、migration-check |
| backend | 服务端行为 | contract-check、build-check |
| frontend | 用户界面 | journey-review、browser-check |
| test | 独立验证 | acceptance-check、regression-check |

