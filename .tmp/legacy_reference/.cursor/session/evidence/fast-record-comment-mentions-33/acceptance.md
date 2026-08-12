# FAST-RECORD-COMMENT-MENTIONS-33 Acceptance

## Verdict

PASS at 2026-07-29T20:09:43+08:00.

Batch 33 closes the first structured record-comment @mention slice. Comment
creation now owns immutable recipient associations and delivers one durable,
actionable inbox message to each eligible record-team member after commit.

## Delivered behavior

- Comment creation accepts up to 20 explicit positive member ids. Recipients are
  normalized deterministically and included in the idempotency fingerprint.
- Every recipient must be an active member in the same system and tenant,
  already belong to the record team and differ from the commenter. Any invalid
  recipient rejects the whole write.
- Collab persists comment and mention associations atomically. Exact replay
  returns the original comment without duplicate rows or deliveries; changing
  the mention set under the same idempotency key conflicts.
- Event provisions and publishes the stable
  `RECORD_COMMENT_MENTIONED` template with bounded `moduleCode`, `recordId` and
  `commentExcerpt` variables.
- Notification command construction and delivery run after commit. A failure
  for one recipient cannot roll back the comment or suppress another
  recipient's delivery.
- Inbox targets open the owning record detail with
  `panel=comments&comment={commentId}`, preserving direct comment context.
- The comment composer loads existing record-team data, excludes the current
  member, submits explicit recipient ids, clears only after success, renders
  mention tags and highlights a route-targeted comment.

## Verification

- `RecordCommentServiceTest`: 9/9 passed, covering normalization, active/team
  validation, self rejection, atomic invalid-write behavior, exact replay,
  changed-request conflict and per-recipient failure isolation.
- `JdbcRecordCommentContractTest`: 7/7 passed, covering atomic associations,
  immutable replay behavior, request hash conflict and migration contract.
- `MessageTemplateServiceTest`: 2/2 passed, covering the seventh system
  template and mention rendering/delivery.
- `RecordCommentJourneyIntegrationTest`: 1/1 passed in 78.43 s against real
  MySQL and Redis. It proves team membership, association persistence, durable
  delivery linkage, actionable routing, replay dedupe and recipient isolation.
- `P4A1SchemaIntegrationTest`: 1/1 passed in 43.75 s against real MySQL 8.4.
  All 49 migrations validated and staged upgrade reached 8.27.0.
- Affected backend regression: Core 20, Collab 50 and Event 25 tests passed.
- `npm.cmd test`: 51 files and 194 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.

## Demo path

Open a record detail -> Comments -> choose record-team members in the mention
picker -> submit -> sign in as a recipient -> open Inbox -> click the message
row -> observe the exact record and highlighted comment.

## Deferred

- rich-text parsing, inferred identities and multi-level threads
- comment attachments, reactions and subscriptions
- email, SMS, webhook and push delivery
- responsive, accessibility and exhaustive visual hardening
