package com.unique.examine.ai.service;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiWorkProposal;
import com.unique.examine.ai.plan.AiWorkDraftPlanParser;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Shared confirmation state machine for one task or personal report draft. */
@Service
public final class AiWorkProposalService {
    private final AiRepository repository;
    private final AiWorkDraftFacade owner;
    private final IdService ids;
    private final Clock clock;

    public AiWorkProposalService(
            AiRepository repository,
            AiWorkDraftFacade owner,
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
            AiWorkDraftPlanParser.Plan plan) {
        requireBindings(actor, turn, policy);
        if (!policy.allowedOperations().contains(plan.operation().name())
                || policy.confirmationMode() != AiPolicy.ConfirmationMode.REQUIRED) {
            throw AiSupport.invalid("AI_WORK_POLICY_DENIED",
                    "AI Work draft is not allowed by policy");
        }
        var now = clock.instant();
        var proposalId = ids.nextId();
        var operation = operation(plan.operation());
        if (!plan.actionable()) {
            var live = new AiWorkProposal(
                    proposalId, actor.accountId(), actor.systemId(), actor.tenantId(),
                    actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                    policy.providerId(), policy.providerVersion(),
                    actor.authorizationEpoch(), policy.promptVersion(), operation,
                    plan.planHash(), AiWorkProposal.State.CLARIFICATION_REQUIRED,
                    0, null, plan.confidence(), plan.clarification(), null,
                    now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                    null, null, "AI_WORK_CLARIFICATION_REQUIRED",
                    actor.requestId(), actor.traceId(), now, now, null);
            var stored = copyClarification(
                    live, AiWorkProposal.REDACTED_CLARIFICATION);
            return new PreparedProposal(live, stored, event(
                    actor, stored, null, "CLARIFICATION_REQUIRED", null,
                    stored.state(), stored.resultCode(), now));
        }

        var task = task(plan);
        var report = report(plan);
        var prepared = owner.prepare(new AiWorkDraftFacade.PrepareRequest(
                Long.toString(proposalId), Long.toString(turn.sessionId()),
                Long.toString(turn.id()), actor.accountId(), actor.systemId(),
                actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                actor.effectivePermissions(), ownerOperation(plan.operation()),
                task, report, Long.toString(policy.id()),
                Long.toString(policy.providerId()), policy.providerVersion(),
                policy.promptVersion(), actor.requestId(), actor.traceId()));
        assertPrepared(operation, task, report, prepared.preview());
        var latestAllowed = now.plus(
                policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS);
        if (!prepared.expiresAt().isAfter(now)) {
            throw AiSupport.conflict("AI_WORK_PREPARE_EXPIRED",
                    "Work owner returned an invalid proposal expiry");
        }
        var expiresAt = prepared.expiresAt().isBefore(latestAllowed)
                ? prepared.expiresAt() : latestAllowed;
        var sealed = new AiWorkProposal.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                prepared.sealedCommand().commandSha256());
        var live = new AiWorkProposal(
                proposalId, actor.accountId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                policy.providerId(), policy.providerVersion(),
                actor.authorizationEpoch(), policy.promptVersion(), operation,
                plan.planHash(), AiWorkProposal.State.PENDING, 0,
                preview(prepared.preview(), false), plan.confidence(), null,
                sealed, expiresAt, null, null, "AI_WORK_PENDING",
                actor.requestId(), actor.traceId(), now, now, null);
        var stored = copy(live, live.state(), live.revision(),
                preview(prepared.preview(), true), null, null,
                live.resultCode(), live.requestId(), live.traceId(), now, null);
        return new PreparedProposal(live, stored, event(
                actor, stored, null, "PROPOSED", null, stored.state(),
                stored.resultCode(), now));
    }

    public AiWorkProposal get(
            AiActor actor, String sessionId, String proposalId) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiWorkProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        return value;
    }

    public AiWorkProposal reject(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiWorkProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiWorkProposal.State.PENDING) {
            throw conflict("AI_WORK_STATE_CONFLICT",
                    "Work proposal is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(
                value, AiWorkProposal.State.REJECTED, value.revision() + 1,
                actor.memberId(), null, "AI_WORK_REJECTED", actor, now);
        if (!repository.transitionWorkProposal(value, rejected, event(
                actor, rejected, null, "REJECTED", value.state(),
                rejected.state(), rejected.resultCode(), now))) {
            throw conflict("AI_WORK_VERSION_CONFLICT",
                    "Work proposal revision changed");
        }
        return rejected;
    }

    public AiWorkProposal confirm(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision, String idempotencyKey) {
        var key = requiredKey(idempotencyKey);
        var value = owned(actor, sessionId, proposalId);
        var requestHash = AiSupport.sha256(value.id() + ":"
                + expectedRevision + ":" + value.operation() + ":CONFIRM");
        var replay = repository.workAttempt(
                actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_WORK_REPLAY_CONFLICT",
                        "Work proposal key was used for another request");
            }
            if (value.state() != AiWorkProposal.State.EXECUTING) return value;
            return execute(actor, value, replay.get());
        }
        if (value.state() == AiWorkProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_WORK_EXPIRED", "Work proposal has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiWorkProposal.State.PENDING) {
            throw conflict("AI_WORK_STATE_CONFLICT",
                    "Work proposal is no longer pending");
        }
        recheckPolicy(value);
        var now = clock.instant();
        var executing = copy(
                value, AiWorkProposal.State.EXECUTING, value.revision() + 1,
                value.preview(), actor.memberId(), null, "AI_WORK_EXECUTING",
                actor.requestId(), actor.traceId(), now, null);
        var attempt = new AiWorkProposal.Attempt(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), "CONFIRM", key, requestHash,
                AiWorkProposal.State.EXECUTING, null, "AI_WORK_EXECUTING",
                now, null);
        var claim = repository.claimWorkProposal(
                value, executing, attempt, event(
                actor, executing, attempt.id(), "CONFIRMING", value.state(),
                executing.state(), executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.workAttempt(
                    actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw conflict("AI_WORK_REPLAY_CONFLICT",
                        "Work proposal key was used for another request");
            }
            if (concurrent.isPresent()) {
                return execute(actor, owned(actor, sessionId, proposalId),
                        concurrent.get());
            }
            throw conflict("AI_WORK_VERSION_CONFLICT",
                    "Work proposal revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private AiWorkProposal execute(
            AiActor actor,
            AiWorkProposal executing,
            AiWorkProposal.Attempt attempt) {
        if (executing.state() != AiWorkProposal.State.EXECUTING
                || attempt.status() != AiWorkProposal.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final AiWorkDraftFacade.DraftReadback ownerResult;
        try {
            recheckPolicy(executing);
            ownerResult = owner.execute(new AiWorkDraftFacade.ExecuteRequest(
                    Long.toString(executing.id()),
                    Long.toString(executing.sessionId()),
                    Long.toString(executing.turnId()), executing.accountId(),
                    executing.systemId(), executing.tenantId(), executing.memberId(),
                    actor.authorizationEpoch(), actor.effectivePermissions(),
                    ownerOperation(executing.operation()),
                    sealed(executing.sealedCommand()), attempt.requestKey(),
                    actor.requestId(), actor.traceId()));
            assertResult(executing, ownerResult);
        } catch (RuntimeException failure) {
            return finishFailure(actor, executing, attempt, failure, now);
        }

        var liveResult = result(ownerResult, false);
        var storedResult = result(ownerResult, true);
        var live = terminal(
                executing, AiWorkProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), liveResult,
                "OK", actor, now);
        var stored = terminal(
                executing, AiWorkProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), storedResult,
                "OK", actor, now);
        var finishedAttempt = finishedAttempt(
                attempt, AiWorkProposal.State.SUCCEEDED,
                resultHash(storedResult), "OK", now);
        if (!repository.finishWorkProposal(executing, stored, finishedAttempt,
                event(actor, stored, attempt.id(), "SUCCEEDED", executing.state(),
                        stored.state(), "OK", now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return live;
    }

    private AiWorkProposal finishFailure(
            AiActor actor,
            AiWorkProposal executing,
            AiWorkProposal.Attempt attempt,
            RuntimeException failure,
            Instant now) {
        var code = failure instanceof BusinessException business
                ? safeCode(business.code()) : "AI_WORK_OWNER_FAILED";
        var state = terminalState(code);
        var terminal = terminal(
                executing, state, executing.revision() + 1, actor.memberId(),
                null, code, actor, now);
        var finishedAttempt = finishedAttempt(
                attempt, state, AiSupport.sha256(code), code, now);
        if (!repository.finishWorkProposal(executing, terminal, finishedAttempt,
                event(actor, terminal, attempt.id(), state.name(), executing.state(),
                        state, code, now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return terminal;
    }

    private void recheckPolicy(AiWorkProposal value) {
        repository.activePolicy(value.systemId(), value.tenantId()).filter(policy ->
                policy.id() == value.policyVersionId() && policy.enabled()
                        && policy.providerId() == value.providerId()
                        && policy.providerVersion() == value.providerVersion()
                        && policy.allowedOperations().contains(value.operation().name())
                        && policy.confirmationMode()
                        == AiPolicy.ConfirmationMode.REQUIRED)
                .orElseThrow(() -> conflict(
                        "AI_WORK_POLICY_CHANGED",
                        "Work proposal exceeds the active policy"));
    }

    private AiWorkProposal owned(
            AiActor actor, String sessionId, String proposalId) {
        var parsedSession = positiveId(sessionId, "sessionId");
        var value = repository.workProposal(
                actor.systemId(), actor.tenantId(), actor.memberId(), parsedSession,
                positiveId(proposalId, "proposalId")).orElseThrow(() ->
                AiSupport.notFound("Work proposal does not exist"));
        if (value.accountId() != actor.accountId()
                || repository.session(actor.systemId(), actor.tenantId(),
                actor.memberId(), parsedSession).isEmpty()) {
            throw AiSupport.notFound("Work proposal does not exist");
        }
        return value;
    }

    private AiWorkProposal expire(AiActor actor, AiWorkProposal value) {
        var now = clock.instant();
        var expired = terminal(
                value, AiWorkProposal.State.EXPIRED, value.revision() + 1,
                null, null, "AI_WORK_EXPIRED", actor, now);
        if (repository.transitionWorkProposal(value, expired, event(
                actor, expired, null, "EXPIRED", value.state(), expired.state(),
                expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.sessionId()), Long.toString(value.id()));
    }

    private AiWorkProposal.Event event(
            AiActor actor,
            AiWorkProposal value,
            Long attemptId,
            String eventType,
            AiWorkProposal.State from,
            AiWorkProposal.State to,
            String code,
            Instant now) {
        return new AiWorkProposal.Event(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), attemptId, eventType, from, to, value.revision(),
                actor.memberId(), actor.requestId(), actor.traceId(), code,
                AiSupport.sha256(value.id() + ":" + value.revision() + ":"
                        + eventType + ":" + code + ":" + actor.memberId()), now);
    }

    private static AiWorkDraftFacade.TaskDraft task(
            AiWorkDraftPlanParser.Plan value) {
        if (!(value instanceof AiWorkDraftPlanParser.TaskPlan task)
                || !value.actionable()) return null;
        return new AiWorkDraftFacade.TaskDraft(
                task.title(), task.description(), task.assigneeMemberId(),
                task.projectId(), task.dueAt());
    }

    private static AiWorkDraftFacade.DailyReportDraft report(
            AiWorkDraftPlanParser.Plan value) {
        if (!(value instanceof AiWorkDraftPlanParser.DailyReportPlan report)
                || !value.actionable()) return null;
        return new AiWorkDraftFacade.DailyReportDraft(
                report.workDate(), report.completedWork(), report.plannedWork(),
                report.blockers());
    }

    private static void assertPrepared(
            AiWorkProposal.Operation operation,
            AiWorkDraftFacade.TaskDraft task,
            AiWorkDraftFacade.DailyReportDraft report,
            AiWorkDraftFacade.DraftPreview preview) {
        if (preview.operation().name().equals(operation.name())
                && Objects.equals(preview.task(), task)
                && Objects.equals(preview.report(), report)) return;
        throw new IllegalStateException("Work owner preview is mismatched");
    }

    private static void assertResult(
            AiWorkProposal proposal,
            AiWorkDraftFacade.DraftReadback value) {
        if (!value.operation().name().equals(proposal.operation().name())) {
            throw new IllegalStateException("Work owner readback is mismatched");
        }
        if (value.task() != null
                && !value.task().assigneeMemberId().equals(
                proposal.preview().task().assigneeMemberId())) {
            throw new IllegalStateException("Work task assignee readback is mismatched");
        }
        if (value.report() != null
                && (!value.report().authorMemberId().equals(
                Long.toString(proposal.memberId()))
                || !value.report().workDate().equals(
                proposal.preview().report().workDate()))) {
            throw new IllegalStateException("Work report readback is mismatched");
        }
    }

    private static AiWorkProposal.Preview preview(
            AiWorkDraftFacade.DraftPreview value, boolean redacted) {
        AiWorkProposal.TaskPreview task = null;
        AiWorkProposal.DailyReportPreview report = null;
        if (value.task() != null) {
            task = new AiWorkProposal.TaskPreview(
                    redacted ? AiWorkProposal.REDACTED_TITLE : value.task().title(),
                    value.task().description() == null ? null : redacted
                            ? AiWorkProposal.REDACTED_DESCRIPTION
                            : value.task().description(),
                    value.task().assigneeMemberId(), null,
                    value.task().projectId(), null, value.task().dueAt());
        } else {
            report = new AiWorkProposal.DailyReportPreview(
                    value.report().workDate(),
                    redacted ? AiWorkProposal.REDACTED_NARRATIVE
                            : value.report().completedWork(),
                    redacted ? AiWorkProposal.REDACTED_NARRATIVE
                            : value.report().plannedWork(),
                    value.report().blockers() == null ? null : redacted
                            ? AiWorkProposal.REDACTED_NARRATIVE
                            : value.report().blockers());
        }
        return new AiWorkProposal.Preview(
                AiWorkProposal.Operation.valueOf(value.operation().name()),
                task, report);
    }

    private static AiWorkProposal.Result result(
            AiWorkDraftFacade.DraftReadback value, boolean redacted) {
        AiWorkProposal.TaskResult task = null;
        AiWorkProposal.DailyReportResult report = null;
        if (value.task() != null) {
            var source = value.task();
            task = new AiWorkProposal.TaskResult(
                    source.taskId(), source.version(), redacted
                    ? AiWorkProposal.REDACTED_TITLE : source.title(),
                    source.description() == null ? null : redacted
                            ? AiWorkProposal.REDACTED_DESCRIPTION
                            : source.description(), source.status(),
                    source.assigneeMemberId(), source.projectId(), source.dueAt(),
                    source.createdAt(), source.updatedAt());
        } else {
            var source = value.report();
            report = new AiWorkProposal.DailyReportResult(
                    source.reportId(), source.version(), source.authorMemberId(),
                    source.workDate(), redacted
                    ? AiWorkProposal.REDACTED_NARRATIVE : source.completedWork(),
                    redacted ? AiWorkProposal.REDACTED_NARRATIVE
                            : source.plannedWork(),
                    source.blockers() == null ? null : redacted
                            ? AiWorkProposal.REDACTED_NARRATIVE : source.blockers(),
                    source.status(), source.createdAt(), source.updatedAt());
        }
        return new AiWorkProposal.Result(
                AiWorkProposal.Operation.valueOf(value.operation().name()),
                task, report);
    }

    private static AiWorkProposal copyClarification(
            AiWorkProposal value, String clarification) {
        return new AiWorkProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.operation(),
                value.planHash(), value.state(), value.revision(), value.preview(),
                value.confidence(), clarification, value.sealedCommand(),
                value.expiresAt(), value.actedBy(), value.result(), value.resultCode(),
                value.requestId(), value.traceId(), value.createdAt(),
                value.updatedAt(), value.finishedAt());
    }

    private static AiWorkProposal terminal(
            AiWorkProposal value,
            AiWorkProposal.State state,
            long revision,
            Long actedBy,
            AiWorkProposal.Result result,
            String resultCode,
            AiActor actor,
            Instant now) {
        return copy(value, state, revision, value.preview(), actedBy, result,
                resultCode, actor.requestId(), actor.traceId(), now, now);
    }

    private static AiWorkProposal copy(
            AiWorkProposal value,
            AiWorkProposal.State state,
            long revision,
            AiWorkProposal.Preview preview,
            Long actedBy,
            AiWorkProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            Instant updatedAt,
            Instant finishedAt) {
        return new AiWorkProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.operation(),
                value.planHash(), state, revision, preview, value.confidence(),
                value.clarification(), value.sealedCommand(), value.expiresAt(),
                actedBy, result, resultCode, requestId, traceId, value.createdAt(),
                updatedAt, finishedAt);
    }

    private static AiWorkProposal.Attempt finishedAttempt(
            AiWorkProposal.Attempt value,
            AiWorkProposal.State state,
            String resultHash,
            String resultCode,
            Instant now) {
        return new AiWorkProposal.Attempt(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), state, resultHash, resultCode,
                value.createdAt(), now);
    }

    private static AiWorkDraftFacade.SealedCommand sealed(
            AiWorkProposal.SealedCommand value) {
        return new AiWorkDraftFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static AiWorkDraftFacade.Operation ownerOperation(
            AiWorkDraftPlanParser.Operation value) {
        return AiWorkDraftFacade.Operation.valueOf(value.name());
    }

    private static AiWorkDraftFacade.Operation ownerOperation(
            AiWorkProposal.Operation value) {
        return AiWorkDraftFacade.Operation.valueOf(value.name());
    }

    private static AiWorkProposal.Operation operation(
            AiWorkDraftPlanParser.Operation value) {
        return AiWorkProposal.Operation.valueOf(value.name());
    }

    private static String resultHash(AiWorkProposal.Result value) {
        var id = value.task() == null
                ? value.report().reportId() : value.task().taskId();
        var version = value.task() == null
                ? value.report().version() : value.task().version();
        return AiSupport.sha256(value.operation() + ":" + id + ":" + version);
    }

    private static AiWorkProposal.State terminalState(String code) {
        if (code.contains("EXPIRED")) return AiWorkProposal.State.EXPIRED;
        if (code.contains("FORBIDDEN") || code.contains("PERMISSION")
                || code.contains("DENIED")) {
            return AiWorkProposal.State.PERMISSION_DENIED;
        }
        if (!code.startsWith("AI_WORK_DRAFT_COMMAND_")
                && (code.contains("STALE") || code.contains("CONFLICT")
                || code.contains("INVALID") || code.contains("NOT_FOUND")
                || code.contains("STATE") || code.contains("EXISTS")
                || code.contains("UNAVAILABLE"))) {
            return AiWorkProposal.State.STALE;
        }
        return AiWorkProposal.State.FAILED;
    }

    private static void requireBindings(
            AiActor actor, AiConversation.Turn turn, AiPolicy.Version policy) {
        if (turn.systemId() != actor.systemId()
                || turn.tenantId() != actor.tenantId()
                || turn.policyVersionId() != policy.id()
                || turn.providerId() != policy.providerId()
                || turn.providerVersion() != policy.providerVersion()
                || turn.authorizationEpoch() != actor.authorizationEpoch()) {
            throw new IllegalArgumentException("AI Work proposal bindings are inconsistent");
        }
    }

    private static void requireRevision(
            AiWorkProposal value, long expectedRevision) {
        if (expectedRevision < 0 || value.revision() != expectedRevision) {
            throw conflict("AI_WORK_VERSION_CONFLICT",
                    "Work proposal revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid("AI_WORK_IDEMPOTENCY_KEY_INVALID",
                    "Work proposal Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "AI_WORK_OWNER_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid("AI_WORK_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    private static BusinessException conflict(String code, String message) {
        return AiSupport.conflict(code, message);
    }

    public record PreparedProposal(
            AiWorkProposal liveProposal,
            AiWorkProposal storedProposal,
            AiWorkProposal.Event event) {
        public PreparedProposal {
            Objects.requireNonNull(liveProposal, "liveProposal");
            Objects.requireNonNull(storedProposal, "storedProposal");
            Objects.requireNonNull(event, "event");
        }
    }
}
