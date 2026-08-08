package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import com.unique.examine.flow.transport.WebhookDeliveryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Restart-safe webhook worker. Database claim and conditional commit are
 * separate transactions; the network call always runs outside a transaction.
 */
@Component
public final class FlowWebhookWorker {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(FlowWebhookWorker.class);
    private static final String SELECT_DUE_SCOPES = """
            SELECT system_id,tenant_id
            FROM un_flow_completion_execution
            WHERE execution_type='WEBHOOK'
              AND (
                (status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                OR (status='LEASED' AND lease_expires_at<=?)
              )
            GROUP BY system_id,tenant_id
            ORDER BY MIN(COALESCE(available_at,lease_expires_at)),
                     system_id,tenant_id
            LIMIT ?
            """;
    public static final int DEFAULT_BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final JdbcApprovalRepositoryFactory repositories;
    private final FlowCompletionExecutionService completions;
    private final WebhookDeliveryClient deliveries;
    private final IdService ids;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private FlowCompensationCoordinator compensations;

    void configureCompensations(FlowCompensationCoordinator compensations) {
        this.compensations = Objects.requireNonNull(
                compensations, "compensations");
    }

    @Autowired
    public FlowWebhookWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            JdbcApprovalRepositoryFactory repositories,
            FlowCompletionExecutionService completions,
            WebhookDeliveryClient deliveries,
            IdService ids
    ) {
        this(
                jdbc, transactionManager, repositories, completions,
                deliveries, ids, Clock.systemUTC());
    }

    FlowWebhookWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            JdbcApprovalRepositoryFactory repositories,
            FlowCompletionExecutionService completions,
            WebhookDeliveryClient deliveries,
            IdService ids,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.completions = Objects.requireNonNull(
                completions, "completions");
        this.deliveries = Objects.requireNonNull(
                deliveries, "deliveries");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(
                        transactionManager, "transactionManager"));
        this.transactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
            initialDelayString =
                    "${examine.flow.webhook.initial-delay-ms:1000}",
            fixedDelayString =
                    "${examine.flow.webhook.poll-delay-ms:1000}"
    )
    public void poll() {
        pollOnce(DEFAULT_BATCH_SIZE);
    }

    public int pollOnce(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Webhook poll limit must be 1..100");
        }
        var now = clock.instant();
        var scopes = jdbc.query(
                SELECT_DUE_SCOPES,
                (result, row) -> new ScopeKey(
                        result.getLong("system_id"),
                        result.getLong("tenant_id")
                ),
                Timestamp.from(now), Timestamp.from(now), limit
        );
        var processed = 0;
        for (var scope : scopes) {
            try {
                var remaining = limit - processed;
                var work = transactions.execute(status ->
                        claimBatch(scope, clock.instant(), remaining));
                for (var claimed : Objects.requireNonNull(work)) {
                    var startedAt = clock.instant();
                    var result = deliver(claimed);
                    var completedAt = clock.instant();
                    var committed = transactions.execute(status ->
                            commit(
                                    claimed, result,
                                    startedAt, completedAt));
                    if (Boolean.TRUE.equals(committed)) {
                        processed++;
                    }
                    if (processed >= limit) {
                        return processed;
                    }
                }
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Webhook completion processing failed for scope "
                                + "system={}, tenant={}",
                        scope.systemId(),
                        scope.tenantId(),
                        failure
                );
            }
        }
        return processed;
    }

    private java.util.List<ClaimedWebhook> claimBatch(
            ScopeKey scope,
            Instant now,
            int limit
    ) {
        var repository = repository(scope);
        return repository.findDueWebhookExecutionsForUpdate(now, limit)
                .stream()
                .map(current -> claim(scope, repository, current, now))
                .filter(Objects::nonNull)
                .toList();
    }

    private ClaimedWebhook claim(
            ScopeKey scope,
            ApprovalRepository repository,
            ApprovalCompletionExecution current,
            Instant now
    ) {
        if (current == null
                || current.step().type()
                        != ApprovalCompletionStep.Type.WEBHOOK
                || !current.isDueAt(now)) {
            return null;
        }
        if (current.status()
                == ApprovalCompletionExecution.Status.LEASED) {
            current = repository.saveCompletionExecution(
                    current.recoverExpiredLease(now));
            append(
                    repository,
                    current,
                    ApprovalCompletionAttempt.Event.LEASE_EXPIRED,
                    null,
                    null,
                    null,
                    current.failure() == null
                            ? null
                            : current.failure().code(),
                    current.failure() == null
                            ? null
                            : current.failure().message(),
                    null,
                    now,
                    now
            );
            if (current.status()
                    == ApprovalCompletionExecution.Status.FAILED) {
                return null;
            }
        }
        var leaseSeed = scope.systemId()
                + ":" + scope.tenantId()
                + ":" + current.id()
                + ":" + now
                + ":" + java.util.UUID.randomUUID();
        var leaseHash = sha256(leaseSeed);
        var config = current.step().webhook();
        var claimed = repository.saveCompletionExecution(current.claim(
                "webhook-worker",
                leaseHash,
                now.plusSeconds(Math.max(
                        60, config.timeoutSeconds() + 30L)),
                now
        ));
        append(
                repository,
                claimed,
                ApprovalCompletionAttempt.Event.CLAIMED,
                null,
                claimed.lease().owner(),
                null,
                    null,
                    null,
                    null,
                    now,
                    now
        );
        var instance = repository.findInstance(claimed.instanceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook parent instance is missing"));
        return new ClaimedWebhook(
                new DueKey(
                        scope.systemId(), scope.tenantId(), claimed.id()),
                claimed,
                leaseHash,
                instance.requesterId()
        );
    }

    private WebhookDeliveryClient.DeliveryResult deliver(
            ClaimedWebhook work
    ) {
        var config = work.execution().step().webhook();
        return deliveries.deliverCanonicalPayload(
                work.key().systemId(),
                work.key().tenantId(),
                new WebhookDeliveryClient.WebhookConfiguration(
                        config.url(),
                        config.secretRef(),
                        config.timeoutSeconds(),
                        config.maxAttempts(),
                        config.baseBackoffSeconds()
                ),
                work.execution().id(),
                work.execution().payloadJson()
                        .getBytes(StandardCharsets.UTF_8),
                work.execution().attemptCount()
        );
    }

    private boolean commit(
            ClaimedWebhook work,
            WebhookDeliveryClient.DeliveryResult result,
            Instant startedAt,
            Instant completedAt
    ) {
        var repository = repository(work.key());
        var parent = repository.findInstanceForUpdate(
                        work.execution().instanceId())
                .orElse(null);
        if (parent == null
                || parent.status() != com.unique.examine.flow.domain
                        .ApprovalInstance.Status.PENDING
                || parent.completionPhase()
                != com.unique.examine.flow.domain.ApprovalInstance
                        .CompletionPhase.EXTERNAL_EXECUTION) {
            return false;
        }
        var current = repository.findCompletionExecutionForUpdate(
                        work.execution().id())
                .orElse(null);
        if (current == null
                || current.status()
                        != ApprovalCompletionExecution.Status.LEASED
                || current.stateVersion()
                        != work.execution().stateVersion()
                || current.lease() == null
                || !current.lease().tokenHash()
                        .equals(work.leaseHash())) {
            return false;
        }
        final ApprovalCompletionExecution updated;
        final ApprovalCompletionAttempt.Event event;
        if (result.outcome()
                == WebhookDeliveryClient.Outcome.SUCCEEDED) {
            updated = current.complete(
                    work.leaseHash(), "{}", completedAt);
            event = ApprovalCompletionAttempt.Event.SUCCEEDED;
        } else {
            var retryable = result.outcome()
                    == WebhookDeliveryClient.Outcome.RETRYABLE_FAILURE;
            updated = current.fail(
                    work.leaseHash(),
                    result.failureCode(),
                    result.failureMessage(),
                    retryable,
                    completedAt
            );
            event = retryable
                    ? ApprovalCompletionAttempt.Event.RETRIED
                    : ApprovalCompletionAttempt.Event.FAILED;
        }
        var saved = repository.saveCompletionExecution(updated);
        append(
                repository,
                saved,
                event,
                null,
                current.lease().owner(),
                result.outcome()
                        == WebhookDeliveryClient.Outcome.SUCCEEDED
                        ? saved.resultJson()
                        : null,
                result.failureCode(),
                result.failureMessage(),
                result,
                startedAt,
                completedAt
        );
        if (saved.status()
                == ApprovalCompletionExecution.Status.SUCCEEDED) {
            completions.coordinateSuccess(
                    work.key().systemId(),
                    work.key().tenantId(),
                    repository,
                    saved,
                    parent,
                    work.requesterId(),
                    completedAt
            );
        } else if (saved.status()
                == ApprovalCompletionExecution.Status.FAILED
                && compensations != null) {
            compensations.onTerminalForwardFailure(
                    work.key().systemId(), work.key().tenantId(),
                    repository, parent, saved, work.requesterId(),
                    completedAt);
        }
        return true;
    }

    private void append(
            ApprovalRepository repository,
            ApprovalCompletionExecution execution,
            ApprovalCompletionAttempt.Event event,
            Long actorId,
            String leaseOwner,
            String resultJson,
            String failureCode,
            String failureMessage,
            WebhookDeliveryClient.DeliveryResult delivery,
            Instant startedAt,
            Instant completedAt
    ) {
        var sequence = repository.findCompletionAttempts(execution.id())
                .stream()
                .mapToInt(ApprovalCompletionAttempt::eventSequence)
                .max()
                .orElse(0) + 1;
        var duration = delivery == null
                ? null
                : delivery.durationMillis() == null
                        ? Math.max(
                                0,
                                Duration.between(
                                        startedAt, completedAt).toMillis())
                        : delivery.durationMillis();
        repository.appendCompletionAttempt(new ApprovalCompletionAttempt(
                ids.nextId(),
                execution.id(),
                execution.attemptCount(),
                sequence,
                event,
                actorId,
                leaseOwner,
                null,
                resultJson,
                failureCode,
                failureMessage,
                delivery == null ? null : delivery.httpStatus(),
                duration,
                delivery == null ? null : delivery.responseSha256(),
                delivery == null ? null : startedAt,
                delivery == null ? null : completedAt,
                completedAt
        ));
    }

    private ApprovalRepository repository(DueKey key) {
        return repositories.forTenant(
                key.systemId(), key.tenantId());
    }

    private ApprovalRepository repository(ScopeKey key) {
        return repositories.forTenant(
                key.systemId(), key.tenantId());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", failure);
        }
    }

    private record DueKey(
            long systemId,
            long tenantId,
            long executionId
    ) {
    }

    private record ScopeKey(long systemId, long tenantId) {
    }

    private record ClaimedWebhook(
            DueKey key,
            ApprovalCompletionExecution execution,
            String leaseHash,
            long requesterId
    ) {
    }
}
