package com.unique.examine.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiConfigurationArtifactProposal;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiConfigurationArtifactPlanParser;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared confirmation state machine for the bounded configuration artifacts. */
@Service
public final class AiConfigurationArtifactProposalService {
    private final AiRepository repository;
    private final AiConfigurationArtifactFacade owner;
    private final IdService ids;
    private final Clock clock;

    public AiConfigurationArtifactProposalService(
            AiRepository repository,
            AiConfigurationArtifactFacade owner,
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
            AiConfigurationArtifactPlanParser.Plan plan) {
        requireBindings(actor, turn, policy);
        if (!policy.allowedOperations().contains(plan.operation().name())
                || policy.confirmationMode() != AiPolicy.ConfirmationMode.REQUIRED) {
            throw AiSupport.invalid("AI_CONFIG_ARTIFACT_POLICY_DENIED",
                    "AI configuration artifact is not allowed by policy");
        }
        var now = clock.instant();
        var id = ids.nextId();
        var operation = ownerOperation(plan.operation());
        if (!plan.actionable()) {
            var live = new AiConfigurationArtifactProposal(
                    id, actor.accountId(), actor.systemId(), actor.tenantId(),
                    actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                    policy.providerId(), policy.providerVersion(),
                    actor.authorizationEpoch(), policy.promptVersion(), operation,
                    null, plan.planHash(),
                    AiConfigurationArtifactProposal.State.CLARIFICATION_REQUIRED,
                    0, null, plan.confidence(), plan.clarification(), null,
                    now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                    null, null, "AI_CONFIG_ARTIFACT_CLARIFICATION_REQUIRED",
                    actor.requestId(), actor.traceId(), now, now, null);
            var stored = copyClarification(
                    live, AiConfigurationArtifactProposal.REDACTED_CLARIFICATION);
            return new PreparedProposal(live, stored, event(actor, stored, null,
                    "CLARIFICATION_REQUIRED", null, stored.state(),
                    stored.resultCode(), now));
        }
        var selection = ownerSelection(plan.selection());
        var page = ownerPage(plan.page());
        var filterScenario = ownerFilterScenario(plan.filterScenario());
        var fieldPermissionStage = ownerFieldPermissionStage(
                plan.fieldPermissionStage());
        var prepared = owner.prepare(new AiConfigurationArtifactFacade.PrepareRequest(
                Long.toString(id), Long.toString(turn.sessionId()),
                Long.toString(turn.id()), actor.accountId(), actor.systemId(),
                actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                actor.effectivePermissions(), plan.moduleCode(), operation,
                selection, page, filterScenario, fieldPermissionStage,
                Long.toString(policy.id()),
                Long.toString(policy.providerId()), policy.providerVersion(),
                policy.promptVersion(), actor.requestId(), actor.traceId()));
        assertPrepared(plan, selection, page, filterScenario,
                fieldPermissionStage, prepared.preview());
        var latestAllowed = now.plus(
                policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS);
        if (!prepared.expiresAt().isAfter(now)) {
            throw AiSupport.conflict("AI_CONFIG_ARTIFACT_PREPARE_EXPIRED",
                    "Configuration owner returned an invalid artifact expiry");
        }
        var proposalExpiresAt = prepared.expiresAt().isBefore(latestAllowed)
                ? prepared.expiresAt() : latestAllowed;
        var sealed = new AiConfigurationArtifactProposal.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                prepared.sealedCommand().commandSha256());
        var live = proposal(actor, turn, policy, plan, proposalExpiresAt,
                preview(prepared.preview(), false), sealed, id, now);
        var stored = copy(live, live.state(), live.revision(),
                preview(prepared.preview(), true), null, null, live.resultCode(),
                live.requestId(), live.traceId(), now, null);
        return new PreparedProposal(live, stored, event(actor, stored, null,
                "PROPOSED", null, stored.state(), stored.resultCode(), now));
    }

    public AiConfigurationArtifactProposal get(
            AiActor actor, String sessionId, String proposalId) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiConfigurationArtifactProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) return expire(actor, value);
        return value;
    }

    public AiConfigurationArtifactProposal reject(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision) {
        var value = owned(actor, sessionId, proposalId);
        if (value.state() == AiConfigurationArtifactProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) return expire(actor, value);
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfigurationArtifactProposal.State.PENDING) {
            throw conflict("AI_CONFIG_ARTIFACT_STATE_CONFLICT",
                    "Configuration artifact proposal is no longer pending");
        }
        var now = clock.instant();
        var rejected = terminal(value, AiConfigurationArtifactProposal.State.REJECTED,
                value.revision() + 1, actor.memberId(), null,
                "AI_CONFIG_ARTIFACT_REJECTED", actor, now);
        if (!repository.transitionConfigurationArtifactProposal(value, rejected,
                event(actor, rejected, null, "REJECTED", value.state(),
                        rejected.state(), rejected.resultCode(), now))) {
            throw conflict("AI_CONFIG_ARTIFACT_VERSION_CONFLICT",
                    "Configuration artifact proposal revision changed");
        }
        return rejected;
    }

    public AiConfigurationArtifactProposal confirm(
            AiActor actor, String sessionId, String proposalId,
            long expectedRevision, String idempotencyKey) {
        var key = requiredKey(idempotencyKey);
        var value = owned(actor, sessionId, proposalId);
        var requestHash = AiSupport.sha256(value.id() + ":"
                + expectedRevision + ":" + value.operation() + ":CONFIRM");
        var replay = repository.configurationArtifactAttempt(
                actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIG_ARTIFACT_REPLAY_CONFLICT",
                        "Configuration artifact key was used for another request");
            }
            if (value.state() != AiConfigurationArtifactProposal.State.EXECUTING) {
                return value;
            }
            return execute(actor, value, replay.get());
        }
        if (value.state() == AiConfigurationArtifactProposal.State.PENDING
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_CONFIG_ARTIFACT_EXPIRED",
                    "Configuration artifact proposal has expired");
        }
        requireRevision(value, expectedRevision);
        if (value.state() != AiConfigurationArtifactProposal.State.PENDING) {
            throw conflict("AI_CONFIG_ARTIFACT_STATE_CONFLICT",
                    "Configuration artifact proposal is no longer pending");
        }
        recheckPolicy(value);
        var now = clock.instant();
        var executing = copy(value, AiConfigurationArtifactProposal.State.EXECUTING,
                value.revision() + 1, value.preview(), actor.memberId(), null,
                "AI_CONFIG_ARTIFACT_EXECUTING", actor.requestId(), actor.traceId(),
                now, null);
        var attempt = new AiConfigurationArtifactProposal.Attempt(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), "CONFIRM", key, requestHash,
                AiConfigurationArtifactProposal.State.EXECUTING, null,
                "AI_CONFIG_ARTIFACT_EXECUTING", now, null);
        var claim = repository.claimConfigurationArtifactProposal(
                value, executing, attempt, event(actor, executing, attempt.id(),
                "CONFIRMING", value.state(), executing.state(),
                executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.configurationArtifactAttempt(
                    actor.systemId(), actor.tenantId(), value.id(), "CONFIRM", key);
            if (concurrent.isPresent()
                    && !concurrent.get().requestHash().equals(requestHash)) {
                throw conflict("AI_CONFIG_ARTIFACT_REPLAY_CONFLICT",
                        "Configuration artifact key was used for another request");
            }
            if (concurrent.isPresent()) return execute(actor,
                    owned(actor, sessionId, proposalId), concurrent.get());
            throw conflict("AI_CONFIG_ARTIFACT_VERSION_CONFLICT",
                    "Configuration artifact proposal revision changed");
        }
        return execute(actor, executing, attempt);
    }

    private AiConfigurationArtifactProposal execute(
            AiActor actor, AiConfigurationArtifactProposal executing,
            AiConfigurationArtifactProposal.Attempt attempt) {
        if (executing.state() != AiConfigurationArtifactProposal.State.EXECUTING
                || attempt.status() != AiConfigurationArtifactProposal.State.EXECUTING) {
            return executing;
        }
        var now = clock.instant();
        final AiConfigurationArtifactFacade.ArtifactReadback ownerResult;
        try {
            recheckPolicy(executing);
            ownerResult = owner.execute(new AiConfigurationArtifactFacade.ExecuteRequest(
                    Long.toString(executing.id()), Long.toString(executing.sessionId()),
                    Long.toString(executing.turnId()), actor.accountId(), actor.systemId(),
                    actor.tenantId(), actor.memberId(), actor.authorizationEpoch(),
                    actor.effectivePermissions(), executing.preview().configRootId(),
                    executing.preview().moduleId(), executing.moduleCode(),
                    executing.preview().expectedDraftRevision(),
                    sealed(executing.sealedCommand()), attempt.requestKey(),
                    actor.requestId(), actor.traceId()));
            assertResult(executing, ownerResult);
        } catch (RuntimeException failure) {
            var code = failure instanceof BusinessException business
                    ? business.code() : "AI_CONFIG_ARTIFACT_OWNER_FAILED";
            var failed = terminal(executing,
                    AiConfigurationArtifactProposal.State.FAILED,
                    executing.revision() + 1, actor.memberId(), null,
                    safeCode(code), actor, now);
            var finishedAttempt = finishedAttempt(attempt,
                    AiConfigurationArtifactProposal.State.FAILED,
                    AiSupport.sha256(failed.resultCode()), failed.resultCode(), now);
            if (!repository.finishConfigurationArtifactProposal(
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
                AiConfigurationArtifactProposal.State.SUCCEEDED,
                executing.revision() + 1, actor.memberId(), result, "OK", actor, now);
        var finishedAttempt = finishedAttempt(attempt,
                AiConfigurationArtifactProposal.State.SUCCEEDED,
                resultHash(result), "OK", now);
        if (!repository.finishConfigurationArtifactProposal(
                executing, succeeded, finishedAttempt, event(actor, succeeded,
                attempt.id(), "SUCCEEDED", executing.state(), succeeded.state(),
                "OK", now))) {
            return owned(actor, Long.toString(executing.sessionId()),
                    Long.toString(executing.id()));
        }
        return succeeded;
    }

    private void recheckPolicy(AiConfigurationArtifactProposal value) {
        repository.activePolicy(value.systemId(), value.tenantId()).filter(policy ->
                policy.id() == value.policyVersionId() && policy.enabled()
                        && policy.providerId() == value.providerId()
                        && policy.providerVersion() == value.providerVersion()
                        && policy.allowedOperations().contains(value.operation().name())
                        && policy.allowedModuleCodes().contains(value.moduleCode())
                        && policy.confirmationMode() == AiPolicy.ConfirmationMode.REQUIRED)
                .orElseThrow(() -> conflict("AI_CONFIG_ARTIFACT_POLICY_CHANGED",
                        "Configuration artifact exceeds the active policy"));
    }

    private AiConfigurationArtifactProposal owned(
            AiActor actor, String sessionId, String proposalId) {
        var parsedSession = positiveId(sessionId, "sessionId");
        var value = repository.configurationArtifactProposal(
                actor.systemId(), actor.tenantId(), actor.memberId(), parsedSession,
                positiveId(proposalId, "proposalId")).orElseThrow(() ->
                AiSupport.notFound("Configuration artifact proposal does not exist"));
        if (value.accountId() != actor.accountId()
                || repository.session(actor.systemId(), actor.tenantId(),
                actor.memberId(), parsedSession).isEmpty()) {
            throw AiSupport.notFound("Configuration artifact proposal does not exist");
        }
        return value;
    }

    private AiConfigurationArtifactProposal expire(
            AiActor actor, AiConfigurationArtifactProposal value) {
        var now = clock.instant();
        var expired = terminal(value, AiConfigurationArtifactProposal.State.EXPIRED,
                value.revision() + 1, null, null,
                "AI_CONFIG_ARTIFACT_EXPIRED", actor, now);
        if (repository.transitionConfigurationArtifactProposal(value, expired,
                event(actor, expired, null, "EXPIRED", value.state(),
                        expired.state(), expired.resultCode(), now))) return expired;
        return owned(actor, Long.toString(value.sessionId()), Long.toString(value.id()));
    }

    private AiConfigurationArtifactProposal.Event event(
            AiActor actor, AiConfigurationArtifactProposal value, Long attemptId,
            String eventType, AiConfigurationArtifactProposal.State from,
            AiConfigurationArtifactProposal.State to, String code, Instant now) {
        return new AiConfigurationArtifactProposal.Event(
                ids.nextId(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.id(), attemptId, eventType, from, to, value.revision(),
                actor.memberId(), actor.requestId(), actor.traceId(), code,
                AiSupport.sha256(value.id() + ":" + value.revision() + ":"
                        + eventType + ":" + code + ":" + actor.memberId()), now);
    }

    private static AiConfigurationArtifactProposal proposal(
            AiActor actor, AiConversation.Turn turn, AiPolicy.Version policy,
            AiConfigurationArtifactPlanParser.Plan plan,
            Instant expiresAt,
            AiConfigurationArtifactProposal.Preview preview,
            AiConfigurationArtifactProposal.SealedCommand sealed,
            long id, Instant now) {
        return new AiConfigurationArtifactProposal(
                id, actor.accountId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), turn.sessionId(), turn.id(), policy.id(),
                policy.providerId(), policy.providerVersion(), actor.authorizationEpoch(),
                policy.promptVersion(), ownerOperation(plan.operation()),
                plan.moduleCode(), plan.planHash(),
                AiConfigurationArtifactProposal.State.PENDING, 0, preview,
                plan.confidence(), null, sealed, expiresAt, null, null,
                "AI_CONFIG_ARTIFACT_PENDING", actor.requestId(), actor.traceId(),
                now, now, null);
    }

    private static AiConfigurationArtifactFacade.SelectionFieldDraft ownerSelection(
            AiConfigurationArtifactPlanParser.SelectionDraft value) {
        if (value == null) return null;
        return new AiConfigurationArtifactFacade.SelectionFieldDraft(
                value.fieldCode(), value.fieldName(),
                AiConfigurationArtifactFacade.SelectionType.valueOf(
                        value.fieldType().name()), value.required(),
                value.dictionaryCode(), value.dictionaryName(),
                value.options().stream().map(option ->
                        new AiConfigurationArtifactFacade.OptionDraft(
                                option.code(), option.label(), option.semanticKey(),
                                option.color(), option.defaultOption())).toList(),
                value.maxSelections());
    }

    private static AiConfigurationArtifactFacade.PageLayoutDraft ownerPage(
            AiConfigurationArtifactPlanParser.PageDraft value) {
        if (value == null) return null;
        var layout = value.layout();
        return new AiConfigurationArtifactFacade.PageLayoutDraft(
                value.pageCode(), AiConfigurationArtifactFacade.PageType.valueOf(
                value.pageType().name()), new AiConfigurationArtifactFacade.PageLayout(
                layout.columns(), layout.gap(),
                AiConfigurationArtifactFacade.LabelPosition.valueOf(
                        layout.labelPosition().name()),
                AiConfigurationArtifactFacade.Density.valueOf(layout.density().name()),
                layout.stickyActions(), layout.pageSize(), layout.searchEnabled(),
                layout.filterEnabled(), layout.sections().stream().map(section ->
                new AiConfigurationArtifactFacade.PageSection(
                        section.code(), section.title(), section.fieldCodes())).toList()));
    }

    private static AiConfigurationArtifactFacade.FilterScenarioDraft ownerFilterScenario(
            AiConfigurationArtifactPlanParser.FilterScenarioDraft value) {
        if (value == null) return null;
        return new AiConfigurationArtifactFacade.FilterScenarioDraft(
                value.pageCode(), new AiConfigurationArtifactFacade.FilterScenario(
                value.scenarioCode(), value.scenarioName(), value.filter(),
                value.sort()), value.makeDefault());
    }

    private static AiConfigurationArtifactFacade.FieldPermissionStageDraft
            ownerFieldPermissionStage(
            AiConfigurationArtifactPlanParser.FieldPermissionStageDraft value) {
        if (value == null) return null;
        return new AiConfigurationArtifactFacade.FieldPermissionStageDraft(
                value.fieldCode(), value.stageRead(), value.stageWrite());
    }

    private static void assertPrepared(
            AiConfigurationArtifactPlanParser.Plan plan,
            AiConfigurationArtifactFacade.SelectionFieldDraft selection,
            AiConfigurationArtifactFacade.PageLayoutDraft page,
            AiConfigurationArtifactFacade.FilterScenarioDraft filterScenario,
            AiConfigurationArtifactFacade.FieldPermissionStageDraft fieldPermissionStage,
            AiConfigurationArtifactFacade.ArtifactPreview preview) {
        if (preview.operation() != ownerOperation(plan.operation())
                || !preview.moduleCode().equals(plan.moduleCode())) {
            throw new IllegalStateException("Configuration owner preview is mismatched");
        }
        if (selection != null && (preview.selectionField() == null
                || !preview.selectionField().draft().equals(selection))) {
            throw new IllegalStateException("Configuration selection preview is mismatched");
        }
        if (page != null && (preview.pageLayout() == null
                || !preview.pageLayout().pageCode().equals(page.pageCode())
                || preview.pageLayout().pageType() != page.pageType()
                || !preview.pageLayout().layout().equals(page.layout()))) {
            throw new IllegalStateException("Configuration page preview is mismatched");
        }
        if (filterScenario != null && (preview.filterScenario() == null
                || !preview.filterScenario().pageCode().equals(
                        filterScenario.pageCode())
                || preview.filterScenario().makeDefault()
                        != filterScenario.makeDefault()
                || !preview.filterScenario().scenario().equals(
                        filterScenario.scenario()))) {
            throw new IllegalStateException(
                    "Configuration filter scenario preview is mismatched");
        }
        if (fieldPermissionStage != null
                && (preview.fieldPermissionStage() == null
                || !preview.fieldPermissionStage().fieldCode().equals(
                        fieldPermissionStage.fieldCode())
                || preview.fieldPermissionStage().stageRead()
                        != fieldPermissionStage.stageRead()
                || preview.fieldPermissionStage().stageWrite()
                        != fieldPermissionStage.stageWrite())) {
            throw new IllegalStateException(
                    "Configuration field permission preview is mismatched");
        }
    }

    private static void assertResult(
            AiConfigurationArtifactProposal proposal,
            AiConfigurationArtifactFacade.ArtifactReadback value) {
        if (value.operation() != proposal.operation()
                || !value.configRootId().equals(proposal.preview().configRootId())
                || !value.moduleId().equals(proposal.preview().moduleId())
                || !value.moduleCode().equals(proposal.moduleCode())
                || value.draftRevision() != proposal.preview().nextDraftRevision()) {
            throw new IllegalStateException("Configuration artifact readback is mismatched");
        }
        switch (value.operation()) {
            case CONFIG_SELECTION_FIELD_DRAFT -> {
                if (value.selectionField() == null) mismatchedReadback();
            }
            case CONFIG_PAGE_LAYOUT_DRAFT -> {
                if (value.pageLayout() == null
                        || !value.pageLayout().pageId().equals(
                                proposal.preview().pageLayout().pageId())
                        || !value.pageLayout().pageCode().equals(
                                proposal.preview().pageLayout().pageCode())) {
                    mismatchedReadback();
                }
            }
            case CONFIG_FILTER_SCENARIO_DRAFT -> {
                if (value.filterScenario() == null
                        || !value.filterScenario().pageId().equals(
                                proposal.preview().filterScenario().pageId())
                        || !value.filterScenario().pageCode().equals(
                                proposal.preview().filterScenario().pageCode())) {
                    mismatchedReadback();
                }
            }
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT -> {
                if (value.fieldPermissionStage() == null
                        || !value.fieldPermissionStage().fieldId().equals(
                                proposal.preview().fieldPermissionStage().fieldId())
                        || !value.fieldPermissionStage().fieldCode().equals(
                                proposal.preview().fieldPermissionStage().fieldCode())) {
                    mismatchedReadback();
                }
            }
        }
    }

    private static void mismatchedReadback() {
        throw new IllegalStateException("Configuration artifact readback is mismatched");
    }

    private static AiConfigurationArtifactProposal.Preview preview(
            AiConfigurationArtifactFacade.ArtifactPreview value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.Preview(
                value.operation(), value.configRootId(), value.moduleId(),
                value.moduleCode(), value.expectedDraftRevision(),
                value.nextDraftRevision(), value.selectionField() == null ? null
                : selectionPreview(value.selectionField(), redacted),
                value.pageLayout() == null ? null
                        : pagePreview(value.pageLayout(), redacted),
                value.filterScenario() == null ? null
                        : filterScenarioPreview(value.filterScenario(), redacted),
                value.fieldPermissionStage() == null ? null
                        : fieldPermissionStagePreview(
                                value.fieldPermissionStage(), redacted));
    }

    private static AiConfigurationArtifactProposal.SelectionPreview selectionPreview(
            AiConfigurationArtifactFacade.SelectionFieldPreview value,
            boolean redacted) {
        var draft = value.draft();
        var options = new ArrayList<AiConfigurationArtifactProposal.Option>();
        for (var index = 0; index < draft.options().size(); index++) {
            var option = draft.options().get(index);
            options.add(new AiConfigurationArtifactProposal.Option(
                    option.code(), redacted
                    ? AiConfigurationArtifactProposal.REDACTED_LABEL
                    : option.label(), option.semanticKey(), option.color(),
                    option.defaultOption(), index * 10));
        }
        return new AiConfigurationArtifactProposal.SelectionPreview(
                value.sortOrder(), draft.fieldCode(), redacted
                ? AiConfigurationArtifactProposal.REDACTED_NAME : draft.fieldName(),
                draft.fieldType().name(), draft.required(), draft.dictionaryCode(),
                redacted ? AiConfigurationArtifactProposal.REDACTED_NAME
                        : draft.dictionaryName(), options, draft.maxSelections());
    }

    private static AiConfigurationArtifactProposal.PagePreview pagePreview(
            AiConfigurationArtifactFacade.PageLayoutPreview value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.PagePreview(
                value.pageId(), value.pageCode(), value.pageType().name(),
                value.pageVersion(), layout(value.layout(), redacted));
    }

    private static AiConfigurationArtifactProposal.FilterScenarioPreview
            filterScenarioPreview(
            AiConfigurationArtifactFacade.FilterScenarioPreview value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.FilterScenarioPreview(
                value.pageId(), value.pageCode(), value.pageVersion(),
                value.makeDefault(), filterScenario(value.scenario(), redacted),
                filterScenarioState(value.resolvedState(), redacted),
                redacted ? redactFilterValues(value.resolvedLayout())
                        : value.resolvedLayout());
    }

    private static AiConfigurationArtifactProposal.FieldPermissionStagePreview
            fieldPermissionStagePreview(
            AiConfigurationArtifactFacade.FieldPermissionStagePreview value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.FieldPermissionStagePreview(
                value.fieldId(), value.fieldCode(), redacted
                ? AiConfigurationArtifactProposal.REDACTED_NAME
                : value.fieldName(), value.fieldVersion(), value.stageRead(),
                value.stageWrite(), value.expectedReadPermissionMode().name(),
                value.expectedWritePermissionMode().name(),
                value.readPermissionMode().name(),
                value.writePermissionMode().name(), value.readPermissionCode(),
                value.writePermissionCode());
    }

    private static AiConfigurationArtifactProposal.FilterScenario filterScenario(
            AiConfigurationArtifactFacade.FilterScenario value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.FilterScenario(
                value.code(), redacted
                ? AiConfigurationArtifactProposal.REDACTED_NAME : value.name(),
                redacted && value.filter() != null
                        ? redactFilterValues(value.filter()) : value.filter(),
                value.sort());
    }

    private static AiConfigurationArtifactProposal.FilterScenarioState
            filterScenarioState(
            AiConfigurationArtifactFacade.FilterScenarioState value,
            boolean redacted) {
        return new AiConfigurationArtifactProposal.FilterScenarioState(
                value.filterScenarios().stream().map(scenario ->
                        filterScenario(scenario, redacted)).toList(),
                value.defaultFilterScenarioCode());
    }

    private static AiConfigurationArtifactProposal.Layout layout(
            AiConfigurationArtifactFacade.PageLayout value, boolean redacted) {
        var sectionCount = value.sections().size();
        var fieldCount = value.sections().stream()
                .mapToInt(section -> section.fieldCodes().size()).sum();
        List<AiConfigurationArtifactProposal.Section> sections = List.of();
        if (!redacted) {
            var result = new ArrayList<AiConfigurationArtifactProposal.Section>();
            for (var index = 0; index < value.sections().size(); index++) {
                var section = value.sections().get(index);
                result.add(new AiConfigurationArtifactProposal.Section(
                        section.code(), section.title(), section.fieldCodes(), index * 10));
            }
            sections = List.copyOf(result);
        }
        return new AiConfigurationArtifactProposal.Layout(
                value.columns(), value.gap(), value.labelPosition().name(),
                value.density().name(), value.stickyActions(), value.pageSize(),
                value.searchEnabled(), value.filterEnabled(), sections,
                sectionCount, fieldCount, redacted);
    }

    private static AiConfigurationArtifactProposal.Result result(
            AiConfigurationArtifactFacade.ArtifactReadback value) {
        AiConfigurationArtifactProposal.SelectionResult selection = null;
        AiConfigurationArtifactProposal.PageResult page = null;
        AiConfigurationArtifactProposal.FilterScenarioResult filterScenario = null;
        AiConfigurationArtifactProposal.FieldPermissionStageResult
                fieldPermissionStage = null;
        if (value.selectionField() != null) {
            var source = value.selectionField();
            var dictionary = source.dictionary();
            var field = source.field();
            selection = new AiConfigurationArtifactProposal.SelectionResult(
                    new AiConfigurationArtifactProposal.DictionaryResult(
                            dictionary.dictionaryId(), dictionary.dictionaryCode(),
                            AiConfigurationArtifactProposal.REDACTED_NAME,
                            dictionary.version()), source.options().stream().map(option ->
                    new AiConfigurationArtifactProposal.OptionResult(
                            option.optionId(), option.code(),
                            AiConfigurationArtifactProposal.REDACTED_LABEL,
                            option.semanticKey(), option.color(), option.defaultOption(),
                            option.sortOrder(), option.version())).toList(),
                    field.fieldId(), field.fieldCode(),
                    AiConfigurationArtifactProposal.REDACTED_NAME,
                    field.fieldType().name(), field.required(), field.dictionaryId(),
                    field.sortOrder(), field.maxSelections(), field.version());
        } else if (value.pageLayout() != null) {
            var source = value.pageLayout();
            page = new AiConfigurationArtifactProposal.PageResult(
                    source.pageId(), source.pageCode(), source.pageType().name(),
                    source.version(), layout(source.layout(), true));
        } else if (value.filterScenario() != null) {
            var source = value.filterScenario();
            filterScenario = new AiConfigurationArtifactProposal.FilterScenarioResult(
                    source.pageId(), source.pageCode(), source.pageVersion(),
                    filterScenarioState(source.state(), true));
        } else {
            var source = value.fieldPermissionStage();
            fieldPermissionStage = new AiConfigurationArtifactProposal
                    .FieldPermissionStageResult(
                    source.fieldId(), source.fieldCode(),
                    AiConfigurationArtifactProposal.REDACTED_NAME,
                    source.fieldVersion(), source.readPermissionMode().name(),
                    source.writePermissionMode().name(),
                    source.readPermissionCode(), source.writePermissionCode());
        }
        return new AiConfigurationArtifactProposal.Result(
                value.operation(), value.configRootId(), value.moduleId(),
                value.moduleCode(), value.draftRevision(), selection, page,
                filterScenario, fieldPermissionStage, "DRAFT");
    }

    private static AiConfigurationArtifactFacade.Operation ownerOperation(
            AiConfigurationArtifactPlanParser.Operation value) {
        return AiConfigurationArtifactFacade.Operation.valueOf(value.name());
    }

    private static AiConfigurationArtifactProposal copyClarification(
            AiConfigurationArtifactProposal value, String clarification) {
        return new AiConfigurationArtifactProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.operation(),
                value.moduleCode(), value.planHash(), value.state(), value.revision(),
                value.preview(), value.confidence(), clarification,
                value.sealedCommand(), value.expiresAt(), value.actedBy(),
                value.result(), value.resultCode(), value.requestId(), value.traceId(),
                value.createdAt(), value.updatedAt(), value.finishedAt());
    }

    private static AiConfigurationArtifactProposal terminal(
            AiConfigurationArtifactProposal value,
            AiConfigurationArtifactProposal.State state, long revision,
            Long actedBy, AiConfigurationArtifactProposal.Result result,
            String resultCode, AiActor actor, Instant now) {
        return copy(value, state, revision, value.preview(), actedBy, result,
                resultCode, actor.requestId(), actor.traceId(), now, now);
    }

    private static AiConfigurationArtifactProposal copy(
            AiConfigurationArtifactProposal value,
            AiConfigurationArtifactProposal.State state, long revision,
            AiConfigurationArtifactProposal.Preview preview, Long actedBy,
            AiConfigurationArtifactProposal.Result result, String resultCode,
            String requestId, String traceId, Instant updatedAt, Instant finishedAt) {
        return new AiConfigurationArtifactProposal(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.operation(),
                value.moduleCode(), value.planHash(), state, revision, preview,
                value.confidence(), value.clarification(), value.sealedCommand(),
                value.expiresAt(), actedBy, result, resultCode, requestId, traceId,
                value.createdAt(), updatedAt, finishedAt);
    }

    private static AiConfigurationArtifactProposal.Attempt finishedAttempt(
            AiConfigurationArtifactProposal.Attempt value,
            AiConfigurationArtifactProposal.State state, String resultHash,
            String resultCode, Instant now) {
        return new AiConfigurationArtifactProposal.Attempt(
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), state, resultHash, resultCode,
                value.createdAt(), now);
    }

    private static AiConfigurationArtifactFacade.SealedCommand sealed(
            AiConfigurationArtifactProposal.SealedCommand value) {
        return new AiConfigurationArtifactFacade.SealedCommand(
                value.ciphertext(), value.keyVersion(), value.commandHash());
    }

    private static String resultHash(AiConfigurationArtifactProposal.Result value) {
        var artifactVersion = switch (value.operation()) {
            case CONFIG_SELECTION_FIELD_DRAFT -> value.selectionField().version();
            case CONFIG_PAGE_LAYOUT_DRAFT -> value.pageLayout().version();
            case CONFIG_FILTER_SCENARIO_DRAFT -> value.filterScenario().version();
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT ->
                    value.fieldPermissionStage().version();
        };
        return AiSupport.sha256(value.operation() + ":" + value.configRootId()
                + ":" + value.draftRevision() + ":" + artifactVersion);
    }

    private static JsonNode redactFilterValues(JsonNode source) {
        var copy = Objects.requireNonNull(source, "source").deepCopy();
        redactPredicateValues(copy);
        return copy;
    }

    private static void redactPredicateValues(JsonNode value) {
        if (value instanceof ObjectNode object) {
            if (object.has("code") && object.has("name")
                    && object.has("filter") && object.has("sort")) {
                object.put("name", AiConfigurationArtifactProposal.REDACTED_NAME);
            }
            if (object.has("fieldCode") && object.has("operator")
                    && object.has("value")) {
                object.set("value", redactTextValues(object.get("value")));
            }
            var names = new ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                if (!"value".equals(name)) {
                    redactPredicateValues(object.get(name));
                }
            }
        } else if (value instanceof ArrayNode array) {
            array.forEach(AiConfigurationArtifactProposalService::redactPredicateValues);
        }
    }

    private static JsonNode redactTextValues(JsonNode value) {
        if (value.isTextual()) {
            return TextNode.valueOf(AiConfigurationArtifactProposal.REDACTED_VALUE);
        }
        var copy = value.deepCopy();
        if (copy instanceof ObjectNode object) {
            var names = new ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                object.set(name, redactTextValues(object.get(name)));
            }
        } else if (copy instanceof ArrayNode array) {
            for (var index = 0; index < array.size(); index++) {
                array.set(index, redactTextValues(array.get(index)));
            }
        }
        return copy;
    }

    private static void requireBindings(
            AiActor actor, AiConversation.Turn turn, AiPolicy.Version policy) {
        if (turn.systemId() != actor.systemId() || turn.tenantId() != actor.tenantId()
                || turn.policyVersionId() != policy.id()
                || turn.providerId() != policy.providerId()
                || turn.providerVersion() != policy.providerVersion()
                || turn.authorizationEpoch() != actor.authorizationEpoch()) {
            throw new IllegalArgumentException("AI artifact bindings are inconsistent");
        }
    }

    private static void requireRevision(
            AiConfigurationArtifactProposal value, long expectedRevision) {
        if (expectedRevision < 0 || value.revision() != expectedRevision) {
            throw conflict("AI_CONFIG_ARTIFACT_VERSION_CONFLICT",
                    "Configuration artifact proposal revision is stale");
        }
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid("AI_CONFIG_ARTIFACT_IDEMPOTENCY_KEY_INVALID",
                    "Configuration artifact Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "AI_CONFIG_ARTIFACT_OWNER_FAILED";
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid("AI_CONFIG_ARTIFACT_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    private static BusinessException conflict(String code, String message) {
        return AiSupport.conflict(code, message);
    }

    public record PreparedProposal(
            AiConfigurationArtifactProposal liveProposal,
            AiConfigurationArtifactProposal storedProposal,
            AiConfigurationArtifactProposal.Event event) {
        public PreparedProposal {
            Objects.requireNonNull(liveProposal, "liveProposal");
            Objects.requireNonNull(storedProposal, "storedProposal");
            Objects.requireNonNull(event, "event");
        }
    }
}
