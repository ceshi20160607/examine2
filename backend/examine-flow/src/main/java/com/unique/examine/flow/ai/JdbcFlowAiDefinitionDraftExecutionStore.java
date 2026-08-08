package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable dual-key execution ledger for one Flow definition draft. */
@Component
public class JdbcFlowAiDefinitionDraftExecutionStore {
    static final String COLUMNS = """
            id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
            account_id,authorization_epoch,operation,policy_version_id,
            provider_id,provider_version,prompt_version,expires_at,
            prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
            execute_request_id,execute_trace_id,result_json,created_at,completed_at
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_flow_ai_definition_draft_execution(
              id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
              account_id,authorization_epoch,operation,policy_version_id,
              provider_id,provider_version,prompt_version,expires_at,
              prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
              execute_request_id,execute_trace_id,result_json,created_at,completed_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,NULL)
            ON DUPLICATE KEY UPDATE id=id
            """;
    static final String FIND_PROPOSAL_SQL = """
            SELECT %s FROM un_flow_ai_definition_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND proposal_id=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String FIND_IDEMPOTENCY_SQL = """
            SELECT %s FROM un_flow_ai_definition_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND idempotency_key=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String COMPLETE_SQL = """
            UPDATE un_flow_ai_definition_draft_execution
            SET result_json=?,completed_at=?
            WHERE system_id=? AND tenant_id=? AND id=? AND result_json IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final DefinitionCreator definitions;
    private final FlowAiDefinitionDraftCommandCodec codec;
    private final Clock clock;

    @Autowired
    public JdbcFlowAiDefinitionDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            FlowMutationService mutations,
            Clock clock) {
        this(jdbc, ids, mutations::createDefinition,
                new FlowAiDefinitionDraftCommandCodec(), clock);
    }

    JdbcFlowAiDefinitionDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            DefinitionCreator definitions,
            FlowAiDefinitionDraftCommandCodec codec,
            Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.definitions = Objects.requireNonNull(
                definitions, "definitions");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public AiFlowDefinitionDraftFacade.DefinitionReadback execute(
            FlowAiDefinitionDraftCommandCodec.Command command,
            FlowSession session,
            Runnable liveFactCheck,
            String idempotencyKey,
            String requestId,
            String traceId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(liveFactCheck, "liveFactCheck");
        var candidateId = ids.nextId();
        var now = clock.instant();
        jdbc.update(INSERT_SQL,
                candidateId, command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.sessionId(),
                command.turnId(), command.accountId(),
                command.authorizationEpoch(), command.operation().name(),
                Long.parseLong(command.policyVersionId()),
                Long.parseLong(command.providerId()),
                command.providerVersion(), command.promptVersion(),
                timestamp(command.expiresAt()), command.prepareRequestId(),
                command.prepareTraceId(), command.payloadHash(), idempotencyKey,
                requestId, traceId, timestamp(now));

        var proposals = proposal(command);
        var keys = idempotency(command, idempotencyKey);
        if (proposals.size() != 1 || keys.size() != 1
                || proposals.getFirst().id() != keys.getFirst().id()) {
            throw conflict();
        }
        var stored = proposals.getFirst();
        if (!stored.matches(command, idempotencyKey)) throw conflict();
        if (stored.resultJson() != null) {
            return codec.readReadback(stored.resultJson());
        }
        if (stored.id() != candidateId) {
            throw new IllegalStateException(
                    "Incomplete Flow AI draft ledger row is not recoverable");
        }

        liveFactCheck.run();
        var draft = command.draft();
        var created = definitions.create(
                session,
                new FlowRequests.CreateDefinition(
                        draft.name(), null, draft.approverMemberIds()));
        var result = new AiFlowDefinitionDraftFacade.DefinitionReadback(
                command.operation(), created.definitionId(), created.name(),
                created.approverIds(), created.revision(),
                Instant.parse(created.updatedAt()), false);
        var updated = jdbc.update(
                COMPLETE_SQL, codec.writeReadback(result),
                timestamp(clock.instant()), command.systemId(),
                command.tenantId(), stored.id());
        if (updated != 1) {
            throw new IllegalStateException(
                    "Flow AI draft ledger completion was not singular");
        }
        return result;
    }

    private List<Row> proposal(
            FlowAiDefinitionDraftCommandCodec.Command command) {
        return jdbc.query(FIND_PROPOSAL_SQL, this::row,
                command.systemId(), command.tenantId(), command.memberId(),
                command.proposalId());
    }

    private List<Row> idempotency(
            FlowAiDefinitionDraftCommandCodec.Command command,
            String key) {
        return jdbc.query(FIND_IDEMPOTENCY_SQL, this::row,
                command.systemId(), command.tenantId(), command.memberId(),
                key);
    }

    private Row row(ResultSet value, int ignored) throws SQLException {
        return new Row(
                value.getLong("id"), value.getLong("system_id"),
                value.getLong("tenant_id"), value.getLong("member_id"),
                value.getString("proposal_id"),
                value.getString("session_id"), value.getString("turn_id"),
                value.getLong("account_id"),
                value.getLong("authorization_epoch"),
                AiFlowDefinitionDraftFacade.Operation.valueOf(
                        value.getString("operation")),
                Long.toString(value.getLong("policy_version_id")),
                Long.toString(value.getLong("provider_id")),
                value.getLong("provider_version"),
                value.getString("prompt_version"),
                instant(value, "expires_at"),
                value.getString("prepare_request_id"),
                value.getString("prepare_trace_id"),
                value.getString("payload_hash"),
                value.getString("idempotency_key"),
                value.getString("result_json"));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(
            ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static BusinessException conflict() {
        return new BusinessException(
                "AI_FLOW_DRAFT_IDEMPOTENCY_CONFLICT",
                "The Flow draft proposal or idempotency key was already used",
                HttpStatus.CONFLICT);
    }

    private record Row(
            long id,
            long systemId,
            long tenantId,
            long memberId,
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long authorizationEpoch,
            AiFlowDefinitionDraftFacade.Operation operation,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId,
            String payloadHash,
            String idempotencyKey,
            String resultJson) {
        boolean matches(
                FlowAiDefinitionDraftCommandCodec.Command command,
                String requestedKey) {
            return systemId == command.systemId()
                    && tenantId == command.tenantId()
                    && memberId == command.memberId()
                    && accountId == command.accountId()
                    && authorizationEpoch == command.authorizationEpoch()
                    && providerVersion == command.providerVersion()
                    && proposalId.equals(command.proposalId())
                    && sessionId.equals(command.sessionId())
                    && turnId.equals(command.turnId())
                    && operation == command.operation()
                    && policyVersionId.equals(command.policyVersionId())
                    && providerId.equals(command.providerId())
                    && promptVersion.equals(command.promptVersion())
                    && expiresAt.equals(command.expiresAt())
                    && prepareRequestId.equals(command.prepareRequestId())
                    && prepareTraceId.equals(command.prepareTraceId())
                    && FlowAiDefinitionDraftCommandSealer.equal(
                    payloadHash, command.payloadHash())
                    && idempotencyKey.equals(requestedKey);
        }
    }

    @FunctionalInterface
    interface DefinitionCreator {
        com.unique.examine.flow.api.FlowViews.DefinitionDraft create(
                FlowSession session, FlowRequests.CreateDefinition request);
    }
}
