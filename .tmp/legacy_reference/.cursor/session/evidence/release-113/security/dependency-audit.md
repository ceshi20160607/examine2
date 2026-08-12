# Dependency and build-tool audit

## Backend toolchain

- Project policy: Java `[21,22)`, Maven `[3.8.5,)` through Maven Enforcer.
- Verified toolchain: `JAVA_HOME=D:\dev\jdk21`, Java 21.0.10, Maven 3.8.5.
- `mvn -f backend/pom.xml -DskipTests validate`: PASS for all 15 reactor projects and Enforcer rules.
- The default shell resolves Java 8. That path is rejected by project policy; automation should set the Java 21 toolchain explicitly.
- A standalone `mvn dependency:tree` did not finish the final web module because internal reactor SNAPSHOT artifacts had not been installed locally. This is an inventory-tool limitation, not a compile/build failure. The fat-JAR inventory below was therefore used.

## Backend OSV scan of available artifact

- Command: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .cursor/session/evidence/release-113/security/scan-backend-osv.ps1 -Artifact <backend-fat-jar-path>`
- Scan time: 2026-08-06T13:44:05Z.
- Source: OSV querybatch plus individual OSV vulnerability records.
- Artifact: current backend executable fat JAR (path intentionally omitted from credential-safe evidence).
- Artifact SHA-256: `8ba532aded499283eb417962d972f97e6ba5b0fb8a6a6970ce4d211205749ccc`.
- Nested libraries: 162; Maven metadata found for 122; external Maven components queried: 110.
- Coverage gap: 40 nested JARs had no discoverable `pom.properties` and were not queried. They include BouncyCastle, Micrometer, MyBatis Plus, MySQL Connector/J, POI, Reactor, Spring Framework/Boot, Tomcat EL and XMLBeans libraries. This scan is not a complete SBOM or complete SCA attestation.
- Matches: 15 across 6 components: 5 HIGH and 10 MODERATE.

| Component | Severity/count | OSV/GHSA | OSV fixed version |
|---|---:|---|---|
| `io.netty:netty-codec:4.1.135.Final` | HIGH x1 | `GHSA-558v-64gr-wgg4` | `4.1.136.Final` |
| `io.netty:netty-codec-http:4.1.135.Final` | HIGH x3, MODERATE x4 | `GHSA-6jqx-86gh-f27w`, `GHSA-jppx-w49h-x2qq`, `GHSA-mvh2-crg5-v77c`, `GHSA-4mp9-239f-g9hg`, `GHSA-6cqp-g7gg-8hr5`, `GHSA-gcjf-9mgh-3p7g`, `GHSA-q4f6-jm68-57ww` | `4.1.136.Final` |
| `io.netty:netty-codec-http2:4.1.135.Final` | HIGH x1, MODERATE x1 | `GHSA-93wv-jw9v-4972`, `GHSA-c69g-56f8-xwqj` | `4.1.136.Final` |
| `com.fasterxml.jackson.core:jackson-databind:2.21.4` | MODERATE x3 | `GHSA-5gvw-p9qm-jgwh`, `GHSA-5jmj-h7xm-6q6v`, `GHSA-mhm7-754m-9p8w` | `2.21.5` on the current line |
| `io.undertow:undertow-core:2.3.24.Final` | MODERATE x1 | `GHSA-3x3v-w654-m28m` | none reported by OSV |
| `org.apache.commons:commons-lang3:3.17.0` | MODERATE x1 | `GHSA-j288-q9x7-2f5v` | `3.18.0` |

Source search found no project use of the Jackson annotations/features named by the three advisories or the Commons Lang methods named by its advisory; this lowers apparent reachability but does not remove the dependency finding. Network-stack HIGH findings remain release blockers until the exact rebuilt artifact is clean or a defensible reachability analysis is approved.

## Frontend npm audit after overrides

- `npm ci --ignore-scripts --dry-run`: PASS.
- `npm audit --omit=dev --json`: exit 0; critical/high/moderate/low/total = `0/0/0/0/0`; 186 production, 137 development and 40 optional dependencies reported.
- `npm audit --json`: exit 0; critical/high/moderate/low/total = `0/0/0/0/0`.
- Installed resolution: `brace-expansion@2.1.4`, `fast-uri@3.1.5`, `postcss@8.5.26`, `undici@7.29.0`; `npm ls ... --all` reports the intended overrides/deduplication.
- `frontend/package.json` SHA-256: `d8da94003de04fcda8e0a29f103b69ae5289faa9c290436cf8e5fdbbf3f42ccf`.
- `frontend/package-lock.json` SHA-256: `b03260c536086b597cb74e056a565d5645fbed858c4a3d6566b5b1b2ab8c9f81`.

The npm result is an online registry audit at this point in time; it is not a guarantee against undisclosed vulnerabilities. Pin the final lockfile hash in release evidence and audit it again at package time.
