# R32 Flow Simulation First Loop Evidence

- Base URL: http://127.0.0.1:18131
- System: 698
- Flow: 124
- High amount path: node_submit_review -> node_amount_condition -> node_manager_review -> node_end_passed
- Low amount path: node_submit_review -> node_amount_condition -> node_end_passed
- Predicted approvers: high=2, low=1
- Runtime instance created by simulation: high=False, low=False
- Missing approver blocker codes: E_SIM_APPROVER_EMPTY
- Cleanup: 698:DELETE
- Deployed assets: /assets/index-DSRIRs9p.js, /assets/index-BbjPFRgk.css
- Browser result: desktop and mobile document overflowX=0; the flow canvas scrolls inside its own panel.
- Browser branch proof: high amount shows Manager Review; low amount skips Manager Review; both show no runtime instance created.

Evidence files:
- D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r32-flow-simulation-result.json
- D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r32-flow-simulation\flow-simulation-api-audit.json
- D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r32-flow-simulation\flow-simulation-browser-audit.json
- D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r32-flow-simulation\desktop-flow-simulation.png
- D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r32-flow-simulation\mobile-flow-simulation.png

Layout correction:

- R32 found and fixed a real deployed UI defect: system-admin content and the flow canvas could create document-level horizontal overflow even when the business result was correct.
- The fix constrains system-admin content to the remaining column width and lets large flow canvases scroll inside the canvas panel instead of widening the page.

Remaining:

- This is first-loop engineering evidence only. Broader workflow node coverage, publish/runtime approval closure, and user acceptance remain open.
