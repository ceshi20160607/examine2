# FAST-CONFIG-FIELD-ACCESS-98 Acceptance

- Verdict: `PASS`
- AcceptedAt: `2026-08-05T06:10:48+08:00`
- PackageCheckpointAttempted: `false`
- SchemaMigrationAdded: `false`

## Delivered owner capability

- Added typed `INHERIT`, `STAGED` and `ENFORCED` read/write modes to the
  existing field draft DTO and existing `un_module_permission` owner. Create
  defaults omitted modes to `INHERIT`; update preserves omitted modes.
- Reused archive/revive semantics for generic and sensitive field permission
  rows, including immutable field-code lifecycle. No table, endpoint or
  `property_json` security flag was added.
- Enforced the safe publication sequence: a generic permission must first be
  published as `STAGED`, granted and published through the existing role
  owner, and only then published as `ENFORCED`. A direct first publication as
  enforced is blocked by `FIELD_PERMISSION_NOT_STAGED`.
- Published staged generic field permissions into the dynamic ACTIVE catalog
  without enforcing them at runtime. Role projections hide inactive historical
  permissions while retained links support rollback and same-code revival.
- Fixed runtime definition and configuration preview projection to collect all
  permission rows per field and evaluate the exact canonical `.read` code.
  Record schema continues to use exact `.read` and `.write` independently.
- Module copy demotes every non-inherited generic mode to `STAGED`; shared
  filter-scenario references block field rename/delete; readonly, reference,
  derived, system-computed and AI_FILL fields cannot declare generic write
  enforcement.
- Configuration Studio now authors both modes, previews exact permission codes,
  explains the safe sequence and links staged publications to a prefix-focused
  role editor. The role page requests ACTIVE permissions only, preserves other
  active grants, keeps allow/deny disjoint and refreshes the session after role
  publication.

## Verification

- Backend focused owner regression: examine-module `24/24` and examine-plat
  `1/1`, total `25/25`, zero failures/errors/skips.
- Backend affected full regression on JDK 21: examine-core `78/78`,
  examine-plat `72/72`, examine-module `467/467`, total `617/617`; all four
  Maven reactor modules succeeded.
- Frontend focused regression: `4` files, `23/23` tests; Vue/TypeScript
  typecheck passed.
- Frontend full regression: `105` files, `521/521` tests. Production
  typecheck/build passed with `5,767` modules transformed; only the pre-existing
  chunk-size notice remained.
- Playwright collection discovered all `12` VS3 project tests.
- Real Microsoft Edge desktop journey: `1/1` passed (`19.0s` test, `23.5s`
  total) against isolated MySQL 8.4 and Redis with all `97` Flyway migrations
  validated and applied.
- The real journey proved staged read/write catalog publication, read-only role
  publication, later configuration enforcement, ordinary-member schema
  `writable:false`, absence of the field in the create drawer, role republish
  with exact write permission, fresh login, schema `writable:true` and the real
  field control appearing in the create drawer.
- No package checkpoint was attempted; the project-wide limit remains unused.

## Harness notes and cleanup

- The first executable-jar overlay copied current mapper resources as well as
  classes, so MyBatis correctly rejected duplicate result-map ids after all 97
  migrations completed. The harness was corrected to a class-only overlay of
  `2,873` current reactor classes; no production source, migration or package
  checkpoint changed because of that harness-only retry.
- The first browser pass reached the correct read-only schema but an assertion
  expected a field header in the empty-record state. The functional page
  contract intentionally renders `1 个可见字段` there; the assertion was aligned
  while retaining stronger create-drawer control checks.
- Backend, Vite, MySQL and Redis processes/containers were stopped and all four
  temporary ports were verified closed. The local command policy rejected
  recursive deletion, so the two inert extracted overlays were moved outside
  the engineering state tree to `.tmp-browser/batch98-overlays`; they contain no
  running process or listener and no longer enter framework test fixtures.

## Deferred boundary

- AI suggestions for filter scenarios and generic field permissions may now
  call this accepted native owner; they remain a separate confirmation-gated
  node and receive no direct configuration-table access.
- Other evidence-ranked owner gaps, formal P1-P9 phase acceptance, responsive
  and accessibility hardening, release gates, package checkpoints and final
  user acceptance remain open.
