package com.unique.examine.flow.interaction.memory;

import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.interaction.FlowComment;
import com.unique.examine.flow.interaction.FlowCopy;
import com.unique.examine.flow.interaction.FlowInteractionRepository;
import com.unique.examine.flow.interaction.FlowUrge;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.PERSISTENCE_CONFLICT;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_ALREADY_EXISTS;

public final class InMemoryFlowInteractionRepository implements FlowInteractionRepository {
    private static final Comparator<FlowUrge> URGE_ORDER =
            Comparator.comparing(FlowUrge::createdAt).thenComparingLong(FlowUrge::id);
    private static final Comparator<FlowComment> COMMENT_ORDER =
            Comparator.comparing(FlowComment::createdAt).thenComparingLong(FlowComment::id);
    private static final Comparator<FlowCopy> COPY_ORDER =
            Comparator.comparing(FlowCopy::createdAt).thenComparingLong(FlowCopy::id);

    private final Map<Long, FlowUrge> urges = new HashMap<>();
    private final Map<Long, FlowComment> comments = new HashMap<>();
    private final Map<Long, FlowCopy> copies = new HashMap<>();

    @Override
    public synchronized FlowUrge saveUrge(FlowUrge urge) {
        if (urges.putIfAbsent(urge.id(), urge) != null) {
            throw conflict("Flow urge ids are immutable");
        }
        return urge;
    }

    @Override
    public synchronized List<FlowUrge> findUrges(long instanceId, int offset, int limit) {
        return urges.values().stream()
                .filter(urge -> urge.instanceId() == instanceId)
                .sorted(URGE_ORDER)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countUrges(long instanceId) {
        return urges.values().stream()
                .filter(urge -> urge.instanceId() == instanceId)
                .count();
    }

    @Override
    public synchronized FlowComment saveComment(FlowComment comment) {
        if (comments.putIfAbsent(comment.id(), comment) != null) {
            throw conflict("Flow comment ids are immutable");
        }
        return comment;
    }

    @Override
    public synchronized List<FlowComment> findComments(long instanceId, int offset, int limit) {
        return comments.values().stream()
                .filter(comment -> comment.instanceId() == instanceId)
                .sorted(COMMENT_ORDER)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countComments(long instanceId) {
        return comments.values().stream()
                .filter(comment -> comment.instanceId() == instanceId)
                .count();
    }

    @Override
    public synchronized FlowCopy saveCopy(FlowCopy copy) {
        if (copies.values().stream().anyMatch(existing ->
                existing.instanceId() == copy.instanceId()
                        && existing.recipientId() == copy.recipientId())) {
            throw new ApprovalDomainException(
                    COPY_ALREADY_EXISTS,
                    "The member is already copied on this Flow instance"
            );
        }
        if (copies.putIfAbsent(copy.id(), copy) != null) {
            throw conflict("Flow copy ids are immutable");
        }
        return copy;
    }

    @Override
    public synchronized List<FlowCopy> findCopies(long instanceId, int offset, int limit) {
        return copies.values().stream()
                .filter(copy -> copy.instanceId() == instanceId)
                .sorted(COPY_ORDER)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countCopies(long instanceId) {
        return copies.values().stream()
                .filter(copy -> copy.instanceId() == instanceId)
                .count();
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(PERSISTENCE_CONFLICT, message);
    }
}
