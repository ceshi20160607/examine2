package com.unique.examine.module.runtime.favorite;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FavoriteMutationSupport {
    private final RecordMutationSupport idempotency;
    private final OperationAuditFacade audit;
    private final OutboxFacade outbox;

    public FavoriteMutationSupport(
            RecordMutationSupport idempotency,
            OperationAuditFacade audit,
            OutboxFacade outbox
    ) {
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
    }

    public <T> T idempotent(
            RuntimeSession session,
            String method,
            String path,
            String key,
            Object request,
            Class<T> responseType,
            int successStatus,
            RecordMutationSupport.Mutation<T> mutation
    ) {
        var scope = "favorite:" + session.systemId() + ":" + session.tenantId() + ":"
                + session.memberId() + ":" + method + ":" + path;
        return idempotency.idempotent(scope, key, request, responseType, successStatus, mutation);
    }

    public void changed(
            RuntimeSession session,
            long favoriteId,
            long version,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        var aggregate = new AggregateRef("RUNTIME_FAVORITE", Long.toString(favoriteId));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, action, before, after, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_FAVORITE_CHANGED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-favorite:" + session.systemId() + ":" + favoriteId + ":" + version + ":" + action,
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "tenantId", Long.toString(session.tenantId()),
                        "memberId", Long.toString(session.memberId()),
                        "favoriteId", Long.toString(favoriteId),
                        "version", Long.toString(version),
                        "action", action
                ),
                traceId
        ));
    }
}
