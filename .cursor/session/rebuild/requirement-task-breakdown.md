# Requirement-To-Task Breakdown

## Status

Task: `REQ-R0-006 Requirement-To-Task Breakdown`

Status: leader draft complete

This file defines the format for future coding tasks. It is not the final implementation backlog yet.

## Required Task Fields

Each future task must include:

- task id;
- product domain from `product-boundary-contract.md`;
- role journey;
- user result;
- source requirement rows or source sections;
- prototype/flow reference;
- backend module;
- frontend shell/route group;
- UI pattern from `ui-system-interaction-contract.md`;
- database table group;
- generated base scope;
- handwritten manage scope;
- permission positive path;
- permission negative path;
- states;
- persistence/readback;
- todo/message/log side effects;
- acceptance script or evidence;
- browser visual evidence;
- non-completion boundary.

## Prohibited Task Shapes

- "Implement page X."
- "Implement CRUD for table Y."
- "Wire API 200."
- "Make prototype real" without role/data/permission/evidence.
- "Fix Flow" without saying Flow type, context, module/app binding, publish/runtime evidence.
- "Make layout nice" without naming shell, list/detail pattern, target state, and visual evidence.

## Draft Future Batch Shape

| Future Batch | Purpose | Starts Only After |
|---|---|---|
| `DESIGN-R1` | product/UI design package from contracts | requirements rebuild accepted |
| `CONTRACT-R1` | API/schema/task contract | design accepted |
| `BUILD-R1` | clean scaffold and module skeleton | contract accepted |
| `BUILD-R2` | auth/context/platform-system shells | scaffold accepted |
| `BUILD-R3` | admin configuration foundation | context accepted |
| `BUILD-R4` | business module config/runtime | admin foundation accepted |
| `BUILD-R5` | Flow and Application gateway | business module and app boundary accepted |
| `BUILD-R6` | work/todo/message/AI | Flow/Application side-effect contracts accepted |
| `BUILD-R7` | release and user trial readiness | role journeys accepted |

This shape is not final until the `REQ-R0` package is accepted.

## First Candidate Task Cards After Requirements Rebuild

These are candidate shapes only. They cannot become implementation tasks until requirements rebuild, design, API, and schema gates pass.

| Candidate | Product Domain | Main Outcome | Required UI Pattern | Backend Boundary |
|---|---|---|---|---|
| `DESIGN-R1-001` | all shells | final product IA and route map | global shell | no backend code |
| `DESIGN-R1-002` | 业务模块 | module list/detail prototype contract | dense list + right detail | no backend code |
| `DESIGN-R1-003` | Flow + 应用 | Flow/Application IA and state contract | dense list + right detail + canvas workspace | no backend code |
| `DESIGN-R1-004` | 工作台 / 工作 / 待办 / 消息 / AI | operational dashboard and personal work surfaces | dense list + right detail + notification surfaces | no backend code |
| `DESIGN-R1-005` | 后台 | platform admin and system admin IA | admin tree/table + right config detail | no backend code |
| `CONTRACT-R1-001` | context/auth | platform/system context API contract | shell states | plat/core manage proposal only |
| `CONTRACT-R1-002` | 业务模块 | module schema/runtime API contract | list/detail data needs | module tables + manage proposal |
| `CONTRACT-R1-003` | Flow/Application | flow canvas and gateway API contract | list/detail/canvas data needs | flow/app tables + manage proposal |
| `CONTRACT-R1-004` | Work/Todo/Message/AI | action, notification, assistant, and audit contracts | list/detail/action result needs | close module/prefix decision, side-effect mechanism, source reference, AI policy, and work/todo/message/AI table proposals |
| `BUILD-R1-001` | scaffold | clean generated project skeleton | no user UI yet | generated base readiness only |

## Prototype And Legacy Baseline Rule

Any `DESIGN-R1` or user-facing `BUILD-R*` task is rejected if it directly uses `docs/design/prototypes/index.html`, `.oldbk/frontend`, old `19999` behavior, or old screenshots as an implementation baseline.

Before reuse, the task must state:

- what prototype behavior is retained;
- what is rejected as old `19999` anti-pattern;
- which UI-system pattern replaces it;
- which route shell, list/detail pattern, state surface, and visual evidence will prove it.

## Task Card Quality Gate

A future implementation task is rejected if:

- it lacks a product domain from `product-boundary-contract.md`;
- it lacks a UI pattern from `ui-system-interaction-contract.md`;
- it does not name backend module and table group;
- it does not separate generator base from handwritten manage;
- it lacks permission positive and negative cases;
- it lacks readback and side-effect evidence;
- it is based on the old `19999` artifact rather than the new contracts.
- it directly implements old prototype or `.oldbk/frontend` behavior without a UI-system delta review.

## Current Gate

No coding tasks are accepted yet.
