# R20 Flow Canvas Designer Smoke

Status: PASS

Base URL: http://127.0.0.1:18131

## Covered Flow

- Created disposable system 542.
- Created empty draft flow 110.
- Empty canvas publish-check failed as expected.
- Browser used real /login, opened system admin flow management, clicked canvas configuration, inserted approval/end nodes, edited node JSON properties, saved canvas, ran simulation, and ran publish-check.
- API readback confirmed 2 nodes and 1 edges persisted.
- Publish-check passed with traceId trc_7b7f0cac-f59b-41e2-a9e7-c41431abf26a.
- Publish created version flow_v1782720928002 and snapshot snap_71.
- Mobile containment kept document overflow at 0 and canvas overflow inside the canvas panel.
- Cleanup: 542:DELETE.

## Screenshots

- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-empty.png
- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-configured.png
- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-publish-check.png
- docs/evidence/recovery/screenshots/r20-flow-canvas-designer/designer-mobile.png

## Result JSON

- docs/evidence/recovery/r20-flow-canvas-designer-result.json
