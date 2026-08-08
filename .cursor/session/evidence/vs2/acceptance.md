# VS2 批次验收记录

- level: `batch`
- scope_id: `S3-VS2-ORG-PERMISSION`
- implementers: `dba, backend, frontend`
- acceptor: `leader`
- environment: `Java 21 / Spring Boot 3.5.16 / MySQL 8.0+ / Redis 7.4 / Vue 3`
- checked_at: `2026-07-11T10:39:26+08:00`
- verdict: `pass`

## 目标结果

- roles: 平台 Root/Admin、系统 Owner/Admin、普通平台账号、系统成员。
- entry_points: 平台后台、系统后台、系统访问申请、系统/租户 context 切换。
- expected_business_outcome: 完成平台与系统组织权限治理；申请批准后形成真实成员和租户访问；有效权限由服务端解释并按 deny 优先；授权变化使旧 context 失效；越权请求拒绝并审计。
- linked_requirements: `REQ-PLATFORM-002, REQ-SYSTEM-001, REQ-TENANT-001, REQ-ORG-001, REQ-RBAC-001, REQ-RBAC-002, REQ-CTX-002, REQ-AUDIT-001, REQ-SECURITY-001`。
- linked_journeys: `JRN-PA1, JRN-PA2, JRN-PA3, JRN-S2, JRN-S3, JRN-C2, JRN-C3, JRN-C5`。

## 证据矩阵

| 证据 | 路径 | 结果 | 边界 |
|---|---|---|---|
| 冻结合同 | `vs2-task-plan.md` | pass | 只覆盖 VS2 |
| Schema/migration | `evidence/vs2/schema.md` | pass | V1 升级、空库和 scope 约束 |
| Generator | `evidence/vs2/generator.md` | pass | generated base 不代表业务完成 |
| Backend integration/security | `evidence/vs2/backend-tests.md` | pass | 真实 HTTP/MySQL/Redis/审计 |
| Frontend unit/build | `evidence/vs2/frontend-tests.md` | pass with observation | 主 chunk 性能观察项 |
| Browser journey | `evidence/vs2/journey-e2e.md` | pass | desktop/mobile 累积旅程 |
| Framework validators | `.base/.cursor scripts/validate-framework.ps1` | pass | 模板和实例结构/合同 |

## Leader 结论

VS2 的组织、租户、成员、角色、允许/拒绝权限、数据范围、权限解释、访问申请、上下文失效、跨 scope 拒绝、审计和真实浏览器证据闭环；无开放 P0/P1。接受本开发批次，允许进入 VS3 无代码配置发布的合同拆分。

本结论不表示完整管理系统、release Gate 或用户最终验收通过。VS3-VS12 仍须逐节点实现，`userAccepted` 保持 `false`。
