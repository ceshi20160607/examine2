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
        schema.put("un_plat_account", columns("id,account_name,mobile,email,password_hash,status,last_login_at,created_at,updated_at,deleted"));
        schema.put("un_plat_system", columns("id,system_code,system_name,tenant_mode,owner_account_id,status,disabled_reason,created_at,updated_at,deleted"));
        schema.put("un_plat_tenant", columns("id,system_id,tenant_code,tenant_name,status,domain,created_at,updated_at,deleted"));
        schema.put("un_plat_department", columns("id,system_id,tenant_id,parent_id,dept_code,dept_name,sort_order,status,created_at,updated_at,deleted"));
        schema.put("un_plat_member", columns("id,system_id,tenant_id,dept_id,member_name,employee_no,mobile,email,status,created_at,updated_at,deleted"));
        schema.put("un_plat_account_member_binding", columns("id,account_id,system_id,tenant_id,system_member_id,binding_status,created_at,updated_at"));
        schema.put("un_plat_sso_binding", columns("id,account_id,system_id,tenant_id,identity_provider,external_user_id,external_dept_id,binding_status,created_at,updated_at"));
        schema.put("un_plat_no_member_access_request", columns("id,request_no,status,identity_provider,external_user_id,target_system_id,tenant_id,request_role,approver_id,approve_result,role_ids,data_scope,reject_reason,trace_id,created_at,updated_at"));
        schema.put("un_audit_login_log", columns("id,account_id,system_id,tenant_id,identity_provider,external_user_id,auth_method,mfa_result,account_binding_result,member_mapping_result,login_result,failure_reason,ip,device,request_id,trace_id,created_at"));
        schema.put("un_plat_role", columns("id,scope,system_id,tenant_id,role_code,role_name,role_type,builtin,status,description,created_at,updated_at,deleted"));
        schema.put("un_plat_role_member", columns("id,role_id,account_id,system_member_id,system_id,tenant_id,created_at"));
        schema.put("un_plat_permission_version", columns("id,scope,system_id,tenant_id,permission_version,published_by,published_at,change_summary"));
        schema.put("un_plat_role_permission", columns("id,role_id,permission_version,target_type,target_code,effect,permission_payload,created_at"));
        schema.put("un_plat_role_field_permission", columns("id,role_id,system_id,tenant_id,module_id,field_id,permission_mode,mask_rule,disabled_reason,created_at"));
        schema.put("un_plat_data_scope_rule", columns("id,role_id,system_id,tenant_id,module_id,scope_type,scope_expression,created_at"));
        schema.put("un_plat_deny_policy", columns("id,system_id,tenant_id,policy_code,policy_name,target_type,target_code,condition_payload,status,created_at"));
        schema.put("un_plat_permission_preview_log", columns("id,system_id,tenant_id,system_member_id,role_ids,module_id,record_id,action_code,decision_payload,trace_id,created_at"));
        schema.put("un_plat_effective_permission_snapshot", columns("id,snapshot_id,permission_version,system_id,tenant_id,system_member_id,source_role_ids,deny_policy_ids,field_permissions,action_permissions,data_scope,disabled_reason,explain_payload,expires_at,created_at"));
        schema.put("un_module_group", columns("id,system_id,tenant_id,group_code,group_name,icon,sort_order,visible_role_ids,publish_status,published_version,created_at,updated_at,deleted"));
        schema.put("un_module_definition", columns("id,system_id,tenant_id,group_id,module_code,module_name,status,publish_status,current_version,created_at,updated_at,deleted"));
        schema.put("un_module_field_definition", columns("id,system_id,tenant_id,module_id,field_code,field_name,field_type,storage_type,required,sortable,filter_operators,default_value,validation_rule,mask_rule,import_export_rule,dict_type_id,sort_order,status,created_at,updated_at,deleted"));
        schema.put("un_module_dict_type", columns("id,system_id,tenant_id,dict_code,dict_name,dict_kind,status,published_version,created_at,updated_at,deleted"));
        schema.put("un_module_dict_item", columns("id,dict_type_id,parent_id,item_code,item_name,color,icon,semantic,sort_order,default_flag,kanban_enabled,status,disabled_at,created_at,updated_at"));
        schema.put("un_module_list_scene", columns("id,module_id,scene_code,scene_name,columns_config,filters_config,sort_config,row_click_target,default_flag,created_at,updated_at"));
        schema.put("un_module_action_config", columns("id,module_id,action_code,action_name,action_type,selection_rule,permission_code,result_contract,status,created_at,updated_at"));
        schema.put("un_module_import_export_config", columns("id,module_id,import_enabled,export_enabled,export_all_enabled,template_file_id,result_task_required,precheck_rule,permission_code,updated_at"));
        schema.put("un_module_print_template", columns("id,module_id,template_code,template_name,template_file_id,field_mapping,status,created_at,updated_at"));
        schema.put("un_module_work_config", columns("id,system_id,tenant_id,config_type,field_list,card_fields,kanban_column_field_id,kanban_swimlane_field_id,kanban_group_field_id,publish_status,published_version,updated_at"));
        schema.put("un_module_publish_version", columns("id,system_id,tenant_id,object_type,object_id,version_no,publish_status,impact_refs,failure_items,trace_id,created_at"));
        schema.put("un_module_dynamic_record", columns("id,system_id,tenant_id,module_id,record_no,title,status,owner_member_id,permission_snapshot_id,created_by,updated_by,created_at,updated_at,deleted"));
        schema.put("un_module_dynamic_value", columns("id,record_id,module_id,field_id,child_row_id,value_text,value_number,value_datetime,value_json,created_at,updated_at"));
        schema.put("un_module_dynamic_index", columns("id,system_id,tenant_id,module_id,record_id,field_id,index_value,index_number,index_datetime"));
        schema.put("un_module_dynamic_child_row", columns("id,record_id,child_module_id,row_no,created_at,updated_at,deleted"));
        schema.put("un_module_dynamic_relation", columns("id,system_id,tenant_id,source_module_id,source_record_id,target_module_id,target_record_id,relation_type,created_at"));
        schema.put("un_module_dynamic_history", columns("id,system_id,tenant_id,module_id,record_id,action_code,field_diff,source_type,permission_snapshot_id,desensitize_result,trace_id,audit_log_id,operated_by,operated_at"));
        schema.put("un_module_dynamic_draft", columns("id,system_id,tenant_id,module_id,draft_no,draft_payload,created_by,updated_at"));
        schema.put("un_module_dynamic_attachment", columns("id,system_id,tenant_id,module_id,record_id,field_id,file_id,file_name,upload_status,draft_id,permission_snapshot_id,trace_id,created_by,created_at"));
        schema.put("un_module_dynamic_sequence", columns("id,system_id,tenant_id,module_id,sequence_type,prefix_rule,current_no,updated_at"));
        schema.put("un_upload_file", columns("id,file_id,scope,system_id,tenant_id,file_name,extension,mime_type,size_bytes,sha256,storage_provider,storage_bucket,object_key,storage_status,preview_status,owner_account_id,owner_member_id,permission_snapshot_id,created_at,updated_at,deleted"));
        schema.put("un_upload_file_version", columns("id,file_id,version_no,object_key,size_bytes,sha256,created_by,created_at"));
        schema.put("un_upload_file_access_log", columns("id,file_id,access_action,actor_account_id,actor_member_id,result,failure_reason,request_id,trace_id,created_at"));
        schema.put("un_upload_file_recycle", columns("id,file_id,recycle_reason,recycled_by,recycled_at,restore_deadline,restore_status,restored_by,restored_at"));
        schema.put("un_upload_storage_policy", columns("id,scope,system_id,tenant_id,policy_code,storage_provider,storage_bucket,secret_ref_id,max_file_size,allowed_extensions,preview_enabled,quota_limit_bytes,status,updated_at"));
        schema.put("un_flow_definition", columns("id,system_id,tenant_id,flow_code,flow_name,bound_module_id,trigger_rule,status,current_version,created_at,updated_at,deleted"));
        schema.put("un_flow_node", columns("id,flow_id,node_key,node_type,node_name,position_payload,property_payload,status,created_at,updated_at"));
        schema.put("un_flow_edge", columns("id,flow_id,edge_key,source_node_key,target_node_key,branch_label,condition_payload,created_at"));
        schema.put("un_flow_snapshot", columns("id,flow_id,version_no,node_payload,edge_payload,publish_check_result,published_by,published_at,trace_id"));
        schema.put("un_flow_instance", columns("id,system_id,tenant_id,flow_id,flow_version,module_id,record_id,status,current_node_ids,started_by,started_at,ended_at,trace_id"));
        schema.put("un_flow_approval_action_log", columns("id,instance_id,task_id,action_code,action_result,action_reason,operator_member_id,next_node_payload,idempotency_key,trace_id,audit_log_id,operated_at"));
        schema.put("un_flow_simulation_log", columns("id,flow_id,version_no,input_payload,output_payload,failure_items,simulated_by,trace_id,created_at"));
        schema.put("un_flow_approval_task", columns("id,instance_id,task_no,node_id,node_name,assignee_member_id,status,action_result,action_reason,field_permission_snapshot,due_at,operated_at,idempotency_key,trace_id,created_at"));
        schema.put("un_message_todo", columns("id,scope,system_id,tenant_id,todo_type,title,source_name,assignee_id,status,priority,due_at,target_payload,primary_action,trace_id,created_at"));
        schema.put("un_message_notification_template", columns("id,scope,system_id,template_code,template_type,variables,channels,target_rule,dedupe_key,read_receipt_required,quiet_policy,retry_policy,status,created_at"));
        schema.put("un_message_target", columns("id,scope,system_id,tenant_id,target_type,target_id,target_system_id,target_tenant_id,requires_system_switch,fallback_action,created_at"));
        schema.put("un_message_message", columns("id,scope,system_id,tenant_id,template_code,receiver_id,title,content,message_type,target_payload,read_status,archive_status,created_at"));
        schema.put("un_message_delivery_log", columns("id,message_id,channel,status,failure_reason,retry_count,read_receipt,do_not_disturb,archive_status,trace_id,created_at"));
        schema.put("un_audit_log_export_task", columns("id,scope,system_id,tenant_id,log_type,filter_payload,async_task_id,requested_by,trace_id,created_at"));
        schema.put("un_audit_business_log", columns("id,log_type,scope,system_id,tenant_id,operator_id,action_code,object_type,object_id,result,request_id,trace_id,audit_log_id,ip,device,field_diff,permission_snapshot,desensitize_result,failure_reason,created_at"));
        schema.put("un_work_project", columns("id,system_id,tenant_id,project_code,project_name,owner_member_id,status,progress,start_date,end_date,created_at,updated_at,deleted"));
        schema.put("un_work_task", columns("id,system_id,tenant_id,task_type,project_id,task_title,assignee_member_id,collaborators,status,tags,progress,due_at,completed_at,related_object,permission_snapshot_id,created_at,updated_at,deleted"));
        schema.put("un_work_task_collaborator", columns("id,task_id,member_id,collaborator_type,created_at"));
        schema.put("un_work_task_comment", columns("id,task_id,parent_id,commenter_id,content,created_at"));
        schema.put("un_work_task_event", columns("id,task_id,event_type,before_payload,after_payload,operator_member_id,trace_id,audit_log_id,created_at"));
        schema.put("un_work_task_relation", columns("id,task_id,related_type,related_id,relation_payload,created_at"));
        schema.put("un_work_kanban_config", columns("id,system_id,tenant_id,task_type,column_field_id,swimlane_field_id,group_field_id,card_fields,published_version,updated_at"));
        schema.put("un_work_daily_report", columns("id,system_id,tenant_id,report_date,submitter_id,status,content,source_summary,permission_snapshot_id,submitted_at,created_at,updated_at"));
        schema.put("un_work_daily_report_source", columns("id,report_id,draft_no,source_type,source_id,source_snapshot,permission_snapshot_id,selected,created_at"));
        schema.put("un_work_calendar_item", columns("id,system_id,tenant_id,member_id,item_date,item_type,item_id,title,target_payload,created_at"));
        schema.put("un_work_daily_report_auto_source_rule", columns("id,system_id,tenant_id,source_types,task_scope,todo_scope,message_scope,log_scope,approval_scope,permission_policy,manual_confirm_required,updated_at"));
        schema.put("un_sys_secret_ref", columns("id,secret_ref_id,ref_type,display_name,version_no,expires_at,rotation_status,last_used_at,storage_ref,created_at,updated_at"));
        schema.put("un_sys_secret_rotation_job", columns("id,job_id,secret_ref_id,status,new_version,rollback_plan,trace_id,audit_log_id,created_at,updated_at"));
        schema.put("un_plat_identity_provider", columns("id,provider_code,provider_name,protocol,issuer,client_id,secret_ref_id,cert_ref_id,domain_whitelist,jit_policy,mfa_policy,status,created_at,updated_at"));
        schema.put("un_plat_system_sso_policy", columns("id,system_id,tenant_id,enabled_provider_ids,tenant_domains,org_mapping,employee_binding,jit_member_policy,no_member_feedback,status,updated_at"));
        schema.put("un_system_data_source", columns("id,system_id,tenant_id,source_code,source_name,source_type,connection_config,auth_config,sync_config,desensitize_config,status,publish_status,published_version,last_check_status,last_check_trace_id,last_checked_at,created_at,updated_at,deleted"));
        schema.put("un_openapi_app", columns("id,system_id,tenant_id,external_app_code,app_name,status,openapi_secret_ref_id,scopes,callback_url,rate_limit,last_used_at,created_at,updated_at"));
        schema.put("un_openapi_call_log", columns("id,external_app_id,system_id,tenant_id,scope_code,request_method,request_path,result,idempotency_key,trace_id,failure_reason,created_at"));
        schema.put("un_agent_model_authorization", columns("id,authorization_code,model_provider,model_name,model_credential_ref_id,quota_config,data_outbound_policy,status,version_no,created_at"));
        schema.put("un_agent_policy", columns("id,system_id,tenant_id,policy_code,module_scope,field_scope,action_scope,data_scope_expression,outbound_limit,desensitize_policy,policy_version,status,created_at"));
        schema.put("un_agent_session", columns("id,session_id,scope,system_id,tenant_id,system_member_id,authorization_code,model_version,prompt_version,permission_snapshot_id,status,created_by,created_at,updated_at"));
        schema.put("un_agent_audit_log", columns("id,session_id,confirmation_id,scope,system_id,tenant_id,model_version,prompt_version,policy_version,permission_snapshot_id,conversation_snapshot,tool_call_snapshot,desensitize_result,outbound_snapshot,trace_id,audit_log_id,created_at"));
        schema.put("un_agent_confirmation", columns("id,confirmation_id,confirm_type,status,source_conversation,diff_payload,permission_snapshot_id,confirmed_by,confirmed_at,audit_log_id,trace_id,created_at"));
        schema.put("un_sys_async_task", columns("id,task_id,biz_type,idempotency_key,status,progress,retryable,cancelable,rollback_supported,result_file_id,error_file_id,failure_reason,partial_success_count,partial_failure_count,trace_id,audit_log_id,created_by,created_at,updated_at"));
        schema.put("un_sys_async_task_event", columns("id,task_id,event_type,event_payload,operator_id,trace_id,created_at"));
        schema.put("un_sys_async_task_file", columns("id,task_id,file_type,file_id,file_name,created_at"));
        schema.put("un_sys_idempotency_key", columns("id,scope,system_id,tenant_id,idempotency_key,request_hash,response_snapshot,expires_at,created_at"));
        schema.put("un_ops_health_check", columns("id,scope,system_id,check_type,status,result_payload,trace_id,checked_by,checked_at"));
        schema.put("un_ops_feature_flag", columns("id,flag_code,scope,system_id,tenant_id,status,rules,rollback_version,audit_log_id,updated_at"));
        schema.put("un_ops_quota", columns("id,scope,system_id,tenant_id,quota_type,quota_limit,quota_used,warn_threshold,updated_at"));
        schema.put("un_ops_rate_limit_policy", columns("id,scope,system_id,tenant_id,policy_code,limit_rule,status,audit_log_id,updated_at"));
        schema.put("un_ops_backup_restore", columns("id,backup_no,backup_type,scope,system_id,status,boundary_payload,result_payload,trace_id,created_at"));
        schema.put("un_ops_archive_restore_request", columns("id,request_no,scope,system_id,object_type,archive_condition,status,approver_id,trace_id,created_at"));
        schema.put("un_ops_deployment", columns("id,deployment_no,env_code,backend_version,frontend_version,config_version,status,rollback_plan,destructive_script_confirmed,trace_id,created_at"));
        schema.put("un_ops_api_cache_policy", columns("id,policy_code,cache_domain,key_rule,invalidation_rule,status,updated_at"));
        return schema;
    }

    private static List<String> columns(String csv) {
        return List.of(csv.split(","));
    }

}
