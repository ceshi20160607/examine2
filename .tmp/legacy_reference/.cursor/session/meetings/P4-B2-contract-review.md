# P4-B2 Contract Review

## Meeting

- meeting_id: `P4-B2-CONTRACT-REVIEW`
- status: `completed`
- owner: `pm`
- verifier: `leader`
- reviewed_at: `2026-07-17T21:02:00+08:00`
- requirements: `REQ-RUNTIME-002`, `REQ-RUNTIME-003`, `REQ-DATA-002`, `REQ-RBAC-001`, `REQ-AUDIT-001`, `REQ-MOBILE-001`
- journeys: `JRN-B4`, `JRN-B5`
- contract: `.cursor/session/current-node.md`

## Decision

P4-B2 is a single-record lifecycle and recovery slice. It delivers archive/unarchive, trash/restore, draft discard, due-draft expiry and recovery as real persisted commands with independent permissions, CAS, idempotency, audit/outbox, typed-index state and restart readback. It does not absorb P4-B3 query/view work, P4-D3 batch actions or P4-D4 my-drafts discovery.

## Role Review

| role | verdict | decision |
|---|---|---|
| product | PASS | The user-visible outcomes and confirmation/recovery paths are complete for one record; physical purge and discovery remain explicitly deferred. |
| architect | PASS | State transitions, prior-state restoration, forward migration, configured CUSTOM permissions and MySQL-driven expiry are bounded and transactionally coherent. |
| dba | PASS | Existing schema already models all statuses; one forward constraint/index migration is sufficient, and typed rows remain recoverable. |
| uiux | PASS | Server-filtered actions, destructive confirmation, read-only expired state and state-directed routing define desktop/mobile behavior without placeholder controls. |
| test | PASS | Positive transitions, each permission negative, illegal states, CAS/idempotency, worker rerun, audit/outbox, restart and responsive browser paths are reproducible. |
| pm | PASS | Three serial tasks each have one outcome and a 240-minute ceiling; B3/D3/D4 work is not pulled into this node. |

## Boundary Resolution

P4-B1's deferral text grouped `my drafts` with lifecycle follow-ups, while the accepted delivery roadmap and VS4 capability split assign discovery to P4-D4. Leader resolves the ambiguity in favor of the more specific roadmap: P4-B2 supports direct server-URL DRAFT/EXPIRED recovery and explicit discard, but no draft inbox.

## Leader Gate

Verdict: **PASS**. The requirements, state machine, permissions, expiry policy, migration, UI outcomes, negative cases and completion boundary are explicit. P4-B2-01 may start; any saved-view, complex query, batch or my-drafts request returns to PM scheduling.
