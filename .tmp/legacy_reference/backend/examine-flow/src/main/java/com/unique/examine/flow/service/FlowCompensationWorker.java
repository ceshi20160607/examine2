package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompensationSubflowRun;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import com.unique.examine.flow.transport.WebhookDeliveryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Webhook and exact-version subflow compensation poller. */
@Component
public final class FlowCompensationWorker {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(FlowCompensationWorker.class);
    private static final String SELECT_SCOPES = """
            SELECT system_id,tenant_id
            FROM un_flow_completion_compensation
            WHERE execution_type IN ('WEBHOOK','SUBFLOW')
              AND (status='RUNNING'
                OR status IN ('AVAILABLE','RETRYING') AND available_at<=?
                OR status='LEASED' AND lease_expires_at<=?)
            GROUP BY system_id,tenant_id
            ORDER BY system_id,tenant_id
            LIMIT ?
            """;
    public static final int DEFAULT_BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final BiFunction<Long, Long, ApprovalRepository> repositories;
    private final FlowCompensationRuntimeService runtime;
    private final FlowSubflowChildLauncher children;
    private final WebhookDeliveryClient webhooks;
    private final IdService ids;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FlowCompensationWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            JdbcApprovalRepositoryFactory repositories,
            FlowCompensationRuntimeService runtime,
            FlowSubflowChildLauncher children,
            WebhookDeliveryClient webhooks,
            IdService ids
    ) {
        this(
                jdbc, transactionManager,
                (systemId, tenantId) -> repositories.forTenant(
                        systemId, tenantId),
                runtime, children,
                webhooks, ids, Clock.systemUTC());
    }

    FlowCompensationWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            BiFunction<Long, Long, ApprovalRepository> repositories,
            FlowCompensationRuntimeService runtime,
            FlowSubflowChildLauncher children,
            WebhookDeliveryClient webhooks,
            IdService ids,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.children = Objects.requireNonNull(children, "children");
        this.webhooks = Objects.requireNonNull(webhooks, "webhooks");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager,
                        "transactionManager"));
        this.transactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
            initialDelayString =
                    "${examine.flow.compensation.initial-delay-ms:5000}",
            fixedDelayString =
                    "${examine.flow.compensation.poll-delay-ms:1000}")
    public void poll() {
        pollOnce(DEFAULT_BATCH_SIZE);
    }

    public int pollOnce(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Compensation poll limit must be 1..100");
        }
        var now = clock.instant();
        var scopes = jdbc.query(
                SELECT_SCOPES,
                (result, row) -> new Scope(
                        result.getLong("system_id"),
                        result.getLong("tenant_id")),
                Timestamp.from(now), Timestamp.from(now), limit);
        var processed = 0;
        for (var scope : scopes) {
            try {
                processed += processWebhooks(scope, limit - processed);
                if (processed >= limit) {
                    return processed;
                }
                processed += processSubflows(scope, limit - processed);
                if (processed >= limit) {
                    return processed;
                }
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Compensation processing failed for system={}, tenant={}",
                        scope.systemId(), scope.tenantId(), failure);
            }
        }
        return processed;
    }

    private int processWebhooks(Scope scope, int limit) {
        if (limit <= 0) {
            return 0;
        }
        var candidates = transactions.execute(status -> repository(scope)
                .findDueWebhookCompensationsForUpdate(
                        clock.instant(), limit).stream()
                .map(ApprovalCompletionCompensation::id).toList());
        var processed = 0;
        for (var id : Objects.requireNonNull(candidates)) {
            var work = transactions.execute(status ->
                    claimWebhook(scope, id, clock.instant()));
            if (work == null) {
                continue;
            }
            var result = webhooks.deliverCanonicalPayload(
                    scope.systemId(), scope.tenantId(),
                    work.configuration(), work.compensationId(),
                    work.payload(), work.attempt());
            var committed = transactions.execute(status ->
                    commitWebhook(scope, work, result, clock.instant()));
            if (Boolean.TRUE.equals(committed)) {
                processed++;
            }
        }
        return processed;
    }

    private WebhookWork claimWebhook(
            Scope scope,
            long compensationId,
            Instant now
    ) {
        var repository = repository(scope);
        var preview = repository.findCompensation(compensationId)
                .orElse(null);
        if (preview == null) {
            return null;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        var current = repository.findCompensationForUpdate(compensationId)
                .orElse(null);
        if (!active(parent) || current == null
                || current.step().type()
                != ApprovalCompletionStep.Type.WEBHOOK
                || !current.isDueAt(now)) {
            return null;
        }
        if (current.status() == ApprovalCompletionExecution.Status.LEASED) {
            current = repository.saveCompensation(
                    current.recoverExpiredLease(now));
            runtime.append(
                    repository, current,
                    ApprovalCompletionAttempt.Event.LEASE_EXPIRED,
                    null,
                    current.execution().failure() == null
                            ? null
                            : current.execution().failure().code(),
                    current.execution().failure() == null
                            ? null
                            : current.execution().failure().message(), now);
            if (current.status()
                    == ApprovalCompletionExecution.Status.FAILED) {
                return null;
            }
        }
        var leaseHash = sha256(
                "compensation-webhook:" + compensationId + ":"
                        + current.stateVersion() + ":" + now);
        var config = current.step().webhook();
        var claimed = repository.saveCompensation(current.claim(
                "webhook-compensation-worker", leaseHash,
                now.plusSeconds(config.timeoutSeconds() + 30L), now));
        runtime.append(
                repository, claimed,
                ApprovalCompletionAttempt.Event.CLAIMED,
                null, null, null, now);
        return new WebhookWork(
                claimed.id(), claimed.stateVersion(), leaseHash,
                claimed.attemptCount(),
                claimed.execution().payloadJson()
                        .getBytes(StandardCharsets.UTF_8),
                new WebhookDeliveryClient.WebhookConfiguration(
                        config.url(), config.secretRef(),
                        config.timeoutSeconds(), config.maxAttempts(),
                        config.baseBackoffSeconds()));
    }

    private boolean commitWebhook(
            Scope scope,
            WebhookWork work,
            WebhookDeliveryClient.DeliveryResult result,
            Instant now
    ) {
        var repository = repository(scope);
        var preview = repository.findCompensation(work.compensationId())
                .orElse(null);
        if (preview == null) {
            return false;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        var current = repository.findCompensationForUpdate(
                        work.compensationId()).orElse(null);
        if (!active(parent) || current == null
                || current.status() != ApprovalCompletionExecution.Status.LEASED
                || current.stateVersion() != work.stateVersion()
                || current.execution().lease() == null
                || !current.execution().lease().tokenHash()
                .equals(work.leaseHash())) {
            return false;
        }
        final ApprovalCompletionCompensation updated;
        final ApprovalCompletionAttempt.Event event;
        if (result.outcome()
                == WebhookDeliveryClient.Outcome.SUCCEEDED) {
            updated = current.complete(work.leaseHash(), "{}", now);
            event = ApprovalCompletionAttempt.Event.SUCCEEDED;
        } else {
            var retryable = result.outcome()
                    == WebhookDeliveryClient.Outcome.RETRYABLE_FAILURE;
            updated = current.fail(
                    work.leaseHash(), result.failureCode(),
                    result.failureMessage(), retryable, now);
            event = updated.status()
                    == ApprovalCompletionExecution.Status.RETRYING
                    ? ApprovalCompletionAttempt.Event.RETRIED
                    : ApprovalCompletionAttempt.Event.FAILED;
        }
        var saved = repository.saveCompensation(updated);
        runtime.append(
                repository, saved, event, null,
                result.failureCode(), result.failureMessage(), now);
        if (saved.status()
                == ApprovalCompletionExecution.Status.SUCCEEDED) {
            runtime.coordinateSuccess(
                    scope.systemId(), scope.tenantId(), repository,
                    parent, saved, parent.requesterId(), now);
        }
        return true;
    }

    private int processSubflows(Scope scope, int limit) {
        if (limit <= 0) {
            return 0;
        }
        var repository = repository(scope);
        var processed = 0;
        for (var candidate : repository.findDueSubflowCompensations(
                clock.instant(), limit)) {
            var launched = transactions.execute(status ->
                    launchSubflow(scope, candidate.id(), clock.instant()));
            if (Boolean.TRUE.equals(launched)) {
                processed++;
            }
        }
        if (processed >= limit) {
            return processed;
        }
        for (var run : repository.findPendingCompensationSubflowRuns(
                limit - processed)) {
            var reconciled = transactions.execute(status ->
                    reconcileSubflow(
                            scope, run.compensationId(),
                            run.run().attemptNumber(), clock.instant()));
            if (Boolean.TRUE.equals(reconciled)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean launchSubflow(
            Scope scope,
            long compensationId,
            Instant now
    ) {
        var repository = repository(scope);
        var preview = repository.findCompensation(compensationId)
                .orElse(null);
        if (preview == null) {
            return false;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        var current = repository.findCompensationForUpdate(compensationId)
                .orElse(null);
        if (!active(parent) || current == null
                || current.step().type()
                != ApprovalCompletionStep.Type.SUBFLOW
                || !current.isDueAt(now)) {
            return false;
        }
        var attempt = current.attemptCount() + 1;
        if (repository.findCompensationSubflowRunByAttempt(
                compensationId, attempt).isPresent()) {
            return false;
        }
        if (depth(parent)
                >= com.unique.examine.flow.domain.ApprovalStartContext
                .MAX_SUBFLOW_DEPTH) {
            var failed = repository.saveCompensation(
                    current.startSubflow(now).failSubflow(
                            "SUBFLOW_DEPTH_EXCEEDED",
                            "Subflow hierarchy exceeds the maximum depth of 8",
                            now));
            runtime.append(
                    repository, failed,
                    ApprovalCompletionAttempt.Event.FAILED,
                    parent.requesterId(), "SUBFLOW_DEPTH_EXCEEDED",
                    "Subflow hierarchy exceeds the maximum depth of 8", now);
            return true;
        }
        var launchKey = sha256(
                "compensation-subflow:" + compensationId + ":" + attempt);
        var started = repository.saveCompensation(
                current.startSubflow(now));
        var child = children.launch(
                scope.systemId(), scope.tenantId(), parent,
                current.step().subflow(), launchKey);
        var context = Objects.requireNonNull(child.startContext());
        repository.appendCompensationSubflowRun(
                new ApprovalCompensationSubflowRun(
                        started.id(),
                        ApprovalSubflowRun.launch(
                                ids.nextId(), started.id(),
                                started.attemptCount(), launchKey, child.id(),
                                current.step().subflow(),
                                Objects.requireNonNull(
                                        context.rootInstanceId()),
                                context.subflowDepth(), now)));
        runtime.append(
                repository, started,
                ApprovalCompletionAttempt.Event.STARTED,
                parent.requesterId(), null, null, now);
        return true;
    }

    private boolean reconcileSubflow(
            Scope scope,
            long compensationId,
            int attempt,
            Instant now
    ) {
        var repository = repository(scope);
        var preview = repository.findCompensation(compensationId)
                .orElse(null);
        if (preview == null) {
            return false;
        }
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElse(null);
        var current = repository.findCompensationForUpdate(compensationId)
                .orElse(null);
        var wrapped = repository.findCompensationSubflowRunByAttempt(
                        compensationId, attempt).orElse(null);
        if (current == null || wrapped == null
                || wrapped.run().resultAppliedAt() != null) {
            return false;
        }
        var child = repository.findInstanceForUpdate(
                        wrapped.run().childInstanceId()).orElse(null);
        if (child == null) {
            return false;
        }
        var run = wrapped.run();
        if (!run.terminal()) {
            var result = childResult(child);
            if (result == null) {
                return false;
            }
            run = repository.saveCompensationSubflowRun(
                    new ApprovalCompensationSubflowRun(
                            compensationId,
                            run.observeTerminal(
                                    result,
                                    child.completedAt() == null
                                            ? now : child.completedAt())))
                    .run();
        }
        if (!run.pendingResult()) {
            return false;
        }
        if (!active(parent)
                || current.status()
                != ApprovalCompletionExecution.Status.RUNNING
                || current.attemptCount() != attempt) {
            repository.saveCompensationSubflowRun(
                    new ApprovalCompensationSubflowRun(
                            compensationId, run.markResultApplied(now)));
            return true;
        }
        if (run.status()
                == ApprovalSubflowRun.Status.APPROVED_COMPLETED) {
            var succeeded = repository.saveCompensation(
                    current.completeSubflow(
                            "{\"childInstanceId\":\"" + child.id()
                                    + "\"}", now));
            runtime.append(
                    repository, succeeded,
                    ApprovalCompletionAttempt.Event.SUCCEEDED,
                    child.requesterId(), null, null, now);
            runtime.coordinateSuccess(
                    scope.systemId(), scope.tenantId(), repository,
                    parent, succeeded, child.requesterId(), now);
        } else {
            var code = "SUBFLOW_CHILD_" + run.status().name();
            var failed = repository.saveCompensation(
                    current.failSubflow(
                            code, "Compensation child flow failed", now));
            runtime.append(
                    repository, failed,
                    ApprovalCompletionAttempt.Event.FAILED,
                    child.requesterId(), code,
                    "Compensation child flow failed", now);
        }
        repository.saveCompensationSubflowRun(
                new ApprovalCompensationSubflowRun(
                        compensationId, run.markResultApplied(now)));
        return true;
    }

    private ApprovalRepository repository(Scope scope) {
        return repositories.apply(scope.systemId(), scope.tenantId());
    }

    private static boolean active(ApprovalInstance parent) {
        return parent != null
                && parent.status() == ApprovalInstance.Status.PENDING
                && parent.completionPhase()
                == ApprovalInstance.CompletionPhase.COMPENSATING;
    }

    private static int depth(ApprovalInstance parent) {
        return parent.startContext() == null
                ? 0 : parent.startContext().subflowDepth();
    }

    private static ApprovalSubflowRun.Status childResult(
            ApprovalInstance child
    ) {
        return switch (child.status()) {
            case PENDING -> null;
            case APPROVED -> child.completionPhase()
                    == ApprovalInstance.CompletionPhase.COMPLETED
                    ? ApprovalSubflowRun.Status.APPROVED_COMPLETED : null;
            case REJECTED -> ApprovalSubflowRun.Status.REJECTED;
            case WITHDRAWN -> ApprovalSubflowRun.Status.WITHDRAWN;
            case TERMINATED -> ApprovalSubflowRun.Status.TERMINATED;
        };
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

    private record Scope(long systemId, long tenantId) {
    }

    private record WebhookWork(
            long compensationId,
            int stateVersion,
            String leaseHash,
            int attempt,
            byte[] payload,
            WebhookDeliveryClient.WebhookConfiguration configuration
    ) {
        private WebhookWork {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
