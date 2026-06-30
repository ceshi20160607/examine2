# R3 Runtime And Approval Smoke Evidence

Time: 2026-06-27 Asia/Shanghai

Scope:

- `REC-P0-005 Runtime Record CRUD, Detail, Draft, Sequence, And History`
- `REC-P0-006 Approval, Todo, Message, And Audit Closure`

Repeatable script:

- `scripts/recovery-r3-runtime-approval-smoke.ps1`

Command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/recovery-r3-runtime-approval-smoke.ps1 -BaseUrl http://127.0.0.1:18131
```

## Result

Status: PASS

Latest evidence run:

- `targetSystemId=115`
- `targetTenantId=120`
- `requesterAccount=r3_requester_0627200557600_0e0c5d`
- `approverAccount=r3_approver_0627200557600_0e0c5d`
- `runtimeRoleId=142`
- `requesterMemberId=141`
- `requesterBindingId=141`
- `approverMemberId=142`
- `approverBindingId=142`
- `requesterEffectiveRoles=[R3_RUNTIME_MEMBER_0627200557600_0e0c5d]`
- `approverEffectiveRoles=[R3_RUNTIME_MEMBER_0627200557600_0e0c5d]`
- `moduleId=51`
- `sceneId=86`
- `permissionVersion=perm_1782561963802_trc76bf8`
- `runtimeSchemaActionCount=3`
- `runtimeSchemaColumnCount=7`
- `draftId=draft_trc_e680`
- `sequenceA=[R3-0627200557600_0e0c5d-0001, R3-0627200557600_0e0c5d-0002]`
- `sequenceB=[R3-0627200557600_0e0c5d-0003]`
- `recordId=37`
- `uploadedFileId=file_f45e010e_1782561965286`
- `uploadedFileName=r3-runtime-attachment-0627200557600_0e0c5d.txt`
- `attachmentBindCount=1`
- `searchTotal=1`
- `historyTotal=2`
- `approvalAccepted=true`
- `pendingTaskId=31`
- `requesterPendingTodoTotal=0`
- `approverPendingTodoTotal=1`
- `messageTotal=1`
- `requesterApproveDeniedStatus=403`
- `approvedTaskStatus=APPROVED`
- `approvedInstanceStatus=APPROVED`
- business record detail readback after approval: `summary.status=APPROVED`
- approval sidebar readback after approval: `visible=true`, `status=APPROVED`, `pendingTaskId` empty
- submit approval action after approval: `enabled=false`
- pending approval todo after approval: `0`
- handled approval todo after approval: `>0`
- `duplicateApproval=true`
- `requesterAndApproverAreSameCurrentLimitation=false`
- `cleanup=[115:DELETE, 116:DELETE, 117:DELETE]`

## Browser Evidence

Browser smoke used the standalone release at `http://127.0.0.1:18131` and Chrome with a normal non-super-admin runtime account.

Temporary browser-evidence data:

- `targetSystemId=107`
- `targetTenantId=112`
- `normalAccount=r3_normal_0627192139849_e297dc`
- `moduleId=48`
- `recordId=34`
- cleanup after screenshots: `107:DELETE`, `108:DELETE`

Browser assertions:

- Runtime list/detail rendered `APPROVED` for the approved record.
- Row/detail submit approval buttons were disabled with reason `记录审批已结束`.
- Pending approval todo total after approval was `0`.
- Handled approval todo total after approval was `1`.
- System messages page rendered the approval message.
- Browser console errors: `0`; failed requests: `0`; HTTP responses >= 400: `0`.

Screenshots:

- `docs/evidence/recovery/screenshots/r3-browser-runtime-list-approved.png`
- `docs/evidence/recovery/screenshots/r3-browser-runtime-detail-approved.png`
- `docs/evidence/recovery/screenshots/r3-browser-system-todos-handled.png`
- `docs/evidence/recovery/screenshots/r3-browser-system-messages-approval.png`

## Covered Flow

The script verifies:

1. Release health is fully `UP`, including Redis.
2. Admin creates a target system.
3. Admin creates and publishes a dynamic module with text, number, auto-number, and attachment fields.
4. Admin creates a custom runtime member role and saves runtime permissions:
   - action permissions include record create/edit/delete/submit approval
   - field permissions are writable
   - data scope is all current-system data
5. Admin publishes a simple approval flow with an explicit canvas.
6. A requester account and an approver account are bound into the target system as two different members with the same custom runtime role.
7. Both accounts switch into the target system, neither has `SYSTEM_SUPER_ADMIN`, and their `systemMemberId` values are different.
8. Runtime schema exposes configured columns and row actions to the normal user.
9. The normal user uploads a real file through `/api/v1/uploads/files`, then saves a draft and reads back the uploaded file ID.
10. The normal user allocates two sequence batches and verifies continuity.
11. The normal user creates a record from the draft, binds the uploaded file to the record, searches it, opens detail, verifies uploaded file metadata, updates it, verifies the attachment survives update, and reads history.
12. The requester submits the record for approval.
13. Submission creates a pending approval task assigned to the approver role member.
14. The requester has no pending approval todo and cannot approve the task; the API returns HTTP `403`.
15. The approver has a pending approval todo and approval message, then approves the task.
16. Approval terminal state is visible from the business record detail and approval sidebar.
17. The submit approval action is disabled after terminal approval.
18. The pending todo is closed and remains visible as a handled todo.
19. Repeating the approve request with the same idempotency key returns `duplicate=true`.
20. Created systems are deleted by default.

## Remaining Gaps

This is API/business/browser evidence for R3, not whole-product acceptance.

- R3 no longer has the requester-vs-approver assignment gap.
- Whole-product acceptance still depends on R2 non-empty tenant-specific business-data redraw, R4 admin breadth, and R5 final user script.
