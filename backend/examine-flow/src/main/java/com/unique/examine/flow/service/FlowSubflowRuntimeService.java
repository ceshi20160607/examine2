package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Transactional SUBFLOW coordinator. Candidate scans are deliberately
 * unlocked; each operation then acquires parent, execution and child in that
 * order. A terminal {@link ApprovalSubflowRun} with no resultAppliedAt is the
 * restart-safe result fact, so replay requires no in-memory event delivery.
 */
@Service
public class FlowSubflowRuntimeService implements FlowSubflowCoordinator {
    private final BiFunction<Long, Long, ApprovalRepository> repositories;
    private final FlowSubflowChildLauncher childLauncher;
    private final FlowCompletionExecutionService completions;
    private final IdService ids;
    private final Clock clock;
    private FlowCompensationCoordinator compensations;

    void configureCompensations(FlowCompensationCoordinator compensations) {
        this.compensations = Objects.requireNonNull(
                compensations, "compensations");
    }

    @Autowired
    public FlowSubflowRuntimeService(
            JdbcApprovalRepositoryFactory repositories,
            FlowSubflowChildLauncher childLauncher,
            FlowCompletionExecutionService completions,
            IdService ids
    ) {
        this(
                (systemId, tenantId) -> repositories.forTenant(
                        systemId, tenantId),
                childLauncher, completions, ids, Clock.systemUTC());
    }

    FlowSubflowRuntimeService(
            BiFunction<Long, Long, ApprovalRepository> repositories,
            FlowSubflowChildLauncher childLauncher,
            FlowCompletionExecutionService completions,
            IdService ids,
            Clock clock
    ) {
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.childLauncher = Objects.requireNonNull(
                childLauncher, "childLauncher");
        this.completions = Objects.requireNonNull(
                completions, "completions");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Launches one due attempt. Duplicate dispatch observes the existing run. */
    @Transactional
    @Override
    public boolean launch(
            long systemId,
            long tenantId,
            long executionId
    ) {
        var repository = repository(systemId, tenantId);
        var preview = repository.findCompletionExecution(executionId)
                .orElse(null);
        if (preview == null) {
            return false;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        if (parent == null || parent.status() != ApprovalInstance.Status.PENDING
                || parent.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION) {
            return false;
        }
        var current = repository.findCompletionExecutionForUpdate(executionId)
                .orElse(null);
        var now = clock.instant();
        if (current == null
                || current.step().type()
                != ApprovalCompletionStep.Type.SUBFLOW
                || current.status()
                != ApprovalCompletionExecution.Status.AVAILABLE
                && current.status()
                != ApprovalCompletionExecution.Status.RETRYING
                || !current.isDueAt(now)) {
            return false;
        }
        var attempt = current.attemptCount() + 1;
        var existing = repository.findSubflowRunByExecutionAndAttempt(
                        executionId, attempt)
                .orElse(null);
        if (existing != null) {
            validateRun(current, parent, existing);
            return false;
        }

        if (depth(parent)
                >= com.unique.examine.flow.domain.ApprovalStartContext
                        .MAX_SUBFLOW_DEPTH) {
            var started = repository.saveCompletionExecution(
                    current.startSubflow(now));
            var code = "SUBFLOW_DEPTH_EXCEEDED";
            var message = "Subflow hierarchy exceeds the maximum depth of 8";
            var failed = repository.saveCompletionExecution(
                    started.failSubflow(code, message, now));
            append(
                    repository, failed,
                    ApprovalCompletionAttempt.Event.FAILED,
                    parent.requesterId(), null,
                    new Failure(code, message), now);
            if (compensations != null
                    && compensations.onTerminalForwardFailure(
                            systemId, tenantId, repository, parent, failed,
                            parent.requesterId(), now)) {
                return true;
            }
            repository.saveCompletionProgress(parent.advanceCompletion(
                    repository.findCompletionExecutionsByInstance(
                            parent.id())));
            return true;
        }

        var launchKey = launchKey(executionId, attempt);
        var started = repository.saveCompletionExecution(
                current.startSubflow(now));
        var child = childLauncher.launch(
                systemId, tenantId, parent, current.step().subflow(),
                launchKey);
        var context = Objects.requireNonNull(
                child.startContext(), "Subflow child start context");
        repository.appendSubflowRun(ApprovalSubflowRun.launch(
                ids.nextId(),
                started.id(),
                started.attemptCount(),
                launchKey,
                child.id(),
                current.step().subflow(),
                Objects.requireNonNull(context.rootInstanceId()),
                context.subflowDepth(),
                now
        ));
        append(
                repository, started,
                ApprovalCompletionAttempt.Event.STARTED,
                parent.requesterId(), null, null, now);
        repository.saveCompletionProgress(parent.advanceCompletion(
                repository.findCompletionExecutionsByInstance(parent.id())));
        return true;
    }

    /**
     * Observes a child terminal state and applies (or no-ops) its durable
     * result. Replaying the same execution/attempt after application is safe.
     */
    @Transactional
    @Override
    public boolean reconcile(
            long systemId,
            long tenantId,
            long executionId,
            int attempt
    ) {
        var repository = repository(systemId, tenantId);
        var candidate = repository.findSubflowRunByExecutionAndAttempt(
                        executionId, attempt)
                .orElse(null);
        if (candidate == null || candidate.resultAppliedAt() != null) {
            return false;
        }
        var preview = repository.findCompletionExecution(executionId)
                .orElse(null);
        if (preview == null) {
            return false;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        if (parent == null) {
            return false;
        }
        var execution = repository.findCompletionExecutionForUpdate(
                        executionId)
                .orElse(null);
        if (execution == null) {
            return false;
        }
        var run = repository.findSubflowRunByExecutionAndAttempt(
                        executionId, attempt)
                .orElse(null);
        if (run == null || run.resultAppliedAt() != null) {
            return false;
        }
        validateRun(execution, parent, run);
        var child = repository.findInstanceForUpdate(run.childInstanceId())
                .orElseThrow(() -> conflict(
                        "Subflow child instance was not found"));
        validateChild(run, parent, child);

        var now = clock.instant();
        if (!run.terminal()) {
            var terminal = childResult(child);
            if (terminal == null) {
                return false;
            }
            run = repository.saveSubflowRun(run.observeTerminal(
                    terminal,
                    child.completedAt() == null ? now : child.completedAt()));
        }
        if (!run.pendingResult()) {
            return false;
        }

        // A late result is durable audit state only; it cannot resurrect a
        // terminal parent, cancelled execution or an older retry attempt.
        if (parent.status() != ApprovalInstance.Status.PENDING
                || parent.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                || execution.status()
                == ApprovalCompletionExecution.Status.CANCELLED
                || execution.status()
                != ApprovalCompletionExecution.Status.RUNNING
                || execution.attemptCount() != run.attemptNumber()) {
            repository.saveSubflowRun(run.markResultApplied(now));
            return true;
        }

        var actorId = child.history().isEmpty()
                ? child.requesterId()
                : child.history().getLast().actorId();
        if (run.status()
                == ApprovalSubflowRun.Status.APPROVED_COMPLETED) {
            var resultJson = "{\"childInstanceId\":\""
                    + child.id() + "\"}";
            var succeeded = repository.saveCompletionExecution(
                    execution.completeSubflow(resultJson, now));
            append(
                    repository, succeeded,
                    ApprovalCompletionAttempt.Event.SUCCEEDED,
                    actorId, succeeded.resultJson(), null, now);
            completions.coordinateSuccess(
                    systemId, tenantId, repository, succeeded, parent,
                    actorId, now);
        } else {
            var failureCode = "SUBFLOW_CHILD_" + run.status().name();
            var failureMessage = switch (run.status()) {
                case REJECTED -> "Child flow was rejected";
                case WITHDRAWN -> "Child flow was withdrawn";
                case TERMINATED -> "Child flow was terminated";
                case RUNNING, APPROVED_COMPLETED ->
                        throw new IllegalStateException(
                                "Subflow terminal result is inconsistent");
            };
            var failed = repository.saveCompletionExecution(
                    execution.failSubflow(
                            failureCode, failureMessage, now));
            append(
                    repository, failed,
                    ApprovalCompletionAttempt.Event.FAILED,
                    actorId, null,
                    new Failure(failureCode, failureMessage), now);
            if (compensations == null
                    || !compensations.onTerminalForwardFailure(
                            systemId, tenantId, repository, parent, failed,
                            actorId, now)) {
                repository.saveCompletionProgress(parent.advanceCompletion(
                        repository.findCompletionExecutionsByInstance(
                                parent.id())));
            }
        }
        repository.saveSubflowRun(run.markResultApplied(now));
        return true;
    }

    private static ApprovalSubflowRun.Status childResult(
            ApprovalInstance child
    ) {
        return switch (child.status()) {
            case PENDING -> null;
            case APPROVED -> child.completionPhase()
                    == ApprovalInstance.CompletionPhase.COMPLETED
                    ? ApprovalSubflowRun.Status.APPROVED_COMPLETED
                    : null;
            case REJECTED -> ApprovalSubflowRun.Status.REJECTED;
            case WITHDRAWN -> ApprovalSubflowRun.Status.WITHDRAWN;
            case TERMINATED -> ApprovalSubflowRun.Status.TERMINATED;
        };
    }

    private static void validateRun(
            ApprovalCompletionExecution execution,
            ApprovalInstance parent,
            ApprovalSubflowRun run
    ) {
        var target = execution.step().subflow();
        if (execution.step().type() != ApprovalCompletionStep.Type.SUBFLOW
                || run.executionId() != execution.id()
                || run.targetDefinitionId() != target.definitionId()
                || run.targetVersion() != target.version()
                || run.rootInstanceId() != root(parent)
                || run.depth() != depth(parent) + 1) {
            throw conflict("Subflow run does not match its frozen parent scope");
        }
    }

    private static void validateChild(
            ApprovalSubflowRun run,
            ApprovalInstance parent,
            ApprovalInstance child
    ) {
        var context = child.startContext();
        if (child.definitionId() != run.targetDefinitionId()
                || child.definitionVersion() != run.targetVersion()
                || child.requesterId() != parent.requesterId()
                || context == null
                || !Objects.equals(
                        context.rootInstanceId(), run.rootInstanceId())
                || context.subflowDepth() != run.depth()) {
            throw conflict("Subflow child link crosses its frozen scope");
        }
    }

    private void append(
            ApprovalRepository repository,
            ApprovalCompletionExecution execution,
            ApprovalCompletionAttempt.Event event,
            long actorId,
            String resultJson,
            Failure failure,
            Instant occurredAt
    ) {
        var sequence = repository.findCompletionAttempts(execution.id())
                .stream()
                .mapToInt(ApprovalCompletionAttempt::eventSequence)
                .max()
                .orElse(0) + 1;
        repository.appendCompletionAttempt(new ApprovalCompletionAttempt(
                ids.nextId(), execution.id(), execution.attemptCount(),
                sequence, event, actorId, null, null, resultJson,
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(), occurredAt));
    }

    private ApprovalRepository repository(long systemId, long tenantId) {
        if (systemId <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException(
                    "Subflow system and tenant scope must be positive");
        }
        return repositories.apply(systemId, tenantId);
    }

    static String launchKey(long executionId, int attempt) {
        if (executionId <= 0 || attempt < 1) {
            throw new IllegalArgumentException(
                    "Subflow launch identity is invalid");
        }
        return sha256("subflow:" + executionId + ":" + attempt);
    }

    private static long root(ApprovalInstance parent) {
        return parent.startContext() == null
                || parent.startContext().rootInstanceId() == null
                ? parent.id()
                : parent.startContext().rootInstanceId();
    }

    private static int depth(ApprovalInstance parent) {
        return parent.startContext() == null
                ? 0
                : parent.startContext().subflowDepth();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.SUBFLOW_RUN_CONFLICT, message);
    }

    private record Failure(String code, String message) {
    }
}
