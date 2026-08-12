# P2 identity and runtime shell phase gap report

- outcomeId: `P2_PHASE_ACCEPTANCE`
- proposed verdict: `NOT_PROVEN`
- promotion safety: `DO_NOT_PROMOTE`

## Frozen requirements

P2 owns authenticated entry and the runtime shell. The authoritative requirement ledger includes password/session behavior (`REQ-AUTH-001/002`), enterprise identity (`REQ-SSO-001`), system/tenant context (`REQ-CTX-001/002`) and four role shells (`REQ-SHELL-001`). The roadmap demo requires login, context switch, logout and restart readback.

## Evidence audit

| requirement | evidence | assessment |
|---|---|---|
| Registration, password login, refresh/logout, atomic first system and audit | `.cursor/session/evidence/vs1/acceptance.md`; `.cursor/session/evidence/vs1/backend-tests.md` | `PROVEN` |
| System context switch, permission snapshot, unauthorized switch rejection and restart readback | `.cursor/session/evidence/vs1/journey-e2e.md`; `.cursor/session/evidence/p2-p3/acceptance.md` | `PROVEN` |
| Account recovery/reset and session invalidation | `.cursor/session/evidence/cycle-account-recovery-109/acceptance.md` | `PROVEN` for email-token recovery only |
| Platform/system/admin/auth shells and breakpoint/accessibility baseline | `.cursor/session/evidence/cycle-ui-hardening-112/acceptance.md` | `PROVEN` |
| Enterprise identity provider lifecycle, OIDC/SAML/OAuth2/LDAP/WeCom/DingTalk, JIT mapping, MFA and provider preflight | `REQ-SSO-001`; `docs/user_requirement.md`; repository/evidence search | `MISSING`: no controller/service/UI/migration acceptance evidence exists. |
| MFA/enterprise-SSO recovery | `.cursor/session/evidence/cycle-account-recovery-109/acceptance.md` Deferred section | `CONTRADICTED`: the accepted recovery cycle explicitly defers MFA and enterprise SSO recovery. |

## Blocking gap

`REQ-SSO-001` is an IN-scope, CLEAR requirement rather than a future option. No later formal phase outcome owns it, and the current evidence explicitly defers its recovery path. The existing password flow is healthy but cannot stand in for enterprise identity and MFA.

## Required closure evidence

Implement and independently verify at least one coherent provider lifecycle covering configuration/SecretRef, preflight, publish/disable, login/JIT mapping, MFA policy, failure fallback, audit/trace and cross-system isolation; then cover the frozen provider matrix or formally narrow it through an authorized requirement change.
