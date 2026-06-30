# R15 Password Reset Account Closure Evidence

Status: PASS

- Base URL: http://127.0.0.1:18131
- Task: REC-P0-018
- Browser route: /forgot-password
- Disposable account: r15_reset_0629230402
- Disposable system id: 616
- Browser request produced reset ticket and a 6-digit verification code.
- Browser confirm completed from the deployed password reset page.
- Old password login was rejected after reset.
- New password login returned account id 258.
- Reset ticket reuse was rejected.
- Cleanup result: DELETE

Machine-readable result: docs/evidence/recovery/r15-password-reset-result.json
Screenshot: docs/evidence/recovery/screenshots/r15-password-reset/desktop-password-reset.png
