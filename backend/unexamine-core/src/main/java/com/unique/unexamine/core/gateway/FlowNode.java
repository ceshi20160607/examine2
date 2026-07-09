package com.unique.unexamine.core.gateway;

public record FlowNode(
        String nodeId,
        String label,
        String type,
        String assignee,
        String action
) {
}