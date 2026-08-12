# FAST-AI-CONFIG-SUGGESTIONS-99 Acceptance

- Verdict: `PASS`
- AcceptedAt: `2026-08-05T10:19:06+08:00`
- PackageCheckpointAttempted: `false`
- SchemaMigrationAdded: `true` (`V8_76_0`)

## Delivered capability

- Extended the existing confirmation-gated AI configuration-artifact lifecycle
  with `CONFIG_FILTER_SCENARIO_DRAFT` and
  `CONFIG_FIELD_PERMISSION_STAGE_DRAFT`; no parallel endpoint or second
  confirmation framework was introduced.
- Filter-scenario suggestions use the native LIST-page layout owner, preserve
  unrelated layout and scenarios, canonicalize filter/sort input and produce
  exactly one configuration draft revision only after confirmation.
- Generic field-permission suggestions derive exact `.read`/`.write` codes from
  the owner and only permit `INHERIT -> STAGED`; they cannot grant a role,
  enforce access, check or publish configuration.
- Both operations bind proposal, policy/provider, authorization epoch, tenant,
  module, target version and draft revision, while reusing expiry, rejection,
  attempt/event and idempotent replay semantics.
- Persisted proposal projections redact free-text scenario values; sealed
  commands stay opaque and the AI module performs no Module-table SQL.
- System Agent renders typed preview/result cards and independent policy
  switches. Successful cards link to Configuration Studio with stable
  `moduleCode/resourceKind/resourceCode`; the Studio resolves fresh owner state
  before focusing the target.
- The only schema change expands the existing artifact-kind check constraint to
  the two accepted kinds. Real MySQL applied all `98` migrations.

## Verification

- Focused owner regression: Core `4/4` plus Module `18/18`, total `22/22`.
- Focused AI regression: `19/19`.
- Full affected JDK 21 backend regression: Core `79/79`, Module `472/472`, AI
  `105/105`, total `656/656`; all four Maven reactor projects succeeded.
- Real MySQL/Redis/OpenAI-compatible HTTP journey: `1/1` passed in `212.0s`;
  it proved zero writes before confirmation, exactly one revision after each
  confirmation, idempotent replay, exact owner readback, unchanged active
  configuration/role grants/runtime and redacted persistence.
- Focused frontend regression: `4` files, `64/64`; TypeScript typecheck passed.
- Full frontend regression: `105` files, `526/526`. Production build passed
  with `5,767` modules transformed; only the existing chunk-size notice
  remained.
- Real Microsoft Edge desktop journey: `1/1` passed (`11.5s` test, `18.0s`
  total). It covered both suggestions, pre-confirmation zero-write checks,
  canonical result state, unchanged active version and fresh Configuration
  Studio field focus. Screenshot:
  `config-studio-field-focus.png`.
- No package checkpoint was attempted; the project-wide attempt count remains
  `0/3`.

## Defects caught during real integration

- Real Spring startup exposed ambiguous constructor selection in
  `AiConfigurationArtifactDraftWriter`; the production constructor is now
  explicitly autowired and the complete real journey passes.
- The combined redaction assertion initially treated a legitimate new sort
  field as legacy proposal leakage. Assertions now isolate old artifact kinds
  while retaining strict redaction checks for the new proposal payloads.
- The standalone Edge fixture now refreshes its authorization snapshot after
  configuration publication and uses the accepted `env://` SecretRef format.
  These were harness-contract corrections, not relaxed production checks.

## Cleanup and deferred boundary

- The dedicated backend and Vite processes were stopped. Disposable MySQL and
  Redis containers were stopped and removed; no Batch 99 listener or container
  remains.
- Automatic check/publish, role grant/publication, ENFORCED transition,
  responsive/visual hardening, release gates, package checkpoints and final
  acceptance remain outside this node.
- The next node is selected from the parallel evidence-ranked functional-gap
  audit and continues to use native owners before any broad UI adaptation.
