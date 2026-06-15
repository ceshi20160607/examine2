# 旧工程备份（只读参考）

本目录存放 v1 流水线产物，**不得**作为 `.cursor` v2 协作的执行依据。

| 目录 | 说明 |
|------|------|
| `.codex/` | 旧 agent 配置、state、oldexamine 参考 |
| `backend/` | 旧 Maven 多模块实现（含 generator、base、manage） |
| `frontend/` | 旧前端工程 |
| `sql/` | 旧 `init.sql` 与迁移脚本 |

## 新开发如何使用这里

- **结构参考**：`.oldbk/backend/` 的模块划分、`base`/`manage` 分层、生成器命令
- **禁止**：在未完成 Design Gate 前，直接复制旧 frontend 页面或继续 patch 旧实现
- **生成 base**：新 `sql/init.sql` 冻结后，用 `examine-generator` 生成 `base/`，业务只写 `manage/`

详见 `.cursor/architecture/backend-structure.md`
