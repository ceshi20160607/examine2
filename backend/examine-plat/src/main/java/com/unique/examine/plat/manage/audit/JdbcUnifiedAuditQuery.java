package com.unique.examine.plat.manage.audit;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class JdbcUnifiedAuditQuery implements UnifiedAuditQuery {
    private static final Set<String> CATEGORIES = Set.of(
            "ALL", "AUTH", "CONFIG", "DATA", "FLOW", "FILE", "TASK",
            "OPENAPI", "MESSAGE", "AI", "OPERATION");
    private static final Set<String> RESULTS = Set.of(
            "ALL", "SUCCESS", "DENIED", "FAILED", "PENDING");

    private static final String UNION_SQL = """
            SELECT CAST(NULL AS BINARY) log_id, CAST(NULL AS BINARY) category,
                   CAST(NULL AS BINARY) source, CAST(NULL AS BINARY) event_type,
                   CAST(NULL AS BINARY) actor_id, CAST(NULL AS BINARY) actor_name,
                   CAST(NULL AS BINARY) object_type, CAST(NULL AS BINARY) object_id,
                   CAST(NULL AS BINARY) result, CAST(NULL AS BINARY) failure_code,
                   CAST(NULL AS BINARY) request_id, CAST(NULL AS BINARY) trace_id,
                   CAST(NULL AS SIGNED) system_id, CAST(NULL AS SIGNED) tenant_id,
                   CAST(NULL AS DATETIME(6)) occurred_at,
                   CAST(NULL AS BINARY) detail_one_label, CAST(NULL AS BINARY) detail_one_value,
                   CAST(NULL AS BINARY) detail_two_label, CAST(NULL AS BINARY) detail_two_value
             WHERE 1=0
            UNION ALL
            SELECT CONCAT('OPERATION:', operation_row.id) log_id,
                   CASE
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'FLOW' THEN 'FLOW'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'FILE|ATTACHMENT' THEN 'FILE'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'OPENAPI|APPLICATION|CALLBACK' THEN 'OPENAPI'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'EVENT|MESSAGE|NOTIFICATION' THEN 'MESSAGE'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'WORK|TASK|TODO|JOB|IMPORT|EXPORT|PRINT' THEN 'TASK'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'RECORD|COMMENT|TEAM|RELATION|FAVORITE|SAVED_VIEW' THEN 'DATA'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'CONFIG|MODULE|FIELD|PAGE|RULE|DICTIONARY|ROLE|PERMISSION|DASHBOARD|DATASOURCE|KPI|REPORT|TEMPLATE|SYSTEM|TENANT|DEPARTMENT|MEMBER' THEN 'CONFIG'
                     WHEN UPPER(CONCAT(operation_row.operation_type, ' ', operation_row.aggregate_type)) REGEXP 'AI|AGENT' THEN 'AI'
                     ELSE 'OPERATION'
                   END category,
                   operation_row.source_type source,
                   operation_row.operation_type event_type,
                   CAST(operation_row.actor_account_id AS CHAR) actor_id,
                   account_row.display_name actor_name,
                   operation_row.aggregate_type object_type,
                   operation_row.aggregate_id object_id,
                   operation_row.result result,
                   operation_row.failure_code failure_code,
                   operation_row.request_id request_id,
                   operation_row.trace_id trace_id,
                   operation_row.system_id system_id,
                   operation_row.tenant_id tenant_id,
                   operation_row.created_at occurred_at,
                   '上下文' detail_one_label, operation_row.context_type detail_one_value,
                   '操作来源' detail_two_label, operation_row.source_type detail_two_value
              FROM un_audit_operation operation_row
              LEFT JOIN un_plat_account account_row ON account_row.id=operation_row.actor_account_id
            UNION ALL
            SELECT CONCAT('SECURITY:', security_row.id), 'AUTH', security_row.source_type,
                   security_row.event_type, CAST(security_row.account_id AS CHAR), account_row.display_name,
                   'AUTHENTICATION', NULL, security_row.result, security_row.failure_code,
                   security_row.request_id, security_row.trace_id, security_row.system_id,
                   security_row.tenant_id, security_row.created_at,
                   '认证来源', security_row.source_type, '事件类型', security_row.event_type
              FROM un_audit_security security_row
              LEFT JOIN un_plat_account account_row ON account_row.id=security_row.account_id
            UNION ALL
            SELECT CONCAT('OPENAPI:', call_row.id), 'OPENAPI', 'OPENAPI',
                   'OPENAPI_CALL', CAST(application_row.id AS CHAR), application_row.name,
                   'ROUTE', call_row.route_template,
                   CASE WHEN call_row.http_status BETWEEN 200 AND 399 THEN 'SUCCESS' ELSE 'FAILED' END,
                   CASE WHEN call_row.http_status BETWEEN 200 AND 399 THEN NULL ELSE call_row.result_category END,
                   call_row.request_id, call_row.trace_id, application_row.system_id,
                   application_row.tenant_id, call_row.created_at,
                   'HTTP', CONCAT(call_row.request_method, ' ', call_row.http_status),
                   '耗时', CONCAT(call_row.latency_ms, ' ms')
              FROM un_openapi_call_log call_row
              JOIN un_openapi_application application_row ON application_row.id=call_row.application_id
            UNION ALL
            SELECT CONCAT('PLATFORM_OPENAPI:', call_row.id), 'OPENAPI', 'PLATFORM_OPENAPI',
                   'OPENAPI_CALL', CAST(application_row.service_account_id AS CHAR), account_row.display_name,
                   'ROUTE', call_row.route_template,
                   CASE WHEN call_row.http_status BETWEEN 200 AND 399 THEN 'SUCCESS' ELSE 'FAILED' END,
                   CASE WHEN call_row.http_status BETWEEN 200 AND 399 THEN NULL ELSE call_row.result_category END,
                   call_row.request_id, call_row.trace_id, NULL, NULL, call_row.created_at,
                   'HTTP', CONCAT(call_row.request_method, ' ', call_row.http_status),
                   'Latency', CONCAT(call_row.latency_ms, ' ms')
              FROM un_platform_openapi_call_log call_row
              LEFT JOIN un_platform_openapi_application application_row ON application_row.id=call_row.application_id
              LEFT JOIN un_plat_account account_row ON account_row.id=application_row.service_account_id
            UNION ALL
            SELECT CONCAT('MESSAGE:', delivery_row.id), 'MESSAGE', delivery_row.channel,
                   'MESSAGE_DELIVERY', CAST(delivery_row.recipient_member_id AS CHAR), member_row.display_name,
                   delivery_row.target_type, delivery_row.target_id,
                   CASE delivery_row.status WHEN 'DELIVERED' THEN 'SUCCESS' WHEN 'FAILED' THEN 'FAILED' WHEN 'SKIPPED' THEN 'DENIED' ELSE 'PENDING' END,
                   delivery_row.failure_code, NULL, delivery_row.trace_id, delivery_row.system_id,
                   delivery_row.tenant_id, delivery_row.created_at,
                   '模板 / 渠道', CONCAT(delivery_row.template_code, ' / ', delivery_row.channel),
                   '尝试次数', CAST(delivery_row.attempt_count AS CHAR)
              FROM un_event_message_delivery_log delivery_row
              LEFT JOIN un_plat_member member_row ON member_row.id=delivery_row.recipient_member_id
            UNION ALL
            SELECT CONCAT('TODO:', todo_row.id), 'TASK', 'TODO', todo_row.requested_action,
                   CAST(todo_row.actor_member_id AS CHAR), member_row.display_name,
                   todo_row.source_type, todo_row.source_id,
                   CASE WHEN todo_row.status='PROCESSING' THEN 'PENDING'
                        WHEN todo_row.result_code='SUCCESS' THEN 'SUCCESS'
                        WHEN todo_row.result_code='DENIED' THEN 'DENIED' ELSE 'FAILED' END,
                   CASE WHEN todo_row.result_code IN ('SUCCESS', 'DENIED') THEN NULL ELSE todo_row.result_code END,
                   todo_row.request_id, todo_row.trace_id, todo_row.system_id, todo_row.tenant_id,
                   todo_row.created_at, '待办动作', todo_row.requested_action,
                   '来源版本', CAST(todo_row.source_version AS CHAR)
              FROM un_todo_action_log todo_row
              LEFT JOIN un_plat_member member_row ON member_row.id=todo_row.actor_member_id
            UNION ALL
            SELECT CONCAT('JOB:', job_row.id), 'TASK', 'JOB', job_row.job_type,
                   CAST(job_row.requested_by AS CHAR), account_row.display_name,
                   job_row.owner_type, job_row.owner_id,
                   CASE job_row.status WHEN 'SUCCEEDED' THEN 'SUCCESS' WHEN 'FAILED' THEN 'FAILED'
                        WHEN 'CANCELLED' THEN 'DENIED' ELSE 'PENDING' END,
                   CASE WHEN job_row.status='FAILED' THEN 'JOB_FAILED' ELSE NULL END,
                   NULL, NULL, job_row.system_id, job_row.tenant_id, job_row.created_at,
                   '进度', CONCAT(job_row.progress_percent, '%'),
                   '尝试次数', CONCAT(job_row.attempt_count, ' / ', job_row.max_attempts)
              FROM un_sys_job job_row
              LEFT JOIN un_plat_account account_row ON account_row.id=job_row.requested_by
            UNION ALL
            SELECT CONCAT('SYSTEM_AI:', ai_row.event_family, ':', ai_row.id), 'AI', 'SYSTEM_AGENT',
                   ai_row.event_type, CAST(ai_row.actor_member_id AS CHAR), member_row.display_name,
                   ai_row.event_family, CAST(ai_row.object_id AS CHAR),
                   CASE WHEN ai_row.event_type IN ('SUCCEEDED') THEN 'SUCCESS'
                        WHEN ai_row.event_type IN ('REJECTED','EXPIRED','STALE','PERMISSION_DENIED') THEN 'DENIED'
                        WHEN ai_row.event_type IN ('PROPOSED','CONFIRMING','REJECTING','CLARIFICATION_REQUIRED') THEN 'PENDING'
                        ELSE 'FAILED' END,
                   CASE WHEN ai_row.event_type='SUCCEEDED' THEN NULL ELSE ai_row.result_code END,
                   ai_row.request_id, ai_row.trace_id, ai_row.system_id, ai_row.tenant_id,
                   ai_row.created_at, 'State', CONCAT(COALESCE(ai_row.from_state, '-'), ' -> ', ai_row.to_state),
                   'Revision', CAST(ai_row.revision AS CHAR)
              FROM (
                    SELECT id, 'CONFIRMATION' event_family, event_type, confirmation_id object_id,
                           actor_member_id, request_id, trace_id, result_code, from_state, to_state,
                           revision, system_id, tenant_id, created_at
                      FROM un_ai_agent_confirmation_event
                    UNION ALL
                    SELECT id, 'CONFIG_FIELD', event_type, proposal_id, actor_member_id,
                           request_id, trace_id, result_code, from_state, to_state, revision,
                           system_id, tenant_id, created_at
                      FROM un_ai_config_field_event
                    UNION ALL
                    SELECT id, 'CONFIG_ARTIFACT', event_type, proposal_id, actor_member_id,
                           request_id, trace_id, result_code, from_state, to_state, revision,
                           system_id, tenant_id, created_at
                      FROM un_ai_config_artifact_event
                    UNION ALL
                    SELECT id, 'WORK_DRAFT', event_type, proposal_id, actor_member_id,
                           request_id, trace_id, result_code, from_state, to_state, revision,
                           system_id, tenant_id, created_at
                      FROM un_ai_work_event
                    UNION ALL
                    SELECT id, 'GENERATED_DRAFT', event_type, proposal_id, actor_member_id,
                           request_id, trace_id, result_code, from_state, to_state, revision,
                           system_id, tenant_id, created_at
                      FROM un_ai_generated_draft_event
                    UNION ALL
                    SELECT id, 'FILL', event_type, proposal_id, actor_member_id,
                           request_id, trace_id, result_code, from_state, to_state, revision,
                           system_id, tenant_id, created_at
                      FROM un_ai_fill_event
                   ) ai_row
              LEFT JOIN un_plat_member member_row ON member_row.id=ai_row.actor_member_id
            UNION ALL
            SELECT CONCAT('PLATFORM_AI:', ai_row.id), 'AI', 'PLATFORM_AGENT', ai_row.event_type,
                   CAST(ai_row.account_id AS CHAR), account_row.display_name,
                   ai_row.aggregate_type, CAST(ai_row.aggregate_id AS CHAR),
                   CASE WHEN ai_row.result_code REGEXP 'SUCCESS|COMPLETED|CONFIRMED|PUBLISHED' THEN 'SUCCESS'
                        WHEN ai_row.result_code REGEXP 'DENIED|REJECTED|CANCELLED' THEN 'DENIED'
                        WHEN ai_row.result_code REGEXP 'PENDING|RUNNING|PROPOSED' THEN 'PENDING' ELSE 'FAILED' END,
                   CASE WHEN ai_row.result_code REGEXP 'SUCCESS|COMPLETED|CONFIRMED|PUBLISHED' THEN NULL ELSE ai_row.result_code END,
                   ai_row.request_id, ai_row.trace_id, NULL, NULL, ai_row.created_at,
                   'Agent 事件', ai_row.event_type, '结果代码', ai_row.result_code
              FROM un_platform_ai_audit_event ai_row
              LEFT JOIN un_plat_account account_row ON account_row.id=ai_row.account_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcUnifiedAuditQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UnifiedAuditModels.Page search(UnifiedAuditModels.Scope scope, UnifiedAuditModels.Query raw) {
        var query = normalize(raw);
        var predicate = new StringBuilder(scope.type() == ContextType.PLATFORM
                ? " WHERE system_id IS NULL AND tenant_id IS NULL"
                : " WHERE system_id=? AND tenant_id=?");
        var parameters = new ArrayList<Object>();
        if (scope.type() == ContextType.SYSTEM) {
            parameters.add(scope.systemId());
            parameters.add(scope.tenantId());
        }
        exact(predicate, parameters, "request_id", query.requestId());
        exact(predicate, parameters, "trace_id", query.traceId());
        contains(predicate, parameters, "CONCAT_WS(' ', actor_id, actor_name)", query.actor());
        contains(predicate, parameters, "CONCAT_WS(' ', object_type, object_id, event_type)", query.object());
        if (!"ALL".equals(query.result())) exact(predicate, parameters, "result", query.result());
        if (!"ALL".equals(query.category())) exact(predicate, parameters, "category", query.category());
        if (query.from() != null) {
            predicate.append(" AND occurred_at>=?");
            parameters.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            predicate.append(" AND occurred_at<=?");
            parameters.add(Timestamp.from(query.to()));
        }

        var wrapped = " FROM (" + UNION_SQL + ") unified" + predicate;
        var total = jdbc.queryForObject("SELECT COUNT(*)" + wrapped, Long.class, parameters.toArray());
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.size());
        pageParameters.add(Math.multiplyExact(query.page() - 1, query.size()));
        var items = jdbc.query("SELECT *" + wrapped + " ORDER BY occurred_at DESC,log_id DESC LIMIT ? OFFSET ?",
                (row, ignored) -> {
                    var details = new LinkedHashMap<String, String>();
                    addDetail(details, row.getString("detail_one_label"), row.getString("detail_one_value"));
                    addDetail(details, row.getString("detail_two_label"), row.getString("detail_two_value"));
                    return new UnifiedAuditModels.Item(
                            row.getString("log_id"), row.getString("category"), row.getString("source"),
                            row.getString("event_type"), row.getString("actor_id"), row.getString("actor_name"),
                            row.getString("object_type"), row.getString("object_id"), row.getString("result"),
                            row.getString("failure_code"), row.getString("request_id"), row.getString("trace_id"),
                            string(row.getObject("system_id")), string(row.getObject("tenant_id")),
                            row.getTimestamp("occurred_at").toInstant(), details);
                }, pageParameters.toArray());
        return new UnifiedAuditModels.Page(items, query.page(), query.size(), total == null ? 0 : total);
    }

    private static UnifiedAuditModels.Query normalize(UnifiedAuditModels.Query query) {
        if (query == null) throw invalid("query is required");
        if (query.page() < 1 || query.page() > 1_000 || query.size() < 1 || query.size() > 100) {
            throw invalid("page must be 1..1000 and size must be 1..100");
        }
        var category = choice(query.category(), "ALL", CATEGORIES, "category");
        var result = choice(query.result(), "ALL", RESULTS, "result");
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw invalid("from must not be after to");
        }
        return new UnifiedAuditModels.Query(
                bounded(query.requestId(), 128, "requestId"), bounded(query.traceId(), 128, "traceId"),
                bounded(query.actor(), 160, "actor"), bounded(query.object(), 200, "object"),
                result, category, query.from(), query.to(), query.page(), query.size());
    }

    private static void exact(StringBuilder sql, List<Object> values, String column, String value) {
        if (value == null) return;
        sql.append(" AND ").append(column).append("=?");
        values.add(value);
    }

    private static void contains(StringBuilder sql, List<Object> values, String column, String value) {
        if (value == null) return;
        sql.append(" AND LOWER(").append(column).append(") LIKE ? ESCAPE '!'");
        values.add("%" + value.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    private static String bounded(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.trim();
        if (normalized.length() > maximum) throw invalid(field + " is too long");
        return normalized;
    }

    private static String choice(String value, String fallback, Set<String> allowed, String field) {
        var normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw invalid(field + " is invalid");
        return normalized;
    }

    private static void addDetail(LinkedHashMap<String, String> details, String label, String value) {
        if (label != null && value != null && !value.isBlank()) details.put(label, value);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("AUDIT_QUERY_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
