# P1 design and engineering foundation acceptance draft

- outcomeId: `P1_PHASE_ACCEPTANCE`
- proposed verdict: `PASS`
- promotion safety: `SAFE_TO_PROMOTE_DRAFT`
- formal-state impact: none in this file

## Frozen requirement

The P1 boundary in `.cursor/session/delivery-roadmap.md` requires an executable project skeleton, module/POM structure, database and API baseline, repeatable generator and first generated base. Its acceptance demo is an empty-schema build/start, health response, generated output and discoverable engineering entry points. It does not claim complete business behavior.

## Requirement/evidence audit

| requirement | authoritative evidence | assessment |
|---|---|---|
| Detailed product/UI/architecture/data/API/test/release design is accepted | `.cursor/session/rebuild/design-package.md` §§1-19; `.cursor/session/meetings/S2-design-review.md` | `PROVEN`: 57 REQ, 52 JRN and 8 NFR are mapped; multi-role design and Leader gates passed. |
| Executable multi-module skeleton and engineering entry points exist | `backend/pom.xml`, `frontend/package.json`, `sql/migration`, `.cursor/scripts`; `.cursor/session/evidence/vs1/acceptance.md` | `PROVEN`: the VS1 acceptance records the Maven/Vue/Flyway/Redis skeleton and framework validators. |
| Empty database migrates and application becomes healthy | `.cursor/session/evidence/vs1/schema.md` | `PROVEN`: empty MySQL 8.0/8.4 migration, 16 initial tables, packaged jar startup, restart and `UP` health were read back. |
| Generator is repeatable and generated base is separated from handwritten behavior | `.cursor/session/evidence/vs1/generator.md` | `PROVEN`: declared base directories were deleted/regenerated three times, 80 generated files were reported and the reactor stayed green. |
| Initial API/security baseline runs on real dependencies | `.cursor/session/evidence/vs1/backend-tests.md` | `PROVEN`: Undertow + MySQL + Redis integration covers registration, session/context, CSRF, authorization negatives and persistence. |
| Initial browser/runtime entry is real, not a static prototype | `.cursor/session/evidence/vs1/journey-e2e.md` | `PROVEN`: desktop/mobile registration, system shell, platform return, context switch and logout passed against the final jar and empty database. |
| Foundation still composes in the current tree | `.cursor/session/evidence/cycle-ui-hardening-112/acceptance.md` | `PROVEN`: current Java 21 production package, frontend build, clean 104-migration cold start and health/frontend HTTP checks passed. |

## Contradictions and missing evidence

None for the P1 boundary. Current product-level gaps do not invalidate the engineering foundation and are not claimed here.

## Draft acceptance conclusion

The executable foundation remains valid in the current project and has both original empty-environment evidence and a current cumulative cold start. `P1_PHASE_ACCEPTANCE` may be promoted to `completed` with this draft as its evidence path. This draft does not accept any functional phase or release gate.
