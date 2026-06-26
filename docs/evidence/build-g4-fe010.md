# TASK-FE-010 Evidence

## Verdict

PASS. The frontend now has route-level shells for authentication, platform workbench, platform admin entry, system runtime shell, system switch, and permission-aware system admin entry.

## Files

- `frontend/src/features/auth/authPages.ts`
- `frontend/src/features/platform/platformShell.ts`
- `frontend/src/features/system-shell/systemShell.ts`
- `frontend/src/app/app.ts`
- `frontend/src/app/routes.ts`
- `frontend/src/app/state.ts`
- `frontend/src/mocks/g0.ts`
- `frontend/src/shared/table.ts`
- `frontend/src/shared/filters.ts`
- `frontend/src/shared/drawer.ts`
- `frontend/src/styles.css`

## Coverage

- Login, register-with-system, and password reset routes exist at `#/login`, `#/register-with-system`, and `#/forgot-password`.
- The login route uses a centered single-card layout with `min-height: 100vh`; the login panel content is compact and is not expected to create a desktop baseline scrollbar.
- The register flow explains that the creator becomes system super admin and shows initialization next steps without exposing technical IDs as primary copy.
- The password reset flow has request and confirm panels with field-level placement for validation feedback.
- Platform workbench includes dashboard navigation, Flow, apps, todo, messages, profile, create system, system switch, and platform admin entry gated by platform role.
- System shell includes dashboard, configured module group/module navigation, todo, messages, system switch, profile, and system admin entry gated by system role.
- Runtime list rows use whole-row detail targets; row actions keep only different operations such as edit/delete, with disabled reasons.

## Command

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

Result: PASS.

```text
tsc --noEmit && vite build
dist/index.html
dist/assets/index-*.css
dist/assets/index-*.js
```

## Residual Risks

- FE-010 is a shell implementation over mock state. Admin configuration views and runtime business detail implementation remain owned by FE-020 and FE-030.
- Browser visual QA was not run in this evidence file; current validation is TypeScript and production build.
