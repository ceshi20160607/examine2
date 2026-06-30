# R17 Normal Member Real-Login Runtime Usability Closure

Status: PASS

Base URL: http://127.0.0.1:18131

Task: REC-P0-020 Normal Member Real-Login Runtime Usability Closure

Evidence:

- Browser result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r17-normal-member-real-login-runtime\normal-member-real-login-runtime-browser.json
- Machine result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r17-normal-member-real-login-runtime-result.json
- Screenshots: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r17-normal-member-real-login-runtime

Assertions:

- Normal member logged in through the deployed /login form.
- Browser opened the target system runtime module page from the same session.
- Frontend create form saved record value R17 Browser Ticket 0629230439120_6ac516.
- Runtime list/detail showed the saved value on desktop and mobile.
- Header did not expose a system backend entry for the normal member.
- Direct system-admin URL rendered access denied instead of admin content.
- Backend admin member-list API rejected the normal member with HTTP 403.
- Runtime API readback found 1 matching record(s).
- Cleanup: 618:DELETE, 619:DELETE
