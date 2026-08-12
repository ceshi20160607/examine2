package com.unique.examine.ai.repository;

import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.domain.PlatformAiTaskProposal;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/** Persistence boundary for the isolated PLATFORM AI scope. */
public interface PlatformAiRepository {
    Optional<PlatformAiProvider> provider(long providerId);

    Optional<PlatformAiProvider> providerByCode(String code);

    List<PlatformAiProvider> providers(int offset, int limit);

    void insertProvider(PlatformAiProvider provider);

    boolean updateProvider(PlatformAiProvider provider, long expectedVersion);

    Optional<PlatformAiPolicy.Draft> policyDraft();

    void insertPolicyDraft(PlatformAiPolicy.Draft draft);

    boolean updatePolicyDraft(
            PlatformAiPolicy.Draft draft, long expectedRevision);

    void insertPolicyCheck(PlatformAiPolicy.Check check);

    Optional<PlatformAiPolicy.Check> policyCheck(
            long policyId, long draftRevision);

    Optional<PlatformAiPolicy.Version> activePolicy();

    Optional<PlatformAiPolicy.Version> policyVersion(long versionId);

    int nextPolicyVersionNumber(long policyId);

    Optional<PlatformAiPolicy.PublishReplay> publishReplay(
            long policyId, String requestKey);

    boolean publishPolicy(
            PlatformAiPolicy.Draft expected,
            PlatformAiPolicy.Draft published,
            PlatformAiPolicy.Version version,
            PlatformAiPolicy.PublishReplay replay);

    void insertSession(
            PlatformAiConversation.Session session,
            PlatformAiConversation.AuditEvent event);

    Optional<PlatformAiConversation.Session> session(
            long accountId, long sessionId);

    List<PlatformAiConversation.Session> sessions(
            long accountId, int offset, int limit);

    List<PlatformAiConversation.Message> messages(
            long accountId, long sessionId, int limit);

    List<PlatformAiConversation.Turn> turns(
            long accountId, long sessionId, int limit);

    List<PlatformAiConversation.Evidence> evidence(
            long accountId, long turnId, int limit);

    AdmissionResult admitTurn(
            PlatformAiConversation.Session expectedSession,
            PlatformAiConversation.Session touchedSession,
            PlatformAiConversation.Message userMessage,
            PlatformAiConversation.Turn runningTurn,
            PlatformAiConversation.QuotaReservation reservation,
            PlatformAiConversation.AuditEvent event);

    boolean finishTurn(
            PlatformAiConversation.Turn expectedRunning,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistantMessage,
            PlatformAiConversation.Usage usage,
            PlatformAiConversation.Evidence evidence,
            PlatformAiConversation.AuditEvent event);

    boolean finishTurnWithTaskProposal(
            PlatformAiConversation.Turn expectedRunning,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistantMessage,
            PlatformAiConversation.Usage usage,
            PlatformAiTaskProposal proposal,
            PlatformAiTaskProposal.Event proposalEvent,
            PlatformAiConversation.AuditEvent auditEvent);

    Optional<PlatformAiTaskProposal> taskProposal(
            long accountId, long sessionId, long proposalId);

    Optional<PlatformAiTaskProposal> taskProposalByTurn(
            long accountId, long turnId);

    Optional<PlatformAiTaskProposal.Attempt> taskProposalAttempt(
            long accountId, long proposalId, String action, String requestKey);

    ClaimResult claimTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal executing,
            PlatformAiTaskProposal.Attempt attempt,
            PlatformAiTaskProposal.Event event);

    boolean finishTaskProposal(
            PlatformAiTaskProposal expectedExecuting,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Attempt terminalAttempt,
            PlatformAiTaskProposal.Event event);

    boolean transitionTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Event event);

    Optional<QuotaUsage> quotaUsage(
            long accountId, long policyVersionId, Instant periodStart);

    List<AgentActivity> agentActivity(long accountId, int limit);

    void insertAudit(PlatformAiConversation.AuditEvent event);

    enum AdmissionResult {
        ADMITTED,
        SESSION_CONFLICT,
        REQUEST_QUOTA_EXCEEDED,
        TOKEN_QUOTA_EXCEEDED,
        CONCURRENCY_EXCEEDED
    }

    enum ClaimResult {
        CLAIMED,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT
    }

    record QuotaUsage(
            long requestCount,
            long usedTokens,
            long reservedTokens,
            long runningCount) {
        public QuotaUsage {
            if (requestCount < 0 || usedTokens < 0
                    || reservedTokens < 0 || runningCount < 0) {
                throw new IllegalArgumentException(
                        "Platform AI quota usage is invalid");
            }
        }
    }

    record AgentActivity(
            String event,
            Instant time,
            String operation,
            String resultCode,
            String requestId,
            String traceId) { }
}
