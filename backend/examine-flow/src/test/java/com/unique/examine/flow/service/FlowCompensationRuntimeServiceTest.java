package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.CompletionFailurePolicy;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowCompensationRuntimeServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final FlowSession SESSION = new FlowSession(
            1L, 2L, 3L, Set.of("flow.external-task.work"));

    @Test
    void createsOneReversePlanIgnoresLateForwardResultAndRetriesEveryType() {
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(10_000L);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var workflow = new ApprovalWorkflowService(
                repository, ids, clock, 1L, 2L);
        var runtime = new FlowCompensationRuntimeService(
                (systemId, tenantId) -> repository,
                null, noRecordFlows(), ids, clock);
        var idempotency = new MemoryIdempotency();
        var json = new ObjectMapper().findAndRegisterModules();
        var external = new FlowCompensationExternalTaskService(
                (systemId, tenantId) -> repository,
                runtime, idempotency, json, clock,
                new SecureRandom(new byte[]{1, 2, 3}));
        var forward = new FlowCompletionExecutionService(
                (systemId, tenantId) -> repository,
                idempotency, ids, json, noRecordFlows(), clock,
                new SecureRandom(new byte[]{4, 5, 6}));
        forward.configureCompensations(runtime);

        var steps = List.of(
                forward("first", ApprovalCompletionStep.Compensation
                        .externalTask(externalConfig("undo.first"))),
                forward("second", ApprovalCompletionStep.Compensation
                        .webhook(webhookConfig("second"))),
                forward("third", ApprovalCompletionStep.Compensation
                        .subflow(new ApprovalCompletionStep.Subflow(901L, 4))),
                forward("failure", ApprovalCompletionStep.Compensation
                        .externalTask(externalConfig("undo.failure"))),
                forward("late", ApprovalCompletionStep.Compensation
                        .externalTask(externalConfig("undo.late"))));
        var draft = workflow.createDraft(
                "Compensating approval", List.of(3L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null,
                null, null, null, null, steps,
                CompletionFailurePolicy.COMPENSATE);
        var version = workflow.publish(draft.id());
        var parent = workflow.start(
                draft.id(), version.version(), "record:55", 9L);
        parent = workflow.approve(parent.id(), 3L, "approved");
        var originals = repository.findCompletionExecutionsByInstance(
                parent.id());

        for (var index = 0; index < 3; index++) {
            var claimed = repository.saveCompletionExecution(
                    originals.get(index).claim(
                            "worker", HASH, NOW.plusSeconds(60), NOW));
            repository.saveCompletionExecution(
                    claimed.complete(HASH, "{}", NOW));
        }
        var failureLeased = repository.saveCompletionExecution(
                originals.get(3).claim(
                        "worker", HASH, NOW.plusSeconds(60), NOW));
        var failed = repository.saveCompletionExecution(
                failureLeased.fail(
                        HASH, "FORWARD_FAILED", "failed", false, NOW));
        repository.saveCompletionExecution(originals.get(4).claim(
                "worker", HASH, NOW.plusSeconds(60), NOW));

        assertThat(runtime.onTerminalForwardFailure(
                1L, 2L, repository,
                repository.findInstanceForUpdate(parent.id()).orElseThrow(),
                failed, 3L, NOW)).isTrue();
        assertThat(runtime.onTerminalForwardFailure(
                1L, 2L, repository,
                repository.findInstanceForUpdate(parent.id()).orElseThrow(),
                failed, 3L, NOW)).isFalse();

        var plan = repository.findCompensationsByInstance(parent.id());
        assertThat(plan)
                .extracting(ApprovalCompletionCompensation::originalOrdinal)
                .containsExactly(2, 1, 0);
        assertThat(plan)
                .extracting(value -> value.step().type())
                .containsExactly(
                        ApprovalCompletionStep.Type.SUBFLOW,
                        ApprovalCompletionStep.Type.WEBHOOK,
                        ApprovalCompletionStep.Type.EXTERNAL_TASK);
        assertThat(plan)
                .extracting(ApprovalCompletionCompensation::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.WAITING,
                        ApprovalCompletionExecution.Status.WAITING);
        assertThat(repository.findCompletionExecutionsByInstance(parent.id())
                .get(4).status())
                .isEqualTo(ApprovalCompletionExecution.Status.CANCELLED);

        var late = forward.complete(
                SESSION, originals.get(4).id(),
                new FlowRequests.CompleteExternalTask(
                        "late-token", json.createObjectNode().put("late", true)),
                "late-forward-0001");
        assertThat(late.status()).isEqualTo("CANCELLED");

        var subflow = repository.saveCompensation(
                plan.get(0).startSubflow(NOW));
        subflow = repository.saveCompensation(
                subflow.failSubflow("CHILD_FAILED", "failed", NOW));
        assertThat(external.retry(
                SESSION, parent.id(), subflow.id(), "retry-subflow-0001")
                .status()).isEqualTo("AVAILABLE");
        subflow = repository.findCompensation(subflow.id()).orElseThrow();
        subflow = repository.saveCompensation(subflow.startSubflow(NOW));
        subflow = repository.saveCompensation(
                subflow.completeSubflow("{}", NOW));
        runtime.append(repository, subflow,
                ApprovalCompletionAttempt.Event.SUCCEEDED,
                3L, null, null, NOW);
        runtime.coordinateSuccess(
                1L, 2L, repository,
                repository.findInstanceForUpdate(parent.id()).orElseThrow(),
                subflow, 3L, NOW);

        var webhook = repository.findCompensationsByInstance(parent.id()).get(1);
        webhook = repository.saveCompensation(webhook.claim(
                "worker", HASH, NOW.plusSeconds(60), NOW));
        webhook = repository.saveCompensation(webhook.fail(
                HASH, "HOOK_FAILED", "failed", false, NOW));
        assertThat(external.retry(
                SESSION, parent.id(), webhook.id(), "retry-webhook-0001")
                .status()).isEqualTo("AVAILABLE");
        webhook = repository.findCompensation(webhook.id()).orElseThrow();
        webhook = repository.saveCompensation(webhook.claim(
                "worker", HASH, NOW.plusSeconds(60), NOW));
        webhook = repository.saveCompensation(
                webhook.complete(HASH, "{}", NOW));
        runtime.append(repository, webhook,
                ApprovalCompletionAttempt.Event.SUCCEEDED,
                3L, null, null, NOW);
        runtime.coordinateSuccess(
                1L, 2L, repository,
                repository.findInstanceForUpdate(parent.id()).orElseThrow(),
                webhook, 3L, NOW);

        var compensationExternal = repository
                .findCompensationsByInstance(parent.id()).get(2);
        var claim = external.claim(
                SESSION, compensationExternal.id(), "claim-undo-first-0001");
        assertThat(external.fail(
                SESSION, compensationExternal.id(),
                new FlowRequests.FailExternalTask(
                        claim.leaseToken(), "UNDO_FAILED", "failed"),
                "fail-undo-first-0001").status()).isEqualTo("FAILED");
        assertThat(external.retry(
                SESSION, parent.id(), compensationExternal.id(),
                "retry-external-0001").status()).isEqualTo("AVAILABLE");
        claim = external.claim(
                SESSION, compensationExternal.id(), "claim-undo-first-0002");
        assertThat(external.complete(
                SESSION, compensationExternal.id(),
                new FlowRequests.CompleteExternalTask(
                        claim.leaseToken(), json.createObjectNode()),
                "complete-undo-first-0001").status())
                .isEqualTo("SUCCEEDED");

        var completed = repository.findInstance(parent.id()).orElseThrow();
        assertThat(completed.status())
                .isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(completed.completionPhase())
                .isEqualTo(ApprovalInstance.CompletionPhase.COMPLETED);
        assertThat(completed.history())
                .filteredOn(event -> event.type()
                        == ApprovalHistoryEvent.Type.COMPLETION_COMPENSATED)
                .hasSize(1);
    }

    private static ApprovalCompletionStep forward(
            String code,
            ApprovalCompletionStep.Compensation compensation
    ) {
        return ApprovalCompletionStep.externalTask(
                        code, code,
                        new ApprovalCompletionStep.ExternalTask(
                                "forward." + code, 60, 1, 1024))
                .withParallelGroup("forward_group")
                .withCompensation(compensation);
    }

    private static ApprovalCompletionStep.ExternalTask externalConfig(
            String topic
    ) {
        return new ApprovalCompletionStep.ExternalTask(topic, 60, 1, 1024);
    }

    private static ApprovalCompletionStep.Webhook webhookConfig(String code) {
        return new ApprovalCompletionStep.Webhook(
                "https://hooks.example.test/" + code, null, 5, 1, 1);
    }

    private static RuntimeRecordFlowFacade noRecordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
                return null;
            }

            @Override
            public RecordFlowState bindAdditional(AdditionalBindRequest request) {
                return null;
            }

            @Override
            public RecordFlowState transition(TransitionRequest request) {
                return null;
            }
        };
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final AtomicLong ids = new AtomicLong();
        private final Map<String, Stored> values = new LinkedHashMap<>();

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key
        ) {
            var stored = values.get(scopeType + ":" + scopeKey + ":" + key);
            return stored == null ? Optional.empty()
                    : Optional.of(stored.record());
        }

        @Override
        public long begin(
                String scopeType, String scopeKey, String key,
                String requestHash, Duration ttl
        ) {
            var id = ids.incrementAndGet();
            values.put(scopeType + ":" + scopeKey + ":" + key,
                    new Stored(id, new IdempotencyRecord(
                            id, requestHash, "STARTED", null)));
            return id;
        }

        @Override
        public void complete(
                long id, int httpStatus, String responseCode,
                String responseBody
        ) {
            values.replaceAll((key, stored) -> stored.id() == id
                    ? new Stored(id, new IdempotencyRecord(
                    id, stored.record().requestHash(),
                    "COMPLETED", responseBody))
                    : stored);
        }

        private record Stored(long id, IdempotencyRecord record) {
        }
    }
}
