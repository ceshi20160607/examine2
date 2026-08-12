package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Durable, atomic proposal and idempotency ledger for confirmed Work drafts. */
@Component
public class JdbcWorkAiDraftExecutionStore {
    static final String COLUMNS = """
            id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
            account_id,authorization_epoch,operation,policy_version_id,
            provider_id,provider_version,prompt_version,expires_at,
            prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
            execute_request_id,execute_trace_id,result_json,created_at,completed_at
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_work_ai_draft_execution(
              id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
              account_id,authorization_epoch,operation,policy_version_id,
              provider_id,provider_version,prompt_version,expires_at,
              prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
              execute_request_id,execute_trace_id,result_json,created_at,completed_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,NULL)
            ON DUPLICATE KEY UPDATE id=id
            """;
    static final String FIND_PROPOSAL_SQL = """
            SELECT %s FROM un_work_ai_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND proposal_id=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String FIND_IDEMPOTENCY_SQL = """
            SELECT %s FROM un_work_ai_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND idempotency_key=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String COMPLETE_SQL = """
            UPDATE un_work_ai_draft_execution
            SET result_json=?,completed_at=?
            WHERE system_id=? AND tenant_id=? AND id=? AND result_json IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final WorkTaskService tasks;
    private final WorkDailyReportService reports;
    private final WorkAiDraftCommandCodec codec;
    private final Clock clock;

    @Autowired
    public JdbcWorkAiDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            WorkTaskService tasks,
            WorkDailyReportService reports,
            Clock clock
    ) {
        this(jdbc, ids, tasks, reports,
                new WorkAiDraftCommandCodec(), clock);
    }

    JdbcWorkAiDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            WorkTaskService tasks,
            WorkDailyReportService reports,
            WorkAiDraftCommandCodec codec,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public AiWorkDraftFacade.DraftReadback execute(
            WorkAiDraftCommandCodec.Command command,
            WorkActor actor,
            Runnable liveFactCheck,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
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

        var proposal = proposal(command);
        var idempotency = idempotency(command, idempotencyKey);
        if (proposal.size() != 1 || idempotency.size() != 1
                || proposal.getFirst().id() != idempotency.getFirst().id()) {
            throw conflict();
        }
        var stored = proposal.getFirst();
        if (!stored.matches(command, idempotencyKey)) {
            throw conflict();
        }
        if (stored.resultJson() != null) {
            var replay = codec.readReadback(stored.resultJson());
            if (replay.operation() != command.operation()) throw conflict();
            return replay;
        }
        if (stored.id() != candidateId) {
            throw new IllegalStateException(
                    "Incomplete Work AI draft ledger row is not recoverable");
        }

        liveFactCheck.run();
        var result = create(command, actor);
        var completedAt = clock.instant();
        var updated = jdbc.update(
                COMPLETE_SQL, codec.writeReadback(result),
                timestamp(completedAt), command.systemId(),
                command.tenantId(), stored.id());
        if (updated != 1) {
            throw new IllegalStateException(
                    "Work AI draft ledger completion was not singular");
        }
        return result;
    }

    private AiWorkDraftFacade.DraftReadback create(
            WorkAiDraftCommandCodec.Command command,
            WorkActor actor
    ) {
        if (command.operation()
                == AiWorkDraftFacade.Operation.WORK_TASK_DRAFT) {
            var draft = command.task();
            var task = tasks.create(
                    actor, draft.title(),
                    Long.parseLong(draft.assigneeMemberId()),
                    draft.projectId() == null
                            ? null : Long.parseLong(draft.projectId()),
                    draft.description(), draft.dueAt());
            return new AiWorkDraftFacade.DraftReadback(
                    command.operation(), task(task), null);
        }
        var draft = command.report();
        var report = reports.create(
                actor, draft.workDate(), draft.completedWork(),
                draft.plannedWork(), draft.blockers());
        return new AiWorkDraftFacade.DraftReadback(
                command.operation(), null, report(report));
    }

    private List<Row> proposal(WorkAiDraftCommandCodec.Command command) {
        return jdbc.query(FIND_PROPOSAL_SQL, this::row,
                command.systemId(), command.tenantId(), command.memberId(),
                command.proposalId());
    }

    private List<Row> idempotency(
            WorkAiDraftCommandCodec.Command command,
            String idempotencyKey
    ) {
        return jdbc.query(FIND_IDEMPOTENCY_SQL, this::row,
                command.systemId(), command.tenantId(), command.memberId(),
                idempotencyKey);
    }

    private Row row(ResultSet value, int ignored) throws SQLException {
        return new Row(
                value.getLong("id"), value.getLong("system_id"),
                value.getLong("tenant_id"), value.getLong("member_id"),
                value.getString("proposal_id"), value.getString("session_id"),
                value.getString("turn_id"), value.getLong("account_id"),
                value.getLong("authorization_epoch"),
                AiWorkDraftFacade.Operation.valueOf(
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

    private static AiWorkDraftFacade.TaskReadback task(WorkTask value) {
        if (value.status() != WorkTask.Status.OPEN) {
            throw new IllegalStateException(
                    "Work task draft did not create an open task");
        }
        return new AiWorkDraftFacade.TaskReadback(
                Long.toString(value.id()), value.version(), value.title(),
                value.description(), value.status().name(),
                Long.toString(value.assigneeMemberId()),
                value.projectId() == null
                        ? null : Long.toString(value.projectId()),
                value.dueAt(), value.createdAt(), value.updatedAt());
    }

    private static AiWorkDraftFacade.DailyReportReadback report(
            WorkDailyReport value
    ) {
        if (value.status() != WorkDailyReport.Status.DRAFT) {
            throw new IllegalStateException(
                    "Work report draft did not create a draft report");
        }
        return new AiWorkDraftFacade.DailyReportReadback(
                Long.toString(value.id()), value.version(),
                Long.toString(value.authorMemberId()), value.workDate(),
                value.completedWork(), value.plannedWork(), value.blockers(),
                value.status().name(), value.createdAt(), value.updatedAt());
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(
            ResultSet value, String column) throws SQLException {
        var result = value.getTimestamp(column);
        return result == null ? null : result.toInstant();
    }

    private static BusinessException conflict() {
        return new BusinessException(
                "AI_WORK_DRAFT_IDEMPOTENCY_CONFLICT",
                "The Work draft proposal or idempotency key was already used",
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
            AiWorkDraftFacade.Operation operation,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId,
            String payloadHash,
            String idempotencyKey,
            String resultJson
    ) {
        boolean matches(
                WorkAiDraftCommandCodec.Command command,
                String requestedIdempotencyKey
        ) {
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
                    && WorkAiDraftCommandSealer.equal(
                    payloadHash, command.payloadHash())
                    && idempotencyKey.equals(requestedIdempotencyKey);
        }
    }
}
