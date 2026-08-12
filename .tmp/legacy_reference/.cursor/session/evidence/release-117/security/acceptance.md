# Release117 dependency and production-security acceptance

- verdict: `PASS`
- evaluatedAt: `2026-08-07T18:14:00+08:00`
- packaged JAR SHA-256: `a2b3801c61cc5f5021db80eb63f2a22097a4f5a85c347b75dbae520e1a3887f0`

## Dependency result

The first current-artifact OSV scan found the newly disclosed
`GHSA-pmhh-3w7g-xqp8` in jsoup `1.22.2`, with `1.23.1` identified as the fixed
version. The parent dependency pin was upgraded to `1.23.1`; the affected
canonical rich-text cleaning contract passed `4/4`, the complete Java reactor
passed, and the final fat JAR contains `BOOT-INF/lib/jsoup-1.23.1.jar`.

The final online OSV scan at `2026-08-07T10:10:29.4212987Z` covered 163 nested
JARs, found Maven metadata for 123 components and queried 111 external Maven
components. There are zero fixable findings and zero HIGH/CRITICAL findings.
The only match is the existing MODERATE Undertow multipart GET DoS
`GHSA-3x3v-w654-m28m`, for which OSV reports no fixed version. The application
mitigation rejects multipart `GET` and `HEAD` before request parsing;
`SecurityBoundaryFilterTest` passed `3/3`.

Forty nested JARs do not expose discoverable Maven `pom.properties`, so this
OSV inventory is not represented as a complete SBOM/SCA attestation. Frontend
`npm audit --json` and production audit report zero vulnerabilities across 360
resolved dependencies.

## Production fail-closed result

`ProductionSecurityGateTest` passed `6/6`, covering production recognition and
fail-closed database TLS, Redis TLS/authentication, HTTPS S3, SMTP STARTTLS,
secure cookie and sensitive-key-ring prerequisites. The complete backend run
also covers security headers, secret-reference boundaries, SSRF controls,
authorization, tenant isolation and idempotent mutation paths.

The remaining Undertow item is accepted only with the named boundary filter
and test. A future fixed Undertow/Spring Boot line must replace this temporary
mitigation when available.
