# 旧工程能力摘要（只读参考）

> **agentId:** analyst  
> **taskId:** phase-0-legacy-scan  
> **来源:** `.oldbk/`（禁止作为验收依据）

## 1. 扫描范围

| 路径 | 内容 |
|------|------|
| `.oldbk/backend/` | Maven 多模块 Java 后端（v1 实现） |
| `.oldbk/frontend/` | Vue/TS 前端与契约映射 |
| `.oldbk/sql/` | 历史 init.sql |
| `.oldbk/.codex/oldexamine/` | 更早期参考 |
| `.oldbk/backend/examine-generator/` | MyBatis-Plus 生成器 |

## 2. 可复用策略（重建时参考）

详见 `.cursor/knowledge/validated-capabilities.md`：

- 模块：core / plat / module / flow / upload / app / generator / web
- `base/` 生成 + `manage/` 业务
- 生成脚本：`.oldbk/backend/examine-generator/scripts/generate-base-crud.ps1`
- 域能力：认证、平台中心、系统 RBAC、动态模块/记录、流程、文件、OpenAPI、审计

## 3. 明确不继承

| 项 | 原因 |
|----|------|
| 旧前端页面与导航 | 用户反馈「普通人不能用」 |
| 旧 PM accepted / fullProjectDeployable | 已撤回 |
| 独立「导出中心」式 IA | 与用户决策冲突 |
| 成员=context 仅靠前端 Header | v1 安全/体验问题 |
| `.oldbk/.codex/state.json` 任务状态 | 作废 |

## 4. 旧前端契约可参考处

`.oldbk/frontend/docs/api-contract-map.md`、各 `page-contracts/FE-*.md` 在 **contract 阶段**可作字段映射参考，**不得**驱动 UI 视觉与信息架构。

## 5. 数据库

- 表前缀分模块与 `backend-structure.md` 一致
- 新 `sql/init.sql` Gate 后由 dba 重写；可读 `.oldbk/sql/` 作域参考，不整文件复制

## 6. 结论

旧工程证明 **后端主链路可 trial deploy**；v2 价值在 **产品 IA + UI + 用户剧本**。重建 = 新 manage + 新 frontend，generator 流程复用。
