# Dynamic Runtime Schema

## Scope

Fragment: `sql/fragments/004-dynamic-runtime.sql`

This fragment owns dynamic records, values, indexes, child rows, relations, record history, drafts, attachments, sequence numbers, and upload storage metadata.

## Tables

- `un_module_dynamic_record`: business record master row.
- `un_module_dynamic_value`: field values by record and field definition.
- `un_module_dynamic_index`: query-friendly values for sortable/filterable fields.
- `un_module_dynamic_child_row`: child-table row metadata.
- `un_module_dynamic_relation`: relation links between records.
- `un_module_dynamic_history`: field/action history with before and after values.
- `un_module_dynamic_draft`: create/edit draft content.
- `un_module_dynamic_attachment`: attachment binding and upload status.
- `un_module_dynamic_sequence`: atomic sequence state for auto-number fields.
- `un_upload_file`: file metadata and object-storage reference.
- `un_upload_file_version`: file version metadata for replacement and rollback.
- `un_upload_file_access_log`: preview, download, upload, and denied access logs.
- `un_upload_file_recycle`: file recycle and restore lifecycle.
- `un_upload_storage_policy`: scoped storage policy with secret reference only.

## Constraints

- Record data always belongs to `systemId`, `tenantId`, and `moduleId`.
- List search must be server-side paginated.
- Sorting/filtering requires configured field capability, published version, permission snapshot, and supported index.
- Large import, export, print, delete, or transfer operations return `AsyncTask`.
- Upload storage keeps only object references and `secret_ref_id`; no storage secret plaintext is stored.

## Acceptance Notes

- Indexes cover module, tenant, owner, status, field index, child rows, relations, drafts, sequence lookups, file references, access traces, and recycle state.
- History stores source channel, permission snapshot, desensitize result, trace ID, and audit ID.
