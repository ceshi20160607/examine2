# R8 Todo And Message Center Evidence

Time: 2026-06-27 22:36 Asia/Shanghai

## Result

- Task card: `REC-P0-011 Todo And Message Center Runtime Closure`
- Script: `scripts/recovery-r8-todo-message-center-smoke.ps1`
- Final orchestration: `scripts/recovery-r5-final-user-script.ps1`
- Final result file: `docs/evidence/recovery/r5-final-release-result.json`
- Status: PASS

## API Evidence

The R8 smoke script created a real approval setup through the R3 setup mode, then verified the todo/message center behavior:

- requester pending todo total: `0`
- approver pending todo total: `1`
- unread active message total: `1`
- read message total after mark-read: `1`
- active message total after archive: `0`
- archived message total after archive: `1`
- handled todo total after todo approval action: `1`

Latest final orchestration R8 step:

- systemId: `184`
- tenantId: `200`
- recordId: `63`
- pendingTodoId: `40`
- messageId: `42`
- markReadTraceId: `trc_4ffefe59-7339-406c-b241-839375be9072`
- archiveTraceId: `trc_bfa34877-33a2-4e95-afe6-474afdd430ab`
- todoActionTraceId: `trc_4730e1c0-5675-4b13-a913-eacdfd4e254d`
- cleanup: `184:DELETE`, `185:DELETE`, `186:DELETE`

## Browser Evidence

Browser evidence used a kept setup system and then cleaned it:

- setup file: `docs/evidence/recovery/r8-browser-setup.json`
- browser evidence systemId: `187`
- requester account: `r3_requester_0627223635066_d324b5`
- approver account: `r3_approver_0627223635066_d324b5`
- recordId: `64`
- pendingTodoId: `41`
- pendingMessageId: `43`
- active frontend asset: `/assets/index-BUGBsCVB.js`

Observed deployed frontend states:

- `#/systems/187/todos` rendered the todo center with heading `待办`, one table row, the created record id `64`, and approval-related text.
- `#/systems/187/messages` rendered the message center with heading `消息`, one message item, record id `64`, and controls for keyword filter, read status, archive status, mark-all-read, archive, and pagination.
- Clicking `全部标为已读` showed visible result text and the message status changed to `read`.
- Clicking `归档` removed the item from the default active stream and showed the empty active state.
- Clicking `已归档` showed one archived message containing record id `64`, with the archive button disabled.

Cleanup:

- `scripts/recovery-clean-test-systems.ps1 -Execute` deleted systems `187`, `188`, and `189`.
- Final cleanup dry-run reports `matchedCount=0`.

## Acceptance

R8 is accepted because the todo/message center now has role-scoped API readback, deployed-browser visibility, read/archive state transitions, default active-vs-archived behavior, todo handled transition, final release orchestration coverage, and cleanup evidence.
