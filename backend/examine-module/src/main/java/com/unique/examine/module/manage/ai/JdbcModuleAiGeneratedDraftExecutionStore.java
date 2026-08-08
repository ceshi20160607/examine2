package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.service.RequestContext;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.service.ReportService;
import com.unique.examine.module.runtime.printing.PrintTemplateService;
import com.unique.examine.module.runtime.printing.PrintViews;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Durable atomic idempotency ledger for report and print AI draft creation. */
@Component
public class JdbcModuleAiGeneratedDraftExecutionStore {
    static final String COLUMNS = """
            id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
            account_id,authorization_epoch,operation,policy_version_id,
            provider_id,provider_version,prompt_version,expires_at,
            prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
            execute_request_id,execute_trace_id,result_json,created_at,completed_at
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_module_ai_generated_draft_execution(
              id,system_id,tenant_id,member_id,proposal_id,session_id,turn_id,
              account_id,authorization_epoch,operation,policy_version_id,
              provider_id,provider_version,prompt_version,expires_at,
              prepare_request_id,prepare_trace_id,payload_hash,idempotency_key,
              execute_request_id,execute_trace_id,result_json,created_at,completed_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,NULL)
            ON DUPLICATE KEY UPDATE id=id
            """;
    static final String FIND_PROPOSAL_SQL = """
            SELECT %s FROM un_module_ai_generated_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND proposal_id=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String FIND_IDEMPOTENCY_SQL = """
            SELECT %s FROM un_module_ai_generated_draft_execution
            WHERE system_id=? AND tenant_id=? AND member_id=? AND idempotency_key=?
            FOR UPDATE
            """.formatted(COLUMNS);
    static final String COMPLETE_SQL = """
            UPDATE un_module_ai_generated_draft_execution
            SET result_json=?,completed_at=?
            WHERE system_id=? AND tenant_id=? AND id=? AND result_json IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ReportService reports;
    private final PrintTemplateService prints;
    private final AiModuleGeneratedDraftContextReader contexts;
    private final AiModuleGeneratedDraftCommandCodec codec;
    private final Clock clock;

    @Autowired
    public JdbcModuleAiGeneratedDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            ReportService reports,
            PrintTemplateService prints,
            AiModuleGeneratedDraftContextReader contexts) {
        this(jdbc, ids, reports, prints, contexts,
                new AiModuleGeneratedDraftCommandCodec(), Clock.systemUTC());
    }

    JdbcModuleAiGeneratedDraftExecutionStore(
            JdbcTemplate jdbc,
            IdService ids,
            ReportService reports,
            PrintTemplateService prints,
            AiModuleGeneratedDraftContextReader contexts,
            AiModuleGeneratedDraftCommandCodec codec,
            Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.prints = Objects.requireNonNull(prints, "prints");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public AiModuleGeneratedDraftFacade.DraftReadback execute(
            AiModuleGeneratedDraftCommandCodec.Command command,
            AiModuleGeneratedDraftContextReader.OwnerContext context,
            Runnable liveFactCheck,
            String idempotencyKey,
            String requestId,
            String traceId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(context, "context");
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
        if (!stored.matches(command, idempotencyKey)) throw conflict();
        if (stored.resultJson() != null) {
            var replay = codec.readReadback(stored.resultJson());
            if (replay.operation() != command.operation()) throw conflict();
            return replay;
        }
        if (stored.id() != candidateId) {
            throw new IllegalStateException(
                    "Incomplete module AI draft ledger row is not recoverable");
        }

        liveFactCheck.run();
        var result = create(command, context, idempotencyKey, requestId, traceId);
        var updated = jdbc.update(
                COMPLETE_SQL, codec.writeReadback(result),
                timestamp(clock.instant()), command.systemId(),
                command.tenantId(), stored.id());
        if (updated != 1) {
            throw new IllegalStateException(
                    "Module AI draft ledger completion was not singular");
        }
        return result;
    }

    private AiModuleGeneratedDraftFacade.DraftReadback create(
            AiModuleGeneratedDraftCommandCodec.Command command,
            AiModuleGeneratedDraftContextReader.OwnerContext context,
            String idempotencyKey,
            String requestId,
            String traceId) {
        if (command.operation() == AiModuleGeneratedDraftFacade.Operation
                .CONFIG_REPORT_DRAFT) {
            var draft = command.report();
            var created = reports.create(
                    context.reportActor(), draft.code(), draft.name(),
                    draft.description(), new ReportDraft(
                            Long.parseLong(draft.dataSourceId()),
                            draft.outputFieldCodes()));
            return new AiModuleGeneratedDraftFacade.DraftReadback(
                    command.operation(), report(created), null);
        }
        var draft = command.printTemplate();
        var moduleId = contexts.printModuleId(
                context.configSession(), draft);
        var created = prints.create(
                context.configSession(), moduleId,
                new PrintViews.CreateTemplateRequest(
                        draft.code(), draft.name(), "DISABLED",
                        draft.paperSize(), draft.orientation(), draft.title(),
                        draft.fieldCodes(), draft.footer()),
                idempotencyKey, new RequestContext(requestId, traceId));
        return new AiModuleGeneratedDraftFacade.DraftReadback(
                command.operation(), null, print(created));
    }

    private List<Row> proposal(
            AiModuleGeneratedDraftCommandCodec.Command command) {
        return jdbc.query(FIND_PROPOSAL_SQL, this::row,
                command.systemId(), command.tenantId(), command.memberId(),
                command.proposalId());
    }

    private List<Row> idempotency(
            AiModuleGeneratedDraftCommandCodec.Command command,
            String idempotencyKey) {
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
                AiModuleGeneratedDraftFacade.Operation.valueOf(
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

    private static AiModuleGeneratedDraftFacade.ReportReadback report(
            ReportDefinition value) {
        return new AiModuleGeneratedDraftFacade.ReportReadback(
                Long.toString(value.id()), value.code(), value.name(),
                value.description(), Long.toString(value.draft().dataSourceId()),
                value.draft().outputFieldCodes(), value.draftVersion(),
                value.version(), value.createdAt(), value.updatedAt(),
                value.activeVersionId() != null);
    }

    private static AiModuleGeneratedDraftFacade.PrintTemplateReadback print(
            PrintViews.Template value) {
        return new AiModuleGeneratedDraftFacade.PrintTemplateReadback(
                value.templateId(), value.moduleId(), value.moduleCode(),
                value.code(), value.name(), value.paperSize(),
                value.orientation(), value.definition().title(),
                value.definition().fieldCodes(), value.definition().footer(),
                value.status(), value.version(),
                value.updatedAt().toInstant(ZoneOffset.UTC),
                value.publishedVersionId() != null);
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
                "AI_MODULE_DRAFT_IDEMPOTENCY_CONFLICT",
                "The module-generated draft proposal or idempotency key was already used",
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
            AiModuleGeneratedDraftFacade.Operation operation,
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
                AiModuleGeneratedDraftCommandCodec.Command command,
                String requestedIdempotencyKey) {
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
                    && AiModuleGeneratedDraftCommandSealer.equal(
                    payloadHash, command.payloadHash())
                    && idempotencyKey.equals(requestedIdempotencyKey);
        }
    }
}
