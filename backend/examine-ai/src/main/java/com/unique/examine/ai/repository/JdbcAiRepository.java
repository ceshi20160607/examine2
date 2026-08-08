package com.unique.examine.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiConfirmation;
import com.unique.examine.ai.domain.AiConfigurationFieldProposal;
import com.unique.examine.ai.domain.AiConfigurationArtifactProposal;
import com.unique.examine.ai.domain.AiFillProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.domain.AiWorkProposal;
import com.unique.examine.ai.domain.AiGeneratedDraftProposal;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Repository
public class JdbcAiRepository implements AiRepository {
    private static final TypeReference<List<AiPolicy.Issue>> ISSUES =
            new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcAiRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<AiProvider> provider(
            long systemId, long tenantId, long providerId) {
        return one("""
                SELECT * FROM un_ai_provider
                WHERE system_id=? AND tenant_id=? AND id=?
                """, this::provider, systemId, tenantId, providerId);
    }

    @Override
    public Optional<AiProvider> providerByCode(
            long systemId, long tenantId, String code) {
        return one("""
                SELECT * FROM un_ai_provider
                WHERE system_id=? AND tenant_id=? AND provider_code=?
                """, this::provider, systemId, tenantId, code);
    }

    @Override
    public List<AiProvider> providers(
            long systemId, long tenantId, int offset, int limit) {
        return jdbc.query("""
                SELECT * FROM un_ai_provider
                WHERE system_id=? AND tenant_id=?
                ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?
                """, this::provider, systemId, tenantId, limit, offset);
    }

    @Override
    public void insertProvider(AiProvider value) {
        jdbc.update("""
                INSERT INTO un_ai_provider(
                  id,system_id,tenant_id,provider_code,provider_name,base_url,
                  model_code,secret_ref,timeout_seconds,enabled,created_at,
                  created_by,updated_at,updated_by,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.code(), value.name(), value.baseUrl(), value.model(),
                value.secretRef(), value.timeoutSeconds(), value.enabled(),
                timestamp(value.createdAt()), value.createdBy(),
                timestamp(value.updatedAt()), value.updatedBy(), value.version());
    }

    @Override
    public boolean updateProvider(AiProvider value, long expectedVersion) {
        return jdbc.update("""
                UPDATE un_ai_provider SET
                  provider_name=?,base_url=?,model_code=?,secret_ref=?,
                  timeout_seconds=?,enabled=?,updated_at=?,updated_by=?,version=?
                WHERE system_id=? AND tenant_id=? AND id=? AND version=?
                """, value.name(), value.baseUrl(), value.model(),
                value.secretRef(), value.timeoutSeconds(), value.enabled(),
                timestamp(value.updatedAt()), value.updatedBy(), value.version(),
                value.systemId(), value.tenantId(), value.id(), expectedVersion) == 1;
    }

    @Override
    public Optional<AiPolicy.Draft> policyDraft(long systemId, long tenantId) {
        return one("""
                SELECT * FROM un_ai_agent_policy
                WHERE system_id=? AND tenant_id=?
                """, this::draft, systemId, tenantId);
    }

    @Override
    public void insertPolicyDraft(AiPolicy.Draft value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_policy(
                  id,system_id,tenant_id,draft_revision,draft_status,provider_id,
                  provider_version,draft_json,max_rows,enabled,redaction_mode,
                  prompt_version,draft_hash,active_version_id,updated_at,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.revision(), value.status().name(), value.providerId(),
                value.providerVersion(), policyJson(value), value.maxRows(),
                value.enabled(), value.redactionMode().name(),
                value.promptVersion(), value.draftHash(), value.activeVersionId(),
                timestamp(value.updatedAt()), value.updatedBy());
    }

    @Override
    public boolean updatePolicyDraft(
            AiPolicy.Draft value, long expectedRevision) {
        return jdbc.update("""
                UPDATE un_ai_agent_policy SET
                  draft_revision=?,draft_status=?,provider_id=?,provider_version=?,
                  draft_json=?,max_rows=?,enabled=?,redaction_mode=?,prompt_version=?,
                  draft_hash=?,active_version_id=?,updated_at=?,updated_by=?
                WHERE system_id=? AND tenant_id=? AND id=? AND draft_revision=?
                """, value.revision(), value.status().name(), value.providerId(),
                value.providerVersion(), policyJson(value), value.maxRows(),
                value.enabled(), value.redactionMode().name(), value.promptVersion(),
                value.draftHash(), value.activeVersionId(),
                timestamp(value.updatedAt()), value.updatedBy(), value.systemId(),
                value.tenantId(), value.id(), expectedRevision) == 1;
    }

    @Override
    public void insertPolicyCheck(AiPolicy.Check value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_policy_check(
                  id,system_id,tenant_id,policy_id,draft_revision,draft_hash,
                  status,issues_json,blocker_count,checked_at,checked_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.policyId(), value.draftRevision(), value.draftHash(),
                value.valid() ? "PASSED" : "FAILED", encode(value.issues()),
                value.issues().stream().filter(issue ->
                        issue.severity() == AiPolicy.Severity.BLOCKER).count(),
                timestamp(value.checkedAt()), value.checkedBy());
    }

    @Override
    public Optional<AiPolicy.Check> policyCheck(
            long systemId, long tenantId, long policyId, long draftRevision) {
        return one("""
                SELECT * FROM un_ai_agent_policy_check
                WHERE system_id=? AND tenant_id=? AND policy_id=?
                  AND draft_revision=?
                """, this::check, systemId, tenantId, policyId, draftRevision);
    }

    @Override
    public Optional<AiPolicy.Version> activePolicy(
            long systemId, long tenantId) {
        return one("""
                SELECT v.* FROM un_ai_agent_policy p
                JOIN un_ai_agent_policy_version v
                  ON v.system_id=p.system_id AND v.tenant_id=p.tenant_id
                 AND v.id=p.active_version_id
                WHERE p.system_id=? AND p.tenant_id=?
                """, this::version, systemId, tenantId);
    }

    @Override
    public Optional<AiPolicy.Version> policyVersion(
            long systemId, long tenantId, long versionId) {
        return one("""
                SELECT * FROM un_ai_agent_policy_version
                WHERE system_id=? AND tenant_id=? AND id=?
                """, this::version, systemId, tenantId, versionId);
    }

    @Override
    public int nextPolicyVersionNumber(
            long systemId, long tenantId, long policyId) {
        var value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no),0)+1
                FROM un_ai_agent_policy_version
                WHERE system_id=? AND tenant_id=? AND policy_id=?
                """, Integer.class, systemId, tenantId, policyId);
        return Objects.requireNonNull(value);
    }

    @Override
    public Optional<AiPolicy.PublishReplay> publishReplay(
            long systemId, long tenantId, long policyId, String requestKey) {
        return one("""
                SELECT * FROM un_ai_agent_policy_publish
                WHERE system_id=? AND tenant_id=? AND policy_id=? AND request_key=?
                """, this::replay, systemId, tenantId, policyId, requestKey);
    }

    @Override
    @Transactional
    public boolean publishPolicy(
            AiPolicy.Draft expected,
            AiPolicy.Draft published,
            AiPolicy.Version version,
            AiPolicy.PublishReplay replay
    ) {
        var locked = jdbc.query("""
                SELECT draft_revision,draft_status,draft_hash
                FROM un_ai_agent_policy
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new LockedDraft(
                        result.getLong("draft_revision"),
                        result.getString("draft_status"),
                        result.getString("draft_hash")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().status().equals(expected.status().name())
                || !locked.getFirst().hash().equals(expected.draftHash())) {
            return false;
        }
        insertVersion(version);
        jdbc.update("""
                INSERT INTO un_ai_agent_policy_publish(
                  id,system_id,tenant_id,policy_id,request_key,request_hash,
                  version_id,created_at) VALUES(?,?,?,?,?,?,?,?)
                """, replay.id(), replay.systemId(), replay.tenantId(),
                replay.policyId(), replay.requestKey(), replay.requestHash(),
                replay.versionId(), timestamp(replay.createdAt()));
        var updated = jdbc.update("""
                UPDATE un_ai_agent_policy SET draft_status=?,active_version_id=?,
                  updated_at=?,updated_by=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND draft_revision=? AND draft_status=? AND draft_hash=?
                """, published.status().name(), published.activeVersionId(),
                timestamp(published.updatedAt()), published.updatedBy(),
                expected.systemId(), expected.tenantId(), expected.id(),
                expected.revision(), expected.status().name(),
                expected.draftHash());
        if (updated != 1) {
            throw new IllegalStateException(
                    "AI policy publish lost its locked draft row");
        }
        return true;
    }

    @Override
    public void insertSession(AiConversation.Session value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_session(
                  id,system_id,tenant_id,member_id,policy_version_id,provider_id,
                  provider_version,model_code,prompt_version,authorization_epoch,
                  status,title_summary,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.memberId(), value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.model(), value.promptVersion(),
                value.authorizationEpoch(), value.status().name(),
                value.titleSummary(), timestamp(value.createdAt()),
                timestamp(value.updatedAt()));
    }

    @Override
    public Optional<AiConversation.Session> session(
            long systemId, long tenantId, long memberId, long sessionId) {
        return one("""
                SELECT * FROM un_ai_agent_session
                WHERE system_id=? AND tenant_id=? AND member_id=? AND id=?
                """, this::session, systemId, tenantId, memberId, sessionId);
    }

    @Override
    public List<AiConversation.Session> sessions(
            long systemId, long tenantId, long memberId, int offset, int limit) {
        return jdbc.query("""
                SELECT * FROM un_ai_agent_session
                WHERE system_id=? AND tenant_id=? AND member_id=?
                ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?
                """, this::session, systemId, tenantId, memberId, limit, offset);
    }

    @Override
    public void insertMessage(AiConversation.Message value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_message(
                  id,system_id,tenant_id,session_id,turn_id,role,redacted_summary,
                  content_hash,character_count,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.sessionId(), value.turnId(), value.role().name(),
                value.redactedSummary(), value.contentHash(), value.characterCount(),
                timestamp(value.createdAt()));
    }

    @Override
    public void insertTurn(AiConversation.Turn value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_turn(
                  id,system_id,tenant_id,session_id,policy_version_id,provider_id,
                  provider_version,authorization_epoch,status,request_summary,
                  request_hash,plan_hash,response_summary,response_hash,returned_rows,
                  result_code,retryable,latency_ms,request_id,trace_id,created_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, turnArguments(value));
    }

    @Override
    public boolean finishTurn(AiConversation.Turn value) {
        return jdbc.update("""
                UPDATE un_ai_agent_turn SET status=?,plan_hash=?,response_summary=?,
                  response_hash=?,returned_rows=?,result_code=?,retryable=?,
                  latency_ms=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND status='RUNNING'
                """, value.status().name(), value.planHash(),
                value.responseSummary(), value.responseHash(), value.returnedRows(),
                value.resultCode(), value.retryable(), value.latencyMs(),
                timestamp(value.finishedAt()), value.systemId(), value.tenantId(),
                value.id()) == 1;
    }

    @Override
    public void insertToolCall(AiConversation.ToolCall value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_tool_call(
                  id,system_id,tenant_id,turn_id,tool_name,status,request_hash,
                  response_hash,result_count,latency_ms,result_code,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.turnId(), value.toolName(), value.status().name(),
                value.requestHash(), value.responseHash(), value.resultCount(),
                value.latencyMs(), value.resultCode(), timestamp(value.createdAt()));
    }

    @Override
    public void insertUsage(AiConversation.Usage value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_usage(
                  id,system_id,tenant_id,turn_id,provider_calls,prompt_tokens,
                  completion_tokens,total_tokens,provider_latency_ms,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.turnId(), value.providerCalls(), value.promptTokens(),
                value.completionTokens(), value.totalTokens(),
                value.providerLatencyMs(), timestamp(value.createdAt()));
    }

    @Override
    public List<AiConversation.Message> messages(
            long systemId, long tenantId, long sessionId, int limit) {
        return jdbc.query("""
                SELECT * FROM un_ai_agent_message
                WHERE system_id=? AND tenant_id=? AND session_id=?
                ORDER BY created_at,id LIMIT ?
                """, this::message, systemId, tenantId, sessionId, limit);
    }

    @Override
    public List<AiConversation.Turn> turns(
            long systemId, long tenantId, long sessionId, int limit) {
        return jdbc.query("""
                SELECT * FROM un_ai_agent_turn
                WHERE system_id=? AND tenant_id=? AND session_id=?
                ORDER BY created_at,id LIMIT ?
                """, this::turn, systemId, tenantId, sessionId, limit);
    }

    @Override
    @Transactional
    public void insertConfirmation(
            AiConfirmation value, AiConfirmation.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_agent_confirmation(
                  id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,prompt_version,
                  authorization_epoch,operation,module_code,schema_version_id,
                  record_id,expected_record_version,plan_hash,preview_json,
                  confidence_json,clarifications_json,sealed_ciphertext,
                  sealed_key_version,sealed_command_hash,state,revision,expires_at,
                  confirmed_by,result_json,result_code,owner_request_id,
                  owner_trace_id,created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, confirmationArguments(value));
        insertConfirmationEvent(event);
    }

    @Override
    public Optional<AiConfirmation> confirmation(
            long systemId, long tenantId, long confirmationId) {
        return one("""
                SELECT * FROM un_ai_agent_confirmation
                WHERE system_id=? AND tenant_id=? AND id=?
                """, this::confirmation, systemId, tenantId, confirmationId);
    }

    @Override
    public Optional<AiConfirmation> confirmationByTurn(
            long systemId, long tenantId, long turnId) {
        return one("""
                SELECT * FROM un_ai_agent_confirmation
                WHERE system_id=? AND tenant_id=? AND turn_id=?
                """, this::confirmation, systemId, tenantId, turnId);
    }

    @Override
    public Optional<AiConfirmation.Attempt> confirmationAttempt(
            long systemId, long tenantId, long confirmationId, String requestKey) {
        return one("""
                SELECT * FROM un_ai_agent_confirmation_attempt
                WHERE system_id=? AND tenant_id=? AND confirmation_id=?
                  AND request_key=?
                """, this::confirmationAttempt, systemId, tenantId,
                confirmationId, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimConfirmation(
            AiConfirmation expected,
            AiConfirmation executing,
            AiConfirmation.Attempt attempt,
            AiConfirmation.Event event
    ) {
        var existingKey = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_ai_agent_confirmation_attempt
                WHERE system_id=? AND tenant_id=? AND confirmation_id=?
                  AND request_key=?
                """, Integer.class, expected.systemId(), expected.tenantId(),
                expected.id(), attempt.requestKey());
        if (existingKey != null && existingKey > 0) return ClaimResult.KEY_EXISTS;
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_agent_confirmation
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                        result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        jdbc.update("""
                INSERT INTO un_ai_agent_confirmation_attempt(
                  id,system_id,tenant_id,confirmation_id,request_key,request_hash,
                  status,result_hash,result_code,created_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, attemptArguments(attempt));
        if (updateConfirmation(expected, executing) != 1) {
            throw new IllegalStateException("AI confirmation claim lost locked row");
        }
        insertConfirmationEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishConfirmation(
            AiConfirmation expected,
            AiConfirmation terminal,
            AiConfirmation.Attempt terminalAttempt,
            AiConfirmation.Event event
    ) {
        if (updateConfirmation(expected, terminal) != 1) return false;
        var attemptUpdated = jdbc.update("""
                UPDATE un_ai_agent_confirmation_attempt SET
                  status=?,result_hash=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND confirmation_id=? AND request_hash=? AND status='EXECUTING'
                """, terminalAttempt.status().name(), terminalAttempt.resultHash(),
                terminalAttempt.resultCode(), timestamp(terminalAttempt.finishedAt()),
                terminalAttempt.systemId(), terminalAttempt.tenantId(),
                terminalAttempt.id(), terminalAttempt.confirmationId(),
                terminalAttempt.requestHash());
        if (attemptUpdated != 1) {
            throw new IllegalStateException("AI confirmation attempt is missing");
        }
        insertConfirmationEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean rejectConfirmation(
            AiConfirmation expected, AiConfirmation rejected,
            AiConfirmation.Event event) {
        if (updateConfirmation(expected, rejected) != 1) return false;
        insertConfirmationEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean expireConfirmation(
            AiConfirmation expected, AiConfirmation expired,
            AiConfirmation.Event event) {
        if (updateConfirmation(expected, expired) != 1) return false;
        insertConfirmationEvent(event);
        return true;
    }

    private int updateConfirmation(
            AiConfirmation expected, AiConfirmation value) {
        return jdbc.update("""
                UPDATE un_ai_agent_confirmation SET
                  state=?,revision=?,confirmed_by=?,result_json=?,result_code=?,
                  owner_request_id=?,owner_trace_id=?,updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.confirmedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null
                        ? null : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertConfirmationEvent(AiConfirmation.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_agent_confirmation_event(
                  id,system_id,tenant_id,confirmation_id,event_type,from_state,
                  to_state,revision,actor_member_id,request_id,trace_id,
                  result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, event.id(), event.systemId(), event.tenantId(),
                event.confirmationId(), event.eventType(),
                event.fromState() == null ? null : event.fromState().name(),
                event.toState().name(), event.revision(), event.actorMemberId(),
                event.requestId(), event.traceId(), event.resultCode(),
                event.eventHash(), timestamp(event.createdAt()));
    }

    @Override
    @Transactional
    public void insertConfigurationFieldProposal(
            AiConfigurationFieldProposal value,
            AiConfigurationFieldProposal.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_config_field_proposal(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,authorization_epoch,
                  prompt_version,module_code,plan_hash,preview_json,confidence,
                  clarification_summary,sealed_ciphertext,sealed_key_version,
                  sealed_command_hash,state,revision,expires_at,acted_by,result_json,
                  result_code,owner_request_id,owner_trace_id,created_at,updated_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, configurationFieldProposalArguments(value));
        insertConfigurationFieldEvent(event);
    }

    @Override
    public Optional<AiConfigurationFieldProposal> configurationFieldProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return one("""
                SELECT * FROM un_ai_config_field_proposal
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND session_id=? AND id=?
                """, this::configurationFieldProposal, systemId, tenantId,
                memberId, sessionId, proposalId);
    }

    @Override
    public Optional<AiConfigurationFieldProposal> configurationFieldProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return one("""
                SELECT * FROM un_ai_config_field_proposal
                WHERE system_id=? AND tenant_id=? AND turn_id=?
                """, this::configurationFieldProposal,
                systemId, tenantId, turnId);
    }

    @Override
    public Optional<AiConfigurationFieldProposal.Attempt> configurationFieldAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return one("""
                SELECT * FROM un_ai_config_field_attempt
                WHERE system_id=? AND tenant_id=? AND proposal_id=?
                  AND action=? AND request_key=?
                """, this::configurationFieldAttempt, systemId, tenantId,
                proposalId, action, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal executing,
            AiConfigurationFieldProposal.Attempt attempt,
            AiConfigurationFieldProposal.Event event) {
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_config_field_proposal
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (configurationFieldAttempt(expected.systemId(), expected.tenantId(),
                expected.id(), attempt.action(), attempt.requestKey()).isPresent()) {
            return ClaimResult.KEY_EXISTS;
        }
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        insertConfigurationFieldAttempt(attempt);
        if (updateConfigurationFieldProposal(expected, executing) != 1) {
            throw new IllegalStateException(
                    "AI configuration field claim lost locked row");
        }
        insertConfigurationFieldEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Attempt attempt,
            AiConfigurationFieldProposal.Event event) {
        if (updateConfigurationFieldProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_ai_config_field_attempt SET
                  status=?,result_hash=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND proposal_id=?
                  AND request_hash=? AND status='EXECUTING'
                """, attempt.status().name(), attempt.resultHash(),
                attempt.resultCode(), timestamp(attempt.finishedAt()),
                attempt.systemId(), attempt.tenantId(), attempt.id(),
                attempt.proposalId(), attempt.requestHash()) != 1) {
            throw new IllegalStateException(
                    "AI configuration field terminal attempt is missing");
        }
        insertConfigurationFieldEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal terminal,
            AiConfigurationFieldProposal.Event event) {
        if (updateConfigurationFieldProposal(expected, terminal) != 1) return false;
        insertConfigurationFieldEvent(event);
        return true;
    }

    private int updateConfigurationFieldProposal(
            AiConfigurationFieldProposal expected,
            AiConfigurationFieldProposal value) {
        return jdbc.update("""
                UPDATE un_ai_config_field_proposal SET
                  state=?,revision=?,acted_by=?,result_json=?,result_code=?,
                  owner_request_id=?,owner_trace_id=?,updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertConfigurationFieldAttempt(
            AiConfigurationFieldProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_ai_config_field_attempt(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,action,
                  request_key,request_hash,status,result_hash,result_code,created_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), value.status().name(), value.resultHash(),
                value.resultCode(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertConfigurationFieldEvent(
            AiConfigurationFieldProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_ai_config_field_event(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,
                  attempt_id,event_type,from_state,to_state,revision,
                  actor_member_id,request_id,trace_id,result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.attemptId(), value.eventType(),
                value.fromState() == null ? null : value.fromState().name(),
                value.toState().name(), value.revision(), value.actorMemberId(),
                value.requestId(), value.traceId(), value.resultCode(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    @Override
    @Transactional
    public void insertConfigurationArtifactProposal(
            AiConfigurationArtifactProposal value,
            AiConfigurationArtifactProposal.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_config_artifact_proposal(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,authorization_epoch,
                  prompt_version,artifact_kind,module_code,plan_hash,preview_json,
                  confidence,clarification_summary,sealed_ciphertext,sealed_key_version,
                  sealed_command_hash,state,revision,expires_at,acted_by,result_json,
                  result_code,request_id,trace_id,created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, artifactProposalArguments(value));
        insertArtifactEvent(event);
    }

    @Override
    public Optional<AiConfigurationArtifactProposal> configurationArtifactProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return one("""
                SELECT * FROM un_ai_config_artifact_proposal
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND session_id=? AND id=?
                """, this::artifactProposal, systemId, tenantId,
                memberId, sessionId, proposalId);
    }

    @Override
    public Optional<AiConfigurationArtifactProposal>
            configurationArtifactProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return one("""
                SELECT * FROM un_ai_config_artifact_proposal
                WHERE system_id=? AND tenant_id=? AND turn_id=?
                """, this::artifactProposal, systemId, tenantId, turnId);
    }

    @Override
    public Optional<AiConfigurationArtifactProposal.Attempt>
            configurationArtifactAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return one("""
                SELECT * FROM un_ai_config_artifact_attempt
                WHERE system_id=? AND tenant_id=? AND proposal_id=?
                  AND action=? AND request_key=?
                """, this::artifactAttempt, systemId, tenantId,
                proposalId, action, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal executing,
            AiConfigurationArtifactProposal.Attempt attempt,
            AiConfigurationArtifactProposal.Event event) {
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_config_artifact_proposal
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (configurationArtifactAttempt(expected.systemId(), expected.tenantId(),
                expected.id(), attempt.action(), attempt.requestKey()).isPresent()) {
            return ClaimResult.KEY_EXISTS;
        }
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        insertArtifactAttempt(attempt);
        if (updateArtifactProposal(expected, executing) != 1) {
            throw new IllegalStateException("AI artifact claim lost locked row");
        }
        insertArtifactEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Attempt attempt,
            AiConfigurationArtifactProposal.Event event) {
        if (updateArtifactProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_ai_config_artifact_attempt SET
                  status=?,result_hash=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND proposal_id=?
                  AND request_hash=? AND status='EXECUTING'
                """, attempt.status().name(), attempt.resultHash(),
                attempt.resultCode(), timestamp(attempt.finishedAt()),
                attempt.systemId(), attempt.tenantId(), attempt.id(),
                attempt.proposalId(), attempt.requestHash()) != 1) {
            throw new IllegalStateException("AI artifact terminal attempt is missing");
        }
        insertArtifactEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionConfigurationArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal terminal,
            AiConfigurationArtifactProposal.Event event) {
        if (updateArtifactProposal(expected, terminal) != 1) return false;
        insertArtifactEvent(event);
        return true;
    }

    private int updateArtifactProposal(
            AiConfigurationArtifactProposal expected,
            AiConfigurationArtifactProposal value) {
        return jdbc.update("""
                UPDATE un_ai_config_artifact_proposal SET
                  state=?,revision=?,acted_by=?,result_json=?,result_code=?,
                  request_id=?,trace_id=?,updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.requestId(), value.traceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertArtifactAttempt(
            AiConfigurationArtifactProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_ai_config_artifact_attempt(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,action,
                  request_key,request_hash,status,result_hash,result_code,created_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), value.status().name(), value.resultHash(),
                value.resultCode(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertArtifactEvent(AiConfigurationArtifactProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_ai_config_artifact_event(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,
                  attempt_id,event_type,from_state,to_state,revision,
                  actor_member_id,request_id,trace_id,result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.attemptId(), value.eventType(),
                value.fromState() == null ? null : value.fromState().name(),
                value.toState().name(), value.revision(), value.actorMemberId(),
                value.requestId(), value.traceId(), value.resultCode(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    @Override
    @Transactional
    public void insertWorkProposal(
            AiWorkProposal value, AiWorkProposal.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_work_proposal(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,authorization_epoch,
                  prompt_version,operation,plan_hash,state,revision,preview_json,
                  confidence,clarification_summary,command_ciphertext,
                  command_key_version,command_hash,expires_at,acted_by,result_json,
                  result_code,request_id,trace_id,created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, workProposalArguments(value));
        insertWorkEvent(event);
    }

    @Override
    public Optional<AiWorkProposal> workProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return one("""
                SELECT * FROM un_ai_work_proposal
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND session_id=? AND id=?
                """, this::workProposal, systemId, tenantId,
                memberId, sessionId, proposalId);
    }

    @Override
    public Optional<AiWorkProposal> workProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return one("""
                SELECT * FROM un_ai_work_proposal
                WHERE system_id=? AND tenant_id=? AND turn_id=?
                """, this::workProposal, systemId, tenantId, turnId);
    }

    @Override
    public Optional<AiWorkProposal.Attempt> workAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return one("""
                SELECT * FROM un_ai_work_attempt
                WHERE system_id=? AND tenant_id=? AND proposal_id=?
                  AND action=? AND request_key=?
                """, this::workAttempt, systemId, tenantId,
                proposalId, action, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal executing,
            AiWorkProposal.Attempt attempt,
            AiWorkProposal.Event event) {
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_work_proposal
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (workAttempt(expected.systemId(), expected.tenantId(), expected.id(),
                attempt.action(), attempt.requestKey()).isPresent()) {
            return ClaimResult.KEY_EXISTS;
        }
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        insertWorkAttempt(attempt);
        if (updateWorkProposal(expected, executing) != 1) {
            throw new IllegalStateException("AI Work proposal claim lost locked row");
        }
        insertWorkEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal terminal,
            AiWorkProposal.Attempt attempt,
            AiWorkProposal.Event event) {
        if (updateWorkProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_ai_work_attempt SET
                  status=?,result_hash=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND proposal_id=?
                  AND request_hash=? AND status='EXECUTING'
                """, attempt.status().name(), attempt.resultHash(),
                attempt.resultCode(), timestamp(attempt.finishedAt()),
                attempt.systemId(), attempt.tenantId(), attempt.id(),
                attempt.proposalId(), attempt.requestHash()) != 1) {
            throw new IllegalStateException("AI Work terminal attempt is missing");
        }
        insertWorkEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionWorkProposal(
            AiWorkProposal expected,
            AiWorkProposal terminal,
            AiWorkProposal.Event event) {
        if (updateWorkProposal(expected, terminal) != 1) return false;
        insertWorkEvent(event);
        return true;
    }

    private int updateWorkProposal(
            AiWorkProposal expected, AiWorkProposal value) {
        return jdbc.update("""
                UPDATE un_ai_work_proposal SET
                  state=?,revision=?,acted_by=?,result_json=?,result_code=?,
                  request_id=?,trace_id=?,updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.requestId(), value.traceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertWorkAttempt(AiWorkProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_ai_work_attempt(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,action,
                  request_key,request_hash,status,result_hash,result_code,created_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), value.status().name(), value.resultHash(),
                value.resultCode(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertWorkEvent(AiWorkProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_ai_work_event(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,
                  attempt_id,event_type,from_state,to_state,revision,
                  actor_member_id,request_id,trace_id,result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.attemptId(), value.eventType(),
                value.fromState() == null ? null : value.fromState().name(),
                value.toState().name(), value.revision(), value.actorMemberId(),
                value.requestId(), value.traceId(), value.resultCode(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    @Override
    @Transactional
    public void insertGeneratedDraftProposal(
            AiGeneratedDraftProposal value,
            AiGeneratedDraftProposal.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_generated_draft_proposal(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,authorization_epoch,
                  prompt_version,operation,plan_hash,state,revision,preview_json,
                  confidence,clarification_summary,command_ciphertext,
                  command_key_version,command_hash,expires_at,acted_by,result_json,
                  result_code,request_id,trace_id,created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, generatedDraftProposalArguments(value));
        insertGeneratedDraftEvent(event);
    }

    @Override
    public Optional<AiGeneratedDraftProposal> generatedDraftProposal(
            long systemId, long tenantId, long memberId,
            long sessionId, long proposalId) {
        return one("""
                SELECT * FROM un_ai_generated_draft_proposal
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND session_id=? AND id=?
                """, this::generatedDraftProposal, systemId, tenantId,
                memberId, sessionId, proposalId);
    }

    @Override
    public Optional<AiGeneratedDraftProposal> generatedDraftProposalByTurn(
            long systemId, long tenantId, long turnId) {
        return one("""
                SELECT * FROM un_ai_generated_draft_proposal
                WHERE system_id=? AND tenant_id=? AND turn_id=?
                """, this::generatedDraftProposal, systemId, tenantId, turnId);
    }

    @Override
    public Optional<AiGeneratedDraftProposal.Attempt> generatedDraftAttempt(
            long systemId, long tenantId, long proposalId,
            String action, String requestKey) {
        return one("""
                SELECT * FROM un_ai_generated_draft_attempt
                WHERE system_id=? AND tenant_id=? AND proposal_id=?
                  AND action=? AND request_key=?
                """, this::generatedDraftAttempt, systemId, tenantId,
                proposalId, action, requestKey);
    }

    @Override
    @Transactional
    public ClaimResult claimGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal executing,
            AiGeneratedDraftProposal.Attempt attempt,
            AiGeneratedDraftProposal.Event event) {
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_generated_draft_proposal
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (generatedDraftAttempt(
                expected.systemId(), expected.tenantId(), expected.id(),
                attempt.action(), attempt.requestKey()).isPresent()) {
            return ClaimResult.KEY_EXISTS;
        }
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        insertGeneratedDraftAttempt(attempt);
        if (updateGeneratedDraftProposal(expected, executing) != 1) {
            throw new IllegalStateException(
                    "AI generated-draft proposal claim lost locked row");
        }
        insertGeneratedDraftEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Attempt attempt,
            AiGeneratedDraftProposal.Event event) {
        if (updateGeneratedDraftProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_ai_generated_draft_attempt SET
                  status=?,result_hash=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND proposal_id=?
                  AND request_hash=? AND status='EXECUTING'
                """, attempt.status().name(), attempt.resultHash(),
                attempt.resultCode(), timestamp(attempt.finishedAt()),
                attempt.systemId(), attempt.tenantId(), attempt.id(),
                attempt.proposalId(), attempt.requestHash()) != 1) {
            throw new IllegalStateException(
                    "AI generated-draft terminal attempt is missing");
        }
        insertGeneratedDraftEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal terminal,
            AiGeneratedDraftProposal.Event event) {
        if (updateGeneratedDraftProposal(expected, terminal) != 1) return false;
        insertGeneratedDraftEvent(event);
        return true;
    }

    private int updateGeneratedDraftProposal(
            AiGeneratedDraftProposal expected,
            AiGeneratedDraftProposal value) {
        return jdbc.update("""
                UPDATE un_ai_generated_draft_proposal SET
                  state=?,revision=?,acted_by=?,result_json=?,result_code=?,
                  request_id=?,trace_id=?,updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.requestId(), value.traceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertGeneratedDraftAttempt(
            AiGeneratedDraftProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_ai_generated_draft_attempt(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,action,
                  request_key,request_hash,status,result_hash,result_code,created_at,
                  finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), value.status().name(), value.resultHash(),
                value.resultCode(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt()));
    }

    private void insertGeneratedDraftEvent(
            AiGeneratedDraftProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_ai_generated_draft_event(
                  id,account_id,system_id,tenant_id,member_id,session_id,turn_id,
                  policy_version_id,provider_id,provider_version,proposal_id,
                  attempt_id,event_type,from_state,to_state,revision,
                  actor_member_id,request_id,trace_id,result_code,event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.proposalId(), value.attemptId(), value.eventType(),
                value.fromState() == null ? null : value.fromState().name(),
                value.toState().name(), value.revision(), value.actorMemberId(),
                value.requestId(), value.traceId(), value.resultCode(),
                value.eventHash(), timestamp(value.createdAt()));
    }

    @Override
    public Optional<AiFillProposal> fillProposal(
            long systemId, long tenantId, long memberId,
            String moduleCode, String recordId, String fieldCode,
            long proposalId) {
        return one("""
                SELECT * FROM un_ai_fill_proposal
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND module_code=? AND record_id=? AND field_code=? AND id=?
                """, this::fillProposal, systemId, tenantId, memberId,
                moduleCode, Long.parseLong(recordId), fieldCode, proposalId);
    }

    @Override
    public Optional<AiFillProposal.Attempt> fillAttempt(
            long systemId, long tenantId, long memberId,
            String action, String requestKey) {
        return one("""
                SELECT * FROM un_ai_fill_attempt
                WHERE system_id=? AND tenant_id=? AND member_id=?
                  AND action=? AND request_key=?
                """, this::fillAttempt, systemId, tenantId, memberId,
                action, requestKey);
    }

    @Override
    public boolean reserveFillAttempt(AiFillProposal.Attempt value) {
        try {
            insertFillAttempt(value);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public boolean completeFillAttempt(
            AiFillProposal.Attempt expected, AiFillProposal.Attempt terminal) {
        return jdbc.update("""
                UPDATE un_ai_fill_attempt SET status=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND member_id=?
                  AND action=? AND request_key=? AND request_hash=? AND status=?
                """, terminal.status().name(), terminal.resultCode(),
                timestamp(terminal.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.memberId(),
                expected.action(), expected.requestKey(), expected.requestHash(),
                expected.status().name()) == 1;
    }

    @Override
    @Transactional
    public void insertFillProposal(
            AiFillProposal value, AiFillProposal.Attempt completed,
            AiFillProposal.Event event) {
        jdbc.update("""
                INSERT INTO un_ai_fill_proposal(
                  id,system_id,tenant_id,member_id,module_code,record_id,field_id,
                  field_code,field_name,result_schema,expected_record_version,schema_version_id,
                  source_version_hash,policy_version_id,provider_id,provider_version,
                  model_code,prompt_version,authorization_epoch,sources_json,
                  before_display_value,after_display_value,confidence,
                  clarification_summary,is_overwrite,result_hash,sealed_ciphertext,
                  sealed_key_version,sealed_command_hash,state,revision,expires_at,
                  acted_by,result_json,result_code,owner_request_id,owner_trace_id,
                  prompt_tokens,completion_tokens,provider_latency_ms,
                  created_at,updated_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, fillProposalArguments(value));
        if (jdbc.update("""
                UPDATE un_ai_fill_attempt SET status=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=? AND status='EXECUTING'
                """, completed.status().name(), completed.resultCode(),
                timestamp(completed.finishedAt()), completed.systemId(),
                completed.tenantId(), completed.id()) != 1) {
            throw new IllegalStateException("AI fill create attempt is missing");
        }
        insertFillEvent(event);
    }

    @Override
    @Transactional
    public ClaimResult claimFillProposal(
            AiFillProposal expected, AiFillProposal executing,
            AiFillProposal.Attempt attempt, AiFillProposal.Event event) {
        var locked = jdbc.query("""
                SELECT revision,state FROM un_ai_fill_proposal
                WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
                """, (result, row) -> new ConfirmationLock(
                        result.getLong("revision"), result.getString("state")),
                expected.systemId(), expected.tenantId(), expected.id());
        if (fillAttempt(expected.systemId(), expected.tenantId(),
                expected.memberId(), attempt.action(), attempt.requestKey()).isPresent()) {
            return ClaimResult.KEY_EXISTS;
        }
        if (locked.size() != 1
                || locked.getFirst().revision() != expected.revision()
                || !locked.getFirst().state().equals(expected.state().name())) {
            return ClaimResult.CONFIRMATION_CONFLICT;
        }
        insertFillAttempt(attempt);
        if (updateFillProposal(expected, executing) != 1) {
            throw new IllegalStateException("AI fill proposal claim lost locked row");
        }
        insertFillEvent(event);
        return ClaimResult.CLAIMED;
    }

    @Override
    @Transactional
    public boolean finishFillProposal(
            AiFillProposal expected, AiFillProposal terminal,
            AiFillProposal.Attempt attempt, AiFillProposal.Event event) {
        if (updateFillProposal(expected, terminal) != 1) return false;
        if (jdbc.update("""
                UPDATE un_ai_fill_attempt SET status=?,result_code=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND proposal_id=? AND request_hash=? AND status='EXECUTING'
                """, attempt.status().name(), attempt.resultCode(),
                timestamp(attempt.finishedAt()), attempt.systemId(),
                attempt.tenantId(), attempt.id(), attempt.proposalId(),
                attempt.requestHash()) != 1) {
            throw new IllegalStateException("AI fill terminal attempt is missing");
        }
        insertFillEvent(event);
        return true;
    }

    @Override
    @Transactional
    public boolean expireFillProposal(
            AiFillProposal expected, AiFillProposal expired,
            AiFillProposal.Event event) {
        if (updateFillProposal(expected, expired) != 1) return false;
        insertFillEvent(event);
        return true;
    }

    private int updateFillProposal(
            AiFillProposal expected, AiFillProposal value) {
        return jdbc.update("""
                UPDATE un_ai_fill_proposal SET state=?,revision=?,acted_by=?,
                  result_json=?,result_code=?,owner_request_id=?,owner_trace_id=?,
                  updated_at=?,finished_at=?
                WHERE system_id=? AND tenant_id=? AND id=?
                  AND revision=? AND state=?
                """, value.state().name(), value.revision(), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()), expected.systemId(),
                expected.tenantId(), expected.id(), expected.revision(),
                expected.state().name());
    }

    private void insertFillAttempt(AiFillProposal.Attempt value) {
        jdbc.update("""
                INSERT INTO un_ai_fill_attempt(
                  id,system_id,tenant_id,member_id,proposal_id,action,request_key,
                  request_hash,status,result_code,created_at,finished_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), value.status().name(), value.resultCode(),
                timestamp(value.createdAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt()));
    }

    private void insertFillEvent(AiFillProposal.Event value) {
        jdbc.update("""
                INSERT INTO un_ai_fill_event(
                  id,system_id,tenant_id,proposal_id,event_type,from_state,to_state,
                  revision,actor_member_id,request_id,trace_id,result_code,
                  event_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.proposalId(), value.eventType(), value.fromState() == null
                        ? null : value.fromState().name(), value.toState().name(),
                value.revision(), value.actorMemberId(), value.requestId(),
                value.traceId(), value.resultCode(), value.eventHash(),
                timestamp(value.createdAt()));
    }

    private void insertVersion(AiPolicy.Version value) {
        jdbc.update("""
                INSERT INTO un_ai_agent_policy_version(
                  id,system_id,tenant_id,policy_id,version_no,provider_id,
                  provider_version,model_code,snapshot_json,max_rows,enabled,
                  redaction_mode,prompt_version,snapshot_hash,published_at,published_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.systemId(), value.tenantId(),
                value.policyId(), value.versionNumber(), value.providerId(),
                value.providerVersion(), value.model(), versionJson(value),
                value.maxRows(), value.enabled(), value.redactionMode().name(),
                value.promptVersion(), value.snapshotHash(),
                timestamp(value.publishedAt()), value.publishedBy());
    }

    private AiProvider provider(ResultSet result, int row) throws SQLException {
        return new AiProvider(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getString("provider_code"),
                result.getString("provider_name"), result.getString("base_url"),
                result.getString("model_code"), result.getString("secret_ref"),
                result.getInt("timeout_seconds"), result.getBoolean("enabled"),
                result.getLong("version"), instant(result, "created_at"),
                result.getLong("created_by"), instant(result, "updated_at"),
                result.getLong("updated_by"));
    }

    private AiPolicy.Draft draft(ResultSet result, int row) throws SQLException {
        var document = decode(result.getString("draft_json"), DraftDocument.class);
        return new AiPolicy.Draft(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("draft_revision"),
                AiPolicy.DraftStatus.valueOf(result.getString("draft_status")),
                result.getLong("provider_id"), result.getLong("provider_version"),
                document.moduleCodes(), document.outboundFields(),
                document.allowedOperations(), document.writableFields(),
                document.fillFields(),
                result.getInt("max_rows"), document.confirmationMode(),
                document.confirmationExpiresSeconds(), result.getBoolean("enabled"),
                AiPolicy.RedactionMode.valueOf(result.getString("redaction_mode")),
                result.getString("prompt_version"), result.getString("draft_hash"),
                nullableLong(result, "active_version_id"),
                instant(result, "updated_at"), result.getLong("updated_by"));
    }

    private AiPolicy.Check check(ResultSet result, int row) throws SQLException {
        return new AiPolicy.Check(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("policy_id"),
                result.getLong("draft_revision"), result.getString("draft_hash"),
                decode(result.getString("issues_json"), ISSUES),
                instant(result, "checked_at"), result.getLong("checked_by"));
    }

    private AiPolicy.Version version(ResultSet result, int row) throws SQLException {
        var document = decode(result.getString("snapshot_json"), VersionDocument.class);
        return new AiPolicy.Version(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("policy_id"),
                result.getInt("version_no"), result.getLong("provider_id"),
                result.getLong("provider_version"), result.getString("model_code"),
                document.moduleCodes(), document.outboundFields(),
                document.allowedOperations(), document.writableFields(),
                document.fillFields(), result.getInt("max_rows"),
                document.confirmationMode(),
                document.confirmationExpiresSeconds(),
                result.getBoolean("enabled"),
                AiPolicy.RedactionMode.valueOf(result.getString("redaction_mode")),
                result.getString("prompt_version"), result.getString("snapshot_hash"),
                instant(result, "published_at"), result.getLong("published_by"));
    }

    private AiPolicy.PublishReplay replay(
            ResultSet result, int row) throws SQLException {
        return new AiPolicy.PublishReplay(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("policy_id"),
                result.getString("request_key"), result.getString("request_hash"),
                result.getLong("version_id"), instant(result, "created_at"));
    }

    private AiConversation.Session session(
            ResultSet result, int row) throws SQLException {
        return new AiConversation.Session(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("member_id"),
                result.getLong("policy_version_id"), result.getLong("provider_id"),
                result.getLong("provider_version"), result.getString("model_code"),
                result.getString("prompt_version"),
                result.getLong("authorization_epoch"),
                AiConversation.SessionStatus.valueOf(result.getString("status")),
                result.getString("title_summary"), instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private AiConversation.Message message(
            ResultSet result, int row) throws SQLException {
        return new AiConversation.Message(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("session_id"),
                nullableLong(result, "turn_id"),
                AiConversation.Role.valueOf(result.getString("role")),
                result.getString("redacted_summary"),
                result.getString("content_hash"), result.getInt("character_count"),
                instant(result, "created_at"));
    }

    private AiConversation.Turn turn(ResultSet result, int row) throws SQLException {
        return new AiConversation.Turn(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("session_id"),
                result.getLong("policy_version_id"), result.getLong("provider_id"),
                result.getLong("provider_version"),
                result.getLong("authorization_epoch"),
                AiConversation.TurnStatus.valueOf(result.getString("status")),
                result.getString("request_summary"), result.getString("request_hash"),
                result.getString("plan_hash"), result.getString("response_summary"),
                result.getString("response_hash"), result.getInt("returned_rows"),
                result.getString("result_code"), result.getBoolean("retryable"),
                result.getLong("latency_ms"), result.getString("request_id"),
                result.getString("trace_id"), instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfirmation confirmation(ResultSet result, int row)
            throws SQLException {
        var operation = AiRecordMutationFacade.Operation.valueOf(
                result.getString("operation"));
        return new AiConfirmation(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("member_id"),
                result.getLong("session_id"), result.getLong("turn_id"),
                result.getLong("policy_version_id"), result.getLong("provider_id"),
                result.getLong("provider_version"), result.getString("prompt_version"),
                result.getLong("authorization_epoch"), operation,
                result.getString("module_code"),
                Long.toString(result.getLong("schema_version_id")),
                nullableDecimal(result, "record_id"),
                nullableLong(result, "expected_record_version"),
                result.getString("plan_hash"),
                decode(result.getString("preview_json"), AiConfirmation.Preview.class),
                decode(result.getString("confidence_json"),
                        new TypeReference<List<AiConfirmation.Confidence>>() { }),
                decode(result.getString("clarifications_json"),
                        new TypeReference<List<String>>() { }),
                new AiRecordMutationFacade.SealedCommand(
                        result.getString("sealed_ciphertext"),
                        result.getString("sealed_key_version"),
                        result.getString("sealed_command_hash")),
                AiConfirmation.State.valueOf(result.getString("state")),
                result.getLong("revision"), instant(result, "expires_at"),
                nullableLong(result, "confirmed_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiConfirmation.Result.class),
                result.getString("result_code"),
                result.getString("owner_request_id"),
                result.getString("owner_trace_id"),
                instant(result, "created_at"), instant(result, "updated_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfirmation.Attempt confirmationAttempt(
            ResultSet result, int row) throws SQLException {
        return new AiConfirmation.Attempt(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("confirmation_id"),
                result.getString("request_key"), result.getString("request_hash"),
                AiConfirmation.State.valueOf(result.getString("status")),
                result.getString("result_hash"), result.getString("result_code"),
                instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfigurationFieldProposal configurationFieldProposal(
            ResultSet result, int row) throws SQLException {
        var ciphertext = result.getString("sealed_ciphertext");
        return new AiConfigurationFieldProposal(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("authorization_epoch"),
                result.getString("prompt_version"), result.getString("module_code"),
                result.getString("plan_hash"),
                AiConfigurationFieldProposal.State.valueOf(
                        result.getString("state")), result.getLong("revision"),
                result.getString("preview_json") == null ? null
                        : decode(result.getString("preview_json"),
                        AiConfigurationFieldProposal.Preview.class),
                result.getDouble("confidence"),
                result.getString("clarification_summary"),
                ciphertext == null ? null : new AiConfigurationFieldProposal.SealedCommand(
                        ciphertext, result.getString("sealed_key_version"),
                        result.getString("sealed_command_hash")),
                instant(result, "expires_at"), nullableLong(result, "acted_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiConfigurationFieldProposal.Result.class),
                result.getString("result_code"),
                result.getString("owner_request_id"),
                result.getString("owner_trace_id"),
                instant(result, "created_at"), instant(result, "updated_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfigurationFieldProposal.Attempt configurationFieldAttempt(
            ResultSet result, int row) throws SQLException {
        return new AiConfigurationFieldProposal.Attempt(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("proposal_id"), result.getString("action"),
                result.getString("request_key"), result.getString("request_hash"),
                AiConfigurationFieldProposal.State.valueOf(
                        result.getString("status")), result.getString("result_hash"),
                result.getString("result_code"), instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfigurationArtifactProposal artifactProposal(
            ResultSet result, int row) throws SQLException {
        var kind = AiConfigurationArtifactProposal.ArtifactKind.valueOf(
                result.getString("artifact_kind"));
        var operation = switch (kind) {
            case SELECTION_FIELD ->
                    com.unique.examine.core.ai.AiConfigurationArtifactFacade.Operation
                            .CONFIG_SELECTION_FIELD_DRAFT;
            case PAGE_LAYOUT ->
                    com.unique.examine.core.ai.AiConfigurationArtifactFacade.Operation
                            .CONFIG_PAGE_LAYOUT_DRAFT;
            case FILTER_SCENARIO ->
                    com.unique.examine.core.ai.AiConfigurationArtifactFacade.Operation
                            .CONFIG_FILTER_SCENARIO_DRAFT;
            case FIELD_PERMISSION_STAGE ->
                    com.unique.examine.core.ai.AiConfigurationArtifactFacade.Operation
                            .CONFIG_FIELD_PERMISSION_STAGE_DRAFT;
        };
        var ciphertext = result.getString("sealed_ciphertext");
        return new AiConfigurationArtifactProposal(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("authorization_epoch"),
                result.getString("prompt_version"), operation,
                result.getString("module_code"), result.getString("plan_hash"),
                AiConfigurationArtifactProposal.State.valueOf(
                        result.getString("state")), result.getLong("revision"),
                result.getString("preview_json") == null ? null
                        : decode(result.getString("preview_json"),
                        AiConfigurationArtifactProposal.Preview.class),
                result.getDouble("confidence"),
                result.getString("clarification_summary"), ciphertext == null ? null
                : new AiConfigurationArtifactProposal.SealedCommand(
                        ciphertext, result.getString("sealed_key_version"),
                        result.getString("sealed_command_hash")),
                instant(result, "expires_at"), nullableLong(result, "acted_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiConfigurationArtifactProposal.Result.class),
                result.getString("result_code"), result.getString("request_id"),
                result.getString("trace_id"), instant(result, "created_at"),
                instant(result, "updated_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiConfigurationArtifactProposal.Attempt artifactAttempt(
            ResultSet result, int row) throws SQLException {
        return new AiConfigurationArtifactProposal.Attempt(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("proposal_id"), result.getString("action"),
                result.getString("request_key"), result.getString("request_hash"),
                AiConfigurationArtifactProposal.State.valueOf(
                        result.getString("status")), result.getString("result_hash"),
                result.getString("result_code"), instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiWorkProposal workProposal(
            ResultSet result, int row) throws SQLException {
        var ciphertext = result.getString("command_ciphertext");
        return new AiWorkProposal(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("authorization_epoch"),
                result.getString("prompt_version"),
                AiWorkProposal.Operation.valueOf(result.getString("operation")),
                result.getString("plan_hash"),
                AiWorkProposal.State.valueOf(result.getString("state")),
                result.getLong("revision"),
                result.getString("preview_json") == null ? null
                        : decode(result.getString("preview_json"),
                        AiWorkProposal.Preview.class),
                result.getDouble("confidence"),
                result.getString("clarification_summary"),
                ciphertext == null ? null : new AiWorkProposal.SealedCommand(
                        ciphertext, result.getString("command_key_version"),
                        result.getString("command_hash")),
                instant(result, "expires_at"), nullableLong(result, "acted_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiWorkProposal.Result.class),
                result.getString("result_code"), result.getString("request_id"),
                result.getString("trace_id"), instant(result, "created_at"),
                instant(result, "updated_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiWorkProposal.Attempt workAttempt(
            ResultSet result, int row) throws SQLException {
        return new AiWorkProposal.Attempt(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("proposal_id"), result.getString("action"),
                result.getString("request_key"), result.getString("request_hash"),
                AiWorkProposal.State.valueOf(result.getString("status")),
                result.getString("result_hash"), result.getString("result_code"),
                instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiGeneratedDraftProposal generatedDraftProposal(
            ResultSet result, int row) throws SQLException {
        var ciphertext = result.getString("command_ciphertext");
        return new AiGeneratedDraftProposal(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("authorization_epoch"),
                result.getString("prompt_version"),
                AiGeneratedDraftProposal.Operation.valueOf(
                        result.getString("operation")),
                result.getString("plan_hash"),
                AiGeneratedDraftProposal.State.valueOf(result.getString("state")),
                result.getLong("revision"),
                result.getString("preview_json") == null ? null
                        : decode(result.getString("preview_json"),
                        AiGeneratedDraftProposal.Preview.class),
                result.getDouble("confidence"),
                result.getString("clarification_summary"),
                ciphertext == null ? null : new AiGeneratedDraftProposal.SealedCommand(
                        ciphertext, result.getString("command_key_version"),
                        result.getString("command_hash")),
                instant(result, "expires_at"), nullableLong(result, "acted_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiGeneratedDraftProposal.Result.class),
                result.getString("result_code"), result.getString("request_id"),
                result.getString("trace_id"), instant(result, "created_at"),
                instant(result, "updated_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiGeneratedDraftProposal.Attempt generatedDraftAttempt(
            ResultSet result, int row) throws SQLException {
        return new AiGeneratedDraftProposal.Attempt(
                result.getLong("id"), result.getLong("account_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("member_id"), result.getLong("session_id"),
                result.getLong("turn_id"), result.getLong("policy_version_id"),
                result.getLong("provider_id"), result.getLong("provider_version"),
                result.getLong("proposal_id"), result.getString("action"),
                result.getString("request_key"), result.getString("request_hash"),
                AiGeneratedDraftProposal.State.valueOf(result.getString("status")),
                result.getString("result_hash"), result.getString("result_code"),
                instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private AiFillProposal fillProposal(ResultSet result, int row)
            throws SQLException {
        var ciphertext = result.getString("sealed_ciphertext");
        return new AiFillProposal(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("member_id"),
                result.getString("module_code"),
                Long.toString(result.getLong("record_id")),
                Long.toString(result.getLong("field_id")),
                result.getString("field_code"),
                result.getString("field_name"),
                com.unique.examine.core.ai.AiFieldFillFacade.ResultSchema.valueOf(
                        result.getString("result_schema")),
                result.getLong("expected_record_version"),
                Long.toString(result.getLong("schema_version_id")),
                result.getString("source_version_hash"),
                result.getLong("policy_version_id"), result.getLong("provider_id"),
                result.getLong("provider_version"), result.getString("model_code"),
                result.getString("prompt_version"),
                result.getLong("authorization_epoch"),
                decode(result.getString("sources_json"),
                        new TypeReference<List<AiFillProposal.SourceSummary>>() { }),
                result.getString("before_display_value"),
                result.getString("after_display_value"), result.getDouble("confidence"),
                result.getString("clarification_summary"),
                result.getBoolean("is_overwrite"), result.getString("result_hash"),
                ciphertext == null ? null
                        : new com.unique.examine.core.ai.AiFieldFillFacade.SealedCommand(
                        ciphertext, result.getString("sealed_key_version"),
                        result.getString("sealed_command_hash")),
                AiFillProposal.State.valueOf(result.getString("state")),
                result.getLong("revision"), instant(result, "expires_at"),
                nullableLong(result, "acted_by"),
                result.getString("result_json") == null ? null
                        : decode(result.getString("result_json"),
                        AiFillProposal.FillResult.class),
                result.getString("result_code"),
                result.getString("owner_request_id"), result.getString("owner_trace_id"),
                result.getInt("prompt_tokens"), result.getInt("completion_tokens"),
                result.getLong("provider_latency_ms"), instant(result, "created_at"),
                instant(result, "updated_at"), nullableInstant(result, "finished_at"));
    }

    private AiFillProposal.Attempt fillAttempt(ResultSet result, int row)
            throws SQLException {
        return new AiFillProposal.Attempt(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("member_id"),
                result.getLong("proposal_id"), result.getString("action"),
                result.getString("request_key"), result.getString("request_hash"),
                AiFillProposal.State.valueOf(result.getString("status")),
                result.getString("result_code"), instant(result, "created_at"),
                nullableInstant(result, "finished_at"));
    }

    private Object[] fillProposalArguments(AiFillProposal value) {
        return new Object[]{
                value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.moduleCode(), Long.parseLong(value.recordId()),
                Long.parseLong(value.fieldId()), value.fieldCode(),
                value.fieldName(),
                value.resultSchema().name(), value.expectedRecordVersion(),
                Long.parseLong(value.schemaVersionId()), value.sourceVersionHash(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.model(), value.promptVersion(), value.authorizationEpoch(),
                encode(value.sources()), value.beforeDisplayValue(),
                value.afterDisplayValue(), value.confidence(),
                value.clarificationSummary(), value.overwrite(), value.resultHash(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().ciphertext(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().encryptionKeyVersion(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().commandSha256(),
                value.state().name(), value.revision(), timestamp(value.expiresAt()),
                value.actedBy(), value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                value.promptTokens(), value.completionTokens(),
                value.providerLatencyMs(), timestamp(value.createdAt()),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt())
        };
    }

    private Object[] confirmationArguments(AiConfirmation value) {
        return new Object[]{
                value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.sessionId(), value.turnId(), value.policyVersionId(),
                value.providerId(), value.providerVersion(), value.promptVersion(),
                value.authorizationEpoch(), value.operation().name(), value.moduleCode(),
                Long.parseLong(value.schemaVersionId()), value.recordId() == null
                        ? null : Long.parseLong(value.recordId()),
                value.expectedRecordVersion(), value.planHash(), encode(value.preview()),
                encode(value.confidence()), encode(value.clarifications()),
                value.sealedCommand().ciphertext(),
                value.sealedCommand().encryptionKeyVersion(),
                value.sealedCommand().commandSha256(), value.state().name(),
                value.revision(), timestamp(value.expiresAt()), value.confirmedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt())
        };
    }

    private Object[] configurationFieldProposalArguments(
            AiConfigurationFieldProposal value) {
        return new Object[]{
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(), value.moduleCode(),
                value.planHash(), value.preview() == null ? null
                        : encode(value.preview()), value.confidence(),
                value.clarification(), value.sealedCommand() == null ? null
                        : value.sealedCommand().ciphertext(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().keyVersion(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().commandHash(),
                value.state().name(), value.revision(), timestamp(value.expiresAt()),
                value.actedBy(), value.result() == null ? null
                        : encode(value.result()), value.resultCode(),
                value.ownerRequestId(), value.ownerTraceId(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt())
        };
    }

    private Object[] artifactProposalArguments(
            AiConfigurationArtifactProposal value) {
        return new Object[]{
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(),
                value.artifactKind().name(), value.moduleCode(), value.planHash(),
                value.preview() == null ? null : encode(value.preview()),
                value.confidence(), value.clarification(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().ciphertext(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().keyVersion(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().commandHash(),
                value.state().name(), value.revision(), timestamp(value.expiresAt()),
                value.actedBy(), value.result() == null ? null
                        : encode(value.result()), value.resultCode(),
                value.requestId(), value.traceId(), timestamp(value.createdAt()),
                timestamp(value.updatedAt()), value.finishedAt() == null ? null
                        : timestamp(value.finishedAt())
        };
    }

    private Object[] workProposalArguments(AiWorkProposal value) {
        return new Object[]{
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(),
                value.operation().name(), value.planHash(), value.state().name(),
                value.revision(), value.preview() == null ? null
                        : encode(value.preview()), value.confidence(),
                value.clarification(), value.sealedCommand() == null ? null
                        : value.sealedCommand().ciphertext(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().keyVersion(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().commandHash(),
                timestamp(value.expiresAt()), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.requestId(), value.traceId(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt())
        };
    }

    private Object[] generatedDraftProposalArguments(
            AiGeneratedDraftProposal value) {
        return new Object[]{
                value.id(), value.accountId(), value.systemId(), value.tenantId(),
                value.memberId(), value.sessionId(), value.turnId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.promptVersion(),
                value.operation().name(), value.planHash(), value.state().name(),
                value.revision(), value.preview() == null ? null
                        : encode(value.preview()), value.confidence(),
                value.clarification(), value.sealedCommand() == null ? null
                        : value.sealedCommand().ciphertext(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().keyVersion(),
                value.sealedCommand() == null ? null
                        : value.sealedCommand().commandHash(),
                timestamp(value.expiresAt()), value.actedBy(),
                value.result() == null ? null : encode(value.result()),
                value.resultCode(), value.requestId(), value.traceId(),
                timestamp(value.createdAt()), timestamp(value.updatedAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt())
        };
    }

    private Object[] attemptArguments(AiConfirmation.Attempt value) {
        return new Object[]{
                value.id(), value.systemId(), value.tenantId(),
                value.confirmationId(), value.requestKey(), value.requestHash(),
                value.status().name(), value.resultHash(), value.resultCode(),
                timestamp(value.createdAt()), value.finishedAt() == null
                        ? null : timestamp(value.finishedAt())
        };
    }

    private Object[] turnArguments(AiConversation.Turn value) {
        return new Object[]{
                value.id(), value.systemId(), value.tenantId(), value.sessionId(),
                value.policyVersionId(), value.providerId(), value.providerVersion(),
                value.authorizationEpoch(), value.status().name(),
                value.requestSummary(), value.requestHash(), value.planHash(),
                value.responseSummary(), value.responseHash(), value.returnedRows(),
                value.resultCode(), value.retryable(), value.latencyMs(),
                value.requestId(), value.traceId(), timestamp(value.createdAt()),
                value.finishedAt() == null ? null : timestamp(value.finishedAt())
        };
    }

    private String policyJson(AiPolicy.Draft value) {
        return encode(new DraftDocument(
                value.allowedModuleCodes(), value.outboundFields(),
                value.allowedOperations(), value.writableFields(),
                value.fillFields(), value.confirmationMode(),
                value.confirmationExpiresSeconds()));
    }

    private String versionJson(AiPolicy.Version value) {
        return encode(new VersionDocument(
                value.allowedModuleCodes(), value.outboundFields(),
                value.allowedOperations(), value.writableFields(),
                value.fillFields(), value.confirmationMode(),
                value.confirmationExpiresSeconds()));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode AI persistence JSON", failure);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored AI JSON is invalid", failure);
        }
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored AI JSON is invalid", failure);
        }
    }

    private <T> Optional<T> one(
            String sql, RowMapper<T> mapper, Object... arguments) {
        return jdbc.query(sql, mapper, arguments).stream().findFirst();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet result, String column)
            throws SQLException {
        var value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet result, String column)
            throws SQLException {
        var value = result.getObject(column, Long.class);
        return value;
    }

    private static String nullableDecimal(ResultSet result, String column)
            throws SQLException {
        var value = result.getObject(column, Long.class);
        return value == null ? null : Long.toString(value);
    }

    private record DraftDocument(
            Set<String> moduleCodes,
            Map<String, Set<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields,
            AiPolicy.ConfirmationMode confirmationMode,
            Integer confirmationExpiresSeconds
    ) {
        private DraftDocument {
            moduleCodes = Set.copyOf(moduleCodes);
            outboundFields = Map.copyOf(new TreeMap<>(outboundFields));
            allowedOperations = allowedOperations == null
                    ? Set.of("RECORD_QUERY") : Set.copyOf(allowedOperations);
            writableFields = writableFields == null
                    ? Map.of() : Map.copyOf(new TreeMap<>(writableFields));
            fillFields = fillFields == null
                    ? Map.of() : Map.copyOf(new TreeMap<>(fillFields));
            confirmationMode = confirmationMode == null
                    ? AiPolicy.ConfirmationMode.REQUIRED : confirmationMode;
            confirmationExpiresSeconds = confirmationExpiresSeconds == null
                    ? 600 : confirmationExpiresSeconds;
        }
    }

    private record VersionDocument(
            Set<String> moduleCodes,
            Map<String, Set<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields,
            AiPolicy.ConfirmationMode confirmationMode,
            Integer confirmationExpiresSeconds
    ) {
        private VersionDocument {
            moduleCodes = Set.copyOf(moduleCodes);
            outboundFields = Map.copyOf(new TreeMap<>(outboundFields));
            allowedOperations = allowedOperations == null
                    ? Set.of("RECORD_QUERY") : Set.copyOf(allowedOperations);
            writableFields = writableFields == null
                    ? Map.of() : Map.copyOf(new TreeMap<>(writableFields));
            fillFields = fillFields == null
                    ? Map.of() : Map.copyOf(new TreeMap<>(fillFields));
            confirmationMode = confirmationMode == null
                    ? AiPolicy.ConfirmationMode.REQUIRED : confirmationMode;
            confirmationExpiresSeconds = confirmationExpiresSeconds == null
                    ? 600 : confirmationExpiresSeconds;
        }
    }

    private record LockedDraft(long revision, String status, String hash) {
    }

    private record ConfirmationLock(long revision, String state) {
    }
}
