package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowNodeCatalogExecutionTest {
    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final FlowNodeExecutionEngine engine = new FlowNodeExecutionEngine();

    @Test
    void frozenCatalogContainsEveryRequiredTypeAndEveryTypeHasARealExecutor() {
        assertThat(FlowNodeCatalog.entries())
                .extracting(FlowNodeCatalog.Entry::type)
                .containsExactlyElementsOf(Arrays.asList(FlowNodeCatalog.Type.values()));
        assertThat(FlowNodeCatalog.entries())
                .allSatisfy(entry -> {
                    assertThat(entry.name()).isNotBlank();
                    assertThat(entry.executor()).isNotBlank();
                    var result = engine.execute(node(entry.type(), "end"),
                            executionInput(entry.type()), NOW);
                    assertThat(result.status()).isNotNull();
                    assertThat(result.output().path("executor").asText())
                            .isEqualTo(entry.executor());
                });
    }

    @Test
    void oneValidatedGraphCanUseTheEntireFrozenNodeCatalog() {
        var types = FlowNodeCatalog.entries().stream()
                .map(FlowNodeCatalog.Entry::type).toList();
        var nodes = new ArrayList<FlowExtensionGraph.Node>();
        for (var index = 0; index < types.size(); index++) {
            var type = types.get(index);
            var next = index + 1 < types.size()
                    ? types.get(index + 1).name().toLowerCase() : "end";
            nodes.add(node(type, next));
        }
        var dependencies = List.of(
                dependency("subflow", FlowExtensionGraph.DependencyType.FLOW_DEFINITION, "901"),
                dependency("notification", FlowExtensionGraph.DependencyType.MESSAGE_TEMPLATE, "notice_v1"),
                dependency("message", FlowExtensionGraph.DependencyType.MESSAGE_TEMPLATE, "message_v1"),
                dependency("webhook", FlowExtensionGraph.DependencyType.OPENAPI_APPLICATION, "flow_callback_app"),
                dependency("ai_assist", FlowExtensionGraph.DependencyType.AI_POLICY, "701")
        );

        var graph = new FlowExtensionGraph.Graph(nodes, dependencies);

        assertThat(graph.nodes()).hasSize(FlowNodeCatalog.Type.values().length);
        assertThat(graph.nodes()).extracting(FlowExtensionGraph.Node::type)
                .containsExactlyElementsOf(types);
        assertThat(graph.dependencies()).hasSize(5);
    }

    @Test
    void waitingExecutorsRejectUnsafeContinuationAndAcceptProvenContinuation() {
        var timer = node(FlowNodeCatalog.Type.TIMER, "end");
        assertThatThrownBy(() -> engine.resume(timer,
                FlowNodeExecutionEngine.Status.WAITING_TIMER,
                json.createObjectNode().put("scheduledResumeAt", NOW.plusSeconds(30).toString()),
                NOW)).hasMessageContaining("before its snapshot deadline");

        var ai = node(FlowNodeCatalog.Type.AI_ASSIST, "end");
        assertThatThrownBy(() -> engine.resume(ai,
                FlowNodeExecutionEngine.Status.WAITING_CONFIRMATION,
                json.createObjectNode().put("confirmed", false), NOW))
                .hasMessageContaining("explicit human confirmation");

        var external = node(FlowNodeCatalog.Type.EXTERNAL, "end");
        assertThatThrownBy(() -> engine.resume(external,
                FlowNodeExecutionEngine.Status.WAITING_EXTERNAL,
                json.createObjectNode().put("succeeded", false), NOW))
                .hasMessageContaining("successful callback");

        assertThat(engine.resume(ai, FlowNodeExecutionEngine.Status.WAITING_CONFIRMATION,
                json.createObjectNode().put("confirmed", true), NOW).status())
                .isEqualTo(FlowNodeExecutionEngine.Status.CONTINUED);
        assertThat(engine.resume(external, FlowNodeExecutionEngine.Status.WAITING_EXTERNAL,
                json.createObjectNode().put("succeeded", true), NOW).status())
                .isEqualTo(FlowNodeExecutionEngine.Status.CONTINUED);
    }

    private FlowExtensionGraph.Node node(FlowNodeCatalog.Type type, String nextCode) {
        var code = type.name().toLowerCase();
        var next = switch (type) {
            case END -> List.<String>of();
            case CONDITIONAL_BRANCH, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY ->
                    List.of(nextCode, "end");
            default -> List.of(nextCode);
        };
        var config = config(type, next);
        var policies = switch (type) {
            case APPROVAL, CONDITIONAL_APPROVAL, FORM -> List.of(
                    new FlowExtensionGraph.FieldPolicy(
                            "title", FlowExtensionGraph.FieldMode.REQUIRED),
                    new FlowExtensionGraph.FieldPolicy(
                            "amount", FlowExtensionGraph.FieldMode.EDITABLE),
                    new FlowExtensionGraph.FieldPolicy(
                            "secret", FlowExtensionGraph.FieldMode.HIDDEN));
            default -> List.<FlowExtensionGraph.FieldPolicy>of();
        };
        var module = FlowNodeCatalog.require(type).requiresBusinessRecord()
                ? "Orders" : null;
        return new FlowExtensionGraph.Node(code, type.name(), type, "core",
                module, config, policies, next);
    }

    private ObjectNode config(FlowNodeCatalog.Type type, List<String> next) {
        var value = json.createObjectNode();
        switch (type) {
            case APPROVAL -> value.put("approvalMode", "SEQUENTIAL");
            case CONDITIONAL_APPROVAL -> value.putObject("condition")
                    .put("expression", "amount > 100");
            case COPY -> value.putArray("recipients").add(30);
            case CONDITIONAL_BRANCH, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY -> {
                var routes = value.putArray("routes");
                next.forEach(routes::add);
            }
            case SUBFLOW -> value.put("definitionId", 901);
            case AUTOMATION -> value.put("operation", "RECALCULATE");
            case FORM -> value.put("formAction", "EDIT");
            case TASK -> value.put("taskKind", "WORK_TASK")
                    .put("assigneeMemberId", 30)
                    .put("title", "Review the Flow result");
            case NOTIFICATION, MESSAGE -> {
                value.put("templateCode", "notice_v1");
                value.putArray("recipients").add(30);
            }
            case FIELD_UPDATE -> value.putObject("values").put("status", "APPROVED");
            case DATA_CREATE_UPDATE -> value.put("operation", "CREATE")
                    .put("targetModuleCode", "Orders")
                    .putObject("values").put("status", "NEW");
            case WAIT -> value.put("eventKey", "payment.received");
            case TIMER -> value.put("delaySeconds", 30);
            case WEBHOOK -> value.put("endpointCode", "order_callback")
                    .put("url", "https://callback.example.test/flow")
                    .put("timeoutSeconds", 5)
                    .put("maxAttempts", 3)
                    .put("baseBackoffSeconds", 10);
            case EXTERNAL -> value.put("topic", "warehouse.pick")
                    .put("leaseSeconds", 60)
                    .put("maxAttempts", 3)
                    .put("resultJsonLimitBytes", 4096);
            case AI_ASSIST -> value.put("modelPolicyCode", "flow_advice")
                    .put("fieldCode", "summary")
                    .put("confirmationRequired", true);
            case START, MERGE_GATEWAY, END -> { }
        }
        return value;
    }

    private ObjectNode executionInput(FlowNodeCatalog.Type type) {
        var value = json.createObjectNode();
        if (type == FlowNodeCatalog.Type.CONDITIONAL_BRANCH) {
            value.put("selectedRoute", "end");
        }
        if (type == FlowNodeCatalog.Type.INCLUSIVE_GATEWAY) {
            value.putArray("selectedRoutes").add("end");
        }
        return value;
    }

    private static FlowExtensionGraph.Dependency dependency(
            String nodeCode,
            FlowExtensionGraph.DependencyType type,
            String key) {
        return new FlowExtensionGraph.Dependency(
                nodeCode, type, 1, 2L, key, 1,
                FlowExtensionGraph.VersionMode.EXACT);
    }
}
