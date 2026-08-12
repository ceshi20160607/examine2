---
name: build-management-ui
description: Build or review human-readable management-system list, table, form, right-side detail, and dashboard interfaces with restrained action placement and explicit UI states. Use when Codex creates or changes CRM/ERP/admin pages, data tables, module workbenches, detail drawers, configurable dashboards, or their UI contracts, especially before frontend coding or when an existing page feels cluttered, repetitive, or developer-oriented.
---

# Build Management UI

Create a frozen UI contract before coding. Optimize for the user's current task, not feature visibility or permission coverage.

## Workflow

1. Read the product requirement and identify the role, business task, real entry point, primary object, and explicit optional capabilities.
2. Read [references/contract.md](references/contract.md). Classify every control as page primary, ordinary toolbar, selection-only batch, row-specific, detail-tab, advanced, or absent.
3. Copy [references/example.json](references/example.json) and adapt it. Use [references/page-contract.schema.json](references/page-contract.schema.json) for the data shape.
4. Run `python scripts/validate_ui_contract.py <contract.json>`. Do not start frontend implementation until it passes.
5. Implement the frozen contract. Reuse the project's shell, tokens, table, drawer, tabs, state, and permission components.
6. Verify the real desktop entry and affected component tests. Keep full responsive, accessibility, and visual-matrix work in the planned hardening stage unless the current task explicitly includes it.
7. Re-run the validator after review changes. Treat a new default entry or duplicated action as a contract change, not a harmless UI addition.

## Non-negotiable layout

- Keep the module title and compact right-side actions on one page-header row. Allow at most one primary action; move lower-frequency actions to compact secondary buttons or More.
- Keep fuzzy search and advanced filtering distinct: search handles quick text matching; advanced filtering handles structured conditions.
- Show batch actions only when at least one row is selected.
- Render a real table header and an intentional compact or comfortable density.
- Open detail by clicking the row. Do not add a redundant View/Detail row action.
- Keep row actions only for behavior different from opening detail, such as edit, delete, transfer, or approve.
- Open detail in a right-side drawer/workspace, preserve list context, and organize overview and related data with tabs. Do not append every capability into one vertical stack.
- Provide loading, empty, error, and permission-denied states.

## Scope restraint

- Do not expose module favorites, record following, Operations, Reports, saved views, or similar entries by default without an explicit requirement and placement decision.
- Do not turn every authorized backend capability into a visible button.
- Do not create duplicate controls with the same purpose under different names.
- Do not add a separate Operations or Reports navigation item merely because metrics or dashboard data exist.
- Treat record following as a row/detail capability, never a module-level page action.

## Dashboard rule

Make visual drag, reorder, and resize the primary dashboard editing surface. JSON may exist only as an optional advanced/import-export representation; never require ordinary administrators to write coordinates or JSON.

## Validation

Run built-in negative cases after changing the contract rules:

```powershell
python scripts/validate_ui_contract.py --self-test
```

The validator is deliberately stricter than the JSON Schema for semantic rules such as redundant detail actions, selection-only batch behavior, optional-entry provenance, and visual-first dashboards.
