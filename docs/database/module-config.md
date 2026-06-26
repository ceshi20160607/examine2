# Module Config Schema

## Scope

Fragment: `sql/fragments/003-module-config.sql`

This fragment owns configurable module groups, modules, fields, dictionaries, list/detail/filter/scenario/action configuration, import/export configuration, print templates, work configuration, and publish records.

## Tables

- `un_module_group`: runtime top navigation groups.
- `un_module_definition`: business module metadata.
- `un_module_field_definition`: dynamic field definitions and field type settings.
- `un_module_dict_type`: dictionary categories.
- `un_module_dict_item`: dictionary options with color, icon, semantic, and status.
- `un_module_list_scene`: columns, filters, sorting, pagination, row click, and scene contracts.
- `un_module_action_config`: page, row, and batch action configuration.
- `un_module_import_export_config`: import/export capability and async result requirements.
- `un_module_print_template`: print template metadata.
- `un_module_work_config`: project task, plain task, and daily report configurable fields and kanban source fields.
- `un_module_publish_version`: publish check, publish, rollback, and impact summary.

## Constraints

- Module group is navigation and visibility only; business data belongs to `systemId/tenantId/moduleId`.
- Dictionary items own option color, icon, semantic, default, disabled, and reference-impact metadata.
- Work config does not own status/tag option values directly; it binds fields whose options come from dictionaries.

## Acceptance Notes

- Publishable configuration keeps draft, published version, rollback version, and audit trace.
- Dynamic list schemas must carry row click, disabled reasons, batch action limits, import/export config, and empty state.
