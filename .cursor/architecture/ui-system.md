# UI System Framework

## Purpose

This framework turns the user's reference screenshots and leader review into mandatory engineering rules for all future user-facing work.

Source contract:

- `.cursor/session/rebuild/ui-system-interaction-contract.md`
- `.cursor/session/rebuild/leader-ui-function-review.md`
- `.cursor/session/rebuild/product-boundary-contract.md`

## Product Direction

The product must be a dense enterprise operations system, similar to CRM/ERP/OA daily work tools:

- compact dark top navigation;
- table-first list pages;
- right-side detail work areas;
- clear action ownership;
- stable status and permission states;
- no decorative landing-page style for core work.

The old artifact at `http://192.168.0.211:19999/` is a negative reference only. Its confusing hierarchy, card-heavy operational screens, duplicate open actions, mixed admin/runtime controls, generic drawers, clipped/wrapped text, and toast-only fake success patterns are banned from future UI work.

## Mandatory UI Patterns

| Pattern | Required For | Rule |
|---|---|---|
| Global shell | platform/system contexts | compact dark navy top nav, active domain highlight, readable context label, right-side AI/todo/message/user/tools/settings utilities |
| Dense list | business records, Flow, Application, work, todo, message, admin lists | title, search, filters, tabs/chips, primary actions, dense table, visible status/key columns, stable row height |
| Right detail | record details, Flow detail, Application detail, task detail, todo processing | preserve list filters/scroll/view, show header summary, use stable tabs and right work area |
| Admin tree/list | org, roles, modules, dictionaries, config | left tree/category with selected-node feedback, right table/form ownership, draft/publish/check/readback states |
| Runtime form/read view | module records and detail fields | structured fields, two-column where useful, clear readonly/edit state |
| State surface | any user-visible async, blocked, empty, or failed condition | preserve layout, explain context/reason, provide valid recovery or readback |

## Pattern Gates

Global shell gates:

- top navigation must remain compact at normal desktop width;
- platform/system context must be visible and must change data scope;
- active domain cannot rely on color alone;
- right utilities cannot force domain navigation to wrap;
- narrow fallback must be named before implementation.

Dense list gates:

- normal desktop row height target is 36-44px unless the task justifies a different density;
- title, search, filters, saved views/chips, primary actions, table, pagination, selected count, and batch actions must read as one work surface;
- business identifier, status, owner/customer, amount/count, date, and distinct actions must remain visible when relevant;
- row click opens detail; a row "view/open/detail" button that duplicates row click is prohibited;
- horizontal overflow, column settings, and empty table behavior must be defined.

Right detail gates:

- detail opens as a right-side work area and keeps list context available;
- header summary contains object title, key status, related object, key amounts/dates, and primary actions;
- tabs/sections are stable for the module and include related objects, attachments, logs, comments, approval/operation history, or print records where relevant;
- disabled actions show reasons, and successful actions show readback in the detail/list state;
- close/back returns to the same list filters, scroll position, and selected view.

Admin tree/list gates:

- admin pages use a left tree/category plus right table/form for structured configuration;
- selected node/category is visible and owns the right-side data;
- platform admin and system admin share grammar but never blur data scope;
- publish, stop, enable, import/export, sync, and other critical config actions show async and final readback states;
- critical config is not accepted as a generic drawer or toast-only result.

## Mandatory States

Every user-facing task must define:

- loading;
- empty;
- no permission;
- validation error;
- backend error;
- disabled with reason;
- draft;
- publish check failed;
- published;
- stopped/disabled;
- async running;
- success readback.

Each state must name its surface owner: shell, list, detail, admin tree, table row, form field, action button, or related-object tab. A state without a visible owner is not acceptable for implementation.

## Acceptance Integration

Screenshots are visual evidence only. They prove hierarchy, density, clipping, overflow, and visible copy. They do not prove persistence, permissions, state transitions, or business completion.

For user-facing work, task acceptance must include:

- desktop visual screenshot or browser check;
- right-detail preservation check where detail exists;
- no duplicate row-click/action behavior;
- no runtime/admin mixing;
- no text overlap;
- narrow viewport fallback when applicable;
- API/readback and permission checks where behavior changes data or visibility.

Minimum visual evidence:

- desktop shell/list screenshot with active domain and dense table;
- detail-open screenshot proving the right workspace preserves list context;
- state screenshot or browser proof for the highest-risk non-happy state;
- admin tree/table screenshot when admin configuration is touched;
- narrow viewport screenshot or browser check for the named fallback.

Visual acceptance must reject any recurrence of the old `19999` confusion pattern: unclear hierarchy, decorative cards as the primary work surface, duplicated open actions, mixed admin/runtime controls, generic detail drawers, clipped controls, or success states that do not read back changed data.

## Required Frontend UI Checklist

No future frontend task may start implementation until it fills these fields:

- Product context: platform, system, or both.
- Domain and route group from `.cursor/session/rebuild/product-boundary-contract.md`.
- User role, permission boundary, visible actions, hidden actions, and disabled reasons.
- UI pattern from this framework and the exact list/detail/admin/state combination.
- Shell behavior: active top domain, context label, right utilities, profile/system switch, narrow fallback.
- List behavior: title/icon, search, filters, saved views/chips, primary actions, table columns, density, selection/batch actions, row click, row actions, pagination, column settings, horizontal overflow.
- Detail behavior: open trigger, preserved list state, right-work-area width, header summary, actions, tabs/sections, related objects, close/back behavior.
- Admin behavior: tree/category source, selected-node feedback, right table/form ownership, draft/publish/check/readback, critical-action confirmation.
- State behavior: loading, empty, no permission, validation error, backend error, disabled reason, draft, publish-check failed, published, stopped/disabled, async running, success readback.
- Data/readback: fields required for shell/list/detail/status, permission fields, action result, todo/message/log side effects where relevant.
- Visual evidence: desktop shell/list, detail-open, key state, admin tree/table if applicable, and narrow fallback.
- Anti-pattern check: explicitly confirm the task does not reproduce the old `19999` confusion.

## Framework Rule

No future task may enter implementation if it affects UI but does not name its UI pattern from this file or `.cursor/session/rebuild/ui-system-interaction-contract.md`.
