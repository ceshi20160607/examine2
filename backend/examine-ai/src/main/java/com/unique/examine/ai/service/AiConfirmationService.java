package com.unique.examine.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiConfirmation;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiRecordMutationPlanParser;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.ai.AiSupport.conflict;
import static com.unique.examine.ai.AiSupport.invalid;
import static com.unique.examine.ai.AiSupport.notFound;

@Service
public final class AiConfirmationService {
    private final AiRepository repository;
    private final AiRecordMutationFacade owner;
    private final IdService ids;
    private final Clock clock;
    private final ObjectMapper json;

    public AiConfirmationService(
            AiRepository repository,
            AiRecordMutationFacade owner,
            IdService ids,
            Clock clock,
            ObjectMapper json
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = Objects.requireNonNull(json, "json");
    }

    public AiConfirmation propose(
            AiActor actor,
            AiConversation.Session session,
            AiConversation.Turn turn,
            AiPolicy.Version policy,
            AiRecordMutationPlanParser.Plan plan
    ) {
        if (!plan.writable()) {
            throw invalid("AI_CONFIRMATION_CLARIFICATION_REQUIRED",
                    "AI mutation plan requires clarification before a proposal can be created");
        }
        if (!policy.allowedOperations().contains(plan.operation().name())
                || !policy.writableFields().getOrDefault(
                plan.moduleCode(), Set.of()).containsAll(plan.fieldCodes())) {
            throw invalid("AI_POLICY_WRITE_SCOPE_DENIED",
                    "AI mutation plan exceeds the published policy scope");
        }
        var confirmationId = ids.nextId();
        var prepared = owner.prepare(new AiRecordMutationFacade.PrepareRequest(
                Long.toString(confirmationId), actor.systemId(), actor.tenantId(),
                actor.memberId(), actor.authorizationEpoch(),
                actor.effectivePermissions(), plan.moduleCode(), plan.operation(),
                plan.canonicalOwnerCommandJson(),
                policy.writableFields().get(plan.moduleCode()),
                actor.requestId(), actor.traceId()));
        assertPrepared(plan, prepared.preview());
        var now = clock.instant();
        var preview = preview(prepared.preview());
        var value = new AiConfirmation(
                confirmationId, actor.systemId(), actor.tenantId(), actor.memberId(),
                session.id(), turn.id(), policy.id(), policy.providerId(),
                policy.providerVersion(), policy.promptVersion(),
                actor.authorizationEpoch(), plan.operation(), plan.moduleCode(),
                prepared.preview().schemaVersionId(), plan.recordId(),
                plan.expectedVersion(), plan.planHash(), preview,
                plan.confidence().stream().map(item -> new AiConfirmation.Confidence(
                        item.fieldCode(), item.confidence())).toList(),
                plan.clarifications(), prepared.sealedCommand(),
                AiConfirmation.State.PENDING, 0,
                now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                null, null, "AI_CONFIRMATION_PENDING", null, null,
                now, now, null);
        repository.insertConfirmation(value, event(
                actor, value, "PROPOSED", null, AiConfirmation.State.PENDING,
                "AI_CONFIRMATION_PENDING", now));
        return value;
    }

    public AiConfirmation get(AiActor actor, String confirmationId) {
        var value = owned(actor, confirmationId);
        if (value.state() == AiConfirmation.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            value = expire(actor, value);
        }
        return value;
    }

    public AiConfirmation reject(
            AiActor actor, String confirmationId, long expectedRevision) {
        var value = owned(actor, confirmationId);
        if (value.state() == AiConfirmation.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfirmation.State.PENDING) {
            throw conflict("AI_CONFIRMATION_STATE_CONFLICT",
                    "AI confirmation is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(value, AiConfirmation.State.REJECTED,
                value.revision() + 1, actor.memberId(), null,
                "AI_CONFIRMATION_REJECTED", actor.requestId(), actor.traceId(), now);
        if (!repository.rejectConfirmation(value, rejected, event(
                actor, rejected, "REJECTED", value.state(), rejected.state(),
                rejected.resultCode(), now))) {
            throw conflict("AI_CONFIRMATION_VERSION_CONFLICT",
                    "AI confirmation revision changed");
        }
        return rejected;
    }

    public AiConfirmation confirm(
            AiActor actor,
            String confirmationId,
            long expectedRevision,
            String idempotencyKey
    ) {
        idempotencyKey = requiredKey(idempotencyKey);
        var value = owned(actor, confirmationId);
        var requestHash = AiSupport.sha256(
                value.id() + ":" + expectedRevision + ":CONFIRM");
        var replay = repository.confirmationAttempt(
                actor.systemId(), actor.tenantId(), value.id(), idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIRMATION_REPLAY_CONFLICT",
                        "AI confirmation key was used for another request");
            }
            if (value.state() != AiConfirmation.State.EXECUTING) return value;
            return execute(actor, value, replay.get());
        }
        if (value.state() == AiConfirmation.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_CONFIRMATION_EXPIRED", "AI confirmation has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfirmation.State.PENDING) {
            throw conflict("AI_CONFIRMATION_STATE_CONFLICT",
                    "AI confirmation is no longer pending");
        }
        recheckPolicy(actor, value);
        var now = clock.instant();
        var executing = executing(value, actor, now);
        var attempt = new AiConfirmation.Attempt(
                ids.nextId(), value.systemId(), value.tenantId(), value.id(),
                idempotencyKey, requestHash, AiConfirmation.State.EXECUTING,
                null, "AI_CONFIRMATION_EXECUTING", now, null);
        var claim = repository.claimConfirmation(value, executing, attempt,
                event(actor, executing, "CONFIRMING", value.state(),
                        executing.state(), executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.confirmationAttempt(
                    actor.systemId(), actor.tenantId(), value.id(), idempotencyKey);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIRMATION_REPLAY_CONFLICT",
                        "AI confirmation key was used for another request");
            }
            if (concurrent.isPresent()) {
                return execute(actor, owned(actor, confirmationId), concurrent.get());
            }
            throw conflict("AI_CONFIRMATION_VERSION_CONFLICT",
                    "AI confirmation revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private AiConfirmation execute(
            AiActor actor,
            AiConfirmation executing,
            AiConfirmation.Attempt attempt
    ) {
        if (executing.state() != AiConfirmation.State.EXECUTING
                || attempt.status() != AiConfirmation.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final AiRecordMutationFacade.RecordView ownerResult;
        try {
            recheckPolicy(actor, executing);
            ownerResult = owner.execute(new AiRecordMutationFacade.ExecuteRequest(
                    Long.toString(executing.id()), actor.systemId(), actor.tenantId(),
                    actor.memberId(), actor.authorizationEpoch(),
                    actor.effectivePermissions(), executing.moduleCode(),
                    executing.operation(), executing.sealedCommand(),
                    attempt.requestKey(), actor.requestId(), actor.traceId()));
        } catch (RuntimeException failure) {
            var code = failure instanceof BusinessException business
                    ? business.code() : "AI_OWNER_EXECUTE_FAILED";
            var failed = terminal(executing, AiConfirmation.State.FAILED,
                    executing.revision() + 1, actor.memberId(), null,
                    safeCode(code), actor.requestId(), actor.traceId(), now);
            var finishedAttempt = new AiConfirmation.Attempt(
                    attempt.id(), attempt.systemId(), attempt.tenantId(),
                    attempt.confirmationId(), attempt.requestKey(),
                    attempt.requestHash(), AiConfirmation.State.FAILED,
                    AiSupport.sha256(failed.resultCode()), failed.resultCode(),
                    attempt.createdAt(), now);
            if (!repository.finishConfirmation(executing, failed,
                    finishedAttempt, event(actor, failed, "FAILED",
                            executing.state(), failed.state(), failed.resultCode(), now))) {
                return repository.confirmation(
                        actor.systemId(), actor.tenantId(), executing.id())
                        .orElseThrow();
            }
            return failed;
        }
        var result = result(ownerResult, now);
        var succeeded = terminal(executing, AiConfirmation.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), result,
                "OK", actor.requestId(), actor.traceId(), now);
        var resultHash = hashResult(result);
        var finishedAttempt = new AiConfirmation.Attempt(
                attempt.id(), attempt.systemId(), attempt.tenantId(),
                attempt.confirmationId(), attempt.requestKey(),
                attempt.requestHash(), AiConfirmation.State.SUCCEEDED,
                resultHash, "OK", attempt.createdAt(), now);
        if (!repository.finishConfirmation(executing, succeeded,
                finishedAttempt, event(actor, succeeded, "SUCCEEDED",
                        executing.state(), succeeded.state(), "OK", now))) {
            return repository.confirmation(
                    actor.systemId(), actor.tenantId(), executing.id())
                    .orElseThrow();
        }
        return succeeded;
    }

    private void recheckPolicy(AiActor actor, AiConfirmation value) {
        var current = repository.activePolicy(actor.systemId(), actor.tenantId())
                .filter(AiPolicy.Version::enabled)
                .orElseThrow(() -> conflict(
                        "AI_POLICY_CHANGED", "AI policy is unavailable at confirmation"));
        var writable = current.writableFields().get(value.moduleCode());
        var fields = value.confidence().stream()
                .map(AiConfirmation.Confidence::fieldCode).collect(
                        java.util.stream.Collectors.toSet());
        if (!current.allowedOperations().contains(value.operation().name())
                || writable == null || !writable.containsAll(fields)
                || current.confirmationMode() != AiPolicy.ConfirmationMode.REQUIRED) {
            throw conflict("AI_POLICY_CHANGED",
                    "AI confirmation exceeds the current active policy");
        }
    }

    private AiConfirmation owned(AiActor actor, String confirmationId) {
        var value = repository.confirmation(
                        actor.systemId(), actor.tenantId(),
                        positiveId(confirmationId, "confirmationId"))
                .orElseThrow(() -> notFound("AI confirmation does not exist"));
        if (value.memberId() != actor.memberId()
                || repository.session(actor.systemId(), actor.tenantId(),
                actor.memberId(), value.sessionId()).isEmpty()) {
            throw notFound("AI confirmation does not exist");
        }
        return value;
    }

    private AiConfirmation expire(AiActor actor, AiConfirmation value) {
        var now = clock.instant();
        var expired = terminal(value, AiConfirmation.State.EXPIRED,
                value.revision() + 1, null, null, "AI_CONFIRMATION_EXPIRED",
                actor.requestId(), actor.traceId(), now);
        if (repository.expireConfirmation(value, expired, event(
                actor, expired, "EXPIRED", value.state(), expired.state(),
                expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.id()));
    }

    private AiConfirmation.Event event(
            AiActor actor, AiConfirmation value, String eventType,
            AiConfirmation.State from, AiConfirmation.State to,
            String code, java.time.Instant now) {
        var eventHash = AiSupport.sha256(value.id() + ":" + value.revision()
                + ":" + eventType + ":" + code + ":" + actor.memberId());
        return new AiConfirmation.Event(
                ids.nextId(), value.systemId(), value.tenantId(), value.id(),
                eventType, from, to, value.revision(), actor.memberId(),
                actor.requestId(), actor.traceId(), code, eventHash, now);
    }

    private static AiConfirmation executing(
            AiConfirmation value, AiActor actor, java.time.Instant now) {
        return new AiConfirmation(
                value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.promptVersion(),
                value.authorizationEpoch(), value.operation(), value.moduleCode(),
                value.schemaVersionId(), value.recordId(),
                value.expectedRecordVersion(), value.planHash(), value.preview(),
                value.confidence(), value.clarifications(), value.sealedCommand(),
                AiConfirmation.State.EXECUTING, value.revision() + 1,
                value.expiresAt(), actor.memberId(), null,
                "AI_CONFIRMATION_EXECUTING", actor.requestId(), actor.traceId(),
                value.createdAt(), now, null);
    }

    private static AiConfirmation terminal(
            AiConfirmation value, AiConfirmation.State state, long revision,
            Long confirmedBy, AiConfirmation.Result result, String resultCode,
            String requestId, String traceId, java.time.Instant now) {
        return new AiConfirmation(
                value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.promptVersion(),
                value.authorizationEpoch(), value.operation(), value.moduleCode(),
                value.schemaVersionId(), value.recordId(),
                value.expectedRecordVersion(), value.planHash(), value.preview(),
                value.confidence(), value.clarifications(), value.sealedCommand(),
                state, revision, value.expiresAt(), confirmedBy, result, resultCode,
                requestId, traceId, value.createdAt(), now, now);
    }

    private static AiConfirmation.Preview preview(
            AiRecordMutationFacade.MutationPreview value) {
        return new AiConfirmation.Preview(
                value.beforeTitle(), value.afterTitle(), value.changes().stream()
                .map(field -> new AiConfirmation.FieldChange(
                        field.fieldCode(), field.fieldName(), field.type(),
                        field.beforeDisplayValue(), field.afterDisplayValue(),
                        field.masked())).toList());
    }

    private static AiConfirmation.Result result(
            AiRecordMutationFacade.RecordView value, java.time.Instant now) {
        return new AiConfirmation.Result(
                value.recordId(), value.recordNo(), value.version(), value.status(),
                value.title(), value.schemaVersionId(), value.values().stream()
                .map(field -> new AiConfirmation.DisplayValue(
                        field.fieldCode(), field.fieldName(), field.type(),
                        field.displayValue(), field.masked())).toList(), now);
    }

    private static void assertPrepared(
            AiRecordMutationPlanParser.Plan plan,
            AiRecordMutationFacade.MutationPreview preview) {
        if (preview.operation() != plan.operation()
                || !preview.moduleCode().equals(plan.moduleCode())
                || !Objects.equals(preview.recordId(), plan.recordId())
                || !Objects.equals(preview.expectedVersion(), plan.expectedVersion())) {
            throw new IllegalStateException(
                    "AI mutation owner returned a mismatched preview");
        }
    }

    private String hashResult(AiConfirmation.Result result) {
        var value = new StringBuilder();
        append(value, result.recordId());
        append(value, result.recordNo());
        append(value, Long.toString(result.recordVersion()));
        append(value, result.status());
        append(value, result.title());
        append(value, result.schemaVersionId());
        result.values().stream()
                .sorted(java.util.Comparator.comparing(
                        AiConfirmation.DisplayValue::fieldCode))
                .forEach(field -> {
                    append(value, field.fieldCode());
                    append(value, field.fieldName());
                    append(value, field.type());
                    append(value, field.displayValue());
                    append(value, Boolean.toString(field.masked()));
                });
        append(value, result.executedAt().toString());
        return AiSupport.sha256(value.toString());
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) target.append("-1:");
        else target.append(value.length()).append(':').append(value);
    }

    private static void requireRevision(AiConfirmation value, long expected) {
        if (expected < 0 || value.revision() != expected) {
            throw conflict("AI_CONFIRMATION_VERSION_CONFLICT",
                    "AI confirmation revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw invalid("AI_IDEMPOTENCY_KEY_INVALID",
                    "AI confirmation Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "AI_OWNER_EXECUTE_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw invalid("AI_ID_INVALID", field + " must be a positive id");
        }
    }
}
