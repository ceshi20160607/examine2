package com.unique.examine.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.domain.PlatformAiTaskProposal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcPlatformAiRepository implements PlatformAiRepository {
    private static final TypeReference<List<PlatformAiPolicy.Issue>> ISSUES =
            new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcPlatformAiRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<PlatformAiProvider> provider(long providerId) {
        return one("SELECT * FROM un_platform_ai_provider WHERE id=?",
                this::mapProvider, providerId);
    }

    @Override
    public Optional<PlatformAiProvider> providerByCode(String code) {
        return one("SELECT * FROM un_platform_ai_provider WHERE code=?",
                this::mapProvider, code);
    }

    @Override
    public List<PlatformAiProvider> providers(int offset, int limit) {
        return jdbc.query("""
                SELECT * FROM un_platform_ai_provider
                ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?
                """, this::mapProvider, limit, offset);
    }

    @Override
    public void insertProvider(PlatformAiProvider value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_provider(
                  id,code,name,base_url,model_code,secret_ref,timeout_seconds,
                  enabled,revision,created_at,created_by,updated_at,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.code(), value.name(), value.baseUrl(),
                value.model(), value.secretRef(), value.timeoutSeconds(),
                value.enabled(), value.version(), timestamp(value.createdAt()),
                value.createdBy(), timestamp(value.updatedAt()), value.updatedBy());
    }

    @Override
    public boolean updateProvider(
            PlatformAiProvider value, long expectedVersion) {
        return jdbc.update("""
                UPDATE un_platform_ai_provider SET name=?,base_url=?,model_code=?,
                  secret_ref=?,timeout_seconds=?,enabled=?,revision=?,updated_at=?,
                  updated_by=? WHERE id=? AND revision=?
                """, value.name(), value.baseUrl(), value.model(),
                value.secretRef(), value.timeoutSeconds(), value.enabled(),
                value.version(), timestamp(value.updatedAt()), value.updatedBy(),
                value.id(), expectedVersion) == 1;
    }

    @Override
    public Optional<PlatformAiPolicy.Draft> policyDraft() {
        return one("""
                SELECT * FROM un_platform_ai_policy
                ORDER BY updated_at DESC,id DESC LIMIT 1
                """, this::mapDraft);
    }

    @Override
    public void insertPolicyDraft(PlatformAiPolicy.Draft value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_policy(
                  id,revision,status,provider_id,provider_version,draft_json,
                  draft_hash,active_version_id,updated_at,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.revision(), value.status().name(),
                value.providerId(), value.providerVersion(), encode(value.settings()),
                value.draftHash(), value.activeVersionId(),
                timestamp(value.updatedAt()), value.updatedBy());
    }

    @Override
    public boolean updatePolicyDraft(
            PlatformAiPolicy.Draft value, long expectedRevision) {
        return jdbc.update("""
                UPDATE un_platform_ai_policy SET revision=?,status=?,provider_id=?,
                  provider_version=?,draft_json=?,draft_hash=?,active_version_id=?,
                  updated_at=?,updated_by=? WHERE id=? AND revision=?
                """, value.revision(), value.status().name(), value.providerId(),
                value.providerVersion(), encode(value.settings()), value.draftHash(),
                value.activeVersionId(), timestamp(value.updatedAt()),
                value.updatedBy(), value.id(), expectedRevision) == 1;
    }

    @Override
    public void insertPolicyCheck(PlatformAiPolicy.Check value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_policy_check(
                  id,policy_id,draft_revision,draft_hash,issues_json,
                  checked_at,checked_by) VALUES(?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE draft_hash=VALUES(draft_hash),
                  issues_json=VALUES(issues_json),checked_at=VALUES(checked_at),
                  checked_by=VALUES(checked_by)
                """, value.id(), value.policyId(), value.draftRevision(),
                value.draftHash(), encode(value.issues()),
                timestamp(value.checkedAt()), value.checkedBy());
    }

    @Override
    public Optional<PlatformAiPolicy.Check> policyCheck(
            long policyId, long draftRevision) {
        return one("""
                SELECT * FROM un_platform_ai_policy_check
                WHERE policy_id=? AND draft_revision=?
                """, this::mapCheck, policyId, draftRevision);
    }

    @Override
    public Optional<PlatformAiPolicy.Version> activePolicy() {
        return one("""
                SELECT version.* FROM un_platform_ai_policy root
                JOIN un_platform_ai_policy_version version
                  ON version.policy_id=root.id
                 AND version.id=root.active_version_id
                ORDER BY root.updated_at DESC,root.id DESC LIMIT 1
                """, this::mapVersion);
    }

    @Override
    public Optional<PlatformAiPolicy.Version> policyVersion(long versionId) {
        return one("SELECT * FROM un_platform_ai_policy_version WHERE id=?",
                this::mapVersion, versionId);
    }

    @Override
    public int nextPolicyVersionNumber(long policyId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no),0)+1
                FROM un_platform_ai_policy_version WHERE policy_id=?
                """, Integer.class, policyId);
        return value == null ? 1 : value;
    }

    @Override
    public Optional<PlatformAiPolicy.PublishReplay> publishReplay(
            long policyId, String requestKey) {
        return one("""
                SELECT * FROM un_platform_ai_policy_publish_replay
                WHERE policy_id=? AND request_key=?
                """, this::mapReplay, policyId, requestKey);
    }

    @Override
    @Transactional
    public boolean publishPolicy(
            PlatformAiPolicy.Draft expected,
            PlatformAiPolicy.Draft published,
            PlatformAiPolicy.Version version,
            PlatformAiPolicy.PublishReplay replay) {
        var locked = jdbc.query("""
                SELECT revision,status,draft_hash FROM un_platform_ai_policy
                WHERE id=? FOR UPDATE
                """, (result, row) -> new PolicyLock(
                result.getLong("revision"), result.getString("status"),
                result.getString("draft_hash")), expected.id());
        if (locked.size() != 1 || locked.getFirst().revision != expected.revision()
                || !locked.getFirst().status.equals(expected.status().name())
                || !locked.getFirst().draftHash.equals(expected.draftHash())) {
            return false;
        }
        insertVersion(version);
        jdbc.update("""
                INSERT INTO un_platform_ai_policy_publish_replay(
                  id,policy_id,request_key,request_hash,version_id,created_at)
                VALUES(?,?,?,?,?,?)
                """, replay.id(), replay.policyId(), replay.requestKey(),
                replay.requestHash(), replay.versionId(),
                timestamp(replay.createdAt()));
        if (!updatePolicyDraft(published, expected.revision())) {
            throw new IllegalStateException(
                    "Platform AI locked policy publish update failed");
        }
        return true;
    }

    @Override
    @Transactional
    public void insertSession(
            PlatformAiConversation.Session value,
            PlatformAiConversation.AuditEvent event) {
        jdbc.update("""
                INSERT INTO un_platform_ai_session(
                  id,scope,account_id,title_summary,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.titleSummary(), value.status().name(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()));
        insertAuditRow(event);
    }

    @Override
    public Optional<PlatformAiConversation.Session> session(
            long accountId, long sessionId) {
        return one("""
                SELECT * FROM un_platform_ai_session
                WHERE account_id=? AND id=? AND scope='PLATFORM'
                """, this::mapSession, accountId, sessionId);
    }

    @Override
    public List<PlatformAiConversation.Session> sessions(
            long accountId, int offset, int limit) {
        return jdbc.query("""
                SELECT * FROM un_platform_ai_session
                WHERE account_id=? AND scope='PLATFORM'
                ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?
                """, this::mapSession, accountId, limit, offset);
    }

    @Override
    public List<PlatformAiConversation.Message> messages(
            long accountId, long sessionId, int limit) {
        return jdbc.query("""
                SELECT * FROM un_platform_ai_message
                WHERE account_id=? AND session_id=? AND scope='PLATFORM'
                ORDER BY created_at,id LIMIT ?
                """, this::mapMessage, accountId, sessionId, limit);
    }

    @Override
    public List<PlatformAiConversation.Turn> turns(
            long accountId, long sessionId, int limit) {
        return jdbc.query("""
                SELECT * FROM un_platform_ai_turn
                WHERE account_id=? AND session_id=? AND scope='PLATFORM'
                ORDER BY created_at,id LIMIT ?
                """, this::mapTurn, accountId, sessionId, limit);
    }

    @Override
    public List<PlatformAiConversation.Evidence> evidence(
            long accountId, long turnId, int limit) {
        return jdbc.query("""
                SELECT * FROM un_platform_ai_evidence
                WHERE account_id=? AND turn_id=? AND scope='PLATFORM'
                ORDER BY created_at,id LIMIT ?
                """, this::mapEvidence, accountId, turnId, limit);
    }

    @Override
    @Transactional
    public AdmissionResult admitTurn(
            PlatformAiConversation.Session expectedSession,
            PlatformAiConversation.Session touchedSession,
            PlatformAiConversation.Message userMessage,
            PlatformAiConversation.Turn runningTurn,
            PlatformAiConversation.QuotaReservation reservation,
            PlatformAiConversation.AuditEvent event) {
        jdbc.update("""
                INSERT IGNORE INTO un_platform_ai_quota_bucket(
                  account_id,policy_version_id,period_start,request_count,
                  used_tokens,reserved_tokens,running_count,revision,updated_at)
                VALUES(?,?,?,0,0,0,0,0,?)
                """, reservation.accountId(), reservation.policyVersionId(),
                timestamp(reservation.periodStart()),
                timestamp(runningTurn.createdAt()));
        var quotas = jdbc.query("""
                SELECT request_count,used_tokens,reserved_tokens,running_count
                FROM un_platform_ai_quota_bucket
                WHERE account_id=? AND policy_version_id=? AND period_start=?
                FOR UPDATE
                """, (result, row) -> new QuotaState(
                result.getLong("request_count"), result.getLong("used_tokens"),
                result.getLong("reserved_tokens"),
                result.getLong("running_count")), reservation.accountId(),
                reservation.policyVersionId(), timestamp(reservation.periodStart()));
        if (quotas.size() != 1) throw new IllegalStateException(
                "Platform AI quota bucket is unavailable");
        var quota = quotas.getFirst();
        if (quota.requestCount + 1 > reservation.requestLimit()) {
            return AdmissionResult.REQUEST_QUOTA_EXCEEDED;
        }
        if (quota.usedTokens + quota.reservedTokens
                + reservation.reservedTokens() > reservation.tokenLimit()) {
            return AdmissionResult.TOKEN_QUOTA_EXCEEDED;
        }
        if (quota.runningCount + 1 > reservation.concurrencyLimit()) {
            return AdmissionResult.CONCURRENCY_EXCEEDED;
        }
        var sessions = jdbc.query("""
                SELECT status FROM un_platform_ai_session
                WHERE account_id=? AND id=? FOR UPDATE
                """, (result, row) -> result.getString("status"),
                expectedSession.accountId(), expectedSession.id());
        if (sessions.size() != 1
                || !sessions.getFirst().equals(expectedSession.status().name())) {
            return AdmissionResult.SESSION_CONFLICT;
        }
        insertTurn(runningTurn);
        insertMessage(userMessage);
        if (jdbc.update("""
                UPDATE un_platform_ai_session SET updated_at=?
                WHERE account_id=? AND id=? AND status=?
                """, timestamp(touchedSession.updatedAt()),
                expectedSession.accountId(), expectedSession.id(),
                expectedSession.status().name()) != 1) {
            throw new IllegalStateException("Platform AI session touch failed");
        }
        if (jdbc.update("""
                UPDATE un_platform_ai_quota_bucket SET request_count=request_count+1,
                  reserved_tokens=reserved_tokens+?,running_count=running_count+1,
                  revision=revision+1,updated_at=?
                WHERE account_id=? AND policy_version_id=? AND period_start=?
                """, reservation.reservedTokens(),
                timestamp(runningTurn.createdAt()), reservation.accountId(),
                reservation.policyVersionId(),
                timestamp(reservation.periodStart())) != 1) {
            throw new IllegalStateException("Platform AI quota reservation failed");
        }
        insertAuditRow(event);
        return AdmissionResult.ADMITTED;
    }

    @Override
    @Transactional
    public boolean finishTurn(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiConversation.Evidence evidence,
            PlatformAiConversation.AuditEvent event) {
        return completeTurn(expected, terminal, assistant, usage, evidence, event);
    }

    @Override
    @Transactional
    public boolean finishTurnWithTaskProposal(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiTaskProposal proposal,
            PlatformAiTaskProposal.Event proposalEvent,
            PlatformAiConversation.AuditEvent auditEvent) {
        if (!completeTurn(
                expected, terminal, assistant, usage, null, auditEvent)) {
            return false;
        }
        insertTaskProposal(proposal);
        insertTaskProposalEvent(proposalEvent);
        return true;
    }

    @Override
    public Optional<PlatformAiTaskProposal> taskProposal(
            long accountId, long sessionId, long proposalId) {
        return one("""
                SELECT * FROM un_platform_ai_task_proposal
                WHERE scope='PLATFORM' AND account_id=? AND session_id=? AND id=?
                """, this::mapTaskProposal, accountId, sessionId, proposalId);
    }

    @Override
    public Optional<PlatformAiTaskProposal> taskProposalByTurn(
            long accountId, long turnId) {
        return one("""
                SELECT * FROM un_platform_ai_task_proposal
                WHERE scope='PLATFORM' AND account_id=? AND turn_id=?
                """, this::mapTaskProposal, accountId, turnId);
    }

    @Override
    public Optional<PlatformAiTaskProposal.Attempt> taskProposalAttempt(
            long accountId, long proposalId, String action, String requestKey) {
        return one("""
                SELECT * FROM un_platform_ai_task_proposal_attempt
                WHERE scope='PLATFORM' AND account_id=? AND proposal_id=?
                  AND action=? AND request_key=?
                """, this::mapTaskProposalAttempt, accountId, proposalId,
                action, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal executing,
            PlatformAiTaskProposal.Attempt attempt,
            PlatformAiTaskProposal.Event event) {
        var locked = jdbc.query("""
                SELECT state,revision FROM un_platform_ai_task_proposal
                WHERE account_id=? AND session_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ProposalLock(
                result.getString("state"), result.getLong("revision")),
                expected.accountId(), expected.sessionId(), expected.id());
        if (locked.size() != 1
                || !locked.getFirst().state.equals(expected.state().name())
                || locked.getFirst().revision != expected.revision()) {
            return ClaimResult.VERSION_CONFLICT;
        }
        try {
            insertTaskProposalAttempt(attempt);
        } catch (DuplicateKeyException duplicate) {
            return ClaimResult.IDEMPOTENCY_CONFLICT;
        }
        if (updateTaskProposal(expected, executing) != 1) {
            throw new IllegalStateException(
                    "Platform AI task proposal claim update failed");
        }
        insertTaskProposalEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Attempt terminalAttempt,
            PlatformAiTaskProposal.Event event) {
        if (updateTaskProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_platform_ai_task_proposal_attempt
                SET status=?,result_hash=?,result_code=?,finished_at=?
                WHERE account_id=? AND proposal_id=? AND id=?
                  AND status='EXECUTING' AND finished_at IS NULL
                """, terminalAttempt.status().name(), terminalAttempt.resultHash(),
                terminalAttempt.resultCode(), timestamp(terminalAttempt.finishedAt()),
                terminalAttempt.accountId(), terminalAttempt.proposalId(),
                terminalAttempt.id()) != 1) {
            throw new IllegalStateException(
                    "Platform AI task proposal attempt completion failed");
        }
        insertTaskProposalEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal terminal,
            PlatformAiTaskProposal.Event event) {
        if (updateTaskProposal(expected, terminal) != 1) return false;
        insertTaskProposalEvent(event);
        return true;
    }

    @Override
    public Optional<QuotaUsage> quotaUsage(
            long accountId, long policyVersionId, Instant periodStart) {
        return one("""
                SELECT request_count,used_tokens,reserved_tokens,running_count
                FROM un_platform_ai_quota_bucket
                WHERE account_id=? AND policy_version_id=? AND period_start=?
                """, (result, row) -> new QuotaUsage(
                result.getLong("request_count"), result.getLong("used_tokens"),
                result.getLong("reserved_tokens"),
                result.getLong("running_count")), accountId, policyVersionId,
                timestamp(periodStart));
    }

    @Override
    public List<AgentActivity> agentActivity(long accountId, int limit) {
        return jdbc.query("""
                SELECT event_type,created_at,operation,result_code,request_id,trace_id
                FROM (
                  SELECT audit.id AS sort_id,audit.event_type,audit.created_at,
                    NULLIF(turn_row.operation,'UNRESOLVED') AS operation,
                    audit.result_code,audit.request_id,audit.trace_id
                  FROM un_platform_ai_audit_event audit
                  LEFT JOIN un_platform_ai_turn turn_row
                    ON audit.account_id=turn_row.account_id
                   AND audit.aggregate_type='TURN'
                   AND audit.aggregate_id=turn_row.id
                  WHERE audit.scope='PLATFORM' AND audit.account_id=?
                  UNION ALL
                  SELECT proposal_event.id AS sort_id,proposal_event.event_type,
                    proposal_event.created_at,'PLATFORM_TASK_DRAFT' AS operation,
                    proposal_event.result_code,proposal_event.request_id,
                    proposal_event.trace_id
                  FROM un_platform_ai_task_proposal_event proposal_event
                  WHERE proposal_event.scope='PLATFORM'
                    AND proposal_event.account_id=?
                ) activity
                ORDER BY created_at DESC,sort_id DESC LIMIT ?
                """, (result, row) -> new AgentActivity(
                result.getString("event_type"), instant(result, "created_at"),
                result.getString("operation"), result.getString("result_code"),
                result.getString("request_id"), result.getString("trace_id")),
                accountId, accountId, limit);
    }

    private boolean completeTurn(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiConversation.Evidence evidence,
            PlatformAiConversation.AuditEvent event) {
        if (usage.totalTokens() > expected.reservedTokens()) {
            throw new IllegalStateException(
                    "Platform AI provider usage exceeded its reservation");
        }
        if (jdbc.update("""
                UPDATE un_platform_ai_turn SET operation=?,status=?,plan_hash=?,
                  response_summary=?,response_hash=?,returned_systems=?,result_code=?,
                  retryable=?,latency_ms=?,finished_at=?
                WHERE account_id=? AND id=? AND status='RUNNING'
                """, terminal.operation(), terminal.status().name(),
                terminal.planHash(), terminal.responseSummary(),
                terminal.responseHash(), terminal.returnedSystems(),
                terminal.resultCode(), terminal.retryable(), terminal.latencyMs(),
                timestamp(terminal.finishedAt()), expected.accountId(),
                expected.id()) != 1) return false;
        if (assistant != null) insertMessage(assistant);
        insertUsage(usage);
        if (evidence != null) insertEvidence(evidence);
        var period = expected.createdAt().atZone(java.time.ZoneOffset.UTC)
                .toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        if (jdbc.update("""
                UPDATE un_platform_ai_quota_bucket SET used_tokens=used_tokens+?,
                  reserved_tokens=reserved_tokens-?,running_count=running_count-1,
                  revision=revision+1,updated_at=?
                WHERE account_id=? AND policy_version_id=? AND period_start=?
                  AND reserved_tokens>=? AND running_count>0
                """, usage.totalTokens(), expected.reservedTokens(),
                timestamp(terminal.finishedAt()), expected.accountId(),
                expected.policyVersionId(), timestamp(period),
                expected.reservedTokens()) != 1) {
            throw new IllegalStateException("Platform AI quota completion failed");
        }
        insertAuditRow(event);
        return true;
    }

    @Override
    public void insertAudit(PlatformAiConversation.AuditEvent event) {
        insertAuditRow(event);
    }

    private void insertVersion(PlatformAiPolicy.Version value) {
        var settings = value.settings();
        jdbc.update("""
                INSERT INTO un_platform_ai_policy_version(
                  id,policy_id,version_no,provider_id,provider_version,model_code,
                  snapshot_json,max_systems,daily_request_quota,daily_token_quota,
                  max_concurrency,strict_redaction,data_residency,prompt_version,
                  snapshot_hash,enabled,published_at,published_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.policyId(), value.versionNumber(),
                value.providerId(), value.providerVersion(), value.model(),
                encode(settings), settings.maxSystems(),
                settings.dailyRequestQuota(), settings.dailyTokenQuota(),
                settings.maxConcurrency(), settings.strictRedaction(),
                settings.dataResidency().name(), settings.promptVersion(),
                value.snapshotHash(), settings.enabled(),
                timestamp(value.publishedAt()), value.publishedBy());
    }

    private void insertTurn(PlatformAiConversation.Turn value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_turn(
                  id,scope,account_id,session_id,policy_version_id,provider_id,
                  provider_version,authorization_epoch,operation,status,
                  request_summary,request_hash,plan_hash,response_summary,
                  response_hash,returned_systems,result_code,retryable,
                  reserved_tokens,latency_ms,request_id,trace_id,created_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.sessionId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.authorizationEpoch(),
                value.operation(), value.status().name(), value.requestSummary(),
                value.requestHash(), value.planHash(), value.responseSummary(),
                value.responseHash(), value.returnedSystems(), value.resultCode(),
                value.retryable(), value.reservedTokens(), value.latencyMs(),
                value.requestId(), value.traceId(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertMessage(PlatformAiConversation.Message value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_message(
                  id,scope,account_id,session_id,turn_id,role,redacted_summary,
                  content_hash,content_length,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.sessionId(), value.turnId(), value.role().name(),
                value.redactedSummary(), value.contentHash(), value.contentLength(),
                timestamp(value.createdAt()));
    }

    private void insertUsage(PlatformAiConversation.Usage value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_usage(
                  id,scope,account_id,turn_id,policy_version_id,provider_id,
                  call_count,prompt_tokens,completion_tokens,total_tokens,
                  latency_ms,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.turnId(), value.policyVersionId(), value.providerId(),
                value.callCount(), value.promptTokens(), value.completionTokens(),
                value.totalTokens(), value.latencyMs(), timestamp(value.createdAt()));
    }

    private void insertEvidence(PlatformAiConversation.Evidence value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_evidence(
                  id,scope,account_id,turn_id,evidence_type,plan_hash,
                  projection_json,projection_hash,returned_systems,result_code,
                  created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.turnId(), value.evidenceType(), value.planHash(),
                value.projectionJson(), value.projectionHash(),
                value.returnedSystems(), value.resultCode(),
                timestamp(value.createdAt()));
    }

    private void insertAuditRow(PlatformAiConversation.AuditEvent value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_audit_event(
                  id,scope,account_id,aggregate_type,aggregate_id,event_type,
                  result_code,request_id,trace_id,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.aggregateType(), value.aggregateId(), value.eventType(),
                value.resultCode(), value.requestId(), value.traceId(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    private void insertTaskProposal(PlatformAiTaskProposal value) {
        var preview = value.preview();
        var sealed = value.sealedCommand();
        var result = value.result();
        jdbc.update("""
                INSERT INTO un_platform_ai_task_proposal(
                  id,scope,account_id,session_id,turn_id,policy_version_id,
                  provider_id,provider_version,authorization_epoch,prompt_version,
                  plan_hash,state,revision,title_summary,description_summary,due_at,
                  priority,confidence,clarification_summary,sealed_ciphertext,
                  sealed_key_version,sealed_hash,expires_at,confirmed_by,task_id,
                  task_title_summary,task_description_summary,task_due_at,
                  task_priority,task_status,task_source,task_created_at,result_code,
                  owner_request_id,owner_trace_id,created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.planHash(),
                value.state().name(), value.revision(),
                preview == null ? null : "[title:redacted]",
                preview == null || preview.description() == null
                        ? null : "[description:redacted]",
                preview == null || preview.dueAt() == null
                        ? null : timestamp(preview.dueAt()),
                preview == null ? null : preview.priority().name(),
                value.confidence(), value.clarification() == null
                        ? null : "[clarification:redacted]",
                sealed == null ? null : sealed.ciphertext(),
                sealed == null ? null : sealed.keyVersion(),
                sealed == null ? null : sealed.commandHash(),
                timestamp(value.expiresAt()), value.actedBy(),
                result == null ? null : Long.parseLong(result.taskId()),
                result == null ? null : "[title:redacted]",
                result == null || result.description() == null
                        ? null : "[description:redacted]",
                result == null || result.dueAt() == null
                        ? null : timestamp(result.dueAt()),
                result == null ? null : result.priority().name(),
                result == null ? null : result.status(),
                result == null ? null : result.source(),
                result == null ? null : timestamp(result.createdAt()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertTaskProposalAttempt(PlatformAiTaskProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_task_proposal_attempt(
                  id,scope,account_id,session_id,turn_id,policy_version_id,
                  provider_id,provider_version,proposal_id,action,request_key,
                  request_hash,status,result_hash,result_code,created_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.proposalId(),
                value.action(), value.requestKey(), value.requestHash(),
                value.status().name(), value.resultHash(), value.resultCode(),
                timestamp(value.createdAt()), value.finishedAt() == null
                        ? null : timestamp(value.finishedAt()));
    }

    private void insertTaskProposalEvent(PlatformAiTaskProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_platform_ai_task_proposal_event(
                  id,scope,account_id,session_id,turn_id,policy_version_id,
                  provider_id,provider_version,proposal_id,attempt_id,event_type,
                  from_state,to_state,revision,actor_account_id,request_id,trace_id,
                  result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.scope().name(), value.accountId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.proposalId(),
                value.attemptId(), value.eventType(),
                value.fromState() == null ? null : value.fromState().name(),
                value.toState().name(), value.revision(), value.actorAccountId(),
                value.requestId(), value.traceId(), value.resultCode(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    private int updateTaskProposal(
            PlatformAiTaskProposal expected,
            PlatformAiTaskProposal value) {
        var result = value.result();
        return jdbc.update("""
                UPDATE un_platform_ai_task_proposal SET state=?,revision=?,
                  confirmed_by=?,task_id=?,task_title_summary=?,
                  task_description_summary=?,task_due_at=?,task_priority=?,
                  task_status=?,task_source=?,task_created_at=?,result_code=?,
                  owner_request_id=?,owner_trace_id=?,updated_at=?,finished_at=?
                WHERE account_id=? AND session_id=? AND id=? AND state=? AND revision=?
                """, value.state().name(), value.revision(), value.actedBy(),
                result == null ? null : Long.parseLong(result.taskId()),
                result == null ? null : "[title:redacted]",
                result == null || result.description() == null
                        ? null : "[description:redacted]",
                result == null || result.dueAt() == null
                        ? null : timestamp(result.dueAt()),
                result == null ? null : result.priority().name(),
                result == null ? null : result.status(),
                result == null ? null : result.source(),
                result == null ? null : timestamp(result.createdAt()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null
                        ? null : timestamp(value.finishedAt()),
                expected.accountId(), expected.sessionId(), expected.id(),
                expected.state().name(), expected.revision());
    }

    private PlatformAiProvider mapProvider(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiProvider(
                value.getLong("id"), value.getString("code"),
                value.getString("name"), value.getString("base_url"),
                value.getString("model_code"), value.getString("secret_ref"),
                value.getInt("timeout_seconds"), value.getBoolean("enabled"),
                value.getLong("revision"), instant(value, "created_at"),
                value.getLong("created_by"), instant(value, "updated_at"),
                value.getLong("updated_by"));
    }

    private PlatformAiPolicy.Draft mapDraft(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiPolicy.Draft(
                value.getLong("id"), value.getLong("revision"),
                PlatformAiPolicy.DraftStatus.valueOf(value.getString("status")),
                value.getLong("provider_id"), value.getLong("provider_version"),
                decodeSettings(value.getString("draft_json")),
                value.getString("draft_hash"), nullableLong(value, "active_version_id"),
                instant(value, "updated_at"), value.getLong("updated_by"));
    }

    private PlatformAiPolicy.Check mapCheck(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiPolicy.Check(
                value.getLong("id"), value.getLong("policy_id"),
                value.getLong("draft_revision"), value.getString("draft_hash"),
                decode(value.getString("issues_json"), ISSUES),
                instant(value, "checked_at"), value.getLong("checked_by"));
    }

    private PlatformAiPolicy.Version mapVersion(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiPolicy.Version(
                value.getLong("id"), value.getLong("policy_id"),
                value.getInt("version_no"), value.getLong("provider_id"),
                value.getLong("provider_version"), value.getString("model_code"),
                decodeSettings(value.getString("snapshot_json")),
                value.getString("snapshot_hash"), instant(value, "published_at"),
                value.getLong("published_by"));
    }

    private PlatformAiPolicy.PublishReplay mapReplay(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiPolicy.PublishReplay(
                value.getLong("id"), value.getLong("policy_id"),
                value.getString("request_key"), value.getString("request_hash"),
                value.getLong("version_id"), instant(value, "created_at"));
    }

    private PlatformAiConversation.Session mapSession(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiConversation.Session(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getString("title_summary"),
                PlatformAiConversation.SessionStatus.valueOf(
                        value.getString("status")),
                instant(value, "created_at"), instant(value, "updated_at"));
    }

    private PlatformAiConversation.Message mapMessage(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiConversation.Message(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getLong("session_id"), value.getLong("turn_id"),
                PlatformAiConversation.Role.valueOf(value.getString("role")),
                value.getString("redacted_summary"),
                value.getString("content_hash"), value.getInt("content_length"),
                instant(value, "created_at"));
    }

    private PlatformAiConversation.Turn mapTurn(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiConversation.Turn(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getLong("session_id"), value.getLong("policy_version_id"),
                value.getLong("provider_id"), value.getLong("provider_version"),
                value.getLong("authorization_epoch"), value.getString("operation"),
                PlatformAiConversation.TurnStatus.valueOf(value.getString("status")),
                value.getString("request_summary"), value.getString("request_hash"),
                value.getString("plan_hash"), value.getString("response_summary"),
                value.getString("response_hash"), value.getInt("returned_systems"),
                value.getString("result_code"), value.getBoolean("retryable"),
                value.getInt("reserved_tokens"), value.getLong("latency_ms"),
                value.getString("request_id"), value.getString("trace_id"),
                instant(value, "created_at"), nullableInstant(value, "finished_at"));
    }

    private PlatformAiConversation.Evidence mapEvidence(ResultSet value, int row)
            throws SQLException {
        return new PlatformAiConversation.Evidence(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getLong("turn_id"), value.getString("evidence_type"),
                value.getString("plan_hash"), value.getString("projection_json"),
                value.getString("projection_hash"),
                value.getInt("returned_systems"), value.getString("result_code"),
                instant(value, "created_at"));
    }

    private PlatformAiTaskProposal mapTaskProposal(ResultSet value, int row)
            throws SQLException {
        PlatformAiTaskProposal.Preview preview = null;
        if (value.getString("title_summary") != null) {
            preview = new PlatformAiTaskProposal.Preview(
                    value.getString("title_summary"),
                    value.getString("description_summary"),
                    nullableInstant(value, "due_at"),
                    PlatformAiTaskProposal.Priority.valueOf(
                            value.getString("priority")));
        }
        PlatformAiTaskProposal.SealedCommand sealed = null;
        if (value.getString("sealed_ciphertext") != null) {
            sealed = new PlatformAiTaskProposal.SealedCommand(
                    value.getString("sealed_ciphertext"),
                    value.getString("sealed_key_version"),
                    value.getString("sealed_hash"));
        }
        PlatformAiTaskProposal.Result result = null;
        var taskId = nullableLong(value, "task_id");
        if (taskId != null) {
            result = new PlatformAiTaskProposal.Result(
                    Long.toString(taskId), value.getString("task_title_summary"),
                    value.getString("task_description_summary"),
                    nullableInstant(value, "task_due_at"),
                    PlatformAiTaskProposal.Priority.valueOf(
                            value.getString("task_priority")),
                    value.getString("task_status"),
                    value.getString("task_source"),
                    instant(value, "task_created_at"));
        }
        return new PlatformAiTaskProposal(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getLong("session_id"), value.getLong("turn_id"),
                value.getLong("policy_version_id"), value.getLong("provider_id"),
                value.getLong("provider_version"),
                value.getLong("authorization_epoch"),
                value.getString("prompt_version"), value.getString("plan_hash"),
                PlatformAiTaskProposal.State.valueOf(value.getString("state")),
                value.getLong("revision"), preview, value.getDouble("confidence"),
                value.getString("clarification_summary"), sealed,
                instant(value, "expires_at"), nullableLong(value, "confirmed_by"),
                result, value.getString("result_code"),
                value.getString("owner_request_id"),
                value.getString("owner_trace_id"), instant(value, "created_at"),
                instant(value, "updated_at"), nullableInstant(value, "finished_at"));
    }

    private PlatformAiTaskProposal.Attempt mapTaskProposalAttempt(
            ResultSet value, int row) throws SQLException {
        return new PlatformAiTaskProposal.Attempt(
                value.getLong("id"), PlatformAiConversation.Scope.valueOf(
                value.getString("scope")), value.getLong("account_id"),
                value.getLong("session_id"), value.getLong("turn_id"),
                value.getLong("policy_version_id"), value.getLong("provider_id"),
                value.getLong("provider_version"), value.getLong("proposal_id"),
                value.getString("action"), value.getString("request_key"),
                value.getString("request_hash"),
                PlatformAiTaskProposal.State.valueOf(value.getString("status")),
                value.getString("result_hash"), value.getString("result_code"),
                instant(value, "created_at"), nullableInstant(value, "finished_at"));
    }

    private PlatformAiPolicy.Settings decodeSettings(String value) {
        return decode(value, new TypeReference<>() { });
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot decode platform AI JSON", failure);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode platform AI JSON", failure);
        }
    }

    private <T> Optional<T> one(
            String sql, RowMapper<T> mapper, Object... arguments) {
        var values = jdbc.query(sql, mapper, arguments);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet value, String column)
            throws SQLException {
        return value.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet value, String column)
            throws SQLException {
        var timestamp = value.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(ResultSet value, String column)
            throws SQLException {
        var number = value.getObject(column, Long.class);
        return value.wasNull() ? null : number;
    }

    private record PolicyLock(long revision, String status, String draftHash) { }

    private record QuotaState(
            long requestCount, long usedTokens,
            long reservedTokens, long runningCount) { }

    private record ProposalLock(String state, long revision) { }
}
