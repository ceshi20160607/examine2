# R30 Schema-Driven Page Runtime

Status: PASS

Task: REC-P0-030 Schema-Driven Page Runtime

Base URL: http://127.0.0.1:18131

Evidence:

- Machine result: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\r30-schema-runtime-result.json
- API audit: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r30-schema-runtime\schema-runtime-api-audit.json
- Browser audit: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r30-schema-runtime\schema-preview-browser-audit.json
- Browser screenshot: D:\workspace\01_project\snow\cursor\examine2\docs\evidence\recovery\screenshots\r30-schema-runtime\desktop-schema-preview.png

Assertions:

- Admin configured fields, scene, page components, and page schema for module 224.
- Page publish-check passed and publish version is PAGE_v1782803744486.
- Admin published schema version equals the publish version.
- Normal runtime schema reads the same schema version.
- Normal runtime schema hides secretNote and removes the component bound to that hidden field.
- Normal runtime schema marks readable fields readonly.
- Runtime list schema also removes the hidden field.
- Direct normal-member record create is rejected with HTTP 403.
- Deployed browser evidence on asset /assets/index-Bm_DgZ50.js shows admin Schema preview rendering the published schema, fields, components, and overflowX=0.
- Cleanup: 686:DELETE, 687:DELETE
