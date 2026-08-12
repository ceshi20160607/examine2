# FAST-FLOW-GRAPH-DRAFT-CANVAS-37 Acceptance

## Verdict

PASS at 2026-07-29T22:09:35+08:00.

Batch 37 closes the first functional Flow graph-authoring slice. The graph is a
canonical editor for the existing executable sequential draft route, not a
parallel browser-only representation.

## Delivered behavior

- A real Vue Flow canvas projects one start node, 1..10 ordered approval nodes,
  explicit approved/rejected terminals and every executable edge.
- Adding, assigning, moving and deleting an approval node updates the exact
  `approverIds` array used by the existing draft API and runtime.
- Selecting a node opens its property panel. Approval nodes reuse the live
  tenant member directory; trigger and terminal nodes expose their current
  draft effects.
- Trigger type and approved/rejected STATUS mappings update the graph summary
  as the existing canonical form properties change.
- `保存并校验` and `保存并模拟` first persist the route, then run Batch 36
  preflight against the server-returned new revision.
- The compact ordered-member form remains available as a safe fallback.
- Unsupported gateway/external-task nodes remain visibly disabled rather than
  being stored as inert or non-executable data.

## Verification

- Graph model/component/view targeted regression: 3 files and 60 tests passed.
- `npm.cmd test`: 53 files and 210 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- Real authenticated browser verification loaded the existing MySQL-backed
  system, rendered the live Vue Flow canvas, added a second approval node,
  observed 5 nodes/5 edges, switched node selection/property panels and loaded
  the real tenant member directory with zero console errors.
- The browser modal was closed without saving, so verification created no
  business definition or runtime data.
- Core regression: 20/20 tests passed.
- Flow regression: 191/191 tests passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.
- Batch 36's immediately preceding real MySQL/Redis journey remains the API and
  canonical draft round-trip proof; Batch 37 changed no backend or schema.

## Demo path

Open system Flow -> New or Revise definition -> add/select/reorder/remove
approval nodes -> assign members in the property panel -> configure trigger and
terminal mappings -> Save and Check or Save and Simulate.

## Deferred

- executable condition gateways, external tasks and subflows
- runtime instance overlay and animated execution tokens
- responsive, accessibility and exhaustive visual hardening
