# R87 Todo Message Workbench Usability

Status: $status

This is engineering evidence only. It does not close user signoff.

## Checks

- `PASS` state: R87 is active and R86 remains accepted engineering evidence - currentBatch=RECOVERY-R87
- `PASS` api-readback: approver scoped todo and message APIs are readable - pendingTodos=0, handledTodos=1, activeMessagesBefore=0, archivedBefore=1
- `PASS` message-state: message read/archive state is backed by API readback when active message exists, or retained archived state exists - action=, archivedAfter=1
- `PASS` permission: anonymous todo and message APIs are denied - /api/v1/systems/1121/todos/search?pageNo=1&pageSize=1; /api/v1/systems/1121/messages/search?pageNo=1&pageSize=1
- `PASS` frontend-source: R87 todo/message workbench source markers exist - missing=
- `PASS` deployed-asset: deployed frontend asset contains R87 markers - assetLength=229607, missing=
- `PASS` browser-todo: browser todo workbench exposes layout and detail/empty markers on desktop and mobile - results=[{"kind":"todo-desktop","viewport":"1366x900","hash":"#/systems/1121/todos","overflowX":0,"blockerText":false,"todoWorkbench":true,"todoLayout":true,"todoDetailPanel":true,"todoEmptyState":true,"todoRows":0,"messageCenter":false,"messageToolbar":false,"messageItems":0,"messageMarkReadButtons":0,"messageArchiveButtons":0,"messageEmptyState":false,"textSampleLength":352},{"kind":"message-desktop","viewport":"1366x900","hash":"#/systems/1121/messages","overflowX":0,"blockerText":false,"todoWorkbench":false,"todoLayout":false,"todoDetailPanel":false,"todoEmptyState":false,"todoRows":0,"messageCenter":true,"messageToolbar":true,"messageItems":0,"messageMarkReadButtons":0,"messageArchiveButtons":0,"messageEmptyState":true,"textSampleLength":251},{"kind":"todo-mobile","viewport":"390x720","hash":"#/systems/1121/todos","overflowX":0,"blockerText":false,"todoWorkbench":true,"todoLayout":true,"todoDetailPanel":true,"todoEmptyState":true,"todoRows":0,"messageCenter":false,"messageToolbar":false,"messageItems":0,"messageMarkReadButtons":0,"messageArchiveButtons":0,"messageEmptyState":false,"textSampleLength":352},{"kind":"message-mobile","viewport":"390x720","hash":"#/systems/1121/messages","overflowX":0,"blockerText":false,"todoWorkbench":false,"todoLayout":false,"todoDetailPanel":false,"todoEmptyState":false,"todoRows":0,"messageCenter":true,"messageToolbar":true,"messageItems":0,"messageMarkReadButtons":0,"messageArchiveButtons":0,"messageEmptyState":true,"textSampleLength":251}]
- `PASS` browser-message: browser message workbench exposes toolbar and message action markers - messageItemEvidence=False, archivedAfter=1
- `PASS` browser-containment: browser todo/message pages have no horizontal overflow or blocker text - overflow=0, blockers=0
- `PASS` signoff-boundary: user signoff remains false - user_script_passed=False

## Data

- System: `1121`
- Pending todos: `0`
- Handled todos: `1`
- Active messages before: `0`
- Archived messages after: `1`
- Browser audit: `D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r87-todo-message-workbench-usability\todo-message-workbench-browser-audit.json`
- User signoff remains `false`.
