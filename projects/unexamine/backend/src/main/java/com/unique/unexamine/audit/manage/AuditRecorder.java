package com.unique.unexamine.audit.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditRecorder {
    private final AuditEventBaseService auditService;
    private final ObjectMapper objectMapper;

    public AuditRecorder(AuditEventBaseService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String traceId, Long actorAccountId, String eventCode, String failureCode, Map<String, ?> detail) {
        record(traceId, actorAccountId, null, null, null, eventCode, null, null, failureCode, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String traceId,
            Long actorAccountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            String eventCode,
            String objectType,
            String objectId,
            String failureCode,
            Map<String, ?> detail) {
        record(traceId, actorAccountId, systemId, tenantId, memberId, eventCode, objectType, objectId, failureCode, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermissionDenied(
            String traceId,
            Long actorAccountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            String permissionCode,
            Map<String, ?> permissionSnapshot) {
        AuditEvent event = new AuditEvent();
        event.setTraceId(traceId);
        event.setActorAccountId(actorAccountId);
        event.setSystemId(systemId);
        event.setTenantId(tenantId);
        event.setMemberId(memberId);
        event.setEventCode("PERMISSION_CHECK");
        event.setObjectType("PERMISSION");
        event.setObjectId(permissionCode);
        event.setResultCode("PERMISSION_DENIED");
        event.setPermissionSnapshot(toJson(permissionSnapshot));
        event.setDetailJson(toJson(Map.of("permissionCode", permissionCode)));
        auditService.insert(event);
    }

    public void record(
            String traceId,
            Long actorAccountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            String eventCode,
            String objectType,
            String objectId,
            String resultCode,
            Map<String, ?> detail) {
        AuditEvent event = new AuditEvent();
        event.setTraceId(traceId);
        event.setActorAccountId(actorAccountId);
        event.setSystemId(systemId);
        event.setTenantId(tenantId);
        event.setMemberId(memberId);
        event.setEventCode(eventCode);
        event.setObjectType(objectType);
        event.setObjectId(objectId);
        event.setResultCode(resultCode);
        event.setDetailJson(toJson(detail));
        auditService.insert(event);
    }

    private String toJson(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize audit detail", exception);
        }
    }
}
