# Cycle116 print composition and field-bound file acceptance

- outcome: `P7_PRINT_FILE_COMPOSITION_GAP`
- cycle: `CYCLE-PHASE-GAP-CLOSURE-116`
- evaluatedAt: `2026-08-07T17:15:00+08:00`
- verdict: `PASS`
- migration: `V8_90_0__file_field_and_print_composition.sql`

## Composition contract

- Versioned templates support header, title, footer, ordered scalar fields,
  relation display values, subtable detail rows, positioned signature/seal/
  preprint areas, QR/barcode elements, pagination and immutable asset bindings.
- Preview, PDF job, history and download consume the exact module publication,
  template version and record version. Generated output is persisted through
  the existing file owner and permission boundary.
- A SIGNATURE field is a real file reference. Empty signatures render an empty
  positioned area; bound images are embedded after authorized file resolution.

## Real database and PDF verification

- `Vs3DraftJourneyIntegrationTest`: `5/5` passed against real MySQL. It creates
  a `RELATION` value (`customer=Acme`) and `SUBTABLE` value (`lines=8.25`), then
  publishes, previews the table, generates PDF and reads the frozen snapshot.
- `ImportJourneyIntegrationTest`: `2/2` passed, including actual PNG signature
  binding, embedded preview data URI, PDF creation, history and post-restart
  permission readback.
- `runtime-print-drawer.spec.ts`, `print-template-manager.spec.ts` and
  `runtime-file-field.spec.ts` are included in the final frontend green suite.

## Packaged browser journey

The in-app browser operated the packaged Cycle116 frontend against the packaged
JAR and a fresh MySQL/Redis pair. No direct API substituted for the key UI
actions.

1. Logged in as the isolated bootstrap root and created system
   `cycle116-print-browser` (`2085493080679829506`).
2. In Module Configuration, created group `print_acceptance`, module
   `print_document`, required TEXT field `document_title`, SIGNATURE field
   `customer_signature`, and template `cycle116_composed_print`.
3. Bound both fields, added a positioned signature area bound to
   `customer_signature`, a QR element and a barcode element, then checked and
   published module V1 and template V1.
4. Created and activated record `R-2085495552790024193`. Preview rendered the
   page header, title, active record/version, scalar value, signature region,
   QR, barcode and footer inside the real iframe.
5. Submitted PDF task `2085495733447086081`. Page state moved from queued to
   completed; print history displayed `已完成 · 记录 v1`.
6. Clicked `下载 PDF`. The downloaded file is
   `print_document-2085495552790024193-print-2085495733447086081.pdf`, size
   `69,366` bytes, starts with `%PDF-1.6`, SHA-256
   `cc5f4796010b7817a6615ed0ddac68663a3d20ce453fde9ee8cc8ffda5bfe97a`.

## Package and restart evidence

- Package archive:
  `.cursor/session/packages/CYCLE-PHASE-GAP-CLOSURE-116/CYCLE-PHASE-GAP-CLOSURE-116-20260807-061707.zip`
- Archive SHA-256:
  `1855be09cc963dc91b47a689505c4252b58fbb962f2412652f6dc6bdedf579c3`
- Packaged JAR SHA-256:
  `837af34233042b1ed179ac71a3be4e58ba442966ab1505814481f919460bcd5b`
- Fresh start applied `112` migrations through `8.91.0`; restart validated
  `112` and reported no migration necessary. Health was `UP`, login succeeded
  and the packaged frontend returned HTTP 200 before the browser journey.

This is rolling-cycle evidence and consumes no CP1/CP2/CP3 attempt.

