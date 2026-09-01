package com.unique.unexamine.runtimedata.manage;

public record RuntimeRecordLifecycleImpact(
        Long recordId,
        String action,
        String currentState,
        long outgoingRelationCount,
        long incomingRelationCount,
        long participantCount,
        String warning) {
}
