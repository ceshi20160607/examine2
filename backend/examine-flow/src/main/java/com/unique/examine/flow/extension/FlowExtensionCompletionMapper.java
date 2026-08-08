package com.unique.examine.flow.extension;

import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps lifecycle-owned extension nodes into the existing immutable completion
 * protocol. The prefix lets a later extension save replace only its own steps,
 * preserving explicitly configured legacy completion steps.
 */
public final class FlowExtensionCompletionMapper {
    static final String STEP_PREFIX = "ext_";

    private FlowExtensionCompletionMapper() {
    }

    public static List<ApprovalCompletionStep> desired(
            ApprovalDefinitionDraft draft,
            FlowExtensionGraph.Graph graph) {
        var result = new ArrayList<ApprovalCompletionStep>();
        draft.completionSteps().stream()
                .filter(step -> !owned(step.code()))
                .forEach(result::add);
        graph.nodes().stream()
                .filter(node -> lifecycleOwned(node.type()))
                .map(node -> map(graph, node))
                .forEach(result::add);
        return ApprovalCompletionStep.requireSteps(result);
    }

    public static boolean synchronizedWith(
            ApprovalDefinitionDraft draft,
            FlowExtensionGraph.Graph graph) {
        return draft.completionSteps().equals(desired(draft, graph));
    }

    public static boolean lifecycleOwned(FlowNodeCatalog.Type type) {
        return type == FlowNodeCatalog.Type.SUBFLOW
                || type == FlowNodeCatalog.Type.WEBHOOK
                || type == FlowNodeCatalog.Type.EXTERNAL;
    }

    public static String stepCode(String nodeCode) {
        return STEP_PREFIX + nodeCode;
    }

    private static boolean owned(String code) {
        return code.startsWith(STEP_PREFIX);
    }

    private static ApprovalCompletionStep map(
            FlowExtensionGraph.Graph graph,
            FlowExtensionGraph.Node node) {
        var code = stepCode(node.code());
        return switch (node.type()) {
            case EXTERNAL -> ApprovalCompletionStep.externalTask(
                    code, node.name(), new ApprovalCompletionStep.ExternalTask(
                    node.config().path("topic").asText(),
                    node.config().path("leaseSeconds").asInt(),
                    node.config().path("maxAttempts").asInt(),
                    node.config().path("resultJsonLimitBytes").asInt()));
            case WEBHOOK -> ApprovalCompletionStep.webhook(
                    code, node.name(), new ApprovalCompletionStep.Webhook(
                    node.config().path("url").asText(),
                    optionalText(node, "secretRef"),
                    node.config().path("timeoutSeconds").asInt(),
                    node.config().path("maxAttempts").asInt(),
                    node.config().path("baseBackoffSeconds").asInt()));
            case SUBFLOW -> {
                var targetId = node.config().path("definitionId").asLong();
                var dependency = graph.dependencies().stream()
                        .filter(value -> value.sourceNodeCode().equals(node.code())
                                && value.type()
                                == FlowExtensionGraph.DependencyType.FLOW_DEFINITION
                                && value.versionMode()
                                == FlowExtensionGraph.VersionMode.EXACT)
                        .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                "Subflow requires an exact published dependency"));
                if (!dependency.targetKey().equals(Long.toString(targetId))) {
                    throw new IllegalArgumentException(
                            "Subflow definition and dependency target must match");
                }
                yield ApprovalCompletionStep.subflow(
                        code, node.name(), new ApprovalCompletionStep.Subflow(
                        targetId, Math.toIntExact(dependency.requiredVersion())));
            }
            default -> throw new IllegalArgumentException(
                    "Flow node is not owned by the completion protocol");
        };
    }

    private static String optionalText(
            FlowExtensionGraph.Node node, String field) {
        var value = node.config().path(field);
        return value.isTextual() && !value.textValue().isBlank()
                ? value.textValue().strip() : null;
    }
}
