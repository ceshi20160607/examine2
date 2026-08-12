# Cycle118 Platform OpenAPI Acceptance

- Verdict: **PASS (gap outcome only)**
- Accepted at: `2026-08-07T19:27:00+08:00`
- Outcome: `GAP_PLATFORM_APPLICATION_EXTERNAL_API`

## Delivered

- Independent platform application, credential version, policy, nonce and call-log persistence in `V8_92_0__platform_openapi_application.sql`.
- SecretRef-only administration with masked responses, rotation, enable/disable, policy and call-log UI.
- External `GET /openapi/v1/platform/tasks` authenticated by HMAC, bounded timestamp, nonce, IP allow-list and per-minute rate limit. Scope and live authorization both require `platform.task.read`.
- Platform and system OpenAPI principals and URL spaces are deliberately separate.

## Verification

- Backend reactor compile passed.
- `PlatformOpenApiRoutePolicyTest` and `PermissionCatalogTest`: `15/15` passed.
- Real MySQL/Flyway `PlatformOpenApiIntegrationTest`: `1/1` passed, including signed request and isolation checks.
- Frontend platform application tests: `4/4` passed; typecheck passed.

## Boundary

This accepts only the platform application/external-API outcome. It does not accept performance, full release, package recovery or user sign-off.
