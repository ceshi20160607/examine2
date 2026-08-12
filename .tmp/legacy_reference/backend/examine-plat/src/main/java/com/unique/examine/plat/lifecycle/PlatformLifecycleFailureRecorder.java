package com.unique.examine.plat.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.SystemAdminMutationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Component
public class PlatformLifecycleFailureRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final SystemAdminMutationSupport mutations;

    public PlatformLifecycleFailureRecorder(
            JdbcTemplate jdbc, ObjectMapper json, IdService ids, SystemAdminMutationSupport mutations) {
        this.jdbc = jdbc;
        this.json = json;
        this.ids = ids;
        this.mutations = mutations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuthenticatedSession session, long systemId, long sourceTenantId, String targetTenantId,
            String planOperationId, String type, String failureCode, ClientRequest request) {
        mutations.failed(session, systemId, "TENANT_LIFECYCLE",
                planOperationId == null ? "unknown" : planOperationId,
                "TENANT_" + type + "_FAILED", failureCode, request);
        var planId = positive(planOperationId);
        if (planId == null) return;
        var plans = jdbc.queryForList("SELECT target_tenant_id,plan_fingerprint,database_migration_version,"
                        + "payload_row_count,payload_size_bytes FROM un_plat_tenant_lifecycle_operation "
                        + "WHERE id=? AND system_id=? "
                        + "AND source_tenant_id=? AND operation_type=?",
                planId, systemId, sourceTenantId, type + "_PREVIEW");
        if (plans.size() != 1) return;
        var plan = plans.getFirst();
        var targetId = "MIGRATION".equals(type)
                ? ((Number) plan.get("target_tenant_id")).longValue() : null;
        var now = LocalDateTime.now();
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("failureCode", failureCode);
        evidence.put("requestedTargetTenantId", targetTenantId);
        evidence.put("compensation", "TRANSACTIONAL_ROLLBACK");
        evidence.put("retryableWithSameIdempotencyKey", true);
        var evidenceJson = write(evidence);
        jdbc.update("""
                INSERT INTO un_plat_tenant_lifecycle_operation(
                  id,system_id,source_tenant_id,target_tenant_id,plan_operation_id,operation_type,status,reason,
                  snapshot_json,result_json,snapshot_checksum,plan_fingerprint,confirmation_token_hash,
                  expires_at,consumed_at,payload_ciphertext,payload_ciphertext_sha256,
                  payload_plaintext_sha256,encryption_key_ref,encryption_key_version,payload_schema_version,
                  database_migration_version,payload_row_count,payload_size_bytes,requested_at,started_at,
                  finished_at,requested_by,request_id,trace_id,version)
                VALUES(?,?,?,?,?,?,'FAILED','failed execution attempt',?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,
                  1,?,?,?,?,?,?,?,?,?,0)
                """, ids.nextId(), systemId, sourceTenantId, targetId, planId, type,
                evidenceJson, evidenceJson, SystemAdminMutationSupport.sha256(evidenceJson),
                String.valueOf(plan.get("plan_fingerprint")), String.valueOf(plan.get("database_migration_version")),
                ((Number) plan.get("payload_row_count")).longValue(),
                ((Number) plan.get("payload_size_bytes")).longValue(),
                now, now, now, session.accountId(), request.requestId(), request.traceId());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("failure evidence serialization failed", failure); }
    }

    private static Long positive(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception invalid) {
            return null;
        }
    }
}
