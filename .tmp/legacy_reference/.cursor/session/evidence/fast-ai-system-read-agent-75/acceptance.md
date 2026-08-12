# FAST-AI-SYSTEM-READ-AGENT-75 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T11:04:04+08:00`
- Delivery mode: functionality first; confirmation-gated writes, AI_FILL and
  responsive/accessibility/visual hardening remain separate follow-up work.

## Delivered

- Added the isolated `examine-ai` Maven module and ten-table MySQL model for
  tenant-scoped provider authorization, versioned policy publication, sessions,
  messages, turns, tool calls and token/latency usage facts.
- Added an OpenAI-compatible chat-completions provider with configurable base
  URL/model/timeout/enabled state. Credentials are persisted and transported
  only as strict `scheme://reference` SecretRefs; provider absence and failures
  produce stable unavailable/failed outcomes without fake assistant answers.
- Added immutable draft/check/publish policy behavior with optimistic CAS,
  exact replay, one published version, `RECORD_QUERY` as the sole tool, a
  maximum of 50 rows, module/output-field allowlists and strict redaction.
- Added strict structured-plan parsing. Unknown keys, modules, fields and
  operations are rejected before owner execution; the AI module contains no
  record SQL or independent authorization/query engine.
- Added core owner ports and `examine-module` adapters that reuse the existing
  runtime schema/query owner for tenant, member, module, row, field and sensitive
  projection enforcement. Live authorization is rechecked for every tool call.
- Added administration provider/policy/capability APIs and runtime capability,
  session list/detail/create and message-submit APIs with string IDs and bounded
  inputs/projections.
- Added function-first desktop administration and runtime Agent pages, routes,
  navigation, services, types and disabled/unconfigured/failed states. Page
  adaptation and visual hardening remain intentionally deferred.

## Integration corrections

- Added a conditional production UTC `Clock` bean required by the isolated AI
  services while preserving test-provided clocks.
- Removed `final` from the two transactional module owner adapters so Spring can
  create their CGLIB transaction proxies.
- Marked the production model-client constructor explicitly for injection while
  retaining the package-private HTTP-client constructor used by deterministic
  tests.
- Extended new-system permission seeding with `ai.agent.use` and
  `ai.policy.manage`, so systems created after the migration receive the same
  AI capability catalog as upgraded systems.
- Updated the schema integration ledger to 80 migrations, 78 application
  tables, the AI table/permission/SecretRef invariants and the authorization
  epoch increment introduced by the permission migration.

## Verification

- Affected backend regression passed Core `36/36`, Platform `44/44`, Module
  `381/381` and AI `11/11`, totaling `472/472` with no failure, error or skip.
- Web AI controller binding/error contracts passed `3/3`.
- The independent MySQL 8.4 schema upgrade test passed `1/1` in `84.79s` and
  validated all `80` migrations plus the final `78`-table/SecretRef/permission
  shape.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 journey passed `1/1` in
  `156.5s` after applying all `80` migrations. A local real HTTP provider
  fixture published a policy, planned and executed a scoped record query,
  summarized the result, read the conversation, revoked module permission and
  proved immediate `PERMISSION_DENIED` downgrade. Database assertions proved
  singular publication/replay, two audited tool attempts and absence of the
  plaintext secret, session title, prompt, answer and record title from stored
  summaries.
- All `14` Maven child modules test-compiled successfully.
- Full frontend verification passed `92` files / `409` tests, Vue/TypeScript
  typecheck and the production build over `5,750` transformed modules.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake checking, the
  active framework validator, all `7` cadence validation cases and both VS4
  machine-contract validators passed; the compatibility ledger remains at
  `43` endpoints.

## Demonstrated journey

An administrator saves a SecretRef-only local provider, drafts and checks one
strict policy for the published `flow_order` module, and publishes it with exact
replay. An authorized member creates an Agent session and asks a natural-language
question. The provider returns a bounded structured `RECORD_QUERY` plan; the
existing module-runtime owner returns only the configured readable display
field; the provider returns a transient answer and the member reads the stable
conversation projection. After live module-view permission revocation, the next
turn fails audibly and auditably without an answer or hidden-value persistence.

## Deferred

- confirmation-gated record writes and automatic write execution
- AI_FILL materialization, Flow AI nodes and generated report/print definitions
- embeddings/vector search, streaming, voice/image input and arbitrary tools
- multi-provider administration beyond the OpenAI-compatible first adapter
- responsive, accessibility, animation and exhaustive visual-state hardening
