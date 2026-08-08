package com.unique.examine.flow.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Flow-owned read boundary for the externally visible instance status contract.
 */
@Service
public class OpenApiFlowStatusService {
    private final FlowRequestServiceFactory services;
    private final RuntimeRecordAccessFacade recordAccess;

    public OpenApiFlowStatusService(
            FlowRequestServiceFactory services,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
    }

    @Transactional(readOnly = true)
    public FlowViews.OpenApiInstanceStatus status(
            FlowSession session,
            long instanceId
    ) {
        requireRead(session);
        if (instanceId <= 0) {
            throw new IllegalArgumentException("Flow instance ID must be positive");
        }

        var instance = services
                .forTenant(session.systemId(), session.tenantId())
                .instance(instanceId);
        var binding = instance.recordBinding();
        if (binding != null) {
            recordAccess.requireView(
                    new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                            session.systemId(),
                            session.tenantId(),
                            session.memberId(),
                            session.permissions(),
                            binding.moduleCode(),
                            binding.recordId()
                    ));
        }
        return FlowViews.OpenApiInstanceStatus.from(instance);
    }

    private static void requireRead(FlowSession session) {
        if (session == null
                || !session.permissions().contains(FlowPermissions.INSTANCE_READ)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The authenticated member does not have the required flow permission",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
