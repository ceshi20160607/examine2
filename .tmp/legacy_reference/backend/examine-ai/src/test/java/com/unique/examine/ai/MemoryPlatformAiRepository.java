package com.unique.examine.ai;

import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.domain.PlatformAiTaskProposal;
import com.unique.examine.ai.repository.PlatformAiRepository;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

final class MemoryPlatformAiRepository implements PlatformAiRepository {
    final Map<Long, PlatformAiProvider> providerValues = new LinkedHashMap<>();
    PlatformAiPolicy.Draft draftValue;
    boolean failPolicyDraftInsertAsDuplicate;
    final Map<String, PlatformAiPolicy.Check> checkValues = new LinkedHashMap<>();
    final Map<Long, PlatformAiPolicy.Version> versionValues = new LinkedHashMap<>();
    final Map<String, PlatformAiPolicy.PublishReplay> replayValues =
            new LinkedHashMap<>();
    final Map<Long, PlatformAiConversation.Session> sessionValues =
            new LinkedHashMap<>();
    final List<PlatformAiConversation.Message> messageValues = new ArrayList<>();
    final Map<Long, PlatformAiConversation.Turn> turnValues = new LinkedHashMap<>();
    final List<PlatformAiConversation.Usage> usageValues = new ArrayList<>();
    final List<PlatformAiConversation.Evidence> evidenceValues = new ArrayList<>();
    final List<PlatformAiConversation.AuditEvent> auditValues = new ArrayList<>();
    final Map<String, Quota> quotas = new LinkedHashMap<>();
    final Map<Long, PlatformAiTaskProposal> taskProposalValues =
            new LinkedHashMap<>();
    final Map<String, PlatformAiTaskProposal.Attempt> taskAttemptValues =
            new LinkedHashMap<>();
    final List<PlatformAiTaskProposal.Event> taskEventValues = new ArrayList<>();

    @Override
    public Optional<PlatformAiProvider> provider(long providerId) {
        return Optional.ofNullable(providerValues.get(providerId));
    }

    @Override
    public Optional<PlatformAiProvider> providerByCode(String code) {
        return providerValues.values().stream()
                .filter(value -> value.code().equals(code)).findFirst();
    }

    @Override
    public List<PlatformAiProvider> providers(int offset, int limit) {
        return providerValues.values().stream().skip(offset).limit(limit).toList();
    }

    @Override
    public void insertProvider(PlatformAiProvider provider) {
        providerValues.put(provider.id(), provider);
    }

    @Override
    public boolean updateProvider(
            PlatformAiProvider provider, long expectedVersion) {
        var current = providerValues.get(provider.id());
        if (current == null || current.version() != expectedVersion) return false;
        providerValues.put(provider.id(), provider);
        return true;
    }

    @Override
    public Optional<PlatformAiPolicy.Draft> policyDraft() {
        return Optional.ofNullable(draftValue);
    }

    @Override
    public void insertPolicyDraft(PlatformAiPolicy.Draft draft) {
        if (failPolicyDraftInsertAsDuplicate) {
            throw new DuplicateKeyException("singleton draft race");
        }
        if (draftValue != null) throw new IllegalStateException("duplicate draft");
        draftValue = draft;
    }

    @Override
    public boolean updatePolicyDraft(
            PlatformAiPolicy.Draft draft, long expectedRevision) {
        if (draftValue == null || draftValue.id() != draft.id()
                || draftValue.revision() != expectedRevision) return false;
        draftValue = draft;
        return true;
    }

    @Override
    public void insertPolicyCheck(PlatformAiPolicy.Check check) {
        checkValues.put(check.policyId() + ":" + check.draftRevision(), check);
    }

    @Override
    public Optional<PlatformAiPolicy.Check> policyCheck(
            long policyId, long draftRevision) {
        return Optional.ofNullable(checkValues.get(policyId + ":" + draftRevision));
    }

    @Override
    public Optional<PlatformAiPolicy.Version> activePolicy() {
        return draftValue == null || draftValue.activeVersionId() == null
                ? Optional.empty()
                : Optional.ofNullable(versionValues.get(draftValue.activeVersionId()));
    }

    @Override
    public Optional<PlatformAiPolicy.Version> policyVersion(long versionId) {
        return Optional.ofNullable(versionValues.get(versionId));
    }

    @Override
    public int nextPolicyVersionNumber(long policyId) {
        return versionValues.values().stream()
                .filter(value -> value.policyId() == policyId)
                .mapToInt(PlatformAiPolicy.Version::versionNumber)
                .max().orElse(0) + 1;
    }

    @Override
    public Optional<PlatformAiPolicy.PublishReplay> publishReplay(
            long policyId, String requestKey) {
        return Optional.ofNullable(replayValues.get(policyId + ":" + requestKey));
    }

    @Override
    public boolean publishPolicy(
            PlatformAiPolicy.Draft expected,
            PlatformAiPolicy.Draft published,
            PlatformAiPolicy.Version version,
            PlatformAiPolicy.PublishReplay replay) {
        if (draftValue == null || draftValue.id() != expected.id()
                || draftValue.revision() != expected.revision()
                || draftValue.status() != expected.status()
                || !draftValue.draftHash().equals(expected.draftHash())) return false;
        draftValue = published;
        versionValues.put(version.id(), version);
        replayValues.put(replay.policyId() + ":" + replay.requestKey(), replay);
        return true;
    }

    @Override
    public void insertSession(
            PlatformAiConversation.Session session,
            PlatformAiConversation.AuditEvent event) {
        sessionValues.put(session.id(), session);
        auditValues.add(event);
    }

    @Override
    public Optional<PlatformAiConversation.Session> session(
            long accountId, long sessionId) {
        return Optional.ofNullable(sessionValues.get(sessionId))
                .filter(value -> value.accountId() == accountId);
    }

    @Override
    public List<PlatformAiConversation.Session> sessions(
            long accountId, int offset, int limit) {
        return sessionValues.values().stream()
                .filter(value -> value.accountId() == accountId)
                .skip(offset).limit(limit).toList();
    }

    @Override
    public List<PlatformAiConversation.Message> messages(
            long accountId, long sessionId, int limit) {
        return messageValues.stream().filter(value ->
                value.accountId() == accountId && value.sessionId() == sessionId)
                .limit(limit).toList();
    }

    @Override
    public List<PlatformAiConversation.Turn> turns(
            long accountId, long sessionId, int limit) {
        return turnValues.values().stream().filter(value ->
                value.accountId() == accountId && value.sessionId() == sessionId)
                .limit(limit).toList();
    }

    @Override
    public List<PlatformAiConversation.Evidence> evidence(
            long accountId, long turnId, int limit) {
        return evidenceValues.stream().filter(value ->
                value.accountId() == accountId && value.turnId() == turnId)
                .limit(limit).toList();
    }

    @Override
    public AdmissionResult admitTurn(
            PlatformAiConversation.Session expectedSession,
            PlatformAiConversation.Session touchedSession,
            PlatformAiConversation.Message userMessage,
            PlatformAiConversation.Turn runningTurn,
            PlatformAiConversation.QuotaReservation reservation,
            PlatformAiConversation.AuditEvent event) {
        var current = sessionValues.get(expectedSession.id());
        if (current == null || current.accountId() != expectedSession.accountId()
                || current.status() != expectedSession.status()) {
            return AdmissionResult.SESSION_CONFLICT;
        }
        var key = quotaKey(reservation);
        var quota = quotas.computeIfAbsent(key, ignored -> new Quota());
        if (quota.requests + 1 > reservation.requestLimit()) {
            return AdmissionResult.REQUEST_QUOTA_EXCEEDED;
        }
        if (quota.usedTokens + quota.reservedTokens
                + reservation.reservedTokens() > reservation.tokenLimit()) {
            return AdmissionResult.TOKEN_QUOTA_EXCEEDED;
        }
        if (quota.running + 1 > reservation.concurrencyLimit()) {
            return AdmissionResult.CONCURRENCY_EXCEEDED;
        }
        quota.requests++;
        quota.reservedTokens += reservation.reservedTokens();
        quota.running++;
        sessionValues.put(touchedSession.id(), touchedSession);
        turnValues.put(runningTurn.id(), runningTurn);
        messageValues.add(userMessage);
        auditValues.add(event);
        return AdmissionResult.ADMITTED;
    }

    @Override
    public boolean finishTurn(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiConversation.Evidence evidence,
            PlatformAiConversation.AuditEvent event) {
        if (usage.totalTokens() > expected.reservedTokens()) {
            throw new IllegalStateException("usage exceeds reservation");
        }
        var current = turnValues.get(expected.id());
        if (current == null
                || current.status() != PlatformAiConversation.TurnStatus.RUNNING) {
            return false;
        }
        turnValues.put(terminal.id(), terminal);
        if (assistant != null) messageValues.add(assistant);
        usageValues.add(usage);
        if (evidence != null) evidenceValues.add(evidence);
        auditValues.add(event);
        var key = expected.accountId() + ":" + expected.policyVersionId() + ":"
                + expected.createdAt().atZone(java.time.ZoneOffset.UTC)
                .toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        var quota = quotas.get(key);
        if (quota == null || quota.running < 1
                || quota.reservedTokens < expected.reservedTokens()) {
            throw new IllegalStateException("quota completion mismatch");
        }
        quota.running--;
        quota.reservedTokens -= expected.reservedTokens();
        quota.usedTokens += usage.totalTokens();
        return true;
    }

    @Override
    public boolean finishTurnWithTaskProposal(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiTaskProposal proposal,
            PlatformAiTaskProposal.Event proposalEvent,
            PlatformAiConversation.AuditEvent auditEvent) {
        if (!finishTurn(expected, terminal, assistant, usage, null, auditEvent)) {
            return false;
        }
        taskProposalValues.put(proposal.id(), proposal);
        taskEventValues.add(proposalEvent);
        return true;
    }

    @Override
    public Optional<PlatformAiTaskProposal> taskProposal(
            long accountId, long sessionId, long proposalId) {
        return Optional.ofNullable(taskProposalValues.get(proposalId))
                .filter(value -> value.accountId() == accountId
                        && value.sessionId() == sessionId);
    }

    @Override
    public Optional<PlatformAiTaskProposal> taskProposalByTurn(
            long accountId, long turnId) {
        return taskProposalValues.values().stream().filter(value ->
                value.accountId() == accountId && value.turnId() == turnId)
                .findFirst();
    }

    @Override
    public Optional<PlatformAiTaskProposal.Attempt> taskProposalAttempt(
            long accountId, long proposalId, String action, String requestKey) {
        return Optional.ofNullable(taskAttemptValues.get(
                accountId + ":" + proposalId + ":" + action + ":" + requestKey));
    }

    @Override
    public synchronized ClaimResult claimTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal executing,
            PlatformAiTaskProposal.Attempt attempt,
            PlatformAiTaskProposal.Event event) {
        var current = taskProposalValues.get(expected.id());
        if (current == null || current.state() != expected.state()
                || current.revision() != expected.revision()) {
            return ClaimResult.VERSION_CONFLICT;
        }
        var key = attempt.accountId() + ":" + attempt.proposalId() + ":"
                + attempt.action() + ":" + attempt.requestKey();
        if (taskAttemptValues.containsKey(key)) {
            return ClaimResult.IDEMPOTENCY_CONFLICT;
        }
        taskProposalValues.put(executing.id(), executing);
        taskAttemptValues.put(key, attempt);
        taskEventValues.add(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    public synchronized boolean finishTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Attempt terminalAttempt,
            PlatformAiTaskProposal.Event event) {
        var current = taskProposalValues.get(expected.id());
        if (current == null || current.state() != expected.state()
                || current.revision() != expected.revision()) return false;
        var key = terminalAttempt.accountId() + ":" + terminalAttempt.proposalId()
                + ":" + terminalAttempt.action() + ":" + terminalAttempt.requestKey();
        taskProposalValues.put(terminal.id(), terminal);
        taskAttemptValues.put(key, terminalAttempt);
        taskEventValues.add(event);
        return true;
    }

    @Override
    public synchronized boolean transitionTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Event event) {
        var current = taskProposalValues.get(expected.id());
        if (current == null || current.state() != expected.state()
                || current.revision() != expected.revision()) return false;
        taskProposalValues.put(terminal.id(), terminal);
        taskEventValues.add(event);
        return true;
    }

    @Override
    public Optional<QuotaUsage> quotaUsage(
            long accountId, long policyVersionId, Instant periodStart) {
        return Optional.ofNullable(quotas.get(
                accountId + ":" + policyVersionId + ":" + periodStart)).map(
                value -> new QuotaUsage(
                        value.requests, value.usedTokens,
                        value.reservedTokens, value.running));
    }

    @Override
    public List<AgentActivity> agentActivity(long accountId, int limit) {
        var values = new ArrayList<AgentActivity>();
        auditValues.stream().filter(value -> value.accountId() == accountId)
                .forEach(value -> {
                    var turn = "TURN".equals(value.aggregateType())
                            ? turnValues.get(value.aggregateId()) : null;
                    values.add(new AgentActivity(
                            value.eventType(), value.createdAt(),
                            turn == null || "UNRESOLVED".equals(turn.operation())
                                    ? null : turn.operation(),
                            value.resultCode(), value.requestId(), value.traceId()));
                });
        taskEventValues.stream().filter(value -> value.accountId() == accountId)
                .forEach(value -> values.add(new AgentActivity(
                        value.eventType(), value.createdAt(),
                        "PLATFORM_TASK_DRAFT", value.resultCode(),
                        value.requestId(), value.traceId())));
        return values.stream().sorted(java.util.Comparator.comparing(
                        AgentActivity::time).reversed())
                .limit(limit).toList();
    }

    @Override
    public void insertAudit(PlatformAiConversation.AuditEvent event) {
        auditValues.add(event);
    }

    private static String quotaKey(
            PlatformAiConversation.QuotaReservation value) {
        return value.accountId() + ":" + value.policyVersionId() + ":"
                + value.periodStart();
    }

    static final class Quota {
        int requests;
        int usedTokens;
        int reservedTokens;
        int running;
    }
}
