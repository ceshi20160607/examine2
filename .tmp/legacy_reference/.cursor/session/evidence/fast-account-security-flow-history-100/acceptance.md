# FAST-ACCOUNT-SECURITY-FLOW-HISTORY-100 Acceptance

- Verdict: `PASS`
- AcceptedAt: `2026-08-05T11:30:15+08:00`
- PackageCheckpointAttempted: `false`
- SchemaMigrationAdded: `true` (`V8_77_0`)

## Delivered capability

- Added authenticated `POST /api/v1/auth/password:change` through the existing
  Plat credential owner. The owner verifies and rehashes with Argon2id, performs
  an optimistic versioned credential update, explicitly clears login lock state
  and revokes every active account context, refresh token and Redis access-token
  cache entry in the same business transaction.
- Successful password change clears access, refresh and CSRF cookies and sends
  both Platform and System shells back to login through one shared account-
  security dialog. Wrong-current attempts remain zero-write and emit bounded
  `PASSWORD_CHANGE/DENIED` audit evidence without credential material.
- Added strict System Agent operation `FLOW_INSTANCE_HISTORY_QUERY`. AI accepts
  only `{operation,instanceId,limit}`, while Flow resolves system/tenant/member,
  live `flow.instance.read`, bound-record visibility, safe ordered history and
  the native Flow route. The AI repository stores hashes/counts only.
- Repaired administration-root candidates for platform AI policy, native
  Configuration Studio, message templates, System Agent policy and OpenAPI.
  Shell projection now exposes Platform/System administration whenever an
  independent administration-page owner permission is present; an incomplete
  configuration permission alone still grants no admin shell or route.
- The only database change expands the existing AI tool-name check constraint.
  Real MySQL applied all `99` migrations.

## Verification

- Focused password/session/shell owner regression passed, including `9/9` in
  the final changed-service set. Full Plat regression passed `84/84`; Core
  passed `81/81`.
- Full affected backend regression passed: Core `81/81`, Plat `84/84`, Flow
  `395/395`, AI `106/106`, total `666/666` with zero failure/error/skip.
- Real MySQL/Redis web integration passed `2/2` in `170.8s`: authentication and
  CSRF boundaries, wrong-current exact failure, versioned lock reset, three
  cleared cookies, all-session/all-refresh revocation, old-token and old-
  password rejection, new-password login and safe SUCCESS/DENIED audits.
- Focused frontend regression passed `6` files and `75/75`. Full frontend
  regression passed `108` files and `552/552`; TypeScript typecheck passed.
  Production build passed with `5,769` modules transformed; only the existing
  chunk-size notice remained.
- Real Microsoft Edge desktop journeys passed `3/3` together in `37.8s`:
  password change with a separately logged-in session, native bound Flow
  history through a local OpenAI-compatible provider with unchanged history,
  and a permission-limited administrator root redirect. The last journey proves
  `module.config.manage` without `system.admin.access` cannot select
  Configuration Studio and falls through to the authorized message-template
  owner.
- Database evidence recorded `FLOW_INSTANCE_HISTORY_QUERY/SUCCEEDED`, result
  count `2`, and 64-character request/response hashes. Strict UTF-8, instance
  framework, project progress, all seven cadence cases, VS4 contracts and
  targeted diff checks passed.
- No package checkpoint was attempted; the project-wide attempt count remains
  `0/3`.

## Defects caught during real integration

- MyBatis' default null-update strategy left `locked_until` populated after a
  successful password replacement. A dedicated optimistic SQL owner now clears
  it explicitly instead of relying on generic entity update behavior.
- The initial Flow fixture supplied both legacy and sequential approver fields;
  it now follows the native exclusive `approverIds` contract.
- Real Edge showed that the administration candidates were unreachable for
  permission-limited owners because shell derivation only recognized the broad
  `*.admin.access` permissions. Shells are now projected from the exact set of
  independent admin-page owner permissions, while incomplete combinations stay
  closed.

## Evidence and cleanup

- `password-change-relogin.png`
  (`sha256:a9888ce9ffb3d975746c298087f1294d8e5d2005077420a1a5263452786dae35`)
- `flow-instance-history.png`
  (`sha256:abeab41f91f921d519befd27b884ab5d9d211bfed7bb84bb005a6690aadfbae6`)
- `limited-admin-root.png`
  (`sha256:084f759de3dfa752e8eae65be6516e6e73747bfa2faf900d66a3f5bd04345b42`)
- Dedicated backend/Vite processes were stopped. Disposable Batch100 MySQL and
  Redis containers and the temporary key ring/start script were removed; no
  Batch100 listener or container remains.
- Anonymous recovery, delivery channels, external data-source/file transports,
  responsive/visual hardening, release gates and package checkpoints remain
  outside this node.
