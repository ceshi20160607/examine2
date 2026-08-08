package com.unique.examine.ai.repository;

import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiConfirmation;
import com.unique.examine.ai.domain.AiConfigurationFieldProposal;
import com.unique.examine.ai.domain.AiConfigurationArtifactProposal;
import com.unique.examine.ai.domain.AiFillProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.domain.AiWorkProposal;
import com.unique.examine.ai.domain.AiGeneratedDraftProposal;

import java.util.List;
import java.util.Optional;

public interface AiRepository {
    Optional<AiProvider> provider(long systemId, long tenantId, long providerId);

    Optional<AiProvider> providerByCode(long systemId, long tenantId, String code);

    List<AiProvider> providers(long systemId, long tenantId, int offset, int limit);

    void insertProvider(AiProvider provider);

    boolean updateProvider(AiProvider provider, long expectedVersion);

    Optional<AiPolicy.Draft> policyDraft(long systemId, long tenantId);

    void insertPolicyDraft(AiPolicy.Draft draft);

    boolean updatePolicyDraft(AiPolicy.Draft draft, long expectedRevision);

    void insertPolicyCheck(AiPolicy.Check check);

    Optional<AiPolicy.Check> policyCheck(
            long systemId, long tenantId, long policyId, long draftRevision);

    Optional<AiPolicy.Version> activePolicy(long systemId, long tenantId);

    Optional<AiPolicy.Version> policyVersion(
            long systemId, long tenantId, long versionId);

    int nextPolicyVersionNumber(long systemId, long tenantId, long policyId);

    Optional<AiPolicy.PublishReplay> publishReplay(
            long systemId, long tenantId, long policyId, String requestKey);

    boolean publishPolicy(
            AiPolicy.Draft expected,
            AiPolicy.Draft published,
            AiPolicy.Version version,
            AiPolicy.PublishReplay replay);

    void insertSession(AiConversation.Session session);

    Optional<AiConversation.Session> session(
            long systemId, long tenantId, long memberId, long sessionId);

    List<AiConversation.Session> sessions(
            long systemId, long tenantId, long memberId, int offset, int limit);

    void insertMessage(AiConversation.Message message);

    void insertTurn(AiConversation.Turn turn);

    boolean finishTurn(AiConversation.Turn turn);

    void insertToolCall(AiConversation.ToolCall call);

    void insertUsage(AiConversation.Usage usage);

    List<AiConversation.Message> messages(
            long systemId, long tenantId, long sessionId, int limit);

    List<AiConversation.Turn> turns(
            long systemId, long tenantId, long sessionId, int limit);

    void insertConfirmation(
            AiConfirmation confirmation, AiConfirmation.Event proposedEvent);

    Optional<AiConfirmation> confirmation(
            long systemId, long tenantId, long confirmationId);

    Optional<AiConfirmation> confirmationByTurn(
            long systemId, long tenantId, long turnId);

    Optional<AiConfirmation.Attempt> confirmationAttempt(
            long systemId, long tenantId, long confirmationId, String requestKey);

    ClaimResult claimConfirmation(
            AiConfirmation expected,
            AiConfirmation executing,
            AiConfirmation.Attempt attempt,
            AiConfirmation.Event confirmingEvent);

    boolean finishConfirmation(
            AiConfirmation expectedExecuting,
            AiConfirmation terminal,
            AiConfirmation.Attempt terminalAttempt,
            AiConfirmation.Event terminalEvent);

    boolean rejectConfirmation(
            AiConfirmation expected, AiConfirmation rejected,
            AiConfirmation.Event rejectedEvent);

    boolean expireConfirmation(
            AiConfirmation expected, AiConfirmation expired,
            AiConfirmation.Event expiredEvent);

    void insertConfigurationFieldProposal(
            AiConfigurationFieldProposal proposal,
            AiConfigurationFieldProposal.Event event);

    Optional<AiConfigurationFieldProposal> configurationFieldProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId);

    Optional<AiConfigurationFieldProposal> configurationFieldProposalByTurn(
            long systemId, long tenantId, long turnId);

    Optional<AiConfigurationFieldProposal.Attempt> configurationFieldAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey);

    ClaimResult claimConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal executing,
            AiConfigurationFieldProposal.Attempt attempt,
            AiConfigurationFieldProposal.Event event);

    boolean finishConfigurationFieldProposal(
            AiConfigurationFieldProposal expectedExecuting,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Attempt terminalAttempt,
            AiConfigurationFieldProposal.Event event);

    boolean transitionConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Event event);

    void insertConfigurationArtifactProposal(
            AiConfigurationArtifactProposal proposal,
            AiConfigurationArtifactProposal.Event event);

    Optional<AiConfigurationArtifactProposal> configurationArtifactProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId);

    Optional<AiConfigurationArtifactProposal> configurationArtifactProposalByTurn(
            long systemId, long tenantId, long turnId);

    Optional<AiConfigurationArtifactProposal.Attempt> configurationArtifactAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey);

    ClaimResult claimConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal executing,
            AiConfigurationArtifactProposal.Attempt attempt,
            AiConfigurationArtifactProposal.Event event);

    boolean finishConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expectedExecuting,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Attempt terminalAttempt,
            AiConfigurationArtifactProposal.Event event);

    boolean transitionConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Event event);

    void insertWorkProposal(
            AiWorkProposal proposal, AiWorkProposal.Event event);

    Optional<AiWorkProposal> workProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId);

    Optional<AiWorkProposal> workProposalByTurn(
            long systemId, long tenantId, long turnId);

    Optional<AiWorkProposal.Attempt> workAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey);

    ClaimResult claimWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal executing,
            AiWorkProposal.Attempt attempt,
            AiWorkProposal.Event event);

    boolean finishWorkProposal(
            AiWorkProposal expectedExecuting,
            AiWorkProposal terminal,
            AiWorkProposal.Attempt terminalAttempt,
            AiWorkProposal.Event event);

    boolean transitionWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal terminal,
            AiWorkProposal.Event event);

    void insertGeneratedDraftProposal(
            AiGeneratedDraftProposal proposal,
            AiGeneratedDraftProposal.Event event);

    Optional<AiGeneratedDraftProposal> generatedDraftProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId);

    Optional<AiGeneratedDraftProposal> generatedDraftProposalByTurn(
            long systemId, long tenantId, long turnId);

    Optional<AiGeneratedDraftProposal.Attempt> generatedDraftAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey);

    ClaimResult claimGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal executing,
            AiGeneratedDraftProposal.Attempt attempt,
            AiGeneratedDraftProposal.Event event);

    boolean finishGeneratedDraftProposal(
            AiGeneratedDraftProposal expectedExecuting,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Attempt terminalAttempt,
            AiGeneratedDraftProposal.Event event);

    boolean transitionGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Event event);

    enum ClaimResult {
        CLAIMED,
        CONFIRMATION_CONFLICT,
        KEY_EXISTS
    }

    Optional<AiFillProposal> fillProposal(
            long systemId, long tenantId, long memberId,
            String moduleCode, String recordId, String fieldCode,
            long proposalId);

    Optional<AiFillProposal.Attempt> fillAttempt(
            long systemId, long tenantId, long memberId,
            String action, String requestKey);

    boolean reserveFillAttempt(AiFillProposal.Attempt attempt);

    boolean completeFillAttempt(
            AiFillProposal.Attempt expectedExecuting,
            AiFillProposal.Attempt terminal);

    void insertFillProposal(
            AiFillProposal proposal,
            AiFillProposal.Attempt completedCreateAttempt,
            AiFillProposal.Event event);

    ClaimResult claimFillProposal(
            AiFillProposal expected,
            AiFillProposal executing,
            AiFillProposal.Attempt attempt,
            AiFillProposal.Event event);

    boolean finishFillProposal(
            AiFillProposal expectedExecuting,
            AiFillProposal terminal,
            AiFillProposal.Attempt terminalAttempt,
            AiFillProposal.Event event);

    boolean expireFillProposal(
            AiFillProposal expected,
            AiFillProposal expired,
            AiFillProposal.Event event);
}
