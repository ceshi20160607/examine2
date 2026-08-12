# P4-C2 Contract Review

## Meeting

- meeting_id: `P4-C2-CONTRACT-REVIEW`
- status: `completed`
- owner: `pm`
- verifier: `leader`
- reviewed_at: `2026-07-20T19:06:12+08:00`
- requirements: `REQ-FIELD-001`, `REQ-RUNTIME-001`, `REQ-RUNTIME-002`, `REQ-RUNTIME-003`, `REQ-DATA-MODEL-001`, `REQ-RBAC-001`, `REQ-MOBILE-001`, `NFR-SEC-001`
- journeys: `JRN-B3`, `JRN-B4`, `JRN-B5`
- contract: `.cursor/session/current-node.md`

## Decision

P4-C2 is one complete contact, sensitive and structured-field slice. It delivers `PHONE`, `EMAIL`, `URL`, `IDENTITY`, `ADDRESS`, `GEO`, `BARCODE`, `RICH_TEXT`, `JSON`, `SECRET` and `STATUS` from configuration publication through canonical persistence, secure authorization, typed query and ordinary-member desktop/mobile use. The machine field/API/DB contracts settle the field set, shapes, operators and exclusions; this review closes the remaining implementation choices without widening the product scope.

## Current-Gap Finding

The current runtime reports only P4-A2/P4-C1 types as available. The value table contains unused ciphertext/hash columns but `ck_rv_p4_plain_value` forbids every encrypted row, carries no key versions and is too small for worst-case UTF-8 SECRET input. Current schema capabilities do not expose separate sensitive-read/query permission, query indexes lack key-version routing and declared JSON paths, publication lacks STATUS transitions, and frontend controls are generic or unavailable. Bypassing these gaps with plaintext, reversible masking, opaque JSON or client-only validation would violate the accepted machine contracts.

## External Engineering Basis

- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): authenticated encryption, independent keys, versioned rotation and external key management shape the sensitive-value contract.
- [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final): AES-GCM is the authenticated-encryption primitive used by the provider.
- [Google libphonenumber](https://github.com/google/libphonenumber): server phone parsing, validation and E.164 formatting use the maintained Java library instead of a local regex.
- [jsoup Safelist sanitizer](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer): rich HTML is parsed and cleaned on the server with a fixed allow-list.
- [MySQL spatial convenience functions](https://dev.mysql.com/doc/refman/8.4/en/spatial-function-reference.html): GEO exact post-filter uses supported spatial functions where applicable after a bounded candidate query.
- [Tiptap Vue 3 integration](https://tiptap.dev/docs/editor/getting-started/install/vue3): the runtime rich-text control uses the established Vue 3 editor packages; the server sanitizer remains authoritative.

## Role Review

| role | verdict | decision |
|---|---|---|
| product | PASS | The eleven field types, user-visible masks, legal transitions, declared JSON paths and explicit deferrals form one coherent increment. |
| architect | PASS | Canonical aggregate values remain the source of truth; typed/path/hash/search indexes are projections and never replace validation or exact GEO checks. |
| security | PASS | AES-GCM plus independent HMAC keys, AAD scope binding, explicit key versions, fail-closed providers and separate read/query permissions close the plaintext and oracle risks. |
| dba | PASS | A forward populated-table migration must replace, not bypass, existing checks; widen ciphertext storage, add key/path routes and preserve P4-A/B/C1 rows. |
| backend | PASS | Task 01 owns publication, normalizers, key provider and value persistence; task 02 owns projections, permissions and all operators. |
| frontend | PASS | Task 03 consumes canonical shapes and capability flags; masked values are never round-tripped and domain controls replace generic text fallbacks. |
| uiux | PASS | Repeatable contact rows, explicit coordinates, structured address/barcode, rich text, JSON validation, secret replacement and legal STATUS targets have responsive states. |
| test | PASS | Boundary tables include key absence/old version, plaintext leakage inspection, sanitization, JSON path restrictions, GEO candidate bounds and transition negatives. |
| pm | PASS | Three tasks each have one outcome and a 240-minute estimate; tasks 02/03 may parallelize only after task 01 freezes persistence and API behavior. |

## Risk Decisions

- The application may remain runnable without a key provider, but sensitive publication/activation and writes fail closed. This preserves non-sensitive systems without inventing a default secret.
- Encryption and HMAC keys are independent and versioned. P4-C2 proves compatible old-key read/query; a bulk rotation operator is not hidden inside record traffic.
- SECRET is write-only even for administrators. IDENTITY plaintext requires an explicit sensitive-read permission, and sensitive equality requires a separate query permission.
- GEO is WGS84-only and bounded to 2,000 candidates before exact evaluation. No geocoder, map SDK or silent coordinate conversion is introduced.
- RICH_TEXT sanitation is server-authoritative. JSON queries are limited to immutable declared scalar paths; arbitrary JSONPath is rejected.
- P4-C2 passes only when all eleven types and all three tasks pass. A partial subset cannot be reported as node completion.

## Leader Gate

Verdict: **PASS**. Requirements, machine contracts, current gaps, security model, forward migration, role ownership, parallel boundary, negative cases and completion boundary are explicit. `P4-C2-01` may start. Any proposal to add map/geocoding, scanner capture, custom HTML allow-lists, arbitrary JSONPath or weaker sensitive storage returns to PM/security review instead of expanding this task.
