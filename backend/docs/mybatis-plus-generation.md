# MyBatis-Plus Generation Report

- generatedAt: 2026-06-06T23:03:42.938504200+08:00
- mode: execute
- backendRoot: E:\workspace\03_project\unique\java\examine2\backend
- datasourceSource: environment variables
- datasourceConfigured: true

## Generation Command

```text
GeneratorCli --backend-root . --sql ..\sql\init.sql --all-from-sql --execute --report docs\mybatis-plus-generation.md
```

## SQL Execution

`sql/init.sql` was imported by DBA-006 before this generation phase. The generator reads table metadata from the configured MySQL database and does not rewrite SQL.

## Module Mappings

| prefixes | module | basePackage | sourceRoot | mapperXmlRoot |
| --- | --- | --- | --- | --- |
| un_plat_ | examine-plat | com.unique.examine.plat.base | examine-plat/src/main/java | examine-plat/src/main/resources/mapper/base |
| un_module_ | examine-module | com.unique.examine.module.base | examine-module/src/main/java | examine-module/src/main/resources/mapper/base |
| un_flow_ | examine-flow | com.unique.examine.flow.base | examine-flow/src/main/java | examine-flow/src/main/resources/mapper/base |
| un_upload_ | examine-upload | com.unique.examine.upload.base | examine-upload/src/main/java | examine-upload/src/main/resources/mapper/base |
| un_openapi_ | examine-app | com.unique.examine.app.base | examine-app/src/main/java | examine-app/src/main/resources/mapper/base |
| un_sys_, un_audit_ | examine-core | com.unique.examine.core.base | examine-core/src/main/java | examine-core/src/main/resources/mapper/base |

## Planned Tables

| table | module | javaOutputRoot | mapperXmlRoot |
| --- | --- | --- | --- |
| un_plat_account | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_system | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_tenant | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_role | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_menu | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_operation | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_account_role | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_role_menu | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_role_operation | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_plat_config | examine-plat | E:\workspace\03_project\unique\java\examine2\backend\examine-plat\src\main\java | examine-plat/src/main/resources/mapper/base |
| un_module_member | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_member_tenant | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_dept | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_member_dept | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_member_role | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_system_menu | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_system_operation | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_menu | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_operation | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_field_permission | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_data_scope | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_openapi_scope | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_role_explicit_deny | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_permission_version | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_dict_type | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_dict_item | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_dict_reference | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_app | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_app_version | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_model | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_field | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_field_option | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_unique_constraint | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_page_schema | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_menu | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_action | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_publish_version | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_serial_sequence | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_value | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_index | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_unique_index | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_relation | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_child_row | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_record_history | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_export_template | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_export_template_field | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_export_job | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_module_export_job_log | examine-module | E:\workspace\03_project\unique\java\examine2\backend\examine-module\src\main\java | examine-module/src/main/resources/mapper/base |
| un_flow_template | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_template_version | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_template_node | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_template_line | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_template_condition | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_binding | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_instance | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_task | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_task_actor | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_cc | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_action_log | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_flow_trace_log | examine-flow | E:\workspace\03_project\unique\java\examine2\backend\examine-flow\src\main\java | examine-flow/src/main/resources/mapper/base |
| un_upload_storage_config | examine-upload | E:\workspace\03_project\unique\java\examine2\backend\examine-upload\src\main\java | examine-upload/src/main/resources/mapper/base |
| un_upload_file | examine-upload | E:\workspace\03_project\unique\java\examine2\backend\examine-upload\src\main\java | examine-upload/src/main/resources/mapper/base |
| un_upload_file_part | examine-upload | E:\workspace\03_project\unique\java\examine2\backend\examine-upload\src\main\java | examine-upload/src/main/resources/mapper/base |
| un_upload_file_reference | examine-upload | E:\workspace\03_project\unique\java\examine2\backend\examine-upload\src\main\java | examine-upload/src/main/resources/mapper/base |
| un_openapi_client | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_client_credential | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_client_scope | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_ip_whitelist | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_nonce | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_idempotency_record | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_rate_limit_policy | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_rate_limit_counter | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_openapi_access_log | examine-app | E:\workspace\03_project\unique\java\examine2\backend\examine-app\src\main\java | examine-app/src/main/resources/mapper/base |
| un_sys_idempotency_record | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_sys_request_log | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_sys_error_log | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_audit_operation_log | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_audit_record_change | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_sys_health_check_result | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_sys_runtime_config_check | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |
| un_sys_migration_status | examine-core | E:\workspace\03_project\unique\java\examine2\backend\examine-core\src\main\java | examine-core/src/main/resources/mapper/base |

## Execution

MyBatis-Plus generation executed by module. Generated artifacts are limited to entity, mapper, mapper.xml, service and serviceImpl under each module base layer.

Base CRUD methods generated in every base service: `queryById`、`queryAll`、`queryPage`、`addOrUpdate`、`deleteByIds`. Mapper XML includes `BaseResultMap` and `Base_Column_List`. No base Controller is generated; public APIs must stay in manage controllers.

| module | tableCount | tables |
| --- | ---: | --- |
| examine-plat | 10 | un_plat_account, un_plat_system, un_plat_tenant, un_plat_role, un_plat_menu, un_plat_operation, un_plat_account_role, un_plat_role_menu, un_plat_role_operation, un_plat_config |
| examine-module | 40 | un_module_member, un_module_member_tenant, un_module_dept, un_module_member_dept, un_module_role, un_module_member_role, un_module_system_menu, un_module_system_operation, un_module_role_menu, un_module_role_operation, un_module_role_field_permission, un_module_role_data_scope, un_module_role_openapi_scope, un_module_role_explicit_deny, un_module_permission_version, un_module_dict_type, un_module_dict_item, un_module_dict_reference, un_module_app, un_module_app_version, un_module_model, un_module_field, un_module_field_option, un_module_unique_constraint, un_module_page_schema, un_module_menu, un_module_action, un_module_publish_version, un_module_serial_sequence, un_module_record, un_module_record_value, un_module_record_index, un_module_record_unique_index, un_module_record_relation, un_module_record_child_row, un_module_record_history, un_module_export_template, un_module_export_template_field, un_module_export_job, un_module_export_job_log |
| examine-flow | 12 | un_flow_template, un_flow_template_version, un_flow_template_node, un_flow_template_line, un_flow_template_condition, un_flow_binding, un_flow_instance, un_flow_task, un_flow_task_actor, un_flow_cc, un_flow_action_log, un_flow_trace_log |
| examine-upload | 4 | un_upload_storage_config, un_upload_file, un_upload_file_part, un_upload_file_reference |
| examine-app | 9 | un_openapi_client, un_openapi_client_credential, un_openapi_client_scope, un_openapi_ip_whitelist, un_openapi_nonce, un_openapi_idempotency_record, un_openapi_rate_limit_policy, un_openapi_rate_limit_counter, un_openapi_access_log |
| examine-core | 8 | un_sys_idempotency_record, un_sys_request_log, un_sys_error_log, un_audit_operation_log, un_audit_record_change, un_sys_health_check_result, un_sys_runtime_config_check, un_sys_migration_status |

### Controller Cleanup

No default controller directory was found after generation.

## Old Generator Reference

Referenced `.codex/oldgenerator` concepts: `GeneratorOwner`, `DefaultTemplateEngine` and `template_owner` base templates. Removed legacy datasource, interactive-only entry, legacy package naming and Controller generation.
