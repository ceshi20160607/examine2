package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSubflowRuntimeServiceTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void launchesExactlyOneChildAndPropagatesCompletedApproval() {
        var fixture = fixture();

        assertThat(fixture.runtime.launch(1, 2, fixture.executionId))
                .isTrue();
        assertThat(fixture.runtime.launch(1, 2, fixture.executionId))
                .isFalse();

        var running = fixture.repository.findSubflowRunsByExecution(
                fixture.executionId);
        assertThat(running).hasSize(1);
        var child = fixture.repository.findInstance(
                running.getFirst().childInstanceId()).orElseThrow();
        assertThat(child.recordBinding()).isNull();
        assertThat(child.startContext().rootInstanceId())
                .isEqualTo(fixture.parentId);
        assertThat(child.startContext().subflowDepth()).isEqualTo(1);
        assertThat(child.startContext().moduleCode()).isEqualTo("orders");
        assertThat(child.startContext().recordId()).isEqualTo(88L);

        fixture.workflow.approve(child.id(), 22, "approved");
        assertThat(fixture.runtime.reconcile(
                1, 2, fixture.executionId, 1)).isTrue();
        assertThat(fixture.runtime.reconcile(
                1, 2, fixture.executionId, 1)).isFalse();

        var parent = fixture.repository.findInstance(
                fixture.parentId).orElseThrow();
        var execution = fixture.repository.findCompletionExecution(
                fixture.executionId).orElseThrow();
        var applied = fixture.repository.findSubflowRunsByExecution(
                fixture.executionId).getFirst();
        assertThat(parent.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(parent.completionPhase())
                .isEqualTo(ApprovalInstance.CompletionPhase.COMPLETED);
        assertThat(execution.status())
                .isEqualTo(ApprovalCompletionExecution.Status.SUCCEEDED);
        assertThat(applied.resultAppliedAt()).isEqualTo(NOW);
    }

    @Test
    void failedRetryCreatesANewChildAndPreservesTheOldRun() {
        var fixture = fixture();
        fixture.runtime.launch(1, 2, fixture.executionId);
        var first = fixture.repository.findSubflowRunsByExecution(
                fixture.executionId).getFirst();
        fixture.workflow.reject(
                first.childInstanceId(), 22, "rejected");
        fixture.runtime.reconcile(1, 2, fixture.executionId, 1);

        var failed = fixture.repository.findCompletionExecution(
                fixture.executionId).orElseThrow();
        fixture.repository.saveCompletionExecution(
                failed.retry(NOW));
        assertThat(fixture.runtime.launch(
                1, 2, fixture.executionId)).isTrue();

        var runs = fixture.repository.findSubflowRunsByExecution(
                fixture.executionId);
        assertThat(runs).extracting(run -> run.attemptNumber())
                .containsExactly(1, 2);
        assertThat(runs.get(0).childInstanceId())
                .isNotEqualTo(runs.get(1).childInstanceId());
        assertThat(runs.get(0).resultAppliedAt()).isNotNull();
        assertThat(runs.get(1).resultAppliedAt()).isNull();
    }

    @Test
    void parentTerminationCancelsExecutionAndTerminatesPendingChild() {
        var fixture = fixture();
        fixture.runtime.launch(1, 2, fixture.executionId);
        var run = fixture.repository.findSubflowRunsByExecution(
                fixture.executionId).getFirst();

        fixture.workflow.terminate(
                fixture.parentId, 99, "administrative termination");

        assertThat(fixture.repository.findInstance(run.childInstanceId())
                .orElseThrow().status())
                .isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(fixture.repository.findCompletionExecution(
                fixture.executionId).orElseThrow().status())
                .isEqualTo(ApprovalCompletionExecution.Status.CANCELLED);
        assertThat(fixture.runtime.reconcile(
                1, 2, fixture.executionId, 1)).isTrue();
        assertThat(fixture.repository.findSubflowRunsByExecution(
                fixture.executionId).getFirst().resultAppliedAt()).isNotNull();
    }

    private static Fixture fixture() {
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(1_000);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var workflow = new ApprovalWorkflowService(
                repository, ids, clock, 1, 2);

        var childDraft = workflow.createDraft("Child", List.of(22L));
        var childVersion = workflow.publish(childDraft.id());
        var subflow = ApprovalCompletionStep.subflow(
                "child", "Child approval",
                new ApprovalCompletionStep.Subflow(
                        childDraft.id(), childVersion.version()));
        var parentDraft = workflow.createDraft(
                "Parent", List.of(11L), null, null, null,
                ApprovalMode.SEQUENTIAL, null, null, null, null, null, null,
                null, null, List.of(subflow));
        var parentVersion = workflow.publish(parentDraft.id());
        var binding = new ApprovalInstance.RecordBinding("orders", 88);
        var parent = workflow.startResolved(
                parentDraft.id(), parentVersion.version(), List.of(11L),
                ApprovalMode.SEQUENTIAL, 1,
                parentVersion.deadlinePolicies().primary(),
                parentVersion.decisionCommentPolicies().primary(),
                "parent-business", 7, binding,
                new ApprovalStartContext(
                        7, "orders", 88L, Map.of()));
        parent = workflow.approve(parent.id(), 11, "approved");
        var execution = repository.findCompletionExecutionsByInstance(
                parent.id()).getFirst();

        FlowSubflowChildLauncher launcher = (
                systemId, tenantId, lockedParent, target, launchKey) -> {
            var targetVersion = workflow.definitionVersion(
                    target.definitionId(), target.version());
            var inherited = lockedParent.startContext();
            var context = inherited.root(lockedParent.id())
                    .child(lockedParent.id(), 1);
            var child = ApprovalInstance.start(
                    ids.nextId(), targetVersion, launchKey,
                    lockedParent.requesterId(), NOW, null, context);
            return repository.saveInstance(child);
        };
        var completions = new FlowCompletionExecutionService(
                (systemId, tenantId) -> repository,
                new NoopIdempotency(), ids, new ObjectMapper(),
                noRecordFlows(), clock,
                new SecureRandom(new byte[]{1, 2, 3}));
        var runtime = new FlowSubflowRuntimeService(
                (systemId, tenantId) -> repository,
                launcher, completions, ids, clock);
        return new Fixture(
                repository, workflow, runtime,
                parent.id(), execution.id());
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

    private static final class NoopIdempotency implements IdempotencyFacade {
        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key
        ) {
            return Optional.empty();
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                java.time.Duration ttl
        ) {
            return 1;
        }

        @Override
        public void complete(
                long id,
                int httpStatus,
                String responseCode,
                String responseBody
        ) {
        }
    }

    private record Fixture(
            InMemoryApprovalRepository repository,
            ApprovalWorkflowService workflow,
            FlowSubflowRuntimeService runtime,
            long parentId,
            long executionId
    ) {
    }
}
