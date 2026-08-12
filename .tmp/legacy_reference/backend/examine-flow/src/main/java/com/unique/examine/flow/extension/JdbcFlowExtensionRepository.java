package com.unique.examine.flow.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcFlowExtensionRepository implements FlowExtensionRepository {
    private static final TypeReference<List<FlowExtensionGraph.FieldPolicy>> POLICIES =
            new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcFlowExtensionRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    @Override
    public StoredGraph saveDraft(
            long systemId, long tenantId, long definitionId,
            int sourceRevision, FlowExtensionGraph.Graph graph,
            String checksum, long actorId, Instant now) {
        var revision = jdbc.query(
                "SELECT revision FROM un_flow_definition_draft "
                        + "WHERE system_id=? AND tenant_id=? AND definition_id=?",
                (row, ignored) -> row.getInt(1), systemId, tenantId, definitionId);
        if (revision.size() != 1 || revision.getFirst() != sourceRevision) {
            throw new IllegalStateException("Flow definition draft revision changed");
        }
        var graphJson = write(graph);
        var updated = jdbc.update("""
                UPDATE un_flow_definition_extension_draft
                   SET source_revision=?,graph_json=?,graph_checksum=?,
                       updated_by=?,updated_at=?
                 WHERE system_id=? AND tenant_id=? AND definition_id=?
                """, sourceRevision, graphJson, checksum, actorId, timestamp(now),
                systemId, tenantId, definitionId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO un_flow_definition_extension_draft
                      (system_id,tenant_id,definition_id,source_revision,
                       graph_json,graph_checksum,updated_by,updated_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, systemId, tenantId, definitionId, sourceRevision,
                    graphJson, checksum, actorId, timestamp(now));
        }
        return new StoredGraph(definitionId, null, sourceRevision, graph,
                checksum, actorId, now);
    }

    @Override
    public Optional<StoredGraph> draft(
            long systemId, long tenantId, long definitionId) {
        return jdbc.query("""
                SELECT definition_id,source_revision,graph_json,graph_checksum,
                       updated_by,updated_at
                  FROM un_flow_definition_extension_draft
                 WHERE system_id=? AND tenant_id=? AND definition_id=?
                """, (row, ignored) -> stored(row, null), systemId, tenantId,
                definitionId).stream().findFirst();
    }

    @Override
    public StoredGraph publish(
            long systemId, long tenantId, long definitionId,
            int definitionVersion, int sourceRevision,
            long actorId, Instant now) {
        var draft = draft(systemId, tenantId, definitionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Flow extension draft was not configured"));
        if (draft.sourceRevision() != sourceRevision) {
            throw new IllegalStateException("Flow extension draft revision is stale");
        }
        jdbc.update("""
                INSERT INTO un_flow_definition_extension_version
                  (system_id,tenant_id,definition_id,definition_version,
                   source_revision,graph_json,graph_checksum,published_by,published_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, systemId, tenantId, definitionId, definitionVersion,
                sourceRevision, write(draft.graph()), draft.checksum(), actorId,
                timestamp(now));
        return new StoredGraph(definitionId, definitionVersion, sourceRevision,
                draft.graph(), draft.checksum(), actorId, now);
    }

    @Override
    public Optional<StoredGraph> published(
            long systemId, long tenantId, long definitionId,
            int definitionVersion) {
        return jdbc.query("""
                SELECT definition_id,definition_version,source_revision,
                       graph_json,graph_checksum,published_by,published_at
                  FROM un_flow_definition_extension_version
                 WHERE system_id=? AND tenant_id=? AND definition_id=?
                   AND definition_version=?
                """, (row, ignored) -> stored(row, row.getInt("definition_version")),
                systemId, tenantId, definitionId, definitionVersion)
                .stream().findFirst();
    }

    @Override
    public DependencyState resolve(
            long defaultTenantId,
            FlowExtensionGraph.Dependency dependency) {
        var tenantId = dependency.targetTenantId() == null
                ? defaultTenantId : dependency.targetTenantId();
        return switch (dependency.type()) {
            case FLOW_DEFINITION -> versionState(dependency, jdbc.query("""
                    SELECT MAX(version_no) current_version
                      FROM un_flow_definition_version
                     WHERE system_id=? AND tenant_id=? AND definition_id=?
                    """, (row, ignored) -> nullableLong(row, "current_version"),
                    dependency.targetSystemId(), tenantId,
                    positiveKey(dependency.targetKey())).stream().findFirst().orElse(null), true);
            case MODULE_CONFIGURATION -> versionState(dependency, jdbc.query("""
                    SELECT v.version_no current_version
                      FROM un_module_config_root r
                      JOIN un_module_config_version v
                        ON v.system_id=r.system_id AND v.id=r.active_version_id
                     WHERE r.system_id=?
                    """, (row, ignored) -> row.getLong("current_version"),
                    dependency.targetSystemId()).stream().findFirst().orElse(null), true);
            case OPENAPI_APPLICATION -> stateWithStatus(dependency, jdbc.query("""
                    SELECT version current_version,status
                      FROM un_openapi_application
                     WHERE system_id=? AND tenant_id=? AND app_key=?
                    """, (row, ignored) -> new VersionStatus(
                            row.getLong("current_version"), row.getString("status")),
                    dependency.targetSystemId(), tenantId, dependency.targetKey())
                    .stream().findFirst().orElse(null), "ACTIVE");
            case MESSAGE_TEMPLATE -> stateWithStatus(dependency, jdbc.query("""
                    SELECT v.version_no current_version,
                           IF(v.enabled=1,'ACTIVE','DISABLED') status
                      FROM un_event_message_template t
                      JOIN un_event_message_template_version v
                        ON v.system_id=t.system_id AND v.id=t.published_version_id
                     WHERE t.system_id=? AND t.template_code=?
                    """, (row, ignored) -> new VersionStatus(
                            row.getLong("current_version"), row.getString("status")),
                    dependency.targetSystemId(), dependency.targetKey())
                    .stream().findFirst().orElse(null), "ACTIVE");
            case AI_POLICY -> stateWithStatus(dependency, jdbc.query("""
                    SELECT v.version_no current_version,
                           IF(v.enabled=1,'ACTIVE','DISABLED') status
                      FROM un_ai_agent_policy p
                      JOIN un_ai_agent_policy_version v
                        ON v.system_id=p.system_id AND v.tenant_id=p.tenant_id
                       AND v.id=p.active_version_id
                     WHERE p.system_id=? AND p.tenant_id=? AND p.id=?
                    """, (row, ignored) -> new VersionStatus(
                            row.getLong("current_version"), row.getString("status")),
                    dependency.targetSystemId(), tenantId,
                    positiveKey(dependency.targetKey())).stream().findFirst().orElse(null),
                    "ACTIVE");
        };
    }

    @Override
    public List<InboundConsumer> inboundConsumers(
            long systemId, long tenantId, long definitionId) {
        var rows = jdbc.query("""
                SELECT v.system_id,v.tenant_id,v.definition_id,v.definition_version,
                       v.graph_json
                  FROM un_flow_definition_extension_version v
                  JOIN (
                    SELECT system_id,tenant_id,definition_id,
                           MAX(definition_version) latest_version
                      FROM un_flow_definition_extension_version
                     GROUP BY system_id,tenant_id,definition_id
                  ) latest
                    ON latest.system_id=v.system_id
                   AND latest.tenant_id=v.tenant_id
                   AND latest.definition_id=v.definition_id
                   AND latest.latest_version=v.definition_version
                """, (row, ignored) -> new GraphRow(
                row.getLong("system_id"), row.getLong("tenant_id"),
                row.getLong("definition_id"), row.getInt("definition_version"),
                readGraph(row.getString("graph_json"))));
        var result = new ArrayList<InboundConsumer>();
        for (var row : rows) {
            if (row.systemId == systemId && row.tenantId == tenantId
                    && row.definitionId == definitionId) continue;
            for (var dependency : row.graph.dependencies()) {
                var targetTenant = dependency.targetTenantId() == null
                        ? row.tenantId : dependency.targetTenantId();
                if (dependency.type() == FlowExtensionGraph.DependencyType.FLOW_DEFINITION
                        && dependency.targetSystemId() == systemId
                        && targetTenant == tenantId
                        && dependency.targetKey().equals(Long.toString(definitionId))) {
                    var node = row.graph.requireNode(dependency.sourceNodeCode());
                    result.add(new InboundConsumer(
                            row.systemId, row.tenantId, row.definitionId,
                            row.definitionVersion, node.applicationCode(), node.code()));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public FormSnapshot materializeFormSnapshot(
            long systemId, long tenantId, long instanceId,
            long definitionId, int definitionVersion,
            FlowExtensionGraph.Node node, ObjectNode initialValues,
            Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO un_flow_instance_form_snapshot
                      (system_id,tenant_id,instance_id,node_code,definition_id,
                       definition_version,module_code,policy_json,initial_form_json,
                       snapshot_version,materialized_at)
                    VALUES (?,?,?,?,?,?,?,?,?,0,?)
                    """, systemId, tenantId, instanceId, node.code(), definitionId,
                    definitionVersion, node.moduleCode(), write(node.fieldPolicies()),
                    write(initialValues), timestamp(now));
        } catch (DuplicateKeyException ignored) {
            // The first materialized snapshot is immutable and wins concurrent opens.
        }
        return formSnapshot(systemId, tenantId, instanceId, node.code())
                .orElseThrow(() -> new IllegalStateException(
                        "Flow form snapshot materialization failed"));
    }

    @Override
    public Optional<FormSnapshot> formSnapshot(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return jdbc.query("""
                SELECT instance_id,node_code,definition_id,definition_version,
                       module_code,policy_json,initial_form_json,snapshot_version,
                       materialized_at
                  FROM un_flow_instance_form_snapshot
                 WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                """, (row, ignored) -> new FormSnapshot(
                row.getLong("instance_id"), row.getString("node_code"),
                row.getLong("definition_id"), row.getInt("definition_version"),
                row.getString("module_code"), readPolicies(row.getString("policy_json")),
                readObject(row.getString("initial_form_json")),
                row.getLong("snapshot_version"), instant(row, "materialized_at")),
                systemId, tenantId, instanceId, nodeCode).stream().findFirst();
    }

    @Override
    public boolean advanceFormSnapshot(
            long systemId, long tenantId, long instanceId,
            String nodeCode, long expectedVersion) {
        return jdbc.update("""
                UPDATE un_flow_instance_form_snapshot
                   SET snapshot_version=snapshot_version+1
                 WHERE system_id=? AND tenant_id=? AND instance_id=?
                   AND node_code=? AND snapshot_version=?
                """, systemId, tenantId, instanceId, nodeCode, expectedVersion) == 1;
    }

    @Override
    public FormWriteHistory appendFormHistory(
            long systemId, long tenantId, long instanceId,
            String nodeCode, long actorId,
            long recordVersionBefore, long recordVersionAfter,
            ObjectNode changes, ObjectNode before, ObjectNode after,
            Instant now) {
        var sequence = jdbc.queryForObject("""
                SELECT COALESCE(MAX(history_sequence),0)+1
                  FROM un_flow_form_write_history
                 WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                """, Integer.class, systemId, tenantId, instanceId, nodeCode);
        jdbc.update("""
                INSERT INTO un_flow_form_write_history
                  (system_id,tenant_id,instance_id,node_code,history_sequence,
                   actor_member_id,record_version_before,record_version_after,
                   changes_json,before_json,after_json,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, systemId, tenantId, instanceId, nodeCode, sequence, actorId,
                recordVersionBefore, recordVersionAfter, write(changes), write(before),
                write(after), timestamp(now));
        return new FormWriteHistory(sequence, actorId, recordVersionBefore,
                recordVersionAfter, changes.deepCopy(), before.deepCopy(),
                after.deepCopy(), now);
    }

    @Override
    public List<FormWriteHistory> formHistory(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return jdbc.query("""
                SELECT history_sequence,actor_member_id,record_version_before,
                       record_version_after,changes_json,before_json,after_json,occurred_at
                  FROM un_flow_form_write_history
                 WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                 ORDER BY history_sequence
                """, (row, ignored) -> new FormWriteHistory(
                row.getInt("history_sequence"), row.getLong("actor_member_id"),
                row.getLong("record_version_before"), row.getLong("record_version_after"),
                readObject(row.getString("changes_json")),
                readObject(row.getString("before_json")),
                readObject(row.getString("after_json")), instant(row, "occurred_at")),
                systemId, tenantId, instanceId, nodeCode);
    }

    @Override
    public NodeExecution saveExecution(
            long systemId, long tenantId, long instanceId,
            long definitionId, int definitionVersion,
            String nodeCode, FlowNodeCatalog.Type nodeType,
            Long expectedVersion, FlowNodeExecutionEngine.Status fromStatus,
            FlowNodeExecutionEngine.Result result, ObjectNode input,
            long actorId, Instant now) {
        var current = execution(systemId, tenantId, instanceId, nodeCode).orElse(null);
        final long nextVersion;
        if (current == null) {
            if (expectedVersion != null && expectedVersion != 0 || fromStatus != null) {
                throw new IllegalStateException("Flow node execution version changed");
            }
            jdbc.update("""
                    INSERT INTO un_flow_node_execution
                      (system_id,tenant_id,instance_id,node_code,node_type,
                       definition_id,definition_version,execution_status,result_json,
                       execution_version,updated_by,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,0,?,?)
                    """, systemId, tenantId, instanceId, nodeCode, nodeType.name(),
                    definitionId, definitionVersion, result.status().name(),
                    write(result.output()), actorId, timestamp(now));
            nextVersion = 0;
        } else {
            if (expectedVersion == null || expectedVersion != current.version()
                    || current.status() != fromStatus) {
                throw new IllegalStateException("Flow node execution version changed");
            }
            var updated = jdbc.update("""
                    UPDATE un_flow_node_execution
                       SET execution_status=?,result_json=?,execution_version=execution_version+1,
                           updated_by=?,updated_at=?
                     WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                       AND execution_version=? AND execution_status=?
                    """, result.status().name(), write(result.output()), actorId, timestamp(now),
                    systemId, tenantId, instanceId, nodeCode, expectedVersion,
                    fromStatus.name());
            if (updated != 1) {
                throw new IllegalStateException("Flow node execution version changed");
            }
            nextVersion = expectedVersion + 1;
        }
        jdbc.update("""
                INSERT INTO un_flow_node_execution_event
                  (system_id,tenant_id,instance_id,node_code,event_sequence,
                   from_status,to_status,input_json,result_json,actor_member_id,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, systemId, tenantId, instanceId, nodeCode, nextVersion + 1,
                fromStatus == null ? null : fromStatus.name(), result.status().name(),
                write(input), write(result.output()), actorId, timestamp(now));
        return new NodeExecution(instanceId, nodeCode, nodeType, result.status(),
                result.output().deepCopy(), nextVersion, actorId, now);
    }

    @Override
    public Optional<NodeExecution> execution(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return jdbc.query("""
                SELECT instance_id,node_code,node_type,execution_status,result_json,
                       execution_version,updated_by,updated_at
                  FROM un_flow_node_execution
                 WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                """, (row, ignored) -> new NodeExecution(
                row.getLong("instance_id"), row.getString("node_code"),
                FlowNodeCatalog.Type.valueOf(row.getString("node_type")),
                FlowNodeExecutionEngine.Status.valueOf(row.getString("execution_status")),
                readObject(row.getString("result_json")), row.getLong("execution_version"),
                row.getLong("updated_by"), instant(row, "updated_at")),
                systemId, tenantId, instanceId, nodeCode).stream().findFirst();
    }

    @Override
    public List<NodeExecutionEvent> executionHistory(
            long systemId, long tenantId, long instanceId, String nodeCode) {
        return jdbc.query("""
                SELECT event_sequence,from_status,to_status,input_json,result_json,
                       actor_member_id,occurred_at
                  FROM un_flow_node_execution_event
                 WHERE system_id=? AND tenant_id=? AND instance_id=? AND node_code=?
                 ORDER BY event_sequence
                """, (row, ignored) -> new NodeExecutionEvent(
                row.getInt("event_sequence"),
                row.getString("from_status") == null ? null
                        : FlowNodeExecutionEngine.Status.valueOf(row.getString("from_status")),
                FlowNodeExecutionEngine.Status.valueOf(row.getString("to_status")),
                readObject(row.getString("input_json")),
                readObject(row.getString("result_json")), row.getLong("actor_member_id"),
                instant(row, "occurred_at")), systemId, tenantId, instanceId, nodeCode);
    }

    private StoredGraph stored(ResultSet row, Integer version) throws SQLException {
        return new StoredGraph(row.getLong("definition_id"), version,
                row.getInt("source_revision"), readGraph(row.getString("graph_json")),
                row.getString("graph_checksum"),
                row.getLong(version == null ? "updated_by" : "published_by"),
                instant(row, version == null ? "updated_at" : "published_at"));
    }

    private DependencyState versionState(
            FlowExtensionGraph.Dependency dependency, Long version, boolean active) {
        return new DependencyState(dependency, version != null, version != null && active,
                version, version == null ? "DEPENDENCY_MISSING" : "AVAILABLE");
    }

    private DependencyState stateWithStatus(
            FlowExtensionGraph.Dependency dependency,
            VersionStatus value,
            String activeStatus) {
        if (value == null) return versionState(dependency, null, false);
        var active = activeStatus.equals(value.status);
        return new DependencyState(dependency, true, active, value.version,
                active ? "AVAILABLE" : "DEPENDENCY_INACTIVE");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Flow extension JSON is invalid", exception);
        }
    }

    private FlowExtensionGraph.Graph readGraph(String value) {
        try {
            return json.readValue(value, FlowExtensionGraph.Graph.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Flow extension graph is invalid", exception);
        }
    }

    private List<FlowExtensionGraph.FieldPolicy> readPolicies(String value) {
        try {
            return json.readValue(value, POLICIES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Flow field policy is invalid", exception);
        }
    }

    private ObjectNode readObject(String value) {
        try {
            return (ObjectNode) json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Flow JSON object is invalid", exception);
        }
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        var value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static long positiveKey(String value) {
        try {
            var result = Long.parseLong(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Flow dependency target key must be a positive id");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getTimestamp(column).toInstant();
    }

    private record VersionStatus(long version, String status) {
    }

    private record GraphRow(
            long systemId, long tenantId, long definitionId,
            int definitionVersion, FlowExtensionGraph.Graph graph) {
    }
}
