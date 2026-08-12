package com.unique.examine.plat.vnext.manage.auth;

import com.unique.examine.core.base.entity.Security;
import com.unique.examine.core.base.service.ISecurityService;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class VNextSecurityAuditService {
    private final ISecurityService securityService;
    private final IdService idService;

    public VNextSecurityAuditService(ISecurityService securityService, IdService idService) {
        this.securityService = securityService;
        this.idService = idService;
    }

    public void record(
            String eventType,
            Long accountId,
            String accountHint,
            String result,
            String failureCode,
            ClientRequest client
    ) {
        record(eventType, accountId, accountHint, null, null, result, failureCode, client);
    }

    public void record(
            String eventType,
            Long accountId,
            String accountHint,
            Long systemId,
            Long tenantId,
            String result,
            String failureCode,
            ClientRequest client
    ) {
        record(
                eventType, accountId, accountHint, systemId, tenantId,
                result, failureCode, client, LocalDateTime.now()
        );
    }

    public void record(
            String eventType,
            Long accountId,
            String accountHint,
            Long systemId,
            Long tenantId,
            String result,
            String failureCode,
            ClientRequest client,
            LocalDateTime occurredAt
    ) {
        var security = new Security();
        security.setId(idService.nextId());
        security.setEventType(eventType);
        security.setAccountId(accountId);
        security.setAccountHint(accountHint);
        security.setSystemId(systemId);
        security.setTenantId(tenantId);
        security.setSourceType("WEB");
        security.setRemoteAddress(client.remoteAddress());
        security.setUserAgent(client.userAgent());
        security.setRequestId(client.requestId());
        security.setTraceId(client.traceId());
        security.setResult(result);
        security.setFailureCode(failureCode);
        security.setDetailJson("{}");
        security.setCreatedAt(occurredAt);
        if (!securityService.save(security)) {
            throw new IllegalStateException("Security audit persistence was rejected");
        }
    }
}
