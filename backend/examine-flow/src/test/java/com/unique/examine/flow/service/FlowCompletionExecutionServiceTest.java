package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStage;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.CompletionFailurePolicy;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowCompletionExecutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final FlowSession SESSION = new FlowSession(
            1, 2, 3, Set.of("flow.external-task.work"));

    @Test
    void persistsOnlyLeaseHashAndDoesNotRevealTokenOnReplay() {
        var repository = new StubRepository();
        repository.execution = execution(3, NOW);
        repository.instance = instance(repository.execution);
        var idempotency = new StubIdempotency();
        var service = service(repository, idempotency);

        var first = service.claim(SESSION, 100, "claim-key-0001");
        var replay = service.claim(SESSION, 100, "claim-key-0001");

        assertThat(first.leaseToken()).isNotBlank();
        assertThat(repository.execution.lease().tokenHash())
                .matches("^[0-9a-f]{64}$")
                .isNotEqualTo(first.leaseToken());
        assertThat(idempotency.responseBody)
                .doesNotContain(first.leaseToken());
        assertThat(replay.leaseToken()).isNull();
        assertThat(replay.execution().status()).isEqualTo("LEASED");
    }

    @Test
    void expiredMaximumAttemptBecomesDurableFailureWithoutNewToken() {
        var repository = new StubRepository();
        var available = execution(1, NOW.minusSeconds(120));
        repository.execution = available.claim(
                "worker", hash(), NOW.minusSeconds(30),
                NOW.minusSeconds(60));
        repository.instance = instance(repository.execution);
        var service = service(repository, new StubIdempotency());

        var response = service.claim(
                SESSION, 100, "claim-key-0002");

        assertThat(response.leaseToken()).isNull();
        assertThat(response.execution().status()).isEqualTo("FAILED");
        assertThat(repository.execution.status())
                .isEqualTo(ApprovalCompletionExecution.Status.FAILED);
        assertThat(repository.attempts)
                .extracting(ApprovalCompletionAttempt::event)
                .containsExactly(
                        ApprovalCompletionAttempt.Event.LEASE_EXPIRED);
    }

    @Test
    void finalHumanApprovalMaterializesOrderedExecutionsBeforeTerminalEffects() {
        var repository = new StubRepository();
        var counter = new AtomicLong(10_000);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return counter.incrementAndGet();
            }
        };
        var workflow = new ApprovalWorkflowService(
                repository, ids, Clock.fixed(NOW, ZoneOffset.UTC), 1, 2);
        var steps = List.of(
                ApprovalCompletionStep.externalTask(
                        "archive", "Archive",
                        new ApprovalCompletionStep.ExternalTask(
                                "records.archive", 60, 3, 1024)),
                ApprovalCompletionStep.externalTask(
                        "index", "Index",
                        new ApprovalCompletionStep.ExternalTask(
                                "records.index", 60, 3, 1024))
        );
        var draft = workflow.createDraft(
                "Approval", List.of(3L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null,
                null, null, null, null, steps);
        var version = workflow.publish(draft.id());
        var started = workflow.start(
                draft.id(), version.version(), "record:42", 9);

        var decided = workflow.approve(started.id(), 3, "approved");

        assertThat(decided.status())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(decided.completionPhase().name())
                .isEqualTo("EXTERNAL_EXECUTION");
        assertThat(decided.completedAt()).isNull();
        assertThat(repository.completionExecutions)
                .extracting(ApprovalCompletionExecution::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.WAITING);
        assertThat(repository.attempts)
                .extracting(ApprovalCompletionAttempt::event)
                .containsExactly(ApprovalCompletionAttempt.Event.ACTIVATED);
    }

    @Test
    void mixedParallelStageWaitsForEveryMemberThenActivatesNextStageOnce() {
        var repository = new StubRepository();
        var service = service(repository, new StubIdempotency());
        var pending = groupedInstance(repository, List.of(
                external("archive", "post_commit"),
                webhook("notify", "post_commit"),
                subflow("child", "post_commit"),
                external("index", null)));

        assertThat(repository.completionExecutions)
                .extracting(ApprovalCompletionExecution::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.WAITING);
        assertThat(pending.activeCompletionOrdinals())
                .containsExactly(0, 1, 2);

        succeed(repository, service, 2, NOW.plusSeconds(1));
        assertThat(repository.instance.status())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(repository.completionExecutions.get(3).status())
                .isEqualTo(ApprovalCompletionExecution.Status.WAITING);

        succeed(repository, service, 0, NOW.plusSeconds(2));
        assertThat(repository.instance.status())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(repository.completionExecutions.get(3).status())
                .isEqualTo(ApprovalCompletionExecution.Status.WAITING);

        succeed(repository, service, 1, NOW.plusSeconds(3));
        assertThat(repository.instance.activeCompletionOrdinal())
                .isEqualTo(3);
        assertThat(repository.completionExecutions)
                .extracting(ApprovalCompletionExecution::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.SUCCEEDED,
                        ApprovalCompletionExecution.Status.SUCCEEDED,
                        ApprovalCompletionExecution.Status.SUCCEEDED,
                        ApprovalCompletionExecution.Status.AVAILABLE);
        assertThat(repository.attempts)
                .filteredOn(attempt -> attempt.event()
                        == ApprovalCompletionAttempt.Event.STAGE_JOINED)
                .hasSize(1);
        assertThat(repository.lockedStageOrdinals)
                .containsExactly(
                        List.of(0, 1, 2),
                        List.of(0, 1, 2),
                        List.of(0, 1, 2),
                        List.of(3));

        succeed(repository, service, 3, NOW.plusSeconds(4));
        assertThat(repository.instance.status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
    }

    @Test
    void failedParallelMemberRetriesWithoutChangingSuccessfulSibling() {
        var repository = new StubRepository();
        var service = service(repository, new StubIdempotency());
        groupedInstance(repository, List.of(
                external("archive", "post_commit"),
                external("index", "post_commit")));
        succeed(repository, service, 0, NOW.plusSeconds(1));

        var second = repository.completionExecutions.get(1);
        var leased = second.claim(
                "worker", hash(), NOW.plusSeconds(90), NOW.plusSeconds(2));
        var failed = leased.fail(
                hash(), "INDEX_FAILED", "Indexing failed", false,
                NOW.plusSeconds(3));
        repository.saveCompletionExecution(failed);

        var retried = service.retry(
                SESSION,
                repository.instance.id(),
                failed.id(),
                "retry-key-0001");

        assertThat(retried.status()).isEqualTo("AVAILABLE");
        assertThat(repository.completionExecutions.get(0).status())
                .isEqualTo(ApprovalCompletionExecution.Status.SUCCEEDED);
        assertThat(repository.completionExecutions.get(1).status())
                .isEqualTo(ApprovalCompletionExecution.Status.AVAILABLE);

        succeed(repository, service, 1, NOW.plusSeconds(4));
        assertThat(repository.instance.status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(repository.attempts)
                .filteredOn(attempt -> attempt.event()
                        == ApprovalCompletionAttempt.Event.STAGE_JOINED)
                .hasSize(1);
    }

    private static ApprovalInstance groupedInstance(
            StubRepository repository,
            List<ApprovalCompletionStep> steps
    ) {
        var counter = new AtomicLong(20_000);
        var workflow = new ApprovalWorkflowService(
                repository,
                new IdService() {
                    @Override
                    public long nextId() {
                        return counter.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC), 1, 2);
        var draft = workflow.createDraft(
                "Approval", List.of(3L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null,
                null, null, null, null, steps);
        var version = workflow.publish(draft.id());
        var started = workflow.start(
                draft.id(), version.version(), "record:42", 9);
        return workflow.approve(started.id(), 3, "approved");
    }

    private static void succeed(
            StubRepository repository,
            FlowCompletionExecutionService service,
            int ordinal,
            Instant now
    ) {
        var current = repository.completionExecutions.get(ordinal);
        final ApprovalCompletionExecution succeeded;
        if (current.step().type() == ApprovalCompletionStep.Type.SUBFLOW) {
            succeeded = current.startSubflow(now).completeSubflow("{}", now);
        } else {
            var leased = current.claim(
                    "worker", hash(), now.plusSeconds(60), now);
            succeeded = leased.complete(hash(), "{}", now);
        }
        repository.saveCompletionExecution(succeeded);
        service.coordinateSuccess(
                1, 2, repository, succeeded, repository.instance, 9, now);
    }

    private static ApprovalCompletionStep external(
            String code,
            String parallelGroup
    ) {
        return ApprovalCompletionStep.externalTask(
                        code, code,
                        new ApprovalCompletionStep.ExternalTask(
                                "records." + code, 60, 3, 1024))
                .withParallelGroup(parallelGroup);
    }

    private static ApprovalCompletionStep webhook(
            String code,
            String parallelGroup
    ) {
        return ApprovalCompletionStep.webhook(
                        code, code,
                        new ApprovalCompletionStep.Webhook(
                                "https://hooks.example.test/" + code,
                                null, 5, 3, 2))
                .withParallelGroup(parallelGroup);
    }

    private static ApprovalCompletionStep subflow(
            String code,
            String parallelGroup
    ) {
        return ApprovalCompletionStep.subflow(
                        code, code,
                        new ApprovalCompletionStep.Subflow(901, 4))
                .withParallelGroup(parallelGroup);
    }

    private static FlowCompletionExecutionService service(
            StubRepository repository,
            StubIdempotency idempotency
    ) {
        var counter = new AtomicLong(1000);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return counter.incrementAndGet();
            }
        };
        return new FlowCompletionExecutionService(
                (systemId, tenantId) -> repository,
                idempotency,
                ids,
                new ObjectMapper(),
                noRecordFlows(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[]{1, 2, 3})
        );
    }

    private static ApprovalCompletionExecution execution(
            int maxAttempts,
            Instant availableAt
    ) {
        var step = ApprovalCompletionStep.externalTask(
                "archive",
                "Archive",
                new ApprovalCompletionStep.ExternalTask(
                        "records.archive", 60, maxAttempts, 1024)
        );
        return ApprovalCompletionExecution.materialize(
                100, 200, 300, 1, 0, step, "{}", availableAt);
    }

    private static ApprovalInstance instance(
            ApprovalCompletionExecution execution
    ) {
        var draft = new ApprovalDefinitionDraft(
                300, "Approval", List.of(3L), 1, NOW.minusSeconds(10),
                null, null, null, ApprovalMode.SEQUENTIAL,
                null, null, null, null, null, null, null,
                null, List.of(execution.step()),
                CompletionFailurePolicy.MANUAL_RETRY);
        var definition = ApprovalDefinitionVersion.publish(
                draft, 1, NOW.minusSeconds(9));
        return ApprovalInstance.start(
                        200, definition, "record:200", 9L,
                        NOW.minusSeconds(5))
                .approve(3L, "", NOW.minusSeconds(4))
                .beginCompletion(List.of(execution));
    }

    private static String hash() {
        return "a".repeat(64);
    }

    private static RuntimeRecordFlowFacade noRecordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
                return null;
            }

            @Override
            public RecordFlowState bindAdditional(
                    AdditionalBindRequest request
            ) {
                return null;
            }

            @Override
            public RecordFlowState transition(TransitionRequest request) {
                return null;
            }
        };
    }

    private static final class StubIdempotency
            implements IdempotencyFacade {
        private IdempotencyRecord record;
        private String responseBody;

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType,
                String scopeKey,
                String key
        ) {
            return Optional.ofNullable(record);
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                java.time.Duration ttl
        ) {
            record = new IdempotencyRecord(
                    1, requestHash, "STARTED", null);
            return 1;
        }

        @Override
        public void complete(
                long id,
                int httpStatus,
                String responseCode,
                String responseBody
        ) {
            this.responseBody = responseBody;
            record = new IdempotencyRecord(
                    id, record.requestHash(), "COMPLETED", responseBody);
        }
    }

    private static final class StubRepository
            implements ApprovalRepository {
        private ApprovalCompletionExecution execution;
        private List<ApprovalCompletionExecution> completionExecutions =
                List.of();
        private final List<ApprovalCompletionAttempt> attempts =
                new ArrayList<>();
        private final List<List<Integer>> lockedStageOrdinals =
                new ArrayList<>();
        private ApprovalDefinitionDraft draft;
        private ApprovalDefinitionVersion version;
        private ApprovalInstance instance;

        @Override
        public List<ApprovalCompletionExecution>
                materializeCompletionExecutions(
                        List<ApprovalCompletionExecution> executions
                ) {
            completionExecutions = List.copyOf(executions);
            execution = executions.isEmpty() ? null : executions.getFirst();
            return completionExecutions;
        }

        @Override
        public Optional<ApprovalCompletionExecution>
                findCompletionExecution(long executionId) {
            if (execution != null && execution.id() == executionId) {
                return Optional.of(execution);
            }
            return completionExecutions.stream()
                    .filter(value -> value.id() == executionId)
                    .findFirst();
        }

        @Override
        public Optional<ApprovalCompletionExecution>
                findCompletionExecutionForUpdate(long executionId) {
            return findCompletionExecution(executionId);
        }

        @Override
        public ApprovalCompletionExecution saveCompletionExecution(
                ApprovalCompletionExecution execution
        ) {
            this.execution = execution;
            if (!completionExecutions.isEmpty()) {
                var updated = new ArrayList<>(completionExecutions);
                for (var index = 0; index < updated.size(); index++) {
                    if (updated.get(index).id() == execution.id()) {
                        updated.set(index, execution);
                        completionExecutions = List.copyOf(updated);
                        break;
                    }
                }
            }
            return execution;
        }

        @Override
        public List<ApprovalCompletionExecution> saveCompletionExecutions(
                List<ApprovalCompletionExecution> executions
        ) {
            executions.forEach(this::saveCompletionExecution);
            return List.copyOf(executions);
        }

        @Override
        public List<ApprovalCompletionExecution> findCompletionStageForUpdate(
                long instanceId,
                int stageCursor
        ) {
            var stage = ApprovalCompletionStage.stageAtOrdinal(
                    completionExecutions, stageCursor);
            var locked = completionExecutions.subList(
                    stage.cursor(), stage.endExclusive());
            lockedStageOrdinals.add(locked.stream()
                    .map(ApprovalCompletionExecution::ordinal)
                    .toList());
            return List.copyOf(locked);
        }

        @Override
        public ApprovalCompletionAttempt appendCompletionAttempt(
                ApprovalCompletionAttempt attempt
        ) {
            attempts.add(attempt);
            return attempt;
        }

        @Override
        public List<ApprovalCompletionAttempt> findCompletionAttempts(
                long executionId
        ) {
            return attempts.stream()
                    .filter(attempt -> attempt.executionId() == executionId)
                    .toList();
        }

        @Override
        public ApprovalDefinitionDraft saveDraft(ApprovalDefinitionDraft value) {
            draft = value;
            return value;
        }

        @Override
        public Optional<ApprovalDefinitionDraft> findDraft(long definitionId) {
            return draft != null && draft.id() == definitionId
                    ? Optional.of(draft) : Optional.empty();
        }

        @Override
        public List<ApprovalDefinitionDraft> findDrafts(int offset, int limit) {
            return List.of();
        }

        @Override
        public long countDrafts() {
            return 0;
        }

        @Override
        public ApprovalDefinitionVersion saveVersion(ApprovalDefinitionVersion value) {
            version = value;
            return value;
        }

        @Override
        public Optional<ApprovalDefinitionVersion> findVersion(
                long definitionId, int version
        ) {
            return this.version != null
                    && this.version.definitionId() == definitionId
                    && this.version.version() == version
                    ? Optional.of(this.version) : Optional.empty();
        }

        @Override
        public Optional<ApprovalDefinitionVersion> findLatestVersion(long definitionId) {
            return version != null && version.definitionId() == definitionId
                    ? Optional.of(version) : Optional.empty();
        }

        @Override
        public List<ApprovalDefinitionVersion> findTriggerCandidates(
                String moduleCode, TriggerBinding.Event event
        ) {
            return List.of();
        }

        @Override
        public Optional<FlowTriggerDispatch> findTriggerDispatchForUpdate(
                String eventKey
        ) {
            return Optional.empty();
        }

        @Override
        public FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch value) {
            return value;
        }

        @Override
        public ApprovalInstance saveInstance(ApprovalInstance value) {
            instance = value;
            return value;
        }

        @Override
        public ApprovalInstance saveCompletionProgress(
                ApprovalInstance value
        ) {
            instance = value;
            return value;
        }

        @Override
        public Optional<ApprovalInstance> findInstance(long instanceId) {
            return instance != null && instance.id() == instanceId
                    ? Optional.of(instance) : Optional.empty();
        }

        @Override
        public Optional<ApprovalInstance> findInstanceForUpdate(
                long instanceId
        ) {
            return findInstance(instanceId);
        }

        @Override
        public List<ApprovalCompletionExecution>
                findCompletionExecutionsByInstance(long instanceId) {
            return completionExecutions.stream()
                    .filter(value -> value.instanceId() == instanceId)
                    .toList();
        }

        @Override
        public List<ApprovalInstance> findInstances(int offset, int limit) {
            return List.of();
        }

        @Override
        public long countInstances() {
            return 0;
        }

        @Override
        public List<ApprovalInstance> findApprovalTasks(
                long approverId,
                ApprovalTaskStatus status,
                int offset,
                int limit
        ) {
            return List.of();
        }

        @Override
        public long countApprovalTasks(
                long approverId, ApprovalTaskStatus status
        ) {
            return 0;
        }

        @Override
        public List<ApprovalInstance> findClaimableTasks(
                int offset, int limit
        ) {
            return List.of();
        }

        @Override
        public long countClaimableTasks() {
            return 0;
        }
    }
}
