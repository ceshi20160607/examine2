package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.security.FlowSession;

public interface FlowNodeEffectPort {
    ObjectNode execute(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            JsonNode input,
            ObjectNode output,
            String idempotencyKey,
            String requestId,
            String traceId);

    ObjectNode resume(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            FlowExtensionRepository.NodeExecution current,
            JsonNode input,
            ObjectNode output,
            String idempotencyKey,
            String requestId,
            String traceId);

    void failed(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            RuntimeException failure,
            String requestId,
            String traceId);
}
