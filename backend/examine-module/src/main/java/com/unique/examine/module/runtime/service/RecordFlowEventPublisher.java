package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves the Flow trigger adapter lazily so record runtime construction does not create a
 * Module-to-Flow-to-Module eager bean cycle.
 */
@Component
public class RecordFlowEventPublisher {
    private final ObjectProvider<RuntimeRecordFlowTriggerFacade> triggers;

    public RecordFlowEventPublisher(
            ObjectProvider<RuntimeRecordFlowTriggerFacade> triggers
    ) {
        this.triggers = Objects.requireNonNull(triggers, "triggers");
    }

    public RuntimeRecordFlowTriggerFacade.TriggerResult publish(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long recordVersion,
            String businessKey,
            RuntimeRecordFlowTriggerFacade.TriggerEvent event,
            Map<String, String> recordValuesJson
    ) {
        var tenantId = requiredTenant(session);
        var eventKey = "record:%d:%d:%s:%d:%d:%s".formatted(
                session.systemId(),
                tenantId,
                moduleCode,
                recordId,
                recordVersion,
                event.name());
        return triggers.getObject().trigger(new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                session.systemId(),
                tenantId,
                session.memberId(),
                session.permissions(),
                moduleCode,
                recordId,
                recordVersion,
                businessKey,
                event,
                eventKey,
                recordValuesJson));
    }

    public RuntimeRecordFlowTriggerFacade.TriggerResult publish(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long recordVersion,
            String businessKey,
            RuntimeRecordFlowTriggerFacade.TriggerEvent event
    ) {
        return publish(
                session,
                moduleCode,
                recordId,
                recordVersion,
                businessKey,
                event,
                Map.of());
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
