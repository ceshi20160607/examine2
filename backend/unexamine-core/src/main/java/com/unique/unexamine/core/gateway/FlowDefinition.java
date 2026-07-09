package com.unique.unexamine.core.gateway;

import java.util.List;

public record FlowDefinition(
        String flowCode,
        String flowName,
        String version,
        String status,
        List<String> boundModules,
        List<String> externalSystems,
        List<String> approvers,
        List<String> dataSources,
        List<FlowNode> nodes,
        List<String> actions,
        String updatedAt
) {
}