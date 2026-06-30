# R16 Register First-Use Browser Closure Evidence

Status: PASS

- Base URL: http://127.0.0.1:18131
- Task: REC-P0-019
- Browser route: /register-with-system
- Disposable account: r16_register_0629230413
- Disposable system id: 617
- The deployed browser submitted the real register form.
- Registration landed on /systems/617/dashboard.
- The dashboard showed the first-use initialization prompt and initialization action.
- The browser clicked the initialization action and opened the system backend.
- Registered account switch context includes SYSTEM_SUPER_ADMIN.
- The fresh system has default_department, and the owner member is bound to it.
- Cleanup result: DELETE

Machine-readable result: docs/evidence/recovery/r16-register-first-use-result.json
Browser measurement: docs/evidence/recovery/screenshots/r16-register-first-use/register-first-use-browser.json
Screenshots:
- docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-dashboard.png
- docs/evidence/recovery/screenshots/r16-register-first-use/desktop-register-admin.png
- docs/evidence/recovery/screenshots/r16-register-first-use/mobile-register-dashboard.png
