package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.WorkTaskCreationFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.interaction.FlowInteractionService;
import com.unique.examine.flow.interaction.memory.InMemoryFlowInteractionRepository;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowNodeEffectServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private FlowInteractionService interactions;
    private MemoryMessages copyMessages;
    private RecordingNotifications notifications;
    private RecordingWorkTasks workTasks;
    private RecordingAi ai;
    private RecordingAudits audits;
    private FlowNodeEffectService effects;
    private ApprovalInstance instance;
    private FlowSession session;

    @BeforeEach
    void setUp() {
        var ids = new AtomicLong(100);
        IdService idService = new IdService() {
            @Override
            public long nextId() {
                return ids.incrementAndGet();
            }
        };
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(), idService, clock);
        var definition = workflow.createDraft("Effect approval", 100);
        workflow.publish(definition.id());
        instance = workflow.start(
                definition.id(), 1, "effect:800", 90,
                new ApprovalInstance.RecordBinding("Orders", 800));
        interactions = new FlowInteractionService(
                workflow, new InMemoryFlowInteractionRepository(), idService, clock);
        copyMessages = new MemoryMessages();
        var mutations = new FlowInteractionMutationService(
                (systemId, tenantId) -> interactions,
                new MemoryIdempotency(), json, copyMessages,
                (systemId, tenantId, memberId) -> memberId == 101
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(101, null))
                        : Optional.empty());
        notifications = new RecordingNotifications();
        workTasks = new RecordingWorkTasks();
        ai = new RecordingAi();
        audits = new RecordingAudits();
        effects = new FlowNodeEffectService(
                mutations, notifications, workTasks, ai, audits, json);
        session = new FlowSession(
                900, 10, 20, 100, 7,
                Set.of("flow.instance.act", "flow.instance.copy",
                        "work.task.create"));
    }

    @Test
    void usesVersionPinnedStableKeysForCopyNotificationAndWorkTaskOwners() {
        var copyConfig = json.createObjectNode()
                .put("message", "Please follow this Flow");
        copyConfig.putArray("recipients").add(101);
        var copy = node("copy", FlowNodeCatalog.Type.COPY, null,
                copyConfig);
        var firstCopy = effects.execute(
                session, instance, copy, json.createObjectNode(),
                json.createObjectNode(), "client-key-1", "request-1", "trace-1");
        var replayCopy = effects.execute(
                session, instance, copy, json.createObjectNode(),
                json.createObjectNode(), "different-client-key", "request-2", "trace-2");

        assertThat(firstCopy.path("copyCount").asInt()).isEqualTo(1);
        assertThat(replayCopy.path("copies").get(0).path("copyId").asText())
                .isEqualTo(firstCopy.path("copies").get(0).path("copyId").asText());
        assertThat(interactions.copies(instance.id(), 1, 20).total()).isEqualTo(1);
        assertThat(copyMessages.commands).hasSize(1);

        var notificationConfig = json.createObjectNode()
                .put("templateCode", "FLOW_RESULT_READY");
        notificationConfig.putArray("recipients").add(101);
        var notification = node(
                "notification", FlowNodeCatalog.Type.NOTIFICATION, null,
                notificationConfig);
        var firstDelivery = effects.execute(
                session, instance, notification, json.createObjectNode(),
                json.createObjectNode(), "delivery-client-1", "request-3", "trace-3");
        var replayDelivery = effects.execute(
                session, instance, notification, json.createObjectNode(),
                json.createObjectNode(), "delivery-client-2", "request-4", "trace-4");

        assertThat(notifications.uniqueKeys()).isEqualTo(1);
        assertThat(firstDelivery.path("deliveries").get(0).path("replay").asBoolean())
                .isFalse();
        assertThat(replayDelivery.path("deliveries").get(0).path("replay").asBoolean())
                .isTrue();

        var task = node("task", FlowNodeCatalog.Type.TASK, null,
                json.createObjectNode().put("taskKind", "WORK_TASK")
                        .put("assigneeMemberId", 101)
                        .put("title", "Review Flow outcome"));
        var taskOutput = effects.execute(
                session, instance, task, json.createObjectNode(),
                json.createObjectNode(), "task-client-1", "request-5", "trace-5");
        var replayTask = effects.execute(
                session, instance, task, json.createObjectNode(),
                json.createObjectNode(), "task-client-2", "request-6", "trace-6");

        assertThat(workTasks.uniqueCreates()).isEqualTo(1);
        assertThat(taskOutput.path("taskReplay").asBoolean()).isFalse();
        assertThat(taskOutput.path("task").path("createdAt").asText())
                .isEqualTo(NOW.toString());
        assertThat(replayTask.path("taskReplay").asBoolean()).isTrue();
        var waiting = new FlowExtensionRepository.NodeExecution(
                instance.id(), "task", FlowNodeCatalog.Type.TASK,
                FlowNodeExecutionEngine.Status.WAITING_HUMAN,
                taskOutput, 0, session.memberId(), NOW);
        var open = catchThrowableOfType(
                com.unique.examine.core.error.BusinessException.class,
                () -> effects.resume(
                        session, instance, task, waiting, json.createObjectNode(),
                        json.createObjectNode(), "resume-1", "request-7", "trace-7"));
        assertThat(open.code()).isEqualTo("FLOW_TASK_NOT_COMPLETED");
        effects.failed(session, instance, task, open, "request-7", "trace-7");

        workTasks.completed = true;
        var completed = effects.resume(
                session, instance, task, waiting, json.createObjectNode(),
                json.createObjectNode(), "resume-2", "request-8", "trace-8");
        assertThat(completed.path("taskStatus").asText()).isEqualTo("COMPLETED");
        assertThat(audits.failures).singleElement()
                .extracting(audit -> audit.failure().code())
                .isEqualTo("FLOW_TASK_NOT_COMPLETED");
    }

    @Test
    void sealsAiPreviewAndExecutesTheSameConfirmedRecordMutationOnce() {
        var node = node("ai_assist", FlowNodeCatalog.Type.AI_ASSIST, "Orders",
                json.createObjectNode().put("modelPolicyCode", "flow_advice")
                        .put("fieldCode", "summary")
                        .put("confirmationRequired", true));
        var input = json.createObjectNode().put("result", "Approved summary");
        input.putObject("provenance")
                .put("providerId", "1")
                .put("providerVersion", 3)
                .put("model", "governed-model")
                .put("promptVersion", "prompt-v2")
                .put("policyVersionId", "2");
        var prepared = effects.execute(
                session, instance, node, input, json.createObjectNode(),
                "ai-client-1", "request-ai-1", "trace-ai-1");
        var waiting = new FlowExtensionRepository.NodeExecution(
                instance.id(), "ai_assist", FlowNodeCatalog.Type.AI_ASSIST,
                FlowNodeExecutionEngine.Status.WAITING_CONFIRMATION,
                prepared, 0, session.memberId(), NOW);

        var first = effects.resume(
                session, instance, node, waiting,
                json.createObjectNode().put("confirmed", true),
                json.createObjectNode(), "ai-client-2",
                "request-ai-2", "trace-ai-2");
        var replay = effects.resume(
                session, instance, node, waiting,
                json.createObjectNode().put("confirmed", true),
                json.createObjectNode(), "ai-client-3",
                "request-ai-3", "trace-ai-3");

        assertThat(prepared.path("sealedCommand").path("commandSha256").asText())
                .hasSize(64);
        assertThat(ai.uniqueExecuteKeys()).isEqualTo(1);
        assertThat(first.path("recordVersion").asLong()).isEqualTo(9);
        assertThat(replay.path("aiFill").path("historyId").asText()).isEqualTo("10");
    }

    private FlowExtensionGraph.Node node(
            String code, FlowNodeCatalog.Type type, String module,
            com.fasterxml.jackson.databind.JsonNode config) {
        return new FlowExtensionGraph.Node(
                code, code, type, "core", module, config, List.of(), List.of("end"));
    }

    private static final class RecordingNotifications implements ResultNotificationFacade {
        private final Map<String, Long> deliveries = new HashMap<>();
        private final AtomicLong ids = new AtomicLong(200);

        @Override
        public DeliveryReceipt dispatch(Command command) {
            var existing = deliveries.get(command.dedupeKey());
            if (existing != null) {
                return new DeliveryReceipt(existing, existing + 1, "DELIVERED", true);
            }
            var id = ids.incrementAndGet();
            deliveries.put(command.dedupeKey(), id);
            return new DeliveryReceipt(id, id + 1, "DELIVERED", false);
        }

        private int uniqueKeys() {
            return deliveries.size();
        }
    }

    private static final class RecordingWorkTasks implements WorkTaskCreationFacade {
        private final Map<String, CreatedTask> tasks = new HashMap<>();
        private boolean completed;

        @Override
        public CreatedTask create(Command command) {
            var existing = tasks.get(command.idempotencyKey());
            if (existing != null) return existing.asReplay();
            var created = new CreatedTask(
                    301, 1, command.systemId(), command.tenantId(),
                    command.creatorMemberId(), command.assigneeMemberId(),
                    command.title(), command.description(), command.projectId(),
                    command.dueAt(), "OPEN", NOW, false);
            tasks.put(command.idempotencyKey(), created);
            return created;
        }

        @Override
        public TaskState state(StateQuery query) {
            return new TaskState(
                    query.taskId(), completed ? 2 : 1,
                    completed ? "COMPLETED" : "OPEN");
        }

        private int uniqueCreates() {
            return tasks.size();
        }
    }

    private static final class RecordingAi implements AiFieldFillFacade {
        private final Set<String> executeKeys = new java.util.HashSet<>();
        private Provenance provenance;

        @Override
        public SourceSnapshot sourceSnapshot(SourceRequest request) {
            return new SourceSnapshot(
                    request.moduleCode(), request.recordId(), 8, "7",
                    new FieldContract(
                            "8", request.fieldCode(), "Summary", ResultSchema.STRING,
                            List.of("9"), "Summarize the record", "SYSTEM_DEFAULT",
                            0.5, OverwriteMode.CONFIRM),
                    "a".repeat(64),
                    List.of(new SourceValue(
                            "9", "title", "Title", "TEXT", "Order 800")), null);
        }

        @Override
        public PreparedFill prepare(PrepareRequest request) {
            provenance = request.provenance();
            return new PreparedFill(
                    new FillPreview(
                            request.moduleCode(), request.recordId(),
                            request.expectedRecordVersion(), request.expectedSchemaVersionId(),
                            "8", request.fieldCode(), ResultSchema.STRING,
                            request.expectedSourceVersionHash(), null,
                            "Approved summary", 0.98, false),
                    new SealedCommand("sealed", "key-v1", "b".repeat(64)));
        }

        @Override
        public FillReadback execute(ExecuteRequest request) {
            executeKeys.add(request.idempotencyKey());
            return new FillReadback(
                    "10", request.moduleCode(), request.recordId(), 9, "7",
                    "8", request.fieldCode(), ResultSchema.STRING,
                    "Approved summary", 0.98, 1, "CREATED", provenance);
        }

        @Override
        public RejectionReadback reject(RejectRequest request) {
            throw new UnsupportedOperationException();
        }

        private int uniqueExecuteKeys() {
            return executeKeys.size();
        }
    }

    private static final class RecordingAudits implements OperationAuditFacade {
        private final List<OperationAudit> successes = new ArrayList<>();
        private final List<OperationAudit> failures = new ArrayList<>();

        @Override
        public void recordSuccess(OperationAudit audit) {
            successes.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            failures.add(audit);
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            failures.add(audit);
        }
    }

    private static final class MemoryMessages implements MemberMessageFacade {
        private final List<Command> commands = new ArrayList<>();

        @Override
        public long send(Command command) {
            commands.add(command);
            return commands.size();
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, String> keys = new HashMap<>();
        private final AtomicLong ids = new AtomicLong(400);

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(compound(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(
                String scopeType, String scopeKey, String key,
                String requestHash, Duration ttl) {
            var id = ids.incrementAndGet();
            var compound = compound(scopeType, scopeKey, key);
            records.put(compound,
                    new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            keys.put(id, compound);
            return id;
        }

        @Override
        public void complete(
                long id, int httpStatus, String responseCode, String responseBody) {
            var key = keys.get(id);
            var current = records.get(key);
            records.put(key, new IdempotencyRecord(
                    id, current.requestHash(), "COMPLETED", responseBody));
        }

        private static String compound(String scopeType, String scopeKey, String key) {
            return scopeType + "|" + scopeKey + "|" + key;
        }
    }
}
