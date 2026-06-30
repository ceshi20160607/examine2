# R19 Admin Aggregated Pagination Smoke

Status: PASS

Base URL: http://127.0.0.1:18131

Target system: 469

Evidence:

- Platform systems page 1 -> page 2 was clicked in the deployed frontend, and the first visible row changed.
- System roles page 1 -> page 2 was clicked in the deployed frontend, and the first visible row changed.
- No verified admin pagination control reported the unsupported-pagination disabled reason.
- Created systems: 21
- Cleanup: 469:DELETE; 470:DELETE; 471:DELETE; 472:DELETE; 473:DELETE; 474:DELETE; 475:DELETE; 476:DELETE; 477:DELETE; 478:DELETE; 479:DELETE; 480:DELETE; 481:DELETE; 482:DELETE; 483:DELETE; 484:DELETE; 485:DELETE; 486:DELETE; 487:DELETE; 488:DELETE; 489:DELETE

Screenshots:

- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/platform-systems-page1.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/platform-systems-page2.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/system-roles-page1.png
- docs/evidence/recovery/screenshots/r19-admin-aggregated-pagination/system-roles-page2.png

Machine-readable result: docs/evidence/recovery/r19-admin-aggregated-pagination-result.json
