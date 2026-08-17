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

@Service
public class AuditQueryService {
    private final AuditEventMapper auditEventMapper;

    public AuditQueryService(AuditEventMapper auditEventMapper) {
        this.auditEventMapper = auditEventMapper;
    }

    @Transactional(readOnly = true)
    public AuditEventList list(
            AuthenticatedContext context,
            int pageNumber,
            int pageSize,
            String traceId,
            String eventCode,
            String objectType,
            String objectId,
            String resultCode) {
        requireSystemContext(context);
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new DomainException("PAGINATION_INVALID", "页码必须大于 0，每页数量不能超过 200", HttpStatus.BAD_REQUEST);
        }
        LambdaQueryWrapper<AuditEvent> query = new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getSystemId, context.systemId())
                .eq(AuditEvent::getTenantId, context.tenantId())
                .eq(hasText(traceId), AuditEvent::getTraceId, strip(traceId))
                .eq(hasText(eventCode), AuditEvent::getEventCode, strip(eventCode))
                .eq(hasText(objectType), AuditEvent::getObjectType, strip(objectType))
                .eq(hasText(objectId), AuditEvent::getObjectId, strip(objectId))
                .eq(hasText(resultCode), AuditEvent::getResultCode, strip(resultCode))
                .orderByDesc(AuditEvent::getOccurredAt, AuditEvent::getId);
        Page<AuditEvent> page = auditEventMapper.selectPage(new Page<>(pageNumber, pageSize), query);
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
