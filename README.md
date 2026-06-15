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

`design_user_approved = false` → 禁止开发；先完成需求理解与 Open Design 原型确认。