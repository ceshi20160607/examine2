package com.unique.unexamine.audit.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.entity.AuditFieldChange;
import com.unique.unexamine.audit.base.entity.AuditRetentionMarker;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.base.service.AuditFieldChangeBaseService;
import com.unique.unexamine.audit.base.service.AuditRetentionMarkerBaseService;
import com.unique.unexamine.audit.manage.AuditGovernanceModels.CreateRetentionMarkerRequest;
import com.unique.unexamine.audit.manage.AuditGovernanceModels.RetentionPreflightRequest;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditGovernanceService {
    private final AuditEventBaseService eventService;
    private final AuditFieldChangeBaseService fieldChangeService;
    private final AuditRetentionMarkerBaseService markerService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AuditGovernanceService(AuditEventBaseService eventService,
                                  AuditFieldChangeBaseService fieldChangeService,
                                  AuditRetentionMarkerBaseService markerService,
                                  PermissionChecker permissionChecker,
                                  AuditRecorder auditRecorder,
                                  ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.fieldChangeService = fieldChangeService;
        this.markerService = markerService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditGovernanceModels.EventDetail detail(AuthenticatedContext context, Long eventId) {
        requireSystemContext(context);
        AuditEvent event = requireEvent(context, eventId);
        boolean sensitiveVisible = permissionChecker.allows(context, "AUDIT", "EVENT", "VIEW_SENSITIVE");
        List<AuditGovernanceModels.FieldChangeView> fields = fieldChangeService.selectList(
                        Wrappers.<AuditFieldChange>lambdaQuery().eq(AuditFieldChange::getAuditEventId, eventId))
                .stream().map(field -> fieldView(field, sensitiveVisible)).toList();
        return new AuditGovernanceModels.EventDetail(event, fields, sensitiveVisible);
    }

    @Transactional(readOnly = true)
    public AuditGovernanceModels.RetentionPreflight retentionPreflight(
            AuthenticatedContext context, RetentionPreflightRequest input) {
        requireSystemContext(context);
        return inspect(context, input.objectType().strip(), input.objectId().strip());
    }

    @Transactional
    public AuditGovernanceModels.RetentionMarkerView retainPermanently(
            AuthenticatedContext context, CreateRetentionMarkerRequest input, String traceId) {
        requireSystemContext(context);
        String objectType = input.objectType().strip();
        String objectId = input.objectId().strip();
        AuditGovernanceModels.RetentionPreflight preflight = inspect(context, objectType, objectId);
        if (!preflight.allowed()) {
            throw new DomainException("AUDIT_RETENTION_PREFLIGHT_FAILED",
                    "审计永久保留检查未通过：" + String.join("；", preflight.blockers()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!input.referenceImpactConfirmed()) {
            throw new DomainException("AUDIT_REFERENCE_IMPACT_UNCONFIRMED", "必须先确认引用影响",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<AuditEvent> events = events(context, objectType, objectId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("approvalReference", input.approvalReference().strip());
        snapshot.put("auditEventIds", events.stream().map(AuditEvent::getId).toList());
        snapshot.put("traceIds", events.stream().map(AuditEvent::getTraceId).distinct().toList());
        snapshot.put("eventCount", events.size());
        snapshot.put("fieldChangeCount", preflight.fieldChangeCount());
        snapshot.put("referenceImpactConfirmed", true);
        String snapshotJson = toJson(snapshot);
        AuditRetentionMarker marker = new AuditRetentionMarker();
        marker.setContextType("SYSTEM");
        marker.setSystemId(context.systemId());
        marker.setTenantId(context.tenantId());
        marker.setObjectType(objectType);
        marker.setObjectId(objectId);
        marker.setBusinessKey(input.businessKey());
        marker.setSnapshotHash(sha256(snapshotJson));
        marker.setSnapshotJson(snapshotJson);
        marker.setPurgeReason(input.reason().strip());
        marker.setPurgedByAccountId(context.accountId());
        marker.setPurgedAt(LocalDateTime.now());
        markerService.insert(marker);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "AUDIT_PERMANENT_RETENTION_MARKED", "AUDIT_RETENTION_MARKER", marker.getId().toString(), "SUCCESS",
                Map.of("objectType", objectType, "objectId", objectId,
                        "approvalReference", input.approvalReference(), "snapshotHash", marker.getSnapshotHash()));
        return new AuditGovernanceModels.RetentionMarkerView(marker.getId(), objectType, objectId,
                marker.getBusinessKey(), marker.getSnapshotHash(), marker.getPurgeReason(),
                marker.getPurgedByAccountId(), marker.getPurgedAt());
    }

    private AuditGovernanceModels.RetentionPreflight inspect(
            AuthenticatedContext context, String objectType, String objectId) {
        List<String> blockers = new ArrayList<>();
        if (objectType.contains("*") || objectId.contains("*") || objectType.length() < 2 || objectId.length() < 1) {
            blockers.add("永久保留范围必须是单个精确对象，不能使用通配或过宽范围");
        }
        List<AuditEvent> events = events(context, objectType, objectId);
        if (events.isEmpty()) blockers.add("目标对象没有可保留的审计事件");
        boolean existing = markerService.selectList(Wrappers.<AuditRetentionMarker>lambdaQuery()
                        .eq(AuditRetentionMarker::getContextType, "SYSTEM")
                        .eq(AuditRetentionMarker::getSystemId, context.systemId())
                        .eq(AuditRetentionMarker::getTenantId, context.tenantId())
                        .eq(AuditRetentionMarker::getObjectType, objectType)
                        .eq(AuditRetentionMarker::getObjectId, objectId))
                .stream().findAny().isPresent();
        if (existing) blockers.add("目标对象已经存在不可变永久保留标记");
        List<Long> eventIds = events.stream().map(AuditEvent::getId).toList();
        long fields = eventIds.isEmpty() ? 0 : fieldChangeService.selectList(
                Wrappers.<AuditFieldChange>lambdaQuery().in(AuditFieldChange::getAuditEventId, eventIds)).size();
        return new AuditGovernanceModels.RetentionPreflight(objectType, objectId, blockers.isEmpty(),
                events.size(), fields, existing, true, true, blockers);
    }

    private List<AuditEvent> events(AuthenticatedContext context, String objectType, String objectId) {
        return eventService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                .eq(AuditEvent::getSystemId, context.systemId())
                .eq(AuditEvent::getTenantId, context.tenantId())
                .eq(AuditEvent::getObjectType, objectType)
                .eq(AuditEvent::getObjectId, objectId)
                .orderByAsc(AuditEvent::getId));
    }

    private AuditEvent requireEvent(AuthenticatedContext context, Long eventId) {
        AuditEvent event = eventService.selectById(eventId);
        if (event == null || !context.systemId().equals(event.getSystemId())
                || !context.tenantId().equals(event.getTenantId())) {
            throw new DomainException("AUDIT_EVENT_NOT_FOUND", "当前上下文不存在该审计事件", HttpStatus.NOT_FOUND);
        }
        return event;
    }

    private AuditGovernanceModels.FieldChangeView fieldView(AuditFieldChange field, boolean sensitiveVisible) {
        boolean masked = "SENSITIVE".equals(field.getSensitivity()) && !sensitiveVisible;
        return new AuditGovernanceModels.FieldChangeView(field.getId(), field.getFieldCode(), field.getValueType(),
                masked ? "\"***\"" : field.getBeforeValueJson(), masked ? "\"***\"" : field.getAfterValueJson(),
                field.getSensitivity(), masked);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize audit retention snapshot", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash audit retention snapshot", exception);
        }
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
    }
}
