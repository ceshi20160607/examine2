package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowInstanceHistoryReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowCompensationRuntimeService;
import com.unique.examine.flow.service.FlowCompletionExecutionService;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Flow-owned translation from one strict AI request to native history facts. */
@Component
public class AiFlowInstanceHistoryReadAdapter
        implements AiFlowInstanceHistoryReadFacade {
    private final InstanceReader instances;
    private final HistoryReader histories;
    private final RuntimeRecordAccessFacade recordAccess;

    @Autowired
    public AiFlowInstanceHistoryReadAdapter(
            FlowRequestServiceFactory services,
            FlowCompletionExecutionService completions,
            FlowCompensationRuntimeService compensations,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this(
                (session, instanceId) -> {
                    var instance = services.forTenant(
                            session.systemId(), session.tenantId())
                            .instance(instanceId);
                    return new ResolvedInstance(
                            instance.status().name(), instance.recordBinding());
                },
                (session, instanceId) -> FlowViews.History.from(
                        instanceId,
                        services.forTenant(session.systemId(), session.tenantId())
                                .history(instanceId),
                        completions.completionHistory(session, instanceId),
                        compensations.history(session, instanceId)),
                recordAccess);
    }

    AiFlowInstanceHistoryReadAdapter(
            InstanceReader instances,
            HistoryReader histories,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this.instances = Objects.requireNonNull(instances, "instances");
        this.histories = Objects.requireNonNull(histories, "histories");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        requireRead(request.effectivePermissions());
        var session = new FlowSession(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.effectivePermissions());
        var instanceId = Long.parseLong(request.instanceId());
        var instance = resolve(session, instanceId);
        requireRecordVisibility(session, instance.recordBinding());
        var owner = history(session, instanceId);
        if (!request.instanceId().equals(owner.instanceId())) {
            throw new IllegalStateException(
                    "Flow history owner returned another instance");
        }
        var all = owner.events();
        var first = Math.max(0, all.size() - request.limit());
        var events = all.subList(first, all.size()).stream()
                .map(AiFlowInstanceHistoryReadAdapter::event)
                .toList();
        return new Result(
                request.instanceId(), instance.status(), all.size(),
                "/systems/" + request.systemId() + "/flows", events);
    }

    private ResolvedInstance resolve(FlowSession session, long instanceId) {
        try {
            return instances.resolve(session, instanceId);
        } catch (ApprovalDomainException failure) {
            if (failure.code() == ApprovalDomainException.Code.INSTANCE_NOT_FOUND) {
                throw notFound();
            }
            throw failure;
        }
    }

    private FlowViews.History history(FlowSession session, long instanceId) {
        try {
            return histories.read(session, instanceId);
        } catch (ApprovalDomainException failure) {
            if (failure.code() == ApprovalDomainException.Code.INSTANCE_NOT_FOUND
                    || failure.code() == ApprovalDomainException.Code
                    .COMPLETION_EXECUTION_NOT_FOUND) {
                throw notFound();
            }
            throw failure;
        }
    }

    private void requireRecordVisibility(
            FlowSession session, ApprovalInstance.RecordBinding binding) {
        if (binding == null) return;
        try {
            recordAccess.requireView(
                    new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                            session.systemId(), session.tenantId(),
                            session.memberId(), session.permissions(),
                            binding.moduleCode(), binding.recordId()));
        } catch (BusinessException hidden) {
            throw notFound();
        }
    }

    private static Event event(FlowViews.HistoryEvent value) {
        return new Event(
                value.sequence(), value.type(), value.fromStatus(),
                value.toStatus(), value.actorId(), value.comment(),
                Instant.parse(value.occurredAt()));
    }

    private static void requireRead(java.util.Set<String> permissions) {
        if (!permissions.contains(FlowPermissions.INSTANCE_READ)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The authenticated member does not have the required flow permission",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "FLOW_INSTANCE_NOT_FOUND",
                "Approval instance was not found",
                HttpStatus.NOT_FOUND);
    }

    record ResolvedInstance(
            String status,
            ApprovalInstance.RecordBinding recordBinding
    ) { }

    @FunctionalInterface
    interface InstanceReader {
        ResolvedInstance resolve(FlowSession session, long instanceId);
    }

    @FunctionalInterface
    interface HistoryReader {
        FlowViews.History read(FlowSession session, long instanceId);
    }
}
