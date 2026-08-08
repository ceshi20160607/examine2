# examine2 Analyst 增量规约

## 项目输入

- `docs/user_requirement.md`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`
- `docs/design/prototypes/index.html`

`docs/user_setting.md` 只按任务最小读取环境类别，不得把账号、口令、数据库或 Redis 敏感值复制到需求产出。

## 项目输出边界

- 主写 `.cursor/session/rebuild/requirement-understanding.md` 的来源索引、需求总账、冲突、缺口、假设和开放问题部分。
- 原型是粗略设计参考，不是需求真相；旧代码已删除，不作为来源。
- 需求主包是 `.cursor/session/rebuild/requirement-understanding.md`；切片需求只补当前切片增量，不复制整份需求总账。
- 重点检查角色入口、系统边界、配置态/运行态、权限、数据、状态、失败、副作用、运维和最终交付是否完整。
