# Examine2 最终目标总账

## 1. Meta

- goal_id: `EXAMINE2-FINAL-GOAL-V1`
- project: `Examine2`
- owner: `leader`
- created_at: `2026-08-07T18:50:00+08:00`
- updated_at: `2026-08-08T11:46:00+08:00`
- source_requirements: `docs/user_requirement.md`, `docs/temp_flow.md`
- source_designs: `.cursor/session/rebuild/requirement-understanding.md`, `.cursor/session/rebuild/design-package.md`
- status: `OPEN`

## 2. 最终目标

平台、系统和外部角色能从真实入口完成冻结的业务旅程；管理员能够配置、发布、解释、恢复和审计；普通成员能在桌面/移动端完成业务、流程、工作、消息和文件；外部系统能在应用 scope 内安全集成；运维能从不可变包安装、升级、备份、恢复和回滚。工程完成必须有源码、API/数据库读回、权限负例、真实浏览器、性能/可访问性、恢复演练和唯一发布包证据，最终用户验收只能由用户签字。

## 3. 当前非完成边界

- 旧阶段/集成证据只证明其列出的局部功能，不再自动外推为完整旅程。
- 页面、路由、CRUD、脚本、截图或 mock 单独存在均不构成完成。
- 当前工程门 `OPEN`，用户状态全部 `OPEN`；CP3 保持 `not_ready`。
- `PASS` 行是源码审计确认已有真实闭环的工程基线，仍须进入最终全量回归；`OPEN` 行是缺失、弱覆盖或刚实现但尚未并入全量验收的工作。

## 4. 角色旅程 Gate（52/52）

| journey_id | engineering | evidence / next action | user |
|---|---|---|---|
| JRN-A1 | PASS | `.cursor/session/evidence/vs1/acceptance.md` | OPEN |
| JRN-A2 | PASS | `.cursor/session/evidence/cycle118-onboarding-command-center/acceptance.md` | OPEN |
| JRN-A3 | PASS | `.cursor/session/evidence/cycle-account-recovery-109/acceptance.md` | OPEN |
| JRN-A4 | PASS | `.cursor/session/evidence/vs1/acceptance.md` | OPEN |
| JRN-P1 | PASS | `.cursor/session/evidence/cycle118-platform-flow-dashboard/acceptance.md` | OPEN |
| JRN-P2 | PASS | `.cursor/session/evidence/cycle118-platform-flow-dashboard/acceptance.md` | OPEN |
| JRN-P3 | PASS | `.cursor/session/evidence/cycle118-platform-openapi/acceptance.md` | OPEN |
| JRN-P4 | PASS | `.cursor/session/evidence/cycle118-platform-lifecycle-settings/acceptance.md` | OPEN |
| JRN-P5 | PASS | `.cursor/session/evidence/cycle118-platform-work-message/acceptance.md` | OPEN |
| JRN-P6 | PASS | `.cursor/session/evidence/cycle118-platform-work-message/acceptance.md` | OPEN |
| JRN-P7 | PASS | `.cursor/session/evidence/cycle118-platform-work-message/acceptance.md` | OPEN |
| JRN-P8 | PASS | `.cursor/session/evidence/cycle118-sso-profile-security/acceptance.md` | OPEN |
| JRN-P9 | PASS | `.cursor/session/evidence/fast-ai-platform-agent-78/acceptance.md` | OPEN |
| JRN-PA1 | PASS | `.cursor/session/evidence/cycle118-platform-lifecycle-settings/acceptance.md` | OPEN |
| JRN-PA2 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-PA3 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-PA4 | PASS | `.cursor/session/evidence/cycle118-platform-flow-dashboard/acceptance.md` | OPEN |
| JRN-PA5 | PASS | lifecycle settings plus `.cursor/session/evidence/cycle118-sso-profile-security/acceptance.md` | OPEN |
| JRN-PA6 | PASS | `.cursor/session/evidence/cycle118-unified-audit/acceptance.md` | OPEN |
| JRN-S1 | PASS | `.cursor/session/evidence/vs1/acceptance.md` | OPEN |
| JRN-S2 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-S3 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-B1 | PASS | `.cursor/session/evidence/fast-dashboard-publish-62/acceptance.md` | OPEN |
| JRN-B2 | PASS | `.cursor/session/evidence/vs3/acceptance.md` | OPEN |
| JRN-B3 | PASS | `.cursor/session/evidence/p4-b3/acceptance.md` | OPEN |
| JRN-B4 | PASS | `.cursor/session/evidence/p4-c3/acceptance.md` | OPEN |
| JRN-B5 | PASS | `.cursor/session/evidence/cycle-phase-gap-closure-116/acceptance.md` | OPEN |
| JRN-B6 | PASS | `.cursor/session/evidence/fast-unified-todo-action-59/acceptance.md` | OPEN |
| JRN-B7 | PASS | `.cursor/session/evidence/fast-import-job-preview-commit-27/acceptance.md` | OPEN |
| JRN-B8 | PASS | `.cursor/session/evidence/fast-work-project-kanban-calendar-56/acceptance.md` | OPEN |
| JRN-B9 | PASS | `.cursor/session/evidence/fast-unified-todo-action-59/acceptance.md` | OPEN |
| JRN-B10 | PASS | system message controller/tests; final regression pending | OPEN |
| JRN-B11 | PASS | `.cursor/session/evidence/cycle118-onboarding-command-center/acceptance.md` | OPEN |
| JRN-C1 | PASS | `.cursor/session/evidence/cycle118-onboarding-command-center/acceptance.md` | OPEN |
| JRN-C2 | PASS | `.cursor/session/evidence/cycle115-platform-lifecycle/acceptance.md` | OPEN |
| JRN-C3 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-C4 | PASS | `.cursor/session/evidence/cycle116-identity-sync/acceptance.md` | OPEN |
| JRN-C5 | PASS | `.cursor/session/evidence/vs2/acceptance.md` | OPEN |
| JRN-C6 | PASS | `.cursor/session/evidence/vs3/acceptance.md` | OPEN |
| JRN-C7 | PASS | `.cursor/session/evidence/cycle114-work-config/acceptance.md` | OPEN |
| JRN-C8 | PASS | `.cursor/session/evidence/fast-ai-system-read-agent-75/acceptance.md` | OPEN |
| JRN-C9 | PASS | `.cursor/session/evidence/cycle-phase-gap-closure-116/acceptance.md` | OPEN |
| JRN-C10 | PASS | `.cursor/session/evidence/vs3/acceptance.md` | OPEN |
| JRN-C11 | PASS | `.cursor/session/evidence/cycle115-dashboard-external-source/acceptance.md` | OPEN |
| JRN-C12 | PASS | `.cursor/session/evidence/fast-openapi-application-foundation-25/acceptance.md` | OPEN |
| JRN-C13 | PASS | `.cursor/session/evidence/cycle118-unified-audit/acceptance.md` | OPEN |
| JRN-E1 | PASS | `.cursor/session/evidence/cycle118-platform-openapi/acceptance.md` | OPEN |
| JRN-E2 | PASS | `.cursor/session/evidence/fast-openapi-record-file-72/acceptance.md` | OPEN |
| JRN-AI1 | PASS | `.cursor/session/evidence/fast-ai-platform-ops-query-80/acceptance.md` | OPEN |
| JRN-AI2 | PASS | `.cursor/session/evidence/fast-ai-confirmed-record-write-76/acceptance.md` | OPEN |
| JRN-PUB1 | PASS | `.cursor/session/evidence/cycle118-config-recovery/acceptance.md` | OPEN |
| JRN-OPS1 | PASS | `.cursor/session/evidence/cycle118-delivery-recovery/acceptance.md` | OPEN |

## 5. 需求覆盖（57/57）

| requirement_id | engineering | journey / gap | user |
|---|---|---|---|
| REQ-AI-001 | PASS | JRN-AI1/JRN-P9 | OPEN |
| REQ-AI-002 | PASS | JRN-AI2/JRN-C8 | OPEN |
| REQ-APP-001 | PASS | JRN-P3/JRN-E1 | OPEN |
| REQ-AUDIT-001 | PASS | JRN-C13/JRN-PA6 | OPEN |
| REQ-AUTH-001 | PASS | JRN-A1/A3/A4/P8 | OPEN |
| REQ-AUTH-002 | PASS | JRN-A2/C1 | OPEN |
| REQ-COLLAB-001 | PASS | JRN-B5 | OPEN |
| REQ-CONFIG-001 | PASS | JRN-PUB1 / cross-domain draft recovery | OPEN |
| REQ-CTX-001 | PASS | JRN-A1/S1 | OPEN |
| REQ-CTX-002 | PASS | JRN-S2 | OPEN |
| REQ-DASH-001 | PASS | system dashboard plus JRN-P1/PA4 | OPEN |
| REQ-DATA-001 | PASS | JRN-B3/B4 | OPEN |
| REQ-DATA-002 | PASS | JRN-B3/B7 | OPEN |
| REQ-DATA-MODEL-001 | PASS | JRN-C6/B4 | OPEN |
| REQ-DATASOURCE-001 | PASS | JRN-C11 | OPEN |
| REQ-DEPLOY-001 | PASS | JRN-OPS1 / rolling package install and operation rehearsal | OPEN |
| REQ-DICT-001 | PASS | JRN-C10 | OPEN |
| REQ-DOCS-001 | PASS | packaged operator/user/integration manuals and rehearsed commands | OPEN |
| REQ-EFFICIENCY-001 | PASS | JRN-B11 | OPEN |
| REQ-EVENT-001 | PASS | event/outbox delivery slices | OPEN |
| REQ-EXPORT-001 | PASS | JRN-B7 | OPEN |
| REQ-FIELD-001 | PASS | JRN-C6/B4 | OPEN |
| REQ-FILE-001 | PASS | JRN-B5/B7 | OPEN |
| REQ-FLAG-001 | PASS | typed platform settings and versioned mutation | OPEN |
| REQ-FLOW-001 | PASS | system Flow plus JRN-P2 platform Flow | OPEN |
| REQ-FLOW-002 | PASS | system Flow plus JRN-P2 platform instance/readback | OPEN |
| REQ-FLOW-003 | PASS | system Flow event/OpenAPI/periodic triggers | OPEN |
| REQ-IMPORT-001 | PASS | JRN-B7 | OPEN |
| REQ-JOB-001 | PASS | import/export/file/event jobs | OPEN |
| REQ-KPI-001 | PASS | JRN-C11/B1 | OPEN |
| REQ-MENU-001 | PASS | JRN-B2/B11 | OPEN |
| REQ-MESSAGE-001 | PASS | system inbox plus isolated JRN-P7 platform inbox | OPEN |
| REQ-MIGRATION-001 | PASS | config recovery plus backup/restore/upgrade/rollback rehearsal | OPEN |
| REQ-MOBILE-001 | PASS | rear-camera image capture, photographed barcode decoding, existing mobile list/form/detail/approval/file journeys; focused frontend 5/5 | OPEN |
| REQ-MODULE-001 | PASS | JRN-C6/B2 | OPEN |
| REQ-MODULE-GROUP-001 | PASS | JRN-C6/B2 | OPEN |
| REQ-OBS-001 | PASS | JRN-C13/PA6 unified health and trace | OPEN |
| REQ-OPENAPI-001 | PASS | system OpenAPI plus JRN-E1 platform API | OPEN |
| REQ-ORG-001 | PASS | JRN-PA2/C3 | OPEN |
| REQ-PAGE-001 | PASS | JRN-C6/B4 | OPEN |
| REQ-PERF-001 | DEFERRED | explicit product decision on 2026-08-08: do not run million-record, high-concurrency or long-running performance work before/for this delivery | OPEN |
| REQ-PLATFORM-001 | PASS | JRN-P1 through JRN-P9 | OPEN |
| REQ-PLATFORM-002 | PASS | JRN-PA1 through JRN-PA6 | OPEN |
| REQ-PRINT-001 | PASS | JRN-B5/C6 | OPEN |
| REQ-RBAC-001 | PASS | JRN-PA3/C5 | OPEN |
| REQ-RBAC-002 | PASS | JRN-PA3/C5 | OPEN |
| REQ-RULE-001 | PASS | JRN-C6/B4 | OPEN |
| REQ-RUNTIME-001 | PASS | JRN-B3/B4/B5 | OPEN |
| REQ-RUNTIME-002 | PASS | JRN-B3/B4/B5 | OPEN |
| REQ-RUNTIME-003 | PASS | JRN-B3/B4/B5 | OPEN |
| REQ-SECURITY-001 | PASS | account profile/MFA/session management, typed global settings, production security gate and stale-cookie re-login recovery | OPEN |
| REQ-SHELL-001 | PASS | JRN-A1/S1/B2 | OPEN |
| REQ-SSO-001 | PASS | JRN-C4/PA5/P8 | OPEN |
| REQ-SYSTEM-001 | PASS | initialization retry, lifecycle and tombstone recovery | OPEN |
| REQ-TENANT-001 | PASS | JRN-C2/S2 | OPEN |
| REQ-TODO-001 | PASS | system Todo plus JRN-P6 platform projection | OPEN |
| REQ-WORK-001 | PASS | system work plus JRN-P5 platform work | OPEN |

## 6. 非功能覆盖（8/8）

| nfr_id | engineering | gap / evidence | user |
|---|---|---|---|
| NFR-A11Y-001 | PASS | `.cursor/session/evidence/cycle118-a11y-usability/acceptance.md` | OPEN |
| NFR-MAINT-001 | OPEN | architecture enforcement and final docs | OPEN |
| NFR-OBS-001 | OPEN | unified logs/health/metrics | OPEN |
| NFR-PERF-001 | DEFERRED | explicitly removed from the required delivery scope by the product owner on 2026-08-08 | OPEN |
| NFR-PORT-001 | OPEN | PowerShell/POSIX package rehearsal | OPEN |
| NFR-REL-001 | PASS | backup/restore/restart/upgrade/rollback rehearsal; timing evidence in Cycle 118 | OPEN |
| NFR-SEC-001 | PASS | `.cursor/session/evidence/release-117/security/acceptance.md` | OPEN |
| NFR-USABILITY-001 | OPEN | all-role browser journey and user acceptance | OPEN |

## 7. 最终证据矩阵

| evidence | required | status |
|---|---:|---|
| Full backend/frontend/build and migration | yes | OPEN |
| 52 real role journeys with permission negatives/readback | yes | OPEN |
| Desktop/mobile visual, axe, keyboard/focus | yes | OPEN |
| Million-record/concurrency/query-plan/browser performance | no — explicitly deferred by product owner | DEFERRED |
| Clean install, upgrade, backup, restore and rollback | yes | OPEN |
| Security/dependency/configuration fail-closed | yes | OPEN |
| Immutable formal package with manifest/version/manuals | yes | OPEN |
| Explicit user sign-off | yes | OPEN |

## 8. 最终签字边界

- engineering_final_gate: `OPEN`
- user_signoff_required: `yes`
- user_accepted: `false`
- user_signoff_path: `null`
- known_limitations: 见所有 `OPEN` 行；性能容量门禁按产品负责人 2026-08-08 的明确决定延期，不得自行恢复。

完成层级固定为 `TASK -> SLICE -> PHASE -> JOURNEY -> RELEASE -> FINAL`。低层证据不能自动继承为高层通过。
