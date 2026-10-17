package com.unique.unexamine.audit.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.mapper.AuditEventMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditQueryService {
    private final AuditEventMapper auditEventMapper;
    private final AuditEventRedactor redactor;

    public AuditQueryService(AuditEventMapper auditEventMapper, AuditEventRedactor redactor) {
        this.auditEventMapper = auditEventMapper;
        this.redactor = redactor;
    }

    @Transactional(readOnly = true)
    public AuditEventList list(
            AuthenticatedContext context,
            int pageNumber,
            int pageSize,
            String traceId,
            String requestId,
            Long actorAccountId,
            Long memberId,
            String eventCode,
            String objectType,
            String objectId,
            String resultCode,
            LocalDateTime occurredFrom,
            LocalDateTime occurredTo) {
        requireSystemContext(context);
        return query(context, false, pageNumber, pageSize, traceId, requestId, actorAccountId, memberId,
                eventCode, objectType, objectId, resultCode, occurredFrom, occurredTo);
    }

    /** 平台后台仅查询本平台事件，不能把系统或租户审计混入平台结果。 */
    @Transactional(readOnly = true)
    public AuditEventList listPlatform(
            AuthenticatedContext context, int pageNumber, int pageSize, String traceId, String requestId,
            Long actorAccountId, String eventCode, String objectType, String objectId, String resultCode,
            LocalDateTime occurredFrom, LocalDateTime occurredTo) {
        if (context.platformId() == null || context.systemId() != null) {
            throw new DomainException("PLATFORM_CONTEXT_REQUIRED", "请先进入平台", HttpStatus.CONFLICT);
        }
        return query(context, true, pageNumber, pageSize, traceId, requestId, actorAccountId, null,
                eventCode, objectType, objectId, resultCode, occurredFrom, occurredTo);
    }

    /** 两种审计工作区共用筛选和脱敏，只在所属范围上分流。 */
    private AuditEventList query(
            AuthenticatedContext context, boolean platform, int pageNumber, int pageSize,
            String traceId, String requestId, Long actorAccountId, Long memberId,
            String eventCode, String objectType, String objectId, String resultCode,
            LocalDateTime occurredFrom, LocalDateTime occurredTo) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new DomainException("PAGINATION_INVALID", "页码必须大于 0，每页数量不能超过 200", HttpStatus.BAD_REQUEST);
        }
        LambdaQueryWrapper<AuditEvent> query = new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getContextType, platform ? "PLATFORM" : "SYSTEM")
                .eq(platform, AuditEvent::getPlatformId, context.platformId())
                .isNull(platform, AuditEvent::getSystemId)
                .isNull(platform, AuditEvent::getTenantId)
                .eq(!platform, AuditEvent::getSystemId, context.systemId())
                .eq(!platform, AuditEvent::getTenantId, context.tenantId())
                .eq(hasText(traceId), AuditEvent::getTraceId, strip(traceId))
                .eq(hasText(requestId), AuditEvent::getRequestId, strip(requestId))
                .eq(actorAccountId != null, AuditEvent::getActorAccountId, actorAccountId)
                .eq(memberId != null, AuditEvent::getMemberId, memberId)
                .eq(hasText(eventCode), AuditEvent::getEventCode, strip(eventCode))
                .eq(hasText(objectType), AuditEvent::getObjectType, strip(objectType))
                .eq(hasText(objectId), AuditEvent::getObjectId, strip(objectId))
                .eq(hasText(resultCode), AuditEvent::getResultCode, strip(resultCode))
                .ge(occurredFrom != null, AuditEvent::getOccurTime, occurredFrom)
                .le(occurredTo != null, AuditEvent::getOccurTime, occurredTo)
                .orderByDesc(AuditEvent::getOccurTime, AuditEvent::getId);
        Page<AuditEvent> page = auditEventMapper.selectPage(new Page<>(pageNumber, pageSize), query);
        redactor.redact(context, page.getRecords());
        return new AuditEventList(page.getRecords(), page.getTotal(), pageNumber, pageSize);
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
