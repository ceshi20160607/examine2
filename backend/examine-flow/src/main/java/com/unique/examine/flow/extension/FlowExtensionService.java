package com.unique.examine.flow.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.FlowDraftPreflight;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowExtensionService {
    private final FlowExtensionRepository repository;
    private final FlowRequestServiceFactory workflows;
    private final FlowBusinessFormPort businessForms;
    private final FlowNodeEffectPort nodeEffects;
    private final FlowNodeExecutionEngine nodeExecutions;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public FlowExtensionService(
            FlowExtensionRepository repository,
            FlowRequestServiceFactory workflows,
            FlowBusinessFormPort businessForms,
            FlowNodeEffectPort nodeEffects,
            ObjectMapper json) {
        this(repository, workflows, businessForms, nodeEffects,
                json, Clock.systemUTC());
    }

    FlowExtensionService(
            FlowExtensionRepository repository,
            FlowRequestServiceFactory workflows,
            FlowBusinessFormPort businessForms,
            FlowNodeEffectPort nodeEffects,
            ObjectMapper json,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.businessForms = Objects.requireNonNull(businessForms, "businessForms");
        this.nodeEffects = Objects.requireNonNull(nodeEffects, "nodeEffects");
        this.nodeExecutions = new FlowNodeExecutionEngine();
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<FlowNodeCatalog.Entry> catalog() {
        return FlowNodeCatalog.entries();
    }

    public FlowExtensionRepository.StoredGraph draft(
            FlowSession session, long definitionId) {
        Objects.requireNonNull(session, "session");
        workflow(session).definitionDraft(definitionId);
        return repository.draft(
                session.systemId(), session.tenantId(), definitionId).orElse(null);
    }

    @Transactional
    public FlowExtensionRepository.StoredGraph saveDraft(
            FlowSession session,
            long definitionId,
            int expectedRevision,
            FlowExtensionGraph.Graph graph) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(graph, "graph");
        var workflow = workflow(session);
        var draft = workflow.definitionDraft(definitionId);
        if (draft.revision() != expectedRevision) {
            throw conflict("FLOW_EXTENSION_DRAFT_VERSION_CONFLICT",
                    "Flow definition draft revision changed");
        }
        validateOwnerScopes(session, graph);
        var completionSteps = FlowExtensionCompletionMapper.desired(draft, graph);
        draft = workflow.reviseCompletionSteps(definitionId, completionSteps);
        return repository.saveDraft(
                session.systemId(), session.tenantId(), definitionId,
                draft.revision(), graph, checksum(graph), session.memberId(),
                clock.instant());
    }

    public ImpactReport impact(FlowSession session, long definitionId) {
        var draft = workflow(session).definitionDraft(definitionId);
        var stored = repository.draft(
                session.systemId(), session.tenantId(), definitionId).orElse(null);
        if (stored == null) {
            return new ImpactReport(definitionId, draft.revision(), true,
                    List.of(), repository.inboundConsumers(
                    session.systemId(), session.tenantId(), definitionId),
                    List.of());
        }
        var dependencies = new ArrayList<DependencyImpact>();
        var issues = new ArrayList<ImpactIssue>();
        if (stored.sourceRevision() != draft.revision()) {
            issues.add(new ImpactIssue("BLOCKER", "FLOW_EXTENSION_REVISION_STALE",
                    "/extension/sourceRevision",
                    "Node graph belongs to draft revision " + stored.sourceRevision()
                            + " but current revision is " + draft.revision()));
        }
        for (var dependency : stored.graph().dependencies()) {
            var state = repository.resolve(session.tenantId(), dependency);
            var compatible = compatible(state);
            dependencies.add(new DependencyImpact(
                    dependency.sourceNodeCode(), dependency.type().name(),
                    dependency.targetSystemId(), dependency.targetTenantId(),
                    dependency.targetKey(), dependency.requiredVersion(),
                    dependency.versionMode().name(), state.currentVersion(),
                    state.exists(), state.active(), compatible, state.reason()));
            if (!compatible) {
                issues.add(new ImpactIssue("BLOCKER", "FLOW_DEPENDENCY_INCOMPATIBLE",
                        "/nodes/" + dependency.sourceNodeCode() + "/dependencies",
                        dependency.type() + " " + dependency.targetKey()
                                + " requires " + dependency.versionMode() + " version "
                                + dependency.requiredVersion() + " but current is "
                                + (state.currentVersion() == null ? "missing" : state.currentVersion())));
            }
        }
        var inbound = repository.inboundConsumers(
                session.systemId(), session.tenantId(), definitionId);
        if (!inbound.isEmpty()) {
            issues.add(new ImpactIssue("WARNING", "FLOW_CROSS_APPLICATION_CONSUMERS",
                    "/impact/inboundConsumers",
                    inbound.size() + " published cross-application node(s) depend on this Flow"));
        }
        var ready = issues.stream().noneMatch(issue -> "BLOCKER".equals(issue.severity()));
        return new ImpactReport(definitionId, draft.revision(), ready,
                List.copyOf(dependencies), inbound, List.copyOf(issues));
    }

    public List<FlowDraftPreflight.Issue> preflight(
            FlowSession session, ApprovalDefinitionDraft draft) {
        var stored = repository.draft(
                session.systemId(), session.tenantId(), draft.id()).orElse(null);
        if (stored == null) return List.of();
        return impact(session, draft.id()).issues().stream().map(issue ->
                new FlowDraftPreflight.Issue(
                        "BLOCKER".equals(issue.severity())
                                ? FlowDraftPreflight.Severity.BLOCKER
                                : FlowDraftPreflight.Severity.WARNING,
                        issue.code(), issue.path(), issue.message())).toList();
    }

    @Transactional
    public void published(
            FlowSession session,
            ApprovalDefinitionDraft draft,
            ApprovalDefinitionVersion version) {
        var extension = repository.draft(
                session.systemId(), session.tenantId(), draft.id());
        if (extension.isEmpty()) return;
        repository.publish(
                session.systemId(), session.tenantId(), draft.id(),
                version.version(), draft.revision(), session.memberId(),
                version.publishedAt());
    }

    @Transactional
    public FormView form(FlowSession session, long instanceId, String nodeCode) {
        var context = formContext(session, instanceId, nodeCode);
        var current = businessForms.read(actor(session),
                context.binding.moduleCode(), context.binding.recordId());
        var initial = object(current.values());
        var snapshot = repository.materializeFormSnapshot(
                session.systemId(), session.tenantId(), instanceId,
                context.instance.definitionId(), context.instance.definitionVersion(),
                context.node, initial, clock.instant());
        return formView(current, snapshot);
    }

    @Transactional
    public FormWriteResult writeForm(
            FlowSession session,
            long instanceId,
            String nodeCode,
            long expectedSnapshotVersion,
            long expectedRecordVersion,
            Map<String, JsonNode> changes,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var context = formContext(session, instanceId, nodeCode);
        var supplied = new LinkedHashMap<String, JsonNode>();
        if (changes != null) {
            changes.forEach((field, value) -> supplied.put(field,
                    value == null ? JsonNodeFactory.instance.nullNode() : value));
        }
        if (supplied.isEmpty()) {
            throw invalid("FLOW_FORM_CHANGES_REQUIRED",
                    "Flow form write requires at least one field change");
        }
        var current = businessForms.read(actor(session),
                context.binding.moduleCode(), context.binding.recordId());
        if (current.version() != expectedRecordVersion) {
            throw conflict("FLOW_FORM_RECORD_VERSION_CONFLICT",
                    "Business record version changed");
        }
        var snapshot = repository.materializeFormSnapshot(
                session.systemId(), session.tenantId(), instanceId,
                context.instance.definitionId(), context.instance.definitionVersion(),
                context.node, object(current.values()), clock.instant());
        if (snapshot.version() != expectedSnapshotVersion) {
            throw conflict("FLOW_FORM_SNAPSHOT_VERSION_CONFLICT",
                    "Flow form snapshot version changed");
        }
        var policies = policies(snapshot.policies());
        for (var field : supplied.keySet()) {
            var policy = policies.get(field);
            var capability = current.capabilities().get(field);
            if (policy == null || !policy.mode().editable()
                    || capability == null || !capability.writable()) {
                throw forbidden("FLOW_FORM_FIELD_WRITE_FORBIDDEN",
                        "Field " + field + " is not editable at node " + nodeCode);
            }
        }
        var merged = new LinkedHashMap<>(current.values());
        merged.putAll(supplied);
        for (var policy : snapshot.policies()) {
            if (policy.mode() == FlowExtensionGraph.FieldMode.REQUIRED
                    && missing(merged.get(policy.fieldCode()))) {
                throw invalid("FLOW_FORM_REQUIRED_FIELD_MISSING",
                        "Field " + policy.fieldCode() + " is required at node " + nodeCode);
            }
        }
        var changed = supplied.entrySet().stream().anyMatch(entry ->
                !Objects.equals(current.values().get(entry.getKey()), entry.getValue()));
        if (!changed) {
            throw invalid("FLOW_FORM_NO_CHANGES",
                    "Flow form write did not change any field");
        }
        if (!repository.advanceFormSnapshot(
                session.systemId(), session.tenantId(), instanceId,
                nodeCode, expectedSnapshotVersion)) {
            throw conflict("FLOW_FORM_SNAPSHOT_VERSION_CONFLICT",
                    "Flow form snapshot version changed");
        }
        var updated = businessForms.update(
                actor(session), context.binding.moduleCode(), context.binding.recordId(),
                expectedRecordVersion, supplied, idempotencyKey, requestId, traceId);
        if (updated.version() <= current.version()) {
            throw new IllegalStateException("Business-form write did not advance record version");
        }
        var history = repository.appendFormHistory(
                session.systemId(), session.tenantId(), instanceId, nodeCode,
                session.memberId(), current.version(), updated.version(),
                object(supplied), object(current.values()), object(updated.values()),
                clock.instant());
        var nextSnapshot = new FlowExtensionRepository.FormSnapshot(
                snapshot.instanceId(), snapshot.nodeCode(), snapshot.definitionId(),
                snapshot.definitionVersion(), snapshot.moduleCode(), snapshot.policies(),
                snapshot.initialValues(), snapshot.version() + 1, snapshot.materializedAt());
        return new FormWriteResult(formView(updated, nextSnapshot), history);
    }

    public List<FlowExtensionRepository.FormWriteHistory> formHistory(
            FlowSession session, long instanceId, String nodeCode) {
        formContext(session, instanceId, nodeCode);
        return repository.formHistory(
                session.systemId(), session.tenantId(), instanceId, nodeCode);
    }

    @Transactional
    public FlowExtensionRepository.NodeExecution executeNode(
            FlowSession session,
            long instanceId,
            String nodeCode,
            Long expectedVersion,
            JsonNode input,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var instance = workflow(session).requireCurrentApprover(
                instanceId, session.memberId());
        var graph = publishedGraph(session, instance);
        var node = graph.requireNode(nodeCode);
        if (FlowExtensionCompletionMapper.lifecycleOwned(node.type())) {
            throw invalid("FLOW_NODE_LIFECYCLE_OWNED",
                    "The node executes once through the existing completion worker chain");
        }
        var existing = repository.execution(
                session.systemId(), session.tenantId(), instanceId, nodeCode);
        if (existing.isPresent()) {
            throw conflict("FLOW_NODE_ALREADY_EXECUTED",
                    "Flow node already has an execution; use resume");
        }
        var result = nodeExecutions.execute(node, input, clock.instant());
        try {
            applyRecordEffect(session, instance, node, result.output(), input,
                    idempotencyKey, requestId, traceId);
            result = new FlowNodeExecutionEngine.Result(
                    result.status(), nodeEffects.execute(
                    session, instance, node, input, result.output(),
                    idempotencyKey, requestId, traceId));
        } catch (RuntimeException failure) {
            nodeEffects.failed(session, instance, node, failure, requestId, traceId);
            throw failure;
        }
        return saveExecution(session, instance, node, expectedVersion,
                null, result, input);
    }

    @Transactional
    public FlowExtensionRepository.NodeExecution resumeNode(
            FlowSession session,
            long instanceId,
            String nodeCode,
            long expectedVersion,
            JsonNode input,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var instance = workflow(session).requireCurrentApprover(
                instanceId, session.memberId());
        var graph = publishedGraph(session, instance);
        var node = graph.requireNode(nodeCode);
        if (FlowExtensionCompletionMapper.lifecycleOwned(node.type())) {
            throw invalid("FLOW_NODE_LIFECYCLE_OWNED",
                    "The node resumes through the existing completion worker chain");
        }
        var current = repository.execution(
                session.systemId(), session.tenantId(), instanceId, nodeCode)
                .orElseThrow(() -> invalid("FLOW_NODE_EXECUTION_NOT_FOUND",
                        "Flow node has not started"));
        var result = nodeExecutions.resume(
                node, current.status(), input, clock.instant());
        try {
            result = new FlowNodeExecutionEngine.Result(
                    result.status(), nodeEffects.resume(
                    session, instance, node, current, input, result.output(),
                    idempotencyKey, requestId, traceId));
        } catch (RuntimeException failure) {
            nodeEffects.failed(session, instance, node, failure, requestId, traceId);
            throw failure;
        }
        return saveExecution(session, instance, node, expectedVersion,
                current.status(), result, input);
    }

    public List<FlowExtensionRepository.NodeExecutionEvent> nodeHistory(
            FlowSession session, long instanceId, String nodeCode) {
        var instance = workflow(session).instance(instanceId);
        publishedGraph(session, instance).requireNode(nodeCode);
        return repository.executionHistory(
                session.systemId(), session.tenantId(), instanceId, nodeCode);
    }

    private FormContext formContext(
            FlowSession session, long instanceId, String nodeCode) {
        var workflow = workflow(session);
        var instance = workflow.requireCurrentApprover(instanceId, session.memberId());
        var binding = instance.recordBinding();
        if (binding == null) {
            throw invalid("FLOW_FORM_RECORD_REQUIRED",
                    "Flow instance is not bound to a business record");
        }
        var node = publishedGraph(session, instance).requireNode(nodeCode);
        if (!List.of(FlowNodeCatalog.Type.APPROVAL,
                FlowNodeCatalog.Type.CONDITIONAL_APPROVAL,
                FlowNodeCatalog.Type.FORM).contains(node.type())) {
            throw invalid("FLOW_FORM_NODE_INVALID",
                    "Flow node does not expose a business form");
        }
        if (!binding.moduleCode().equals(node.moduleCode())) {
            throw invalid("FLOW_FORM_MODULE_MISMATCH",
                    "Flow node business module does not match the bound record");
        }
        return new FormContext(instance, binding, node);
    }

    private FlowExtensionGraph.Graph publishedGraph(
            FlowSession session, ApprovalInstance instance) {
        return repository.published(
                session.systemId(), session.tenantId(), instance.definitionId(),
                instance.definitionVersion()).orElseThrow(() -> invalid(
                "FLOW_EXTENSION_VERSION_NOT_FOUND",
                "The instance definition version has no executable node snapshot"))
                .graph();
    }

    private static void validateOwnerScopes(
            FlowSession session, FlowExtensionGraph.Graph graph) {
        for (var dependency : graph.dependencies()) {
            var ownerLocal = dependency.type()
                    == FlowExtensionGraph.DependencyType.FLOW_DEFINITION
                    || dependency.type()
                    == FlowExtensionGraph.DependencyType.MESSAGE_TEMPLATE
                    || dependency.type()
                    == FlowExtensionGraph.DependencyType.AI_POLICY;
            var targetTenant = dependency.targetTenantId() == null
                    ? session.tenantId() : dependency.targetTenantId();
            if (ownerLocal && (dependency.targetSystemId() != session.systemId()
                    || targetTenant != session.tenantId())) {
                throw invalid(
                        "FLOW_NODE_OWNER_SCOPE_MISMATCH",
                        dependency.type()
                                + " runtime dependencies must stay in the Flow system and tenant");
            }
        }
    }

    private FlowExtensionRepository.NodeExecution saveExecution(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            Long expectedVersion,
            FlowNodeExecutionEngine.Status from,
            FlowNodeExecutionEngine.Result result,
            JsonNode input) {
        try {
            return repository.saveExecution(
                    session.systemId(), session.tenantId(), instance.id(),
                    instance.definitionId(), instance.definitionVersion(),
                    node.code(), node.type(), expectedVersion, from, result,
                    object(input), session.memberId(), clock.instant());
        } catch (IllegalStateException exception) {
            throw conflict("FLOW_NODE_EXECUTION_VERSION_CONFLICT",
                    exception.getMessage());
        }
    }

    private void applyRecordEffect(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            ObjectNode output,
            JsonNode input,
            String idempotencyKey,
            String requestId,
            String traceId) {
        if (node.type() == FlowNodeCatalog.Type.FIELD_UPDATE) {
            var binding = requireBinding(instance);
            var version = requireVersion(input);
            var updated = businessForms.update(
                    actor(session), binding.moduleCode(), binding.recordId(), version,
                    fields(node.config().path("values")), idempotencyKey,
                    requestId, traceId);
            output.put("recordId", updated.recordId()).put("recordVersion", updated.version());
        }
        if (node.type() == FlowNodeCatalog.Type.DATA_CREATE_UPDATE) {
            var operation = node.config().path("operation").asText();
            var moduleCode = node.config().path("targetModuleCode").asText();
            FlowBusinessFormPort.FormRecord record;
            if ("CREATE".equals(operation)) {
                record = businessForms.create(actor(session), moduleCode,
                        node.config().path("title").asText(node.name()),
                        fields(node.config().path("values")), idempotencyKey,
                        requestId, traceId);
            } else if ("UPDATE".equals(operation)) {
                var binding = requireBinding(instance);
                record = businessForms.update(actor(session), moduleCode,
                        binding.recordId(), requireVersion(input),
                        fields(node.config().path("values")), idempotencyKey,
                        requestId, traceId);
            } else {
                throw invalid("FLOW_DATA_NODE_OPERATION_INVALID",
                        "Data node operation must be CREATE or UPDATE");
            }
            output.put("recordId", record.recordId()).put("recordVersion", record.version());
        }
    }

    private FormView formView(
            FlowBusinessFormPort.FormRecord record,
            FlowExtensionRepository.FormSnapshot snapshot) {
        var fields = new ArrayList<FormField>();
        for (var policy : snapshot.policies()) {
            var capability = record.capabilities().get(policy.fieldCode());
            if (!policy.mode().readable() || capability == null || !capability.readable()) continue;
            fields.add(new FormField(
                    policy.fieldCode(), policy.mode().name(),
                    policy.mode().editable() && capability.writable(),
                    policy.mode() == FlowExtensionGraph.FieldMode.REQUIRED,
                    record.values().get(policy.fieldCode())));
        }
        return new FormView(snapshot.instanceId(), snapshot.nodeCode(),
                snapshot.definitionVersion(), snapshot.moduleCode(), record.recordId(),
                record.version(), snapshot.version(), List.copyOf(fields));
    }

    private static Map<String, FlowExtensionGraph.FieldPolicy> policies(
            List<FlowExtensionGraph.FieldPolicy> values) {
        var result = new LinkedHashMap<String, FlowExtensionGraph.FieldPolicy>();
        values.forEach(value -> result.put(value.fieldCode(), value));
        return result;
    }

    private boolean compatible(FlowExtensionRepository.DependencyState state) {
        if (!state.exists() || !state.active() || state.currentVersion() == null) return false;
        return state.dependency().versionMode() == FlowExtensionGraph.VersionMode.EXACT
                ? state.currentVersion() == state.dependency().requiredVersion()
                : state.currentVersion() >= state.dependency().requiredVersion();
    }

    private com.unique.examine.flow.service.ApprovalWorkflowService workflow(
            FlowSession session) {
        return workflows.forTenant(session.systemId(), session.tenantId());
    }

    private static FlowBusinessFormPort.Actor actor(FlowSession session) {
        return new FlowBusinessFormPort.Actor(
                session.accountId(), session.systemId(), session.tenantId(), session.memberId(),
                session.permissions());
    }

    private static ApprovalInstance.RecordBinding requireBinding(ApprovalInstance instance) {
        if (instance.recordBinding() == null) {
            throw invalid("FLOW_NODE_RECORD_REQUIRED",
                    "Flow node requires a bound business record");
        }
        return instance.recordBinding();
    }

    private static long requireVersion(JsonNode input) {
        var version = input == null ? -1 : input.path("recordVersion").asLong(-1);
        if (version < 0) {
            throw invalid("FLOW_NODE_RECORD_VERSION_REQUIRED",
                    "Flow record mutation node requires recordVersion");
        }
        return version;
    }

    private static Map<String, JsonNode> fields(JsonNode value) {
        if (!value.isObject()) return Map.of();
        var result = new LinkedHashMap<String, JsonNode>();
        value.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    private ObjectNode object(Object value) {
        if (value == null) return JsonNodeFactory.instance.objectNode();
        if (value instanceof JsonNode node) {
            if (!node.isObject()) return JsonNodeFactory.instance.objectNode();
            return (ObjectNode) node.deepCopy();
        }
        return json.valueToTree(value);
    }

    private String checksum(FlowExtensionGraph.Graph graph) {
        try {
            var bytes = json.writeValueAsString(graph).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot checksum Flow extension graph", exception);
        }
    }

    private static boolean missing(JsonNode value) {
        return value == null || value.isNull()
                || value.isTextual() && value.textValue().isBlank()
                || value.isArray() && value.isEmpty();
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException forbidden(String code, String message) {
        return new BusinessException(code, message, HttpStatus.FORBIDDEN);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record ImpactIssue(
            String severity, String code, String path, String message) {
    }

    public record DependencyImpact(
            String sourceNodeCode,
            String type,
            long targetSystemId,
            Long targetTenantId,
            String targetKey,
            long requiredVersion,
            String versionMode,
            Long currentVersion,
            boolean exists,
            boolean active,
            boolean compatible,
            String reason) {
    }

    public record ImpactReport(
            long definitionId,
            int draftRevision,
            boolean ready,
            List<DependencyImpact> outboundDependencies,
            List<FlowExtensionRepository.InboundConsumer> inboundConsumers,
            List<ImpactIssue> issues) {
    }

    public record FormField(
            String fieldCode,
            String mode,
            boolean editable,
            boolean required,
            JsonNode value) {
    }

    public record FormView(
            long instanceId,
            String nodeCode,
            int definitionVersion,
            String moduleCode,
            long recordId,
            long recordVersion,
            long snapshotVersion,
            List<FormField> fields) {
    }

    public record FormWriteResult(
            FormView form,
            FlowExtensionRepository.FormWriteHistory history) {
    }

    private record FormContext(
            ApprovalInstance instance,
            ApprovalInstance.RecordBinding binding,
            FlowExtensionGraph.Node node) {
    }
}
