# R37 Runtime First-Navigation List/Detail Usability Evidence

Status: PASS

Base URL: http://127.0.0.1:18131

Task: REC-P0-037 Runtime first-navigation and list/detail usability hardening

Evidence:

- Machine result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r37-runtime-first-navigation-result.json
- Browser result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r37-runtime-first-navigation\runtime-first-navigation-browser.json
- Screenshots: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r37-runtime-first-navigation

Assertions:

- Normal member logs in through the deployed login form.
- Browser opens direct non-hash /systems/726/modules on desktop and mobile.
- The first runtime navigation normalizes to #/systems/726/modules and renders the runtime list, first row, detail panel, and published print preview without manual reload.
- The pre-created record R37 First Navigation Record 0630191726034_a6f571 is visible in list/detail/print preview on first navigation.
- No runtime empty state, loading state, or document horizontal overflow remains after first load.
- Normal-member API readback finds 1 matching record.
- Direct normal-member record create is rejected with HTTP 403.
- Cleanup: 726:DELETE, 727:DELETE
