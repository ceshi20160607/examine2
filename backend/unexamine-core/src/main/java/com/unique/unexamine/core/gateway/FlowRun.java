package com.unique.unexamine.core.gateway;

public record FlowRun(
        String runId,
        String flowCode,
        String moduleCode,
        String recordId,
        String status,
        String currentNode,
        String traceId,
        String todoMessage,
        String logSummary,
        String startedAt
) {
}