package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health endpoint for service and database verification.
 */
@RestController
public class HealthController {

    private static final Map<String, List<String>> REQUIRED_SCHEMA = requiredSchema();

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @GetMapping("/api/v1/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        checkRedis(status);
        try {
            Integer databaseProbe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            status.put("database", Integer.valueOf(1).equals(databaseProbe) ? "UP" : "DOWN");
            List<String> missingSchema = missingSchema();
            status.put("schema", missingSchema.isEmpty() ? "UP" : "MISMATCH");
            if (!missingSchema.isEmpty()) {
                status.put("status", "DEGRADED");
                status.put("missingSchema", missingSchema);
            }
        } catch (RuntimeException ex) {
            status.put("status", "DEGRADED");
            status.put("database", "DOWN");
            status.put("databaseError", ex.getClass().getSimpleName());
        }
        return ApiResponse.success(status);
    }

    private void checkRedis(Map<String, Object> status) {
        if (redisTemplate == null) {
            status.put("redis", "NOT_CONFIGURED");
            status.put("status", "DEGRADED");
            return;
        }
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            if ("PONG".equalsIgnoreCase(pong)) {
                status.put("redis", "UP");
                return;
            }
            status.put("redis", "DOWN");
            status.put("status", "DEGRADED");
        } catch (RuntimeException ex) {
            status.put("redis", "DOWN");
            status.put("redisError", ex.getClass().getSimpleName());
            status.put("status", "DEGRADED");
        }
    }

    private List<String> missingSchema() {
        List<String> missing = new ArrayList<>();
        REQUIRED_SCHEMA.forEach((table, columns) -> {
            for (String column : columns) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) "
                                + "FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() "
                                + "AND table_name = ? "
                                + "AND column_name = ?",
                        Integer.class, table, column);
                if (!Integer.valueOf(1).equals(count)) {
                    missing.add(table + "." + column);
                }
            }
        });
        return missing;
    }

    private static Map<String, List<String>> requiredSchema() {
        Map<String, List<String>> schema = new LinkedHashMap<>();
        schema.put("un_plat_account", List.of("account_name", "password_hash", "status", "deleted"));
        schema.put("un_plat_system", List.of("system_code", "system_name", "tenant_mode",
                "owner_account_id", "deleted"));
        schema.put("un_plat_tenant", List.of("system_id", "tenant_code", "tenant_name", "deleted"));
        schema.put("un_plat_department", List.of("system_id", "tenant_id", "department_name", "parent_id",
                "deleted"));
        schema.put("un_plat_member", List.of("system_id", "tenant_id", "member_name", "deleted"));
        schema.put("un_plat_account_member_binding", List.of("account_id", "system_id", "tenant_id",
                "system_member_id", "binding_status"));
        schema.put("un_plat_role", List.of("scope", "system_id", "tenant_id", "role_code", "role_name",
                "role_type", "builtin", "deleted"));
        schema.put("un_plat_role_member", List.of("role_id", "account_id", "system_member_id"));
        schema.put("un_plat_permission_version", List.of("system_id", "tenant_id", "version_no", "status"));
        schema.put("un_plat_role_permission", List.of("role_id", "permission_code", "effect"));
        schema.put("un_plat_role_field_permission", List.of("role_id", "module_id", "field_id",
                "permission_mode"));
        schema.put("un_plat_data_scope_rule", List.of("role_id", "scope_type", "scope_expression"));
        schema.put("un_plat_deny_policy", List.of("role_id", "permission_code", "deny_reason"));
        schema.put("un_plat_effective_permission_snapshot", List.of("system_id", "tenant_id",
                "system_member_id", "snapshot_json"));
        schema.put("un_plat_identity_provider", List.of("provider_code", "protocol_type", "status",
                "secret_ref_id"));
        schema.put("un_plat_system_sso_policy", List.of("system_id", "enabled_provider_ids",
                "org_mapping_rule", "jit_strategy"));
        schema.put("un_plat_sso_binding", List.of("identity_provider", "external_user_id", "account_id",
                "system_member_id"));
        schema.put("un_plat_no_member_access_request", List.of("request_id", "status", "target_system_id",
                "tenant_id", "role_ids", "data_scope"));
        schema.put("un_audit_login_log", List.of("account_id", "auth_method", "login_result", "request_id",
                "trace_id"));
        schema.put("un_audit_business_log", List.of("system_id", "tenant_id", "module_id", "action_code",
                "trace_id"));

        schema.put("un_module_group", List.of("system_id", "tenant_id", "group_code", "group_name"));
        schema.put("un_module_definition", List.of("system_id", "tenant_id", "module_code", "module_name",
                "publish_status"));
        schema.put("un_module_field_definition", List.of("module_id", "field_code", "field_type",
                "storage_type", "import_export_rule"));
        schema.put("un_module_dict_type", List.of("system_id", "dict_code", "dict_name"));
        schema.put("un_module_dict_item", List.of("dict_type_id", "item_code", "item_name", "color", "icon"));
        schema.put("un_module_list_scene", List.of("module_id", "scene_code", "filter_schema"));
        schema.put("un_module_action_config", List.of("module_id", "action_code", "position",
                "result_hook"));
        schema.put("un_module_import_export_config", List.of("module_id", "template_code",
                "field_mapping"));
        schema.put("un_module_print_template", List.of("module_id", "template_code", "template_body"));
        schema.put("un_module_work_config", List.of("system_id", "project_task_fields",
                "plain_task_fields", "kanban_field_code"));
        schema.put("un_module_publish_version", List.of("module_id", "version_no", "publish_status"));
        schema.put("un_module_dynamic_record", List.of("system_id", "tenant_id", "module_id", "title",
                "status", "owner_member_id"));
        schema.put("un_module_dynamic_value", List.of("record_id", "module_id", "field_id", "value_text",
                "value_json"));
        schema.put("un_module_dynamic_history", List.of("record_id", "action_code", "field_diff",
                "trace_id"));
        schema.put("un_module_dynamic_draft", List.of("system_id", "tenant_id", "module_id", "draft_key",
                "draft_json"));
        schema.put("un_module_dynamic_attachment", List.of("record_id", "file_id", "attachment_type"));
        schema.put("un_module_dynamic_sequence", List.of("system_id", "tenant_id", "module_id",
                "sequence_code", "current_value"));

        schema.put("un_upload_file", List.of("file_id", "file_name", "file_type", "object_key",
                "storage_policy_code"));
        schema.put("un_upload_file_version", List.of("file_id", "version_no", "object_key"));
        schema.put("un_upload_file_access_log", List.of("file_id", "action_code", "result", "trace_id"));
        schema.put("un_upload_storage_policy", List.of("policy_code", "storage_type", "status"));

        schema.put("un_flow_definition", List.of("system_id", "flow_code", "flow_name", "status"));
        schema.put("un_flow_node", List.of("flow_id", "node_code", "node_type", "node_config"));
        schema.put("un_flow_edge", List.of("flow_id", "source_node_code", "target_node_code",
                "condition_expression"));
        schema.put("un_flow_snapshot", List.of("flow_id", "version_no", "snapshot_json"));
        schema.put("un_flow_instance", List.of("system_id", "module_id", "record_id", "status",
                "current_node_code"));
        schema.put("un_flow_approval_task", List.of("instance_id", "node_code", "assignee_member_id",
                "task_status"));
        schema.put("un_flow_approval_action_log", List.of("instance_id", "task_id", "action_code",
                "operated_by"));
        schema.put("un_flow_simulation_log", List.of("flow_id", "input_json", "result_json"));

        schema.put("un_message_todo", List.of("system_id", "tenant_id", "todo_type", "status",
                "target_type"));
        schema.put("un_message_notification_template", List.of("template_code", "template_type", "channels",
                "status"));
        schema.put("un_message_message", List.of("system_id", "tenant_id", "message_type", "read_status",
                "target_type"));
        schema.put("un_message_delivery_log", List.of("message_id", "channel", "delivery_status",
                "trace_id"));

        schema.put("un_work_project", List.of("system_id", "tenant_id", "project_name", "status"));
        schema.put("un_work_task", List.of("system_id", "tenant_id", "task_type", "task_title", "status",
                "owner_member_id"));
        schema.put("un_work_task_comment", List.of("task_id", "comment_text", "created_by"));
        schema.put("un_work_task_event", List.of("task_id", "event_type", "event_payload"));
        schema.put("un_work_kanban_config", List.of("system_id", "task_type", "group_field_code",
                "column_config"));
        schema.put("un_work_daily_report", List.of("system_id", "tenant_id", "report_date",
                "owner_member_id", "content"));
        schema.put("un_work_daily_report_source", List.of("report_id", "source_type", "source_id"));
        schema.put("un_work_calendar_item", List.of("system_id", "tenant_id", "item_type", "item_date"));
        schema.put("un_work_daily_report_auto_source_rule", List.of("system_id", "source_types",
                "permission_scope"));

        schema.put("un_sys_secret_ref", List.of("secret_ref_id", "ref_type", "version_no",
                "rotation_status"));
        schema.put("un_sys_secret_rotation_job", List.of("job_id", "secret_ref_id", "status",
                "rollback_plan"));
        schema.put("un_sys_async_task", List.of("task_id", "biz_type", "status", "progress",
                "result_file_id"));
        schema.put("un_sys_async_task_event", List.of("task_id", "event_type", "event_payload"));
        schema.put("un_sys_async_task_file", List.of("task_id", "file_id", "file_role"));
        schema.put("un_sys_idempotency_key", List.of("idempotency_key", "biz_type", "request_hash",
                "response_json"));

        schema.put("un_openapi_app", List.of("system_id", "tenant_id", "external_app_code",
                "openapi_secret_ref_id", "scopes"));
        schema.put("un_openapi_call_log", List.of("external_app_id", "system_id", "tenant_id", "scope_code",
                "request_path", "result"));
        schema.put("un_agent_model_authorization", List.of("authorization_code", "model_provider",
                "secret_ref_id", "status"));
        schema.put("un_agent_policy", List.of("system_id", "policy_code", "module_scope", "field_scope",
                "action_scope", "data_scope_expression"));
        schema.put("un_agent_session", List.of("session_id", "session_scope", "account_id",
                "system_id"));
        schema.put("un_agent_audit_log", List.of("session_id", "tool_name", "result", "trace_id"));
        schema.put("un_agent_confirmation", List.of("confirmation_id", "confirmation_type", "status",
                "diff_json"));
        schema.put("un_ops_health_check", List.of("check_code", "status", "checked_at"));
        schema.put("un_ops_feature_flag", List.of("flag_code", "enabled", "scope"));
        schema.put("un_ops_quota", List.of("quota_code", "limit_value", "used_value"));
        schema.put("un_ops_rate_limit_policy", List.of("policy_code", "limit_value", "window_seconds"));
        schema.put("un_ops_backup_restore", List.of("backup_id", "status", "backup_type"));
        schema.put("un_ops_archive_restore_request", List.of("request_id", "status", "scope"));
        schema.put("un_ops_deployment", List.of("deployment_id", "environment_code", "status"));
        schema.put("un_ops_api_cache_policy", List.of("policy_code", "path_pattern", "ttl_seconds"));
        return schema;
    }
}
