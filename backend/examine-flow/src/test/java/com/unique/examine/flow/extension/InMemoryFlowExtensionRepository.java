package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryFlowExtensionRepository implements FlowExtensionRepository {
    private final Map<String, StoredGraph> drafts = new HashMap<>();
    private final Map<String, StoredGraph> versions = new HashMap<>();
    private final Map<String, FormSnapshot> snapshots = new HashMap<>();
    private final Map<String, List<FormWriteHistory>> formHistory = new HashMap<>();
    private final Map<String, NodeExecution> executions = new HashMap<>();
    private final Map<String, List<NodeExecutionEvent>> executionHistory = new HashMap<>();
    private final Map<String, DependencyState> dependencies = new HashMap<>();
    private List<InboundConsumer> inbound = List.of();

    @Override
    public synchronized StoredGraph saveDraft(
            long systemId, long tenantId, long definitionId, int sourceRevision,
            FlowExtensionGraph.Graph graph, String checksum, long actorId, Instant now) {
        var stored = new StoredGraph(definitionId, null, sourceRevision,
                graph, checksum, actorId, now);
        drafts.put(scope(systemId, tenantId, definitionId), stored);
        return stored;
    }

    @Override
    public synchronized Optional<StoredGraph> draft(
            long systemId, long tenantId, long definitionId) {
        return Optional.ofNullable(drafts.get(scope(systemId, tenantId, definitionId)));
    }

    @Override
    public synchronized StoredGraph publish(
            long systemId, long tenantId, long definitionId, int definitionVersion,
            int sourceRevision, long actorId, Instant now) {
        var draft = draft(systemId, tenantId, definitionId).orElseThrow();
        if (draft.sourceRevision() != sourceRevision) {
            throw new IllegalStateException("Flow extension draft revision is stale");
        }
        return putPublished(systemId, tenantId, definitionId, definitionVersion,
                sourceRevision, draft.graph(), draft.checksum(), actorId, now);
    }

    public synchronized StoredGraph putPublished(
            long systemId, long tenantId, long definitionId, int definitionVersion,
            int sourceRevision, FlowExtensionGraph.Graph graph) {
        return putPublished(systemId, tenantId, definitionId, definitionVersion,
                sourceRevision, graph, "0".repeat(64), 1, Instant.EPOCH);
    }

    private StoredGraph putPublished(
            long systemId, long tenantId, long definitionId, int definitionVersion,
            int sourceRevision, FlowExtensionGraph.Graph graph, String checksum,
            long actorId, Instant now) {
        var stored = new StoredGraph(definitionId, definitionVersion, sourceRevision,
                graph, checksum, actorId, now);
        versions.put(versionScope(systemId, tenantId, definitionId, definitionVersion), stored);
        return stored;
    }

    @Override
    public synchronized Optional<StoredGraph> published(
            long systemId, long tenantId, long definitionId, int definitionVersion) {
        return Optional.ofNullable(versions.get(
                versionScope(systemId, tenantId, definitionId, definitionVersion)));
    }

    @Override
    public synchronized DependencyState resolve(
            long defaultTenantId, FlowExtensionGraph.Dependency dependency) {
        return dependencies.getOrDefault(dependencyKey(dependency),
                new DependencyState(dependency, false, false, null, "DEPENDENCY_MISSING"));
    }

    public synchronized void dependency(
            FlowExtensionGraph.Dependency dependency, boolean active, long version) {
        dependencies.put(dependencyKey(dependency),
                new DependencyState(dependency, true, active, version,
                        active ? "AVAILABLE" : "DEPENDENCY_INACTIVE"));
    }

    @Override
    public synchronized List<InboundConsumer> inboundConsumers(
            long systemId, long tenantId, long definitionId) {
        return inbound;
    }

    public synchronized void inboundConsumers(List<InboundConsumer> value) {
        inbound = List.copyOf(value);
    }

    @Override
    public synchronized FormSnapshot materializeFormSnapshot(
            long systemId, long tenantId, long instanceId, long definitionId,
            int definitionVersion, FlowExtensionGraph.Node node,
            ObjectNode initialValues, Instant now) {
        return snapshots.computeIfAbsent(runtimeScope(systemId, tenantId, instanceId, node.code()),
                ignored -> new FormSnapshot(instanceId, node.code(), definitionId,
                        definitionVersion, node.moduleCode(), node.fieldPolicies(),
                        initialValues.deepCopy(), 0, now));
    }

    @Override
    public synchronized Optional<FormSnapshot> formSnapshot(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return Optional.ofNullable(snapshots.get(
                runtimeScope(systemId, tenantId, instanceId, nodeCode)));
    }

    @Override
    public synchronized boolean advanceFormSnapshot(
            long systemId, long tenantId, long instanceId,
            String nodeCode, long expectedVersion) {
        var key = runtimeScope(systemId, tenantId, instanceId, nodeCode);
        var current = snapshots.get(key);
        if (current == null || current.version() != expectedVersion) return false;
        snapshots.put(key, new FormSnapshot(current.instanceId(), current.nodeCode(),
                current.definitionId(), current.definitionVersion(), current.moduleCode(),
                current.policies(), current.initialValues(), current.version() + 1,
                current.materializedAt()));
        return true;
    }

    @Override
    public synchronized FormWriteHistory appendFormHistory(
            long systemId, long tenantId, long instanceId, String nodeCode,
            long actorId, long recordVersionBefore, long recordVersionAfter,
            ObjectNode changes, ObjectNode before, ObjectNode after, Instant now) {
        var key = runtimeScope(systemId, tenantId, instanceId, nodeCode);
        var values = formHistory.computeIfAbsent(key, ignored -> new ArrayList<>());
        var history = new FormWriteHistory(values.size() + 1, actorId,
                recordVersionBefore, recordVersionAfter, changes.deepCopy(),
                before.deepCopy(), after.deepCopy(), now);
        values.add(history);
        return history;
    }

    @Override
    public synchronized List<FormWriteHistory> formHistory(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return List.copyOf(formHistory.getOrDefault(
                runtimeScope(systemId, tenantId, instanceId, nodeCode), List.of()));
    }

    @Override
    public synchronized NodeExecution saveExecution(
            long systemId, long tenantId, long instanceId, long definitionId,
            int definitionVersion, String nodeCode, FlowNodeCatalog.Type nodeType,
            Long expectedVersion, FlowNodeExecutionEngine.Status fromStatus,
            FlowNodeExecutionEngine.Result result, ObjectNode input,
            long actorId, Instant now) {
        var key = runtimeScope(systemId, tenantId, instanceId, nodeCode);
        var current = executions.get(key);
        final long version;
        if (current == null) {
            if (expectedVersion != null && expectedVersion != 0 || fromStatus != null) {
                throw new IllegalStateException("Flow node execution version changed");
            }
            version = 0;
        } else {
            if (expectedVersion == null || current.version() != expectedVersion
                    || current.status() != fromStatus) {
                throw new IllegalStateException("Flow node execution version changed");
            }
            version = current.version() + 1;
        }
        var execution = new NodeExecution(instanceId, nodeCode, nodeType,
                result.status(), result.output().deepCopy(), version, actorId, now);
        executions.put(key, execution);
        var events = executionHistory.computeIfAbsent(key, ignored -> new ArrayList<>());
        events.add(new NodeExecutionEvent(events.size() + 1, fromStatus,
                result.status(), input.deepCopy(), result.output().deepCopy(), actorId, now));
        return execution;
    }

    @Override
    public synchronized Optional<NodeExecution> execution(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return Optional.ofNullable(executions.get(
                runtimeScope(systemId, tenantId, instanceId, nodeCode)));
    }

    @Override
    public synchronized List<NodeExecutionEvent> executionHistory(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return List.copyOf(executionHistory.getOrDefault(
                runtimeScope(systemId, tenantId, instanceId, nodeCode), List.of()));
    }

    private static String scope(long systemId, long tenantId, long definitionId) {
        return systemId + ":" + tenantId + ":" + definitionId;
    }

    private static String versionScope(
            long systemId, long tenantId, long definitionId, int version) {
        return scope(systemId, tenantId, definitionId) + ":" + version;
    }

    private static String runtimeScope(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return systemId + ":" + tenantId + ":" + instanceId + ":" + nodeCode;
    }

    private static String dependencyKey(FlowExtensionGraph.Dependency value) {
        return value.type() + ":" + value.targetSystemId() + ":"
                + value.targetTenantId() + ":" + value.targetKey();
    }
}
