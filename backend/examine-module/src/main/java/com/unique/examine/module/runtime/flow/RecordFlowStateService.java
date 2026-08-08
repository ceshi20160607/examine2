package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class RecordFlowStateService {
    private final RuntimeRecordAccessFacade access;
    private final RecordFlowProjectionStore projections;

    public RecordFlowStateService(
            RuntimeRecordAccessFacade access,
            RecordFlowProjectionStore projections
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    @Transactional(readOnly = true)
    public RecordFlowViews.RecordFlowState find(
            RuntimeSession session,
            String moduleCode,
            long recordId
    ) {
        var tenantId = requiredTenant(session);
        access.requireView(new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                session.systemId(),
                tenantId,
                session.memberId(),
                session.permissions(),
                moduleCode,
                recordId));
        return projections.findByRecord(session.systemId(), tenantId, recordId)
                .map(RecordFlowProjectionStore.Projection::toState)
                .map(RecordFlowViews.RecordFlowState::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RecordFlowViews.RecordFlowState> findAll(
            RuntimeSession session,
            String moduleCode,
            long recordId
    ) {
        var tenantId = requiredTenant(session);
        access.requireView(new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                session.systemId(),
                tenantId,
                session.memberId(),
                session.permissions(),
                moduleCode,
                recordId));
        var states = new ArrayList<RecordFlowViews.RecordFlowState>();
        projections.findByRecord(session.systemId(), tenantId, recordId)
                .map(RecordFlowProjectionStore.Projection::toState)
                .map(RecordFlowViews.RecordFlowState::from)
                .ifPresent(states::add);
        projections.findAdditionalByRecord(session.systemId(), tenantId, recordId).stream()
                .map(RecordFlowProjectionStore.Projection::toState)
                .map(RecordFlowViews.RecordFlowState::from)
                .forEach(states::add);
        return List.copyOf(states);
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "Tenant context is required",
                    HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }
}
