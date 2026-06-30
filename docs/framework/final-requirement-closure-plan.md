# Final Requirement Closure Plan

Time: 2026-06-30 Asia/Shanghai

## Purpose

This plan turns the failing requirement coverage audit into executable product work.

The project is not complete until `scripts/final-requirement-coverage-audit.ps1` passes, which means every major requirement row is `PROVEN` or `USER_EXCLUDED`.

Current direction is to implement and prove the requirements, not exclude them.

## Execution Order

| Batch | Coverage Rows | Objective | Completion Evidence |
|---|---|---|---|
| FRC-0 Coverage Gate | all rows | Keep the coverage ledger and audit scripts truthful. | `final-requirement-coverage-audit.ps1` runs and reports current blockers. |
| FRC-1 Missing Product Surfaces | REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9 | Close the currently OPEN page/home/visual/page-designer/advanced-capability surfaces or split them into implementable task cards. | Deployed browser evidence plus updated coverage rows. |
| FRC-2 No-Code Configuration Depth | REQ-4.3, REQ-5.3, REQ-5.3.1, REQ-5.4, REQ-5.5, REQ-5.6, REQ-5.7, REQ-5.9, REQ-5.10, REQ-6.7, REQ-6.10 | Prove system admins can configure the no-code system beyond the current module/flow subset. | Browser/API/readback for module lifecycle, fields, dictionaries, org, menus, and permissions. |
| FRC-3 Runtime User Depth | REQ-4.4, REQ-5.11, REQ-5.13, REQ-5.14, REQ-5.16, REQ-5.20, REQ-6.4, REQ-6.5, REQ-6.6, REQ-6.11 | Prove normal members can use the customized system for real daily work across list/form/detail/file/import/export/message/work/mobile. | Deployed normal-member journeys with persistence, permission, state, and mobile evidence. |
| FRC-4 Workflow, Integration, AI Depth | REQ-4.5, REQ-5.12, REQ-5.15, REQ-5.19 | Prove workflow workbench, OpenAPI service, and AI capabilities are complete enough for the requirement, not only minimal happy paths. | Workflow simulation/runtime, OpenAPI scope/rate/callback evidence, AI policy/session/action/log evidence. |
| FRC-5 Operations, Robustness, Delivery | REQ-4.6, REQ-7, REQ-8, REQ-10, REQ-14.1-14.37, REQ-A | Prove technical architecture, robustness, launch rules, delivery standards, and confirmed clarifications. | Release/deploy evidence, operations evidence, appendix coverage report, and requirement rows updated. |
| FRC-6 Human Acceptance Pass | REQ-2.1, REQ-4.1, REQ-4.2, REQ-6.2 | Prove the whole system is coherent, attractive, understandable, and pleasant to use. | Fresh deployed role-journey audit, visual/wording review, and user signoff. |

## Batch Rules

- A batch cannot start from code. It starts by updating affected coverage rows and task cards.
- A batch cannot close with only API or build evidence.
- A batch cannot mark a row `PROVEN` unless the evidence covers the full requirement area named by the row.
- If a requirement row is too broad, split it into sub-rows before coding.
- If product scope is truly not wanted now, record `USER_EXCLUDED` only with explicit user approval.

## Current Next Batch

Start with FRC-1 because it contains all currently `OPEN` rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- `REQ-9` Advanced/new capabilities

FRC-1 must produce concrete task cards and deployed evidence before broader PARTIAL rows can be honestly closed.
