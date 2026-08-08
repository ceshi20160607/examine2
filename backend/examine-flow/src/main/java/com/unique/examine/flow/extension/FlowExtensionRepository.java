package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlowExtensionRepository {
    StoredGraph saveDraft(
            long systemId, long tenantId, long definitionId,
            int sourceRevision, FlowExtensionGraph.Graph graph,
            String checksum, long actorId, Instant now);

    Optional<StoredGraph> draft(
            long systemId, long tenantId, long definitionId);

    StoredGraph publish(
            long systemId, long tenantId, long definitionId,
            int definitionVersion, int sourceRevision,
            long actorId, Instant now);

    Optional<StoredGraph> published(
            long systemId, long tenantId, long definitionId,
            int definitionVersion);

    DependencyState resolve(
            long defaultTenantId,
            FlowExtensionGraph.Dependency dependency);

    List<InboundConsumer> inboundConsumers(
            long systemId, long tenantId, long definitionId);

    FormSnapshot materializeFormSnapshot(
            long systemId, long tenantId, long instanceId,
            long definitionId, int definitionVersion,
            FlowExtensionGraph.Node node, ObjectNode initialValues,
            Instant now);

    Optional<FormSnapshot> formSnapshot(
            long systemId, long tenantId, long instanceId,
            String nodeCode);

    boolean advanceFormSnapshot(
            long systemId, long tenantId, long instanceId,
            String nodeCode, long expectedVersion);

    FormWriteHistory appendFormHistory(
            long systemId, long tenantId, long instanceId,
            String nodeCode, long actorId,
            long recordVersionBefore, long recordVersionAfter,
            ObjectNode changes, ObjectNode before, ObjectNode after,
            Instant now);

    List<FormWriteHistory> formHistory(
            long systemId, long tenantId, long instanceId,
            String nodeCode);

    NodeExecution saveExecution(
            long systemId, long tenantId, long instanceId,
            long definitionId, int definitionVersion,
            String nodeCode, FlowNodeCatalog.Type nodeType,
            Long expectedVersion, FlowNodeExecutionEngine.Status fromStatus,
            FlowNodeExecutionEngine.Result result, ObjectNode input,
            long actorId, Instant now);

    Optional<NodeExecution> execution(
            long systemId, long tenantId, long instanceId,
            String nodeCode);

    List<NodeExecutionEvent> executionHistory(
            long systemId, long tenantId, long instanceId,
            String nodeCode);

    record StoredGraph(
            long definitionId,
            Integer definitionVersion,
            int sourceRevision,
            FlowExtensionGraph.Graph graph,
            String checksum,
            long actorId,
            Instant occurredAt) {
    }

    record DependencyState(
            FlowExtensionGraph.Dependency dependency,
            boolean exists,
            boolean active,
            Long currentVersion,
            String reason) {
    }

    record InboundConsumer(
            long systemId,
            long tenantId,
            long definitionId,
            int definitionVersion,
            String applicationCode,
            String nodeCode) {
    }

    record FormSnapshot(
            long instanceId,
            String nodeCode,
            long definitionId,
            int definitionVersion,
            String moduleCode,
            List<FlowExtensionGraph.FieldPolicy> policies,
            ObjectNode initialValues,
            long version,
            Instant materializedAt) {
    }

    record FormWriteHistory(
            int sequence,
            long actorId,
            long recordVersionBefore,
            long recordVersionAfter,
            ObjectNode changes,
            ObjectNode before,
            ObjectNode after,
            Instant occurredAt) {
    }

    record NodeExecution(
            long instanceId,
            String nodeCode,
            FlowNodeCatalog.Type nodeType,
            FlowNodeExecutionEngine.Status status,
            ObjectNode result,
            long version,
            long actorId,
            Instant updatedAt) {
    }

    record NodeExecutionEvent(
            int sequence,
            FlowNodeExecutionEngine.Status fromStatus,
            FlowNodeExecutionEngine.Status toStatus,
            ObjectNode input,
            ObjectNode result,
            long actorId,
            Instant occurredAt) {
    }
}
