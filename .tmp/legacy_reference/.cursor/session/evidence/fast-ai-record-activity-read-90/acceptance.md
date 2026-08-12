# FAST-AI-RECORD-ACTIVITY-READ-90 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-05T00:48:40+08:00`
- Delivery mode: function-first parallel Core/owner, AI and frontend lanes;
  responsive, accessibility and exhaustive visual adaptation remain deferred.

## Delivered

- Added the exact four-field, policy-gated `RECORD_COMMENT_QUERY`,
  `RECORD_HISTORY_QUERY` and `RECORD_FILE_QUERY` plans. Each plan accepts only
  `{operation,moduleCode,recordId,limit}`, fixes the first page, bounds the
  limit to `1..20`, and never lets model output select actor, tenant, system,
  permission, route, raw-value access or storage details.
- Added narrow Core read ports backed by the existing Collab comment, Module
  history and File reference owners. Every branch resolves the live actor and
  retains the owner module's record visibility, history-read and file-read
  authorization, including non-disclosing tenant isolation.
- Added mutually exclusive `recordComments`, `recordHistory` and `recordFiles`
  Agent result branches plus the exact native record route
  `/systems/{systemId}/workbench?module={moduleCode}&mode=view&record={recordId}`.
  History remains permission-projected; files expose safe metadata only and
  never expose content, download credentials or internal storage identity.
- Added policy-editor coverage and functional frontend cards for the three
  branches. Existing workbench routing is reused; no responsive redesign or
  breakpoint pass was mixed into the functional delivery.
- Added `V8_71_0__ai_record_activity_tool_names.sql`, broadening only the
  existing AI tool-name constraint. No owner table, speculative index,
  confirmation path or mutation endpoint was added.

## Verification

- Affected backend full regression passed Core `70/70`, Module `433/433`,
  Collab `53/53`, File `42/42` and AI `93/93`, totaling `691/691` with no
  failure, error or skip. Agent Web controller contracts passed `3/3`.
- All `14` Maven modules and their tests compiled successfully.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible provider HTTP
  journey passed `1/1` in `197.5s` test time (`3:37` reactor total). It applied
  all `93` migrations and retained the existing cross-module journey.
- The independent MySQL 8.4 schema test passed `1/1`, validated all `93`
  migrations through `V8_71_0`, applied `51` migrations from V8.20 and proved
  the final repeat migration performs zero work.
- Frontend full verification passed `99` files / `478` tests. Typecheck and the
  production build passed over `5,764` transformed modules; the only message
  was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, staged and unstaged `git diff --check`, seven cadence
  cases, the canonical progress gate and both VS4 `43`-endpoint validators
  passed. No package checkpoint was attempted.

## Demonstrated journey

The real journey creates one visible record, one real comment and one real file,
then compares all three Agent results with their native owner pages. It proves
the exact drill route, projected history, safe file metadata and one successful
tool row per operation with the real result count.

The journey separately revokes record visibility, history-read and file-read
access, then queries a foreign-tenant record for every operation. Each denied
call produces no summary-provider call and a zero-result failed tool row. The
ledger contains exactly one `RECORD_NOT_FOUND` failure for each operation,
`PERMISSION_DENIED` for comments and history, and `FILE_FORBIDDEN` for files.
Comment, mention, history, file-object and file-reference row counts and version
sums do not change during the read operations. Durable AI projections contain
none of the operation markers, raw comment body, file name/content, result JSON
or native route.

## Integration corrections

- Real create-history data legitimately uses `recordVersion=0`; the narrow
  history contract now permits nonnegative versions instead of rejecting valid
  owner evidence.
- File-owner `FILE_FORBIDDEN` and `FILE_NOT_FOUND` errors are translated without
  losing their stable codes or HTTP semantics, rather than being collapsed into
  a generic Agent failure.
- The comment adapter remains proxyable for Spring read-only transactions.
- `referencedByMemberId` was removed from the Agent file projection because it
  was outside the frozen safe-metadata whitelist; the native file API remains
  unchanged.

## Completion boundary

This accepts only the record activity read node. It does not accept a project
package checkpoint, responsive/visual hardening, release gates or final user
acceptance. The next function-first owner gap is the platform personal-task
complete/reopen/cancel lifecycle.
