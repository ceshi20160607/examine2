# Release117 install and supported-upgrade acceptance

- verdict: `PASS`
- target: `CYCLE-RELEASE-HARDENING-117`
- target archive SHA-256: `e2fb087a23ee7479502bbf7ea1f111ded6e830591c2af1ec06ce09dbfdee2f0a`
- runtime: packaged Java 21 JAR, MySQL `8.4.10`, Redis `7.4-alpine`

The packaged target started against an empty schema, applied exactly `112/112`
migrations through `8.91.0`, reached health `UP`, authenticated the bootstrap
root, created a system, restarted, and read the exact durable system back. The
restart remained at `112/112` with zero failed migrations.

The oldest retained accepted executable baseline, Cycle108 at `8.78.0`, then
created durable account/system state through its public API. Cycle117 upgraded
the same database by exactly 12 migrations. The original credential, account
ID, system ID/code/name, all 50 baseline permissions and the exact
`SYSTEM_ADMIN`/`SYSTEM_RUNTIME` shell set were preserved. The new
`work.config.manage` permission was added monotonically for the new Work
configuration feature; no baseline permission was removed.

All exact containers, six dedicated ports, Java processes and the temporary
runtime directory were removed. Machine summary:
`.cursor/session/evidence/release-117/install-upgrade/result-summary.json`.
The original raw run is retained at
`.cursor/session/evidence/release-116/install-upgrade/result.json`.

This acceptance proves clean install and the explicitly supported
Cycle108-to-Cycle117 upgrade path. It does not claim support for an older
binary that is not retained as an immutable accepted package.
