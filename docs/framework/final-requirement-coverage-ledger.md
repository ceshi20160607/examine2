# Final Requirement Coverage Ledger

Time: 2026-06-30 Asia/Shanghai

## Purpose

This ledger prevents the project from redefining the final goal around the features that already passed scripts.

The final target says users must be able to use the requirements in `docs/user_requirement.md`. Therefore, every major requirement section must have an explicit coverage status and evidence.

## Status Values

| Status | Meaning |
|---|---|
| `PROVEN` | Deployed evidence proves the requirement for the stated scope. |
| `PARTIAL` | Some evidence exists, but the requirement is broader than the proof. |
| `OPEN` | No strong deployed evidence yet, or the implementation is known incomplete. |
| `USER_EXCLUDED` | The user explicitly excluded this requirement from current final acceptance. |

Only `PROVEN` and `USER_EXCLUDED` can close a requirement. `PARTIAL` is not completion.

## Coverage Matrix

| Req ID | Source | Requirement Area | Status | Current Evidence | Gap / Next Action |
|---|---|---|---|---|---|
| REQ-2.1 | `docs/user_requirement.md#2.1` | Product goal: customizable no-code systems with workflow, OpenAPI, AI, tasks, messages, logs, and usable people-facing product. | PARTIAL | R21 J0-J7 PASS; R12/R13/J10 fresh audit subset PASS. | Need all functional and usability rows below to be `PROVEN` or `USER_EXCLUDED`. |
| REQ-4.1 | `docs/user_requirement.md#4.1` | Platform layer and custom system layer separation. | PARTIAL | R13 four-shell browser audit PASS. | Need manual role-journey audit for platform member/admin/system admin/system member information architecture. |
| REQ-4.2 | `docs/user_requirement.md#4.2` | Platform center. | PARTIAL | R4 platform admin breadth; R22 ops governance. | Need platform workspace usability audit beyond admin evidence. |
| REQ-4.3 | `docs/user_requirement.md#4.3` | Application configuration center. | PARTIAL | R12 module builder; R20 flow canvas; R19 pagination. | Need full app/page/action/print/configuration coverage, not only module/flow subset. |
| REQ-4.4 | `docs/user_requirement.md#4.4` | Application runtime. | PARTIAL | R17/R18 normal member runtime; R10 import/export subset. | Need full runtime page/list/form/detail/search/draft/advanced table coverage. |
| REQ-4.5 | `docs/user_requirement.md#4.5` | Workflow workbench. | PARTIAL | R3 approval; R8 todo/message; R20 flow canvas. | Need broader workflow workbench scenarios and simulation coverage. |
| REQ-4.6 | `docs/user_requirement.md#4.6` | System management and operations center. | PARTIAL | R4/R6/R9/R11/R19/R22. | Need full operator usability and all maintenance controls verified from deployed UI. |
| REQ-5.1 | `docs/user_requirement.md#5.1` | Account and login. | PARTIAL | R14 login; R15 password reset; R16 register-first-use; R9 SSO/no-member. | Need full MFA/SSO variants and account security UX coverage if still in final scope. |
| REQ-5.2 | `docs/user_requirement.md#5.2` | Systems and tenants. | PARTIAL | R1 context; R2 tenant switch/redraw; R16 first system. | Need full system lifecycle, tenant lifecycle, initialization, preview/publish and rollback UX. |
| REQ-5.3 | `docs/user_requirement.md#5.3` | Applications. | PARTIAL | R10 external app/OpenAPI; R12 module setup. | Need app lifecycle and platform-level app/system data interop coverage. |
| REQ-5.3.1 | `docs/user_requirement.md#5.3.1` | Module groups as runtime navigation. | PARTIAL | R2 module publish; R13 shell screenshots. | Need direct verification of module group visibility, sorting, role visibility and published navigation behavior. |
| REQ-5.4 | `docs/user_requirement.md#5.4` | Modules. | PARTIAL | R12 module builder; R19 module pagination. | Need all module lifecycle states, scenes, actions, permissions, print/import/export/publish rollback coverage. |
| REQ-5.5 | `docs/user_requirement.md#5.5` | Fields. | PARTIAL | R12 proves TEXT/DATE/SELECT/ATTACHMENT/AUTO_NUMBER subset. | Need all field types and field-specific config in the requirement, including formula/summary/relation/person/subtable if in scope. |
| REQ-5.6 | `docs/user_requirement.md#5.6` | Dictionaries. | PARTIAL | R4/R19 dictionary admin evidence; R12 status dictionary for module field. | Need dictionary types, versioning, colors/icons, reference impact and publish checks. |
| REQ-5.7 | `docs/user_requirement.md#5.7` | Departments and members. | PARTIAL | R12/R16 default department; R4 org/member admin. | Need full org tree, member lifecycle, binding, import/sync and permission impact. |
| REQ-5.8 | `docs/user_requirement.md#5.8` | Pages. | PARTIAL | R24 proves a system home/page configuration loop and a module page designer API/runtime loop: admin draft save/list readback/publish-check/publish, runtime readback, normal-member runtime read, and normal-member write denial. | Need deployed browser interaction evidence and broader page-definition coverage before `PROVEN`. |
| REQ-5.9 | `docs/user_requirement.md#5.9` | Menus. | PARTIAL | R13 shell navigation screenshots. | Need menu configuration, permissions, ordering and runtime behavior evidence. |
| REQ-5.10 | `docs/user_requirement.md#5.10` | Permissions. | PARTIAL | R2 permission negatives; R17 normal member denial; R9 no-member. | Need effective permission preview, field/data/action permission matrix and UI/backend consistency coverage. |
| REQ-5.11 | `docs/user_requirement.md#5.11` | Data records. | PARTIAL | R3/R17/R18 record CRUD subset; R10 OpenAPI records. | Need full list/form/detail/edit/history/draft/advanced query/sequence/attachment coverage. |
| REQ-5.12 | `docs/user_requirement.md#5.12` | Workflow. | PARTIAL | R3 approval runtime; R20 canvas. | Need full node library, branching, timers, external API, field update, simulation, publish impact. |
| REQ-5.13 | `docs/user_requirement.md#5.13` | Files. | PARTIAL | R3/R10 upload and attachment subset. | Need file permission, preview/download, version, relation, storage and failure coverage. |
| REQ-5.14 | `docs/user_requirement.md#5.14` | Import and export. | PARTIAL | R10 import/export tasks and result files. | Need rollback, precheck, mapping, error files, permissions and large-file UX. |
| REQ-5.15 | `docs/user_requirement.md#5.15` | OpenAPI. | PARTIAL | R10 external app, SecretRef, call logs, OpenAPI record calls. | Need broader scope management, rate limits, callbacks/webhooks and API documentation UX if in scope. |
| REQ-5.16 | `docs/user_requirement.md#5.16` | Messages. | PARTIAL | R8 message read/archive/filter subset. | Need templates/channels, delivery log, failure retry, do-not-disturb and platform/system separation evidence. |
| REQ-5.17 | `docs/user_requirement.md#5.17` | Logs and audit. | PARTIAL | R4/R11/R22 logs/trace evidence. | Need full login/business/API/AI/import/export/approval audit query and detail UX. |
| REQ-5.18 | `docs/user_requirement.md#5.18` | System management settings. | PARTIAL | R4/R6/R9/R11/R22 admin evidence. | Need all setting pages and risk confirmations verified from deployed UI. |
| REQ-5.19 | `docs/user_requirement.md#5.19` | Intelligent capabilities. | PARTIAL | R11 AI Agent boundaries, policy and confirmations. | Need model authorization lifecycle, user assistant flows, AI write/read permissions, logs and failure handling. |
| REQ-5.20 | `docs/user_requirement.md#5.20` | Work management: projects, tasks, daily reports, calendar. | PARTIAL | R7 work management; R13 layout. | Need full daily work usability, calendar, dashboard, task board, report draft and message/todo integration. |
| REQ-6.1 | `docs/user_requirement.md#6.1` | Overall visual style. | PARTIAL | R24 visual-density pass reduces system dashboard panel stacking and collapses system-admin load warnings; deployed browser metrics show system dashboard desktop panel count reduced to 4 with no horizontal overflow, and system-admin default load warnings summarized instead of fully stacked. | Need broader human/UI audit against aesthetics, hierarchy, density, wording, role clarity, and user acceptance before `PROVEN`. |
| REQ-6.2 | `docs/user_requirement.md#6.2` | Web main layout. | PARTIAL | R13 desktop/mobile shell screenshots. | Need manual inspection for ambiguity and information architecture. |
| REQ-6.3 | `docs/user_requirement.md#6.3` | Home page. | PARTIAL | R24 proves configurable system home page title/subtitle/widgets in admin and runtime dashboard on desktop/mobile. | Need platform home page and broader role-specific home page usability audit before `PROVEN`. |
| REQ-6.4 | `docs/user_requirement.md#6.4` | List pages. | PARTIAL | R17/R18 runtime list; R19 admin pagination. | Need full list design, filters, sort, columns, batch actions, empty/error states. |
| REQ-6.5 | `docs/user_requirement.md#6.5` | Form pages. | PARTIAL | R17/R18 create record; R12 builder forms. | Need validation, field-specific UI, drafts, errors and permission-specific forms. |
| REQ-6.6 | `docs/user_requirement.md#6.6` | Detail pages. | PARTIAL | R17/R18 detail subset. | Need full detail tabs, history, attachments, approval, logs and actions. |
| REQ-6.7 | `docs/user_requirement.md#6.7` | Field designer. | PARTIAL | R12 field type subset. | Need all field type specialized configuration. |
| REQ-6.8 | `docs/user_requirement.md#6.8` | Page designer. | PARTIAL | R24 page designer API/runtime closure proves admin draft save, list readback, publish-check, publish snapshot, runtime readback, normal-member runtime read, and normal-member write denial. | Need deployed browser page-designer interaction, visual preview, broader component coverage, copy/drag-drop/mobile preview, and usability evidence before `PROVEN`. |
| REQ-6.9 | `docs/user_requirement.md#6.9` | Flow designer. | PARTIAL | R20 flow canvas. | Need broader node/property/branch/zoom/usability coverage. |
| REQ-6.10 | `docs/user_requirement.md#6.10` | Permission page. | PARTIAL | R2/R17 permission negatives. | Need permission configuration page and effective permission preview. |
| REQ-6.11 | `docs/user_requirement.md#6.11` | Mobile. | PARTIAL | R13/R18 mobile containment. | Need mobile task usability across all primary journeys. |
| REQ-7 | `docs/user_requirement.md#7` | Technical architecture. | PARTIAL | Build/release and existing backend/frontend implementation evidence. | Need requirement-by-requirement technical architecture conformance audit. |
| REQ-8 | `docs/user_requirement.md#8` | Robustness. | PARTIAL | R5/R22 health/release; selected failure states. | Need full config/data/permission/workflow/deploy robustness audit. |
| REQ-9 | `docs/user_requirement.md#9` | Advanced/new capabilities. | OPEN | Some AI/flow evidence only. | Need command center, assistant, schema-driven page, advanced table, simulation, print designer decisions/evidence. |
| REQ-10 | `docs/user_requirement.md#10` | Full delivery standard. | PARTIAL | R5/R21/R22 release evidence. | Need all delivery checklist rows mapped to evidence and user signoff. |
| REQ-14.1-14.37 | `docs/user_requirement.md#14` | Launch capability rules: versioning, publish, lifecycle, business rules, uniqueness, rollback, print, KPI, health, permission preview, archive, confirmations, UX details, APIs, errors, idempotency, rate limit, cache, design system, performance, reports, audit, gray release, quotas, masking, accessibility, data design. | PARTIAL | R22 covers some operations; R10/R11/R20 cover selected items. | Need split task cards/evidence for every 14.x item or user-approved exclusion. |
| REQ-A | `docs/user_requirement.md#appendix-a` | Confirmed product clarifications. | PARTIAL | Many reflected in project rules and recovery evidence. | Need explicit appendix A coverage scan against implementation and UI. |

## Current Verdict

The final requirement coverage is not complete.

The existing R-series evidence proves important slices, but the original requirement remains broader than current proof. The next work must either:

1. implement and prove the missing areas, or
2. get explicit user exclusion for areas not intended in the current final acceptance.

No agent may claim final completion while any row is `PARTIAL` or `OPEN`.
