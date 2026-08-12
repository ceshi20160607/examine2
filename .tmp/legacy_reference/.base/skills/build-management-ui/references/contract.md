# Management UI contract

Use this contract for list-centric CRM, ERP, operations, and administration modules. It controls default information architecture and action placement; it does not prescribe a component library.

## Required composition

| Region | Required behavior | Reject |
|---|---|---|
| Page header | Module title and compact right actions in one row; no more than one primary action | Oversized action panels, selection-dependent actions, multiple competing primary actions |
| Toolbar | Fuzzy search and structured advanced filter have different purposes; import/export stay compact | Search, views, drafts, and filters presented as interchangeable controls |
| Batch bar | Hidden with no selection; appears for selected rows | Always-visible batch edit/delete/export |
| Table | Visible header, intentional density, checkbox selection when batching, row click opens detail | Card-like rows without headers, duplicate View/Detail buttons |
| Row actions | Only actions different from opening detail | View, Details, Open, or another alias for row click |
| Detail | Right drawer/workspace, list context retained, overview first, related content in tabs | One long stack of fields, relations, comments, history, files, and team |
| States | Loading, empty, error, and permission denied | Blank canvas, silent failure, permission treated as empty data |

## Action placement

Use this order when deciding where a capability belongs:

1. Put the page's dominant creation action in `header.rightActions` as the only primary action.
2. Put fuzzy search and advanced filter in `toolbar`.
3. Put compact import/export in the toolbar, header secondary area, or More according to frequency. Every visible transfer action needs a requirement ID.
4. Put actions requiring selected records only in `toolbar.batchActions` with `when_selection_nonempty` visibility.
5. Make the row itself the detail entry. Keep edit/delete/transfer/approve as row actions only when required.
6. Put record-scoped collaboration and related objects in detail tabs.

## Optional-entry provenance

Declare optional entries in `optionalEntries`, including entries intentionally hidden. The validator recognizes these high-risk defaults:

- `favorite_module`
- `follow_record`
- `operations`
- `reports`
- `saved_views`

An entry may be default-visible only when `requirementId` is non-empty. `follow_record` must use `row_or_detail` placement. Operations and reports do not become separate navigation entries merely because dashboards can display their information.

## Dashboard

When `dashboard.enabled` is true:

- Set `primaryEditor` to `visual_drag_resize`.
- Set `primarySurface` to `visual_canvas`.
- Set `jsonMode` to `advanced_optional` or `disabled`.
- Keep coordinates and serialized configuration internal to the visual editor.

## Contract lifecycle

Freeze the JSON contract before coding. A new button, navigation item, default tab, or visible capability changes product behavior and requires a contract update plus validation. Permissions may hide an approved control, but permissions alone never create its placement.
