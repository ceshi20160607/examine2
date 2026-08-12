package com.unique.examine.ai.service;

import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.PlatformAiActor;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiTaskProposal;
import com.unique.examine.ai.plan.PlatformAiPlanParser;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Confirmation state machine. All platform task writes remain owner-controlled. */
@Service
public final class PlatformAiTaskProposalService {
    private final PlatformAiRepository repository;
    private final PlatformTaskFacade owner;
    private final IdService ids;
    private final Clock clock;

    public PlatformAiTaskProposalService(
            PlatformAiRepository repository,
            PlatformTaskFacade owner,
            IdService ids,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedProposal prepare(
            PlatformAiActor actor,
            PlatformAiConversation.Turn turn,
            PlatformAiPolicy.Version policy,
            PlatformAiPlanParser.Plan plan) {
        var task = Objects.requireNonNull(plan.taskDraft(), "taskDraft");
        var now = clock.instant();
        var id = ids.nextId();
        if (!task.actionable()) {
            var proposal = new PlatformAiTaskProposal(
                    id, PlatformAiConversation.Scope.PLATFORM,
                    actor.accountId(), turn.sessionId(), turn.id(), policy.id(),
                    policy.providerId(), policy.providerVersion(),
                    actor.authorizationEpoch(), policy.settings().promptVersion(),
                    plan.planHash(),
                    PlatformAiTaskProposal.State.CLARIFICATION_REQUIRED, 0,
                    null, task.confidence(), task.clarification(), null,
                    now.plus(15, ChronoUnit.MINUTES), null, null,
                    "PLATFORM_AI_TASK_CLARIFICATION_REQUIRED", null, null,
                    now, now, null);
            return new PreparedProposal(proposal, event(
                    actor, proposal, null, "CLARIFICATION_REQUIRED", null,
                    proposal.state(), proposal.resultCode(), now));
        }
        actor.require("platform.task.create");
        var draft = new PlatformTaskFacade.TaskDraft(
                task.title(), task.description(), task.dueAt(),
                PlatformTaskFacade.Priority.valueOf(task.priority().name()));
        var prepared = owner.prepare(new PlatformTaskFacade.PrepareRequest(
                Long.toString(id), actor.accountId(), actor.authorizationEpoch(),
                draft, actor.requestId(), actor.traceId()));
        assertPrepared(actor, draft, prepared);
        if (!prepared.expiresAt().isAfter(now)) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_PREPARE_EXPIRED",
                    "Platform task owner returned an expired command");
        }
        var preview = new PlatformAiTaskProposal.Preview(
                prepared.preview().title(), prepared.preview().description(),
                prepared.preview().dueAt(),
                PlatformAiTaskProposal.Priority.valueOf(
                        prepared.preview().priority().name()));
        var sealed = new PlatformAiTaskProposal.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                prepared.sealedCommand().commandSha256());
        var proposal = new PlatformAiTaskProposal(
                id, PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), turn.sessionId(), turn.id(), policy.id(),
                policy.providerId(), policy.providerVersion(),
                actor.authorizationEpoch(), policy.settings().promptVersion(),
                plan.planHash(), PlatformAiTaskProposal.State.PENDING, 0,
                preview, task.confidence(), null, sealed, prepared.expiresAt(),
                null, null, "PLATFORM_AI_TASK_PENDING", null, null,
                now, now, null);
        return new PreparedProposal(proposal, event(
                actor, proposal, null, "PROPOSED", null, proposal.state(),
                proposal.resultCode(), now));
    }

    public PlatformAiTaskProposal get(
            PlatformAiActor actor, String sessionId, String proposalId) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == PlatformAiTaskProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        return value;
    }

    public PlatformAiTaskProposal reject(
            PlatformAiActor actor,
            String sessionId,
            String proposalId,
            long expectedRevision) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == PlatformAiTaskProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        requireRevision(value, expectedRevision);
        if (value.state() != PlatformAiTaskProposal.State.PENDING) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_STATE_CONFLICT",
                    "Platform AI task proposal is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(
                value, PlatformAiTaskProposal.State.REJECTED,
                value.revision() + 1, actor.accountId(), null,
                "PLATFORM_AI_TASK_REJECTED", actor.requestId(), actor.traceId(), now);
        if (!repository.transitionTaskProposal(value, rejected, event(
                actor, rejected, null, "REJECTED", value.state(), rejected.state(),
                rejected.resultCode(), now))) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_VERSION_CONFLICT",
                    "Platform AI task proposal revision changed");
        }
        return rejected;
    }

    public PlatformAiTaskProposal confirm(
            PlatformAiActor actor,
            String sessionId,
            String proposalId,
            long expectedRevision,
            String idempotencyKey) {
        actor.require("platform.task.create");
        var key = requiredKey(idempotencyKey);
        var value = owned(actor, sessionId, proposalId);
        var requestHash = AiSupport.sha256(
                value.id() + ":" + expectedRevision + ":CONFIRM");
        var replay = repository.taskProposalAttempt(
                actor.accountId(), value.id(), "CONFIRM", key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw AiSupport.conflict(
                        "PLATFORM_AI_TASK_REPLAY_CONFLICT",
                        "Platform AI task key was used for another request");
            }
            if (value.state() != PlatformAiTaskProposal.State.EXECUTING) {
                return value;
            }
            return execute(actor, value, replay.get());
        }
        if (value.state() == PlatformAiTaskProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_EXPIRED",
                    "Platform AI task proposal has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != PlatformAiTaskProposal.State.PENDING) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_STATE_CONFLICT",
                    "Platform AI task proposal is no longer pending");
        }
        recheckPolicy(value);
        var now = clock.instant();
        var executing = executing(value, actor, now);
        var attempt = new PlatformAiTaskProposal.Attempt(
                ids.nextId(), value.scope(), value.accountId(), value.sessionId(),
                value.turnId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.id(), "CONFIRM", key, requestHash,
                PlatformAiTaskProposal.State.EXECUTING, null,
                "PLATFORM_AI_TASK_EXECUTING", now, null);
        var claim = repository.claimTaskProposal(
                value, executing, attempt, event(
                        actor, executing, attempt.id(), "CONFIRMING", value.state(),
                        executing.state(), executing.resultCode(), now));
        if (claim != PlatformAiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.taskProposalAttempt(
                    actor.accountId(), value.id(), "CONFIRM", key);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw AiSupport.conflict(
                        "PLATFORM_AI_TASK_REPLAY_CONFLICT",
                        "Platform AI task key was used for another request");
            }
            if (concurrent.isPresent()) {
                return execute(actor, owned(actor, sessionId, proposalId),
                        concurrent.get());
            }
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_VERSION_CONFLICT",
                    "Platform AI task proposal revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private PlatformAiTaskProposal execute(
            PlatformAiActor actor,
            PlatformAiTaskProposal executing,
            PlatformAiTaskProposal.Attempt attempt) {
        if (executing.state() != PlatformAiTaskProposal.State.EXECUTING
                || attempt.status() != PlatformAiTaskProposal.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final PlatformTaskFacade.TaskView ownerResult;
        try {
            recheckPolicy(executing);
            ownerResult = owner.execute(new PlatformTaskFacade.ExecuteRequest(
                    Long.toString(executing.id()), actor.accountId(),
                    actor.authorizationEpoch(), sealed(executing.sealedCommand()),
                    attempt.requestKey(), actor.requestId(), actor.traceId()));
            assertResult(actor, ownerResult);
        } catch (RuntimeException failure) {
            var code = failure instanceof BusinessException business
                    ? business.code() : "PLATFORM_AI_TASK_OWNER_FAILED";
            var failed = terminal(
                    executing, PlatformAiTaskProposal.State.FAILED,
                    executing.revision() + 1, actor.accountId(), null,
                    safeCode(code), actor.requestId(), actor.traceId(), now);
            var finishedAttempt = finishedAttempt(
                    attempt, PlatformAiTaskProposal.State.FAILED,
                    AiSupport.sha256(failed.resultCode()), failed.resultCode(), now);
            if (!repository.finishTaskProposal(
                    executing, failed, finishedAttempt, event(
                            actor, failed, attempt.id(), "FAILED", executing.state(),
                            failed.state(), failed.resultCode(), now))) {
                return owned(actor, Long.toString(executing.sessionId()),
                        Long.toString(executing.id()));
            }
            return failed;
        }
        var result = result(ownerResult);
        var succeeded = terminal(
                executing, PlatformAiTaskProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.accountId(), result, "OK",
                actor.requestId(), actor.traceId(), now);
        var finishedAttempt = finishedAttempt(
                attempt, PlatformAiTaskProposal.State.SUCCEEDED,
                hashResult(result), "OK", now);
        if (!repository.finishTaskProposal(
                executing, succeeded, finishedAttempt, event(
                        actor, succeeded, attempt.id(), "SUCCEEDED", executing.state(),
                        succeeded.state(), "OK", now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return succeeded;
    }

    private void recheckPolicy(PlatformAiTaskProposal value) {
        repository.activePolicy().filter(policy ->
                policy.id() == value.policyVersionId()
                        && policy.settings().enabled()
                        && policy.settings().allowedOperations().contains(
                        "PLATFORM_TASK_DRAFT")).orElseThrow(() ->
                AiSupport.conflict(
                        "PLATFORM_AI_POLICY_CHANGED",
                        "Platform AI task proposal exceeds the active policy"));
    }

    private PlatformAiTaskProposal owned(
            PlatformAiActor actor, String sessionId, String proposalId) {
        var value = repository.taskProposal(
                actor.accountId(), positiveId(sessionId, "sessionId"),
                positiveId(proposalId, "proposalId")).orElseThrow(() ->
                AiSupport.notFound("Platform AI task proposal does not exist"));
        if (repository.session(actor.accountId(), value.sessionId()).isEmpty()) {
            throw AiSupport.notFound("Platform AI task proposal does not exist");
        }
        return value;
    }

    private PlatformAiTaskProposal expire(
            PlatformAiActor actor, PlatformAiTaskProposal value) {
        var now = clock.instant();
        var expired = terminal(
                value, PlatformAiTaskProposal.State.EXPIRED,
                value.revision() + 1, null, null,
                "PLATFORM_AI_TASK_EXPIRED", actor.requestId(), actor.traceId(), now);
        if (repository.transitionTaskProposal(value, expired, event(
                actor, expired, null, "EXPIRED", value.state(), expired.state(),
                expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.sessionId()), Long.toString(value.id()));
    }

    private PlatformAiTaskProposal.Event event(
            PlatformAiActor actor,
            PlatformAiTaskProposal value,
            Long attemptId,
            String eventType,
            PlatformAiTaskProposal.State from,
            PlatformAiTaskProposal.State to,
            String code,
            java.time.Instant now) {
        return new PlatformAiTaskProposal.Event(
                ids.nextId(), value.scope(), value.accountId(), value.sessionId(),
                value.turnId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.id(), attemptId, eventType, from, to,
                value.revision(), actor.accountId(), actor.requestId(), actor.traceId(),
                code, AiSupport.sha256(value.id() + ":" + value.revision() + ":"
                + eventType + ":" + code + ":" + actor.accountId()), now);
    }

    private static PlatformAiTaskProposal executing(
            PlatformAiTaskProposal value,
            PlatformAiActor actor,
            java.time.Instant now) {
        return copy(value, PlatformAiTaskProposal.State.EXECUTING,
                value.revision() + 1, actor.accountId(), null,
                "PLATFORM_AI_TASK_EXECUTING", actor.requestId(),
                actor.traceId(), now, null);
    }

    private static PlatformAiTaskProposal terminal(
            PlatformAiTaskProposal value,
            PlatformAiTaskProposal.State state,
            long revision,
            Long actedBy,
            PlatformAiTaskProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            java.time.Instant now) {
        return copy(value, state, revision, actedBy, result, resultCode,
                requestId, traceId, now, now);
    }

    private static PlatformAiTaskProposal copy(
            PlatformAiTaskProposal value,
            PlatformAiTaskProposal.State state,
            long revision,
            Long actedBy,
            PlatformAiTaskProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            java.time.Instant updatedAt,
            java.time.Instant finishedAt) {
        return new PlatformAiTaskProposal(
                value.id(), value.scope(), value.accountId(), value.sessionId(),
                value.turnId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.authorizationEpoch(),
                value.promptVersion(), value.planHash(), state, revision,
                value.preview(), value.confidence(), value.clarification(),
                value.sealedCommand(), value.expiresAt(), actedBy, result,
                resultCode, requestId, traceId, value.createdAt(), updatedAt,
                finishedAt);
    }

    private static PlatformAiTaskProposal.Attempt finishedAttempt(
            PlatformAiTaskProposal.Attempt value,
            PlatformAiTaskProposal.State state,
            String resultHash,
            String resultCode,
            java.time.Instant now) {
        return new PlatformAiTaskProposal.Attempt(
                value.id(), value.scope(), value.accountId(), value.sessionId(),
                value.turnId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.proposalId(), value.action(),
                value.requestKey(), value.requestHash(), state, resultHash,
                resultCode, value.createdAt(), now);
    }

    private static PlatformTaskFacade.SealedCommand sealed(
            PlatformAiTaskProposal.SealedCommand value) {
        return new PlatformTaskFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static PlatformAiTaskProposal.Result result(
            PlatformTaskFacade.TaskView value) {
        return new PlatformAiTaskProposal.Result(
                value.taskId(), value.title(), value.description(), value.dueAt(),
                PlatformAiTaskProposal.Priority.valueOf(value.priority().name()),
                value.status().name(), value.source().name(), value.createdAt());
    }

    private static void assertPrepared(
            PlatformAiActor actor,
            PlatformTaskFacade.TaskDraft draft,
            PlatformTaskFacade.PreparedTask prepared) {
        var preview = prepared.preview();
        if (preview.accountId() != actor.accountId()
                || !preview.title().equals(draft.title())
                || !Objects.equals(preview.description(), draft.description())
                || !Objects.equals(preview.dueAt(), draft.dueAt())
                || preview.priority() != draft.priority()
                || preview.status() != PlatformTaskFacade.Status.OPEN
                || preview.source() != PlatformTaskFacade.Source.AGENT) {
            throw new IllegalStateException(
                    "Platform task owner returned a mismatched preview");
        }
    }

    private static void assertResult(
            PlatformAiActor actor,
            PlatformTaskFacade.TaskView result) {
        if (result.accountId() != actor.accountId()
                || result.createdBy() != actor.accountId()
                || result.authorizationEpoch() != actor.authorizationEpoch()
                || result.status() != PlatformTaskFacade.Status.OPEN
                || result.source() != PlatformTaskFacade.Source.AGENT) {
            throw new IllegalStateException(
                    "Platform task owner returned a mismatched task");
        }
    }

    private static String hashResult(PlatformAiTaskProposal.Result value) {
        return AiSupport.sha256(value.taskId() + ":" + value.status() + ":"
                + value.source() + ":" + value.createdAt());
    }

    private static void requireRevision(
            PlatformAiTaskProposal value, long expectedRevision) {
        if (expectedRevision < 0 || value.revision() != expectedRevision) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TASK_VERSION_CONFLICT",
                    "Platform AI task proposal revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_IDEMPOTENCY_KEY_INVALID",
                    "Platform AI task Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "PLATFORM_AI_TASK_OWNER_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_ID_INVALID", field + " must be a positive id");
        }
    }

    public record PreparedProposal(
            PlatformAiTaskProposal proposal,
            PlatformAiTaskProposal.Event event) {
        public PreparedProposal {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(event, "event");
        }
    }
}
