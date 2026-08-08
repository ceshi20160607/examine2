package com.unique.examine.web.todo;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoIdentity;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoSourceSnapshot;
import com.unique.examine.todo.port.TodoActionPort;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourcePort;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Composes Flow's public assignment and mutation APIs into Todo. */
@Component
public final class FlowTodoAdapter implements TodoSourcePort, TodoActionPort {
    private static final String ACTION_SCOPE_PREFIX = "DECIDE:";
    private static final int SOURCE_PAGE_SIZE = 100;
    private static final int PRIORITY = 50;

    private final FlowRequestServiceFactory workflows;
    private final FlowMutationService mutations;

    public FlowTodoAdapter(
            FlowRequestServiceFactory workflows,
            FlowMutationService mutations
    ) {
        if (workflows == null || mutations == null) {
            throw new IllegalArgumentException("Flow Todo services are required");
        }
        this.workflows = workflows;
        this.mutations = mutations;
    }

    @Override
    public TodoItem.SourceType sourceType() {
        return TodoItem.SourceType.FLOW_APPROVAL;
    }

    @Override
    public List<TodoSourceSnapshot> loadOpen(TodoActor actor) {
        if (!canReadAndDecide(actor.permissions())) {
            return List.of();
        }
        var service = workflows.forTenant(actor.systemId(), actor.tenantId());
        var result = new ArrayList<TodoSourceSnapshot>();
        var page = 1;
        while (true) {
            var current = service.approvalTaskAssignments(
                    actor.memberId(), ApprovalTaskStatus.PENDING,
                    page, SOURCE_PAGE_SIZE);
            for (var assignment : current.items()) {
                for (var authority : assignment.representedAuthorities()) {
                    result.add(snapshot(
                            actor, assignment.instance(),
                            authority.representedMemberId()));
                }
            }
            if ((long) page * SOURCE_PAGE_SIZE >= current.total()) {
                return List.copyOf(result);
            }
            page++;
        }
    }

    @Override
    public TodoSourceReload reload(
            TodoActor actor,
            TodoSourceReference reference
    ) {
        if (reference.sourceType() != sourceType()
                || reference.representedMemberId() == null
                || !scope(reference.representedMemberId())
                        .equals(reference.actionScope())) {
            return TodoSourceReload.of(TodoSourceReload.Status.INELIGIBLE);
        }
        if (!canReadAndDecide(actor.permissions())) {
            return TodoSourceReload.of(TodoSourceReload.Status.DENIED);
        }
        var instanceId = positiveId(reference.sourceId());
        if (instanceId == null) {
            return TodoSourceReload.of(TodoSourceReload.Status.MISSING);
        }
        var service = workflows.forTenant(actor.systemId(), actor.tenantId());
        var assignment = findAssignment(
                service, actor.memberId(), instanceId,
                reference.representedMemberId());
        if (assignment == null) {
            return TodoSourceReload.of(nonAssignmentStatus(service, instanceId));
        }
        var instance = assignment.instance();
        if (sourceVersion(instance) != reference.sourceVersion()) {
            return TodoSourceReload.of(TodoSourceReload.Status.STALE);
        }
        return TodoSourceReload.live(snapshot(
                actor, instance, reference.representedMemberId()));
    }

    @Override
    public TodoSourceActionResult execute(TodoSourceActionCommand command) {
        if (command.sourceType() != sourceType()
                || command.representedMemberId() == null
                || !scope(command.representedMemberId())
                        .equals(command.actionScope())
                || !Set.of(
                        TodoItem.ActionCode.APPROVE,
                        TodoItem.ActionCode.REJECT)
                        .contains(command.action())
                || !canReadAndDecide(command.permissions())) {
            return result(
                    TodoSourceActionResult.Code.DENIED,
                    command.expectedSourceVersion(),
                    "Flow approval action is not authorized");
        }
        var instanceId = positiveId(command.sourceId());
        if (instanceId == null) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    command.expectedSourceVersion(),
                    "Flow approval is no longer available");
        }
        var workflow = workflows.forTenant(
                command.systemId(), command.tenantId());
        var assignment = findAssignment(
                workflow, command.actorMemberId(), instanceId,
                command.representedMemberId());
        if (assignment == null) {
            var status = nonAssignmentStatus(workflow, instanceId);
            return result(
                    status == TodoSourceReload.Status.INELIGIBLE
                            ? TodoSourceActionResult.Code.DENIED
                            : TodoSourceActionResult.Code.STALE,
                    command.expectedSourceVersion(),
                    "Flow approval is no longer actionable");
        }
        var currentVersion = sourceVersion(assignment.instance());
        if (currentVersion != command.expectedSourceVersion()) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    currentVersion,
                    "Flow approval changed before the decision");
        }

        var session = new FlowSession(
                command.actorAccountId(), command.systemId(), command.tenantId(),
                command.actorMemberId(), command.permissions());
        var represented = Long.toString(command.representedMemberId());
        try {
            if (command.action() == TodoItem.ActionCode.APPROVE) {
                mutations.approve(
                        session, instanceId,
                        new FlowRequests.Decision(
                                command.comment(), represented),
                        command.callerIdempotencyKey());
            } else {
                mutations.reject(
                        session, instanceId,
                        new FlowRequests.Rejection(
                                command.reason(), represented),
                        command.callerIdempotencyKey());
            }
            var decided = workflow.instance(instanceId);
            return result(
                    TodoSourceActionResult.Code.SUCCESS,
                    sourceVersion(decided),
                    command.action() == TodoItem.ActionCode.APPROVE
                            ? "Flow approval approved"
                            : "Flow approval rejected");
        } catch (ApprovalDomainException failure) {
            return domainFailure(failure, currentVersion);
        } catch (BusinessException failure) {
            return businessFailure(failure, currentVersion);
        } catch (RuntimeException failure) {
            return result(
                    TodoSourceActionResult.Code.FAILED,
                    currentVersion,
                    "Flow approval action failed");
        }
    }

    private static TodoSourceSnapshot snapshot(
            TodoActor actor,
            ApprovalInstance instance,
            long representedMemberId
    ) {
        var dueAt = instance.deadline() == null
                ? null : instance.deadline().dueAt();
        return new TodoSourceSnapshot(
                new TodoIdentity(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        TodoItem.SourceType.FLOW_APPROVAL,
                        Long.toString(instance.id()),
                        scope(representedMemberId)),
                sourceVersion(instance), TodoItem.Category.APPROVAL,
                PRIORITY, "Approval: " + instance.businessKey(), dueAt,
                "/systems/" + actor.systemId()
                        + "/flows?instanceId=" + instance.id(),
                Set.of(
                        TodoItem.ActionCode.APPROVE,
                        TodoItem.ActionCode.REJECT),
                representedMemberId);
    }

    private static ApprovalTaskAssignment findAssignment(
            com.unique.examine.flow.service.ApprovalWorkflowService service,
            long actorMemberId,
            long instanceId,
            long representedMemberId
    ) {
        var page = 1;
        while (true) {
            var current = service.approvalTaskAssignments(
                    actorMemberId, ApprovalTaskStatus.PENDING,
                    page, SOURCE_PAGE_SIZE);
            for (var assignment : current.items()) {
                if (assignment.instance().id() == instanceId
                        && assignment.representedAuthorities().stream()
                        .anyMatch(value -> value.representedMemberId()
                                == representedMemberId)) {
                    return assignment;
                }
            }
            if ((long) page * SOURCE_PAGE_SIZE >= current.total()) {
                return null;
            }
            page++;
        }
    }

    private static TodoSourceReload.Status nonAssignmentStatus(
            com.unique.examine.flow.service.ApprovalWorkflowService service,
            long instanceId
    ) {
        try {
            return service.instance(instanceId).status()
                    == ApprovalInstance.Status.PENDING
                    ? TodoSourceReload.Status.INELIGIBLE
                    : TodoSourceReload.Status.COMPLETED;
        } catch (ApprovalDomainException failure) {
            return TodoSourceReload.Status.MISSING;
        }
    }

    private static long sourceVersion(ApprovalInstance instance) {
        return Math.max(1L, instance.history().size());
    }

    private static String scope(long representedMemberId) {
        return ACTION_SCOPE_PREFIX + representedMemberId;
    }

    private static boolean canReadAndDecide(Set<String> permissions) {
        return permissions.contains(FlowPermissions.INSTANCE_READ)
                && permissions.contains(FlowPermissions.INSTANCE_DECIDE);
    }

    private static Long positiveId(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static TodoSourceActionResult domainFailure(
            ApprovalDomainException failure,
            long sourceVersion
    ) {
        var name = failure.code().name();
        if (name.contains("FORBIDDEN") || name.contains("INACTIVE")) {
            return result(
                    TodoSourceActionResult.Code.DENIED,
                    sourceVersion,
                    "Flow approval authority is no longer valid");
        }
        if (name.contains("STATE") || name.contains("NOT_FOUND")) {
            return result(
                    TodoSourceActionResult.Code.STALE,
                    sourceVersion,
                    "Flow approval is no longer actionable");
        }
        return result(
                TodoSourceActionResult.Code.FAILED,
                sourceVersion,
                "Flow approval action failed");
    }

    private static TodoSourceActionResult businessFailure(
            BusinessException failure,
            long sourceVersion
    ) {
        var code = failure.status() == HttpStatus.FORBIDDEN
                ? TodoSourceActionResult.Code.DENIED
                : failure.status() == HttpStatus.CONFLICT
                ? TodoSourceActionResult.Code.CONFLICT
                : failure.status() == HttpStatus.NOT_FOUND
                ? TodoSourceActionResult.Code.STALE
                : TodoSourceActionResult.Code.FAILED;
        return result(code, sourceVersion, switch (code) {
            case DENIED -> "Flow approval authority is no longer valid";
            case CONFLICT -> "Flow approval was changed concurrently";
            case STALE -> "Flow approval is no longer actionable";
            default -> "Flow approval action failed";
        });
    }

    private static TodoSourceActionResult result(
            TodoSourceActionResult.Code code,
            long sourceVersion,
            String message
    ) {
        return new TodoSourceActionResult(code, sourceVersion, message);
    }
}
