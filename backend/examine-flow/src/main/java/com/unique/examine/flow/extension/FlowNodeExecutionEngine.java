package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class FlowNodeExecutionEngine {
    public enum Status {
        CONTINUED,
        WAITING_HUMAN,
        WAITING_EVENT,
        WAITING_TIMER,
        WAITING_EXTERNAL,
        WAITING_CONFIRMATION,
        COMPLETED
    }

    public record Result(Status status, ObjectNode output) {
        public Result {
            Objects.requireNonNull(status, "status");
            output = output == null ? JsonNodeFactory.instance.objectNode() : output.deepCopy();
        }
    }

    public Result execute(FlowExtensionGraph.Node node, JsonNode input, Instant now) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(now, "now");
        var supplied = input == null || !input.isObject()
                ? JsonNodeFactory.instance.objectNode() : input;
        var output = JsonNodeFactory.instance.objectNode();
        output.put("executor", FlowNodeCatalog.require(node.type()).executor());
        output.put("nodeCode", node.code());
        output.put("executedAt", now.toString());
        return switch (node.type()) {
            case START -> result(Status.CONTINUED, output.put("started", true));
            case APPROVAL, CONDITIONAL_APPROVAL -> result(
                    Status.WAITING_HUMAN, output.put("decisionRequired", true));
            case COPY -> result(Status.CONTINUED,
                    output.set("copyRecipients", node.config().path("recipients").deepCopy()));
            case CONDITIONAL_BRANCH -> result(Status.CONTINUED,
                    output.put("selectedRoute", requireText(supplied, "selectedRoute")));
            case PARALLEL_GATEWAY -> result(Status.CONTINUED,
                    output.set("activatedRoutes", node.config().path("routes").deepCopy()));
            case INCLUSIVE_GATEWAY -> result(Status.CONTINUED,
                    output.set("activatedRoutes", requireArray(supplied, "selectedRoutes").deepCopy()));
            case MERGE_GATEWAY -> result(Status.CONTINUED,
                    output.put("joined", supplied.path("completedRoutes").size()));
            case SUBFLOW -> result(Status.WAITING_EXTERNAL,
                    output.put("childDefinitionId", node.config().path("definitionId").asLong()));
            case AUTOMATION -> result(Status.CONTINUED,
                    output.put("operation", node.config().path("operation").asText()));
            case FORM -> result(Status.WAITING_HUMAN, output.put("formRequired", true));
            case TASK -> result(Status.WAITING_HUMAN,
                    output.put("taskKind", node.config().path("taskKind").asText()));
            case NOTIFICATION -> result(Status.CONTINUED,
                    delivery(output, node, "notificationRequested"));
            case FIELD_UPDATE -> result(Status.CONTINUED,
                    output.set("recordChanges", node.config().path("values").deepCopy()));
            case DATA_CREATE_UPDATE -> result(Status.CONTINUED,
                    output.put("recordOperation", node.config().path("operation").asText())
                            .put("targetModuleCode", node.config().path("targetModuleCode").asText())
                            .set("recordValues", node.config().path("values").deepCopy()));
            case WAIT -> result(Status.WAITING_EVENT,
                    output.put("eventKey", node.config().path("eventKey").asText()));
            case TIMER -> result(Status.WAITING_TIMER,
                    output.put("resumeAt", resumeAt(node.config(), now).toString()));
            case MESSAGE -> result(Status.CONTINUED,
                    delivery(output, node, "messageRequested"));
            case WEBHOOK -> result(Status.WAITING_EXTERNAL,
                    output.put("endpointCode", node.config().path("endpointCode").asText()));
            case EXTERNAL -> result(Status.WAITING_EXTERNAL,
                    output.put("topic", node.config().path("topic").asText()));
            case AI_ASSIST -> result(Status.WAITING_CONFIRMATION,
                    output.put("modelPolicyCode", node.config().path("modelPolicyCode").asText())
                            .put("confirmationRequired", true));
            case END -> result(Status.COMPLETED, output.put("ended", true));
        };
    }

    public Result resume(
            FlowExtensionGraph.Node node,
            Status current,
            JsonNode input,
            Instant now) {
        Objects.requireNonNull(current, "current");
        var supplied = input == null || !input.isObject()
                ? JsonNodeFactory.instance.objectNode() : input;
        if (!Set.of(Status.WAITING_HUMAN, Status.WAITING_EVENT,
                Status.WAITING_TIMER, Status.WAITING_EXTERNAL,
                Status.WAITING_CONFIRMATION).contains(current)) {
            throw new IllegalArgumentException("Flow node is not waiting for continuation");
        }
        if (current == Status.WAITING_TIMER
                && now.isBefore(Instant.parse(requireText(supplied, "scheduledResumeAt")))) {
            throw new IllegalArgumentException("Flow timer cannot resume before its snapshot deadline");
        }
        if (current == Status.WAITING_CONFIRMATION
                && !supplied.path("confirmed").asBoolean(false)) {
            throw new IllegalArgumentException("AI assist output requires explicit human confirmation");
        }
        if (current == Status.WAITING_EXTERNAL
                && !supplied.path("succeeded").asBoolean(false)) {
            throw new IllegalArgumentException("External Flow node requires a successful callback");
        }
        var output = JsonNodeFactory.instance.objectNode();
        output.put("executor", FlowNodeCatalog.require(node.type()).executor());
        output.put("nodeCode", node.code());
        output.put("resumedAt", now.toString());
        output.set("continuation", supplied.deepCopy());
        return result(Status.CONTINUED, output);
    }

    private static ObjectNode delivery(
            ObjectNode output, FlowExtensionGraph.Node node, String flag) {
        return output.put(flag, true)
                .put("templateCode", node.config().path("templateCode").asText());
    }

    private static Instant resumeAt(JsonNode config, Instant now) {
        if (config.hasNonNull("resumeAt")) {
            return Instant.parse(config.path("resumeAt").asText());
        }
        return now.plusSeconds(config.path("delaySeconds").asLong());
    }

    private static String requireText(JsonNode value, String field) {
        var node = value.path(field);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException("Flow node continuation requires " + field);
        }
        return node.textValue();
    }

    private static JsonNode requireArray(JsonNode value, String field) {
        var node = value.path(field);
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("Flow node continuation requires " + field);
        }
        return node;
    }

    private static Result result(Status status, JsonNode output) {
        return new Result(status, (ObjectNode) output);
    }
}
