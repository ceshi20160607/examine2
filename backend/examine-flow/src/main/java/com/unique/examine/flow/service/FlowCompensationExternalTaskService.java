package com.unique.examine.flow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Independent external-task protocol for compensation executions. */
@Service
public class FlowCompensationExternalTaskService {
    private static final String IDEMPOTENCY_SCOPE =
            "FLOW_COMPLETION_COMPENSATION";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final BiFunction<Long, Long, ApprovalRepository> repositories;
    private final FlowCompensationRuntimeService runtime;
    private final IdempotencyFacade idempotency;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random;

    @org.springframework.beans.factory.annotation.Autowired
    public FlowCompensationExternalTaskService(
            JdbcApprovalRepositoryFactory repositories,
            FlowCompensationRuntimeService runtime,
            IdempotencyFacade idempotency,
            ObjectMapper json
    ) {
        this(
                (systemId, tenantId) -> repositories.forTenant(
                        systemId, tenantId),
                runtime, idempotency, json,
                Clock.systemUTC(), new SecureRandom());
    }

    FlowCompensationExternalTaskService(
            BiFunction<Long, Long, ApprovalRepository> repositories,
            FlowCompensationRuntimeService runtime,
            IdempotencyFacade idempotency,
            ObjectMapper json,
            Clock clock,
            SecureRandom random
    ) {
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.idempotency = Objects.requireNonNull(
                idempotency, "idempotency");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Transactional(readOnly = true)
    public FlowViews.CompensationExternalTaskPage externalTasks(
            FlowSession session,
            String topic,
            int page,
            int size
    ) {
        topic = topic == null || topic.isBlank() ? null : topic.strip();
        requireTopic(topic);
        var bounds = bounds(page, size);
        var repository = repository(session);
        var now = clock.instant();
        return new FlowViews.CompensationExternalTaskPage(
                repository.findAvailableCompensationExternalTasks(
                                topic, now, bounds.offset(), bounds.size())
                        .stream()
                        .map(value -> runtime.view(repository, value))
                        .toList(),
                page,
                size,
                repository.countAvailableCompensationExternalTasks(
                        topic, now));
    }

    @Transactional
    public FlowViews.CompensationExternalTaskClaim claim(
            FlowSession session,
            long compensationId,
            String idempotencyKey
    ) {
        return mutate(
                session, compensationId, "claim", null, idempotencyKey,
                FlowViews.CompensationExternalTaskClaim.class,
                () -> claimNow(session, compensationId),
                value -> new FlowViews.CompensationExternalTaskClaim(
                        value.execution(), null));
    }

    @Transactional
    public FlowViews.CompensationExecution heartbeat(
            FlowSession session,
            long compensationId,
            FlowRequests.ExternalTaskLease request,
            String idempotencyKey
    ) {
        return mutate(
                session, compensationId, "heartbeat", request,
                idempotencyKey, FlowViews.CompensationExecution.class,
                () -> {
                    var locked = lockActiveExternal(
                            session, compensationId, null);
                    var now = clock.instant();
                    var saved = locked.repository().saveCompensation(
                            locked.compensation().heartbeat(
                                    tokenHash(token(request)),
                                    now.plusSeconds(locked.compensation()
                                            .step().externalTask()
                                            .leaseSeconds()),
                                    now));
                    return runtime.view(locked.repository(), saved);
                }, Function.identity());
    }

    @Transactional
    public FlowViews.CompensationExecution complete(
            FlowSession session,
            long compensationId,
            FlowRequests.CompleteExternalTask request,
            String idempotencyKey
    ) {
        return mutate(
                session, compensationId, "complete", request,
                idempotencyKey, FlowViews.CompensationExecution.class,
                () -> {
                    if (request == null || request.result() == null
                            || !request.result().isObject()) {
                        throw invalid(
                                "Compensation result must be a JSON object");
                    }
                    var locked = external(lock(
                            session, compensationId, null));
                    if (!active(locked.parent())) {
                        return runtime.view(
                                locked.repository(), locked.compensation());
                    }
                    var now = clock.instant();
                    var saved = locked.repository().saveCompensation(
                            locked.compensation().complete(
                                    tokenHash(request.leaseToken()),
                                    request.result().toString(), now));
                    runtime.append(
                            locked.repository(), saved,
                            ApprovalCompletionAttempt.Event.SUCCEEDED,
                            session.memberId(), null, null, now);
                    runtime.coordinateSuccess(
                            session.systemId(), session.tenantId(),
                            locked.repository(), locked.parent(), saved,
                            session.memberId(), now);
                    return runtime.view(locked.repository(), saved);
                }, Function.identity());
    }

    @Transactional
    public FlowViews.CompensationExecution fail(
            FlowSession session,
            long compensationId,
            FlowRequests.FailExternalTask request,
            String idempotencyKey
    ) {
        return mutate(
                session, compensationId, "fail", request,
                idempotencyKey, FlowViews.CompensationExecution.class,
                () -> {
                    if (request == null) {
                        throw invalid(
                                "Compensation failure body is required");
                    }
                    var locked = external(lock(
                            session, compensationId, null));
                    if (!active(locked.parent())) {
                        return runtime.view(
                                locked.repository(), locked.compensation());
                    }
                    var code = failureCode(request.code());
                    var message = failureMessage(request.message());
                    var now = clock.instant();
                    var saved = locked.repository().saveCompensation(
                            locked.compensation().fail(
                                    tokenHash(request.leaseToken()),
                                    code, message, true, now));
                    runtime.append(
                            locked.repository(), saved,
                            saved.status()
                                    == ApprovalCompletionExecution.Status.RETRYING
                                    ? ApprovalCompletionAttempt.Event.RETRIED
                                    : ApprovalCompletionAttempt.Event.FAILED,
                            session.memberId(), code, message, now);
                    return runtime.view(locked.repository(), saved);
                }, Function.identity());
    }

    @Transactional
    public FlowViews.CompensationExecution retry(
            FlowSession session,
            long instanceId,
            long compensationId,
            String idempotencyKey
    ) {
        return mutate(
                session, compensationId, "retry", instanceId,
                idempotencyKey, FlowViews.CompensationExecution.class,
                () -> {
                    var locked = lockActive(
                            session, compensationId, instanceId);
                    var now = clock.instant();
                    var saved = locked.repository().saveCompensation(
                            locked.compensation().retry(now));
                    runtime.append(
                            locked.repository(), saved,
                            ApprovalCompletionAttempt.Event.RETRIED,
                            session.memberId(), null, null, now);
                    return runtime.view(locked.repository(), saved);
                }, Function.identity());
    }

    private FlowViews.CompensationExternalTaskClaim claimNow(
            FlowSession session,
            long compensationId
    ) {
        var locked = lockActiveExternal(session, compensationId, null);
        var current = locked.compensation();
        var now = clock.instant();
        if (current.status() == ApprovalCompletionExecution.Status.LEASED
                && current.execution().lease() != null
                && !current.execution().lease().expiresAt().isAfter(now)) {
            current = locked.repository().saveCompensation(
                    current.recoverExpiredLease(now));
            runtime.append(
                    locked.repository(), current,
                    ApprovalCompletionAttempt.Event.LEASE_EXPIRED,
                    null,
                    current.execution().failure() == null
                            ? null
                            : current.execution().failure().code(),
                    current.execution().failure() == null
                            ? null
                            : current.execution().failure().message(),
                    now);
            if (current.status()
                    == ApprovalCompletionExecution.Status.FAILED) {
                return new FlowViews.CompensationExternalTaskClaim(
                        runtime.view(locked.repository(), current), null);
            }
        }
        var token = newToken();
        var claimed = locked.repository().saveCompensation(current.claim(
                "member:" + session.memberId(),
                tokenHash(token),
                now.plusSeconds(current.step().externalTask().leaseSeconds()),
                now));
        runtime.append(
                locked.repository(), claimed,
                ApprovalCompletionAttempt.Event.CLAIMED,
                session.memberId(), null, null, now);
        return new FlowViews.CompensationExternalTaskClaim(
                runtime.view(locked.repository(), claimed), token);
    }

    private Locked lockActive(
            FlowSession session,
            long compensationId,
            Long expectedInstanceId
    ) {
        var locked = lock(session, compensationId, expectedInstanceId);
        if (!active(locked.parent())) {
            throw conflict(
                    "FLOW_COMPENSATION_STATE_CONFLICT",
                    "Compensation execution is no longer active");
        }
        return locked;
    }

    private Locked lockActiveExternal(
            FlowSession session,
            long compensationId,
            Long expectedInstanceId
    ) {
        return external(lockActive(
                session, compensationId, expectedInstanceId));
    }

    private static Locked external(Locked locked) {
        if (locked.compensation().step().type()
                != ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            throw notFound();
        }
        return locked;
    }

    private Locked lock(
            FlowSession session,
            long compensationId,
            Long expectedInstanceId
    ) {
        var repository = repository(session);
        var preview = repository.findCompensation(compensationId)
                .orElseThrow(FlowCompensationExternalTaskService::notFound);
        var parent = repository.findInstanceForUpdate(preview.instanceId())
                .orElseThrow(FlowCompensationExternalTaskService::notFound);
        var current = repository.findCompensationForUpdate(compensationId)
                .orElseThrow(FlowCompensationExternalTaskService::notFound);
        if (expectedInstanceId != null
                && current.instanceId() != expectedInstanceId) {
            throw notFound();
        }
        return new Locked(repository, parent, current);
    }

    private ApprovalRepository repository(FlowSession session) {
        Objects.requireNonNull(session, "session");
        return repositories.apply(session.systemId(), session.tenantId());
    }

    private <T> T mutate(
            FlowSession session,
            long compensationId,
            String action,
            Object request,
            String idempotencyKey,
            Class<T> responseType,
            Supplier<T> mutation,
            Function<T, T> durableResponse
    ) {
        requireKey(idempotencyKey);
        var scopeKey = session.systemId() + ":" + session.tenantId()
                + ":" + session.memberId() + ":" + compensationId
                + ":" + action;
        var requestHash = sha256(write(request));
        var existing = idempotency.find(
                IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw conflict(
                        "IDEMPOTENCY_CONFLICT",
                        "The idempotency key cannot be reused for a different request");
            }
            if (!"COMPLETED".equals(record.status())
                    || record.responseBody() == null) {
                throw conflict(
                        "REQUEST_IN_PROGRESS",
                        "The compensation request is already being processed");
            }
            return read(record.responseBody(), responseType);
        }
        final long id;
        try {
            id = idempotency.begin(
                    IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey,
                    requestHash, IDEMPOTENCY_TTL);
        } catch (DataIntegrityViolationException failure) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The compensation request is already being processed");
        }
        var response = mutation.get();
        idempotency.complete(
                id, 200, "OK", write(durableResponse.apply(response)));
        return response;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Compensation response serialization failed", failure);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Stored compensation response is invalid", failure);
        }
    }

    private String newToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes);
    }

    private static boolean active(ApprovalInstance parent) {
        return parent.status() == ApprovalInstance.Status.PENDING
                && parent.completionPhase()
                == ApprovalInstance.CompletionPhase.COMPENSATING;
    }

    private static String token(FlowRequests.ExternalTaskLease request) {
        return request == null ? null : request.leaseToken();
    }

    private static String tokenHash(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw invalid("A valid leaseToken is required");
        }
        return sha256(value);
    }

    private static String failureCode(String value) {
        if (value == null) {
            throw invalid("Compensation failure code is required");
        }
        var normalized = value.strip()
                .toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[A-Z][A-Z0-9_]{0,63}$")) {
            throw invalid("Compensation failure code is invalid");
        }
        return normalized;
    }

    private static String failureMessage(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("Compensation failure message is required");
        }
        var normalized = value.replaceAll(
                "[\\p{Cntrl}&&[^\\t]]", " ").strip();
        return normalized.length() > 500
                ? normalized.substring(0, 500)
                : normalized;
    }

    private static void requireTopic(String topic) {
        if (topic != null
                && !topic.matches("^[a-z][a-z0-9._-]{0,63}$")) {
            throw invalid("A canonical compensation task topic is required");
        }
    }

    private static PageBounds bounds(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("page must be positive and size must be 1..100");
        }
        return new PageBounds(Math.multiplyExact(page - 1, size), size);
    }

    private static void requireKey(String value) {
        if (value == null
                || !value.matches("^[A-Za-z0-9._:-]{8,128}$")) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required",
                    HttpStatus.BAD_REQUEST);
        }
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

    private static ApprovalDomainException notFound() {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_EXECUTION_NOT_FOUND,
                "Compensation execution was not found");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "FLOW_COMPENSATION_EXECUTION_INVALID",
                message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private record Locked(
            ApprovalRepository repository,
            ApprovalInstance parent,
            ApprovalCompletionCompensation compensation
    ) {
    }

    private record PageBounds(int offset, int size) {
    }
}
