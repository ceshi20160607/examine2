# examine2（unexamine）

可配置业务系统平台。协作规范见 **[`.cursor/README.md`](./.cursor/README.md)**。

## 目录

| 路径 | 说明 |
|------|------|
| `docs/user_requirement.md` | 原始产品需求 |
| `.cursor/` | v2 协作架构（Conductor、Agent、Skill、Gate） |
| `.oldbk/` | 旧工程备份（只读参考：backend、frontend、sql、.codex） |

## 后端分层（重建时）

- **`base/`** — `examine-generator` + MyBatis-Plus 从表生成，禁止 Agent 手写大批量 CRUD
- **`manage/`** — 业务 Controller、Service、BO/VO、权限与事务

详见 [`.cursor/architecture/backend-structure.md`](./.cursor/architecture/backend-structure.md)

## 当前状态

当前以 [`.cursor/session/state.json`](./.cursor/session/state.json) 为唯一状态源。

截至 2026-06-27：

- `design_user_approved = true`
- `api_frozen = true`
- `tasks_planned = true`
- `build_batch_accepted = true`
- `user_script_passed = false`

用户启动试用后反馈：页面堆叠、功能混乱、原型和真实业务整合不完整。项目进入 **recovery task-card** 整改口径，后续 coding 只能按 [`docs/recovery/fix-batches.md`](./docs/recovery/fix-batches.md) 中的明确任务卡推进。

整改入口见 [`docs/recovery/README.md`](./docs/recovery/README.md)。任务不再按“页面存在 / 接口存在 / build 通过”关闭，只能按“角色在真实系统中完成可验收业务动作”关闭。
