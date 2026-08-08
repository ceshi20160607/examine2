package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalCompensationAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.CompletionFailurePolicy;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Durable COMPENSATE plan creation and reverse all-success coordinator. */
@Service
public class FlowCompensationRuntimeService
        implements FlowCompensationCoordinator {
    private final BiFunction<Long, Long, ApprovalRepository> repositories;
    private final FlowRequestServiceFactory workflows;
    private final RuntimeRecordFlowFacade recordFlows;
    private final IdService ids;
    private final Clock clock;

    @Autowired
    public FlowCompensationRuntimeService(
            JdbcApprovalRepositoryFactory repositories,
            FlowRequestServiceFactory workflows,
            RuntimeRecordFlowFacade recordFlows,
            IdService ids
    ) {
        this(
                (systemId, tenantId) -> repositories.forTenant(
                        systemId, tenantId),
                workflows, recordFlows, ids, Clock.systemUTC());
    }

    FlowCompensationRuntimeService(
            BiFunction<Long, Long, ApprovalRepository> repositories,
            FlowRequestServiceFactory workflows,
            RuntimeRecordFlowFacade recordFlows,
            IdService ids,
            Clock clock
    ) {
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.workflows = workflows;
        this.recordFlows = Objects.requireNonNull(recordFlows, "recordFlows");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean onTerminalForwardFailure(
            long systemId,
            long tenantId,
            ApprovalRepository repository,
            ApprovalInstance lockedParent,
            ApprovalCompletionExecution failed,
            long actorId,
            Instant occurredAt
    ) {
        if (failed.status() != ApprovalCompletionExecution.Status.FAILED
                || lockedParent.status() != ApprovalInstance.Status.PENDING
                || lockedParent.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                || lockedParent.completionFailurePolicy()
                != CompletionFailurePolicy.COMPENSATE) {
            return false;
        }
        if (!repository.findCompensationsByInstance(
                lockedParent.id()).isEmpty()) {
            return false;
        }

        var originals = lockOriginals(repository, lockedParent.id());
        var observedFailure = originals.stream()
                .filter(value -> value.id() == failed.id())
                .findFirst()
                .orElse(null);
        if (observedFailure == null
                || observedFailure.status()
                != ApprovalCompletionExecution.Status.FAILED) {
            return false;
        }

        var closed = new ArrayList<ApprovalCompletionExecution>(
                originals.size());
        for (var original : originals) {
            if (terminal(original.status())) {
                closed.add(original);
                continue;
            }
            var cancelled = repository.saveCompletionExecution(
                    original.cancel(occurredAt));
            appendForwardCancelled(
                    repository, cancelled, actorId, occurredAt);
            if (original.step().type()
                    == ApprovalCompletionStep.Type.SUBFLOW
                    && original.status()
                    == ApprovalCompletionExecution.Status.RUNNING) {
                terminateForwardChild(
                        systemId, tenantId, repository, original,
                        actorId, occurredAt);
            }
            closed.add(cancelled);
        }
        closed.sort(Comparator.comparingInt(
                ApprovalCompletionExecution::ordinal));

        var compensating = repository.saveCompletionProgress(
                lockedParent.beginCompensation(closed));
        var plan = ApprovalCompletionCompensation.materializePlan(
                closed, ids::nextId, occurredAt);
        var persisted = plan.isEmpty()
                ? List.<ApprovalCompletionCompensation>of()
                : repository.materializeCompensationPlan(plan);
        if (persisted.isEmpty()) {
            completeParent(
                    systemId, tenantId, repository, compensating,
                    closed, actorId, occurredAt);
            return true;
        }
        append(
                repository,
                persisted.getFirst(),
                ApprovalCompletionAttempt.Event.ACTIVATED,
                actorId, null, null, occurredAt);
        return true;
    }

    /** Parent is locked by caller; compensation rows lock in reverse order. */
    void coordinateSuccess(
            long systemId,
            long tenantId,
            ApprovalRepository repository,
            ApprovalInstance lockedParent,
            ApprovalCompletionCompensation completed,
            long actorId,
            Instant occurredAt
    ) {
        if (lockedParent.status() != ApprovalInstance.Status.PENDING
                || lockedParent.completionPhase()
                != ApprovalInstance.CompletionPhase.COMPENSATING) {
            return;
        }
        var originals = lockOriginals(repository, lockedParent.id());
        var plan = repository.findCompensationsForUpdate(lockedParent.id());
        if (plan.stream().noneMatch(value -> value.id() == completed.id())) {
            return;
        }
        var next = plan.stream()
                .filter(value -> value.status()
                        != ApprovalCompletionExecution.Status.SUCCEEDED)
                .findFirst()
                .orElse(null);
        if (next == null) {
            completeParent(
                    systemId, tenantId, repository, lockedParent,
                    originals, actorId, occurredAt);
            return;
        }
        if (next.status() == ApprovalCompletionExecution.Status.WAITING) {
            var activated = repository.saveCompensation(
                    next.activate(occurredAt));
            append(
                    repository, activated,
                    ApprovalCompletionAttempt.Event.ACTIVATED,
                    actorId, null, null, occurredAt);
        }
        repository.saveCompletionProgress(
                lockedParent.advanceCompensation(originals));
    }

    @Transactional(readOnly = true)
    public List<FlowViews.CompensationExecution> executions(
            FlowSession session,
            long instanceId
    ) {
        var repository = repository(session);
        return repository.findCompensationsByInstance(instanceId).stream()
                .map(value -> view(repository, value))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FlowViews.CompensationHistory> history(
            FlowSession session,
            long instanceId
    ) {
        var repository = repository(session);
        return repository.findCompensationsByInstance(instanceId).stream()
                .flatMap(compensation -> repository
                        .findCompensationAttempts(compensation.id()).stream()
                        .map(attempt -> FlowViews.CompensationHistory.from(
                                compensation,
                                attempt,
                                repository.findCompensationSubflowRuns(
                                                compensation.id()).stream()
                                        .filter(run -> run.run().attemptNumber()
                                                == attempt.fact()
                                                .attemptNumber())
                                        .findFirst()
                                        .orElse(null))))
                .sorted(Comparator.comparing(
                        FlowViews.CompensationHistory::occurredAt))
                .toList();
    }

    FlowViews.CompensationExecution view(
            ApprovalRepository repository,
            ApprovalCompletionCompensation value
    ) {
        return FlowViews.CompensationExecution.from(
                value,
                repository.findCompensationAttempts(value.id()),
                repository.findCompensationSubflowRuns(value.id()));
    }

    void append(
            ApprovalRepository repository,
            ApprovalCompletionCompensation compensation,
            ApprovalCompletionAttempt.Event event,
            Long actorId,
            String failureCode,
            String failureMessage,
            Instant occurredAt
    ) {
        var sequence = repository.findCompensationAttempts(
                        compensation.id()).stream()
                .map(ApprovalCompensationAttempt::fact)
                .mapToInt(ApprovalCompletionAttempt::eventSequence)
                .max().orElse(0) + 1;
        var execution = compensation.execution();
        repository.appendCompensationAttempt(
                new ApprovalCompensationAttempt(
                        compensation.id(),
                        new ApprovalCompletionAttempt(
                                ids.nextId(), compensation.id(),
                                compensation.attemptCount(), sequence, event,
                                actorId,
                                execution.lease() == null
                                        ? null
                                        : execution.lease().owner(),
                                null,
                                event == ApprovalCompletionAttempt.Event.SUCCEEDED
                                        ? execution.resultJson()
                                        : null,
                                failureCode, failureMessage, occurredAt)));
    }

    private List<ApprovalCompletionExecution> lockOriginals(
            ApprovalRepository repository,
            long instanceId
    ) {
        return repository.findCompletionExecutionsByInstance(instanceId)
                .stream()
                .sorted(Comparator
                        .comparingInt(ApprovalCompletionExecution::ordinal)
                        .thenComparingLong(ApprovalCompletionExecution::id))
                .map(value -> repository.findCompletionExecutionForUpdate(
                                value.id())
                        .orElseThrow(FlowCompensationRuntimeService::notFound))
                .toList();
    }

    private void terminateForwardChild(
            long systemId,
            long tenantId,
            ApprovalRepository repository,
            ApprovalCompletionExecution execution,
            long actorId,
            Instant occurredAt
    ) {
        var run = repository.findSubflowRunByExecutionAndAttempt(
                        execution.id(), execution.attemptCount())
                .orElse(null);
        if (run == null || run.terminal()) {
            return;
        }
        var child = repository.findInstance(run.childInstanceId())
                .orElse(null);
        if (child == null || child.status() != ApprovalInstance.Status.PENDING) {
            return;
        }
        if (workflows != null) {
            workflows.forTenant(systemId, tenantId).terminate(
                    child.id(), actorId,
                    "Parent completion entered compensation");
        }
    }

    private void appendForwardCancelled(
            ApprovalRepository repository,
            ApprovalCompletionExecution execution,
            long actorId,
            Instant occurredAt
    ) {
        var sequence = repository.findCompletionAttempts(execution.id())
                .stream()
                .mapToInt(ApprovalCompletionAttempt::eventSequence)
                .max().orElse(0) + 1;
        repository.appendCompletionAttempt(new ApprovalCompletionAttempt(
                ids.nextId(), execution.id(), execution.attemptCount(),
                sequence, ApprovalCompletionAttempt.Event.CANCELLED,
                actorId, null, null, null, null, null, occurredAt));
    }

    private void completeParent(
            long systemId,
            long tenantId,
            ApprovalRepository repository,
            ApprovalInstance parent,
            List<ApprovalCompletionExecution> originals,
            long actorId,
            Instant occurredAt
    ) {
        var terminal = repository.saveInstance(
                parent.completeCompensation(
                        originals, actorId, occurredAt));
        if (terminal.recordBinding() != null) {
            recordFlows.transition(
                    new RuntimeRecordFlowFacade.TransitionRequest(
                            systemId, tenantId, terminal.id(),
                            RuntimeRecordFlowFacade.FlowStatus.TERMINATED,
                            actorId, occurredAt));
        }
    }

    private ApprovalRepository repository(FlowSession session) {
        Objects.requireNonNull(session, "session");
        return repositories.apply(session.systemId(), session.tenantId());
    }

    private static boolean terminal(
            ApprovalCompletionExecution.Status status
    ) {
        return status == ApprovalCompletionExecution.Status.SUCCEEDED
                || status == ApprovalCompletionExecution.Status.FAILED
                || status == ApprovalCompletionExecution.Status.CANCELLED;
    }

    private static ApprovalDomainException notFound() {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_EXECUTION_NOT_FOUND,
                "Completion execution was not found");
    }
}
