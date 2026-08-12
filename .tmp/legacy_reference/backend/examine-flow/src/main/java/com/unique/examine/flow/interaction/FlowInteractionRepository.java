package com.unique.examine.flow.interaction;

import java.util.List;

public interface FlowInteractionRepository {
    FlowUrge saveUrge(FlowUrge urge);

    List<FlowUrge> findUrges(long instanceId, int offset, int limit);

    long countUrges(long instanceId);

    FlowComment saveComment(FlowComment comment);

    List<FlowComment> findComments(long instanceId, int offset, int limit);

    long countComments(long instanceId);

    FlowCopy saveCopy(FlowCopy copy);

    List<FlowCopy> findCopies(long instanceId, int offset, int limit);

    long countCopies(long instanceId);
}
