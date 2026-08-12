package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

public final class FlowExtensionGraph {
    private FlowExtensionGraph() {
    }

    public enum FieldMode {
        VISIBLE,
        EDITABLE,
        REQUIRED,
        HIDDEN;

        public boolean readable() {
            return this != HIDDEN;
        }

        public boolean editable() {
            return this == EDITABLE || this == REQUIRED;
        }
    }

    public enum DependencyType {
        FLOW_DEFINITION,
        MODULE_CONFIGURATION,
        OPENAPI_APPLICATION,
        MESSAGE_TEMPLATE,
        AI_POLICY
    }

    public enum VersionMode {
        EXACT,
        MINIMUM
    }

    public record FieldPolicy(String fieldCode, FieldMode mode) {
        public FieldPolicy {
            fieldCode = token(fieldCode, "field code");
            if (mode == null) {
                throw new IllegalArgumentException("Flow node field mode is required");
            }
        }
    }

    public record Dependency(
            String sourceNodeCode,
            DependencyType type,
            long targetSystemId,
            Long targetTenantId,
            String targetKey,
            long requiredVersion,
            VersionMode versionMode
    ) {
        public Dependency {
            sourceNodeCode = token(sourceNodeCode, "dependency source node code");
            if (type == null || targetSystemId <= 0 || requiredVersion <= 0
                    || versionMode == null || targetKey == null
                    || targetKey.isBlank() || targetKey.length() > 160
                    || targetTenantId != null && targetTenantId <= 0) {
                throw new IllegalArgumentException("Flow dependency is invalid");
            }
            targetKey = targetKey.strip();
        }
    }

    public record Node(
            String code,
            String name,
            FlowNodeCatalog.Type type,
            String applicationCode,
            String moduleCode,
            JsonNode config,
            List<FieldPolicy> fieldPolicies,
            List<String> next
    ) {
        public Node {
            code = token(code, "node code");
            if (name == null || name.isBlank() || name.strip().length() > 128
                    || type == null) {
                throw new IllegalArgumentException("Flow node identity is invalid");
            }
            name = name.strip();
            applicationCode = token(applicationCode, "application code");
            moduleCode = optionalModule(moduleCode);
            config = config == null ? JsonNodeFactory.instance.objectNode() : config.deepCopy();
            if (!config.isObject()) {
                throw new IllegalArgumentException("Flow node config must be an object");
            }
            fieldPolicies = fieldPolicies == null ? List.of() : List.copyOf(fieldPolicies);
            next = next == null ? List.of() : next.stream()
                    .map(value -> token(value, "next node code")).toList();
            if (Set.of(FlowNodeCatalog.Type.SUBFLOW,
                    FlowNodeCatalog.Type.WEBHOOK,
                    FlowNodeCatalog.Type.EXTERNAL).contains(type)
                    && (!code.matches("^[a-z][a-z0-9_]{0,59}$")
                    || name.codePointCount(0, name.length()) > 80)) {
                throw new IllegalArgumentException(
                        "Lifecycle-owned Flow node identity must fit the completion protocol");
            }
            requireUniquePolicies(fieldPolicies);
            validateConfig(type, moduleCode, config, fieldPolicies, next);
        }
    }

    public record Graph(List<Node> nodes, List<Dependency> dependencies) {
        public Graph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            if (nodes.size() < 2 || nodes.size() > 200 || dependencies.size() > 500) {
                throw new IllegalArgumentException("Flow extension graph size is invalid");
            }
            validateGraph(nodes, dependencies);
        }

        public Node requireNode(String nodeCode) {
            return nodes.stream().filter(node -> node.code().equals(nodeCode))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Flow node was not found: " + nodeCode));
        }
    }

    private static void validateGraph(List<Node> nodes, List<Dependency> dependencies) {
        var byCode = new HashMap<String, Node>();
        for (var node : nodes) {
            if (byCode.put(node.code(), node) != null) {
                throw new IllegalArgumentException("Duplicate Flow node code: " + node.code());
            }
        }
        var starts = nodes.stream().filter(node -> node.type() == FlowNodeCatalog.Type.START).toList();
        var ends = nodes.stream().filter(node -> node.type() == FlowNodeCatalog.Type.END).toList();
        if (starts.size() != 1 || ends.isEmpty()) {
            throw new IllegalArgumentException("Flow graph requires exactly one start and at least one end");
        }
        for (var node : nodes) {
            for (var target : node.next()) {
                if (!byCode.containsKey(target)) {
                    throw new IllegalArgumentException(
                            "Flow node " + node.code() + " references missing node " + target);
                }
            }
        }
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(starts.getFirst().code());
        while (!queue.isEmpty()) {
            var code = queue.removeFirst();
            if (visited.add(code)) {
                queue.addAll(byCode.get(code).next());
            }
        }
        if (visited.size() != nodes.size()) {
            throw new IllegalArgumentException("Every Flow node must be reachable from start");
        }
        var dependencyKeys = new HashSet<String>();
        for (var dependency : dependencies) {
            if (!byCode.containsKey(dependency.sourceNodeCode())) {
                throw new IllegalArgumentException("Flow dependency source node does not exist");
            }
            var key = dependency.sourceNodeCode() + "|" + dependency.type() + "|"
                    + dependency.targetSystemId() + "|" + dependency.targetTenantId()
                    + "|" + dependency.targetKey();
            if (!dependencyKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate Flow dependency: " + key);
            }
        }
        for (var node : nodes) {
            if (requiresDependency(node.type())
                    && dependencies.stream().noneMatch(dependency ->
                    dependency.sourceNodeCode().equals(node.code())
                            && compatible(node.type(), dependency.type())
                            && (node.type() != FlowNodeCatalog.Type.SUBFLOW
                            || dependency.versionMode() == VersionMode.EXACT))) {
                throw new IllegalArgumentException(
                        "Flow node " + node.code() + " requires a declared publication dependency");
            }
        }
    }

    private static void validateConfig(
            FlowNodeCatalog.Type type,
            String moduleCode,
            JsonNode config,
            List<FieldPolicy> policies,
            List<String> next) {
        FlowNodeCatalog.require(type);
        if (type == FlowNodeCatalog.Type.END) {
            if (!next.isEmpty()) {
                throw new IllegalArgumentException("End node cannot have outgoing edges");
            }
        } else if (next.isEmpty()) {
            throw new IllegalArgumentException("Non-end Flow node requires an outgoing edge");
        }
        if (Set.of(
                FlowNodeCatalog.Type.CONDITIONAL_BRANCH,
                FlowNodeCatalog.Type.PARALLEL_GATEWAY,
                FlowNodeCatalog.Type.INCLUSIVE_GATEWAY).contains(type)
                && (next.size() < 2 || !config.path("routes").isArray()
                || config.path("routes").size() < 2)) {
            throw new IllegalArgumentException("Branch node requires at least two executable routes");
        }
        if (FlowNodeCatalog.require(type).requiresBusinessRecord() && moduleCode == null) {
            throw new IllegalArgumentException("Flow node " + type + " requires a business module");
        }
        if (!policies.isEmpty() && !Set.of(
                FlowNodeCatalog.Type.APPROVAL,
                FlowNodeCatalog.Type.CONDITIONAL_APPROVAL,
                FlowNodeCatalog.Type.FORM).contains(type)) {
            throw new IllegalArgumentException("Field policies are only valid for approval and form nodes");
        }
        switch (type) {
            case START, MERGE_GATEWAY, END -> {
            }
            case APPROVAL -> requireText(config, "approvalMode");
            case CONDITIONAL_APPROVAL -> requireObjectOrArray(config, "condition");
            case COPY -> requireMemberArray(config, "recipients");
            case CONDITIONAL_BRANCH, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY ->
                    requireArray(config, "routes");
            case SUBFLOW -> requirePositive(config, "definitionId");
            case AUTOMATION -> requireText(config, "operation");
            case FORM -> {
                requireText(config, "formAction");
                if (policies.isEmpty()) {
                    throw new IllegalArgumentException("Form node requires field policies");
                }
            }
            case TASK -> {
                requireText(config, "taskKind");
                if (!"WORK_TASK".equals(config.path("taskKind").asText())) {
                    throw new IllegalArgumentException(
                            "Task node taskKind must be WORK_TASK");
                }
                requirePositive(config, "assigneeMemberId");
                requireBoundedText(config, "title", 500, false);
                requireBoundedText(config, "description", 2_000, true);
                if (config.hasNonNull("projectId")) {
                    requirePositive(config, "projectId");
                }
                if (config.hasNonNull("dueAt")) {
                    try {
                        Instant.parse(config.path("dueAt").asText());
                    } catch (RuntimeException invalid) {
                        throw new IllegalArgumentException(
                                "Task node dueAt must be an ISO-8601 instant", invalid);
                    }
                }
            }
            case NOTIFICATION, MESSAGE -> {
                requireText(config, "templateCode");
                requireMemberArray(config, "recipients");
            }
            case FIELD_UPDATE -> requireObject(config, "values");
            case DATA_CREATE_UPDATE -> {
                requireText(config, "operation");
                requireText(config, "targetModuleCode");
                requireObject(config, "values");
            }
            case WAIT -> requireText(config, "eventKey");
            case TIMER -> {
                if (!config.hasNonNull("resumeAt") && config.path("delaySeconds").asLong(0) <= 0) {
                    throw new IllegalArgumentException("Timer node requires resumeAt or delaySeconds");
                }
            }
            case WEBHOOK -> {
                requireText(config, "endpointCode");
                requireText(config, "url");
                requireRange(config, "timeoutSeconds", 1, 30);
                requireRange(config, "maxAttempts", 1, 10);
                requireRange(config, "baseBackoffSeconds", 1, 300);
            }
            case EXTERNAL -> {
                requireText(config, "topic");
                requireRange(config, "leaseSeconds", 30, 900);
                requireRange(config, "maxAttempts", 1, 10);
                requireRange(config, "resultJsonLimitBytes", 1, 8192);
            }
            case AI_ASSIST -> {
                requireText(config, "modelPolicyCode");
                requireCode(config, "fieldCode");
                if (!config.path("confirmationRequired").asBoolean(false)) {
                    throw new IllegalArgumentException("AI assist must require human confirmation");
                }
            }
        }
    }

    private static boolean requiresDependency(FlowNodeCatalog.Type type) {
        return Set.of(
                FlowNodeCatalog.Type.SUBFLOW,
                FlowNodeCatalog.Type.NOTIFICATION,
                FlowNodeCatalog.Type.MESSAGE,
                FlowNodeCatalog.Type.WEBHOOK,
                FlowNodeCatalog.Type.AI_ASSIST).contains(type);
    }

    private static boolean compatible(
            FlowNodeCatalog.Type nodeType, DependencyType dependencyType) {
        return switch (nodeType) {
            case SUBFLOW -> dependencyType == DependencyType.FLOW_DEFINITION;
            case NOTIFICATION, MESSAGE ->
                    dependencyType == DependencyType.MESSAGE_TEMPLATE;
            case WEBHOOK -> dependencyType == DependencyType.OPENAPI_APPLICATION;
            case AI_ASSIST -> dependencyType == DependencyType.AI_POLICY;
            default -> true;
        };
    }

    private static void requireUniquePolicies(List<FieldPolicy> policies) {
        var codes = new HashSet<String>();
        for (var policy : policies) {
            if (!codes.add(policy.fieldCode())) {
                throw new IllegalArgumentException(
                        "Duplicate Flow node field policy: " + policy.fieldCode());
            }
        }
    }

    private static void requireText(JsonNode config, String field) {
        var value = config.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Flow node config requires " + field);
        }
    }

    private static void requireArray(JsonNode config, String field) {
        if (!config.path(field).isArray() || config.path(field).isEmpty()) {
            throw new IllegalArgumentException("Flow node config requires non-empty " + field);
        }
    }

    private static void requireMemberArray(JsonNode config, String field) {
        requireArray(config, field);
        var unique = new HashSet<Long>();
        for (var value : config.path(field)) {
            var valid = value.isIntegralNumber() && value.canConvertToLong()
                    && value.asLong() > 0;
            long memberId = valid ? value.asLong() : 0;
            if (!valid && value.isTextual()) {
                try {
                    memberId = Long.parseLong(value.textValue());
                    valid = memberId > 0;
                } catch (NumberFormatException ignored) {
                    valid = false;
                }
            }
            if (!valid || !unique.add(memberId)) {
                throw new IllegalArgumentException(
                        "Flow node config requires unique positive member ids in " + field);
            }
        }
    }

    private static void requireBoundedText(
            JsonNode config, String field, int maximum, boolean optional) {
        var value = config.path(field);
        if (optional && (value.isMissingNode() || value.isNull())) return;
        if (!value.isTextual() || value.textValue().isBlank()
                || value.textValue().strip().codePointCount(
                0, value.textValue().strip().length()) > maximum) {
            throw new IllegalArgumentException(
                    "Flow node config " + field + " is invalid");
        }
    }

    private static void requireObject(JsonNode config, String field) {
        if (!config.path(field).isObject() || config.path(field).isEmpty()) {
            throw new IllegalArgumentException("Flow node config requires non-empty " + field);
        }
    }

    private static void requireObjectOrArray(JsonNode config, String field) {
        var value = config.path(field);
        if (!(value.isObject() || value.isArray()) || value.isEmpty()) {
            throw new IllegalArgumentException("Flow node config requires " + field);
        }
    }

    private static void requirePositive(JsonNode config, String field) {
        if (!config.path(field).canConvertToLong() || config.path(field).asLong() <= 0) {
            throw new IllegalArgumentException("Flow node config requires positive " + field);
        }
    }

    private static void requireRange(
            JsonNode config, String field, int minimum, int maximum) {
        var value = config.path(field);
        if (!value.canConvertToInt()
                || value.asInt() < minimum || value.asInt() > maximum) {
            throw new IllegalArgumentException(
                    "Flow node config " + field + " must be within "
                            + minimum + ".." + maximum);
        }
    }

    private static void requireCode(JsonNode config, String field) {
        var value = config.path(field);
        if (!value.isTextual()
                || !value.textValue().matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Flow node config requires " + field);
        }
    }

    private static String optionalModule(String value) {
        if (value == null || value.isBlank()) return null;
        var stripped = value.strip();
        if (!stripped.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Flow module code is invalid");
        }
        return stripped;
    }

    private static String token(String value, String name) {
        if (value == null || !value.matches("^[a-z][a-z0-9_.-]{0,63}$")) {
            throw new IllegalArgumentException("Flow " + name + " is invalid");
        }
        return value;
    }
}
