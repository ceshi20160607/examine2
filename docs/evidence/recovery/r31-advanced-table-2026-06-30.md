# R31 Advanced Table

Status: PASS

Task: REC-P0-031 Advanced Table

Base URL: http://127.0.0.1:18131

Evidence:

- Machine result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r31-advanced-table-result.json
- API audit: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r31-advanced-table\advanced-table-api-audit.json
- Browser audit: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r31-advanced-table\advanced-table-browser-audit.json
- Desktop screenshot: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r31-advanced-table\desktop-saved-view.png
- Mobile screenshot: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r31-advanced-table\mobile-saved-view.png

Assertions:

- Created 25 real runtime records for module 228.
- Saved view r31_compact_0630155841062_7dbd18 persisted hidden/reordered columns and sort/filter field definitions.
- Normal member readback after login can select the saved view.
- Server paging returns 10 of 25 records.
- Saved-view schema puts amountValue first and marks it fixed.
- Hidden field secretNote does not appear in saved-view columns or row fields.
- Scene default sort returns amountValue ASC; explicit DESC sort returns amountValue 25 first.
- Server field filter returns only rows whose publicName matches Item 2.
- Deployed browser asset is /assets/index-Dg4-dv2W.js.
- Browser desktop selected the saved view, rendered Amount Value / Public Name only, fixed the first column, and had duplicate detail buttons = 0.
- Browser row click opened the right-side detail for R31 Item 03 without a row-level detail button.
- Browser mobile viewport 390x720 had document overflowX = 0; table overflow stayed inside .runtime-table-shell.
- Cleanup: 694:DELETE, 695:DELETE, final dry-run matchedCount=0.
