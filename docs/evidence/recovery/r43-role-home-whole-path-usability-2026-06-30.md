# R43 Role Home And Whole-Path Usability Smoke

Status: FAIL

Base URL: http://127.0.0.1:18132

System ID: 1175

Module ID: 475

Result JSON: docs/evidence/recovery/r43-role-home-whole-path-usability-result.json

Browser audit JSON: docs/evidence/recovery/screenshots/r43-role-home-whole-path-usability/role-home-whole-path-usability-audit.json

Screenshots: docs/evidence/recovery/screenshots/r43-role-home-whole-path-usability/

Failure count: 16

Warning count: 0

Normal forbidden create: HTTP 403

Normal forbidden admin action read: HTTP 403

## Failure Routes

- desktop / admin / platform-workbench: required role-path text missing
- desktop / admin / system-dashboard: expected role surface marker missing; required role-path text missing
- desktop / admin / system-work: expected role surface marker missing
- desktop / normal / normal-platform-workbench: required role-path text missing
- desktop / normal / normal-platform-admin-denied: required role-path text missing
- desktop / normal / normal-system-dashboard: expected role surface marker missing; required role-path text missing
- desktop / normal / normal-system-work: expected role surface marker missing
- desktop / normal / normal-system-admin-denied: expected role surface marker missing
- mobile / admin / platform-workbench: required role-path text missing
- mobile / admin / system-dashboard: expected role surface marker missing; required role-path text missing
- mobile / admin / system-work: expected role surface marker missing
- mobile / normal / normal-platform-workbench: required role-path text missing
- mobile / normal / normal-platform-admin-denied: required role-path text missing
- mobile / normal / normal-system-dashboard: expected role surface marker missing; required role-path text missing
- mobile / normal / normal-system-work: expected role surface marker missing
- mobile / normal / normal-system-admin-denied: expected role surface marker missing

## Cleanup

- 1175:DELETE, 1176:DELETE
