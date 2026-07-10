# UI System And Interaction Contract

## Status

Task: `REQ-R0-008 UI System And Interaction Contract`

Status: leader draft complete

This file turns the user's reference screenshots and the old artifact problems into a concrete UI contract for future design and implementation.

Coding remains closed.

## Source Evidence

- User reference screenshot: contract list page.
- User reference screenshot: contract detail right work area.
- `.cursor/session/rebuild/leader-ui-function-review.md`
- `.cursor/session/rebuild/product-boundary-contract.md`

## UI Direction

The product must feel like a dense enterprise operations system: CRM/ERP/OA style, table-first, workflow-capable, and optimized for repeated daily work.

It must not feel like:

- a marketing landing page;
- a decorative dashboard;
- a scattered low-code demo;
- a generic admin template with unrelated cards;
- an API/debug console.

## Legacy 19999 Anti-Pattern Ban

The old artifact at `http://192.168.0.211:19999/` is diagnostic evidence only. It is not the accepted product direction, page baseline, or visual precedent.

Future work must explicitly avoid these old-artifact failure modes:

- unclear first screen where product domains, current context, and primary work target cannot be identified within one glance;
- loose dashboard/card composition used as the default surface for operational work;
- scattered buttons, repeated "view/open/enter" actions, or row actions that duplicate row click;
- runtime business navigation mixed with admin/settings navigation;
- generic drawers that hide business record context instead of a right-side work area;
- toast-only "success" that does not show state change, readback, todo/message/log side effects, or disabled reason;
- crowded text, clipped labels, wrapped buttons, or overlapping controls accepted as prototype style;
- API/debug/config words exposed as the main user experience without business task framing;
- inconsistent status colors, badges, and action availability across modules;
- unrelated cards or decorative widgets replacing dense list/detail workflows.

## Global Shell Contract

- Dark navy top navigation.
- The top bar is the primary product orientation layer, not a decorative header.
- Height is compact and stable; target normal desktop height is 48-56px unless a future design task proves another density.
- Left side shows logo/system identity and current platform/system context without taking over the work area.
- Compact first-level product domains sit in the top bar with predictable order from the product boundary contract.
- Active domain is visibly highlighted with both contrast and spatial treatment, not color alone.
- Right-side utility group is compact and ordered: AI, todo/message badges, user, quick tools, settings.
- Badge counts must be readable at normal desktop width and must not push domain navigation into wrapping.
- No duplicate system entry under profile if top-level switch exists.
- Platform context and system context use the same visual grammar but different data scopes.
- Switching platform/system context must visibly change the context label and data scope.
- Top navigation must remain available on list, detail, admin, todo, message, Flow, Application, and AI surfaces.
- Shell must define the narrow-viewport fallback: horizontal domain overflow, collapsed menu, or another named behavior.

## List Surface Contract

Use this for business lists, work lists, todo lists, message lists, Flow lists, Application lists, and admin list surfaces where appropriate.

Required structure:

1. Page title with small icon.
2. Search box and high-frequency filters near the title.
3. View tabs/filter chips under or beside search.
4. Primary actions on top right.
5. Dense table as the main content.
6. Optional left-side tabs/categories for list groups.
7. Row click opens detail.
8. Row action buttons only perform distinct actions.

Table expectations:

- stable row height;
- normal desktop row height target is 36-44px for high-density work surfaces;
- compact headers;
- header, filter row, pagination, and batch-action areas do not jump when data loads;
- selection column when batch actions exist;
- favorite/star column only when meaningful;
- primary business number as blue link-style text;
- status column with consistent color semantics;
- important money/quantity/date columns visible;
- action column compact and not overloaded;
- column settings/filtering available for configurable modules;
- saved views or quick filters are required when a module has multiple common work queues;
- table must have a defined horizontal overflow strategy before columns are hidden;
- pagination, total count, selected count, and batch actions must be visually close enough to the table to read as one control group;
- empty state must keep the table frame/header when that helps the user understand columns and filters;
- row hover/selected/open states must be distinguishable from status badges.

List pages fail acceptance when:

- the main area is mostly cards while the actual records are secondary;
- important identifiers, status, owner/customer, amount/count, or date columns are hidden at normal desktop width;
- action buttons occupy more visual weight than the business record itself;
- clicking a row and clicking a "view/open/detail" row button do the same thing;
- filters, search, saved views, and primary actions are scattered across unrelated regions.

## Detail Surface Contract

Use this for business record details, Flow detail, Application detail, task detail, todo processing, message target detail, and admin object detail where appropriate.

Required structure:

1. Right-side work area or drawer opens while preserving list context.
2. Header summary with object title, key status, key related object, key amounts/dates, primary actions.
3. Left vertical secondary tabs or clear right-side tabs.
4. Main body uses structured two-column fields or task-specific panels.
5. Related objects are tabs/sections, not random cards.
6. Operation records, attachments, print records, comments, approval history, and logs have stable locations.

Right work area rules:

- It is a business workspace, not a generic modal drawer.
- Opening detail must not erase the user's list filters, scroll position, selected view, or current context.
- Normal desktop width should be large enough for two-column fields and related tabs while leaving list context visible.
- The detail header should remain visible or quickly recoverable while scrolling detail content.
- Primary actions live in the detail header/action zone and must show disabled reasons when unavailable.
- Secondary tabs must be stable across records in the same module; unavailable tabs are hidden or disabled with a reason by contract, not by accident.
- Long related tables inside detail use dense table rules at a smaller scope.
- Close/back behavior returns to the same list state.

Reference detail tab examples:

- 详细资料 / 基本信息;
- 活动;
- 产品;
- 回款;
- 发票;
- 团队成员;
- 附件;
- 操作记录;
- 打印记录.

## Admin Surface Contract

- Admin pages use left tree/category plus right table/form when managing structured configuration.
- Runtime business navigation must not be mixed into admin settings.
- Config pages must show draft/publish/publish-check/readback states.
- Critical config cannot be a generic drawer or toast-only result.
- Tree/category width, selected node, search/filter, and empty states must be defined for each admin module.
- Right table/form must show which tree node or category is currently active.
- Admin tables follow the dense list contract but prioritize configuration identity, status, owner, updated time, and publish state.
- Dangerous actions require clear disabled reason, confirmation, permission result, and readback.
- Publishing, stopping, enabling, importing, exporting, and syncing must show async running and final readback states.
- Platform admin and system admin can reuse components, but labels and data scope must make the current context unmistakable.

Admin pages fail acceptance when:

- a normal member can confuse admin configuration with runtime business operation;
- a tree selection changes hidden state without visible active-node feedback;
- publish/check/readback is represented only by a toast;
- critical configuration is edited in an unstructured free-form panel without field grouping or validation surface.

## Status And Interaction States

Every future UI task must define:

- loading;
- empty;
- no permission;
- validation error;
- backend error;
- disabled state with reason;
- draft;
- publish check failed;
- published;
- stopped/disabled;
- async task running;
- success readback.

State surface rules:

- Loading states must preserve expected layout dimensions for shell, table, detail, or admin tree/form.
- Empty states must name the current filter/context and provide the next valid action when one exists.
- No-permission states must distinguish hidden action, disabled action, and blocked page.
- Validation errors must be close to the field or row that caused them and summarized when multiple errors exist.
- Backend errors must offer retry or recovery when safe, and must not pretend success.
- Disabled states must show a reason in tooltip, inline copy, or status text.
- Draft, publish-check failed, published, stopped/disabled, and async running are product states, not only visual badges.
- Success readback must show the updated state/data after the action, not only a transient toast.

## Visual Acceptance Checks

Before coding acceptance, screenshots or browser checks must prove:

- top navigation is clear and not crowded beyond usability;
- list surface is dense but readable;
- detail opens on the right and preserves list context;
- right detail header, tabs, and key fields are visible without visual collision;
- admin tree/table pages show selected tree node and right-side data ownership;
- text does not overlap controls;
- buttons do not wrap awkwardly;
- row click and row action buttons are not duplicated;
- admin/runtime controls are not mixed;
- important table columns remain visible at normal desktop width;
- narrow viewport has a defined fallback.

Minimum visual evidence for future frontend tasks:

- desktop shell screenshot showing active domain and right utilities;
- desktop list screenshot showing title, filters, primary actions, dense table, status badges, and action column;
- detail-open screenshot showing preserved list context and right work area;
- state screenshot or browser proof for at least the riskiest non-happy state in the task;
- admin tree/table screenshot when the task touches backend/admin configuration;
- narrow viewport screenshot or explicit browser check for the named fallback behavior.

Visual acceptance fails if any screenshot repeats the old `19999` confusion: unclear hierarchy, card-heavy default work area, mixed admin/runtime controls, duplicate open actions, clipped labels, or fake success without readback.

## Implementation Impact

Future frontend tasks must include:

- target shell;
- route group;
- list/detail pattern;
- table density and columns;
- detail tabs;
- state surfaces;
- browser visual evidence.

## Required UI Checklist For Future Frontend Tasks

Every future frontend task that touches user-facing UI must fill this checklist before implementation starts:

- Product context: platform context, system context, or both.
- Domain: one of the product-boundary domains and the exact route group.
- User role and permission boundary: who can see the page, actions, and disabled reasons.
- UI pattern: global shell, dense list, right detail, admin tree/list, runtime form/read view, state-only surface, or a named combination.
- Navigation contract: active top domain, context label, breadcrumb or local title, and profile/system-switch behavior.
- List contract: title icon, search, filters, saved views/chips, primary actions, table columns, row height target, selection/batch behavior, row click behavior, row action behavior, pagination, column settings, horizontal overflow.
- Detail contract: open trigger, preserved list state, detail width behavior, header summary fields, primary/secondary actions, tabs/sections, related objects, close/back behavior.
- Admin contract: left tree/category source, selected-node feedback, right table/form ownership, draft/publish/check/readback states, critical-action confirmation.
- State contract: loading, empty, no permission, validation error, backend error, disabled reason, draft, publish-check failed, published, stopped/disabled, async running, success readback.
- Data/readback contract: data needed for shell/list/detail/status, permission fields, action result, side effects in todo/message/log where relevant.
- Visual evidence plan: desktop shell/list, detail-open, key state, admin tree/table if applicable, and narrow fallback.
- Anti-pattern review: confirm no old `19999` confusion pattern remains.

Future backend tasks must include:

- data needed by list header/table/detail header/detail tabs;
- permissions for visible actions;
- readback after each action;
- side effects shown by todo/message/log surfaces.
