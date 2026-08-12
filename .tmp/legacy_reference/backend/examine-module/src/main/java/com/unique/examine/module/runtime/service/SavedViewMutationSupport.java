package com.unique.examine.module.runtime.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SavedViewMutationSupport {
    private final RecordMutationSupport idempotency;
    private final OperationAuditFacade audit;
    private final OutboxFacade outbox;

    public SavedViewMutationSupport(
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
        var scope = "saved-view:" + session.systemId() + ":" + session.tenantId() + ":"
                + session.memberId() + ":" + method + ":" + path;
        return idempotency.idempotent(scope, key, request, responseType, successStatus, mutation);
    }

    public void changed(
            RuntimeSession session,
            long viewId,
            long version,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        var aggregate = new AggregateRef("RUNTIME_SAVED_VIEW", Long.toString(viewId));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate, action, before, after, requestId, traceId
        ));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_SAVED_VIEW_CHANGED", 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-saved-view:" + session.systemId() + ":" + viewId + ":" + version + ":" + action,
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "tenantId", Long.toString(session.tenantId()),
                        "memberId", Long.toString(session.memberId()),
                        "viewId", Long.toString(viewId),
                        "version", Long.toString(version),
                        "action", action
                ),
                traceId
        ));
    }
}
