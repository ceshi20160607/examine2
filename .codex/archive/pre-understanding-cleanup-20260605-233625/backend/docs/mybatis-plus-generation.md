# MyBatis-Plus Generation Report

## Database Connection Source

- Source: `docs/service_info.md`
- JDBC URL: `jdbc:mysql://192.168.0.211:3306/examine1?characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai&useAffectedRows=true&allowPublicKeyRetrieval=true`
- Username: `root`

## SQL Import

- SQL 导入: success, imported `sql/init.sql` into `examine1` on `192.168.0.211:3306`; executed statements: 57; base code generation started only after the import completed.
- SQL file: `E:\workspace\03_project\unique\java\codex\sql\init.sql`
- Command: `mvn -pl examine-web -Dtest=MybatisPlusCodeGeneratorTest test`
- Result: success
- Executed statements: 57

## Generator Command

`mvn -pl examine-web -Dtest=MybatisPlusCodeGeneratorTest test`

## Table And Module Mapping

- `un_platt_` -> `examine-plat` -> `com.unique.examine.plat.base`
  - `un_platt_system`
  - `un_platt_tenant`
  - `un_platt_account`
  - `un_platt_account_tenant`
  - `un_platt_department`
  - `un_platt_role`
  - `un_platt_permission`
  - `un_platt_role_permission`
  - `un_platt_account_role`
  - `un_platt_dict`
  - `un_platt_dict_item`
- `un_module_` -> `examine-module` -> `com.unique.examine.module.base`
  - `un_module_model`
  - `un_module_field`
  - `un_module_field_option`
  - `un_module_page`
  - `un_module_menu`
  - `un_module_record`
  - `un_module_record_value`
  - `un_module_data_scope`
  - `un_module_export_job`
- `un_flow_` -> `examine-flow` -> `com.unique.examine.flow.base`
  - `un_flow_template`
  - `un_flow_template_version`
  - `un_flow_instance`
  - `un_flow_task`
  - `un_flow_approval_log`
- `un_upload_` -> `examine-upload` -> `com.unique.examine.upload.base`
  - `un_upload_storage_config`
  - `un_upload_file`
  - `un_upload_attachment`
  - `un_upload_import_export_job`
- `un_app_/un_openapi_` -> `examine-app` -> `com.unique.examine.app.base`
  - `un_app_application`
  - `un_app_version`
  - `un_openapi_client`
  - `un_openapi_credential`
  - `un_openapi_scope`
  - `un_openapi_ip_whitelist`
  - `un_openapi_idempotent`
  - `un_openapi_access_log`
- `un_sys_/un_audit_` -> `examine-core` -> `com.unique.examine.core.base`
  - `un_sys_config`
  - `un_sys_login_log`
  - `un_audit_operation_log`

## Output Paths

- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/`
- `backend/examine-plat/src/main/resources/com/unique/examine/plat/base/mapper/xml/`
- `backend/examine-module/src/main/java/com/unique/examine/module/base/`
- `backend/examine-module/src/main/resources/com/unique/examine/module/base/mapper/xml/`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/base/`
- `backend/examine-flow/src/main/resources/com/unique/examine/flow/base/mapper/xml/`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/base/`
- `backend/examine-upload/src/main/resources/com/unique/examine/upload/base/mapper/xml/`
- `backend/examine-app/src/main/java/com/unique/examine/app/base/`
- `backend/examine-app/src/main/resources/com/unique/examine/app/base/mapper/xml/`
- `backend/examine-core/src/main/java/com/unique/examine/core/base/`
- `backend/examine-core/src/main/resources/com/unique/examine/core/base/mapper/xml/`

## Execution Status

- Actual generator execution: yes
- Generated table count: 40
- OpenAPI tables: mapped to `examine-app` because `docs/db_design.md` assigns OpenAPI management to that business module.
- `un_sys_` and `un_audit_` tables: mapped to `examine-core` because they provide system configuration, login log, and audit foundations.
- Updated at: 2026-06-05T01:12:41.822404
