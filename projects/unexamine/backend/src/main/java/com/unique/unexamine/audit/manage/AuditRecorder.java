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
        event.setRequestId(traceId);
        event.setContextType(systemId == null ? "PLATFORM" : "SYSTEM");
        event.setActorAccountId(actorAccountId);
        event.setSystemId(systemId);
        event.setTenantId(tenantId);
        event.setMemberId(memberId);
        event.setEventCategory("AUTHORIZATION");
        event.setEventCode("PERMISSION_CHECK");
        event.setObjectType("PERMISSION");
        event.setObjectId(permissionCode);
        event.setResultCode("PERMISSION_DENIED");
        event.setPermissionSnapshot(toJson(permissionSnapshot));
        event.setDetailJson(toJson(Map.of("permissionCode", permissionCode)));
        auditService.insert(event);
    }

    public Long record(
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
        return recordWithPermissionSnapshot(traceId, actorAccountId, systemId, tenantId, memberId, eventCode,
                objectType, objectId, resultCode, null, detail);
    }

    public Long recordWithPermissionSnapshot(
            String traceId,
            Long actorAccountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            String eventCode,
            String objectType,
            String objectId,
            String resultCode,
            Map<String, ?> permissionSnapshot,
            Map<String, ?> detail) {
        AuditEvent event = new AuditEvent();
        event.setTraceId(traceId);
        event.setRequestId(traceId);
        event.setContextType(systemId == null ? "PLATFORM" : "SYSTEM");
        event.setActorAccountId(actorAccountId);
        event.setSystemId(systemId);
        event.setTenantId(tenantId);
        event.setMemberId(memberId);
        event.setEventCategory(categoryOf(eventCode));
        event.setEventCode(eventCode);
        event.setObjectType(objectType);
        event.setObjectId(objectId);
        event.setResultCode(resultCode);
        if (permissionSnapshot != null) {
            event.setPermissionSnapshot(toJson(permissionSnapshot));
        }
        event.setDetailJson(toJson(detail));
        auditService.insert(event);
        return event.getId();
    }

    private String categoryOf(String eventCode) {
        if (eventCode == null) {
            return "BUSINESS";
        }
        if (eventCode.contains("LOGIN") || eventCode.contains("SESSION") || eventCode.contains("PASSWORD")) {
            return "SECURITY";
        }
        if (eventCode.contains("PERMISSION") || eventCode.contains("ROLE")) {
            return "AUTHORIZATION";
        }
        if (eventCode.contains("CONFIG") || eventCode.contains("MODULE") || eventCode.contains("PUBLISH")) {
            return "CONFIGURATION";
        }
        return "BUSINESS";
    }

    private String toJson(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize audit detail", exception);
        }
    }
}
