package com.unique.examine.ai.service;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiGeneratedDraftProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiGeneratedDraftPlanParser;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Shared confirmation lifecycle for one generated Flow, report, or print draft. */
@Service
public final class AiGeneratedDraftProposalService {
    private final AiRepository repository;
    private final AiFlowDefinitionDraftFacade flowOwner;
    private final AiModuleGeneratedDraftFacade moduleOwner;
    private final IdService ids;
    private final Clock clock;

    public AiGeneratedDraftProposalService(
            AiRepository repository,
            AiFlowDefinitionDraftFacade flowOwner,
            AiModuleGeneratedDraftFacade moduleOwner,
            IdService ids,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.flowOwner = Objects.requireNonNull(flowOwner, "flowOwner");
        this.moduleOwner = Objects.requireNonNull(moduleOwner, "moduleOwner");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedProposal prepare(
            AiActor actor,
            AiConversation.Turn turn,
            AiPolicy.Version policy,
            AiGeneratedDraftPlanParser.Plan plan) {
        requireBindings(actor, turn, policy);
        if (!policy.allowedOperations().contains(plan.operation().name())
                || policy.confirmationMode() != AiPolicy.ConfirmationMode.REQUIRED) {
            throw AiSupport.invalid("AI_GENERATED_DRAFT_POLICY_DENIED",
                    "AI generated draft is not allowed by policy");
        }
        var now = clock.instant();
        var proposalId = ids.nextId();
        var operation = operation(plan.operation());
        if (!plan.actionable()) {
            var live = new AiGeneratedDraftProposal(
                    proposalId, actor.accountId(), actor.systemId(), actor.tenantId(),
                    actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                    policy.providerId(), policy.providerVersion(),
                    actor.authorizationEpoch(), policy.promptVersion(), operation,
                    plan.planHash(),
                    AiGeneratedDraftProposal.State.CLARIFICATION_REQUIRED,
                    0, null, plan.confidence(), plan.clarification(), null,
                    now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                    null, null, "AI_GENERATED_DRAFT_CLARIFICATION_REQUIRED",
                    actor.requestId(), actor.traceId(), now, now, null);
            var stored = copyClarification(
                    live, AiGeneratedDraftProposal.REDACTED_CLARIFICATION);
            return new PreparedProposal(live, stored, event(
                    actor, stored, null, "CLARIFICATION_REQUIRED", null,
                    stored.state(), stored.resultCode(), now));
        }

        var prepared = prepareOwner(actor, turn, policy, plan, proposalId);
        var latestAllowed = now.plus(
                policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS);
        if (!prepared.expiresAt().isAfter(now)) {
            throw AiSupport.conflict("AI_GENERATED_DRAFT_PREPARE_EXPIRED",
                    "Generated-draft owner returned an invalid proposal expiry");
        }
        var expiresAt = prepared.expiresAt().isBefore(latestAllowed)
                ? prepared.expiresAt() : latestAllowed;
        var live = new AiGeneratedDraftProposal(
                proposalId, actor.accountId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                policy.providerId(), policy.providerVersion(),
                actor.authorizationEpoch(), policy.promptVersion(), operation,
                plan.planHash(), AiGeneratedDraftProposal.State.PENDING, 0,
                prepared.preview(), plan.confidence(), null, prepared.sealedCommand(),
                expiresAt, null, null, "AI_GENERATED_DRAFT_PENDING",
                actor.requestId(), actor.traceId(), now, now, null);
        var stored = copy(live, live.state(), live.revision(),
                redact(live.preview()), null, null, live.resultCode(),
                live.requestId(), live.traceId(), now, null);
        return new PreparedProposal(live, stored, event(
                actor, stored, null, "PROPOSED", null, stored.state(),
                stored.resultCode(), now));
    }

    public AiGeneratedDraftProposal get(
            AiActor actor, String sessionId, String proposalId) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiGeneratedDraftProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        return value;
    }

    public AiGeneratedDraftProposal reject(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiGeneratedDraftProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            return expire(actor, value);
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiGeneratedDraftProposal.State.PENDING) {
            throw conflict("AI_GENERATED_DRAFT_STATE_CONFLICT",
                    "Generated-draft proposal is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(
                value, AiGeneratedDraftProposal.State.REJECTED,
                value.revision() + 1, actor.memberId(), null,
                "AI_GENERATED_DRAFT_REJECTED", actor, now);
        if (!repository.transitionGeneratedDraftProposal(value, rejected, event(
                actor, rejected, null, "REJECTED", value.state(),
                rejected.state(), rejected.resultCode(), now))) {
            throw conflict("AI_GENERATED_DRAFT_VERSION_CONFLICT",
                    "Generated-draft proposal revision changed");
        }
        return rejected;
    }

    public AiGeneratedDraftProposal confirm(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision, String idempotencyKey) {
        var key = requiredKey(idempotencyKey);
        var value = owned(actor, sessionId, proposalId);
        var requestHash = AiSupport.sha256(value.id() + ":"
                + expectedRevision + ":" + value.operation() + ":CONFIRM");
        var replay = repository.generatedDraftAttempt(
                actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_GENERATED_DRAFT_REPLAY_CONFLICT",
                        "Generated-draft proposal key was used for another request");
            }
            if (value.state() != AiGeneratedDraftProposal.State.EXECUTING) {
                return value;
            }
            return execute(actor, value, replay.get());
        }
        if (value.state() == AiGeneratedDraftProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_GENERATED_DRAFT_EXPIRED",
                    "Generated-draft proposal has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiGeneratedDraftProposal.State.PENDING) {
            throw conflict("AI_GENERATED_DRAFT_STATE_CONFLICT",
                    "Generated-draft proposal is no longer pending");
        }
        var now = clock.instant();
        var executing = copy(
                value, AiGeneratedDraftProposal.State.EXECUTING,
                value.revision() + 1, value.preview(), actor.memberId(), null,
                "AI_GENERATED_DRAFT_EXECUTING", actor.requestId(), actor.traceId(),
                now, null);
        var attempt = new AiGeneratedDraftProposal.Attempt(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), "CONFIRM", key, requestHash,
                AiGeneratedDraftProposal.State.EXECUTING, null,
                "AI_GENERATED_DRAFT_EXECUTING", now, null);
        var claim = repository.claimGeneratedDraftProposal(
                value, executing, attempt, event(
                actor, executing, attempt.id(), "CONFIRMING", value.state(),
                executing.state(), executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.generatedDraftAttempt(
                    actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw conflict("AI_GENERATED_DRAFT_REPLAY_CONFLICT",
                        "Generated-draft proposal key was used for another request");
            }
            if (concurrent.isPresent()) {
                return execute(actor, owned(actor, sessionId, proposalId),
                        concurrent.get());
            }
            throw conflict("AI_GENERATED_DRAFT_VERSION_CONFLICT",
                    "Generated-draft proposal revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private AiGeneratedDraftProposal execute(
            AiActor actor,
            AiGeneratedDraftProposal executing,
            AiGeneratedDraftProposal.Attempt attempt) {
        if (executing.state() != AiGeneratedDraftProposal.State.EXECUTING
                || attempt.status() != AiGeneratedDraftProposal.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final AiGeneratedDraftProposal.Result liveResult;
        try {
            recheckPolicy(executing);
            liveResult = executeOwner(actor, executing, attempt.requestKey());
        } catch (RuntimeException failure) {
            return finishFailure(actor, executing, attempt, failure, now);
        }
        var storedResult = redact(liveResult);
        var live = terminal(
                executing, AiGeneratedDraftProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), liveResult,
                "OK", actor, now);
        var stored = terminal(
                executing, AiGeneratedDraftProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), storedResult,
                "OK", actor, now);
        var finishedAttempt = finishedAttempt(
                attempt, AiGeneratedDraftProposal.State.SUCCEEDED,
                resultHash(storedResult), "OK", now);
        if (!repository.finishGeneratedDraftProposal(
                executing, stored, finishedAttempt, event(
                actor, stored, attempt.id(), "SUCCEEDED", executing.state(),
                stored.state(), "OK", now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return live;
    }

    private AiGeneratedDraftProposal finishFailure(
            AiActor actor,
            AiGeneratedDraftProposal executing,
            AiGeneratedDraftProposal.Attempt attempt,
            RuntimeException failure,
            Instant now) {
        var code = failure instanceof BusinessException business
                ? safeCode(business.code()) : "AI_GENERATED_DRAFT_OWNER_FAILED";
        var state = terminalState(code);
        var terminal = terminal(
                executing, state, executing.revision() + 1, actor.memberId(),
                null, code, actor, now);
        var finishedAttempt = finishedAttempt(
                attempt, state, AiSupport.sha256(code), code, now);
        if (!repository.finishGeneratedDraftProposal(
                executing, terminal, finishedAttempt, event(
                actor, terminal, attempt.id(), state.name(), executing.state(),
                state, code, now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return terminal;
    }

    private PreparedOwner prepareOwner(
            AiActor actor,
            AiConversation.Turn turn,
            AiPolicy.Version policy,
            AiGeneratedDraftPlanParser.Plan plan,
            long proposalId) {
        if (plan instanceof AiGeneratedDraftPlanParser.FlowPlan flow) {
            var draft = new AiFlowDefinitionDraftFacade.Draft(
                    flow.name(), flow.approverMemberIds());
            var value = flowOwner.prepare(
                    new AiFlowDefinitionDraftFacade.PrepareRequest(
                            Long.toString(proposalId), Long.toString(turn.sessionId()),
                            Long.toString(turn.id()), actor.accountId(), actor.systemId(),
                            actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                            actor.effectivePermissions(),
                            AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT,
                            draft, Long.toString(policy.id()),
                            Long.toString(policy.providerId()), policy.providerVersion(),
                            policy.promptVersion(), actor.requestId(), actor.traceId()));
            if (value.preview().operation()
                    != AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT
                    || !value.preview().draft().equals(draft)) {
                throw new IllegalStateException("Flow owner preview is mismatched");
            }
            return new PreparedOwner(
                    new AiGeneratedDraftProposal.Preview(
                            AiGeneratedDraftProposal.Operation.FLOW_DEFINITION_DRAFT,
                            new AiGeneratedDraftProposal.FlowPreview(
                                    draft.name(), draft.approverMemberIds()), null, null),
                    sealed(value.sealedCommand()), value.expiresAt());
        }
        var report = report(plan);
        var print = print(plan);
        var operation = moduleOperation(plan.operation());
        var value = moduleOwner.prepare(new AiModuleGeneratedDraftFacade.PrepareRequest(
                Long.toString(proposalId), Long.toString(turn.sessionId()),
                Long.toString(turn.id()), actor.accountId(), actor.systemId(),
                actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                actor.effectivePermissions(), operation, report, print,
                Long.toString(policy.id()), Long.toString(policy.providerId()),
                policy.providerVersion(), policy.promptVersion(),
                actor.requestId(), actor.traceId()));
        if (value.preview().operation() != operation
                || !Objects.equals(value.preview().report(), report)
                || !Objects.equals(value.preview().printTemplate(), print)) {
            throw new IllegalStateException("Module owner preview is mismatched");
        }
        return new PreparedOwner(
                preview(value.preview()), sealed(value.sealedCommand()),
                value.expiresAt());
    }

    private AiGeneratedDraftProposal.Result executeOwner(
            AiActor actor,
            AiGeneratedDraftProposal proposal,
            String idempotencyKey) {
        if (proposal.operation()
                == AiGeneratedDraftProposal.Operation.FLOW_DEFINITION_DRAFT) {
            var value = flowOwner.execute(
                    new AiFlowDefinitionDraftFacade.ExecuteRequest(
                            Long.toString(proposal.id()),
                            Long.toString(proposal.sessionId()),
                            Long.toString(proposal.turnId()), proposal.accountId(),
                            proposal.systemId(), proposal.tenantId(), proposal.memberId(),
                            actor.authorizationEpoch(), actor.effectivePermissions(),
                            AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT,
                            flowSealed(proposal.sealedCommand()), idempotencyKey,
                            actor.requestId(), actor.traceId()));
            if (!value.approverMemberIds().equals(
                    proposal.preview().flowDefinition().approverMemberIds())) {
                throw new IllegalStateException("Flow owner readback is mismatched");
            }
            return new AiGeneratedDraftProposal.Result(proposal.operation(),
                    new AiGeneratedDraftProposal.FlowResult(
                            value.definitionId(), value.name(), value.approverMemberIds(),
                            value.revision(), value.updatedAt(), value.published()),
                    null, null);
        }
        var operation = AiModuleGeneratedDraftFacade.Operation.valueOf(
                proposal.operation().name());
        var value = moduleOwner.execute(
                new AiModuleGeneratedDraftFacade.ExecuteRequest(
                        Long.toString(proposal.id()),
                        Long.toString(proposal.sessionId()),
                        Long.toString(proposal.turnId()), proposal.accountId(),
                        proposal.systemId(), proposal.tenantId(), proposal.memberId(),
                        actor.authorizationEpoch(), actor.effectivePermissions(),
                        operation, moduleSealed(proposal.sealedCommand()),
                        idempotencyKey, actor.requestId(), actor.traceId()));
        if (value.operation() != operation) {
            throw new IllegalStateException("Module owner readback is mismatched");
        }
        if (value.report() != null) {
            var source = value.report();
            var expected = proposal.preview().report();
            if (!source.code().equals(expected.code())
                    || !source.dataSourceId().equals(expected.dataSourceId())
                    || !source.outputFieldCodes().equals(expected.outputFieldCodes())) {
                throw new IllegalStateException("Report owner readback is mismatched");
            }
            return new AiGeneratedDraftProposal.Result(proposal.operation(), null,
                    new AiGeneratedDraftProposal.ReportResult(
                            source.reportId(), source.code(), source.name(),
                            source.description(), source.dataSourceId(),
                            source.outputFieldCodes(), source.draftVersion(),
                            source.version(), source.createdAt(), source.updatedAt(),
                            source.published()), null);
        }
        var source = value.printTemplate();
        var expected = proposal.preview().printTemplate();
        if (!source.moduleCode().equals(expected.moduleCode())
                || !source.code().equals(expected.code())
                || !source.fieldCodes().equals(expected.fieldCodes())) {
            throw new IllegalStateException("Print owner readback is mismatched");
        }
        return new AiGeneratedDraftProposal.Result(proposal.operation(), null, null,
                new AiGeneratedDraftProposal.PrintResult(
                        source.templateId(), source.moduleId(), source.moduleCode(),
                        source.code(), source.name(), source.paperSize(),
                        source.orientation(), source.title(), source.fieldCodes(),
                        source.footer(), source.status(), source.version(),
                        source.updatedAt(), source.published()));
    }

    private void recheckPolicy(AiGeneratedDraftProposal value) {
        repository.activePolicy(value.systemId(), value.tenantId()).filter(policy ->
                policy.id() == value.policyVersionId() && policy.enabled()
                        && policy.providerId() == value.providerId()
                        && policy.providerVersion() == value.providerVersion()
                        && policy.allowedOperations().contains(value.operation().name())
                        && policy.confirmationMode()
                        == AiPolicy.ConfirmationMode.REQUIRED)
                .orElseThrow(() -> conflict(
                        "AI_GENERATED_DRAFT_PERMISSION_DENIED",
                        "Generated-draft proposal exceeds the active policy"));
    }

    private AiGeneratedDraftProposal owned(
            AiActor actor, String sessionId, String proposalId) {
        var parsedSession = positiveId(sessionId, "sessionId");
        var value = repository.generatedDraftProposal(
                actor.systemId(), actor.tenantId(), actor.memberId(), parsedSession,
                positiveId(proposalId, "proposalId")).orElseThrow(() ->
                AiSupport.notFound("Generated-draft proposal does not exist"));
        if (value.accountId() != actor.accountId()
                || repository.session(actor.systemId(), actor.tenantId(),
                actor.memberId(), parsedSession).isEmpty()) {
            throw AiSupport.notFound("Generated-draft proposal does not exist");
        }
        return value;
    }

    private AiGeneratedDraftProposal expire(
            AiActor actor, AiGeneratedDraftProposal value) {
        var now = clock.instant();
        var expired = terminal(
                value, AiGeneratedDraftProposal.State.EXPIRED,
                value.revision() + 1, null, null,
                "AI_GENERATED_DRAFT_EXPIRED", actor, now);
        if (repository.transitionGeneratedDraftProposal(value, expired, event(
                actor, expired, null, "EXPIRED", value.state(), expired.state(),
                expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.sessionId()), Long.toString(value.id()));
    }

    private AiGeneratedDraftProposal.Event event(
            AiActor actor,
            AiGeneratedDraftProposal value,
            Long attemptId,
            String eventType,
            AiGeneratedDraftProposal.State from,
            AiGeneratedDraftProposal.State to,
            String code,
            Instant now) {
        return new AiGeneratedDraftProposal.Event(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), attemptId, eventType, from, to, value.revision(),
                actor.memberId(), actor.requestId(), actor.traceId(), code,
                AiSupport.sha256(value.id() + ":" + value.revision() + ":"
                        + eventType + ":" + code + ":" + actor.memberId()), now);
    }

    private static AiModuleGeneratedDraftFacade.ReportDraft report(
            AiGeneratedDraftPlanParser.Plan value) {
        if (!(value instanceof AiGeneratedDraftPlanParser.ReportPlan report)) return null;
        return new AiModuleGeneratedDraftFacade.ReportDraft(
                report.code(), report.name(), report.description(),
                report.dataSourceId(), report.outputFieldCodes());
    }

    private static AiModuleGeneratedDraftFacade.PrintTemplateDraft print(
            AiGeneratedDraftPlanParser.Plan value) {
        if (!(value instanceof AiGeneratedDraftPlanParser.PrintPlan print)) return null;
        return new AiModuleGeneratedDraftFacade.PrintTemplateDraft(
                print.moduleCode(), print.code(), print.name(), print.paperSize(),
                print.orientation(), print.title(), print.fieldCodes(), print.footer());
    }

    private static AiGeneratedDraftProposal.Preview preview(
            AiModuleGeneratedDraftFacade.DraftPreview value) {
        if (value.report() != null) {
            var source = value.report();
            return new AiGeneratedDraftProposal.Preview(
                    AiGeneratedDraftProposal.Operation.valueOf(value.operation().name()),
                    null, new AiGeneratedDraftProposal.ReportPreview(
                    source.code(), source.name(), source.description(),
                    source.dataSourceId(), source.outputFieldCodes()), null);
        }
        var source = value.printTemplate();
        return new AiGeneratedDraftProposal.Preview(
                AiGeneratedDraftProposal.Operation.valueOf(value.operation().name()),
                null, null, new AiGeneratedDraftProposal.PrintPreview(
                source.moduleCode(), source.code(), source.name(), source.paperSize(),
                source.orientation(), source.title(), source.fieldCodes(),
                source.footer()));
    }

    private static AiGeneratedDraftProposal.Preview redact(
            AiGeneratedDraftProposal.Preview value) {
        if (value.flowDefinition() != null) {
            return new AiGeneratedDraftProposal.Preview(value.operation(),
                    new AiGeneratedDraftProposal.FlowPreview(
                            AiGeneratedDraftProposal.REDACTED_NAME,
                            value.flowDefinition().approverMemberIds()), null, null);
        }
        if (value.report() != null) {
            var source = value.report();
            return new AiGeneratedDraftProposal.Preview(value.operation(), null,
                    new AiGeneratedDraftProposal.ReportPreview(
                            source.code(), AiGeneratedDraftProposal.REDACTED_NAME,
                            source.description() == null ? null
                                    : AiGeneratedDraftProposal.REDACTED_DESCRIPTION,
                            source.dataSourceId(), source.outputFieldCodes()), null);
        }
        var source = value.printTemplate();
        return new AiGeneratedDraftProposal.Preview(value.operation(), null, null,
                new AiGeneratedDraftProposal.PrintPreview(
                        source.moduleCode(), source.code(),
                        AiGeneratedDraftProposal.REDACTED_NAME, source.paperSize(),
                        source.orientation(), AiGeneratedDraftProposal.REDACTED_TITLE,
                        source.fieldCodes(), source.footer() == null ? null
                        : AiGeneratedDraftProposal.REDACTED_FOOTER));
    }

    private static AiGeneratedDraftProposal.Result redact(
            AiGeneratedDraftProposal.Result value) {
        if (value.flowDefinition() != null) {
            var source = value.flowDefinition();
            return new AiGeneratedDraftProposal.Result(value.operation(),
                    new AiGeneratedDraftProposal.FlowResult(
                            source.definitionId(), AiGeneratedDraftProposal.REDACTED_NAME,
                            source.approverMemberIds(), source.revision(),
                            source.updatedAt(), source.published()), null, null);
        }
        if (value.report() != null) {
            var source = value.report();
            return new AiGeneratedDraftProposal.Result(value.operation(), null,
                    new AiGeneratedDraftProposal.ReportResult(
                            source.reportId(), source.code(),
                            AiGeneratedDraftProposal.REDACTED_NAME,
                            source.description() == null ? null
                                    : AiGeneratedDraftProposal.REDACTED_DESCRIPTION,
                            source.dataSourceId(), source.outputFieldCodes(),
                            source.draftVersion(), source.version(), source.createdAt(),
                            source.updatedAt(), source.published()), null);
        }
        var source = value.printTemplate();
        return new AiGeneratedDraftProposal.Result(value.operation(), null, null,
                new AiGeneratedDraftProposal.PrintResult(
                        source.templateId(), source.moduleId(), source.moduleCode(),
                        source.code(), AiGeneratedDraftProposal.REDACTED_NAME,
                        source.paperSize(), source.orientation(),
                        AiGeneratedDraftProposal.REDACTED_TITLE, source.fieldCodes(),
                        source.footer() == null ? null
                                : AiGeneratedDraftProposal.REDACTED_FOOTER,
                        source.status(), source.version(), source.updatedAt(),
                        source.published()));
    }

    private static AiGeneratedDraftProposal copyClarification(
            AiGeneratedDraftProposal value, String clarification) {
        return new AiGeneratedDraftProposal(
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

    private static AiGeneratedDraftProposal terminal(
            AiGeneratedDraftProposal value,
            AiGeneratedDraftProposal.State state,
            long revision,
            Long actedBy,
            AiGeneratedDraftProposal.Result result,
            String resultCode,
            AiActor actor,
            Instant now) {
        return copy(value, state, revision, value.preview(), actedBy, result,
                resultCode, actor.requestId(), actor.traceId(), now, now);
    }

    private static AiGeneratedDraftProposal copy(
            AiGeneratedDraftProposal value,
            AiGeneratedDraftProposal.State state,
            long revision,
            AiGeneratedDraftProposal.Preview preview,
            Long actedBy,
            AiGeneratedDraftProposal.Result result,
            String resultCode,
            String requestId,
            String traceId,
            Instant updatedAt,
            Instant finishedAt) {
        return new AiGeneratedDraftProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.operation(),
                value.planHash(), state, revision, preview, value.confidence(),
                value.clarification(), value.sealedCommand(), value.expiresAt(),
                actedBy, result, resultCode, requestId, traceId, value.createdAt(),
                updatedAt, finishedAt);
    }

    private static AiGeneratedDraftProposal.Attempt finishedAttempt(
            AiGeneratedDraftProposal.Attempt value,
            AiGeneratedDraftProposal.State state,
            String resultHash,
            String resultCode,
            Instant now) {
        return new AiGeneratedDraftProposal.Attempt(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), state, resultHash, resultCode,
                value.createdAt(), now);
    }

    private static AiGeneratedDraftProposal.SealedCommand sealed(
            AiFlowDefinitionDraftFacade.SealedCommand value) {
        return new AiGeneratedDraftProposal.SealedCommand(
                value.ciphertext(), value.encryptionKeyVersion(), value.commandSha256());
    }

    private static AiGeneratedDraftProposal.SealedCommand sealed(
            AiModuleGeneratedDraftFacade.SealedCommand value) {
        return new AiGeneratedDraftProposal.SealedCommand(
                value.ciphertext(), value.encryptionKeyVersion(), value.commandSha256());
    }

    private static AiFlowDefinitionDraftFacade.SealedCommand flowSealed(
            AiGeneratedDraftProposal.SealedCommand value) {
        return new AiFlowDefinitionDraftFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static AiModuleGeneratedDraftFacade.SealedCommand moduleSealed(
            AiGeneratedDraftProposal.SealedCommand value) {
        return new AiModuleGeneratedDraftFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static AiModuleGeneratedDraftFacade.Operation moduleOperation(
            AiGeneratedDraftPlanParser.Operation value) {
        return AiModuleGeneratedDraftFacade.Operation.valueOf(value.name());
    }

    private static AiGeneratedDraftProposal.Operation operation(
            AiGeneratedDraftPlanParser.Operation value) {
        return AiGeneratedDraftProposal.Operation.valueOf(value.name());
    }

    private static String resultHash(AiGeneratedDraftProposal.Result value) {
        if (value.flowDefinition() != null) {
            return AiSupport.sha256(value.operation() + ":"
                    + value.flowDefinition().definitionId() + ":"
                    + value.flowDefinition().revision());
        }
        if (value.report() != null) {
            return AiSupport.sha256(value.operation() + ":"
                    + value.report().reportId() + ":" + value.report().version());
        }
        return AiSupport.sha256(value.operation() + ":"
                + value.printTemplate().templateId() + ":"
                + value.printTemplate().version());
    }

    private static AiGeneratedDraftProposal.State terminalState(String code) {
        if (code.contains("EXPIRED")) {
            return AiGeneratedDraftProposal.State.EXPIRED;
        }
        if (code.contains("FORBIDDEN") || code.contains("PERMISSION")
                || code.contains("DENIED")) {
            return AiGeneratedDraftProposal.State.PERMISSION_DENIED;
        }
        if (!code.contains("COMMAND_")
                && (code.contains("STALE") || code.contains("CONFLICT")
                || code.contains("INVALID") || code.contains("NOT_FOUND")
                || code.contains("STATE") || code.contains("EXISTS")
                || code.contains("UNAVAILABLE") || code.contains("HIDDEN"))) {
            return AiGeneratedDraftProposal.State.STALE;
        }
        return AiGeneratedDraftProposal.State.FAILED;
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
                    "AI generated-draft proposal bindings are inconsistent");
        }
    }

    private static void requireRevision(
            AiGeneratedDraftProposal value, long expectedRevision) {
        if (expectedRevision < 0 || value.revision() != expectedRevision) {
            throw conflict("AI_GENERATED_DRAFT_VERSION_CONFLICT",
                    "Generated-draft proposal revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid("AI_GENERATED_DRAFT_IDEMPOTENCY_KEY_INVALID",
                    "Generated-draft proposal Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "AI_GENERATED_DRAFT_OWNER_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid("AI_GENERATED_DRAFT_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    private static BusinessException conflict(String code, String message) {
        return AiSupport.conflict(code, message);
    }

    private record PreparedOwner(
            AiGeneratedDraftProposal.Preview preview,
            AiGeneratedDraftProposal.SealedCommand sealedCommand,
            Instant expiresAt) {
        private PreparedOwner {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(sealedCommand, "sealedCommand");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    public record PreparedProposal(
            AiGeneratedDraftProposal liveProposal,
            AiGeneratedDraftProposal storedProposal,
            AiGeneratedDraftProposal.Event event) {
        public PreparedProposal {
            Objects.requireNonNull(liveProposal, "liveProposal");
            Objects.requireNonNull(storedProposal, "storedProposal");
            Objects.requireNonNull(event, "event");
        }
    }
}
