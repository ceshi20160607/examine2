# TASK-QA-012 Role Route Check

## Verdict

PASS for G4 route shell scope.

## Checked Routes

| Route | Owner | Required Context | G4 Status |
|---|---|---|---|
| `#/login` | FE-010 | none | Implemented |
| `#/register-with-system` | FE-010 | none | Implemented |
| `#/forgot-password` | FE-010 | none | Implemented |
| `#/platform` | FE-010 | platform account | Implemented |
| `#/platform/admin` | FE-020 later content, FE-010 entry | platform backend permission | Entry implemented and permission-gated |
| `#/systems/{systemId}/dashboard` | FE-010 | `SystemSwitchContext` | Implemented |
| `#/systems/{systemId}/modules` | FE-010 shell, FE-030 later runtime depth | `SystemSwitchContext` | Shell implemented |
| `#/systems/{systemId}/todos` | FE-030 later depth | `SystemSwitchContext` | Shell entry implemented |
| `#/systems/{systemId}/work` | FE-030 later depth | `SystemSwitchContext` | Shell entry implemented |
| `#/systems/{systemId}/admin` | FE-020 later content, FE-010 entry | system backend permission | Entry implemented and permission-gated |

## Backend Boundary Checks

- `backend-plat-smoke.md` covers login, create system, system switch, tenant switch, role permissions, and permission preview.
- `build-g4` smoke covers module config, messages, notification templates, SSO policy, SecretRef metadata, no-member requests, and ops governance startup routes.

## Non-Bypass Rule

Platform users cannot directly open system business data from platform workbench, platform messages, or platform Agent outputs. They must first establish system member context through system switch. System messages and runtime rows then open business details within that context.

## Residual Risks

- Route checks are static and smoke-level. Full browser E2E remains owned by later QA batches after FE-020/FE-030 and backend runtime slices are complete.
