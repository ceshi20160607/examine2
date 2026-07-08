# R86 Page Designer Drag Canvas Runtime Contract

Status: PASS

Task: REC-P0-086 / FRC-1D

Base URL: http://127.0.0.1:18131

Evidence:

- Machine result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r86-page-designer-drag-canvas-runtime-contract-result.json
- Browser audit: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r86-page-designer-drag-canvas-runtime-contract\page-designer-browser-audit.json
- Screenshots: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r86-page-designer-drag-canvas-runtime-contract

Assertions:

- System admin configures and publishes module page main for module $ModuleId.
- Page designer component workbench renders component rows, action buttons, keyboard-focusable controls, and mobile preview.
- Browser interaction copies a component, moves component order, hides a component, saves the component layout, and the admin API reads back the changed component set.
- Runtime schema form still shows required-field validation for publicName and clears validation after a successful save.
- Page publish-check passed and published version is $(@{result=PUBLISHED_MODULE_PAGE; targetId=104; version=PAGE_v1783421161375; traceId=trc_e74026a7-7608-4534-90c0-abe1bf77b1ea; auditLogId=aud_trc_e74026a7-7608-4534-90c0-abe1bf77b1ea; asyncTaskId=; operatedAt=2026-07-07T18:46:01.3903326}.version).
- Module management shows the module page designer, selected module, selected page, and schema preview together.
- Normal member runtime schema reads the same version, hides secretNote, removes the hidden component, and marks readable fields readonly.
- Normal member direct create is rejected with HTTP 403, and browser create is disabled or falls back to a fully readonly form.
- Desktop and mobile browser permission-state screenshots have no horizontal overflow.
- Component readback count: 6; copied: toolbar_copy; hidden: toolbar_copy.
- Cleanup: 1130:DELETE, 1131:DELETE
