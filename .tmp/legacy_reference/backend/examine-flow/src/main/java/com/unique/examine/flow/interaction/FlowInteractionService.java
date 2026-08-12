package com.unique.examine.flow.interaction;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import com.unique.examine.flow.domain.ApprovalInstance;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class FlowInteractionService {
    private final ApprovalWorkflowService workflows;
    private final FlowInteractionRepository repository;
    private final IdService ids;
    private final Clock clock;

    public FlowInteractionService(
            ApprovalWorkflowService workflows,
            FlowInteractionRepository repository,
            IdService ids,
            Clock clock
    ) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UrgeDispatch urge(long instanceId, long actorId, String message) {
        var instance = workflows.instance(instanceId);
        var urge = FlowUrge.create(ids.nextId(), instance, actorId, message, clock.instant());
        return new UrgeDispatch(repository.saveUrge(urge), instance.businessKey());
    }

    public Page<FlowUrge> urges(long instanceId, int page, int size) {
        workflows.instance(instanceId);
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findUrges(instanceId, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countUrges(instanceId)
        );
    }

    public FlowComment comment(long instanceId, long authorId, String body) {
        workflows.instance(instanceId);
        return repository.saveComment(new FlowComment(
                ids.nextId(),
                instanceId,
                authorId,
                body,
                clock.instant()
        ));
    }

    public Page<FlowComment> comments(long instanceId, int page, int size) {
        workflows.instance(instanceId);
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findComments(instanceId, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countComments(instanceId)
        );
    }

    public ApprovalInstance instance(long instanceId) {
        return workflows.instance(instanceId);
    }

    public FlowCopy copy(
            ApprovalInstance instance,
            long actorId,
            long recipientId,
            String message
    ) {
        Objects.requireNonNull(instance, "instance");
        return repository.saveCopy(FlowCopy.create(
                ids.nextId(),
                instance,
                actorId,
                recipientId,
                message,
                clock.instant()
        ));
    }

    public Page<FlowCopy> copies(long instanceId, int page, int size) {
        workflows.instance(instanceId);
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findCopies(instanceId, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countCopies(instanceId)
        );
    }

    private static PageBounds bounds(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1 || size > ApprovalWorkflowService.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + ApprovalWorkflowService.MAX_PAGE_SIZE
            );
        }
        var offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page and size produce an unsupported offset");
        }
        return new PageBounds((int) offset, size);
    }

    public record UrgeDispatch(FlowUrge urge, String businessKey) {
        public UrgeDispatch {
            Objects.requireNonNull(urge, "urge");
            Objects.requireNonNull(businessKey, "businessKey");
        }
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
            if (page < 1 || size < 1 || total < 0) {
                throw new IllegalArgumentException("Flow interaction page metadata is invalid");
            }
        }
    }

    private record PageBounds(int offset, int size) {
    }
}
