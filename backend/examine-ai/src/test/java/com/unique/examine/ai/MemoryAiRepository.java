package com.unique.examine.ai;

import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiConfirmation;
import com.unique.examine.ai.domain.AiConfigurationFieldProposal;
import com.unique.examine.ai.domain.AiConfigurationArtifactProposal;
import com.unique.examine.ai.domain.AiFillProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.domain.AiWorkProposal;
import com.unique.examine.ai.domain.AiGeneratedDraftProposal;
import com.unique.examine.ai.repository.AiRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MemoryAiRepository implements AiRepository {
    final Map<String, AiProvider> providers = new LinkedHashMap<>();
    final Map<String, AiPolicy.Draft> drafts = new LinkedHashMap<>();
    final Map<Long, AiPolicy.Version> versions = new LinkedHashMap<>();
    final Map<String, AiPolicy.Check> checks = new LinkedHashMap<>();
    final Map<String, AiPolicy.PublishReplay> replays = new LinkedHashMap<>();
    final Map<Long, AiConversation.Session> sessionValues = new LinkedHashMap<>();
    final List<AiConversation.Message> messageValues = new ArrayList<>();
    final Map<Long, AiConversation.Turn> turnValues = new LinkedHashMap<>();
    final List<AiConversation.ToolCall> toolValues = new ArrayList<>();
    final List<AiConversation.Usage> usageValues = new ArrayList<>();
    final Map<Long, AiConfirmation> confirmationValues = new LinkedHashMap<>();
    final Map<String, AiConfirmation.Attempt> confirmationAttempts =
            new LinkedHashMap<>();
    final List<AiConfirmation.Event> confirmationEvents = new ArrayList<>();
    final Map<Long, AiConfigurationFieldProposal> configurationFieldProposals =
            new LinkedHashMap<>();
    final Map<String, AiConfigurationFieldProposal.Attempt> configurationFieldAttempts =
            new LinkedHashMap<>();
    final List<AiConfigurationFieldProposal.Event> configurationFieldEvents =
            new ArrayList<>();
    final Map<Long, AiConfigurationArtifactProposal> configurationArtifactProposals =
            new LinkedHashMap<>();
    final Map<String, AiConfigurationArtifactProposal.Attempt>
            configurationArtifactAttempts = new LinkedHashMap<>();
    final List<AiConfigurationArtifactProposal.Event> configurationArtifactEvents =
            new ArrayList<>();
    final Map<Long, AiWorkProposal> workProposals = new LinkedHashMap<>();
    final Map<String, AiWorkProposal.Attempt> workAttempts = new LinkedHashMap<>();
    final List<AiWorkProposal.Event> workEvents = new ArrayList<>();
    final Map<Long, AiGeneratedDraftProposal> generatedDraftProposals =
            new LinkedHashMap<>();
    final Map<String, AiGeneratedDraftProposal.Attempt> generatedDraftAttempts =
            new LinkedHashMap<>();
    final List<AiGeneratedDraftProposal.Event> generatedDraftEvents =
            new ArrayList<>();
    final Map<Long, AiFillProposal> fillProposals = new LinkedHashMap<>();
    final Map<String, AiFillProposal.Attempt> fillAttempts = new LinkedHashMap<>();
    final List<AiFillProposal.Event> fillEvents = new ArrayList<>();

    private static String scope(long systemId, long tenantId) {
        return systemId + ":" + tenantId;
    }

    private static String providerKey(long systemId, long tenantId, long id) {
        return scope(systemId, tenantId) + ":" + id;
    }

    @Override
    public Optional<AiProvider> provider(long systemId, long tenantId, long providerId) {
        return Optional.ofNullable(providers.get(providerKey(systemId, tenantId, providerId)));
    }

    @Override
    public Optional<AiProvider> providerByCode(long systemId, long tenantId, String code) {
        return providers.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.code().equals(code)).findFirst();
    }

    @Override
    public List<AiProvider> providers(long systemId, long tenantId, int offset, int limit) {
        return providers.values().stream().filter(value ->
                        value.systemId() == systemId && value.tenantId() == tenantId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    public void insertProvider(AiProvider provider) {
        providers.put(providerKey(provider.systemId(), provider.tenantId(), provider.id()), provider);
    }

    @Override
    public boolean updateProvider(AiProvider provider, long expectedVersion) {
        var key = providerKey(provider.systemId(), provider.tenantId(), provider.id());
        var current = providers.get(key);
        if (current == null || current.version() != expectedVersion) return false;
        providers.put(key, provider);
        return true;
    }

    @Override
    public Optional<AiPolicy.Draft> policyDraft(long systemId, long tenantId) {
        return Optional.ofNullable(drafts.get(scope(systemId, tenantId)));
    }

    @Override
    public void insertPolicyDraft(AiPolicy.Draft draft) {
        drafts.put(scope(draft.systemId(), draft.tenantId()), draft);
    }

    @Override
    public boolean updatePolicyDraft(AiPolicy.Draft draft, long expectedRevision) {
        var key = scope(draft.systemId(), draft.tenantId());
        var current = drafts.get(key);
        if (current == null || current.revision() != expectedRevision) return false;
        drafts.put(key, draft);
        return true;
    }

    @Override
    public void insertPolicyCheck(AiPolicy.Check check) {
        checks.put(check.policyId() + ":" + check.draftRevision(), check);
    }

    @Override
    public Optional<AiPolicy.Check> policyCheck(
            long systemId, long tenantId, long policyId, long draftRevision) {
        return Optional.ofNullable(checks.get(policyId + ":" + draftRevision))
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public Optional<AiPolicy.Version> activePolicy(long systemId, long tenantId) {
        return policyDraft(systemId, tenantId).flatMap(draft ->
                draft.activeVersionId() == null ? Optional.empty()
                        : policyVersion(systemId, tenantId, draft.activeVersionId()));
    }

    @Override
    public Optional<AiPolicy.Version> policyVersion(long systemId, long tenantId, long versionId) {
        return Optional.ofNullable(versions.get(versionId)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public int nextPolicyVersionNumber(long systemId, long tenantId, long policyId) {
        return versions.values().stream().filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId && value.policyId() == policyId)
                .mapToInt(AiPolicy.Version::versionNumber).max().orElse(0) + 1;
    }

    @Override
    public Optional<AiPolicy.PublishReplay> publishReplay(
            long systemId, long tenantId, long policyId, String requestKey) {
        return Optional.ofNullable(replays.get(policyId + ":" + requestKey))
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public boolean publishPolicy(
            AiPolicy.Draft expected,
            AiPolicy.Draft published,
            AiPolicy.Version version,
            AiPolicy.PublishReplay replay) {
        var current = drafts.get(scope(expected.systemId(), expected.tenantId()));
        if (current == null || current.revision() != expected.revision()
                || current.status() != expected.status()
                || !current.draftHash().equals(expected.draftHash())) return false;
        drafts.put(scope(published.systemId(), published.tenantId()), published);
        versions.put(version.id(), version);
        replays.put(replay.policyId() + ":" + replay.requestKey(), replay);
        return true;
    }

    @Override
    public void insertSession(AiConversation.Session session) {
        sessionValues.put(session.id(), session);
    }

    @Override
    public Optional<AiConversation.Session> session(
            long systemId, long tenantId, long memberId, long sessionId) {
        return Optional.ofNullable(sessionValues.get(sessionId)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.memberId() == memberId);
    }

    @Override
    public List<AiConversation.Session> sessions(
            long systemId, long tenantId, long memberId, int offset, int limit) {
        return sessionValues.values().stream().filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId && value.memberId() == memberId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    public void insertMessage(AiConversation.Message message) {
        messageValues.add(message);
    }

    @Override
    public void insertTurn(AiConversation.Turn turn) {
        turnValues.put(turn.id(), turn);
    }

    @Override
    public boolean finishTurn(AiConversation.Turn turn) {
        var current = turnValues.get(turn.id());
        if (current == null || current.status() != AiConversation.TurnStatus.RUNNING) return false;
        turnValues.put(turn.id(), turn);
        return true;
    }

    @Override
    public void insertToolCall(AiConversation.ToolCall call) {
        toolValues.add(call);
    }

    @Override
    public void insertUsage(AiConversation.Usage usage) {
        usageValues.add(usage);
    }

    @Override
    public List<AiConversation.Message> messages(
            long systemId, long tenantId, long sessionId, int limit) {
        return messageValues.stream().filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId && value.sessionId() == sessionId)
                .limit(limit).toList();
    }

    @Override
    public List<AiConversation.Turn> turns(
            long systemId, long tenantId, long sessionId, int limit) {
        return turnValues.values().stream().filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId && value.sessionId() == sessionId)
                .limit(limit).toList();
    }

    @Override
    public void insertConfirmation(
            AiConfirmation confirmation, AiConfirmation.Event event) {
        confirmationValues.put(confirmation.id(), confirmation);
        confirmationEvents.add(event);
    }

    @Override
    public Optional<AiConfirmation> confirmation(
            long systemId, long tenantId, long confirmationId) {
        return Optional.ofNullable(confirmationValues.get(confirmationId))
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId);
    }

    @Override
    public Optional<AiConfirmation> confirmationByTurn(
            long systemId, long tenantId, long turnId) {
        return confirmationValues.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.turnId() == turnId).findFirst();
    }

    @Override
    public Optional<AiConfirmation.Attempt> confirmationAttempt(
            long systemId, long tenantId, long confirmationId, String requestKey) {
        return Optional.ofNullable(confirmationAttempts.get(
                confirmationId + ":" + requestKey)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public ClaimResult claimConfirmation(
            AiConfirmation expected, AiConfirmation executing,
            AiConfirmation.Attempt attempt, AiConfirmation.Event event) {
        var key = attempt.confirmationId() + ":" + attempt.requestKey();
        if (confirmationAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = confirmationValues.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        confirmationValues.put(executing.id(), executing);
        confirmationAttempts.put(key, attempt);
        confirmationEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishConfirmation(
            AiConfirmation expected, AiConfirmation terminal,
            AiConfirmation.Attempt terminalAttempt, AiConfirmation.Event event) {
        var current = confirmationValues.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        confirmationValues.put(terminal.id(), terminal);
        confirmationAttempts.put(terminalAttempt.confirmationId() + ":"
                + terminalAttempt.requestKey(), terminalAttempt);
        confirmationEvents.add(event);
        return true;
    }

    @Override
    public boolean rejectConfirmation(
            AiConfirmation expected, AiConfirmation rejected,
            AiConfirmation.Event event) {
        var current = confirmationValues.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        confirmationValues.put(rejected.id(), rejected);
        confirmationEvents.add(event);
        return true;
    }

    @Override
    public boolean expireConfirmation(
            AiConfirmation expected, AiConfirmation expired,
            AiConfirmation.Event event) {
        return rejectConfirmation(expected, expired, event);
    }

    @Override
    public void insertConfigurationFieldProposal(
            AiConfigurationFieldProposal proposal,
            AiConfigurationFieldProposal.Event event) {
        configurationFieldProposals.put(proposal.id(), proposal);
        configurationFieldEvents.add(event);
    }

    @Override
    public Optional<AiConfigurationFieldProposal> configurationFieldProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return Optional.ofNullable(configurationFieldProposals.get(proposalId))
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId
                        && value.memberId() == memberId
                        && value.sessionId() == sessionId);
    }

    @Override
    public Optional<AiConfigurationFieldProposal> configurationFieldProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return configurationFieldProposals.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.turnId() == turnId).findFirst();
    }

    @Override
    public Optional<AiConfigurationFieldProposal.Attempt> configurationFieldAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return Optional.ofNullable(configurationFieldAttempts.get(
                proposalId + ":" + action + ":" + requestKey)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public ClaimResult claimConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal executing,
            AiConfigurationFieldProposal.Attempt attempt,
            AiConfigurationFieldProposal.Event event) {
        var key = configurationFieldAttemptKey(attempt);
        if (configurationFieldAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = configurationFieldProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        configurationFieldProposals.put(executing.id(), executing);
        configurationFieldAttempts.put(key, attempt);
        configurationFieldEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Attempt attempt,
            AiConfigurationFieldProposal.Event event) {
        var current = configurationFieldProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        configurationFieldProposals.put(terminal.id(), terminal);
        configurationFieldAttempts.put(configurationFieldAttemptKey(attempt), attempt);
        configurationFieldEvents.add(event);
        return true;
    }

    @Override
    public boolean transitionConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Event event) {
        var current = configurationFieldProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        configurationFieldProposals.put(terminal.id(), terminal);
        configurationFieldEvents.add(event);
        return true;
    }

    @Override
    public void insertConfigurationArtifactProposal(
            AiConfigurationArtifactProposal proposal,
            AiConfigurationArtifactProposal.Event event) {
        configurationArtifactProposals.put(proposal.id(), proposal);
        configurationArtifactEvents.add(event);
    }

    @Override
    public Optional<AiConfigurationArtifactProposal> configurationArtifactProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return Optional.ofNullable(configurationArtifactProposals.get(proposalId))
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId
                        && value.memberId() == memberId
                        && value.sessionId() == sessionId);
    }

    @Override
    public Optional<AiConfigurationArtifactProposal>
            configurationArtifactProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return configurationArtifactProposals.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.turnId() == turnId).findFirst();
    }

    @Override
    public Optional<AiConfigurationArtifactProposal.Attempt>
            configurationArtifactAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return Optional.ofNullable(configurationArtifactAttempts.get(
                proposalId + ":" + action + ":" + requestKey)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public ClaimResult claimConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal executing,
            AiConfigurationArtifactProposal.Attempt attempt,
            AiConfigurationArtifactProposal.Event event) {
        var key = artifactAttemptKey(attempt);
        if (configurationArtifactAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = configurationArtifactProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        configurationArtifactProposals.put(executing.id(), executing);
        configurationArtifactAttempts.put(key, attempt);
        configurationArtifactEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Attempt attempt,
            AiConfigurationArtifactProposal.Event event) {
        var current = configurationArtifactProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        configurationArtifactProposals.put(terminal.id(), terminal);
        configurationArtifactAttempts.put(artifactAttemptKey(attempt), attempt);
        configurationArtifactEvents.add(event);
        return true;
    }

    @Override
    public boolean transitionConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Event event) {
        var current = configurationArtifactProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        configurationArtifactProposals.put(terminal.id(), terminal);
        configurationArtifactEvents.add(event);
        return true;
    }

    @Override
    public void insertWorkProposal(
            AiWorkProposal proposal, AiWorkProposal.Event event) {
        workProposals.put(proposal.id(), proposal);
        workEvents.add(event);
    }

    @Override
    public Optional<AiWorkProposal> workProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return Optional.ofNullable(workProposals.get(proposalId)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.memberId() == memberId
                        && value.sessionId() == sessionId);
    }

    @Override
    public Optional<AiWorkProposal> workProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return workProposals.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.turnId() == turnId).findFirst();
    }

    @Override
    public Optional<AiWorkProposal.Attempt> workAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return Optional.ofNullable(workAttempts.get(
                proposalId + ":" + action + ":" + requestKey)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public ClaimResult claimWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal executing,
            AiWorkProposal.Attempt attempt,
            AiWorkProposal.Event event) {
        var key = workAttemptKey(attempt);
        if (workAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = workProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        workProposals.put(executing.id(), executing);
        workAttempts.put(key, attempt);
        workEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal terminal,
            AiWorkProposal.Attempt attempt,
            AiWorkProposal.Event event) {
        var current = workProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        workProposals.put(terminal.id(), terminal);
        workAttempts.put(workAttemptKey(attempt), attempt);
        workEvents.add(event);
        return true;
    }

    @Override
    public boolean transitionWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal terminal,
            AiWorkProposal.Event event) {
        var current = workProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        workProposals.put(terminal.id(), terminal);
        workEvents.add(event);
        return true;
    }

    @Override
    public void insertGeneratedDraftProposal(
            AiGeneratedDraftProposal proposal,
            AiGeneratedDraftProposal.Event event) {
        generatedDraftProposals.put(proposal.id(), proposal);
        generatedDraftEvents.add(event);
    }

    @Override
    public Optional<AiGeneratedDraftProposal> generatedDraftProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return Optional.ofNullable(generatedDraftProposals.get(proposalId)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.memberId() == memberId
                        && value.sessionId() == sessionId);
    }

    @Override
    public Optional<AiGeneratedDraftProposal> generatedDraftProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return generatedDraftProposals.values().stream().filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.turnId() == turnId).findFirst();
    }

    @Override
    public Optional<AiGeneratedDraftProposal.Attempt> generatedDraftAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return Optional.ofNullable(generatedDraftAttempts.get(
                proposalId + ":" + action + ":" + requestKey)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public ClaimResult claimGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal executing,
            AiGeneratedDraftProposal.Attempt attempt,
            AiGeneratedDraftProposal.Event event) {
        var key = generatedDraftAttemptKey(attempt);
        if (generatedDraftAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = generatedDraftProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        generatedDraftProposals.put(executing.id(), executing);
        generatedDraftAttempts.put(key, attempt);
        generatedDraftEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Attempt attempt,
            AiGeneratedDraftProposal.Event event) {
        var current = generatedDraftProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        generatedDraftProposals.put(terminal.id(), terminal);
        generatedDraftAttempts.put(generatedDraftAttemptKey(attempt), attempt);
        generatedDraftEvents.add(event);
        return true;
    }

    @Override
    public boolean transitionGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Event event) {
        var current = generatedDraftProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        generatedDraftProposals.put(terminal.id(), terminal);
        generatedDraftEvents.add(event);
        return true;
    }

    @Override
    public Optional<AiFillProposal> fillProposal(
            long systemId, long tenantId, long memberId, String moduleCode,
            String recordId, String fieldCode, long proposalId) {
        return Optional.ofNullable(fillProposals.get(proposalId)).filter(value ->
                value.systemId() == systemId && value.tenantId() == tenantId
                        && value.memberId() == memberId
                        && value.moduleCode().equals(moduleCode)
                        && value.recordId().equals(recordId)
                        && value.fieldCode().equals(fieldCode));
    }

    @Override
    public Optional<AiFillProposal.Attempt> fillAttempt(
            long systemId, long tenantId, long memberId,
            String action, String requestKey) {
        return Optional.ofNullable(fillAttempts.get(
                systemId + ":" + tenantId + ":" + memberId + ":"
                        + action + ":" + requestKey));
    }

    @Override
    public boolean reserveFillAttempt(AiFillProposal.Attempt attempt) {
        var key = fillAttemptKey(attempt);
        if (fillAttempts.containsKey(key)) return false;
        fillAttempts.put(key, attempt);
        return true;
    }

    @Override
    public boolean completeFillAttempt(
            AiFillProposal.Attempt expected, AiFillProposal.Attempt terminal) {
        var key = fillAttemptKey(expected);
        var current = fillAttempts.get(key);
        if (current == null || current.status() != expected.status()
                || !current.requestHash().equals(expected.requestHash())) return false;
        fillAttempts.put(key, terminal);
        return true;
    }

    @Override
    public void insertFillProposal(
            AiFillProposal proposal, AiFillProposal.Attempt attempt,
            AiFillProposal.Event event) {
        fillProposals.put(proposal.id(), proposal);
        fillAttempts.put(fillAttemptKey(attempt), attempt);
        fillEvents.add(event);
    }

    @Override
    public ClaimResult claimFillProposal(
            AiFillProposal expected, AiFillProposal executing,
            AiFillProposal.Attempt attempt, AiFillProposal.Event event) {
        var key = fillAttemptKey(attempt);
        if (fillAttempts.containsKey(key)) return ClaimResult.KEY_EXISTS;
        var current = fillProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        fillProposals.put(executing.id(), executing);
        fillAttempts.put(key, attempt);
        fillEvents.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public boolean finishFillProposal(
            AiFillProposal expected, AiFillProposal terminal,
            AiFillProposal.Attempt attempt, AiFillProposal.Event event) {
        var current = fillProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        fillProposals.put(terminal.id(), terminal);
        fillAttempts.put(fillAttemptKey(attempt), attempt);
        fillEvents.add(event);
        return true;
    }

    @Override
    public boolean expireFillProposal(
            AiFillProposal expected, AiFillProposal expired,
            AiFillProposal.Event event) {
        var current = fillProposals.get(expected.id());
        if (current == null || current.revision() != expected.revision()
                || current.state() != expected.state()) return false;
        fillProposals.put(expired.id(), expired);
        fillEvents.add(event);
        return true;
    }

    private static String fillAttemptKey(AiFillProposal.Attempt value) {
        return value.systemId() + ":" + value.tenantId() + ":" + value.memberId()
                + ":" + value.action() + ":" + value.requestKey();
    }

    private static String configurationFieldAttemptKey(
            AiConfigurationFieldProposal.Attempt value) {
        return value.proposalId() + ":" + value.action() + ":" + value.requestKey();
    }

    private static String artifactAttemptKey(
            AiConfigurationArtifactProposal.Attempt value) {
        return value.proposalId() + ":" + value.action() + ":" + value.requestKey();
    }

    private static String workAttemptKey(AiWorkProposal.Attempt value) {
        return value.proposalId() + ":" + value.action() + ":" + value.requestKey();
    }

    private static String generatedDraftAttemptKey(
            AiGeneratedDraftProposal.Attempt value) {
        return value.proposalId() + ":" + value.action() + ":" + value.requestKey();
    }
}
