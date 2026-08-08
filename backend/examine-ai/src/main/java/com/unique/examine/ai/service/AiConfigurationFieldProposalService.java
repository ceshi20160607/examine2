package com.unique.examine.ai.service;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiConfigurationFieldProposal;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiConfigurationFieldPlanParser;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Confirmation state machine; configuration writes only cross the owner facade. */
@Service
public final class AiConfigurationFieldProposalService {
    private final AiRepository repository;
    private final AiConfigurationFieldFacade owner;
    private final IdService ids;
    private final Clock clock;

    public AiConfigurationFieldProposalService(
            AiRepository repository,
            AiConfigurationFieldFacade owner,
            IdService ids,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedProposal prepare(
            AiActor actor,
            AiConversation.Turn turn,
            AiPolicy.Version policy,
            AiConfigurationFieldPlanParser.Plan plan) {
        requireBindings(actor, turn, policy);
        if (!policy.allowedOperations().contains("CONFIG_FIELD_DRAFT")
                || policy.confirmationMode() != AiPolicy.ConfirmationMode.REQUIRED) {
            throw AiSupport.invalid(
                    "AI_CONFIG_FIELD_POLICY_DENIED",
                    "AI configuration field operation is not allowed by policy");
        }
        var now = clock.instant();
        var id = ids.nextId();
        if (!plan.actionable()) {
            var live = new AiConfigurationFieldProposal(
                    id, actor.accountId(), actor.systemId(), actor.tenantId(),
                    actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                    policy.providerId(), policy.providerVersion(),
                    actor.authorizationEpoch(), policy.promptVersion(), null,
                    plan.planHash(),
                    AiConfigurationFieldProposal.State.CLARIFICATION_REQUIRED,
                    0, null, plan.confidence(), plan.clarification(), null,
                    now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                    null, null, "AI_CONFIG_FIELD_CLARIFICATION_REQUIRED",
                    actor.requestId(), actor.traceId(), now, now, null);
            var stored = copyClarification(live,
                    AiConfigurationFieldProposal.REDACTED_CLARIFICATION);
            return new PreparedProposal(live, stored, event(
                    actor, stored, null, "CLARIFICATION_REQUIRED", null,
                    stored.state(), stored.resultCode(), now));
        }

        var field = new AiConfigurationFieldFacade.FieldDraft(
                plan.fieldCode(), plan.fieldName(),
                AiConfigurationFieldFacade.FieldType.valueOf(plan.fieldType().name()),
                plan.required(), ownerSettings(plan.settings(), plan.fieldType()));
        var prepared = owner.prepare(new AiConfigurationFieldFacade.PrepareRequest(
                Long.toString(id), actor.accountId(), actor.systemId(),
                actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                actor.effectivePermissions(), plan.moduleCode(), field,
                Long.toString(policy.id()), Long.toString(policy.providerId()),
                policy.providerVersion(), policy.promptVersion(),
                actor.requestId(), actor.traceId()));
        assertPrepared(plan, prepared);
        var latestAllowed = now.plus(
                policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS);
        if (!prepared.expiresAt().isAfter(now)) {
            throw AiSupport.conflict(
                    "AI_CONFIG_FIELD_PREPARE_EXPIRED",
                    "Configuration owner returned an invalid command expiry");
        }
        var proposalExpiresAt = prepared.expiresAt().isBefore(latestAllowed)
                ? prepared.expiresAt() : latestAllowed;
        var livePreview = preview(prepared.preview(), false);
        var storedPreview = preview(prepared.preview(), true);
        var sealed = new AiConfigurationFieldProposal.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                prepared.sealedCommand().commandSha256());
        var live = new AiConfigurationFieldProposal(
                id, actor.accountId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                policy.providerId(), policy.providerVersion(),
                actor.authorizationEpoch(), policy.promptVersion(),
                plan.moduleCode(), plan.planHash(),
                AiConfigurationFieldProposal.State.PENDING, 0, livePreview,
                plan.confidence(), null, sealed, proposalExpiresAt, null,
                null, "AI_CONFIG_FIELD_PENDING", actor.requestId(),
                actor.traceId(), now, now, null);
        var stored = copy(live, AiConfigurationFieldProposal.State.PENDING,
                0, storedPreview, null, null, "AI_CONFIG_FIELD_PENDING",
                actor.requestId(), actor.traceId(), now, null);
        return new PreparedProposal(live, stored, event(
                actor, stored, null, "PROPOSED", null, stored.state(),
                stored.resultCode(), now));
    }

    public AiConfigurationFieldProposal get(
            AiActor actor, String sessionId, String proposalId) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiConfigurationFieldProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        return value;
    }

    public AiConfigurationFieldProposal reject(
            AiActor actor,
            String sessionId,
            String proposalId,
            long expectedRevision) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiConfigurationFieldProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfigurationFieldProposal.State.PENDING) {
            throw conflict("AI_CONFIG_FIELD_STATE_CONFLICT",
                    "Configuration field proposal is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(value,
                AiConfigurationFieldProposal.State.REJECTED,
                value.revision() + 1, actor.memberId(), null,
                "AI_CONFIG_FIELD_REJECTED", actor.requestId(), actor.traceId(), now);
        if (!repository.transitionConfigurationFieldProposal(
                value, rejected, event(actor, rejected, null, "REJECTED",
                value.state(), rejected.state(), rejected.resultCode(), now))) {
            throw conflict("AI_CONFIG_FIELD_VERSION_CONFLICT",
                    "Configuration field proposal revision changed");
        }
        return rejected;
    }

    public AiConfigurationFieldProposal confirm(
            AiActor actor,
            String sessionId,
            String proposalId,
            long expectedRevision,
            String idempotencyKey) {
        var key = requiredKey(idempotencyKey);
        var value = owned(actor, sessionId, proposalId);
        var requestHash = AiSupport.sha256(
                value.id() + ":" + expectedRevision + ":CONFIRM");
        var replay = repository.configurationFieldAttempt(
                actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIG_FIELD_REPLAY_CONFLICT",
                        "Configuration field key was used for another request");
            }
            if (value.state() != AiConfigurationFieldProposal.State.EXECUTING) {
                return value;
            }
            return execute(actor, value, replay.get());
        }
        if (value.state() == AiConfigurationFieldProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_CONFIG_FIELD_EXPIRED",
                    "Configuration field proposal has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfigurationFieldProposal.State.PENDING) {
            throw conflict("AI_CONFIG_FIELD_STATE_CONFLICT",
                    "Configuration field proposal is no longer pending");
        }
        recheckPolicy(value);
        var now = clock.instant();
        var executing = copy(value,
                AiConfigurationFieldProposal.State.EXECUTING,
                value.revision() + 1, value.preview(), actor.memberId(), null,
                "AI_CONFIG_FIELD_EXECUTING", actor.requestId(), actor.traceId(),
                now, null);
        var attempt = new AiConfigurationFieldProposal.Attempt(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), "CONFIRM", key, requestHash,
                AiConfigurationFieldProposal.State.EXECUTING, null,
                "AI_CONFIG_FIELD_EXECUTING", now, null);
        var claim = repository.claimConfigurationFieldProposal(
                value, executing, attempt, event(actor, executing, attempt.id(),
                "CONFIRMING", value.state(), executing.state(),
                executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.configurationFieldAttempt(
                    actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIG_FIELD_REPLAY_CONFLICT",
                        "Configuration field key was used for another request");
            }
            if (concurrent.isPresent()) {
                return execute(actor, owned(actor, sessionId, proposalId),
                        concurrent.get());
            }
            throw conflict("AI_CONFIG_FIELD_VERSION_CONFLICT",
                    "Configuration field proposal revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private AiConfigurationFieldProposal execute(
            AiActor actor,
            AiConfigurationFieldProposal executing,
            AiConfigurationFieldProposal.Attempt attempt) {
        if (executing.state() != AiConfigurationFieldProposal.State.EXECUTING
                || attempt.status() != AiConfigurationFieldProposal.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final AiConfigurationFieldFacade.FieldReadback ownerResult;
        try {
            recheckPolicy(executing);
            ownerResult = owner.execute(new AiConfigurationFieldFacade.ExecuteRequest(
                    Long.toString(executing.id()), actor.accountId(),
                    actor.systemId(), actor.tenantId(), actor.memberId(),
                    actor.authorizationEpoch(), actor.effectivePermissions(),
                    executing.preview().configRootId(), executing.preview().moduleId(),
                    executing.moduleCode(), executing.preview().expectedDraftRevision(),
                    sealed(executing.sealedCommand()), attempt.requestKey(),
                    actor.requestId(), actor.traceId()));
            assertResult(executing, ownerResult);
        } catch (RuntimeException failure) {
            var code = failure instanceof BusinessException business
                    ? business.code() : "AI_CONFIG_FIELD_OWNER_FAILED";
            var failed = terminal(executing,
                    AiConfigurationFieldProposal.State.FAILED,
                    executing.revision() + 1, actor.memberId(), null,
                    safeCode(code), actor.requestId(), actor.traceId(), now);
            var finishedAttempt = finishedAttempt(attempt,
                    AiConfigurationFieldProposal.State.FAILED,
                    AiSupport.sha256(failed.resultCode()), failed.resultCode(), now);
            if (!repository.finishConfigurationFieldProposal(
                    executing, failed, finishedAttempt, event(actor, failed,
                    attempt.id(), "FAILED", executing.state(), failed.state(),
                    failed.resultCode(), now))) {
                return owned(actor, Long.toString(executing.sessionId()),
                        Long.toString(executing.id()));
            }
            return failed;
        }
        var result = result(ownerResult);
        var succeeded = terminal(executing,
                AiConfigurationFieldProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), result, "OK",
                actor.requestId(), actor.traceId(), now);
        var finishedAttempt = finishedAttempt(attempt,
                AiConfigurationFieldProposal.State.SUCCEEDED,
                resultHash(result), "OK", now);
        if (!repository.finishConfigurationFieldProposal(
                executing, succeeded, finishedAttempt, event(actor, succeeded,
                attempt.id(), "SUCCEEDED", executing.state(), succeeded.state(),
                "OK", now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return succeeded;
    }

    private void recheckPolicy(AiConfigurationFieldProposal value) {
        repository.activePolicy(value.systemId(), value.tenantId()).filter(policy ->
                policy.id() == value.policyVersionId() && policy.enabled()
                        && policy.providerId() == value.providerId()
                        && policy.providerVersion() == value.providerVersion()
                        && policy.allowedOperations().contains("CONFIG_FIELD_DRAFT")
                        && policy.allowedModuleCodes().contains(value.moduleCode())
                        && policy.confirmationMode()
                        == AiPolicy.ConfirmationMode.REQUIRED).orElseThrow(() ->
                conflict("AI_CONFIG_FIELD_POLICY_CHANGED",
                        "Configuration field proposal exceeds the active policy"));
    }

    private AiConfigurationFieldProposal owned(
            AiActor actor, String sessionId, String proposalId) {
        var parsedSession = positiveId(sessionId, "sessionId");
        var value = repository.configurationFieldProposal(
                actor.systemId(), actor.tenantId(), actor.memberId(), parsedSession,
                positiveId(proposalId, "proposalId")).orElseThrow(() ->
                AiSupport.notFound("Configuration field proposal does not exist"));
        if (value.accountId() != actor.accountId()
                || repository.session(actor.systemId(), actor.tenantId(),
                actor.memberId(), parsedSession).isEmpty()) {
            throw AiSupport.notFound("Configuration field proposal does not exist");
        }
        return value;
    }

    private AiConfigurationFieldProposal expire(
            AiActor actor, AiConfigurationFieldProposal value) {
        var now = clock.instant();
        var expired = terminal(value,
                AiConfigurationFieldProposal.State.EXPIRED,
                value.revision() + 1, null, null, "AI_CONFIG_FIELD_EXPIRED",
                actor.requestId(), actor.traceId(), now);
        if (repository.transitionConfigurationFieldProposal(value, expired,
                event(actor, expired, null, "EXPIRED", value.state(),
                        expired.state(), expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.sessionId()), Long.toString(value.id()));
    }

    private AiConfigurationFieldProposal.Event event(
            AiActor actor,
            AiConfigurationFieldProposal value,
            Long attemptId,
            String eventType,
            AiConfigurationFieldProposal.State from,
            AiConfigurationFieldProposal.State to,
            String code,
            java.time.Instant now) {
        return new AiConfigurationFieldProposal.Event(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), attemptId, eventType, from, to, value.revision(),
                actor.memberId(), actor.requestId(), actor.traceId(), code,
                AiSupport.sha256(value.id() + ":" + value.revision() + ":"
                        + eventType + ":" + code + ":" + actor.memberId()), now);
    }

    private static void requireBindings(
            AiActor actor, AiConversation.Turn turn, AiPolicy.Version policy) {
        if (turn.systemId() != actor.systemId()
                || turn.tenantId() != actor.tenantId()
                || turn.policyVersionId() != policy.id()
                || turn.providerId() != policy.providerId()
                || turn.providerVersion() != policy.providerVersion()
                || turn.authorizationEpoch() != actor.authorizationEpoch()) {
            throw new IllegalArgumentException(
                    "AI configuration proposal bindings are inconsistent");
        }
    }

    private static void assertPrepared(
            AiConfigurationFieldPlanParser.Plan plan,
            AiConfigurationFieldFacade.PreparedField prepared) {
        var preview = prepared.preview();
        var field = preview.field();
        if (!preview.moduleCode().equals(plan.moduleCode())
                || !field.fieldCode().equals(plan.fieldCode())
                || !field.fieldName().equals(plan.fieldName())
                || !field.fieldType().name().equals(plan.fieldType().name())
                || field.required() != plan.required()
                || !settings(field.settings()).equals(settings(
                ownerSettings(plan.settings(), plan.fieldType())))) {
            throw new IllegalStateException(
                    "Configuration owner returned a mismatched preview");
        }
    }

    private static void assertResult(
            AiConfigurationFieldProposal proposal,
            AiConfigurationFieldFacade.FieldReadback result) {
        if (!result.configRootId().equals(proposal.preview().configRootId())
                || !result.moduleId().equals(proposal.preview().moduleId())
                || !result.moduleCode().equals(proposal.moduleCode())
                || result.draftRevision()
                != proposal.preview().nextDraftRevision()) {
            throw new IllegalStateException(
                    "Configuration owner returned a mismatched field readback");
        }
    }

    private static AiConfigurationFieldProposal.Preview preview(
            AiConfigurationFieldFacade.FieldPreview value, boolean redacted) {
        var field = value.field();
        return new AiConfigurationFieldProposal.Preview(
                value.configRootId(), value.moduleId(),
                value.expectedDraftRevision(), value.nextDraftRevision(),
                value.moduleCode(), redacted
                ? AiConfigurationFieldProposal.REDACTED_FIELD_CODE
                : field.fieldCode(), redacted
                ? AiConfigurationFieldProposal.REDACTED_FIELD_NAME
                : field.fieldName(), field.fieldType().name(), field.required(),
                settings(field.settings()));
    }

    private static AiConfigurationFieldProposal.Result result(
            AiConfigurationFieldFacade.FieldReadback value) {
        var field = value.field();
        return new AiConfigurationFieldProposal.Result(
                value.configRootId(), value.moduleId(), value.draftRevision(),
                value.moduleCode(), field.fieldId(),
                AiConfigurationFieldProposal.REDACTED_FIELD_CODE,
                AiConfigurationFieldProposal.REDACTED_FIELD_NAME,
                field.fieldType().name(), field.required(),
                settings(field.settings()), field.sortOrder(), field.version(), "DRAFT");
    }

    private static AiConfigurationFieldFacade.ScalarSettings ownerSettings(
            AiConfigurationFieldPlanParser.Settings value,
            AiConfigurationFieldPlanParser.FieldType type) {
        Integer precision = type == AiConfigurationFieldPlanParser.FieldType.INTEGER
                ? Integer.valueOf(38) : value.precision();
        Integer scale = type == AiConfigurationFieldPlanParser.FieldType.INTEGER
                ? Integer.valueOf(0) : value.scale();
        return new AiConfigurationFieldFacade.ScalarSettings(
                null, value.maxLength(), null, null, value.minimum(),
                value.maximum(), precision, scale, null, null);
    }

    private static AiConfigurationFieldProposal.Settings settings(
            AiConfigurationFieldFacade.ScalarSettings value) {
        return new AiConfigurationFieldProposal.Settings(
                value.maxLength(), value.precision(), value.scale(),
                value.minimum(), value.maximum());
    }

    private static AiConfigurationFieldProposal.Settings settings(
            AiConfigurationFieldPlanParser.Settings value) {
        return new AiConfigurationFieldProposal.Settings(
                value.maxLength(), value.precision(), value.scale(),
                value.minimum(), value.maximum());
    }

    private static AiConfigurationFieldFacade.SealedCommand sealed(
            AiConfigurationFieldProposal.SealedCommand value) {
        return new AiConfigurationFieldFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static AiConfigurationFieldProposal copyClarification(
            AiConfigurationFieldProposal value, String clarification) {
        return new AiConfigurationFieldProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.moduleCode(),
                value.planHash(), value.state(), value.revision(), value.preview(),
                value.confidence(), clarification, value.sealedCommand(),
                value.expiresAt(), value.actedBy(), value.result(), value.resultCode(),
                value.ownerRequestId(), value.ownerTraceId(), value.createdAt(),
                value.updatedAt(), value.finishedAt());
    }

    private static AiConfigurationFieldProposal terminal(
            AiConfigurationFieldProposal value,
            AiConfigurationFieldProposal.State state,
            long revision,
            Long actedBy,
            AiConfigurationFieldProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            java.time.Instant now) {
        return copy(value, state, revision, value.preview(), actedBy, result,
                resultCode, requestId, traceId, now, now);
    }

    private static AiConfigurationFieldProposal copy(
            AiConfigurationFieldProposal value,
            AiConfigurationFieldProposal.State state,
            long revision,
            AiConfigurationFieldProposal.Preview preview,
            Long actedBy,
            AiConfigurationFieldProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            java.time.Instant updatedAt,
            java.time.Instant finishedAt) {
        return new AiConfigurationFieldProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.moduleCode(),
                value.planHash(), state, revision, preview, value.confidence(),
                value.clarification(), value.sealedCommand(), value.expiresAt(),
                actedBy, result, resultCode, requestId, traceId, value.createdAt(),
                updatedAt, finishedAt);
    }

    private static AiConfigurationFieldProposal.Attempt finishedAttempt(
            AiConfigurationFieldProposal.Attempt value,
            AiConfigurationFieldProposal.State state,
            String resultHash,
            String resultCode,
            java.time.Instant now) {
        return new AiConfigurationFieldProposal.Attempt(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), state, resultHash, resultCode,
                value.createdAt(), now);
    }

    private static String resultHash(AiConfigurationFieldProposal.Result value) {
        return AiSupport.sha256(value.configRootId() + ":" + value.moduleId() + ":"
                + value.draftRevision() + ":" + value.fieldId() + ":"
                + value.fieldVersion());
    }

    private static void requireRevision(
            AiConfigurationFieldProposal value, long expectedRevision) {
        if (expectedRevision < 0 || value.revision() != expectedRevision) {
            throw conflict("AI_CONFIG_FIELD_VERSION_CONFLICT",
                    "Configuration field proposal revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid(
                    "AI_CONFIG_FIELD_IDEMPOTENCY_KEY_INVALID",
                    "Configuration field Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "AI_CONFIG_FIELD_OWNER_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid("AI_CONFIG_FIELD_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    private static BusinessException conflict(String code, String message) {
        return AiSupport.conflict(code, message);
    }

    public record PreparedProposal(
            AiConfigurationFieldProposal liveProposal,
            AiConfigurationFieldProposal storedProposal,
            AiConfigurationFieldProposal.Event event) {
        public PreparedProposal {
            Objects.requireNonNull(liveProposal, "liveProposal");
            Objects.requireNonNull(storedProposal, "storedProposal");
            Objects.requireNonNull(event, "event");
        }
    }
}
