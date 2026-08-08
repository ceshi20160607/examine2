package com.unique.examine.core.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.base.entity.Operation;
import com.unique.examine.core.base.entity.Security;
import com.unique.examine.core.base.mapper.CoreOperationMapper;
import com.unique.examine.core.base.mapper.CoreSecurityMapper;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AuditService implements AuditFacade, OperationAuditFacade {
    private final CoreSecurityMapper securityMapper;
    private final CoreOperationMapper operationMapper;
    private final IdService idService;
    private final ObjectMapper objectMapper;

    public AuditService(
            CoreSecurityMapper securityMapper,
            CoreOperationMapper operationMapper,
            IdService idService,
            ObjectMapper objectMapper
    ) {
        this.securityMapper = securityMapper;
        this.operationMapper = operationMapper;
        this.idService = idService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recordSecurity(AuditEvent event) {
        var entity = new Security();
        entity.setId(idService.nextId());
        entity.setEventType(event.eventType());
        entity.setAccountId(event.accountId());
        entity.setAccountHint(event.accountHint());
        entity.setSystemId(event.systemId());
        entity.setTenantId(event.tenantId());
        entity.setSourceType(event.sourceType());
        entity.setRemoteAddress(event.remoteAddress());
        entity.setUserAgent(event.userAgent());
        entity.setRequestId(event.requestId());
        entity.setTraceId(event.traceId());
        entity.setResult(event.result());
        entity.setFailureCode(event.failureCode());
        entity.setDetailJson(event.detailJson());
        entity.setCreatedAt(LocalDateTime.now());
        securityMapper.insert(entity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(OperationAudit audit) {
        insertOperation(audit, OperationAudit.Result.SUCCESS);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(OperationAudit audit) {
        insertOperation(audit, OperationAudit.Result.DENIED);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(OperationAudit audit) {
        insertOperation(audit, OperationAudit.Result.FAILED);
    }

    private void insertOperation(OperationAudit audit, OperationAudit.Result expectedResult) {
        Objects.requireNonNull(audit, "audit is required");
        if (audit.result() != expectedResult) {
            throw new IllegalArgumentException(
                    "Expected audit result " + expectedResult + " but was " + audit.result()
            );
        }

        var entity = new Operation();
        entity.setId(idService.nextId());
        entity.setOperationType(audit.action());
        entity.setAggregateType(audit.aggregate().type());
        entity.setAggregateId(audit.aggregate().id());
        entity.setActorAccountId(audit.actor().accountId());
        entity.setContextType(audit.context().type().name());
        entity.setSystemId(audit.context().systemId());
        entity.setTenantId(audit.context().tenantId());
        entity.setSourceType(audit.actor().sourceType());
        entity.setRequestId(audit.requestId());
        entity.setTraceId(audit.traceId());
        entity.setResult(audit.result().name());
        entity.setBeforeJson(writeJson(audit.before(), "before"));
        entity.setAfterJson(writeJson(audit.after(), "after"));
        entity.setFailureCode(audit.failure() == null ? null : audit.failure().code());
        entity.setCreatedAt(LocalDateTime.now());
        operationMapper.insert(entity);
    }

    private String writeJson(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Operation audit " + field + " must be JSON serializable", exception);
        }
    }
}
