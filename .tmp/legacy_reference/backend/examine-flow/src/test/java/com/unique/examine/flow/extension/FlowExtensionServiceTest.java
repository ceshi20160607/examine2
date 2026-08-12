package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowExtensionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private InMemoryFlowExtensionRepository extensions;
    private ApprovalWorkflowService workflow;
    private FakeBusinessForms forms;
    private RecordingNodeEffects nodeEffects;
    private FlowExtensionService service;
    private FlowSession approver;
    private long definitionId;
    private long instanceId;

    @BeforeEach
    void setUp() {
        var ids = new AtomicLong(100);
        workflow = new ApprovalWorkflowService(new InMemoryApprovalRepository(),
                new IdService() {
                    @Override
                    public long nextId() {
                        return ids.incrementAndGet();
                    }
                }, Clock.fixed(NOW, ZoneOffset.UTC));
        var draft = workflow.createDraft("Order approval", 20);
        definitionId = draft.id();
        workflow.publish(definitionId);
        var instance = workflow.start(definitionId, 1, "order:900", 10,
                new ApprovalInstance.RecordBinding("Orders", 900));
        instanceId = instance.id();

        extensions = new InMemoryFlowExtensionRepository();
        extensions.putPublished(1, 2, definitionId, 1, 1, graphV1());
        extensions.putPublished(1, 2, definitionId, 2, 2, graphV2());
        forms = new FakeBusinessForms(json);
        nodeEffects = new RecordingNodeEffects();
        service = new FlowExtensionService(extensions,
                (systemId, tenantId) -> workflow, forms, nodeEffects, json,
                Clock.fixed(NOW, ZoneOffset.UTC));
        approver = new FlowSession(99, 1, 2, 20, Set.of("flow.instance.act"));
    }

    @Test
    void springContextSelectsTheProductionConstructorWithoutADefaultConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FlowExtensionRepository.class, () -> extensions);
            context.registerBean(com.unique.examine.flow.service.FlowRequestServiceFactory.class,
                    () -> (systemId, tenantId) -> workflow);
            context.registerBean(FlowBusinessFormPort.class, () -> forms);
            context.registerBean(FlowNodeEffectPort.class, () -> nodeEffects);
            context.registerBean(ObjectMapper.class, () -> json);
            context.registerBean(FlowExtensionService.class);
            context.refresh();

            assertThat(context.getBean(FlowExtensionService.class).catalog())
                    .hasSize(FlowNodeCatalog.Type.values().length);
        }
    }

    @Test
    void readsThePersistedDefinitionDraftForTheManagementEditor() {
        assertThat(service.draft(approver, definitionId)).isNull();

        var missing = catchThrowableOfType(
                ApprovalDomainException.class,
                () -> service.draft(approver, definitionId + 999));
        assertThat(missing.code()).isEqualTo(
                ApprovalDomainException.Code.DRAFT_NOT_FOUND);

        var stored = extensions.saveDraft(
                approver.systemId(), approver.tenantId(), definitionId,
                1, graphV1(), "checksum-1", approver.memberId(), NOW);

        assertThat(service.draft(approver, definitionId)).isEqualTo(stored);
        assertThat(service.draft(approver, definitionId).graph().nodes())
                .extracting(FlowExtensionGraph.Node::code)
                .contains("approval");
    }

    @Test
    void readsAndWritesThroughTheExactInstanceVersionWithDualFieldAuthorizationAndHistory() {
        var form = service.form(approver, instanceId, "approval");

        assertThat(form.definitionVersion()).isEqualTo(1);
        assertThat(form.recordVersion()).isEqualTo(7);
        assertThat(form.fields()).extracting(FlowExtensionService.FormField::fieldCode)
                .containsExactly("title", "amount", "locked");
        assertThat(form.fields()).filteredOn(field -> field.fieldCode().equals("locked"))
                .singleElement().extracting(FlowExtensionService.FormField::editable)
                .isEqualTo(false);
        assertThat(form.fields()).extracting(FlowExtensionService.FormField::fieldCode)
                .doesNotContain("secret");

        assertCode("FLOW_FORM_CHANGES_REQUIRED", () -> service.writeForm(
                approver, instanceId, "approval", 0, 7, Map.of(),
                "idem-empty", "request-empty", "trace-empty"));

        var result = service.writeForm(approver, instanceId, "approval", 0, 7,
                Map.of("amount", json.getNodeFactory().numberNode(11)),
                "idem-1", "request-1", "trace-1");

        assertThat(result.form().recordVersion()).isEqualTo(8);
        assertThat(result.form().snapshotVersion()).isEqualTo(1);
        assertThat(result.history().recordVersionBefore()).isEqualTo(7);
        assertThat(result.history().recordVersionAfter()).isEqualTo(8);
        assertThat(service.formHistory(approver, instanceId, "approval"))
                .singleElement().extracting(FlowExtensionRepository.FormWriteHistory::actorId)
                .isEqualTo(20L);

        assertCode("FLOW_FORM_NO_CHANGES", () -> service.writeForm(
                approver, instanceId, "approval", 1, 8,
                Map.of("amount", json.getNodeFactory().numberNode(11)),
                "idem-same", "request-same", "trace-same"));
        assertThat(service.formHistory(approver, instanceId, "approval"))
                .hasSize(1);
        var afterNoOp = service.form(approver, instanceId, "approval");
        assertThat(afterNoOp.recordVersion()).isEqualTo(8);
        assertThat(afterNoOp.snapshotVersion()).isEqualTo(1);

        assertCode("FLOW_FORM_FIELD_WRITE_FORBIDDEN", () -> service.writeForm(
                approver, instanceId, "approval", 1, 8,
                Map.of("locked", json.getNodeFactory().textNode("changed")),
                "idem-2", "request-2", "trace-2"));
        assertCode("FLOW_FORM_FIELD_WRITE_FORBIDDEN", () -> service.writeForm(
                approver, instanceId, "approval", 1, 8,
                Map.of("secret", json.getNodeFactory().textNode("leak")),
                "idem-3", "request-3", "trace-3"));
        var explicitNull = new LinkedHashMap<String, JsonNode>();
        explicitNull.put("title", null);
        assertCode("FLOW_FORM_REQUIRED_FIELD_MISSING", () -> service.writeForm(
                approver, instanceId, "approval", 1, 8,
                explicitNull,
                "idem-4", "request-4", "trace-4"));
        assertCode("FLOW_FORM_RECORD_VERSION_CONFLICT", () -> service.writeForm(
                approver, instanceId, "approval", 1, 7,
                Map.of("amount", json.getNodeFactory().numberNode(12)),
                "idem-5", "request-5", "trace-5"));
        assertCode("FLOW_FORM_SNAPSHOT_VERSION_CONFLICT", () -> service.writeForm(
                approver, instanceId, "approval", 0, 8,
                Map.of("amount", json.getNodeFactory().numberNode(12)),
                "idem-6", "request-6", "trace-6"));
    }

    @Test
    void rejectsNonApproverAndProtectsRecordMutationNodesWithTheSameBoundary() {
        var outsider = new FlowSession(98, 1, 2, 21, Set.of("flow.instance.act"));
        var readFailure = catchThrowableOfType(
                ApprovalDomainException.class,
                () -> service.form(outsider, instanceId, "approval"));
        assertThat(readFailure.code())
                .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);

        var executionFailure = catchThrowableOfType(
                ApprovalDomainException.class,
                () -> service.executeNode(outsider, instanceId, "field_update",
                        null, json.createObjectNode().put("recordVersion", 7),
                        "idem-x", "request-x", "trace-x"));
        assertThat(executionFailure.code())
                .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);

        var execution = service.executeNode(approver, instanceId, "field_update",
                null, json.createObjectNode().put("recordVersion", 7),
                "idem-update", "request-update", "trace-update");
        assertThat(execution.status()).isEqualTo(FlowNodeExecutionEngine.Status.CONTINUED);
        assertThat(execution.result().path("recordVersion").asLong()).isEqualTo(8);
        assertThat(forms.values.get("status").asText()).isEqualTo("APPROVED");
        assertCode("FLOW_NODE_ALREADY_EXECUTED", () -> service.executeNode(
                approver, instanceId, "field_update", null,
                json.createObjectNode().put("recordVersion", 8),
                "idem-update-2", "request-update-2", "trace-update-2"));
    }

    @Test
    void mapsLifecycleNodesIntoTheExistingVersionPinnedCompletionChainExactlyOnce() {
        var graph = lifecycleGraph();

        var stored = service.saveDraft(
                approver, definitionId, 1, graph);
        var synchronizedDraft = workflow.definitionDraft(definitionId);

        assertThat(stored.sourceRevision()).isEqualTo(2);
        assertThat(synchronizedDraft.completionSteps())
                .extracting(step -> step.code())
                .containsExactly("ext_webhook", "ext_subflow", "ext_external");
        assertThat(synchronizedDraft.completionSteps().get(1).subflow().definitionId())
                .isEqualTo(901);
        assertThat(synchronizedDraft.completionSteps().get(1).subflow().version())
                .isEqualTo(4);

        var published = workflow.publish(definitionId);
        service.published(approver, synchronizedDraft, published);
        var started = workflow.start(
                definitionId, published.version(), "order:lifecycle", 10,
                new ApprovalInstance.RecordBinding("Orders", 901));

        assertCode("FLOW_NODE_LIFECYCLE_OWNED", () -> service.executeNode(
                approver, started.id(), "webhook", null,
                json.createObjectNode(), "manual-webhook",
                "request-webhook", "trace-webhook"));

        var completing = workflow.approve(started.id(), 20, "approved");
        assertThat(completing.definitionVersion()).isEqualTo(published.version());
        assertThat(completing.completionExecutions())
                .extracting(execution -> execution.step().code())
                .containsExactly("ext_webhook", "ext_subflow", "ext_external");
        assertThat(completing.completionExecutions())
                .extracting(execution -> execution.definitionVersion())
                .containsOnly(published.version());
        assertThat(completing.completionExecutions().stream()
                .map(execution -> execution.step().code()).distinct().count())
                .isEqualTo(3);
    }

    @Test
    void rejectsOwnerRuntimeDependenciesThatCannotExecuteInTheFlowTenant() {
        var local = lifecycleGraph();
        var crossTenant = new FlowExtensionGraph.Dependency(
                "subflow", FlowExtensionGraph.DependencyType.FLOW_DEFINITION,
                9, 8L, "901", 4, FlowExtensionGraph.VersionMode.EXACT);
        var graph = new FlowExtensionGraph.Graph(
                local.nodes(), List.of(local.dependencies().getFirst(), crossTenant));

        assertCode("FLOW_NODE_OWNER_SCOPE_MISMATCH", () -> service.saveDraft(
                approver, definitionId, 1, graph));
        assertThat(workflow.definitionDraft(definitionId).revision()).isEqualTo(1);
    }

    @Test
    void reportsOutboundBlockersAndPublishedCrossApplicationConsumers() {
        var dependency = new FlowExtensionGraph.Dependency(
                "webhook", FlowExtensionGraph.DependencyType.OPENAPI_APPLICATION,
                3, 4L, "callback_application", 4,
                FlowExtensionGraph.VersionMode.EXACT);
        var impactGraph = dependencyGraph(dependency);
        extensions.saveDraft(1, 2, definitionId, 1, impactGraph,
                "a".repeat(64), 20, NOW);
        extensions.dependency(dependency, true, 3);
        extensions.inboundConsumers(List.of(new FlowExtensionRepository.InboundConsumer(
                8, 9, 701, 5, "finance", "subflow")));

        var impact = service.impact(approver, definitionId);

        assertThat(impact.ready()).isFalse();
        assertThat(impact.outboundDependencies()).singleElement()
                .extracting(FlowExtensionService.DependencyImpact::compatible)
                .isEqualTo(false);
        assertThat(impact.inboundConsumers()).singleElement()
                .extracting(FlowExtensionRepository.InboundConsumer::applicationCode)
                .isEqualTo("finance");
        assertThat(impact.issues()).extracting(FlowExtensionService.ImpactIssue::code)
                .containsExactly("FLOW_DEPENDENCY_INCOMPATIBLE",
                        "FLOW_CROSS_APPLICATION_CONSUMERS");
    }

    private FlowExtensionGraph.Graph graphV1() {
        return new FlowExtensionGraph.Graph(List.of(
                node("start", FlowNodeCatalog.Type.START, null,
                        json.createObjectNode(), List.of(), List.of("approval")),
                node("approval", FlowNodeCatalog.Type.APPROVAL, "Orders",
                        json.createObjectNode().put("approvalMode", "SEQUENTIAL"),
                        List.of(
                                policy("title", FlowExtensionGraph.FieldMode.REQUIRED),
                                policy("amount", FlowExtensionGraph.FieldMode.EDITABLE),
                                policy("locked", FlowExtensionGraph.FieldMode.EDITABLE),
                                policy("secret", FlowExtensionGraph.FieldMode.HIDDEN)),
                        List.of("field_update")),
                node("field_update", FlowNodeCatalog.Type.FIELD_UPDATE, "Orders",
                        json.createObjectNode().set("values",
                                json.createObjectNode().put("status", "APPROVED")),
                        List.of(), List.of("end")),
                node("end", FlowNodeCatalog.Type.END, null,
                        json.createObjectNode(), List.of(), List.of())), List.of());
    }

    private FlowExtensionGraph.Graph graphV2() {
        return new FlowExtensionGraph.Graph(List.of(
                node("start", FlowNodeCatalog.Type.START, null,
                        json.createObjectNode(), List.of(), List.of("approval")),
                node("approval", FlowNodeCatalog.Type.APPROVAL, "OtherModule",
                        json.createObjectNode().put("approvalMode", "SEQUENTIAL"),
                        List.of(policy("title", FlowExtensionGraph.FieldMode.HIDDEN)),
                        List.of("end")),
                node("end", FlowNodeCatalog.Type.END, null,
                        json.createObjectNode(), List.of(), List.of())), List.of());
    }

    private FlowExtensionGraph.Graph dependencyGraph(
            FlowExtensionGraph.Dependency dependency) {
        return new FlowExtensionGraph.Graph(List.of(
                node("start", FlowNodeCatalog.Type.START, null,
                        json.createObjectNode(), List.of(), List.of("webhook")),
                node("webhook", FlowNodeCatalog.Type.WEBHOOK, null,
                        json.createObjectNode()
                                .put("endpointCode", "callback")
                                .put("url", "https://callback.example.test/flow")
                                .put("timeoutSeconds", 5)
                                .put("maxAttempts", 3)
                                .put("baseBackoffSeconds", 10),
                        List.of(), List.of("end")),
                node("end", FlowNodeCatalog.Type.END, null,
                        json.createObjectNode(), List.of(), List.of())), List.of(dependency));
    }

    private FlowExtensionGraph.Graph lifecycleGraph() {
        var webhookDependency = new FlowExtensionGraph.Dependency(
                "webhook", FlowExtensionGraph.DependencyType.OPENAPI_APPLICATION,
                1, 2L, "callback_application", 3,
                FlowExtensionGraph.VersionMode.EXACT);
        var subflowDependency = new FlowExtensionGraph.Dependency(
                "subflow", FlowExtensionGraph.DependencyType.FLOW_DEFINITION,
                1, 2L, "901", 4,
                FlowExtensionGraph.VersionMode.EXACT);
        return new FlowExtensionGraph.Graph(List.of(
                node("start", FlowNodeCatalog.Type.START, null,
                        json.createObjectNode(), List.of(), List.of("webhook")),
                node("webhook", FlowNodeCatalog.Type.WEBHOOK, null,
                        json.createObjectNode()
                                .put("endpointCode", "callback")
                                .put("url", "https://callback.example.test/flow")
                                .put("secretRef", "vault/flow/callback")
                                .put("timeoutSeconds", 5)
                                .put("maxAttempts", 3)
                                .put("baseBackoffSeconds", 10),
                        List.of(), List.of("subflow")),
                node("subflow", FlowNodeCatalog.Type.SUBFLOW, null,
                        json.createObjectNode().put("definitionId", 901),
                        List.of(), List.of("external")),
                node("external", FlowNodeCatalog.Type.EXTERNAL, null,
                        json.createObjectNode()
                                .put("topic", "warehouse.pick")
                                .put("leaseSeconds", 60)
                                .put("maxAttempts", 3)
                                .put("resultJsonLimitBytes", 4096),
                        List.of(), List.of("end")),
                node("end", FlowNodeCatalog.Type.END, null,
                        json.createObjectNode(), List.of(), List.of())),
                List.of(webhookDependency, subflowDependency));
    }

    private FlowExtensionGraph.Node node(
            String code, FlowNodeCatalog.Type type, String module,
            JsonNode config, List<FlowExtensionGraph.FieldPolicy> policies,
            List<String> next) {
        return new FlowExtensionGraph.Node(code, code, type, "core",
                module, config, policies, next);
    }

    private static FlowExtensionGraph.FieldPolicy policy(
            String code, FlowExtensionGraph.FieldMode mode) {
        return new FlowExtensionGraph.FieldPolicy(code, mode);
    }

    private static void assertCode(String code, Runnable action) {
        var failure = catchThrowableOfType(BusinessException.class, action::run);
        assertThat(failure.code()).isEqualTo(code);
    }

    private static final class FakeBusinessForms implements FlowBusinessFormPort {
        private final ObjectMapper json;
        private final Map<String, JsonNode> values = new LinkedHashMap<>();
        private final Map<String, FieldCapability> capabilities = new LinkedHashMap<>();
        private long version = 7;

        private FakeBusinessForms(ObjectMapper json) {
            this.json = json;
            values.put("title", json.getNodeFactory().textNode("Purchase"));
            values.put("amount", json.getNodeFactory().numberNode(10));
            values.put("locked", json.getNodeFactory().textNode("fixed"));
            values.put("secret", json.getNodeFactory().textNode("internal"));
            values.put("status", json.getNodeFactory().textNode("PENDING"));
            capabilities.put("title", new FieldCapability(true, true));
            capabilities.put("amount", new FieldCapability(true, true));
            capabilities.put("locked", new FieldCapability(true, false));
            capabilities.put("secret", new FieldCapability(true, true));
            capabilities.put("status", new FieldCapability(true, true));
        }

        @Override
        public FormRecord read(Actor actor, String moduleCode, long recordId) {
            assertActor(actor, moduleCode, recordId);
            return record();
        }

        @Override
        public FormRecord update(
                Actor actor, String moduleCode, long recordId, long expectedVersion,
                Map<String, JsonNode> changes, String idempotencyKey,
                String requestId, String traceId) {
            assertActor(actor, moduleCode, recordId);
            if (version != expectedVersion) {
                throw new IllegalStateException("record version changed");
            }
            values.putAll(changes);
            version++;
            return record();
        }

        @Override
        public FormRecord create(
                Actor actor, String moduleCode, String title,
                Map<String, JsonNode> values, String idempotencyKey,
                String requestId, String traceId) {
            throw new UnsupportedOperationException();
        }

        private FormRecord record() {
            return new FormRecord("Orders", 900, version, "Purchase",
                    "schema-v1", values, capabilities);
        }

        private static void assertActor(Actor actor, String moduleCode, long recordId) {
            assertThat(actor.accountId()).isEqualTo(99);
            assertThat(actor.systemId()).isEqualTo(1);
            assertThat(actor.tenantId()).isEqualTo(2);
            assertThat(moduleCode).isEqualTo("Orders");
            assertThat(recordId).isEqualTo(900);
        }
    }

    private static final class RecordingNodeEffects implements FlowNodeEffectPort {
        @Override
        public com.fasterxml.jackson.databind.node.ObjectNode execute(
                FlowSession session, ApprovalInstance instance,
                FlowExtensionGraph.Node node, JsonNode input,
                com.fasterxml.jackson.databind.node.ObjectNode output,
                String idempotencyKey, String requestId, String traceId) {
            return output;
        }

        @Override
        public com.fasterxml.jackson.databind.node.ObjectNode resume(
                FlowSession session, ApprovalInstance instance,
                FlowExtensionGraph.Node node,
                FlowExtensionRepository.NodeExecution current, JsonNode input,
                com.fasterxml.jackson.databind.node.ObjectNode output,
                String idempotencyKey, String requestId, String traceId) {
            return output;
        }

        @Override
        public void failed(
                FlowSession session, ApprovalInstance instance,
                FlowExtensionGraph.Node node, RuntimeException failure,
                String requestId, String traceId) {
        }
    }
}
