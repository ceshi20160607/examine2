package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcFlowExtensionRepositoryJourneyTest {
    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate jdbc;
    private JdbcFlowExtensionRepository repository;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:flow_extension_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE un_flow_definition_draft(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  definition_id BIGINT NOT NULL, revision INT NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,definition_id))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_definition_extension_draft(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  definition_id BIGINT NOT NULL, source_revision INT NOT NULL,
                  graph_json MEDIUMTEXT NOT NULL, graph_checksum CHAR(64) NOT NULL,
                  updated_by BIGINT NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,definition_id))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_definition_extension_version(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  definition_id BIGINT NOT NULL, definition_version INT NOT NULL,
                  source_revision INT NOT NULL, graph_json MEDIUMTEXT NOT NULL,
                  graph_checksum CHAR(64) NOT NULL, published_by BIGINT NOT NULL,
                  published_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,definition_id,definition_version))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_instance_form_snapshot(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  instance_id BIGINT NOT NULL, node_code VARCHAR(64) NOT NULL,
                  definition_id BIGINT NOT NULL, definition_version INT NOT NULL,
                  module_code VARCHAR(64) NOT NULL, policy_json MEDIUMTEXT NOT NULL,
                  initial_form_json MEDIUMTEXT NOT NULL, snapshot_version BIGINT NOT NULL,
                  materialized_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,instance_id,node_code))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_form_write_history(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  instance_id BIGINT NOT NULL, node_code VARCHAR(64) NOT NULL,
                  history_sequence INT NOT NULL, actor_member_id BIGINT NOT NULL,
                  record_version_before BIGINT NOT NULL, record_version_after BIGINT NOT NULL,
                  changes_json MEDIUMTEXT NOT NULL, before_json MEDIUMTEXT NOT NULL,
                  after_json MEDIUMTEXT NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,instance_id,node_code,history_sequence))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_node_execution(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  instance_id BIGINT NOT NULL, node_code VARCHAR(64) NOT NULL,
                  node_type VARCHAR(32) NOT NULL, definition_id BIGINT NOT NULL,
                  definition_version INT NOT NULL, execution_status VARCHAR(32) NOT NULL,
                  result_json MEDIUMTEXT NOT NULL, execution_version BIGINT NOT NULL,
                  updated_by BIGINT NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,instance_id,node_code))
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_node_execution_event(
                  system_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
                  instance_id BIGINT NOT NULL, node_code VARCHAR(64) NOT NULL,
                  event_sequence INT NOT NULL, from_status VARCHAR(32),
                  to_status VARCHAR(32) NOT NULL, input_json MEDIUMTEXT NOT NULL,
                  result_json MEDIUMTEXT NOT NULL, actor_member_id BIGINT NOT NULL,
                  occurred_at TIMESTAMP(6) NOT NULL,
                  PRIMARY KEY(system_id,tenant_id,instance_id,node_code,event_sequence))
                """);
        jdbc.update("INSERT INTO un_flow_definition_draft VALUES (1,2,101,1)");
        repository = new JdbcFlowExtensionRepository(jdbc, json);
    }

    @Test
    void persistsImmutableVersionFormSnapshotWriteHistoryAndCasExecutionLedger() {
        var graphV1 = graph("Approval V1");
        repository.saveDraft(1, 2, 101, 1, graphV1, "a".repeat(64), 20, NOW);
        repository.publish(1, 2, 101, 1, 1, 20, NOW);

        jdbc.update("UPDATE un_flow_definition_draft SET revision=2 "
                + "WHERE system_id=1 AND tenant_id=2 AND definition_id=101");
        repository.saveDraft(1, 2, 101, 2, graph("Approval V2"),
                "b".repeat(64), 21, NOW.plusSeconds(1));

        assertThat(repository.published(1, 2, 101, 1)).get()
                .extracting(stored -> stored.graph().requireNode("approval").name())
                .isEqualTo("Approval V1");

        var initial = json.createObjectNode().put("title", "before").put("amount", 10);
        var snapshot = repository.materializeFormSnapshot(
                1, 2, 501, 101, 1, graphV1.requireNode("approval"), initial, NOW);
        var concurrentOpen = repository.materializeFormSnapshot(
                1, 2, 501, 101, 1, graphV1.requireNode("approval"),
                json.createObjectNode().put("title", "must-not-replace"), NOW.plusSeconds(2));
        assertThat(concurrentOpen.initialValues()).isEqualTo(snapshot.initialValues());
        assertThat(repository.advanceFormSnapshot(1, 2, 501, "approval", 0)).isTrue();
        assertThat(repository.advanceFormSnapshot(1, 2, 501, "approval", 0)).isFalse();

        repository.appendFormHistory(1, 2, 501, "approval", 20, 7, 8,
                json.createObjectNode().put("amount", 11), initial,
                json.createObjectNode().put("title", "before").put("amount", 11), NOW);
        assertThat(repository.formHistory(1, 2, 501, "approval"))
                .singleElement().satisfies(history -> {
                    assertThat(history.recordVersionBefore()).isEqualTo(7);
                    assertThat(history.recordVersionAfter()).isEqualTo(8);
                    assertThat(history.changes().path("amount").asInt()).isEqualTo(11);
                });

        var engine = new FlowNodeExecutionEngine();
        var node = graphV1.requireNode("approval");
        var firstResult = engine.execute(node, json.createObjectNode(), NOW);
        var first = repository.saveExecution(1, 2, 501, 101, 1,
                "approval", FlowNodeCatalog.Type.APPROVAL, null, null,
                firstResult, json.createObjectNode(), 20, NOW);
        var continued = engine.resume(node, first.status(),
                json.createObjectNode().put("decision", "APPROVED"), NOW.plusSeconds(3));
        var second = repository.saveExecution(1, 2, 501, 101, 1,
                "approval", FlowNodeCatalog.Type.APPROVAL, 0L, first.status(),
                continued, json.createObjectNode().put("decision", "APPROVED"),
                20, NOW.plusSeconds(3));

        assertThat(first.version()).isZero();
        assertThat(second.version()).isEqualTo(1);
        assertThat(repository.executionHistory(1, 2, 501, "approval"))
                .extracting(FlowExtensionRepository.NodeExecutionEvent::toStatus)
                .containsExactly(FlowNodeExecutionEngine.Status.WAITING_HUMAN,
                        FlowNodeExecutionEngine.Status.CONTINUED);
        assertThatThrownBy(() -> repository.saveExecution(
                1, 2, 501, 101, 1, "approval", FlowNodeCatalog.Type.APPROVAL,
                0L, first.status(), continued, json.createObjectNode(), 20, NOW))
                .hasMessageContaining("version changed");
    }

    private FlowExtensionGraph.Graph graph(String approvalName) {
        var start = new FlowExtensionGraph.Node("start", "Start",
                FlowNodeCatalog.Type.START, "core", null,
                json.createObjectNode(), List.of(), List.of("approval"));
        var approval = new FlowExtensionGraph.Node("approval", approvalName,
                FlowNodeCatalog.Type.APPROVAL, "core", "Orders",
                json.createObjectNode().put("approvalMode", "SEQUENTIAL"),
                List.of(
                        new FlowExtensionGraph.FieldPolicy(
                                "title", FlowExtensionGraph.FieldMode.REQUIRED),
                        new FlowExtensionGraph.FieldPolicy(
                                "amount", FlowExtensionGraph.FieldMode.EDITABLE),
                        new FlowExtensionGraph.FieldPolicy(
                                "secret", FlowExtensionGraph.FieldMode.HIDDEN)),
                List.of("end"));
        var end = new FlowExtensionGraph.Node("end", "End",
                FlowNodeCatalog.Type.END, "core", null,
                json.createObjectNode(), List.of(), List.of());
        return new FlowExtensionGraph.Graph(List.of(start, approval, end), List.of());
    }
}
