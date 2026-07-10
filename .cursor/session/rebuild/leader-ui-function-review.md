# Leader UI And Function Review

## Status

Task: leader review after user reported the old artifact at `http://192.168.0.211:19999/` is confusing, unclear, and visually messy.

Status: leader draft complete

Coding remains closed.

## Evidence Used

- User screenshots:
  - contract list reference style;
  - contract detail right-work-area reference style.
- Current old artifact URL:
  - `http://192.168.0.211:19999/`
  - HTTP entry reachable through Nginx.
  - Entry HTML loads `/config.js`, `/assets/index-B1ms4WN3.js`, `/assets/index-GpmV72HI.css`.
  - `config.js` has `window.__UNEXAMINE_API_BASE_URL__ = ''`.
  - Browser rendering from this environment timed out, so the old artifact is not accepted as usable visual evidence.
- Current requirements rebuild artifacts:
  - `.cursor/session/rebuild/product-boundary-contract.md`
  - `.cursor/session/rebuild/engineering-architecture-map.md`
  - `.cursor/session/rebuild/requirement-task-breakdown.md`
  - `.cursor/session/rebuild/ui-system-interaction-contract.md`

## Leader Conclusion

The old artifact is not acceptable as the target product direction.

It may contain product words in the bundle, but the reported user experience is still unclear: layout hierarchy is messy, function ownership is not obvious, and the page does not express a stable enterprise operations product.

The reference screenshots show the correct direction: a dense CRM/ERP-like business system, not a marketing dashboard, decorative low-code demo, or scattered admin prototype.

## Reference Style Extracted From Screenshots

### Global Shell

- Deep navy top navigation.
- Compact logo/system area on the left.
- First-level product domains in the top bar.
- Current active domain clearly highlighted.
- Right-side global utilities: AI, notifications, user, quick tools, settings.
- No large marketing hero and no decorative cards as the primary work surface.

### List Surface

- Page title at top left with a small icon.
- Search and common filters directly under title.
- View tabs or filter chips near the search area.
- Primary action buttons at top right.
- Dense table as the main content.
- Fixed columns for selection, favorite, main business number, status, key amounts, customer, and action.
- Row click opens detail; action column only has distinct actions.
- Table density is high but orderly.

### Detail Surface

- Right-side work area or drawer opens from list while preserving list context.
- Left vertical secondary tabs inside the detail area.
- Header summary includes object name, customer, status, key amounts, dates, and primary actions.
- Main detail is a structured two-column form/read view.
- Detail tabs include related business objects: activity, products, payments, invoices, team members, attachments, operation records, and print records.
- The detail view is a working surface, not a generic drawer.

## Current Functional Completeness Judgment

Not complete.

The project currently has requirements and governance artifacts, but no accepted active implementation. The old deployed artifact must be treated as historical visual evidence only, not current completion.

| Area | Status | Problem |
|---|---|---|
| Product shell | incomplete | top-level domains are not yet proven as a clear platform/system shell |
| Business module runtime | incomplete | list/detail contract exists but is not proven in a real runnable system |
| Detail work area | incomplete | must become right-side tabbed business workspace |
| Flow | incomplete | must cover canvas, nodes, binding, publish checks, runtime readback |
| Application | incomplete | must be authorization valve for internal communication and external service exposure |
| Workbench | incomplete | must show context-based statistics and dashboard configured by admin |
| Work | incomplete | must include work dashboard, project tasks, normal tasks, and daily reports |
| Todo | incomplete | must be configuration-driven action/reminder workbench |
| Message | incomplete | must be user-related message stream with clear jump targets |
| AI | incomplete | must have backend/admin policy, scope, audit, and assisted actions |
| Admin | incomplete | must clearly separate platform admin and system admin |
| Permissions | incomplete | must prove positive/negative paths in backend and frontend |
| Persistence/readback | incomplete | must prove data survives and updates are visible |
| Side effects | incomplete | todo/message/log effects are not proven |
| Release usability | incomplete | old URL rendering is not accepted as a user-trial-ready system |

## Required Product Optimizations

1. Freeze one enterprise operations visual system.
   - Use compact dark top navigation.
   - Use dense table-first operational pages.
   - Avoid decorative dashboard/card-heavy surfaces for core work.

2. Split shells clearly.
   - Platform workbench.
   - Platform admin.
   - System runtime.
   - System admin.

3. Normalize list/detail pattern.
   - Lists use left-side tabs/categories where relevant.
   - Details use right-side tabbed work area.
   - Row click opens detail.
   - Row buttons only carry distinct actions.

4. Make every domain show its business result.
   - 工作台 shows statistics and dashboard configured by context.
   - Flow shows flow list/canvas/publish/runtime.
   - 应用 shows gateway apps/scopes/secret refs/call logs.
   - 工作 shows work dashboard/project tasks/normal tasks/daily reports.
   - AI shows assistant entry and admin policy relation.
   - 待办 shows configured action categories and processing.
   - 消息 shows message stream and jump outcomes.
   - 后台 shows system/platform management.

5. Define module runtime like the reference contract page.
   - Table: search, filters, saved views, column settings, selection, status, amounts, customer, actions.
   - Detail: header summary, left vertical tabs, structured fields, related objects, attachments, logs, print.

6. Stop accepting prototype-only actions.
   - No "success toast" as a fake result.
   - Every action must either be disabled with reason, wired to a real result, or excluded from the current scope.

7. Add visual acceptance checks before coding.
   - Desktop list density.
   - Detail right work area.
   - Top navigation clarity.
   - No overlapping text.
   - No mixed admin/runtime controls.
   - Mobile or narrow fallback for dense tables.

## Required Engineering Optimizations

1. Add a UI system contract before frontend coding.
   - Route shells.
   - Navigation hierarchy.
   - Table pattern.
   - Detail work area pattern.
   - Form/read-only field pattern.
   - Status color semantics.

2. Add frontend task fields to future task cards.
   - shell;
   - route;
   - list/detail pattern;
   - responsive behavior;
   - empty/loading/error/disabled states;
   - browser visual evidence.

3. Add backend side-effect requirements to every business action.
   - permission check;
   - state change;
   - persistence;
   - readback;
   - todo/message/log where relevant.

4. Treat the old URL as diagnostic only.
   - It can show what failed.
   - It cannot be accepted as target implementation.

## Required Task Plan Adjustment

`REQ-R0-008 UI System And Interaction Contract` is now part of the active requirements rebuild package.

Outputs:

- `.cursor/session/rebuild/ui-system-interaction-contract.md`
- `.cursor/architecture/ui-system.md`
- updated `requirement-task-breakdown.md`
- updated `user-review-package.md`

This task turns the reference screenshots into a concrete reusable UI contract.
