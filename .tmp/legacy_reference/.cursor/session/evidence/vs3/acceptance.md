# VS3 批次验收记录

- level: `batch`
- scope_id: `S3-VS3-CONFIG-PUBLISH`
- implementers: `dba, backend, frontend`
- acceptor: `leader`
- environment: `Java 21 / Spring Boot 3.5.16 / MySQL 8.0+ / Redis 7.4 / Vue 3`
- checked_at: `2026-07-15T12:36:48+08:00`
- verdict: `pass`

## 目标结果

- roles: 系统 Owner/Admin、普通系统成员。
- entry_points: 系统后台模块配置、成员预览、系统运行工作台。
- expected_business_outcome: 管理员无需代码和原始 JSON 即可配置系统结构，经检查后原子发布不可变版本；普通成员只能按服务端有效权限读取 active 导航和定义；管理员可比较版本并将历史版本作为 N+1 回滚发布。
- linked_requirements: `REQ-MODULE-GROUP-001, REQ-MODULE-001, REQ-FIELD-001, REQ-DICT-001, REQ-PAGE-001, REQ-MENU-001, REQ-RULE-001, REQ-CONFIG-001, REQ-RBAC-001, REQ-MIGRATION-001`。
- linked_journeys: `JRN-C6` 配置核心、`JRN-C10, JRN-PUB1, JRN-B2`。

## 配置能力矩阵

| 能力 | VS3 结果 | 运行边界 |
|---|---|---|
| 基础、高级、系统字段类型目录 | 可创建、类型化配置、检查、复制和发布 | 动态值持久化在 VS4 |
| 默认值来源 | NONE/FIXED/当前用户/当前部门/当前日期/公式/父字段 | 公式执行在后续运行切片 |
| 字典/关系/子表 | 字典引用、关系过滤 AST、子表列和行策略可配置 | 关联记录和子表记录在 VS4 |
| 页面/动作/规则 | LIST/FORM/DETAIL、声明动作、深度受限递归规则 | 导入导出/打印在 VS5，仪表盘在 VS8 |
| 发布治理 | 检查、CAS、immutable snapshot、diff、N+1 rollback | 不回滚未来业务数据 |
| 权限运行 | 动态权限、owner 默认授权、普通成员默认拒绝及授权后可见 | 只展示 schema 驱动空状态，不冒充 VS4 CRUD |

## 证据矩阵

| 证据 | 路径 | 结果 | 边界 |
|---|---|---|---|
| 冻结合同 | `rebuild/vs3-task-plan.md` | pass | 只覆盖 VS3 |
| Schema/migration | `evidence/vs3/schema.md` | pass | V1 到 V3.2、MySQL 8.0/8.4 |
| Generator | `evidence/vs3/generator.md` | pass | generated base 不代表业务完成 |
| Backend integration/security | `evidence/vs3/backend-tests.md` | pass with observations | 真实 HTTP/MySQL/Redis/事务/权限 |
| Frontend unit/build | `evidence/vs3/frontend-tests.md` | pass with observation | 主 chunk 性能 P2 |
| Browser journey | `evidence/vs3/journey-e2e.md` | pass | 1440/1280/390 与普通成员闭环 |
| Independent review | `meetings/S3-vs3-review.md` | pass | 三路独立复审无开放 P0/P1，Leader 接受 VS3 |

## 当前结论

自动验证、人工截图复核和三路独立复审满足 VS3 声明范围。Leader 接受本开发批次，允许进入 VS4 动态业务运行合同评审。

本批次不表示完整管理系统、release Gate 或用户最终验收通过。VS4 及后续业务切片仍须实现，`userAccepted` 保持 `false`。
